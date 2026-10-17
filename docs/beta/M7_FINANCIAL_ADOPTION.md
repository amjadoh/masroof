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

The Beta shell now exposes a separate initial approval queue and preview. It
shows preserved baseline amount/currency/time and available merchant, SMS FX and
fee facts, then requires explicit type, confirmed same-bank stable asset identities,
external endpoints and inbound attribution. No default financial choices or
last-four matches are submitted. The pure classifier disables invalid endpoint/type
combinations before the write adapter independently validates them. An explicit
confirmation checkbox is required and busy writes block editing and dismissal.

Drafts survive recreation; Back/Cancel discard without posting. Preview keys bind
evidence identity, archive revision, registry fingerprint, posting revision and the
exact pending request, so changed observations reset drafts. The optional request
registry fingerprint is persisted in the existing request JSON (old requests remain
readable); a changed bank registry or disabled bank rejects a newly reviewed request.
No schema migration is required by this approval UI.

Pending decisions display their saved type and endpoints and support exact-request
cancel/retry. Retry does not rewrite the durable intent: it checks the reviewed
request and the atomic posting path checks the same saved intent again. A
superseding request cannot be retried or cleared by an older screen. Success clears
only the same request and preserves the existing idempotent history and complete
FX pair. Correction-owned rows expose trusted reconciliation rather than fresh
financial approval; suppression/manual decisions remain preserved. The correction
editor remains separate. No automatic financial posting is enabled.

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

## Initial approval verification and limits

Meaningful JVM regressions cover preserved preview with no financial side effect,
stale registry and archive, bank disable, unsafe OTP archive, exact pending request
cancellation, superseded retry protection, inbound attribution and an actual stable
asset ID named `external` distinguished from the external-party choice. Existing
adoption, hold, correction and FX tests remain in the same suite.

API 36 tests in the existing instrumentation job cover RTL draft recreation and
cancel, explicit confirmation plus busy editing/dismissal protection, actual panel
approval into Room with full SMS FX followed by close/reopen idempotent recovery,
and actual panel retry of the reviewed durable request without duplicate history.
These are synthetic isolated Beta data; the new approval adapter does not yet have
a dedicated operating-system force-stop journey. Existing lifecycle CI is unchanged.

This completes the initial approval entry point, not all M7/M5/M10 acceptance:
experimental interpreter acceptance, automatic schedules, additional banks, broader
owned-transfer pairing and full visual/financial parity still require their own gates.
