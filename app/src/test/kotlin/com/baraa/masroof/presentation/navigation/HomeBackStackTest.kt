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
    fun homeTab_clearsHistoryButKeepsRootVisit() {
        val deep = switchHomeTab(pushHome(pushHome(homeRootStack(), HomeDestination.Settings), HomeDestination.NotificationCenter), HomeDestination.Review)
        assertEquals(homeRootStack(), switchHomeTab(deep, HomeDestination.Dashboard))
    }

    @Test
    fun unknownEntry_isIgnored() {
        assertEquals(listOf(HomeDestination.Dashboard), destinations(homeRootStack() + "7:RemovedScreen" + "junk"))
    }
}
