package com.baraa.masroof.application.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardLayoutPreferencesTest {
    @Test
    fun withMergedSections_preservesCustomOrderWhenComplete() {
        val custom = DashboardLayoutSnapshot(
            sections = listOf(
                entry(DashboardSectionId.TRANSACTIONS),
                entry(DashboardSectionId.HERO),
                entry(DashboardSectionId.CARDS),
                entry(DashboardSectionId.LOANS),
                entry(DashboardSectionId.COMMITMENTS),
                entry(DashboardSectionId.MERCHANTS),
                entry(DashboardSectionId.ACCOUNTS),
                entry(DashboardSectionId.QUICK),
            ),
        )

        val merged = custom.withMergedSections()

        assertEquals(custom, merged)
        assertEquals(
            listOf(
                DashboardSectionId.TRANSACTIONS,
                DashboardSectionId.HERO,
                DashboardSectionId.CARDS,
                DashboardSectionId.LOANS,
                DashboardSectionId.COMMITMENTS,
                DashboardSectionId.MERCHANTS,
                DashboardSectionId.ACCOUNTS,
                DashboardSectionId.QUICK,
            ),
            merged.sections.map { it.id },
        )
    }

    @Test
    fun withMergedSections_insertsLoansWithoutReorderingExistingSections() {
        val legacy = DashboardLayoutSnapshot(
            sections = listOf(
                entry(DashboardSectionId.HERO),
                entry(DashboardSectionId.TRANSACTIONS),
                entry(DashboardSectionId.CARDS),
                entry(DashboardSectionId.ACCOUNTS),
                entry(DashboardSectionId.QUICK),
            ),
        )

        val merged = legacy.withMergedSections()

        val previousIds = legacy.sections.map { it.id }
        assertEquals(previousIds, merged.sections.filter { it.id in previousIds }.map { it.id })
        assertTrue(merged.sections.map { it.id }.containsAll(DashboardLayoutSnapshot.default().sections.map { it.id }))
        assertTrue(merged.sections.indexOfFirst { it.id == DashboardSectionId.LOANS } < merged.sections.indexOfFirst { it.id == DashboardSectionId.TRANSACTIONS })

    }

    @Test
    fun withMergedSections_insertsLoansBeforeTransactionsInDefaultLayout() {
        val preLoansDefaultOrder = DashboardLayoutSnapshot(
            sections = listOf(
                entry(DashboardSectionId.HERO, DashboardSectionSize.LARGE),
                entry(DashboardSectionId.QUICK),
                entry(DashboardSectionId.ACCOUNTS),
                entry(DashboardSectionId.CARDS, DashboardSectionSize.LARGE),
                entry(DashboardSectionId.TRANSACTIONS),
            ),
        )

        val merged = preLoansDefaultOrder.withMergedSections()

        assertEquals(DashboardLayoutSnapshot.default().sections.map { it.id }, merged.sections.map { it.id })
        assertEquals(DashboardSectionSize.MEDIUM, merged.entry(DashboardSectionId.LOANS)!!.size)
        assertTrue(merged.entry(DashboardSectionId.LOANS)!!.visible)
    }

    @Test
    fun defaultLayout_placesShortcutsAndCommitmentsBeforeMetrics() {
        val defaults = DashboardLayoutSnapshot.default()

        val ids = defaults.sections.map { it.id }
        assertTrue(DashboardSectionId.LOANS in ids)
        assertTrue(DashboardSectionId.COMMITMENTS in ids)
        assertTrue(DashboardSectionId.MERCHANTS in ids)
        assertTrue(ids.indexOf(DashboardSectionId.LOANS) > ids.indexOf(DashboardSectionId.CARDS))
        assertTrue(ids.indexOf(DashboardSectionId.ACCOUNTS) < ids.indexOf(DashboardSectionId.COMMITMENTS))
        assertTrue(ids.indexOf(DashboardSectionId.COMMITMENTS) < ids.indexOf(DashboardSectionId.QUICK))
        assertTrue(ids.indexOf(DashboardSectionId.MERCHANTS) > ids.indexOf(DashboardSectionId.COMMITMENTS))
        assertTrue(ids.indexOf(DashboardSectionId.MERCHANTS) < ids.indexOf(DashboardSectionId.TRANSACTIONS))
    }

    private fun entry(
        id: DashboardSectionId,
        size: DashboardSectionSize = DashboardSectionSize.MEDIUM,
    ): DashboardSectionEntry =
        DashboardSectionEntry(id = id, visible = true, size = size)
}
