# Masroof 2 Beta

The lab now contains only the independent BankEngine, Finance Core, Beta app and isolated upgrade test host. Open Beta through Settings → App → About → Developer settings → Masroof 2.

The copied legacy parser, old Parsing Lab app, comparison adapters, old-engine tests and benchmark reports have been removed under explicit user authorization. This iteration starts fresh in `masroof_beta_v2.db` with isolated `masroof_beta_v2_prefs`. No old lab or production data is migrated, opened or deleted. Current-schema encrypted backups preserve labels, decisions and audit history. Production parsing, financial code and data are unchanged.

Use `:lab:bank-engine:test`, `:lab:finance-core:test`, `:lab:beta:testDebugUnitTest` and the existing latest-stable Android native/lifecycle CI checks. Clear transactions use one validated current interpretation and uniquely confirmed owned accounts/cards. Partial, unsafe, ambiguous and unexecuted records remain unposted; security and informational messages never become transactions. Manual corrections and approvals keep authority.

All 42 fixture files from the removed parser module were byte-identical to the retained canonical `app/src/test/resources/testdata/bank_aljazira` corpus. Their only copies under the removed module were redundant; canonical inputs and expectations were not edited. Human learning labels and original messages are stored in the independent current Beta database. Production migration/release remain out of scope. See [the implemented lab parity report](beta/PRODUCTION_PARITY_RESULT_AR.html) for schema and lifecycle details.

Offline Python training, benchmark tools and their sample training annotations were removed at the user’s request. Human corrections stored in Beta remain intact; ML training and runtime integration are still pending.

Build the separate SMS-enabled lab host with `./gradlew :lab:upgrade-host:assembleStandaloneDebug`. Its package is `com.baraa.masroof.lab.standalone`; live intake is registered only in this flavor. Production sources and its manifest are unchanged. The normal embedded Beta entry retains historical import.
