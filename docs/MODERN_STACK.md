# M1 build foundation: first increment

This PR implements the Kotlin 2 build foundation (M1.1/M1.2). Versions are a
conservative, pinned compatibility step, not a claim that every dependency is the
latest release. Runtime-library upgrades and target-SDK behavior changes require
separate review and acceptance evidence. M1 remains in progress.

## Pinned version matrix

| Component | Before | This increment | Reason |
| --- | --- | --- | --- |
| Build JDK / JVM bytecode | 17 / 17 | 17 / 17 | Supported AGP baseline; same local and CI major version |
| Gradle | 8.7 | 8.8 | Supported by Kotlin 2.0.20/2.0.21; official distribution SHA-256 pinned |
| Android Gradle Plugin | 8.5.2 | 8.6.1 | Supports compile API 35 and includes fixes for Kotlin 2 dexing/R8 |
| Android build tools | AGP default 34.0.0 | AGP default 34.0.0 | AGP 8.6 documented default; no preview SDK required |
| Kotlin | 1.9.24 | 2.0.21 | Stable K2 compiler foundation |
| KSP | 1.9.24-1.0.20 | 2.0.21-1.0.28 | Matching Kotlin compiler line, retaining compatible Room generation |
| Compose compiler | AndroidX compiler 1.5.14 | Kotlin Compose plugin 2.0.21 | Official Kotlin 2 integration; same version as Kotlin |
| Compose BOM | 2024.06.00 | 2024.06.00 | Compatible runtime baseline retained to isolate compiler migration |
| compileSdk | 34 | 35 | AGP-supported stable SDK; applies equally to app and Android lab modules |
| targetSdk / minSdk | 34 / 26 | 34 / 26 | Behavior and device-support changes deferred |

The Compose compiler plugin is applied to every Compose module (`app`,
`lab/android`, `lab/beta`). `lab/engine` uses the same catalog Kotlin version.
No financial code, expected results, Room schemas, application IDs, or signing
configuration change in this increment. Build tools are shared by the production
and Beta code in the APK, so existing production regression tests remain gates.
Testing on the latest stable emulator image is separate from compile/target SDK.

## Compatibility references

- [AGP 8.6 compatibility and 8.6.1 fixes](https://developer.android.com/build/releases/agp-8-6-0-release-notes): JDK 17, Gradle 8.7 minimum, API 35 maximum, build tools 34.0.0 default.
- [Kotlin 2.0.20 Gradle support](https://kotlinlang.org/docs/whatsnew2020.html#gradle): support through Gradle 8.8.
- [Android Kotlin compatibility](https://developer.android.com/build/kotlin-support): Kotlin 2.0 requires AGP 8.5 or later.
- [KSP 2.0.21-1.0.28 release](https://github.com/google/ksp/releases/tag/2.0.21-1.0.28).
- [Compose compiler setup](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler): use the Kotlin Compose plugin matching Kotlin 2.x.
- [Gradle 8.8 distribution checksum](https://services.gradle.org/distributions/gradle-8.8-bin.zip.sha256).

## Acceptance and remaining work

The PR must pass assembleDebug, unit tests (including the golden ledger and
architecture/isolation tests), lint/detekt, and the existing latest-stable Android
UI and process-recovery jobs before merge. Device screenshots remain CI artifacts;
this document does not substitute for green CI evidence.

Remaining M1 deliverables:

- M1.3: review and upgrade Room, WorkManager, coroutines, and AndroidX independently.
  Any schema change needs migrations and migration tests; none is part of this PR.
- M1.4: choose future targetSdk and minSdk based on actual supported devices; test
  target-SDK behavior separately. CI optimization was implemented in PR #248.
- Compose runtime/BOM upgrades remain a separate visual/API compatibility change.
- Record green CI and device/visual acceptance evidence before declaring M1 complete.
