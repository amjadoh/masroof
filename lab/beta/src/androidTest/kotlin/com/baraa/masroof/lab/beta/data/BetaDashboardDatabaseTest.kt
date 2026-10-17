package com.baraa.masroof.lab.beta.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.baraa.masroof.lab.beta.BetaContainer
import com.baraa.masroof.lab.beta.application.corrections.BetaCorrectionResult
import com.baraa.masroof.lab.finance.asset.*
import com.baraa.masroof.lab.finance.classification.*
import com.baraa.masroof.lab.finance.corrections.*
import com.baraa.masroof.lab.finance.dashboard.*
import com.baraa.masroof.lab.finance.dashboard.producer.*
import com.baraa.masroof.lab.finance.posting.*
import com.baraa.masroof.lab.finance.value.*
import java.math.BigDecimal
import java.time.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Real Android SQLite/current lifecycle reads; all rows are synthetic and Beta-only. */
@RunWith(AndroidJUnit4::class)
class BetaDashboardDatabaseTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: BetaDatabase
    private val bank = BankId("BANK_ALJAZIRA")
    private val asset = OwnedAsset(AssetId(Environment.BETA, bank, "synthetic-dashboard"), AssetKind.ACCOUNT, "1234")
    private val now = Instant.parse("2026-10-17T12:00:00Z")
    private val zone = ZoneId.of("Asia/Riyadh")
    private val query = DashboardQuery(Environment.BETA, now.minusSeconds(60), now.plusSeconds(60), zone, Currency.SAR)
    private fun input(id: String = "synthetic-transaction") = FinancialClassificationInput(PostingKey(Environment.BETA, id),
        PostingRevision(0, 0), FinancialType.EXPENSE, Money.of("42.35", Currency.SAR), TransactionTime(now, zone),
        FinancialEndpoint.Owned(asset.id), FinancialEndpoint.External, mapOf(asset.id to asset), setOf("synthetic-evidence-$id"), true)
    private fun openDatabase(observer: (String) -> Unit = {}) = Room.databaseBuilder(context, BetaDatabase::class.java, BetaDatabase.NAME)
        .setQueryCallback({ statement, _ -> observer(statement) }, Executor { it.run() }).build()
    @Before fun open() = runBlocking {
        BetaContainer.resetOffline(context); db = openDatabase(); BetaAssetRegistry(db).register(asset)
    }
    @After fun close() { db.close(); BetaContainer.resetOffline(context) }

    @Test fun deviceReaderFollowsSuppressionAndReopenWithoutChangingFinancialHistory() = runBlocking {
        val value = input()
        assertTrue(RoomBetaFinancialLedger(db).post(value) is FinancialPostingResult.Applied)
        val producer = BetaDashboardProducer(RoomBetaDashboardSource(db))
        val active = producer.readWithCoverage(query)
        assertEquals(1, active.snapshot.transactions.size)
        assertFalse(DashboardSection.RUNTIME_SOURCE in active.unavailableSections)
        val request = CorrectionRequest("synthetic-suppress", value.key, 1, 0, value.evidenceIds,
            FinancialDecision.SuppressAsNonFinancial, expectedCorrectionRevision = 0)
        assertTrue(RoomBetaCorrectionLifecycle(db).submit(CorrectionObservation(value, ObservationSafety.ORDINARY), request) is BetaCorrectionResult.Applied)
        val suppressed = producer.readWithCoverage(query)
        assertTrue(suppressed.snapshot.transactions.isEmpty())
        assertEquals(BigDecimal("0.00"), suppressed.snapshot.fleetFlow!!.externalOut.amount)
        assertNotEquals(active.snapshot.revision!!.token, suppressed.snapshot.revision!!.token)
        assertEquals(1, RoomBetaFinancialLedger(db).history(value.key).size)
        db.close(); db = openDatabase()
        val reopened = BetaDashboardProducer(RoomBetaDashboardSource(db)).readWithCoverage(query)
        assertEquals(suppressed.snapshot.revision!!.token, reopened.snapshot.revision!!.token)
        assertTrue(reopened.snapshot.transactions.isEmpty())
        assertEquals(1, RoomBetaFinancialLedger(db).history(value.key).size)
    }

    @Test fun deviceReaderRejectsTruncationAndKeepsSmsFeeCoverageUnknown() = runBlocking {
        val value = input().copy(amount = Money.of("10", Currency.USD), appliedFx = AppliedFx(BigDecimal("3.75"), "SMS"))
        assertTrue(RoomBetaFinancialLedger(db).post(value) is FinancialPostingResult.Applied)
        val result = BetaDashboardProducer(RoomBetaDashboardSource(db)).readWithCoverage(query)
        assertEquals(BigDecimal("37.50"), result.snapshot.transactions.single().sarEquivalent!!.amount)
        assertNull(result.snapshot.fleetFlow)
        assertNull(result.snapshot.totals!!.accountOutflow)
        assertNotNull(result.snapshot.analysis)
        assertTrue(RoomBetaFinancialLedger(db).post(input("another")) is FinancialPostingResult.Applied)
        try {
            RoomBetaDashboardSource(db, limits = BetaDashboardReadLimits(currentRows = 1)).load(query)
            fail("A limited result must not be reported as complete")
        } catch (expected: BetaDashboardReadLimitExceeded) { assertEquals("current ledger rows", expected.collection) }
    }

    @Test fun deviceSnapshotAndConcurrentCorrectionUseOneConsistentDatabaseTransaction() = runBlocking {
        val value = input()
        assertTrue(RoomBetaFinancialLedger(db).post(value) is FinancialPostingResult.Applied)
        val writerArmed = AtomicBoolean(false)
        val writerEntered = CountDownLatch(1)
        val second = openDatabase { statement -> if (writerArmed.get() && statement.startsWith("BEGIN")) writerEntered.countDown() }
        second.openHelper.writableDatabase
        val reachedEnd = CountDownLatch(1); val release = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        val clock = object : Clock() {
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: ZoneId): Clock = this
            override fun instant(): Instant { reachedEnd.countDown(); check(release.await(10, TimeUnit.SECONDS)); return now }
        }
        try {
            val read = pool.submit(Callable { runBlocking { RoomBetaDashboardSource(db, clock = clock).load(query) } })
            assertTrue(reachedEnd.await(10, TimeUnit.SECONDS))
            writerArmed.set(true)
            val write = pool.submit(Callable { runBlocking {
                RoomBetaCorrectionLifecycle(second).submit(CorrectionObservation(value, ObservationSafety.ORDINARY),
                    CorrectionRequest("concurrent-device-suppression", value.key, 1, 0, value.evidenceIds,
                        FinancialDecision.SuppressAsNonFinancial, expectedCorrectionRevision = 0))
            } })
            assertTrue(writerEntered.await(10, TimeUnit.SECONDS))
            assertFalse(write.isDone)
            release.countDown()
            assertEquals(1, read.get(15, TimeUnit.SECONDS).rows.size)
            assertTrue(write.get(15, TimeUnit.SECONDS) is BetaCorrectionResult.Applied)
            second.close()
            assertTrue(RoomBetaDashboardSource(db).load(query).rows.isEmpty())
        } finally { release.countDown(); pool.shutdownNow(); second.close() }
    }
}
