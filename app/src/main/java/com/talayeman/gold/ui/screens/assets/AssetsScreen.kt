package com.talayeman.gold.ui.screens.assets

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.talayeman.gold.domain.model.Asset
import com.talayeman.gold.domain.model.AssetCategory
import com.talayeman.gold.domain.model.AssetStatus
import com.talayeman.gold.domain.model.AssetType
import com.talayeman.gold.domain.model.WeightUnit
import com.talayeman.gold.domain.usecase.PortfolioCalculator
import com.talayeman.gold.ui.AppViewModel
import com.talayeman.gold.ui.components.AppCard
import com.talayeman.gold.ui.components.EmptyState
import com.talayeman.gold.ui.components.IconBadge
import com.talayeman.gold.ui.theme.lossColor
import com.talayeman.gold.ui.theme.profitColor
import com.talayeman.gold.util.JalaliCalendar
import com.talayeman.gold.util.MoneyUtils
import com.talayeman.gold.util.WeightConverter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetsScreen(
    navController: NavController,
    viewModel: AppViewModel = viewModel()
) {
    val active by viewModel.activeAssets.collectAsState()
    val history by viewModel.historyAssets.collectAsState()
    var showHistory by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf<AssetType?>(null) }
    var showFilter by remember { mutableStateOf(false) }
    // Section filter: all / gold / coin / other
    var category by remember { mutableStateOf<AssetCategory?>(null) }
    val priceMap by viewModel.priceMap.collectAsState()
    val calculator = remember { PortfolioCalculator() }

    val source = if (showHistory) history else active
    val filtered = source.filter { asset ->
        val matchesSearch = searchQuery.isBlank() ||
            asset.name.contains(searchQuery, ignoreCase = true) ||
            (asset.notes?.contains(searchQuery, ignoreCase = true) == true) ||
            (asset.statusNote?.contains(searchQuery, ignoreCase = true) == true)
        val matchesType = filterType == null || asset.type == filterType
        val matchesCategory = category == null || asset.type.category == category
        matchesSearch && matchesType && matchesCategory
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("دارایی‌ها", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showFilter = true }) {
                        BadgedBox(badge = { if (filterType != null) Badge() }) {
                            Icon(Icons.Default.FilterList, contentDescription = "فیلتر")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("asset_edit/-1") },
                shape = RoundedCornerShape(18.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "افزودن دارایی")
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("جستجو...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )

            // Active assets  /  history of sold & gifted assets
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !showHistory,
                    onClick = { showHistory = false },
                    label = { Text("فعال (${JalaliCalendar.toPersianDigits("${active.size}")})") }
                )
                FilterChip(
                    selected = showHistory,
                    onClick = { showHistory = true },
                    label = { Text("تاریخچه فروش و هدیه (${JalaliCalendar.toPersianDigits("${history.size}")})") }
                )
            }

            // Separate asset types: all / gold / coin / other
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = category == null,
                    onClick = { category = null },
                    label = { Text("همه") }
                )
                AssetCategory.entries.forEach { c ->
                    val n = source.count { it.type.category == c }
                    FilterChip(
                        selected = category == c,
                        onClick = { category = c },
                        label = { Text("${c.persianName} (${JalaliCalendar.toPersianDigits("$n")})") }
                    )
                }
            }

            if (filtered.isEmpty()) {
                val noAssetsAtAll = source.isEmpty()
                EmptyState(
                    message = when {
                        showHistory && noAssetsAtAll -> "هنوز دارایی‌ای فروخته یا هدیه داده نشده است."
                        noAssetsAtAll -> "هنوز هیچ دارایی ثبت نشده است."
                        else -> "نتیجه‌ای یافت نشد."
                    },
                    actionLabel = if (!showHistory && noAssetsAtAll) "افزودن اولین دارایی" else null,
                    onAction = if (!showHistory && noAssetsAtAll) ({ navController.navigate("asset_edit/-1") }) else null
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // One section per category (gold / coin / other), each with its own totals.
                    val grouped = filtered.groupBy { it.type.category }
                    AssetCategory.entries.forEach { cat ->
                        val list = grouped[cat] ?: return@forEach
                        item(key = "section_${cat.name}") {
                            CategoryHeader(cat, list, priceMap, calculator, showValue = !showHistory)
                        }
                        items(list.sortedBy { it.type.ordinal }, key = { it.id }) { asset ->
                            AssetRowCard(asset) { navController.navigate("asset_detail/${asset.id}") }
                        }
                    }
                }
            }
        }
    }

    if (showFilter) {
        AlertDialog(
            onDismissRequest = { showFilter = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text("فیلتر نوع") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TextButton(onClick = { filterType = null; showFilter = false }) { Text("همه") }
                    AssetType.entries.forEach { type ->
                        TextButton(onClick = { filterType = type; showFilter = false }) {
                            Text(type.persianName)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFilter = false }) { Text("بستن") } }
        )
    }
}

@Composable
private fun AssetRowCard(asset: Asset, onClick: () -> Unit) {
    val isActive = asset.status == AssetStatus.ACTIVE
    AppCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                if (asset.isCoin) Icons.Default.MonetizationOn else Icons.Default.Diamond,
                size = 48,
                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                background = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(asset.name, fontWeight = FontWeight.Bold)
                Text(
                    asset.type.persianName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (asset.isCoin) "تعداد: ${JalaliCalendar.toPersianDigits("${asset.quantity}")}"
                    else "وزن: ${WeightConverter.format(asset.weightMg, WeightUnit.GRAM)} گرم",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!isActive) {
                    val date = asset.statusDate?.let { JalaliCalendar.formatDate(it) } ?: "—"
                    Text(
                        "${asset.status.persianName} در $date",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(MoneyUtils.format(asset.totalPurchaseCost), fontWeight = FontWeight.Medium)
                if (isActive) {
                    Text(
                        "عیار ${JalaliCalendar.toPersianDigits("${asset.purity}")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (asset.status == AssetStatus.SOLD && asset.soldPrice != null) {
                    // Realised result of the sale
                    val result = asset.soldPrice.subtract(asset.totalPurchaseCost)
                    val positive = result.signum() >= 0
                    Text(
                        "${if (positive) "+" else ""}${MoneyUtils.format(result)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (positive) profitColor() else lossColor(),
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        asset.status.persianName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Section header: totals of one category plus a per-type breakdown (e.g. each coin type). */
@Composable
private fun CategoryHeader(
    category: AssetCategory,
    assets: List<Asset>,
    prices: Map<com.talayeman.gold.domain.model.PriceType, com.talayeman.gold.domain.model.MarketPrice>,
    calculator: PortfolioCalculator,
    showValue: Boolean
) {
    fun quantityText(list: List<Asset>): String = when (category) {
        AssetCategory.GOLD -> {
            val mg = list.sumOf { it.weightMg * it.quantity }
            "${WeightConverter.format(mg, WeightUnit.GRAM)} گرم"
        }
        else -> {
            val pieces = list.sumOf { it.quantity }
            JalaliCalendar.toPersianDigits(pieces.toString()) + " عدد"
        }
    }

    val cost = assets.fold(java.math.BigDecimal.ZERO) { acc, a -> acc.add(a.totalPurchaseCost) }
    val value = if (showValue) {
        assets.fold(java.math.BigDecimal.ZERO) { acc, a -> acc.add(calculator.estimateAssetValue(a, prices)) }
    } else null
    val pl = value?.subtract(cost)

    AppCard(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    if (category == AssetCategory.COIN) Icons.Default.MonetizationOn else Icons.Default.Diamond,
                    size = 36
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "${category.persianName} (${JalaliCalendar.toPersianDigits("${assets.size}")} مورد)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(quantityText(assets), style = MaterialTheme.typography.labelLarge)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("هزینه خرید", style = MaterialTheme.typography.bodySmall)
                Text(MoneyUtils.format(cost, true), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            if (value != null && pl != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("ارزش فعلی (تخمینی)", style = MaterialTheme.typography.bodySmall)
                    Text(MoneyUtils.format(value, true), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (pl.signum() >= 0) "سود" else "ضرر", style = MaterialTheme.typography.bodySmall)
                    Text(
                        MoneyUtils.format(pl.abs(), true),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (pl.signum() >= 0) profitColor() else lossColor()
                    )
                }
            }
            // Totals per asset type inside this section
            val byType = assets.groupBy { it.type }
            if (byType.size > 1 || category == AssetCategory.COIN) {
                HorizontalDivider()
                byType.entries.sortedBy { it.key.ordinal }.forEach { (type, list) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(type.persianName, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(
                            quantityText(list) + " • " +
                                MoneyUtils.format(list.fold(java.math.BigDecimal.ZERO) { a, x -> a.add(x.totalPurchaseCost) }),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
