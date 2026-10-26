package com.baraa.masroof.application.patterns

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.application.AppContainer
import com.baraa.masroof.application.ingestion.SmsIngestionResult
import com.baraa.masroof.domain.model.*
import com.baraa.masroof.engine.bank.patterns.*
import com.baraa.masroof.sms.hash.SmsBodyHasher
import com.baraa.masroof.testsupport.TestStorageContext
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PatternProcessingMilestoneTest {
    private suspend fun fixture(source: com.baraa.masroof.sms.datasource.SmsDataSource? = null, block: suspend (AppContainer) -> Unit) {
        val context = TestStorageContext(ApplicationProvider.getApplicationContext<Context>(), "pattern-${java.util.UUID.randomUUID()}")
        val container = AppContainer(context, smsDataSourceOverride = source)
        try { kotlinx.coroutines.withTimeout(20_000) { block(container) } } finally { container.close(); context.getDatabasePath("masroof.db").parentFile?.deleteRecursively() }
    }
    private fun sms(id: String, body: String, sender: String = "STC Bank") = RawSms(id, sender, body,
        Instant.parse("2026-10-15T10:00:00Z"), id, SmsBodyHasher.sha256Hex(body))
    private val incoming = "Internal incoming transfer\nAmount:10SAR\nFrom:TEST PERSON\nAcc:321\nAt:15/10/26 12:30"

    @Test fun pendingBatchPersistsRetryIntentWithFactsBeforeItsDerivedPass() = runBlocking {
        fixture { c ->
            val w = c.messagePatternsWorkflow
            val raw = sms("interrupted-batch", incoming)
            c.processRawSmsUseCase.ingest(raw)
            val account = w.confirmPatternAccount("BANK_STC", "4321")
            w.saveFinancial(w.previewFinancial(w.load().generation, "BANK_STC", "stc-internal-in", PatternOperation.TRANSFER_IN,
                account.id, incoming, "account_reference", emptySet()))
            val batch = c.historicalSmsBatchProcessor.startBatch(Bank("BANK_STC"))
            assertTrue(batch.reprocess(raw) is SmsIngestionResult.Parsed)
            val saved = c.parsedEventRepository.findByRawSmsId(raw.id)
            assertEquals(listOf(raw.id), c.processingRetryRepository.listRetryableRawSmsIds(ProcessingRetryMode.HISTORICAL_BATCH))
            assertTrue(c.financialTransactionRepository.listAll().isEmpty())
            // A new recovery pass can finish even if the original caller never reaches finish().
            c.historicalDerivedRecovery.recoverPending()
            assertEquals(saved, c.parsedEventRepository.findByRawSmsId(raw.id))
            assertEquals(1, c.financialTransactionRepository.listAll().size)
            assertTrue(c.processingRetryRepository.listRetryableRawSmsIds().isEmpty())
        }
    }

    @Test fun allSixStcLayoutsCanBeExplicitlyApprovedWithoutInventingFundingCardsOrIncome() = runBlocking {
        fixture { c ->
            val w = c.messagePatternsWorkflow
            val account = w.confirmPatternAccount("BANK_STC", "4321")
            val rows = Json.parseToJsonElement(java.io.File("src/test/resources/testdata/patterns/stc-seed-v1.json").readText())
                .jsonObject.getValue("rows").let { it as kotlinx.serialization.json.JsonArray }.map { it.jsonObject }
                .filter { it.getValue("teach").jsonPrimitive.content == "true" }
            assertEquals(6, rows.size)
            for (row in rows) {
                val id = row.getValue("id").jsonPrimitive.content
                val body = row.getValue("body").jsonPrimitive.content
                val fields = row.getValue("fields").jsonObject
                val reference = fields["account_reference"]?.jsonPrimitive?.content?.takeIf { it.matches(Regex("[0-9]{1,3}")) }?.let { "account_reference" }
                val context = fields.keys.filter { it in setOf("source_account", "destination_account", "account_reference", "funding_reference", "counterparty_bank") && it != reference }.toSet()
                val operation = PatternOperation.valueOf(row.getValue("operation").jsonPrimitive.content)
                w.saveFinancial(w.previewFinancial(w.load().generation, "BANK_STC", id, operation, account.id, body, reference, context))
                assertTrue(c.processRawSmsUseCase.ingest(sms("approved-$id", body)) is SmsIngestionResult.Parsed)
                val event = c.parsedEventRepository.findByRawSmsId("approved-$id")!!.event
                assertEquals(ParseStatus.SUCCESS, event.parseStatus)
                assertEquals(MessageFamily.valueOf(operation.name), event.messageFamily)
                assertNull(event.bankNetworkType)
            }
            assertTrue(c.cardRegistryRepository.listAll().isEmpty())
            assertFalse(c.financialTransactionRepository.listAll().any { it.type in setOf(FinancialTransactionType.INCOME, FinancialTransactionType.LOAN_REPAYMENT) })
        }
    }

    @Test fun realRedactedOtpWithTransferAndAmountIsNeverFinancial() = runBlocking {
        fixture { c ->
            val sample = Json.parseToJsonElement(java.io.File("src/test/resources/testdata/patterns/stc-otp-redacted.json").readText()).jsonObject
            val text = sample.getValue("body").jsonPrimitive.content
            val result = c.processRawSmsUseCase.ingest(sms("real-otp", text))
            assertTrue(result is SmsIngestionResult.NonFinancial)
            val event = c.parsedEventRepository.findByRawSmsId("real-otp")!!.event
            assertEquals(MessageFamily.OTP, event.messageFamily)
            assertEquals(ParseStatus.NON_FINANCIAL, event.parseStatus)
            assertNull(event.amount)
            assertTrue(c.financialTransactionRepository.listAll().isEmpty())
        }
    }

    @Test fun captureSnapshotsThatExpireBeforeProcessingAreRetriedBeforeSavingFacts() = runBlocking {
        fixture { c ->
            val store = com.baraa.masroof.data.patterns.MessagePatternStore(c.applicationContext)
            val registry = com.baraa.masroof.bank.BankSmsRegistry(emptyList(), ConfiguredPatternAdapters(store)::snapshot)
            val raw = sms("stale-capture", incoming)
            val route = registry.route(raw.sender, raw.body)
            c.rawSmsRepository.insertIfAbsent(raw)
            val w = c.messagePatternsWorkflow
            val account = w.confirmPatternAccount("BANK_STC", "4321")
            w.saveFinancial(w.previewFinancial(w.load().generation, "BANK_STC", "stc-internal-in", PatternOperation.TRANSFER_IN,
                account.id, incoming, "account_reference", emptySet()))
            assertTrue(c.processStoredSmsUseCase.process(raw, route) is SmsIngestionResult.Failed)
            assertNull(c.parsedEventRepository.findByRawSmsId(raw.id))
            assertTrue(c.processStoredSmsUseCase.process(raw.id) is SmsIngestionResult.Parsed)
            assertEquals(1, c.financialTransactionRepository.listAll().size)
        }
    }

    @Test fun failedFinancialBatchKeepsReviewsAndAtomicRetrySetUntilRecoverySucceeds() = runBlocking {
        fixture { c ->
            val w = c.messagePatternsWorkflow
            val account = w.confirmPatternAccount("BANK_STC", "4321")
            c.processRawSmsUseCase.ingest(sms("failed-stc", incoming))
            val approved = w.saveFinancial(w.previewFinancial(w.load().generation, "BANK_STC", "stc-internal-in",
                PatternOperation.TRANSFER_IN, account.id, incoming, "account_reference", emptySet()))
            c.database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_pattern_post BEFORE INSERT ON financial_transaction BEGIN SELECT RAISE(ABORT,'injected'); END")
            val applied = w.applyPending("BANK_STC", approved.generation)
            assertTrue(applied.incomplete)
            assertNull(applied.failed) // A thrown write has no returned reconciliation count.
            assertTrue(c.appLogService.readAll().any { it.message.contains("retry_state=marked") })
            assertEquals(0, applied.posted)
            assertTrue(c.financialTransactionRepository.listAll().isEmpty())
            assertEquals(ReviewStatus.REQUIRED, c.reviewRepository.findByRawSmsId("failed-stc")!!.status)
            assertEquals(listOf("failed-stc"), c.processingRetryRepository.listRetryableRawSmsIds(ProcessingRetryMode.HISTORICAL_BATCH))
            c.database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_pattern_post")
            c.historicalDerivedRecovery.recoverPending()
            assertEquals(1, c.financialTransactionRepository.listAll().size)
            assertTrue(c.processingRetryRepository.listRetryableRawSmsIds().isEmpty())
            assertEquals(ReviewStatus.RESOLVED, c.reviewRepository.findByRawSmsId("failed-stc")!!.status)
        }
    }

    @Test fun ownershipChangesPreventPostingAndLaterConfirmationResumesWithoutReparse() = runBlocking {
        fixture { c ->
            val w = c.messagePatternsWorkflow
            val body = "Purchase\nAmount:10SAR\nAt:2026-10-15 12:30"
            val p = SavedMessagePattern.fromChoices("owned-purchase", "OWNERSHIP_BANK", "Ownership Bank", "PURCHASE", body,
                listOf("amount" to "10SAR", "occurred_at" to "2026-10-15 12:30").map { (field, value) ->
                    FieldChoice(field, body.indexOf(value), body.indexOf(value) + value.length)
                })
            w.saveBank(w.load().generation, BankMessagePatterns("OWNERSHIP_BANK", listOf("Ownership Bank"), patterns = listOf(p)))
            c.processRawSmsUseCase.ingest(sms("ownership-race", body, "Ownership Bank"))
            val account = w.confirmPatternAccount("OWNERSHIP_BANK", "1234")
            val approved = w.saveFinancial(w.previewFinancial(w.load().generation, "OWNERSHIP_BANK", p.id, PatternOperation.PURCHASE,
                account.id, body, null, emptySet()))
            val sql = c.database.openHelper.writableDatabase
            for (action in listOf("INSERT", "UPDATE")) sql.execSQL("CREATE TRIGGER revoke_on_$action AFTER $action ON parsed_event WHEN NEW.rawSmsId='ownership-race' BEGIN UPDATE account_registry SET ownershipStatus='EXTERNAL' WHERE bankId='OWNERSHIP_BANK'; END")
            val result = w.applyPending("OWNERSHIP_BANK", approved.generation)
            assertEquals(0, result.posted)
            assertTrue(c.financialTransactionRepository.listAll().isEmpty())
            assertEquals(OwnershipStatus.EXTERNAL, c.accountRegistryRepository.get(AccountReference(Bank("OWNERSHIP_BANK"), "1234"))!!.ownership)
            val saved = c.parsedEventRepository.findByRawSmsId("ownership-race")
            for (action in listOf("INSERT", "UPDATE")) sql.execSQL("DROP TRIGGER revoke_on_$action")
            w.confirmPatternAccount("OWNERSHIP_BANK", "1234")
            c.processStoredSmsUseCase.process("ownership-race")
            assertEquals(saved, c.parsedEventRepository.findByRawSmsId("ownership-race"))
            assertEquals(1, c.financialTransactionRepository.listAll().size)
            assertTrue(c.processingRetryRepository.listRetryableRawSmsIds().isEmpty())
        }
    }

    @Test fun financialCorrectionRetryResumesSavedEvidenceWithoutReparsingOrLosingTheDecision() = runBlocking {
        fixture { c ->
            val w = c.messagePatternsWorkflow
            c.processRawSmsUseCase.ingest(sms("manual-retry", incoming))
            val original = c.parsedEventRepository.findByRawSmsId("manual-retry")
            val account = w.confirmPatternAccount("BANK_STC", "4321")
            w.saveFinancial(w.previewFinancial(w.load().generation, "BANK_STC", "stc-internal-in", PatternOperation.TRANSFER_IN,
                account.id, incoming, "account_reference", emptySet()))
            c.userCorrectionRepository.save(UserCorrection("manual-family", "manual-retry", MessageFamily.PURCHASE,
                com.baraa.masroof.core.money.Money.of("15", com.baraa.masroof.core.money.Currency.SAR), "MANUAL", null, Instant.now()))
            val review = c.reviewRepository.findByRawSmsId("manual-retry")!!
            c.reviewRepository.markResolved(review.id, ReviewResolutionKind.USER_CORRECTION, Instant.now(), null)
            c.processingRecovery.markExhausted("manual-retry")
            c.processStoredSmsUseCase.process("manual-retry")
            assertEquals(original, c.parsedEventRepository.findByRawSmsId("manual-retry"))
            assertEquals("15.00", c.financialTransactionRepository.listAll().single().amount.amount.toPlainString())
            assertEquals(ReviewResolutionKind.USER_CORRECTION, c.reviewRepository.findByRawSmsId("manual-retry")!!.resolutionKind)
            assertTrue(c.processingRetryRepository.listRetryableRawSmsIds().isEmpty())
        }
    }

    @Test fun scopedImportUsesDynamicRoutingAndPreservesCollisionsWithoutClaimingOtherBanks() = runBlocking {
        var queries = 0
        val source = object : com.baraa.masroof.sms.datasource.SmsDataSource {
            override fun queryInbox(receivedAfter: Instant?): Sequence<com.baraa.masroof.sms.datasource.InboxRow> {
                queries++
                return listOf("STC Bank", "AlJazira", "PERSON").mapIndexed { index, sender ->
                    com.baraa.masroof.sms.datasource.InboxRow.Valid(com.baraa.masroof.sms.model.ProviderSmsRecord(
                        "import-$index", sender, incoming, Instant.parse("2026-10-15T10:00:00Z")))
                }.asSequence()
            }
        }
        fixture(source) { c ->
            val w = c.messagePatternsWorkflow
            assertTrue(runCatching { w.importBank("NOT_REGISTERED") }.isFailure)
            assertEquals(0, queries)
            w.saveBank(w.load().generation, BankMessagePatterns("COLLISION", listOf("STC Bank")))
            val result = w.importBank("BANK_STC")
            assertEquals(1, result.inserted)
            assertEquals(2, result.notRelevant)
            assertTrue(c.parsedEventRepository.listAll().isEmpty())
            assertEquals(1, c.reviewRepository.listRequired().size)
            assertTrue(c.financialTransactionRepository.listAll().isEmpty())
        }
    }

    @Test fun staleApprovalAndChangedOwnershipCannotPublishTheReviewedContract() = runBlocking {
        fixture { c ->
            val w = c.messagePatternsWorkflow
            val account = w.confirmPatternAccount("BANK_STC", "4321")
            val preview = w.previewFinancial(w.load().generation, "BANK_STC", "stc-internal-in", PatternOperation.TRANSFER_IN,
                account.id, incoming, "account_reference", emptySet())
            c.accountRegistryRepository.setOwnership(AccountReference(Bank("BANK_STC"), "4321"), OwnershipStatus.EXTERNAL)
            assertTrue(runCatching { w.saveFinancial(preview) }.isFailure)
            assertNull(w.load().banks.single { it.bankId == "BANK_STC" }.currentPatterns.single { it.id == "stc-internal-in" }.factContract)
            w.confirmPatternAccount("BANK_STC", "4321")
            w.setEnabled(w.load().generation, "BANK_STC", "stc-internal-in", false)
            assertTrue(runCatching { w.saveFinancial(preview) }.isFailure)
        }
    }

    @Test fun seededStcRoutesBeforeOpeningSettingsAndOnlyExplicitApprovalUnlocksMappedFacts() = runBlocking {
        fixture { c ->
            val workflow = c.messagePatternsWorkflow
            assertTrue(c.processRawSmsUseCase.ingest(sms("pending", incoming)) is SmsIngestionResult.ReviewRequired)
            assertTrue(c.financialTransactionRepository.listAll().isEmpty())
            val before = c.parsedEventRepository.findByRawSmsId("pending")!!
            val account = workflow.confirmPatternAccount("BANK_STC", "4321")
            val snapshot = workflow.load()
            val preview = workflow.previewFinancial(snapshot.generation, "BANK_STC", "stc-internal-in", PatternOperation.TRANSFER_IN,
                account.id, incoming, "account_reference", emptySet())
            assertEquals(before, c.parsedEventRepository.findByRawSmsId("pending"))
            assertTrue(c.financialTransactionRepository.listAll().isEmpty())
            val saved = workflow.saveFinancial(preview)
            assertEquals(2, saved.banks.single { it.bankId == "BANK_STC" }.currentPatterns.single { it.id == "stc-internal-in" }.revision)
            val applied = workflow.applyPending("BANK_STC", saved.generation)
            assertEquals(1, applied.processed)
            assertEquals(ParseStatus.SUCCESS, c.parsedEventRepository.findByRawSmsId("pending")!!.event.parseStatus)
            assertEquals(AccountReference(Bank("BANK_STC"), "4321"), c.parsedEventRepository.findByRawSmsId("pending")!!.event.destinationAccountRef)
            // Existing core policy permits an external transfer with an explicitly confirmed owned leg.
            val transfer = c.financialTransactionRepository.listAll().single()
            assertEquals(FinancialTransactionType.EXTERNAL_TRANSFER_IN, transfer.type)
            assertNull(transfer.sourceContainerId)
            assertEquals("account:BANK_STC:4321", transfer.destinationContainerId)
            assertEquals(1, applied.posted)
            assertEquals(ReviewStatus.RESOLVED, c.reviewRepository.findByRawSmsId("pending")!!.status)
            assertTrue(c.processRawSmsUseCase.ingest(sms("different", incoming.replace("321", "322"))) is SmsIngestionResult.ReviewRequired)
            assertEquals(1, c.financialTransactionRepository.listAll().size)
        }
    }

    @Test fun approvedGenericPatternPostsOnceAndNeverReparsesPostedOrManuallyDecidedEvidence() = runBlocking {
        fixture { c ->
            val w = c.messagePatternsWorkflow
            val body = "Purchase\nAmount:10SAR\nMerchant:SHOP\nAt:2026-10-15 12:30"
            val p = SavedMessagePattern.fromChoices("purchase", "NEW_BANK", "New Bank", "PURCHASE", body,
                listOf("amount" to "10SAR", "merchant" to "SHOP", "occurred_at" to "2026-10-15 12:30").map { (field,value) ->
                    FieldChoice(field, body.indexOf(value), body.indexOf(value) + value.length)
                })
            w.saveBank(w.load().generation, BankMessagePatterns("NEW_BANK", listOf("New Bank"), patterns = listOf(p)))
            c.processRawSmsUseCase.ingest(sms("purchase-pending", body, "New Bank"))
            c.processRawSmsUseCase.ingest(sms("manual-ignore", body, "New Bank"))
            val ignored = c.reviewRepository.findByRawSmsId("manual-ignore")!!
            c.reviewRepository.markResolved(ignored.id, ReviewResolutionKind.USER_NON_FINANCIAL, Instant.now(), null)
            val beforeIgnored = c.parsedEventRepository.findByRawSmsId("manual-ignore")
            val account = w.confirmPatternAccount("NEW_BANK", "1234")
            val preview = w.previewFinancial(w.load().generation, "NEW_BANK", p.id, PatternOperation.PURCHASE,
                account.id, body, null, emptySet())
            val approved = w.saveFinancial(preview)
            assertEquals(1, w.applyPending("NEW_BANK", approved.generation).processed)
            assertEquals(1, c.financialTransactionRepository.listAll().size)
            assertEquals(beforeIgnored, c.parsedEventRepository.findByRawSmsId("manual-ignore"))
            val postedEvent = c.parsedEventRepository.findByRawSmsId("purchase-pending")
            w.setEnabled(w.load().generation, "NEW_BANK", p.id, false)
            c.processStoredSmsUseCase.process("purchase-pending")
            assertEquals(postedEvent, c.parsedEventRepository.findByRawSmsId("purchase-pending"))
            assertEquals(1, c.financialTransactionRepository.listAll().size)
            c.processStoredSmsUseCase.process("manual-ignore")
            assertEquals(beforeIgnored, c.parsedEventRepository.findByRawSmsId("manual-ignore"))
        }
    }
}
