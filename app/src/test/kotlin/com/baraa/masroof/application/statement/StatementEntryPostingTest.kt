package com.baraa.masroof.application.statement

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.application.dashboard.AccountOpeningBalance
import com.baraa.masroof.application.dashboard.AccountOpeningBalanceStore
import com.baraa.masroof.application.settings.AccountBalanceSettingsWorkflow
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.data.repository.RoomFinancialTransactionRepository
import com.baraa.masroof.data.repository.RoomParsedEventRepository
import com.baraa.masroof.data.repository.RoomRawSmsRepository
import com.baraa.masroof.data.room.MasroofDatabase
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.statement.BankStatementEntry
import com.baraa.masroof.domain.statement.CanonicalStatementFormat
import com.baraa.masroof.domain.statement.ParsedBankStatement
import com.baraa.masroof.domain.statement.StatementCoverage
import com.baraa.masroof.domain.statement.StatementDirection
import com.baraa.masroof.testsupport.NoOpCardRegistryRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/** A fictional statement-only purchase posted twice stays one account expense with no SMS link. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StatementEntryPostingTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), MasroofDatabase::class.java)
        .allowMainThreadQueries().build()
    private val transactions = RoomFinancialTransactionRepository(db.financialTransactionDao(), db.parsedEventDao())

    @After fun close() = db.close()

    @Test fun aPostedLineIsOneExpenseOnItsAccount() = runBlocking {
        val entry = BankStatementEntry(4, Bank("BANK_SNB"), "0100", LocalDate.of(2026, 10, 14), null, "2026-10-14",
            StatementDirection.DEBIT, Money.of("12.00", Currency.SAR), "شراء عبر نقاط بيع", null)
        val statement = ParsedBankStatement(CanonicalStatementFormat.VERSION,
            StatementCoverage(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 11, 2)), emptyList(), listOf(entry), emptyList())
        val posting = StatementEntryPosting(transactions, balances(), ZoneId.of("Asia/Riyadh"))
        assertTrue(posting.post(statement, entry))
        assertTrue(posting.post(statement, entry))
        val saved = transactions.listAll().single()
        assertEquals(FinancialTransactionType.EXPENSE, saved.type)
        assertEquals("account:BANK_SNB:0100", saved.sourceContainerId)
        assertEquals(Money.of("12.00", Currency.SAR), saved.amount)
        assertTrue(transactions.listRawSmsIds(saved.id).isEmpty())
    }

    private fun balances() = AccountBalanceSettingsWorkflow(
        store = object : AccountOpeningBalanceStore {
            override fun load(): List<AccountOpeningBalance> = emptyList()
            override fun save(balance: AccountOpeningBalance) = Unit
            override fun remove(bankId: String, maskedNumber: String) = Unit
        },
        parsedEvents = RoomParsedEventRepository(db.parsedEventDao()),
        rawSms = RoomRawSmsRepository(db.rawSmsDao()),
        cards = NoOpCardRegistryRepository(emptyList()),
    )
}
