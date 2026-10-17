package com.dnsguardian.app.security

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import com.dnsguardian.app.Constants
import com.dnsguardian.app.services.GuardianAccessibilityService
import com.dnsguardian.app.services.GuardianDeviceAdminReceiver

/** Android-backed, honest snapshot of every Phase 2 dashboard layer. */
class SecurityStateManager(private val context: Context) {

    enum class LayerState(val wireName: String) {
        ACTIVE_VERIFIED("activeVerified"),
        ACTIVE_UNVERIFIED("activeUnverified"),
        INACTIVE("inactive"),
        PERMISSION_REQUIRED("permissionRequired"),
        RECOVERY_REQUIRED("recoveryRequired"),
        UNSUPPORTED("unsupported"),
        UNKNOWN("unknown"),
        ERROR("error")
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
        context.getSharedPreferences(Constants.PREF_RUNTIME_NAME, Context.MODE_PRIVATE)
    }

    fun isPaused(): Boolean = prefs.getBoolean(Constants.PREF_PAUSED, false)

    /** Persist the response-pause request synchronously and verify the stored value. */
    fun setPaused(paused: Boolean): Boolean {
        val committed = prefs.edit().putBoolean(Constants.PREF_PAUSED, paused).commit()
        return committed && isPaused() == paused
    }

    fun isDeviceAdminActive(): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(deviceAdminComponent(context))
    }

    private fun accessibilityState(): LayerState = try {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val component = ComponentName(context, GuardianAccessibilityService::class.java)
        val enabled = manager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        ).any { info ->
            val service = info.resolveInfo?.serviceInfo
            service != null && ComponentName(service.packageName, service.name) == component
        }
        if (enabled) LayerState.ACTIVE_VERIFIED else LayerState.PERMISSION_REQUIRED
    } catch (error: Throwable) {
        logQueryFailure("accessibility", error)
        LayerState.ERROR
    }

    private fun privateDnsMonitorState(): LayerState = try {
        // Reading the mode proves that the query is available, not that DNS is
        // protected. The registration flag only represents this process's observer.
        Settings.Global.getString(
            context.contentResolver,
            Constants.SETTINGS_PRIVATE_DNS_MODE
        )
        if (prefs.getBoolean(Constants.PREF_DNS_MONITOR_REGISTERED, false)) {
            LayerState.ACTIVE_UNVERIFIED
        } else {
            LayerState.INACTIVE
        }
    } catch (error: Throwable) {
        logQueryFailure("private_dns_monitor", error)
        LayerState.ERROR
    }

    private fun passwordState(): LayerState = try {
        if (Crypto.hasPassword(context)) {
            LayerState.ACTIVE_VERIFIED
        } else {
            LayerState.PERMISSION_REQUIRED
        }
    } catch (error: Throwable) {
        logQueryFailure("password_lock", error)
        LayerState.ERROR
    }

    private fun historyState(): LayerState = try {
        val store = EventStore(context)
        val open = store.readableDatabase.isOpen
        val chainIntact = store.verifyChain() == -1
        store.close()
        when {
            !open -> LayerState.INACTIVE
            chainIntact -> LayerState.ACTIVE_VERIFIED
            else -> LayerState.RECOVERY_REQUIRED
        }
    } catch (error: Throwable) {
        logQueryFailure("security_history", error)
        LayerState.ERROR
    }

    private fun deviceAdminState(): LayerState = try {
        if (isDeviceAdminActive()) LayerState.ACTIVE_VERIFIED else LayerState.INACTIVE
    } catch (error: Throwable) {
        logQueryFailure("device_admin", error)
        LayerState.ERROR
    }

    private fun currentBootCount(): Int? = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
    } catch (error: Throwable) {
        logQueryFailure("boot_count", error)
        null
    }

    fun buildSnapshot(): Snapshot {
        val accessibility = accessibilityState()
        val lastCheck = prefs.getLong(Constants.PREF_LAST_RECOVERY_CHECK, 0L)
        val checkedBoot = prefs.getInt(Constants.PREF_LAST_RECOVERY_BOOT_COUNT, -1)
        val bootCount = currentBootCount()
        val recoveryState = when {
            bootCount == null -> LayerState.ERROR
            lastCheck <= 0L -> LayerState.UNKNOWN
            checkedBoot == bootCount -> LayerState.ACTIVE_VERIFIED
            else -> LayerState.RECOVERY_REQUIRED
        }

        val layers = listOf(
            Layer(
                id = "private_dns_monitor",
                titleKey = "privateDnsMonitor",
                state = privateDnsMonitorState(),
                detailKey = "Observer availability only; does not claim DNS protection."
            ),
            Layer(
                id = "accessibility_service",
                titleKey = "accessibilityService",
                state = accessibility
            ),
            Layer(
                id = "app_management_monitor",
                titleKey = "appManagementMonitor",
                state = when (accessibility) {
                    LayerState.ACTIVE_VERIFIED -> LayerState.ACTIVE_UNVERIFIED
                    else -> accessibility
                },
                detailKey = "Depends on Accessibility; response outcome is not automatically verified."
            ),
            Layer(
                id = "password_lock",
                titleKey = "passwordLock",
                state = passwordState(),
                detailKey = "Verifies that an app password is configured; current session authentication is separate."
            ),
            Layer(
                id = "security_history",
                titleKey = "securityHistory",
                state = historyState()
            ),
            Layer(
                id = "recovery_boot",
                titleKey = "recoveryBoot",
                state = recoveryState,
                lastVerifiedAt = if (lastCheck > 0) lastCheck else null,
                detailKey = "Verified only when RecoveryWorker ran during the current Android boot."
            ),
            Layer(
                id = "device_admin",
                titleKey = "deviceAdmin",
                state = deviceAdminState(),
                detailKey = "Optional; ordinary Device Admin is not Device Owner and does not guarantee uninstall prevention."
            )
        )

        return Snapshot(
            layers = layers,
            overall = deriveOverall(layers, isPaused()),
            lastRecoveryCheckAt = if (lastCheck > 0) lastCheck else null,
            protectionPaused = isPaused()
        )
    }

    private fun deriveOverall(layers: List<Layer>, responsePaused: Boolean): LayerState {
        if (responsePaused) return LayerState.INACTIVE
        val required = layers.filter { it.id != "device_admin" }
        val priority = listOf(
            LayerState.ERROR,
            LayerState.UNKNOWN,
            LayerState.RECOVERY_REQUIRED,
            LayerState.PERMISSION_REQUIRED,
            LayerState.INACTIVE,
            LayerState.ACTIVE_UNVERIFIED
        )
        return priority.firstOrNull { state -> required.any { it.state == state } }
            ?: LayerState.ACTIVE_VERIFIED
    }

    fun toJson(snapshot: Snapshot): Map<String, Any?> = mapOf(
        "layers" to snapshot.layers.map { layer ->
            mapOf(
                "id" to layer.id,
                "titleKey" to layer.titleKey,
                "state" to layer.state.wireName,
                "lastVerifiedAt" to layer.lastVerifiedAt,
                "detailKey" to layer.detailKey
            )
        },
        "lastRecoveryCheckAt" to snapshot.lastRecoveryCheckAt,
        "protectionPaused" to snapshot.protectionPaused
    )

    private fun logQueryFailure(query: String, error: Throwable) {
        Log.e(TAG, "query=$query failed type=${error.javaClass.simpleName}", error)
    }

    companion object {
        private const val TAG = "DNSGuardian.State"

        fun accessibilityComponent(context: Context) =
            ComponentName(context, GuardianAccessibilityService::class.java)

        fun deviceAdminComponent(context: Context) =
            ComponentName(context, GuardianDeviceAdminReceiver::class.java)

    }
}
