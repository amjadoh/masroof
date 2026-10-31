package com.baraa.masroof.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.application.locale.AppDigits
import com.baraa.masroof.ui.components.MasroofCoachMark
import com.baraa.masroof.ui.components.MasroofCoachMarkTargets
import com.baraa.masroof.ui.components.MasroofCoachMarkTour

/** First-run tour of Home. Shown once after setup; About can bring it back. */
internal object HomeTour {
    const val SUMMARY = "tour-summary"
    const val PERIOD = "tour-period"

    /** Device tests drive Home directly, so their runner turns the automatic tour off. */
    @Volatile var autoStart = true

    fun tabId(destination: HomeDestination): String? = when (destination) {
        HomeDestination.AllTransactions -> "tour-transactions"
        HomeDestination.Review -> "tour-review"
        HomeDestination.Settings -> "tour-settings"
        else -> null
    }
}

@Composable
internal fun HomeTour(targets: MasroofCoachMarkTargets, onFinish: () -> Unit) {
    val steps = listOf(
        HomeTour.SUMMARY to (R.string.tour_summary_title to R.string.tour_summary_body),
        HomeTour.PERIOD to (R.string.tour_period_title to R.string.tour_period_body),
        HomeTour.tabId(HomeDestination.AllTransactions) to (R.string.tour_transactions_title to R.string.tour_transactions_body),
        HomeTour.tabId(HomeDestination.Review) to (R.string.tour_review_title to R.string.tour_review_body),
        HomeTour.tabId(HomeDestination.Settings) to (R.string.tour_settings_title to R.string.tour_settings_body),
    ).map { (id, text) -> MasroofCoachMark(requireNotNull(id), stringResource(text.first), stringResource(text.second)) }
    val progress = stringResource(R.string.tour_progress)
    MasroofCoachMarkTour(
        targets = targets,
        steps = steps,
        nextLabel = stringResource(R.string.tour_next),
        doneLabel = stringResource(R.string.tour_done),
        skipLabel = stringResource(R.string.tour_skip),
        progressLabel = { current, total -> AppDigits.display(progress.format(current, total)) },
        onFinish = onFinish,
    )
}
