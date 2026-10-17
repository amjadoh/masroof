# M2.UI.1 — shared production theme and card

Source: [Clean Beta / ADR-19](https://app.notion.com/p/3fc44ee65d118130899bc774b292e434), with the [design file map](https://app.notion.com/p/3fc44ee65d118149a076ed887bdaa6bd). Base: merged M0 `bd21a352637b6ea3f23268e52af53f932db9915d`.

This is the first small PR within M2, not completion of the milestone. M6 and later stages remain closed.

## Ownership and reuse

- Move the fourteen existing production theme files and four original IBM Plex Arabic fonts into `:ui-kit`. Use the neutral `com.baraa.masroof.ui.theme` package. Production call sites only update theme imports; their rendering calls and behavior remain unchanged.
- Keep theme values, font files, shapes, typography, system-bar behavior and production call sites. The font resource import now uses the library namespace. Replace the theme's `AppLocale.isRtl` call with the equivalent display-only language check (English LTR, other configured languages RTL), removing the application dependency.
- Extract the exact production `MasroofCard` renderer into `ui-kit/.../ui/components/MasroofCard.kt`; the production function delegates to it with the same defaults and content.
- BetaActivity uses the shared theme for both normal and startup-failure content. Existing Beta appearance preference continues to select light/dark/system; no Production preferences are read.
- BetaDashboardPanel non-hero cards use the extracted production card. The existing hero renderer remains pending its own neutral display contract. No financial calculations enter Compose or ui-kit.
- Move the existing internal hero palette test to its owning module without changing assertions. Other production theme tests remain in app with updated shared-theme imports/package. Add a module boundary test and preserve the new module's reports in the existing CI artifact; no duplicate jobs or suites.

`M2_UI_1.json` records original and extracted file hashes. No parser/legacy runtime cleanup, data-schema deletion, data reset or migration occurs. Original sources are relocated with Git history, not discarded as obsolete implementations.

## Evidence and acceptance scope

`SharedUiExtractionParityTest` renders the frozen pre-extraction production card and extracted card with identical synthetic display values. It compares every pixel, retains before/shared PNGs and semantics/metadata, and verifies a semantic action callback for Arabic light/dark and English light/dark (including 1.3 font scale). It opens no financial database. The frozen renderer exists only in test sources; runtime has one shared card.

The source/hash manifest establishes unchanged production palette/typography/font/shape inputs; native pixel tests establish card extraction parity. They do **not** establish whole-screen dashboard parity, real-device/foldable coverage, all text paths, or complete navigation parity. Those remain required within M2/M9/M10.

## Remaining M2 work

- Extract dependency-free navigation, home/hero/chart sections and relevant common components from the real production implementation.
- Add neutral formatted display contracts and Production/Beta mappers, preserving unavailable values and finance ownership.
- Extend actual screen screenshot/semantics coverage across RTL/English, light/dark, loading/error/empty/populated states and adaptive/foldable layouts before declaring M2 complete.
- Do not open M6 until M2 acceptance is evidenced or the user explicitly changes scope.

## Data protection

Beta schema remains 10 and Parsing Lab schema remains 3. All Beta application/data services, new persistent entry state and migrations are unchanged. No real device is accessed and no DB is deleted. Production financial/business sources and golden expectations are unchanged; only production display ownership/delegation and build dependencies change.

Validation results belong to the PR's final tested SHA. Do not infer green CI from this document.
