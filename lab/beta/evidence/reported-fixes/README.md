# Reported Lab SMS fixes

Verified on an Android API 34 emulator, using anonymized fixtures transcribed from the three reported screenshots. Production app and ui-kit sources are unchanged.

## Eight fixes

1. Recognize instant incoming/outgoing transfer titles.
2. Bind a bare principal amount to an explicit, unambiguous currency field.
3. Extract beneficiary account/identifier and beneficiary-bank labels.
4. Recognize the Mada card-number label.
5. Extract bill-payment source, service, biller code and invoice reference.
6. Offer outgoing, incoming and own-account transfers in unknown-message review, with ownership and duplicate-posting protections.
7. Supply theme-aware default text color to Lab screens.
8. Parse labeled day-month-year local dates with time before or after the date, rejecting invalid/conflicting dates.

Message details now explain engine rules, statistical ML, learned templates, manual decisions, unresolved interpretation or unavailable provenance. The three reported samples are understood by rules; their cards explicitly state that ML did not contribute. Existing posted/manual records retain their authority; older unresolved records can be reprocessed after the engine version update.

## Validation

- 192 JVM tests passed (137 beta, 55 bank-engine).
- 11 native tests passed: five reported-message/theme/manual-transfer/reprocessing tests, plus six existing ML and setup tests.
- Final Android test APK build, beta lint, production debug build and production detekt passed.
- Native tests used direct Android instrumentation on API 34. The offline Gradle connected-test launcher lacked a cached UTP dependency.
- Arabic dark/light checks inspect rendered text contrast. Import/replay checks assert three transactions, correct amounts/types/roles, preserved parsed local dates and no duplicates after reopen.

## Screenshots

- [Engine provenance: outgoing transfer](reported-100-00-dark.png)
- [Engine provenance: purchase](reported-300-00-dark.png)
- [Engine provenance: bill payment](reported-16-23-dark.png)
- [Unknown-message explanation and transfer choice](manual-transfer-dark.png)
- [Arabic import dates in dark mode](import-date-ar-dark.png)
- [Arabic import dates in light mode](import-date-ar-light.png)

Transaction screenshots use the emulator's English locale; Arabic translations are included. The date-selection screenshots exercise Arabic directly. Displayed transaction summary dates follow the existing receipt-time policy for local timestamps without a timezone; parsed local dates are retained separately.
