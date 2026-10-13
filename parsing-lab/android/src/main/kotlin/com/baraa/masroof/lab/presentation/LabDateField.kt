package com.baraa.masroof.lab.presentation

import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.baraa.masroof.lab.ui.copy.presentation.theme.MasroofSpacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LabDateField(label: String, value: String, enabled: Boolean, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.CalendarMonth, contentDescription = null)
        Spacer(Modifier.width(MasroofSpacing.smallGap))
        Text("$label · $value")
    }
    if (open) {
        // Material's calendar millis represent UTC calendar dates, not the bank's time zone.
        val picker = rememberDatePickerState(initialSelectedDateMillis =
            LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { open = false },
            confirmButton = { TextButton(enabled = picker.selectedDateMillis != null, onClick = {
                picker.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                open = false
            }) { Text("تأكيد") } },
            dismissButton = { TextButton(onClick = { open = false }) { Text("إلغاء") } }) {
            DatePicker(state = picker, title = { Text(label, modifier = Modifier.padding(MasroofSpacing.cardInnerPadding)) }, showModeToggle = false)
        }
    }
}
