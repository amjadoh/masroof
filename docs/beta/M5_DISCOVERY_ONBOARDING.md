# M5.1 evidence proposals and M10.1 onboarding increment

Beta welcome now describes the actual sequence: select banks, optionally scan a
7/30/90-day window, review account/card proposals, or configure assets manually.
No financial dashboard or ledger is fabricated. Nothing runs on entry.

## Observation, identity and explicit confirmation

The discovery page reads at most 25 existing evidence IDs using the existing
keyset DAO. It consumes saved candidate/baseline archives, never reparses SMS in
presentation. Only a supported matched bank, still enabled by the user, is shown.
Retries, malformed/unsupported archives, bank/evidence mismatch, security, OTP,
the NON_FINANCIAL message family (not a blanket parse-status rejection), unknown family, unexecuted states, conflicting or untyped validation
failures stay in the evidence journey; they do not create owned assets.

Partial financial status does not itself prohibit a proposal: a typed amount-only
finding (or partial result without blockers), explicit role provenance and agreeing
baseline facts can identify an asset without a successful financial parse. Candidate
source/destination/card/channel ranges must be valid within the saved body. The
preserved baseline exposes only whole-message provenance, so it independently
corroborates values and roles rather than pretending to expose field ranges.
Current shared-engine generic LAB_TRACE validation failures are deliberately not
reinterpreted as amount-only. UNKNOWN family identity extraction remains a later
M5.1 increment until the contract can prove security and number-role safety.

Incoming source / outgoing destination are counterparties even in the same bank.
Only the recipient account on incoming transfer or source account on other safe
families can be proposed. Card suffix alone never supplies a kind: explicit debit,
credit, statement or the existing typed available-balance context is required.
Full numbers, IBAN suffixes, OTP, amount/balance digits and references are not
converted into asset suffixes. Debit-source observations never assign a card parent.

Each proposal has an opaque stable ID derived from bank + captured evidence ID +
field role, never last4. Distinct evidence with equal suffix is not merged. A warning
shows other registered same-suffix assets. Repeated confirmation of the same
configuration is idempotent via the existing registry; a different configuration
cannot silently replace it. No candidate is an OwnedAsset. The existing manual
editor requires fresh ownership confirmation and explicit typed parent/facility
selection, with existing primary/supplementary rules. Users may correct the proposed
kind/suffix explicitly; subsequent disagreement is displayed for manual review.

Confirmation rechecks the enabled bank, evidence retry state, matched route and a
SHA-256 revision of both archived interpretations inside the same Room transaction
as the registry save. Stale archive changes cannot silently confirm an old proposal.
Already confirmed identity remains unchanged and any changed observation stays
visible as a conflict. No financial gate is lifted and no transaction is created.

Users may explicitly select an existing same-bank, same-kind, same-suffix asset
rather than create another asset from a later observation. No match is preselected;
fresh ownership confirmation is required. This writes evidence only, leaving its
stable ID, parent and credit role unchanged. A stable proposal binding is checked
across every archive revision, so recompare cannot bind that role to another asset.
Account and card roles within one capture keep separate bindings even at equal
suffix. Changed observations show a conflict against the retained confirmed asset.

A second existing LAST4 alias is written only after explicit confirmation in the
same Room transaction. Its versioned provenance binds stable per-role proposal UUID, Base64url captured ID and
both-archive SHA-256 revision. The alias stores the observed suffix; the existing
manual confirmation alias retains the user's final suffix. Retrying confirmation
does not duplicate the audit alias. Later manual edits clear only their existing
manual suffix alias and preserve this evidence trail. Historical aliases are not
ownership/matching authority and may remain ambiguous; this is no full financial
decision overlay or historical archive snapshot.

No tables/schema/migration: Beta schema remains v4. Existing backup/restore/reset
lifetime includes evidence and confirmed assets; no new preference namespace needed.

## Device journey

A single real system-dialog test runs in the existing latest-stable lifecycle job,
excluded from normal smoke with the other external lifecycle phases. The script
clears only the self-instrumenting Beta test package and resets READ_SMS before
instrumentation starts. It clicks actual Android permission buttons by controller
resource ID across interactive windows, saves permission-dialog screenshots and bounded resource-ID-only diagnostics (never node text), checks denial creates no scan/evidence, recreates the actual Beta
Activity, verifies manual setup remains available, then clicks a second explicit
Start and grants permission. The empty CI emulator is the only provider read;
there is no real phone/inbox. It waits for import completion before inspecting
storage. Existing external force-stop tests run once afterward. The independent Beta block runs before the existing M13 app block in the same lifecycle job so the new gate fails early; both blocks still run exactly once.

Additional UI coverage verifies RTL proposal editing, saved unconfirmed draft,
explicit stable identity, existing-asset selection, fresh confirmation after an archive revision changes, and no invented credit facility. Existing Return/Back,
permission callback, provider, restore, migration and isolation checks remain.

This is an independently reviewable increment, not complete M5/M10 or financial
acceptance. Existing app/source/data, legacy code, parser reference and goldens
are untouched; no receiver, live worker or automatic financial posting is added.

Local validation: 88 Beta unit tests passed with zero failures/errors/skips
(12 new). Android instrumentation sources compiled and the device test APK
assembled. Lint passed. Actual CI/device journey results are recorded in the PR.

The Beta library test APK explicitly targets SDK34 via testOptions.targetSdk,
matching the existing host app. AGP otherwise defaults library tests to minSdk26;
this test-only setting leaves the library/host production target and source unchanged.
The permission test asserts the effective test target, uses actual controller IDs,
and records the READ_SMS permission flags before execution. The first API36 run
failed to find the dialog under the old library test target; its exact active-window
cause was not recorded, so no unsupported root-cause claim is made.
