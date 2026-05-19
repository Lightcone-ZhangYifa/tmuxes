package com.tmuxes.ui.components.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import com.tmuxes.i18n.t
import com.tmuxes.ui.design.appTokens

/** Universal switch — color from tokens.primary. */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val tokens = MaterialTheme.appTokens
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = tokens.colors.onPrimary,
            checkedTrackColor = tokens.colors.primary,
            checkedBorderColor = tokens.colors.primary,
            uncheckedThumbColor = tokens.colors.outline,
            uncheckedTrackColor = tokens.colors.surfaceContainerHighest,
            uncheckedBorderColor = tokens.colors.outline
        )
    )
}

@Composable
fun AppCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val tokens = MaterialTheme.appTokens
    Checkbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = CheckboxDefaults.colors(
            checkedColor = tokens.colors.primary,
            uncheckedColor = tokens.colors.outline,
            checkmarkColor = tokens.colors.onPrimary
        )
    )
}

@Composable
fun AppRadioButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val tokens = MaterialTheme.appTokens
    RadioButton(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = RadioButtonDefaults.colors(
            selectedColor = tokens.colors.primary,
            unselectedColor = tokens.colors.outline
        )
    )
}

@Composable
fun AppSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true
) {
    val tokens = MaterialTheme.appTokens
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        valueRange = valueRange,
        steps = steps,
        enabled = enabled,
        colors = SliderDefaults.colors(
            thumbColor = tokens.colors.primary,
            activeTrackColor = tokens.colors.primary,
            inactiveTrackColor = tokens.colors.surfaceContainerHighest
        )
    )
}

/** Universal outlined text field. Replaces 15+ sites that re-roll OutlinedTextFieldDefaults colors. */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    supportingText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    val tokens = MaterialTheme.appTokens
    val labelText = label?.let { t(it) }
    val placeholderText = placeholder?.let { t(it) }
    val supportingTextValue = supportingText?.let { t(it) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        label = if (labelText != null) ({ Text(labelText) }) else null,
        placeholder = if (placeholderText != null) ({ Text(placeholderText) }) else null,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        isError = isError,
        supportingText = if (supportingTextValue != null) ({ Text(supportingTextValue) }) else null,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        shape = tokens.shape.sm,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = tokens.colors.primary,
            unfocusedBorderColor = tokens.colors.outline,
            errorBorderColor = tokens.colors.danger,
            focusedLabelColor = tokens.colors.primary,
            unfocusedLabelColor = tokens.colors.onSurfaceVariant,
            focusedTextColor = tokens.colors.onSurface,
            unfocusedTextColor = tokens.colors.onSurface,
            cursorColor = tokens.colors.primary,
            errorCursorColor = tokens.colors.danger
        )
    )
}
