package com.baraa.masroof.lab.bank

import org.junit.Assert.*
import org.junit.Test

class RuleSchemaTest {
    private val rule = BankRule("purchase", MessageFamily.PURCHASE, listOf("purchase"))
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
}
