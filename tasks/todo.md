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
