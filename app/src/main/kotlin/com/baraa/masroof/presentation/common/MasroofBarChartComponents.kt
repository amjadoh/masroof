package com.baraa.masroof.presentation.common
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import java.math.BigDecimal
enum class MasroofHorizontalBarStyle { Default, Inflow, Outflow }
object MasroofBarChart {
    fun progress(value: BigDecimal, max: BigDecimal) = com.baraa.masroof.ui.components.MasroofBarChart.progress(value, max)
}
@Composable
fun MasroofHorizontalBar(progress: Float, modifier: Modifier = Modifier, style: MasroofHorizontalBarStyle = MasroofHorizontalBarStyle.Default) =
    com.baraa.masroof.ui.components.MasroofHorizontalBar(progress, modifier, com.baraa.masroof.ui.components.MasroofHorizontalBarStyle.valueOf(style.name))
@Composable
fun MasroofRankedBarRow(title: String, value: String, progress: Float, modifier: Modifier = Modifier,
    subtitle: String? = null, style: MasroofHorizontalBarStyle = MasroofHorizontalBarStyle.Default, onClick: (() -> Unit)? = null) =
    com.baraa.masroof.ui.components.MasroofRankedBarRow(com.baraa.masroof.application.locale.AppDigits.display(title),
        com.baraa.masroof.application.locale.AppDigits.display(value), progress, modifier,
        subtitle?.let(com.baraa.masroof.application.locale.AppDigits::display),
        com.baraa.masroof.ui.components.MasroofHorizontalBarStyle.valueOf(style.name), onClick)
