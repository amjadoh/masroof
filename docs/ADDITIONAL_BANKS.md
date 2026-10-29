# Additional banking senders

The production pattern store installs five additional declarative role profiles:

| Bank identity | Exact sender |
|---|---|
| `BANK_SNB` | `SNB-AlAhli` |
| `BANK_BARQ` | `barq app` |
| `BANK_EMIRATES_NBD` | `EmiratesNBD` |
| `BANK_SAB` | `SAB` |
| `BANK_ALINMA` | `alinma` |

They use the existing `MessagePatternSmsAdapter`, `PatternFactInterpreter`,
validation, ownership, reconciliation and durable-retry paths. There is no
parallel parser or database change. Installation adds absent bank definitions
without replacing user definitions, corrections or posted history.

## One-time account setup

Initial ownership setup and each bank's patterns page offer a shared account
sheet. Only explicitly owned accounts are selectable. Saving declares a fallback
local account and currency for abbreviated messages; the engine never infers a
default from the number of registry accounts. An observed account wins, and an
explicit card on a purchase prevents cash-account substitution. Loan funding and
personal-loan ownership require a separate affirmative choice. Card settlement
and ambiguous funding remain subject to the existing financial authority checks.

The choice is stored as a validated version-2 fact contract in immutable pattern
revisions. Dismissing the sheet discards the draft. Saving reprocesses eligible
pending evidence through the original batch workflow, preserving corrections and
already posted rows. The preview limit applies after bank routing; another bank's
queue cannot hide eligible messages. Repeated identical saves do not add revisions.

## Signed secondary balances without changing ledger money

Transaction `Money` remains a non-negative magnitude. A negative available
balance has its own optional magnitude (`negativeAvailableBalance`) in engine,
parse and dashboard read models. Its sign is stored losslessly in the existing
nullable `availableBalanceDecimal` column and restored by the mapper. No table,
column, Room version or identity hash changes. Currency pairing remains required;
negative transaction principals remain invalid. Latest negative snapshots replace
older positive snapshots and display with a minus sign. Bundled new-bank profiles
explicitly opt into this secondary-balance grammar. The original AlJazira
definitions and frozen reference files remain unchanged.

Known destination bank plus suffix is a strong bridge for an owned cross-bank
transfer, just as an unresolved destination bank plus that suffix already was.
Amounts, time compatibility, ownership and mutual uniqueness still apply; a
same-container pair is rejected. Credit-card-funded bills use the card container,
so a default cash account is not charged for them.

The shared role grammar reads compact labeled amounts, suffix-labeled currency
amounts, masked card suffixes and the observed Arabic inline purchase clauses.
Original evidence offsets remain attached to the original SMS. The engine
version advances because those grammar capabilities changed.

## Conservative limits

- An unknown local account is never invented for a Barq wallet transfer; select
  or manually add the real wallet IBAN suffix and explicitly approve the link.
- EmiratesNBD financing messages with only two account digits or a principal
  without a proven currency remain in review until the funding account, currency
  and personal loan are explicitly confirmed. A reminder or a financing
  statement is not evidence of a new debit.
- The additional SAB payment confirmation is informational; the primary
  settlement notification is the financial evidence, when complete.
- Unrecognized layouts, conflicting bank roles, missing facts and declined
  operations remain reviewable. Support for a sender does not imply automatic
  posting of every message it sends.
- Old SNB one-time-password wording is protected by the shared security gate.
- Historical import and ownership confirmation still require the existing
  user workflows. Updating the app does not reclassify posted transactions.

## Validation

Fixtures under `testdata/multibank` contain fictional names, values, dates and
identifiers. Each bank participates in the shared adapter contract and the
multi-adapter routing checks. `FiveBankSmsTest` checks family, amount, channel,
review boundaries and informational dispositions. An optional local-only corpus
can be supplied with `MASROOF_PRIVATE_CORPUS`; it reports counts without bodies.
Never upload that corpus or its financial contents.
