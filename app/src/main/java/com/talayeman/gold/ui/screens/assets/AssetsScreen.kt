package com.talayeman.gold.ui.screens.assets

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
import com.talayeman.gold.domain.model.AssetType
import com.talayeman.gold.ui.AppViewModel
import com.talayeman.gold.ui.components.EmptyState
import com.talayeman.gold.util.MoneyUtils
import com.talayeman.gold.util.WeightConverter
import com.talayeman.gold.domain.model.WeightUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetsScreen(
    navController: NavController,
    viewModel: AppViewModel = viewModel()
) {
    val assets by viewModel.assets.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf<AssetType?>(null) }
    var showFilter by remember { mutableStateOf(false) }

    val filtered = assets.filter { asset ->
        val matchesSearch = searchQuery.isBlank() ||
                asset.name.contains(searchQuery, ignoreCase = true) ||
                (asset.notes?.contains(searchQuery, ignoreCase = true) == true)
        val matchesType = filterType == null || asset.type == filterType
        matchesSearch && matchesType
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("دارایی‌ها", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showFilter = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = "فیلتر")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate("asset_edit/-1") }) {
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
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("جستجو...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true
            )

            if (filtered.isEmpty()) {
                EmptyState(
                    message = if (assets.isEmpty()) "هنوز هیچ دارایی ثبت نشده است." else "نتیجه‌ای یافت نشد.",
                    actionLabel = if (assets.isEmpty()) "افزودن اولین دارایی" else null,
                    onAction = if (assets.isEmpty()) ({ navController.navigate("asset_edit/-1") }) else null
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { asset ->
                        Card(
                            onClick = { navController.navigate("asset_detail/${asset.id}") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (asset.isCoin) Icons.Default.MonetizationOn else Icons.Default.Diamond,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(asset.name, fontWeight = FontWeight.Bold)
                                    Text(asset.type.persianName, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        if (asset.isCoin) "تعداد: ${asset.quantity}"
                                        else "وزن: ${WeightConverter.format(asset.weightMg, WeightUnit.GRAM)} گرم",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(MoneyUtils.format(asset.totalPurchaseCost), fontWeight = FontWeight.Medium)
                                    Text("عیار ${asset.purity}", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilter) {
        AlertDialog(
            onDismissRequest = { showFilter = false },
            title = { Text("فیلتر نوع") },
            text = {
                Column {
                    TextButton(onClick = { filterType = null; showFilter = false }) {
                        Text("همه")
                    }
                    AssetType.entries.forEach { type ->
                        TextButton(onClick = { filterType = type; showFilter = false }) {
                            Text(type.persianName)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFilter = false }) { Text("بستن") }
            }
        )
    }
}
