package com.baraa.masroof.bank.patterns

import com.baraa.masroof.data.patterns.BankRuleProfileCodec
import com.baraa.masroof.lab.bank.interpretation.RuleInterpreter
import com.baraa.masroof.lab.bank.rules.AlJaziraRuleProfile
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class BankRuleProfileCodecTest {
    @Test fun bundledProfileKeepsEveryLiteralRoleAndRefreshIdentity() {
        val original = AlJaziraRuleProfile.current()
        val encoded = BankRuleProfileCodec.encode(original)
        File("build/reports/pattern-parity/aljazira-rules.json").apply { parentFile.mkdirs(); writeText(encoded) }
        assertEquals(original, BankRuleProfileCodec.decode(encoded))
        val bundled = File("src/main/assets/bank-patterns/BANK_ALJAZIRA.rules.json")
        assertTrue("Bundled profile missing; original profile exported under build/reports/pattern-parity", bundled.isFile)
        val loaded = BankRuleProfileCodec.decode(bundled.readText())
        assertEquals(original, loaded)
        assertEquals(RuleInterpreter(original).version, RuleInterpreter(loaded).version)
    }

    @Test fun closedSchemaRejectsUnknownCapabilitiesVersionsAndOversizedInput() {
        val valid = Json.parseToJsonElement(BankRuleProfileCodec.encode(AlJaziraRuleProfile.current())).jsonObject
        listOf(JsonObject(valid + ("schema" to JsonPrimitive(99))),
            JsonObject(valid + ("regex" to JsonPrimitive("execute"))),
            JsonObject(valid + ("rules" to buildJsonArray {
                add(JsonObject(valid.getValue("rules").jsonArray.first().jsonObject + ("operator" to JsonPrimitive("REGEX"))))
            }))).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { BankRuleProfileCodec.decode(invalid.toString()) }
        }
        assertThrows(IllegalArgumentException::class.java) { BankRuleProfileCodec.decode(" ".repeat(262_145)) }
    }
}
