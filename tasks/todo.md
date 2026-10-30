# Personal spending and forecasting tasks

## Repository modernization (approved)

Execution record and compatibility baseline: [modernization](../docs/MODERNIZATION.md).

- [ ] Update build/analysis tooling, HTTP dependency and pinned CI actions.
- [ ] Validate SDK package/runtime API separation and required CI contracts.
- [ ] Migrate pure JVM tests to Jupiter without losing existing scenarios.
- [ ] Simplify settings state, dashboard projections and repeated write/cleanup paths.
- [ ] Prove cancellation propagation and preserve financial/recovery invariants.
- [ ] Delete proven unused code/resources; preserve schema and compatibility hooks.
- [ ] Redesign both product READMEs and reconcile technical documentation.
- [ ] Complete focused checks, APK/static checks and visual parity evidence.

## Existing personal spending work

Approved for implementation. Local production changes are in progress on
`feat/personal-spending-insights`; the #329 branch is unchanged.
Design and capability map: [PERSONAL_INSIGHTS_DESIGN.md](../docs/PERSONAL_INSIGHTS_DESIGN.md).

- [x] A. Audit exclusions and current readiness (2–3 files/tools).
  - Accept: reason-grouped counts, production cleaner parity, no inferred labels.
  - Verify: feature export, private qualification report, unchanged ledger/schema.
  - Areas: `ml-assist/tools`, `FamilyFeatureExportTest`, private outputs outside git.
  - Dependency: #329 baseline; no model activation from this audit alone.
- [x] B. Explain current forecast method/readiness (3–5 files).
  - Accept: existing result drives text/detail; error metric has horizon/sample count;
    absent checks show an honest empty state.
  - Verify: `AnalysisPresentationBuilder` focused tests and device detail journey.
  - Areas: `AnalysisPresentationBuilder`, `AnalysisForecastViews`, localized strings/tests.
  - Dependency: A; no numerical forecast changes.
- [x] C. Build unusual-payment observations from scoped facts (2–3 files).
  - Accept: prior-only reference, scope isolation, explicit sparse/invalid abstention.
  - Verify: focused analytics tests for MAD=0, FX, refunds, dates and same-day ambiguity.
  - Areas: pure `ml-assist` calculator/test, existing approved fact model if needed.
  - Dependency: A; provisional heuristic reviewed before enabling in UI.
- [x] D. Connect one observation to Spending and evidence (3–5 files).
  - Accept: at most three useful observations; exact drilldown; correction/deletion refresh.
  - Verify: application analytics tests, ledger parity and Arabic/English device journey.
  - Areas: `AnalyticsWorkflow`, snapshot/result, `AnalyticsScreen`, strings/tests.
  - Dependency: C; preserve current comparison and transaction navigation.
- [x] Checkpoint B–D: inspect observations privately; build/lint,
  data-quality states and unchanged financial totals before expanding the feature.
  - Private production probe produced observations and unchanged ledger;
    this demonstrates participation, not user-rated usefulness or measured accuracy.
- [x] E. Retain the existing spending-contribution explanation; no duplicate observation needed.
  - Accept: same completed-day window, no duplicate observation or double-counted claims.
  - Verify: scoped-vs-full characterization and spending-insights tests.
  - Areas: `AnalysisSpendingInsightsBuilder`, `AnalysisSpendingCharts`, focused tests.
  - Dependency: D evidence; cut this task if current comparison already communicates it.
- [x] F. Reuse recurring review/evidence/undo and clarify explicit decisions (3–5 files).
  - Accept: save/editor, negative/dismiss/undo remain distinct; actual origin visible.
  - Verify: recurring persistence, lifecycle/device correction/undo and backup tests.
  - Areas: existing recurring workflow, Commitment UI, strings/tests.
  - Dependency: A; retain seed and personal qualification gates.
- [x] G. Support reviewed structural message wording (3–5 files).
  - Accept: no personal vocabulary; version change and human-decision preservation.
  - Verify: cleaner/security/refund/negation regression, frozen evaluation partitions.
  - Areas: `FamilyInputCleaner`, feature export and focused regression tests.
  - Dependency: A audit; do not reparse posted evidence.
- [x] H. Select useful distinct message review examples (2–3 files).
  - Accept: bank-qualified groups, current unresolved evidence, deterministic priorities;
    no guessed labels or confidence when seed is ineligible.
  - Verify: grouping, changed/revoked decisions, unsupported and security cases.
  - Areas: application interpretation workflow/result and focused tests.
  - Dependency: G; use existing review authority.
- [x] I. Add optional small review session to existing settings (3–5 files).
  - Accept: max three initial examples, explicit decisions, discard draft on dismissal,
    visible pending/error state and preserved current correction flow.
  - Verify: device save/cancel/reopen and learning-provenance tests.
  - Areas: message settings, shared sheet, localized strings/tests.
  - Dependency: H; persistent labels use existing decisions, no duplicate features.
- [ ] J. Qualify base artifacts when independent data is sufficient (existing tools).
  - Accept: unchanged validation/final-test gates; report either eligible or blocked.
  - Verify: reproducible family/recurring training and untouched final evaluation.
  - Areas: reviewed corpus and generated artifacts/reports; private source stays outside git.
  - Dependency: G plus sufficient independent reviewed data; not promised by other tasks.
  - Current evaluation: ineligible. Reviewed return wording is accepted by the
    updated private family export, with frozen holdouts retained. Private counts
    stay outside git. Shipped family seed still enables no statistical family.
- [ ] Final checkpoint: focused tests, lint/detekt, APKs, relevant device lifecycle,
  upgrade/restore/process-death and full PR CI; verify actual readiness against UI.
  - Local checks passed; full follow-up PR CI remains required before merge.

## Personal insights local verification

- CI run 40111544025 failed only the recurring-feedback device journey with
  `performMeasureAndLayout called during measure layout`. Its diagnostic stacks
  show Compose's `waitUntil` advancing the virtual frame clock while waiting for
  external workflow feedback. The CI APK bundle matched source `a86fa81b`; three
  isolated local baseline runs passed, so the failure was timing-dependent.
- Recurring-feedback waits now observe the workflow StateFlow with the same
  90-second timeout, leaving UI synchronization to the existing Compose actions.
  Assertions retain financial parity and training counts and now require the
  exact series/decision kind and complete undo removal. Production code is unchanged.
- App/test APK builds and lint/detekt passed. The repaired case passed three
  isolated Orchestrator runs, then all 31 required cases in the original failing
  shard passed with API 36, Orchestrator isolation and CI's default font scale.
  No case, assertion, CI shard or validation gate was removed or disabled.
- A supplementary run at system font scale 1.3 passed the recurring journey but
  exposed the separate English home-shortcut one-line fidelity assertion; that
  variant is not reported as passing. The complete default-font shard passed.
- Approved visual refinement: append shared current-payment/prior-median bars
  after existing spending charts and retain exact evidence navigation. Explanations
  use shared exclamation-mark guidance, without expanding text in the chart card.
  No new chart dependency,
  model activation, persistence or financial calculation was introduced.
- The initial comparison refinement passed all 1,825 app and 16 ui-kit unit tests, lint, detekt,
  and both debug APK builds. Three API 36 journeys passed together, covering
  Arabic/English, light/dark, enlarged text, comparison evidence, inline expansion,
  correction refresh, ledger parity, existing charts/drafts and recurring undo.
- The subsequent contextual-help refinement passed 329 scoped app tests, lint,
  detekt and both debug APK builds. Four API 36 journeys passed with system font
  scale 1.3, covering hidden-by-default explanation, exclamation-mark opening,
  Back dismissal, Arabic/English and light/dark, chart drilldowns, quality actions
  and unchanged financial data. Native closed/open screenshots were inspected.
  The new help interaction failed against the previous text-button UI before the
  implementation. PR/CI monitoring remains stopped at the user's request.
- CI #944 exposed permanent hint text and non-ASCII resource digits. Both failures
  were reproduced locally and repaired through shared guidance and digit policy.
  Its evidence-row device failure came from checking before asynchronous loading;
  the journey now waits for the actual row and retains every evidence assertion.
  Full CI for the updated head remains required before merge.
- 373 focused app tests passed (analytics, dashboard, family learning, migration,
  persistence and package boundaries), plus two new message-selection tests.
  Targeted regressions were rerun after the final navigation/display corrections.
- 51 ml-assist tests, 11 Python training tests and 10 CI-device registry tests passed.
  Both shipped seed reproducibility checks passed. No statistical family enabled.
- App lint/detekt and debug/app instrumentation APK builds passed. No new dependency,
  financial schema change, worker identity or derived-model persistence was added.
- Nine API 36 tests passed together: new spending evidence/forecast explanation
  (Arabic/English, light/dark, 1.3× font), optional review/cancel, forecast ledger
  parity/reopen, recurring correction/undo, four lifecycle/restore migrations, and
  setup/learning/provenance/financial-validation journey.
- The additional private-backup device probe passed against the supplied encrypted
  backup. Production analysis kept the ledger unchanged; its model-participation
  report and derived observations stay outside git. Offline qualification remains
  insufficient under the unchanged gates.
- The first new forecast-detail test assertion used unconverted Arabic digits;
  it now uses the shared AppDigits display policy and asserts the full text is
  displayed after scrolling. Validation was retained; all nine device cases passed.
- Private inputs and generated private reports remain outside git/CI. Published
  artifact regeneration uses only the repository's existing reviewed corpus.

---

# Continuous local learning checklist

- [x] Family personalization and held-out templates.
- [x] Recurring features, classifier, fitting and held-out series.
- [x] Analytics migration/archive 4 and durable feedback.
- [x] Workflow integration, qualified identities and feedback UI.
- [x] Model participation/readiness explanations.
- [x] Private data tools and reproducible seed evaluation.
- [x] Focused tests, build, architecture/lint checks and documentation.
- [x] Local device checks recorded; full PR CI remains required before merge.


## Verification and remaining activation data

- 46 ml-assist tests and 472 scoped app tests passed, including recurring feedback,
  forecast/persistence and package boundaries; 10 Python training tests and 10
  device-suite registry tests passed. Both seed reproducibility checks passed.
- Debug and instrumentation APK builds, Android lint and app detekt passed.
- API 36: four advisory lifecycle/migration/restore tests, recurring correction/undo
  UI, forecast financial parity/draft/reopen UI and learning/settings/catalog UI passed.
- Consented backup probe runs production analysis in separate storage and verifies
  the financial ledger fingerprint is unchanged. Private data stays outside git/CI.
- The supplied history covers 89 days/two complete salary periods; its six confirmed
  recurring series split into four training/two held-out series. Of 73 unambiguous
  reviewed message candidates, cleaning accepts 29 records/10 distinct templates.
  These are insufficient for activation. Forecasts use WEEKDAY_MEDIAN (NEEDS_HISTORY),
  recurrence uses RULES, and no statistical message family is enabled. Exact human
  memory remains separate from statistical learning; no real-world accuracy is claimed.
- Full PR/main CI, including upgrade/process-death/device suites, is required before
  merge. Release CI is unchanged. No private input is committed or uploaded.

---

# Previous completed work: finance core extraction checklist

- [x] Freeze financial compatibility examples and schema contract; run baseline tests.
- [x] Move money, periods, time policy and identities; compile and test; commit.
- [x] Move financial classification policies and models; compile and test; commit.
- [x] Move matching, assembly and statement policy; compile and test; commit.
- [x] Enforce boundaries, static analysis and documentation; verify integration.

## Required remote verification

Run full PR/main CI including device upgrade and recovery. The pull request checks
and linked Actions run are the execution record for the final commit.

Baseline verification: focused domain, compatibility, schema, statement and bank/module tests passed before extraction.

Foundation: finance-core tests/detekt, bank-engine tests, app compatibility/schema/architecture/time/statement tests passed.

Classification: finance-core tests/detekt, bank-engine tests and focused app assembly/ownership/model/architecture compatibility tests passed.

Assembly: finance-core tests/detekt, bank-engine tests, targeted app transaction/statement/dashboard/FX/analysis/architecture tests passed. Public nullable FX properties are captured in local vals for cross-module Kotlin smart casts; conditions are unchanged.

Final local verification: 91 finance-core and 56 bank-engine tests; focused app schema, architecture, persistence, backup/restore and dashboard tests; debug and instrumentation APK builds; lint and app/core detekt all passed. The deliberately injected bank-engine financial-policy call was rejected, then removed and the real suite passed. Independent review found no actionable issue. All 44 moved production files are byte-identical to the baseline.
