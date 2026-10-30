# Production source audit

Baseline: `05b7e045f894b6a5283022e822da99083a9e4375`.

## Coverage and method

| Module | Production Kotlin files | Lines |
| --- | ---: | ---: |
| app | 507 | 51,214 |
| finance-core | 42 | 2,442 |
| bank-engine | 21 | 1,781 |
| ml-assist | 12 | 827 |
| ui-kit | 35 | 2,217 |
| Total | 617 | 58,481 |

`inventory.csv` enumerates every tracked `src/main/**/*.kt` file, with its baseline
SHA-256, line count, construct counts and disposition. There are no other tracked
production Kotlin source sets in this baseline. Generated sources and Gradle Kotlin
DSL are outside the source-file count; build configuration was inspected separately.

This was full-scope automated source screening plus focused semantic review of
candidate bodies, surrounding control flow, contracts and tests. It is not a claim
of manual line-by-line inspection of every expression in all 58,481 lines. Screening
looked for conditional/type dispatch, mutable/read-only pairs, casts/assertions,
stdlib/encoding/identity code, adjacency checks, labeled returns, aliases,
deprecation suppressions and escaped-dollar workarounds. Compilation and existing
architecture, financial characterization and integration tests supplement screening.
A signal is a review lead, not a defect or permission for a mechanical rewrite.

Baseline signals include 552 `when` occurrences, 14 Flow-wrapper calls in 10 files,
49 non-null assertions in 22 files, 8 adopted hex calls, 8 adjacent-pair operations,
8 type aliases, and one existing guarded `when` branch. Counts include comments or
imports where the lexical pattern matches; they are not AST diagnostic counts.

All adopted opportunities use guard conditions introduced in 2.1 preview and
stable since 2.2. The installed 2.4.21 compiler accepts them with no opt-in.
See [feature/version/source reference](reference.md).

## Adopted opportunities

Paths below are relative to the named module's `src/main/kotlin/com/baraa/masroof/`.
Each change retains its unguarded fallback, so adding a guard does not conceal
unhandled members of the sealed hierarchy.

| ID / file | Current baseline | Replacement and benefit | Risk and verification |
| --- | --- | --- | --- |
| A1 — `finance-core: domain/assembly/TransactionAssembler.kt`, `assembleSingle` | Classified branch contains an `if`; NeedsReview contains a second subjectless `when`. | Three guarded branches express deferred classification, conservative purchase and pending transfer directly; one exhaustive decision table replaces nested branching. | Financial risk: priority or short-circuit changes. Preserve Classified before NeedsReview, deferred pairing first, conservative purchase before transfer, exact predicates and early returns. Full finance-core and statement/reconciliation checks pass. |
| A2 — `app: application/statement/ImportStatementUseCase.kt`, `import` | Accepted branch nests ownership rejection and comparison in `if/else`. | Guard Accepted with failed exact-ownership validation, then compare the remaining Accepted branch; makes the no-ledger-read rejection boundary explicit. | Rejected parse must not run ownership/comparison; unowned Accepted must not read ledger. Existing statement service tests cover both and exact bank/mask identities. |
| A3 — `app: bank/BankSmsRegistry.kt`, `route` | Zero matches branches again on nonempty suspicions. | Guard the zero-match case, then retain plain NotMatched; removes nested result construction. | No adapter loop, detector, sender rule or collision ordering changes. Suspicions must not override one/multiple matches. Existing registry/adapter contract tests cover routing and ingestion. |
| A4 — `app: application/sms/HistoricalSmsScanner.kt`, `scanOnDispatcher` | Failed ingestion conditionally increments inserted, then always increments failed. | Guard the persisted-RawSms failure, retain Failed fallback; makes the durable versus uncaptured outcomes explicit at dispatch. | Failed must increment exactly once in both cases; inserted only for non-null id. Existing scanner tests explicitly cover both and batch/reference equality. |

No new public APIs, models, dependencies, compiler flags, repository reads/writes,
entities or schema changes are introduced. The six new guards remove nested result
selection and keep the original bodies, evaluation order and fallbacks.

## Evaluated and retained opportunities

| Files / implementation | Candidate replacement | Benefit / reason retained | Version and risk |
| --- | --- | --- | --- |
| `presentation/{dashboard,review,settings,onboarding,notification,statement}/*ViewModel.kt`, `application/analytics/AnalyticsWorkflow.kt`, `application/dashboard/ChartDataWorkflow.kt`, `presentation/navigation/AnalyticsNavigation.kt`, `application/maintenance/MaintenanceCompletionSignal.kt` | Explicit backing fields instead of mutable field + `asStateFlow`/`asSharedFlow` | Reject: shorter declaration exposes the mutable instance rather than the existing wrapper; retaining both objects eliminates the claimed simplification. | Stable 2.4; encapsulation, identity and concurrent state risk. |
| `finance-core: domain/assembly/TransactionAssembler.kt`, unmatched transfer switch | Remove fallback through data-flow exhaustiveness | Already adopted: earlier family filter permits an exhaustive two-case switch with no else. No new change to claim. | Stable 2.3; financial fallback semantics must remain. |
| `app: application/ingestion/ProcessStoredSmsUseCase.kt`, Partial branch | Guard event-null dispatch | Already adopted in baseline. ReviewRequired/NonFinancial also perform common outcome construction and different logging/retry work; splitting those bodies would duplicate behavior. | Stable 2.2; retry/posting/log-order risk. |
| `app: domain/ids/RegistryEntityIdFactory.kt`, `sms/hash/SmsBodyHasher.kt`, `application/update/ApkIntegrityVerifier.kt`; `finance-core: domain/ids/FinancialContainerIdFactory.kt`; `bank-engine: interpretation/RuleInterpreter.kt`, `patterns/PatternFactInterpreter.kt`; `ml-assist: analytics/AnalyticsData.kt`, `ml/family/FamilyInputCleaner.kt` | `toHexString` instead of formatting loops | Eight files already use stable stdlib conversion. Keep exact persisted identity/hash representations. | Hex stable 2.2; IDs, checksums and reproducibility risk. |
| `app: sms/receiver/DebugSmsPduExtra.kt` | Generic hex parser | Reject: existing parser deliberately accepts input differently. Prior modernization records this contract too. | Hex stable 2.2; debug-intake compatibility risk. |
| `app: data/preferences/AndroidKeystoreGitHubTokenEncryptor.kt`, `application/backup/DatabaseRestoreRecovery.kt` | Kotlin Base64 | Java implementation is already concise on minSdk 26; decoding/padding/error compatibility requires more work than the replacement saves. | Stable 2.2; encrypted preferences and restore-journal risk. |
| `app: domain/ids/{RegistryEntityIdFactory,UserCorrectionIdFactory}.kt`, `application/review/ReviewWorkflowService.kt`, `application/patterns/MessagePatternsWorkflow.kt` | `kotlin.uuid.Uuid` | No multiplatform requirement or functional benefit; retain generation and formatting. | Common UUID stable 2.4 with generation exceptions; persisted identity risk. |
| `bank-engine: patterns/ExampleTemplate.kt`; `ml-assist: analytics/{RecurringDetector,PurchaseForecaster}.kt`, `ml/family/FamilyInputCleaner.kt` | `isSorted*` | Existing adjacency operations compute gaps, continuity, overlap/separating evidence or bigrams. Ordering alone is not equivalent. | Stable 2.4; parsing/forecast-feature risk. |
| `bank-engine: interpretation/*`, `ml-assist: analytics/*`, `app: application/dashboard/*` non-null assertions | New smart casts / filtered fact types | Compiler cannot generally carry a filter predicate through arbitrary collection transformations. New fact wrappers would exceed source modernization; retain existing validated assumptions. | Mostly pre-2.0 mechanisms; no unsupported claim that 2.4 removes arbitrary `!!`. |
| `app: presentation/settings/SettingsViewModel.kt`, pending-update restoration | Capture state once and type-dispatch instead of repeated reads/cast | Potential safety cleanup, but not a post-2.0 capability; snapshotting concurrent state needs its own explicit semantics review. Remains a follow-up. | Existing smart casts; concurrent state behavior risk. |
| `app: application/dashboard/ForeignSarMarketRateProvider.kt:44`, `response.body?.string() ?: return@runCatching null` | Read non-null response body directly | Baseline compiler reports an unnecessary safe call under the installed OkHttp contract. Deferred as a separate library-contract cleanup, rather than claiming a new Kotlin language feature. | Current 2.4.21 diagnostic; verify HTTP failure/empty-body behavior and test doubles before removing fallback. |
| `app: application/update/AppUpdateService.kt`, GitHub error mapping | Guarded `when` / delete unused private parameter | A single conjunction is already clear; changing its syntax supplies no meaningful version-specific benefit. | Guard stable 2.2; low risk, rejected as churn. |
| `app: application/patterns/PatternApprovalWorkflow.kt`, fallback review findings | Type guard instead of safe cast | The cast is intentional optional probing after higher-priority diagnostics; a new nested typed switch adds branches. Retain diagnostic priority. | Guard stable 2.2; financial approval risk. |
| Existing compatibility aliases under presentation/common, dashboard and application/dashboard | Nested aliases | These names bridge established public/architectural contracts; scoping them into a class changes callers without useful simplification. | Stable 2.3; API/architecture risk. |
| Injected clocks and financial timing/period policies across app/finance-core | `kotlin.time.Clock`/`Instant` | `java.time` remains native to Android/JVM policies and persisted zone handling. Type migration has no source-only benefit. | Stable 2.3; timezone/backup/timing risk. |
| `ui-kit: ui/theme/MasroofTheme.kt` deprecation suppressions | Delete obsolete import workaround | Suppressions apply to Android window-color calls, not deprecated-import diagnostics. Kotlin 2.4 does not obsolete the Android compatibility branch. | 2.4 import change; UI/platform risk. |
| All modules' workflow dependencies / Room declarations | Context parameters / annotation target rewrites | Stable does not imply useful: explicit dependencies and processor targets already express required boundaries. | Stable 2.4 with context exceptions; API/schema/generated-code risk. |

## Scope decisions and remaining work

No experimental collection/map/result-checker/context-resolution features are
adopted. No collection pipelines are rewritten merely to reduce line count; numeric
folds retain checked arithmetic, ordering and rounding. No runtime optimizations are
claimed without measurements. Additional manual review may find further opportunities;
the lexical inventory is not proof that none remain. The pending-update cast and
potential internal validated-fact types are explicitly deferred, not silently fixed.
