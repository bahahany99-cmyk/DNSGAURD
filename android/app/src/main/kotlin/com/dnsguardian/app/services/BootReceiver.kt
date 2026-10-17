package com.dnsguardian.app.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
                val work = OneTimeWorkRequestBuilder<RecoveryWorker>().build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    "recovery_after_boot",
                    ExistingWorkPolicy.REPLACE,
                    work
                )
            }
        }
    }
}
