# Proposed next phase: personal spending and forecasting

Design proposal: [PERSONAL_INSIGHTS_DESIGN.md](../docs/PERSONAL_INSIGHTS_DESIGN.md).
Start from PR #329 / `d2d3ebe5`; CI #943 passed, PR remains draft/unmerged.
This is a reviewable plan and prototype, not an implementation or activation.

Work areas and order: evidence audit → forecast explanation → scoped spending
observations → recurring review → diverse message review. Base-model
qualification is a separate data milestone. Reuse current models and screens;
use robust descriptive statistics for initial spending observations. Preserve
financial v18, human decisions, all qualification gates and current authorities.
Detailed scope, acceptance, UI behavior and validation are in the linked design;
proposed implementation slices are in `todo.md`. The completed baseline below
is retained as the record of #329.

---

# Continuous local learning

Implement the approved plan on `feat/continuous-local-learning`. Inference and
personal training stay on-device. Financial schema 18 and financial authority
stay unchanged. User-supplied backups are private inputs, never repository data.

## Ordered increments

1. Family personalization: stable held-out templates, seed safety gates and
   operational status distinct from exact human-template memory.
2. Recurring numerical classification and local fitting from explicit human
   decisions; held-out series; deterministic amount/date estimation.
3. Analytics schema/archive 4, durable feedback, qualified identities and UI.
4. Actual model participation/readiness for forecasts, messages and recurrence.
5. Private data tools, reproducible reports, scoped tests, migration/restore,
   device checks and documentation.

## Acceptance gates

Family seeds require >=8 accepted naturally eligible validation and >=8 final
test templates per enabled family, with no accepted wrong families. Personal
fitting holds out 20% of templates, requires >=8 eligible held-out templates,
more correct coverage, no added errors and passing seed validation.

Recurrence seeds require >=95% precision, >=10-point recall improvement over
rules and no increased false-positive rate; validation/test each need >=30
accepted independent series per cadence and >=30 hard negatives. Personal
fitting holds out 20% of series and needs >=8 held-out series (>=2 recurring,
>=2 non-recurring), >=95% precision, better coverage and no increased FPR.

Insufficient real labels leave models ineligible. Synthetic examples test
behavior, not real-world accuracy. Preserve forecast training/evaluation gates,
immutable published comparisons and the current live regression policy.

Focused checks run locally; full suites run in PR/main CI, never release CI.

---

# Previous completed work: finance core policy extraction

Baseline: main after #317 (`33a7c06e`). Move production policies, preserving
packages, signatures, behavior and persistence. Keep `masroof.db` schema 18,
all existing writes, backup format and worker identities unchanged. No reparse,
backfill, new store, UI feature or new financial write is part of this change.

## Ordered increments

1. Freeze financial compatibility examples and the v18 schema contract.
2. Move production money, periods, bank time and deterministic container/transaction
   identities with their value dependencies and focused tests.
3. Move financial classification, impact, informational authority and transfer
   ownership policies with their input/output models and tests.
4. Move transfer matching, timing, assembly and statement matching. Keep CSV I/O,
   ownership repositories, reconciliation and writes in app.
5. Enforce module/source ownership, retain static analysis and document boundaries.

Each increment must compile and pass its focused tests before its commit. The
existing PR/main CI runs the full unit/module/device/upgrade/recovery suites.
Release CI is unchanged. There will be one implementation of each policy, no
compatibility wrappers and no duplicate legacy engine.

## Compatibility and ownership

Keep both existing Money contracts; unifying them changes scope. The bank engine
may continue to import only its existing finance value contracts, never posting
policies. Finance core has no project dependencies or Android/Room/repository I/O.
Only the transitive value dependencies of extracted policies move. Dashboard
aggregations, application completion/retry policies and lifecycle remain in app.

## Acceptance

- Frozen amounts, IDs, periods, decisions and review reasons remain identical.
- Financial v18 schema and identity hash remain identical; real APK upgrade and
  backup/restore checks retain stored financial facts and manual decisions.
- Architecture checks inspect moved sources and fail for missing source coverage.
- Existing statement, dashboard, reconciliation and human-decision tests pass.
- All relocated policy tests run in finance-core; integration tests stay in app.

Progress and verification are recorded in [todo.md](todo.md).
