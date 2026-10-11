package com.baraa.masroof.presentation.common

import com.baraa.masroof.presentation.theme.MasroofSizes
import com.baraa.masroof.presentation.theme.MasroofIconSizes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.baraa.masroof.presentation.theme.MasroofLogoShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.baraa.masroof.R

@Composable
fun MasroofLogo(
    modifier: Modifier = Modifier,
    size: Dp = MasroofIconSizes.logo,
    contentDescription: String? = null,
) {
    Image(
        painter = painterResource(R.drawable.ic_masroof_logo),
        contentDescription = contentDescription,
        modifier = modifier.size(size).clip(MasroofLogoShape),
    )
}

@Composable
fun MasroofLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = MasroofSizes.size18,
    contentDescription: String? = null,
) {
    Image(
        painter = painterResource(R.drawable.ic_masroof_logo_mark),
        contentDescription = contentDescription,
        modifier = modifier.size(size).clip(MasroofLogoShape),
    )
}
