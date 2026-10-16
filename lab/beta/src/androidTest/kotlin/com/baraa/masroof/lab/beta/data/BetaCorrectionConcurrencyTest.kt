package com.baraa.masroof.lab.beta.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.baraa.masroof.lab.beta.BetaContainer
import com.baraa.masroof.lab.beta.application.corrections.*
import com.baraa.masroof.lab.finance.asset.*
import com.baraa.masroof.lab.finance.classification.*
import com.baraa.masroof.lab.finance.corrections.*
import com.baraa.masroof.lab.finance.posting.*
import com.baraa.masroof.lab.finance.value.*
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BetaCorrectionConcurrencyTest {
    @Test fun twoDatabaseInstancesCommitExactlyOneDecisionAndMatchingFinancialTransition() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        BetaContainer.resetOffline(context)
        val first = Room.databaseBuilder(context, BetaDatabase::class.java, BetaDatabase.NAME).build()
        val second = Room.databaseBuilder(context, BetaDatabase::class.java, BetaDatabase.NAME).build()
        val executor = Executors.newFixedThreadPool(4)
        val asset = OwnedAsset(AssetId(Environment.BETA, BankId("BANK_ALJAZIRA"), "account"), AssetKind.ACCOUNT)
        val input = FinancialClassificationInput(PostingKey(Environment.BETA, "correction-race"), PostingRevision(0, 0), FinancialType.EXPENSE,
            Money.of("42", Currency.SAR), TransactionTime(Instant.parse("2026-10-17T12:00:00Z"), ZoneId.of("Asia/Riyadh")), FinancialEndpoint.Owned(asset.id),
            FinancialEndpoint.External, mapOf(asset.id to asset), setOf("race-evidence"), false)
        val observation = CorrectionObservation(input, ObservationSafety.ORDINARY)
        try {
            runBlocking { BetaAssetRegistry(first).register(asset) }; second.openHelper.writableDatabase
            val results = executor.invokeAll((0 until 16).map { index -> Callable { runBlocking {
                RoomBetaCorrectionLifecycle(if (index % 2 == 0) first else second).submit(observation,
                    CorrectionRequest("race-$index", input.key, 1, 0, input.evidenceIds, FinancialDecision.ResolveFinancialType(FinancialType.EXPENSE), mapOf("note" to "$index")))
            } } }).map { it.get(30, TimeUnit.SECONDS) }
            assertEquals(1, results.count { it is BetaCorrectionResult.Applied })
            assertEquals(15, results.count { it == BetaCorrectionResult.Rejected(CorrectionRejection.STALE) })
            runBlocking {
                val lifecycle = RoomBetaCorrectionLifecycle(second)
                assertEquals(BetaLedgerActivity.ACTIVE, lifecycle.read(input.key)!!.activity)
                assertEquals(1, lifecycle.history(input.key).size)
                assertEquals(1, RoomBetaFinancialLedger(first).history(input.key).size)
                assertEquals(input.key.transactionId, second.correctionDao().evidenceOwner("BETA", "race-evidence"))
                assertNotNull(RoomBetaFinancialLedger(first).read(input.key))
            }
            first.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        } finally { executor.shutdownNow(); first.close(); second.close(); BetaContainer.resetOffline(context) }
    }
}
