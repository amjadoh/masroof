package com.baraa.masroof.presentation.settings

import com.baraa.masroof.ui.theme.MasroofSpacing
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import com.baraa.masroof.presentation.common.MasroofText as Text
import com.baraa.masroof.presentation.common.MasroofTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.baraa.masroof.R
import com.baraa.masroof.application.locale.AppLocale

@Composable
fun SettingsLanguageDialog(
    selectedLanguageTag: String,
    onDismiss: () -> Unit,
    onSelectLanguage: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_language_title)) },
        text = {
            Column {
                languageOption(
                    tag = AppLocale.TAG_AR,
                    label = stringResource(R.string.settings_language_arabic),
                    selectedLanguageTag = selectedLanguageTag,
                    onSelectLanguage = onSelectLanguage,
                )
                languageOption(
                    tag = AppLocale.TAG_EN,
                    label = stringResource(R.string.settings_language_english),
                    selectedLanguageTag = selectedLanguageTag,
                    onSelectLanguage = onSelectLanguage,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}

@Composable
private fun languageOption(
    tag: String,
    label: String,
    selectedLanguageTag: String,
    onSelectLanguage: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = tag == selectedLanguageTag,
                onClick = { onSelectLanguage(tag) },
                role = Role.RadioButton,
            )
            .padding(vertical = MasroofSpacing.cardInnerGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = tag == selectedLanguageTag,
            onClick = { onSelectLanguage(tag) },
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .padding(start = MasroofSpacing.cardInnerGap)
                .fillMaxWidth(),
            textAlign = TextAlign.Start,
        )
    }
}
