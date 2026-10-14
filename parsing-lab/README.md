# Masroof Parsing Lab — first working increment

The laboratory ships inside the normal Masroof APK, in debug and release builds. Open
Settings → Developer settings → Bank SMS parsing lab (the About shortcut also remains).
The application ID stays `com.baraa.masroof`; no separate installation or build property
is required. The lab engine/code and `masroof_parsing_lab.db` remain independent from
the normal financial database and services. Data from the previous separate experimental
installation is not automatically transferred into the normal app.

## Implemented

- Independent JVM engine and Android library; neither depends on `:app` or imports
  production Masroof packages. The host passes only a back callback.
- An independent copy of the AlJazira parser, normalization, validation, model dependencies,
  fixture corpus and regression tests. Origin hashes are in `copied-sources.json`.
  Documented local patches support standalone `Credit card 1234` lines without a colon.
  The initial POS template includes English amount/card/merchant/balance labels. Existing
  lab users can select Update English purchase template on the Banks tab; it saves a new
  rule version and preserves custom rules and past run snapshots. Re-run saved messages
  to see new lab results; production messages use the normal reparse-stored-messages tool.
- Copied theme, Arabic fonts and locale helpers, with library-owned resources.
- Literal, declarative bank rules: positive phrases, exclusions, amount/card/account/
  merchant labels, direction and family. No executable scripts or user-supplied regex.
- Exact sender routing, collision rejection, fixed OTP protection, copied classification
  arbitration with optional additive bank OTP phrases (built-in protection cannot be disabled),
  and a finalization gate. Supported money currencies: SAR/USD/EUR/GBP,
  maximum two decimal places, explicit currency required.
- Separate Room file `masroof_parsing_lab.db`; no production Room migrations or services.
- Bank configuration, immutable rule versions, dated imported SMS, preserved existing annotations, saved-message preview and immutable batch runs. Every run stores its rule
  snapshot, original sample text, expected answer and both dynamic/reference outputs.
- The Experiment tab imports the selected bank's inbox SMS using native calendar pickers.
  Both dates are inclusive in the bank's time zone (next midnight is exclusive). READ_SMS
  is requested on demand; the lab has no receiver or background worker. Exact configured
  sender matching filters messages before persistence. Original bodies and timestamps
  stay in the lab database only; imported messages have no automatic expected answers.
- Imports are atomic, limited to 1,000 matching messages and idempotent by bank/provider
  ID/timestamp/sender/body. Provider errors, denied permission or oversized ranges save
  nothing. Existing samples, annotations and results remain intact. Results date pickers
  filter already saved lab messages; they do not read the inbox.
- Rule-pack JSON import/export through a text field, latest/historical run selection,
  expected-field differences, baseline comparison for the AlJazira bank ID, and an
  explicitly confirmed lab-only reset.

The bundled initial rules and four synthetic samples demonstrate the workflow; they do
not establish production bank coverage. The cloned baseline remains available for comparison.
Baseline output is not used to manufacture expected answers.

## Current boundaries

- The manual sample-entry screen is replaced by import. Existing expected answers remain
  usable; newly imported messages are unannotated and do not establish an accuracy score.
- Merchant extraction expects a labeled, separate line. Bank transaction wall-clock dates,
  currency aliases, advanced extraction and field-highlighting UI are follow-up work.
- No ownership, posting, reconciliation, balances dashboard or production ledger writes.
- Batches are bounded to 1,000 samples. Calculation is off the UI thread and the completed
  run commits atomically. Leaving the lab cancels its screen scope; an unfinished run is
  not recorded as completed. Long-running background recovery is not enabled.
- No measured multi-bank accuracy claim. Inspect labeled results and held-out real-bank
  samples before using outputs outside the laboratory.

## Verification and cloud CI

```shell
./gradlew :parsing-lab:engine:test :parsing-lab:android:testDebugUnitTest
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

`parsing-lab.yml` runs these checks in GitHub Actions on the lab branch/PR and publishes
the normal debug APK with the integrated lab and test reports. Existing release workflow remains
unchanged. GitHub Actions is the cloud test runner; configuring a separate Codex cloud
development environment is not performed by this build configuration.

The tests cover the copied fixture corpus, OTP/financial conflicts, ambiguous amounts,
currencies, suffixes and directions, registration-order independence, import validation,
forbidden production imports, inclusive date boundaries, durable run/version snapshots,
and reset preserving an independent `masroof.db` sentinel.

## Removal

1. Remove the host entry: `ENABLE_PARSING_LAB`/dependency in app Gradle;
   DeveloperSettings/ParsingLab destinations and About callbacks; developer screen and
   the two `parsing_lab_strings.xml` resources. Remove matching navigation/smoke tests.
2. Remove the two includes in `settings.gradle.kts`, `parsing-lab/`, and its workflow.
3. Existing devices retain lab data until the lab reset or app uninstall.
   Code deletion alone is not database deletion. Do not delete `masroof.db`.

All host changes are confined to feature entry/navigation and build configuration.
