package com.talayeman.gold.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.talayeman.gold.util.JalaliCalendar
import com.talayeman.gold.util.JalaliDate

private val WEEKDAY_HEADERS = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")

/**
 * Persian-calendar date picker (month grid starting on Saturday).
 * [initialMillis] is the current value; [onConfirm] returns the chosen Jalali date.
 */
@Composable
fun JalaliDatePickerDialog(
    initialMillis: Long,
    title: String = "انتخاب تاریخ",
    onDismiss: () -> Unit,
    onConfirm: (JalaliDate) -> Unit
) {
    val initial = remember { JalaliCalendar.fromMillis(initialMillis) }
    var year by remember { mutableIntStateOf(initial.year) }
    var month by remember { mutableIntStateOf(initial.month) }
    var day by remember { mutableIntStateOf(initial.day) }

    val daysInMonth = JalaliCalendar.daysInMonth(year, month)
    if (day > daysInMonth) day = daysInMonth
    // Weekday index of the 1st of the month: Saturday = 0 ... Friday = 6
    val firstOffset = remember(year, month) {
        (JalaliDate(year, month, 1).toLocalDate().dayOfWeek.value + 1) % 7
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text(title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Year selector
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { year++ }) { Icon(Icons.Default.KeyboardArrowUp, "سال بعد") }
                    Text(
                        JalaliCalendar.toPersianDigits(year.toString()),
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(onClick = { if (year > 1300) year-- }) { Icon(Icons.Default.KeyboardArrowDown, "سال قبل") }
                }

                // Month selector: 4 rows x 3
                JalaliCalendar.MONTH_NAMES.chunked(3).forEachIndexed { rowIdx, names ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        names.forEachIndexed { colIdx, name ->
                            val m = rowIdx * 3 + colIdx + 1
                            FilterChip(
                                selected = month == m,
                                onClick = { month = m },
                                label = { Text(name, maxLines = 1, style = MaterialTheme.typography.labelMedium) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Weekday header
                Row(Modifier.fillMaxWidth()) {
                    WEEKDAY_HEADERS.forEach {
                        Text(
                            it,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Day grid
                val cells: List<Int?> = List(firstOffset) { null } + (1..daysInMonth).toList()
                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        for (i in 0 until 7) {
                            val d = week.getOrNull(i)
                            Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(2.dp)
                                    .clip(CircleShape)
                                    .then(
                                        if (d == null) Modifier
                                        else if (d == day) Modifier.background(MaterialTheme.colorScheme.primary)
                                        else Modifier.clickable { day = d }
                                    )
                                    .then(if (d != null && d == day) Modifier else Modifier),
                                contentAlignment = Alignment.Center
                            ) {
                                if (d != null) {
                                    Text(
                                        JalaliCalendar.toPersianDigits(d.toString()),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (d == day) FontWeight.Bold else FontWeight.Normal,
                                        color = if (d == day) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                TextButton(onClick = {
                    val t = JalaliCalendar.today()
                    year = t.year; month = t.month; day = t.day
                }) { Text("امروز") }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(JalaliDate(year, month, day)) }) { Text("تأیید") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("لغو") } }
    )
}

/** Read-only field that opens the Jalali picker. Value = epoch millis. */
@Composable
fun JalaliDateField(
    label: String,
    millis: Long,
    onChange: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = JalaliCalendar.formatFull(millis),
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        modifier = modifier.clickable { open = true },
        enabled = false,
        colors = OutlinedTextFieldDefaults.colors(
            disabledTextColor = MaterialTheme.colorScheme.onSurface,
            disabledBorderColor = MaterialTheme.colorScheme.outline,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        singleLine = true
    )
    if (open) {
        JalaliDatePickerDialog(
            initialMillis = millis,
            onDismiss = { open = false },
            onConfirm = {
                open = false
                onChange(JalaliCalendar.withDate(millis, it))
            }
        )
    }
}
