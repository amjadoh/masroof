# Masroof agent notes

## Design system and theme

- All UI must follow the shared theme in `ui-kit/src/main/kotlin/com/baraa/masroof/ui/theme/` — do not introduce ad-hoc colors, typography, spacing, or icon sizes.
- Use `MasroofTheme` / `MaterialTheme` for colors and typography; extended semantic colors live in `MasroofThemeExtras.extendedColors`.
- Spacing and sizing tokens: `MasroofSpacing`, `MasroofIconSizes`, `MasroofElevation`, `MasroofShapes`.
- Reuse shared components from `presentation/common/` (`MasroofCard`, `MasroofSectionHeader`, `MasroofAmountText`, `MasroofMoneyRow`, `MasroofSecondaryScaffold`, etc.) instead of one-off layouts.
- All modal bottom sheets use `MasroofBottomSheet`; geometry and adaptive widths live in `MasroofSheetTokens`. Header/actions stay fixed and only the body scrolls. Multiple-choice rows use `MasroofSheetChoices`. Draft changes apply only on Save/Apply; Back, scrim, swipe, Close and Cancel discard them. Busy financial writes block dismissal.
- Chart category projections and filtering live in application dashboard builders / `ChartDataWorkflow`. Chart choices never change entity balances, dues, or full-scope summary metrics. Persist keys with their bank-qualified entity identity, never translated names or only last4.
- Do not add hardcoded `dp` values in feature screens when an existing token fits; add a new token in `ui-kit/src/main/kotlin/com/baraa/masroof/ui/theme/` only when the design truly needs a new size.
- App navigation lives in `presentation/navigation/` (`MasroofRoot`, `HomeDestination`, `SettingsDestination`).
- Debug builds expose a design catalog at **Settings → About → Design catalog** (`presentation/debug/DesignCatalogScreen.kt`) for previewing tokens and shared components.

## Architecture (follow on every feature)

**Layers:** `presentation` → `application` → `domain` / `parsing` / `data` / `sms` / `bank`. Dependencies point inward. Rules are enforced in `PackageDependencyRulesTest` — do not add exceptions; fix the import instead.

**Financial core:** `finance-core` owns production `core.money`, financial value models, period/time policies, container/transaction identities, financial classification/impact, transfer matching/assembly and statement matching. Their original `com.baraa.masroof.core.money` / `com.baraa.masroof.domain.*` packages are preserved. Do not recreate these implementations in `app`. Core receives facts and returns in-memory decisions; repository access, ownership discovery/confirmation, reconciliation writes/retries, CSV I/O and dashboard aggregation remain in `app`. `bank-engine` may import only the existing `lab.finance.value` contracts, never the financial decision policies. Both current Money contracts remain distinct. Extraction adds no schema migration, backfill or reparse.

**ViewModels:** Call `application/*Workflow` facades only. No direct use of `domain.repository`, `domain.ownership`, or `domain.period` from presentation.

**SMS:** Live intake = `IncomingSmsReceiver` → `LiveSmsIntake` → `CaptureBankSmsUseCase` (durable `RawSms`) → `LiveSmsWorkScheduler` → `LiveSmsProcessingWorker` → `ProcessStoredSmsUseCase`. Live `receivedAt` is the PDU service-center timestamp when valid (`LiveReceiptTimestamp`, earliest part for multipart); otherwise the injected device clock. Work input is the rawSmsId only — never SMS body/OTP text. Historical scan = `application/sms/HistoricalSmsScanner` → `HistoricalSmsBatchProcessor` (parse each row, then one discovery/reconcile/review pass per batch — never reconcile per historical SMS). `ProcessRawSmsUseCase` is only a capture-then-process facade. Do not add orchestration under `sms/`.

**Processing completion:** `ReconciliationCompletionPolicy` is the one reading of a reconciliation report. `summary.failed == 0` means the required writes did not report a failure. `summary.failed > 0`, or a thrown exception, is incomplete reconciliation even when other rows posted. Pending-match and intentional non-financial dispositions are not failures. A stored ParsedEvent is not a posted ledger row. Live processing returns `DerivedIncomplete(RECONCILIATION)` and WorkManager retries; exhaustion keeps a `LIVE` processing-retry row and an explicit error review, and startup reschedules it. A historical batch with any failed count stays `Incomplete`, writes one atomic `HISTORICAL_BATCH` set, and does not clear that set. Recovery clears the set only after a later pass has `failed == 0` and the review update succeeds. Interactive correction, restore, and ownership changes keep the manual decision and record a durable retry for the failed RawSms ids. A restore that already wrote `USER_FINANCIAL_TYPE` stays financial when reconciliation fails; it is not rewritten to `USER_NON_FINANCIAL`. A full maintenance `refreshReviewQueue` with `summary.failed > 0` throws and records the failed RawSms ids; it does not return success. Logs carry stage, failure count, a masked id, and retry state — never SMS body, account numbers, or secrets.

**Parsing vs dashboard:** Bank-specific logic stays in `bank/*` parsers. Populate `ParsedEventDetails` at parse time (`cardSmsChannel`, balances, due dates, etc.). Dashboard code in `application/dashboard/*` reads persisted facts only — never re-parse SMS text and never import `bank.*`.

**Room changes:** Migration + mapper + parser population + migration test. If existing users need the new column filled, wire backfill (see `ParsedEventFactsBackfillCoordinator`). Declare the new schema version in `SchemaFactsBackfillPolicy`: `BLOCKING` only if existing rows display incorrectly until re-parse, otherwise `BACKGROUND`. Device-test after schema/backfill merges.

**Maintenance:** Startup waits for `BLOCKING` maintenance (`StartupMaintenance`) and fails closed if it is incomplete: never expose financial screens until a blocking retry succeeds. Only `BACKGROUND` work may run while the app is open; it must be idempotent, keep failed rows retryable, and emit `MaintenanceCompletionSignal` so screens refresh.

**Parse-status gate:** Only `ParseStatus.SUCCESS`, an explicit automation-confirming correction (message family/amount), or an explicit `USER_FINANCIAL_TYPE` resolution may create/pair/post a `FinancialTransaction` (`TransactionAssembler.isAutomationEligible`). Merchant/counterparty-only edits never lift the gate. Never bypass it in reconciliation passes.

**Informational authority:** Auto-ignore and stale-link cleanup follow the parsed message family (`OTP`, `NON_FINANCIAL`, `BALANCE_NOTICE`) via `InformationalMessagePolicy`. Reconciliation and review dismiss do not re-read SMS wording. `UNKNOWN` stays reviewable.

**Transaction time:** Convert SMS wall clocks with `BankTransactionTimePolicy`, the only bank-to-zone map. AlJazira uses `Asia/Riyadh`. Other banks keep the zone stored on first assembly (`occurredAtZone`). Manual resolutions `resolveTransferAsExternal` and `resolveAsFinancialType` use `TransactionTiming.effectiveOccurredAt` and persist that zone. Stale transfer healing matches with the zone already stored on the transaction. Dashboard code does not branch on a bank timezone.

**Bank routing:** `BankSmsRegistry` evaluates every adapter; `Ambiguous` and `SuspectedBank` are persisted and reviewed, never parsed by a guessed adapter. Never resolve collisions by registration order. The router owns detection: adapter parse pipelines must not re-check the sender. Stored reprocessing selects an explicit `user_selected_bank:<bankId>` choice, then the stored ParsedEvent bank, then a fresh route. `Matched` parses. `Ambiguous` and `SuspectedBank` stay in review. Only a genuine `NotMatched` route may use the sole-adapter fallback. Queue refresh preserves the selection prefix. Selecting a bank does not change detector rules.

**SMS text matching:** Match on `NormalizedSms.comparisonBody` with `comparisonRegex(...)` / `containsComparison(...)` (Arabic-folded patterns); never display `comparisonBody` — slice display values with `normalizedSlice(range)`.

**Classification:** Bank interpretation lives in `bank-engine/`, using `RuleLibrary` / `AlJaziraRuleProfile` and explicit evidence/blockers. Competing families stay UNKNOWN and reviewable. OTP security wins over quoted financial wording; refunds retain refund authority. Add regression coverage for role or classification changes. The retired AlJazira parser/classifier/extractor implementation must not return, including test-only copies. Local ML in `ml-assist/` supplies family assistance only, never money, ownership or posting authority.

**Validation firewall:** Only `ParseFinalizer` may emit `ParseResult.Success`, and only when `DefaultParsedEventValidator` reports no ERROR findings. Tighten automatic use through validator rules / `AutomaticUsePolicy`, not by special-casing parsers; validators never resolve ownership or transaction type.

**New bank:** Implement `BankSmsAdapter`, add fixture tests under `testdata/`, and register a `BankSmsAdapterContractCase` (real financial + non-financial fixtures, positive/negative senders) in `BankSmsAdapterContractTest`; no sample may be claimed by more than one adapter.

**PRs:** Target `main` only. Partial architecture merges may show broken UI until backfill lands — that is expected.

**Release CI:** Full tests run in PR/main **CI** only. Do not add `testDebugUnitTest` back to `release.yml`; release verifies green CI then builds the APK.

**Reference:** `docs/ARCHITECTURE.md` describes the current production modules; `docs/ANALYTICS.md` describes advisory analysis. Follow this file when historical records disagree.

**Production cutover:** Keep `masroof.db` schema v18, preference names, Keystore alias, worker identities and the original application id. No Lab UI, generated application replica or parallel intake may ship. Pure module package names may retain their historical `lab` segment; this is not a second runtime. Engine/model refreshes must not reparse posted evidence or override human decisions.

## Dashboard calculations

- All money totals live in `application/dashboard/*Builder` and `*Calculator`.
- Compose screens in `presentation/dashboard` must display pre-computed values only.
- Do not sum transactions, classify Mada vs credit, or aggregate spending inside Composables.
- Row-level facts (card last4, effective type, SAR equivalent) and owned account container ids come from `DashboardOverview.transactionFacts` / `ownedAccountContainerIds`; ViewModels and navigation never build container ids or call conversion helpers (enforced in `PackageDependencyRulesTest`).
- Classify card type via `ParsedEventDetails.cardSmsChannel` — not SMS body text in dashboard code. AlJazira credit includes an unlabeled `بطاقة` / `card` line plus available balance. A card line alone stays null. Debit markers still win.
- Use helpers such as `CreditFacilitiesOverview.aggregateCreditSalaryPeriodSpending()`, `DebitCardOverview.salaryPeriodSpendingNet`, and `AccountsSummary.totalInflow`.
- Credit facility due is one value per facility (primary + supplementaries share the statement due).
- Mada (debit) cards have salary-period spending only — no statement due.
- Loan repayments are detected from `LOAN_REPAYMENT` or `FEE` + `FINANCING_INSTALLMENT` SMS via `LoanRepaymentAttribution`; all dashboard calculators must use it.
- Dashboard evidence is scoped: read parsed/raw SMS only through `DashboardEvidenceSource` (`DashboardEvidenceScope`). Never call `ParsedEventRepository.listAll()` or per-row `RawSmsRepository.getById` on the dashboard load path; a rule needing unlinked history gets an explicit fact query (superset-safe, ordered by event id).
- `DashboardProjectionBuilder` is composition only. Add dashboard logic to the matching section (`AnalysisDashboardProjection`, `AccountsDashboardProjection`, `CardsDashboardProjection`, `CommitmentsDashboardProjection`) via specialist builders/calculators; shared inputs come from `DashboardProjectionContext`, never a second repository read.
- Dashboard loads are read-only: never write from `application/dashboard/*` projection code. Persisted FX enrichment belongs to `ExchangeRateEnrichmentWorkflow`; the dashboard applies resolved rates in memory only. A complete `(appliedExchangeRate, exchangeRateSource)` pair is immutable. A row with only one half is repaired by writing both columns from the same resolution, and a later market rate does not replace a complete pair.

## Testing dashboard changes

- Run targeted unit tests under `app/src/test/kotlin/com/baraa/masroof/application/dashboard/`.
- Evidence-scope changes must keep `DashboardFixtureCharacterizationTest` scoped-vs-whole-history equality green.
- For Mada linking, cover both registry-linked cards and Google Pay SMS without `خصمت من حساب`.
