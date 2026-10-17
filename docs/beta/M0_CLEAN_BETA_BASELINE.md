# M0 — Clean Beta entry and isolation

Source decision: [ADR-19 / Clean Beta](https://app.notion.com/p/3fc44ee65d118130899bc774b292e434), 17 October 2026.
Audit base: `308e405ee65adb45f28d677974b42166ee3d20a3` (after #282). One owner, one milestone, one PR. No M2/M6 implementation in this change.

## Audit and retained baseline

- `settings.gradle.kts`: app hosts lab:beta; Beta depends on finance-core, bank-engine and the transitional lab:engine. No ui-kit exists yet. Removing the old interpreter is M6/M7/M8 work, not M0.
- `BetaEnvironment.kt`: only `masroof_beta.db` and `masroof_beta_prefs` are permitted. `masroof.db`, Parsing Lab and migration staging are forbidden. No production repository/container is imported by Beta.
- `BetaDatabase.kt`: retain all 21 tables and schema 10, including migrations 1→10. No new migration or destructive fallback.
- `BetaEntry.kt` / host `SettingsScreen.kt`: one explicit Developer Settings bridge, same APK, non-exported activity, no launcher/deep link/second live SMS receiver. Return finishes Beta without clearing the caller's task.
- `BetaContainer.kt`: FIFO lifetime and backup/recovery retained. An existing database is preflighted read-only using a non-deleting corruption handler. Failed integrity, unknown entry state or unsupported Room schema fails closed; startup never resets or creates a replacement for unreadable data.
- Production runtime/UI, Production Room schemas, financial golden fixtures, Finance Core and existing interpreter code remain unchanged. Git tree/blob and SHA-256 baseline is in `M0_CLEAN_BETA_BASELINE.json`. The manifest is code evidence, not screenshot parity certification.

## Entry state and acceptance

| Existing isolated Beta state | Screen | Explicit action | Retained state |
| --- | --- | --- | --- |
| All user tables empty and no Beta preferences | Welcome to Masroof 2 | Start experience | One string acknowledgement in Beta prefs; no bank, asset, SMS or posting created |
| Any previous Beta row or preference, no acknowledgement | Previous Beta data is available | Continue existing Beta data | All previous rows, annotations and unrelated preferences retained |
| Known acknowledgement | Existing Beta shell | None on subsequent launches | No reset/import/reprocessing on entry |
| Unreadable database/recovery or unknown entry version | Safe failure, Retry/Return | Retry or return | Original files retained; no fallback to a fresh account |

Back/Return before acknowledging leaves entry unacknowledged. Acknowledgement is idempotent, serialized with the existing lifetime lock, backed up with the existing string-only preference format, and survives a new process. It is **not** completion of the bank/SMS/financial onboarding wizard. No permission is requested, inbox is read, or interpretation/financial decision is made by startup.

## Verification map

- `BetaStartStateTest` (JVM): fresh state ignores populated synthetic production preferences and protected database files; repeated reads/reopen do not acknowledge; explicit start remains empty; all 21 table snapshots and unrelated preferences survive existing-data acknowledgement and backup/restore; preference-only/annotation-only history; unknown state and corrupt bytes retained.
- Existing `BetaEnvironmentTest`, `BetaModuleLayoutTest`, `BetaContainerIsolationTest`: allowlists, dependency boundaries, entry task flags and isolation.
- Production `BetaBridgeEntryPointTest`, `BetaProductionIsolationTest`, `M0ProductionBaselineTest`, golden/dashboard oracle tests remain unchanged. CI runs the existing production suite rather than rewriting its expectations.
- `BetaStartActivityTest` (native isolated emulator package): welcome → Back → welcome → explicit start; recreation and fresh launch; previous-data notice → Return → same notice → resume; corrupt database → Retry/Back with exact byte preservation. Protected synthetic file hashes and production preference sentinels are checked. This is an isolated test APK, not a claim of testing a user's Production installation.
- `BetaStartProcessDeathTest`: three separately started instrumentation processes prove NEW survives force-stop without acknowledgement, then READY survives a second force-stop after explicit acknowledgement; distinct PIDs are asserted. Runs once in the existing lifecycle job, excluded from smoke.
- Existing Activity/permission tests now explicitly tap the entry acknowledgement before exercising their original routes/assertions. No original assertion or financial gate is removed.
- Existing CI retains one dynamically selected latest stable Android API for both smoke and lifecycle; no new matrix, emulator job, full-suite duplicate, release or production migration work.

## Native evidence

The existing `instrumentation-reports` artifact collects `beta-dashboard-screenshots/m0-{new-welcome,existing-preserved,corrupt-retained}.png`. Each M0 capture also records API/hardware/window configuration and the synthetic screen's semantics. Native XML is authoritative for pass/fail. CI run/head and verification results are recorded in the PR after the run completes.

M0 screenshots document entry/failure behavior only. Full Production/Beta screenshot parity for Home/Transactions/Review/Settings/details, Arabic/English, light/dark/font/foldable is not certified here and belongs to the following shared-design work. Existing Production source/financial golden expectations are frozen by the manifest and baseline tests; user data is never used for screenshots.

## Remaining / stop boundary

- M0 retains previous Beta data visibly instead of silently treating it as a new user's accounts. It does not create a second Beta database or hide/move that data.
- The old baseline/candidate interpreter and baseline-authorized financial adoption still exist. M0 grants them no new authority and does not claim the new-engine gate is met.
- No new shared UI design, full wizard, ML model, ledger/schema change, Production v18 import, signed production release, cleanup or next milestone begins in this PR.
- Review this PR and its final-head CI before merge. M2 starts only on a subsequent user instruction.
