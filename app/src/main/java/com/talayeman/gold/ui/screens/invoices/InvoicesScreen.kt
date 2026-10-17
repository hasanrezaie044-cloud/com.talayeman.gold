package com.talayeman.gold.ui.screens.invoices

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.talayeman.gold.ui.AppViewModel
import com.talayeman.gold.ui.components.EmptyState
import com.talayeman.gold.ui.components.rememberGallerySave
import com.talayeman.gold.util.AttachmentStorage
import com.talayeman.gold.util.JalaliCalendar
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoicesScreen(
    navController: NavController,
    viewModel: AppViewModel = viewModel()
) {
    val invoices by viewModel.assetRepo.getAllInvoices().collectAsState(initial = emptyList())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    // Invoice photos live inside the app; this copies them to the phone gallery on request.
    val saveToGallery = rememberGallerySave { saved, total ->
        scope.launch {
            snackbar.showSnackbar(
                when {
                    saved == 0 -> "ذخیره در گالری ناموفق بود"
                    total == 1 -> "تصویر فاکتور در گالری ذخیره شد"
                    else -> "${JalaliCalendar.toPersianDigits("$saved")} تصویر در گالری ذخیره شد"
                }
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("فاکتورها", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                },
                actions = {
                    if (invoices.isNotEmpty()) {
                        IconButton(onClick = {
                            saveToGallery(invoices.map { AttachmentStorage.resolve(context, it.filePath) })
                        }) {
                            Icon(Icons.Default.SaveAlt, contentDescription = "ذخیره‌ی همه‌ی فاکتورها در گالری")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (invoices.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                EmptyState(message = "هنوز هیچ فاکتوری ثبت نشده است.")
            }
        } else {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(invoices) { inv ->
                    Card(
                        onClick = { navController.navigate("asset_detail/${inv.assetId}") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Receipt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(inv.fileName, fontWeight = FontWeight.Medium)
                                Text(
                                    com.talayeman.gold.util.JalaliCalendar.formatDate(inv.createdAt),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            IconButton(onClick = {
                                saveToGallery(listOf(AttachmentStorage.resolve(context, inv.filePath)))
                            }) {
                                Icon(Icons.Default.SaveAlt, contentDescription = "ذخیره در گالری", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
