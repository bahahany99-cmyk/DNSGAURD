package com.dnsguardian.app

import android.app.Application
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
        dnsMonitor = DnsMonitor(this)
        // Only register while the process is alive; if paused, skip.
        val mgr = SecurityStateManager(this)
        if (!mgr.isPaused()) {
            dnsMonitor.register()
        }
    }

    override fun onTerminate() {
        if (::dnsMonitor.isInitialized) dnsMonitor.unregister()
        super.onTerminate()
    }
}
