# M6 — transfer, bank-network and observation grammar

Source: [ADR-19 Clean Beta](https://app.notion.com/p/3fc44ee65d118130899bc774b292e434), retrieved for this increment after merging #287.
Base: `88504def05d73cb5ac93847c5e52117d996da85a`.

## Implemented behavior

- The existing independent `RuleInterpreter` now supports incoming/outgoing transfers with explicit principal, source/destination account roles, transaction time and uniquely resolved bank network. Direction contradicting the transfer wording remains blocked, including custom profiles that remove the opposite family rule. A shared literal vocabulary supplies both default transfer cues and immutable safety-context checks.
- `TransferNetworkResolver` uses exact normalized caller-configured bank aliases, source/destination bank labels and network markers. It preserves sender-catalog ambiguity and never selects a bank by registration order, account last4, merchant name, selected import scope or partial bank-name match. Bank identities qualify observed account references; they do not establish ownership.
- Incoming remote source and outgoing remote destination identities remain unknown when only an explicit INTER_BANK marker is available; they are never replaced with the sender bank. INTRA_BANK explicitly binds both observed references to the local bank. Contradictory aliases/markers, wrong local-side bank, unknown names, missing network or missing account roles require review. No pairing, ledger key, conversion, totals or posting is performed.
- Optional standalone destination brackets require explicit configuration, a complete exact alias, an outgoing transfer title and adjacency to that title. Other bracket contexts are held for review. Unconfigured brackets cannot supply a network or bank identity.
- `ObservationExtractor` executes the closed observation vocabulary for card type (credit/debit/statement), purchase channel (POS/online), loan kind (personal/auto/mortgage), salary wording and the available-balance marker. Network observations are handled by the network resolver. Unknown/conflicting typed values or observations attached to the wrong family require review. Available balance never implies a credit card, absent salary wording is not false/zero, and a statement channel cannot authorize a movement.
- Observations come from explicit typed role lines, the recognized title or existing bound card/balance evidence. Merchant/counterparty/reference text cannot inject them. Original UTF-16 evidence and raw human strings remain intact; no guessed roles, ownership, ML confidence or receipt-time fallback is added.
- The family cue is bound to the first meaningful title, excluding role-field prefixes. A second independent movement cue still conflicts, while a family word inside a party field does not become authority. This is conservative canonical grammar; additional real-message layouts require reviewed evidence rather than legacy fallback.

## Reuse and exact source scope

`RoleLineReader` extracts the existing detail-line grammar into one shared implementation for detail facts, bank identities and typed observation headers; `DetailExtractor` delegates instead of duplicating normalization/role parsing. `SourceText`, `MoneyGrammar`, all contracts and schema version 1 remain unchanged. The current literal profile is revision 3 and the independent engine identity is v3; the revision-one profile remains regression input for the same interpreter. No module or parallel parser is added.

Changed/new implementation: `interpretation/{RuleInterpreter,DetailExtractor,RoleLineReader,TransferNetworkResolver,ObservationExtractor}.kt` and `rules/RuleLibrary.kt`. Tests: new `TransferNetworkTest.kt` / `ObservationRulesTest.kt` and targeted expectation updates in the previous independent/detail tests. This document is the only non-bank-engine change.

Three old capability expectations change because valid network/observation configuration is now implemented: unused configuration must not invent a fact, unsupported versions/keys/identities still fail closed, and incomplete transfers remain review for missing proof rather than an unsupported-family code. Existing money, no-fallback, security, currency, source-evidence and conflict assertions are retained. Initial local regressions exposed missing secondary movement-context conflicts; they were fixed, not removed.

## Validation / data safety

Local bank-engine suite: 44 test methods, including fourteen new methods with synthetic annotated positives and adversarial variants. Coverage includes both transfer directions, intra/inter bank identity, alias collisions, equal last4 across banks, immutable snapshots, literal/non-executable aliases, bracket scope, title/party injection, every observation category, malformed/contradictory fields, Arabic original ranges and security/non-execution/currency guards. These are development regressions, not an independent bank-accuracy holdout. Final-head CI and actual artifact/report digests are recorded in the PR after verification.

No changes to app/, ui-kit/, lab/beta/, lab/engine/, Finance Core, schemas, preferences, archives, ledger, golden fixtures or historical corpus. No physical device/user DB access, reset, import, migration, release or deletion. Existing Beta data and Production financial data remain intact. Old code deletion stays deferred as requested.

## Remaining gate

This completes the requested transfer/network/observation implementation increment, not the full M6/Beta runtime acceptance gate. Independently reviewed corpus/holdout coverage, additional real-message-layout gaps and proof of single-authority operation through the planned Beta intake/archive integration remain. The current Beta baseline/candidate path and legacy financial authority are unchanged. No M7/M8 work or model training/promotion starts in this PR.
