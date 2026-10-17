# M9 / M10 / M9.4 — Beta dashboard controls

This UI increment keeps all money in the existing authoritative dashboard read
contract. It introduces confirmed configuration editing and chart/transaction
exploration in the actual `BetaActivity`. Production UI/data are never opened.

## Independent branches and durable integration

The frozen application configuration contract is shared unchanged from data-track
commit `620e051302069a9363dff9e0d181c93456453610`, which is also a common ancestor.
`betaDashboardControls(container)` is the stable binding. This independent UI base
returns explicit unavailable and cannot pretend to save. The data PR replaces only
the factory implementation with its durable schema-10 adapter; combining the two
PRs activates these same screens without reflection or extra Activity glue. Neither
this branch nor its screens own Room, migrations, period arithmetic or financial
calculations. The shared facade owns validation and compare-and-set revisions.

## Configuration drafts

Salary day, qualified facility closing day, recurring labels/SAR amounts/due days/
first and optional last month are edited in a draft sheet. Empty salary/statement
day explicitly removes that configuration. Short months clamp according to the
data contract; configured salary day 27 preserves its Friday/Saturday adjustment.
The producer displays actual half-open dates rather than treating a calendar month
as a salary or statement period. Current-cycle selection and month navigation are
owned by the data projection, including the selected month offset from the current
financial cycle.

Apply previews the draft and requires confirmation before saving. Cancel, Back,
scrim or swipe discard; only the scrollable body moves and actions remain fixed.
The isolated sheet mirrors shared Masroof spacing, maximum width and height
geometry, using Material theme tokens. Busy writes prevent dismissal, duplicate
submission, dashboard navigation, management actions and returning from Beta.
Stale revisions cannot overwrite newer settings: the user must discard/reload and
review again. Saved draft fields and the original revision survive recreation.
Invalid/store-error messages are localized, not raw technical diagnostics.

Manual paid/unpaid decisions are confirmed and keyed to a displayed due month.
They never create ledger postings or establish card/loan repayment authority.
Editing the recurrence range cannot silently erase paid-month decisions. Removing
a commitment explicitly confirms removal of its configuration and manual decisions;
financial history remains unchanged.

## Charts and transaction details

Chart customization chooses an existing producer trend by stable scope/category
and bank-qualified asset identity. A category selects its authoritative financial
outflow series; no sum, attribution, FX or synthetic category projection is done in
Compose. The producer's full-scope summary metrics, balances and dues remain intact.
Daily bars normalize absolute magnitude for drawing only; signed labels and null
values remain unchanged, and the average comes from the producer. A table view is
available. Chart choices apply only on Save; Cancel preserves the previous view.

Nonfinancial choices persist as string values in `masroof_beta_prefs`, through an
application workflow rather than direct presentation access. Fresh launch reads
stable enum keys and qualified identity; invalid/removed choices fall back safely.
The existing Beta backup/restore mechanism includes this same string-only store.

Day and asset drilldowns select authoritative transaction IDs/membership, never
recompute financial totals. The month list and transaction detail show original
money, effective/stored type, authoritative time/zone, merchant/counterparty, full
FX precision, separate fee-free SAR equivalent and observed international fee.
No converted amount is inferred for a missing value. Details do not approve,
repost or alter history. Transaction detail navigation is ephemeral across a fresh
launch but survives actual Activity recreation; removing its transaction shows an
explicit missing-item state. Long lists have bounded initial presentation.

## Verification and limits

New JVM cases cover configuration isolation/validation/manual-paid boundaries,
signed/unknown chart geometry and actual string-only Beta backup/restore of view
preferences. Device journeys cover confirm/cancel/busy/CAS conflict, restored stale
drafts and full actual Arabic BetaActivity exploration with synthetic real Room
postings, fresh-launch view preferences, recreation and unchanged financial history.
Full Activity screenshots include BETA/return chrome and are collected through the
existing screenshot artifact path, without another emulator job. Final-head check
and actual artifact results are recorded in the PR after completion.

This does not claim complete original dashboard visual parity, performance/foldable
device certification, manual recurring pause/resume semantics, arbitrary multi-
category aggregation, or transaction editing. No old code is deleted.
