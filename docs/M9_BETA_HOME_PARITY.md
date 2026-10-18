# M9 — Beta Home using production visuals and current Beta facts

Authority: ADR-19 / Notion page 12. Starting main: `73952d6971f0150d5ae4a9380bd02a45618235a6` (merged M8 PR #295).

## Result

The Beta Home follows the production default section order: account-movement hero,
account/card/loan/commitment shortcuts, period commitments, expense/average tiles,
purchase/overall daily chart, merchant spending, and recent financial transactions.
The production logo/header, period geometry, metrics, contextual help, commitment band,
chart controls, ranked bars and recent rows use neutral ui-kit renderers. Existing
production wrappers keep their resource formatting, financial models and callbacks.
The approved logo bytes move into ui-kit rather than creating a second asset.

The old bank/asset/import/approval workspace no longer follows the financial Home.
Its explicit Beta data-management toolbar entry retains those existing actions.
The BETA badge and Return remain visible. The four existing primary tabs remain.

## Financial source and scope

Home reads the existing `BetaDashboardWorkflow` / `BetaContainer.dashboard` facade.
The Room adapter still owns the atomic current-ledger, registry, adoption, evidence,
FX and configuration snapshot. Presentation never parses messages, posts money,
converts FX, sums daily points, or derives account/card/loan balances.

`SpendingTrend.totalSpending` exposes the elapsed-point total already calculated
alongside the daily average by `BetaAnalysisBuilder`. It preserves negative
refund-heavy values, known zero and unavailable FX/coverage. This also prevents
future-dated ledger rows or a selected chart category from changing the Home tiles.
The original financial builder is cross-run in the existing parity test.

The hero reads portfolio account movement, not a sum of observed balances.
Its income/outflow actions show the precomputed fleet flow buckets. Commitment
remaining is supplied by its builder and is never reconstructed from member cards.
The quick tiles always use the full OVERALL elapsed trend; the chart independently
switches between PURCHASES and OVERALL. That choice is stored in the isolated,
backed-up Beta preference file and cannot affect ledger values or the analysis
screen's separate saved chart choice.

The selected window is explicitly labeled **Calendar month**. The date range uses
Riyadh month boundaries; no salary period is invented. The existing period and
commitment configuration route remains available. Missing commitments, trends,
merchant or current-ledger coverage show unavailable, never an empty or zero claim.
Recent rows use the native transaction amount/currency/time, with exact transaction
identity for drilldown; the separate detail retains fee-free SAR, fee and FX facts.

## Verification

- Existing Beta storage/adoption/backup/correction and Finance Core tests remain enabled.
- Financial parity asserts the new elapsed total against the original production
  trend builder, including refund-heavy FX plus fees, known-empty/future windows,
  and unavailable FX.
- Room-backed Home journeys cover Arabic/English, light/dark, large text, chart
  preference restore, transaction/flow drilldown, and no dashboard database writes
  or additional ledger revisions.
- Frozen test-only production visual functions from starting main compare pixels
  before/after extraction on phone and unfolded widths in both languages/themes.
  They do not copy a parser or financial calculator into runtime.
- Existing navigation/settings/permission journeys follow the explicit workspace
  entry and retain their original preservation, confirmation and retry assertions.

Final commands, CI state, test counts and artifact links belong to the PR description.

## Remaining stage acceptance

This PR owns Home and its read-only entry points. Full account/card/loan detail
visual parity, full Home section rearrangement/customization, complete M10
onboarding/settings/navigation acceptance and long-history performance acceptance
remain separate increments. The existing persisted period/commitment configuration
and analysis category editor remain usable; this PR does not claim that their
screens already match production. No ML activation, schema change, production
migration, data reset or release/cutover is introduced.
