# M8.4 beginning أ¢â‚¬â€‌ executable production oracle; M6 paired device timing

Status: In Progress. Offline parity tooling plus one Beta-only classification correction. No app source, parser, rule library, reference source,
golden, database, production data or legacy deletion changes. No migration; Beta remains schema 4.
This PR is independently mergeable without the concurrent M8.2 matcher or M5/M10 ownership UI.

## Oracle provenance and scope

`lab/finance-core` now cross-runs the **actual unchanged production implementation** of
`MonthlyFinancialSummaryCalculator`, `MonthlyFinancialSummary`, `AccountFlowClassifier`,
`AccountOutflow` and `LoanRepaymentAttribution` against `FinancialImpactClassifier`. This replaces
neither the classifier nor the prior authored M8.1 matrix: it supplies a separately executed oracle.

The explicit `parity/oracle-sha256.properties` manifest pins 46 original main Kotlin source files
(calculators and their exact pure transitive models/helpers) at main `683a8f7`, plus two existing
fixture-source test files. Gradle validates SHA-256 of UTF-8 source with CRLF normalized to LF,
then copies only the main sources into `build/generated/m8-oracle`. Packages, declarations and
behavior are unchanged. The two original test files are **provenance only**, not copied or rerun;
their existing app CI remains the sole execution of those JUnit classes.

Generated oracle code is compiled on the **test** source set only. No production app dependency,
Android/Room dependency, runtime calculator copy, financial stub, or boundary exception is added.
An original source/fixture change fails the task rather than silently updating approval hashes or
expected baseline output. Reviewing a deliberate manifest update is a separate review decision.

The cross-run fixture table retains amounts and scenarios from the pinned production
`MonthlyFinancialSummaryCalculatorTest` and `ForeignPurchaseSarConverterTest`, with explicit
confirmed Beta ownership and debit/credit channel evidence to exercise the matching input contract.
These are authored synthetic resolved fixtures, **not real SMS, an independent holdout, or bank data**.
Vocabulary adapters only translate types/endpoints and flow category names. Expected financial
results are obtained by calling the original calculators at test runtime, not by a second authored
table of monetary expectations.

Six new cross-run tests check:

- 20 resolved cases, each independently and as an aggregate, across 11 monthly metrics; empty
  batches and a refund-heavy negative-net batch preserve zero and signed accounting.
- Original account flow category decisions versus candidate account movement buckets, including
  debit-linked refunds, credit refunds, primary/supplementary purchases, salary/ordinary inbound
  transfers, settlements, loans, fees, bills, withdrawals and both internal transfer legs.
- Original `AccountOutflow.coreTotal` versus `ACTUAL_ACCOUNT_SPENDING`, per case and aggregate.
  Refunds remain inflow and do not net against this gross account spending indicator.
- Original loan attribution versus candidate settlement, including financing `FEE` and an explicit
  stored `BILL_PAYMENT` overriding stale financing evidence. This is **effective stored-type
  precedence**, not M8.3 correction-overlay/reparse/rollback parity.
- A discovered Beta defect is fixed: the production salary policy always treats an explicit stored
  `INCOME` as salary, whereas M8.1 had let `IncomeAttribution.OTHER` override that stored type.
  The classifier now preserves the original salary bucket for either INCOME attribution. An added
  cross-run case and the existing typed-income regression verify this. External inbound transfers
  still use their explicit salary/ordinary attribution; input facts and the conservative missing-
  attribution review gate remain unchanged. No original oracle, app source or expected golden changes.
- Four existing USD/EUR/GBP conversion fixtures execute the original converter. Candidate native
  amount/currency and full persisted FX precision/source are preserved; the report explicitly says
  `FX_AGGREGATE_PARITY: BLOCKED_NATIVE_ONLY_CANDIDATE`. The original SAR aggregate and the
  native candidate are never compared as equivalent currencies or used to claim FX closure.

Run `:lab:finance-core:test :lab:finance-core:verifyPureJvmDependencies`. The safe synthetic summary
is `lab/finance-core/build/reports/m8-parity/summary.txt`. Assertion failures fail ordinary CI.
The report identifies remaining gaps rather than assigning a misleading full-ledger pass.

## M6 device timing

The existing `SharedEngineDeviceMeasurementTest` is extended in place, once in the existing
LabAndroid connected-test suite on the single latest-stable emulator. No extra job, emulator,
matrix, test invocation or duplicated original test suite is introduced.

Both preserved `LabResultProjection.baseline` and `SharedLabEngine` receive identical synthetic
body, `ALJAZIRA` sender, ID, received timestamp and device environment. Each includes routing,
parse, validation/finalization and projection to `LabOutcome`. Each engine first establishes its
own expected output, then runs five warmups and 25 measured calls. Reference-first and
candidate-first order alternates in warmups and measurements to reduce ordering bias. Each has
31/31 deterministic-outcome assertions, outside the monotonic timing interval.

Output records both engines' p50/p95/max and statuses, API/ABI, engine/library versions, fixed
scenario labels and the measurement scope, without SMS or financial facts. Cross-engine semantic
equality is deliberately not inferred from repeatability or timing. The max dynamic configuration
is **asymmetric stress**: the reference is a fixed one-bank parser while the candidate accepts
100-bank configuration. It is not equivalent work or a fair speed-ratio claim. The other scenarios
use the one-bank default candidate configuration and the same preserved fixed reference.

This establishes contemporaneous reference/candidate device timing, **not** an approved M0 budget,
physical-phone latency, real-world precision, statistical performance acceptance or promotion.
Existing instrumentation artifacts retain the safe paired timing in per-test logcat.

## M6 gates that remain open

The unmodified regression gate still detects `collision_statement_amount_ar_001`: the candidate
extracts the explicit ISO due date and the reference only recognizes slash dates. A valid new
observation is not silently nulled to force parity, and this PR does not approve the extension.
Full-field review remains necessary even when financial Success results agree.

The checked-in corpus is development/regression data. No independent reviewed real holdout or
reviewed acceptance policy is introduced or fabricated. `:lab:engine:assessM6Holdout` remains an
explicit local fail-closed measurement command when those private prerequisites are absent.

M8.4 remains open for durable Beta ledger and transaction identity/revision/evidence parity,
correction overlays/reconciliation, complete facility/loan/dashboard aggregates, FX resolution
precedence and international fee/refund application. `CREDIT_PURCHASES` as a facility aggregate
is not independently certified by the monthly/account oracle here. No posting authority or
production cutover follows from these tests.

## Verification

Local and final-head CI/device evidence will be recorded in the PR before review. APK assembly
alone is not device execution evidence. The PR remains a draft and must not merge automatically.
