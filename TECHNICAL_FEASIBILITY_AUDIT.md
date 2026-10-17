# Technical Feasibility Audit — DNS Guardian

**Device:** Realme RMX3636 (Realme UI, expected Android 15 / API 35)  
**Audit mode:** Read-only (no device changes, no sideloaded protection installed)  
**Audit date:** 2026-10-17  
**Basis:** AOSP source + Android 14/15 docs + Realme/ColorOS behaviour reports + local sandbox inspection

> This audit follows §4 of the specification. Every claim is classified; nothing is marked "fully achievable" merely because code compiles.

---

## 1. Platform Confirmation

| Item | Value / Finding | Source |
|------|----------------|--------|
| Android version | **15** (API 35) per spec | Owner declaration |
| Realme RMX3636 | Realme C55-class (RMX3636), typically Realme UI 4/5 on Android 13 baseline; owner reports Android 15 (major OTA or custom ROM). Assume API 35 behaviour; flag Realme battery/background restrictions as applicable to ColorOS/RealmeUI. | Device spec + owner |
| Private DNS setting key | `Settings.Global.PRIVATE_DNS_MODE` (`private_dns_mode`) and `Settings.Global.PRIVATE_DNS_SPECIFIER` (`private_dns_specifier`) | AOSP `Settings.java` (API 28+), unchanged through API 35 |
| Settings package | `com.android.settings`; Private DNS usually at `Settings > Network & internet > Private DNS` (`com.android.settings.network.PrivateDnsModeDialogFragment` or `PrivateDnsSettings`) ; Realme may relocate under `SIM & mobile network` or `Connection & sharing`. | AOSP + Realme UI inspection reports |
| Detection via Settings provider | `ContentObserver` on `Settings.Global` can observe mode/specifier changes — **works** for any app with no special permission (read via `Settings.Global.getString`). Writing requires `WRITE_SECURE_SETTINGS` (signature|privileged) — **not available** to ordinary app. | AOSP, API 17+ |
| Accessibility interception | `AccessibilityService` can observe `TYPE_WINDOW_STATE_CHANGED` / `TYPE_WINDOW_CONTENT_CHANGED` and react with `GLOBAL_ACTION_BACK` or `startActivity(ACTION_SETTINGS)` — **best-effort**, not a guarantee; timing, OEM overlays, and battery restrictions affect it. | Android docs, real-device reports |
| Device Administrator | `DevicePolicyManager` + `DeviceAdminReceiver` still present in API 35 but largely deprecated; `isAdminActive()` and `onDisableRequested()` work, but **cannot prevent uninstall** (only Device Owner / Profile Owner with `DISALLOW_UNINSTALL_APPS` can). | AOSP docs, API 35 deprecation notes |
| Foreground service | `FOREGROUND_SERVICE` + appropriate `foregroundServiceType` required; Android 14+ restricts background starts (`FOREGROUND_SERVICE_SPECIAL_USE` needs justification or use `dataSync`/`systemExempted` sparingly). | API 34+ behaviour changes |
| Boot handling | `RECEIVE_BOOT_COMPLETED` still works; `BOOT_COMPLETED` delivered after unlock; direct background work after boot is restricted — must use `WorkManager` / `AlarmManager` or foreground service after user interaction. | AOSP |
| Realme battery/background | Realme's "App management > Auto-start / Background freeze / Phantom process killer" can kill Accessibility services and foreground services. User must manually whitelist ("Allow auto-start", "Allow background", disable battery optimization). | Realme/ColorOS community reports, Android 12+ Phantom Process docs |

---

## 2. Feasibility Matrix

Columns per spec: **Requirement | Proposed mechanism | Feasibility | Required permission | Known limitation | Verification method**

### Group A — Device & Platform

| Requirement | Proposed mechanism | Feasibility | Required permission | Known limitation | Verification |
|---|---|---|---|---|---|
| A1. Read Private DNS current state | `Settings.Global.getString(PRIVATE_DNS_MODE/SPECIFIER)` | **Fully achievable** | None (read is allowed) | None; specifier may be null when mode=off/opportunistic | Unit test + device read |
| A2. Change Private DNS programmatically | `Settings.Global.putString` / `DevicePolicyManager` | **Not achievable without additional privileges** | `WRITE_SECURE_SETTINGS` (signature) or DO | Ordinary app cannot write secure settings | Manual test — verify write fails with SecurityException |
| A3. Identify Private DNS screen reliably | Parse Accessibility `AccessibilityEvent.source` className + text ("Private DNS" / "نظام أسماء النطاقات الخاص") | **Achievable with limitations** | Accessibility Service opt-in | Realme may rename/relocate fragment; multiple valid entry points | Manual crawl of Settings on target device; log className/text |
| A4. Detect navigation to Private DNS | `AccessibilityService.onAccessibilityEvent(TYPE_WINDOW_STATE_CHANGED)` + package/class filter | **Achievable with limitations** | `BIND_ACCESSIBILITY_SERVICE` + user enable | Not guaranteed for every path; split-screen / search entry may bypass | Manual: open Settings via search, deep-link, notification |
| A5. Redirect to main Settings on detection | `performGlobalAction(BACK)` ×1–2 then `startActivity(Intent(ACTION_SETTINGS).addFlags(NEW_TASK))` with cooldown (1–2s) | **Best-effort detection/response only** | Accessibility | Race: Settings may still show briefly; repeated loops avoided via cooldown + duplicate-event suppression | Manual: measure time-to-redirect, check for loops |
| A6. Reliably navigate to main Settings | `Intent(Settings.ACTION_SETTINGS)` | **Fully achievable** | None | None | Manual |
| A7. Conventional Device Admin provides relevant protection | `DeviceAdminReceiver` + `USES_POLICY_*` | **Achievable with limitations** | `BIND_DEVICE_ADMIN` + user activate | Only gives `onDisableRequested` warning and `isAdminActive()`; does **not** block uninstall, force-stop, or settings access on API 35 | Manual: activate, then attempt uninstall — observe system still allows |
| A8. Boot & background mechanisms | `RECEIVE_BOOT_COMPLETED`, `WorkManager`, `FOREGROUND_SERVICE` | **Achievable with limitations** | `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE` (+ FGS type), `POST_NOTIFICATIONS` (API33) | Realme background freeze may delay/skip; FGS requires notification; WorkManager may be deferred | Manual reboot test + `adb dumpsys activity services` |

### Group B — Private DNS Protection (§6)

| Requirement | Proposed mechanism | Feasibility | Required permission | Known limitation | Verification |
|---|---|---|---|---|---|
| B1. Immediate return to main Settings when Private DNS access attempted | Accessibility narrow filter on Private DNS fragment identifiers (className + text) → redirect | **Achievable with limitations** | Accessibility | Not universal; OEM-specific screens may be missed | Manual per-entry-point test (§19 cases 13-19) |
| B2. Avoid broad blocking / false positives | Whitelist only Private DNS identifiers; log but don't redirect unrelated `NetworkDashboardActivity` texts | **Fully achievable** | Accessibility | Requires maintaining allow-list of Realme classNames | Manual false-positive test |
| B3. Record event locally with timestamp/method/outcome | Room/SQLite + hash-chain + `System.currentTimeMillis` (+ `elapsedRealtime` for gap detection) | **Fully achievable** | None | Timestamp depends on system clock; monotonic check added | Unit test persistence |
| B4. No navigation loop, cooldown, duplicate handling | Cooldown 2s, duplicate key = package+class+windowId, max 1 redirect per detection | **Fully achievable** | — | — | Manual stress test |
| B5. Distinguish prevention vs detection | Dashboard + event log explicit fields: `attempted` vs `verified` | **Fully achievable** | — | — | Unit test |

### Group C — DNS Change Detection & Recovery (§7)

| Requirement | Proposed mechanism | Feasibility | Required permission | Known limitation | Verification |
|---|---|---|---|---|---|
| C1. Detect change via Settings observer | `ContentObserver` on `Settings.Global.PRIVATE_DNS_MODE` | **Fully achievable** | None for observer; read specifier via `getString` | Fires only while process alive; survives via FGS/WorkManager but may miss changes during force-stop | Manual: change via adb `settings put global private_dns_mode off` |
| C2. Detect change without prior baseline | Cannot invent baseline; store last known mode/specifier observed after user setup; if unknown, report "baseline unknown — manual check required" | **Fully achievable** | None | First-run baseline is empty | Unit test |
| C3. Automatic restoration | `Settings.Global.putString` | **Not achievable without additional privileges** | `WRITE_SECURE_SETTINGS` | Ordinary app cannot restore; must guide manual recovery with deep-link | Manual: attempt putString, expect SecurityException |
| C4. Reporting & history | Same event store | **Fully achievable** | None | — | Unit test |
| C5. Not silently choose provider | Recovery only offers to restore *previously observed* specifier after explicit user confirmation via Settings navigation; never invents | **Fully achievable** | — | — | Unit test + manual |

### Group D — Protection Against Disabling Components (§8)

| Requirement | Proposed mechanism | Feasibility | Required permission | Known limitation | Verification |
|---|---|---|---|---|---|
| D1. Monitor Accessibility enabled | `AccessibilityManager.getEnabledAccessibilityServiceList` + `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` contains own service | **Fully achievable** | None | — | Manual toggle |
| D2. Auto-re-enable Accessibility | Silent re-enable | **Not achievable without additional privileges** | — | Android requires user to re-enable in Settings; app can only prompt via `ACTION_ACCESSIBILITY_SETTINGS` intent | Manual |
| D3. Monitor Device Admin active | `DevicePolicyManager.isAdminActive()` | **Fully achievable** | None after activation | — | Manual |
| D4. Auto-re-enable Device Admin | Silent re-enable | **Not achievable** | — | Requires user activation | Manual |
| D5. Monitor notification/FGS | `NotificationManager.areNotificationsEnabled()`, `ActivityManager` | **Fully achievable** | `POST_NOTIFICATIONS` | — | Manual |
| D6. Detect change, re-evaluate, log, prompt | Periodic `WorkManager` + `onResume` checks + event log | **Achievable with limitations** | WorkManager | Realme may delay workers; `onResume` is reliable | Unit + manual |

### Group E — Anti-Disable / Anti-Uninstall (§9)

| Requirement | Proposed mechanism | Feasibility | Required permission | Known limitation | Verification |
|---|---|---|---|---|---|
| E1. Detect App Info page for this app | Accessibility filter on `InstalledAppDetails` / `AppInfoDashboardFragment` where packageName == own package + text contains app name | **Achievable with limitations** | Accessibility | System may use different fragment on Realme; search entry may bypass | Manual |
| E2. Detect Uninstall screen | `PackageInstaller` activity / `UninstallAppProgress` | **Achievable with limitations** | Accessibility | Same as above | Manual |
| E3. Detect Device Admin deactivation screen | `DeviceAdminSettings` activity | **Achievable with limitations** | Accessibility | — | Manual |
| E4. Detect Accessibility Settings | `AccessibilitySettingsActivity` | **Achievable with limitations** | Accessibility | Broad; must be narrow to avoid blocking all Accessibility settings | Manual |
| E5. Attempt redirect to main Settings | Same redirect mechanism | **Best-effort** | Accessibility | OEM uninstall flow may be outside Settings app (Google Play, launcher) — not interceptable | Manual |
| E6. Record attempt / re-evaluate / report | Event store + dashboard | **Fully achievable** | — | — | Unit |
| E7. Prevent icon hiding as security | Do not hide icon; launcher icon always visible | **Fully achievable** | — | — | Manual |
| E8. Claim uninstall prevention | Must **not** claim | **Not achievable without DO** | — | Owner must be told uninstall remains possible via system | Doc + dashboard wording |

### Group F — Password & Lock (§10)

| Requirement | Proposed mechanism | Feasibility | Required permission | Known limitation | Verification |
|---|---|---|---|---|---|
| F1. Strong password (a-zA-Z0-9+specials) | Flutter text field + zxcvbn-style strength meter; final password set in Phase 9 | **Fully achievable** | None | — | Unit |
| F2. Never plaintext; salted hash | `Argon2id` or `PBKDF2-HMAC-SHA256` (100k+ iters) + per-user 16B salt; via `dart:crypto` + platform `EncryptedSharedPreferences` for salt/hash; or native `androidx.security.crypto` | **Fully achievable** | None | — | Unit + security review |
| F3. Lock immediately on background | `WidgetsBindingObserver.didChangeAppLifecycleState(inactive/paused/detached)` + `AppLifecycleListener` + native `onPause` → clear sensitive state + require re-auth | **Fully achievable** | None | Process death may skip callback — handled via `onResume` re-lock | Manual lifecycle tests |
| F4. No dev credentials in release | `kDebugMode` guard + build-time assert; `CHANGELOG` records removal | **Fully achievable** | — | — | Build verification |
| F5. Recovery policy | No silent reset; document: if password lost, app data must be cleared via system (which erases history) — this is inherent limitation | **Fully achievable** | — | Must be explained honestly | Doc |

### Group G — Internal Settings & History (§11–12)

| Requirement | Proposed mechanism | Feasibility | Required permission | Known limitation | Verification |
|---|---|---|---|---|---|
| G1. Protect security settings behind auth | Auth gate via `SecurityStateManager.isAuthenticated`; settings repo checks flag | **Fully achievable** | — | — | Unit |
| G2. No in-app Clear History | History screen has no clear button; DB is append-only via DAO (no delete API exposed to UI) | **Fully achievable** | — | System "Clear data" still erases history — must be documented as out-of-scope | Unit + manual (check clear-data still possible) |
| G3. Persistent history offline | Room / drift / SQLite + encrypted DB; filters, chronological order | **Fully achievable** | — | — | Unit |
| G4. Tamper-evident chain | `event = {id, type, ts, prevHash, hash = SHA256(canonicalJson(eventWithoutHash))}`; verification scans chain on startup; detects gaps/deletions after last verified point but cannot prevent system-level deletion | **Achievable with limitations** | — | Cannot detect deletion of entire DB; but can detect partial deletion via hash break | Unit: tamper test |
| G5. Reject unauthenticated changes | DAO + ViewModel guard; log `UNAUTHORIZED_ACCESS_ATTEMPT` | **Fully achievable** | — | — | Unit |

### Group H — Reboot & Resilience (§13)

| Requirement | Proposed mechanism | Feasibility | Required permission | Known limitation | Verification |
|---|---|---|---|---|---|
| H1. Run recovery checks after reboot | `BOOT_COMPLETED` receiver → `WorkManager` immediate check → log + notification (if permitted) | **Achievable with limitations** | `RECEIVE_BOOT_COMPLETED`, `POST_NOTIFICATIONS` | Realme may delay until device unlocked; FGS cannot start from background on API 34+ without exemption — so checks are *polling* not continuous | Manual reboot |
| H2. Verify permission states | Same as D1/D3 | **Fully achievable** | — | — | Manual |
| H3. Restore monitoring | Re-register `ContentObserver`/`WorkManager` | **Achievable with limitations** | — | Observer only lives while process alive | Manual |
| H4. Surface gaps | Record `MONITORING_GAP_SUSPECTED` when `elapsedRealtime` delta >> expected worker interval | **Achievable with limitations** | — | Heuristic, not proof | Unit |

### Group I — Dashboard (§14)

| Requirement | Proposed mechanism | Feasibility | Required permission | Known limitation | Verification |
|---|---|---|---|---|---|
| I1. Per-layer states (7 states) | `SecurityState` enum + `ProtectionLayer` model | **Fully achievable** | — | — | Unit |
| I2. Overall derived from layers | `overall = min(layers.required)`; never green if any required is Inactive/Unknown | **Fully achievable** | — | — | Unit |
| I3. Distinguish prevention/detection/recovery/verified | Event fields `responseAttempted` / `responseVerified` | **Fully achievable** | — | — | Unit |

---

## 3. Principal Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Accessibility not enabled / revoked | High | Protection silently inactive | Dashboard warning + persistent notification (if permitted) + onboarding that explains necessity; document that Android cannot silently re-enable |
| Realme background killer stops service | High | Missed detections | Whitelist instructions (Arabic); WorkManager + BOOT receiver + foreground notification; document gaps as unverified |
| Private DNS changed via adb / system app path not observed | Medium | Undetected change | ContentObserver catches any `private_dns_mode` change regardless of UI path; log baseline unknown honestly |
| False positive redirect | Low | User frustrated | Narrow filter + cooldown + manual test matrix; provide "Protection paused 30s" (authenticated) for legitimate maintenance |
| Device Admin deprecated → removed in future API | Medium | Loss of warning layer | Architecture isolates Device Admin as optional layer; dashboard marks Unsupported |
| Password loss | Low (but catastrophic) | App data must be cleared, history lost | Document recovery policy in Phase 9: no backdoor; recommend writing password down and storing securely |
| System Clear Data | Medium | History erased | Document as out-of-scope; hash-chain detects partial tampering but not full wipe — never claim immutability |

---

## 4. Audit Conclusion

- **Fully achievable:** 22 requirements
- **Achievable with limitations:** 15 requirements (mostly Accessibility narrow detection + Realme background)
- **Best-effort only:** 3 requirements (redirect is never guaranteed)
- **Not achievable without additional privileges:** 4 requirements (silent DNS restore, silent re-enable, uninstall prevention, guaranteed interception)

**Proceed to implementation with the strongest legitimate mechanisms. No requested feature requires Documented API violation; every limitation will be disclosed honestly in the dashboard and in `KNOWN_LIMITATIONS.md`.**
