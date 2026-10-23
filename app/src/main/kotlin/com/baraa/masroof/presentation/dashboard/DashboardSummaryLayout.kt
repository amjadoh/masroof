package com.baraa.masroof.presentation.dashboard

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.baraa.masroof.presentation.common.MasroofText as Text
import com.baraa.masroof.presentation.common.MasroofTextStyles

@Composable
fun DashboardSummaryBreakdownHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        title,
        modifier = modifier,
        style = MasroofTextStyles.breakdownLabel,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
