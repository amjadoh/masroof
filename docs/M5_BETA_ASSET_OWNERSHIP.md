# M5 — Beta asset ownership and loan configuration

Plan: ADR-19, Notion page 12 (reviewed 2026-10-18, edited 04:52:45Z).
Base: merged M4 PR #292, d3d2bf45787bbca365856ff1fb5d0da8663b4cde.

## Audit and demonstrated gap

Keep the existing asset editor, discovery, registry and Finance Core identities. Discovery reads saved interpretation evidence, never establishes ownership and never merges equal suffixes. Explicit confirmation revalidates the proposal revision and enabled bank in a transaction. Manual edits retain stable identity and reject stale conflicting configuration. Debit cards need an account; credit cards need a facility and role, with primary/supplementary validation. These paths already have JVM and native tests.

Loan is already a Finance Core asset kind, supported by the existing Beta registry, review picker and loan dashboard. However, BetaAssetEditor omits it from creation choices and BetaManualAssetWorkflow rejects it. A user cannot create or edit a loan through the normal manual ownership path. The new regression demonstrates this rejection before the change.

## Change

Expose LOAN in the existing editor and accept it in the manual workflow. Use the existing translated loan label, four-digit suffix validation, explicit ownership checkbox, opaque stable ID, enabled-bank creation and optimistic edit validation. Loans have no card parent or credit role. Two loans with the same suffix remain separate. No amount, debt balance, repayment linkage or financial posting is invented by this registration.

Only two runtime files change: application/BetaManualAssetWorkflow.kt and presentation/BetaAssetEditor.kt. Discovery/registry, database schema 10, archived evidence and prior Beta data stay intact. No migration or reset is needed. No production source/data/inbox access, no old-code deletion, no M7 work.

## Validation

Regression: isolated JVM manual-workflow test for loan confirmation, invalid parent/role/empty suffix, idempotent retry, equal suffix identities, edit/reopen, stale and kind-changing edit rejection, explicit backup restore and paused-bank behavior. Native Compose regression exercises the existing editor with Arabic digits, rejects save before ownership confirmation, saves a loan and verifies restart persistence without SMS or posting. Existing ownership, discovery, card configuration, storage isolation and lifecycle suites remain enabled.

Local and final-head CI results are recorded in the PR after completion. Tests use synthetic isolated test app data; no connected device/user database is accessed.
