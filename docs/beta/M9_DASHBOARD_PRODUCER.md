# Beginning M9.1 — Pure, read-only Beta dashboard production

This increment implements the existing `DashboardReader` boundary with executable pure builders in
`lab/finance-core/dashboard/producer`. It uses current committed financial snapshots and confirmed
registry facts. It introduces no Room/SQL adapter, schema, UI, parser, market request, posting call,
production repository access or legacy deletion. M9.1 remains in progress.

## One scoped source batch

`BetaDashboardProducer` calls `DashboardReadSource.load(query)` exactly once. The trusted future
storage adapter must provide one atomic consistency token covering current rows, lifecycle activity,
registry, optional structured observations, scoped review count, fees and coverage flags together.
The batch must cover the selected half-open query plus explicitly supplied salary/calendar/statement
windows. Extra out-of-scope active rows are rejected; there is no full-history/per-row fetch API.
One source call is **not** evidence of SQL query plans or database performance.

Only `DashboardLedgerRow.active` rows contribute to financial facts, evidence, counts and totals.
The durable correction adapter must join current correction lifecycle activity: ACTIVE is financial;
SUPPRESSED and REVIEW/pending are not. Audit/history reads must never be supplied as financial rows.
The source is responsible for truthful current/complete coverage; a pure builder cannot discover a
missing newer revision in a database it does not read.

The builder rejects duplicate keys (including two revisions), shared active evidence, foreign
environments, stale metadata/fee links, dangling parents and registry drift. It recomputes the
unchanged posting gate/classification and compares the complete stored command/classification.
Paired transfers re-run the checked bank/user proof; a caller-built `Classified` or plain self-transfer
cannot grant eligibility. Existing manual authority remains intact; metadata cannot promote it.

The existing snapshot cannot represent orphan credit cards, so an unlinked credit-card registry is
explicitly rejected instead of inventing a facility or silently omitting an owned card. Unlinked
debit cards remain representable. Suffixes are display facts only, never identity or ownership.

## Available results and explicit gaps

| Result | Meaning in this increment |
| --- | --- |
| Account/fleet flow | Complete selected-period SAR buckets; external remaining excludes internal transfers, cash position includes both legs |
| Selected money summary | All 13 existing `SummaryMetric` fields in the new `DashboardFinancialSummary`, not a full `AnalysisSummary` |
| Transaction facts | Original native amount/time/zone/revision/evidence/FX and resolved involvement; fee-free SAR equivalent and separate persisted international fee |
| Debit/card/facility salary values | Computed only when the caller explicitly provides a salary window |
| Card calendar/statement spending | Computed only for the separately supplied window; never relabels the selected query |
| Balance, available credit, due, loan remaining/installment | Present only from explicit structured persisted observations and their timestamps |
| Portfolio due/loan remaining | Null if any required observation is missing or non-SAR; facility due counted once for all members |
| Complete analysis/trends/merchant totals/commitments | Unavailable; `snapshot.analysis` and `snapshot.commitments` remain null |

`DashboardProduction` carries typed selected money plus unavailable sections. It never populates a
complete `AnalysisSummary` with empty, uncomputed trends or merchant lists. `DashboardReader.read`
rejects incomplete ledger/registry row coverage because the existing snapshot cannot mark its row
lists partial; `readWithCoverage` exposes a partial result with explicit coverage sections for a
future application coordinator. No current UI consumes either API.

Known complete empty financial history yields actual zero metrics/flows. Unknown ledger, registry
or review-count coverage cannot become a complete selected summary. Missing FX, unsupported stored
FX source or unknown applicable international-fee coverage leaves the relevant period's totals and
flows null, not partial totals or zeros. Native transaction facts remain available. This beginning
supports Beta/SAR display only and explicitly rejects another environment/display currency.

All SAR work uses the already merged `PersistedSarProjection`, including its existing fee rules.
Dashboard reads do not enrich FX, select a historical/market rate or persist a replacement pair.
The transaction's SAR equivalent excludes the fee while the appropriate spending aggregate includes
the linked SMS fee. Loan and card balances/statement due never come from sums of transactions.

Period card spending intentionally differs from the monthly summary: the unchanged
`DebitCardSpendClassifier` counts debit purchases and withdrawals minus refunds, excluding fees,
bills and settlements. `CreditCardOverviewBuilder.netSpending` counts credit purchases and fees
minus refunds, excluding bills, withdrawals and settlements. Facility/portfolio credit period
spending sums that member-card policy rather than the monthly CREDIT_PURCHASES indicator. Direct
facility purchases/refunds without member-card allocation make the member-card/facility/portfolio period
indicator unavailable; they still retain their separately defined monthly financial impact.
`TransactionFacts.cards` preserves broad card involvement; `debitSpendingCards` contains only the
debit-spend-qualified purchase/withdrawal/refund IDs, so presentation never repeats this policy.
An account-sourced purchase/withdrawal or account-destination refund may have a Mada association
in separate persisted card-involvement facts. This beginning has no such exact typed association;
spending for the related linked card and same-bank unlinked Mada stays null, as does the portfolio
Mada sum. A card confirmed under a different account retains its independently known result.
Complete genuinely empty periods still produce zero. No suffix-based card attribution is inferred.
Transaction involvement sets contain resolved exact IDs only; an empty set is not proof that an
unattributed account transaction never used a card. The affected spending fields stay unavailable.

Snapshots and nested collections are frozen and ordered by qualified registry/posting identities.
Optional observations may predate the selected period, but cannot exceed the read freshness or
query upper bound. Metadata binds exact posting revision and evidence. Chart labels/categories use
already resolved financial type/endpoints; refunds conservatively retain UNATTRIBUTED_REFUNDS in
this increment. Full chart/merchant attribution remains unavailable, not parser text interpretation.

## Verification and remaining acceptance

Focused tests cover gate/payload revalidation, actual unchanged hash-pinned monthly-calculator
comparison, negative refund-heavy values, unknown versus known-empty coverage, inactive rows,
environment/key/evidence conflicts, exact fee/metadata scope, independently selected periods,
typed parent/current-registry checks, shared facility dues, mixed/missing observations and immutable
stable results. Run `:lab:finance-core:test :lab:finance-core:verifyPureJvmDependencies`.
Local verification on 2026-10-17: **145 finance tests passed**, including 23 new producer tests and
122 existing tests, zero failures/errors/skips. The pure JVM dependency guard passed. Full PR CI
and existing stable-Android checks remain required before merge. The card policy regressions
characterize the read-only production sources; they are not a cross-run of the complete debit/credit
dashboard builders or a full historical-evidence attribution parity claim.

Still required: the real atomic Beta database batch adapter and lifecycle join/query plans; full
analysis/trends/merchant/hierarchy/commitment builders; explicit orphan-credit representation if
needed; full ledger-to-dashboard parity and long-history measurements; Beta screens and interaction
acceptance. Production adoption, real-data migration and deletion of old code remain deferred.
