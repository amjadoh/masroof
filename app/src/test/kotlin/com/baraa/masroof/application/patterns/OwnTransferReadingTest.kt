package com.baraa.masroof.application.patterns

import com.baraa.masroof.data.patterns.MessagePatternConfigurationCodec
import com.baraa.masroof.engine.bank.*
import com.baraa.masroof.engine.bank.patterns.*
import com.baraa.masroof.finance.value.BankId
import com.baraa.masroof.finance.value.Currency
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDateTime

/** Entirely fictional values: STC/SNB transfer, refund and deposit layouts, never private SMS fixtures. */
class OwnTransferReadingTest {
    private val receivedAt = Instant.parse("2026-06-23T11:39:00Z")

    @Test fun stcOutgoingToANamedBankKeepsTheStatedFeeApartFromThePrincipal() {
        val body = "حوالة صادرة (سريع)\n150.00 ر.س\nإلى فلان الاختبار\nإلى البنك الأهلي السعودي \nحساب *123\nرسوم 0.58 ر.س\n15/05/26 09:32\nمرجع *AB12"
        val result = stc(body)
        assertEquals(result.diagnostics.toString(), ParseStatus.SUCCESS, result.status)
        assertEquals(MessageFamily.TRANSFER_OUT, result.facts.family)
        assertEquals("150.00", result.facts.amount?.amount?.toPlainString())
        assertEquals("0.58", result.facts.transferFee?.amount?.toPlainString())
        // The account line is the sender's own STC account, never the counterparty.
        assertEquals(AccountReference(STC, "6123"), result.facts.sourceAccount)
        assertEquals(AccountReference(SNB, null), result.facts.destinationAccount)
        assertEquals(LocalDateTime.of(2026, 5, 15, 9, 32), result.facts.occurredAtLocal)
        // Another bank, or another local account line, is not this confirmed definition.
        assertNotEquals(ParseStatus.SUCCESS, stc(body.replace("البنك الأهلي السعودي", "مصرف الإنماء")).status)
        assertNotEquals(ParseStatus.SUCCESS, stc(body.replace("*123", "*124")).status)
    }

    @Test fun stcLocalOutgoingNamesTheBankWithoutTreatingItsAccountPrefixAsAnAccount() {
        val result = stc("حوالة صادرة محلية\nSR136.50\nرسوم 0.58SR\nإلى فلان الاختبار\nإلىSNB\nإلى456\n24/10/26 20:16")
        assertEquals(result.diagnostics.toString(), ParseStatus.SUCCESS, result.status)
        assertEquals("136.50", result.facts.amount?.amount?.toPlainString())
        assertEquals("0.58", result.facts.transferFee?.amount?.toPlainString())
        assertEquals(AccountReference(STC, "6123"), result.facts.sourceAccount)
        assertEquals(AccountReference(SNB, null), result.facts.destinationAccount)
    }

    @Test fun placeholderBankClockFallsBackToTheDurableReceipt() {
        val body = "حوالة واردة (سريع)\n2500.00 رس\nمن فلان الاختبار\nمن بنك NCBK\nحساب *123\n01-01-1970 03:00\nمرجع *GH78"
        val result = stc(body)
        assertEquals(result.diagnostics.toString(), ParseStatus.SUCCESS, result.status)
        assertEquals(MessageFamily.TRANSFER_IN, result.facts.family)
        assertEquals(AccountReference(STC, "6123"), result.facts.destinationAccount)
        assertNull(result.facts.occurredAtLocal)
        assertEquals(receivedAt, result.facts.occurredAt)
        assertTrue(requireNotNull(result.patternProvenance).usesReceiptTime)
        val dated = stc(body.replace("01-01-1970 03:00", "29-06-2026 12:12"))
        assertEquals(LocalDateTime.of(2026, 6, 29, 12, 12), dated.facts.occurredAtLocal)
        assertNull(dated.facts.occurredAt)
        // Without a durable receipt there is no time to fall back to.
        val volatile = interpret(configured(), "BANK_STC", "STC Bank", body, durable = false)
        assertNotEquals(ParseStatus.SUCCESS, volatile.status)
    }

    @Test fun bundledStcTransferTemplatesStayPreviewOnlyUntilTheAccountIsConfirmed() {
        val bundled = bundled("BANK_STC")
        for (body in listOf("حوالة صادرة (سريع)\n150.00 ر.س\nإلى فلان الاختبار\nإلى مصرف الإنماء \nحساب *123\nرسوم 0.58 ر.س\n15/05/26 09:32\nمرجع *AB12",
            "حوالة واردة (سريع)\n2500.00 رس\nمن فلان الاختبار\nمن بنك NCBK\nحساب *123\n29-06-2026 12:12\nمرجع *CD34")) {
            val result = interpret(bundled, "BANK_STC", "STC Bank", body)
            assertEquals(listOf("PATTERN_CONTRACT_MISSING"), result.diagnostics.map { it.code })
            assertEquals(ParseStatus.REVIEW_REQUIRED, result.status)
        }
    }

    @Test fun feeIsOnlyAnOutgoingTransferFact() {
        val config = bundled("BANK_STC").let { definitions -> definitions.copy(banks = definitions.banks.map { bank ->
            bank.copy(patterns = bank.patterns.map { pattern -> if (pattern.id != "stc-sarie-out-ar") pattern else pattern.copy(
                factContract = PatternFactContract(MessageFamily.TRANSFER_IN, MoneyDirection.INCOMING, Currency.SAR, version = 2,
                    accountBindings = listOf(PatternAccountBinding(PatternAccountRole.DESTINATION, STC, "6123")),
                    contextReferences = setOf("counterparty_bank"))) })
        }) }
        val result = interpret(config, "BANK_STC", "STC Bank", "حوالة صادرة (سريع)\n150.00 ر.س\nإلى فلان الاختبار\nإلى مصرف الإنماء \nحساب *123\nرسوم 0.58 ر.س\n15/05/26 09:32\nمرجع *AB12")
        assertTrue(result.diagnostics.toString(), result.diagnostics.any { it.code == "PATTERN_ROLE_UNSUPPORTED" })
        assertNull(result.facts.transferFee)
        assertNotEquals(ParseStatus.SUCCESS, result.status)
    }

    @Test fun snbCreditCardHoldReleaseAndCashBackAreRefundsToTheCard() {
        val release = interpret(bundled("BANK_SNB"), "BANK_SNB", "SNB-AlAhli",
            "بطاقة إئتمانية الغاء حجز مبلغ\nبطاقة **8765\nمبلغ 34.50 SAR\nمن TEST STORE\nفي 20/06/2026 21:55\nالرصيد المتبقي 1500.00")
        assertEquals(release.diagnostics.toString(), ParseStatus.SUCCESS, release.status)
        assertEquals(MessageFamily.REFUND, release.facts.family)
        assertEquals(MoneyDirection.INCOMING, release.facts.direction)
        assertEquals("8765", release.facts.card?.last4)
        assertEquals("34.50", release.facts.amount?.amount?.toPlainString())
        assertEquals(CardChannel.CREDIT, release.facts.cardChannel)
        assertNull(release.facts.destinationAccount)
        val cashBack = interpret(bundled("BANK_SNB"), "BANK_SNB", "SNB-AlAhli",
            "بطاقة ائتمانية استرجاع نقدي\nفي بطاقة **8765\nمبلغ 56.00 SAR\nفي 10/17 22:19\nالصرف المتبقي 768.25 SAR")
        assertEquals(cashBack.diagnostics.toString(), ParseStatus.SUCCESS, cashBack.status)
        assertEquals(MessageFamily.REFUND, cashBack.facts.family)
        // A month/day line without a year is not a wall clock; the durable receipt is the time.
        assertNull(cashBack.facts.occurredAtLocal)
        assertEquals(receivedAt, cashBack.facts.occurredAt)
        assertEquals("8765", cashBack.facts.card?.last4)
        assertEquals("56.00", cashBack.facts.amount?.amount?.toPlainString())
        // Only the configured title consumes its cancellation word; another one still blocks.
        val cancelled = interpret(bundled("BANK_SNB"), "BANK_SNB", "SNB-AlAhli",
            "بطاقة ائتمانية استرجاع نقدي\nفي بطاقة **8765\nمبلغ 56.00 SAR\nتم الغاء العملية")
        assertNotEquals(ParseStatus.SUCCESS, cancelled.status)
        // A purchase title is still a purchase; the refund cue must lead the title.
        val purchase = interpret(bundled("BANK_SNB"), "BANK_SNB", "SNB-AlAhli",
            "شراء عبر نقاط البيع\nبطاقة ائتمانية **8765\nمبلغ 34.50 SAR\nلدى TEST STORE\nفي 20/06/2026 21:55")
        assertNotEquals(MessageFamily.REFUND, purchase.facts.family)
    }

    @Test fun snbCashDepositPostsOnlyToTheConfirmedAccountReference() {
        val body = "ايداع صراف آلي\nمبلغ 700 SAR\nحساب 200*321\nفي 19/07/26 21:21"
        val unconfirmed = interpret(bundled("BANK_SNB"), "BANK_SNB", "SNB-AlAhli", body)
        assertEquals(MessageFamily.TRANSFER_IN, unconfirmed.facts.family)
        assertNotEquals(ParseStatus.SUCCESS, unconfirmed.status)
        val confirmed = interpret(bundled("BANK_SNB").bind("snb.cash-deposit",
            PatternAccountBinding(PatternAccountRole.DESTINATION, SNB, "4321", "destination_account", "200*321")), "BANK_SNB", "SNB-AlAhli", body)
        assertEquals(confirmed.diagnostics.toString(), ParseStatus.SUCCESS, confirmed.status)
        assertEquals(AccountReference(SNB, "4321"), confirmed.facts.destinationAccount)
        assertNull(confirmed.facts.sourceAccount)
        assertEquals("700.00", confirmed.facts.amount?.amount?.toPlainString())
        assertNotEquals(true, confirmed.facts.salaryIncomeWording)
        val other = interpret(bundled("BANK_SNB").bind("snb.cash-deposit",
            PatternAccountBinding(PatternAccountRole.DESTINATION, SNB, "4321", "destination_account", "200*321")), "BANK_SNB", "SNB-AlAhli",
            body.replace("200*321", "200*322"))
        assertNotEquals(AccountReference(SNB, "4321"), other.facts.destinationAccount)
        assertNotEquals(ParseStatus.SUCCESS, other.status)
    }

    private fun stc(body: String) = interpret(configured(), "BANK_STC", "STC Bank", body)

    private fun interpret(config: MessagePatternConfiguration, bank: String, sender: String, body: String, durable: Boolean = true): Interpretation {
        val persisted = MessagePatternConfigurationCodec.decode(MessagePatternConfigurationCodec.encode(config))
        return PatternFactInterpreter(persisted).interpretRouted(MessageInput("fictional", sender, body, receivedAt, durable), BankId(bank))
    }

    companion object {
        val STC = BankId("BANK_STC")
        val SNB = BankId("BANK_SNB")

        fun bundled(bank: String) = MessagePatternConfigurationCodec.decode(File("src/main/assets/bank-patterns/$bank.patterns.json").readText())

        fun MessagePatternConfiguration.bind(patternId: String, binding: PatternAccountBinding) = copy(banks = banks.map { bank ->
            bank.copy(patterns = bank.patterns.map { pattern -> if (pattern.id != patternId) pattern else pattern.copy(
                factContract = pattern.factContract!!.copy(version = 2, currency = Currency.SAR, accountBindings = listOf(binding))) })
        })

        /**
         * What a confirmed device configuration holds: the user's STC account bound by its
         * observed line, and receiving-bank variants that state that bank literally.
         */
        fun configured(): MessagePatternConfiguration {
            val definitions = bundled("BANK_STC")
            val bank = definitions.banks.single()
            val sarieOut = bank.currentPatterns.single { it.id == "stc-sarie-out-ar" }
            val localOut = bank.currentPatterns.single { it.id == "stc-local-out-ar" }
            val sarieIn = bank.currentPatterns.single { it.id == "stc-sarie-in-rs" }
            val confirmed = listOf(
                sarieOut.copy(id = "local-stc-sarie-out-snb", parts = sarieOut.parts.literal("counterparty_bank", "البنك الأهلي السعودي"),
                    factContract = PatternFactContract(MessageFamily.TRANSFER_OUT, MoneyDirection.OUTGOING, Currency.SAR,
                        destinationAccountBank = SNB, version = 2,
                        accountBindings = listOf(PatternAccountBinding(PatternAccountRole.SOURCE, STC, "6123", "source_account", "*123")))),
                localOut.copy(id = "local-stc-local-out-snb", parts = localOut.parts.literal("counterparty_bank", "SNB"),
                    factContract = PatternFactContract(MessageFamily.TRANSFER_OUT, MoneyDirection.OUTGOING, Currency.SAR,
                        destinationAccountBank = SNB, version = 2,
                        accountBindings = listOf(PatternAccountBinding(PatternAccountRole.SOURCE, STC, "6123")),
                        contextReferences = setOf("account_reference"))),
                sarieIn.copy(id = "local-stc-sarie-in-rs",
                    factContract = PatternFactContract(MessageFamily.TRANSFER_IN, MoneyDirection.INCOMING, Currency.SAR, version = 2,
                        accountBindings = listOf(PatternAccountBinding(PatternAccountRole.DESTINATION, STC, "6123", "destination_account", "*123")),
                        contextReferences = setOf("counterparty_bank"))),
            )
            // Generic previews stay out of a device that confirmed the specific variants.
            val kept = bank.patterns.filterNot { it.id in setOf("stc-sarie-out-ar", "stc-local-out-ar", "stc-sarie-in-rs") }
            return definitions.copy(banks = listOf(bank.copy(patterns = kept + confirmed)))
        }

        private fun List<PatternPart>.literal(field: String, text: String): List<PatternPart> {
            val index = indexOfFirst { it is FieldSlot && it.field == field }
            require(index > 0)
            val merged = StaticText((this[index - 1] as StaticText).value + text + (this[index + 1] as StaticText).value)
            return take(index - 1) + merged + drop(index + 2)
        }
    }
}
