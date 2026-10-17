# Explicit archive adoption, scoped asset replay and persisted FX

This is a Beta-only continuation of M7 / M5.4 / M8.4, not acceptance of the
experimental M6 interpreter. Production source, inbox and databases are untouched.

## Authority and public application APIs

`BetaContainer.financialReviewPage()` returns bounded safe identifiers, archive
revision and processing state. `approveEvidenceFinancial(BetaFinancialApproval)`
is a separate explicit financial decision. Comparison acknowledgement, parser
agreement and discovery proposals never call it. A caller explicitly chooses the
financial type and exact confirmed asset IDs; no last-four/alias matching occurs.
The adapter obtains amount, native currency and transaction time from the durable
**preserved baseline** archive. It checks contract / engine / rules versions,
matched full-catalog routing, bank identity, successful parse, blockers, diagnostics,
executed state and non-informational family. Only the reviewed AlJazira fixed
`Asia/Riyadh` bank-clock policy is currently supported. Missing bank time fails
closed; no device receipt clock or experimental candidate supplies money.

This explicit API is operational and exercised against Room. The initial approval
screen with a bank-fact preview and asset chooser is **not implemented** here.
The separate Review UI can edit a posted row; it does not bootstrap approval of
unposted archived evidence. No automatic financial posting is enabled.

## Durable state and retries

Schema **6 → 7**, owned by this PR only, adds two tables without modifying any
previous table or exported schema version 1–6:

- `beta_financial_adoption`: one evidence ID and stable `evidence:<id>` transaction,
  exact archive fingerprint, approved financial snapshot, explicit request,
  financial revision and monotonic observation revision, `ACTIVE` / `REVIEW` state.
- `beta_financial_approval_retry`: exact explicit request committed before financial
  work. Successful posting and adoption update share one Room transaction. The
  marker clears afterward; death between commit and clear retries idempotently.

`retryFinancialApproval(id)` rechecks the original archive and expected financial
revision. Stale requests remain visible/retryable; they never migrate to a newer
interpretation silently. `cancelFinancialApproval(request)` clears only the exact
pending request and cannot clear parser, registry or correction holds. Unknown
statuses and corrupt payloads fail closed. No SMS body enters public summaries.

Reprocessing marks an adopted row `REVIEW` in the same transaction that writes
the durable interpretation retry. Financial `read()` hides held and pending
adoptions, suppressed/review corrections, and stale registry snapshots. Audit and
history retain their exact original values. A raw outcome is not financial
completion.

## Manual assets and corrections

An actual manual registry change marks only affected same-bank adoptions for
review, including endpoint parent dependencies. No financial history is replayed
or overwritten. Existing direct / correction ledger rows are also checked against
the registry before financial read, so old facts do not leak as current money.
The initial implementation scans bounded-by-bank adoption snapshots on an asset
edit; a normalized dependency index remains a scalability improvement.

Uncorrected held rows need a fresh explicit approval with the current archive and
financial revision. Complete FX pairs cannot migrate to a different native
currency through this route. Correction-owned rows instead use
`reconcileApprovedEvidence(id)`: the trusted latest archive/registry observation
is rebased through the durable correction lifecycle atomically. Existing manual
amount/time/endpoints/FX and suppression remain selected. Unsafe or malformed
archives establish a sticky security review hold, never an old ordinary fallback.
Registry changes cannot clear an ownership conflict or a prior security hold.

## FX persistence

Initial explicit approval can adopt the merged pure FX policy's whole SMS pair.
Missing SAR valuation leaves a native financial result with `FX_UNAVAILABLE`,
not a fabricated SAR zero. `enrichEvidenceFx(id, expectedRevision, provider)`
resolves through an injected cache/provider **outside** its write transaction,
then rechecks archive, current financial snapshot, registry, holds and revision
before atomically writing both rate and source in a new financial revision.
Currency conflicts fail closed. A complete pair is immutable to this workflow.
Dashboard reads never invoke enrichment or write rates. No network client or
background scheduler is added. Historical merchant lookup across the archive,
automatic scheduling, manual FX editing, broader banks and engine acceptance are
still separate work.

## Verification scope

Room tests cover explicit baseline approval, native facts/full precision pair,
reopen/idempotent retry, pending-intent cancellation, reparse and market lookup
races, unsupported/security archives, stale/manual/correction ownership, asset
changes, correction rebase and sticky safety. API 36 instrumentation adds 1→7 and
6→7 migration/preservation checks and an actual close/reopen financial journey.
Existing smoke and external force-stop suites run unchanged in the existing
emulator jobs; no second device matrix is introduced. Reopen is not claimed as a
new external force-stop test for this adapter.
