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

    private fun accessibilityState(): LayerState = queryLayer("accessibility") {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val component = accessibilityComponent(context)
        val enabled = manager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        ).any { info ->
            val service = info.resolveInfo?.serviceInfo
            service != null && ComponentName(service.packageName, service.name) == component
        }
        val connected = prefs.getBoolean(Constants.PREF_ACCESSIBILITY_CONNECTED, false)
        when {
            !enabled -> LayerState.PERMISSION_REQUIRED
            connected -> LayerState.ACTIVE_VERIFIED
            else -> LayerState.ACTIVE_UNVERIFIED
        }
    }

    private fun privateDnsMonitorState(): LayerState = queryLayer("private_dns_monitor") {
        // A successful read proves only that Android exposes the mode query.
        // Never log or return the mode/specifier value.
        Settings.Global.getString(
            context.contentResolver,
            Constants.SETTINGS_PRIVATE_DNS_MODE
        )
        if (prefs.getBoolean(Constants.PREF_DNS_MONITOR_REGISTERED, false)) {
            LayerState.ACTIVE_UNVERIFIED
        } else {
            LayerState.INACTIVE
        }
    }

    private fun passwordState(): LayerState = queryLayer("password_lock") {
        if (Crypto.hasPassword(context)) {
            LayerState.ACTIVE_VERIFIED
        } else {
            LayerState.PERMISSION_REQUIRED
        }
    }

    private fun historyState(): LayerState = queryLayer("security_history") {
        val store = EventStore(context)
        try {
            val open = store.readableDatabase.isOpen
            val chainIntact = store.verifyChain() == -1
            when {
                !open -> LayerState.INACTIVE
                chainIntact -> LayerState.ACTIVE_VERIFIED
                else -> LayerState.RECOVERY_REQUIRED
            }
        } finally {
            store.close()
        }
    }

    private fun deviceAdminState(): LayerState = queryLayer("device_admin") {
        if (isDeviceAdminActive()) LayerState.ACTIVE_VERIFIED else LayerState.INACTIVE
    }

    private fun currentBootCount(): Int? {
        Log.d(TAG, "query=recovery_boot stage=android_api_started")
        return try {
            val value = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
            Log.d(TAG, "query=recovery_boot stage=android_api_succeeded")
            value
        } catch (error: Throwable) {
            logQueryFailure("recovery_boot", error)
            null
        }
    }

    fun buildSnapshot(): Snapshot {
        val accessibility = accessibilityState()
        var lastCheck = 0L
        val recoveryState = try {
            Log.d(TAG, "query=recovery_boot stage=evidence_read_started")
            lastCheck = prefs.getLong(Constants.PREF_LAST_RECOVERY_CHECK, 0L)
            val checkedBoot = prefs.getInt(Constants.PREF_LAST_RECOVERY_BOOT_COUNT, -1)
            val bootCount = currentBootCount()
            when {
                bootCount == null -> LayerState.ERROR
                lastCheck <= 0L -> LayerState.UNKNOWN
                checkedBoot == bootCount -> LayerState.ACTIVE_VERIFIED
                else -> LayerState.RECOVERY_REQUIRED
            }.also { state ->
                Log.d(
                    TAG,
                    "query=recovery_boot stage=evidence_read_succeeded state=${state.wireName}"
                )
            }
        } catch (error: Throwable) {
            logQueryFailure("recovery_boot", error)
            LayerState.ERROR
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

    private inline fun queryLayer(query: String, block: () -> LayerState): LayerState {
        Log.d(TAG, "query=$query stage=android_api_started")
        return try {
            val state = block()
            Log.d(TAG, "query=$query stage=android_api_succeeded state=${state.wireName}")
            state
        } catch (error: Throwable) {
            logQueryFailure(query, error)
            LayerState.ERROR
        }
    }

    private fun logQueryFailure(query: String, error: Throwable) {
        // Query identifiers and exception classes are safe; queried values are
        // deliberately excluded (especially DNS and credential material).
        Log.e(
            TAG,
            "query=$query stage=android_api_failed " +
                "type=${error.javaClass.simpleName} description=local_query_failed"
        )
    }

    companion object {
        private const val TAG = "DNSGuardian.State"

        fun accessibilityComponent(context: Context) =
            ComponentName(context, GuardianAccessibilityService::class.java)

        fun deviceAdminComponent(context: Context) =
            ComponentName(context, GuardianDeviceAdminReceiver::class.java)

    }
}
