package com.baraa.masroof.presentation.navigation

import androidx.compose.runtime.saveable.listSaver

/** One visit to a home screen. The id is unique per visit and keys that visit's saved screen state. */
internal data class HomeBackStackEntry(val id: Int, val destination: HomeDestination)

internal typealias HomeBackStack = List<HomeBackStackEntry>

private val homeRoot = HomeBackStackEntry(0, HomeDestination.Dashboard)

internal fun homeRootStack(): HomeBackStack = listOf(homeRoot)

/** The visible visit; the stack is never empty in practice, the fallback only guards a corrupt restore. */
internal fun HomeBackStack.top(): HomeBackStackEntry = lastOrNull() ?: homeRoot

/** Saved as `"<id>:<HomeDestination>"`; entries naming a removed screen are dropped on restore. */
internal val HomeBackStackSaver = listSaver<HomeBackStack, String>(
    save = { stack -> stack.map { "${it.id}:${it.destination.name}" } },
    restore = { saved ->
        saved.mapNotNull { raw ->
            val id = raw.substringBefore(':').toIntOrNull() ?: return@mapNotNull null
            HomeDestination.entries.firstOrNull { it.name == raw.substringAfter(':') }?.let { HomeBackStackEntry(id, it) }
        }.ifEmpty { homeRootStack() }
    },
)

/** Opens [destination] on top; a repeated tap on the current screen changes nothing. */
internal fun pushHome(stack: HomeBackStack, destination: HomeDestination): HomeBackStack =
    if (stack.top().destination == destination) stack else withVisit(stack, destination)

/** `null` means the stack is at its root and back should be consumed. */
internal fun popHome(stack: HomeBackStack): HomeBackStack? = if (stack.size <= 1) null else stack.dropLast(1)

/** Home clears history and reopens a fresh dashboard; the current tab restarts fresh; another tab is pushed like any link. */
internal fun switchHomeTab(stack: HomeBackStack, tab: HomeDestination): HomeBackStack = when {
    tab == HomeDestination.Dashboard -> listOf(HomeBackStackEntry(nextHomeId(stack), tab))
    stack.top().destination == tab -> stack.dropLast(1) + HomeBackStackEntry(nextHomeId(stack), tab)
    else -> withVisit(stack, tab)
}

/** Saved state for every visit lives in one bundle with a size limit, so only the newest visits are kept behind the root. */
internal const val MAX_HOME_VISITS = 30

private fun withVisit(stack: HomeBackStack, destination: HomeDestination): HomeBackStack {
    val next = stack + HomeBackStackEntry(nextHomeId(stack), destination)
    return if (next.size <= MAX_HOME_VISITS) next else listOf(next.first()) + next.takeLast(MAX_HOME_VISITS - 1)
}

private fun nextHomeId(stack: HomeBackStack) = (stack.maxOfOrNull { it.id } ?: -1) + 1
