package com.dnsguardian.app.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.dnsguardian.app.Constants
import com.dnsguardian.app.security.EventStore
import com.dnsguardian.app.security.SecurityStateManager

/**
 * Narrowly-scoped accessibility monitor.
 *
 * Responsibilities (best-effort detection + immediate response):
 *  - Detect navigation to the Private DNS settings screen and attempt an immediate
 *    return to the main Android Settings page.
 *  - Detect attempts to open App Info / Uninstall / Accessibility settings / Device
 *    Admin deactivation that reference THIS app, and attempt to bounce back.
 *
 * Honest reporting: every response is logged as "attempted", and we NEVER mark a
 * redirect as "verified" because the system may still show the screen (timing, OEM
 * overlays, battery killers). Attempted != verified.
 */
class GuardianAccessibilityService : AccessibilityService() {

    private var lastRedirectAt = 0L
    private val redirectTimestamps = mutableListOf<Long>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            packageNames = arrayOf("com.android.settings")
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 100
        }
        serviceInfo = info

        EventStore(this).addEvent(
            type = "STARTUP_RECOVERY_CHECK",
            tsMillis = System.currentTimeMillis(),
            elapsedRealtimeMs = android.os.SystemClock.elapsedRealtime(),
            source = "accessibility_service",
            component = "accessibility_service",
            responseAttempted = "SERVICE_CONNECTED",
            responseVerified = true,
            diagnostic = "Accessibility service connected."
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        // Only inspect system Settings; never other apps (privacy).
        if (pkg != "com.android.settings") return

        val className = event.className?.toString() ?: ""
        val text = event.text?.joinToString(" ")?.lowercase() ?: ""

        // Paused (authenticated maintenance)? Skip redirection but still observe.
        val paused = getSharedPreferences(Constants.PREF_RUNTIME_NAME, MODE_PRIVATE)
            .getBoolean(Constants.PREF_PAUSED, false)
        if (paused) return

        // --- Private DNS detection (narrow) ---
        val isPrivateDnsClass = Constants.PRIVATE_DNS_CLASSES.any { className.endsWith(it) }
        val isPrivateDnsText = Constants.PRIVATE_DNS_TEXT.any { t ->
            text.contains(t.lowercase())
        } && (className.contains("PrivateDns", ignoreCase = true) ||
            text.contains("private dns") || text.contains("dns الخاص"))

        if (isPrivateDnsClass || isPrivateDnsText) {
            handleRedirect(
                eventType = "PRIVATE_DNS_ACCESS_ATTEMPT",
                source = "accessibility:$className"
            )
            return
        }

        // --- App-management screens that reference THIS app ---
        val refersToThisApp = text.contains("dns guardian") ||
            text.contains(Constants.APP_PACKAGE)

        if (Constants.APP_INFO_CLASSES.any { className.endsWith(it) } && refersToThisApp) {
            handleRedirect(eventType = "APP_INFO_ATTEMPT", source = "accessibility:$className")
            return
        }
        if (Constants.UNINSTALL_CLASSES.any { className.endsWith(it) }) {
            handleRedirect(eventType = "UNINSTALL_ATTEMPT", source = "accessibility:$className")
            return
        }
        if (Constants.ACCESSIBILITY_SETTINGS_CLASSES.any { className.endsWith(it) }) {
            handleRedirect(
                eventType = "ACCESSIBILITY_SETTINGS_ACCESS",
                source = "accessibility:$className"
            )
            return
        }
        if (Constants.DEVICE_ADMIN_SETTINGS_CLASSES.any { className.endsWith(it) }) {
            handleRedirect(
                eventType = "DEVICE_ADMIN_DEACTIVATE_ATTEMPT",
                source = "accessibility:$className"
            )
            return
        }
    }

    private fun handleRedirect(eventType: String, source: String) {
        val now = System.currentTimeMillis()
        // Cooldown + anti-loop.
        synchronized(redirectTimestamps) {
            redirectTimestamps.removeAll { now - it > Constants.REDIRECT_WINDOW_MS }
            if (now - lastRedirectAt < Constants.REDIRECT_COOLDOWN_MS) {
                return
            }
            if (redirectTimestamps.size >= Constants.MAX_REDIRECTS_PER_WINDOW) {
                EventStore(this).addEvent(
                    type = eventType,
                    tsMillis = now,
                    elapsedRealtimeMs = android.os.SystemClock.elapsedRealtime(),
                    source = source,
                    component = "app_management_monitor",
                    responseAttempted = "REDIRECT_THROTTLED",
                    responseVerified = false,
                    diagnostic = "Too many redirects; throttled to avoid loops."
                )
                return
            }
            lastRedirectAt = now
            redirectTimestamps.add(now)
        }

        // Attempt immediate return to main Settings (best-effort).
        performGlobalAction(GLOBAL_ACTION_BACK)
        val intent = Intent(Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            // ignore — log attempted
        }

        EventStore(this).addEvent(
            type = eventType,
            tsMillis = now,
            elapsedRealtimeMs = android.os.SystemClock.elapsedRealtime(),
            source = source,
            component = "app_management_monitor",
            responseAttempted = "REDIRECT_TO_SETTINGS",
            responseVerified = false, // we cannot confirm the screen closed
            diagnostic = "Back + open ACTION_SETTINGS attempted. Outcome unverified."
        )
        ProtectionEventBroadcaster.notifyStateChanged(this)
    }

    override fun onInterrupt() {
        // Required override; no-op.
    }
}
