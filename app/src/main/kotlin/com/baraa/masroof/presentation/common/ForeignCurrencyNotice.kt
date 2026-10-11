package com.baraa.masroof.presentation.common

import com.baraa.masroof.presentation.theme.MasroofSpacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import com.baraa.masroof.presentation.common.MasroofText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.presentation.theme.MasroofThemeExtras

@Composable
fun ForeignCurrencyNotice(
    excludedCount: Int,
    modifier: Modifier = Modifier,
) {
    val extended = MasroofThemeExtras.extendedColors
    MasroofCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MasroofSpacing.smallGap)) {
            IconLabelRow(
                icon = MasroofIcons.periodHint,
                label = stringResource(R.string.dashboard_excluded_other_currency_title),
                iconTint = extended.liability,
            )
            Text(
                stringResource(R.string.dashboard_excluded_other_currency, excludedCount),
                style = MaterialTheme.typography.bodyMedium,
            )
            com.baraa.masroof.presentation.common.MasroofGuidanceLabel(label = stringResource(R.string.guidance_title), message = stringResource(R.string.dashboard_excluded_other_currency_hint))
        }
    }
}
