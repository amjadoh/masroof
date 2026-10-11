package com.baraa.masroof.presentation.common

import com.baraa.masroof.presentation.theme.MasroofFontWeights
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.baraa.masroof.presentation.common.MasroofText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.baraa.masroof.presentation.theme.MasroofIconSizes
import com.baraa.masroof.presentation.theme.MasroofSpacing

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
    val reference = com.baraa.masroof.presentation.theme.MasroofReference
    Surface(modifier.fillMaxWidth(), color = if (showLogo) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background) {
        Row(Modifier.fillMaxWidth().padding(
            start = if (showLogo) reference.headerHorizontal else MasroofSpacing.screenHorizontal,
            end = if (showLogo) reference.headerHorizontal else MasroofSpacing.screenHorizontal,
            top = if (showLogo) reference.headerTop else MasroofSpacing.screenVertical,
            bottom = reference.headerBottom),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionHeaderGap)) {
            if (onBack != null) BackNavigationIcon(onBack, backContentDescription)
            if (showLogo) MasroofLogo(size = MasroofIconSizes.logo, contentDescription = null)
            Text(title, Modifier.weight(1f),
                style = if (showLogo) reference.brand else MaterialTheme.typography.headlineSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
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
