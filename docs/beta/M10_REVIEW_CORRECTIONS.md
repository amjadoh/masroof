# Beta review and typed correction increment

This increment starts M10.2 on top of the durable M8.3 correction lifecycle. The Beta shell opens an actual paginated review screen backed by saved Beta financial ledger and correction records. Unaccepted engine comparisons are not presented as posted transactions, and an empty page does not claim that all SMS evidence has been financially reconciled.

## Trust and atomic writes

The application workflow reads a bounded ordered union of `beta_ledger_current` and `beta_correction_current`. Detail comes from the latest trusted correction observation or an existing persisted financial posting. It never reconstructs an interpretation from display text, SMS wording, or the candidate engine archive. Existing manual financial authority is seeded by the correction lifecycle from the stored posting.

The displayed token binds transaction identity, interpretation, evidence, correction revision, transition revision, and stored ledger revision. Save re-reads it and commits the explicit correction and financial activity within one Room transaction. It also compares the selected financial endpoints/loan, their parents, paired observations, and explicitly requested endpoint facts to the current Beta registry. Changed registry facts require a fresh trusted interpretation rather than accepting a stale ownership assertion. Stable decision IDs make an exact retry idempotent; different decisions cannot reuse the same ID.

Schema 6 is supported independently. When the M7 adoption capability exists, a held/unknown adoption status, evidence retry, or financial approval retry blocks detail and save. The screen cannot restore a previously ordinary posting after a security reparse by falling back to the old ledger snapshot. This capability lookup has no schema migration and fails closed on an incompatible schema.

## Decisions

- Notes retain financial authority and cannot confirm a partial parse.
- Same-currency amount confirmation and explicit financial type require an unchecked-by-default confirmation control.
- Whole-record non-financial suppression retains audit history and excludes the active financial effect.
- Typed `EditFacts` supports transaction instant plus its existing zone, and bank-qualified source/destination endpoints. It requires an existing selected financial result and preserves its authority, suppression, full FX pair, and safety hold.
- Endpoint choices are deliberately restricted to the confirmed asset facts already in the trusted observation, plus an explicitly selected external party. This screen does not bind a newly discovered asset or resolve a sticky ownership blocker. Existing validation/ownership blockers remain visible and may keep the result in review.
- Local time entry retains the transaction's stored zone. Invalid dates and ambiguous/nonexistent wall times are rejected rather than guessing a UTC offset.
- Financial edits to matched two-evidence aggregates remain unsupported; the screen disables these actions. Notes and whole-group suppression remain available.
- Manual FX replacement and native-currency changes with a complete applied pair remain unsupported. No user-entered rate is mislabeled as SMS/history/market provenance. Automatic enrichment persistence belongs to the separate M7/M5.4 increment.

## Presentation and verification

The full-screen editor uses the existing Beta theme/spacing, localized Arabic/English labels, and the host's layout direction. Drafts survive state restoration; Back and Cancel discard them. Financial choices are never silently confirmed by opening a screen. Busy saves block editing, Save, Cancel, Back and returning from Beta. The screen displays financially active, suppressed, and review-required states without exposing raw SMS or identifiers in labels.

Targeted tests cover pure authority preservation and reparse, Room persistence and optimistic staleness, retry idempotency, registry change, optional adoption holds, bounded queue cursors, and actual device UI cancellation, state restoration, explicit amount confirmation, busy controls, and a durable suppression through the real panel. This does not complete all M8.3 commands or the full M10 review experience: raw evidence review, new asset binding, pairing edits, manual FX proof, and trusted M7 held-adoption rebase remain follow-up work.
