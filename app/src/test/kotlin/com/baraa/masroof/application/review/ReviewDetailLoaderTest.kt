package com.baraa.masroof.application.review

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.application.transaction.TransactionReconciliationService
import com.baraa.masroof.data.repository.RoomAccountRegistryRepository
import com.baraa.masroof.data.repository.RoomCardRegistryRepository
import com.baraa.masroof.data.repository.RoomFinancialTransactionRepository
import com.baraa.masroof.data.repository.RoomLoanRegistryRepository
import com.baraa.masroof.data.repository.RoomManualReviewResolutionRepository
import com.baraa.masroof.data.repository.RoomParsedEventRepository
import com.baraa.masroof.data.repository.RoomRawSmsRepository
import com.baraa.masroof.data.repository.RoomReviewRepository
import com.baraa.masroof.data.repository.RoomUserCorrectionRepository
import com.baraa.masroof.data.room.MasroofDatabase
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.domain.ownership.OwnershipConfirmationService
import com.baraa.masroof.domain.ownership.OwnershipResolver
import com.baraa.masroof.sms.hash.SmsBodyHasher
import com.baraa.masroof.sms.time.InstantClock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReviewDetailLoaderTest {

    private lateinit var db: MasroofDatabase
    private lateinit var rawRepo: RoomRawSmsRepository
    private lateinit var reviewRepo: RoomReviewRepository
    private lateinit var ingestionReviews: IngestionReviewService
    private lateinit var loader: ReviewDetailLoader
    private val clock = InstantClock { Instant.parse("2026-08-11T12:00:00Z") }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MasroofDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        rawRepo = RoomRawSmsRepository(db.rawSmsDao())
        val parsedRepo = RoomParsedEventRepository(db.parsedEventDao())
        val ftRepo = RoomFinancialTransactionRepository(db.financialTransactionDao(), db.parsedEventDao())
        reviewRepo = RoomReviewRepository(db.reviewItemDao())
        val correctionRepo = RoomUserCorrectionRepository(db.userCorrectionDao())
        val accounts = RoomAccountRegistryRepository.from(db)
        val cards = RoomCardRegistryRepository.from(db)
        val loans = RoomLoanRegistryRepository.from(db)
        val resolver = OwnershipResolver(accounts, cards, loans)
        val effective = EffectiveParsedEventProvider(parsedRepo, correctionRepo)
        val reconciliation = TransactionReconciliationService(
            parsedEventRepository = parsedRepo,
            rawSmsRepository = rawRepo,
            financialTransactionRepository = ftRepo,
            ownershipResolver = resolver,
            effectiveParsedEventProvider = effective,
            reviewRepository = reviewRepo,
        )
        val workflow = ReviewWorkflowService(
            reviewRepository = reviewRepo,
            userCorrectionRepository = correctionRepo,
            financialTransactionRepository = ftRepo,
            rawSmsRepository = rawRepo,
            ownershipResolver = resolver,
            ownershipConfirmationService = OwnershipConfirmationService(accounts, cards, loans),
            effectiveParsedEventProvider = effective,
            parsedEventRepository = parsedRepo,
            reconciliationService = reconciliation,
            reviewQueueUpdater = ReviewQueueUpdater(reviewRepo, ftRepo, clock),
            manualReviewResolutionRepository = RoomManualReviewResolutionRepository(db, ftRepo),
            clock = clock,
        )
        ingestionReviews = IngestionReviewService(reviewRepo, clock)
        loader = ReviewDetailLoader(workflow, rawRepo, effective)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun ingestionReviewWithoutParsedEvent_showsSenderBodyAndDate() = runBlocking {
        val body = "رسالة بنكية بصيغة غير مدعومة"
        val receivedAt = Instant.parse("2026-08-10T09:30:00Z")
        rawRepo.insertIfAbsent(
            RawSms(
                id = "sms-unsupported",
                sender = "AlJazira",
                body = body,
                receivedAt = receivedAt,
                deviceMessageId = "u1",
                bodyHash = SmsBodyHasher.sha256Hex(body),
            ),
        )
        val review = ingestionReviews.requireReview(
            "sms-unsupported",
            IngestionReviewService.REASON_UNSUPPORTED_FORMAT,
        )

        val detail = loader.loadDetail(review.id)!!
        assertEquals("AlJazira", detail.sender)
        assertEquals(body, detail.body)
        assertEquals(receivedAt, detail.receivedAt)
        assertNull(detail.messageFamily)
        assertNull(detail.amount)
        assertNull(detail.bankId) // No bank may be guessed from the sender or quoted message text.
        val selected = reviewRepo.upsertRequired(review.rawSmsId, review.kind,
            review.reasons + "user_selected_bank:STC", clock.now())
        assertEquals("STC", loader.loadDetail(review.id)!!.bankId)
        assertEquals(selected, reviewRepo.getById(review.id))
        assertEquals(body, rawRepo.getById(review.rawSmsId)!!.body)

        val summary = loader.loadSummaries().single()
        assertEquals(review.id, summary.review.id)
        assertEquals(body, summary.body)
        assertEquals(selected.reasons, summary.review.reasons)
    }
    @Test fun teachingUsesSelectedBankAndNeverChangesTheReviewedEvidenceOrDecision() = runBlocking {
        val body = "Payment\nAmount:10SAR"
        val raw = RawSms("teaching-source", "Sender", body, clock.now(), "teaching-provider", SmsBodyHasher.sha256Hex(body))
        rawRepo.insertIfAbsent(raw)
        val parsed = RoomParsedEventRepository(db.parsedEventDao())
        val event = com.baraa.masroof.domain.model.ParsedEvent("event-teaching", raw.id,
            com.baraa.masroof.domain.model.Bank.BANK_ALJAZIRA, com.baraa.masroof.domain.model.MessageFamily.UNKNOWN,
            null, null, null, null, null, null, null, null, null, null,
            com.baraa.masroof.domain.model.Confidence(0.0), com.baraa.masroof.domain.model.ParseStatus.REVIEW_REQUIRED)
        parsed.save(event)
        val review = ingestionReviews.requireReview(raw.id, IngestionReviewService.REASON_UNSUPPORTED_FORMAT)
        assertEquals("BANK_ALJAZIRA", loader.loadDetail(review.id)!!.bankId)
        reviewRepo.upsertRequired(raw.id, review.kind, review.reasons + "user_selected_bank:STC", clock.now())
        val decision = reviewRepo.markResolved(review.id,
            com.baraa.masroof.domain.model.ReviewResolutionKind.USER_NON_FINANCIAL, clock.now(), null)
        val detail = loader.loadDetail(review.id)!!
        assertEquals("STC", detail.bankId)
        val directory = java.nio.file.Files.createTempDirectory("review-teaching-").toFile()
        try {
            val patterns = com.baraa.masroof.application.patterns.MessagePatternsWorkflow(
                com.baraa.masroof.data.patterns.MessagePatternStore(directory.resolve("patterns.json")),
                com.baraa.masroof.data.room.DatabaseAccessGate())
            patterns.addBank(0, "Synthetic bank", detail.sender!!, detail.bankId)
            val preview = patterns.teach(1, detail.bankId!!, detail.sender,
                com.baraa.masroof.application.patterns.PatternOperation.PURCHASE, detail.body!!,
                listOf(com.baraa.masroof.application.patterns.PatternBinding("amount", body.indexOf("10SAR"), body.length)))
            patterns.saveTeaching(preview)
            assertEquals(decision, reviewRepo.getById(review.id))
            assertEquals(raw, rawRepo.getById(raw.id))
            assertEquals(event, parsed.findByRawSmsId(raw.id)!!.event)
            assertEquals(emptyList<com.baraa.masroof.domain.model.FinancialTransaction>(),
                RoomFinancialTransactionRepository(db.financialTransactionDao(), db.parsedEventDao()).listAll())
        } finally { directory.deleteRecursively() }
        Unit
    }

}
