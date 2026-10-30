# Build stack

Use JDK 27 for Gradle and install the JDK 21 compilation/Android test toolchain. Dependency versions are pinned in `gradle/libs.versions.toml`; the wrapper pins Gradle. The Android application is `:app`; shared UI is `:ui-kit`. Pure Kotlin JVM modules are `:bank-engine`, `:finance-core` and `:ml-assist`, with dependency checks in `build-logic/pure-jvm-boundaries.gradle.kts`.

There is no generated application copy or isolated Lab module. Financial Room schema version remains 18. Unit tests, pure JVM tests, model reproducibility, lint and detekt run in CI; stateful process-death/recovery checks run on isolated emulators. Release verifies green CI before APK generation.

Pure JVM modules use JUnit Jupiter 6.1.3; Android/Robolectric runners remain JUnit 4.
CI pins Python 3.15.2, Build Tools 37.0.0 and SDK package 37.0 / runtime API 37,
with existing Ubuntu labels retained. `minSdk` 26, `targetSdk` 36 and bytecode 21
are unchanged. The approved Detekt 2 alpha is a tooling-only exception; production
dependencies use stable releases. See [CI](CI.md) and the
[modernization verification record](MODERNIZATION.md).
