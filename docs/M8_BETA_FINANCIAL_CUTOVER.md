# M8 — Beta financial adoption cutover

Authority: ADR-19 page 12 (2026-10-18), starting main a9e995a51af88e7a25987b97c5cd3caa7253316d (PR #294).

## Audited gap

The default intake already runs the independent BankEngine once. Financial adoption and write-side dashboard fact maintenance still read the historical baseline and require the legacy version, preventing approval of new Beta messages. Existing Finance Core classification, ownership, revision, matching, correction, FX and atomic posting protections are reusable.

## Change

A single current-record boundary checks the exact BankEngine contract/engine/rules identity, saved evidence identity, unique persisted sender route, empty historical slot, and original UTF-16 span boundaries. Financial inputs additionally require successful current facts with amount/time proof. No interpreter runs during approval or dashboard reads. Explicit financial confirmation remains mandatory. The financial request hash now binds the current archive as well as the route and interpretation attempt. Enabled-bank checks apply even to pending requests predating UI registry fingerprints.

Financial corrections continue through the existing explicit, revision-bound correction lifecycle, which preserves selected amounts/endpoints/time/complete FX and sticky security holds. M7 learning labels remain human annotations, not an automatic financial override or a second parser. ML stays inactive. Unsafe/partial/security/unexecuted records cannot be promoted by a label or financial type choice.

The existing Beta ledger, historical archives, annotations and decision payloads are retained. A historical pending request cannot become a new posting; it requires current reprocessing and fresh explicit approval. Already posted audit history is not rewritten or replayed. There is no schema change, database reset, production migration, inbox access, or production financial change.

## Observation gap exposed by cutover

The independent observation reader excluded family-proved titles containing a colon, losing statement authority for an existing Arabic statement fixture. It now uses the already recorded original family span to scope such titles; merchant text still cannot supply a statement role. Engine identity advances to v7 so previous interpretations require explicit reprocessing for new decisions.

Synthetic financial backup and UI fixtures now exercise independent intake and explicitly label the currency of their international fee. Financial amounts, FX and snapshot expectations are unchanged. A separate regression keeps an unlabeled fee in REVIEW rather than borrowing currency from a foreign principal or a balance; frozen production fixtures are untouched.

## Authorized legacy removal

The user explicitly reversed the cleanup deferral and requested removal of every old parser file/code inside the lab. The old `lab/engine` and `lab/android` modules, copied parser and UI, comparison adapters/factory/helper, old-engine-only tests, paired benchmark runner and old reports are removed. The old developer navigation link, build flag/dependencies and obsolete CI tasks/report paths are removed as well; the Beta entry remains.

All 42 fixture files in the removed engine module were byte-identical to the retained canonical production **test** corpus. Canonical examples and expected fields were not changed. Independent BankEngine/Finance Core/Beta tests and generic optional offline learning utilities/annotations remain. Tests belonging exclusively to the deleted implementation are removed; the new interpretation/financial guards remain tested.

There is no schema reset or automatic data cleanup. The two historical Room archive columns and enum values remain readable solely to preserve existing Beta records, original messages, labels and audit history; historical records cannot authorize current discovery or posting and require explicit reprocessing. No old interpreter or fallback remains. The obsolete Parsing Lab startup cleanup path is removed; an explicit fresh-Beta choice still affects Beta only and never runs on ordinary launch.

Production parsing, financial repositories, data, preferences and migrations are unchanged. App changes only remove the obsolete developer link/dependency and retain the Beta entry. M9 is not started.

## Validation

Final cleanup-head local checks, runtime graph, native/lifecycle reports, test counts and artifact hashes are recorded in [PR #295](https://github.com/bakhshb/masroof/pull/295). Earlier pre-cleanup test counts must not be used as final-head evidence. Require the final reports before merging.
