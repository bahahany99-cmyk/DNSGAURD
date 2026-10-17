package com.dnsguardian.app.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.dnsguardian.app.Constants

/**
 * Runs recovery checks after boot (or app update).
 * Schedules a one-time RecoveryWorker. On Realme/ColorOS, background start may be
 * delayed; the worker re-checks permissions and surfaces gaps honestly.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val now = System.currentTimeMillis()
                val prefs = context.getSharedPreferences(
                    Constants.PREF_RUNTIME_NAME,
                    Context.MODE_PRIVATE
                )
                prefs.edit().putLong(Constants.PREF_BOOT_RECEIVER_INVOKED_AT, now).commit()
                Log.i(TAG, "query=recovery_boot stage=receiver_invoked action=${intent.action}")
                try {
                    val work = OneTimeWorkRequestBuilder<RecoveryWorker>().build()
                    WorkManager.getInstance(context).enqueueUniqueWork(
                        "recovery_after_boot",
                        ExistingWorkPolicy.REPLACE,
                        work
                    )
                    prefs.edit()
                        .putLong(Constants.PREF_RECOVERY_WORK_SCHEDULED_AT, now)
                        .commit()
                    Log.i(TAG, "query=recovery_boot stage=worker_scheduled")
                } catch (error: Throwable) {
                    Log.e(
                        TAG,
                        "query=recovery_boot stage=worker_schedule_failed " +
                            "type=${error.javaClass.simpleName} description=scheduling_failed"
                    )
                }
            }
        }
    }

    companion object {
        private const val TAG = "DNSGuardian.State"
    }
}
