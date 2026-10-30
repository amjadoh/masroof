package com.baraa.masroof.bank.patterns

import com.baraa.masroof.data.patterns.MessagePatternConfigurationCodec
import com.baraa.masroof.engine.bank.MessageFamily
import com.baraa.masroof.engine.bank.MessageInput
import com.baraa.masroof.engine.bank.MoneyDirection
import com.baraa.masroof.engine.bank.ParseStatus
import com.baraa.masroof.engine.bank.patterns.FieldChoice
import com.baraa.masroof.engine.bank.patterns.PatternAccountBinding
import com.baraa.masroof.engine.bank.patterns.PatternAccountRole
import com.baraa.masroof.engine.bank.patterns.PatternFactContract
import com.baraa.masroof.engine.bank.patterns.PatternFactInterpreter
import com.baraa.masroof.engine.bank.patterns.SavedMessagePattern
import com.baraa.masroof.finance.value.BankId
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.time.Instant

/** A message the bundled role profile does not recognize must stay teachable. */
class TaughtPatternOverRoleProfileTest {
    @Test fun unrecognizedBarqMessageCanBeTaughtAsIncomingTransfer() {
        val base = MessagePatternConfigurationCodec.decode(File("src/main/assets/bank-patterns/BANK_BARQ.patterns.json").readText())
        val bank = base.banks.single()
        val sender = bank.senders.first()
        val body = "إضافة أموال\nالمبلغ: 400.0 SAR\nالبطاقة: 7271**\nفي: 20:31 22-10-2026"
        val choices = listOf("amount" to "400.0 SAR", "funding_reference" to "7271**", "occurred_at" to "20:31 22-10-2026").map { (field, value) ->
            val start = body.indexOf(value)
            FieldChoice(field, start, start + value.length)
        }
        val contract = PatternFactContract(MessageFamily.TRANSFER_IN, MoneyDirection.INCOMING, version = 2,
            accountBindings = listOf(PatternAccountBinding(PatternAccountRole.DESTINATION, BankId(bank.bankId), "1482")),
            contextReferences = setOf("funding_reference"))
        val taught = SavedMessagePattern.fromChoices("user.topup", bank.bankId, sender, "TRANSFER_IN", body, choices).copy(factContract = contract)
        val configuration = base.copy(banks = listOf(bank.copy(patterns = bank.patterns + taught)))

        val result = PatternFactInterpreter(configuration).interpretRouted(MessageInput("sms", sender, body, Instant.EPOCH, true), BankId(bank.bankId))

        assertEquals(result.diagnostics.toString(), ParseStatus.SUCCESS, result.status)
        assertEquals(MessageFamily.TRANSFER_IN, result.facts.family)
        assertEquals("400.00", result.facts.amount!!.amount.toPlainString())
        assertEquals("1482", result.facts.destinationAccount!!.maskedNumber)
    }
}
