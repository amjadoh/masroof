package com.baraa.masroof.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofReferenceNavigationRow
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.MasroofSecondaryScaffold
import com.baraa.masroof.presentation.common.MasroofText
import com.baraa.masroof.presentation.theme.MasroofSpacing

@Composable
fun SettingsDeveloperScreen(onBack: () -> Unit, onOpenLab: () -> Unit) {
    MasroofSecondaryScaffold(title = stringResource(R.string.settings_developer_title), onBack = onBack,
        backContentDescription = stringResource(R.string.settings_back)) { contentModifier ->
        Column(contentModifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
            MasroofCard {
                MasroofReferenceNavigationRow(stringResource(R.string.settings_parsing_lab_title),
                    stringResource(R.string.settings_parsing_lab_hint), MasroofIcons.periodHint, onOpenLab, divider = false)
            }
            MasroofText(stringResource(R.string.settings_parsing_lab_hint), style = MaterialTheme.typography.bodySmall)
        }
    }
}
