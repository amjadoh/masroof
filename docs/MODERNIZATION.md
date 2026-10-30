# Repository modernization

Baseline: `fc8424bd43732f5b4e8e7779deef3b50485c1fa4` (2026-10-30).
The source inventory contains 2,027 named test methods in 328 test files.
Existing scenarios remain required when tests are migrated or split.

## Compatibility contract

| Store | Version | Identity hash |
| --- | --- | --- |
| Financial | 18 | `71f1193767b3aa12bcb6cc6e7ce77147` |
| Learning | 2 | `f7c337b86d9ca65685b1c1d8ac1b4eec` |
| Analytics | 4 | `9365757a722ce4ed979a352f9bd3a946` |

All exported schemas, financial fixtures, persisted keys, the application ID,
Keystore alias and worker identities are preserved. `minSdk` stays 26,
`targetSdk` stays 36 and Java/Kotlin bytecode stays at 21. Lifecycle cancellation
corrections are the only intentional behavior fixes in this work.

## Execution record

- Baseline debug and instrumentation APKs built successfully on JDK 21.
- Android image resolver regression reproduced before implementing numeric
  major/minor package selection. SDK package `37.0` and runtime API 37 are
  distinct values; previews, extensions and mismatched metadata remain rejected.
- Full unit and device suites remain PR/main CI requirements. Focused local
  checks are recorded below as each implementation slice completes.

The unfinished independent model-qualification work in the task list is
unrelated to this refactor and remains pending; this work does not activate models.

## Dependency snapshot

Official Maven and release metadata checked on 2026-10-30. Kotlin 2.4.21, AGP
9.5.0, Gradle 9.8.2, KSP 2.3.12, Compose BOM 2026.09.00 and the existing
AndroidX pins are already current stable releases. OkHttp moves to 5.5.1.
Detekt 2.0.0-alpha.6 is the explicit tooling-only prerelease exception needed
for the Kotlin 2.4 frontend; its split unused-class/function/property rules
preserve the previous unused-member check, and checks do not rewrite sources.

CI actions are pinned to the release commit, with the version beside each pin.
The Gradle daemon uses JDK 27, compilation targets remain 21, and Android unit
tests explicitly launch JDK 21 for Robolectric compatibility. CI installs Python
3.15.2. Ubuntu runner labels remain unchanged at the user's request: CI uses
Ubuntu 24.04, and release/command workflows retain their existing labels. Build Tools 37.0.0 and SDK image 37.0/runtime API 37
replace the previous validated CI image after the required device jobs pass.

Baseline visual evidence: four production fidelity cases passed on API 36,
covering home, transactions, accounts, cards and review in both languages/themes.
The 40 original captures are retained outside git for the final comparison.

Tooling checkpoint: Detekt app/core checks pass after the v2 rule migration;
all CI protocol/routing, release gate, version resolution and PR command contract
checks pass. Debug APK and instrumentation Kotlin compile with OkHttp 5.5.1;
focused HTTP/update unit tests pass. All seven exported schemas are byte-identical.
