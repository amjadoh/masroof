package com.baraa.masroof.integration.bank

import com.baraa.masroof.engine.bank.*
import com.baraa.masroof.engine.bank.interpretation.RuleInterpreter
import com.baraa.masroof.engine.bank.rules.AlJaziraRuleProfile
import com.baraa.masroof.finance.value.BankId
import com.baraa.masroof.testsupport.InterpretationTestAdapter
import com.baraa.masroof.parsing.model.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDateTime

class DeviceReportedMessagesTest {
    private val bank=BankId("BANK_ALJAZIRA")
    private val engine=RuleInterpreter(AlJaziraRuleProfile.current())
    private fun fixture(name: String)=Json.parseToJsonElement(File("src/test/resources/testdata/engine/device-reported/$name.json").readText()).jsonObject
    private fun parse(body: String)=engine.interpretRouted(MessageInput("reported","AlJazira",body,Instant.parse("2026-10-21T00:00:00Z"),true),bank)
    private fun body(name: String)=fixture(name).getValue("body").jsonPrimitive.content

    @Test fun reportedMessagesPassTheProductionValidationGateWithCorrectRolesAndBankTime() {
        for(name in listOf("device_instant_transfer","device_google_pay","device_water_bill")) {
            val f=fixture(name); val expected=f.getValue("expected").jsonObject
            val result=InterpretationTestAdapter {engine.interpretRouted(it,bank)}.parse(SmsParseInput(name,"AlJazira",body(name),Instant.parse("2026-10-21T00:00:00Z")))
            assertTrue("$name $result",result is ParseResult.Success)
            val success=result as ParseResult.Success
            assertEquals(expected.getValue("messageFamily").jsonPrimitive.content,success.event.messageFamily.name)
            assertEquals(expected.getValue("amount").jsonPrimitive.content,success.event.amount!!.amount.toPlainString())
            assertFalse(success.event.confidence.reasons.any {it.startsWith("family_origin:")})
            assertNotNull(success.details.occurredAtLocal)
        }
        val transfer=parse(body("device_instant_transfer"))
        assertEquals("3001",transfer.facts.sourceAccount!!.maskedNumber)
        assertEquals("9466",transfer.facts.destinationAccount!!.maskedNumber)
        assertNull(transfer.facts.destinationAccount!!.bank) // Never infer local ownership for an unnamed external bank.
        assertEquals(BankNetwork.INTER_BANK,transfer.facts.bankNetwork)
        assertEquals(LocalDateTime.of(2026,10,5,20,4),transfer.facts.occurredAtLocal)
        assertNull(transfer.facts.occurredAt)
        assertEquals(CardChannel.DEBIT,parse(body("device_google_pay")).facts.cardChannel)
        val bill=parse(body("device_water_bill"))
        assertEquals("3001",bill.facts.sourceAccount!!.maskedNumber)
        assertEquals("138",bill.facts.billerCode)
        assertEquals("شركة المياه الوطنية",bill.facts.biller)
        assertEquals("8714347961",bill.facts.transactionReference)
    }

    @Test fun separateCurrencyNeverInventsCurrencyOrAcceptsConflictsInvalidPrecisionOrBalanceAsPrincipal() {
        val original=body("device_instant_transfer")
        val unsafe=listOf(original.replace("العملة: SAR",""),original.replace("العملة: SAR","العملة: JPY"),
            original+"\nالعملة: USD",original.replace("100.00","100.001"),original.replace("100.00","-100.00"),
            original.replace("100.00","0.00"),original.replace("100.00","100.00 USD"),
            original.replace("مبلغ العملية: 100.00","الرصيد المتاح: 100.00"))
        unsafe.forEach {assertNotEquals(it,ParseStatus.SUCCESS,parse(it).status)}
    }

    @Test fun dateAndTimeOrdersHaveEqualMeaningAndInvalidOrUnboundTimeCannotFallBackToReceipt() {
        val original=body("device_instant_transfer")
        val expected=parse(original).facts.occurredAtLocal
        assertEquals(expected,parse(original.replace("05-10-2026 20:04","20:04 05-10-2026")).facts.occurredAtLocal)
        assertEquals(expected,parse(original.replace("05-10-2026","05/10/2026")).facts.occurredAtLocal)
        for(body in listOf(original.replace("05-10-2026","31-02-2026"),original.replace("20:04","25:04"),
            original.replace("التاريخ:","انتهاء الصلاحية:"),original+"\nفي: 06-10-2026 20:04")) {
            val result=parse(body)
            assertEquals(body,ParseStatus.REVIEW_REQUIRED,result.status)
            assertNull(result.facts.occurredAt)
        }
    }

    @Test fun titlesAndRoleCollisionsStillRequireReview() {
        for(body in listOf(body("device_google_pay")+"\nبطاقة مدى رقم: 9999",
            body("device_instant_transfer").replace("حوالة فورية صادرة","حوالة فورية صادرة وحوالة واردة"),
            body("device_water_bill")+"\nمن: 5001"))
            assertNotEquals(body,ParseStatus.SUCCESS,parse(body).status)
    }
}
