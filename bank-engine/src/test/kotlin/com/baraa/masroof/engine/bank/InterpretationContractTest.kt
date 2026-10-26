package com.baraa.masroof.engine.bank

import com.baraa.masroof.finance.value.BankId
import com.baraa.masroof.finance.value.Currency
import com.baraa.masroof.finance.value.Money
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class InterpretationContractTest {
    private val a = BankId("BANK_ALJAZIRA")
    private val b = BankId("D360")
    private val version = InterpretationVersion(1, "shadow-adapter-test", "legacy-fixture-1")

    @Test fun catalogKeepsAllSenderCollisionsRegardlessOfOrder() {
        val entries = listOf(BankDefinition(a, setOf("BANK")), BankDefinition(b, setOf("BANK")))
        assertEquals(setOf(a, b), BankCatalog(entries).candidates("BANK"))
        assertEquals(setOf(a, b), BankCatalog(entries.reversed()).candidates("BANK"))
        assertTrue(BankCatalog(entries).candidates("bank").isEmpty())
        assertThrows(IllegalArgumentException::class.java) { BankCatalog(entries + entries.first()) }
    }

    @Test fun originalEvidenceOffsetsDoNotUseFoldedArabicText() {
        val input = MessageInput("sms-1", "BANK", "شراء بمبلغ 10.00 SAR", Instant.EPOCH)
        val start = input.body.indexOf("10.00")
        assertEquals("10.00", Evidence("amount-label", "amount", start, start + 5).slice(input))
    }

    @Test fun futureContractIsNotSilentlySupported() {
        assertTrue(version.isSupported())
        assertFalse(version.copy(contract = 2).isSupported())
    }

    @Test fun statementAndNeutralDirectionAreFactsNotExpenseClassification() {
        val statement = Facts(MessageFamily.BALANCE_NOTICE, direction = MoneyDirection.NEUTRAL,
            cardChannel = CardChannel.STATEMENT, outstandingBalance = Money.of("100", Currency.SAR))
        assertEquals(CardChannel.STATEMENT, statement.cardChannel)
        assertEquals(MoneyDirection.NEUTRAL, statement.direction)
        assertNull(Facts(MessageFamily.UNKNOWN).direction)
    }

    @Test fun unknownRoutingAndFamilyRequireExplicitBlockers() {
        assertThrows(IllegalArgumentException::class.java) {
            Interpretation("sms-1", version, null, Facts(MessageFamily.UNKNOWN), ParseStatus.REVIEW_REQUIRED,
                null, emptyList(), emptySet())
        }
        val unresolved = Interpretation("sms-1", version, null, Facts(MessageFamily.UNKNOWN),
            ParseStatus.REVIEW_REQUIRED, null, emptyList(),
            setOf(Blocker.AMBIGUOUS_BANK, Blocker.UNKNOWN_FAMILY))
        assertEquals(2, unresolved.blockers.size)
    }

    @Test fun adapterApiPreservesLegacyFinancialFactsWithoutOwnershipOrPosting() {
        // Synthetic compile-time adapter example, not a claim that a production adapter is wired.
        val originalLocalTime = LocalDateTime.parse("2026-10-16T12:00:00")
        val adapter = BankInterpreter { input, catalog ->
            Interpretation(input.evidenceId, version, catalog.candidates(input.sender).single(),
                Facts(MessageFamily.PURCHASE, Money.of("10.00", Currency.USD), MoneyDirection.OUTGOING,
                    card = CardReference(a, "1234"), cardChannel = CardChannel.CREDIT,
                    occurredAtLocal = originalLocalTime, availableBalance = Money.of("2000", Currency.SAR),
                    exchangeRate = BigDecimal("3.751234"), internationalFee = Money.of("1.50", Currency.SAR),
                    labeledForeignAmount = Money.of("10", Currency.USD)), ParseStatus.SUCCESS,
                Confidence(1.0, listOf("synthetic")), emptyList(), emptySet())
        }
        val result = adapter.interpret(MessageInput("sms-1", "BANK", "synthetic", Instant.EPOCH),
            BankCatalog(listOf(BankDefinition(a, setOf("BANK")))))
        assertEquals(originalLocalTime, result.facts.occurredAtLocal)
        assertNull(result.facts.occurredAt)
        assertEquals(BigDecimal("3.751234"), result.facts.exchangeRate)
        assertEquals(Currency.USD, result.facts.amount!!.currency)
        assertEquals(CardChannel.CREDIT, result.facts.cardChannel)
    }

    @Test fun interBankTransferKeepsBothReferencesAndDoesNotInferOwnership() {
        val transfer = Facts(MessageFamily.TRANSFER_OUT, Money.of("100", Currency.SAR), MoneyDirection.OUTGOING,
            sourceAccount = AccountReference(a, "**1234"),
            destinationAccount = AccountReference(b, "**1234"), bankNetwork = BankNetwork.INTER_BANK,
            transactionReference = "reference-1", salaryIncomeWording = false)
        val source = requireNotNull(transfer.sourceAccount)
        val destination = requireNotNull(transfer.destinationAccount)
        assertNotEquals(source.bank, destination.bank)
        assertEquals(source.maskedNumber, destination.maskedNumber)
        assertEquals(BankNetwork.INTER_BANK, transfer.bankNetwork)
        assertEquals("reference-1", transfer.transactionReference)
    }
}
