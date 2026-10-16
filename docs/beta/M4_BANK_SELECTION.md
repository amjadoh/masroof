# M4: Beta bank selection

Status: initial integrated selection increment; not the completed M4 milestone.

## Behavior

The Beta bank picker shows supported capabilities separately from the user's durable
selection. The current catalog exposes **Bank AlJazira only**; the three-bank
fixtures do not claim support for other real banks. Add, pause and resume save an
explicit ENABLED/DISABLED row in `masroof_beta.db`. Fresh Beta starts with no
selection. Selection never registers an owned asset or alters routing rules.

The BETA badge and return action stay fixed while content scrolls. Labels use
Arabic/English resources and MaterialTheme. Neutral spacing mirrors the existing
MasroofSpacing values inside Beta because Beta must not import production UI.

Pausing affects eligibility for new work only. Assets, aliases, shell markers and
future financial history do not cascade from the selection table. The table has no
history foreign keys and the workflow has no delete operation. Restart, Beta
backup/restore and reset share the existing container lifetime lock; each call
constructs a store around the current open database.

## Scope contract for the future capture/import adapter

`BetaBankImportScopePolicy` accepts candidates from routing over the **full**
catalog. It rejects zero candidates and collisions before consulting selection.
One enabled candidate is allowed for live scope. Historical scope additionally
requires that bank in the explicit per-import set, so adding bank B and requesting
B history cannot import A again. Counterparty/source/destination facts do not
participate in activation or ownership. The three-bank fixture tests both catalog
orders, one enabled bank, adding B, a B-only historical request, pause/resume and
unknown senders.

This PR does **not** open the SMS inbox, register a receiver, schedule imports,
interpret a captured sample, or read any production store. The policy is a tested
integration boundary, not a claim that live/historical capture is implemented.
M7 must bind the authoritative full routing result and the latest durable selection
at the capture/processing boundary; only its successful `Accept` may write into
Beta. It must preserve ambiguity for review rather than filter a router's catalog.

## Schema and migration

Beta schema 3 adds `beta_bank_selection(bankId, state)` with an additive Room
auto-migration from 2; the existing 1-to-2 migration remains. No production schema,
preferences, bank registry or financial definitions change. Migration starts with
no invented bank choice even if Beta assets exist. It does not infer selection
from suffixes or seed it from production preferences.

Device tests cover 1-to-3, 2-to-3, asset/alias/marker preservation, no invented
selection, selection restart, pause/resume, backup/restore and reset on the same
container. A Compose device test exercises the picker and fixed return/badge.
The existing stable Android CI suite runs these tests once; no new emulator job.

## Remaining acceptance work

- M4.2 real live/historical capture binding and optional historical import, with
  explicit permission and Beta-only durable raw-message storage (M7 dependency).
- M4.4 an explicit consented production-to-Beta migration mapping: no production
  preference read or automatic ownership inference is introduced here.
- Acceptance screen recording and DB comparison on an approved device.
- More real supported banks only after their own bank contract fixtures exist.

Legacy code is retained. Nothing is promoted into the production app.
