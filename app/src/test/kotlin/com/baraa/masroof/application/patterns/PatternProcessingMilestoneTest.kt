package com.baraa.masroof.application.patterns

import com.baraa.masroof.application.interpretation.ConfiguredPatternAdapters

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
    @Test fun explicitReparseRefreshesSuccessfulUnpostedEvidenceButBackgroundRetryPreservesIt() = runBlocking {
        fixture { c ->
            val raw = sms("pending-success", "شراء-POS\nبـ10.00 SAR\nمن SHOP\nمدى **4321\nفي 15/10/26 12:30", "SNB-AlAhli")
            val batch = c.historicalSmsBatchProcessor.startBatch()
            batch.ingest(raw)
            val before = requireNotNull(c.parsedEventRepository.findByRawSmsId(raw.id))
            assertEquals(ParseStatus.SUCCESS, before.event.parseStatus)
            assertFalse(c.financialTransactionRepository.isRawSmsLinked(raw.id))
            val w = c.messagePatternsWorkflow
            w.setEnabled(w.load().generation, "BANK_SNB", "snb.purchase", false)
            c.processStoredSmsUseCase.reparseAndStore(raw)
            assertEquals(before, c.parsedEventRepository.findByRawSmsId(raw.id))
            c.processStoredSmsUseCase.reparseAndStore(raw, refreshSuccessfulUnposted = true)
            assertNotEquals(ParseStatus.SUCCESS, c.parsedEventRepository.findByRawSmsId(raw.id)!!.event.parseStatus)
            assertFalse(c.financialTransactionRepository.isRawSmsLinked(raw.id))
            assertEquals(raw, c.rawSmsRepository.getById(raw.id))
        }
    }

    @Test fun confirmedMaskedSourceKeepsLoanRepaymentSeparateFromBillSpendingAndReplay() = runBlocking {
        fixture { c ->
            val bankId = "BANK_SNB"
            val bank = Bank(bankId)
            val w = c.messagePatternsWorkflow
            w.confirmPatternAccount(bankId, "6789")
            c.ownershipConfirmationService.confirmLoanOwned(LoanReference(bank, LoanType.PERSONAL))
            val current = w.load()
            val definition = current.banks.single { it.bankId == bankId }
            val revisions = definition.currentPatterns.filter { it.label in setOf("FINANCING_INSTALLMENT", "BILL_PAYMENT") }.map { pattern ->
                pattern.copy(revision = pattern.revision + 1, factContract = pattern.factContract!!.copy(version = 2,
                    currency = com.baraa.masroof.finance.value.Currency.SAR,
                    accountBindings = listOf(PatternAccountBinding(PatternAccountRole.SOURCE,
                        com.baraa.masroof.finance.value.BankId(bankId), "6789", "source_account", "900*567"))))
            }
            w.saveBank(current.generation, definition.copy(patterns = revisions))
            val loan = "خصم قسط تمويل\nمن 900*567\nالقسط 123.45 SAR\nالمبلغ المتبقي 54321.00 SAR\nلـ تمويل شخصي\nفي 2026-10-13 12:30"
            c.processRawSmsUseCase.ingest(sms("confirmed-loan", loan, "SNB-AlAhli"))
            val repayment = c.financialTransactionRepository.listAll().single()
            assertEquals(FinancialTransactionType.LOAN_REPAYMENT, repayment.type)
            assertEquals("account:BANK_SNB:6789", repayment.sourceContainerId)
            assertEquals("123.45", repayment.amount.amount.toPlainString())
            c.processStoredSmsUseCase.process("confirmed-loan")
            assertEquals(listOf(repayment), c.financialTransactionRepository.listAll())
            val bill = "مدفوعات وزارة الداخلية\nمن 900*567\nمبلغ 37.50 SAR\nالجهة TEST SERVICE\nفي 2026-10-13 12:35"
            c.processRawSmsUseCase.ingest(sms("confirmed-bill", bill, "SNB-AlAhli"))
            val transactions = c.financialTransactionRepository.listAll()
            assertEquals(2, transactions.size)
            assertEquals(1, transactions.count { it.type == FinancialTransactionType.LOAN_REPAYMENT })
            assertEquals(1, transactions.count { it.type == FinancialTransactionType.BILL_PAYMENT })
            assertEquals("account:BANK_SNB:6789", transactions.single { it.type == FinancialTransactionType.BILL_PAYMENT }.sourceContainerId)
        }
    }

    @Test fun anotherOwnersCardOnTheSamePhoneNeverUsesTheDefaultOwnedAccount() = runBlocking {
        fixture { c ->
            val bank = Bank("BANK_SNB")
            val parent = AccountReference(bank, "6789")
            val other = AccountReference(bank, "2345")
            val parentCard = CardReference(bank, "4321")
            val otherCard = CardReference(bank, "7654")
            val w = c.messagePatternsWorkflow
            val account = w.confirmPatternAccount(bank.id, "6789")
            w.configureBankAccount(w.load().generation, bank.id, account.id, "SAR", false)
            c.ownershipConfirmationService.markAccountExternal(other)
            c.ownershipConfirmationService.markCardExternal(otherCard)
            c.ownershipConfirmationService.confirmCardOwned(parentCard)
            c.cardRegistryRepository.markAsDebit(parentCard)
            c.cardRegistryRepository.linkDebitToAccount(parentCard, parent)
            val purchase = "شراء-POS\nبـ37.50 SAR\nمن TEST SHOP\nمدى-ابل*7654\nفي 2026-10-13 12:30"
            c.processRawSmsUseCase.ingest(sms("other-owner", purchase, "SNB-AlAhli"))
            val parsed = requireNotNull(c.parsedEventRepository.findByRawSmsId("other-owner")).event
            assertEquals("7654", parsed.cardRef?.last4)
            assertNull(parsed.sourceAccountRef)
            assertTrue(c.financialTransactionRepository.listAll().isEmpty())
            assertTrue(c.reviewRepository.findByRawSmsId("other-owner")?.status != ReviewStatus.REQUIRED)
            c.processRawSmsUseCase.ingest(sms("unknown-owner", purchase.replace("7654", "9999"), "SNB-AlAhli"))
            assertTrue(c.financialTransactionRepository.listAll().isEmpty())
            assertEquals(ReviewStatus.REQUIRED, c.reviewRepository.findByRawSmsId("unknown-owner")!!.status)
            assertTrue(c.processingRetryRepository.listRetryableRawSmsIds().isEmpty())
            c.processRawSmsUseCase.ingest(sms("external-account-unknown-card",
                purchase.replace("7654", "9999") + "\nمن حساب 2345", "SNB-AlAhli"))
            assertTrue(c.financialTransactionRepository.listAll().isEmpty())
            c.processRawSmsUseCase.ingest(sms("owned-control", purchase.replace("7654", "4321"), "SNB-AlAhli"))
            assertEquals(listOf(FinancialTransactionType.EXPENSE), c.financialTransactionRepository.listAll().map { it.type })
            val transfer = "حوالة صادرة داخلية\nمبلغ 12 SAR\nمن حساب 6789\nالى حساب 2345\nلـ TEST PERSON\nفي 2026-10-13 12:35"
            c.processRawSmsUseCase.ingest(sms("to-other-owner", transfer, "SNB-AlAhli"))
            val receipt = "حوالة داخلية واردة\nمبلغ 12 SAR\nمن حساب 6789\nالى حساب 2345\nمن TEST PERSON\nفي 2026-10-13 12:35"
            c.processRawSmsUseCase.ingest(sms("other-owner-receipt", receipt, "SNB-AlAhli"))
            val transactions = c.financialTransactionRepository.listAll()
            assertEquals(1, transactions.count { it.type == FinancialTransactionType.EXPENSE })
            assertFalse(transactions.any { it.type == FinancialTransactionType.SELF_TRANSFER })
            assertEquals(OwnershipStatus.EXTERNAL, c.accountRegistryRepository.resolve(other))
            assertEquals(OwnershipStatus.EXTERNAL, c.cardRegistryRepository.resolve(otherCard))
            c.settingsRegistryWorkflow.updateAccountDisplayName(other, "Family member")
            c.settingsRegistryWorkflow.configureCard(otherCard, com.baraa.masroof.domain.model.CardConfiguration(
                com.baraa.masroof.domain.model.CardNetwork.MADA, com.baraa.masroof.domain.model.CardType.DEBIT,
                linkedAccount = other,
            ))
            assertTrue(runCatching { c.settingsRegistryWorkflow.configureCard(otherCard,
                com.baraa.masroof.domain.model.CardConfiguration(com.baraa.masroof.domain.model.CardNetwork.MADA,
                    com.baraa.masroof.domain.model.CardType.DEBIT, linkedAccount = parent)) }.isFailure)
            assertTrue(runCatching { c.settingsRegistryWorkflow.configureCard(otherCard,
                com.baraa.masroof.domain.model.CardConfiguration(null, com.baraa.masroof.domain.model.CardType.CREDIT)) }.isFailure)
            val spending = c.settingsRegistryWorkflow.externalAccountSpending(other)
            // The unknown card's explicit account conflicts with the approved default; it stays unconfirmed.
            assertTrue(c.parsedEventRepository.findByRawSmsId("external-account-unknown-card")!!.event.parseStatus != ParseStatus.SUCCESS)
            assertEquals(listOf("other-owner"), spending.rows.map { it.rawSmsId })
            assertEquals("37.50", spending.totals.single().purchasesAndFees.amount.toPlainString())
            c.settingsRegistryWorkflow.updateAccountDisplayName(other, "اسم اختياري")
            assertEquals("اسم اختياري", c.accountRegistryRepository.get(other)?.displayName)
            assertEquals(spending, c.settingsRegistryWorkflow.externalAccountSpending(other))
            assertEquals(transactions, c.financialTransactionRepository.listAll())
            assertEquals(OwnershipStatus.EXTERNAL, c.accountRegistryRepository.resolve(other))
            assertTrue(c.processingRetryRepository.listRetryableRawSmsIds().isEmpty())
        }
    }

    @Test fun deletingAnApprovedPatternPreservesPostedEvidenceAndRejectsAStaleApproval() = runBlocking {
        fixture { c ->
            val w = c.messagePatternsWorkflow
            val account = w.confirmPatternAccount("BANK_STC", "4321")
            w.saveFinancial(w.previewFinancial(w.load().generation, "BANK_STC", "stc-internal-in", PatternOperation.TRANSFER_IN,
                account.id, incoming, "account_reference", emptySet()))
            c.processRawSmsUseCase.ingest(sms("delete-posted", incoming))
            val evidence = c.parsedEventRepository.findByRawSmsId("delete-posted")
            val transactions = c.financialTransactionRepository.listAll()
            assertEquals(1, transactions.size)
            val stale = w.previewFinancial(w.load().generation, "BANK_STC", "stc-internal-in", PatternOperation.TRANSFER_IN,
                account.id, incoming, "account_reference", emptySet())
            w.setEnabled(w.load().generation, "BANK_STC", "stc-internal-in", false)
            w.deletePattern(w.load().generation, "BANK_STC", "stc-internal-in")
            assertTrue(runCatching { w.saveFinancial(stale) }.isFailure)
            c.processStoredSmsUseCase.process("delete-posted")
            assertEquals(evidence, c.parsedEventRepository.findByRawSmsId("delete-posted"))
            assertEquals(transactions, c.financialTransactionRepository.listAll())
            assertTrue(w.load().banks.single { it.bankId == "BANK_STC" }.patterns.none { it.id == "stc-internal-in" })
        }
    }

    @Test fun sarieTransferWithEmptyAccountCanBeApprovedAndAppliedOnlyToTheSelectedAccount() = runBlocking {
        fixture { c ->
            val w = c.messagePatternsWorkflow
            val body = "Inward transfer (SARIE)\n100.00 SAR\nFrom TEST PERSON\nFrom BJAZ\nAccount\n02-09-2026 08:01\nRef. No. *8PJ7"
            c.processRawSmsUseCase.ingest(sms("empty-sarie-account", body))
            assertTrue(c.financialTransactionRepository.listAll().isEmpty())
            val review = requireNotNull(c.reviewRepository.findByRawSmsId("empty-sarie-account"))
            val account = w.confirmPatternAccount("BANK_STC", "1234")
            val missingReference = runCatching {
                w.previewFinancial(w.load().generation, "BANK_STC", "stc-sarie-in", PatternOperation.TRANSFER_IN,
                    account.id, body, "account_reference", setOf("counterparty_bank"))
            }.exceptionOrNull()
            assertEquals(PatternApprovalProblem.REFERENCE_MISMATCH, (missingReference as? PatternApprovalException)?.problem)
            val approved = w.saveFinancial(w.previewFinancial(w.load().generation, "BANK_STC", "stc-sarie-in",
                PatternOperation.TRANSFER_IN, account.id, body, null, setOf("account_reference", "counterparty_bank")))
            assertTrue(c.financialTransactionRepository.listAll().isEmpty())
            assertEquals(1, w.applyPending("BANK_STC", approved.generation, review.id).posted)
            val event = requireNotNull(c.parsedEventRepository.findByRawSmsId("empty-sarie-account")).event
            assertEquals(ParseStatus.SUCCESS, event.parseStatus)
            assertEquals(MessageFamily.TRANSFER_IN, event.messageFamily)
            assertEquals("100.00", event.amount!!.amount.toPlainString())
            assertEquals(AccountReference(Bank("BANK_STC"), "1234"), event.destinationAccountRef)
            assertNull(event.sourceAccountRef)
            assertEquals(1, c.financialTransactionRepository.listAll().size)
        }
    }

    @Test fun manualCardSetupRollsBackInvalidLinksAndNeverCreatesBalancesOrTransactions() = runBlocking {
        fixture { c ->
            val bank = Bank("BANK_STC")
            val invalid = CardConfiguration(CardNetwork.MADA, CardType.DEBIT,
                linkedAccount = AccountReference(bank, "9999"))
            assertTrue(runCatching { c.settingsRegistryWorkflow.addCard(bank.id, "8219", null, invalid) }.isFailure)
            assertNull(c.cardRegistryRepository.get(CardReference(bank, "8219")))
            val account = c.settingsRegistryWorkflow.addAccount(bank.id, "٣٠٠١", "My account")
            c.settingsRegistryWorkflow.addCard(bank.id, "8219", "My card", invalid.copy(linkedAccount = account))
            assertEquals("3001", account.maskedNumber)
            assertEquals(OwnershipStatus.OWNED, c.cardRegistryRepository.resolve(CardReference(bank, "8219")))
            assertTrue(c.financialTransactionRepository.listAll().isEmpty())
            assertTrue(runCatching { c.settingsRegistryWorkflow.addAccount(bank.id, "3001", "Overwrite") }.isFailure)
            assertEquals("My account", c.accountRegistryRepository.get(account)?.displayName)
        }
    }

    @Test fun newBankCardPatternCanBeApprovedWithoutInventingAFundingAccount() = runBlocking {
        fixture { c ->
            val w = c.messagePatternsWorkflow
            val bankId = "CARD_BANK"
            w.addBank(w.catalog().generation, "Card bank", "CardBank", bankId)
            val ref = c.settingsRegistryWorkflow.addCard(bankId, "8219", null,
                CardConfiguration(CardNetwork.VISA, CardType.CREDIT, CardRole.STANDALONE))
            val card = requireNotNull(c.cardRegistryRepository.get(ref))
            val body = "Purchase\nCard:8219\nAmount:10SAR\nMerchant:SHOP\nAt:2026-10-15 12:30"
            val bindings = listOf("card" to "8219", "amount" to "10SAR", "merchant" to "SHOP", "occurred_at" to "2026-10-15 12:30")
                .map { (field, value) -> PatternBinding(field, body.indexOf(value), body.indexOf(value) + value.length) }
            w.saveTeaching(w.teach(w.catalog().generation, bankId, "CardBank", PatternOperation.PURCHASE, body, bindings))
            c.processRawSmsUseCase.ingest(sms("card-pending", body, "CardBank"))
            val patternId = w.catalog().banks.single { it.bankId == bankId }.currentPatterns.single().id
            val preview = w.previewFinancial(w.catalog().generation, bankId, patternId, PatternOperation.PURCHASE,
                null, body, null, emptySet(), cardId = card.id)
            c.settingsRegistryWorkflow.markCardExternal(ref)
            assertTrue(runCatching { w.saveFinancial(preview) }.isFailure)
            c.settingsRegistryWorkflow.confirmCardOwned(ref)
            val approved = w.saveFinancial(preview)
            assertEquals(1, w.applyPending(bankId, approved.generation).posted)
            assertTrue(c.accountRegistryRepository.listAll().none { it.bank.id == bankId })
            assertEquals(ref, c.parsedEventRepository.findByRawSmsId("card-pending")?.event?.cardRef)
            assertEquals(1, c.financialTransactionRepository.listAll().size)
            assertEquals(com.baraa.masroof.domain.ids.FinancialContainerIdFactory.cardId(ref),
                c.financialTransactionRepository.listAll().single().sourceContainerId)
        }
    }

    private suspend fun fixture(source: com.baraa.masroof.sms.datasource.SmsDataSource? = null, block: suspend (AppContainer) -> Unit) {
        val context = TestStorageContext(ApplicationProvider.getApplicationContext<Context>(), "pattern-${java.util.UUID.randomUUID()}")
        val container = AppContainer(context, smsDataSourceOverride = source)
        try { kotlinx.coroutines.withTimeout(20_000) { block(container) } } finally { container.close(); context.getDatabasePath("masroof.db").parentFile?.deleteRecursively() }
    }
    private fun sms(id: String, body: String, sender: String = "STC Bank") = RawSms(id, sender, body,
        Instant.parse("2026-10-15T10:00:00Z"), id, SmsBodyHasher.sha256Hex(body))
    private val incoming = "Internal incoming transfer\nAmount:10SAR\nFrom:TEST PERSON\nAcc:321\nAt:15/10/26 12:30"

    @Test fun applyToCurrentReviewLeavesOtherPendingMessagesAndManualDecisionsUntouched() = runBlocking {
        fixture { c ->
            val w = c.messagePatternsWorkflow
            c.processRawSmsUseCase.ingest(sms("current-ui", incoming))
            c.processRawSmsUseCase.ingest(sms("other-ui", incoming.replace("10SAR", "20SAR")))
            val current = c.reviewRepository.findByRawSmsId("current-ui")!!
            val other = c.reviewRepository.findByRawSmsId("other-ui")!!
            val untouched = c.parsedEventRepository.findByRawSmsId("other-ui")
            val account = w.confirmPatternAccount("BANK_STC", "4321")
            val approved = w.saveFinancial(w.previewFinancial(w.load().generation, "BANK_STC", "stc-internal-in",
                PatternOperation.TRANSFER_IN, account.id, incoming, "account_reference", emptySet()))
            val result = w.applyPending("BANK_STC", approved.generation, current.id)
            assertEquals(1, result.processed)
            assertEquals(1, result.posted)
            assertFalse(result.incomplete)
            assertEquals(untouched, c.parsedEventRepository.findByRawSmsId("other-ui"))
            assertEquals(ReviewStatus.REQUIRED, c.reviewRepository.getById(other.id)!!.status)
            assertEquals(0, w.applyPending("BANK_STC", approved.generation, current.id).processed)
            c.reviewRepository.markResolved(other.id, ReviewResolutionKind.USER_NON_FINANCIAL, Instant.now(), null)
            assertEquals(0, w.applyPending("BANK_STC", approved.generation, other.id).processed)
            assertEquals(1, c.financialTransactionRepository.listAll().size)
            assertEquals(untouched, c.parsedEventRepository.findByRawSmsId("other-ui"))
        }
    }

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

    @Test fun allEightStcLayoutsCanBeExplicitlyApprovedWithoutInventingFundingCardsOrIncome() = runBlocking {
        fixture { c ->
            val w = c.messagePatternsWorkflow
            val account = w.confirmPatternAccount("BANK_STC", "4321")
            val rows = Json.parseToJsonElement(java.io.File("src/test/resources/testdata/patterns/stc-seed-v1.json").readText())
                .jsonObject.getValue("rows").let { it as kotlinx.serialization.json.JsonArray }.map { it.jsonObject }
                .filter { it.getValue("teach").jsonPrimitive.content == "true" }
            assertEquals(8, rows.size)
            for (row in rows) {
                val id = row.getValue("id").jsonPrimitive.content
                val body = row.getValue("body").jsonPrimitive.content
                val fields = row.getValue("fields").jsonObject
                val reference = fields["account_reference"]?.jsonPrimitive?.content
                    ?.takeIf { id != "stc-internal-out-ar" && it.matches(Regex("[0-9]{1,3}")) }?.let { "account_reference" }
                val context = fields.keys.filter { it in setOf("source_account", "destination_account", "account_reference", "funding_reference", "counterparty_bank") && it != reference }.toSet()
                val operation = PatternOperation.valueOf(row.getValue("operation").jsonPrimitive.content)
                w.saveFinancial(w.previewFinancial(w.load().generation, "BANK_STC", id, operation, account.id, body, reference, context,
                    currency = com.baraa.masroof.core.money.Currency.SAR))
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
            c.processStoredSmsUseCase.reparseAndStore(c.rawSmsRepository.getById("purchase-pending")!!, refreshSuccessfulUnposted = true)
            assertEquals(postedEvent, c.parsedEventRepository.findByRawSmsId("purchase-pending"))
            assertEquals(1, c.financialTransactionRepository.listAll().size)
            c.processStoredSmsUseCase.process("manual-ignore")
            c.processStoredSmsUseCase.reparseAndStore(c.rawSmsRepository.getById("manual-ignore")!!, refreshSuccessfulUnposted = true)
            assertEquals(beforeIgnored, c.parsedEventRepository.findByRawSmsId("manual-ignore"))
        }
    }
}
