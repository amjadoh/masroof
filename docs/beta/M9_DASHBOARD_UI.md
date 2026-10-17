# M9 / M10 — isolated Beta dashboard UI

The actual `BetaActivity` home displays the persistent dashboard, not fixture-only
previews. This increment leaves the original app and data untouched. The BETA
badge and return action remain visible; import, discovery, manual assets, bank
selection, approval, correction, history and settings remain reachable.

`BetaDashboardWorkflow` owns the read scope and rejects mismatched responses.
The current calendar month is calculated in `Asia/Riyadh`, using local month
boundaries converted to half-open instants. Previous/next month changes only the
calendar scope. No salary or statement period is invented: these dates require
future authoritative configuration and remain explicitly unavailable meanwhile.

`BetaDashboardPanel` reads once per load through the existing application facade
on `BetaIo`. It displays the producer's precomputed summary, portfolio, account
flows, debit spending, facility dues, card periods, loan observations, commitments,
merchant totals and daily trend values. It contains no financial summation,
classification, FX conversion, bank parser or repository access. A nullable value
is unavailable; explicit computed zero remains zero. Missing coverage is visible.
Load failures clear previous values and require an explicit retry.

Routes, calendar month and selected bank-qualified registry identity survive
saved state. A home saveable-state holder retains selection through settings and
other existing routes. Removed identities show a missing-item state. Back exits
detail before the section; existing write busy/cancel/confirmation guards remain
owned by their financial panels. Dashboard loading is read-only and may be exited.

The module cannot depend on `:app`; it uses the existing neutral `BetaSpacing`
mirror, Material theme colors/typography/shapes and the production card/hero
layout patterns inspected in `MasroofCardComponents` and `DashboardSummaryHeroCard`.
Stacked values and horizontally scrollable section choices fit narrow windows,
RTL and large text. It adds no hard-coded feature dimensions or new app UI paths.

## Verification

JVM tests exercise Riyadh rollover, leap years, daylight-saving month boundaries,
half-open periods, absent salary/statement configuration and rejected mismatched
or failed reads. Actual Room-backed Compose tests cover read-only navigation,
unknown balances, RTL saved selection/month and failed-period retry without stale
money. An actual Activity journey crosses settings and recreation while preserving
dashboard selection and the BETA/return chrome. All fixtures are synthetic.

The financial device journey imports a synthetic Arabic purchase through the
preserved real parser, then explicitly approves it through `BetaContainer` before
loading the actual dashboard. USD 23 with full SMS FX 3.756957 and a SAR 1.99 fee
displays SAR 88.40 spending; the transaction's fee-free SAR 86.41 remains distinct.
The same journey verifies one SAR 2694.32 facility due, SAR 16958.89 available card
balance, and unchanged posting/FX/history across month and detail navigation.
Arabic RTL overview, account-unknown, approved facility/card and failed-period
screenshots are collected by the existing emulator job and instrumentation artifact.
No new emulator job or repeated workflow test command is introduced.

Incomplete registry and commitment coverage never becomes a known-empty list.
Merchant and trend coverage is distinct: an unavailable unselected trend scope
does not invalidate a complete selected ordinary-spending series; its nullable
points and average remain unavailable individually. Full-scope financial metrics
are never recalculated from chart or list selections.

## Remaining acceptance

This increment is an operational dashboard starting point, not completed visual
parity or full M9/M10 acceptance. Salary/statement configuration, editable recurring
commitments, customized chart/category selection, complete transaction drilldowns,
full original dashboard visual parity and performance/foldable device acceptance
remain open. No schema or migration is owned here; the sibling data PR owns any
additive Beta migration and richer facts. No old code is deleted.
