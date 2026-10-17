# M9.2 / M8.4: persisted observation projection

This increment remains entirely inside the isolated Beta database. It does not import production
repositories, access the production database, delete legacy code or adopt candidate-engine results.

## Schema and authority

Beta 7→8 adds only `beta_dashboard_evidence_facts`. Historical schemas 1–7 and all 18 old tables
remain unchanged. Each row is bound to one raw evidence identity, exact bank, adopted archive
fingerprint/observation revision, current financial correction/interpretation revision and complete
financial input. Foreign keys retain the evidence; `transactionId` is unique.

Approval, FX enrichment and active correction changes refresh this projection inside their existing
Room transaction. Failed projection writes roll back the financial change; the separately committed
explicit approval retry remains available. No background comparison grants financial authority.

The writer accepts the same strict preserved-legacy archive gate as financial adoption. It stores
typed amount/currency fee fields, bank transaction time, merchant/counterparty, role-bound balances,
facility dues/date and confirmed account-backed Mada involvement. The dashboard reads these fields
in one bounded SQL snapshot with current rows, lifecycle state and registry. It does not load SMS
bodies, parse an archive, fetch FX, write, scan audit tables or perform per-row repository queries.

The complete FX pair is unchanged. USD 23 × SMS rate 3.756957 rounds to SAR 86.41; the exact linked
SAR 1.99 purchase fee produces SAR 88.40 in period spending. Native transaction SAR equivalent
remains fee-free. Refunds and non-SMS FX keep the existing fee exclusion policy. Non-SAR fees,
missing projections and manual amount/time changes leave fee coverage unavailable. Explicit null
fee in the trusted baseline proves absence; absence of a projection does not.

Available credit belongs to its exact credit card; account balance requires a matching structured
account reference. Outstanding due belongs to the confirmed facility once. A bank-local asset id or
last4 alone never joins across banks. Account-backed Mada involvement requires the exact confirmed
debit card, parent account, channel and suffix; it does not move the financial endpoint or double
the account movement. Missing/changed attribution keeps card spending unknown.

Observations use the actual bank timestamp, not receipt time or a manually edited financial time.
Only observations at/before the snapshot/query bound are eligible. A newer held, suppressed,
edited or stale observation blocks fallback to an older amount. Invalidated target identities are
retained as tombstones with null amounts. Same-time duplicate/conflicting observations stay unknown.
There is no inferred zero, conversion of observed balances, or recomputation from financial flows.

## Upgrade maintenance and coverage

Migration creates an empty projection, deliberately leaving old facts unknown. The explicit,
idempotent `RoomBetaDashboardFacts.refreshPage(afterId, limit)` / container `refreshDashboardFacts`
performs bounded write-side maintenance; its returned cursor supports restart/retry. It skips
unapproved, held, unsafe, pending and inactive financial records and never posts a ledger row.
No dashboard read starts this maintenance or rewrites history. It is background/nonblocking because
missing rows remain explicitly unavailable, rather than showing an incorrect value.

The global review count is available only when each unresolved evidence identity has trusted
financial projection coverage. Overlapping adoption, retry and correction holds are counted once
per stable transaction. Unapproved informational/OTP/comparison evidence keeps the count unknown;
acknowledgement alone is never treated as financial completion. Complete adopted/suppressed sets
can prove zero review rows. Schema 6/7 reads retain their prior unknown review/fee coverage.

This is not completion of the whole dashboard: Analysis, Trends, Merchant Totals, Commitments,
loan observation adapters, informational-only statement adoption, complete UI parity and measured
M0 p50/p95 performance gates remain open. The SQL reader still decodes a bounded current financial
payload set because transaction timestamps are not normalized into SQL columns. Correction payload
size may grow with per-key decision history; the new facts table does not claim to solve that limit.
