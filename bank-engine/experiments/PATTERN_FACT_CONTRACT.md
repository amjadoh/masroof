# Pattern facts and financial write gate

The pattern path remains inactive for live financial processing. Teaching and the
STC bundle still create preview-only patterns. This increment provides conversion,
persistence, mapper validation and the write guard before routing is activated.

## Definition and conversion

`SavedMessagePattern.factContract` is optional. A matching pattern without it
returns `REVIEW_REQUIRED`, an unknown family and no financial facts. Display labels
are never converted to financial enums. The current editor does not approve contracts.

`PatternFactContract` v1 supplies a family, direction, optional transaction currency
and explicit account/card bank identities. It contains no ownership, financial
transaction type, container id or posting flag.

`PatternFactInterpreter` freezes the configuration and evaluates every enabled
current definition for the routed bank. Overlaps, including preview-only patterns,
remain conflicts. Sender detection is never repeated after routing.

Supported roles: `amount`, `merchant`, `counterparty`, `biller`, `biller_code`,
`reference`, `occurred_at`, `source_account`, `destination_account`, `card`,
`available_balance`, `outstanding_balance`, `international_fee`, `foreign_amount`.
Unknown roles block use; explicitly ignored slots remain ignored.

- Money needs a currency in its own span, or an explicit transaction currency for
  bare `amount`. Transaction currency never fills balance/fee/foreign currencies.
  Contradictory transaction currency blocks use. Production validation still checks
  positivity, family/direction compatibility and plausible timestamps.
- Full timestamps stay local for the existing financial time policy. Date-only
  fields are incomplete; midnight is never invented. Missing time uses only a
  durable SMS receipt instant.
- Account/card identities require four normalized digits and an explicit bank.
  No padding, truncation, name-to-bank lookup or ownership inference occurs.
- `account_reference`, `funding_reference`, `counterparty_bank` and short account
  references retain their original evidence and block automatic use. STC's partial
  reference identity policy is still outstanding.
- Shared OTP evidence wins. Existing non-execution and notice-context safeguards
  also block pattern financial use.

Every mapped value has original UTF-16 evidence offsets, a definition source
(family, direction, currency, reference bank) or a durable receipt source.
`InterpretationDraftMapper` preserves hash, pattern id/revision and source metadata
in existing confidence reasons and always uses `ParseFinalizer` and the production
validator. No financial schema change is needed.

## Persistence and write checks

Configuration schema 1 stays readable and preview-only. Schema 2 is emitted only
when a contract exists; its closed fields reject unsupported contract versions
before save/restore. Changing a contract requires a new immutable pattern revision.
The existing encrypted backup/restore path carries the same document.

The deterministic SHA-256 covers generation, bank identities/senders, definitions,
enable states, revisions, contracts and history with length-prefixed encoding.
Reordering definitions preserves it; same-generation restores with changed content
do not. Automatic pattern facts require this hash and an enabled current id/revision
with a contract inside the financial Room transaction. Missing, corrupt, unsupported
or stale configuration refuses the mutation. The existing access lease prevents
edits/restores between the check and the write.

Explicit automation-confirming corrections and `USER_FINANCIAL_TYPE` retain manual
authority, while comparison against current effective evidence still rejects older
assemblies after an edit. Merchant/counterparty-only edits do not bypass the pattern
check. Existing protection of already linked manual decisions remains in force.
