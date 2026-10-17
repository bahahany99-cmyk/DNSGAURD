package com.dnsguardian.app.security

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.dnsguardian.app.Constants
import com.dnsguardian.app.services.GuardianAccessibilityService
import com.dnsguardian.app.services.GuardianDeviceAdminReceiver

/**
 * Single source of truth for the protection state.
 *
 * Builds a list of ProtectionLayer objects and an honest overall verdict.
 * Important: the verdict NEVER reports "active" if a required layer is inactive,
 * unknown, or permission-required (principle: attempted != verified).
 */
class SecurityStateManager(private val context: Context) {

    enum class LayerState {
        ACTIVE_VERIFIED,
        ACTIVE_UNVERIFIED,
        INACTIVE,
        PERMISSION_REQUIRED,
        RECOVERY_REQUIRED,
        UNSUPPORTED,
        UNKNOWN
    }

    data class Layer(
        val id: String,
        val titleKey: String,
        val state: LayerState,
        val lastVerifiedAt: Long? = null,
        val detailKey: String? = null
    )

    data class Snapshot(
        val layers: List<Layer>,
        val overall: LayerState,
        val lastRecoveryCheckAt: Long?,
        val protectionPaused: Boolean
    )

    private val prefs by lazy {
        context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)
    }

    fun isPaused(): Boolean = prefs.getBoolean(Constants.PREF_PAUSED, false)

    fun setPaused(paused: Boolean) {
        prefs.edit().putBoolean(Constants.PREF_PAUSED, paused).apply()
    }

    private fun isAccessibilityEnabled(): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        val component = ComponentName(context, GuardianAccessibilityService::class.java)
        return enabled.any { it.resolveInfo.serviceInfo != null &&
            ComponentName(it.resolveInfo.serviceInfo.packageName, it.resolveInfo.serviceInfo.name) == component }
    }

    private fun isDeviceAdminActive(): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val component = ComponentName(context, GuardianDeviceAdminReceiver::class.java)
        return try { dpm.isAdminActive(component) } catch (_: Exception) { false }
    }

    private fun notificationsEnabled(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            return nm.areNotificationsEnabled()
        }
        return true // Not required before API 33
    }

    private fun privateDnsMode(): String? = try {
        Settings.Global.getString(context.contentResolver, Constants.SETTINGS_PRIVATE_DNS_MODE)
    } catch (_: Exception) { null }

    /** Re-evaluate all layers. */
    fun buildSnapshot(): Snapshot {
        val layers = mutableListOf<Layer>()

        // 1. Private DNS Monitor (ContentObserver) — best-effort presence.
        val dnsMode = privateDnsMode()
        layers.add(
            Layer(
                id = "private_dns_monitor",
                titleKey = "privateDnsMonitor",
                state = if (dnsMode != null) LayerState.ACTIVE_UNVERIFIED else LayerState.UNKNOWN,
                detailKey = "Observes private_dns_mode changes."
            )
        )

        // 2. Accessibility Service (required for navigation detection).
        layers.add(
            Layer(
                id = "accessibility_service",
                titleKey = "accessibilityService",
                state = if (isAccessibilityEnabled()) LayerState.ACTIVE_VERIFIED else LayerState.PERMISSION_REQUIRED
            )
        )

        // 3. App Management Monitor — depends on Accessibility (shares detection).
        layers.add(
            Layer(
                id = "app_management_monitor",
                titleKey = "appManagementMonitor",
                state = if (isAccessibilityEnabled()) LayerState.ACTIVE_UNVERIFIED else LayerState.PERMISSION_REQUIRED
            )
        )

        // 4. Password Lock.
        layers.add(
            Layer(
                id = "password_lock",
                titleKey = "passwordLock",
                state = if (Crypto.hasPassword(context)) LayerState.ACTIVE_VERIFIED else LayerState.PERMISSION_REQUIRED
            )
        )

        // 5. Security History (always functional if DB present).
        layers.add(
            Layer(
                id = "security_history",
                titleKey = "securityHistory",
                state = LayerState.ACTIVE_VERIFIED
            )
        )

        // 6. Reboot Recovery (WorkManager scheduled).
        val lastCheck = prefs.getLong("last_recovery_check", 0L)
        layers.add(
            Layer(
                id = "recovery_boot",
                titleKey = "recoveryBoot",
                state = if (lastCheck > 0) LayerState.ACTIVE_VERIFIED else LayerState.UNKNOWN,
                lastVerifiedAt = if (lastCheck > 0) lastCheck else null
            )
        )

        // 7. Device Admin (optional; UNSUPPORTED if not active on this device — honest).
        layers.add(
            Layer(
                id = "device_admin",
                titleKey = "deviceAdmin",
                state = if (isDeviceAdminActive()) LayerState.ACTIVE_VERIFIED else LayerState.UNSUPPORTED,
                detailKey = "Optional layer; does not prevent uninstall."
            )
        )

        val overall = deriveOverall(layers, isPaused())
        return Snapshot(
            layers = layers,
            overall = overall,
            lastRecoveryCheckAt = if (lastCheck > 0) lastCheck else null,
            protectionPaused = isPaused()
        )
    }

    private fun deriveOverall(layers: List<Layer>, paused: Boolean): LayerState {
        if (paused) return LayerState.UNSUPPORTED
        var worst: LayerState = LayerState.ACTIVE_VERIFIED
        for (l in layers) {
            if (l.id == "device_admin" && l.state == LayerState.UNSUPPORTED) continue
            // States that downgrade the verdict
            if (l.state == LayerState.INACTIVE ||
                l.state == LayerState.PERMISSION_REQUIRED ||
                l.state == LayerState.RECOVERY_REQUIRED ||
                l.state == LayerState.UNKNOWN
            ) {
                if (l.state.ordinal > worst.ordinal) worst = l.state
            }
        }
        return worst
    }

    fun toJson(snapshot: Snapshot): Map<String, Any?> = mapOf(
        "layers" to snapshot.layers.map { layer ->
            mapOf(
                "id" to layer.id,
                "titleKey" to layer.titleKey,
                "state" to layer.state.name,
                "lastVerifiedAt" to (layer.lastVerifiedAt ?: JSONObject.NULL),
                "detailKey" to (layer.detailKey ?: JSONObject.NULL)
            )
        },
        "lastRecoveryCheckAt" to (snapshot.lastRecoveryCheckAt ?: JSONObject.NULL),
        "protectionPaused" to snapshot.protectionPaused
    )

    companion object {
        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        fun openDeviceAdminSettings(context: Context) {
            val intent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
