# Evidence engine experiment

This implements the staged lab plan: reference contract, independent evidence extraction,
relationships, competing interpretations, validation, paired persistence and review UI.
`SharedLabEngine` remains the default for existing lab runs and previews. `EvidenceLabEngine`
is available through an explicit paired comparison; neither output posts financial data.

## Runtime and isolation

- Engine code is under `engine/.../evidence/`, with no production imports or network/model dependencies.
- Bank profiles route exact sender IDs only. One knowledge library serves every bank.
- Extractors record original-text ranges, preserving Arabic display spelling, digits and leading zeros.
- Monetary values have roles: principal, balance, outstanding amount, fee and other amounts.
  A decimal is not sufficient evidence of principal; currency and a supported relationship are required.
  Exchange rates are retained separately at up to six decimal places.
- Reference suffixes require supported card/account/IBAN wording. A year-shaped number alone
  never becomes a date or card. Explicit credit wording does not identify the card network.
  A transaction date without time stays reviewable instead of inventing midnight.
- Field relationships do not depend on the winning family. Required-role conditions expand
  only from independently established relationships; cycles cannot justify themselves.
- Competing hypotheses retain support and blockers. OTP authority is built in. Conflicting
  directions/families, unexecuted states, malformed dates and ambiguous values remain reviewable.
  The three previous context conflicts remain UNKNOWN, including slash-form due-date context.
- Refund plus its quoted purchase can resolve as refund; fee wording accompanying a transfer
  is distinct from a fee-titled transfer, which remains reviewable. Multiple unresolved events
  do not produce multiple financial rows.
- The copied finalizer and structural validator still gate SUCCESS. Only this experimental
  engine supplies a local validation policy with `minFinancialConfidence=0`: its numeric
  score is deliberately not presented as a calibrated probability. Explicit evidence/consistency
  blockers, positive money, currency, references and time validation are still applied.
  Shared-engine and production policies are unchanged. Lab SUCCESS has no posting effect.
- Maximum input 16,000 characters, 200 knowledge rules, 30 phrases per rule and 512 matched
  evidence spans. Excess evidence fails to review before relationship expansion. Phrases are
  escaped literals; no executable expressions, user regex or scripts are accepted.

## Storage and reproducibility

Room migration 2→3 is additive in `masroof_parsing_lab.db` only. It adds versioned evidence
knowledge, comparison snapshots and paired comparison results. The existing migration 1→2
remains in the chain. Previous messages, rule versions and runs are untouched.

Each comparison captures the bank profiles, shared rules, evidence knowledge, both engine
versions, original samples and expected annotations. Both engines use the same snapshot.
Calculation runs off the UI thread. Cancellation/failure does not commit a completed comparison;
the header and all paired results commit atomically. First-encounter per-message times are
diagnostic only, not a controlled benchmark. Reset clears new tables within the lab transaction.

`ExpectedResult.absentFields` adds explicit reviewed absence. Legacy null optional fields
remain unreviewed. New annotation UI starts without parser-prefilled answers, allows explicit
direction and execution state, and validates conflicting absent/value annotations. Historical
results keep their original expected snapshots; corrections affect future runs only.

## Phone workflow

Settings → About → Developer settings → Bank SMS parsing lab:

1. Add a bank name and exact sender IDs.
2. Experiment: choose inclusive dates using the existing native date picker, then import.
3. Compare the two engines on saved messages, or open Results → Evidence comparison.
4. Expand a result for the highlighted original text, relationships, alternative hypotheses,
   both facts/statuses and timing. Review expected fields and rerun for scored results.
5. Rules → Evidence knowledge: choose a concept, edit literal phrases/exclusions, optionally
   require roles or allow the next line, then preview before saving a new version.

Knowledge preview evaluates all saved banks together (maximum 1,000 samples total), not just
the selected bank. It reports changed messages and improvements/regressions on reviewed
fields, retaining cases that improve one field but regress another in both categories. Unlabeled
changes are not counted as accuracy gains. Saving requires the previewed draft and current
version; edits invalidate the UI preview. Draft evaluation does not save runs or change rules.

No ownership registry is implemented: incoming does not mean income, and source/destination
suffixes do not establish that both accounts belong to the user. No ledger, reconciliation,
merchant spending-category model or production backfill is added.

## Offline comparison

The 70 previously studied messages are development/regression data. They are not a fresh
holdout after inspecting them during development. Existing fixture expectations supply 40
partial field references; the remaining 30 have family-only reference labels. Source authenticity
and unseen-bank generalization are not independently established. Baseline outputs never
manufacture expected answers. Explicit absence is exercised by unit tests and the manual UI;
missing optional keys in old fixtures are not promoted to absent annotations.

The comparator is the existing **shared lab engine**, not the production AlJazira parser.
Initial evidence defaults also include additional wording/relationships, so this compares
whole systems, not an isolated architecture-only causal effect. The optional v2 source-label
experiment holds engine code fixed and varies knowledge only.

Prepare local inputs (containing SMS text) outside Git:

```shell
python parsing-lab/experiments/evidence_comparison.py --prepare <private-directory>
```

After build/test activity finishes, set `LAB_BENCHMARK_INPUT` to `<private-directory>/cases.json`
and `LAB_BENCHMARK_OUTPUT` to `<private-directory>/measurements.json`, then run alone:

```shell
./gradlew :parsing-lab:engine:exportEvidenceBenchmark --no-parallel
python parsing-lab/experiments/evidence_comparison.py --input <private-directory>/measurements.json --manifest <private-directory>/manifest.json --output <public-results.json> --report <report.html>
```

The exporter writes the default knowledge to `measurements.json.default-knowledge.json`.
To reproduce a separate, explicitly post-error-analysis vocabulary experiment, add the two
source labels `من` and `رقم حساب المرسل` in knowledge v2 (the app default stays v1):

```shell
python parsing-lab/experiments/evidence_comparison.py --prepare-tuned <private-directory> --knowledge <private-directory>/measurements.json.default-knowledge.json
```

Set benchmark input/output to `cases-tuned.json` / `measurements-tuned.json`, run the same
export task alone, then add `--tuned-input <private-directory>/measurements-tuned.json` to
the report command. This changes knowledge data only; cases and expected answers remain
identical. It is development tuning on known cases, never a held-out accuracy result. Money
spans with explicit currency cannot also supply account/card suffixes at the same position.

Each case has five warmups and 25 measurements with alternating engine order. The report's
median/p95 summarize per-case median latency, not all invocations pooled. First-encounter
latency is retained but is not a fresh-process cold-start benchmark. The evidence workload
also produces explanations and candidate/relationship objects. Published results contain
IDs, hashes, families/statuses, differences, counts and timings, with no SMS or extracted values.

Scoring distinguishes family-only accuracy, exact agreement on reviewed fields plus status,
individual-field accuracy, accepted reviewed cases, critical accepted errors, and review rate.
Partial annotations cannot establish full-event correctness or absence of all critical errors.
No independent real-phone holdout is currently available; the report must state that limit.

## Validation

```shell
python -m unittest discover -s parsing-lab/experiments -p 'test_*.py'
./gradlew :parsing-lab:engine:test :parsing-lab:android:testDebugUnitTest :app:testDebugUnitTest
./gradlew :app:assembleDebug lintDebug detekt
./gradlew :parsing-lab:android:connectedDebugAndroidTest
```

The library's device tests run in `com.baraa.masroof.lab.android.test` and assert they are
outside the installed financial app. They exercise comparison/review/knowledge screens and
record paired Android-runtime timing on four synthetic cases. Their process PSS samples
include instrumentation and UI; they are not the engine's isolated peak memory or phone measurements.
The lab's connected test task runs after the app's connected task when both are requested,
so they cannot compete for the emulator foreground. CI publishes both instrumentation reports.

Acceptance remains experimental until independent reviewed templates demonstrate better
event correctness and accepted precision with coverage reported alongside it. Do not hide
regressions or call zero observed errors proof of zero risk. Automatic default replacement
or production integration requires a separate decision after this comparison.

## Recorded paired result — 2026-10-14

Public measurements are in `results/2026-10-14-evidence.json`. The 70 known examples have
40 partial fixture-backed references; the other 30 are scored for family only.

| Method | Family / 70 | Reviewed fields + status / 40 | Accepted reviewed / matching | Critical accepted rows on scored fields |
| --- | ---: | ---: | ---: | ---: |
| Shared lab rules | 62 | 17 | 15 / 9 | 5 |
| Evidence, default knowledge v1 | 62 | 22 | 17 / 11 | 4 |
| Evidence, source-label development tuning v2 | 62 | 26 | 17 / 15 | 0 |

The critical differences here are missing expected account references, not observed wrong
principal amounts or reversed direction among accepted cases. Unknown/unreviewed fields
are outside this count. Zero in the tuned development sample is not proof of safety or
independent-bank performance. Whole-message reviewed-field matching is partial annotation
agreement, never full financial-event accuracy.

On the 48 inline/archive source examples, family accuracy is 41/48 (85.4%) for shared rules
and 42/48 (87.5%) for evidence. Across all 70 the total remains 62/70 (88.6%): two previously
recognized inactive-beneficiary notices regress to UNKNOWN/review. Improvements in field
extraction do not establish a general classification improvement.

Desktop per-case warm medians: shared 0.802 ms, evidence v1 0.729 ms, tuned v2 0.750 ms.
p95 of per-case medians: 1.396 / 1.227 / 1.297 ms. The separate tuned run remeasures the
shared baseline too; the headline shared number comes from the default paired run.
Emulator API 30 x86_64, four synthetic cases: shared median 57.67 / p95 91.48 ms;
evidence v1 median 40.89 / p95 78.80 ms. These debug/instrumented emulator numbers must
not be presented as ARM-phone speed. Instrumentation-process sampled PSS is 121,999→154,051 KB,
including UI/test infrastructure and both engines, not isolated engine peak memory.

Validation on the final implementation: 386 engine tests, 21 Android lab JVM tests,
19 Python tests and two device tests pass; integrated debug APK, lint and detekt pass.
The separate full application suite on Windows ran 1,762 tests with 21 failures in backup,
SQLite reconciliation and update installation tests. No app source/build files differ from
main. Do not describe this local full-app check as green or assume the failures are proven
pre-existing; use the final commit's GitHub Linux checks as additional evidence. Previous
commit 9004daab was green in both CI workflows, which alone does not validate this addition.

Decision: retain opt-in comparison. Default v1 still misses source aliases; v2 demonstrates
knowledge-only correction without changing engine code, but remains a separate experiment
and is not silently installed into saved app knowledge. Obtain independently reviewed bank
templates and evaluate all required fields/absence before considering default replacement.
