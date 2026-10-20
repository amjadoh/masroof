package com.baraa.masroof.lab.beta.bank

import com.baraa.masroof.lab.bank.MessageInput
import com.baraa.masroof.lab.bank.interpretation.RuleInterpreter
import com.baraa.masroof.lab.bank.rules.AlJaziraRuleProfile
import com.baraa.masroof.lab.finance.value.BankId
import com.baraa.masroof.lab.replica.bank.NewEngineSmsAdapter
import com.baraa.masroof.lab.replica.parsing.model.*
import com.baraa.masroof.lab.reference.bank.aljazira.AlJaziraParsingPipeline
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.time.Instant

@RunWith(Parameterized::class)
class NewEngineFixtureTest(private val file: File) {
    @Test fun matchesApprovedFixtureAndProductionFacts() {
        val fixture = Json.parseToJsonElement(file.readText()).jsonObject
        val expected = fixture.getValue("expected").jsonObject
        val input = SmsParseInput(fixture.getValue("id").jsonPrimitive.content,fixture.getValue("sender").jsonPrimitive.content,
            fixture.getValue("body").jsonPrimitive.content,Instant.parse("2026-10-20T12:00:00Z"))
        val engine = RuleInterpreter(AlJaziraRuleProfile.current())
        val actual = NewEngineSmsAdapter { engine.interpretRouted(it,BankId("BANK_ALJAZIRA")) }.parse(input)
        val production = AlJaziraParsingPipeline().parse(input)
        fun event(r: ParseResult) = when(r) { is ParseResult.Success -> r.event; is ParseResult.NonFinancial -> r.event; is ParseResult.ReviewRequired -> r.event; is ParseResult.Partial -> r.event; else -> null }
        fun details(r: ParseResult) = when(r) { is ParseResult.Success -> r.details; is ParseResult.NonFinancial -> r.details; is ParseResult.ReviewRequired -> r.details; is ParseResult.Partial -> r.details; else -> null }
        val e = event(actual)
        assertNotNull("$file outcome=$actual",e)
        val errors = mutableListOf<String>()
        if (expected.getValue("parseStatus").jsonPrimitive.content != e!!.parseStatus.name) errors += "status expected=${expected["parseStatus"]} actual=${e.parseStatus} reasons=${e.confidence.reasons}"
        if (expected.getValue("messageFamily").jsonPrimitive.content != e.messageFamily.name) errors += "family expected=${expected["messageFamily"]} actual=${e.messageFamily}"
        if (e.parseStatus.name == "SUCCESS") {
            val p = event(production)!!
            val fields = mutableMapOf("family" to (p.messageFamily to e.messageFamily), "status" to (p.parseStatus to e.parseStatus), "amount" to (p.amount to e.amount),"direction" to (p.direction to e.direction),
                "source" to (p.sourceAccountRef to e.sourceAccountRef),"destination" to (p.destinationAccountRef to e.destinationAccountRef),
                "card" to (p.cardRef to e.cardRef),"merchant" to (p.merchant to e.merchant),"counterparty" to (p.counterparty to e.counterparty),
                "purchaseChannel" to (p.purchaseChannel to e.purchaseChannel),"network" to (p.bankNetworkType to e.bankNetworkType),
                "occurredAt" to (p.occurredAt to e.occurredAt))
            fun addDetails(d: ParsedEventDetails?) = mapOf("transactionReference" to d?.transactionReference,"availableBalance" to d?.availableBalance,
                "outstandingBalance" to d?.outstandingBalance,"biller" to d?.biller,"billerCode" to d?.billerCode,"occurredAtLocal" to d?.occurredAtLocal,
                "cardSmsChannel" to d?.cardSmsChannel,"paymentDueDate" to d?.paymentDueDate,"exchangeRate" to d?.exchangeRate,"internationalFee" to d?.internationalFee,
                "labeledForeignAmount" to d?.labeledForeignAmount,"loanType" to d?.loanType,"debitSourceAccountLast4" to d?.debitSourceAccountLast4,"salaryIncomeWording" to d?.salaryIncomeWording)
            val pd = addDetails(details(production)); val ad = addDetails(details(actual))
            pd.forEach { (k,v) -> fields["details.$k"] = v to ad[k] }
            val approved = Json.parseToJsonElement(File("ENGINE_PARITY_DIFFERENCES.json").readText()).jsonObject[input.rawSmsId]?.jsonObject
            fun equivalent(a: Any?, b: Any?) = if (a is java.math.BigDecimal && b is java.math.BigDecimal) a.compareTo(b)==0 else a==b
            fields.filterValues { !equivalent(it.first,it.second) }.forEach { (name,values) ->
                val change = approved?.get(name)?.jsonObject
                if (change == null || change["production"]?.jsonPrimitive?.content != values.first.toString() || change["engine"]?.jsonPrimitive?.content != values.second.toString())
                    errors += "$name production=${values.first} engine=${values.second}"
            }
        }
        assertTrue("${file.name}:\n${errors.joinToString("\n")}\nresult=$actual",errors.isEmpty())
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name="{0}") fun cases(): List<Array<File>> {
            val canonical = listOf(File("../../app/src/test/resources/testdata/bank_aljazira"),File("testdata/reported")).asSequence()
                .flatMap { it.walkTopDown() }.filter { it.extension=="json" && it.name!="jazira_sms_bodies.json" }.toList()
            val reference = Json.parseToJsonElement(File("../../app/src/test/resources/testdata/bank_aljazira/reference/jazira_sms_bodies.json").readText()).jsonObject.getValue("messages").jsonArray
            val families = listOf("TRANSFER_OUT","TRANSFER_IN","TRANSFER_IN","PURCHASE","PURCHASE","TRANSFER_OUT","PURCHASE","TRANSFER_OUT","TRANSFER_IN","FINANCING_INSTALLMENT")
            check(reference.size==families.size)
            val generated = reference.mapIndexed { index,value ->
                val row = value.jsonObject
                val id = row.getValue("id").jsonPrimitive.content
                val fixture = buildJsonObject {
                    put("id",id);put("sender","AlJazira");put("body",row.getValue("body"))
                    put("expected",buildJsonObject { put("messageFamily",families[index]);put("parseStatus","SUCCESS") })
                }
                File("build/generated/reference-fixtures/$id.json").apply { parentFile.mkdirs();writeText(fixture.toString()) }
            }
            return (canonical+generated).sortedBy { it.name }.map { arrayOf(it) }.also { check(it.size>=55) }
        }
    }
}
