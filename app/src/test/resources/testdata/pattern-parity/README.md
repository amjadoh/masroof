# Frozen AlJazira migration reference

Captured before changing the production interpreter, adapter, financial policies,
or schema, from the production sources at `daa63747` (2026-10-26).
The experiment branch adds test tools; it does not replace that interpreter.

- `aljazira-parse-v1.json`: all 74 inputs selected by `NewEngineFixtureTest.cases()`,
  including the ten reference inputs; 2,690 input/detection/outcome/field checks.
- `aljazira-financial-v1.json`: seven existing independent golden-ledger scenarios;
  834 persisted-fact/link/review/registry/dashboard checks. Both isolated worlds
  must also satisfy the existing independent expected financial values.

Values are SHA-256 field digests, not message bodies or clear-text identifiers.
Failures name changed fields only. Only `engine:` and `rules:` trace versions
are omitted from confidence; scores, blockers, statuses and other reasons remain.
Newly allocated account/card registry UUIDs differ across fresh databases, so only
those UUIDs are normalized across worlds. Each world's exact registry IDs must
remain unchanged after repeat import. Transaction identities and evidence links
are compared exactly. This does not replace an APK upgrade test on existing IDs.

Run `:app:testDebugUnitTest --tests '*AlJazira*ParityTest'` with JDK 21.
Reports go to `app/build/reports/pattern-parity/`; tests never update these files.
A changed corpus or baseline requires explicit review, not automatic regeneration
to make a changed implementation pass. No production financial data is read.

These checks establish the current reference and comparison harness. Passing them
before enabling a replacement is not proof that learned patterns have reached
production parity. Device upgrade, backup/restore and learned-pattern cutover
remain separate acceptance gates in `PRODUCTION_MIGRATION_PLAN.md`.

The candidate now reads `src/main/assets/bank-patterns/BANK_ALJAZIRA.rules.json`;
the reference retains the original compiled profile. Both match the frozen files.
`BankRuleProfileCodecTest` additionally requires complete profile equality and
the exact same refresh fingerprint before the production container loads the asset.

On this host, mixed Robolectric test classes can fail loading native SQLite.
The qualified run keeps native SQLite and the original assertions, and isolates
test classes using a temporary Gradle init script:

```groovy
gradle.projectsEvaluated {
    rootProject.project(':app').tasks.withType(org.gradle.api.tasks.testing.Test).configureEach {
        doFirst {
            forkEvery = 1
            maxParallelForks = 1
        }
    }
}
```

Pass it with `--init-script /path/to/isolation.gradle`; the repository's CI and
release configuration are unchanged. This is test-process isolation, not an
Android device/upgrade qualification or a substitute SQLite implementation.
