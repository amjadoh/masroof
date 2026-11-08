package com.baraa.masroof.presentation.common

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.ui.components.MasroofAmountPrivacy
import com.baraa.masroof.ui.components.MasroofOutlineIcons
import com.baraa.masroof.ui.theme.MasroofReference
import com.baraa.masroof.ui.theme.MasroofShapes
import com.baraa.masroof.ui.theme.MasroofSpacing
import com.baraa.masroof.ui.theme.MasroofThemeExtras

/** Applies and remembers the hide-amounts choice. Provided by MainActivity. */
val LocalAmountPrivacyToggle = staticCompositionLocalOf<(Boolean) -> Unit> { { MasroofAmountPrivacy.hidden = it } }

/** The small eye in the top bar: shows or hides every amount in the app. */
@Composable
fun AmountPrivacyIconButton(modifier: Modifier = Modifier) {
    val toggle = LocalAmountPrivacyToggle.current
    val hidden = MasroofAmountPrivacy.hidden
    Box(modifier.size(MasroofReference.iconButton)) {
        Surface(
            onClick = { toggle(!hidden) },
            modifier = Modifier.matchParentSize().testTag("amount-privacy-toggle"),
            shape = MasroofShapes.small,
            border = BorderStroke(MasroofSpacing.borderWidth, MasroofThemeExtras.extendedColors.cardBorder),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Crossfade(targetState = hidden, label = "amount-privacy") { isHidden ->
                    Icon(
                        if (isHidden) MasroofOutlineIcons.EyeOff else MasroofOutlineIcons.Eye,
                        stringResource(if (isHidden) R.string.amounts_show else R.string.amounts_hide),
                        Modifier.size(MasroofReference.icon),
                    )
                }
            }
        }
    }
}
