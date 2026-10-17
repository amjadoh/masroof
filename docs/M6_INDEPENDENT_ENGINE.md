# M6.1 — independent rules interpreter foundation

Source: [ADR-19 Clean Beta](https://app.notion.com/p/3fc44ee65d118130899bc774b292e434), edited 2026-10-17.
Base: `aca3ed498b44d94c56e167411666e54bc4117de3`, after user-authorized merge of #285.

## Audit and scope

`lab/engine/SharedLabEngine.kt` imports copied AlJazira classification/resolver, normalizer, OTP heuristics, validator and finalizer classes. Its `SharedContextRules` and `SharedNetworkResolver` also import copied rules/folding. `lab/bank-engine` previously contained contracts and literal schema only. Beta's `SharedRuleInterpretationAdapter` invokes that old engine; `BetaInterpretationService` compares it to `LegacyInterpretationAdapter`. `LabEvidenceComparer` stores both archives, and `RoomBetaFinancialAdoption` requires the legacy version/baseline JSON. These remain live dependencies after this PR; changing financial authority is not safe as an incidental constructor replacement.

This is the first small M6 implementation PR, **not completion of M6 or a Beta runtime cutover**. It provides a concrete `BankInterpreter` that executes without any legacy implementation. No new module, runtime toggle, database migration, UI, ML model, production change or cleanup is included. The new interpreter is intentionally not wired into `BetaInterpretationService` yet. Existing Beta records, decisions and ledger remain untouched.

## Files and behavior

- `lab/bank-engine/.../interpretation/SourceText.kt`: one comparison normalization implementation for this new engine, with original UTF-16 range mapping. Handles Arabic/Persian digits, combining marks, compatibility punctuation and supplementary characters. Raw merchant text comes from original ranges, not folded text.
- `.../interpretation/RuleInterpreter.kt`: actual implementation of the existing `BankInterpreter` contract. Uses the complete sender catalog, preserves unknown/ambiguous sender outcomes, snapshots caller configuration, records engine/revision/content-hash identity in every result, and returns original evidence. Logs are represented as codes/version metadata; no raw message or identifier is added to diagnostics.
- `.../rules/RuleLibrary.kt`: initial bank-neutral literal data for purchase/refund/withdrawal/fee/balance/login. It is not a bank detector and does not claim coverage of the historical corpus. This is the only default rule source for the independent engine; no copied parser is invoked or reproduced.
- `IndependentRuleInterpreterTest.kt`: synthetic annotated development examples and adversarial variants for amounts/currencies, role conflicts, missing time/card/source, OTP/non-execution/advertisements, sender collisions, Unicode source ranges, bank-neutral execution, immutable rule snapshots and unsupported configuration. These are development regression tests, **not independent holdout accuracy evidence**.
- `ModuleBoundaryTest.kt` and `bank-engine/build.gradle.kts`: recognize the existing approved neutral ui-kit dependency/import namespace, preserve the production-business prohibition, reject fully qualified legacy references in pure cores, and declare inspected external source/build files as test inputs. Previously the test cache could retain a passing source-graph result after external Beta source/build changes.

The first grammar supports labeled principal/balance money, card/account identifiers, original merchant text and explicit ISO local transaction time. Money roles are reserved before principal extraction. Unsupported currency/precision, duplicate conflicting values, missing positive principal/time/required identifier, overlapping asset roles and movement/notice conflicts require review. No receivedAt-to-transaction-time fallback, zero guessing, timezone inference, account ownership, pairing, balance totals or posting occurs. SUCCESS is only an interpretation result, never financial authority.

OTP guards run independently of editable rules. Rejection/request/pending/cancelled wording prevents executed money. Unimplemented transfer/bill/settlement/loan context is held for review, including when mixed with a supported purchase. Advanced schema details/observations/network grammar are explicitly rejected as unsupported, not silently ignored. Existing schema-v1 consumers are unchanged.

## Validation and protected state

Final-head local/CI results and downloaded report digests belong in the PR after verification. All existing regression suites remain enabled; no production financial golden expectations are changed. The two stale module-boundary expectations were exposed by running the pure engine suite against the M2-merged tree, rather than accepted from a cached result.

Only `lab/bank-engine` implementation/tests/build test inputs and this document change. `app/`, `ui-kit/`, all Beta source/data/schemas/preferences, `lab/engine`, `lab/finance-core` and all historical fixtures remain unchanged. No physical device or user database is opened. No destructive reset, import, release or main-CI wait is performed.

## Remaining M6 gate

1. Implement and verify the rest of the supported message families and role-bound detail/network grammar, including FX/fee/statement/due semantics. Preserve reviewed regression examples and add independently reviewed holdout evidence; no general accuracy claim from these initial synthetic cases.
2. Add safe single-authority Beta entry/archival integration under the planned M7 work, coordinate financial adoption under M8, and prove every new message takes only the new interpreter. The current baseline/candidate path still exists; this PR does not satisfy the no-legacy-runtime acceptance gate.
3. Preserve existing Beta archives/decisions/ledger through any future versioned archive/schema upgrade; no automatic reposting or conversion of old results. Remove old runtime only after zero-reference/data-safety gates and explicit scope authorization. The user's current deletion deferral remains in force.

No M3/M4/M5, M7/M8 or ML training/promotion is started by this increment.
