# M1 build compatibility audit for Beta rehearsals

The repository retains its existing stack during financial refactoring. This is
an evidence-based audit of the current tuple, not an upgrade or a claim that every
vendor combination is fully certified.

| Component | Pinned source | Current value |
|---|---|---|
| Runtime/bytecode | existing CI and Android compileOptions | JDK/JVM 17 |
| Gradle | wrapper URL and SHA256 | 8.8 |
| Android Gradle Plugin | gradle/libs.versions.toml | 8.6.1 |
| Kotlin / Compose compiler plugin | version catalog | 2.0.21 |
| KSP | version catalog | 2.0.21-1.0.28 |
| Room runtime/compiler/testing | version catalog | 2.6.1 |
| Android compile / target / minimum | existing app and new isolated host | 35 / 34 / 26 |

[Gradle8.8 release notes](https://docs.gradle.org/8.8/release-notes.html)
identify the pinned Gradle release; its successful execution here is recorded by
the build logs, not inferred from another major version.

[AGP 8.6 compatibility](https://developer.android.com/build/releases/agp-8-6-0-release-notes)
supports compile API35 and requires JDK17 and Gradle8.7 minimum; this project's
Gradle8.8 exceeds that minimum. Runtime emulator API is independently selected by
the existing stable-image resolver; testing API36 does not mean compileSdk or
targetSdk was raised.

The [official Kotlin compatibility table](https://kotlinlang.org/docs/gradle-configure-project.html)
lists Kotlin2.0.20-2.0.21 with Gradle through8.8. Its Gradle8.7+ caveat concerns
Multiplatform withJava, which this Android/JVM repository does not use. However,
the listed fully-supported AGP range ends at8.5: **AGP8.6.1 is outside that listed
full-support range**. Passing repository builds are empirical compatibility
coverage, not evidence that the vendor support matrix includes this tuple.

[Room2.6.1](https://developer.android.com/jetpack/androidx/releases/room#2.6.1)
remains pinned with the existing matching KSP plugin. Current compilation, native
migration and recovery tests provide project-specific evidence. No new compiler
support guarantee is inferred from an older Room release number.

The test-only host reuses these pins, disables release variants, and creates a
separate ephemeral test signing certificate. It changes neither production app
identity/version/signing configuration nor the financial implementation. A future
coordinated toolchain upgrade should resolve the Kotlin/AGP support gap in its own
PR with Room/KSP, native migration, device and release verification. M1 remains
open wherever full compatibility acceptance requires that work.
