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
