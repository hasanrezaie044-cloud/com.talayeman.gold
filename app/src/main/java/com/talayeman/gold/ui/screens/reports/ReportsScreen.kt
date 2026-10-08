package com.talayeman.gold.ui.screens.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.talayeman.gold.ui.AppViewModel
import com.talayeman.gold.ui.components.StatCard
import com.talayeman.gold.util.MoneyUtils
import com.talayeman.gold.util.WeightConverter
import com.talayeman.gold.domain.model.WeightUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    navController: NavController,
    viewModel: AppViewModel = viewModel()
) {
    val portfolio by viewModel.portfolio.collectAsState()
    val assets by viewModel.assets.collectAsState()
    val currency by viewModel.currency.collectAsState()

    val goldCount = assets.count { !it.isCoin }
    val coinCount = assets.count { it.isCoin }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("گزارش‌ها", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("خلاصه پورتفوی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            StatCard("ارزش فعلی", MoneyUtils.format(portfolio.currentEstimatedValue, true, currency), Modifier.fillMaxWidth())
            StatCard("سرمایه‌گذاری", MoneyUtils.format(portfolio.totalPurchaseCost, true, currency), Modifier.fillMaxWidth())
            StatCard(
                if (portfolio.profitLoss >= java.math.BigDecimal.ZERO) "سود" else "ضرر",
                "${MoneyUtils.format(portfolio.profitLoss.abs(), true, currency)} (${portfolio.profitLossPercent}%)",
                Modifier.fillMaxWidth()
            )

            HorizontalDivider()
            Text("ترکیب دارایی‌ها", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("طلا", "$goldCount مورد", Modifier.weight(1f))
                StatCard("سکه", "$coinCount مورد", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("وزن طلا", "${WeightConverter.format(portfolio.totalGoldWeightMg, WeightUnit.GRAM)} گرم", Modifier.weight(1f))
                StatCard("تعداد سکه", "${portfolio.totalCoinCount}", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("ارزش طلا", MoneyUtils.format(portfolio.goldValue, true, currency), Modifier.weight(1f))
                StatCard("ارزش سکه", MoneyUtils.format(portfolio.coinValue, true, currency), Modifier.weight(1f))
            }

            Text(
                "ارزش‌ها بر اساس آخرین قیمت بازار تخمین زده شده‌اند و ممکن است با قیمت واقعی معامله متفاوت باشند.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
