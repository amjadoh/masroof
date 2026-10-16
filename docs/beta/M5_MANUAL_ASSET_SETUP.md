# M5 manual asset setup in Beta

Status: integrated M5.2 and part of M5.3, not full M5 completion.

## User workflow

Enable the supported bank in Beta, then choose **Add account or card**. A user can
create an account, a debit card linked to an account, a credit facility, and a
primary or supplementary credit card linked to that facility. Creation needs no
SMS, parser match, posting or ledger. Every save requires explicit ownership
confirmation. Opening, canceling or leaving a draft writes nothing.

Only the final four digits are accepted for accounts/cards. Arabic/Persian keyboard
digits normalize to ASCII; full numbers are rejected, never truncated. Facilities may omit a
suffix. A suffix is an alias, never an identity. The draft receives an independent
stable ID that survives retry and recreation. Replaying the exact create draft is
idempotent; a different draft with the same suffix creates a separate asset.

Configure an existing card to correct its suffix, debit/credit type, role and parent
without changing its stable ID or bank. Accounts and facilities keep their kind.
Editing remains available when the bank is paused; new assets require an enabled
bank. The form does not expose a field whose value cannot be persisted.

## Safety and persistence

All validation and changes to the registry/manual suffix alias are one Room
transaction. An account is the only debit parent; a facility is the only credit
parent. Environment and bank cannot cross. The typed one-level parent graph cannot
contain cycles. A facility allows one primary, and supplementary cards require a
primary in that same facility. Primary and supplementary cards both reference the
facility, not separate debt containers or a fabricated direct-primary ID field.

Edits compare the original saved asset to the current record atomically. A stale
form after another edit/restore is rejected instead of replacing the restored
configuration. The original snapshot and draft fields survive activity recreation.
Validation inspects affected parents/facilities only; unrelated older M3 records
are not silently normalized or allowed to block an unrelated new account.

Manual suffix corrections replace only the manual-confirmation alias. Existing
message evidence aliases remain intact as discovery evidence. No suffix match is
promoted automatically into ownership and no history or user decision is rewritten.

The container constructs its workflow against the current database on each locked
operation, so backup, restore and reset cannot retain stale stores. The UI serializes
writes with maintenance, refreshes from the store after success and blocks dismissal
while saving an asset. MaterialTheme and existing Beta spacing tokens are used.

## Schema boundary and remaining M5 scope

Beta stays on schema **3** in this PR: BetaDatabase and all schema JSON files are
unchanged. Existing asset/alias columns carry the saved manual configuration.
Production stores and application runtime sources are neither read nor changed.
No legacy code is removed.

- OWNED is represented by explicit creation of an `OwnedAsset`. EXTERNAL and
  candidate/unverified states are not claimed or offered here.
- Card network configuration needs persistent metadata and is deferred.
- M5.1 guarded SMS discovery, unknown-card/OTP handling and later evidence matching
  remain separate intake/interpretation work.
- M5.4 linking previous SMS/transactions and ledger fingerprint acceptance await
  actual Beta evidence and posting integration. This PR cannot create/replay a
  transaction; it has no posting dependency or callback.
- No production preference migration, SMS reading, permission request or automatic
  ownership inference is introduced.

## Validation

Unit tests cover explicit confirmation, stable-ID create retry, same-suffix
separation, typed links, role/primary guards, atomic failed updates, paused-bank
behavior, stale edit rejection after restore, unrelated legacy records, restart,
backup/restore/reset and unchanged production sentinels. Device UI tests cover
cancel/back, Arabic-digit account creation before SMS, saved-draft retry after
state restoration and debit-to-supplementary setup
with stable identity and durable facility linkage. Existing stable-Android CI owns
execution; no new emulator workflow or duplicate test job is added.
