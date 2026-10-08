package com.talayeman.gold.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.talayeman.gold.domain.model.PriceType
import com.talayeman.gold.ui.AppViewModel
import com.talayeman.gold.ui.components.EmptyState
import com.talayeman.gold.ui.components.PriceCard
import com.talayeman.gold.ui.components.StatCard
import com.talayeman.gold.util.MoneyUtils
import com.talayeman.gold.util.WeightConverter
import com.talayeman.gold.domain.model.WeightUnit
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: AppViewModel = viewModel()
) {
    val portfolio by viewModel.portfolio.collectAsState()
    val prices by viewModel.prices.collectAsState()
    val assets by viewModel.assets.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val refreshError by viewModel.refreshError.collectAsState()
    val currency by viewModel.currency.collectAsState()

    val priceMap = prices.associateBy { it.priceType }
    val lastUpdate = prices.maxOfOrNull { it.updatedAt }
    val isCached = prices.any { it.isCached }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("خانه", fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            if (refreshError != null) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(
                            refreshError!!,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Portfolio summary
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "ارزش کل پورتفوی",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            MoneyUtils.format(portfolio.currentEstimatedValue, true, currency),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("هزینه خرید", style = MaterialTheme.typography.bodySmall)
                                Text(MoneyUtils.format(portfolio.totalPurchaseCost), fontWeight = FontWeight.Medium)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    if (portfolio.profitLoss >= java.math.BigDecimal.ZERO) "سود تخمینی" else "ضرر تخمینی",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    "${if (portfolio.profitLoss >= java.math.BigDecimal.ZERO) "+" else ""}${MoneyUtils.format(portfolio.profitLoss)} (${portfolio.profitLossPercent}%)",
                                    fontWeight = FontWeight.Medium,
                                    color = if (portfolio.profitLoss >= java.math.BigDecimal.ZERO)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        Text(
                            "این یک ارزش تخمینی بازار است. قیمت واقعی خرید/فروش ممکن است متفاوت باشد.",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            // Stats row
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard(
                        title = "وزن طلا",
                        value = "${WeightConverter.format(portfolio.totalGoldWeightMg, WeightUnit.GRAM)} گرم",
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "تعداد سکه",
                        value = "${portfolio.totalCoinCount}",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard(
                        title = "ارزش طلا",
                        value = MoneyUtils.format(portfolio.goldValue),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "ارزش سکه",
                        value = MoneyUtils.format(portfolio.coinValue),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Market prices snapshot
            item {
                Text("قیمت بازار", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (lastUpdate != null) {
                    val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale("fa"))
                    Text(
                        "آخرین به‌روزرسانی: ${sdf.format(Date(lastUpdate))}" +
                                if (isCached) " (ذخیره‌شده)" else "",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PriceCard("۱۸ عیار", priceMap[PriceType.GOLD_18K], currency, Modifier.weight(1f))
                    PriceCard("۲۴ عیار", priceMap[PriceType.GOLD_24K], currency, Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PriceCard("مثقال", priceMap[PriceType.MITHQAL], currency, Modifier.weight(1f))
                    PriceCard("امامی", priceMap[PriceType.EMAMI], currency, Modifier.weight(1f))
                }
            }

            // Recent assets
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("دارایی‌های اخیر", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { navController.navigate("assets") }) {
                        Text("مشاهده همه")
                    }
                }
            }

            if (assets.isEmpty()) {
                item {
                    EmptyState(
                        message = "هنوز هیچ دارایی ثبت نشده است.",
                        actionLabel = "افزودن اولین دارایی",
                        onAction = { navController.navigate("asset_edit/-1") }
                    )
                }
            } else {
                items(assets.take(5)) { asset ->
                    Card(
                        onClick = { navController.navigate("asset_detail/${asset.id}") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (asset.isCoin) Icons.Default.MonetizationOn else Icons.Default.Diamond,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(asset.name, fontWeight = FontWeight.Medium)
                                Text(asset.type.persianName, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                MoneyUtils.format(asset.totalPurchaseCost),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
