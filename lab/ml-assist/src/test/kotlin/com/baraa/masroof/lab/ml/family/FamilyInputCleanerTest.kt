package com.baraa.masroof.lab.ml.family

import org.junit.Assert.*
import org.junit.Test

class FamilyInputCleanerTest {
    private fun clean(title: String, fields: List<String> = emptyList()) =
        requireNotNull(FamilyInputCleaner.clean(title, fields).features)

    @Test fun dynamicValuesAndUnreviewedFieldNamesNeverBecomeFeatures() {
        val first = clean("purchase 60.99 SAR SA123456789 BTMS987654 https://private.example +966555123456",
            listOf("amount", "account", "Sarah", "merchant@example.test"))
        val second = clean("purchase 20.01 SAR SA987654321 BTMS123456 https://another.example +966500654321",
            listOf("account", "amount", "Khalid"))
        assertEquals(first.terms, second.terms)
        assertEquals(first.template, second.template)
        assertFalse(first.terms.keys.any {it.contains("sarah") || it.contains("private") || it.any(Char::isDigit) && !it.startsWith("c")})
    }

    @Test fun whitespaceAndInvisibleFormattingDoNotFragmentTemplates() {
        val a = clean("حوالة محلية واردة", listOf("من", "مبلغ العملية"))
        val b = clean("  حوالة\u200f\u00a0محلية\tواردة  ", listOf("مبلغ\tالعملية", "من", "من"))
        assertEquals(a.template, b.template)
        assertEquals(a.terms, b.terms)
    }

    @Test fun securityAndNegationRemainMeaningfulFeatures() {
        val purchase = clean("online purchase")
        val otp = clean("one time password for online purchase: 482913")
        val declined = clean("online purchase declined")
        val request = clean("طلب حوالة محلية واردة")
        assertTrue(otp.terms.containsKey("w:password"))
        assertTrue(declined.terms.containsKey("w:declined"))
        assertTrue(request.terms.containsKey("w:طلب"))
        assertNotEquals(purchase.template, otp.template)
        assertNotEquals(purchase.template, declined.template)
        assertNotEquals(clean("حوالة محلية واردة").template, request.template)
    }

    @Test fun explicitPersonalHeadingKeepsOnlyTheHeading() {
        assertEquals(clean("اسم المستفيد: Sarah Example").template, clean("اسم المستفيد: شخص آخر").template)
        assertFalse(clean("اسم المستفيد: Sarah Example").terms.keys.any {it.contains("Sarah", ignoreCase=true)})
    }

    @Test fun unfamiliarOrOversizedTitleAbstainsRatherThanDroppingUnknownWords() {
        assertEquals(CleaningRejection.UNRECOGNIZED_TITLE, FamilyInputCleaner.clean("شراء محمد", emptyList()).rejection)
        assertEquals(CleaningRejection.EMPTY, FamilyInputCleaner.clean("123456", emptyList()).rejection)
        assertEquals(CleaningRejection.TOO_LARGE, FamilyInputCleaner.clean("purchase ".repeat(100), emptyList()).rejection)
        assertEquals(CleaningRejection.UNRECOGNIZED_TITLE, FamilyInputCleaner.clean("purchase hypotheticalnegation", emptyList()).rejection)
    }
}
