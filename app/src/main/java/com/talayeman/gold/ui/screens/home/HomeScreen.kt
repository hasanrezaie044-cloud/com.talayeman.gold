package com.talayeman.gold.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.talayeman.gold.domain.model.PriceType
import com.talayeman.gold.domain.model.WeightUnit
import com.talayeman.gold.ui.AppViewModel
import com.talayeman.gold.ui.components.AppCard
import com.talayeman.gold.ui.components.EmptyState
import com.talayeman.gold.ui.components.IconBadge
import com.talayeman.gold.ui.components.PriceCard
import com.talayeman.gold.ui.components.SectionTitle
import com.talayeman.gold.ui.components.StatCard
import com.talayeman.gold.ui.theme.GoldBright
import com.talayeman.gold.ui.theme.GoldDeep
import com.talayeman.gold.ui.theme.GoldMid
import com.talayeman.gold.util.JalaliCalendar
import com.talayeman.gold.util.MoneyUtils
import com.talayeman.gold.util.WeightConverter
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: AppViewModel = viewModel()
) {
    val portfolio by viewModel.portfolio.collectAsState()
    val prices by viewModel.prices.collectAsState()
    val activeAssets by viewModel.activeAssets.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val refreshError by viewModel.refreshError.collectAsState()
    val currency by viewModel.currency.collectAsState()

    val priceMap = prices.associateBy { it.priceType }
    val lastUpdate = prices.maxOfOrNull { it.updatedAt }
    val isCached = prices.any { it.isCached }
    val todayText = remember { JalaliCalendar.todayFull() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("طلای من", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CalendarMonth, null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(4.dp))
                            // Today's Persian (Jalali) date
                            Text(
                                todayText,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshPrices() }) {
                        if (isRefreshing) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "بروزرسانی قیمت")
                        }
                    }
                    IconButton(onClick = { navController.navigate("reports") }) {
                        Icon(Icons.Default.BarChart, contentDescription = "گزارش‌ها")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(Modifier.height(2.dp)) }

            if (refreshError != null) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(
                            refreshError!!,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // ---------- Hero: portfolio (active assets only) ----------
            item {
                val positive = portfolio.profitLoss >= BigDecimal.ZERO
                val plColor = if (positive) Color(0xFF5BD98B) else Color(0xFFFF7B72)
                val shape = RoundedCornerShape(28.dp)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF18294A), Color(0xFF0A1428), Color(0xFF0E1B33))),
                            shape
                        )
                        .border(1.dp, Brush.linearGradient(listOf(GoldMid, GoldDeep.copy(alpha = 0.3f))), shape)
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "ارزش کل پورتفوی",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color(0xFFB8C4D9),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${com.talayeman.gold.util.JalaliCalendar.toPersianDigits("${portfolio.assetCount}")} دارایی فعال",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFB8C4D9)
                            )
                        }
                        Text(
                            MoneyUtils.format(portfolio.currentEstimatedValue, true, currency),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = GoldBright
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("هزینه خرید", style = MaterialTheme.typography.labelMedium, color = Color(0xFFB8C4D9))
                                Text(
                                    MoneyUtils.format(portfolio.totalPurchaseCost),
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    if (positive) "سود تخمینی" else "ضرر تخمینی",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFFB8C4D9)
                                )
                                Text(
                                    "${if (positive) "+" else ""}${MoneyUtils.format(portfolio.profitLoss)}",
                                    fontWeight = FontWeight.Bold,
                                    color = plColor
                                )
                                Text(
                                    "${if (positive) "+" else ""}${portfolio.profitLossPercent.toPlainString()}٪",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = plColor
                                )
                            }
                        }
                        Text(
                            "ارزش تخمینی بازار است؛ قیمت واقعی خرید و فروش ممکن است متفاوت باشد. " +
                                "دارایی‌های فروخته یا هدیه‌شده در این مبلغ حساب نمی‌شوند.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF8E9BB4),
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }

            // ---------- Stats ----------
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(
                        title = "وزن طلا",
                        value = "${WeightConverter.format(portfolio.totalGoldWeightMg, WeightUnit.GRAM)} گرم",
                        icon = Icons.Default.Scale,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "تعداد سکه",
                        value = JalaliCalendar.toPersianDigits("${portfolio.totalCoinCount}"),
                        icon = Icons.Default.MonetizationOn,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(
                        title = "ارزش طلا",
                        value = MoneyUtils.format(portfolio.goldValue),
                        icon = Icons.Default.Diamond,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "ارزش سکه",
                        value = MoneyUtils.format(portfolio.coinValue),
                        icon = Icons.Default.Paid,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ---------- Market ----------
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SectionTitle("قیمت بازار")
                    if (lastUpdate != null) {
                        Text(
                            "آخرین به‌روزرسانی: ${JalaliCalendar.formatDateTime(lastUpdate)}" +
                                if (isCached) " (ذخیره‌شده)" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PriceCard("۱۸ عیار", priceMap[PriceType.GOLD_18K], currency, Modifier.weight(1f))
                    PriceCard("۲۴ عیار", priceMap[PriceType.GOLD_24K], currency, Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PriceCard("مثقال", priceMap[PriceType.MITHQAL], currency, Modifier.weight(1f))
                    PriceCard("امامی", priceMap[PriceType.EMAMI], currency, Modifier.weight(1f))
                }
            }

            // ---------- Recent assets ----------
            item {
                SectionTitle("دارایی‌های اخیر") {
                    TextButton(onClick = { navController.navigate("assets") }) { Text("مشاهده همه") }
                }
            }

            if (activeAssets.isEmpty()) {
                item {
                    EmptyState(
                        message = "هنوز هیچ دارایی ثبت نشده است.",
                        actionLabel = "افزودن اولین دارایی",
                        onAction = { navController.navigate("asset_edit/-1") }
                    )
                }
            } else {
                items(activeAssets.sortedByDescending { it.createdAt }.take(5), key = { it.id }) { asset ->
                    AppCard(
                        onClick = { navController.navigate("asset_detail/${asset.id}") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconBadge(if (asset.isCoin) Icons.Default.MonetizationOn else Icons.Default.Diamond)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(asset.name, fontWeight = FontWeight.Bold)
                                Text(
                                    "${asset.type.persianName} • ثبت: ${JalaliCalendar.formatDate(asset.createdAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                MoneyUtils.format(asset.totalPurchaseCost),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
