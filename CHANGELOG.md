# Changelog — DNS Guardian

All notable changes are documented here. The format follows a simplified keep-a-changelog.

## [1.0.0] — 2026-10-17 (Phase 1–2 foundation)
### Added
- Project specification, feasibility audit, security architecture, and phased plan.
- Flutter Arabic-first UI scaffold: dashboard, lock screen, password enrollment,
  security history (auth-gated, no in-app delete), protected settings, onboarding.
- Security-state model with 7 honest layer states and derived overall verdict
  (never "green" when a required layer needs attention).
- Local append-only event store with SHA-256 hash chain (tamper-evident partial
  detection; acknowledged not-immutable against OS wipe).
- Password strength estimator + salt generator (real hashing delegated to native
  PBKDF2, Argon2 noted as alternative).
- Immediate lock on background (`LifecycleLockObserver`, zero grace).
- Complete native Android side (Kotlin): `GuardianApplication`, `MainActivity`,
  `SecurityStateManager`, `DnsMonitor` (ContentObserver), `GuardianAccessibilityService`
  (narrow detection + cooldown + anti-loop), `GuardianDeviceAdminReceiver`,
  `BootReceiver`, `RecoveryWorker` (WorkManager), `EventStore` (SQLite), `Crypto`
  (PBKDF2 + EncryptedSharedPreferences), and the three platform channels.
- AndroidManifest with least-privilege permissions; no INTERNET/location/contacts/
  camera/mic. `allowBackup=false`, backup/transfer rules exclude data.
- Gradle/Kotlin build config (API 28–35), ProGuard rules, release signing via env/key.properties.
- GitHub Actions CI workflow to build a reproducible signed APK on hosted runners.
- Documentation set: PROJECT_SPECIFICATION, TECHNICAL_FEASIBILITY_AUDIT,
  SECURITY_ARCHITECTURE, IMPLEMENTATION_PLAN, BUILD_VERIFICATION_REPORT,
  SECURITY_TEST_REPORT, KNOWN_LIMITATIONS, this CHANGELOG.

### Constraints respected
- No factory reset, no Google-account removal, no Device Owner prerequisite.
- No root, no undocumented APIs, no misleading "fully protected" claims.
- Offline-first; no cloud/analytics/tracking.

### Known limitations
- See `KNOWN_LIMITATIONS.md` (uninstall not preventable, DNS not silently restorable,
  Accessibility best-effort, background killed by Realme, history erasable via OS).

### Next phases
- Phase 3: finalize password enrollment flow (Phase 9 owner password).
- Phase 4: validate Accessibility/Device Admin on the real device.
- Phase 5–7: Private DNS detection/response, app-management monitoring, reboot recovery.
- Phase 8: full test suite + release APK + security review.
- Phase 9: owner installs final APK, enables permissions, sets final password.

## [Unreleased]
- (planned) Device-crawl to enumerate Realme Private DNS entry points; add to allow-list.
- (planned) Arabic user guide + installation checklist.
