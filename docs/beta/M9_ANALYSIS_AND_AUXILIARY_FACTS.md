# M9.2 / M9.3 with M8.4: Beta analysis and non-financial observations

This increment targets the isolated Beta database only. It never opens production storage, changes
production source, deletes legacy code, imports production containers or grants authority to a
candidate-engine comparison. All fixtures and device data are synthetic.

## Schema 9 and background maintenance

Only this PR owns Beta 8→9. Historical schemas 1–8 stay unchanged. The migration adds
`beta_dashboard_aux_facts` and a nullable `messageFamily` column to the existing financial fact
projection. All prior columns, tables and rows remain intact. New projections start empty/unknown;
new family values stay null on old rows until explicit bounded `refreshDashboardFacts` maintenance.
This is nonblocking background maintenance because unknown values are shown unavailable.

Auxiliary facts accept only the preserved legacy engine/rules/contract, an exact matched bank route,
completed interpretation attempt, no blockers/errors/conflicting diagnostics, and a matching evidence
identity. `NON_FINANCIAL` + OTP/notice/balance families can prove a non-financial disposition without
financial approval. A comparison acknowledgement, partial parse, unknown family, candidate engine,
ambiguous route or missing archive cannot do so. Completion writes the auxiliary row atomically;
projection failure keeps the evidence attempt retryable. It never creates a ledger posting.

Statement/card and account observations require unique, bank-qualified structured references to
confirmed assets. Only a typed informational `STATEMENT` channel proves statement dues; the auxiliary
row persists that proof explicitly. Purchase/refund outstanding balances never supply or block a
statement, including untouched schema8 financial projection rows with a null message family. Their
historical columns/archives remain stored, while new financial projections stop emitting a due.
Card dues belong to the confirmed facility once. Conflicting references and
collisions stay unknown. OTP never supplies money observations. Bank local time uses the existing
AlJazira Riyadh policy; absent bank timestamps use captured receipt time for informational snapshots,
matching the original statement reader. Neither a due date nor a manual transaction time becomes
the observation timestamp.

Loan remaining/installment/type require an explicitly approved current loan-attributed posting from
a successful financing-installment archive. A parsed loan label alone never selects an owned loan.
Manual amount/time changes, held/suppressed financial lifecycle, pending retry, changed archive or
registry invalidate the observation. Native amounts and the full stored FX pair are never rewritten.

The dashboard reads a bounded consistent snapshot of typed columns. It never re-parses an SMS or
archive, fetches FX, scans append-only audit history, writes, or queries evidence per row. Current
completed outcome generations gate auxiliary rows. Invalidated rows remain tombstones so a newer
held/edited observation cannot revive an older apparently valid balance. Same-time collisions stay
unknown. Registry changes invalidate observation roles conservatively while a still-valid OTP/notice
disposition stays non-financial.

## Pure calculations and unknown coverage

Existing `DashboardSnapshot.analysis` is now executable: complete ledger/registry/review coverage
produces the complete summary metric map, ordinary daily trend, explicit financial-outflow trend,
account/card/asset/category scopes, and merchant ranking. Daily points include elapsed zero days,
stop at `asOf` in the query zone, retain signed refunds, and use HALF_EVEN daily averages. Future
periods have known empty points and zero average. Missing FX/fee/involvement/family facts produce
unavailable values, never a partial total presented as zero. The projection rejects more than ten
years or 250,000 trend points explicitly, rather than allocating unbounded memory.

Ordinary spending excludes settlements, transfers, withdrawals and loan repayments. Explicit
financial-outflow scope includes withdrawals/transfers/loans but excludes card settlements to avoid
counting purchases twice. Accounts use actual projected movement buckets, including settlements;
internal transfers affect their source account only and are excluded from aggregate account trend.
Credit-card asset trends use ordinary spending. Mada trends require exact proved involvement.
The purchases scope uses the persisted parse-time family and the original card/withdrawal policy;
missing family proof keeps that scope unavailable. Chart selection never changes full-scope totals.

Merchant grouping uses the unchanged original NFC, conservative punctuation/whitespace and ROOT
case policy. At least two purchases are required; refunds do not count toward the threshold but
subtract from spending. Labels and sorting are deterministic. The original context uses fee-inclusive
SMS SAR conversions for merchants and trends. The transaction-row SAR equivalent remains fee-free.
An independent test cross-runs hash-pinned original FX, merchant and daily-trend calculators with two
foreign purchases, linked SMS fees and a refund. Additional cases exercise unknown vs known-empty,
period clipping and financial movement scopes. This is an expanded parity increment, not full M8.4
acceptance across every bank, recurrence or UI combination.

## Commitments and remaining acceptance

The builder produces known facility/loan rows for an explicitly supplied salary window, and can
aggregate authoritative user occurrences supplied through the pure batch contract. The runtime has
no authoritative recurring-user-commitment store yet, so commitment totals remain null and the
COMMITMENTS section remains unavailable. A calendar-only caller does not silently invent a salary
period. PaymentStatus has no UNKNOWN: incomplete financial rows or an unproved prior-payment window
must not become an UNPAID row. Non-SAR or missing obligation facts cannot become known SAR totals.

Still open: complete recurring commitment persistence/matching, cross-period settlement-window
coverage, salary/statement configuration authority, broad multi-bank and independent holdout
acceptance, full dashboard parity, and measured M0 p50/p95/performance budgets. Schema9 is supported
by the separate recovery PR only after integration; this PR owns the migration itself. No phase is
marked globally complete by this increment, and no merge is authorized here.
