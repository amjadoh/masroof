# M6 message-layout coverage in Beta

Scope: ADR-19 Clean Beta, following merged #289 (`c0b72dcbcb99bc357c4ba97f4eea075a7e0408e4`). Plan: https://app.notion.com/p/3fc44ee65d118130899bc774b292e434.

## Behavior

One generic interpreter now handles explicit time-first dates, role-bound inline English purchases, compound refund titles, split/same-line card-number roles, transfer/local-account layouts, financing principal labels, statement and beneficiary-directory notices, and additional security wording. Original UTF-16 evidence is retained through comparison-only inline segmentation. Strict dates have no receipt-time fallback. A longer role label wins over a shorter prefix; optional biller code is not fabricated. Principal, balance, due, fee and foreign amounts remain separate.

Network identities are exact caller configuration, including remote Riyad/ANB/SNB/D360 names, not bank-specific parser branches. Incoming source-bank brackets and outgoing destination-bank brackets have separate opt-in capabilities. Later brackets require the local account role and explicit party. Unknown/ambiguous aliases and contradictory networks stay reviewable. An incoming debit line is not silently a remote sender account. Same-line/split card references are checked against ordinary card references for conflicts.

Runtime engine is `bank-rules-independent-v6`, rule revision 5. Beta uses it once, with no old-parser prediction, fallback or learning from a parser output. Historical adapters reject the new source-bracket capability rather than silently ignoring it. Balance alone never proves credit-card type.

## Evaluation and limits

| Set | SUCCESS | NON_FINANCIAL | REVIEW_REQUIRED | Purpose / core labels |
| --- | ---: | ---: | ---: | --- |
| Known 40 development fixtures | 27 | 8 | 5 | Exact expected statuses; one optional credit-kind difference retained |
| 13 source challenge examples, after refinement | 7 | 5 | 1 | 13/13 purposes and selected core labels |
| 5 additional unique complete source messages | 4 | 1 | 0 | 5/5 purposes and selected core labels |
| 12 additional incomplete source snippets | 0 | 0 | 12 | 12/12 purposes and selected core labels; no invented time |

No unsafe automatic financial acceptance against the reviewed core labels was observed. These are retrospective repository examples, not fresh phone messages, template-isolated samples or a general accuracy guarantee. SUCCESS here is interpretation, not financial posting. Optional facts are not all covered by the core-label metric.

The known 40 were refined as development regressions. Their unchanged source manifest is `37f77445edd69905393428bb1a4848d884ffddaea6c70d348fa40e4e6479a35b`; fixture bodies/annotations remain untouched. Previous #289 reported 4 success / 5 nonfinancial / 31 review. Five remaining reviews are the four intentional collision cases and the unknown format. The unlabeled-card purchase keeps an unknown card kind despite the historical parser's balance-based credit inference.

The 10 reference bodies contain 9 exact whitespace-normalized duplicates of those 40. Only the new financing example was selected. Twelve additional unique source literals formed a 13-example challenge. Feature commit `ea24381` preceded reading/labeling/evaluating those challenge bodies; first evaluation was 3 success / 3 nonfinancial / 7 review (10/13 purposes). They then became development examples. The remaining review is a USD purchase whose international fee has no stated currency; the principal/card/type are understood, but fee currency is not guessed.

A second set of 12 snippets was reserved before this refinement and is frozen in `b0b120c`, which also freezes the layout grammar. Seventeen candidate literals from the regression source were reserved in `ffdd8f2` before their first evaluation. Explicit UTF-8 duplicate validation found that 12 were already in the known 40; those were removed, leaving 5 genuinely distinct complete source examples. A test now rejects exact whitespace-normalized duplication across all sets and the known 40. Source/index identity is stable; candidate literal hashes were corrected to explicit UTF-8 during annotation, before execution. Source text and original assertions supplied the labels, not parser predictions. No grammar was tuned to the final sets' outcomes. Subsequent review tightened conflict handling for duplicate ordinary/split card roles with a synthetic adversarial test; therefore the final published revision is not claimed as a fully blinded freeze. The final sets share historical templates with development examples.

Incomplete snippets deliberately omit transaction time (some also omit local account/network). Their REVIEW_REQUIRED outcome is asserted, while family and stated principal/card/account facts are still measured. A classifier-only fixture is not evidence that a full financial transaction is safe to adopt.

Reports, without raw SMS bodies, are generated under `lab/beta/build/reports/beta-interpretation/`: `independent-corpus.txt`, `new-source-challenge.txt`, `final-heldout-messages.txt`, `final-heldout-snippets.txt`. Manifests include source paths, literal indexes, hashes and human-reviewed core labels. Tests execute only the new interpreter; reading an app test fixture does not execute the app parser. Gradle declares all referenced source fixtures as test inputs. Existing CI uploads these reports.

## Isolation and verification

All runtime edits are under `lab/bank-engine` and `lab/beta`. No app runtime/parser, Finance Core, device inbox/database, Room schema/migration, reset, release or deletion is changed. Existing Beta raw records, new data, decisions and financial rows remain intact. There is no automatic historical reprocessing. Exact engine/rule archive identity means older outcomes require explicit reprocessing before becoming current; their stored data is preserved. M7 learning/corrections and M8 financial adoption are not added here.

Pure tests cover cross-bank generic layouts, original Arabic/emoji spans, strict invalid dates, party/time injection, compound-family collisions, directory metadata/security, contradictory card references and incoming source-bracket role/alias/local-account proof. Existing Beta storage, backup/reopen and independent-runtime device checks remain. Local/CI verification and final-head artifacts are recorded in the pull request; no tests or golden expectations are disabled or rewritten to match parser output.
