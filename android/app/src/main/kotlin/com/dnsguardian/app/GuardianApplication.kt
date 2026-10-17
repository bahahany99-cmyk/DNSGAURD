package com.dnsguardian.app

import android.app.Application
import android.util.Log
import com.dnsguardian.app.security.DnsMonitor

/**
 * Application class. Initializes the DNS ContentObserver monitor on process start
 * (best-effort detection while alive). Does not request any permission here.
 */
class GuardianApplication : Application() {

    private lateinit var dnsMonitor: DnsMonitor

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "stage=application_onCreate")
        // Process-local connection evidence must never survive as a stale true
        // value after Android kills and later recreates the process.
        getSharedPreferences(Constants.PREF_RUNTIME_NAME, MODE_PRIVATE)
            .edit().putBoolean(Constants.PREF_ACCESSIBILITY_CONNECTED, false).commit()
        try {
            dnsMonitor = DnsMonitor(this)
            Log.i(TAG, "stage=dns_monitor_created")
            // Monitoring remains active during a maintenance-response pause;
            // only automated Accessibility redirection is suspended.
            dnsMonitor.register()
            getSharedPreferences(Constants.PREF_RUNTIME_NAME, MODE_PRIVATE)
                .edit().putBoolean(Constants.PREF_DNS_MONITOR_REGISTERED, true).apply()
            Log.i(TAG, "stage=dns_monitor_registered")
        } catch (error: Throwable) {
            getSharedPreferences(Constants.PREF_RUNTIME_NAME, MODE_PRIVATE)
                .edit().putBoolean(Constants.PREF_DNS_MONITOR_REGISTERED, false).apply()
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
