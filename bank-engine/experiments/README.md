# Extraction from a single annotated SMS: offline experiment

This experiment checks whether human-selected field ranges can generate a
bounded extractor for a message layout. It runs in `bank-engine` **test sources
only**. It is absent from the application runtime and APK. No application,
adapter, intake, database, worker, model, or financial policy is modified.

## Contract and scope

- Input: an exact sender, a human label, original message text, and non-overlapping
  original UTF-16 selections with field names and primitive kinds.
- Learning: retain literal context and the first title line; replace selected
  values with bounded typed slots. Escape all literals; accept no executable
  patterns or scripts from the input. Reuse the engine's `SourceText` for text
  normalization and original evidence offsets.
- Output: a `Preview` containing original field slices and offsets, or abstention.
  A preview always has `financialPostingAllowed == false`. Labels are human
  metadata, not production `MessageFamily` or transaction decisions.
- This proves a restricted template construction step, not unrestricted program
  synthesis, optional-field learning, statistical learning, or financial posting.

Supported demonstrations: incoming/outgoing internal transfer, card-funded
account addition, incoming local transfer with three `From` roles, and completed
person payment. Only anonymized synthetic examples are committed. The labels for
funding and person payment deliberately do not invent production enum members.

## Verification

```bash
./gradlew :bank-engine:test --tests '*experiment.*'
./gradlew :bank-engine:test :finance-core:test
```

Use JDK 17 as required by the repository's existing toolchain. The first test
failed against an unimplemented learner, then passed. A later failing ambiguity
test led to rejecting multiple selected fields on one line instead of guessing
their boundary.

The optional private probe takes a JSON array of objects containing `label`,
`body`, and `selections` (`field`, `kind`, `start`, `end`). Each range is half-open
and measured in original UTF-16 code units. Inputs and output belong in private
storage outside Git. No SMS text or field values are printed to the console.

```bash
umask 077
MASROOF_PATTERN_INPUT=/private/input.json \
MASROOF_PATTERN_REPORT=/private/report.json \
./gradlew :bank-engine:test --tests '*experiment.*' --rerun-tasks --no-build-cache
```

Force execution for private probes because their external inputs are intentionally
not Gradle build inputs. Do not upload private inputs, reports, or failed private
test artifacts to CI. Without private input, the probe executes its synthetic
fixtures and writes no report.

Observed private probe on 2026-10-26:

| Check | Result |
| --- | --- |
| Supplied teaching examples reproduced | 5/5 |
| Synthetic probes changing selected values | 10/10 |
| Synthetic structural/typed negatives rejected | 25/25 |
| Independent real messages held out from teaching | 0 |
| Financial writes | 0 |
| Production integration tested | No |

The full module runs passed: 72 bank-engine tests and 95 finance-core tests,
with zero failures, errors or skipped tests. No Android/device test was run.

The negatives cover changed execution titles, an appended OTP line, another
sender, a missing amount line, and unsupported amount precision. The OTP case is
rejected by layout mismatch; this is not a replacement or validation of the
production security gate. Other tests cover conflicting templates, invalid dates,
overlapping selections, repeated values, original Unicode offsets and leading
zeroes. These counts are behavior checks, **not a real-world accuracy estimate**.

## Limits before application integration

The prototype allows one selected field per line, keeps the observed identifier
width and date format, and abstains on unseen field omissions or structural
changes. Reduced years explicitly validate against 2000–2099 for this preview;
no canonical transaction timestamp or zone is produced. Money is a raw typed
string; it is not normalized into either production Money contract.

Real three-digit account identifiers survive extraction unchanged. They cannot
be passed off as four-digit suffixes to production validation. Funding-card
issuers, account ownership, transfers between owned accounts, financial family
mapping, and commitment settlement still require the existing production
authorities and an integration design. No amount/account/time interpretation
from this prototype can post, pair transactions, or clear review.

Next evidence needed: new real messages from the same layouts, excluded from
teaching. Integration, persistence/restore, revocation and production safeguards
are separate increments after that evidence is reviewed.
