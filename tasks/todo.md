# Finance core extraction checklist

- [x] Freeze financial compatibility examples and schema contract; run baseline tests.
- [x] Move money, periods, time policy and identities; compile and test; commit.
- [x] Move financial classification policies and models; compile and test; commit.
- [ ] Move matching, assembly and statement policy; compile and test; commit.
- [ ] Enforce boundaries, static analysis and documentation; verify integration.
- [ ] Run PR/main CI including device upgrade and recovery; record results.

Baseline verification: focused domain, compatibility, schema, statement and bank/module tests passed before extraction.

Foundation: finance-core tests/detekt, bank-engine tests, app compatibility/schema/architecture/time/statement tests passed.

Classification: finance-core tests/detekt, bank-engine tests and focused app assembly/ownership/model/architecture compatibility tests passed.
