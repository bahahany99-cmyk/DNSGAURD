# Implementation Plan — DNS Guardian

**Method:** Incremental, small verifiable phases (2–9)  
**Branch:** `arena/f09b47b4-dnsgaurd` — all work lands here, pushed to origin for audit trail  
**Build sandbox:** Debian 12, no preinstalled Android toolchain — reproducible build via GitHub-hosted toolchains locally; sandbox documents exact failure and provides local-build instructions (see `BUILD_VERIFICATION_REPORT.md` when created)

---

## Phase 1 — Read-only Audit & Architecture (Current)

**Goal:** No device changes; produce feasibility evidence and architecture.

Tasks (done in this commit):
1. Inspect repo + sandbox toolchain.
2. Complete feasibility matrix (TECHNICAL_FEASIBILITY_AUDIT.md).
3. Design architecture & permission model (SECURITY_ARCHITECTURE.md).
4. Define phased plan (this file) and project spec (PROJECT_SPECIFICATION.md).
5. Identify risks and document limitations.

Deliverables:
- `PROJECT_SPECIFICATION.md`
- `TECHNICAL_FEASIBILITY_AUDIT.md`
- `SECURITY_ARCHITECTURE.md`
- `IMPLEMENTATION_PLAN.md` (this file)

Exit criteria: Docs committed, pushed, reviewed; no code that claims protection.

---

## Phase 2 — Application Foundation

**Goal:** Compilable Flutter+Kotlin skeleton, Arabic-first UI, dashboard, persistence.

Tasks:
1. `flutter create` structure (or manual gradle+kotlin if Flutter toolchain unavailable in sandbox — provide local-build instructions).
2. Theme (light/dark via `ThemeData` + `ColorScheme`), RTL, Arabic strings (ARB) + English fallback.
3. Navigation (`go_router`), DashboardScreen with `ProtectionLayer` tiles (states per architecture).
4. Local persistence: `EncryptedSharedPreferences` wrapper + Room `security_events` table + hash-chain util.
5. `SecurityState` model (Dart + Kotlin mirror) + platform channel.
6. Error handling, logging (in-memory ring, never secrets).
7. Unit tests for state transitions (e.g. `overall = worst(requiredLayers)`).

Deliverables:
- Compilable app (debug APK if toolchain available; otherwise build script + verification report explaining sandbox limitation).
- `BUILD_VERIFICATION_REPORT.md` (first edition).
- Automated tests.

Branch: same branch, logical commit per sub-task.

---

## Phase 3 — Password & Local Security History

**Goal:** Secure password infrastructure + history, no history deletion.

Tasks:
1. Password enrollment flow (deferred final password; dev-only test password gated by `kDebugMode`).
2. Hashing: Argon2id preferred, PBKDF2 fallback, salt in EncryptedSharedPreferences; verification via platform channel.
3. Lock on background: `AppLifecycleListener` + `onPause` + `onResume` re-lock tests.
4. Event repository: append-only DAO, filters, chronological order, hash-chain insert + verification.
5. History screen (auth-gated, no Clear button); unauthorized attempts logged.
6. Protected settings screen (auth-gated).

Tests:
- Correct/incorrect password.
- Background → relock (normal navigation, rotation, process recreation via `adb shell am kill`).
- History persists after restart.
- No in-app deletion path.

Deliverables: APK + phase report.

---

## Phase 4 — Android Protection Components

**Goal:** Accessibility + Device Admin (if justified) with honest status.

Tasks:
1. `GuardianAccessibilityService` (narrow filter, cooldown, anti-loop) — feature-flagged until manual Realme crawl.
2. `DeviceAdminReceiver` (optional layer, `onDisableRequested` warning, `isAdminActive` check).
3. Permission enablement screens (Arabic guides, deep-links to `ACTION_ACCESSIBILITY_SETTINGS`, `ACTION_DEVICE_ADMIN_ENABLE`).
4. Service-state checks (periodic + onResume).
5. Handle revocation: mark layer `INACTIVE`, log, prompt.

Tests:
- Enable/disable Accessibility manually → dashboard reflects.
- Activate/deactivate Device Admin → dashboard reflects.
- Revoke POST_NOTIFICATIONS → warning.

Deliverables: APK + phase report + permission-state tests.

---

## Phase 5 — Private DNS Monitoring & Response

**Goal:** Narrowly-scoped detection + immediate redirect; honest recovery reporting.

Tasks:
1. Crawl target device Settings to enumerate Private DNS entry points (AOSP + Realme paths).
2. Implement `DnsMonitor` ContentObserver for `private_dns_mode` / `specifier`.
3. Wire Accessibility filter for those entry points; implement redirect with cooldown.
4. Event logging for each attempt with `responseAttempted`/`responseVerified`.
5. Manual recovery dialog (deep-link to Settings) — no silent restore.
6. Test for false positives / loops.

Tests:
- Cases 13–22 of §19 (open main Settings, unrelated settings, open Private DNS → redirect, false-positive, loop, change via alternative route, detection, recovery, no silent provider selection).

Deliverables: APK + detection/nav tests + documented limitations.

---

## Phase 6 — Disablement & Uninstall-Attempt Monitoring

**Goal:** Best-effort detection of management screens.

Tasks:
1. Enumerate relevant screens: App Info, Uninstall, Device Admin deactivation, Accessibility settings.
2. Implement narrow detection (packageName == own package, or settings package + specific text) + redirect attempt.
3. Monitor protection permissions continuously.
4. Record attempts/outcomes; verify dashboard never claims prevention when removal succeeded.
5. Document bypasses (Play Store uninstall, launcher uninstall, adb `pm uninstall`, clear data, force-stop).

Tests: Cases 29–34.

Deliverables: Device test report + limitations report + APK.

---

## Phase 7 — Reboot, Recovery & Resilience

**Goal:** Reliable recovery checks after restart, visible gaps.

Tasks:
1. `BootReceiver` + `RecoveryWorker` (WorkManager).
2. Verify permissions after restart; restore scheduling where permitted.
3. Handle process recreation, battery restrictions (Realme whitelist instructions Arabic).
4. Test interrupted/failed recovery.

Tests: Cases 35–42.

Deliverables: Reboot/recovery test results + APK.

---

## Phase 8 — Integration, Security Review & Final Verification

**Goal:** Full test suite, security review, release APK.

Tasks:
1. Run all automated tests.
2. Security review: permissions, password handling, storage, history integrity, dashboard honesty.
3. Test all principal journeys (§19 1–48).
4. Remove dev credentials / bypasses.
5. Build release APK + integrity verification (`sha256sum`, `apksigner verify`).
6. Produce `SECURITY_TEST_REPORT.md`, `KNOWN_LIMITATIONS.md`, `CHANGELOG.md`.

Completion requires evidence, not just compilation.

---

## Phase 9 — Final Owner Configuration

**Goal:** Handoff with real password.

Tasks (documented, not executed in sandbox):
1. Install final APK on target device.
2. Enable required permissions (guide in Arabic).
3. Explain limitations per layer.
4. Owner creates final strong password (verified lock works).
5. Verify dashboard accurate and DNS not silently changed.
6. Provide manual test checklist.

---

## Timeline & Commits

- Each phase = 1–4 logical commits on `arena/f09b47b4-dnsgaurd`.
- Push after each phase: `git push origin arena/f09b47b4-dnsgaurd`.
- No force-push to main; PR from arena branch when ready.

---

## Risks & Fallbacks

| Risk | Fallback |
|------|----------|
| Sandbox lacks JDK/Android SDK/Flutter (current) | Produce full source + local-build instructions; `BUILD_VERIFICATION_REPORT.md` documents exact failure and reproducible local steps; CI badge ≠ device verification |
| Realme relocates Private DNS | Additive narrow filters; ContentObserver still catches any change regardless of UI location |
| Accessibility killed by battery manager | WorkManager + boot check + Arabic whitelist guide; honest gap reporting |
| Device Admin deprecated removal | Treat as optional layer; mark Unsupported |

---

## Definition of Done (Project)

- All 8 doc files present and honest.
- Release APK builds reproducibly locally; sandbox build documented.
- Dashboard truthful; no false "Fully Protected".
- Manual device checklist executed (or explicitly marked Not tested if device unavailable, with reason).
- No secrets in repo, no dev backdoors.
