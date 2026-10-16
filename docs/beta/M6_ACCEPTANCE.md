# M6 — acceptance measurement and Android timing

Status: In Progress. This increment adds an executable offline measurement gate and device timing to the existing shared candidate. It changes no parser semantics, production app, database, rule defaults, reference sources, corpus, golden expectation or financial authority. Legacy deletion remains deferred.

## ISO due-date decision

The remaining `collision_statement_amount_ar_001` difference is not a demonstrated candidate defect: the shared extractor explicitly recognizes ISO dates, whereas the preserved reference due-date extractor recognizes slash dates. Silencing a correctly observed ISO date merely to reach 40/40 would hide an unapproved extension. The candidate observation and the full-field regression gate remain unchanged: 39/40 exact, 27 candidate Success results with zero Success differences, one non-financial date difference. This increment does not approve that extension. The assessment tool also blocks an informational/date difference; absence of a critical accepted error alone is insufficient.

## Offline reviewed-holdout assessment

Run explicitly with JDK 17:

```text
./gradlew :lab:engine:assessM6Holdout
```

Without `M6_REVIEWED_HOLDOUT`, the task **fails intentionally** and writes a safe `lab/engine/build/reports/m6-acceptance/summary.txt` identifying the missing independent reviewed real data and current candidate source/library fingerprints. It is not added to CI until that private input and an independently reviewed measurement policy exist. Ordinary unit tests test the evaluator with authored synthetic inputs; their success is not measured real-world precision.

For actual local evaluation set `M6_REVIEWED_HOLDOUT` to a private JSON manifest and `M6_ACCEPTANCE_POLICY` to a separately reviewed JSON policy. Keep these files outside the repository and report/artifact directories. Neither is copied into output. Input JSON is strict and bounded to 10 MB, with at most 20,000 cases and 16,000 UTF-16 units per body.

Manifest schema 1 requires:

| Field | Meaning |
|---|---|
| `provenance` | Exactly `REVIEWED_REAL_SMS` for measurement eligibility |
| `split` | Exactly `INDEPENDENT_HOLDOUT` |
| `frozenAt` | ISO Instant recording the declared frozen data split |
| `reviewReference` | Independently inspectable source/split review record |
| `engineSourceSha256`, `librarySha256` | Exact fingerprints printed by the task for the evaluated candidate/default library |
| `cases` | Reviewed cases containing `id`, `sender`, `body`, `receivedAt` ISO Instant, `banks` (`BankProfile` JSON), `expectedStatus`, `expectedFacts`, `reviewer`, `reviewReference` |

Every `expectedFacts` field must be explicitly present, including nulls, using the complete `LabFacts` serialization schema. Missing fields cannot become wildcards. Financial Success annotations require a financial family, direction, positive amount and currency. Cases must have unique IDs; duplicate normalized message content is blocked even if IDs or senders differ. Content hashes use the shared normalizer, Arabic folding/digit normalization, case and whitespace folding. Comparison against the unchanged development corpus also uses body-only hashes, preventing a changed sender from disguising that known overlap. This detects exact/normalized reuse, **not paraphrasing, template leakage or an untruthful provenance declaration**. Human source/split review remains necessary; this tool cannot authenticate a declaration or a reviewer.

The separate policy requires `reviewReference`, positive `minimumCases` and `minimumFinancialCases`, and finite fractions in [0,1] for `minimumAcceptedPrecision` and `minimumFinancialCoverage`. There is no invented default target or sample count. A policy filename/declaration does not itself prove approval.

Measurements compare status and **every `LabFacts` field**, including unexpected additions and nulls. Decimal representation is deliberately exact: `10` versus `10.00` remains a reported conservative difference. No allow-list suppresses field differences.

- Accepted precision = exact reviewed status/all-fact Success matches ÷ all candidate Success outputs.
- Exact financial coverage = exact Success matches ÷ reviewed financial Success cases.
- A candidate Success with any status/fact difference is a critical accepted error and always blocks, regardless of configured thresholds.
- No candidate Success means precision is undefined, not 100%; no reviewed financial cases means coverage is undefined. Either blocks.
- A financial message sent to review/partial/invalid/unsupported reduces coverage; a reviewed coverage threshold may permit these abstentions. Every difference remains in the report. Silently classifying a reviewed financial message as non-financial is not an allowed abstention. Other unapproved status/fact differences, changed candidate/library fingerprint, development overlap, duplicate message, missing policy or insufficient reviewed cases block the measurement gate.

Output contains input/policy/source/library SHA-256, aggregate counts, precision/coverage, per-family exact counts, and per-message content hashes with status and differing **field names**. It contains no sender, body, IDs, extracted financial values, reviewer or review-reference strings. Invalid-input exceptions are replaced without retaining parser exceptions that could quote private JSON. Each invocation replaces the previous report first, so a failed run cannot leave an old green artifact. Treat even hashes as private dataset-derived metadata when sharing artifacts.

`Measurement gates met: true` means only these measurement prerequisites pass. It never authorizes parser promotion, ownership, reconciliation or posting. Independent provenance review, unresolved ISO semantics, performance budget approval, broader supported formats and an explicit promotion decision remain separate requirements.

## Android device measurement

`SharedEngineDeviceMeasurementTest` runs once as part of the **existing** `:lab:android:connectedDebugAndroidTest` invocation on CI's single latest-stable Android emulator. It adds no job, matrix, emulator or duplicate test invocation. Four fixed authored synthetic scenarios cover purchase, security, unexecuted transfer and a bounded 16k/max-configuration input (100 banks × 20 aliases; 20 labels per role/network).

Each scenario records one expected outcome, five warmups and 25 measured calls. Parse plus rule validation is timed with a monotonic Android clock; equality assertions run outside the timer and require all 31 outcomes to match. The safe instrumentation stream/XML output records API, ABI, engine/library version, p50, p95 and max milliseconds with fixed scenario labels. No SMS or facts are printed or written to a database. Timing thresholds are intentionally absent until a comparable M0/device budget is independently established; correctness equality is enforced. Emulator measurements are device-runtime evidence, not physical-phone latency or a statistical performance acceptance claim. This source increment alone is not evidence that the device test has executed; PR CI supplies that result.

## Remaining closure gates

No independently reviewed real holdout or corresponding reviewed measurement policy was found in checked-in evidence. No comparable M0 parser-device budget exists in this increment. These gaps are now executable/visible instead of being inferred from regression success. M6 remains open; no production cutover or cleanup is included.

## Local verification

JDK 17 / Gradle 8.8: `:lab:engine:test` passed **418 tests**, zero failures/errors, including 15 new evaluator/exporter tests. `:lab:android:assembleDebugAndroidTest` and `:lab:android:lintDebug` passed. The explicit `:lab:engine:assessM6Holdout` command exited nonzero as intended, reporting `MISSING_INDEPENDENT_REVIEWED_REAL_HOLDOUT` without claiming metrics. Candidate source fingerprint remains `b27c88b75d5daaf06d56c2c971b6b4fd1bffcc577c82b6ebefdaa989a5395ec9`; library fingerprint remains `6e6763be92d34b4c342aed74bb53fdbfaac26f5af8002788025f6d4b905a2964`, confirming unchanged runtime/default rules. Android timing execution is still pending PR emulator CI; building its APK is not device execution evidence.
