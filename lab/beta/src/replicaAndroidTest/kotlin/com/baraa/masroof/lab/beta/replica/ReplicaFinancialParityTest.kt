package com.baraa.masroof.lab.beta.replica

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.baraa.masroof.lab.replica.application.AppContainer
import com.baraa.masroof.lab.replica.application.dashboard.DashboardOverview
import com.baraa.masroof.lab.replica.bank.BankSmsAdapter
import com.baraa.masroof.lab.replica.domain.model.*
import com.baraa.masroof.lab.replica.domain.period.FinancialPeriod
import com.baraa.masroof.lab.replica.parsing.model.*
import com.baraa.masroof.lab.replica.sms.datasource.*
import com.baraa.masroof.lab.replica.sms.model.ProviderSmsRecord
import com.baraa.masroof.lab.reference.bank.aljazira.AlJaziraSmsAdapter
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.*

/** Both engines traverse the identical Room/reconciliation/dashboard implementation. */
@RunWith(AndroidJUnit4::class)
class ReplicaFinancialParityTest {
    private val host = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun importsOwnershipLedgerDashboardReplayAndReopenMatchProduction() = runBlocking { exercise(ReferenceHistory.names) }
    @Test fun fiveReportedMessagesPostWithoutRepeatedReviews() = runBlocking { exercise(ReferenceHistory.reported) }
    @Test fun supportedFormatsPostInTheImportPassWithoutLearningOrAManualRefresh() = runBlocking {
        LabResetWorkflow.reset(host)
        val context = LabStorageContext(host,"lab_reference_")
        context.databaseList().forEach { context.deleteDatabase(it) }
        val names = listOf("personal","vehicle","auto","house","mortgage","generic").map { "supported_loan_$it" } +
            listOf("supported_withdrawal_en","supported_fee_title") +
            listOf("same_bank_other_out","same_bank_other_in","same_bank_own","other_bank_same_suffix").map { "supported_transfer_$it" }
        val source = ReferenceHistory.source(host,names)
        val reference = AppContainer(context,bankAdapterOverride = AlJaziraSmsAdapter(),smsDataSourceOverride = source)
        var actual = AppContainer(LabStorageContext(host),smsDataSourceOverride = source)
        try {
            for (c in listOf(reference,actual)) {
                // Existing confirmed ownership, including the same suffix in a different bank.
                for (suffix in listOf("3001","3002","5001")) c.onboardingOwnershipWorkflow.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA,suffix))
                c.onboardingOwnershipWorkflow.markAccountExternal(AccountReference(Bank.BANK_ALJAZIRA,"5002"))
                val result = c.historicalSmsScanner.scan(Instant.EPOCH)
                assertEquals(result.toString(),0,result.failed)
                assertTrue("The import pass itself must post every supported message", c.financialTransactionRepository.listAll().size == names.size)
                assertTrue("No additional classification or model refresh",c.reviewRepository.listRequired().isEmpty())
                assertTrue(c.parsedEventRepository.listAll().all { it.event.confidence.reasons.none { reason -> reason.startsWith("family_origin:") } })
            }
            val rows = actual.financialTransactionRepository.listAll().sortedBy { it.id }
            val referenceRows = reference.financialTransactionRepository.listAll().sortedBy { it.id }
            // Reviewed engine improvement: explicit external bank identity, never local suffix ownership.
            val external = rows.single { it.amount.amount.toPlainString() == "64.00" }
            assertEquals("account:BANK_D360:5001", external.destinationContainerId)
            assertNull(referenceRows.single { it.id == external.id }.destinationContainerId)
            assertEquals(referenceRows, rows.map { if (it.id == external.id) it.copy(destinationContainerId = null) else it })
            val expectedTypes = mapOf("61.00" to FinancialTransactionType.EXTERNAL_TRANSFER_OUT,"62.00" to FinancialTransactionType.EXTERNAL_TRANSFER_IN,
                "63.00" to FinancialTransactionType.SELF_TRANSFER,"64.00" to FinancialTransactionType.EXTERNAL_TRANSFER_OUT)
            expectedTypes.forEach { (amount,type) -> assertEquals(type,rows.single { it.amount.amount.toPlainString()==amount }.type) }
            assertEquals(6,rows.count { it.type == FinancialTransactionType.LOAN_REPAYMENT })
            val overview = reference.dashboardService.loadOverview(ReferenceHistory.period())
            assertOverview(overview,actual.dashboardService.loadOverview(ReferenceHistory.period()))
            val replay = actual.historicalSmsScanner.scan(Instant.EPOCH)
            assertEquals(names.size,replay.duplicates)
            assertEquals(rows,actual.financialTransactionRepository.listAll().sortedBy { it.id })
            actual.close()
            actual = AppContainer(LabStorageContext(host),smsDataSourceOverride = source)
            assertEquals(rows,actual.financialTransactionRepository.listAll().sortedBy { it.id })
            assertOverview(overview,actual.dashboardService.loadOverview(ReferenceHistory.period()))
        } finally { reference.close(); actual.close(); LabResetWorkflow.reset(host) }
    }
    private suspend fun exercise(names: List<String>) {
        LabResetWorkflow.reset(host)
        val referenceContext = LabStorageContext(host,"lab_reference_")
        referenceContext.databaseList().forEach { referenceContext.deleteDatabase(it) }
        val source = ReferenceHistory.source(host,names)
        val expected = AppContainer(referenceContext,bankAdapterOverride = AlJaziraSmsAdapter(),smsDataSourceOverride = source)
        var actual = AppContainer(LabStorageContext(host),smsDataSourceOverride = source)
        try {
            for (container in listOf(expected,actual)) {
                val scan = container.historicalSmsScanner.scan(Instant.EPOCH)
                assertEquals(scan.toString(),0,scan.failed)
                assertEquals(names.size,scan.scanned)
                confirmOwnership(container)
            }
            val expectedRows = expected.financialTransactionRepository.listAll().sortedBy { it.id }
            val actualRows = actual.financialTransactionRepository.listAll().sortedBy { it.id }
            assertEquals("Ledger rows",expectedRows,actualRows)
            assertTrue("Financial data must be posted",actualRows.size == names.size)
            val period = ReferenceHistory.period()
            val baseline = expected.dashboardService.loadOverview(period)
            assertOverview(baseline,actual.dashboardService.loadOverview(period))
            assertTrue(actual.dashboardService.loadOverview(period).creditFacilities.hasContent)
            assertFalse(actual.loanRegistryRepository.listAll().isEmpty())
            val reviews = actual.reviewRepository.listRequired()
            assertEquals("All known fixture messages should be automatic after ownership",0,reviews.size)
            val replay = actual.historicalSmsScanner.scan(Instant.EPOCH)
            assertEquals(0,replay.inserted)
            assertEquals(names.size,replay.duplicates)
            assertEquals(actualRows,actual.financialTransactionRepository.listAll().sortedBy { it.id })
            actual.close()
            actual = AppContainer(LabStorageContext(host),smsDataSourceOverride = source)
            assertEquals(actualRows,actual.financialTransactionRepository.listAll().sortedBy { it.id })
            assertOverview(baseline,actual.dashboardService.loadOverview(period))
            val report = java.io.File(host.getExternalFilesDir(null),"replica-financial-parity-${names.size}.txt")
            report.writeText("Same input: ${names.size} messages\nLedger rows: ${actualRows.size}\nReviews after ownership: ${reviews.size}\nSummary: ${baseline.summary}\nReplay duplicates: ${replay.duplicates}\nReopen preserved ledger and dashboard.\n")
        } finally { expected.close(); actual.close() }
    }
    private suspend fun confirmOwnership(container: AppContainer) {
        container.accountRegistryRepository.listAll().forEach {
            val ref = AccountReference(it.bank,it.maskedNumber)
            if (it.bank == Bank.BANK_ALJAZIRA) container.onboardingOwnershipWorkflow.confirmAccountOwned(ref)
            else container.onboardingOwnershipWorkflow.markAccountExternal(ref)
        }
        container.cardRegistryRepository.listAll().forEach { container.onboardingOwnershipWorkflow.confirmCardOwned(CardReference(it.bank,it.last4)) }
        container.reconcileStoredEvents()
        container.refreshReviewQueue()
    }
    private fun assertOverview(expected: DashboardOverview, actual: DashboardOverview) {
        assertEquals("Summary",expected.summary,actual.summary)
        assertEquals("Accounts",expected.currentAccount,actual.currentAccount)
        assertEquals("Spending split",expected.spendingSplit,actual.spendingSplit)
        assertEquals("Cards",expected.creditFacilities,actual.creditFacilities)
        assertEquals("Loans",expected.loansOverview,actual.loansOverview)
        assertEquals("Commitments",expected.commitmentsOverview,actual.commitmentsOverview)
        assertEquals("Merchants",expected.merchantSpending,actual.merchantSpending)
        assertEquals("Daily trend",expected.dailySpendingTrend,actual.dailySpendingTrend)
        assertEquals("Transaction facts",expected.transactionFacts,actual.transactionFacts)
        assertEquals("Owned accounts",expected.ownedAccountContainerIds,actual.ownedAccountContainerIds)
    }
}

internal object ReferenceHistory {
    val names = listOf("transfer_in_salary_ar_001","purchase_pos_ar_debit_001","purchase_online_ar_001",
        "financing_installment_ar_001","collision_transfer_fee_line_ar_001","fee_ar_001","card_payment_ar_001")
    fun period() = FinancialPeriod(LocalDate.now().minusMonths(1).withDayOfMonth(1),LocalDate.now().plusMonths(1).withDayOfMonth(1))
    val reported = (1..5).map { "reported_" + it.toString().padStart(2,'0') }
    fun source(context: Context, names: List<String> = this.names): SmsDataSource {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val today = LocalDate.now().minusDays(1)
        val records = names.mapIndexed { index,name ->
            val json = Json.parseToJsonElement(assets.open("bank-fixtures/$name.json").bufferedReader().use { it.readText() }).jsonObject
            var body = json.getValue("body").jsonPrimitive.content
            body = body.replace(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"),today.toString())
                .replace(Regex("[0-9]{2}-[0-9]{2}-[0-9]{4}"),today.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy")))
            ProviderSmsRecord("replica-history-$index",json.getValue("sender").jsonPrimitive.content,body,today.atTime(22,0).toInstant(ZoneOffset.UTC).plusSeconds(index.toLong()))
        }
        return object : SmsDataSource {
            override fun queryInbox(receivedAfter: Instant?) = records.asSequence().filter { receivedAfter == null || !it.receivedAt.isBefore(receivedAfter) }.map { InboxRow.Valid(it) }
        }
    }
}
