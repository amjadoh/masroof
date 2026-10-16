# M7 history import and M10.1 optional onboarding increment

Beta now offers an explicit optional historical inbox scan. This is an evidence
comparison/review journey, not a financial import, full onboarding or completed
M7/M10. Production receiver, WorkManager, database, preferences, parser reference,
goldens and legacy code are unchanged. No live Beta receiver or automatic startup
scan is added. No real phone/inbox is used by development or CI tests.

## Explicit scope and permission

The user first enables supported banks in Beta, then opens Import messages and
chooses 7/30/90 days. Start asks for READ_SMS if absent. Denial leaves manual asset
setup available. Granting permission through system settings never starts work;
Start/Resume is required. READ_SMS is declared in the Beta library manifest so its
self-instrumenting test APK can request it; the host app already declares this
same permission. No receiver/service or production permission flow is changed.

The screen explains that the provider must expose messages within the selected
time range to identify the selected banks, and only eligible evidence is saved.
Sender/body are read transiently for routing; no data is written to the provider.
The full adapter registry routes before bank scope is applied. The engine remains
comparison only; ambiguous/suspected outcomes never become financial authority.

A session freezes its lower/upper provider timestamp bounds and enabled-bank IDs.
The saved date range/banks are displayed. Only the current enabled selection can
permit a new capture, so disabling a saved bank fails closed without deleting its
existing evidence. Re-enabling or adding a bank does not expand an existing scan.
Starting a new scan explicitly replaces only session metadata; evidence remains.
Provider IDs/time are preserved with PROVIDER_TIMESTAMP provenance. Mutating a
provider row behind an existing evidence identity records a conflict and never
rewrites raw, outcome or decisions.

## Bounded progress and recovery

Each provider query uses `(date, _id)` keyset order over the fixed inclusive date
window and consumes at most 25 rows before closing the cursor. No unsupported SQL
LIMIT token is appended to sortOrder. This bounds application memory and query
count to one per batch; it does not promise the provider internally limits its
SQL sort to 25 rows. The provider owns that query implementation. Scanning stops
at 10,000 rows, visibly incomplete with a shorter-period prompt. It never silently
claims the full window was scanned at the cap.

A string-only, versioned JSON checkpoint lives in existing Beta preferences. It
contains dates, bank IDs, last provider timestamp/ID and closed numeric counters;
no message text or exception data. Counts/date/order/schema/bank capability are
validated on read. No Room schema/table change or migration is required: version4
and all existing schemas remain unchanged. The shared maintenance lock protects
checkpoint/evidence operations and Beta backup/restore/reset. The checkpoint is
saved synchronously only after each row's Room evidence operation completes.
Cross-store atomicity is intentionally unnecessary: death between evidence and
checkpoint replays the same stable identity, which existing evidence idempotency
handles. A failed checkpoint or provider access leaves the earlier cursor usable.

Pause stops after the in-flight batch; Return and system Back are disabled while
an import/retry/recompare is busy. Beta UI work, container creation and destruction
share one process-wide FIFO I/O queue. Activity destruction enqueues close without
blocking the main thread; a recreated Activity creates its container behind the
old operation and close. Cancellation during queued creation still has a queued
close. This prevents old work from racing a recreated container reset/restore.
OS death needs no background job.
Reopening shows the saved scope and explicit Resume, never auto-reads the inbox.
Restoring a Beta backup restores its checkpoint with the corresponding evidence;
reset removes both. Provider messages removed while paused cannot be recovered
from the provider; already captured evidence remains local in Beta.

Period scan complete means every returned row in that window was considered,
not every row was financially processed. Separate saved states say Needs retry,
Needs review or Comparison saved only. Retry handles up to100 pending evidence
rows. Recompare acts on an explicit saved evidence row, including a bank that was
subsequently disabled, and retains the durable user decision. A keyset metadata
page of25 evidence rows avoids loading raw text into presentation. No financial
posting or reconciliation occurs per history row.

## Verification and limits

Local JDK17/Gradle8.8 validation passed76 Beta unit tests (11 new), device APK assembly and lint. Device/provider/process-death execution remains a CI gate until its actual results are recorded.

Added tests cover bounded25+3 paging at identical timestamps, range/order/cap
validation, source/permission failure, crash between capture and checkpoint,
conflict/retry/exclusion counters, metadata redaction, Beta backup/restore/reset,
disabled-bank scope, unsupported checkpoint versions, deterministic parked work→close→recreation/reset ordering, busy Return/Back, controlled deniedpermission callback, actual emulator provider
queries with28 synthetic rows and cleanup, and screen saved-state restoration
without starting a scan. Existing isolation/golden tests are retained unchanged.

`beta-process-death.sh` installs only the Beta self-instrumenting test APK in the
existing latest-stable Android lifecycle CI job. Its two external `am force-stop`
journeys verify a new PID after interruption during comparison (durable retry),
and after comparison before checkpoint (replay plus preserved manual decision).
They use synthetic explicit fixtures and never query the inbox. They are excluded
from smoke so these phase tests are not duplicated or package-cleared mid-journey.
Actual CI execution, not compilation alone, is required before claiming these
process-death/provider gates passed.

Still outstanding: full permission-denied system-dialog journey/snapshots,
complete onboarding to a real dashboard, review correction UX, live Beta intake,
M8 authoritative ledger adapters, live/history/reparse ledger parity, device-scale
performance acceptance and final Beta acceptance. No cleanup or promotion is
included by this independently reviewable increment.
