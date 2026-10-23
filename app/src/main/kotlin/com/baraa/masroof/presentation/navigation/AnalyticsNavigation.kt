package com.baraa.masroof.presentation.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object AnalyticsNavigation {
    private val mutable = MutableStateFlow<String?>(null)
    val target = mutable.asStateFlow()
    private val visible = MutableStateFlow(false)
    val analysisVisible = visible.asStateFlow()
    fun setAnalysisVisible(value: Boolean) { visible.value = value }
    private var returnToCenter = false
    private val returns = MutableStateFlow(0L)
    val centerReturns = returns.asStateFlow()
    fun open(alert: String = "", fromCenter: Boolean = false) { returnToCenter = fromCenter; mutable.value = alert }
    /** Consume the destination without losing its origin while displaying details. */
    fun clear() { mutable.value = null }
    fun reset() { clear(); returnToCenter = false; visible.value = false }
    fun finish() {
        clear()
        if (returnToCenter) returns.value += 1
        returnToCenter = false
    }
}

