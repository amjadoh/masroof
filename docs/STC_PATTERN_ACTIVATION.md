# Imported-message pattern approval

The STC journey must work when SMS account references belong to the counterparty
and never identify the user's account. Keep financial schema v18 and reuse the
existing pattern approval, historical batch, ownership and dashboard workflows.

Acceptance criteria:

- Approval starts with a matching imported, unresolved message when available;
  manual examples remain available. Imported evidence stays read-only.
- One page (selected by the user): imported message, owned account/reference
  meaning, inline financial preview and explicit save. A user can select a fixed
  owned account and declare that SMS references do not identify their account in
  one choice. Starting another account draft invalidates the prior preview.
- Explain a blocked preview with an actionable reason. Saving alone changes only
  the pattern configuration; applying pending messages remains a separate action.
- After save, show the approved state and a direct action for this pattern's
  pending messages. Show processed/posted/failed and bank review counts before
  and after, with the existing bounded-scope notice when applicable.
- Matching enablement, financial approval and pending application have distinct
  labels. Put revision history behind advanced options; revisions are saved
  versions, not additional patterns or proof of approval.
- Imported preview-only patterns explain why review is required. Ledger movement
  appears in dashboard after successful posting; no account balance is invented
  when SMS provides no balance evidence.

Implementation order: test real import → approval/application models → single-page approval
UI → visible pending results and import guidance → device journey and docs.
Production code stays in application/patterns and presentation/patterns/settings;
tests use JUnit/Robolectric under integration and existing Compose device tests.
Reuse MaterialTheme, Masroof components and theme tokens; add no dependencies.

Verification (ANDROID_HOME=/home/debian/Android/sdk):

```sh
./gradlew :app:testDebugUnitTest --tests 'com.baraa.masroof.integration.bank.StcSingleAccountJourneyTest' --tests 'com.baraa.masroof.application.patterns.*' --tests 'com.baraa.masroof.application.dashboard.*' --tests '*PackageDependencyRulesTest'
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.baraa.masroof.instrumentation.PatternFinancialActivationJourneyTest,com.baraa.masroof.instrumentation.PatternCurrentMessageJourneyTest,com.baraa.masroof.instrumentation.AlJaziraSharedPatternsJourneyTest
```

Verified: 344 targeted unit tests, including dashboard and package boundaries,
and all three device journeys on API 35. The STC fixture imports three messages;
approving incoming/outgoing patterns does not post them. Processing incoming only
posts two messages and changes bank reviews from 3 to 1; processing outgoing then
changes 1 to 0. Dashboard movement is SAR 150 in, 25 out and 125 net. Later
counterparty-reference changes still use the explicitly bound own account.
Device coverage also checks discarded previews, account-draft cancellation,
restoring a preview-only revision, future intake and protection of posted facts.
Light/dark screenshots were inspected. No production schema or financial
calculation changed. Reverting a completed import remains separate future work.
