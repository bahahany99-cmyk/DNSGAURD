package com.dnsguardian.app.platform

import android.content.Context
import com.dnsguardian.app.security.EventStore
import com.dnsguardian.app.security.SecurityStateManager
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

/**
 * Exposes the current SecurityState snapshot to Flutter.
 * Method "getState" -> Map (mirrors lib/core/security_state.dart).
 * Method "refresh" -> rebuilds & stores last_recovery_check timestamp, returns nothing.
 */
class SecurityStateChannel(private val context: Context) : MethodChannel.MethodCallHandler {
    private val mgr = SecurityStateManager(context)

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "getState" -> {
                val snapshot = mgr.buildSnapshot()
                result.success(mgr.toJson(snapshot))
            }
            "refresh" -> {
                val prefs = context.getSharedPreferences(
                    com.dnsguardian.app.Constants.PREF_NAME, Context.MODE_PRIVATE
                )
                prefs.edit()
                    .putLong("last_recovery_check", System.currentTimeMillis())
                    .apply()
                EventStore(context).addEvent(
                    type = "STARTUP_RECOVERY_CHECK",
                    tsMillis = System.currentTimeMillis(),
                    elapsedRealtimeMs = android.os.SystemClock.elapsedRealtime(),
                    source = "manual_refresh",
                    component = "recovery_boot",
                    responseAttempted = "MANUAL_REFRESH",
                    responseVerified = true
                )
                result.success(null)
            }
            else -> result.notImplemented()
        }
    }
}
