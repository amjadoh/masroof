# Continuous local learning checklist

- [ ] Family personalization and held-out templates.
- [ ] Recurring features, classifier, fitting and held-out series.
- [ ] Analytics migration/archive 4 and durable feedback.
- [ ] Workflow integration, qualified identities and feedback UI.
- [ ] Model participation/readiness explanations.
- [ ] Private data tools and reproducible seed evaluation.
- [ ] Focused tests, build, architecture/lint checks and documentation.
- [ ] Device/CI checks recorded without claiming unrun checks.

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
