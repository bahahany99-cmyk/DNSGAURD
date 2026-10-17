package com.dnsguardian.app.platform

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.dnsguardian.app.security.SecurityStateManager
import com.dnsguardian.app.services.GuardianDeviceAdminReceiver
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

/**
 * Authenticated maintenance actions and explicit Android settings routing.
 * These actions never grant permissions themselves and never modify Private DNS.
 */
class ActionChannel(
    private val activity: Activity
) : MethodChannel.MethodCallHandler {
    private val stateManager = SecurityStateManager(activity.applicationContext)
    private var pendingDeviceAdminResult: MethodChannel.Result? = null

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        try {
            when (call.method) {
                "pauseProtection" -> result.success(stateManager.setPaused(true))
                "resumeProtection" -> result.success(stateManager.setPaused(false))
                "openAccessibilitySettings" -> result.success(openAccessibilitySettings())
                "canDrawOverlays" -> result.success(Settings.canDrawOverlays(activity))
                "hasWriteSecureSettings" -> {
                    val granted = com.dnsguardian.app.security.DnsMonitor
                        .hasWriteSecureSettingsPermission(activity)
                    if (granted) {
                        // Returning to Protected Settings after an ADB grant also
                        // performs an immediate reconciliation; the process-wide
                        // observer handles subsequent changes.
                        com.dnsguardian.app.security.DnsMonitor(
                            activity.applicationContext
                        ).reconcileNow(source = "permission_status_check")
                    }
                    result.success(granted)
                }
                "openOverlayPermissionSettings" -> result.success(openOverlayPermissionSettings())
                "openDeviceAdminSettings" -> openDeviceAdminActivation(result)
                else -> result.notImplemented()
            }
        } catch (_: Exception) {
            // Return only a non-sensitive local error code to Flutter.
            result.error("ACTION_FAILED", "Requested local action failed", null)
        }
    }

    private fun openAccessibilitySettings(): Boolean {
        // Some OEM Settings apps expose this documented action string even
        // though it is not a public Settings constant in every compile SDK.
        val direct = Intent(ACTION_ACCESSIBILITY_DETAILS).apply {
            data = Uri.parse("package:${activity.packageName}")
        }
        val fallback = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        return startSettingsIntent(if (direct.resolveActivity(activity.packageManager) != null) direct else fallback)
    }

    private fun openOverlayPermissionSettings(): Boolean = try {
        activity.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${activity.packageName}")
            )
        )
        true
    } catch (_: Exception) {
        try {
            activity.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun startSettingsIntent(intent: Intent): Boolean = try {
        activity.startActivity(intent)
        true
    } catch (_: Exception) {
        try {
            activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun openDeviceAdminActivation(result: MethodChannel.Result) {
        if (pendingDeviceAdminResult != null) {
            result.error("ACTION_IN_PROGRESS", "Device Admin activation is already open", null)
            return
        }

        val receiver = ComponentName(activity, GuardianDeviceAdminReceiver::class.java)
        val dpm = activity.getSystemService(DevicePolicyManager::class.java)
        if (dpm == null) {
            result.success(false)
            return
        }
        if (dpm.isAdminActive(receiver)) {
            result.success(true)
            return
        }

        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, receiver)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Optional DNS Guardian layer. This is not Device Owner and does not guarantee uninstall prevention."
            )
        }
        try {
            pendingDeviceAdminResult = result
            activity.startActivityForResult(intent, REQUEST_ADD_DEVICE_ADMIN)
        } catch (_: Exception) {
            pendingDeviceAdminResult = null
            result.success(false)
        }
    }

    fun onActivityResult(requestCode: Int): Boolean {
        if (requestCode != REQUEST_ADD_DEVICE_ADMIN) return false
        val result = pendingDeviceAdminResult ?: return true
        pendingDeviceAdminResult = null
        val receiver = ComponentName(activity, GuardianDeviceAdminReceiver::class.java)
        val active = try {
            activity.getSystemService(DevicePolicyManager::class.java)?.isAdminActive(receiver) == true
        } catch (_: Exception) {
            false
        }
        result.success(active)
        return true
    }

    fun dispose() {
        pendingDeviceAdminResult?.error("ACTIVITY_DESTROYED", "Activation flow interrupted", null)
        pendingDeviceAdminResult = null
    }

    companion object {
        private const val ACTION_ACCESSIBILITY_DETAILS =
            "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"
        private const val REQUEST_ADD_DEVICE_ADMIN = 7401
    }
}
