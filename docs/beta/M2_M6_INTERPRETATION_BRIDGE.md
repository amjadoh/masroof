# Beta interpretation bridge — M2 increment / M6 start

Status: In Progress. This increment is a Beta shadow/comparison API, not completion of M2 or M6 and not a production parser promotion.

The subsequent [M6 shared rule migration increment](M6_SHARED_RULE_PARITY.md) improves the same 40-fixture comparison to 35 exact matches, with four unresolved network observations and one ISO due-date difference. The results below describe the original bridge increment; the linked document records the newer enforced parity gate and validation.

## Executable path and boundaries

`BetaInterpretationService.sharedRules()` accepts an explicitly supplied `MessageInput` and `BankCatalog`, then compares:

- `LegacyInterpretationAdapter`: preserved `lab/engine` AlJazira parser → complete versioned M2 facts/status/confidence. The real detector and validator still decide routing and Success.
- `SharedRuleInterpretationAdapter`: closed literal `BankRuleSchema` → the same `SharedLabEngine` and `SharedRuleDefaults` source used by Parsing Lab → M2 interpretation.

No SMS inbox reads, database opens, corrections, ownership resolution, posting or financial writes occur in either path. There is no automatic invocation during production SMS intake. Candidate Success is a parser result only, never posting authority. Differences remain visible even when the candidate returns Success. The Beta application service is callable without UI or repositories.

The temporary dependency is `lab/beta → lab/engine`. `lab/bank-engine` and `lab/finance-core` remain pure Kotlin/JDK with their existing runtime dependency guards. Beta never imports or depends on `:app`. The copied parser is the comparison reference; this does **not** establish parity with the current production parser. No legacy source or database is removed.

## M2 field and evidence decisions

The legacy adapter preserves all ParsedEvent and ParsedEventDetails facts, including nullable/NEUTRAL direction, STATEMENT channel, bank-qualified references (UNKNOWN becomes absent BankId), FX currencies, statement dues, local time, loan type and salary wording. Invalid/draft-only results retain observed facts and remain Invalid. Finding codes/messages/severity and review/unsupported/non-financial reasons survive as typed diagnostics; internal mapper checks message/bank provenance before adaptation. Partial/review/invalid results carry validation blockers; unknown family/bank and sender collisions remain blocked. User metadata corrections are not an input to this API and cannot elevate its gate.

Legacy has no extraction spans. Its `source_message` evidence points to the entire original message and is explicitly not field evidence. Shared engine field evidence is converted from normalized offsets to original UTF-16 ranges; a strict normalized-text equality check fails closed if the mapping cannot reproduce the engine input transformation. CRLF, trimming/collapsed spaces, Arabic/Persian digits, Arabic decimal/group separators, NFC composition and emoji are tested. Neither folded comparison text nor guessed substring positions are used as original evidence.

The original Lab display projection omitted secondary amount currencies. Additive nullable `balanceCurrency`, `dueCurrency` and `feeCurrency` fields and confidence metadata fix transport omissions without changing extraction/classification/validation. Shared balance/dues currencies come directly from the engine's existing typed values; they never inherit the principal amount currency. Old stored snapshots remain readable with default-null fields. A missing secondary currency blocks successful adaptation rather than inventing a currency.

Additional optional M2 facts retain Lab transport observations: IBAN suffix, execution state, card kind/network, sender/recipient names, sender bank and account identifier. These remain observations, not ownership decisions. Contract v1 stays additive-compatible.

## M6.1 safe initial schema

The only operator is `CONTAINS_TOKEN_PHRASE`. Matching and label extraction use engine-owned escaped literals; there is no script, regex or executable code field. Schema version, revision, unique IDs, family/direction vocabulary and rule/phrase/label bounds are checked before execution. Security OTP classification cannot be replaced by a user rule. Unknown versions fail closed. This is a typed in-process schema, not a new external JSON importer or rule editor. It intentionally exposes only operators supported by the actual shared engine; broader schema evolution remains work.

## Reproducible evidence

Run:

```text
./gradlew :lab:finance-core:test :lab:bank-engine:test :lab:engine:test :lab:beta:testDebugUnitTest :lab:beta:assembleDebug
```

`LegacyInterpretationParityTest` reuses every checked-in AlJazira fixture without copying or editing the corpus. It checks annotated golden fields and independently checks all typed parser fields. Reflection enumerates every ParsedEventDetails property so future field additions cannot silently disappear. Draft-only Invalid, NEUTRAL/STATEMENT, unknown routing and sender collision cases extend fixture coverage.

`SharedRuleInterpretationTest` records every status/fact difference against that reference and verifies explicit OTP, rejected/pending/request, conflicting-amount, literal regex-looking term and cross-currency cases. Reports contain fixture IDs and differing field names, not SMS bodies:

- `lab/beta/build/reports/beta-interpretation/legacy-parity.txt`
- `lab/beta/build/reports/beta-interpretation/shared-shadow-diff.txt`

CI uploads these alongside Android unit test reports. Reported shared-engine differences are deliberately not hidden or approved as equivalent. They are the backlog for per-rule M6 migration. The known fixtures are a regression/comparison corpus; no independent reviewed holdout was supplied, so accepted precision/coverage and cutover eligibility are not claimed.

## Results of this increment

Local JDK 17 / Gradle 8.8: **429 tests passed** (394 preserved engine, 11 bank contracts/schema/boundaries, 9 finance contracts, 15 Beta). Beta debug build and lint passed.

The legacy adapter retained **40 / 40 fixtures with zero golden or full typed-field mismatches**: 27 Success, 8 NonFinancial, 5 ReviewRequired.

The existing shared rule engine is **not equivalent yet**: 9 / 40 exact status-plus-all-facts matches; 15 candidate Success results, **12 of those with reference status/fact differences**. Differences include amount, family/direction, references, card channel, local time and other fields. These observations block promotion; they are not approved deviations. The service only compares samples and never posts either result. See the uploaded generated report for per-fixture field differences. No independent holdout precision or coverage is claimed.

## Outstanding gates

- M2: durable interpretation/finance adapters, correction replay/idempotency integration, full dashboard read model and product acceptance remain outside this increment.
- M6: complete AlJazira rule migration and explained field differences; independently reviewed holdout; critical accepted error assessment; repeatable shadow performance; integration with shared Lab rule configuration; and an explicit promotion decision.
- Production: `masroof.db`, current parser/receiver, financial semantics and repository access remain untouched. `masroof_parsing_lab.db` samples/corrections and old engine remain intact. No live data import, migration, cutover or deletion occurs.
