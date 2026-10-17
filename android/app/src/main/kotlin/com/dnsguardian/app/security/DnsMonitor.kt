package com.dnsguardian.app.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.dnsguardian.app.Constants
import com.dnsguardian.app.services.ProtectionEventBroadcaster

/**
 * Process-local observer for Android's Private DNS global settings.
 *
 * When WRITE_SECURE_SETTINGS has been granted explicitly (normally with ADB),
 * mismatches are repaired to the fixed family-filtering provider. Without that
 * grant this class remains read-only and reports that manual recovery is needed.
 * Android may kill an ordinary application process, so this is best effort rather
 * than a Device Owner guarantee; boot/application startup performs another check.
 */
class DnsMonitor(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private var repairRunning = false

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            val path = uri?.lastPathSegment
            if (path != null &&
                path != Constants.SETTINGS_PRIVATE_DNS_MODE &&
                path != Constants.SETTINGS_PRIVATE_DNS_SPECIFIER
            ) return
            inspectAndRepair(source = "content_observer")
        }
    }

    fun register() {
        context.contentResolver.registerContentObserver(
            Settings.Global.CONTENT_URI,
            true,
            observer
        )
        // Reconcile immediately on every process start instead of waiting for a
        // future settings notification.
        inspectAndRepair(source = "application_start")
    }

    fun unregister() {
        context.contentResolver.unregisterContentObserver(observer)
    }

    /** Reconcile after an explicit permission-status refresh (for example, returning from ADB). */
    fun reconcileNow(source: String = "explicit_reconcile") {
        inspectAndRepair(source)
    }

    private fun inspectAndRepair(source: String) {
        if (repairRunning) return

        val resolver = context.contentResolver
        val mode: String?
        val specifier: String?
        try {
            mode = Settings.Global.getString(resolver, Constants.SETTINGS_PRIVATE_DNS_MODE)
            specifier = Settings.Global.getString(
                resolver,
                Constants.SETTINGS_PRIVATE_DNS_SPECIFIER
            )
        } catch (_: Throwable) {
            record(
                source = source,
                response = "DNS_STATE_QUERY_FAILED",
                verified = false,
                diagnostic = "Private DNS state query failed; values were not recorded."
            )
            return
        }

        if (mode == Constants.REQUIRED_PRIVATE_DNS_MODE &&
            specifier == Constants.REQUIRED_PRIVATE_DNS_SPECIFIER
        ) return

        if (!hasWriteSecureSettingsPermission(context)) {
            record(
                source = source,
                response = "MANUAL_RECOVERY_REQUIRED",
                verified = false,
                diagnostic = "Private DNS mismatch detected; WRITE_SECURE_SETTINGS is not granted."
            )
            return
        }

        repairRunning = true
        try {
            // Write the hostname first so switching to hostname mode never points
            // at an empty provider. Check both return values and read back state.
            val specifierWritten = Settings.Global.putString(
                resolver,
                Constants.SETTINGS_PRIVATE_DNS_SPECIFIER,
                Constants.REQUIRED_PRIVATE_DNS_SPECIFIER
            )
            val modeWritten = Settings.Global.putString(
                resolver,
                Constants.SETTINGS_PRIVATE_DNS_MODE,
                Constants.REQUIRED_PRIVATE_DNS_MODE
            )
            val verified = specifierWritten && modeWritten &&
                Settings.Global.getString(
                    resolver,
                    Constants.SETTINGS_PRIVATE_DNS_MODE
                ) == Constants.REQUIRED_PRIVATE_DNS_MODE &&
                Settings.Global.getString(
                    resolver,
                    Constants.SETTINGS_PRIVATE_DNS_SPECIFIER
                ) == Constants.REQUIRED_PRIVATE_DNS_SPECIFIER

            record(
                source = source,
                response = "WRITE_SECURE_SETTINGS_REPAIR",
                verified = verified,
                diagnostic = if (verified) {
                    "Private DNS repair was written and read back successfully; values were not recorded."
                } else {
                    "Private DNS repair was attempted but read-back verification failed; values were not recorded."
                }
            )
        } catch (_: Throwable) {
            record(
                source = source,
                response = "WRITE_SECURE_SETTINGS_REPAIR_FAILED",
                verified = false,
                diagnostic = "Private DNS repair failed; values were not recorded."
            )
        } finally {
            repairRunning = false
        }
    }

    private fun record(
        source: String,
        response: String,
        verified: Boolean,
        diagnostic: String
    ) {
        EventStore(context).addEvent(
            type = "DNS_CONFIG_CHANGED",
            tsMillis = System.currentTimeMillis(),
            elapsedRealtimeMs = android.os.SystemClock.elapsedRealtime(),
            source = source,
            component = "private_dns_monitor",
            responseAttempted = response,
            responseVerified = verified,
            diagnostic = diagnostic
        )
        ProtectionEventBroadcaster.notifyStateChanged(context)
    }

    companion object {
        fun hasWriteSecureSettingsPermission(context: Context): Boolean =
            context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
                PackageManager.PERMISSION_GRANTED
    }
}
