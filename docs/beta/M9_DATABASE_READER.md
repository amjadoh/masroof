# M9.1/M9.3: atomic Beta database dashboard reader

`RoomBetaDashboardSource` is the first real `DashboardReadSource` for the isolated
`masroof_beta.db`. Use `BetaDashboardProducer(source).readWithCoverage(query)`;
the existing `read(query)` intentionally refuses an incomplete current ledger.
No production container, repository, database, inbox or financial screen is used.

## Snapshot contract

- One Room transaction covers capability checks, adoption eligibility, pending
  processing/approval state, evidence identities, current ledger/correction state,
  and the confirmed asset registry. There are five SQL reads on schema 6 and six
  when the schema 7 adoption capability is present, independent of row count.
- Only current financial snapshots enter the projection. Suppressed corrections
  are intentionally non-financial; REVIEW, incomplete approval, retry, stale
  ownership, and unapproved comparison archives produce incomplete coverage.
  They cannot manufacture known zero spending. Comparison acknowledgement is not
  financial approval. An adoption held without a ledger row still blocks coverage.
- Schema 7 must include both `beta_financial_adoption` and
  `beta_financial_approval_retry`. ACTIVE/REVIEW adoption states are recognized;
  an unknown state fails closed. No migration or write is owned by this PR.
- A current active row must match its checksummed payload and active correction
  revisions. Invalid financial or correction data fails closed. Inactive financial
  audit payloads are not decoded or reactivated. Current asset configurations are
  checked against the actual source/destination/financing-loan dependencies and
  their parents, including paired observations. Unused snapshot assets do not
  invalidate an otherwise valid financial row.
- Corrected merchant/counterparty metadata is loaded from the durable correction
  payload and bound to that exact current posting revision and its evidence.
  No raw SMS body or archival interpretation JSON is read or reparsed.
- All supplied selected, salary, calendar and statement windows share the query
  zone. The source returns their union; the pure producer keeps each definition
  separate. All periods use inclusive `from`, exclusive `until`.
- `ReadRevision.token` hashes length-framed loaded values and the query/period
  configuration. Money, corrections, activity, registry changes and eligibility
  invalidate it. `asOf` comes from an injected clock. Collections are frozen.

## Explicit limits and unavailable facts

The current v6 ledger has an opaque typed financial payload and no SQL timestamp
index. This increment therefore loads a bounded current set and filters its typed
transaction times in memory. It never loads append-only financial history or
performs per-row repository queries. Defaults are 10,000 current/adoption rows,
2,000 assets and 10,000 raw evidence **IDs only**. Crossing a limit throws
`BetaDashboardReadLimitExceeded`; no truncated complete snapshot is returned.
The primary/composite-key query plans avoid history scans and sorting temp tables.
An indexed persisted time/read-model projection is still needed for larger datasets.

Coverage is conservative across the isolated Beta archive, including pending
evidence outside the selected time window. This is necessary until processing and
approval have authoritative indexed financial scope. A missing source fact remains
unknown: SMS international fee completeness, observed balances, statement dues,
loan observations, parser merchant/category facts and one authoritative global
review count are not invented. In particular, SMS FX without complete persisted fee
facts leaves period money totals unavailable. Native transaction facts remain visible.

The producer now removes `RUNTIME_SOURCE` from unavailable sections only when the
source declares this actual atomic runtime coverage. That does not mark M9 complete.
Analysis, daily trends/averages, merchant aggregates, commitments, dashboard UI,
visual parity and M0 p50/p95 acceptance remain open. Existing financial definitions
and pure producer calculations are unchanged.

## Verification

Real Room tests exercise half-open scope and independent period windows, immutable
read-only snapshots, revision invalidation/reopen, suppression/restore/security holds,
stale registry, unknown fee coverage, explicit overflow, corrupt rows, neighboring
schema adoption gates, unapproved completed comparison archives, fixed query counts
with long append-only history, query plans and concurrent access through two actual
Room database instances. Tests use isolated synthetic Beta data only.
