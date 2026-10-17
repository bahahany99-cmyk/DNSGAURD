package com.dnsguardian.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.dnsguardian.app.MainActivity
import com.dnsguardian.app.security.EventStore
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Local, DNS-only VPN.
 *
 * Android is given a virtual DNS server and only that /32 is routed into the TUN.
 * Consequently ordinary web/application traffic stays on the underlying network.
 * DNS packets received on the TUN are carried to the family resolver over
 * certificate-validated DNS-over-TLS. Applications using hard-coded IPs, their
 * own VPN, or in-app DNS-over-HTTPS are outside this system-resolver path.
 */
class LocalDnsVpnService : VpnService() {

    private val stopping = AtomicBoolean(false)
    private var tun: ParcelFileDescriptor? = null
    private var worker: Thread? = null
    private var dotClient: DnsOverTlsClient? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn("user_request")
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        if (!running.get()) startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        stopping.set(false)
        try {
            // Resolve before establishing the VPN so bootstrap lookup cannot be
            // directed back to the virtual DNS endpoint.
            val upstream = InetAddress.getAllByName(UPSTREAM_HOST)
                .firstOrNull { it is Inet4Address }
                ?: throw IOException("No IPv4 upstream address")

            val configureIntent = PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val descriptor = Builder()
                .setSession(SESSION_NAME)
                .setMtu(MTU)
                .addAddress(TUN_ADDRESS, 32)
                .addRoute(VIRTUAL_DNS_ADDRESS, 32)
                .addDnsServer(VIRTUAL_DNS_ADDRESS)
                .setConfigureIntent(configureIntent)
                .setBlocking(true)
                .establish()
                ?: throw IOException("VPN consent unavailable or TUN creation rejected")

            tun = descriptor
            dotClient = DnsOverTlsClient(upstream)
            running.set(true)
            record(
                type = "LOCAL_DNS_VPN_STARTED",
                response = "TUN_ESTABLISHED_DOT_FORWARDER_STARTED",
                verified = true,
                diagnostic = "DNS-only TUN established; upstream hostname and query data were not logged."
            )
            ProtectionEventBroadcaster.notifyStateChanged(this)

            worker = Thread({ packetLoop(descriptor) }, "dns-guardian-vpn").apply {
                isDaemon = true
                start()
            }
        } catch (_: Throwable) {
            record(
                type = "LOCAL_DNS_VPN_START_FAILED",
                response = "VPN_START_FAILED",
                verified = false,
                diagnostic = "Local DNS VPN startup failed; no packet or hostname data was logged."
            )
            stopVpn("startup_failure")
        }
    }

    private fun packetLoop(descriptor: ParcelFileDescriptor) {
        val input = FileInputStream(descriptor.fileDescriptor)
        val output = FileOutputStream(descriptor.fileDescriptor)
        val packet = ByteArray(MAX_PACKET_SIZE)
        try {
            while (!stopping.get()) {
                val length = input.read(packet)
                if (length <= 0) continue
                val request = parseDnsPacket(packet, length) ?: continue
                val responsePayload = try {
                    dotClient?.query(request.payload)
                } catch (_: Throwable) {
                    null
                } ?: buildServerFailure(request.payload)
                val responsePacket = buildUdpResponse(request, responsePayload)
                output.write(responsePacket)
            }
        } catch (_: IOException) {
            // Closing the TUN during stop/revoke intentionally interrupts read().
        } catch (_: Throwable) {
            record(
                type = "LOCAL_DNS_VPN_RUNTIME_FAILED",
                response = "PACKET_LOOP_STOPPED",
                verified = false,
                diagnostic = "Local DNS packet loop stopped unexpectedly; packet data was not logged."
            )
        } finally {
            if (!stopping.get()) stopVpn("packet_loop_exit")
        }
    }

    private data class DnsPacket(
        val sourceAddress: ByteArray,
        val sourcePort: Int,
        val payload: ByteArray
    )

    private fun parseDnsPacket(packet: ByteArray, length: Int): DnsPacket? {
        if (length < MIN_IPV4_UDP_PACKET) return null
        if ((packet[0].toInt() ushr 4) != 4) return null
        val headerLength = (packet[0].toInt() and 0x0f) * 4
        if (headerLength < IPV4_HEADER_SIZE || length < headerLength + UDP_HEADER_SIZE) return null
        if ((packet[9].toInt() and 0xff) != UDP_PROTOCOL) return null
        // Ignore fragmented packets. Typical DNS queries fit comfortably in MTU.
        val fragmentBits = readU16(packet, 6)
        if ((fragmentBits and 0x3fff) != 0) return null

        val destination = packet.copyOfRange(16, 20)
        if (!destination.contentEquals(VIRTUAL_DNS_BYTES)) return null
        val udpOffset = headerLength
        if (readU16(packet, udpOffset + 2) != DNS_PORT) return null
        val udpLength = readU16(packet, udpOffset + 4)
        if (udpLength < UDP_HEADER_SIZE || udpOffset + udpLength > length) return null
        val payload = packet.copyOfRange(
            udpOffset + UDP_HEADER_SIZE,
            udpOffset + udpLength
        )
        if (payload.size < DNS_HEADER_SIZE) return null
        return DnsPacket(
            sourceAddress = packet.copyOfRange(12, 16),
            sourcePort = readU16(packet, udpOffset),
            payload = payload
        )
    }

    private fun buildUdpResponse(request: DnsPacket, payload: ByteArray): ByteArray {
        val udpLength = UDP_HEADER_SIZE + payload.size
        val totalLength = IPV4_HEADER_SIZE + udpLength
        val packet = ByteArray(totalLength)
        packet[0] = 0x45
        writeU16(packet, 2, totalLength)
        writeU16(packet, 4, nextPacketId())
        writeU16(packet, 6, 0x4000) // Don't fragment.
        packet[8] = 64
        packet[9] = UDP_PROTOCOL.toByte()
        System.arraycopy(VIRTUAL_DNS_BYTES, 0, packet, 12, 4)
        System.arraycopy(request.sourceAddress, 0, packet, 16, 4)
        writeU16(packet, 10, checksum(packet, 0, IPV4_HEADER_SIZE))

        val udpOffset = IPV4_HEADER_SIZE
        writeU16(packet, udpOffset, DNS_PORT)
        writeU16(packet, udpOffset + 2, request.sourcePort)
        writeU16(packet, udpOffset + 4, udpLength)
        System.arraycopy(payload, 0, packet, udpOffset + UDP_HEADER_SIZE, payload.size)
        val udpChecksum = udpChecksum(packet, udpOffset, udpLength)
        writeU16(packet, udpOffset + 6, if (udpChecksum == 0) 0xffff else udpChecksum)
        return packet
    }

    private fun buildServerFailure(query: ByteArray): ByteArray {
        val response = query.copyOf()
        // Preserve the ID/question and return QR=1, RA=1, RCODE=SERVFAIL.
        val requestFlags = readU16(response, 2)
        writeU16(response, 2, (requestFlags and 0x7900) or 0x8082)
        writeU16(response, 6, 0)
        writeU16(response, 8, 0)
        writeU16(response, 10, 0)
        return response
    }

    private fun udpChecksum(packet: ByteArray, udpOffset: Int, udpLength: Int): Int {
        var sum = 0L
        fun add(value: Int) {
            sum += value.toLong()
            while (sum > 0xffff) sum = (sum and 0xffff) + (sum ushr 16)
        }
        for (offset in 12 until 20 step 2) add(readU16(packet, offset))
        add(UDP_PROTOCOL)
        add(udpLength)
        var offset = udpOffset
        val end = udpOffset + udpLength
        while (offset + 1 < end) {
            add(readU16(packet, offset))
            offset += 2
        }
        if (offset < end) add((packet[offset].toInt() and 0xff) shl 8)
        return sum.inv().toInt() and 0xffff
    }

    private fun checksum(bytes: ByteArray, start: Int, length: Int): Int {
        var sum = 0L
        var offset = start
        val end = start + length
        while (offset + 1 < end) {
            sum += readU16(bytes, offset).toLong()
            while (sum > 0xffff) sum = (sum and 0xffff) + (sum ushr 16)
            offset += 2
        }
        if (offset < end) sum += ((bytes[offset].toInt() and 0xff) shl 8).toLong()
        while (sum > 0xffff) sum = (sum and 0xffff) + (sum ushr 16)
        return sum.inv().toInt() and 0xffff
    }

    private fun readU16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 8) or
            (bytes[offset + 1].toInt() and 0xff)

    private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 8).toByte()
        bytes[offset + 1] = value.toByte()
    }

    @Synchronized
    private fun nextPacketId(): Int {
        packetId = (packetId + 1) and 0xffff
        return packetId
    }

    /** A persistent, certificate-validated DNS-over-TLS connection. */
    private inner class DnsOverTlsClient(private val upstream: InetAddress) {
        private var socket: SSLSocket? = null
        private var input: DataInputStream? = null
        private var output: DataOutputStream? = null

        @Synchronized
        fun query(payload: ByteArray): ByteArray {
            require(payload.size in DNS_HEADER_SIZE..0xffff)
            repeat(2) { attempt ->
                try {
                    ensureConnected()
                    output!!.writeShort(payload.size)
                    output!!.write(payload)
                    output!!.flush()
                    val responseLength = input!!.readUnsignedShort()
                    if (responseLength < DNS_HEADER_SIZE || responseLength > MAX_DNS_MESSAGE) {
                        throw IOException("Invalid DNS-over-TLS response length")
                    }
                    return ByteArray(responseLength).also(input!!::readFully)
                } catch (error: Throwable) {
                    close()
                    if (attempt == 1) throw error
                }
            }
            throw IOException("DNS-over-TLS query failed")
        }

        private fun ensureConnected() {
            if (socket?.isConnected == true && socket?.isClosed == false) return
            val raw = Socket()
            if (!protect(raw)) {
                raw.close()
                throw IOException("Could not protect upstream socket from VPN")
            }
            raw.connect(InetSocketAddress(upstream, DOT_PORT), IO_TIMEOUT_MS)
            raw.soTimeout = IO_TIMEOUT_MS
            val tls = SSLSocketFactory.getDefault().createSocket(
                raw,
                UPSTREAM_HOST,
                DOT_PORT,
                true
            ) as SSLSocket
            tls.sslParameters = tls.sslParameters.apply {
                endpointIdentificationAlgorithm = "HTTPS"
                serverNames = listOf(SNIHostName(UPSTREAM_HOST))
            }
            tls.startHandshake()
            socket = tls
            input = DataInputStream(tls.inputStream)
            output = DataOutputStream(tls.outputStream)
        }

        @Synchronized
        fun close() {
            try {
                socket?.close()
            } catch (_: Throwable) {
                // Best-effort shutdown.
            }
            socket = null
            input = null
            output = null
        }
    }

    override fun onRevoke() {
        stopVpn("system_revoke")
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpn("service_destroy")
        super.onDestroy()
    }

    private fun stopVpn(reason: String) {
        if (!stopping.compareAndSet(false, true) && !running.get()) {
            stopSelf()
            return
        }
        running.set(false)
        dotClient?.close()
        dotClient = null
        try {
            tun?.close()
        } catch (_: Throwable) {
            // Best-effort shutdown.
        }
        tun = null
        worker?.interrupt()
        worker = null
        record(
            type = "LOCAL_DNS_VPN_STOPPED",
            response = "TUN_CLOSED",
            verified = true,
            diagnostic = "Local DNS VPN stopped; reason=$reason."
        )
        ProtectionEventBroadcaster.notifyStateChanged(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Local DNS VPN",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when DNS Guardian's local DNS-only VPN is active"
            }
        )
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("DNS Guardian")
            .setContentText("Local DNS-only VPN protection is active")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun record(
        type: String,
        response: String,
        verified: Boolean,
        diagnostic: String
    ) {
        EventStore(this).addEvent(
            type = type,
            tsMillis = System.currentTimeMillis(),
            elapsedRealtimeMs = android.os.SystemClock.elapsedRealtime(),
            source = "local_dns_vpn",
            component = "local_dns_vpn",
            responseAttempted = response,
            responseVerified = verified,
            diagnostic = diagnostic
        )
    }

    companion object {
        const val ACTION_START = "com.dnsguardian.app.action.START_LOCAL_DNS_VPN"
        const val ACTION_STOP = "com.dnsguardian.app.action.STOP_LOCAL_DNS_VPN"

        private const val SESSION_NAME = "DNS Guardian Local DNS"
        private const val CHANNEL_ID = "dns_guardian_local_vpn"
        private const val NOTIFICATION_ID = 7410
        private const val UPSTREAM_HOST = "family.adguard-dns.com"
        private const val DOT_PORT = 853
        private const val DNS_PORT = 53
        private const val TUN_ADDRESS = "10.111.222.1"
        private const val VIRTUAL_DNS_ADDRESS = "10.111.222.2"
        private val VIRTUAL_DNS_BYTES = byteArrayOf(10, 111, 222.toByte(), 2)
        private const val MTU = 1500
        private const val MAX_PACKET_SIZE = 32767
        private const val MAX_DNS_MESSAGE = 65535
        private const val IPV4_HEADER_SIZE = 20
        private const val UDP_HEADER_SIZE = 8
        private const val DNS_HEADER_SIZE = 12
        private const val MIN_IPV4_UDP_PACKET = IPV4_HEADER_SIZE + UDP_HEADER_SIZE + DNS_HEADER_SIZE
        private const val UDP_PROTOCOL = 17
        private const val IO_TIMEOUT_MS = 5000

        private val running = AtomicBoolean(false)
        private var packetId = 0

        fun isRunning(): Boolean = running.get()
    }
}
