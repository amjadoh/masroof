# Finance core policy extraction

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
