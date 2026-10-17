package com.dnsguardian.app.services

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.dnsguardian.app.R
import com.dnsguardian.app.security.EventStore

/**
 * Optional Device Admin receiver.
 *
 * IMPORTANT (honest limitation): On Android 15, a conventional Device Admin receiver
 * does NOT prevent uninstalling the app, force-stop, or changing system settings.
 * We only use onDisableRequested to show a warning and log the attempt. The dashboard
 * marks this layer UNSUPPORTED when not active. We never claim Device Owner powers.
 */
class GuardianDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        context.let {
            EventStore(it).addEvent(
                type = "STARTUP_RECOVERY_CHECK",
                tsMillis = System.currentTimeMillis(),
                elapsedRealtimeMs = android.os.SystemClock.elapsedRealtime(),
                source = "device_admin",
                component = "device_admin",
                responseAttempted = "ADMIN_ENABLED",
                responseVerified = true
            )
        }
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        context.let {
            EventStore(it).addEvent(
                type = "COMPONENT_DISABLED",
                tsMillis = System.currentTimeMillis(),
                elapsedRealtimeMs = android.os.SystemClock.elapsedRealtime(),
                source = "device_admin",
                component = "device_admin",
                responseAttempted = "ADMIN_DISABLED",
                responseVerified = true,
                diagnostic = "Device admin deactivated by user."
            )
        }
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        // Show a warning; note we cannot block the action.
        context.let {
            EventStore(it).addEvent(
                type = "DEVICE_ADMIN_DEACTIVATE_ATTEMPT",
                tsMillis = System.currentTimeMillis(),
                elapsedRealtimeMs = android.os.SystemClock.elapsedRealtime(),
                source = "device_admin",
                component = "device_admin",
                responseAttempted = "WARNING_SHOWN",
                responseVerified = false,
                diagnostic = "Deactivation attempted; warning shown. Cannot block (non-DO)."
            )
            Toast.makeText(it, it.getString(R.string.device_admin_warning), Toast.LENGTH_LONG).show()
        }
        return context?.getString(R.string.device_admin_warning)
            ?: "Warning: deactivating DNS Guardian protection."
    }
}
