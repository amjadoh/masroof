# M7 — Current interpretation review and trusted human labels

Source: ADR-19 / Notion page 12, reviewed 2026-10-18. Base main: merged M5 PR #293, 17f6f12b7499a6c7b169f26c9eae564f239e053a.

## Existing behavior retained

M6 already invokes one independent BankEngine through ProcessBetaEvidence, LabEvidenceComparer and BetaInterpretationService. Versioned archive v1 records BANK_ENGINE authority, engine/rule versions and original UTF-16 spans. New rows have one current archive and an inert `{}` baseline. Explicit optional history permission/import, durable checkpoints/retry, and original evidence are already implemented. No second interpretation call, shadow fallback or speculative rewrite is added.

The historical physical archive columns remain to preserve Beta evidence and the separately gated historical finance path. Removing them or switching financial authority is M8, not this PR. Old comparison rows require an explicit current reprocess before new annotation; no migration silently reparses them.

## Demonstrated M7 gap and change

The existing BetaReviewWorkflow/BetaReviewPanel only review already admitted financial inputs. Independent-engine intake is deliberately not financially adopted yet, so those messages have no human interpretation-label review path. A KEEP_FOR_REVIEW acknowledgement is not a corrected field label.

Add an explicit review action to each saved message in BetaHistoryPanel. The new panel reads a single current interpretation, shows its immutable original text and the selected observed field, and lets the user select supporting original text, enter/choose a correction and explicitly confirm it. No prediction pre-fills a human label or writes on entering/canceling. Reopening/restoring the form clears its prior value, text selection and confirmation so an old draft cannot silently attach to a newer interpretation. Supported roles are family, amount, currency, source account suffix, destination account suffix and card suffix. This is annotation, not ownership registration or financial approval.

The application workflow re-reads saved route, current engine/archive identity, pending state and label revision inside one Room transaction. Stale/new conflicting submissions fail; a matching decision retry is idempotent. Each append records original selected text/UTF-16 range, explicit-user provenance v1, field/value, archive SHA-256, interpreter attempt, engine/rule versions, decision ID, revision and timestamp. Reprocessing retains old labels and identifies their older interpretation basis; it never applies them automatically. Saved text is only shown in explicit local review, never a log or report.

## Storage and stage boundaries

Beta schema 10 -> 11 is an additive Room auto-migration: only beta_learning_label is added; all existing table definitions are identical. A raw-evidence foreign key prevents orphan labels. No old rows are rewritten, relabeled, reset, deleted, imported from production or automatically posted. Beta backup/restore automatically includes the new table. The existing financial-review and dashboard schema capability gates include 11 solely to retain prior behavior; no finance adoption/ledger calculations change. Historical schema-10 migration assertions still validate schema 10 against its own generated schema, while current backup restoration is checked against schema 11.

The labels stay inside protected Beta storage. No export endpoint, unencrypted dataset output, model artifact, training, model download or runtime ML adapter is introduced. The previous offline Python grouping/dedup/benchmark tools and sample training annotations have since been removed at the user’s request. A replacement ML training/evaluation pipeline and runtime integration remain unfinished. Optional ML remains disabled; no precision/latency/promotion claim is made. M8 and old-code deletion are explicitly not started.

## Verification

Shared JVM/native synthetic checks cover no automatic labels, explicit confirmation, role/value/range validation including Arabic/Persian digits and UTF-16 surrogate boundaries, idempotency/conflicts, stale label revisions, pending/reprocessed interpretations, independent historical labels, equal original vs corrected value, reopen, backup/restore and unchanged raw/outcome/ledger authority. A synthetic protected-production canary is byte-identical; no real production file/inbox is queried.

Native migration checks validate 10 -> 11 and 1 -> 11, preserve registry/selection/raw archive/decisions/opaque ledger rows/markers, and require empty labels after migration. Native UI enters from saved history, selects original text, explicitly corrects an amount and verifies persistence and unchanged engine facts; state restoration after reparse requires fresh selection and confirmation. New host-driven before/after-COMMIT force-stop journeys verify durable/idempotent label recovery and backup restore in an isolated test APK; they remain excluded from the ordinary native suite to avoid duplicate execution.

Final local/CI evidence is recorded on the PR. Optional inbox import uses the existing explicit permission path; automated verification uses synthetic inputs and never reads user inbox data.

## UI continuation safety found by CI

CI exposed Android View layout on masroof-beta-io after a UI coroutine completed IO under Compose 1.6.8's unconfined test effect context. Explicitly confine Beta presentation effects/actions to Android Main through shared BetaLaunchedEffect/rememberBetaUiScope helpers. Keep the same cancellation/keys and the existing FIFO BetaIo dispatcher for actual database work. This also covers dashboard preference/configuration facades that switch to IO internally. No application/financial dispatcher or calculation changes, test framework upgrade, skip or retry loop.

A native regression verifies both an effect and button action resume on Android Main after real BetaIo work under the unchanged default test rule. Existing activity assertions remain enabled, with exact-state waits for asynchronous IO/rendering. The selected supporting range is stored independently of the text caret, displayed to the user and cleared on reopening/restoration, so focus changes cannot erase the chosen evidence.
