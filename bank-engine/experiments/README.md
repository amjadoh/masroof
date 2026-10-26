# Generic text candidates and learned field bindings: offline experiment

The first stage is now **text-only candidate discovery**:
`GenericCandidateExtractor.extract(body)`. It receives no sender, bank, message
family, teaching examples, semantic field names, or layout template. Common
lexical grammars offer money-shaped strings, timestamp-shaped strings, masked or
mixed identifiers, standalone numbers, and text fragments with original offsets.
No financial value, account role, bank identity, time interpretation or approval
is produced.

Human binding follows discovery. The user chooses which candidate belongs in
which field and can select a narrower range directly. The earlier learned-layout
experiment below checks the subsequent reuse of those bindings; it is not the
generic candidate extractor. Both stages remain in test sources only.

## Text-only discovery check

The ten-message private batch was scanned without passing a template or training
input. All 40 independently selected raw value spans appeared among the
candidates with the expected primitive kind and original offsets, including the
SARIE examples. Masked values retain their mask rather than inventing account
suffixes. Candidate precision and semantic interpretation were not measured;
extra text fragments can include headings or possible labels.

The full module run after adding discovery passed 85 bank-engine tests and
95 finance-core tests, with zero failures, errors or skipped tests. A separate
check binds human-selected candidates to semantic fields and only then teaches
a layout; no semantic mapping is inferred by the generic extractor.

```bash
MASROOF_GENERIC_INPUT=/private/generic-input.json \
MASROOF_GENERIC_REPORT=/private/generic-report.json \
./gradlew :bank-engine:test --tests '*GenericCandidateExtractorTest' --rerun-tasks --no-build-cache
```

Each private input row contains `body` and independent `expected` primitive
spans (`kind`, `raw`, `start`, `end`). Only `body` reaches the extractor. Without
private input, the same check uses a synthetic fixture. No private text is
committed or printed.

Generic means independent of message layouts and banks, not unlimited language
coverage. The prototype supports common SAR/SR, USD, EUR and GBP tokens and
selected currency symbols, date/time shapes, masked/mixed identifiers and
decimal-looking numbers. It deliberately preserves signs, precision and
ambiguous separators for later interpretation. A date-shaped string can still
be an invalid date, and a money-shaped string can still be a balance or a fee.
No chosen century, timezone, canonical Money, ownership or principal amount is
inferred. Text fragments use boundaries and an optional first-word split, not
named-entity recognition. Direct text selection remains necessary when a desired
value is not proposed. Inputs are bounded to 4096 UTF-16 units and candidates to
160 normalized characters. Production validation/integration is still pending.

## Subsequent learned-layout experiment

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

## First real held-out batch

The next supplied batch contained ten messages, manually separated at their
message headings. The original five teaching examples and `ExampleTemplate.kt`
were unchanged (both file hashes verified before and after evaluation).
Only the private evaluation harness changed; all private text stays outside Git.

| Frozen original templates | Result |
| --- | --- |
| Completed person payments | 3/3 raw field maps correct |
| Account funding | 1/1 raw field map correct |
| Internal outward transfers | 3/3 raw field maps correct |
| Internal incoming transfers | 1/1 raw field map correct |
| Previously unseen SARIE layout | 2/2 abstained with no match |
| Wrong accepted field maps in this batch | 0 |

A separate incremental experiment then selected fields in **one** SARIE message
to teach an additional template, and tested the **other** SARIE message without
teaching from it. All six fields matched their independently specified expected
slices: amount, counterparty, counterparty bank, account identifier, time and
reference. The original templates still reproduced their teaching examples.
The new teaching message is not counted as an independent successful holdout.

To repeat the frozen evaluation, supply `MASROOF_PATTERN_INPUT` as before plus
`MASROOF_PATTERN_HOLDOUT` and optionally `MASROOF_PATTERN_HOLDOUT_REPORT`.
Holdouts are a JSON array of `id`, `sender`, `body`, and `expected` (either null
for an expected no-match, or an object with `label` and a raw `fields` map).
The incremental check additionally accepts `MASROOF_PATTERN_NEW_TEACHER` (one
annotated example), `MASROOF_PATTERN_NEW_HOLDOUT`, and optionally
`MASROOF_PATTERN_NEW_REPORT`. Use `--rerun-tasks --no-build-cache` for private runs.

Verification after adding these checks: 74 bank-engine and 95 finance-core
tests passed, with zero failures, errors or skipped tests. Financial writes
remain zero. This small batch supports the narrow extraction experiment; it
does not establish a general accuracy rate or production financial eligibility.

Next evidence needed: more independent layouts and cases involving structural
variation or conflict. Integration, persistence/restore, revocation and production
safeguards remain separate increments.
