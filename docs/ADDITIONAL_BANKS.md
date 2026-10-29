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

The shared role grammar reads compact labeled amounts, suffix-labeled currency
amounts, masked card suffixes and the observed Arabic inline purchase clauses.
Original evidence offsets remain attached to the original SMS. The engine
version advances because those grammar capabilities changed.

## Conservative limits

- An unknown local account is never invented for a Barq wallet transfer.
- EmiratesNBD financing messages with only two account digits or a principal
  without a proven currency remain in review. A reminder or a financing
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
