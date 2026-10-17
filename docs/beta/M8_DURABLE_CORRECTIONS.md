# M8.3 — Durable correction and financial lifecycle

This increment adopts the existing pure correction contract in an **explicit Beta-only API**. It does
not wire intake, historical scan, review UI, M7, dashboard, production storage or legacy deletion.
Shared financial/classification/dashboard DTOs and the existing financial definitions remain intact.

## One decision and effect boundary

`RoomBetaCorrectionLifecycle.submit` commits a displayed, evidence-bound correction and its financial
activity together. `rebase` requires the expected current correction revision. The result exposes
`ACTIVE`, `SUPPRESSED` or `REVIEW`; only ACTIVE carries financial output. A successful decision stored
as REVIEW is explicitly a blocked financial result, never an acknowledgement that posting succeeded.
Unexpected writes/errors roll back and propagate; posting conflicts reject the entire transition.

Beta alone migrates **5 → 6**. Schemas 1–5 remain unchanged. Four additive tables contain current overlay,
append-only transition audit, retained evidence claims and retained match-proof claims. These claims
reference the correction anchor, allowing suppression before any financial posting **without inventing
a zero-valued financial row**. Existing ledger claims/history continue to reference their retained
financial snapshot. Both claim spaces are checked inside the same Room transaction by all posting
paths, so a suppressed/reviewed message cannot fund another key. Old claims are never released.

Decision IDs are unique per environment across keys. An identical operation retry returns AlreadyApplied
with the **current** committed state, even if a later transition has occurred. A changed request under
the same decision ID conflicts. Optimistic correction/interpretation/evidence bindings reject stale
requests; a rebase cannot overwrite a newer user decision. Transition sequence, overlay, financial
write/activity, claims and audit commit in one database transaction.

The existing `RoomBetaFinancialLedger.read` and `BetaContainer.financialPosting` return active rows only,
using one coherent transaction. Inactive financial snapshots and financial history remain available
through explicitly named audit APIs. Ordinary posting refuses overlay-owned keys, so a late raw reparse
or a higher caller-supplied revision cannot resurrect suppression. The internal coordinator posting
entry requires an existing outer transaction and a matching provisional lifecycle/revision marked
INCOMPLETE_WRITE; it cannot independently reactivate an already committed inactive row.

## Priority, seeds and paired transactions

When the first overlay follows an already posted transaction, the trusted storage adapter seeds the
exact stored financial selection and its original full snapshot. It verifies/freeze-copies that seed
without inventing a historical user request. The new correction must exceed the existing ledger's
correction revision, even though the preceding overlay revision is zero. Metadata Keep preserves prior
manual authority, native amount/currency, time/zone, endpoints, attribution and complete FX pair. Reparse
does not replace those facts. The current observed interpretation remains separate and may add blockers.

Suppression removes the whole current effect from financial reads while retaining the previous snapshot,
claims, seed, decisions and audit. Explicit restoration passes the existing financial gate and current
registry checks. Metadata cannot restore suppression. Security/unexecuted/informational observations
retain the sticky validation hold and remain REVIEW, even when suppression was requested.

A previously matched transfer is one canonical aggregate: suppression requires both original evidence
sets. A correction to one incoming/outgoing observation cannot retract one leg. Restoration rechecks
both frozen observations and the original bank-reference/user-pair proof against the current registry.
Only the overlay's synthetic SELF_TRANSFER ambiguity is satisfied by that checked proof; actual new
AMBIGUOUS_MATCH, ownership, validation and safety blockers remain REVIEW. Unsupported changes to paired
financial facts are explicitly refused, and neither evidence nor proof claims are dropped. This API
does not perform fresh heuristic matching or infer that new competing observations disappeared.

## Stored state and recovery

The correction codec has explicit snapshot and operation versions with checksums and strict enum/field,
history/revision, identity, evidence and metadata consistency checks. Seed snapshots retain their original
financial result. Reads validate/deep-freeze structure without replaying old decisions or recalculating
historical financial metrics. Initial seed creation verifies equality with the shared checked posting
rules; it never silently substitutes a newly calculated result for the stored one. Caller collections
cannot mutate the confirmed overlay, selected seed or historical result.

Reads/writes are targeted by posting key, evidence/proof and decision ID. There is no global ledger or
history replay per message. The current per-key overlay snapshot contains its cumulative decision audit;
loading/copying that individual snapshot grows with that key's history. Audit listing is an explicit
operation. Audit revisions retain complete snapshots as well, trading storage for exact recovery;
audit compaction is outside this increment. No artificial storage cap or whole-history reprocessing
is introduced.

Valid Beta backups include all correction state and activity; reset clears Beta only. Existing offline
restore still replaces the live Beta file before validation, and the maintenance lock remains per
container. Checksums are accidental-corruption detection, not authentication. This does not claim M11
failure-atomic/untrusted-backup or concurrent-maintenance acceptance.

## Verification and remaining scope

New pure tests protect seed validation, caller isolation and snapshot/history integrity. Room tests cover
active→suppressed→restored effects, suppression before posting, both claim spaces, prior-manual seeds,
metadata/reparse priority, full FX/time precision, security/ownership/ambiguity review, optimistic and
idempotent concurrency, failed initial writes/failed suppression rollback, unsupported currency/paired
changes and corrupt-state failure through the existing financial read path.

Device tests exercise 1→6, 5→6 with all twelve existing data families/financial proof preserved, and
two independent Room database instances competing to commit one correction. Two additional external
force-stop journeys run before/after suppression COMMIT in the existing lifecycle job, each phase once,
with restart/retry, prior manual/FX restoration and valid-backup checks. No new emulator, Android matrix
or duplicate normal-device suite is added. Final PR validation records actual results after execution.

Full M8.3 acceptance still needs review UI/runtime adoption, typed validator-finding resolution, explicit
time/endpoint/FX correction commands, broader paired financial edits and full legacy-vs-Beta correction
parity. Existing amount/type commands do not invent those authorities. Currency changes with an existing
complete FX pair remain unsupported until an explicit rate-resolution command exists.
