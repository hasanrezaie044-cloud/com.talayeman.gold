package com.talayeman.gold.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import com.talayeman.gold.domain.usecase.PurchasePricing
import com.talayeman.gold.util.MoneyInputFormatter
import com.talayeman.gold.util.MoneyUtils

/**
 * Percentage input restricted to 0..100 (e.g. making charge, tax).
 * Persian digits are accepted; a keystroke that would push the value above 100 (or add too many
 * decimals) is ignored, so the field can never hold an invalid percentage while typing.
 * An empty field means 0 %.
 */
@Composable
fun PercentTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = "بین ۰ تا ۱۰۰"
) {
    OutlinedTextField(
        value = value,
        onValueChange = { incoming ->
            val clean = MoneyInputFormatter.sanitize(incoming, incoming.length, allowDecimal = true).text
            val decimals = clean.substringAfter('.', "").length
            val tooBig = clean.isNotEmpty() && !PurchasePricing.isValidPercent(MoneyUtils.parse(clean))
            if (decimals <= MAX_DECIMALS && !tooBig) onValueChange(clean)
        },
        label = { Text(label) },
        suffix = { Text("٪") },
        supportingText = supportingText?.let { { Text(it) } },
        singleLine = true,
        modifier = modifier,
        textStyle = LocalTextStyle.current.copy(
            textDirection = TextDirection.Ltr,
            textAlign = TextAlign.Right
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

private const val MAX_DECIMALS = 6
