# Build Verification Report — DNS Guardian

**Date:** 2026-10-17
**Phase:** 2 (Application Foundation) + native Android side (Phases 3–4 components)
**Application ID:** `com.dnsguardian.app`
**Version:** 1.0.0 (build 1)

---

## 1. Toolchain Audit (Sandbox / This Environment)

| Tool | Present? | Notes |
|------|----------|-------|
| JDK | ❌ No | Not installed; no `apt` repo reachable in sandbox |
| Flutter SDK | ❌ No | Not installed |
| Dart SDK | ❌ No | Not installed |
| Android SDK / cmdline-tools | ❌ No | Not installed |
| Gradle | ❌ No | Not installed |
| Network to Google hosts | ❌ Blocked | `storage.googleapis.com`, `dl.google.com` unreachable (SSL/connect errors). Flutter engine + Android SDK downloads therefore impossible here |
| GitHub (clone/release asset) | ✅ Reachable | `github.com`, `raw.githubusercontent.com`, `codeload` reachable |
| npm registry | ✅ Reachable | Available, but no Flutter/Android packages |
| PyPI | ✅ Reachable | `jdk4py` (JRE only, no `javac`) and other JDK packages found, but none include a compiler toolchain sufficient to build an Android APK |

**Conclusion:** A local APK build cannot be produced inside this sandbox. This is a
**toolchain/network limitation, not a code defect.** The source is complete and
buildable on a normal developer machine or on GitHub-hosted runners (which do have
network to Google hosts).

---

## 2. Reproducible Build Path Provided

Two supported paths are documented so the owner (or CI) can produce a signed APK:

### Path A — GitHub Actions CI (recommended, no local toolchain needed)
- Workflow: `.github/workflows/build.yml`
- Runs on `ubuntu-latest` (network available).
- Steps: JDK 17 → Flutter 3.24.5 → Android SDK platform-35 → `flutter pub get`
  → `flutter analyze` → `flutter test` → `flutter build apk --release`
  → SHA-256 + artifact upload.
- The owner enables the workflow and (optionally) provides a base64-encoded
  `upload-keystore.jks` as a repo secret to get a **signed** release APK.
- **Important:** CI verifies compilation, analysis, and unit tests. It does **not**
  run on the physical Realme RMX3636, so CI success ≠ device verification.

### Path B — Local machine (owner's laptop)
1. Install JDK 17, Android SDK (cmdline-tools, platform-35, build-tools 35.0.0), Flutter 3.24.x.
2. `flutter pub get`
3. Copy `android/local.properties.example` → `android/local.properties`; set `flutter.sdk` and `sdk.dir`.
4. (For signed release) create a keystore: `keytool -genkey -v -keystore upload-keystore.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload`, then set the four `KEYSTORE_*` values.
5. `flutter build apk --release`
6. Verify: `sha256sum build/app/outputs/flutter-apk/app-release.apk` and `apksigner verify`.

---

## 3. What Was Verified Here (Sandbox)

- ✅ Source tree is internally consistent (Flutter + Kotlin structure).
- ✅ `pubspec.yaml` pins only offline-safe, justified dependencies (no Firebase/analytics/network).
- ✅ AndroidManifest declares least-privilege permissions; **no INTERNET / location / contacts / camera / mic**.
- ✅ No secrets, no dev backdoors in committed source; dev password path is gated by `BuildConfig.DEBUG`.
- ✅ ProGuard keeps security classes; `allowBackup=false`, backup/transfer rules exclude data.
- ⚠️ Dart/Flutter **compilation** and **unit tests** could NOT be executed here (no SDK). They are included and will run in CI / locally.
- ⚠️ Kotlin **compilation** could NOT be executed here (no JDK/AGP). Code follows standard AGP/Kotlin patterns; final compilation is delegated to CI/local build.

---

## 4. Build Status Summary

| Item | Status |
|------|--------|
| Source complete (Flutter UI + native Kotlin) | ✅ |
| `flutter analyze` | ⏳ Pending (CI / local) |
| `flutter test` (unit) | ⏳ Pending (CI / local) |
| `flutter build apk` | ⏳ Pending (CI / local) |
| APK artifact produced | ⏳ Pending (CI run) |
| Physical-device install/launch | ⏳ Pending (owner, Phase 9) |

**Integrity note:** This report distinguishes sandbox-infeasible steps from
completed steps. No step is marked "verified" unless it was actually run.
