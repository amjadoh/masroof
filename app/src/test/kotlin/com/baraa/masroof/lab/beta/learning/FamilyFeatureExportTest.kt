package com.baraa.masroof.lab.beta.learning

import com.baraa.masroof.lab.ml.family.FamilyInputCleaner
import com.baraa.masroof.application.interpretation.LabFamilyAssist
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant
import com.baraa.masroof.bank.*
import com.baraa.masroof.lab.bank.interpretation.*
import com.baraa.masroof.lab.bank.rules.AlJaziraRuleProfile
import com.baraa.masroof.lab.finance.value.BankId
import com.baraa.masroof.parsing.model.*

/** Exports the frozen human-labelled training corpus; parser regression fixtures do not enroll themselves. */
class FamilyFeatureExportTest {
    @Test fun exportVerifiedCorpusFeatures() {
        val canonical = File("src/test/resources/testdata/bank_aljazira")
        val reported = File("src/test/resources/testdata/engine/reported")
        val approvedIds = Json.parseToJsonElement(File(System.getenv("MASROOF_CORPUS_SPLITS") ?: "../ml-assist/tools/corpus-splits.json").readText())
            .jsonObject.getValue("partitions").jsonObject.keys
        assertTrue("The reviewed training corpus must not be empty", approvedIds.isNotEmpty())
        val exportedIds = mutableSetOf<String>()
        val rows = mutableListOf<JsonObject>()
        val rejected = mutableListOf<JsonObject>()
        fun add(id: String, body: String, family: String) {
            if (id !in approvedIds) return
            assertTrue("Duplicate approved fixture: $id", exportedIds.add(id))
            assertNotEquals("Approved fixture needs an explicit human family: $id", "UNKNOWN", family)
            val cleaned = LabFamilyAssist.clean(body)
            val features = cleaned.features
            if (features == null) {
                rejected += buildJsonObject {put("id",id);put("family",family);put("reason",cleaned.rejection!!.name)}
                return
            }
            var requested = false
            val outcomes = mutableMapOf<String,JsonObject>()
            for(candidate in com.baraa.masroof.lab.ml.family.FamilyModel.FINANCIAL.sortedBy { it.name }) {
                val engine = RuleInterpreter(AlJaziraRuleProfile.current(), FamilyAssist { _, _ ->
                    requested = true
                    FamilySuggestion(com.baraa.masroof.lab.bank.MessageFamily.valueOf(candidate.name), 1.0, "evaluation", FamilyOrigin.ML)
                })
                val adapter = NewEngineSmsAdapter { engine.interpretRouted(it, BankId("BANK_ALJAZIRA")) }
                val route = BankSmsRegistry(listOf(adapter)).route("AlJazira", body)
                val received = Instant.parse("2026-10-06T09:00:00Z")
                val parse = (route as? BankRoutingResult.Matched)?.adapter?.parse(SmsParseInput(id,"AlJazira",body,received))
                val success = parse as? ParseResult.Success
                val assembled = success?.let { result ->
                    fun ownership(bank: com.baraa.masroof.domain.model.Bank?) = if(bank == com.baraa.masroof.domain.model.Bank.BANK_ALJAZIRA)
                        com.baraa.masroof.domain.model.OwnershipStatus.OWNED else com.baraa.masroof.domain.model.OwnershipStatus.EXTERNAL
                    com.baraa.masroof.domain.assembly.TransactionAssembler.assembleSingle(result.event,received,
                        ownership(result.event.sourceAccountRef?.bank),ownership(result.event.destinationAccountRef?.bank),
                        com.baraa.masroof.domain.model.OwnershipStatus.OWNED,com.baraa.masroof.domain.model.OwnershipStatus.OWNED,result.details.loanType)
                }
                outcomes[candidate.name] = buildJsonObject {
                    put("validationAccepted", success != null)
                    put("assembly", assembled?.javaClass?.simpleName ?: "NOT_ELIGIBLE")
                    (assembled as? com.baraa.masroof.domain.assembly.TransactionAssembler.Outcome.Assembled)?.let {
                        put("financialType",it.transaction.type.name)
                        put("amount",it.transaction.amount.amount.toPlainString())
                    }
                }
            }
            rows += buildJsonObject {
                put("runtimeEligible", requested)
                put("candidateOutcomes",JsonObject(outcomes))
                put("expectedPassesValidation",outcomes[family]?.get("validationAccepted")?.jsonPrimitive?.booleanOrNull ?: false)
                put("id",id);put("family",family);put("title",features.title);put("template",features.template)
                put("features",buildJsonObject { features.terms.forEach { (key,value) -> put(key,value) } })
            }
        }
        (canonical.walkTopDown()+reported.walkTopDown()).filter {it.extension=="json" && !it.path.contains("/supported/") && !it.path.contains("/device-reported/")}.sortedBy {it.path}.forEach { file ->
            val json = Json.parseToJsonElement(file.readText()).jsonObject
            val expected = json["expected"]?.jsonObject
            if (expected!=null) add(json.getValue("id").jsonPrimitive.content,json.getValue("body").jsonPrimitive.content,expected.getValue("messageFamily").jsonPrimitive.content)
        }
        val reference = Json.parseToJsonElement(File(canonical,"reference/jazira_sms_bodies.json").readText()).jsonObject.getValue("messages").jsonArray
        val families = listOf("TRANSFER_OUT","TRANSFER_IN","TRANSFER_IN","PURCHASE","PURCHASE","TRANSFER_OUT","PURCHASE","TRANSFER_OUT","TRANSFER_IN","FINANCING_INSTALLMENT")
        reference.forEachIndexed { index,element -> val row=element.jsonObject; add(row.getValue("id").jsonPrimitive.content,row.getValue("body").jsonPrimitive.content,families[index]) }
        Json.parseToJsonElement(File("src/test/resources/testdata/learning/family-runtime.json").readText()).jsonObject
            .getValue("cases").jsonArray.forEach { element -> val row = element.jsonObject
                add(row.getValue("id").jsonPrimitive.content,row.getValue("body").jsonPrimitive.content,row.getValue("family").jsonPrimitive.content)
            }
        System.getenv("MASROOF_REVIEWED_CORPUS")?.let { path ->
            Json.parseToJsonElement(File(path).readText()).jsonObject.getValue("cases").jsonArray.forEach { element ->
                val row = element.jsonObject
                add(row.getValue("id").jsonPrimitive.content, row.getValue("body").jsonPrimitive.content, row.getValue("family").jsonPrimitive.content)
            }
        }
        val output = buildJsonObject { put("cleaningVersion",FamilyInputCleaner.VERSION);put("featuresVersion",FamilyInputCleaner.FEATURES_VERSION);put("bankId","BANK_ALJAZIRA");put("rows",JsonArray(rows.sortedBy {it.getValue("id").jsonPrimitive.content}));put("rejected",JsonArray(rejected)) }
        File(System.getenv("MASROOF_FEATURE_EXPORT") ?: "build/model-training/features.json").apply {parentFile!!.mkdirs();writeText(output.toString())}
        assertEquals("Every reviewed training fixture must still exist", approvedIds, exportedIds)
        assertEquals(approvedIds.size, rows.size + rejected.size)
    }
}
