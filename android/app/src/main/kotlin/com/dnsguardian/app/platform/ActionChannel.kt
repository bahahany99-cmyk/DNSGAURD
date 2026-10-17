package com.dnsguardian.app.platform

import android.content.Context
import com.dnsguardian.app.Constants
import com.dnsguardian.app.security.SecurityStateManager
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

/**
 * User-initiated actions from the Flutter UI:
 *  - pauseProtection / resumeProtection: authenticated maintenance pause (honest: marks paused).
 *  - openAccessibilitySettings / openDeviceAdminSettings: deep links to system settings.
 */
class ActionChannel(private val context: Context) : MethodChannel.MethodCallHandler {
    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        val mgr = SecurityStateManager(context)
        when (call.method) {
            "pauseProtection" -> {
                mgr.setPaused(true)
                result.success(null)
            }
            "resumeProtection" -> {
                mgr.setPaused(false)
                result.success(null)
            }
            "openAccessibilitySettings" -> {
                SecurityStateManager.openAccessibilitySettings(context)
                result.success(null)
            }
            "openDeviceAdminSettings" -> {
                SecurityStateManager.openDeviceAdminSettings(context)
                result.success(null)
            }
            else -> result.notImplemented()
        }
    }
}
