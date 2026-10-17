# DNS Guardian (DNSGAURD)

Offline-first Android protection for **Private DNS** settings on a personal device
(Realme RMX3636, Android 15). Makes changing Private DNS, disabling protection,
and bypassing the app as difficult and detectable as Android's supported security
model permits — **without** a factory reset, without Device Owner provisioning,
and without root.

> This is a personal project. It is **not** an extension of Rihla or AlarmX.

## What it does (and does not) do

- Detects attempts to open the Private DNS settings screen and attempts an immediate
  return to the main Settings page (best-effort via Accessibility).
- Detects Private DNS configuration changes (via `Settings.Global` observer) and
  guides **manual** recovery — it never silently chooses or restores a DNS provider.
- Monitors its own protection components (Accessibility, Device Admin optional,
  notifications, boot recovery) and reports honest per-layer status.
- Locks the app UI immediately on background with a strong password (no grace period).
- Keeps a local, append-only, tamper-evident security history (no in-app delete).
- **Does not** prevent uninstalling the app, force-stop, or clearing its data —
  those are Android limitations for non-Device-Owner apps, disclosed honestly.

## Documentation

| File | Purpose |
|------|---------|
| `PROJECT_SPECIFICATION.md` | Goals, constraints, scope |
| `TECHNICAL_FEASIBILITY_AUDIT.md` | Feasibility matrix (what is/ isn't achievable) |
| `SECURITY_ARCHITECTURE.md` | Flutter + Kotlin design, permission model, threat model |
| `IMPLEMENTATION_PLAN.md` | Phased plan (1–9) |
| `BUILD_VERIFICATION_REPORT.md` | How to build the APK (CI + local); sandbox limitation noted |
| `SECURITY_TEST_REPORT.md` | Automated + manual device test checklist |
| `KNOWN_LIMITATIONS.md` | Honest list of Android-enforced limits |
| `CHANGELOG.md` | Version history |

## Building

See **`BUILD_VERIFICATION_REPORT.md`**. Short version:

- **CI (no local toolchain):** enable `.github/workflows/build.yml` on GitHub; it
  builds a signed release APK on hosted runners and uploads it as an artifact.
- **Local:** install JDK 17 + Android SDK (platform-35) + Flutter 3.24.x, then
  `flutter pub get` → `flutter build apk --release`.

> The sandbox used to author this project could not fetch JDK/Flutter/Android SDK
> (Google download hosts blocked), so the APK is produced via CI or your machine.
> CI success verifies compilation/tests, **not** physical-device behavior.

## Privacy

No internet permission is requested. No Firebase, analytics, advertising, or tracking.
No location, contacts, camera, or microphone access. The DNS *specifier* (your
provider hostname) is intentionally **not** stored.

## License

Personal project — all rights reserved to the owner.
