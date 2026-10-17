# Known Limitations — DNS Guardian

*Last updated: 2026-10-17 (Phase 2 + native components). This list is living; update per phase.*

These limitations are **by design of Android's security model** for an ordinary,
non-Device-Owner, non-rooted, factory-reset-free app. The app maximizes friction
but cannot override the OS. Nothing here is hidden from the dashboard or docs.

---

## 1. Cannot prevent uninstalling the app
Ordinary apps cannot block their own uninstallation. Only Device Owner / Profile
Owner with `DISALLOW_UNINSTALL_APPS` can, and that requires a factory reset to
provision — explicitly out of scope. The app **detects and logs** uninstall-screen
attempts where Android exposes them, but the system can still proceed.

## 2. Cannot silently restore a changed Private DNS configuration
Writing `Settings.Global.private_dns_mode`/`specifier` requires
`WRITE_SECURE_SETTINGS` (signature/privileged) or Device Owner. An ordinary app
only has read access. DNS Guardian detects changes and guides **manual** recovery;
it never invents or auto-applies a DNS provider.

## 3. Accessibility Service is best-effort, not universal
- It observes `TYPE_WINDOW_STATE_CHANGED`/`TYPE_WINDOW_CONTENT_CHANGED` for
  `com.android.settings`. It **cannot intercept every OEM screen, shortcut, search
  entry, or launcher uninstall path**.
- Realme/ColorOS may rename or relocate the Private DNS screen; detection relies on
  an additive allow-list of class names + Arabic/English text fragments. New Realme
  paths require manual addition (see `Constants.kt`).
- A redirect is logged as **attempted, not verified** — we cannot confirm the screen closed.

## 4. Cannot silently re-enable a revoked Accessibility Service or Device Admin
Android requires the user to re-enable these in system Settings. The app can only
prompt via a deep link and log the revocation.

## 5. Background execution is constrained (especially on Realme)
- Realme's "Auto-start", "Background freeze", and "Phantom process killer" can stop
  the service and the foreground/WorkManager tasks.
- After reboot, checks run via `WorkManager` + `BOOT_COMPLETED`, but may be delayed
  until first unlock or be deferred by battery policy. The dashboard shows
  `UNKNOWN`/gap states honestly.

## 6. Monitoring gap while the app/process is dead
If the app is force-stopped or killed, no detection occurs. We infer a possible gap
heuristically (long elapsed since last recovery check) and log
`MONITORING_GAP_SUSPECTED` — but a gap is **not proof of an attack** (requirement 17).

## 7. History is not immutable against external deletion
The event log is append-only within the app and uses a SHA-256 hash chain to detect
partial tampering. However, **system "Clear data" or ADB `pm clear` deletes the
entire database**. We do not provide an in-app delete (requirement 11), but we
cannot prevent OS-level data wipes. We never claim immutability.

## 8. Password recovery
There is **no silent or insecure reset**. If the owner forgets the password, the
only path is to clear app data via system settings — which erases the history
(because the keystore is app-private). This is inherent; documented in the handoff.

## 9. Device Admin does not grant Device Owner powers
On API 35, `DeviceAdminReceiver` only gives a `onDisableRequested` warning and
`isAdminActive` status. It does **not** block uninstall, force-stop, or settings
changes. Marked `UNSUPPORTED` in the dashboard when not active; never presented as
equivalent to Device Owner.

## 10. UI lock ≠ system protection
Locking the Flutter UI on background (zero grace) protects in-app access. It does
**not** by itself prevent someone from changing Android system settings via other
paths. The dashboard separates "app locked" from "system protected".

## 11. Sandbox build limitation
The development sandbox lacks JDK/Flutter/Android SDK and cannot reach Google
download hosts, so the APK is built via CI or the owner's machine (see
`BUILD_VERIFICATION_REPORT.md`). This is a toolchain constraint, not a code defect.
