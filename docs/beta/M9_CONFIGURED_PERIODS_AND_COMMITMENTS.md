# M9.3 / M8.4: configured Beta periods and monthly commitments

This is an executable increment, not closure of all dashboard or commitments acceptance.
The shared UI/data contract was frozen in commit `620e051302069a9363dff9e0d181c93456453610`
and is intentionally the same commit on both independent PRs. The UI factory has an explicit
unavailable fallback on its standalone branch; this data branch binds it to durable Beta storage.

## Authority and lifecycle

Schema 10 adds only `beta_dashboard_configuration`, a single bounded, versioned configuration
snapshot. Schemas 1–9, old tables, financial payloads and legacy source are preserved. There is
no inferred salary, statement cycle or user recurrence after migration. Existing installations
stay unknown until the user explicitly saves settings. A saved empty recurrence collection is
authoritative known-empty; an absent configuration is not. No financial backfill is required.

Salary day is 1–31 in the explicit Asia/Riyadh zone. Statement closing days are 1–31 per confirmed,
bank-qualified Beta facility, shared by its member cards. Configuration saves validate the full
draft and compare its expected revision inside one Room transaction. Stale saves return a conflict;
invalid drafts leave all settings intact. Repeating the same canonical draft is idempotent.
The controls facade follows the Container's database across encrypted import, restore and reset.

Monthly user commitments have a stable ID, label, positive SAR amount, due day, first/optional last
due-month, and manually paid due-months. A manual paid occurrence never approves evidence, creates a
posting, changes a balance or supplies payment-matching authority. Short months clamp the due day.
There is one occurrence per actual due date inside the half-open salary window. Settings support
at most 100 facilities, 100 definitions, 1200 paid months per definition and a 256-KiB payload.

## Explicit period navigation

The atomic read's local asOf date selects the current configured cycle. Selecting another calendar
month shifts this current financial cycle by that calendar month's offset; periods are returned
with their actual start and exclusive end and must be labelled by those dates. This avoids a
current-month view reverting to the preceding salary after payday. Future/historical navigation
advances/reverses one whole cycle, rather than silently equating salary with the calendar month.

The preserved original policy applies only to configured salary day27: Friday shifts to26 and
Saturday to28. Other salary days clamp to month length without weekend adjustment. Statement
cycles start on their configured closing day inclusively and clamp without weekend adjustment.
No salary/statement period exists before explicit configuration; explicit caller-supplied periods
remain supported. The read consistency token includes the settings revision and complete content.

## Read-only settlement evidence

One bounded scan of current ledger rows supplies the normal selected/salary/calendar/statement
windows and additional CREDIT_CARD_PAYMENT rows since each proved statement's issuance through
readAsOf. This coverage is independent of the selected calendar window: payments before its start
or after its end can satisfy a due inside the displayed salary period. Payments in the future
cannot. Unrelated outside rows are removed before projection. No raw body/archive parsing, history
scan, per-row DAO query or write runs on dashboard load.

Calendar balance/statement observations keep their original query-end cutoff. If the displayed
salary tail extends beyond that cutoff, already-loaded newer proved statement/loan observations
(including held, tied or tombstone candidates) mark only affected obligation assets unavailable.
Their automatic status rows are omitted and combined totals stay unknown; unrelated payments alone
never invalidate coverage. Inert financial outstanding fields and purchases are not blockers. This
is conservative coverage, not a new historical statement/loan reconstruction model.

The pure producer validates every additional window against its facility's authoritative statement
and readAsOf, and admits outside rows only for correctly attributed credit settlements. Such rows
do not enter selected-period transactions, analysis, merchant totals or calendar spending. Missing
ledger/FX/fee/window authority remains unknown. A prior statement cannot be labelled UNPAID from an
incomplete salary-only payment set. Dues still require informational STATEMENT proof: purchase or
refund outstanding values do not become statement dues. Saved financial FX remains immutable and
existing fee-inclusive analysis/merchant projection is unchanged. Exact per-row fee-proof keys are
bound to the current posting metadata/fee identity; a missing fee proof on an unrelated outside
settlement cannot suppress an otherwise proved selected purchase or merchant total. The settlement
itself remains unavailable. The legacy global completeness flag still works for older callers.

## Validation and remaining acceptance

Tests cover day27 Friday/Saturday, custom day31, leap February, inclusive/exclusive boundaries,
current/before/after-payday and future/historical navigation, known-empty versus unknown,
revision conflicts, validation, reopen, no financial side effects, bounded read-only SQL,
settlements outside both display windows, payments after the calendar end, future payments, and
encrypted configuration round trips. Native migration tests verify9→10 and1→10, preserve old rows,
compare every TableInfo against a fresh schema10 and restore a synthetic encrypted schema9 archive.

This increment deliberately implements monthly manually paid obligations. Original automatic
transaction matching, occurrence exceptions, whole-salary-period pause/resume transitions and
non-monthly recurrence are not implemented or claimed equivalent. Missing facility due or loan
observations still keep combined totals unavailable. Independent holdout/bank parity, the full
M8.4 matrix and M0 performance budgets remain open. All tests use synthetic Beta data; no installed
production database, keys, APK or inbox are accessed.
