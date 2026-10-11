package com.baraa.masroof.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import com.baraa.masroof.presentation.common.MasroofText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.presentation.theme.MasroofSpacing
import com.baraa.masroof.presentation.common.MasroofTextStyles

object SettingsSpacing {
    val sectionGap = MasroofSpacing.sectionGap
    val groupGap = MasroofSpacing.listItemGap
}

@Composable
fun SettingsGroupTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        title,
        modifier = modifier,
        style = MasroofTextStyles.sectionTitle,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
fun SettingsScreenHeader(
    bank: Bank,
    hint: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        com.baraa.masroof.presentation.common.MasroofGuidanceLabel(settingsBankLabel(bank), hint,
            style = MasroofTextStyles.screenLabel, contentColor = MaterialTheme.colorScheme.primary)
    }
}
