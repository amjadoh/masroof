# M11 encrypted copies and M12 preparation (Beta only)

This increment provides an actual Settings document export/restore flow for
**encrypted copies from this Beta installation**. It does not complete M11/M12,
provide cross-device migration, or read any production database or preference.
Old production code is retained.

## User boundary and origin trust

Settings displays the restriction before either operation and in the password
confirmation: copies restore only in the current Beta installation. Uninstalling
the app, clearing Android app data, or losing its Android Keystore origin key
makes these copies unrestorable.
A confirmed Beta reset retains that key, so a deliberately exported copy can
restore after resetting Beta. Export creates a Beta-only nonextractable
HMAC-SHA256 key under `masroof.beta.archive.origin.v1`; import never creates a
missing key or adopts a foreign origin. The key is never placed in preferences,
files, document metadata, diagnostics, or the exported copy.

This deliberate restriction prevents a password-authenticated but fabricated
external database from acquiring financial authority. Knowing a copy's password
is insufficient to create approved financial rows: the complete payload must
also carry a valid seal from the existing trusted installation. Cross-device,
reinstall, key recovery, explicit foreign-origin review, and production import
remain future work. This is not a portable disaster-recovery completion claim.
A compromised app UID/Keystore signing oracle is outside this boundary.

The user enters a 12-256-character password and repeats it for export. Import has
an explicit replacement confirmation. Android's document picker grants access
only to the chosen URI. The password is not saved in preferences, saved instance
state, logs or files. Cancel never starts a database operation. Password arrays
and PBEKeySpec are cleared after use; immutable UI strings and provider memory
cannot be guaranteed erased by a managed runtime. A canceled picker/recreated
screen cannot proceed without the ephemeral password. Background work uses the
Beta FIFO executor/lifetime lock; other destructive actions and close are blocked
while an operation runs.

## Version 1 wire format

No ZIP, compression, entry names, arbitrary paths or serialized object graphs are
accepted. A fixed ASCII BETA/version marker, random 16-byte salt, random 8-byte
nonce prefix and 64-bit total plaintext length form the authenticated header.
PBKDF2-HMAC-SHA256 uses a fixed 600,000 iterations and 256-bit AES key; input cannot
select an algorithm, provider, iteration count or key length.

Payload is exactly three entries in fixed order: database.db, preferences.json,
manifest.json. Each has a signed 64-bit length followed by bytes. Database is
bounded to 512 MiB, each metadata entry to 2 MiB, and total payload is bounded
before reading/allocating. A final 32-byte installation HMAC authenticates the
version marker, entry lengths and every payload byte including the manifest.
The manifest binds BETA, the fixed Beta database name, schema and content hashes.

The payload is encrypted in 64 KiB AES-256-GCM chunks, each with a 128-bit tag.
Each nonce is the fresh random prefix plus its fixed-width 32-bit chunk index;
the entire header and chunk index are AAD. Total plaintext length determines
exact chunk sizes, number and final chunk. Truncation, replacement, reordering,
duplication and appended bytes fail closed. No unauthenticated whole-archive
buffer or attacker-sized allocation is needed. Private staging can contain a
previously authenticated chunk before later checks complete, but it is never
published, opened as the live database or assigned financial authority. All tags,
origin HMAC, exact payload length and input EOF must pass first.

Only then do the existing strict manifest, SHA, SQLite integrity/FK, supported
schema, generated Room table/column/index and migration checks run. A matching
Room identity hash alone remains insufficient. Migration occurs on a private
stage using `BetaDatabase.VERSION`, not on the historical copy. Installation
uses the existing durable REPLACING/COMMITTED journal and retained originals,
including WAL/SHM/journal files and JSON preferences. Export/import do not replace
the user's existing local snapshot pointer. Failed import preserves live data;
failed export may leave an incomplete encrypted document, which cannot import.
Storage errors never report successful recovery. A killed process may leave a
private unreferenced archive stage; explicit Beta reset clears such stages after
journal recovery. No automatic unrelated-file deletion is introduced.

## Verification and M12 preparation

The suite includes wrong passwords, unknown origin, corruption, malformed sizes,
truncation/trailing bytes, export failure simulating ENOSPC, password bounds,
randomized repeated exports, JSON preference fidelity, current-data rollback and
multiple encrypted chunk boundaries. Native tests exercise Android Keystore,
SQLite, restore-after-Beta-reset, 1,000 synthetic historical marker rows, and
an encrypted v1-to-current staged upgrade while the published v1 copy remains
unchanged. The JVM rehearsal uses 2,000 synthetic rows. These are an executable
starting harness, not financial/parity/performance acceptance or a device p95.

The existing single API 36 lifecycle job gains actual external UID force-stop
journeys after encrypted restore database installation, preference installation
and commit. It checks original/committed state after a new process starts. No
additional CI job, Android matrix or duplicate test execution is added.

Remaining qualification includes rich synthetic financial-history totals and
cross-screen parity at scale, low-space/power-loss on physical devices, signed
application-update rehearsal, independent M6 samples and performance budgets.
No installed production APK/data, signing key or real inbox is used.

References: [Android cryptography](https://developer.android.com/privacy-and-security/cryptography),
[Android Keystore](https://developer.android.com/privacy-and-security/keystore).
