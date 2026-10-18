# M3: verify existing Beta storage and isolation

Source: ADR-19 Clean Beta, https://app.notion.com/p/3fc44ee65d118130899bc774b292e434. Base: merged #290, `31c036be5f6e6731c3f6f8d944b925699cd04573`.

M3 is verification of the existing Beta store with fixes for demonstrated gaps, not a storage rewrite. M4, parser changes, old-code removal and financial adoption are outside this increment.

## Existing behavior retained

- `BetaEnvironment` fixes the Beta database/preferences/backup names and forbids production, Parsing Lab and migration-staging database names.
- `BetaDatabase` remains schema 10 with the existing non-destructive 1 through 10 auto-migrations. No new schema, migration, reset, import or backfill is introduced.
- `BetaStartStateTest` checks new-user emptiness, acknowledged start/reopen, preference-only history, existing ledger/assets/markers, logical snapshots of all 21 tables across backup/restore, corrupt-file retention and unsupported entry versions.
- `BetaContainerIsolationTest` checks protected synthetic database/preferences remain unchanged across Beta maintenance. `IndependentBetaRuntimeTest` / `IndependentBetaRuntimeDeviceTest` check the current interpreter's saved raw evidence/outcome/manual decision survives reopen/restore, and historical records are inert.
- Existing identity and migration tests retain bank-qualified stable IDs, do not merge equal last4 across banks/environments, check foreign keys and preserve old Beta rows. No ownership logic is rebuilt in M3.
- Existing recovery/financial-recovery/device/process-death tests retain explicit rollback, snapshots and decisions. Startup does not automatically restore a snapshot or reinterpret old evidence.

## Demonstrated gap and minimal fix

Before the fix, two new isolated JVM regression tests failed: missing main database with retained sidecars, and an existing zero-byte main file, both opened successfully as an empty experience instead of stopping. Android/SQLite may initialize empty storage and clear/replace sidecar state. That is unsafe as a recovery policy even when those synthetic sidecars contain no usable SQLite data.

`BetaContainer.openDatabase()` now checks before Room can create/open storage:

1. Missing main with any `-wal`, `-shm` or `-journal` file: fail closed.
2. Missing main with a saved acknowledged entry: fail closed. Acknowledgement occurs only after a validated Room open; missing storage is not first launch.
3. Existing non-file or truncated file shorter than SQLite's 100-byte header: fail closed before SQLite initialization.

The existing read-only, non-destructive corruption preflight and Room's normal migration/schema checks remain. Truly fresh startup (no file/sidecars/acknowledgement) is unchanged; preference-only historical state and future entry-version checks keep their existing behavior. Startup uses the existing retained-data error/Retry/Return flow. No automatic reset, restore, deletion or new recovery UI is added.

`BetaStorageChecks` is shared test source for JVM and the native emulator. Three scenarios check repeated entry attempts preserve the missing/empty main state, every sidecar byte, Beta preference values, published backup files, and all protected synthetic database sidecars/preferences. They then explicitly restore a valid Beta-only backup and verify acknowledged start, marker and preference survive. All writes/deletions in these fixtures use isolated test-context synthetic files; no user database/inbox or physical device is opened.

## Verification

Before-fix JVM result: two storage regression tests failed because no exception was thrown. After-fix local and final-head CI/device results, actual report counts and artifact digests are recorded in the pull request. Existing assertions, golden fixtures and migration tests are retained. The native tests verify actual Android storage behavior, not just Robolectric simulation.

Runtime edit: `lab/beta/.../BetaContainer.kt` only. New shared verification plus JVM/device entry points and this audit document. No app/production/UI-kit/Finance Core/engine/runtime-backup implementation is changed. Existing Beta data, backups, corrections and history remain owned by the current store. Old-code deletion stays deferred, and M4 is not started.
