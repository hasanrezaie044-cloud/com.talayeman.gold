package com.talayeman.gold.ui.screens.market

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.talayeman.gold.domain.model.PriceType
import com.talayeman.gold.ui.AppViewModel
import com.talayeman.gold.util.MoneyUtils
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketScreen(
    navController: NavController,
    viewModel: AppViewModel = viewModel()
) {
    val prices by viewModel.prices.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val refreshError by viewModel.refreshError.collectAsState()
    val currency by viewModel.currency.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("قیمت بازار", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.refreshPrices() }) {
                        if (isRefreshing) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "بروزرسانی")
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (refreshError != null) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(refreshError!!, Modifier.padding(12.dp))
                    }
                }
            }

            if (prices.isEmpty()) {
                item {
                    Text(
                        "هنوز قیمتی دریافت نشده است. برای دریافت قیمت‌های روز دکمه بروزرسانی را بزنید.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            items(PriceType.entries) { type ->
                val price = prices.find { it.priceType == type }
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(type.persianName, fontWeight = FontWeight.Bold)
                            if (price != null) {
                                val sdf = SimpleDateFormat("HH:mm", Locale("fa"))
                                Text(
                                    "به‌روزرسانی: ${sdf.format(Date(price.updatedAt))}" +
                                            if (price.isCached) " (ذخیره‌شده)" else "",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        Text(
                            price?.let { MoneyUtils.format(it.price, true, currency) } ?: "—",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
