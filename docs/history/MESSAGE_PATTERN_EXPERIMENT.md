# Generic text candidates and learned field bindings

> Historical experiment checkpoints. For current production ownership and processing, see [Architecture](../ARCHITECTURE.md) and [Pattern facts](../PATTERN_FACT_CONTRACT.md). Prototype CLI persistence is test tooling, not the application store.

The production integration proposal, including AlJazira parity gates, automatic
seed preparation, bank-scoped import, persistence/restore and rollout tasks, is
documented in [PRODUCTION_MIGRATION_PLAN.md](PRODUCTION_MIGRATION_PLAN.md).
It is a plan; the offline results below do not establish production parity.

## Implementation checkpoint: shared pure core

The text-only extractor and the declarative pattern builder/compiler now live in
`bank-engine/src/main/kotlin/com/baraa/masroof/engine/bank/patterns/`. The experiment
imports that implementation directly; it keeps no compatibility wrappers or second
copy. No runtime dependency was added. `Preview` still carries no financial
authority. Configured new banks now feed the existing intake/ledger path after
explicit financial approval. AlJazira now uses the shared adapter/store with
preapproved role patterns, preserving its flexible layouts and existing facts.
Pattern labels are raw teaching metadata, not production financial families.

The optional [fact contract and write gate](PATTERN_FACT_CONTRACT.md) now convert
explicit versioned definitions through the production mapper/validator and reject
stale configuration at write time. Existing taught patterns and the STC seed have
no fact contracts and remain preview-only until explicitly approved. Dynamic routing,
scoped import, pending application and protected live processing are wired through
the existing workflows. Partial-reference links require a confirmed four-digit
account; unknown references remain reviewable.

The application now reads AlJazira's shared definitions from
`app/src/main/assets/bank-patterns/BANK_ALJAZIRA.patterns.json`. The same snapshot
feeds production interpretation and ML fallback qualification.
The strict codec rejects unknown fields/operators/versions and oversized input.
Round-trip tests pin full schema equality and the unchanged refresh fingerprint;
the candidate in the parity harness reads the bundled JSON rather than calling
the old profile builder. All 74 parse inputs and seven financial scenarios match
the frozen reference. These are 13 classification rules across 11 families, not
13 automatically learned message layouts. Bank pattern management is available
through message-learning settings and the bank page. Teaching is preview-only;
financial approval, pending application and scoped import are separate actions.
AlJazira's shared-pattern cutover and preservation checks are described in
[ALJAZIRA_SHARED_PATTERNS.md](ALJAZIRA_SHARED_PATTERNS.md). Its generic role matcher
is retained alongside learned typed slots, rather than narrowing it to fixture templates.

The current milestone covers financial approval, exact partial-reference links,
dynamic routing, scoped import and protected pending/live processing. Verification
counts and remaining release gates are recorded in the migration plan. No APK was
installed on the user's device.

Three review regressions now cover normalized identifier widths (including hidden
direction marks and supplementary Unicode digits), immutable owned pattern parts
and copies, and variable-width currency-free monetary values. The last are raw
`DECIMAL` slots for monetary field names, without inventing a currency or financial
authority; account/card digit widths remain fixed. The same snapshot drives
compilation, preview and persistence. All three tests failed before their fixes.
The two-process lifecycle was rerun after the fixes: 12 held-out messages,
49 fields and 36 rejection controls still pass, with zero financial writes.

The shared core supports original UTF-16 ranges selected directly by a user even
when discovery misses a value, time-first timestamps, and standalone date slots.
Those slots reject impossible dates and survive save/reload using generated
representatives rather than reopening teaching messages. The CLI and its file
store remain test tools. Android now has a separate private-file
`MessagePatternStore` and `MessagePatternsWorkflow`, exposed through AppContainer.
The validated document records bank aliases, immutable pattern revision history
and an optimistic editor generation; atomic replacement preserves the old file
on write failure. The latest revision, including a disabled revision, is current.

Pattern configuration is included in encrypted backup preferences, keeping the
same three archive entries and Room v18. Existing database access coordination
keeps export and pattern edits consistent. Import validates before touching live
data; older backups clear pattern configuration. Restore journals pair original
or incoming definitions with the corresponding database after interruption.
Rollback preserves even invalid original bytes rather than silently discarding
them; normal reads still reject invalid configuration. Tests cover these paths.
The teaching workflow now generates a preview from explicit field bindings and
saves that same immutable definition only if the configuration generation still
matches. The screen supports original text selection, extracted candidates, an
additional-message preview, disabling, and restoring an older revision as a new
revision. Leaving discards unsaved text and bindings. Its first device journey
uses synthetic messages only. Review details now open teaching with an in-memory,
read-only copy of the SMS and sender. The saved explicit bank selection takes
precedence over the parsed bank; unknown banks require a user choice. Saving a
pattern returns to the review without resolving it or rewriting evidence. A Room
test preserves original SMS, parsed facts and a prior human decision; the device
journey verifies immutable prefilled inputs and return navigation. Routing/import,
explicit pending application and the pattern fingerprint guard are now connected;
teaching alone still grants no posting authority.

The new audit of the same 64 fixtures offers all 33 expected transaction timestamps
and both expected due dates as raw candidates. This closes the seven previously
reported date-discovery gaps; it does not assign roles or prove financial parity.
The fresh two-process lifecycle still verifies eight generated definitions,
12 held-out messages, 49 fields and 36 rejection controls, with zero financial writes.

The app now has a frozen pre-cutover reference for 74 inputs (2,690 checks) and
seven financial scenarios (834 field/link/registry/projection checks). These
reports characterize the working interpreter; a future replacement must pass
them. The original fixture and independent financial-oracle tests remain active.
The historical experiment results below describe earlier checkpoints.

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
generic candidate extractor. The shared pure implementation is now in main
sources; the persistence lifecycle runner remains in test sources.

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
inferred. Text fragments use boundaries and up to four literal windows, optionally
excluding a word at either edge, not named-entity recognition. Direct text selection remains necessary when a desired
value is not proposed. Inputs are bounded to 4096 UTF-16 units and candidates to
160 normalized characters. Production validation/integration is still pending.

An inline-message regression exposed a lost text fragment: the initial splitter
started after the first unprotected colon, discarding a merchant before a late
time label. Two new tests reproduced this before the fix. Text scanning now
starts at the beginning of each line and visits both sides of every unprotected
colon; protected timestamps and numeric tokens keep their original spans. The
additional literal windows can expose a value between two context words, without
hardcoding a bank, sender, message title, merchant, or language-specific label.

The follow-up private run covered eleven messages and all 45 selected raw value
spans, including the full previously missing merchant. The inline message now
offers 20 candidates, including extra context fragments; this is recall of the
selected values, not an evaluated precision or automatic semantic assignment.
87 bank-engine and 95 finance-core tests passed, with zero failures, errors or
skipped tests. No production source, financial write, or app data changed.

## Subsequent learned-layout experiment

This experiment checks whether human-selected field ranges can generate a
bounded extractor for a message layout. Its runner remains in `bank-engine`
test sources and now uses the shared pure pattern compiler. The app's adapter,
intake, database, worker, model and financial-policy wiring are unchanged.

## Full cycle with automatically generated, persisted definitions

`SavedMessagePattern.fromChoices` takes text, scope/type metadata selected by a
user, and only field names plus original start/end selections (and an optional
ignore choice). It runs generic candidate discovery, validates selection coverage,
infers supported primitive field shapes from the selected text, and automatically
constructs alternating literal/slot parts. The writer accepts no primitive type,
regex, literal-part list or manually authored template in its field-choice input.

`PatternStore` saves schema/normalization versions, scope, id, revision, enabled
state, literals and typed slots to JSON. Selected example values are not persisted.
Loading compiles the declarative definition using generated primitive placeholder
tokens and the trusted compiler; it does not reopen the original teaching SMS.
No executable regex is accepted from disk. Saves sync a new file before an atomic
replacement; write-failure injection verifies that the previous file survives.
The prototype serializes writes in one JVM; concurrent application writers and
Android encrypted backup integration are not covered by this file experiment.

The actual private lifecycle run used two separate JVM invocations:

```bash
./gradlew :bank-engine:patternLifecycle --args='teach /private/lifecycle'
# After the first process exits, remove teachers.json from its expected path.
./gradlew :bank-engine:patternLifecycle --args='verify /private/lifecycle'
```

The first process generated eight definitions from 37 human field choices, saved
`patterns.json`, and recorded its PID, definition fingerprint and teaching-body
hashes. The teaching input was then renamed and made unreadable. A new process
verified a different PID, loaded definitions without teaching input, rejected
any test body duplicating a teacher, and interpreted 12 held-out messages with
all 49 expected field values and original offsets correct. Those cases comprise
nine supplied STC messages, one separate AlJazira repository fixture, and two
synthetic mutations of the inline purchase example. The inline example's bank
and sender scope was simulated as a user selection; bank detection is not tested.
Thirty-six wrong-scope, wrong-sender and structurally changed controls abstained.

The user-binding step was simulated from explicit field selections; pattern
construction and primitive-shape inference were automatic. No saved definition
was hand-authored. Unit tests additionally cover disable/revision persistence,
conflicting matches in either order, unsupported/corrupt data, executable-part
rejection, interrupted writes, ignored varying fields and a local file snapshot
restore. This does not test the app's production backup/restore workflow.

The full module runs passed 95 bank-engine and 95 finance-core tests with zero
failures, errors or skipped tests. Financial writes remain zero. All new classes
are test-source tools and `patternLifecycle` uses only the test classpath.

## Contract and scope

- Public teaching input: bank scope, exact sender, human label, original message
  text, and non-overlapping original UTF-16 selections with field names and an
  optional ignore choice. Primitive shapes are inferred; explicit user ranges do
  not require discovery to have offered the value.
- Learning: retain literal context; replace selected
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

The prototype allows at most one free-text slot per line; other typed slots can
share a line when distinct literal separators remain. A literal anchor must precede
the first selected field. It keeps the observed identifier width and supported
date shape, and abstains on unseen field omissions or structural
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

## Pending-message preview checkpoint

The bank page now reads pending review evidence and runs current saved patterns
against each message independently. Posted evidence, manual corrections/decisions
and successful interpretations are excluded. Explicit selections and stored banks
win; otherwise configured exact senders are considered alongside every current
router candidate, preserving bank collisions. Multiple matching patterns remain
conflicts rather than choosing the first. The preview never saves interpreted
facts, posts transactions or removes reviews. It checks the first 1,000 review
rows and displays up to 50 bank results with a partial-coverage notice. Results
refresh when configuration changes and link back to the original review message.
Unit and API 36 device checks cover different extracted values, pattern/bank
conflicts, stale generations, protected rows and disabling patterns. This is a
read-only preview, not financial activation or a full inbox import.

## Bundled STC preview patterns

`app/src/main/assets/bank-patterns/BANK_STC.patterns.json` contains six definitions
produced by the shared `SavedMessagePattern.fromChoices` learner: internal incoming,
internal outgoing, account top-up, local credit transfer, SARIE incoming and paid
qattah. `StcBundledPatternsTest` regenerates the exact bundle from explicit bindings
and checks all nine held-out message layouts, a synthetic local-transfer variant,
and wrong-bank/sender/extra-text rejection. Personal names and identifier values in
the fixtures are anonymized; the layout, amount and timestamp formats are retained.
The local-credit-transfer format has only one user-supplied example, so its separate
generalization check is synthetic. Selected teaching values do not survive in the
bundle. Ambiguous account and funding references retain neutral roles and masks.

Opening the pattern workflow installs the bundle only when its bank and sender
identity are absent; subsequent loads and updates preserve user definitions and
disabled revisions. The bundle is included in the APK, and installation uses the
same versioned store and backup path. No bank adapter, financial posting authority,
self-transfer ownership or income classification is enabled by this seed.
