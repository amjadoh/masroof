# Application lifecycle test contracts

This is a map of existing production paths and executable coverage, not a claim that tests prove every possible bank message or device safe. All tests run on PR/main CI unless the API-specific exception in CI.md applies.

| Boundary | Required evidence |
| --- | --- |
| SMS capture -> durable raw evidence -> parse -> reconciliation -> review | Bank adapter contracts/fixtures, parse validator tests, historical/live retry unit tests, and M13 real process-death phases at each boundary |
| Reconciliation failures remain incomplete and recoverable | `HistoricalDerivedRecoveryTest`, processing retry repository/workflow tests, native retry/process-death journeys |
| Manual decisions survive refresh and restart | `ModelRefreshWorkflowTest`, learning persistence/device tests, `LearningProcessDeathJourneyTest` |
| ML assists family only, and unsafe assistance abstains | Family feature export/runtime residue, trainer validation/test acceptance gates, `FamilyLearningTest`, interpretation device journeys |
| Forecast coverage, published-vs-actual and baseline gate | `AnalysisLifecyclePolicyTest`, pure analytics policy tests and advisory SQLite migration tests; estimator algorithms are unchanged |
| Commitment identity, changed amount, financial period | `CommitmentPaymentAttributionTest`, `CommitmentsOverviewBuilderTest`, `DashboardFixtureCharacterizationTest`, `DashboardViewModelTest`, `CommitmentPeriodLifecycleTest` |
| Current period rolls forward while explicit history stays selected | Moving-clock ViewModel tests; application period workflow remains the only period authority |
| UI draft/apply/discard and language/theme geometry | Four independent fidelity variants and four chart interaction variants, existing sheet/geometry tests; assertions and pages/scopes retained |
| First SQLite open -> application close | `StartupQueryShutdownTest` uses a real connection callback barrier; storage corruption recovery tests remain unchanged; startup unit teardown now closes the container normally |
| Whole worker/database lease -> restore/close | Model refresh workflow/worker tests, database access/restore recovery tests and native backup/recovery cases |
| Real installed APK update | `AppUpgradePreservationTest` seeds real SMS/ledger, account/card/loan ownership, active/paused commitments and a correction; identical persisted fingerprint required after baseline-to-current update |
| CI output -> merge/release authority | `test_ci_device.py`, routing/script tests and `test_verify_ci_status.py`; incomplete/skipped/duplicate/missing case output and partial CI are rejected |

## Root causes corrected

Cancelling startup did not cancel SQLite's native first-open callback. Room close could acquire the close write lock and wait for SQLiteOpenHelper's monitor, while the callback held that monitor and waited for Room's invalidation read lock. The native barrier regression reproduced both sides of that deadlock. Shutdown now stops producers and drains admitted startup, application-scope capture, learning, analytics and financial leases before closing stores; it does not solve the lock inversion by increasing CI timeouts.

Commitment display names are editable labels. Payment matching now uses the source transaction's persisted merchant/counterparty/biller identity, with a unique biller code taking precedence when both sides provide it. Amount changes do not prevent a match. Only eligible positive payments inside the selected period can mark an occurrence paid. Actual paid amount and source payment ID are exposed; future expected amounts and the ledger are not rewritten. Competing commitments remain unpaid with a visible ambiguity reason instead of assigning the first ID. A one-time commitment's date alone is not payment evidence.

No financial database, schema version, application identity, preference namespace or production worker identity is added or changed by this work. The financial schema remains v18. Existing auxiliary learning/analytics databases retain their own migration tests.
