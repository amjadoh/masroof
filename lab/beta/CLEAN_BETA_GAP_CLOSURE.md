# Clean Beta gap closure

Source: ADR-19, Notion page 12, reviewed 18 October 2026.
Baseline: main `5c1c2190` (PR #296). Branch: `feat/clean-beta-gap-closure`.
User requested all remaining gaps on one branch. Work stays sequential by stage.

User clarification: all changes must stay inside lab/. Production app/ and shared ui-kit/ source are frozen; the pre-existing user gradle.properties change is retained untouched. Temporary production visual-extraction edits were reverted before continuing. No production device/data/migration was used.

## Scope and acceptance

- M7: complete direction/execution/balance human labels and versioned normalized-token provenance; preserve existing Beta labels and financial authority. Optional ML stays off pending a consented dataset, offline evaluation, Android measurements and user promotion approval.
- M9: share production entity-detail visuals, complete persisted Home arrangement/visibility with cancel/apply semantics, and measure bounded long-history reads. Financial values remain Finance Core outputs.
- M10: durable fresh onboarding through bank/optional import/discovery/review/approval; financial-management settings only; maintenance behind an explicit developer-tools route; consistent primary navigation and restoration.
- Preserve production data, financial calculations and golden expectations. No production migration, release or model activation.

## Verification

Record actual completed checks and remaining user/device gates here as work proceeds. Do not treat old PR reports as validation of this branch.

## M7 implementation

Human labels now include direction, execution state, available balance and outstanding balance. Every new label stores source receipt time, normalized-token indices, tokenizer version, and HUMAN_CONFIRMED/HUMAN_CORRECTED provenance. Model version remains null because inference is disabled. Tokenization reuses BankEngine SourceText, including its original UTF-16 map and Arabic normalization.

Beta schema 11 -> 12 adds nullable metadata columns only. Historical annotations keep their original contents and null metadata rather than acquiring invented provenance. Existing labels, raw evidence, archives, ledger, revisions and protected production data are retained. Learning labels still cannot authorize posting or modify interpretation.

The existing encrypted Beta archive includes the label table; no plaintext export, background training, model download or promotion is introduced. M7.2-M7.5 require an explicitly supplied/consented human dataset, frozen template-separated holdout, actual candidate measurements and promotion approval. These are evidence gates, not a reason to enable a placeholder model.

Local verification: BankEngine tests, the full Beta JVM suite, and all seven native learning/migration tests passed on isolated Android 15 / API 35. The native set includes 11 -> 12 preservation and existing 10 -> 11 / 1 -> 11 schema checks.

## M9 implementation

- Beta-only detail layouts reuse unchanged ui-kit primitives and mirror the existing production geometry. No production source, formatter or callback is changed. Beta account/card/loan details display precomputed current facts, observation dates, configured periods and unavailable values; facility due appears on the facility detail only; member-card details explain that shared authority without repeating a due value.
- Debit gross/refund totals are computed in BetaAnalysisBuilder and checked against the original production trend builder, never added in Compose.
- Home section order/visibility and quick metrics have a draft-only editor using a Beta-only layout-choice row with the existing production tokens. Apply uses compare-and-save in isolated Beta preferences; dismissal discards. Layout is included in existing Beta backups. Financial period configuration remains accessible from the layout editor.
- A native synthetic 10,000-current-row test checks three complete reads, constant query count, no audit/raw-body scans, no read-side writes and explicit overflow rejection. API 35 emulator cold/warm reads were 7,987 / 6,257 / 5,912 ms while local builds were running. The test budget is 30 seconds per read; these are emulator measurements, not a promise of phone latency.

Verification: the 10,000-row native history check and all 12 Beta-only Home/detail/layout UI journeys passed against the unchanged shared ui-kit. Native expectations use localized money and assert facility-only due display; financial fixture values are unchanged.


## M10 implementation

New welcome acknowledgement atomically starts a persisted Beta-only BANK -> IMPORT -> ASSETS -> REVIEW -> COMPLETE journey. Bank choice is explicit; import is optional and only its existing Start action requests READ_SMS/reads the inbox. Closing import returns to the saved step without scanning. Discovery and manual asset management require explicit ownership; interpretation labels and financial approval remain distinct actions. Setup may finish without importing or approving uncertain evidence. Existing acknowledged Beta installations do not restart setup. Unknown future progress fails closed without deleting data.

The four primary tabs remain visible on normal financial routes, show their actual selection, discard unsaved drafts on navigation and are disabled during busy writes. Settings contains only accounts, cards, loans and commitments. Account bank scope and owned entities use stable bank-qualified identities. Commitment management reuses the existing revision-checked draft workflow, with salary/statement configuration excluded from that settings category. Appearance, backup, restore, reset and encrypted archives remain available only through the explicit developer-tools workspace route.

Local targeted checks passed: new/reopened onboarding state and backup restoration, full fresh optional journey, current-engine discovery -> explicit ownership -> financial approval -> Home -> reopen with one posting, existing-data preservation, confirmed fresh-start safety, financial-management-only settings, developer-tools restoration and primary navigation (11 native tests). The complete isolated Beta Android suite also passed: 148 tests, no failures or skips. JVM verification passed 244 Beta + 56 BankEngine + 178 Finance Core tests (478 total), with no failures/skips. Beta lintDebug passed.

A separate run without concurrent builds measured the same 10,000-row read at 6,762 / 5,998 / 5,759 ms on this emulator. Real-phone latency and manual production/Beta comparison remain acceptance work.


## Final local verification

- `:lab:beta:testDebugUnitTest :lab:bank-engine:test :lab:finance-core:test :lab:beta:lintDebug`: pass; 478 JVM tests, no failures/errors/skips, and Beta lint passes.
- `:lab:beta:connectedDebugAndroidTest`: 148 native tests pass on an isolated Android 15 / API 35 emulator, no failures/errors/skips.
- Existing `scripts/beta-process-death.sh`, run against only com.baraa.masroof.lab.beta.test: actual permission deny/grant and 39 separate instrumentation phases pass, including the persisted BANK onboarding step after a new process, evidence/approval/correction/label COMMIT boundaries and restore/WAL recovery.
- Synthetic native screenshots inspected for account details, bank onboarding and financial-management settings. Production app/ and ui-kit/ have no source changes. The pre-existing gradle.properties edit remains outside this work.

## Remaining acceptance gates

The implementation is ready for review and a fresh isolated Beta trial. User comparison on the same real phone, exact visual acceptance across its supported window configurations, and phone latency remain unverified. Automated synthetic screenshots and emulator timings are not substitutes for those checks. Optional ML stays OFF: consented trusted-label dataset, template-separated holdout, candidate safety/quality measurements, Android size/heap/latency and explicit promotion approval are still required. No production migration, cleanup, release or cutover is authorized by this branch.

## PR 299 integration after PR 298

Main is incorporated into the M9 detail branch. BetaAssetDetail replaces BetaEntityDetail and its unused layout helpers. Accounts show cashPosition (including internal transfers); debit gross/refund facts from PR 298 remain visible; facility due appears only on the facility screen, with explanatory text on members. Home customization, learning-label capture and M10 onboarding remain intact. Duplicate loan-type resources are removed. Period display uses localized dates.

Local verification: 246 Beta JVM tests pass with no failures/errors/skips; Beta lint and Android test APK assembly pass. Sixteen targeted API 35 native tests pass across asset details, dashboard navigation, Home layout and the clean onboarding journey. New native checks cover internal-transfer cash position and debit gross/refund presentation. Synthetic Arabic account/card screenshots were inspected. These checks do not close real-phone acceptance or the ML training/evaluation gates.

The user requested removal of the offline Python training/benchmark tools and their sample annotations. The directory and its CI step have been deleted. Beta learning labels and correction capture remain intact; ML training/evaluation and runtime integration remain unfinished.

## Device-trial follow-up: setup and automatic financial processing

User testing found the initial journey unclear: importing showed all messages as review tasks and finishing setup could lead to an unexplained empty Home. The user requested lab reset in Settings and automatic addition of clear transactions after confirming accounts/cards.

The flow is now bank selection -> optional period import -> confirmed accounts/cards -> remaining exceptions -> Home. During initial import the message list stays hidden; a completed scan offers Continue to accounts and cards. Discovery groups repeated observations for display, and an already confirmed unique owner does not need confirmation for each message. Ambiguous owners retain explicit binding. Setup uses specific primary actions and explicit skip labels, displays saved progress and remaining-review counts, and can be resumed from Home or Settings without deleting evidence. Settings reset has a separate destructive confirmation and returns to the fresh welcome, preserving main Masroof, phone SMS and externally exported archives.

Automatic processing is a bounded write-side sweep over persisted BankEngine facts, separate from dashboard reads and human learning labels. Only current successful financial archives with no blockers, safe execution, bank time/amount and uniquely resolved confirmed ownership can post. Supported clear purchases, refunds, withdrawals, bills, ordinary fees, proven salary income and fully resolved card payments use Finance Core gates with authority NONE. Ambiguous transfers, financing attribution, missing/conflicting ownership and duplicate captures stay unposted. Security/informational messages never become transactions. Manual decisions, pending explicit approvals and corrections keep authority. Exact archive/registry/evidence bindings are checked in Room transactions. Committed batches and a durable versioned cursor make interrupted sweeps retryable; financial screens fail closed while required processing fails. Existing complete FX pairs are retained.

The message inbox defaults to real review cases, with all messages available separately. Ownership confirmation is counted as a grouped setup task, not a separate correction request for every understood message. Review opens either interpretation correction or an exact financial decision as appropriate. Old posted-transaction corrections remain available separately.

Follow-up validation: the full Beta JVM suite passed 256 tests with no failures/errors/skips, alongside Beta lint. A subsequent six-case targeted automatic-posting JVM run also passed, including the additional pending-manual-request case. Forty targeted API 35 native tests passed across setup, inbox, interpretation correction, financial approval, discovery, dashboard and Home layout. After extending the accounts-before-messages journey and pending-manual-request coverage, all ten targeted setup/automatic-posting native cases passed again. Synthetic setup, empty-Home and reset-confirmation screenshots were inspected. Main Masroof and shared ui-kit source remain unchanged; the pre-existing local gradle.properties edit is excluded. CI and a new real-phone trial remain pending.


## Production ownership flow parity

The follow-up replaces page-local proposal cards with the production-style account/card ownership list across the complete import. My account/My card and Not mine save one reference-level decision; registry identities remain distinct, and multiple matches require explicit selection. Ownership and required card configuration gate continuation. Credit facility creation requires the explicit primary-card checkbox and commits atomically with the card; debit links reuse consistent proved local account roles. Clear operations are processed only after this stage.

Beta schema 12 -> 13 adds ownership decisions and derived evidence exclusions without rewriting existing rows. External ownership is respected in subsequent imports, current asset visibility and ledger projections; audit and saved manual decisions remain preserved. Details are in OWNERSHIP_ONBOARDING.md.

Validation: 268 Beta JVM tests pass with no failures/errors/skips; lintDebug and Android test assembly pass. Thirty distinct API 35 native cases pass across ownership, automatic posting, schema preservation, setup, discovery, read consistency and the bounded 10,000-row reader. The four discovery UI cases were rerun after adding an assertion for RTL inside the shared theme; Arabic and English synthetic screenshots were inspected. No production source/data or shared ui-kit changes were made. CI results belong to PR #301; real-phone acceptance remains separate.
