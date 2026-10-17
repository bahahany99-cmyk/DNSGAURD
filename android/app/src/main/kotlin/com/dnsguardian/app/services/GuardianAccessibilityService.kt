package com.dnsguardian.app.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.dnsguardian.app.Constants
import com.dnsguardian.app.MainActivity
import com.dnsguardian.app.security.EventStore
import java.util.ArrayDeque
import java.util.Locale

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
        getSharedPreferences(Constants.PREF_RUNTIME_NAME, MODE_PRIVATE)
            .edit().putBoolean(Constants.PREF_ACCESSIBILITY_CONNECTED, true).commit()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            packageNames = arrayOf("com.android.settings")
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.DEFAULT or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 50
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

        // Class checks catch AOSP routes; hierarchy traversal catches OEM screens
        // (Realme UI/ColorOS/MIUI/OneUI) that keep generic Activity class names.
        val isPrivateDnsClass = Constants.PRIVATE_DNS_CLASSES.any { className.endsWith(it) }
        val isPrivateDnsText = containsPrivateDnsTrigger(text) ||
            activeWindowContainsPrivateDnsTrigger()

        if (isPrivateDnsClass || isPrivateDnsText) {
            handlePrivateDnsIntervention(source = "accessibility:$className")
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

    private fun containsPrivateDnsTrigger(raw: CharSequence?): Boolean {
        val value = raw?.toString()?.trim()?.lowercase(Locale.ROOT) ?: return false
        if (value.isEmpty()) return false

        // Do not mistake this app's own Accessibility entry for a DNS setting.
        if (value.contains("dns guardian") || value.contains("حارس dns")) return false

        if (value.contains("private dns") ||
            value.contains("dns خاص") ||
            value.contains("نظام أسماء النطاقات الخاص")) {
            return true
        }
        if (Constants.PRIVATE_DNS_TEXT.any { trigger ->
                value.contains(trigger.lowercase(Locale.ROOT))
            }) {
            return true
        }

        // OEM/localized Settings screens may expose only a generic DNS label.
        // This remains scoped to com.android.settings by onAccessibilityEvent().
        return DNS_TOKEN.containsMatchIn(value)
    }

    @Suppress("DEPRECATION")
    private fun activeWindowContainsPrivateDnsTrigger(): Boolean {
        val root = rootInActiveWindow ?: return false
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        var visited = 0
        try {
            while (pending.isNotEmpty() && visited < MAX_WINDOW_NODES) {
                val node = pending.removeLast()
                try {
                    visited++
                    if (containsPrivateDnsTrigger(node.text) ||
                        containsPrivateDnsTrigger(node.contentDescription)) {
                        return true
                    }
                    for (index in 0 until node.childCount) {
                        node.getChild(index)?.let(pending::addLast)
                    }
                } finally {
                    node.recycle()
                }
            }
        } catch (_: Throwable) {
            // A window can change while being traversed. The next accessibility
            // event retries; no node text or description is logged.
            return false
        } finally {
            while (pending.isNotEmpty()) {
                pending.removeLast().recycle()
            }
        }
        return false
    }

    private fun handlePrivateDnsIntervention(source: String) {
        val now = System.currentTimeMillis()
        if (!reserveRedirect(now, "PRIVATE_DNS_ACCESS_ATTEMPT")) return

        val backRequested = performGlobalAction(GLOBAL_ACTION_BACK)
        var lockShown = false
        try {
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            })
            lockShown = true
        } catch (_: Exception) {
            // The attempted response is recorded below without exposing details.
        }

        EventStore(this).addEvent(
            type = "PRIVATE_DNS_ACCESS_ATTEMPT",
            tsMillis = now,
            elapsedRealtimeMs = android.os.SystemClock.elapsedRealtime(),
            source = source,
            component = "private_dns_monitor",
            responseAttempted = "BACK_AND_SHOW_APP_LOCK",
            responseVerified = false,
            diagnostic = "Back requested=$backRequested; app lock launch requested=$lockShown; outcome unverified."
        )
        ProtectionEventBroadcaster.notifyStateChanged(this)
    }

    private fun reserveRedirect(now: Long, eventType: String): Boolean {
        synchronized(redirectTimestamps) {
            redirectTimestamps.removeAll { now - it > Constants.REDIRECT_WINDOW_MS }
            if (now - lastRedirectAt < Constants.REDIRECT_COOLDOWN_MS) return false
            if (redirectTimestamps.size >= Constants.MAX_REDIRECTS_PER_WINDOW) {
                EventStore(this).addEvent(
                    type = eventType,
                    tsMillis = now,
                    elapsedRealtimeMs = android.os.SystemClock.elapsedRealtime(),
                    source = "accessibility",
                    component = "app_management_monitor",
                    responseAttempted = "REDIRECT_THROTTLED",
                    responseVerified = false,
                    diagnostic = "Too many redirects; throttled to avoid loops."
                )
                return false
            }
            lastRedirectAt = now
            redirectTimestamps.add(now)
            return true
        }
    }

    private fun handleRedirect(eventType: String, source: String) {
        val now = System.currentTimeMillis()
        if (!reserveRedirect(now, eventType)) return

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
        // Required override; an interrupt is not proof of disconnection.
    }

    override fun onDestroy() {
        getSharedPreferences(Constants.PREF_RUNTIME_NAME, MODE_PRIVATE)
            .edit().putBoolean(Constants.PREF_ACCESSIBILITY_CONNECTED, false).commit()
        super.onDestroy()
    }

    private companion object {
        const val MAX_WINDOW_NODES = 2500
        val DNS_TOKEN = Regex("(^|[^a-z0-9])dns([^a-z0-9]|$)")
    }
}
