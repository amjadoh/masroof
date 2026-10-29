package com.baraa.masroof.bank.contract

import com.baraa.masroof.bank.BankSmsRegistry
import com.baraa.masroof.bank.BankRoutingResult
import com.baraa.masroof.bank.MessagePatternSmsAdapter
import com.baraa.masroof.data.patterns.MessagePatternConfigurationCodec
import com.baraa.masroof.engine.bank.patterns.PatternFactInterpreter
import com.baraa.masroof.engine.bank.patterns.PatternAccountBinding
import com.baraa.masroof.engine.bank.patterns.PatternAccountRole
import com.baraa.masroof.engine.bank.MessageInput
import com.baraa.masroof.finance.value.BankId
import com.baraa.masroof.finance.value.Currency
import com.baraa.masroof.bank.InterpretationDraftMapper
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
    @Test fun negativeAvailableBalanceSurvivesStorageAndReplacesAnOlderPositiveSnapshot() {
        val at = Instant.parse("2026-09-15T09:30:00Z")
        val body = "شراء إنترنت\nمبلغ 25 SAR\nبطاقة ائتمانية *4321\nمن TEST SHOP\nفي 2026-09-15 12:30\nالصرف المتبقي -50 SAR"
        val parsed = configuredAdapter("BANK_SNB").parse(SmsParseInput("negative-balance", "SNB-AlAhli", body, at))
        assertTrue(parsed.toString(), parsed is ParseResult.Success)
        parsed as ParseResult.Success
        assertNull(parsed.details.availableBalance)
        assertEquals("50.00", parsed.details.negativeAvailableBalance!!.amount.toPlainString())
        val entity = com.baraa.masroof.data.room.mapper.ParsedEventMapper.toEntity(parsed.event, parsed.details)
        assertEquals("-50.00", entity.availableBalanceDecimal)
        val restored = com.baraa.masroof.data.room.mapper.ParsedEventMapper.toRecord(entity)
        assertEquals(parsed.details, restored.details)
        assertTrue("Negative principal money remains invalid", runCatching {
            com.baraa.masroof.data.room.mapper.ParsedEventMapper.toRecord(entity.copy(amountDecimal = "-25.00"))
        }.isFailure)
        val old = restored.copy(event = restored.event.copy(id = "old-event", rawSmsId = "old-sms"),
            details = restored.details.copy(availableBalance = com.baraa.masroof.core.money.Money.of("100", com.baraa.masroof.core.money.Currency.SAR), negativeAvailableBalance = null))
        fun raw(id: String, instant: Instant) = com.baraa.masroof.domain.model.RawSms(id, "SNB-AlAhli", body, instant, id, id)
        val overview = com.baraa.masroof.application.dashboard.CreditCardOverviewBuilder.build(
            com.baraa.masroof.domain.period.FinancialPeriod(java.time.LocalDate.of(2026, 9, 1), java.time.LocalDate.of(2026, 10, 1)),
            emptyList(), listOf(old, restored), mapOf("old-sms" to raw("old-sms", at.minusSeconds(60)),
                restored.event.rawSmsId to raw(restored.event.rawSmsId, at)), java.time.ZoneId.of("Asia/Riyadh"))
        val snapshot = overview.cards.single().snapshot!!
        assertNull(snapshot.availableBalance)
        assertEquals("50.00", snapshot.negativeAvailableBalance!!.amount.toPlainString())
    }

    @Test fun creditBillUsesItsCardAndCompactTransferKeepsItsVariablePrincipal() {
        val adapter = configuredAdapter("BANK_SNB")
        val bill = adapter.parse(SmsParseInput("card-bill", "SNB-AlAhli",
            "سداد فاتورة\nبطاقة ائتمانية **4321\nمبلغ 60 SAR\nمفوتر 100\nفي 2026-09-15 12:30", Instant.now()))
        assertTrue(bill.toString(), bill is ParseResult.Success)
        bill as ParseResult.Success
        assertNull(bill.event.sourceAccountRef)
        val assembled = com.baraa.masroof.domain.assembly.TransactionAssembler.assembleSingle(bill.event, Instant.now(),
            com.baraa.masroof.domain.model.OwnershipStatus.UNKNOWN, com.baraa.masroof.domain.model.OwnershipStatus.UNKNOWN,
            com.baraa.masroof.domain.model.OwnershipStatus.OWNED)
        assertTrue(assembled is com.baraa.masroof.domain.assembly.TransactionAssembler.Outcome.Assembled)
        val transaction = (assembled as com.baraa.masroof.domain.assembly.TransactionAssembler.Outcome.Assembled).transaction
        assertEquals(com.baraa.masroof.domain.ids.FinancialContainerIdFactory.cardId(bill.event.cardRef!!), transaction.sourceContainerId)
        val compact = adapter.parse(SmsParseInput("compact", "SNB-AlAhli",
            "حوالة صادرة داخلية ب125.50 SAR\nل*5678 TEST PERSON\n2026-09-15 12:30", Instant.now()))
        assertTrue(compact.toString(), compact is ParseResult.Success)
        assertEquals("125.50", (compact as ParseResult.Success).event.amount!!.amount.toPlainString())
    }

    @Test fun knownCrossBankOwnedEndpointsMatchWithoutCreatingExpenseOrIncome() {
        val at = Instant.parse("2026-09-15T09:30:00Z")
        val out = configuredAdapter("BANK_SNB").parse(SmsParseInput("cross-out", "SNB-AlAhli",
            "حوالة صادرة محلية\nمن:*5678\nإلى:*1234\nعبر:برق\nمبلغ:100 SAR\nفي:2026-09-15 12:30", at))
        val incoming = configuredAdapter("BANK_BARQ").parse(SmsParseInput("cross-in", "barq app",
            "حوالة واردة محلية\n100SAR\nمن TEST PERSON\nبنك:NCBKSARI\n2026-09-15 12:30", at))
        assertTrue(out.toString(), out is ParseResult.Success)
        assertTrue(incoming.toString(), incoming is ParseResult.Success)
        val owned = com.baraa.masroof.domain.model.OwnershipStatus.OWNED
        val unknown = com.baraa.masroof.domain.model.OwnershipStatus.UNKNOWN
        val outgoing = com.baraa.masroof.domain.matching.TransferMatchCandidate((out as ParseResult.Success).event,
            null, out.details.occurredAtLocal, at, owned, unknown)
        val received = com.baraa.masroof.domain.matching.TransferMatchCandidate((incoming as ParseResult.Success).event,
            null, incoming.details.occurredAtLocal, at, unknown, owned)
        assertTrue(com.baraa.masroof.domain.matching.TransactionMatcher.compatiblePair(outgoing, received))
        assertFalse(com.baraa.masroof.domain.matching.TransactionMatcher.compatiblePair(outgoing.copy(sourceOwnership = unknown), received))
        val paired = com.baraa.masroof.domain.assembly.TransactionAssembler.assembleMatchedPair(
            com.baraa.masroof.domain.matching.TransferMatchPair(outgoing, received), owned, owned)
        assertTrue(paired is com.baraa.masroof.domain.assembly.TransactionAssembler.Outcome.Assembled)
        val transaction = (paired as com.baraa.masroof.domain.assembly.TransactionAssembler.Outcome.Assembled).transaction
        assertEquals(com.baraa.masroof.domain.model.FinancialTransactionType.SELF_TRANSFER, transaction.type)
        val impact = com.baraa.masroof.domain.rules.FinancialImpactCalculator.forType(transaction.type)
        assertFalse(impact.countsAsExpense)
        assertFalse(impact.countsAsIncome)
    }
    @Test fun confirmedSetup_readsChangingValuesAndNeverOverridesExplicitCreditCardOrAccount() {
        val barq = configuredAdapter("BANK_BARQ")
        for (amount in listOf("9.75", "4321", "12500.50")) {
            val result = barq.parse(SmsParseInput("variable-$amount", "barq app",
                "شراء نقاط البيع\nمدى$amount SAR\nالرصيد 900\nلدى DIFFERENT SHOP\n2026-09-15 12:30", Instant.now()))
            assertTrue("amount $amount: $result", result is ParseResult.Success)
            val event = (result as ParseResult.Success).event
            assertEquals(0, amount.toBigDecimal().compareTo(event.amount!!.amount))
            assertNull("An amount with four digits is not a card number", event.cardRef)
            assertEquals("1234", event.sourceAccountRef?.maskedNumber)
        }
        val snb = configuredAdapter("BANK_SNB")
        val credit = BankSmsAdapterContract.parse(snb, sms(rows.single { it.text("id") == "snb-online" })) as ParseResult.Success
        assertNull("A credit purchase must not debit the default cash account", credit.event.sourceAccountRef)
        val explicit = snb.parse(SmsParseInput("explicit-account", "SNB-AlAhli",
            "شراء عبر Google Pay\nمبلغ 25 SAR\nحساب: *5678\nمن TEST SHOP\nفي 2026-09-15 12:30", Instant.now())) as ParseResult.Success
        assertEquals("5678", explicit.event.sourceAccountRef?.maskedNumber)
        val foreign = snb.parse(SmsParseInput("foreign", "SNB-AlAhli",
            "شراء إنترنت\nمبلغ 25 USD\nبطاقة ائتمانية *4321\nمن TEST SHOP\nفي 2026-09-15 12:30", Instant.now())) as ParseResult.Success
        assertEquals("USD", foreign.event.amount!!.currency.name)
    }

    @Test fun confirmedSetup_readsBareTransferAndLoanWithoutGivingNoticesPostingAuthority() {
        val barq = configuredAdapter("BANK_BARQ")
        val transfer = barq.parse(SmsParseInput("incoming", "barq app",
            "حوالة واردة محلية\n100SAR\nمن TEST PERSON\nبنك:NCBKSARI\n2026-09-15 12:30", Instant.now()))
        assertTrue(transfer.toString(), transfer is ParseResult.Success)
        assertEquals("1234", (transfer as ParseResult.Success).event.destinationAccountRef?.maskedNumber)
        val enbd = configuredAdapter("BANK_EMIRATES_NBD")
        val loan = BankSmsAdapterContract.parse(enbd, sms(rows.single { it.text("id") == "enbd-installment-masked" }))
        assertTrue(loan.toString(), loan is ParseResult.Success)
        loan as ParseResult.Success
        assertEquals("PERSONAL", loan.details.loanType?.name)
        assertEquals("125", loan.event.amount!!.amount.stripTrailingZeros().toPlainString())
        assertEquals("1500", loan.details.outstandingBalance!!.amount.stripTrailingZeros().toPlainString())
        assertEquals("1234", loan.event.sourceAccountRef?.maskedNumber)
        rows.filter { it.text("status") == "NON_FINANCIAL" || it.text("id").endsWith("-declined") }.forEach { row ->
            assertFalse(row.text("id"), BankSmsAdapterContract.parse(configuredAdapter(row.text("bankId")), sms(row)) is ParseResult.Success)
        }
    }
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
        val findings = linkedMapOf<String, Int>()
        val configurations = senders.values.distinct().associateWith { bank ->
            configuredConfiguration(bank)
        }
        val engines = configurations.mapValues { PatternFactInterpreter(it.value) }
        val diagnosticRows = mutableListOf<JsonElement>()
        Json.parseToJsonElement(File(path).readText(Charsets.UTF_8).removePrefix("\uFEFF")).jsonArray.forEachIndexed { index, element ->
            val row = element.jsonObject
            val bank = senders.getValue(row.text("sender"))
            val input = MessageInput("private-$index", row.text("sender"), row.text("body"), Instant.now(), true)
            val interpretation = engines.getValue(bank).interpretRouted(input, BankId(bank))
            val result = InterpretationDraftMapper().map(input, interpretation, Bank(bank))
            val status = BankSmsAdapterContract.parsedEvent(result)?.parseStatus?.name ?: result.javaClass.simpleName
            counts.merge("$bank:$status", 1, Int::plus)
            if (result is ParseResult.ReviewRequired) interpretation.diagnostics.forEach {
                findings.merge("$bank:${it.code}", 1, Int::plus)
            }
            if (result is ParseResult.ReviewRequired) diagnosticRows += buildJsonObject {
                put("index", index); put("bank", bank); put("family", interpretation.facts.family.name)
                put("codes", JsonArray(interpretation.diagnostics.map { JsonPrimitive(it.code) }))
            }
        }
        println("Private corpus outcomes (no message contents): $counts")
        println("Private corpus validation codes: $findings")
        System.getenv("MASROOF_PRIVATE_DIAGNOSTICS")?.let { File(it).writeText(JsonArray(diagnosticRows).toString()) }
    }

    companion object {
        val rows: List<JsonObject> = Json.parseToJsonElement(File("src/test/resources/testdata/multibank/messages.json").readText()).jsonArray.map { it.jsonObject }
        val adapters = rows.map { it.text("bankId") }.distinct().associateWith { bank ->
            val config = MessagePatternConfigurationCodec.decode(File("src/main/assets/bank-patterns/$bank.patterns.json").readText())
            MessagePatternSmsAdapter(config.banks.single(), PatternFactInterpreter(config))
        }
        private fun configuredConfiguration(bank: String): com.baraa.masroof.engine.bank.patterns.MessagePatternConfiguration {
            val config = MessagePatternConfigurationCodec.decode(File("src/main/assets/bank-patterns/$bank.patterns.json").readText())
            return config.copy(banks = config.banks.map { definition -> definition.copy(patterns = definition.patterns.map { pattern ->
                val contract = requireNotNull(pattern.factContract)
                val family = contract.family.name
                if (family !in setOf("PURCHASE", "TRANSFER_IN", "TRANSFER_OUT", "REFUND", "WITHDRAWAL", "FEE", "FINANCING_INSTALLMENT")) pattern
                else pattern.copy(revision = pattern.revision + 1, factContract = contract.copy(version = 2, currency = Currency.SAR,
                    accountBindings = listOf(PatternAccountBinding(if (family in setOf("TRANSFER_IN", "REFUND")) PatternAccountRole.DESTINATION else PatternAccountRole.SOURCE,
                        BankId(bank), "1234"))))
            }) })
        }
        private fun configuredAdapter(bank: String) = configuredConfiguration(bank).let { MessagePatternSmsAdapter(it.banks.single(), PatternFactInterpreter(it)) }
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
