# Masroof Parsing Lab — shared local message engine

The laboratory ships inside the normal Masroof APK, in debug and release builds. Open
Settings → About → Developer settings → Bank SMS parsing lab.
Developer settings is available only inside About.
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
  Legacy POS templates retain English amount/card/merchant/balance labels as archived
  bank rules. New parsing uses the shared library, which already supports these formats;
  no bank-template upgrade is needed. Re-run saved messages to see new lab results;
  production messages use the normal reparse-stored-messages tool.
- Copied theme, Arabic fonts and locale helpers, with library-owned resources.
- A bank-independent `SharedLabEngine` and versioned `SharedRuleLibrary`. Adding a bank
  requires its name and exact SMS sender(s), not a parser or a bank-specific rule pack.
  All banks use the same library, edited in the Rules tab. Literal phrases, exclusions,
  amount/card/account/merchant/balance labels and direction are editable without code.
  New extraction grammars (not just aliases) still require engine code and regression tests.
  No executable scripts, downloaded models, cloud requests or user-supplied regex.
- The initial catalog includes Arabic/English purchase, incoming/outgoing transfer,
  bill/card payment, financing installment, withdrawal, refund, fee, balance notice and
  informational phrases, plus fixed OTP detection. This is fixture-supported initial
  coverage, not a claim to recognize every possible bank format.
- Exact sender routing, collision rejection, fixed OTP protection, copied classification
  arbitration with optional additive shared OTP phrases (built-in protection cannot be disabled),
  and a finalization gate. Supported money currencies: SAR/USD/EUR/GBP,
  maximum two decimal places, explicit currency required.
- Engine `shared-2` adds bank-independent, paired context candidates: due date plus due
  amount, inactive beneficiary status, and a transfer-fee title. Conflicting movement
  candidates remain UNKNOWN/review, including with reordered or edited shared rules.
  An outstanding amount alone and a beneficiary name alone do not trigger these guards.
- Separate Room file `masroof_parsing_lab.db`; no production Room migrations or services.
- Bank configuration, immutable shared rule versions, dated imported SMS, saved-message
  preview and immutable batch runs. New runs snapshot the bank definition, entire shared
  library, engine version, original sample, expected answer and shared/reference outputs.
  Lab-only Room migration 1→2 adds the shared library table without rewriting any old
  banks, custom rules, messages, runs or results. Legacy bank rules stay archived; they
  are not silently promoted to global rules. `DynamicLabEngine` remains as the legacy
  implementation with its tests; the workflow now uses `SharedLabEngine`.
- The Experiment tab imports the selected bank's inbox SMS using native calendar pickers.
  Both dates are inclusive in the bank's time zone (next midnight is exclusive). READ_SMS
  is requested on demand; the lab has no receiver or background worker. Exact configured
  sender matching filters messages before persistence. Original bodies and timestamps
  stay in the lab database only; imported messages have no automatic expected answers.
- Imports are atomic, limited to 1,000 matching messages and idempotent by bank/provider
  ID/timestamp/sender/body. Provider errors, denied permission or oversized ranges save
  nothing. Existing samples, annotations and results remain intact. Results date pickers
  filter already saved lab messages; they do not read the inbox.
- Legacy bank-pack JSON import/export through a text field, latest/historical run selection,
  expected-field differences, baseline comparison for the AlJazira bank ID, and an
  explicitly confirmed lab-only reset.

The bundled shared rules and four optional synthetic samples demonstrate the workflow; they do
not establish production bank coverage. The cloned baseline remains available for comparison.
Baseline output is not used to manufacture expected answers.

## Current boundaries

- Experimental evidence comparison is available alongside the shared engine. Results →
  Evidence comparison saves paired outputs, original-text evidence, hypotheses, and manual
  reference snapshots. Rules → Evidence knowledge previews changes across saved banks
  before saving a new version. The shared engine remains the existing run/preview default.
  Lab-only migration 2→3 preserves old data and adds paired results/knowledge tables.
  See [implementation and measurement contract](experiments/EVIDENCE_ENGINE.md).

- Imported messages start unannotated. Results → details → correct answer saves manually
  reviewed expected fields for the next run; it never rewrites prior runs or changes rules
  automatically. Optional blank expected fields are unscored, not assertions of absence.
  The UI shows matching annotated fields and unlabeled counts, with a review-only filter.
- Extraction handles currency before/after amount, Arabic digits and decimal separators,
  a narrowly scoped `(0xx) CURRENCY amount` marker, labeled four-digit card/account suffixes,
  card names followed by `(last4)`, separately retained IBAN suffixes, labeled ISO wall-clock
  times, and inline merchants terminated by an ISO date label. Available balance and due
  amount are separate roles, excluded from the transaction amount. No relationship between
  account suffix and IBAN suffix is inferred. Other date/number/reference grammars need tests
  and development; malformed or competing evidence stays reviewable.
- No ownership, posting, reconciliation, balances dashboard or production ledger writes.
- Batches are bounded to 1,000 samples. Calculation is off the UI thread and the completed
  run commits atomically. Leaving the lab cancels its screen scope; an unfinished run is
  not recorded as completed. Long-running background recovery is not enabled.
- No measured multi-bank accuracy claim. Inspect labeled results and held-out real-bank
  samples before using outputs outside the laboratory.

## Verification and cloud CI

Local model experiments, SetFit training separation, measured results and reproduction
steps are documented in [experiments/README.md](experiments/README.md). Neither the
embedding classifier nor SetFit is loaded by the app or active lab workflows.

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
