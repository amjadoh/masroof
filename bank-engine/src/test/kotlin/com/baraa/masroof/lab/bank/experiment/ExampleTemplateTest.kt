package com.baraa.masroof.lab.bank.experiment

import org.junit.Assert.*
import org.junit.Test

class ExampleTemplateTest {
    @Test fun oneAnnotatedExampleExtractsNewValuesRatherThanCopyingTheTeacher() {
        val body = "Internal incoming transfer\nAmount:123.45SR\nFrom:EXAMPLE PERSON\nAcc:007\nAt:14/10/26 12:21"
        fun select(field: String, kind: FieldKind, value: String): Selection {
            val start = body.indexOf(value)
            return Selection(field, kind, start, start + value.length)
        }
        val template = ExampleTemplate.learn("STC Bank", "TRANSFER_IN", body, listOf(
            select("amount", FieldKind.MONEY, "123.45SR"),
            select("counterparty", FieldKind.TEXT, "EXAMPLE PERSON"),
            select("account", FieldKind.DIGITS, "007"),
            select("time", FieldKind.DATE_DMY_SHORT, "14/10/26 12:21"),
        ))
        val changed = "Internal incoming transfer\nAmount:96SR\nFrom:ANOTHER PERSON\nAcc:009\nAt:15/10/26 09:05"
        val result = requireNotNull(template.preview("STC Bank", changed))
        assertEquals("96SR", result.fields.getValue("amount").raw)
        assertEquals("ANOTHER PERSON", result.fields.getValue("counterparty").raw)
        assertEquals("009", result.fields.getValue("account").raw)
        assertEquals("15/10/26 09:05", result.fields.getValue("time").raw)
        assertFalse(result.financialPostingAllowed)
        result.fields.values.forEach { assertEquals(it.raw, changed.substring(it.start, it.end)) }
    }
}
