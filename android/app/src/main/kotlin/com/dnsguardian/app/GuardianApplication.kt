package com.dnsguardian.app

import android.app.Application
import android.util.Log
import com.dnsguardian.app.security.DnsMonitor
import com.dnsguardian.app.security.SecurityStateManager

/**
 * Application class. Initializes the DNS ContentObserver monitor on process start
 * (best-effort detection while alive). Does not request any permission here.
 */
class GuardianApplication : Application() {

    private lateinit var dnsMonitor: DnsMonitor

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "stage=application_onCreate")
        try {
            dnsMonitor = DnsMonitor(this)
            Log.i(TAG, "stage=dns_monitor_created")
            // Only register while the process is alive; if paused, skip.
            val mgr = SecurityStateManager(this)
            if (!mgr.isPaused()) {
                dnsMonitor.register()
                Log.i(TAG, "stage=dns_monitor_registered")
            } else {
                Log.i(TAG, "stage=dns_monitor_paused")
            }
        } catch (error: Throwable) {
            // Monitoring is best-effort and must never prevent Flutter startup.
            // No user data or DNS provider hostname is included in this log.
            Log.e(TAG, "stage=application_initialization_failed", error)
        }
    }

    override fun onTerminate() {
        if (::dnsMonitor.isInitialized) {
            try {
                dnsMonitor.unregister()
            } catch (error: Throwable) {
                Log.e(TAG, "stage=dns_monitor_unregister_failed", error)
            }
        }
        super.onTerminate()
    }

    private companion object {
        const val TAG = "DNSGuardian.Startup"
    }
}
