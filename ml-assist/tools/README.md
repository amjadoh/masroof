# Local learning tools

CI uses Python 3.15.2 and Gradle on JDK 27 with the JDK 21 toolchain. The pure
`ml-assist` test suite uses JUnit Jupiter; Android feature-export tests retain
their Android runner. Run focused tool checks locally; full suites and model
reproducibility run in PR/main CI.

The Android runtime and offline recurrence trainer share the same Kotlin
features, fitter and inference. There is no Python/TensorFlow dependency in the
application. The backup inspector optionally needs Python `cryptography`.

## Reproducible shipped artifacts

```sh
./gradlew :app:testDebugUnitTest --tests '*FamilyFeatureExportTest'
python3 ml-assist/tools/test_train_family_model.py
python3 ml-assist/tools/test_training.py
python3 ml-assist/tools/train_family_model.py --check --skip-export
./gradlew :ml-assist:trainRecurringSeed -PrecurringCheck=true
```

The recurring corpus is currently empty. Its shipped artifact/report explicitly
say `accepted=false`; this is not a qualified pretrained model. Do not replace
real qualification data with synthetic examples or fabricated independent IDs.

## Consented private backup

Run these outside CI. Keep original inputs, passwords and private outputs out of
git. The inspector authenticates/decrypts in memory and prints aggregate counts
only. `--private-output` additionally creates **raw private** human-labelled
inputs with restrictive permissions. It does not automatically enroll them in
the shipped corpus. The password is requested interactively; never pass it as a
command-line argument or commit it to a script.

```sh
python3 ml-assist/tools/inspect_private_backup.py /private/input.masroof --private-output /private/learning
MASROOF_REVIEWED_CORPUS=/private/learning/reviewed-input.json \
MASROOF_CORPUS_SPLITS=/private/learning/splits.json \
MASROOF_FEATURE_EXPORT=/private/learning/features.json \
./gradlew :app:testDebugUnitTest --tests '*FamilyFeatureExportTest'
python3 ml-assist/tools/train_family_model.py --skip-export \
  --export /private/learning/features.json --splits /private/learning/splits.json \
  --freeze-splits /private/learning/frozen-splits.json
python3 ml-assist/tools/train_family_model.py --skip-export \
  --export /private/learning/features.json --splits /private/learning/frozen-splits.json \
  --output /private/learning/model.json --report /private/learning/evaluation.json
```

Kotlin performs the actual production cleaning and financial-validation probe;
Python does not introduce another tokenizer. Unknown title words abstain, rather
than exporting possible names. Review rejected inputs privately. Source labels
come only from unambiguous explicit human decisions. Parsed families, generic
EXPENSE/INCOME, ownership, pending decisions and unresolved corrections are not
automatically promoted into family truth. Frozen partitions group cleaned
templates before training and preserve previously frozen holdouts.

## Recurrence corpus

TSV fields: `seriesKey`, `contributorGroup`, `partition`, `humanLabel`,
`ruleLabel`, then eight numerical features from `RecurringDetector.series`.
Partitions are `train`, `validation`, `test`; labels are `NONE`, `WEEKLY`,
`MONTHLY`. Contributors and series cannot cross partitions. The features carry
no merchant name, account identifier, date or absolute monetary amount. Do not
reimplement feature extraction in Python.

The opt-in instrumentation `PrivateBackupAnalysisTest` accepts an encrypted
backup and a transient password file in the target app's private files directory
(`private-backup.masroof`, `private-backup-passphrase`) and argument
`privateBackup=true`. This private probe is excluded from public CI because its inputs must stay private; normal feature and lifecycle tests remain required. Use an isolated emulator. It runs production analysis in a
separate storage context, compares ledger fingerprints, deletes the private
copies and emits aggregate diagnostics plus `private-recurring-corpus.tsv`.
The export includes only explicit recurring feedback or saved human commitments;
all rows initially belong to training under one pseudonymous contributor group.
Other backups from that same person must retain that contributor group and
partition. This export cannot independently qualify a global model.

```sh
./gradlew :ml-assist:trainRecurringSeed \
  -PrecurringCorpus=/private/learning/recurring.tsv \
  -PrecurringOutput=/private/learning/recurring-model.json \
  -PrecurringReport=/private/learning/recurring-report.json
```

Validation alone chooses the threshold. The untouched final test can veto
qualification, never choose weights or thresholds. Qualification requires 95%
precision, at least 10 percentage points more recall than rules, no increased
false-positive rate, >=30 proposed independent series per cadence and >=30 hard
negatives in both validation and test. Personal on-device training has its own
stable held-out series gate; it cannot activate an ineligible base model.

## Forecast replay

`ForecastReplay.evaluate` and the developer-only workflow replay use a strict
historical prefix with no final origin-day amount. Future days supply evaluation
targets only; gaps prevent accuracy claims. They never write retrospective
published forecasts. The existing live forecast comparison policy is unchanged.
