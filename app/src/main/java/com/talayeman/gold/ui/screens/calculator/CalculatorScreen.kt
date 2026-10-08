package com.talayeman.gold.ui.screens.calculator

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.talayeman.gold.domain.model.PriceType
import com.talayeman.gold.domain.model.WeightUnit
import com.talayeman.gold.ui.AppViewModel
import com.talayeman.gold.ui.components.MoneyTextField
import com.talayeman.gold.util.MoneyUtils
import com.talayeman.gold.util.WeightConverter
import java.math.BigDecimal
import java.math.RoundingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    navController: NavController,
    viewModel: AppViewModel = viewModel()
) {
    val priceMap by viewModel.priceMap.collectAsState()
    val currency by viewModel.currency.collectAsState()

    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("تبدیل وزن", "ارزش طلا", "قدرت خرید", "سود/ضرر")

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("ماشین حساب", fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { i, title ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
                }
            }

            when (tab) {
                0 -> WeightConversionTab()
                1 -> GoldValueTab(priceMap, currency)
                2 -> BuyingPowerTab(priceMap, currency)
                3 -> ProfitLossTab(currency)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeightConversionTab() {
    var value by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(WeightUnit.GRAM) }
    var unitExpanded by remember { mutableStateOf(false) }

    val mg = remember(value, unit) {
        runCatching { WeightConverter.toMilligrams(MoneyUtils.parse(value), unit) }.getOrDefault(0L)
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            label = { Text("مقدار") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )

        ExposedDropdownMenuBox(expanded = unitExpanded, onExpandedChange = { unitExpanded = it }) {
            OutlinedTextField(
                value = unit.persianName,
                onValueChange = {},
                readOnly = true,
                label = { Text("واحد") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(unitExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = unitExpanded, onDismissRequest = { unitExpanded = false }) {
                WeightUnit.entries.forEach { u ->
                    DropdownMenuItem(text = { Text(u.persianName) }, onClick = { unit = u; unitExpanded = false })
                }
            }
        }

        if (mg > 0) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("نتایج تبدیل", fontWeight = FontWeight.Bold)
                    Text("میلی‌گرم: ${WeightConverter.format(mg, WeightUnit.MILLIGRAM)}")
                    Text("سوت: ${WeightConverter.format(mg, WeightUnit.SOOT)}")
                    Text("گرم: ${WeightConverter.format(mg, WeightUnit.GRAM)}")
                    Text("مثقال: ${WeightConverter.format(mg, WeightUnit.MITHQAL)}")
                }
            }
        }
    }
}

@Composable
private fun GoldValueTab(
    priceMap: Map<PriceType, com.talayeman.gold.domain.model.MarketPrice>,
    currency: com.talayeman.gold.domain.model.Currency
) {
    var weight by remember { mutableStateOf("") }
    var purity by remember { mutableIntStateOf(18) }
    val karatOptions = listOf(9, 10, 14, 18, 20, 21, 22, 24)

    val grams = MoneyUtils.parse(weight)
    val price18 = priceMap[PriceType.GOLD_18K]?.price
    val estimated = if (price18 != null && grams > BigDecimal.ZERO) {
        MoneyUtils.estimateGoldValue(
            weightMg = WeightConverter.toMilligrams(grams, WeightUnit.GRAM),
            quantity = 1,
            purity = purity,
            pricePerGram18or24 = price18,
            is24kPrice = false
        )
    } else BigDecimal.ZERO

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = weight,
            onValueChange = { weight = it },
            label = { Text("وزن (گرم)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )

        Text("عیار", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            karatOptions.forEach { k ->
                FilterChip(
                    selected = purity == k,
                    onClick = { purity = k },
                    label = { Text("${k}K") }
                )
            }
        }

        if (price18 == null) {
            Text("قیمت بازار در دسترس نیست. ابتدا قیمت‌ها را به‌روز کنید.", color = MaterialTheme.colorScheme.error)
        } else {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("ارزش تخمینی", fontWeight = FontWeight.Bold)
                    Text(MoneyUtils.format(estimated, true, currency), style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
    }
}

@Composable
private fun BuyingPowerTab(
    priceMap: Map<PriceType, com.talayeman.gold.domain.model.MarketPrice>,
    currency: com.talayeman.gold.domain.model.Currency
) {
    var amount by remember { mutableStateOf("") }
    val budget = MoneyUtils.parse(amount)
    val price18 = priceMap[PriceType.GOLD_18K]?.price

    val grams = if (price18 != null && price18 > BigDecimal.ZERO && budget > BigDecimal.ZERO) {
        budget.divide(price18, 4, RoundingMode.HALF_UP)
    } else BigDecimal.ZERO

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MoneyTextField(
            value = amount,
            onValueChange = { amount = it },
            label = "مبلغ (تومان)",
            modifier = Modifier.fillMaxWidth()
        )

        if (price18 == null) {
            Text("قیمت بازار در دسترس نیست.", color = MaterialTheme.colorScheme.error)
        } else {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("با این مبلغ می‌توانید بخرید:", fontWeight = FontWeight.Bold)
                    Text("${grams.toPlainString()} گرم طلای ۱۸ عیار", style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
    }
}

@Composable
private fun ProfitLossTab(currency: com.talayeman.gold.domain.model.Currency) {
    var purchase by remember { mutableStateOf("") }
    var current by remember { mutableStateOf("") }

    val p = MoneyUtils.parse(purchase)
    val c = MoneyUtils.parse(current)
    val pl = MoneyUtils.profitLoss(p, c)
    val pct = MoneyUtils.profitLossPercent(p, c)

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MoneyTextField(
            value = purchase,
            onValueChange = { purchase = it },
            label = "قیمت خرید (تومان)",
            modifier = Modifier.fillMaxWidth()
        )
        MoneyTextField(
            value = current,
            onValueChange = { current = it },
            label = "ارزش فعلی (تومان)",
            modifier = Modifier.fillMaxWidth()
        )

        if (p > BigDecimal.ZERO) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        if (pl >= BigDecimal.ZERO) "سود: ${MoneyUtils.format(pl, true, currency)}"
                        else "ضرر: ${MoneyUtils.format(pl.abs(), true, currency)}",
                        fontWeight = FontWeight.Bold,
                        color = if (pl >= BigDecimal.ZERO) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Text("درصد: $pct%")
                }
            }
        }
    }
}
