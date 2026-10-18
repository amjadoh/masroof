# M6 — independent engine in Beta intake

Source: [ADR-19 Clean Beta](https://app.notion.com/p/3fc44ee65d118130899bc774b292e434), fetched for this increment. Base after authorized merge of #288: `67bd450ab937032e67421e28b7fb4ca0b319ba93`.

## Runtime and archive boundary

The default `BetaContainer` evidence path now routes through a literal full-catalog `SenderRouter` and calls `RuleInterpreter` exactly once. Sender identity configuration is separate from user-enabled import scope. Normalization reuses `SourceText`; a plausible sender/body can request review but cannot authorize a bank. All collisions remain ambiguous. No copied detector/parser, legacy comparison, shadow, selected-bank override or fallback is invoked by default intake.

`BetaInterpretationService.independent()` selects the new interpreter. Its constructor no longer creates a legacy interpreter implicitly. The explicitly named `sharedRules()` factory remains for historical regression fixtures only. The container accepts the existing evidence interface as an injection seam; historical ledger/backup/upgrade tests explicitly inject their prior interpreter to retain those regression assertions. The normal Activity constructs the container with the independent default. No UI toggle, preference, failed-parse fallback or automatic historical reprocessing selects the old implementation.

The existing Room schema v10 is retained. New outcomes put a single versioned interpretation in the existing `candidateJson` storage column, leave `baselineJson` as `{}`, and record `authority=BANK_ENGINE`, contract/rules/engine identity and original UTF-16 evidence. The column names are storage compatibility, not two sources of current truth. `INTERPRETED` means interpreted evidence, never financial approval. Previously saved outcomes, raw bodies, manual decisions, assets, preferences and ledger rows are not rewritten on entry. Reimport remains idempotent; only an explicit reprocess replaces an outcome and retains its raw evidence/manual decision and existing financial hold behavior.

Current asset proposals use this one current archive with exact engine/rule identity and original role proof. Old archives keep their prior guarded historical discovery behavior. Ownership still requires explicit confirmation. Integration exposed that typed card/loan/etc. observations retained their header span but lacked a category evidence span; the extractor now emits that original span too. Interpreter version is bumped to v4; rules revision remains 3. Available balance still never implies a credit card. Device discovery fixtures now state card type explicitly instead of relying on that retired inference.

## Financial boundary and retained work

This is M6 interpretation activation, not M8 financial adoption cutover. New archives do not satisfy the old baseline financial reader and cannot post through it. No archive is forged as a successful legacy baseline to preserve posting. Existing financial records/decisions remain intact; no new current interpretation silently replaces their authority. New current-archive financial approval, dashboard financial cutover and the richer M7 correction/archive UI remain their planned work. ML stays off. Old parser/adapters/dependency remain dormant for historical tooling because deletion is explicitly deferred.

## Independent evaluation and its limits

The evaluation reads only the 40 annotated JSON fixtures in `app/src/test/resources/testdata/bank_aljazira` outside `reference/`. These predate M6; their last source change at this base is `111002630e33f6ec6646f625938cb8ed8ddff84b`. No Production parser, database, provider or device messages are opened. No expectations are generated from a parser or changed to fit this engine.

The sorted relative-path + SHA-256 manifest is frozen at `37f77445edd69905393428bb1a4848d884ffddaea6c70d348fa40e4e6479a35b` (UTF-8 source with CRLF normalized to LF). The test reports every sample's status, field differences and diagnostic codes in `lab/beta/build/reports/beta-interpretation/independent-corpus.txt`, uploaded by the existing CI reports artifact. It checks accepted financial core fields against annotations, records optional missing fields rather than pretending exact parity, rejects wrong non-null extracted values, and checks all original ranges.

Initial evaluation: **4 SUCCESS, 5 NON_FINANCIAL, 31 REVIEW_REQUIRED; zero unsafe financial successes** against those annotations. One successful purchase lacks its merchant, which is recorded as missing, not exact field parity. The large review count is a real coverage limitation: the canonical generic grammar does not yet accept many prior bank-message layouts. No rules are tuned from this split in this PR. This is retrospective evaluation independent of engine outputs, not a new blinded, template-isolated or human-supplied phone-message holdout and not a general accuracy estimate. A fresh reviewed real-message holdout is still needed before claiming broad acceptance/accuracy.

## Files and verification

New engine files: `interpretation/SenderRouter.kt`, `SenderRouterTest.kt`. Engine edits: `ObservationExtractor.kt` category role evidence and `RuleInterpreter.kt` version.

Beta runtime edits: `BetaContainer.kt`, `BetaInterpretationService.kt`, `LabEvidenceComparison.kt`, `EvidenceModels.kt`, `InterpretationArchive.kt`, `ProcessBetaEvidence.kt`, `RoomBetaEvidenceStore.kt`, `BetaAssetDiscovery.kt`, `BetaHistoryPanel.kt`, Arabic/English `strings.xml`. No UI redesign or new financial calculations.

New tests: `IndependentCorpusEvaluationTest.kt`, `IndependentBetaRuntimeTest.kt`, `IndependentBetaRuntimeDeviceTest.kt`, and shared test-only `runtime-verification/kotlin/.../IndependentEngineChecks.kt`. Checks cover independent default provenance, review/security/no posting, original spans, idempotency, reopen, backup/restore, inert historical records/manual decisions and bank-qualified asset proof. Existing finance/aux/dashboard/backup/upgrade fixtures explicitly retain historical interpretation; assertions are not relaxed. Beta-internal Room checks are shared only between Beta JVM/device tests; the separate signed-upgrade host retains its existing verification sources.

Local and final-head CI results, actual reports, artifact digests and any regression fixes are recorded in the PR after verification. No migration, destructive fallback, data reset/import or release operation is introduced. Synthetic test resets run only in isolated test contexts. Production app/UI-kit/Finance Core/old engine source, golden fixtures, device/user data and old code deletion are outside this change.

Initial CI exposed a lifecycle race in `BetaSettingsActivityTest`: after retry/recreation, its wait predicate required a Compose root before the asynchronous activity had installed the new content. The Beta activity wait predicates in settings/start/shared-navigation tests now use `atLeastOneRootRequired=false` while waiting for a specific tag. They still require that tag before the same deadline and retain all subsequent UI/data assertions. No test is disabled and no runtime behavior is changed by this fix.

A subsequent device run passed that recovery check but exposed a separate controls-test window race: it clicked `controls-confirm` immediately after Apply, before the confirmation dialog existed. Controls tests now wait up to 10 seconds for that specific dialog button, assert it is displayed and still perform the real touch. The distinct bank-qualified facility values, single-save, busy, conflict and cancel assertions are retained.
