# Local context comparison experiment

Opt-in developer experiment, entirely under `lab/`. Nothing imports it from
the Android app. It neither changes the active shared library nor writes any database.
No model downloads or network calls occur during evaluation; `download` is an explicit
separate command and sends no message data. Model weights and private results must stay
outside Git (the local `artifacts/` and `models/` directories are ignored).

## What is compared

- **Rules:** the actual `SharedLabEngine`, including normalization, full extraction,
  library validation, arbitration and finalization.
- **Model:** a frozen, quantized multilingual sentence embedding model with two authored
  semantic descriptions per family. Nearest-description classification, with fixed
  similarity 0.55 and margin 0.05 abstention thresholds. Not SetFit, not fine-tuned,
  not a field extractor. Similarities are not calibrated probabilities.
- **Hybrid:** retains rules authority for OTP and accepted rule results, uses model
  suggestions for unknown cases, and abstains on unresolved disagreements. Every
  parsing status is preserved; a suggestion cannot become a successful financial parse.

All three are scored on message-family classification only. Rules' expected-field
differences are recorded separately, never presented as model extraction accuracy.
Model and hybrid suggestions on review cases remain review-only. This conservative
hybrid does not repair accepted rule errors; it is one policy to measure, not an optimum.

## Reproduce

1. Create a Python 3.11 virtual environment and install `requirements.txt`.
2. Run `python context_benchmark.py download --model-dir <local-model-directory>`.
   Downloads the official Apache-2.0 model at pinned revision
   `e8f8c211226b894fcb81acc59f3b34ba3efd5f42` and records file hashes.
3. Run `python context_benchmark.py prepare --output <private-cases.json>`.
   Optional `--lab-db <consistent-read-only-lab-snapshot>` adds saved lab messages and
   their current library. Never pass `masroof.db`. If WAL is active, use a consistent
   SQLite backup, or stop the lab and copy its DB plus WAL/SHM together before opening
   the local copy. Keep that snapshot and all raw message outputs private.
4. From the repository root set `LAB_BENCHMARK_INPUT` to the absolute cases path and
   `LAB_BENCHMARK_OUTPUT` to an absolute private output path, then run:
   `./gradlew :lab:engine:exportContextBenchmark`.
5. Run `python context_benchmark.py evaluate --input <private-cases.json>
   --rules <private-rule-output.json> --model-dir <local-model-directory>
   --output <private-comparison.json>` (one command).
6. Run `python -m unittest discover -s lab/experiments -p "test_*.py"`
   and `./gradlew :lab:engine:test` for behavioral checks.
7. Run `python context_benchmark.py report --input <private-comparison.json>
   --output <local-report.html>` for an Arabic report without SMS bodies.

## Initial desktop result (2026-10-14)

Frozen source baseline: `7fd64053`. The public measurements are in
`results/2026-10-14-desktop.json`; no private lab snapshot or SMS bodies are included.

| Method | Correct family / 40 | Accuracy | Warm median ms | p95 ms |
| --- | ---: | ---: | ---: | ---: |
| Shared rules | 31 | 77.5% | 0.71 | 1.82 |
| Embedding classifier with abstention | 9 | 22.5% | 9.07 | 35.18 |
| Conservative hybrid suggestions | 26 | 65.0% | 10.08 | 35.84 |

Without the fixed abstention thresholds, the model's top candidate is correct on
17/40 (42.5%). No message was truncated. Model loading took 712 ms; prototype
preparation took 96 ms. These are a single desktop run with repeated per-message
timings, sensitive to CPU scheduling and warmup, not Android performance promises.

**Decision: do not activate this model or hybrid policy.** It confuses credit purchases
with repayments and one statement notification with a card payment. It helps suggest
a missing card-payment wording and one outgoing-transfer wording, but cannot safely
replace current rules. This result does not evaluate a fine-tuned SetFit classifier.

The comparison also exposes three current **lab** rule SUCCESS outputs where the
fixture expects UNKNOWN/review: `collision_purchase_statement_due_ar_001`,
`collision_titled_fee_transfer_ar_001`, and `collision_transfer_beneficiary_notice_ar_001`.
The hybrid retains those errors through rules authority. Current shared-engine unit
tests passing do not imply agreement with the entire copied specialist fixture corpus.
Only 16/40 cases match all represented annotated fields/status in this export; this
is separate from family accuracy and excludes unsupported reference-only fields.
The experiment deliberately leaves the baseline unchanged. The production parser
is neither measured nor modified by this experiment.

Next: add shared-engine regression coverage for these conflicts, establish independently
reviewed train/validation/held-out template and bank splits, then compare a trained
classifier and profile the selected Android runtime before considering activation.

## Interpretation and limits

- Corpus: 40 existing AlJazira fixtures, including conflict/OTP cases, plus any
  explicitly supplied lab snapshot. Reference/raw-body collections have no independent
  expected answers and are excluded from accuracy. Unlabeled samples are timed only.
- These fixtures were already used to develop rules; this is a regression comparison,
  not an unbiased held-out or cross-bank evaluation. Synthetic saved examples are
  reported as a separate dataset. Never claim production-wide accuracy from these.
- Cold model loading and description preparation are recorded separately. Warm timings
  include tokenization, model inference, pooling and similarity. Rules use 5 warmups
  and 25 measurements per message; model uses 1 warmup and 10 measurements.
- CPU inference uses one ONNX thread and max 128 tokens per message. Rules are not
  truncated. Report truncation counts; long SMS may lose meaningful model context.
- Desktop JVM parser and Python/ONNX classifier costs describe these implementations;
  they are not equivalent workloads or Android measurements. Hybrid time is an estimate
  summing both measured costs, not a directly measured integrated pipeline.
- No threshold tuning on evaluation labels. Before acceptance use a separate validation
  set to calibrate thresholds and a separate held-out bank/template test set, then
  profile the chosen Android model with its exact tokenizer and runtime.
- The current experiment exposes no new UI action or background SMS intake. App/lab
  flows continue using the existing shared rules until a separately reviewed change.

Model: https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2
Runtime: https://onnxruntime.ai/docs/get-started/with-mobile.html

## Follow-up: context guards and trained SetFit

`SharedLabEngine.VERSION = shared-2` adds paired context candidates for the three
conflicts identified above. They participate in deterministic classification arbitration;
OTP authority is preserved. SharedContextCollisionTest covers all three original
fixtures, reversed registration order, ordinary due-amount/beneficiary/fee-line cases,
English context and OTP conflicts. Historical runs and active library versions are not
rewritten. No production parser or database is changed.

SetFit is a separate opt-in experiment. Use a **separate Python 3.11 environment**:

1. Install `torch==2.6.0` from the official PyTorch CPU index, then
   `pip install -r requirements-setfit.txt`.
2. `python setfit_benchmark.py download --base-dir <local-base-directory>` downloads
   the pinned original weights/configs (not the quantized ONNX weights).
3. `python setfit_benchmark.py audit --corpus setfit_training.json --input <cases.json>`.
4. `python setfit_benchmark.py train --corpus setfit_training.json
   --base-dir <local-base-directory> --model-dir <local-trained-directory>`.
5. Repeat the JVM export with `shared-2`; retain the original shared-1 measurement file.
6. `python setfit_benchmark.py evaluate --corpus setfit_training.json
   --model-dir <local-trained-directory> --input <cases.json>
   --rules <shared-2-output.json> --before-rules <shared-1-output.json>
   --output <comparison.json>`.
7. `python setfit_benchmark.py report --input <comparison.json> --output <report.html>`.

Commands shown across lines should be entered as one line. Training and evaluation
set Hugging Face offline mode, use a workspace-local cache and disable experiment
tracking uploads. Model files and generated checkpoints remain outside Git.
Only a model generated by this trusted local experiment should be loaded: SetFit's
classification-head artifact is a local Python pickle.

### Data separation and frozen configuration

- **Train:** 78 manually authored synthetic examples, 6 per class across 13 classes.
- **Validation:** 26 separately authored examples, 2 per class; selects the abstention
  threshold from a fixed grid. No held-out labels participate in training/calibration.
- **Test:** all 40 original repository SMS fixtures, never read by the train command.
  These were already seen by prior rule developers and in the first benchmark, so
  they are held out from model training, not a blind/new-bank evaluation.
- Test separately reports the 3 fixtures used to implement the context fix and the
  remaining 37. A related beneficiary notice among the 37 benefits from the same fix;
  do not interpret that subset as completely independent of rule development.
- Cross-split audit masks digits, folds Arabic variants and rejects exact duplicates
  or character similarity >=0.85. Observed max train/test similarity is 0.549. This
  audit does not prove absence of semantic/template-family overlap.
- One frozen run: seed 20261014, 32 actual contrastive training steps, batch 8, max 128
  tokens, learning rate 2e-5, then logistic regression. Embedding body weights changed;
  parameter name and trained model hashes are recorded. Training took 42.7 seconds.
- Validation chose threshold 0.25, with 20/26 correct. Probabilities are not calibrated
  financial acceptance confidence. No retraining or threshold adjustment after test.

### Measured result

Recorded in `results/2026-10-14-setfit.json` (public fixture IDs and metrics only).

| Method | All 40 correct | Accuracy | Other 37 correct | Accuracy | Warm median ms, all 40 |
| --- | ---: | ---: | ---: | ---: | ---: |
| Rules before fix | 31 | 77.5% | 31 | 83.8% | 0.71 |
| Rules after fix | 35 | 87.5% | 32 | 86.5% | 0.98 |
| Trained SetFit | 13 | 32.5% | 11 | 29.7% | 29.18 |
| Guarded hybrid suggestions | 34 | 85.0% | 31 | 83.8% | 30.81 estimated |

The 3 conflicts now all return UNKNOWN/REVIEW_REQUIRED, compared with SUCCESS before.
SetFit labels 2/3 as UNKNOWN; the guarded hybrid retains all 3 conflicts for review.
Hybrid policy here differs from the initial experiment: known rule families and
explicit ambiguity/context guards retain authority; only genuinely unrecognized
messages receive review-only model suggestions. All parse statuses remain unchanged.
The model incorrectly labels the unknown-format case NON_FINANCIAL, causing the
hybrid's one-case loss; it is still not automatically ignored or posted.

**Recommendation:** review the context fixes independently; keep SetFit disabled.
This small synthetic-training experiment does not establish SetFit's best achievable
performance. The validation/test drop suggests domain mismatch and insufficient
representative training data. Next needs reviewed SMS-style training data and a new
template/bank-held-out evaluation set. No accuracy promise is supported yet.

All timings are desktop warm measurements. SetFit uses FP32 PyTorch CPU with 4
threads; initial MiniLM used quantized ONNX with 1 thread. Their latency difference
cannot be attributed to fine-tuning. Current SetFit load time is 1.71 seconds. Hybrid
latency is the sum of measured component costs, not integrated Android execution.

SetFit training: https://huggingface.co/docs/setfit/quickstart

## Bank-message source experiment with held-out template groups

SetFit is used for **message-family classification only**. It receives the SMS body as
text; it is not an SMS reader, field extractor, merchant expense categorizer or ownership
resolver. Amount/card/account/merchant/date extraction stays in SharedLabEngine.
Direction is currently derived from the family/rules, not independently trained. An
incoming transfer is not automatically income; financial interpretation needs ownership.

`bank_sms_dataset.py --prepare --output <private-directory>` reads the application test
files as offline research data (no production-code imports or runtime dependencies):

- 47 static SMS literals in AlJaziraParserRegressionTest, CreditCardMessageHeuristicsTest
  and ParsedEventDetailsFactsCharacterizationTest;
- 10 archived reference bodies, and 40 existing JSON fixtures;
- 97 source entries, collapsed into 70 examples and 19 conservative template groups.
  48 examples have an inline/reference source and are eligible for training. The 22
  fixture-only examples are **never trained**, including constructed conflict controls.

These are bank-shaped examples from the repository, including anonymized real-world
shapes and constructed/modified test cases. Do not claim verified phone capture or
fresh new-bank generalization for every example. No new prose teaching examples or
synthetic SMS augmentation is added. Labels come from reviewing the SMS/test assertions
and existing fixture expectations, never from predictions of the parser being measured.
Inline assertions that contradict the curated mapping fail preparation.

Normalization masks numbers, placeholder names and selected variable fields; it retains
context after merchant lines. Value-only duplicates collapse with aliases/provenance.
Archived source records take priority over equivalent fixtures. Manual semantic/layout
groups (e.g. wallet variants of Arabic POS purchases) are joined transitively with any
pair having normalized character similarity >=0.80. All copies/variants in a resulting
component stay in the same outer fold. This is conservative grouping, not proof of
perfect semantic independence.

Three deterministic balanced outer folds each test complete groups. For each fold:

1. Start a new model from the original pinned base, never another fold's weights.
2. Train only eligible sources outside that fold (28/34/34 messages).
3. Use the previous fixed training settings: seed 20261014, 32 steps, batch 8, max 128
   tokens, learning rate 2e-5 and logistic head. No hyperparameter search or test-based
   selection. `--fixed-threshold 0.0` deliberately measures top-1 type classification;
   it is not a financial acceptance threshold. No inner validation is used/tuned here.
4. Evaluate the fold's test cases with `--dataset bank_sms`. The optional old-baseline
   `--before-rules` argument is omitted. Each message's reported prediction comes from
   a model that did not train on that message or its template component.

The same source can train models in the other folds; aggregated results describe
cross-validation, not one final deployed model. Five rare types have only one template
component, so some folds have test classes absent from training. Report those cases in
the main result, plus a clearly labeled diagnostic restricted to covered classes.

To reproduce, prepare sources, export SharedLabEngine over `<directory>/all-cases.json`
using the JVM task, then for fold `N` (0, 1, 2) run each command on one line:

```shell
python setfit_benchmark.py train --corpus <directory>/fold-N-train.json --base-dir <original-base> --model-dir <model-N> --fixed-threshold 0.0
python setfit_benchmark.py evaluate --corpus <directory>/fold-N-train.json --model-dir <model-N> --input <directory>/fold-N-test.json --rules <directory>/rules.json --dataset bank_sms --output <directory>/fold-N-results.json
python bank_sms_results.py --directory <directory> --output <local-results.json> --report <local-report.html>
```

Run heavy training and latency measurement sequentially. `bank_sms_results.py` checks
each source has exactly one out-of-fold prediction and records row-weighted accuracy,
macro F1 and equal-template mean accuracy, with source-only and fixture-only results
separated. SMS text and model weights remain local; public manifests/results contain
source pointers, hashes, grouping and metrics only. No model is activated in the app.

### Frozen grouped result (2026-10-14)

Recorded in `results/2026-10-14-bank-sms-grouped.json`. No generated prose teaching
examples were used. Each row is scored with its out-of-template fold model, never
with a model trained on that group. Three fresh embedding bodies were fine-tuned for
32 steps each (62.6/59.3/63.7 seconds); original pretrained weights are reused as the
starting point, never the previous fold's trained weights. All cross-split normalized
text similarities are below .75; no input was truncated at 128 tokens.

| Method | Source examples correct / 48 | Accuracy | Warm median ms |
| --- | ---: | ---: | ---: |
| Shared rules | 41 | 85.42% | 0.69 |
| SetFit | 27 | 56.25% | 28.41 |
| Guarded hybrid suggestions | 44 | 91.67% | 29.28 estimated |

Equal-template mean accuracy on the 17 source-covered components is 79.9%, 39.8%,
93.6% respectively. On all 70 examples the methods score 62/70 (88.6%), 34/70 (48.6%)
and 64/70 (91.4%). On the 22 fixture-only cases, rules score 21/22 (95.5%) while
hybrid scores 20/22 (90.9%): the previously unrecognized-format case receives a wrong
review-only model suggestion. Do not hide this regression behind the overall gain.

The hybrid gains three source classifications: the credit-card settlement wording,
the newly accepted incoming-transfer wording, and a compact English online purchase.
The model also labels three external outgoing-transfer examples TRANSFER_IN. Those
are unsafe direction suggestions if reused automatically: the experiment changes no
stored direction, ownership, parse eligibility or ledger row. All remain review-only.

Six of 48 source examples have a class absent from that fold's training, due to rare
single-template types. On the other 42, SetFit scores 27/42 (64.3%); this is diagnostic
only and never replaces the full 48-example headline. Rules had prior development
exposure to these examples; this is template-held-out for model training, not a blind
comparison of equal training exposure. These figures are not directly comparable to
the earlier 40-example, synthetic-training experiment.

**Decision:** useful evidence for model-assisted review of unrecognized messages, not
for replacement or automatic financial approval. Preserve rules/guards and collect
additional independently reviewed templates and banks, especially rare types, before
calibrating confidence or testing Android performance. All model use remains opt-in
offline tooling in this draft experiment.
