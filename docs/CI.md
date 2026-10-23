# CI and release

Every PR to main and every main push runs the full required suite. The stable merge checks are `unit-test`, `static-analysis`, `emulator-smoke` and `emulator-lifecycle`. The smoke aggregate requires metadata/script contracts, the shared APK build, every ordinary device shard, recovery, unit tests and static analysis. Missing, cancelled, skipped or failed dependencies fail the aggregate. Release requires the latest complete successful **main push** CI for the immutable checked-out SHA, including all four checks. It never reruns the full unit suite and has no emergency bypass.

## Execution and coverage

Unit tests (including pure modules), lint/detekt and the shared APK build run independently. The feature export is a declared unit-test output, so model reproducibility consumes the export from that same test execution/cache without a second Gradle test invocation.

`build-test-apks` builds baseline `b76c1afc` (version 900001), promoted app (900002) and instrumentation APK once. They use the same ephemeral debug signing key. `scripts/ci_device.py bundle` verifies package names and signing certificates, records commit SHA and APK SHA-256 hashes, and downloads the official AndroidX Orchestrator/test-services APKs. Device jobs consume that immutable artifact; they do not compile again. Java setup does not compete with setup-gradle for the Gradle cache.

`ci/device-suites.json` pins APK verification to the project's validated Build Tools 34.0.0 (rather than the newest SDK tool installed on a runner) and owns the validated API 36 image, four ordinary shards and the stateful phase registry. Ordinary tests default into the required suite, including new tests. Unknown stateful phases, overlap and stale registry entries fail. Runtime log-only discovery of the compiled APK must match the entire source inventory before execution. Shards use deterministic longest-processing-time assignment from `ci/device-durations.json`; update measured weights when test costs change. Arabic/English and light/dark matrix variants are separate cases with the same page/scope assertions and captures.

All four ordinary shards use native AndroidX Orchestrator with app data cleared between cases. Production process-death and real APK update each use a separate emulator. Stateful phases run directly without Orchestrator clearing their data; the upgrade has no uninstall or clear between before/after phases. The process-death pairs cover capture, parsing, reconciliation, review, manual decisions, retries, atomic ignore and learning feedback.

The default case deadline is 120 seconds. A 60-second listener captures thread stacks and shutdown stage; a missed deadline fails and preserves diagnostics. Every expected case must have exactly one successful result and a terminal instrumentation frame. `adb` exit status alone is insufficient. There is no retry-to-green policy. Per-case JUnit XML, timings, instrumentation output, logcat, job/process diagnostics and screenshots are retained even on failure. Screenshot copying occurs before Orchestrator clears the app data.

## Device scope and performance

API 36 and Ubuntu 24.04 are pinned validated choices, not auto-updated during a PR. `minSdk` remains 26. By agreement there is no additional API 26 job: `PreVacuumBackupSnapshotTest.concurrentExport_onPre327Sqlite_importsAndReopensRoom` needs API 26–28 and is explicitly excluded from this device scope. API 36 does **not** establish that older SQLite compatibility contract.

The target for normal review is 10–15 minutes, not a proven percentile. Inspect the critical path in Actions (queue, APK build, image/boot, case execution and evidence upload) and use JUnit/JSON case timings to rebalance. Assess p50/p95 after twenty natural complete runs; do not manufacture repeated reruns or remove coverage to meet the target. Cold caches, queueing and host contention can exceed it.

See [lifecycle contracts](TESTING_LIFECYCLE.md) for the safety map and regressions.
