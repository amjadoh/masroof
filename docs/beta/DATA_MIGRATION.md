# M3: Beta identity persistence and v18 migration inventory

Status: first integrated increment; M3 remains In Progress.

## Implemented in Beta only

Beta Room v2 adds `beta_asset` and `beta_asset_alias` to the existing shell schema.
Room exports v2 and generates the additive v1-to-v2 migration from committed schemas.
No destructive fallback is used. Existing Beta marker rows survive.

Identity is `(environment, bankId, localId)`, where localId is an explicit registry ID,
never inferred from last4. Registration is explicit and insert-only; duplicate IDs
cannot overwrite relationships. The registry accepts BETA only. Rehearsal uses a
separate database, never rows mixed into the live Beta file.

Debit parents must be accounts; credit parents must be credit facilities in the same
bank and environment. Composite foreign keys reject missing/cross-bank parents and
restrict parent deletion. Primary and supplementary roles are retained explicitly.
Unknown relationships remain null. Alias rows retain evidence IDs, permit collisions
within a bank, and return all candidates without selecting or merging them. Even one
suffix match is evidence, not a newly confirmed ownership decision. Asset and alias
registration is atomic. There is no update, delete, automatic discovery or posting API.

## v18 mapping decisions

This inventory is design input, not an executable production migration. The source
is the committed exported schema v18; no production database is opened. Every source
column and foreign reference is listed below, including nullable/type/primary-key
information. No source column is implicitly discarded.

- `bank_registry`: catalog/selection separation remains a later adapter. Bank IDs are
  retained as namespace inputs, not translated display labels.
- `account_registry`, `card_registry`, `credit_facility`, `loan_registry`: proposed
  target is Beta stable assets plus aliases and typed relationships. Their persisted
  IDs must be mapped explicitly; suffix collisions must remain separate. Current
  registry supports fresh explicit Beta assets only, not import.
- All remaining tables: deferred lossless staging mapping. Raw evidence and provider
  identities, parsed facts, financial transaction IDs/amounts/links, commitments,
  review decisions, corrections and processing retries need dedicated target tables
  and complete replay/parity tests before any conversion is executable.
- Preferences, ownership decisions, CardConfiguration and restore markers outside
  Room require a separate inventory; the exported schema does not cover them.

The future importer must copy a consistent read-only snapshot to
`masroof_beta_migration_test.db`, preserve source IDs through an explicit map, keep
user decisions and relationships, and verify counts, currency totals, FK integrity,
retry/resume and golden parity. It must never import into the empty Beta experience
or modify `masroof.db`. Production v18-to-target and historical MigrationTestHelper
coverage, interruption/resume, golden conversion and cutover are **not implemented**.
Only Beta v1-to-v2 migration is executable in this PR.

## Validation

- JVM/Robolectric: stable IDs and colliding aliases across reopen, bank scoping,
  debit/credit relationships and roles, failed-batch rollback, FK rejection,
  duplicate ID rejection and rehearsal environment rejection.
- Latest stable Android CI: MigrationTestHelper validates v1-to-v2 including the
  pre-existing shell marker, then persists an asset across a real database reopen;
  verifies foreign_key_check, integrity_check and unchanged production sentinel.
- Existing Beta maintenance isolation suite exercises backup/restore/reset on v2
  and verifies production preference and parsing-lab sentinels remain unchanged.

## Proposed identity projection (not an importer)

| Source | Proposed Beta identity mapping | Gate before conversion |
|---|---|---|
| Asset registries `id` | Explicit source-to-target `localId` map | Preserve IDs and distinguish colliding source rows; never derive from suffix |
| Asset registries `bankId` | `beta_asset.bankId` | Validate against bank catalog, preserve namespace |
| Accounts `maskedNumber` | Account reference alias plus evidence | Preserve original text; normalization/parity specification still required |
| Cards `last4` | `beta_asset.last4` and LAST4 alias | Non-unique evidence; never an identity key |
| Cards `cardType` | `beta_asset.kind` | Explicit validated credit/debit mapping, unknown stays unresolved |
| Cards `cardRole` | `beta_asset.creditCardRole` | Explicit PRIMARY/SUPPLEMENTARY mapping, unknown stays null |
| Cards `creditFacilityId` | Credit parent `parentLocalId` through ID map | Referenced credit facility must exist in same bank |
| Cards `linkedAccountBankId`, `linkedAccountMaskedNumber` | Debit parent `parentLocalId` through evidence resolution | Cross-bank/ambiguous links blocked; never infer solely from last4 |
| Cards `parentCardLast4` | Staging relationship evidence | Not a FK; cannot safely resolve when suffixes collide |
| Facilities `primaryLast4` | Staging primary-card evidence | Resolve via explicit identity map, never registration order |
| Registries `ownershipStatus` | Staging decision, then explicit OwnedAsset registration | Only confirmed ownership enters current registry; preserve other states outside it |
| Registries `firstSeenRawSmsId`, `lastSeenRawSmsId` | Staging evidence references | RawEvidence target/ID mapping pending; do not fabricate aliases |
| Registry display names, account/loan types, card networks | Deferred metadata target | Current minimal asset contract cannot represent all metadata; retain staging source fields |
| Bank display names and every non-identity table/column below | Deferred catalog/evidence/financial/decision target | No import until complete lossless mapping and tests exist |

## Complete v18 inventory

### `raw_sms`

Target: Deferred staging mapping; preserve every field.

| Source column | SQLite type | Required | Primary key |
|---|---|---|---|
| `id` | TEXT | True | True |
| `sender` | TEXT | True | False |
| `body` | TEXT | True | False |
| `receivedAtEpochMillis` | INTEGER | True | False |
| `deviceMessageId` | TEXT | False | False |
| `bodyHash` | TEXT | True | False |
| `dedupeKey` | TEXT | True | False |

Foreign references: none declared in source schema (application links still require audit).

### `raw_sms_provider_alias`

Target: Deferred staging mapping; preserve every field.

| Source column | SQLite type | Required | Primary key |
|---|---|---|---|
| `providerMessageId` | TEXT | True | True |
| `rawSmsId` | TEXT | True | False |

Foreign references: `rawSmsId` -> `raw_sms` (`id`), delete=RESTRICT, update=CASCADE.

### `parsed_event`

Target: Deferred staging mapping; preserve every field.

| Source column | SQLite type | Required | Primary key |
|---|---|---|---|
| `id` | TEXT | True | True |
| `rawSmsId` | TEXT | True | False |
| `bankId` | TEXT | True | False |
| `messageFamily` | TEXT | True | False |
| `direction` | TEXT | False | False |
| `amountDecimal` | TEXT | False | False |
| `amountCurrency` | TEXT | False | False |
| `purchaseChannel` | TEXT | False | False |
| `sourceAccountBankId` | TEXT | False | False |
| `sourceAccountMaskedNumber` | TEXT | False | False |
| `destinationAccountBankId` | TEXT | False | False |
| `destinationAccountMaskedNumber` | TEXT | False | False |
| `cardBankId` | TEXT | False | False |
| `cardLast4` | TEXT | False | False |
| `merchant` | TEXT | False | False |
| `counterparty` | TEXT | False | False |
| `occurredAtEpochMillis` | INTEGER | False | False |
| `bankNetworkType` | TEXT | False | False |
| `confidenceScore` | REAL | True | False |
| `confidenceReasons` | TEXT | True | False |
| `parseStatus` | TEXT | True | False |
| `transactionReference` | TEXT | False | False |
| `availableBalanceDecimal` | TEXT | False | False |
| `availableBalanceCurrency` | TEXT | False | False |
| `outstandingBalanceDecimal` | TEXT | False | False |
| `outstandingBalanceCurrency` | TEXT | False | False |
| `biller` | TEXT | False | False |
| `billerCode` | TEXT | False | False |
| `occurredAtLocal` | TEXT | False | False |
| `cardSmsChannel` | TEXT | False | False |
| `paymentDueDate` | TEXT | False | False |
| `exchangeRate` | TEXT | False | False |
| `internationalFeeDecimal` | TEXT | False | False |
| `internationalFeeCurrency` | TEXT | False | False |
| `labeledForeignAmountDecimal` | TEXT | False | False |
| `labeledForeignAmountCurrency` | TEXT | False | False |
| `loanType` | TEXT | False | False |
| `debitSourceAccountLast4` | TEXT | False | False |
| `salaryIncomeWording` | INTEGER | False | False |

Foreign references: `rawSmsId` -> `raw_sms` (`id`), delete=RESTRICT, update=CASCADE.

### `bank_registry`

Target: Deferred staging mapping; preserve every field.

| Source column | SQLite type | Required | Primary key |
|---|---|---|---|
| `bankId` | TEXT | True | True |
| `displayName` | TEXT | False | False |

Foreign references: none declared in source schema (application links still require audit).

### `account_registry`

Target: Beta asset/alias design; executable import deferred.

| Source column | SQLite type | Required | Primary key |
|---|---|---|---|
| `id` | TEXT | True | False |
| `bankId` | TEXT | True | True |
| `maskedNumber` | TEXT | True | True |
| `ownershipStatus` | TEXT | True | False |
| `displayName` | TEXT | False | False |
| `accountType` | TEXT | True | False |
| `firstSeenRawSmsId` | TEXT | False | False |
| `lastSeenRawSmsId` | TEXT | False | False |

Foreign references: none declared in source schema (application links still require audit).

### `card_registry`

Target: Beta asset/alias design; executable import deferred.

| Source column | SQLite type | Required | Primary key |
|---|---|---|---|
| `id` | TEXT | True | False |
| `bankId` | TEXT | True | True |
| `last4` | TEXT | True | True |
| `ownershipStatus` | TEXT | True | False |
| `displayName` | TEXT | False | False |
| `cardNetwork` | TEXT | False | False |
| `cardType` | TEXT | False | False |
| `linkedAccountBankId` | TEXT | False | False |
| `linkedAccountMaskedNumber` | TEXT | False | False |
| `parentCardLast4` | TEXT | False | False |
| `cardRole` | TEXT | False | False |
| `creditFacilityId` | TEXT | False | False |
| `firstSeenRawSmsId` | TEXT | False | False |
| `lastSeenRawSmsId` | TEXT | False | False |

Foreign references: none declared in source schema (application links still require audit).

### `credit_facility`

Target: Beta asset/alias design; executable import deferred.

| Source column | SQLite type | Required | Primary key |
|---|---|---|---|
| `id` | TEXT | True | True |
| `bankId` | TEXT | True | False |
| `primaryLast4` | TEXT | True | False |
| `displayName` | TEXT | False | False |

Foreign references: none declared in source schema (application links still require audit).

### `loan_registry`

Target: Beta asset/alias design; executable import deferred.

| Source column | SQLite type | Required | Primary key |
|---|---|---|---|
| `id` | TEXT | True | False |
| `bankId` | TEXT | True | True |
| `loanType` | TEXT | True | True |
| `ownershipStatus` | TEXT | True | False |
| `displayName` | TEXT | False | False |
| `firstSeenRawSmsId` | TEXT | False | False |
| `lastSeenRawSmsId` | TEXT | False | False |

Foreign references: none declared in source schema (application links still require audit).

### `financial_transaction`

Target: Deferred staging mapping; preserve every field.

| Source column | SQLite type | Required | Primary key |
|---|---|---|---|
| `id` | TEXT | True | True |
| `type` | TEXT | True | False |
| `amountDecimal` | TEXT | True | False |
| `amountCurrency` | TEXT | True | False |
| `occurredAtEpochMillis` | INTEGER | True | False |
| `sourceContainerId` | TEXT | False | False |
| `destinationContainerId` | TEXT | False | False |
| `merchant` | TEXT | False | False |
| `counterparty` | TEXT | False | False |
| `categoryId` | TEXT | False | False |
| `appliedExchangeRate` | TEXT | False | False |
| `exchangeRateSource` | TEXT | False | False |
| `occurredAtZone` | TEXT | False | False |

Foreign references: none declared in source schema (application links still require audit).

### `financial_transaction_raw_sms_link`

Target: Deferred staging mapping; preserve every field.

| Source column | SQLite type | Required | Primary key |
|---|---|---|---|
| `rawSmsId` | TEXT | True | True |
| `transactionId` | TEXT | True | False |

Foreign references: `rawSmsId` -> `raw_sms` (`id`), delete=RESTRICT, update=CASCADE; `transactionId` -> `financial_transaction` (`id`), delete=CASCADE, update=CASCADE.

### `commitment`

Target: Deferred staging mapping; preserve every field.

| Source column | SQLite type | Required | Primary key |
|---|---|---|---|
| `id` | TEXT | True | True |
| `name` | TEXT | True | False |
| `amountDecimal` | TEXT | True | False |
| `amountCurrency` | TEXT | True | False |
| `transactionDateIso` | TEXT | True | False |
| `recurrence` | TEXT | False | False |
| `dueDateIso` | TEXT | False | False |
| `active` | INTEGER | True | False |
| `pauseIntervalsJson` | TEXT | True | False |
| `sourceTransactionId` | TEXT | True | False |
| `createdAtEpochMillis` | INTEGER | True | False |
| `updatedAtEpochMillis` | INTEGER | True | False |

Foreign references: none declared in source schema (application links still require audit).

### `review_item`

Target: Deferred staging mapping; preserve every field.

| Source column | SQLite type | Required | Primary key |
|---|---|---|---|
| `id` | TEXT | True | True |
| `rawSmsId` | TEXT | True | False |
| `kind` | TEXT | True | False |
| `status` | TEXT | True | False |
| `reasons` | TEXT | True | False |
| `createdAtEpochMillis` | INTEGER | True | False |
| `updatedAtEpochMillis` | INTEGER | True | False |
| `resolvedAtEpochMillis` | INTEGER | False | False |
| `resolutionKind` | TEXT | False | False |
| `resolvedTransactionId` | TEXT | False | False |

Foreign references: `rawSmsId` -> `raw_sms` (`id`), delete=RESTRICT, update=CASCADE.

### `user_correction`

Target: Deferred staging mapping; preserve every field.

| Source column | SQLite type | Required | Primary key |
|---|---|---|---|
| `id` | TEXT | True | True |
| `targetRawSmsId` | TEXT | True | False |
| `correctedMessageFamily` | TEXT | False | False |
| `correctedAmountDecimal` | TEXT | False | False |
| `correctedAmountCurrency` | TEXT | False | False |
| `correctedMerchant` | TEXT | False | False |
| `correctedCounterparty` | TEXT | False | False |
| `createdAtEpochMillis` | INTEGER | True | False |

Foreign references: `targetRawSmsId` -> `raw_sms` (`id`), delete=RESTRICT, update=CASCADE.

### `processing_retry`

Target: Deferred staging mapping; preserve every field.

| Source column | SQLite type | Required | Primary key |
|---|---|---|---|
| `rawSmsId` | TEXT | True | True |
| `createdAtEpochMillis` | INTEGER | True | False |
| `mode` | TEXT | True | False |

Foreign references: `rawSmsId` -> `raw_sms` (`id`), delete=RESTRICT, update=CASCADE.
