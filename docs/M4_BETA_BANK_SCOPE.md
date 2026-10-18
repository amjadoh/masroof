# M4: verify persisted bank selection and independent import scope

Source: ADR-19 Clean Beta, https://app.notion.com/p/3fc44ee65d118130899bc774b292e434 (M4: saved enable/disable, no bank guessing/collision resolution, independent history, approved picker design). Base: merged #291, `9150987b0206f2abe61a5ae3c211630849e04c8a`.

## Existing implementation retained

- `BetaBankSelectionWorkflow` / `RoomBetaBankSelectionStore` persist supported bank state independently from assets. Disable/resume does not erase saved evidence, relationships or financial history. Unsupported banks cannot be enabled. The current supported sender bank remains AlJazira; remote network aliases do not add import capabilities or ownership.
- `LabEvidenceRouter` routes over the full independent sender catalog before user selection is considered. Ambiguous evidence retains all candidates for review and never invokes a bank interpreter chosen by the enabled subset. Unknown/unproven history is excluded. Existing routing/collision/unit tests remain unchanged.
- `BetaHistoryImport` retains explicit, bounded, checkpointed historical scans; dates and selected banks belong to a saved session. No automatic inbox scan/resume is added. Existing permission, checkpoint/replay/conflict/limit tests remain.
- The bank picker/UI and persisted schema 10 remain unchanged. Existing native picker tests cover add/pause/resume, busy actions, Beta badge and Return. M2 shared UI and screenshot checks continue; this increment is not a UI redesign.

## Proven gap and fix

Two new regression tests failed before the fix:
1. Reimporting an already captured pending message after disabling its bank returned Stored and implicitly retried interpretation (attempts advanced).
2. Reimporting an already stored history message in another bank's requested scope returned Stored instead of being excluded.

`ProcessBetaEvidence.importBatch()` now uses the same existing full routing and current scope checks for new and repeated messages. Scope is checked before a duplicate can return Stored or resume a pending interpretation. A conflicting provider payload/origin still returns IdentityConflict without modifying the saved raw row. No new scope policy, route guessing or duplicate normalization is introduced.

An out-of-scope duplicate returns NotCaptured, so historical-session counters treat it as excluded. It does not delete or rewrite the original capture, outcome, retry, manual decision or capture-time selection snapshot. Active in-scope duplicates remain idempotent. Re-enabling a bank permits intake again. The separate explicit reprocess/retry actions on saved evidence remain available after disable, preserving the existing review contract.

## Verification and boundaries

New Room regression tests prove exclusion, no implicit retry, unchanged saved history/manual decisions and unchanged explicit reprocess behavior. `BetaBankScopeChecks` runs the same persistence scenario on JVM/native emulator: a simulated interruption after capture, pause/reopen, retained history-window metadata and decision, blocked duplicate, re-enable/default independent BankEngine interpretation, no duplicate interpretation/ownership/posting, then explicit backup restore retaining the paused state and original pending evidence. A protected synthetic production-named file remains unchanged. The fixture never queries an inbox.

Before-fix result: 20 evidence-workflow tests, 2 failures for these new regressions. Local/full final-head CI, native test results and artifact digests are recorded in the PR. Existing expectations, golden corpus and tests are not weakened.

Only runtime edit: `lab/beta/application/evidence/ProcessBetaEvidence.kt`. No app/production, bank engine/profile, UI-kit, ownership/asset discovery/editor, finance, database migration/reset/backfill, live intake or release changes. Beta data is retained. Production data is not opened. Old-code deletion is deferred. M5 is not started.
