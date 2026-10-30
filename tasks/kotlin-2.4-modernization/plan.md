# Kotlin 2.4 source modernization

Approved scope: behavior-preserving source changes, no dependency upgrade.
Baseline: `05b7e045f894b6a5283022e822da99083a9e4375` (latest main fetched 2026-10-30).
Branch: `refactor/kotlin-2.4-modernization`, isolated from the original checkout.

## Sequence and acceptance

1. Research official Kotlin 2.1–2.4 documentation with Context7, cross-check
   stability and installed toolchain support; record findings in `reference.md`.
2. Establish unit/static/APK baseline. Screen every production Kotlin file in
   all five modules against the reference; inspect candidate implementations,
   callers and tests. Record baseline hashes and signals in `inventory.csv`,
   and concrete adopted/rejected opportunities in `audit.md`.
3. Refactor small groups with stable features only; preserve signatures,
   evaluation order, errors, financial decisions, formats and architecture.
   Run focused checks for each group and commit independently.
4. Run full units/integration, lint/detekt, app/test APK builds and available
   device suites. Preserve schema exports and all AGENTS.md invariants.
5. Write measured results, exact counts and remaining opportunities in
   `report.md`; push a PR to main without merging.

No source-count target or quota for feature adoption. Existing stdlib adoption
is not a new change. Flow read-only wrappers, financial identities, database
annotations, byte formats and worker identities retain their contracts.
Experimental features and speculative abstractions are excluded.

## Checklist

- [x] Fetch latest main, fast-forward local main and create isolated branch.
- [x] Read current AGENTS.md, architecture and previous modernization record.
- [x] Query Context7 and cross-check official Kotlin 2.1–2.4 release notes.
- [x] Baseline units: 2,154 tests, zero failures/errors/skips.
- [x] Baseline static analysis and APKs.
- [x] Complete production screening inventory and candidate audit.
- [x] Apply and verify justified source changes incrementally (two source commits).
- [x] Final units/static/APKs/device checks and compatibility review.
  2,154 units and 156 device cases/phases verified; initial UI selector failure
  and successful paired probes/full affected-shard rerun are recorded in report.md.
- [x] Final report and pull request targeting main: [#347](https://github.com/bakhshb/masroof/pull/347). No automatic merge.
