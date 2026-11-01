package com.baraa.masroof.presentation.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class HomeBackStackTest {
    private fun destinations(stack: List<String>) = homeEntries(stack).map { it.destination }

    @Test
    fun pushThenPop_returnsToPreviousScreen() {
        val settings = pushHome(homeRootStack(), HomeDestination.Settings)
        val notifications = pushHome(settings, HomeDestination.NotificationCenter)

        assertEquals(listOf(HomeDestination.Dashboard, HomeDestination.Settings, HomeDestination.NotificationCenter), destinations(notifications))
        assertEquals(settings, popHome(notifications))
        assertEquals(homeRootStack(), popHome(settings))
    }

    @Test
    fun pushSameScreenTwice_changesNothing() {
        val notifications = pushHome(homeRootStack(), HomeDestination.NotificationCenter)
        assertSame(notifications, pushHome(notifications, HomeDestination.NotificationCenter))
    }

    @Test
    fun popAtRoot_isConsumed() {
        assertNull(popHome(homeRootStack()))
    }

    @Test
    fun idsStayUniqueAcrossRevisits() {
        var stack = homeRootStack()
        repeat(3) {
            stack = pushHome(stack, HomeDestination.Settings)
            stack = pushHome(stack, HomeDestination.NotificationCenter)
        }
        val ids = homeEntries(stack).map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun otherTab_isPushedAndBackReturnsToPreviousScreen() {
        val settings = pushHome(homeRootStack(), HomeDestination.Settings)
        val review = switchHomeTab(settings, HomeDestination.Review)

        assertEquals(listOf(HomeDestination.Dashboard, HomeDestination.Settings, HomeDestination.Review), destinations(review))
        assertEquals(settings, popHome(review))
    }

    @Test
    fun currentTabAgain_restartsWithFreshVisit() {
        val settings = pushHome(homeRootStack(), HomeDestination.Settings)
        val again = switchHomeTab(settings, HomeDestination.Settings)

        assertEquals(destinations(settings), destinations(again))
        assertEquals(homeEntries(settings).last().id + 1, homeEntries(again).last().id)
    }

    @Test
    fun homeTab_clearsHistoryAndOpensFreshDashboard() {
        val deep = switchHomeTab(pushHome(pushHome(homeRootStack(), HomeDestination.Settings), HomeDestination.NotificationCenter), HomeDestination.Review)
        val home = homeEntries(switchHomeTab(deep, HomeDestination.Dashboard))

        assertEquals(listOf(HomeDestination.Dashboard), home.map { it.destination })
        // A new visit id drops the old dashboard's saved sub-pages (analytics, flow detail).
        assertEquals(homeEntries(deep).maxOf { it.id } + 1, home.single().id)
        assertEquals(1, homeEntries(switchHomeTab(homeRootStack(), HomeDestination.Dashboard)).single().id)
    }

    @Test
    fun longTabSwitching_keepsRootAndNewestVisitsOnly() {
        var stack = homeRootStack()
        repeat(100) { stack = switchHomeTab(stack, if (it % 2 == 0) HomeDestination.Settings else HomeDestination.Review) }
        val entries = homeEntries(stack)

        assertEquals(MAX_HOME_VISITS, entries.size)
        assertEquals(HomeBackStackEntry(0, HomeDestination.Dashboard), entries.first())
        assertEquals(HomeDestination.Review, entries.last().destination)
        assertEquals(100, entries.last().id)
    }

    @Test
    fun unknownEntry_isIgnored() {
        assertEquals(listOf(HomeDestination.Dashboard), destinations(homeRootStack() + "7:RemovedScreen" + "junk"))
    }
}
