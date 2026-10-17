package com.dnsguardian.app.platform

import android.content.Context
import android.util.Log
import com.dnsguardian.app.security.SecurityStateManager
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

/**
 * Channel: dns_guardian/security_state (registered in MainActivity).
 * getState returns the Android-backed dashboard snapshot; refresh only executes
 * the same reads and never manufactures reboot-recovery evidence.
 */
class SecurityStateChannel(private val context: Context) : MethodChannel.MethodCallHandler {
    private val mgr = SecurityStateManager(context)

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        val queryId = "security_state.${call.method}"
        Log.d(TAG, "query=$queryId stage=native_handler_reached")
        try {
            when (call.method) {
                "getState" -> {
                    val snapshot = mgr.buildSnapshot()
                    Log.d(TAG, "query=$queryId stage=snapshot_built layers=${snapshot.layers.size}")
                    result.success(mgr.toJson(snapshot))
                    Log.d(TAG, "query=$queryId stage=result_submitted_to_flutter")
                }
                "refresh" -> {
                    mgr.buildSnapshot()
                    result.success(null)
                    Log.d(TAG, "query=$queryId stage=result_submitted_to_flutter")
                }
                else -> result.notImplemented()
            }
        } catch (error: Throwable) {
            val type = error.javaClass.simpleName
            Log.e(
                TAG,
                "query=$queryId stage=native_handler_failed " +
                    "type=$type description=snapshot_unavailable"
            )
            result.error(
                "STATE_UNAVAILABLE",
                "Local security state is unavailable",
                mapOf(
                    "query" to queryId,
                    "stage" to "native_handler",
                    "exceptionType" to type
                )
            )
        }
    }

    companion object {
        private const val TAG = "DNSGuardian.State"
    }
}
