# M8.4 continuation: executable FX source and date selection

Status: In Progress. This increment supplies an offline policy and executable comparison, not
runtime enrichment or full M8.4 closure. Beta-only; no Room/schema, production app/data, parser,
original calculator/oracle, old hash, golden or legacy deletion changes. The concurrent durable
correction path alone owns Beta migration 5 to 6. M9 can use the already merged stored-rate
projection without depending on this PR.

## Policy and boundaries

`FxEnrichmentPolicy` reads existing resolved `FinancialClassificationInput` plus explicit typed
persisted evidence facts, their original linked-event order and exact stored FX columns. It first
uses the existing financial gate. It neither parses a message nor infers ownership, financial
authority, bank timezone, merchant alias, period or rate source. The adapter supplies actual bank
policy zones; the policy has no second bank-to-zone map and never consults handset timezone.

Resolution follows the original `TransactionSarEquivalentResolver`:

1. A complete stored rate/source pair wins and is never replaced. Native SAR skips conversion.
2. The first existing record in original linked-event order supplies its SMS rate/fee. A later
   rate-bearing record does not silently become the transaction's own linked record.
3. Otherwise, exact normalized merchant + currency history at or before the transaction instant
   and no older than 30 days supplies the newest instant, then greatest event ID. Merchant keys
   use NFC, trim, whitespace/punctuation spacing normalization and root-locale lowercase only.
   A null transaction merchant permits linked merchant fallback; a blank one does not.
4. Otherwise, the injected provider receives the foreign currency and transaction civil date in
   the valid stored transaction zone, else the explicitly supplied linked bank's fixed zone.
5. Missing zone/rate or a provider failure stays explicitly unavailable. Cancellation propagates.

Historical currency prefers a foreign event amount, else a labeled foreign amount. Historical time
prefers a local clock converted with the fixed bank zone, else that event's persisted transaction
zone. An unzoned local clock is ineligible; it does not fall through to event/inbox time. Without a
local clock, event instant wins over received instant. Missing merchant/foreign currency/rate/time,
future evidence and evidence older than 30 days are excluded. Like the actual original index,
historical rate selection does not filter by ownership or financial message family. It selects
typed exchange-rate evidence; the current financial row still passes the financial gate separately.

The existing `PersistedSarProjection` supplies precise per-row SAR conversion, SMS-only purchase
fee policy, refund fee exclusion and immutable native/derived outputs. A known complete pair is
preserved at full precision and with its source. A half-written pair is ignored as a whole and
replaced by one proposal containing both newly selected columns, never a new half combined with
the old half. Proposals bind key, revision, evidence, native currency and expected stored columns.

This is a proposal only. A future trusted application/storage adapter must compare that binding
and expected columns in one transaction, reject a late proposal if a complete pair already exists,
and persist a new interpretation/revision with normal financial authority. No write, revision bump,
posting, network client, scheduled enrichment or read-path mutation is introduced here.

Intentional stricter guards are not represented as old-oracle parity: conflicting duplicate evidence
IDs, mixed environments, inconsistent linked IDs/input FX columns, mismatched native currency,
nonpositive rates, unknown complete stored sources, invalid applicable fee currency and contradictory
resolved financial/market zones stay unavailable. Identical duplicate facts are idempotent.

## Actual original oracle

The test-only oracle manifest appends nine unchanged main source files for the actual complete
resolver/index, merchant normalization, provider declaration/implementation and their pure models/
timing dependencies, plus two existing fixture-source provenance entries. Existing hashes and source
files remain unchanged. Old fixture tests are not copied or rerun: ordinary app CI still owns them.

The original provider shares a file with its HTTP implementation. The test source set therefore
adds the app's already pinned OkHttp dependency and `org.json:json:20240303` to compile that file
unchanged. No fake original provider type or resolver stub is supplied. Cross-runs instantiate the
actual resolver with its real injectable provider interface and deterministic recording test doubles;
the original HTTP class is never constructed and no test requests a network rate. Main compile/
runtime dependencies remain Kotlin/JDK only and the existing pure JVM guard is unchanged.

Thirteen new tests cross-run all four currencies, stored/SMS/historical/market precedence, partial
pairs, fees/refunds, linked-record ordering, identical duplicates, merchant/currency exclusions,
inclusive 30-day limits, instant/ID ties, local/event/inbox clocks, fixed/persisted/unknown zones,
handset timezone independence and missing rates against the actual unchanged resolver/index.
Four separate regressions verify the deliberate corruption guards, cancellation/failure handling and
caller/output immutability. The existing converter/monthly financial cross-runs remain green.

`build/reports/m8-parity/fx-enrichment.txt` is a declared cache output included by the existing Lab
artifact path; no new CI job, emulator, matrix or duplicate suite. Completed JUnit results remain
authoritative. Run `:lab:finance-core:test :lab:finance-core:verifyPureJvmDependencies`.

Remaining: trusted application persistence/adoption of proposals, complete card/facility statement
dues and balances, loan remaining balances and full dashboard oracle, independent holdout and
approved performance budget. This PR does not claim those financial models are now equivalent.
