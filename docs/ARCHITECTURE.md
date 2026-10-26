# Production architecture

Masroof has one Android application, one financial database and one bank interpretation engine. The tested engine and advisory features are integrated into the existing app; the parallel Lab application and generated source replica are removed.

## Modules

- `app`: presentation → application workflows → domain/parsing/data/SMS/bank ports and the pure financial core. `AppContainer` wires the only production runtime.
- `bank-engine`: pure Kotlin interpretation and literal rules, depending only on `finance-core` value contracts. `NewEngineSmsAdapter` maps its output through `InterpretationDraftMapper` and the production `ParseFinalizer`.
- `finance-core`: production money/value models, salary and statement periods, bank transaction time, deterministic container/transaction identities, financial classification/impact, transfer matching and assembly, and statement matching. Policies consume supplied facts and return decisions or transactions in memory; there is no ledger, repository, Android dependency or I/O. Existing engine value contracts remain available separately.
- `ml-assist`: local family classifier, cleaned human templates, forecasting and recurring-pattern detection. The engine consumes an injected assistance contract rather than depending on ML implementation.
- `ui-kit`: shared theme, typography, fonts, cards and charts. It cannot access financial storage.

Source namespaces use `com.baraa.masroof.engine.bank`, `com.baraa.masroof.finance.value`,
`com.baraa.masroof.ml` and `com.baraa.masroof.analytics`. Application integration
tests use `com.baraa.masroof.integration`. Historical `lab`/`beta` source paths and
source file prefixes have been removed. Existing serialized backup keys and retired
work identifiers keep their original spelling for upgrade compatibility.

## Financial policy ownership

The extracted sources keep their original `com.baraa.masroof.core.money` and
`com.baraa.masroof.domain.*` packages and persisted enum values.
Classification consumes `ClassificationEvidence` with resolved container facts;
production and policy tests use the same input contract.
Only the value models needed by those policies move. The two existing Money
contracts are not unified. `bank-engine` may still reference only
`com.baraa.masroof.finance.value`; depending on the core module does not give
it authority to classify, match or assemble financial transactions.

Ownership lookup/discovery/confirmation, repository ports, reconciliation,
human correction, durable retry and `ReconciliationCompletionPolicy` stay in
`app`. Assembly returns an in-memory outcome; the existing application workflow
still checks current evidence and human decisions inside the financial write
transaction. Dashboard builders/calculators and evidence scoping also stay in
`app`. The statement match policy and direction value live in core; canonical
CSV parsing, its stream interface and reconciliation workflow stay in app.

`FinancialTransaction.fromSingleEvent` shares the construction of a transaction
from one parsed event between automatic assembly and manual review. Callers
still authorize the operation and choose its type, endpoints and timing; the
factory does not classify, resolve ownership or write to storage. Manual and
automatic endpoint precedence remain separate.

This extraction preserves schema v18 and the existing write paths. It introduces
no backfill, history reparse, new database, worker, backup format or UI behavior.
Policy tests run directly on JVM in `finance-core`; app integration tests cover
the unchanged persistence, orchestration and dashboard paths. Architecture checks
scan both source roots, require the moved policies to exist in core, reject
app-side copies and reject fully qualified calls that bypass module import rules.

## Processing and authority

Live SMS is captured durably before WorkManager receives a raw SMS id. Historical intake parses a batch before one discovery/reconciliation/review pass. Validation, confirmed ownership and manual decisions retain financial authority. Informational/OTP messages never post. Reconciliation failures retain durable retries.

The engine uses rules first. Eligible local family inference never supplies an amount, ownership or approval. Explicit corrections feed local learning. Rule/model refreshes target unresolved, unposted evidence without a manual decision. Previously posted history is preserved.

## Data and recovery

`masroof.db` supports schema 18 only, with its fourteen tables and unchanged identity hash. Older database and backup schemas are rejected without migration or destructive fallback. Automatic schema-fact backfill is removed; already queued backfill work completes without parsing or writing. Existing preference files, Keystore alias, app id and worker names remain stable. `learning.db` stores labels/provenance; `analytics.db` stores advisory results and preferences. Old Lab files are neither imported nor deleted automatically.

The learning store is schema 2 and the analytics store is schema 4. Posted type edits atomically update the financial transaction and its latest `USER_FINANCIAL_TYPE` review decision in the unchanged financial schema. Message learning rebuilds from durable decisions before enabling personal assistance; no derived message-model cache is written or loaded. Exact template suppressions include bank and feature identity and revoke both statistical and human-template assistance. Financial `EXPENSE`/`INCOME`, ownership decisions and field-only edits do not invent family labels. Unsupported or ambiguous decisions have an explicit feedback disposition.

`USER_EXTERNAL_TRANSFER` now teaches an incoming/outgoing family from the external type committed with the resolved review, including compatible retained decisions. The UI sends the direction explicitly displayed by its action. A changed direction is rejected before assembly; a changed effective evidence snapshot is rejected inside the financial write transaction. The refreshed detail explains the rejection. Missing decision evidence remains pending; later contradictory edits supersede the label and can suppress the template. Broad financial types still do not count as learned family corrections. Classifier features and score calibration are unchanged; activation gates, rather than a new estimator, restrict automatic use.

Automatic reconciliation compares its assembled evidence with current effective evidence and human decisions inside the financial write transaction. A stale model version or a concurrent correction cannot post an older interpretation. Feedback failures remain retryable without undoing the financial correction. Model refresh work synchronizes feedback before selecting a version and only refreshes eligible unposted evidence. Each refreshed parse commits an existing `HISTORICAL_BATCH` processing-retry marker in the same financial transaction. One scoped derived-recovery pass clears the marker only after reconciliation and review completion; retries also run that pass when all interpretations are already current. The worker holds a database access lease across the full operation, and container shutdown drains admitted operations before closing the financial or learning database.

Family activation uses naturally reached rule-fallback cases, current score/margin/coverage thresholds, at least eight distinct accepted validation templates that pass financial validation, and zero accepted wrong validation families. Frozen test groups cannot choose thresholds or activation; an accepted wrong final-test family or fewer than eight independent accepted final-test templates per enabled family blocks artifact generation. The current small corpus does not meet activation support, so statistical families are suspended while rules and consistent explicit human templates remain available. The report distinguishes suggestion, validation and assembly, while device lifecycle tests verify persistence and later corrections.

Startup blocks financial screens while required maintenance is incomplete. Dashboards read persisted scoped evidence without parsing SMS or writing FX. Analysis reads approved financial inputs and writes only advisory results; a suggested commitment needs explicit confirmation through the existing commitment workflow.

Navigation state, including analytics notification targets and return origins,
lives in `presentation/navigation`. Workers and application workflows do not
own screen navigation. Settings uses the shared card setup draft and one
registry-write/reload path; configuring a card does not reparse or reconcile
financial history. Renaming an account or card also uses that metadata-only path.
Manual reparse reconciles once, synchronizes merchant evidence, then completes
review updates using that same report. Settings reloads registries after a completed
reparse or rescan; it does not start another financial pass. A failed report remains
incomplete and durably retryable even if a second pass could succeed.

Encrypted backups retain the existing envelope and accept financial schema 18 only. Optional analysis is included in the durable restore journal for commit/rollback. Restoring an older backup clears stale analysis and rebuilds available results. Learning state is invalidated and reconstructed from retained human decisions.

See [AGENTS.md](../AGENTS.md) for enforced boundaries and [ANALYTICS.md](ANALYTICS.md) for model and alert policy.

### Closing SQLite-backed services

Application shutdown stops producers and drains admitted startup/database work before closing Room. Never cancel a first-open Room continuation and assume SQLite has stopped: its connection callback can still own the open-helper monitor while invalidation setup requests the Room close read lock. Taking Room's close write lock at that point deadlocks. Startup, learning/audit and analytics drain normally; the database access gate also drains independent WorkManager leases. `StartupQueryShutdownTest` controls that native first-open boundary.

## Continuous local personalization

Message statistics hold out stable bank-qualified template groups (20%) while
exact human-template memory remains separate. A personal candidate must improve
correct fallback coverage, add no wrong accepted families and retain the seed
validation gate. Seed-ineligible families cannot be enabled by local fitting.
Revoked/conflicted decisions are removed before rebuilding; held-out template
answers never enter personal statistical training or its evaluation lookup.

Recurring detection has a small numerical L2-regularized softmax classifier,
shared by offline training and on-device fitting. Entire series are held out;
only explicit human recurrence decisions and currently saved human commitments
supply labels. Prediction, rejection-by-absence and ordinary dismissal do not.
Series identities include the existing bank-qualified source container. Rules
remain the fallback when neither seed nor personal model is eligible; an eligible
model's abstention never falls through to rules. Amount/date estimates remain
deterministic. The current reviewed recurring seed corpus is empty, and its
artifact explicitly reports ineligibility, rather than shipping invented training
labels or forcing activation.

Analytics schema 4 adds durable recurring feedback, including an intent recorded
before the financial save. A later pass completes that intent only when the
saved commitment's source/cadence agrees. Feedback failure cannot undo a saved
financial commitment. Legacy rejection remains dismissal, not a negative label.
Explicit negative preferences remain until undone; missing evidence disqualifies
them from statistical training. Removing/changing a confirmed commitment revokes
its earlier training label. Numerical models are rebuilt from current facts and human decisions; unused
candidate weights and duplicate feature snapshots are not persisted.
Archive 4 retains compatibility with archives 1-3 and financial schema 18.

Forecast replay uses the existing forecaster on a strict historical prefix with
an empty origin day; later actuals are evaluation targets only. Replay results
never become published forecast history. Existing readiness and live performance
rollback gates are unchanged.
