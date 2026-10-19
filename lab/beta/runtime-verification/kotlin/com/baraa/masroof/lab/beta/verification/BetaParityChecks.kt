package com.baraa.masroof.lab.beta.verification

import android.content.Context
import androidx.room.Room
import com.baraa.masroof.lab.beta.*
import com.baraa.masroof.lab.beta.application.*
import com.baraa.masroof.lab.beta.application.evidence.*
import com.baraa.masroof.lab.beta.application.history.HistorySession
import com.baraa.masroof.lab.beta.data.*
import com.baraa.masroof.lab.finance.asset.*
import com.baraa.masroof.lab.finance.classification.*
import com.baraa.masroof.lab.finance.dashboard.*
import com.baraa.masroof.lab.finance.posting.*
import com.baraa.masroof.lab.finance.value.*
import java.time.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*

/** Fresh isolated data only. These verify the contract, not a legacy migration. */
object BetaParityChecks {
    private val bank=BankId("BANK_ALJAZIRA")
    private val at=Instant.parse("2026-10-18T09:00:00Z")
    private fun isolated(context: Context,block: (BetaContainer)->Unit) {
        BetaContainer.resetOffline(context)
        try {BetaContainer(context).use(block)} finally {BetaContainer.resetOffline(context)}
    }
    private fun begin(beta: BetaContainer) {
        beta.beginOnboarding();assertEquals(BetaOnboardingStep.PERMISSION,beta.onboardingStep())
        assertTrue(beta.bankChoices().all {it.state==BankSelectionState.ENABLED})
        beta.advanceOnboarding(BetaOnboardingStep.PERMISSION)
    }
    fun freshSchemaHasSixteenTablesAndQueryableFinancialFacts(context: Context) {
        isolated(context) { beta ->
            val db=Room.databaseBuilder(context,BetaDatabase::class.java,BetaDatabase.NAME).build()
            try {
                val names=db.openHelper.writableDatabase.query("SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'beta_%'").use {c->buildSet {while(c.moveToNext()) add(c.getString(0))}}
                assertEquals(16,names.size)
                assertTrue(names.containsAll(listOf("beta_parsed_event","beta_financial_transaction","beta_audit_log","beta_learning_label")))
                val columns=db.openHelper.writableDatabase.query("PRAGMA table_info(beta_financial_transaction)").use {c->buildSet {while(c.moveToNext()) add(c.getString(1))}}
                assertTrue(columns.containsAll(listOf("occurredAtEpochMillis","amountDecimal","amountCurrency","financialType")))
            } finally {db.close()}
            assertEquals(BetaStartState.NEW,beta.startState())
        }
    }
    fun ownedCardsCanFinishSetupAndCreateOnlyOneConfigurationTask(context: Context) = isolated(context) { beta ->
        begin(beta)
        val messages=(1..3).map { n -> EvidenceMessage(n.toString(),"AlJazira","POS Purchase\nCard: 5678\nCard type: credit\nAmount: $n SAR\nAvailable balance: 100 SAR\nDate: 2026-10-18 12:00",at.plusSeconds(n.toLong()),ReceiptTimeSource.USER_SUPPLIED) }
        val ids=beta.importEvidence(EvidenceRequest("parity-cards",EvidenceOrigin.EXPLICIT_SAMPLE),messages).map { (it as EvidenceResult.Stored).summary.id }
        beta.advanceOnboarding(BetaOnboardingStep.IMPORT)
        beta.decideOwnership(beta.ownershipSnapshot().candidates.single(),true)
        beta.advanceOnboarding(BetaOnboardingStep.ASSETS)
        beta.processAutomaticEvidence();beta.advanceOnboarding(BetaOnboardingStep.REVIEW)
        assertEquals(BetaOnboardingStep.COMPLETE,beta.onboardingStep())
        assertEquals(1,beta.cardSetupTasks().size)
        assertTrue(beta.evidencePage(needsReviewOnly=true).isEmpty()) // One card task, not three SMS tasks.
        assertTrue(ids.all {beta.financialPosting(PostingKey(Environment.BETA,"evidence:$it"))!=null})
        val view=beta.dashboard(DashboardQuery(Environment.BETA,Instant.parse("2026-09-26T21:00:00Z"),Instant.parse("2026-10-26T21:00:00Z"),ZoneId.of("Asia/Riyadh"),Currency.SAR))
        assertEquals(1,view.snapshot.standaloneCreditCards.size)
        assertEquals(3,view.snapshot.transactions.size)
        assertTrue(view.snapshot.creditFacilities.isEmpty())
        val card=beta.assets().single()
        beta.saveManualAsset(ManualAssetDraft(card.id,AssetKind.CREDIT_CARD,"5678",AssetId(Environment.BETA,bank,"facility"),CreditCardRole.PRIMARY,true,true,card,true))
        beta.processAutomaticEvidence()
        assertTrue(beta.cardSetupTasks().isEmpty())
        assertTrue(ids.all {beta.financialPosting(PostingKey(Environment.BETA,"evidence:$it"))!=null})
    }
    fun liveThenHistoricalReceiptHasOneIdentityAndOnePosting(context: Context) = isolated(context) { beta ->
        begin(beta);beta.advanceOnboarding(BetaOnboardingStep.IMPORT);beta.advanceOnboarding(BetaOnboardingStep.ASSETS);beta.advanceOnboarding(BetaOnboardingStep.REVIEW)
        beta.saveManualAsset(ManualAssetDraft(AssetId(Environment.BETA,bank,"account"),AssetKind.ACCOUNT,"1234",ownedConfirmed=true))
        val message=EvidenceMessage("service-center-id","AlJazira","Cash withdrawal\nFrom account: 1234\nAmount: 42 SAR\nDate: 2026-10-18 12:00",at,ReceiptTimeSource.SERVICE_CENTER_TIMESTAMP)
        val id=requireNotNull(beta.captureLive(message));val generation=requireNotNull(beta.storageGeneration())
        assertNotNull(beta.evidenceSummary(id).retryStage)
        val faultDb=Room.databaseBuilder(context,BetaDatabase::class.java,BetaDatabase.NAME).build()
        try {
            faultDb.openHelper.writableDatabase.execSQL("CREATE TRIGGER parity_live_failure BEFORE INSERT ON beta_financial_transaction BEGIN SELECT RAISE(ABORT,'synthetic posting failure'); END")
            assertFalse(beta.processLive(id,generation))
            assertEquals(EvidenceStage.RECONCILIATION,beta.evidenceSummary(id).retryStage)
            assertTrue(id in beta.pendingEvidenceIds())
        } finally {faultDb.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS parity_live_failure");faultDb.close()}
        assertTrue(beta.processLive(id,generation))
        assertNotNull(beta.financialPosting(PostingKey(Environment.BETA,"evidence:$id")))
        val historical=beta.importEvidence(EvidenceRequest(HistorySession.NAMESPACE,EvidenceOrigin.EXPLICIT_HISTORY,setOf(bank)),listOf(message.copy(providerId="phone-row-42",timeSource=ReceiptTimeSource.PROVIDER_TIMESTAMP))).single() as EvidenceResult.Stored
        assertEquals(id,historical.summary.id)
        beta.processAutomaticEvidence()
        assertEquals(1,beta.evidencePage().size)
        assertEquals(1,beta.financialHistory(PostingKey(Environment.BETA,"evidence:$id")).size)
        beta.backup();assertTrue(beta.restore())
        assertNotEquals(generation,beta.storageGeneration())
        assertTrue(beta.processLive(id,generation))
        assertEquals(1,beta.financialHistory(PostingKey(Environment.BETA,"evidence:$id")).size)
        beta.reset();beta.beginOnboarding()
        assertNotEquals(generation,beta.storageGeneration())
        assertTrue(beta.processLive(id,generation));assertTrue(beta.evidencePage().isEmpty())
        val pending=(1..6).map {n -> requireNotNull(beta.captureLive(message.copy(providerId="pending-$n",receivedAt=at.plusSeconds(n.toLong()))))}.toSet()
        val first=beta.pendingEvidenceIds(limit=2)
        val second=beta.pendingEvidenceIds(first.last(),2)
        val third=beta.pendingEvidenceIds(second.last(),2)
        assertEquals(pending,(first+second+third).toSet())
        assertEquals(6,(first+second+third).size)
        assertTrue(beta.pendingEvidenceIds(third.last(),2).isEmpty())
    }
    fun oldHistoryDoesNotConsumeTheSelectedPeriodReadBudget(context: Context) = isolated(context) { _ -> runBlocking {
        val db=Room.databaseBuilder(context,BetaDatabase::class.java,BetaDatabase.NAME).build()
        try {
            val asset=OwnedAsset(AssetId(Environment.BETA,bank,"account"),AssetKind.ACCOUNT,"1234")
            BetaAssetRegistry(db).register(asset)
            val input=FinancialClassificationInput(PostingKey(Environment.BETA,"current"),PostingRevision(0,0),FinancialType.EXPENSE,Money.of("42",Currency.SAR),TransactionTime(at,ZoneId.of("Asia/Riyadh")),FinancialEndpoint.Owned(asset.id),FinancialEndpoint.External,mapOf(asset.id to asset),setOf("current-proof"),true)
            assertTrue(RoomBetaFinancialLedger(db).post(input) is FinancialPostingResult.Applied)
            val sql=db.openHelper.writableDatabase
            sql.beginTransaction()
            try { repeat(10_100) { n -> sql.execSQL("INSERT INTO beta_financial_transaction(environment,transactionId,correction,interpretation,payload,occurredAtEpochMillis,financialType) VALUES('BETA',?,0,0,'must not decode outside scope',?,'EXPENSE')",arrayOf("old-$n",at.minusSeconds(370L*86400).toEpochMilli())) };sql.setTransactionSuccessful() } finally {sql.endTransaction()}
            val query=DashboardQuery(Environment.BETA,Instant.parse("2026-09-26T21:00:00Z"),Instant.parse("2026-10-26T21:00:00Z"),ZoneId.of("Asia/Riyadh"),Currency.SAR)
            val batch=RoomBetaDashboardSource(db,limits=BetaDashboardReadLimits(currentRows=2),clock=Clock.fixed(at,ZoneOffset.UTC)).load(query)
            assertEquals(listOf("current"),batch.rows.map {it.posting.command.key.transactionId})
            val plan=sql.query("EXPLAIN QUERY PLAN SELECT transactionId FROM beta_financial_transaction WHERE occurredAtEpochMillis>=0 AND occurredAtEpochMillis<1").use {c->buildList {while(c.moveToNext()) add(c.getString(3))}}
            assertTrue(plan.toString(),plan.any {it.contains("index_beta_financial_transaction_occurredAtEpochMillis")})
        } finally {db.close()}
    } }
}
