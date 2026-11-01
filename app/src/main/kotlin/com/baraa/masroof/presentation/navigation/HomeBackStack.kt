package com.baraa.masroof.presentation.navigation

/**
 * Home-level back stack, encoded as `"<id>:<HomeDestination>"` so it fits `rememberSaveable`.
 * The id is unique per visit and keys that visit's saved screen state.
 */
internal data class HomeBackStackEntry(val id: Int, val destination: HomeDestination)

internal fun homeRootStack(): List<String> = listOf(encodeHome(0, HomeDestination.Dashboard))

internal fun homeEntries(stack: List<String>): List<HomeBackStackEntry> = stack.mapNotNull { raw ->
    val id = raw.substringBefore(':').toIntOrNull() ?: return@mapNotNull null
    val destination = HomeDestination.entries.firstOrNull { it.name == raw.substringAfter(':') } ?: return@mapNotNull null
    HomeBackStackEntry(id, destination)
}

/** Opens [destination] on top; a repeated tap on the current screen changes nothing. */
internal fun pushHome(stack: List<String>, destination: HomeDestination): List<String> =
    if (homeEntries(stack).lastOrNull()?.destination == destination) stack else stack + encodeHome(nextHomeId(stack), destination)

/** `null` means the stack is at its root and back should be consumed. */
internal fun popHome(stack: List<String>): List<String>? = if (stack.size <= 1) null else stack.dropLast(1)

/** Dashboard clears history; the current tab restarts fresh; another tab is pushed like any link. */
internal fun switchHomeTab(stack: List<String>, tab: HomeDestination): List<String> = when {
    tab == HomeDestination.Dashboard -> stack.take(1)
    homeEntries(stack).lastOrNull()?.destination == tab -> stack.dropLast(1) + encodeHome(nextHomeId(stack), tab)
    else -> stack + encodeHome(nextHomeId(stack), tab)
}

private fun encodeHome(id: Int, destination: HomeDestination) = "$id:${destination.name}"

private fun nextHomeId(stack: List<String>) = (homeEntries(stack).maxOfOrNull { it.id } ?: -1) + 1
