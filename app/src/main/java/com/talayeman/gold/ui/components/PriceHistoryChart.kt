package com.talayeman.gold.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talayeman.gold.domain.model.Currency
import com.talayeman.gold.domain.model.PriceType
import com.talayeman.gold.ui.AppViewModel
import com.talayeman.gold.ui.theme.lossColor
import com.talayeman.gold.ui.theme.profitColor
import com.talayeman.gold.util.JalaliCalendar
import com.talayeman.gold.util.MoneyUtils
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.abs

private val PanelBg = Color(0xFF101D33)
private val ChartGold = Color(0xFFE8C355)
private val AxisText = Color(0xFFB8C4D9)
private val GridColor = Color(0x22FFFFFF)

/** What the user can chart: gold, coins, or the remaining price types. */
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

private enum class ChartRange(val label: String, val days: Int?) {
    W1("۷ روز", 7),
    M1("۳۰ روز", 30),
    M3("۹۰ روز", 90),
    Y1("۱ سال", 365),
    ALL("همه", null)
}

private const val MAX_POINTS = 240

/**
 * Interactive price chart: choose gold / coin / other and the exact type, pick a range
 * (7 days ... 1 year) and touch or drag on the chart to read the price at any moment.
 * Older daily prices are loaded from TGJU's history (when online) for the longer ranges;
 * recent points come from the app's own price log, so the chart also works offline.
 */
@Composable
fun PriceHistoryChartCard(
    viewModel: AppViewModel,
    currency: Currency,
    modifier: Modifier = Modifier
) {
    var categoryName by rememberSaveable { mutableStateOf(ChartCategory.GOLD.name) }
    var typeName by rememberSaveable { mutableStateOf(PriceType.GOLD_18K.name) }
    var rangeName by rememberSaveable { mutableStateOf(ChartRange.M1.name) }
    val category = ChartCategory.valueOf(categoryName)
    val type = PriceType.fromString(typeName).takeIf { it in category.types } ?: category.types.first()
    val range = ChartRange.valueOf(rangeName)

    val historyFlow = remember(type, range) { viewModel.priceHistory(type, range.days) }
    val raw by historyFlow.collectAsState(initial = emptyList())
    val points = remember(raw) { downsample(raw) }

    // Longer ranges: add TGJU's daily history (only missing days are stored, nothing is overwritten).
    var importing by remember { mutableStateOf(false) }
    var importFailed by remember(type, range) { mutableStateOf(false) }
    LaunchedEffect(type, range) {
        if (range.days == null || range.days > 7) {
            importing = true
            importFailed = viewModel.importPriceHistory(type).isFailure
            importing = false
        }
    }

    var selected by remember(type, range) { mutableStateOf<Int?>(null) }
    val shown = selected?.takeIf { it in points.indices }

    AppCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("نمودار قیمت")

            // 1) what to show: gold / coin / other, then the exact type
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
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                category.types.forEach { t ->
                    FilterChip(selected = type == t, onClick = { typeName = t.name }, label = { Text(t.persianName) })
                }
            }
            // 2) how far back
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChartRange.entries.forEach { r ->
                    FilterChip(selected = range == r, onClick = { rangeName = r.name }, label = { Text(r.label) })
                }
            }

            if (points.size < 2) {
                Text(
                    when {
                        importing -> "در حال دریافت تاریخچه‌ی قیمت…"
                        importFailed -> "تاریخچه‌ی قدیمی‌تر در دسترس نیست (اتصال اینترنت را بررسی کنید). نمودار با قیمت‌هایی که برنامه ثبت می‌کند پر می‌شود."
                        else -> "برای رسم نمودار «${type.persianName}» داده‌ی کافی نیست. قیمت‌ها فقط در ساعت کار بازار (۱۱ تا ۱۸) به‌روز می‌شوند."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val first = points.first().second
                val shownPoint = shown?.let { points[it] } ?: points.last()
                val change = if (first.signum() > 0) {
                    shownPoint.second.subtract(first).multiply(BigDecimal(100)).divide(first, 2, RoundingMode.HALF_UP)
                } else BigDecimal.ZERO
                val up = change.signum() >= 0

                // Readout for the touched point (or the latest price)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(
                            if (shown != null) "قیمت در ${JalaliCalendar.formatDateTime(shownPoint.first)}" else "آخرین قیمت",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            MoneyUtils.format(shownPoint.second, true, currency),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("تغییر از ابتدای بازه", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${if (up) "+" else "−"}${JalaliCalendar.toPersianDigits(change.abs().toPlainString())}٪",
                            fontWeight = FontWeight.Bold,
                            color = if (up) profitColor() else lossColor()
                        )
                    }
                }

                // Time axis runs left (older) -> right (newer) whatever the UI direction is.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(PanelBg, RoundedCornerShape(16.dp))
                            .padding(vertical = 8.dp)
                    ) {
                        InteractiveChart(points = points, selected = shown, onSelect = { selected = it })
                    }
                }
                Text(
                    if (importing) "در حال دریافت تاریخچه…" else "برای دیدن قیمت در هر زمان، روی نمودار بزنید یا انگشت را روی آن بکشید.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "قیمت‌ها از منابع عمومی گرفته می‌شوند و قطعی نیستند.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

/** Keeps the chart light: at most [MAX_POINTS] points (last value of each time bucket). */
private fun downsample(raw: List<Pair<Long, BigDecimal>>): List<Pair<Long, BigDecimal>> {
    if (raw.size <= MAX_POINTS) return raw
    val t0 = raw.first().first
    val span = (raw.last().first - t0).coerceAtLeast(1L)
    val buckets = LinkedHashMap<Int, Pair<Long, BigDecimal>>()
    raw.forEach { p ->
        val b = (((p.first - t0).toDouble() / span) * (MAX_POINTS - 1)).toInt()
        buckets[b] = p
    }
    return buckets.values.toList()
}

private fun millions(v: Double): String =
    JalaliCalendar.toPersianDigits(String.format(Locale.US, "%.2f", v / 1_000_000.0)).replace('.', '٫') + "M"

@Composable
private fun InteractiveChart(
    points: List<Pair<Long, BigDecimal>>,
    selected: Int?,
    onSelect: (Int?) -> Unit
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val leftPx = with(density) { 54.dp.toPx() }
    val rightPx = with(density) { 10.dp.toPx() }
    val topPx = with(density) { 10.dp.toPx() }
    val bottomPx = with(density) { 26.dp.toPx() }

    val times = remember(points) { points.map { it.first } }
    val values = remember(points) { points.map { it.second.toDouble() } }
    val tMin = times.first()
    val span = (times.last() - tMin).coerceAtLeast(1L)
    val rawMin = values.min()
    val rawMax = values.max()
    val pad = ((rawMax - rawMin) * 0.08).takeIf { it > 1e-9 } ?: (rawMax * 0.01).coerceAtLeast(1.0)
    val vLo = rawMin - pad
    val vHi = rawMax + pad

    var widthPx by remember { mutableIntStateOf(0) }
    fun nearest(x: Float): Int {
        val plotW = (widthPx - leftPx - rightPx).coerceAtLeast(1f)
        val t = tMin + (((x - leftPx) / plotW).coerceIn(0f, 1f) * span).toLong()
        var best = 0
        var bestD = Long.MAX_VALUE
        for (i in times.indices) {
            val d = abs(times[i] - t)
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }

    val axisStyle = TextStyle(color = AxisText, fontSize = 10.sp)
    val chipStyle = TextStyle(color = Color(0xFF1A1305), fontSize = 10.sp, fontWeight = FontWeight.Bold)
    val bubbleStyle = TextStyle(color = Color(0xFF1A1305), fontSize = 11.sp, fontWeight = FontWeight.Bold)

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(250.dp)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(points) { detectTapGestures { onSelect(nearest(it.x)) } }
            .pointerInput(points) {
                detectHorizontalDragGestures(
                    onDragStart = { onSelect(nearest(it.x)) }
                ) { change, _ ->
                    onSelect(nearest(change.position.x))
                    change.consume()
                }
            }
    ) {
        val plotW = size.width - leftPx - rightPx
        val plotH = size.height - topPx - bottomPx
        fun xOf(t: Long) = leftPx + ((t - tMin).toFloat() / span) * plotW
        fun yOf(v: Double) = topPx + (1f - ((v - vLo) / (vHi - vLo)).toFloat()) * plotH

        // grid + y-axis labels (in millions, like the reference chart)
        for (i in 0..4) {
            val v = vHi - (vHi - vLo) * i / 4.0
            val y = yOf(v)
            drawLine(GridColor, Offset(leftPx, y), Offset(size.width - rightPx, y), strokeWidth = 1f)
            val txt = millions(v)
            val m = measurer.measure(txt, axisStyle)
            drawText(measurer, txt, Offset(4f, y - m.size.height / 2f), style = axisStyle)
        }
        // x-axis labels (Jalali dates)
        for (i in 0..4) {
            val t = tMin + span * i / 4
            val txt = if (span < 3L * 86_400_000L) JalaliCalendar.formatTime(t) else JalaliCalendar.formatDate(t)
            val m = measurer.measure(txt, axisStyle)
            val x = (xOf(t) - m.size.width / 2f).coerceIn(0f, size.width - m.size.width)
            drawText(measurer, txt, Offset(x, size.height - bottomPx + 6f), style = axisStyle)
        }

        // area + line
        val offsets = points.mapIndexed { i, p -> Offset(xOf(p.first), yOf(values[i])) }
        val line = Path().apply { offsets.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
        val area = Path().apply {
            addPath(line)
            lineTo(offsets.last().x, topPx + plotH)
            lineTo(offsets.first().x, topPx + plotH)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(ChartGold.copy(alpha = 0.45f), ChartGold.copy(alpha = 0.02f)), startY = topPx, endY = topPx + plotH))
        drawPath(line, ChartGold, style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // dashed line + gold chip with the latest price
        val lastY = offsets.last().y
        drawLine(
            ChartGold.copy(alpha = 0.8f), Offset(leftPx, lastY), Offset(size.width - rightPx, lastY),
            strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
        )
        val lastTxt = millions(values.last())
        val lastM = measurer.measure(lastTxt, chipStyle)
        drawRoundRect(
            ChartGold, Offset(2f, lastY - lastM.size.height / 2f - 3f),
            Size(lastM.size.width + 12f, lastM.size.height + 6f), CornerRadius(8f, 8f)
        )
        drawText(measurer, lastTxt, Offset(8f, lastY - lastM.size.height / 2f), style = chipStyle)

        // touched point: guide line, marker and a bubble with date + price
        if (selected != null && selected in points.indices) {
            val o = offsets[selected]
            drawLine(Color.White.copy(alpha = 0.55f), Offset(o.x, topPx), Offset(o.x, topPx + plotH), strokeWidth = 2f)
            drawCircle(Color.White, 9f, o)
            drawCircle(ChartGold, 6f, o)
            val txt = JalaliCalendar.formatDate(points[selected].first) + "\n" + millions(values[selected])
            val m = measurer.measure(txt, bubbleStyle)
            val bw = m.size.width + 20f
            val bh = m.size.height + 12f
            val bx = (o.x - bw / 2f).coerceIn(leftPx, size.width - rightPx - bw)
            val by = (o.y - bh - 14f).coerceAtLeast(topPx)
            drawRoundRect(ChartGold, Offset(bx, by), Size(bw, bh), CornerRadius(10f, 10f))
            drawText(measurer, txt, Offset(bx + 10f, by + 6f), style = bubbleStyle)
        }
    }
}
