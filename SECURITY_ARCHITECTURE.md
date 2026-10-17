# Security Architecture — DNS Guardian

**Status:** Phase 1 — Approved for implementation  
**Target:** Android 15 (API 35), Realme RMX3636, no Device Owner  
**Principles:** Offline-first, least privilege, honest reporting, defence in depth within Android sandbox

---

## 1. High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│ Flutter UI (Arabic-first)                                       │
│  ├─ Dashboard (ProtectionLayer tiles)                            │
│  ├─ LockScreen / PasswordEnrollment                              │
│  ├─ SecurityHistory (filtered, auth-gated)                       │
│  ├─ ProtectedSettings (auth-gated)                               │
│  └─ Onboarding / Permission Grants (Arabic)                      │
│         │  Platform Channels (MethodChannel + EventChannel)       │
├─────────┼────────────────────────────────────────────────────────┤
│ Native Android (Kotlin)                                          │
│  ├─ SecurityStateManager (single source of truth)                │
│  ├─ DnsMonitor (ContentObserver on Settings.Global)              │
│  ├─ GuardianAccessibilityService (narrow detection → redirect)    │
│  ├─ DeviceAdmin (DeviceAdminReceiver, optional)                  │
│  ├─ BootReceiver + WorkManager (recovery checks)                 │
│  ├─ ForegroundMonitorService (only if justified, else WorkManager)|
│  └─ Crypto / SecureStorage / EventLog                            │
├─────────────────────────────────────────────────────────────────┤
│ Local Persistence                                                │
│  ├─ EncryptedSharedPreferences (salt, hash, flags)               │
│  ├─ SQLite (Room) — SecurityEvent + hash-chain                   │
│  └─ DataStore / Preferences (non-sensitive prefs)                │
└─────────────────────────────────────────────────────────────────┘
```

**Why Flutter + native?**
- Flutter gives fast Arabic-first UI, dark/light, RTL, accessibility.
- DNS monitoring, Accessibility, Device Admin, boot handling **cannot** be done in Dart alone — they require Kotlin platform services.
- All security-sensitive logic lives in Kotlin or in Dart with explicit channel calls; UI is never authoritative.

---

## 2. Permission Model (Minimal, Justified)

| Permission / Capability | When requested | Justification shown to user (Arabic) |
|-------------------------|---------------|--------------------------------------|
| `android.permission.BIND_ACCESSIBILITY_SERVICE` (GuardianAccessibilityService) | Onboarding, after user taps "Enable protection" | "لمراقبة محاولة فتح إعدادات الـ Private DNS وإعادتك إلى الإعدادات الرئيسية. لا نقرأ محتوى التطبيقات الأخرى." |
| `android.permission.BIND_DEVICE_ADMIN` (optional) | Dashboard → "Activate extra layer" | "لإظهار تحذير عند محاولة إلغاء تفعيل الحماية. لا يمنع إلغاء التثبيت." |
| `android.permission.FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` or `SYSTEM_EXEMPTED` | Only if audit proves WorkManager insufficient on Realme; otherwise not requested | "للحفاظ على المراقبة بعد إعادة التشغيل في أجهزة Realme التي تقيد العمل في الخلفية." |
| `android.permission.RECEIVE_BOOT_COMPLETED` | At install | "للتحقق من حالة الحماية بعد إعادة تشغيل الهاتف." |
| `android.permission.POST_NOTIFICATIONS` (API33+) | Onboarding | "لإظهار تنبيه عند توقف طبقة حماية مهمة." |
| **Not requested** | — | `ACCESS_FINE_LOCATION`, `READ_CONTACTS`, `CAMERA`, `RECORD_AUDIO`, `READ_MEDIA_*`, `INTERNET` (optional, but app works offline; if added, only for optional update check — currently **no** internet permission) |

All permissions are **opt-in** and reversible; dashboard reflects missing permissions as `Permission required`.

---

## 3. Component Design

### 3.1 SecurityStateManager (Kotlin singleton + Dart mirror)

```kotlin
enum class LayerState {
  ACTIVE_VERIFIED,      // checked now, all good
  ACTIVE_UNVERIFIED,    // enabled but not yet verified this session
  INACTIVE,             // disabled / permission revoked
  PERMISSION_REQUIRED,  // user must grant
  RECOVERY_REQUIRED,    // manual action needed
  UNSUPPORTED,          // device cannot support (e.g. Device Admin removed)
  UNKNOWN               // cannot determine (e.g. process was dead)
}

data class ProtectionLayer(
  val id: String,          // e.g. "private_dns_monitor"
  val state: LayerState,
  val lastVerifiedAt: Long?,
  val detail: String?      // Arabic user-facing detail
)

data class SecurityState(
  val layers: List<ProtectionLayer>,
  val overall: LayerState, // derived = worst of required layers
  val lastRecoveryCheckAt: Long?
)
```

- Single source of truth in Kotlin (`SecurityStateManager`), exposed to Flutter via `MethodChannel("dns_guardian/security_state")`.
- Flutter holds a cached copy and listens to `EventChannel` for pushes (e.g. after Accessibility event).
- No UI state can override manager's verdict.

### 3.2 DnsMonitor

- `ContentObserver` on `content://settings/global` filtered to `private_dns_mode` / `private_dns_specifier`.
- On change: read new values, compare to `lastKnownGood` (stored after user confirms), log `DNS_CONFIG_CHANGED`, update `SecurityState`, show notification if configured.
- **Cannot restore** — offers `Intent(Settings.ACTION_WIRELESS_SETTINGS)` deep-link after auth; never calls `putString` without `WRITE_SECURE_SETTINGS`.

### 3.3 GuardianAccessibilityService

```xml
<accessibility-service
  android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged"
  android:packageNames="com.android.settings"
  android:accessibilityFeedbackType="feedbackGeneric"
  android:notificationTimeout="100"
  android:canRetrieveWindowContent="true"
  android:description="@string/accessibility_desc"/>
```

- Filter (`GuardianFilter.kt`):
  ```kotlin
  private val PRIVATE_DNS_TEXT = listOf("Private DNS", "Private DNS provider", "نظام أسماء النطاقات الخاص", "DNS الخاص")
  private val PRIVATE_DNS_CLASSES = listOf(
    "com.android.settings.network.PrivateDnsModeDialogFragment",
    "com.android.settings.network.PrivateDnsSettings"
  )
  // Realme variants discovered via manual crawl — additive, narrow
  ```

- On match: check cooldown (`lastRedirectAt`), then `performGlobalAction(GLOBAL_ACTION_BACK)` + `startActivity(ACTION_SETTINGS, NEW_TASK|CLEAR_TOP)`, log `PRIVATE_DNS_ACCESS_ATTEMPT` with `responseAttempted=REDIRECT_TO_SETTINGS`, `responseVerified` = whether next window is Settings (verified via next event within 500ms).

- App-info / uninstall detection: similar filter on `InstalledAppDetails`, `UninstallAppProgress`, `AccessibilitySettingsActivity`, `DeviceAdminSettings` **only when** `event.text` contains own package/app name — never broad.

- Anti-loop: per-package cooldown 2s, max 3 redirects per 10s, then pause and log `REDIRECT_THROTTLED`.

### 3.4 DeviceAdmin (Optional Layer)

- `DeviceAdminReceiver` subclass with `onDisableRequested` returning warning string (Arabic).
- `isAdminActive()` polled; dashboard shows `INACTIVE` if not; `DevicePolicyManager` not used to enforce restrictions (impossible on API 35).

### 3.5 Boot & Recovery

- `BootReceiver : BroadcastReceiver` for `BOOT_COMPLETED` → enqueues `RecoveryWorker` (WorkManager `OneTimeWorkRequest`).
- `RecoveryWorker` re-checks all layers, re-registers `ContentObserver`, logs `RECOVERY_CHECK`, posts notification if gaps.
- `ForegroundMonitorService` only enabled if Realme testing shows WorkManager delayed >15min; otherwise not started to respect battery.

### 3.6 Crypto & Storage

- **Password hashing:** `Argon2id` (preferred, via `org.mindrot:jbcrypt` alternative `com.lambdaworks:scrypt` fallback) or `PBKDF2WithHmacSHA256` with `iterations=120_000`, `salt=16 random bytes` from `SecureRandom`, stored via `EncryptedSharedPreferences` (AndroidX Security, `AES256_GCM`). If `dart:ffi` Argon2 unavailable, use platform channel to Kotlin hashing.
- **Event log:** `Room` DB `security_events`:
  ```kotlin
  @Entity
  data class SecurityEvent(
    @PrimaryKey val id: String, // UUID
    val type: String,            // enum
    val tsMillis: Long,
    val elapsedRealtimeMs: Long,
    val source: String,          // e.g. "accessibility:PrivateDnsSettings"
    val component: String?,       // e.g. "private_dns_monitor"
    val responseAttempted: String?,
    val responseVerified: Boolean,
    val prevHash: String?,       // hex SHA-256
    val hash: String,            // SHA-256(canonicalJsonWithoutHash)
    val diagnostic: String?       // no secrets
  )
  ```
- Verification: on startup, iterate ordered by `tsMillis`, recompute chain; on mismatch log `INTEGRITY_BREAK` and mark history `TAMPER_SUSPECTED`; never auto-delete.
- Sensitive UI state cleared on `onPause` / `didChangeAppLifecycleState`.

### 3.7 Flutter Layers

```
lib/
  main.dart
  app.dart (MaterialApp, RTL, Theme)
  l10n/ (Arabic ARB, English)
  core/
    security_state.dart (Dart mirror of Kotlin model)
    platform_channel.dart
    crypto.dart (hash verification helpers)
  features/
    dashboard/ (DashboardScreen, LayerTile)
    lock/ (LockScreen, PasswordEnrollmentScreen)
    history/ (HistoryScreen, filters)
    settings/ (ProtectedSettingsScreen)
    onboarding/ (PermissionScreen, Arabic guides)
  data/
    event_repository.dart (DAO wrapper)
```

- Navigation: `go_router`, auth-guarded routes.
- Lock: `AppLifecycleListener` → `SecurityStateManager.lock()` on `inactive|paused|detached`; `WillPopScope` prevents back-escape from lock screen.

---

## 4. Threat Model (Honest)

| Threat | What app does | What it cannot do |
|--------|--------------|-------------------|
| Open Private DNS via Settings UI | Detect (narrow) + redirect + log | Cannot block every OEM path; search/shortcut may bypass |
| Change DNS via adb / secure-settings writer | Detect via ContentObserver + log | Cannot prevent; cannot silently restore |
| Disable Accessibility | Detect on next resume/worker + log + prompt | Cannot silently re-enable |
| Uninstall app | Detect attempt (best-effort) + log | Cannot prevent (non-DO) |
| Clear app data via system | — | History deleted; app discloses this limitation |
| Force-stop / Realme kill | Detect gap heuristically + log `MONITORING_GAP_SUSPECTED` | Cannot run while dead |
| Bypass by leaving protection inactive | Dashboard never green when inactive; boot check surfaces gap | Cannot prove continuous protection |

---

## 5. Logging & Privacy

- No network, no analytics, no location, no contacts.
- Logs contain only: event type, timestamp, component, response; never password, DNS hostname in plaintext if user opts (specifier stored hashed or not at all — decision: store mode only, not hostname, to avoid leaking provider choice).
- `android:allowBackup="false"`, `android:fullBackupContent="false"` to avoid cloud backup of history.

---

## 6. Build & Supply Chain

- `applicationId = com.dnsguardian.app` (placeholder, confirmed in build)
- `minSdk 28` (Android 9 — Private DNS introduced), `targetSdk 35`, `compileSdk 35`
- Kotlin 1.9.x, Gradle 8.7, Android Gradle Plugin 8.5.x (exact versions pinned in `gradle/libs.versions.toml`)
- Dependencies justified: `androidx.security:security-crypto`, `androidx.room`, `androidx.work`, `androidx.lifecycle`, `flutter_secure_storage` (or native), `argon2` (if available); no Firebase, no analytics.
- Release signing via `upload-keystore.jks` (not committed); CI uses `debug` for sandbox, release via local `signing.properties`.

---

## 7. Verification Hooks

- `SecurityStateManager` exposes `getStateSnapshot()` for tests.
- `FakeAccessibilityEvent` for unit tests.
- `InMemoryEventRepository` for widget tests.
- `adb shell settings get global private_dns_mode` for manual verification.
- `adb shell dumpsys accessibility` / `dumpsys device_policy` for layer checks.
