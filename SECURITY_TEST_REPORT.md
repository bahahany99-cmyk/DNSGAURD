# Security Test Report — DNS Guardian

**Last updated:** 2026-10-17
**Status:** Phase 2 foundation complete; native components written. Automated unit
tests authored; **device tests pending physical Realme RMX3636 (Phase 9)**.

> Per requirement 19, every test is recorded with an actual result. Where a test
> could not be executed in this environment, it is marked **Not tested** — never
> "Passed".

---

## A. Automated tests (run by CI / locally via `flutter test`)

| # | Test | Result | Notes |
|---|------|--------|-------|
| T1 | SecurityState overall = activeVerified when all verified | ✅ authored | `test/security_state_test.dart` |
| T2 | Overall = inactive if one layer inactive | ✅ authored | No false "green" |
| T3 | Overall = permissionRequired/inactive/unknown downgrades | ✅ authored | |
| T4 | Device Admin UNSUPPORTED ignored (optional layer) | ✅ authored | |
| T5 | Paused protection → UNSUPPORTED | ✅ authored | |
| T6 | JSON round-trip of SecurityState | ✅ authored | |
| T7 | Hash chain intact → verify -1 | ✅ authored | `test/crypto_test.dart`, `test/event_repository_test.dart` |
| T8 | Tampered record breaks chain at index | ✅ authored | |
| T9 | Password strength estimator boundaries | ✅ authored | |
| T10 | Salt uniqueness/format | ✅ authored | |
| T11 | Event append + retrieve + chain link | ✅ authored | In-memory sqlite (sqflite_ffi) |
| T12 | Append-only (no delete API) | ✅ authored | Verifies no deletion method exposed |
| T13 | getEventsByType filter | ✅ authored | |

All authored tests are **Not yet executed** in this sandbox (no Flutter SDK). They
will run automatically in `.github/workflows/build.yml` and on the owner's machine.

---

## B. Manual device-test checklist (Realme RMX3636, Android 15)

> Execute in Phase 6/7/9. Record PASS / FAIL / PARTIAL / Not tested with timestamp.

### Application & password
| # | Scenario | Result | Date |
|---|----------|--------|------|
| 1 | Fresh install | ☐ | |
| 2 | First launch → enroll prompt | ☐ | |
| 3 | Password enrollment (≥12 mixed) | ☐ | |
| 4 | Correct password unlocks | ☐ | |
| 5 | Incorrect password rejected | ☐ | |
| 6 | Background → immediate relock | ☐ | |
| 7 | Return from another app → locked | ☐ | |
| 8 | Process recreation (adb am kill) → locked | ☐ | |
| 9 | Screen lock/unlock → locked | ☐ | |
| 10 | Protected settings without auth → denied | ☐ | |
| 11 | Security-rule change without auth → denied+logged | ☐ | |
| 12 | In-app history deletion absent | ☐ | |

### Private DNS
| # | Scenario | Result | Date |
|---|----------|--------|------|
| 13 | Open main Settings (no redirect) | ☐ | |
| 14 | Navigate unrelated settings (no false redirect) | ☐ | |
| 15 | Attempt open Private DNS → redirect | ☐ | |
| 16 | Immediate return to main Settings | ☐ | |
| 17 | No false-positive redirects | ☐ | |
| 18 | No navigation loop (cooldown) | ☐ | |
| 19 | Change DNS via alt route → detected | ☐ | |
| 20 | Change detected (ContentObserver) | ☐ | |
| 21 | Previous config recoverable? (manual only) | ☐ | |
| 22 | No silent provider selection | ☐ | |

### Protection components
| # | Scenario | Result | Date |
|---|----------|--------|------|
| 23 | Enable/disable Accessibility manually | ☐ | |
| 24 | Activate/deactivate Device Admin | ☐ | |
| 25 | Revoke notification permission | ☐ | |
| 26 | Each change detected | ☐ | |
| 27 | Which auto-recover (none silent) | ☐ | |
| 28 | Manual intervention needed flagged | ☐ | |

### App management
| # | Scenario | Result | Date |
|---|----------|--------|------|
| 29 | Open App Info for DNS Guardian | ☐ | |
| 30 | Attempt disable app | ☐ | |
| 31 | Attempt uninstall | ☐ | |
| 32 | Each detected (best-effort) | ☐ | |
| 33 | Redirect works (Settings) | ☐ | |
| 34 | Report does NOT claim prevention when removed | ☐ | |

### Lifecycle & resilience
| # | Scenario | Result | Date |
|---|----------|--------|------|
| 35 | Normal restart | ☐ | |
| 36 | Device reboot → recovery check | ☐ | |
| 37 | Force-stop → gap logged as unverified | ☐ | |
| 38 | Realme battery policy impact | ☐ | |
| 39 | Lost permission surfaced | ☐ | |
| 40 | Local storage error handled | ☐ | |
| 41 | Failed recovery reported (not success) | ☐ | |
| 42 | Recovery after manual re-enable | ☐ | |

### Data & integrity
| # | Scenario | Result | Date |
|---|----------|--------|------|
| 43 | Events persist after restart | ☐ | |
| 44 | No in-app delete function | ☐ | |
| 45 | Corrupt record handled (chain break) | ☐ | |
| 46 | No secrets in logs | ☐ | |
| 47 | Dashboard shows inactive/unknown correctly | ☐ | |
| 48 | Failed op never reported verified | ☐ | |

---

## C. Summary

- **Automated:** 13 test groups authored (pending execution in CI/local).
- **Manual:** 48 device scenarios defined; **Not tested** until Phase 9 on real hardware.
- **No test is marked passed without execution.**
