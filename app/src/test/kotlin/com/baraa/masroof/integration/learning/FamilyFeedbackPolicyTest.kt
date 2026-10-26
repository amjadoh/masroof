package com.baraa.masroof.integration.learning

import com.baraa.masroof.application.interpretation.FamilyFeedbackPolicy
import com.baraa.masroof.domain.model.FinancialTransactionType.*
import com.baraa.masroof.domain.model.MessageFamily.*
import org.junit.Assert.*
import org.junit.Test

class FamilyFeedbackPolicyTest {
    @Test fun broadFinancialTypesNeverInventFamilyLabels() {
        assertNull(FamilyFeedbackPolicy.explicitFamily(EXPENSE))
        assertNull(FamilyFeedbackPolicy.explicitFamily(INCOME))
        assertNull(FamilyFeedbackPolicy.explicitFamily(SELF_TRANSFER))
        assertEquals(TRANSFER_IN,FamilyFeedbackPolicy.explicitFamily(EXTERNAL_TRANSFER_IN))
    }
    @Test fun ambiguityCanStillDisproveAnEarlierFamily() {
        assertTrue(FamilyFeedbackPolicy.contradicts(TRANSFER_IN,EXPENSE))
        assertFalse(FamilyFeedbackPolicy.contradicts(PURCHASE,EXPENSE))
        assertFalse(FamilyFeedbackPolicy.contradicts(TRANSFER_IN,INCOME))
        assertFalse(FamilyFeedbackPolicy.contradicts(com.baraa.masroof.domain.model.MessageFamily.UNKNOWN,EXPENSE))
    }
}
