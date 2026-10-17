# DNS Guardian — Project Specification

**Project:** DNS Guardian (DNSGUARD)  
**Target platform:** Android 15 (API 35)  
**Target device:** Realme RMX3636 (Realme UI / ColorOS variant)  
**Repository:** `bahahany99-cmyk/DNSGAURD`  
**Branch:** `arena/f09b47b4-dnsgaurd` (base `main` @ `bf5735f`)  
**Date:** 2026-10-17  
**Status:** Phase 1 — Audit & Architecture (read-only)  
**Author:** Senior Android Security Engineer (Arena Agent Mode)

---

## 1. Purpose

A personal, **offline-first** Android application whose sole purpose is to make it as difficult as the Android security model permits to:

- open or change the **Private DNS** (`Settings > Network & internet > Private DNS`) configuration,
- disable the app's own protection components,
- access app-management / uninstall screens without detection,
- modify security rules or delete the local security history.

It is **not** an extension of Rihla or AlarmX. It runs on the owner's existing device **without** factory reset, without removing Google accounts, and without requiring Device Owner provisioning.

The app must **never** silently choose a DNS provider, replace the user's chosen provider, or claim capabilities it does not have.

---

## 2. Non-Negotiable Constraints

| # | Constraint | Rationale |
|---|-----------|-----------|
| 1 | No factory reset | Owner explicitly refuses destructive provisioning |
| 2 | No Google-account removal | Preserve existing device |
| 3 | No Device Owner as prerequisite | Provisioning a personally-owned device as DO normally requires factory reset |
| 4 | No root, no undocumented APIs, no exploits | Must remain supportable and Play-Protect compatible |
| 5 | Do not force/assume a DNS provider | Owner configures provider independently |
| 6 | Offline-first, no Firebase/cloud/analytics/tracking | Privacy; app must work without internet |
| 7 | No plaintext password, no hard-coded secrets | Standard secure-storage practice |
| 8 | No in-app history deletion | Requirement; system-level deletion is acknowledged as out-of-scope |
| 9 | Immediate lock on background | Zero grace period |
| 10 | Honest reporting — attempted ≠ verified | Never show green "Protected" when a layer is inactive/unknown |

---

## 3. Primary Objectives (Layered Protection)

1. **Private DNS access protection** — detect navigation to the Private DNS screen and attempt immediate return to the main Settings page; record event with timestamp/source/outcome.
2. **DNS change detection & recovery** — detect `private_dns_mode` / `private_dns_specifier` changes via Settings observation; attempt recovery only through supported APIs; otherwise guide manual recovery.
3. **Protection-component monitoring** — Accessibility Service, Device Admin, notification/foreground-service permissions, boot handling.
4. **Anti-disable / anti-uninstall monitoring** — narrowly-scoped detection of App Info, Device-Admin deactivation, Accessibility settings, Uninstall screens; attempt redirect; never claim uninstall is impossible for a non-DO app.
5. **Password & app lock** — strong password (a–z, A–Z, 0–9, special chars), salted hashing, immediate background lock, lifecycle-safe.
6. **Protected internal settings** — security rules editable only after authentication; unauthorized attempts logged.
7. **Local security history** — persistent, filtered, tamper-evident (hash-chain), no in-app clear.
8. **Reboot & lifecycle recovery** — re-verify permissions after restart, restore monitoring where permitted.
9. **Dashboard** — per-layer status (Active verified / Active unverified / Inactive / Permission required / Unsupported / Unknown) and honest overall assessment.
10. **Arabic-first UI** — Modern Standard Arabic primary, English secondary; source/docs in English.

---

## 4. Scope Boundaries

### In scope
- Flutter UI + native Kotlin platform channels
- Accessibility Service (narrow detection, user-consented)
- Conventional Device Administrator (where it adds verifiable value)
- Foreground service (only where justified; Android 14+ restrictions observed)
- Encrypted local storage (EncryptedSharedPreferences / SQLCipher-equivalent) + hash-chained event log
- Boot-completed handling, permission-state checks

### Out of scope
- Device Owner / Device Policy Controller provisioning
- MDM / Play private app publishing
- Cloud sync, remote wipe, remote admin
- DNS-over-TLS enforcement at OS level (cannot be done without DO/system app)
- Hiding launcher icon as security mechanism
- Blocking system settings via overlay without Accessibility

---

## 5. Actors

- **Owner** — the only legitimate user; creates final strong password in Phase 9.
- **Adversary** — anyone with physical access attempting to weaken DNS protection (including the owner under duress; the app raises friction, not absolute prevention).
- **Android platform** — Settings app (`com.android.settings`), Realme Settings variant, Package Installer, DevicePolicyManager, AccessibilityManager.

---

## 6. User Journeys

1. **Fresh install → first launch → permission grant → password enrollment (dev) → dashboard**
2. **Attempt to open Private DNS** → Accessibility detects `com.android.settings.Settings$NetworkDashboardActivity` / `com.android.settings.network.PrivateDnsModeDialogFragment` or Realme equivalent → GLOBAL_ACTION_BACK / intent to `ACTION_SETTINGS` → log → cooldown.
3. **Change DNS via alternative route** → ContentObserver on `Settings.Global.private_dns_mode` fires → log → show manual-recovery dialog (cannot silently restore without DO).
4. **Disable Accessibility** → `onServiceConnected` missing / `isEnabled` check fails → dashboard warning → re-prompt authorization.
5. **Open App Info / Uninstall** → detect package `com.android.settings.applications.InstalledAppDetails` with this app's package → redirect → log.
6. **Background / screen rotation / process death** → immediate relock; state restored via SavedState + encrypted store.
7. **Reboot** → `BOOT_COMPLETED` → re-check all layers → surface gaps.

---

## 7. Glossary

- **Private DNS** — Android's Private DNS / DNS-over-TLS setting (`private_dns_mode` = `off|opportunistic|hostname`, `private_dns_specifier` = hostname).
- **Accessibility Service** — `android.accessibilityservice.AccessibilityService`; observes `TYPE_WINDOW_STATE_CHANGED` events; cannot universally block system screens but can detect and respond.
- **Device Administrator** — deprecated `android.app.admin.DeviceAdminReceiver`; on Android 15 provides only `USES_POLICY_EXPIRE_PASSWORD`/`LIMIT_PASSWORD` remnants and `onDisableRequested` warning; **does not** prevent uninstall.
- **Device Owner** — DO; requires factory reset via `dpm` or NFC provisioning; can set `DISALLOW_CONFIG_PRIVATE_DNS`, lock settings — **not used** per constraint.

---

## 8. Success Criteria (Phases 1–9)

- Phase 1 deliverables present and reviewed.
- Compilable Flutter + native project, reproducible build, no secrets.
- Automated unit/widget tests for state transitions, password hashing, event log integrity.
- Manual device-test checklist executed on Realme RMX3636 Android 15; results recorded honest.
- Final APK installable, dashboard truthful, password enrollment enforced, limitations documented in Arabic user guide.
