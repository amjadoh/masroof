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

## Scope exceptions and remaining

The user explicitly deferred old-code deletion. Historical comparison tooling and its source dependency are retained, but the current finance/observation path no longer uses their authority. Physical legacy cleanup is deferred. M9 is not started. Storage fixture tests are converted to current versioned records without changing their financial expectations; separate end-to-end tests exercise real independent interpretation and original spans.

## Validation

Pending final local/PR validation; record exact final head and native/lifecycle report results before declaring ready.
