package com.baraa.masroof.bank.contract

import com.baraa.masroof.bank.BankSmsRegistry
import com.baraa.masroof.bank.BankRoutingResult
import com.baraa.masroof.bank.MessagePatternSmsAdapter
import com.baraa.masroof.data.patterns.MessagePatternConfigurationCodec
import com.baraa.masroof.engine.bank.patterns.PatternFactInterpreter
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.parsing.model.ParseResult
import com.baraa.masroof.parsing.model.SmsParseInput
import java.io.File
import java.time.Instant
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Observed bank layouts with entirely fictional values, names and timestamps. */
class FiveBankSmsTest {
    @Test fun fixtures_preserveFinancialFactsAndSecurityGates() {
        rows.forEach { row ->
            val label = row.text("id")
            val result = BankSmsAdapterContract.parse(adapters.getValue(row.text("bankId")), sms(row))
            val event = BankSmsAdapterContract.parsedEvent(result)
            assertNotNull(label, event)
            assertEquals("$label: status, $result", row.text("status"), event!!.parseStatus.name)
            assertEquals(label, row.text("family"), event.messageFamily.name)
            row["amount"]?.jsonPrimitive?.contentOrNull?.let {
                assertEquals(label, 0, it.toBigDecimal().compareTo(event.amount!!.amount))
            }
            row["cardChannel"]?.jsonPrimitive?.contentOrNull?.let {
                val details = when (result) {
                    is ParseResult.Success -> result.details
                    else -> error("$label expected success")
                }
                assertEquals(label, it, details.cardSmsChannel?.name)
            }
        }
    }

    @Test fun exactSenders_routeOnceAndDoNotOverlapOtherBanks() {
        val registry = BankSmsRegistry(BankSmsAdapterContractTest.cases().map { it.adapter })
        rows.forEach { row ->
            val route = registry.route(row.text("sender"), row.text("body"))
            assertTrue(row.text("id"), route is BankRoutingResult.Matched)
            assertEquals(row.text("id"), Bank(row.text("bankId")), (route as BankRoutingResult.Matched).adapter.bank)
        }
        assertTrue(registry.route("Barg app", "شراء") is BankRoutingResult.NotMatched)
    }

    @Test fun privateCorpus_reportsCountsOnlyWhenExplicitlyProvided() {
        val path = System.getenv("MASROOF_PRIVATE_CORPUS") ?: return
        val senders = rows.associate { it.text("sender") to it.text("bankId") }
        val counts = linkedMapOf<String, Int>()
        Json.parseToJsonElement(File(path).readText(Charsets.UTF_8).removePrefix("\uFEFF")).jsonArray.forEachIndexed { index, element ->
            val row = element.jsonObject
            val bank = senders.getValue(row.text("sender"))
            val result = adapters.getValue(bank).parse(SmsParseInput("private-$index", row.text("sender"), row.text("body"), Instant.now()))
            val status = BankSmsAdapterContract.parsedEvent(result)?.parseStatus?.name ?: result.javaClass.simpleName
            counts.merge("$bank:$status", 1, Int::plus)
        }
        println("Private corpus outcomes (no message contents): $counts")
    }

    companion object {
        val rows: List<JsonObject> = Json.parseToJsonElement(File("src/test/resources/testdata/multibank/messages.json").readText()).jsonArray.map { it.jsonObject }
        val adapters = rows.map { it.text("bankId") }.distinct().associateWith { bank ->
            val config = MessagePatternConfigurationCodec.decode(File("src/main/assets/bank-patterns/$bank.patterns.json").readText())
            MessagePatternSmsAdapter(config.banks.single(), PatternFactInterpreter(config))
        }
        fun cases(): List<BankSmsAdapterContractCase> = adapters.map { (bank, adapter) ->
            val fixtures = rows.filter { it.text("bankId") == bank }
            val sender = fixtures.first().text("sender")
            BankSmsAdapterContractCase(adapter, BankSmsAdapterContractSamples(listOf(sender),
                listOf("AlJazira", "$sender-fake", "PERSON"),
                fixtures.filter { it.text("status") == "SUCCESS" }.map(::sms),
                fixtures.filter { it.text("status") == "NON_FINANCIAL" }.map(::sms),
                fixtures.filter { it.text("status") == "REVIEW_REQUIRED" }.map(::sms)))
        }
        private fun sms(row: JsonObject) = ContractSms(row.text("id"), row.text("sender"), row.text("body"))
        private fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.content
    }
}
