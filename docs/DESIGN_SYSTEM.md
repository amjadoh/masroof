# Approved Masroof design

This document is the current UI reference. The implementation entry point is
`presentation/theme/MasroofDesign.kt`, version `approved-2026-10-10`.

## One source of visual values

| Concern | Authoritative source |
| --- | --- |
| Light/dark and semantic colors | `MasroofColors.kt`, `MasroofHeroColors.kt` |
| Brand/network colors | `MasroofNetworkColors.kt`, `res/values/masroof_brand.xml` for the Android launcher |
| Opacity and font weights | `MasroofDesign.kt` |
| Spacing and geometry | `MasroofSpacing.kt`, `MasroofDesign.kt` |
| Typography and font weights | `MasroofTypography.kt`, `MasroofFontFamily.kt`, `MasroofFontWeights` |
| Shapes | `MasroofShapes.kt` |
| Icon geometry | `MasroofIconSizes.kt` |
| Icon meanings | `presentation/common/MasroofIcons.kt` |
| Elevation | `MasroofElevation.kt` |
| Localized terminology | `res/values/strings.xml`, `res/values-en/strings.xml` |

Screens use these tokens through MaterialTheme and shared components. Do not add
numeric dp/sp or color literals in feature screens. `ApprovedDesignBoundaryTest`
enforces this boundary. Cards use radius 20, controls 12, screen/card padding 16,
flat elevation, and IBM Plex Sans Arabic. Filled light-mode buttons use onPrimary
white text. Account blue, card purple and liability amber each have paired
readable foregrounds in both modes.

## Shared screen behavior

The global header contains the brand and notifications. Home, financial
transactions, review and settings are in the bottom navigation. Home has one
interactive daily expenses chart. Account, credit aggregate, individual credit
and debit scopes reuse the same chart interaction. Detail pages display their
complete scoped transactions inline; tapping a transaction opens the existing
correction, ignore and commitment actions.

All entity summaries use the same neutral card shell, with type-specific facts:

- Account: calculated inflow/outflow/net movement, with its calculation described.
- Credit: recorded available balance, facility amount due and due date.
  Supplementary cards explain shared facility due; it is counted once in totals.
- Debit: linked account and net purchases/refunds, without a fictional credit due.
- Loan: recorded remaining balance, latest installment and its recorded date.

Arabic expenses terminology is «المصاريف», «متوسط المصاريف اليومية» and
«المصاريف حسب التاجر». Merchant analysis requires at least two eligible purchases.

## Financial chart scope

`application/dashboard/DashboardSpendingTrendsBuilder` computes every display
scope from the existing persisted dashboard context. Compose does not sum money.
Overall expenses include expenses, bills, fees, loan repayments, external outgoing
transfers and withdrawals, less refunds. Self-transfers and card settlements are
excluded to avoid double counting purchases. Credit/debit charts show purchases,
bills and ordinary fees less refunds. FX requires a resolved local equivalent;
unresolved foreign amounts are not silently added to SAR.

The daily mean uses all elapsed dates in the selected period including zero days;
completed periods use their full duration. It is not a fixed fourteen-day mean.
The selected date and amount are visible above the chart, with accessible
previous/next controls as an alternative to tapping a point.

The debug design catalog remains available under Settings → About and displays
the current tokens. Removed background gradients, raised-card shadows, entity
carousels, salary/statement credit-detail blocks and previous brand assets are
obsolete and must not be reintroduced.
