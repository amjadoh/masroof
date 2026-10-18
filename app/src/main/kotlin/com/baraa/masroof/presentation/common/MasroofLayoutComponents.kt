package com.baraa.masroof.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import com.baraa.masroof.presentation.common.MasroofText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.baraa.masroof.ui.theme.MasroofSpacing

@Composable
fun MasroofScreenBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val background = MaterialTheme.colorScheme.background
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background),
    ) {
        content()
    }
}

@Composable
fun MasroofAppBar(
    title: String,
    modifier: Modifier = Modifier,
    showLogo: Boolean = true,
    onBack: (() -> Unit)? = null,
    backContentDescription: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    com.baraa.masroof.ui.components.MasroofAppBar(com.baraa.masroof.application.locale.AppDigits.display(title), modifier, showLogo,
        navigation = if (onBack != null) ({ BackNavigationIcon(onBack, backContentDescription) }) else null, actions = actions)

}

@Composable
fun MasroofSecondaryScaffold(
    title: String,
    onBack: () -> Unit,
    backContentDescription: String?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        MasroofAppBar(
            title = title,
            showLogo = false,
            onBack = onBack,
            backContentDescription = backContentDescription,
            actions = actions,
        )
        content(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = MasroofSpacing.screenHorizontal)
                .padding(bottom = MasroofSpacing.screenVertical),
        )
    }
}

@Composable
fun MasroofSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        title,
        modifier = modifier,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}
