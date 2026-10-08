package com.talayeman.gold.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import com.talayeman.gold.util.MoneyInputFormatter

/**
 * Display-only thousands separators. The underlying text stays raw digits, so
 * parsing/calculation never sees a comma, and caret mapping is exact.
 */
class ThousandsSeparatorTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val grouped = MoneyInputFormatter.group(text.text)
        return TransformedText(
            AnnotatedString(grouped.formatted),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int =
                    grouped.originalToTransformed(offset)

                override fun transformedToOriginal(offset: Int): Int =
                    grouped.transformedToOriginal(offset)
            }
        )
    }
}

/**
 * Money input used everywhere an amount is typed.
 *
 * [value] / [onValueChange] carry the raw numeric string (e.g. "85000000"); the
 * user sees "85,000,000". Persian digits typed on a Persian keyboard are accepted.
 * [onValueChange] is only invoked when the text actually changes (not on caret moves),
 * which lets callers reliably detect a real manual edit.
 */
@Composable
fun MoneyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    allowDecimal: Boolean = true
) {
    val raw = remember(value, allowDecimal) {
        MoneyInputFormatter.sanitize(value, value.length, allowDecimal).text
    }
    var fieldValue by remember { mutableStateOf(TextFieldValue(raw, TextRange(raw.length))) }
    // If the value was changed from outside (auto calculation / loading), show it with caret at end.
    val current = if (fieldValue.text == raw) fieldValue
    else TextFieldValue(raw, TextRange(raw.length))

    val transformation = remember { ThousandsSeparatorTransformation() }

    OutlinedTextField(
        value = current,
        onValueChange = { incoming ->
            val s = MoneyInputFormatter.sanitize(incoming.text, incoming.selection.end, allowDecimal)
            if (MoneyInputFormatter.integerDigits(s.text) > MoneyInputFormatter.MAX_INTEGER_DIGITS) {
                return@OutlinedTextField // ignore absurdly long input, keep previous value
            }
            fieldValue = if (s.text == incoming.text) incoming
            else TextFieldValue(s.text, TextRange(s.cursor))
            if (s.text != raw) onValueChange(s.text)
        },
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        supportingText = supportingText,
        trailingIcon = trailingIcon,
        visualTransformation = transformation,
        // Numbers are LTR even inside the RTL form; keep them right-aligned like other fields.
        textStyle = LocalTextStyle.current.copy(
            textDirection = TextDirection.Ltr,
            textAlign = TextAlign.Right
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number
        )
    )
}
