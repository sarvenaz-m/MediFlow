# Verification record — 1.0.0

The source and distributable companion were checked in a Linux host environment using JDK 17, Gradle 8.13, Android SDK 36, Robolectric, Node.js, and Chromium. Results below describe completed checks, not intended coverage.

## Completed checks

| Check | Result |
| --- | --- |
| Kotlin schedule engine | 16 tests passed: date boundaries, weekdays, travel, DST gaps/overlaps, half-hour transitions, exclusions |
| Kotlin inventory policy | 12 tests passed: exact fractional quantities, stock bounds, reversible record transitions |
| Room repository | 12 tests passed: transactions, concurrent duplicate actions, optimistic edits, history retention, backup validation and rollback |
| Android reminder identities | 3 tests passed: distinct occurrences and actions, immutable PendingIntents |
| Android reminder reconciliation | 6 tests passed: idempotence, exact/approximate modes, denied permission, notification cancellation, snooze replacement |
| Native Compose journey | 1 end-to-end host test passed: onboarding, recording, cabinet search, validation, fractional entry, activity recreation with retained draft, Insights and Settings |
| Browser domain engine | 25 tests passed |
| Standalone browser journey | 15 checks passed: record/undo, stock, persistence, search, validation, backup export/restore, themes, keyboard dialog handling, archiving, responsive navigation, and no external requests/runtime errors |
| Automated web accessibility | No axe-core WCAG 2.1 A/AA violations in 11 checked states across light/dark themes, desktop/mobile layouts, and medication dialogs |
| Android lint | 0 errors; advisory warnings for newer dependency versions and the API-qualified adaptive launcher resource |
| Debug app | `:app:assembleDebug` completed |
| Optimized preview app | `:app:assembleBenchmark` completed with R8 and resource shrinking |
| Benchmark harness | `:benchmarks:assembleBenchmark` completed |
| APK signature | Verified with Android Build Tools `apksigner`; APK Signature Scheme v2 |
| APK manifest | Package `dev.mediflow.app`, version `1.0.0`, minimum API 26, target API 36, not debuggable, no `INTERNET` permission |

There are **50 JVM/Android host tests**, **25 browser domain tests**, and **15 browser journey checks**. These are counts of executed tests/checks, not a code-coverage percentage.

Machine-readable evidence is included in [android-tests.json](validation/android-tests.json), [android-lint.json](validation/android-lint.json), [web-journey.json](validation/web-journey.json), and [build.json](validation/build.json). `CONTENTS.sha256` identifies the packaged files.

## Native images

The images in `assets/native/` are captures of the actual Android Compose view rendered in Robolectric's native graphics mode at 411 × 891. They are host renders with fictional data, not photographs or captures from a physical handset. The UI journey also checks that a medication draft survives activity recreation before it is saved.

## What remains device-dependent

No physical device or hardware-accelerated Android emulator was available for this verification. Real notification timing, Doze, reboot delivery, vendor battery restrictions, TalkBack, device font scaling, installation behavior, and system document-picker interactions have not been measured on a handset.

The Macrobenchmark and baseline-profile collection journeys compile, but were not run on a device. No measured startup/frame-time improvement or app-specific generated baseline profile is claimed. The APK can contain profiles supplied by AndroidX dependencies.

The included quality workflow is configured to repeat the build and host checks. It has not been executed on a remote repository as part of this package preparation.

## Toolchain policy

Dependency versions are pinned as a tested compatible set in `gradle/libs.versions.toml` and `package-lock.json`. Hilt 2.58 is retained with AGP 8.13.2; changing the major Android build toolchain is a separate compatibility update. New-version lint advisories are retained in the report rather than presented as a zero-warning result.

The preview APK uses a development signing certificate. It contains no private signing key and is not presented as a store release. A future production distribution requires its own signing configuration and device validation.
