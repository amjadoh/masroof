package com.baraa.masroof.lab.beta.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.baraa.masroof.lab.beta.BetaContainer
import com.baraa.masroof.lab.finance.asset.*
import com.baraa.masroof.lab.finance.classification.*
import com.baraa.masroof.lab.finance.dashboard.*
import com.baraa.masroof.lab.finance.dashboard.producer.*
import com.baraa.masroof.lab.finance.posting.*
import com.baraa.masroof.lab.finance.value.*
import java.time.*
import java.util.concurrent.Executor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated synthetic ledger at the default 10,000-row ceiling; no inbox or production data. */
@RunWith(AndroidJUnit4::class)
class BetaLongHistoryReadTest {
    @Test fun completeBoundedHistoryUsesConstantQueriesAndNeverWritesDuringRead(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        BetaContainer.resetOffline(context)
        val queries = java.util.Collections.synchronizedList(mutableListOf<String>())
        val db = Room.databaseBuilder(context, BetaDatabase::class.java, BetaDatabase.NAME)
            .setQueryCallback({ sql, _ -> queries.add(sql) }, Executor { it.run() }).build()
        try {
            val bank = BankId("BANK_ALJAZIRA")
            val asset = OwnedAsset(AssetId(Environment.BETA, bank, "long-history"), AssetKind.ACCOUNT, "1234")
            val at = Instant.parse("2026-10-17T12:00:00Z")
            val zone = ZoneId.of("Asia/Riyadh")
            BetaAssetRegistry(db).register(asset)
            db.withTransaction {
                repeat(10_000) { index ->
                    val input = FinancialClassificationInput(PostingKey(Environment.BETA, "long-$index"), PostingRevision(0, 0),
                        FinancialType.EXPENSE, Money.of("1", Currency.SAR), TransactionTime(at.minusSeconds(index.toLong()), zone),
                        FinancialEndpoint.Owned(asset.id), FinancialEndpoint.External, mapOf(asset.id to asset), setOf("evidence-$index"), true)
                    assertTrue(RoomBetaFinancialLedger(db).post(input) is FinancialPostingResult.Applied)
                }
            }
            val query = DashboardQuery(Environment.BETA, at.minusSeconds(20_000), at.plusSeconds(60), zone, Currency.SAR)
            val elapsed = mutableListOf<Long>()
            repeat(3) {
                queries.clear()
                val start = android.os.SystemClock.elapsedRealtime()
                val result = BetaDashboardProducer(RoomBetaDashboardSource(db)).readWithCoverage(query)
                elapsed += android.os.SystemClock.elapsedRealtime() - start
                assertEquals(10_000, result.snapshot.transactions.size)
                val statements = queries.toList()
                assertTrue("Queries must be bounded independently of rows: ${statements.size}", statements.size < 50)
                assertFalse(statements.any { sql -> Regex("(?i)^\\s*(INSERT|UPDATE|DELETE|REPLACE)").containsMatchIn(sql) })
                assertFalse(statements.any { sql -> "beta_ledger_history" in sql || Regex("(?i)SELECT.*\\bbody\\b").containsMatchIn(sql) })
                assertTrue("10,000-row read exceeded 30-second emulator budget", elapsed.last() < 30_000)
            }
            val output = java.io.File(context.getExternalFilesDir(null), "beta-performance/long-history.txt")
            output.parentFile?.mkdirs()
            android.util.Log.i("BetaLongHistory", "10000 rows cold/warm milliseconds=$elapsed")
            output.writeText("Synthetic current rows: 10000\nCold/warm read milliseconds: $elapsed\nBudget per read: 30000 ms\nNo read-side writes, raw SMS body reads or audit-history scans\n")
            assertThrows(BetaDashboardReadLimitExceeded::class.java) {
                runBlocking { RoomBetaDashboardSource(db, limits = BetaDashboardReadLimits(currentRows = 9_999)).load(query) }
            }
        } finally { db.close(); BetaContainer.resetOffline(context) }
    }
}
