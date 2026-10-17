package com.dnsguardian.app.security

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.dnsguardian.app.Constants
import com.dnsguardian.app.services.ProtectionEventBroadcaster

/**
 * Observes Settings.Global for Private DNS mode/specifier changes.
 *
 * NOTE: This app CANNOT write secure settings (no WRITE_SECURE_SETTINGS / Device Owner).
 * So detection + honest reporting + manual-recovery guidance is all we do. We never
 * silently restore a DNS provider (requirement 7.5/7.6). We store the last observed
 * value only to report deltas — we DO NOT store the specifier hostname by default
 * (privacy: avoid leaking the owner's chosen provider).
 */
class DnsMonitor(private val context: Context) {

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            val path = uri?.lastPathSegment ?: return
            if (path == Constants.SETTINGS_PRIVATE_DNS_MODE ||
                path == Constants.SETTINGS_PRIVATE_DNS_SPECIFIER
            ) {
                val mode = try {
                    Settings.Global.getString(context.contentResolver, Constants.SETTINGS_PRIVATE_DNS_MODE)
                } catch (_: Exception) { null }
                // Log change (mode only; never the specifier hostname).
                val store = EventStore(context)
                store.addEvent(
                    type = "DNS_CONFIG_CHANGED",
                    tsMillis = System.currentTimeMillis(),
                    elapsedRealtimeMs = android.os.SystemClock.elapsedRealtime(),
                    source = "content_observer",
                    component = "private_dns_monitor",
                    responseAttempted = "MANUAL_RECOVERY_REQUIRED",
                    responseVerified = false,
                    diagnostic = "new_mode=$mode (specifier not recorded by design)"
                )
                ProtectionEventBroadcaster.notifyStateChanged(context)
            }
        }
    }

    fun register() {
        // Register for the whole Global table; filter inside onChange.
        context.contentResolver.registerContentObserver(
            Settings.Global.CONTENT_URI, true, observer
        )
    }

    fun unregister() {
        context.contentResolver.unregisterContentObserver(observer)
    }
}
