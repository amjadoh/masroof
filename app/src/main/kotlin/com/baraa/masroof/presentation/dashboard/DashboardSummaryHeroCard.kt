package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import com.baraa.masroof.presentation.common.MasroofHeroCard
import com.baraa.masroof.presentation.common.MasroofDetailRow
import com.baraa.masroof.presentation.theme.MasroofHeroRole
import com.baraa.masroof.presentation.theme.MasroofSpacing

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofCardAccent

data class DashboardSummaryHeroSpec(
    val accent: MasroofCardAccent,
    val primary: DashboardSummaryMetricItem,
    val secondary: List<DashboardSummaryMetricItem> = emptyList(),
    val footerHint: String? = null,
)

@Composable
fun DashboardSummaryHeroCard(spec: DashboardSummaryHeroSpec, modifier: Modifier = Modifier) {
    val role = when (spec.accent) {
        MasroofCardAccent.Account -> MasroofHeroRole.Account
        MasroofCardAccent.Credit -> MasroofHeroRole.Card
        MasroofCardAccent.Liability -> MasroofHeroRole.Liability
        MasroofCardAccent.Inflow -> MasroofHeroRole.Inflow
        MasroofCardAccent.Outflow -> MasroofHeroRole.Outflow
        MasroofCardAccent.None -> MasroofHeroRole.Brand
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
        MasroofHeroCard(spec.primary.title, spec.primary.amount, spec.primary.hint ?: spec.footerHint, role)
        if (spec.secondary.isNotEmpty()) {
            MasroofCard { spec.secondary.forEach { MasroofDetailRow(it.title, it.amount) } }
        }
    }
}
