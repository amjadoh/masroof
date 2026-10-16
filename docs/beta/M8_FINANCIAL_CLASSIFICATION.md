# M8.1 — Beta financial impact classification

This increment implements a pure classifier in `lab/finance-core`. It does not change `app`, the
production parser, production indicators, or either database. It does not import SMS, match
transactions, discover ownership, post ledger entries, or delete legacy code. No migration is needed.

## Boundary

`FinancialImpactClassifier.classify` consumes a resolved `FinancialType`, native `Money`, original
transaction key/revision/time/evidence references and explicit ownership endpoints. The caller supplies
confirmed registry entries and previously resolved financing-loan attribution. This API does not accept
SMS text, message-family wording, card suffixes as identity, or a parser result to promote. Bank message
family remains an observation in the bank contract; financial type and financial impact are separate.

An endpoint is explicitly `Owned`, `External`, or `Unresolved`. External never means "lookup failed".
Owned IDs must resolve to the exact registry key in the correct environment. Debit cards require a
confirmed same-bank account parent; credit cards require a confirmed same-bank facility parent.
Credit facility/card relationships do not infer ownership of a counterparty. A stable registry entry
is trusted as confirmed ownership; this increment does not implement the registry or its editing.

Classification first checks provenance, ownership, endpoint/type consistency and `PostingGate`.
Unknown type, ambiguous matches, dangling/wrong-kind parents, environment mismatch, unsupported
adjustment direction and missing evidence return `Review` without usable impact. Existing blockers
survive explicit corrections. Metadata-only correction cannot lift unsuccessful parse eligibility.
Only the existing explicit financial-correction authorities can satisfy that existing gate. Callers
must obtain these authorities from the correction workflow, never from an M7 acknowledgement or
shadow comparison. This API is not a validator/finalizer, parsing-success issuer or posting store.

The input collections are copied before validation. The returned input snapshot retains the amount,
currency, time/zone, revision, evidence IDs and full FX precision. No registry lookup or classification
can silently mutate those facts.

## Preserved financial definitions

These definitions deliberately preserve the current application's different indicators. They must not
be collapsed into one new definition of spending or cash flow:

| Case | Monthly spending gross | Account outflow / actual account spending | Credit purchases net | Settlement |
| --- | --- | --- | --- | --- |
| Account/debit purchase | amount | amount | zero | none |
| Credit purchase (primary or supplementary) | amount | zero | amount | none |
| Credit payment from owned account | zero | amount | zero | credit |
| Loan repayment / attributed financing fee | zero | amount | zero | loan |
| Ordinary fee / bill paid from account | amount | amount | zero | none |
| Cash withdrawal / external transfer out | zero | amount | zero | none |
| Account/debit refund | refunds bucket | zero; account inflow | zero | none |
| Credit refund | refunds bucket | zero; no account inflow | negative amount | none |
| Self transfer between distinct owned accounts | zero | excluded from portfolio outflow | zero | none |

Monthly `SPENDING_NET = SPENDING_GROSS - REFUNDS` can be negative. Monthly `NET_CASH_FLOW` retains
`INCOME + EXTERNAL_IN - EXTERNAL_OUT - SPENDING_NET`: it includes credit purchases/refunds and excludes
credit settlements, loan repayments and withdrawals. It is **not** a current-account balance movement.
`ACTUAL_ACCOUNT_SPENDING` retains `AccountOutflow.coreTotal`: POS, fees, bills, withdrawals, external
transfers out, credit payments and loan repayments. Refunds do not reduce that gross account-outflow
indicator; they appear as account inflow. Internal transfer legs are separate and excluded from it.

`AccountMovement` provides typed native-currency account legs, separate from the monthly metrics.
Income and external inbound transfers require explicit resolved `IncomeAttribution` from persisted
facts. The stored `INCOME` type always maps to the salary account bucket, preserving the existing
`SalaryIncomeHeuristics` precedence even when attribution says `OTHER`. External inbound transfers
use the resolved attribution to select salary or external-transfer inflow. Missing attribution stays
in review. The original M8.1 implementation incorrectly allowed `INCOME` + `OTHER` into the ordinary
income bucket; the executable M8.4 oracle exposed this and the Beta-only classifier is corrected.
The input attribution facts, stored type and monthly income/external-transfer metric are preserved.
Self transfer preserves one caller transaction identity with one inbound and one outbound account
leg. It never pairs similar messages, infers a second owner, or deduplicates transactions. Those
guarantees still require M8.2's matcher/posting implementation and ledger parity tests.

Financing attribution mirrors the existing narrow loan rule: stored `FEE` plus already established
financing-loan evidence has effective `LOAN_REPAYMENT`. A stored explicit different type, such as
`BILL_PAYMENT`, overrides the old financing evidence. Invalid or contradictory loan attribution
stays in review rather than silently becoming a fee. Same-bank financing attribution is required.

All metrics remain in the transaction's native currency, including foreign purchases with missing
FX. The classifier neither calculates a rate nor manufactures SAR totals. `AppliedFx` represents a
complete positive-rate/nonblank-source pair and retains its precision. A missing pair remains null.
Downstream projection must exclude unresolved conversions explicitly; rate selection, international
fee application and old-vs-new FX aggregate parity remain M8.4 work.

## Authoritative read-only sources and verification

The authored characterization matrix in `FinancialImpactClassifierTest` derives expected values from
these read-only sources. It is not a cross-run of the production implementation or a ledger oracle:

- `app/.../application/dashboard/MonthlyFinancialSummaryCalculator.kt`: gross spending, refund,
  transfer, withdrawal, settlement and financing-fee buckets.
- `app/.../application/dashboard/MonthlyFinancialSummary.kt`: signed monthly net cash-flow formula.
- `app/.../application/dashboard/AccountFlowClassifier.kt`: credit purchase exclusion from account
  cash, account/debit refund inflow, credit refund exclusion, separate internal transfer legs.
- `app/.../application/dashboard/AccountOutflow.kt`: unchanged actual account-outflow total.
- `app/.../application/dashboard/CurrentAccountSummaryCalculator.kt`: account/credit spending split.
- `app/.../application/dashboard/LoanRepaymentAttribution.kt`: narrow financing-fee attribution and
  explicit reclassification precedence.
- `app/.../application/dashboard/TransactionSarEquivalentResolver.kt`: complete persisted FX pair
  is authoritative. This increment preserves that pair and defers conversion rather than replacing it.

Tests include 17 matrix cases plus purchase/payment double-count protection, linked debit versus
credit refunds, explicit classification precedence, invalid financing evidence, stable self-transfer
identity and separate messages, suffix collisions, unresolved/foreign ownership, malformed parents,
automation/correction gates, missing evidence, negative refund-heavy spending, foreign native amount
and FX provenance, mutable caller snapshot isolation, and explicit salary/ordinary attribution.
No reference/golden fixtures are edited.

Run `:lab:finance-core:test :lab:finance-core:verifyPureJvmDependencies`. All existing finance contract
and dashboard-read fixture tests must also remain green. CI supplies the full repository checks.

Local verification on 2026-10-16: **44 tests passed**, zero failures/errors/skips, including 13 new
classification tests and all 31 existing finance/dashboard contract tests. The pure JVM dependency
guard passed. This does not substitute for the PR's full CI or later ledger/parsing/device acceptance.

## Remaining acceptance

M8 remains in progress. This is not a new runtime or full financial-parity claim. M8.2 must implement
ownership/matching/posting atomically with retry/deduplication and actual Beta ledger storage. M8.3
must implement correction overlays without rewriting raw evidence or granting authority through
metadata. M8.4 must compare complete old-vs-new ledger and corrections/FX/loan/card outputs using
offline fixtures. Dashboard runtime adoption and financial screen totals are not part of this PR.
Production data, production migration and legacy deletion remain outside this Beta increment.
