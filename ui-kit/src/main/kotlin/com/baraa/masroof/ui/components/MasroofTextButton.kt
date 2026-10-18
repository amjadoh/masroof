package com.baraa.masroof.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.baraa.masroof.ui.theme.MasroofSpacing

@Composable
fun MasroofTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    contentPadding: PaddingValues = PaddingValues(horizontal = MasroofSpacing.none, vertical = MasroofSpacing.inlineGap),
    content: @Composable RowScope.() -> Unit) {
    Surface(onClick = onClick, modifier = modifier, enabled = enabled, color = Color.Transparent,
        contentColor = if (enabled) colors.contentColor else colors.disabledContentColor) {
        ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
            Row(Modifier.padding(contentPadding), verticalAlignment = Alignment.CenterVertically, content = content)
        }
    }
}
