package com.dnsguardian.app

/**
 * Shared constants for DNS Guardian.
 * No secret values are present in source; secrets (keystore, password hash) live in
 * EncryptedSharedPreferences on the device.
 */
object Constants {
    const val APP_PACKAGE = "com.dnsguardian.app"

    // Platform channel names (must match lib/core/platform_channel.dart)
    const val CHANNEL_STATE = "dns_guardian/security_state"
    const val CHANNEL_AUTH = "dns_guardian/auth"
    const val CHANNEL_ACTION = "dns_guardian/actions"
    const val CHANNEL_EVENTS = "dns_guardian/security_events"

    // Encrypted pref master key alias (AndroidX Security)
    const val MASTER_KEY_ALIAS = "dns_guardian_master"
    const val PREF_NAME = "dns_guardian_secure"
    // Non-secret operational state must not share the encrypted preference file.
    const val PREF_RUNTIME_NAME = "dns_guardian_runtime"
    const val PREF_PASSWORD_HASH = "password_hash"
    const val PREF_PASSWORD_SALT = "password_salt"
    const val PREF_HAS_PASSWORD = "has_password"
    const val PREF_PAUSED = "protection_paused"
    const val PREF_DNS_MONITOR_REGISTERED = "dns_monitor_registered"
    const val PREF_ACCESSIBILITY_CONNECTED = "accessibility_connected"
    const val PREF_BOOT_RECEIVER_INVOKED_AT = "boot_receiver_invoked_at"
    const val PREF_RECOVERY_WORK_SCHEDULED_AT = "recovery_work_scheduled_at"
    const val PREF_LAST_RECOVERY_CHECK = "last_recovery_check"
    const val PREF_LAST_RECOVERY_BOOT_COUNT = "last_recovery_boot_count"

    // Event DB
    const val DB_NAME = "dns_guardian.db"
    const val TABLE_EVENTS = "security_events"

    // Settings global keys (API 28+)
    const val SETTINGS_PRIVATE_DNS_MODE = "private_dns_mode"
    const val SETTINGS_PRIVATE_DNS_SPECIFIER = "private_dns_specifier"

    // Cooldown to avoid redirect loops (ms)
    const val REDIRECT_COOLDOWN_MS = 2000L
    const val MAX_REDIRECTS_PER_WINDOW = 3
    const val REDIRECT_WINDOW_MS = 10000L

    // Accessibility detection targets (AOSP + Realme; additive, narrow).
    // These are class names / text fragments we treat as the Private DNS screen.
    val PRIVATE_DNS_TEXT = setOf(
        "Private DNS",
        "Private DNS provider",
        "Private DNS mode",
        "نظام أسماء النطاقات الخاص",
        "DNS الخاص",
        "الـ DNS الخاص"
    )

    // Known AOSP class fragments for Private DNS. Realme may differ; discovered
    // via manual crawl on the target device and appended here.
    val PRIVATE_DNS_CLASSES = setOf(
        "com.android.settings.network.PrivateDnsModeDialogFragment",
        "com.android.settings.network.PrivateDnsSettings",
        "com.android.settings.Settings\$PrivateDnsSettingsActivity"
    )

    // App-management screens we monitor (narrow to our own package where possible).
    val APP_INFO_CLASSES = setOf(
        "com.android.settings.applications.InstalledAppDetails",
        "com.android.settings.Settings\$AppOpsDetailsActivity"
    )
    val UNINSTALL_CLASSES = setOf(
        "com.android.packageinstaller.UninstallerActivity",
        "com.google.android.packageinstaller.UninstallerActivity",
        "com.android.settings.applications.UninstallAppProgress"
    )
    val ACCESSIBILITY_SETTINGS_CLASSES = setOf(
        "com.android.settings.Settings\$AccessibilitySettingsActivity",
        "com.android.settings.accessibility.AccessibilitySettings"
    )
    val DEVICE_ADMIN_SETTINGS_CLASSES = setOf(
        "com.android.settings.Settings\$DeviceAdminSettingsActivity",
        "com.android.settings.deviceadmin.DeviceAdminSettings"
    )
}
