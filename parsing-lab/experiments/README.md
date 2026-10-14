# Local context comparison experiment

Opt-in developer experiment, entirely under `parsing-lab/`. Nothing imports it from
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
   `./gradlew :parsing-lab:engine:exportContextBenchmark`.
5. Run `python context_benchmark.py evaluate --input <private-cases.json>
   --rules <private-rule-output.json> --model-dir <local-model-directory>
   --output <private-comparison.json>` (one command).
6. Run `python -m unittest discover -s parsing-lab/experiments -p "test_*.py"`
   and `./gradlew :parsing-lab:engine:test` for behavioral checks.
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
