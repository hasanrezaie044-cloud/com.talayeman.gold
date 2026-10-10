package com.talayeman.gold.ui.screens.assets

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.talayeman.gold.domain.model.Asset
import com.talayeman.gold.domain.model.AssetStatus
import com.talayeman.gold.domain.model.AttachmentType
import com.talayeman.gold.domain.model.WeightUnit
import com.talayeman.gold.domain.usecase.PortfolioCalculator
import com.talayeman.gold.ui.AppViewModel
import com.talayeman.gold.ui.components.AppCard
import com.talayeman.gold.ui.components.AttachmentThumbnail
import com.talayeman.gold.ui.components.ConfirmDialog
import com.talayeman.gold.ui.components.ImagePreviewDialog
import com.talayeman.gold.ui.components.JalaliDateField
import com.talayeman.gold.ui.components.MoneyTextField
import com.talayeman.gold.ui.theme.lossColor
import com.talayeman.gold.ui.theme.profitColor
import com.talayeman.gold.util.AttachmentStorage
import com.talayeman.gold.util.GallerySaver
import com.talayeman.gold.util.JalaliCalendar
import com.talayeman.gold.util.MoneyUtils
import com.talayeman.gold.util.WeightConverter
import kotlinx.coroutines.launch
import java.io.File
import java.math.BigDecimal

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
    var menuOpen by remember { mutableStateOf(false) }
    var statusDialog by remember { mutableStateOf<AssetStatus?>(null) }
    var showRestore by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val attachments by remember(assetId) { viewModel.assetRepo.getAttachments(assetId) }
        .collectAsState(initial = emptyList())
    var previewModel by remember { mutableStateOf<Any?>(null) }

    val calculator = remember { PortfolioCalculator() }
    val currentValue = asset?.takeIf { it.status == AssetStatus.ACTIVE }
        ?.let { calculator.estimateAssetValue(it, priceMap) }

    // Optional "save to gallery" (only when the user taps the button)
    val storagePermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        val model = previewModel
        if (granted && model != null) {
            scope.launch {
                val ok = GallerySaver.save(context, model)
                snackbar.showSnackbar(if (ok) "عکس در گالری ذخیره شد" else "ذخیره در گالری ناموفق بود")
            }
        } else if (!granted) {
            scope.launch { snackbar.showSnackbar("برای ذخیره در گالری، اجازه‌ی دسترسی لازم است") }
        }
    }
    fun saveToGallery(model: Any) {
        if (GallerySaver.needsLegacyPermission) {
            storagePermission.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            scope.launch {
                val ok = GallerySaver.save(context, model)
                snackbar.showSnackbar(if (ok) "عکس در گالری ذخیره شد" else "ذخیره در گالری ناموفق بود")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
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
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "گزینه‌ها")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (asset?.status == AssetStatus.ACTIVE) {
                                DropdownMenuItem(
                                    text = { Text("ثبت فروش") },
                                    leadingIcon = { Icon(Icons.Default.Sell, null) },
                                    onClick = { menuOpen = false; statusDialog = AssetStatus.SOLD }
                                )
                                DropdownMenuItem(
                                    text = { Text("ثبت هدیه") },
                                    leadingIcon = { Icon(Icons.Default.CardGiftcard, null) },
                                    onClick = { menuOpen = false; statusDialog = AssetStatus.GIFTED }
                                )
                            } else if (asset != null) {
                                DropdownMenuItem(
                                    text = { Text("بازگرداندن به دارایی فعال") },
                                    leadingIcon = { Icon(Icons.Default.Undo, null) },
                                    onClick = { menuOpen = false; showRestore = true }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("حذف دارایی", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { menuOpen = false; showDelete = true }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (asset == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
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
                // ---- Sold / gifted record ----
                if (a.status != AssetStatus.ACTIVE) {
                    StatusRecordCard(a, currency)
                }

                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DetailRow("نوع", a.type.persianName)
                        DetailRow("تعداد", JalaliCalendar.toPersianDigits(a.quantity.toString()))
                        DetailRow("وزن", "${WeightConverter.format(a.weightMg, WeightUnit.GRAM)} گرم")
                        DetailRow("سوت", WeightConverter.format(a.weightMg, WeightUnit.SOOT))
                        DetailRow("مثقال", WeightConverter.format(a.weightMg, WeightUnit.MITHQAL))
                        DetailRow("عیار", JalaliCalendar.toPersianDigits(a.purity.toString()))
                        DetailRow("تاریخ ثبت در برنامه", JalaliCalendar.formatDateTime(a.createdAt))
                    }
                }

                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("اطلاعات خرید", fontWeight = FontWeight.Bold)
                        DetailRow("قیمت خرید", MoneyUtils.format(a.purchasePrice, true, currency))
                        DetailRow("اجرت", MoneyUtils.format(a.makingCharge, true, currency))
                        DetailRow("مالیات", MoneyUtils.format(a.tax, true, currency))
                        DetailRow("سایر هزینه‌ها", MoneyUtils.format(a.otherFees, true, currency))
                        DetailRow("جمع کل", MoneyUtils.format(a.totalPurchaseCost, true, currency))
                        a.seller?.let { DetailRow("فروشنده", it) }
                        DetailRow("تاریخ خرید", JalaliCalendar.formatDate(a.purchaseDate))
                    }
                }

                if (currentValue != null) {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("ارزش فعلی تخمینی", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(
                                MoneyUtils.format(currentValue, true, currency),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            val pl = currentValue.subtract(a.totalPurchaseCost)
                            Text(
                                if (pl >= BigDecimal.ZERO) "سود: ${MoneyUtils.format(pl)}"
                                else "ضرر: ${MoneyUtils.format(pl.abs())}",
                                fontWeight = FontWeight.Bold,
                                color = if (pl >= BigDecimal.ZERO) profitColor() else lossColor()
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
                        AppCard(Modifier.fillMaxWidth()) {
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
                    AppCard(Modifier.fillMaxWidth()) {
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
        ImagePreviewDialog(
            model = model,
            onDismiss = { previewModel = null },
            onSaveToGallery = { saveToGallery(model) }
        )
    }

    statusDialog?.let { target ->
        asset?.let { a ->
            AssetStatusDialog(
                status = target,
                onDismiss = { statusDialog = null },
                onConfirm = { date, price, note ->
                    viewModel.markAssetStatus(a, target, date, price, note)
                    statusDialog = null
                    scope.launch {
                        snackbar.showSnackbar(
                            if (target == AssetStatus.SOLD) "دارایی به‌عنوان فروخته‌شده ثبت و از پورتفوی کم شد"
                            else "دارایی به‌عنوان هدیه ثبت و از پورتفوی کم شد"
                        )
                    }
                }
            )
        }
    }

    if (showRestore) {
        ConfirmDialog(
            title = "بازگرداندن به دارایی فعال",
            message = "این دارایی دوباره در پورتفوی و سرمایه‌ی شما حساب می‌شود و سابقه‌ی فروش/هدیه پاک می‌شود.",
            confirmLabel = "بازگردان",
            onConfirm = {
                asset?.let { viewModel.restoreAssetToActive(it) }
                showRestore = false
            },
            onDismiss = { showRestore = false }
        )
    }

    if (showDelete) {
        ConfirmDialog(
            title = "حذف دارایی",
            message = "آیا از حذف این دارایی اطمینان دارید؟ این عمل قابل بازگشت نیست. " +
                "اگر دارایی را فروخته یا هدیه داده‌اید، به‌جای حذف از «ثبت فروش / ثبت هدیه» استفاده کنید تا سابقه‌اش بماند.",
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
private fun StatusRecordCard(a: Asset, currency: com.talayeman.gold.domain.model.Currency) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    if (a.status == AssetStatus.SOLD) Icons.Default.Sell else Icons.Default.CardGiftcard,
                    null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    a.status.persianName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            val c = MaterialTheme.colorScheme.onTertiaryContainer
            a.statusDate?.let {
                Text("تاریخ: ${JalaliCalendar.formatFull(it)}", color = c, style = MaterialTheme.typography.bodyMedium)
            }
            if (a.status == AssetStatus.SOLD && a.soldPrice != null) {
                Text("مبلغ فروش: ${MoneyUtils.format(a.soldPrice, true, currency)}", color = c, style = MaterialTheme.typography.bodyMedium)
                val result = a.soldPrice.subtract(a.totalPurchaseCost)
                val positive = result.signum() >= 0
                Text(
                    (if (positive) "سود حاصل از فروش: " else "ضرر حاصل از فروش: ") + MoneyUtils.format(result.abs(), true, currency),
                    fontWeight = FontWeight.Bold,
                    color = if (positive) profitColor() else lossColor()
                )
            }
            a.statusNote?.let {
                Text(
                    (if (a.status == AssetStatus.SOLD) "خریدار / توضیح: " else "گیرنده / توضیح: ") + it,
                    color = c,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                "این دارایی دیگر در پورتفوی و سرمایه‌ی شما حساب نمی‌شود.",
                style = MaterialTheme.typography.labelMedium,
                color = c.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun AssetStatusDialog(
    status: AssetStatus,
    onDismiss: () -> Unit,
    onConfirm: (date: Long, soldPrice: BigDecimal?, note: String?) -> Unit
) {
    var date by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var price by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val sold = status == AssetStatus.SOLD
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text(if (sold) "ثبت فروش دارایی" else "ثبت هدیه دادن دارایی") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "با ثبت این وضعیت، ارزش دارایی از مجموع پورتفوی کم می‌شود و دیگر جزو سرمایه‌ی شما نیست. " +
                        "سابقه‌ی آن در «تاریخچه» می‌ماند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                JalaliDateField(
                    label = if (sold) "تاریخ فروش" else "تاریخ هدیه",
                    millis = date,
                    onChange = { date = it }
                )
                if (sold) {
                    MoneyTextField(
                        value = price,
                        onValueChange = { price = it },
                        label = "مبلغ فروش (اختیاری)",
                        allowDecimal = false
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(if (sold) "خریدار / توضیح (اختیاری)" else "گیرنده / توضیح (اختیاری)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val p = if (sold && price.isNotBlank()) MoneyUtils.parse(price) else null
                onConfirm(date, p, note.trim().ifBlank { null })
            }) { Text("ثبت") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("لغو") } }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}
