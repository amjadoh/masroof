package com.baraa.masroof.presentation.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DashboardRegistryLabelsTest {
    @Test
    fun customNameTrimsWhitespaceAndTreatsMissingNamesAsAbsent() {
        assertEquals("Home", DashboardRegistryLabels.customName("  Home  "))
        assertNull(DashboardRegistryLabels.customName("   "))
        assertNull(DashboardRegistryLabels.customName(null))
    }

    @Test
    fun accountFallbackUsesOnlyLastFourDigits() {
        assertEquals("3001", DashboardRegistryLabels.accountLast4("  ****12343001  "))
        assertEquals("3001", DashboardRegistryLabels.accountLast4("3001"))
    }
}
