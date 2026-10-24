# Proposed personal spending and forecasting tasks

All tasks below are proposals; production implementation has not started.
Design and capability map: [PERSONAL_INSIGHTS_DESIGN.md](../docs/PERSONAL_INSIGHTS_DESIGN.md).

- [ ] A. Audit exclusions and current readiness (2–3 files/tools).
  - Accept: reason-grouped counts, production cleaner parity, no inferred labels.
  - Verify: feature export, private qualification report, unchanged ledger/schema.
  - Areas: `ml-assist/tools`, `FamilyFeatureExportTest`, private outputs outside git.
  - Dependency: #329 baseline; no model activation from this audit alone.
- [ ] B. Explain current forecast method/readiness (3–5 files).
  - Accept: existing result drives text/detail; error metric has horizon/sample count;
    absent checks show an honest empty state.
  - Verify: `AnalysisPresentationBuilder` focused tests and device detail journey.
  - Areas: `AnalysisPresentationBuilder`, `AnalysisForecastViews`, localized strings/tests.
  - Dependency: A; no numerical forecast changes.
- [ ] C. Build unusual-payment observations from scoped facts (2–3 files).
  - Accept: prior-only reference, scope isolation, explicit sparse/invalid abstention.
  - Verify: focused analytics tests for MAD=0, FX, refunds, dates and same-day ambiguity.
  - Areas: pure `ml-assist` calculator/test, existing approved fact model if needed.
  - Dependency: A; provisional heuristic reviewed before enabling in UI.
- [ ] D. Connect one observation to Spending and evidence (3–5 files).
  - Accept: at most three useful observations; exact drilldown; correction/deletion refresh.
  - Verify: application analytics tests, ledger parity and Arabic/English device journey.
  - Areas: `LabAnalyticsWorkflow`, snapshot/result, `LabAnalyticsScreen`, strings/tests.
  - Dependency: C; preserve current comparison and transaction navigation.
- [ ] Checkpoint B–D: inspect useful vs incorrect observations privately; build/lint,
  data-quality states and unchanged financial totals before expanding the feature.
- [ ] E. Improve existing spending-contribution explanation if needed (2–4 files).
  - Accept: same completed-day window, no duplicate observation or double-counted claims.
  - Verify: scoped-vs-full characterization and spending-insights tests.
  - Areas: `AnalysisSpendingInsightsBuilder`, `AnalysisSpendingCharts`, focused tests.
  - Dependency: D evidence; cut this task if current comparison already communicates it.
- [ ] F. Polish recurring review with evidence and explicit decisions (3–5 files).
  - Accept: save/editor, negative/dismiss/undo remain distinct; actual origin visible.
  - Verify: recurring persistence, lifecycle/device correction/undo and backup tests.
  - Areas: existing recurring workflow, Commitment UI, strings/tests.
  - Dependency: A; retain seed and personal qualification gates.
- [ ] G. Support reviewed structural message wording (3–5 files).
  - Accept: no personal vocabulary; version change and human-decision preservation.
  - Verify: cleaner/security/refund/negation regression, frozen evaluation partitions.
  - Areas: `FamilyInputCleaner`, feature export and focused regression tests.
  - Dependency: A audit; do not reparse posted evidence.
- [ ] H. Select useful distinct message review examples (2–3 files).
  - Accept: bank-qualified groups, current unresolved evidence, deterministic priorities;
    no guessed labels or confidence when seed is ineligible.
  - Verify: grouping, changed/revoked decisions, unsupported and security cases.
  - Areas: application interpretation workflow/result and focused tests.
  - Dependency: G; use existing review authority.
- [ ] I. Add optional small review session to existing settings (3–5 files).
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
- [ ] Final checkpoint: focused tests, lint/detekt, APKs, relevant device lifecycle,
  upgrade/restore/process-death and full PR CI; verify actual readiness against UI.

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
