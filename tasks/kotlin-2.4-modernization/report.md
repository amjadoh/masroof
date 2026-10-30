# Kotlin source modernization report

Base: `05b7e045f894b6a5283022e822da99083a9e4375` (latest main at start).
Source revision: `a24494d80b86733fe4fbbaaca32d1eeb92d45a69`.
Branch: `refactor/kotlin-2.4-modernization`.

## Outcome

Six stable guarded `when` branches flatten five nested conditional blocks in four production
files: transaction assembly, statement ownership rejection, zero-match routing
and historical persisted-failure accounting. Branch precedence, predicates,
short-circuiting, result construction and side effects are preserved. Production
Kotlin diff: **25 lines added, 37 removed (net −12)**. No new public interfaces.

The change is deliberately small because the baseline already adopted stable
hex conversion, enum entries, a partial-ingestion guard and data-flow-based
exhaustiveness. Stable does not automatically make a feature useful for this
application. There is no claim of adopting every Kotlin release feature.

## Research and review

- [Reference](reference.md): Context7 queries, official release/API sources,
  introduction/stabilization versions, Android toolchain and pre-stable exclusions.
- [Audit](audit.md): per-opportunity current/recommended implementations, benefit,
  version, risks, tests and adopted/rejected decisions.
- [Inventory](inventory.csv): all **617 production Kotlin files / 58,481 baseline
  lines** screened across five modules, with baseline hashes and dispositions.
  Candidate bodies/flows/tests were inspected semantically; this is not a claim
  of manual line-by-line review of every source expression.
- **4 production files modified; 613 retained.** Five documentation/audit artifacts
  accompany the four source files (nine files total in the PR).

Adopted feature: guards on subject `when`, introduced as preview in 2.1 and stable
since 2.2; compiled successfully with Kotlin 2.4.21 and no new opt-ins.
[Official release reference](https://kotlinlang.org/docs/whatsnew22.html#stable-features-guard-conditions-non-local-break-and-continue-and-multi-dollar-interpolation).

Rejected/deferred: explicit backing fields where they would replace read-only
Flow wrappers; context/annotation/UUID/time migrations without a concrete
benefit; `isSorted` where adjacency means gap/overlap/cadence rather than order;
Base64 or PDU conversions with compatibility risks; cosmetic aliases/string/loop
rewrites. Experimental language/library/checker APIs remain excluded. See audit
for exact locations and follow-ups.

## Compatibility evidence

- Dependency catalog, wrapper, build scripts, compiler options and CI unchanged.
- All seven Room schema exports byte-identical to base. Financial schema **18**,
  identity hash **71f1193767b3aa12bcb6cc6e7ce77147** unchanged.
- No changes to parser definitions/assets, resources, Compose code, models,
  persistence, preferences, identities, worker scheduling or test expectations.
- Existing finance-core, reconciliation, routing, scanner, dashboard fixture,
  architecture/schema and application integration tests remain intact and pass.
- Gradle dependency insight resolves compiler and runtime stdlib **2.4.21**. JVM task options use
  bytecode **21**, with language/API levels left at compiler defaults. No new
  compiler arguments were introduced; plugin-supplied arguments remain unchanged.
- Project AGP 9.5.0 / Gradle 9.8.2 exceed the KGP table's fully supported maxima.
  Successful builds are project evidence, not a claim of vendor-qualified
  compatibility. No dependency change is included.
- Local Gradle runtime was JDK **21.0.13**, also the compilation/test toolchain;
  CI's configured daemon JDK 27 was not available locally. The exact baseline
  main commit passed [full CI run 41081433811](https://github.com/bakhshb/masroof/actions/runs/41081433811)
  in that configured environment; PR CI must verify the new head too.

## Verification

| Check | Baseline | After refactoring |
| --- | --- | --- |
| App unit/integration tests | 1,871 pass | 1,871 pass |
| ui-kit unit tests | 16 pass | 16 pass |
| bank-engine tests | 121 pass | 121 pass |
| finance-core tests | 95 pass | 95 pass |
| ml-assist tests | 51 pass | 51 pass |
| Total units | **2,154; zero failures/errors/skips** | **2,154; zero failures/errors/skips** |
| lintDebug / detekt | Pass | Pass |
| Debug app / instrumentation APK | Both build | Both build |
| API 37 primary device suites | Not run locally before changes | **136 unique cases pass** across all four shards; shard 2 required one full rerun |
| API 37 process-death/recovery | Not run locally before changes | **20 phases / 10 journeys pass**, exact manifest coverage |

Increment 1 passed full finance-core plus targeted statement/reconciliation tests.
Increment 2 passed **478** targeted bank/SMS/ingestion/import-outcome tests,
including the explicit uncaptured-versus-persisted failure scenarios.
All seven existing CI protocol/routing/version checks and both family and recurring
model reproducibility paths also pass. Family/recurring trainer regression checks
pass; no generated model assets changed.

Existing baseline warnings remain, including Gradle deprecations and Android/Kotlin
warnings outside the four changed bodies. No checks or expectations were weakened.

Commands:

```sh
./gradlew testDebugUnitTest :bank-engine:test :finance-core:test :ml-assist:test --stacktrace --console=plain
./gradlew lintDebug detekt :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
./gradlew :finance-core:test :app:testDebugUnitTest --tests 'com.baraa.masroof.application.statement.StatementReconciliationServiceTest' --tests 'com.baraa.masroof.application.transaction.*' --console=plain
./gradlew :app:testDebugUnitTest --tests 'com.baraa.masroof.bank.*' --tests 'com.baraa.masroof.application.sms.*' --tests 'com.baraa.masroof.application.ingestion.*' --tests 'com.baraa.masroof.application.onboarding.HistoricalImportOutcomeTest' --console=plain
./gradlew testDebugUnitTest :bank-engine:test :finance-core:test :ml-assist:test lintDebug detekt :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
python3 scripts/ci_device.py bundle
# Run each shard 0, 1, 2, 3 on the isolated API 37 emulator:
python3 scripts/ci_device.py run --shard 0
bash .github/scripts/run-lifecycle-suite.sh production test
```

No performance improvement is claimed: there was no runtime benchmark. Build
elapsed times include caching and are not comparable performance measurements.

## Remaining opportunities and limitations

The pending-update state cast deserves separate concurrency-focused review;
the existing response-body safe-call warning could be addressed as a library-contract
cleanup; validated advisory facts could potentially reduce assertions if a future design
justifies new types. Further manual review beyond lexical screening may find
additional simplifications. None is needed for this guarded-branch change.

Two device cases are excluded by the existing CI manifest: a private-backup probe
requires private user input, and the pre-3.27 SQLite test requires API 26–28.
They are not silently counted as passing or supplied with invented private data.
The first primary-suite attempt had one UI finder failure in
`MessagePatternsJourneyTest.bankPagePreviewsPendingMessagesWithoutRemovingReviews`:
`performScrollTo` could not find the merged text node while the unmerged tree
contained it. The test helper and screen are byte-identical to baseline. The same
case passed in isolation on saved baseline APKs and on modernized APKs, then
passed inside the full affected-shard rerun. This is consistent with an
intermittent selector/readiness issue; isolated passing runs do not prove its
precise cause. No test, wait, selector, assertion or production code was changed
to obtain a pass. The original failure evidence is retained locally under
`/tmp/masroof-kotlin-first-shard-2/`, with paired probe results in
`/tmp/masroof-kotlin-case-comparison.json`. The full shard rerun passed all 35 cases, so final coverage is 136 regular
cases plus 20 recovery phases, with zero skipped or missing required cases.
The first failed attempt is not erased from this report. PR CI is pending at
submission; local results do not claim a completed hosted run for the new head.
