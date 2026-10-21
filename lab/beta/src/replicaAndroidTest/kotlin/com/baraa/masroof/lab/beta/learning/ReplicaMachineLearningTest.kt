package com.baraa.masroof.lab.beta.learning

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.baraa.masroof.lab.beta.replica.*
import com.baraa.masroof.lab.replica.application.AppContainer
import com.baraa.masroof.lab.replica.application.review.ReviewWorkflowResult
import com.baraa.masroof.lab.replica.domain.model.*
import com.baraa.masroof.lab.replica.sms.hash.SmsBodyHasher
import com.baraa.masroof.lab.replica.data.learning.LearningDatabase
import com.baraa.masroof.lab.replica.data.learning.LearningLabelRow
import com.baraa.masroof.lab.ml.family.FamilyInputCleaner
import com.baraa.masroof.lab.replica.application.interpretation.InterpretationOrigin
import androidx.room.Room
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.*
import java.time.format.DateTimeFormatter

/** Actual capture → new engine/ML → production Room/review/reconciliation, without mock postings. */
@RunWith(AndroidJUnit4::class)
class ReplicaMachineLearningTest {
    private val host=InstrumentationRegistry.getInstrumentation().targetContext
    private val bank=Bank.BANK_ALJAZIRA
    private fun at()=LocalDateTime.now().minusMinutes(2).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    private fun evidence(id: String,body: String)=RawSms(id,"AlJazira",body,Instant.now(),id,SmsBodyHasher.sha256Hex(body))
    private suspend fun fresh(): AppContainer {
        LabResetWorkflow.reset(host)
        val c=LabRuntime.container(host)
        c.awaitPostStartupBackgroundWorkForTesting()
        c.onboardingOwnershipWorkflow.confirmAccountOwned(AccountReference(bank,"3001"))
        return c
    }
    private suspend fun await(condition: suspend ()->Boolean) = withTimeout(15_000) {while(!condition()) delay(25)}
    @Test fun aTrainedModelAssistsANewTitleAndTheDashboardGetsThePostedAmount() = runBlocking {
        val c=fresh()
        try {
            val body="حوالة محلية واردة\nبنك المرسل: بنك الرياض\nاودعت الى حساب: 3001\nمن: TEST_EMPLOYER\nمبلغ العملية: SAR 2000.00\nفي: ${at()}"
            c.processRawSmsUseCase.ingest(evidence("ml-income",body))
            val parsed=requireNotNull(c.parsedEventRepository.findByRawSmsId("ml-income"))
            assertEquals(ParseStatus.SUCCESS,parsed.event.parseStatus)
            assertTrue(parsed.event.confidence.reasons.toString(),parsed.event.confidence.reasons.any {it=="family_origin:ML"})
            val rows=c.financialTransactionRepository.listAll()
            assertEquals(1,rows.size);assertEquals("2000.00",rows.single().amount.amount.toPlainString())
            assertEquals(FinancialTransactionType.EXTERNAL_TRANSFER_IN,rows.single().type)
            assertTrue(c.reviewRepository.listRequired().isEmpty())
            assertEquals(InterpretationOrigin.ML,c.interpretationProvenanceWorkflow.load("ml-income")!!.origin)
            val service=requireNotNull(c.labInterpretationService)
            await {service.auditHistory("ml-income").any {it.origin=="ML"}}
            File(host.getExternalFilesDir(null),"ml-inference.txt").writeText("Engine family: ${parsed.event.messageFamily}\nAmount: ${rows.single().amount}\nModel evidence: ${parsed.event.confidence.reasons}\nFinancial type: ${rows.single().type}\nNo manual approval was requested.\n")
        } finally {LabResetWorkflow.reset(host)}
    }
    @Test fun aRealManualLoanDecisionTeachesPendingAndFutureMessagesAfterColdReopen() = runBlocking {
        var c=fresh()
        fun body(amount: Int,remaining: Int)="سداد تمويل خاص\nمن: 3001\nالقسط: SAR $amount\nالمبلغ المتبقي: SAR $remaining\nلـ: تمويل شخصي\nفي: ${at()}"
        try {
            c.processRawSmsUseCase.ingest(evidence("ml-loan-one",body(20,300)))
            c.processRawSmsUseCase.ingest(evidence("ml-loan-pending",body(21,299)))
            assertEquals(2,c.reviewRepository.listRequired().size)
            assertTrue(c.financialTransactionRepository.listAll().isEmpty())
            val review=requireNotNull(c.reviewRepository.findByRawSmsId("ml-loan-one"))
            val decision=c.reviewWorkflowService.resolveAsFinancialType(review.id,FinancialTransactionType.LOAN_REPAYMENT)
            assertTrue(decision.toString(),decision is ReviewWorkflowResult.Success)
            val version=requireNotNull(requireNotNull(c.labInterpretationService).synchronizeFeedback())
            await { c.financialTransactionRepository.listAll().size==2 && c.reviewRepository.listRequired().isEmpty() }
            assertTrue(requireNotNull(c.parsedEventRepository.findByRawSmsId("ml-loan-pending")).event.confidence.reasons.any {it=="family_origin:HUMAN_TEMPLATE"})
            LabRuntime.close()
            c=LabRuntime.container(host)
            c.awaitPostStartupBackgroundWorkForTesting()
            assertEquals(version,requireNotNull(c.labInterpretationService).modelVersion)
            c.processRawSmsUseCase.ingest(evidence("ml-loan-next",body(22,298)))
            await {c.financialTransactionRepository.listAll().size==3}
            assertTrue(c.reviewRepository.listRequired().isEmpty())
            val parsed=requireNotNull(c.parsedEventRepository.findByRawSmsId("ml-loan-next"))
            assertEquals(ParseStatus.SUCCESS,parsed.event.parseStatus)
            assertEquals("22.00",parsed.event.amount!!.amount.toPlainString())
            assertTrue(parsed.event.confidence.reasons.toString(),parsed.event.confidence.reasons.any {it=="family_origin:HUMAN_TEMPLATE"})
            assertEquals(InterpretationOrigin.HUMAN_TEMPLATE,c.interpretationProvenanceWorkflow.load("ml-loan-next")!!.origin)
            assertEquals(InterpretationOrigin.MANUAL,c.interpretationProvenanceWorkflow.load("ml-loan-one").origin)
            val dashboard=c.dashboardService.loadCurrentOverview()
            assertEquals("298.00",dashboard.loansOverview!!.loans.single().remainingBalance!!.amount.toPlainString())
            File(host.getExternalFilesDir(null),"ml-feedback.txt").writeText("First two messages: UNKNOWN and review required.\nOne real production manual loan decision.\nPending message automatically reparsed and posted.\nModel: $version\nCold reopen preserved learning.\nNext amount extracted fresh: ${parsed.event.amount}\nLatest remaining balance: ${dashboard.loansOverview!!.loans.single().remainingBalance}\nNo repeated review.\n")
        } finally {LabResetWorkflow.reset(host)}
    }
    @Test fun corruptLearningCacheDoesNotBlockKnownRulesOrChangeFinancialHistory() = runBlocking {
        var c=fresh()
        try {
            c.onboardingOwnershipWorkflow.confirmCardOwned(CardReference(bank,"7271"))
            fun body(amount: Int)="شراء عبر الانترنت\nبطاقة ائتمانية: 7271\nمبلغ: SAR $amount\nلدى: TEST_SHOP\nفي: ${at()}"
            c.processRawSmsUseCase.ingest(evidence("ml-known-one",body(12)))
            requireNotNull(c.labInterpretationService).synchronizeFeedback()
            val before=c.financialTransactionRepository.listAll()
            LabRuntime.close()
            File(LabStorageContext(host).filesDir,"learning/family-model.json").writeText("{corrupt")
            c=LabRuntime.container(host)
            c.awaitPostStartupBackgroundWorkForTesting()
            assertEquals(before,c.financialTransactionRepository.listAll())
            c.processRawSmsUseCase.ingest(evidence("ml-known-two",body(13)))
            assertEquals(2,c.financialTransactionRepository.listAll().size)
            val parsed=requireNotNull(c.parsedEventRepository.findByRawSmsId("ml-known-two"))
            assertEquals(ParseStatus.SUCCESS,parsed.event.parseStatus)
            assertFalse(parsed.event.confidence.reasons.any {it.startsWith("model:")})
        } finally {LabResetWorkflow.reset(host)}
    }

    @Test fun oldFeatureCacheIsRebuiltFromHumanDecisionsWithoutChangingPostedHistory() = runBlocking {
        var c=fresh()
        fun body(amount: Int)="سداد تمويل خاص\nمن: 3001\nالقسط: SAR $amount\nالمبلغ المتبقي: SAR 300\nلـ: تمويل شخصي\nفي: ${at()}"
        try {
            c.processRawSmsUseCase.ingest(evidence("old-model-human",body(20)))
            val review=requireNotNull(c.reviewRepository.findByRawSmsId("old-model-human"))
            assertTrue(c.reviewWorkflowService.resolveAsFinancialType(review.id,FinancialTransactionType.LOAN_REPAYMENT) is ReviewWorkflowResult.Success)
            requireNotNull(c.labInterpretationService).synchronizeFeedback()
            val before=c.financialTransactionRepository.listAll()
            LabRuntime.close()
            val scoped=LabStorageContext(host)
            val legacy=Room.databaseBuilder(scoped,LearningDatabase::class.java,"learning.db").build()
            try {
                legacy.learning().replaceLabels(listOf(LearningLabelRow("legacy","old-model-human","BANK_ALJAZIRA",
                    "FINANCING_INSTALLMENT","old-template","{\"w:private_person\":1}","bank-family-title-v1",1L,"USER_EVENT_KIND","hash")))
            } finally {legacy.close()}
            File(scoped.filesDir,"learning/family-model.json").writeText("{\"schema\":1,\"featuresVersion\":\"bank-family-title-v1\"}")
            c=LabRuntime.container(host)
            c.awaitPostStartupBackgroundWorkForTesting()
            requireNotNull(c.labInterpretationService).synchronizeFeedback()
            assertEquals(before,c.financialTransactionRepository.listAll())
            val rebuilt=Room.databaseBuilder(scoped,LearningDatabase::class.java,"learning.db").build()
            try {
                val rows=rebuilt.learning().labels()
                assertEquals(1,rows.size)
                assertEquals(FamilyInputCleaner.FEATURES_VERSION,rows.single().featuresVersion)
                assertFalse(rows.single().featuresJson.contains("private_person"))
            } finally {rebuilt.close()}
            assertTrue(File(scoped.filesDir,"learning/family-model.json").readText().contains(FamilyInputCleaner.VERSION))
            assertEquals(ReviewStatus.RESOLVED,c.reviewRepository.findByRawSmsId("old-model-human")!!.status)
            c.processRawSmsUseCase.ingest(evidence("old-model-next",body(21)))
            assertEquals(2,c.financialTransactionRepository.listAll().size)
            assertTrue(c.reviewRepository.listRequired().isEmpty())
            File(host.getExternalFilesDir(null),"ml-cleaning-upgrade.txt").writeText(
                "Old feature cache rejected.\nHuman decision rebuilt using ${FamilyInputCleaner.VERSION}.\nPosted history unchanged across reopen.\nFuture amount 21 SAR extracted fresh; no repeated review.\n")
        } finally {LabResetWorkflow.reset(host)}
    }

    @Test fun aRuleUpgradeRefreshesUnresolvedEvidenceWithoutRewritingPostedHistory() = runBlocking {
        var c=fresh()
        try {
            c.onboardingOwnershipWorkflow.confirmCardOwned(CardReference(bank,"7271"))
            c.processRawSmsUseCase.ingest(evidence("rule-posted","شراء عبر الانترنت\nبطاقة ائتمانية: 7271\nمبلغ: SAR 12\nلدى: TEST\nفي: ${at()}"))
            c.processRawSmsUseCase.ingest(evidence("rule-old-loyalty","رصيد نقاطك في برنامج مكافآتي هو 99 نقطة."))
            val old=requireNotNull(c.parsedEventRepository.findByRawSmsId("rule-old-loyalty"))
            c.parsedEventRepository.save(old.event.copy(messageFamily=MessageFamily.UNKNOWN,parseStatus=ParseStatus.REVIEW_REQUIRED,
                confidence=Confidence(0.0,listOf("engine:previous","rules:previous"))),old.details)
            c.reviewRepository.upsertRequired("rule-old-loyalty",ReviewKind.NEEDS_REVIEW,listOf("unknown_or_unsupported_format"),Instant.now())
            val before=c.financialTransactionRepository.listAll()
            val posted=requireNotNull(c.parsedEventRepository.findByRawSmsId("rule-posted"))
            LabRuntime.close()
            LabStorageContext(host).getSharedPreferences("learning_runtime",0).edit().putString("scheduled_interpretation","previous").commit()
            c=LabRuntime.container(host)
            c.awaitPostStartupBackgroundWorkForTesting()
            requireNotNull(c.labInterpretationService).synchronizeFeedback()
            await { c.reviewRepository.listRequired().isEmpty() }
            assertEquals(MessageFamily.NON_FINANCIAL,c.parsedEventRepository.findByRawSmsId("rule-old-loyalty")!!.event.messageFamily)
            assertEquals(before,c.financialTransactionRepository.listAll())
            assertEquals(posted,c.parsedEventRepository.findByRawSmsId("rule-posted"))
            assertEquals(InterpretationOrigin.RULES,c.interpretationProvenanceWorkflow.load("rule-old-loyalty").origin)
        } finally { LabResetWorkflow.reset(host) }
    }
}
