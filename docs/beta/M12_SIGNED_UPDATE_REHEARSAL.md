# M12 synthetic recovery and signed update rehearsal

This is executable Beta qualification work, not M11/M12 completion or a production
migration. It does not open a production database, inbox, signing key or installed
production application. Legacy code is retained.

## Financial corpus and recovery

A test-source-only generator runs the real preserved bank parser on caller-supplied
synthetic messages, explicitly approves each review, and persists financial history.
The signed update host uses **600 purchases spanning 2018-2025**, 17 merchant labels,
SAR/USD amounts, full SMS FX 3.756957, international fees, four confirmed Beta
assets, manual time corrections, explicit suppressions, evidence/adoption records,
ledger and correction audit histories, theme and string preferences. This is a
long-duration 600-row workload, not a 10,000-row or representative customer corpus.
It is primarily purchase history, not acceptance of every financial message family.

Before/after fingerprints frame every column and value in every beta_* table,
including counts and full content digests, plus all Beta preference keys. SQLite
integrity and foreign keys are checked. Financial assertions separately compare
active native-currency totals, precise FX, correction histories and preferences.
No financial approval is manufactured from a comparison candidate or SMS suffix.

JVM recovery tests use 80 real approved synthetic records and injected synthetic
HMAC keys. Wrong password, foreign origin, truncation and tampering must preserve
the complete current state. Injected storage interruptions before/after journal,
database and preference replacement preserve current state before commit; after a
durable commit, the restored copy remains authoritative even if cleanup reports
failure. Tests then verify complete financial recovery. This is fault injection,
not a physical full-disk or power-loss certification.

The native constructed-v8 fixture copies compatible typed columns from synthetic
current-schema state into the committed v8 schema, with all FK checks, and encrypts
it through the supported internal writer. Restoration migrates only a private
stage and preserves evidence, assets, ledger, FX, correction history and preferences.
The original v8 copy remains unchanged. New derived columns may remain unknown
until their explicit backfill. This constructed fixture is not a database downgrade
feature or proof of an actual historical production APK update. Existing encrypted
v1 and v2-v5 backup/migration tests remain in place.

## Actual APK replacement, isolated identity

`lab:upgrade-host` has two debug-only flavors built from the same source: before
versionCode 1 and after versionCode 2. Both use only the dedicated applicationId
`com.baraa.masroof.beta.upgrade.rehearsal`. Both compile the same current Beta
schema (9 in this independent PR); therefore this is **not a 9-to-10 APK migration**.
The module has no production-module dependency, exported launcher, inbox permission
or release variant. Its manifest is testOnly and allowBackup=false.

A dedicated ephemeral test certificate is generated inside the ignored host build
directory, not read from the production or default debug keystore. Both host APKs
must pass apksigner verification and have identical public certificate digests.
The private test keystore is never uploaded. APK hashes, public certificate reports,
manifest/version evidence and installation logs are retained.

The existing single latest-stable emulator lifecycle job seeds under version 1,
then performs `adb install -r -t` of version 2 with **no clear/uninstall/downgrade**.
It checks the same UID, a changed process, exact table/preference state, financial
invariants, and restoration of the v1-created encrypted copy after updating. This
last step proves the installation's Keystore origin key survives the signed update.
Wrong-password and damaged-copy attempts must preserve the mutated current state.
Only then may the correct explicit copy restore the original complete state.

The trusted-installation boundary is unchanged: no foreign-origin adoption, TOFU,
private-key export, cross-device/reinstall or portable disaster-recovery claim.
Android clear app data/uninstall/key loss can still make copies unrestorable;
Beta's own reset intentionally retains the origin key.

## Measured evidence, not invented acceptance

The host measures synchronous dashboard read, hydration and production on the
same synthetic workload under each installed version. Each report contains five
warmups, 25 raw elapsed-time samples, nearest-rank p50/p95, peak observed managed
heap usage and process PSS, API/device identity, and EXPLAIN QUERY PLAN output for
three bounded current/fact queries. Memory includes the instrumented process and
is not an allocation count or guaranteed peak. Timings exclude UI rendering,
import, startup and archive encryption. The script uploads before.json/after.json
and the two explicit instrumentation results as beta-upgrade-diagnostics.

No time/memory threshold is invented. M0DashboardReadBenchmarkTest uses a tiny
Robolectric SDK28 golden workload (n=8); M0ProductionBaselineTest leaves native
startup/import10k measurements absent. These are not comparable native baselines,
so no performance-regression or release-acceptance claim is made. Richer transaction
families, larger volumes, physical devices, low-space/power-loss and real signed
release-to-release migration qualification remain open.

No new emulator job or Android matrix is added. The two host phases run exactly
once inside the existing lifecycle job; host connected-test suites are not run a
second time by smoke. The existing Beta device suite and strict full schema
validation remain intact.
