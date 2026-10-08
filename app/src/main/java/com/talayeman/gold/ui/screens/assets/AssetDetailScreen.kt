package com.talayeman.gold.ui.screens.assets

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.talayeman.gold.ui.AppViewModel
import com.talayeman.gold.domain.model.AttachmentType
import com.talayeman.gold.ui.components.AttachmentThumbnail
import com.talayeman.gold.ui.components.ConfirmDialog
import com.talayeman.gold.ui.components.ImagePreviewDialog
import com.talayeman.gold.util.AttachmentStorage
import com.talayeman.gold.util.MoneyUtils
import com.talayeman.gold.util.WeightConverter
import com.talayeman.gold.domain.model.WeightUnit
import com.talayeman.gold.domain.usecase.PortfolioCalculator
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetDetailScreen(
    navController: NavController,
    assetId: Long,
    viewModel: AppViewModel = viewModel()
) {
    val asset by viewModel.assetRepo.getAssetByIdFlow(assetId)
        .collectAsState(initial = null)
    val priceMap by viewModel.priceMap.collectAsState()
    val currency by viewModel.currency.collectAsState()
    var showDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val attachments by remember(assetId) { viewModel.assetRepo.getAttachments(assetId) }
        .collectAsState(initial = emptyList())
    var previewModel by remember { mutableStateOf<Any?>(null) }

    val calculator = remember { PortfolioCalculator() }
    val currentValue = asset?.let { calculator.estimateAssetValue(it, priceMap) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(asset?.name ?: "جزئیات", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate("asset_edit/$assetId") }) {
                        Icon(Icons.Default.Edit, contentDescription = "ویرایش")
                    }
                    IconButton(onClick = { showDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف")
                    }
                }
            )
        }
    ) { padding ->
        if (asset == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val a = asset!!
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DetailRow("نوع", a.type.persianName)
                        DetailRow("تعداد", a.quantity.toString())
                        DetailRow("وزن", "${WeightConverter.format(a.weightMg, WeightUnit.GRAM)} گرم")
                        DetailRow("سوت", WeightConverter.format(a.weightMg, WeightUnit.SOOT))
                        DetailRow("مثقال", WeightConverter.format(a.weightMg, WeightUnit.MITHQAL))
                        DetailRow("عیار", a.purity.toString())
                    }
                }

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("اطلاعات خرید", fontWeight = FontWeight.Bold)
                        DetailRow("قیمت خرید", MoneyUtils.format(a.purchasePrice, true, currency))
                        DetailRow("اجرت", MoneyUtils.format(a.makingCharge, true, currency))
                        DetailRow("مالیات", MoneyUtils.format(a.tax, true, currency))
                        DetailRow("سایر هزینه‌ها", MoneyUtils.format(a.otherFees, true, currency))
                        DetailRow("جمع کل", MoneyUtils.format(a.totalPurchaseCost, true, currency))
                        a.seller?.let { DetailRow("فروشنده", it) }
                        val sdf = SimpleDateFormat("yyyy/MM/dd", Locale("fa"))
                        DetailRow("تاریخ خرید", sdf.format(Date(a.purchaseDate)))
                    }
                }

                if (currentValue != null) {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("ارزش فعلی تخمینی", fontWeight = FontWeight.Bold)
                            Text(
                                MoneyUtils.format(currentValue, true, currency),
                                style = MaterialTheme.typography.headlineSmall
                            )
                            val pl = currentValue.subtract(a.totalPurchaseCost)
                            Text(
                                if (pl >= java.math.BigDecimal.ZERO) "سود: ${MoneyUtils.format(pl)}"
                                else "ضرر: ${MoneyUtils.format(pl.abs())}",
                                color = if (pl >= java.math.BigDecimal.ZERO)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                listOf(
                    AttachmentType.PHOTO to "عکس‌ها",
                    AttachmentType.INVOICE to "فاکتورها"
                ).forEach { (attType, title) ->
                    val items = attachments.filter { it.type == attType }
                    if (items.isNotEmpty()) {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(title, fontWeight = FontWeight.Bold)
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items.forEach { att ->
                                        val file = remember(att.filePath) {
                                            AttachmentStorage.resolve(context, att.filePath)
                                        }
                                        AttachmentThumbnail(
                                            model = file,
                                            contentDescription = att.fileName,
                                            onClick = { previewModel = file }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                a.notes?.let {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("یادداشت", fontWeight = FontWeight.Bold)
                            Text(it)
                        }
                    }
                }
            }
        }
    }

    previewModel?.let { model ->
        ImagePreviewDialog(model = model, onDismiss = { previewModel = null })
    }

    if (showDelete) {
        ConfirmDialog(
            title = "حذف دارایی",
            message = "آیا از حذف این دارایی اطمینان دارید؟ این عمل قابل بازگشت نیست.",
            confirmLabel = "حذف",
            onConfirm = {
                viewModel.deleteAsset(assetId)
                showDelete = false
                navController.popBackStack()
            },
            onDismiss = { showDelete = false }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}
