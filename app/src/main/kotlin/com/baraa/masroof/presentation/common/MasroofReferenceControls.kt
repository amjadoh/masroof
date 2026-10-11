package com.baraa.masroof.presentation.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import com.baraa.masroof.presentation.theme.MasroofReference
import com.baraa.masroof.presentation.theme.MasroofShapes
import com.baraa.masroof.presentation.theme.MasroofBadgeShape
import com.baraa.masroof.presentation.theme.MasroofSpacing
import com.baraa.masroof.presentation.theme.MasroofThemeExtras

@Composable
fun MasroofChoice(label: String, selected: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    Surface(onClick = onClick, enabled = enabled, modifier = modifier.semantics { this.selected = selected },
        shape = MasroofBadgeShape, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(MasroofSpacing.borderWidth, if (selected) MaterialTheme.colorScheme.primary else MasroofThemeExtras.extendedColors.cardBorder)) {
        Row(Modifier.padding(horizontal = MasroofSpacing.periodDisplayHorizontalPadding,
            vertical = MasroofReference.shortcutGap + MasroofSpacing.borderWidth), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MasroofReference.shortcutGap)) {
            icon?.let { Icon(it, null, Modifier.size(MasroofReference.icon)) }
            MasroofText(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun MasroofTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    contentPadding: PaddingValues = PaddingValues(horizontal = MasroofSpacing.none, vertical = MasroofSpacing.inlineGap),
    content: @Composable RowScope.() -> Unit) {
    Surface(onClick = onClick, modifier = modifier, enabled = enabled,
        color = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = if (enabled) colors.contentColor else colors.disabledContentColor) {
        ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
            Row(Modifier.padding(contentPadding), verticalAlignment = Alignment.CenterVertically, content = content)
        }
    }
}

@Composable
fun MasroofSelect(value: String, choices: List<String>, onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier, label: String? = null) {
    var open by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MasroofReference.shortcutGap)) {
        label?.let { MasroofText(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Box {
            Surface(onClick = { open = true }, modifier = Modifier.fillMaxWidth(), shape = MasroofShapes.small,
                border = BorderStroke(MasroofSpacing.borderWidth, MasroofThemeExtras.extendedColors.cardBorder)) {
                Row(Modifier.padding(horizontal = MasroofSpacing.periodDisplayHorizontalPadding,
                    vertical = MasroofReference.rowGap), verticalAlignment = Alignment.CenterVertically) {
                    MasroofText(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Icon(MasroofIcons.periodNext, null, Modifier.size(MasroofReference.icon).graphicsLayer { rotationZ = 90f })
                }
            }
            DropdownMenu(open, { open = false }) {
                choices.forEachIndexed { index, text ->
                    DropdownMenuItem(text = { MasroofText(text) }, onClick = { open = false; onSelected(index) })
                }
            }
        }
    }
}

@Composable
fun MasroofButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = MasroofShapes.small, colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = null, border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = MasroofSpacing.compactCardContentPadding,
        vertical = MasroofReference.rowGap), content: @Composable RowScope.() -> Unit) {
    androidx.compose.material3.Button(onClick, modifier, enabled, shape, colors, elevation, border,
        contentPadding = contentPadding, content = content)
}

@Composable
fun MasroofOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = MasroofShapes.small, colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = BorderStroke(MasroofSpacing.borderWidth, MasroofThemeExtras.extendedColors.cardBorder),
    contentPadding: PaddingValues = PaddingValues(horizontal = MasroofSpacing.compactCardContentPadding,
        vertical = MasroofReference.rowGap), content: @Composable RowScope.() -> Unit) {
    androidx.compose.material3.OutlinedButton(onClick, modifier, enabled, shape, colors, elevation, border,
        contentPadding = contentPadding, content = content)
}

@Composable
fun MasroofFormField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, readOnly: Boolean = false, textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    label: (@Composable () -> Unit)? = null, placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null, trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null, isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false, maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1, shape: Shape = MasroofShapes.small) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MasroofReference.shortcutGap)) {
        ProvideTextStyle(MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
            label?.invoke()
        }
        Surface(Modifier.fillMaxWidth(), shape = shape, color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(MasroofSpacing.borderWidth, if (isError) MaterialTheme.colorScheme.error else MasroofThemeExtras.extendedColors.cardBorder)) {
            Row(Modifier.padding(horizontal = MasroofSpacing.periodDisplayHorizontalPadding + MasroofSpacing.borderWidth,
                vertical = MasroofReference.rowGap + MasroofSpacing.borderWidth),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionHeaderGap)) {
                leadingIcon?.invoke()
                BasicTextField(value = value, onValueChange = onValueChange,
                    modifier = Modifier.weight(1f), enabled = enabled, readOnly = readOnly,
                    textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                    keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
                    visualTransformation = if (visualTransformation is androidx.compose.ui.text.input.PasswordVisualTransformation) visualTransformation
                        else VisualTransformation { original ->
                            visualTransformation.filter(com.baraa.masroof.presentation.locale.AppDigitsVisualTransformation.filter(original).text)
                        }, singleLine = singleLine, maxLines = maxLines, minLines = minLines,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), decorationBox = { field ->
                        Box {
                            if (value.isEmpty()) placeholder?.invoke()
                            field()
                        }
                    })
                trailingIcon?.invoke()
            }
        }
        supportingText?.invoke()
    }
}
