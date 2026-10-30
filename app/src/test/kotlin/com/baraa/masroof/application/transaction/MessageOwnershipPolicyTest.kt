package com.baraa.masroof.application.transaction

import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.OwnershipStatus.*
import org.junit.Assert.*
import org.junit.Test

class MessageOwnershipPolicyTest {
    @Test fun anotherHoldersLocalAccountOrCardCannotCreateUserActivity() {
        for (family in listOf(MessageFamily.PURCHASE, MessageFamily.REFUND, MessageFamily.FEE,
                MessageFamily.BILL_PAYMENT, MessageFamily.WITHDRAWAL)) {
            assertNotNull(MessageOwnershipPolicy.reviewReason(family, UNKNOWN, UNKNOWN, EXTERNAL, true))
        }
        assertNotNull(MessageOwnershipPolicy.reviewReason(MessageFamily.TRANSFER_IN, OWNED, EXTERNAL, UNKNOWN, false))
        assertNotNull(MessageOwnershipPolicy.reviewReason(MessageFamily.TRANSFER_OUT, EXTERNAL, OWNED, UNKNOWN, false))
        assertNull(MessageOwnershipPolicy.reviewReason(MessageFamily.TRANSFER_OUT, OWNED, EXTERNAL, UNKNOWN, false))
        assertNull(MessageOwnershipPolicy.reviewReason(MessageFamily.TRANSFER_IN, EXTERNAL, OWNED, UNKNOWN, false))
    }

    @Test fun unknownCardOnlyFundingWaitsForOwnershipWithoutRejectingKnownOwnedFunding() {
        assertNotNull(MessageOwnershipPolicy.reviewReason(MessageFamily.PURCHASE, UNKNOWN, UNKNOWN, UNKNOWN, true))
        assertNotNull(MessageOwnershipPolicy.reviewReason(MessageFamily.REFUND, UNKNOWN, UNKNOWN, UNKNOWN, true))
        assertNotNull(MessageOwnershipPolicy.reviewReason(MessageFamily.WITHDRAWAL, OWNED, UNKNOWN, UNKNOWN, true))
        assertNull(MessageOwnershipPolicy.reviewReason(MessageFamily.PURCHASE, UNKNOWN, UNKNOWN, OWNED, true))
        assertNull(MessageOwnershipPolicy.reviewReason(MessageFamily.PURCHASE, OWNED, UNKNOWN, UNKNOWN, false))
        assertNull(MessageOwnershipPolicy.reviewReason(MessageFamily.REFUND, UNKNOWN, OWNED, UNKNOWN, false))
    }
}
