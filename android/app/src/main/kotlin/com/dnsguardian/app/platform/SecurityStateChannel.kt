package com.dnsguardian.app.platform

import android.content.Context
import android.util.Log
import com.dnsguardian.app.security.SecurityStateManager
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

/**
 * Exposes the current SecurityState snapshot to Flutter.
 * Method "getState" -> Map (mirrors lib/core/security_state.dart).
 * Method "refresh" -> queries current facts without creating recovery evidence.
 */
class SecurityStateChannel(private val context: Context) : MethodChannel.MethodCallHandler {
    private val mgr = SecurityStateManager(context)

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        try {
            when (call.method) {
            "getState" -> {
                val snapshot = mgr.buildSnapshot()
                result.success(mgr.toJson(snapshot))
            }
            "refresh" -> {
                // A dashboard refresh cannot prove that boot recovery ran.
                mgr.buildSnapshot()
                result.success(null)
            }
                else -> result.notImplemented()
            }
        } catch (error: Throwable) {
            Log.e("DNSGuardian.Startup", "stage=state_channel_${call.method}_failed", error)
            result.error("STATE_UNAVAILABLE", "Local security state is unavailable", null)
        }
    }
}
