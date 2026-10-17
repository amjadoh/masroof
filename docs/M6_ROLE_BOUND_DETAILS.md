# M6 follow-up — secondary facts and payment roles

Source: [ADR-19 Clean Beta](https://app.notion.com/p/3fc44ee65d118130899bc774b292e434), edited 2026-10-17.
Base: `8d9d003f326be090ca3d87d222524a5600e93875`, after the authorized merge of #286.
This continues the independent engine audited in `M6_INDEPENDENT_ENGINE.md`. It does not complete the M6 acceptance gate or switch Beta intake/financial authority.

## Implementation

- `interpretation/MoneyGrammar.kt` owns the single bounded money grammar used by principal, balance, due, fee and foreign roles. The grammar is moved from the existing independent interpreter, not copied from a legacy parser. Unsupported currency/precision/signs cannot be silently rounded into valid money.
- `interpretation/DetailExtractor.kt` implements the existing schema-v1 detail vocabulary: outstanding amount, foreign amount, counterparty, transaction reference, biller, biller code and due date. It also reads explicit international-fee and exchange-rate role lines through stable generic grammar versioned with the engine. No new schema key/version or storage format is introduced.
- `RuleInterpreter.kt` now executes that detail vocabulary and supports card settlement, bill payment and financing installment. These require a positive principal, explicit transaction time and source account; card settlement also requires a card, a bill requires biller/code, an installment requires a financing counterparty. Facts do not assert owned assets, loan type, matched transfers or ledger eligibility. Incomplete evidence stays REVIEW_REQUIRED.
- `rules/RuleLibrary.kt` adds a revision-two `current()` profile by extending the existing revision-one profile; one interpreter executes either profile. No second parser/default algorithm is added. Revision-one fixtures remain regression coverage.
- `RoleBoundDetailsTest.kt` adds synthetic annotated development cases for all new roles, Arabic/Persian comparison mapping and original UTF-16 evidence, role conflicts, currencies, invalid dates/rates/precision, missing payment roles, quote/security/non-execution guards and field injection. These are not an independently reviewed holdout or a general accuracy benchmark.

Every amount keeps its own explicit currency. The engine never computes a SAR equivalent, totals fees into the principal or derives an exchange rate. A missing amount/rate remains unavailable; known zero due/fee stays zero, while zero principal/rate is rejected. A SAR value cannot fill the explicitly foreign role, and a foreign/principal pair in the same currency with different amounts is held for review.

Role values must be bound to a recognized line. Principal/balance/identifier extraction is also line-bound, so merchant/reference text cannot inject principal money or an asset. Secondary ranges are reserved before principal extraction. Longer configured labels take precedence over shorter prefixes (Biller code versus Biller); one original span cannot authorize two different roles. Malformed known secondary lines produce review rather than being ignored. Original human text and evidence ranges are retained; codes/amounts use normalized comparison values only where their contract requires it.

Due date is not a transaction timestamp. A statement/due-date context mixed with movement wording remains review; an informational balance/statement fact does not create a principal or an executed movement. Fenced/quoted message blocks cannot be accepted as executed money. Existing OTP, sender-collision, rejected/pending and missing-proof guards still apply to the newly supported families.

## Protected state and validation

Only bank-engine implementation, literal profile, tests and this document change. No `app/`, `ui-kit/`, `lab/beta/`, `lab/engine/`, Finance Core, schemas, preferences, archives, ledger or historical corpus modifications. No device/user database is opened, reset or imported. Existing Beta data and Production data are untouched. No migration, old-code deletion, new module, model training or release occurs.

The final-head PR records local/full-CI results and actual downloaded report digests. Production golden expectations and all existing CI gates remain unchanged. The native suites remain regression checks for the integrated build; no claim is made that these new pure rules have been activated in Beta.

## Remaining M6 work

- Role-bound transfer/network resolution and observation grammar (card/purchase channel, salary and loan-kind observations). Network/observation configuration and transfer context remain explicitly unsupported/review, rather than silently ignored.
- Independently reviewed corpus/holdout coverage and any additional real-message grammar gaps. This increment establishes facts and conservative safety behavior, not general bank accuracy.
- Prove new-message-only independent-engine operation through the planned archive/intake integration and coordinate later financial adoption. Beta's current baseline/candidate and legacy financial authority remain unchanged; no legacy-free Beta-runtime gate is claimed here.
- Preserve Beta records/annotations/decisions through any later archive/schema change. Old deletion stays deferred as instructed. No M7/M8 or other milestone is started in this PR.
