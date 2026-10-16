package com.talayeman.gold.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.talayeman.gold.domain.model.Currency
import com.talayeman.gold.domain.model.PriceType
import com.talayeman.gold.ui.AppViewModel
import com.talayeman.gold.ui.theme.lossColor
import com.talayeman.gold.ui.theme.profitColor
import com.talayeman.gold.util.JalaliCalendar
import com.talayeman.gold.util.MoneyUtils
import java.math.BigDecimal
import java.math.RoundingMode

private const val DAY_MS = 24L * 60 * 60 * 1000
private const val WEEK_MS = 7 * DAY_MS

/** What the user can choose to chart: gold, coins, or the remaining price types. */
private enum class ChartCategory(val persianName: String, val types: List<PriceType>) {
    GOLD("طلا", listOf(PriceType.GOLD_18K, PriceType.GOLD_24K)),
    COIN(
        "سکه",
        listOf(
            PriceType.EMAMI, PriceType.BAHAR_AZADI, PriceType.HALF_COIN,
            PriceType.QUARTER_COIN, PriceType.GRAM_COIN, PriceType.PARSIAN
        )
    ),
    OTHER("سایر", listOf(PriceType.MITHQAL, PriceType.MELTED_GOLD))
}

/**
 * One-week (7-day) price chart with a selector for what to display (gold / coin / other, then the
 * exact type). Uses the price history stored locally each time prices are refreshed, so it works
 * offline; days without data simply show no points.
 */
@Composable
fun WeeklyPriceChartCard(
    viewModel: AppViewModel,
    currency: Currency,
    modifier: Modifier = Modifier
) {
    var categoryName by rememberSaveable { mutableStateOf(ChartCategory.GOLD.name) }
    var typeName by rememberSaveable { mutableStateOf(PriceType.GOLD_18K.name) }
    val category = ChartCategory.valueOf(categoryName)
    val type = PriceType.fromString(typeName).takeIf { it in category.types } ?: category.types.first()

    val historyFlow = remember(type) { viewModel.weeklyHistory(type) }
    val points by historyFlow.collectAsState(initial = emptyList())

    AppCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("نمودار تغییر قیمت یک هفته‌ی اخیر")

            // 1) what to show: gold / coin / other
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChartCategory.entries.forEach { c ->
                    FilterChip(
                        selected = category == c,
                        onClick = {
                            categoryName = c.name
                            typeName = c.types.first().name
                        },
                        label = { Text(c.persianName) }
                    )
                }
            }
            // 2) which exact type inside it
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                category.types.forEach { t ->
                    FilterChip(
                        selected = type == t,
                        onClick = { typeName = t.name },
                        label = { Text(t.persianName) }
                    )
                }
            }

            if (points.size < 2) {
                Text(
                    "برای رسم نمودار «${type.persianName}» حداقل دو بار دریافت قیمت در هفته‌ی گذشته لازم است. " +
                        "قیمت‌ها فقط در ساعت کار بازار (۱۱ تا ۱۸) به‌روز می‌شوند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val first = points.first().second
                val last = points.last().second
                val values = points.map { it.second }
                val min = values.minOrNull() ?: BigDecimal.ZERO
                val max = values.maxOrNull() ?: BigDecimal.ZERO
                val changePct = if (first.signum() > 0) {
                    last.subtract(first).multiply(BigDecimal(100)).divide(first, 2, RoundingMode.HALF_UP)
                } else BigDecimal.ZERO
                val up = changePct.signum() >= 0

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("آخرین قیمت", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(MoneyUtils.format(last, true, currency), fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("تغییر هفته", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${if (up) "+" else "−"}${JalaliCalendar.toPersianDigits(changePct.abs().toPlainString())}٪",
                            fontWeight = FontWeight.Bold,
                            color = if (up) profitColor() else lossColor()
                        )
                    }
                }

                // Time axis runs left (older) to right (newer) whatever the UI direction is.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        PriceLineChart(
                            points = points,
                            lineColor = if (up) profitColor() else lossColor(),
                            gridColor = MaterialTheme.colorScheme.outlineVariant,
                            nowMs = System.currentTimeMillis()
                        )
                        val now = System.currentTimeMillis()
                        Row(Modifier.fillMaxWidth()) {
                            for (i in 0..6) {
                                val day = now - (6 - i) * DAY_MS
                                Text(
                                    JalaliCalendar.weekdayName(day) + "\n" +
                                        JalaliCalendar.toPersianDigits(JalaliCalendar.fromMillis(day).day.toString()),
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "کمترین: ${MoneyUtils.format(min)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "بیشترین: ${MoneyUtils.format(max)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                "قیمت‌ها از منابع عمومی گرفته می‌شوند و قطعی نیستند.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

/** Line chart over a fixed 7-day window (gaps without data stay visible). Drawing only uses Double. */
@Composable
private fun PriceLineChart(
    points: List<Pair<Long, BigDecimal>>,
    lineColor: Color,
    gridColor: Color,
    nowMs: Long
) {
    val startMs = nowMs - WEEK_MS
    val values = points.map { it.second.toDouble() }
    val min = values.minOrNull() ?: 0.0
    val max = values.maxOrNull() ?: 0.0
    val range = (max - min).takeIf { it > 1e-9 } ?: 1.0

    Canvas(Modifier.fillMaxWidth().height(170.dp)) {
        for (i in 0..3) {
            val y = size.height * i / 3f
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        val offsets = points.mapIndexed { i, p ->
            val x = ((p.first - startMs).toFloat() / WEEK_MS).coerceIn(0f, 1f) * size.width
            val norm = ((values[i] - min) / range).toFloat()
            Offset(x, size.height * (1f - (0.06f + 0.88f * norm)))
        }
        val line = Path().apply {
            offsets.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(offsets.last().x, size.height)
            lineTo(offsets.first().x, size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.22f), Color.Transparent)))
        drawPath(line, lineColor, style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        offsets.forEach { drawCircle(lineColor, radius = 4.5f, center = it) }
    }
}
