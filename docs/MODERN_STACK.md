# Build stack

Use JDK 17 and the versions pinned in `gradle/libs.versions.toml`. The Android application is `:app`; shared UI is `:ui-kit`. Pure Kotlin JVM modules are `:bank-engine`, `:finance-core` and `:ml-assist`, with dependency checks in `build-logic/pure-jvm-boundaries.gradle.kts`.

There is no generated application copy or isolated Lab module. Financial Room schema version remains 18. Unit tests, pure JVM tests, model reproducibility, lint and detekt run in CI; stateful device update/recovery checks run on isolated emulators. Release verifies green CI before APK generation.
