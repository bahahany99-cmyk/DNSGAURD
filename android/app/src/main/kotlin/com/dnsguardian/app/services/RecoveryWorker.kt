package com.dnsguardian.app.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dnsguardian.app.Constants
import com.dnsguardian.app.security.EventStore
import com.dnsguardian.app.security.SecurityStateManager

/**
 * Re-verify protection state after reboot / update.
 * Records a STARTUP_RECOVERY_CHECK event, checks required permissions, detects
 * disabled components, and stores last_recovery_check so the dashboard can show
 * honest "unknown/recovered" status. Cannot run while the app process is dead,
 * so gaps during downtime are reported as suspected (not confirmed attacks).
 */
class RecoveryWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = EventStore(context)
        val mgr = SecurityStateManager(context)

        val prefs = context.getSharedPreferences(Constants.PREF_RUNTIME_NAME, Context.MODE_PRIVATE)
        val bootCount = try {
            android.provider.Settings.Global.getInt(
                context.contentResolver,
                android.provider.Settings.Global.BOOT_COUNT
            )
        } catch (_: Exception) { -1 }
        val evidenceStored = bootCount >= 0 && prefs.edit()
            .putLong(Constants.PREF_LAST_RECOVERY_CHECK, System.currentTimeMillis())
            .putInt(Constants.PREF_LAST_RECOVERY_BOOT_COUNT, bootCount)
            .commit()
        val snapshot = mgr.buildSnapshot()

        store.addEvent(
            type = "STARTUP_RECOVERY_CHECK",
            tsMillis = System.currentTimeMillis(),
            elapsedRealtimeMs = android.os.SystemClock.elapsedRealtime(),
            source = "recovery_worker",
            component = "recovery_boot",
            responseAttempted = "STATE_REEVALUATED",
            responseVerified = evidenceStored,
            diagnostic = "overall=${snapshot.overall.name}; layers=${snapshot.layers.size}; currentBootEvidence=$evidenceStored"
        )

        // Detect notifications permission (API 33+).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                store.addEvent(
                    type = "COMPONENT_DISABLED",
                    tsMillis = System.currentTimeMillis(),
                    elapsedRealtimeMs = android.os.SystemClock.elapsedRealtime(),
                    source = "recovery_worker",
                    component = "notifications",
                    responseAttempted = "PERMISSION_MISSING",
                    responseVerified = true,
                    diagnostic = "POST_NOTIFICATIONS not granted; gap alerts suppressed."
                )
            }
        }

        return Result.success()
    }
}
