package com.baraa.masroof.engine.bank

import com.baraa.masroof.engine.bank.interpretation.RuleInterpreter
import com.baraa.masroof.engine.bank.rules.AlJaziraRuleProfile
import com.baraa.masroof.engine.bank.rules.RuleLibrary
import org.junit.Assert.*
import org.junit.Test

class RuleSchemaTest {
    private val rule = BankRule("purchase", MessageFamily.PURCHASE, listOf("purchase"))

    @Test fun profileRefactoringPreservesRefreshIdentities() {
        // Collection order participates in the content hash used by production refresh scheduling.
        assertEquals("literal-v1-r1-ad3adfef15b3a6da60f97cb1bcc580356ed01429745b41989ad2abee177d45e5",
            RuleInterpreter(RuleLibrary.initial()).version.rules)
        assertEquals("literal-v1-r13-44c60f975c10bf3e6ec0e436c2a0b1940fcb2c2d3be0ee85dbc4c82f7953e55b",
            RuleInterpreter(RuleLibrary.current()).version.rules)
        assertEquals("literal-v1-r13-9954b0694e5b0dc7fe60db2ee7485bbee818b2a7b1b8ab443203261e72396947",
            RuleInterpreter(AlJaziraRuleProfile.current()).version.rules)
    }

    @Test fun closedLiteralSchemaRejectsUnknownVersionsAndUnsafeShape() {
        BankRuleSchema(revision = 1, rules = listOf(rule)).validate()
        listOf(BankRuleSchema(schema = 2, revision = 1, rules = listOf(rule)),
            BankRuleSchema(revision = 0, rules = listOf(rule)),
            BankRuleSchema(revision = 1, rules = listOf(rule, rule)),
            BankRuleSchema(revision = 1, rules = listOf(rule.copy(family = MessageFamily.OTP))),
            BankRuleSchema(revision = 1, rules = listOf(rule.copy(phrases = listOf("\n")))),
            BankRuleSchema(revision = 1, rules = listOf(rule.copy(detailLabels = mapOf("script" to listOf("execute"))))),
            BankRuleSchema(revision = 1, rules = listOf(rule.copy(observations = mapOf("ownership.mine" to listOf("owned"))))),
            BankRuleSchema(revision = 1, rules = listOf(rule.copy(observations = mapOf("card.credit" to listOf("x".repeat(101)))))),
            BankRuleSchema(revision = 1, rules = listOf(rule.copy(direction = MoneyDirection.NEUTRAL))))
            .forEach { invalid -> assertThrows(IllegalArgumentException::class.java) { invalid.validate() } }
        assertEquals(listOf("CONTAINS_TOKEN_PHRASE"), RuleOperator.entries.map { it.name })
    }

    @Test fun networkDataCannotAddUnboundedOrExecutableRoutingOperators() {
        val invalid = listOf(NetworkRules(bankAliases = mapOf("bad id" to listOf("Bank"))),
            NetworkRules(bankAliases = mapOf("LOCAL" to emptyList())),
            NetworkRules(sourceBankLabels = listOf("sender\nbank")),
            NetworkRules(bankAliases = mapOf("LOCAL" to listOf("x".repeat(101)))))
        invalid.forEach { assertThrows(IllegalArgumentException::class.java) { BankRuleSchema(revision = 1, rules = listOf(rule), network = it).validate() } }
        BankRuleSchema(revision = 1, rules = listOf(rule), network = NetworkRules(bankAliases = mapOf("LOCAL" to listOf("(a+)+$")))).validate()
    }
}
