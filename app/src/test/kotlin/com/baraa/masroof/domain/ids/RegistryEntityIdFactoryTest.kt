package com.baraa.masroof.domain.ids

import org.junit.Assert.assertEquals
import org.junit.Test

class RegistryEntityIdFactoryTest {
    @Test
    fun stableIds_keepTheirPersistedValue() {
        // Persisted registry ids: the first 16 bytes of SHA-256(bankId + U+001E + key), lowercase hex.
        assertEquals("creg_a3f7605683d66cef37dd1d83197699ce", RegistryEntityIdFactory.stableCardId("aljazira", " 1234 "))
    }
}
