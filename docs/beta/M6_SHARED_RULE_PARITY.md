# M6 shared rule migration increment

Status: In Progress. Beta shadow comparison only; no production parser, inbox, database, ledger or promotion change. Legacy engine, fixtures and stored Lab samples remain intact.

## Shared execution and configuration

Both Parsing Lab and Beta call `SharedLabEngine` (`shared-3`) with `SharedRuleDefaults` revision 2. There is no candidate call to the reference parser and no duplicate engine implementation. The existing temporary location in `lab/engine` remains; moving the generic runtime into `bank-engine` belongs to a later architecture increment.

`RuleSpec` and the Beta rule schema add bounded literal maps with closed keys:

- Detail labels: due/foreign amount, counterparty, transaction reference, biller, biller code and payment due date.
- Observations: purchase channel, debit/credit/statement card channel, transfer network, salary wording and loan product.

Every literal is escaped by the engine, with the existing term/count/body bounds. No regex, script, ownership decision or posting operator is exposed. Unknown keys and oversized terms fail closed. A different bank can configure labels and observations without a new Kotlin bank parser; tests exercise that path.

Extraction now retains same-line English card/merchant fields, qualified Arabic card labels, time-first day/month/year dates and both account roles. Local timestamps stay local; no handset timezone or Instant is invented. Conflicting amounts, identities, channels, network evidence or dates block Success through the validation/finalization gate. OTP and pending/rejected/request gates remain enforced.

Debit markers override credit evidence. A bare card line without credit evidence does not imply credit; an explicitly labeled available balance plus a card reference can establish the legacy unlabeled credit form. A generic balance observation does not create asset ownership.

Transport additions are nullable/defaulted so old Lab snapshots remain readable. A regression test decodes a saved v1 JSON library without the new maps and proves that a quoted due amount never becomes the principal, including when the principal is absent and when currencies differ. The engine retains its secondary-money reservation firewall even when new configuration is absent. User-saved library revisions remain unchanged.

The Beta comparison still enumerates every `Facts` field by reflection; no comparable field is removed to improve reported parity. Newly transported fields include purchase/card channel, bank network, counterparty, biller, transaction reference, loan/salary observations, due date and foreign amount. A foreign principal's currency alone does not create `labeledForeignAmount`: that observation needs a configured label and has independent currency/evidence. Explicit separate foreign labels are reserved before principal extraction.

Source/destination bank scope uses explicit transfer network evidence. A counterparty account stays bank-unknown unless intra-bank evidence proves its scope; a generic outgoing title does not establish an external network or local counterparty bank. Neither bank scope nor matching suffixes establishes ownership.

## Evidence and limits

The fixture report remains `lab/beta/build/reports/beta-interpretation/shared-shadow-diff.txt`; it contains fixture IDs and field names, not message bodies. The immutable checked-in reference corpus is regression evidence, not an independent holdout. All reference sources and golden expectations are unchanged.

## Verified regression results

Local JDK 17 / Gradle 8.8: **431 tests passed** (395 engine, 11 bank contracts/schema/boundaries, 25 Beta). Beta debug assembly and lint passed. The reference adapter still retains all **40/40** golden and typed-field expectations (27 Success, 8 NonFinancial, 5 ReviewRequired).

| Comparison | Before | This increment |
|---|---:|---:|
| Exact status and every typed fact | 9/40 | 35/40 |
| Candidate Success | 15 | 27 |
| Candidate Success with any reference difference | 12 | 4 |

The regression gate requires exact status and **all** facts for every fixture outside five named unfinished migrations. The only remaining compared differences are:

- `transfer_in_salary_ar_001`: `bankNetwork` is UNKNOWN rather than the reference INTRA_BANK. Resolving the named sender bank requires reviewed bank identity/alias configuration.
- `collision_transfer_fee_line_ar_001`, `transfer_out_inter_ar_001`, `transfer_out_inter_ar_002`: `bankNetwork` is UNKNOWN rather than INTER_BANK. External counterparty bank scope stays unknown; named/bracketed bank routing semantics remain to port.
- `collision_statement_amount_ar_001`: the candidate retains an ISO payment due date that the reference's slash-date extractor does not expose. This is an observed additive difference awaiting review, not approved equivalence.

All four candidate Success differences are the unresolved network observation. The corpus has zero remaining amount/family/direction/reference/card-channel/purchase-channel/local-time differences. The four network differences are still financially relevant and block a whole-engine equivalence or promotion claim.

New negatives cover ambiguous network/purchase channels, contradictory and invalid date formats, debit priority (including statement/dues wording), insufficient credit evidence, unknown counterparty bank scope, and separate/conflicting foreign values. The existing OTP, pending/rejected/request, amount conflict, routing collision and original evidence suites remain green. Generic merchant label matching no longer splits identifiers such as `TEST_MERCHANT_POS`, and generic account labels no longer assign a destination suffix as the source.

M6 is not complete until independent reviewed holdout, critical accepted-error assessment, repeatable performance and explicit promotion gates are met. This service performs zero financial writes regardless of parser Success.
