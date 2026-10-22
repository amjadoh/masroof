# Production architecture

Masroof has one Android application, one financial database and one bank interpretation engine. The tested engine and advisory features are integrated into the existing app; the parallel Lab application and generated source replica are removed.

## Modules

- `app`: presentation → application workflows → domain/parsing/data/SMS/bank ports. `AppContainer` wires the only production runtime.
- `bank-engine`: pure Kotlin interpretation and literal rules, depending only on `finance-core` value contracts. `NewEngineSmsAdapter` maps its output through `InterpretationDraftMapper` and the production `ParseFinalizer`.
- `finance-core`: bank/money value contracts; no parallel financial ledger or calculators.
- `ml-assist`: local family classifier, cleaned human templates, forecasting and recurring-pattern detection. The engine consumes an injected assistance contract rather than depending on ML implementation.
- `ui-kit`: shared theme, typography, fonts, cards and charts. It cannot access financial storage.

## Processing and authority

Live SMS is captured durably before WorkManager receives a raw SMS id. Historical intake parses a batch before one discovery/reconciliation/review pass. Validation, confirmed ownership and manual decisions retain financial authority. Informational/OTP messages never post. Reconciliation failures retain durable retries.

The engine uses rules first. Eligible local family inference never supplies an amount, ownership or approval. Explicit corrections feed local learning. Rule/model refreshes target unresolved, unposted evidence without a manual decision. Previously posted history is preserved.

## Data and recovery

`masroof.db` remains schema 18 with its fourteen tables, full migrations and importable backup hashes. Existing preference files, Keystore alias, app id and worker names remain stable. `learning.db` stores labels/provenance; `analytics.db` stores advisory results and preferences. Old Lab files are neither imported nor deleted automatically.

The advisory stores are schema 2. Posted type edits atomically update the financial transaction and its latest `USER_FINANCIAL_TYPE` review decision in the unchanged financial schema. Learning rebuilds from durable decisions before enabling personal assistance; a cached model alone is not startup authority. Exact template suppressions include bank and feature identity and revoke both statistical and human-template assistance. Financial `EXPENSE`/`INCOME`, ownership decisions and field-only edits do not invent family labels. Unsupported or ambiguous decisions have an explicit feedback disposition.

`USER_EXTERNAL_TRANSFER` remains context-only: that action selects external ownership using the existing parsed transfer direction, rather than independently selecting an SMS family. Broad financial types and this resolution do not count as learned family corrections. The classifier features and score calibration are unchanged; activation gates, rather than a newly fitted estimator, provide the additional automatic-use restriction.

Automatic reconciliation compares its assembled evidence with current effective evidence and human decisions inside the financial write transaction. A stale model version or a concurrent correction cannot post an older interpretation. Feedback failures remain retryable without undoing the financial correction. Model refresh work synchronizes feedback before selecting a version and only refreshes eligible unposted evidence.

Family activation uses naturally reached rule-fallback cases, current score/margin/coverage thresholds, at least eight distinct accepted validation templates that pass financial validation, and zero accepted wrong validation families. Frozen test groups cannot choose thresholds or activation; an accepted wrong final-test family blocks artifact generation. The current small corpus does not meet activation support, so statistical families are suspended while rules and consistent explicit human templates remain available. The report distinguishes suggestion, validation and assembly, while device lifecycle tests verify persistence and later corrections.

Startup blocks financial screens while required maintenance is incomplete. Dashboards read persisted scoped evidence without parsing SMS or writing FX. Analysis reads approved financial inputs and writes only advisory results; a suggested commitment needs explicit confirmation through the existing commitment workflow.

Encrypted backups retain the existing envelope and financial migration support. Optional analysis is included in the durable restore journal for commit/rollback. Restoring an older backup clears stale analysis and rebuilds available results. Learned caches are invalidated and reconstructed from retained human decisions.

See [AGENTS.md](../AGENTS.md) for enforced boundaries and [ANALYTICS.md](ANALYTICS.md) for model and alert policy.
