package com.baraa.masroof.lab.finance

import com.baraa.masroof.lab.finance.value.*
import org.junit.Assert.*
import org.junit.Test

class ValueContractTest {
    @Test fun amountsAreNonNegativeRoundedMagnitudesAndNumericEqualityHasConsistentHash() {
        assertEquals(Money.of("1.005",Currency.SAR),Money.of("1.00",Currency.SAR))
        assertEquals(Money.of("1",Currency.SAR).hashCode(),Money.of("1.00",Currency.SAR).hashCode())
        assertNotEquals(Money.of("1",Currency.SAR),Money.of("1",Currency.USD))
        assertThrows(IllegalArgumentException::class.java) {Money.of("-1",Currency.SAR)}
    }
    @Test fun unknownRoutingCannotPretendToBeABank() {
        assertThrows(IllegalArgumentException::class.java) {BankId("UNKNOWN")}
        assertThrows(IllegalArgumentException::class.java) {BankId(" ")}
    }
}
