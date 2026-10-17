package com.baraa.masroof.lab.beta.data

import android.database.Cursor
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.baraa.masroof.lab.beta.application.corrections.BetaLedgerActivity
import com.baraa.masroof.lab.finance.asset.*
import com.baraa.masroof.lab.finance.classification.FinancialClassificationInput
import com.baraa.masroof.lab.finance.classification.FinancialEndpoint
import com.baraa.masroof.lab.finance.dashboard.*
import com.baraa.masroof.lab.finance.dashboard.producer.*
import com.baraa.masroof.lab.finance.posting.*
import com.baraa.masroof.lab.finance.value.*
import java.security.MessageDigest
import java.time.Clock
import java.util.Collections

/** The v6 ledger stores time inside checksummed financial payloads, not an SQL time index.
 * Consequently this adapter reads a bounded CURRENT set, never the append-only audit history.
 * Overflow fails explicitly: a truncated set is never presented as complete or as zero.
 */
data class BetaDashboardReadLimits(val currentRows: Int = 10_000, val assets: Int = 2_000, val evidenceIds: Int = 10_000) {
    init { require(currentRows in 1..100_000 && assets in 1..10_000 && evidenceIds in 1..100_000) }
}

class BetaDashboardReadLimitExceeded(val collection: String, val limit: Int) : IllegalStateException(
    "Beta dashboard $collection exceeds the explicit read limit $limit; no partial snapshot was returned",
)

/** No parser, market service, raw SMS, per-row DAO call, migration or write entry point.
 * Each invocation owns one Room transaction; the data revision covers every loaded collection.
 * The optional v7 adoption gate is read within that same snapshot so held postings never leak.
 */
class RoomBetaDashboardSource(
    private val database: BetaDatabase,
    private val periods: DashboardPeriods = DashboardPeriods(),
    private val limits: BetaDashboardReadLimits = BetaDashboardReadLimits(),
    private val clock: Clock = Clock.systemUTC(),
) : DashboardReadSource {
    init { require(database.openHelper.databaseName == BetaDatabase.NAME) { "Dashboard requires the isolated Beta database" } }

    override suspend fun load(query: DashboardQuery): DashboardReadBatch {
        require(query.environment == Environment.BETA && query.currency == Currency.SAR)
        val frozenPeriods = periods.copy(statements = Collections.unmodifiableMap(LinkedHashMap(periods.statements)))
        val windows = listOf(DisplayPeriod(query.from, query.until, query.zone, PeriodKind.SELECTED, null)) +
            listOfNotNull(frozenPeriods.salary, frozenPeriods.calendar) + frozenPeriods.statements.values
        require(windows.all { it.zone == query.zone })
        return database.withTransaction {
            val digest = MessageDigest.getInstance("SHA-256")
            fun fingerprint(value: String?) {
                // Length framing distinguishes null, empty and concatenation collisions.
                val bytes = value?.toByteArray(Charsets.UTF_8)
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes?.size ?: -1).array())
                if (bytes != null) digest.update(bytes)
            }
            fingerprint("BETA_DASHBOARD_CURRENT_1")
            fingerprint(query.toString()); fingerprint(frozenPeriods.toString())
            val schema = database.openHelper.readableDatabase.version
            check(schema in 6..7) { "Unsupported Beta dashboard schema" }
            fingerprint(schema.toString())
            val capabilities = database.query(SimpleSQLiteQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('beta_financial_adoption','beta_financial_approval_retry')",
            )).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }
            val hasAdoption = "beta_financial_adoption" in capabilities
            val hasApprovalRetry = "beta_financial_approval_retry" in capabilities
            check(schema != 7 || (hasAdoption && hasApprovalRetry)) { "Missing Beta financial adoption gates" }
            val held = linkedSetOf<String>()
            if (hasAdoption) {
                database.query(SimpleSQLiteQuery(
                    "SELECT transactionId,status FROM beta_financial_adoption ORDER BY evidenceId LIMIT ?",
                    arrayOf(limits.currentRows + 1),
                )).use { cursor ->
                    var count = 0
                    while (cursor.moveToNext()) {
                        if (++count > limits.currentRows) throw BetaDashboardReadLimitExceeded("adoption rows", limits.currentRows)
                        val id = cursor.getString(0); val status = cursor.getString(1)
                        check(id.isNotBlank() && status in setOf("ACTIVE", "REVIEW")) { "Invalid Beta adoption state" }
                        fingerprint(id); fingerprint(status)
                        if (status != "ACTIVE") held += id
                    }
                }
            }
            // A safety hold is not an intentional non-financial dismissal. Neither a durable
            // retry nor a review-only correction may turn an incomplete period into known zero.
            var completeLedger = database.query(SimpleSQLiteQuery(
                "SELECT EXISTS(SELECT 1 FROM beta_correction_current WHERE environment=? AND activity='REVIEW') " +
                    "OR EXISTS(SELECT 1 FROM beta_evidence_retry)" +
                    (if (hasApprovalRetry) " OR EXISTS(SELECT 1 FROM beta_financial_approval_retry)" else ""), arrayOf(Environment.BETA.name),
            )).use { it.moveToFirst(); it.getInt(0) == 0 }
            if (held.isNotEmpty()) completeLedger = false
            fingerprint(completeLedger.toString())
            val unhandledEvidence = linkedSetOf<String>()
            database.query(SimpleSQLiteQuery(
                "SELECT r.id,c.transactionId,c.correction,c.interpretation,c.transition,c.activity,c.payload " +
                    "FROM beta_raw_evidence r LEFT JOIN beta_correction_evidence_claim x ON x.environment=? AND x.evidenceId=r.id " +
                    "LEFT JOIN beta_correction_current c ON c.environment=x.environment AND c.transactionId=x.transactionId " +
                    "ORDER BY r.id LIMIT ?",
                arrayOf(Environment.BETA.name, limits.evidenceIds + 1),
            )).use { cursor ->
                var count = 0
                while (cursor.moveToNext()) {
                    if (++count > limits.evidenceIds) throw BetaDashboardReadLimitExceeded("evidence identities", limits.evidenceIds)
                    repeat(7) { fingerprint(cursor.optional(it)) }
                    val id = cursor.getString(0)
                    val correction = cursor.optional(6)?.let(BetaCorrectionCodec::decode)
                    if (correction != null) check(correction.overlay.key == PostingKey(Environment.BETA, cursor.getString(1)) &&
                        correction.overlay.correctionRevision == cursor.getLong(2) && correction.overlay.interpretationRevision == cursor.getLong(3) &&
                        correction.transition == cursor.getLong(4) && correction.activity.name == cursor.getString(5)) { "Inconsistent evidence correction state" }
                    else check(cursor.isNull(5)) { "Missing evidence correction payload" }
                    if (correction?.activity != BetaLedgerActivity.SUPPRESSED) unhandledEvidence += id
                }
            }
            val assets = database.query(SimpleSQLiteQuery(
                "SELECT environment,bankId,localId,kind,last4,parentLocalId,creditCardRole FROM beta_asset " +
                    "WHERE environment=? ORDER BY bankId,localId LIMIT ?",
                arrayOf(Environment.BETA.name, limits.assets + 1),
            )).use { cursor ->
                val result = mutableListOf<OwnedAsset>()
                while (cursor.moveToNext()) {
                    if (result.size == limits.assets) throw BetaDashboardReadLimitExceeded("confirmed assets", limits.assets)
                    repeat(7) { fingerprint(cursor.optional(it)) }
                    val id = AssetId(Environment.valueOf(cursor.getString(0)), BankId(cursor.getString(1)), cursor.getString(2))
                    result += OwnedAsset(id, AssetKind.valueOf(cursor.getString(3)), cursor.optional(4),
                        cursor.optional(5)?.let { AssetId(id.environment, id.bank, it) }, cursor.optional(6)?.let(CreditCardRole::valueOf))
                }
                result
            }
            val registry = assets.associateBy { it.id }
            val rows = mutableListOf<DashboardLedgerRow>()
            val postingFacts = mutableListOf<DashboardPostingFacts>()
            database.query(SimpleSQLiteQuery(
                "SELECT l.transactionId,l.correction,l.interpretation,l.payload," +
                    "c.correction,c.interpretation,c.transition,c.activity,c.payload " +
                    "FROM beta_ledger_current l LEFT JOIN beta_correction_current c " +
                    "ON c.environment=l.environment AND c.transactionId=l.transactionId " +
                    "WHERE l.environment=? ORDER BY l.transactionId LIMIT ?",
                arrayOf(Environment.BETA.name, limits.currentRows + 1),
            )).use { cursor ->
                var count = 0
                while (cursor.moveToNext()) {
                    if (++count > limits.currentRows) throw BetaDashboardReadLimitExceeded("current ledger rows", limits.currentRows)
                    repeat(9) { fingerprint(cursor.optional(it)) }
                    val id = cursor.getString(0)
                    val key = PostingKey(Environment.BETA, id)
                    val revision = PostingRevision(cursor.getLong(1), cursor.getLong(2))
                    val correction = cursor.optional(8)?.let(BetaCorrectionCodec::decode)
                    if (correction != null) {
                        check(correction.overlay.key == key && correction.overlay.correctionRevision == cursor.getLong(4) &&
                            correction.overlay.interpretationRevision == cursor.getLong(5) && correction.transition == cursor.getLong(6) &&
                            correction.activity.name == cursor.getString(7)) { "Inconsistent correction current row" }
                    } else check(cursor.isNull(7)) { "Missing correction payload" }
                    val active = id !in held && (correction == null || correction.activity == BetaLedgerActivity.ACTIVE)
                    if (id in held || correction?.activity == BetaLedgerActivity.REVIEW) completeLedger = false
                    if (!active) continue // Audit snapshots are never decoded/projected as current financial rows.
                    if (correction != null) check(revision == PostingRevision(correction.overlay.correctionRevision,
                        correction.overlay.interpretationRevision)) { "Active correction and ledger revisions differ" }
                    val posting = BetaLedgerCodec.decode(cursor.getString(3))
                    check(posting.command.key == key && posting.command.revision == revision) { "Inconsistent financial current row" }
                    // Registry edits require explicit financial reconsideration. A stale frozen
                    // ownership snapshot is unavailable, not a crash or a silent zero balance.
                    if ((listOf(posting.classification.input) + posting.observations.map { it.input }).any { input ->
                            !matchesCurrentRegistry(input, registry)
                        }) { completeLedger = false; continue }
                    unhandledEvidence.removeAll(posting.command.evidenceIds)
                    if (windows.none { posting.command.time.instant >= it.from && posting.command.time.instant < it.until }) continue
                    rows += DashboardLedgerRow(posting, true)
                    val metadata = correction?.overlay?.metadata.orEmpty()
                    if (metadata.keys.any { it == "merchant" || it == "counterparty" }) postingFacts += DashboardPostingFacts(
                        key, revision, posting.command.evidenceIds.sorted().first(), metadata["merchant"], metadata["counterparty"],
                    )
                }
            }
            if (unhandledEvidence.isNotEmpty()) completeLedger = false
            // Review queues currently mix comparison, processing retry and correction scopes.
            // They are not one authoritative M9 review count. Do not manufacture a global zero.
            val token = digest.digest().joinToString("") { "%02x".format(it) }
            DashboardReadBatch(query, ReadRevision("beta-current-v1:$token", clock.instant()),
                Collections.unmodifiableList(rows), Collections.unmodifiableList(assets),
                DashboardCoverage(completeLedger = completeLedger, completeRegistry = true, completeFeeFacts = false,
                    reviewRequiredCount = null, atomicRuntimeSource = true),
                frozenPeriods, postingFacts = Collections.unmodifiableList(postingFacts))
        }
    }
}

private fun Cursor.optional(index: Int): String? = if (isNull(index)) null else getString(index)

private fun matchesCurrentRegistry(input: FinancialClassificationInput, registry: Map<AssetId, OwnedAsset>): Boolean {
    val endpoints = listOfNotNull((input.source as? FinancialEndpoint.Owned)?.id,
        (input.destination as? FinancialEndpoint.Owned)?.id, input.financingLoan).toSet()
    val dependencies = endpoints + endpoints.mapNotNull { input.assets[it]?.parentId }
    return dependencies.all { id -> input.assets[id]?.let { it.id == id && registry[id] == it } == true }
}
