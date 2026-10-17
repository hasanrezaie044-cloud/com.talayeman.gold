package com.talayeman.gold.ui.screens.assets

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.talayeman.gold.domain.model.AcquisitionType
import com.talayeman.gold.domain.model.Asset
import com.talayeman.gold.domain.model.AssetStatus
import com.talayeman.gold.domain.model.AssetType
import com.talayeman.gold.domain.model.Attachment
import com.talayeman.gold.domain.model.AttachmentType
import com.talayeman.gold.domain.model.WeightUnit
import com.talayeman.gold.domain.usecase.PortfolioCalculator
import com.talayeman.gold.domain.usecase.PurchasePricing
import com.talayeman.gold.ui.AppViewModel
import com.talayeman.gold.ui.components.AppCard
import com.talayeman.gold.ui.components.AttachmentThumbnail
import com.talayeman.gold.ui.components.ImagePreviewDialog
import com.talayeman.gold.ui.components.JalaliDateField
import com.talayeman.gold.ui.components.MoneyTextField
import com.talayeman.gold.ui.components.PercentTextField
import com.talayeman.gold.util.AttachmentStorage
import com.talayeman.gold.util.GallerySaver
import com.talayeman.gold.util.JalaliCalendar
import com.talayeman.gold.util.MoneyInputFormatter
import com.talayeman.gold.util.MoneyUtils
import com.talayeman.gold.util.WeightConverter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

/** An image picked/captured in this form that is not yet saved to app storage. */
private data class PendingImage(val uri: Uri, val type: AttachmentType, val fromCamera: Boolean = false)

private const val MAX_PICK = 10

/** Reference (standard) weight of bank coins, used only to pre-fill an empty weight field. */
private fun standardCoinWeightMg(type: AssetType): Long? = when (type) {
    AssetType.EMAMI -> WeightConverter.StandardCoinWeights.EMAMI_MG
    AssetType.BAHAR_AZADI -> WeightConverter.StandardCoinWeights.BAHAR_AZADI_MG
    AssetType.HALF_COIN -> WeightConverter.StandardCoinWeights.HALF_COIN_MG
    AssetType.QUARTER_COIN -> WeightConverter.StandardCoinWeights.QUARTER_COIN_MG
    AssetType.GRAM_COIN -> WeightConverter.StandardCoinWeights.GRAM_COIN_MG
    else -> null
}

private val THOUSAND = BigDecimal(1000)

/** Amount of weight in grams for a weight stored in milligrams. */
private fun gramsOf(mg: Long): BigDecimal = BigDecimal.valueOf(mg).divide(THOUSAND, 8, RoundingMode.HALF_UP)

/** Percentage typed in a field; empty / invalid = 0. */
private fun parsePercent(text: String): BigDecimal = MoneyUtils.parse(text)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetEditScreen(
    navController: NavController,
    assetId: Long?,
    viewModel: AppViewModel = viewModel()
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(AssetType.GOLD_18K) }
    var quantity by remember { mutableStateOf("1") }
    var weightValue by remember { mutableStateOf("") }
    var weightUnit by remember { mutableStateOf(WeightUnit.GRAM) }
    var purity by remember { mutableStateOf("18") }
    // Bought (price paid) or received as a gift (nothing paid, but it counts in the portfolio).
    var acquisition by remember { mutableStateOf(AcquisitionType.PURCHASED) }

    // ---- Pricing inputs (the final price is calculated live from these) ----
    var unitPrice by remember { mutableStateOf("") }        // per gram (gold) or per piece (coin / other)
    var makingPercent by remember { mutableStateOf("") }    // 0..100, empty = 0
    var taxPercent by remember { mutableStateOf("") }       // 0..100, empty = 0
    var otherFees by remember { mutableStateOf("") }        // optional fixed amount

    var seller by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var typeExpanded by remember { mutableStateOf(false) }
    var unitExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val isEdit = assetId != null && assetId > 0

    // Preserve the registration date when editing; the purchase date is chosen in the Jalali picker.
    var purchaseDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var originalCreatedAt by remember { mutableStateOf<Long?>(null) }
    // The loaded asset: sold / gifted status must survive an edit.
    var originalAsset by remember { mutableStateOf<Asset?>(null) }
    val registeredAtPreview = remember { System.currentTimeMillis() }

    // Unit price follows today's market price for the selected type (new assets). A manual edit
    // switches it off so a typed / saved value is never overwritten.
    var priceAuto by remember { mutableStateOf(!isEdit) }
    // True while the weight field holds a reference weight we pre-filled (not user-typed).
    var weightAutoFilled by remember { mutableStateOf(false) }

    // Attachments
    val existingFlow = remember(assetId) {
        if (isEdit) viewModel.assetRepo.getAttachments(assetId!!) else flowOf(emptyList<Attachment>())
    }
    val existingAttachments by existingFlow.collectAsState(initial = emptyList())
    val removedAttachmentIds = remember { mutableStateListOf<Long>() }
    val pendingImages = remember { mutableStateListOf<PendingImage>() }
    var previewModel by remember { mutableStateOf<Any?>(null) }
    var saved by remember { mutableStateOf(false) }

    var galleryTarget by rememberSaveable { mutableStateOf(AttachmentType.PHOTO) }
    var cameraTarget by rememberSaveable { mutableStateOf(AttachmentType.PHOTO) }
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    // ---------------------------------------------------------------------------------------
    // Optional "save camera photos to the phone's gallery". Only happens when the user ticks the
    // box (automatic after each shot) or taps a save button. Android 10+ needs no permission;
    // Android 8-9 ask for the legacy storage permission at that moment.
    // ---------------------------------------------------------------------------------------
    val scope = rememberCoroutineScope()
    var saveCameraToGallery by rememberSaveable { mutableStateOf(false) }
    val gallerySaved = remember { mutableStateListOf<Uri>() }
    var pendingSaveModels by remember { mutableStateOf<List<Any>>(emptyList()) }

    fun toast(msg: String) =
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()

    fun doSaveToGallery(models: List<Any>) {
        scope.launch {
            var ok = 0
            models.forEach { m ->
                if (GallerySaver.save(context, m)) {
                    ok++
                    if (m is Uri) gallerySaved.add(m)
                }
            }
            toast(
                when {
                    ok == 0 -> "ذخیره در گالری ناموفق بود"
                    ok == 1 -> "عکس در گالری ذخیره شد"
                    else -> "${JalaliCalendar.toPersianDigits(ok.toString())} عکس در گالری ذخیره شد"
                }
            )
        }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val models = pendingSaveModels
        pendingSaveModels = emptyList()
        if (granted && models.isNotEmpty()) doSaveToGallery(models)
        else toast("برای ذخیره در گالری، اجازه‌ی دسترسی لازم است")
    }

    fun saveToGallery(models: List<Any>) {
        if (models.isEmpty()) return
        if (GallerySaver.needsLegacyPermission &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            pendingSaveModels = models
            storagePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            doSaveToGallery(models)
        }
    }

    // ---- Gallery picker (system photo picker; falls back to the classic content picker) ----
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK)
    ) { uris ->
        uris.forEach { pendingImages.add(PendingImage(it, galleryTarget)) }
    }
    val classicPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.take(MAX_PICK).forEach { pendingImages.add(PendingImage(it, galleryTarget)) }
    }

    // Delegates to the system camera app; no CAMERA permission is declared, so none is needed.
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = cameraUri
        if (uri != null) {
            if (success) {
                pendingImages.add(PendingImage(uri, cameraTarget, fromCamera = true))
                if (saveCameraToGallery) saveToGallery(listOf(uri))
            } else {
                AttachmentStorage.deleteCameraTemp(context, uri)
            }
        }
        cameraUri = null
    }

    fun openGallery(target: AttachmentType) {
        galleryTarget = target
        try {
            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (e: Exception) {
            // No photo picker available: use the classic "pick content" screen instead.
            try {
                classicPickerLauncher.launch("image/*")
            } catch (e2: Exception) {
                error = "برنامه‌ای برای انتخاب تصویر یافت نشد"
            }
        }
    }

    fun openCamera(target: AttachmentType) {
        cameraTarget = target
        try {
            val uri = AttachmentStorage.createCameraTempUri(context)
            cameraUri = uri
            cameraLauncher.launch(uri)
        } catch (e: android.content.ActivityNotFoundException) {
            cameraUri?.let { AttachmentStorage.deleteCameraTemp(context, it) }
            cameraUri = null
            error = "برنامه دوربین روی این دستگاه یافت نشد"
        } catch (e: Exception) {
            cameraUri = null
            error = "باز کردن دوربین ممکن نشد"
        }
    }

    // Clean up camera temp files if the user leaves without saving.
    DisposableEffect(Unit) {
        onDispose {
            if (!saved) pendingImages.forEach { AttachmentStorage.deleteCameraTemp(context, it.uri) }
        }
    }

    // Load an existing asset. Old assets stored amounts, so unit price / percentages are derived.
    LaunchedEffect(assetId) {
        if (isEdit) {
            viewModel.assetRepo.getAssetById(assetId!!)?.let { asset ->
                name = asset.name
                type = asset.type
                quantity = asset.quantity.toString()
                weightValue = WeightConverter.format(asset.weightMg, WeightUnit.GRAM)
                weightUnit = WeightUnit.GRAM
                purity = asset.purity.toString()
                seller = asset.seller ?: ""
                otherFees = MoneyUtils.toInputString(asset.otherFees)
                notes = asset.notes ?: ""
                purchaseDate = asset.purchaseDate
                originalCreatedAt = asset.createdAt
                originalAsset = asset
                acquisition = asset.acquisition

                val count = if (asset.type.pricedPerGram) gramsOf(asset.weightMg).multiply(BigDecimal(asset.quantity))
                else BigDecimal(asset.quantity)
                unitPrice = if (count.signum() > 0) {
                    MoneyUtils.toInputString(asset.purchasePrice.divide(count, 4, RoundingMode.HALF_UP))
                } else MoneyUtils.toInputString(asset.purchasePrice)
                val mp = PurchasePricing.percentOf(asset.makingCharge, asset.purchasePrice)
                val tp = PurchasePricing.percentOf(asset.tax, asset.purchasePrice)
                makingPercent = if (mp.signum() == 0) "" else mp.toPlainString()
                taxPercent = if (tp.signum() == 0) "" else tp.toPlainString()
            }
        }
    }

    // ---- Derived values, recomputed instantly on every input change ----
    val weightMg = remember(weightValue, weightUnit) {
        runCatching {
            WeightConverter.toMilligrams(MoneyUtils.parse(weightValue), weightUnit)
        }.getOrDefault(0L)
    }
    val qty = quantity.toIntOrNull()?.takeIf { it > 0 } ?: 1
    val purityValue = purity.toIntOrNull() ?: 0

    val priceMap by viewModel.priceMap.collectAsState()
    val calculator = remember { PortfolioCalculator() }
    val marketUnit = remember(type, purityValue, weightMg, priceMap) {
        calculator.marketUnitPrice(type, purityValue, weightMg, priceMap)
    }
    // Selecting ANY type (gold, coin, other) pre-fills today's market unit price while auto is on.
    LaunchedEffect(priceAuto, marketUnit) {
        if (priceAuto && marketUnit != null) {
            unitPrice = MoneyUtils.toInputString(marketUnit.price)
        }
    }

    val count: BigDecimal = if (type.pricedPerGram) gramsOf(weightMg).multiply(BigDecimal(qty)) else BigDecimal(qty)
    val makingPct = parsePercent(makingPercent)
    val taxPct = parsePercent(taxPercent)
    val isGift = acquisition == AcquisitionType.GIFT_RECEIVED
    // A gift costs nothing: every price component is 0 (value still counts in the portfolio).
    val pricing = if (isGift) {
        PurchasePricing.calculate(BigDecimal.ZERO, count, BigDecimal.ZERO, BigDecimal.ZERO)
    } else {
        PurchasePricing.calculate(
            unitPrice = MoneyUtils.parse(unitPrice),
            count = count,
            makingPercent = makingPct,
            taxPercent = taxPct,
            otherFees = MoneyUtils.parse(otherFees)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) "ویرایش دارایی" else "افزودن دارایی", fontWeight = FontWeight.Bold) },
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
            // ================= Photos & invoices: at the TOP of the form =================
            GalleryOptionCard(
                saveCameraToGallery = saveCameraToGallery,
                onToggle = { saveCameraToGallery = it },
                unsavedCameraCount = pendingImages.count { it.fromCamera && it.uri !in gallerySaved },
                onSaveAllNow = {
                    saveToGallery(pendingImages.filter { it.fromCamera && it.uri !in gallerySaved }.map { it.uri })
                }
            )
            AttachmentTypeSection(
                title = "عکس دارایی",
                type = AttachmentType.PHOTO,
                existing = existingAttachments.filter {
                    it.type == AttachmentType.PHOTO && it.id !in removedAttachmentIds
                },
                pending = pendingImages.filter { it.type == AttachmentType.PHOTO },
                onCamera = { openCamera(AttachmentType.PHOTO) },
                onGallery = { openGallery(AttachmentType.PHOTO) },
                onRemoveExisting = { removedAttachmentIds.add(it.id) },
                onRemovePending = {
                    pendingImages.remove(it)
                    AttachmentStorage.deleteCameraTemp(context, it.uri)
                },
                onPreview = { previewModel = it },
                onSaveToGallery = { saveToGallery(listOf(it.uri)) },
                onSaveExisting = { saveToGallery(listOf(it)) },
                gallerySaved = gallerySaved
            )
            AttachmentTypeSection(
                title = "تصویر فاکتور",
                type = AttachmentType.INVOICE,
                existing = existingAttachments.filter {
                    it.type == AttachmentType.INVOICE && it.id !in removedAttachmentIds
                },
                pending = pendingImages.filter { it.type == AttachmentType.INVOICE },
                onCamera = { openCamera(AttachmentType.INVOICE) },
                onGallery = { openGallery(AttachmentType.INVOICE) },
                onRemoveExisting = { removedAttachmentIds.add(it.id) },
                onRemovePending = {
                    pendingImages.remove(it)
                    AttachmentStorage.deleteCameraTemp(context, it.uri)
                },
                onPreview = { previewModel = it },
                onSaveToGallery = { saveToGallery(listOf(it.uri)) },
                onSaveExisting = { saveToGallery(listOf(it)) },
                gallerySaved = gallerySaved
            )

            // ================= Basic info =================
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("نام") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Which Persian date this asset is recorded on (read-only) ...
            AppCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(
                            if (isEdit) "تاریخ ثبت در برنامه" else "تاریخ ثبت (امروز)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val reg = originalCreatedAt ?: registeredAtPreview
                        Text(
                            JalaliCalendar.formatFull(reg) + " — " + JalaliCalendar.formatTime(reg),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            // ... and the purchase date, chosen with the Persian calendar.
            JalaliDateField(
                label = if (acquisition == AcquisitionType.GIFT_RECEIVED) "تاریخ دریافت هدیه" else "تاریخ خرید",
                millis = purchaseDate,
                onChange = { purchaseDate = it },
                modifier = Modifier.fillMaxWidth()
            )

            // ================= Type: drives the automatic calculation for EVERY asset type =================
            ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                OutlinedTextField(
                    value = type.persianName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("نوع") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                    AssetType.entries.forEach { t ->
                        DropdownMenuItem(
                            text = { Text("${t.category.persianName} · ${t.persianName}") },
                            onClick = {
                                type = t
                                purity = when (t) {
                                    AssetType.GOLD_24K -> "24"
                                    AssetType.GOLD_18K, AssetType.JEWELRY -> "18"
                                    else -> purity
                                }
                                // Pre-fill the reference weight of standard coins only if the user
                                // hasn't typed a weight; it remains fully editable.
                                val std = standardCoinWeightMg(t)
                                if (weightValue.isBlank() || weightAutoFilled) {
                                    if (std != null) {
                                        weightValue = WeightConverter.format(std, WeightUnit.GRAM)
                                        weightUnit = WeightUnit.GRAM
                                        weightAutoFilled = true
                                    } else if (weightAutoFilled) {
                                        weightValue = ""
                                        weightAutoFilled = false
                                    }
                                }
                                // Gold bank = melted gold only, grams / milligrams, one lot, fixed 750 fineness.
                                if (t.isGoldBank) {
                                    purity = "18"
                                    quantity = "1"
                                    if (weightUnit !in t.allowedWeightUnits) weightUnit = WeightUnit.GRAM
                                }
                                // A new type = start from today's market price again.
                                priceAuto = true
                                typeExpanded = false
                            }
                        )
                    }
                }
            }

            if (type.isGoldBank) {
                Text(
                    "بانک طلا: فقط طلای آب‌شده است و وزن آن را به گرم یا میلی‌گرم وارد می‌کنید. ارزش آن با قیمت طلای آب‌شده محاسبه می‌شود.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ---- How it was acquired: bought, or received as a gift ----
            Text("نحوه‌ی دریافت", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AcquisitionType.entries.forEach { a ->
                    FilterChip(
                        selected = acquisition == a,
                        onClick = {
                            acquisition = a
                            if (a == AcquisitionType.PURCHASED && unitPrice.isBlank()) priceAuto = true
                        },
                        label = { Text(a.persianName) }
                    )
                }
            }

            // ---- Quantity ----
            if (!type.isGoldBank) OutlinedTextField(
                value = quantity,
                onValueChange = { quantity = MoneyInputFormatter.sanitize(it, allowDecimal = false).text },
                label = { Text(if (type.isCoin) "تعداد سکه" else "تعداد (تکه)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            // ---- Weight (+ unit) ----
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                OutlinedTextField(
                    value = weightValue,
                    onValueChange = {
                        weightValue = MoneyInputFormatter.sanitize(it, allowDecimal = true).text
                        weightAutoFilled = false
                    },
                    label = { Text(if (type.isCoin) "وزن هر سکه" else "وزن") },
                    modifier = Modifier.weight(1.6f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                ExposedDropdownMenuBox(
                    expanded = unitExpanded,
                    onExpandedChange = { unitExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = weightUnit.persianName,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text("واحد") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(unitExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = unitExpanded, onDismissRequest = { unitExpanded = false }) {
                        type.allowedWeightUnits.forEach { u ->
                            DropdownMenuItem(
                                text = { Text(u.persianName) },
                                onClick = { weightUnit = u; unitExpanded = false }
                            )
                        }
                    }
                }
            }

            if (weightMg > 0) {
                Text(
                    "معادل: ${WeightConverter.format(weightMg, WeightUnit.GRAM)} گرم | " +
                        "${WeightConverter.format(weightMg, WeightUnit.SOOT)} سوت | " +
                        "${WeightConverter.format(weightMg, WeightUnit.MITHQAL)} مثقال",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )
                if (weightAutoFilled) {
                    Text(
                        "وزن استاندارد مرجع این سکه درج شد؛ در صورت نیاز وزن واقعی را وارد کنید.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!type.isGoldBank) OutlinedTextField(
                value = purity,
                onValueChange = { purity = MoneyInputFormatter.sanitize(it, allowDecimal = false).text },
                label = { Text("عیار") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            if (acquisition == AcquisitionType.GIFT_RECEIVED) {
                GiftInfoCard(
                    estimatedValue = marketUnit?.price?.multiply(count)
                        ?.setScale(0, RoundingMode.HALF_UP)
                )
            } else {
                // ================= Pricing: live calculation =================
                val unitLabel = if (type.pricedPerGram) "هر گرم" else "هر عدد"
                MoneyTextField(
                    value = unitPrice,
                    onValueChange = {
                        unitPrice = it
                        // A real manual edit: stop auto-updating so the user's value is respected.
                        priceAuto = false
                    },
                    label = "قیمت واحد ($unitLabel) - تومان",
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { UnitPriceHint(type, marketUnit, priceAuto, unitLabel) }
                )
                if (!priceAuto && marketUnit != null) {
                    TextButton(onClick = { priceAuto = true }) {
                        Icon(Icons.Default.Autorenew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("بازگشت به قیمت روز (${MoneyUtils.format(marketUnit.price)} تومان)")
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PercentTextField(
                        value = makingPercent,
                        onValueChange = { makingPercent = it },
                        label = "اجرت ساخت (درصد)",
                        modifier = Modifier.weight(1f)
                    )
                    PercentTextField(
                        value = taxPercent,
                        onValueChange = { taxPercent = it },
                        label = "مالیات (درصد)",
                        modifier = Modifier.weight(1f)
                    )
                }

                MoneyTextField(
                    value = otherFees,
                    onValueChange = { otherFees = it },
                    label = "سایر هزینه‌ها (اختیاری) - تومان",
                    modifier = Modifier.fillMaxWidth()
                )

                PriceSummaryCard(
                    type = type,
                    count = count,
                    unitPrice = MoneyUtils.parse(unitPrice),
                    makingPct = makingPct,
                    taxPct = taxPct,
                    pricing = pricing
                )

            }

            OutlinedTextField(
                value = seller,
                onValueChange = { seller = it },
                label = { Text(if (isGift) "از طرف چه کسی (اختیاری)" else "فروشنده / فروشگاه") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("یادداشت") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
            }

            Button(
                enabled = !isSaving,
                onClick = {
                    if (name.isBlank()) {
                        error = "نام الزامی است"
                        return@Button
                    }
                    if (!isGift && (!PurchasePricing.isValidPercent(makingPct) || !PurchasePricing.isValidPercent(taxPct))) {
                        error = "درصد اجرت و مالیات باید بین ۰ تا ۱۰۰ باشد"
                        return@Button
                    }
                    if (type.pricedPerGram && weightMg <= 0) {
                        error = "وزن را وارد کنید"
                        return@Button
                    }
                    val pure = purity.toIntOrNull() ?: 18

                    val now = System.currentTimeMillis()
                    val asset = Asset(
                        id = assetId ?: 0L,
                        name = name.trim(),
                        type = type,
                        quantity = qty,
                        weightMg = weightMg,
                        purity = pure,
                        // Stored breakdown of the calculated price (see PurchasePricing)
                        purchasePrice = pricing.base,
                        purchaseDate = purchaseDate,
                        seller = seller.ifBlank { null },
                        makingCharge = pricing.making,
                        tax = pricing.tax,
                        otherFees = pricing.fees,
                        totalPurchaseCost = pricing.total,
                        notes = notes.ifBlank { null },
                        isCoin = type.isCoin,
                        coinType = if (type.isCoin) type.name else null,
                        createdAt = originalCreatedAt ?: now,
                        status = originalAsset?.status ?: AssetStatus.ACTIVE,
                        statusDate = originalAsset?.statusDate,
                        soldPrice = originalAsset?.soldPrice,
                        statusNote = originalAsset?.statusNote,
                        acquisition = acquisition
                    )
                    isSaving = true
                    error = null
                    viewModel.saveAssetWithAttachments(
                        asset = asset,
                        newImages = pendingImages.map { it.uri to it.type },
                        removedAttachmentIds = removedAttachmentIds.toList()
                    ) { _, failed ->
                        saved = true
                        isSaving = false
                        if (failed > 0) {
                            android.widget.Toast.makeText(
                                context,
                                "$failed تصویر ذخیره نشد",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                        navController.popBackStack()
                    }
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("ذخیره")
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    previewModel?.let { model ->
        // Offer saving only for camera shots and already-stored images (not for images the user
        // just picked from the gallery, which are already there).
        val canSave = model !is Uri || pendingImages.any { it.uri == model && it.fromCamera }
        ImagePreviewDialog(
            model = model,
            onDismiss = { previewModel = null },
            onSaveToGallery = if (canSave) ({ saveToGallery(listOf(model)) }) else null
        )
    }
}

/**
 * Visible, near-the-top control for the optional "register photo in the phone's gallery" feature.
 * Ticking the box saves every photo taken with the camera into the gallery right after the shot;
 * the button saves the ones already taken. Nothing is saved unless the user chooses so.
 */
@Composable
private fun GalleryOptionCard(
    saveCameraToGallery: Boolean,
    onToggle: (Boolean) -> Unit,
    unsavedCameraCount: Int,
    onSaveAllNow: () -> Unit
) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = saveCameraToGallery, onCheckedChange = onToggle)
                Column(Modifier.weight(1f)) {
                    Text("ثبت عکس‌های دوربین در گالری گوشی", fontWeight = FontWeight.Bold)
                    Text(
                        "اختیاری است؛ اگر فعال کنید، هر عکسی که با دوربین می‌گیرید در گالری هم ذخیره می‌شود.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (unsavedCameraCount > 0) {
                OutlinedButton(onClick = onSaveAllNow, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("ذخیره عکس‌های گرفته‌شده در گالری (${JalaliCalendar.toPersianDigits(unsavedCameraCount.toString())})")
                }
            }
        }
    }
}

/** Where the suggested unit price comes from (or why it is unavailable). */
@Composable
private fun UnitPriceHint(
    type: AssetType,
    market: PortfolioCalculator.MarketUnitPrice?,
    priceAuto: Boolean,
    unitLabel: String
) {
    val text = when {
        market == null && type == AssetType.OTHER -> "قیمت واحد را دستی وارد کنید."
        market == null -> "قیمت روز «${type.persianName}» در دسترس نیست؛ قیمت‌ها را به‌روز کنید یا قیمت را دستی وارد کنید."
        else -> {
            val mode = if (priceAuto) "قیمت روز" else "قیمت دستی؛ قیمت روز"
            val cached = if (market.isCached) " (ذخیره‌شده)" else ""
            "$mode ${market.priceType.persianName} ($unitLabel): ${MoneyUtils.format(market.price)} تومان$cached"
        }
    }
    Text(text, style = MaterialTheme.typography.bodySmall)
}

/** Final price, shown clearly and updated instantly while typing. */
@Composable
private fun PriceSummaryCard(
    type: AssetType,
    count: BigDecimal,
    unitPrice: BigDecimal,
    makingPct: BigDecimal,
    taxPct: BigDecimal,
    pricing: PurchasePricing.Result
) {
    fun pct(p: BigDecimal) = JalaliCalendar.toPersianDigits(p.stripTrailingZeros().toPlainString())
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "محاسبه خودکار قیمت نهایی",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            val countText = JalaliCalendar.toPersianDigits(count.setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString())
            SummaryRow(
                "مبلغ پایه ($countText ${if (type.pricedPerGram) "گرم" else "عدد"} × ${MoneyUtils.format(unitPrice)})",
                MoneyUtils.format(pricing.base)
            )
            SummaryRow("اجرت ساخت (${pct(makingPct)}٪)", MoneyUtils.format(pricing.making))
            SummaryRow("مالیات (${pct(taxPct)}٪)", MoneyUtils.format(pricing.tax))
            if (pricing.fees.signum() > 0) SummaryRow("سایر هزینه‌ها", MoneyUtils.format(pricing.fees))
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.25f))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "قیمت نهایی",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "${MoneyUtils.format(pricing.total)} تومان",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun AttachmentTypeSection(
    title: String,
    type: AttachmentType,
    existing: List<Attachment>,
    pending: List<PendingImage>,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onRemoveExisting: (Attachment) -> Unit,
    onRemovePending: (PendingImage) -> Unit,
    onPreview: (Any) -> Unit,
    onSaveToGallery: (PendingImage) -> Unit,
    onSaveExisting: (java.io.File) -> Unit,
    gallerySaved: List<Uri>
) {
    val context = LocalContext.current
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCamera) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("دوربین")
                }
                OutlinedButton(onClick = onGallery) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("گالری")
                }
            }
            if (existing.isEmpty() && pending.isEmpty()) {
                Text(
                    "تصویری انتخاب نشده است",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    existing.forEach { att ->
                        val file = remember(att.filePath) { AttachmentStorage.resolve(context, att.filePath) }
                        AttachmentThumbnail(
                            model = file,
                            contentDescription = att.fileName,
                            onClick = { onPreview(file) },
                            onRemove = { onRemoveExisting(att) },
                            // Photos already stored in the app can be copied to the gallery any time
                            onSaveToGallery = { onSaveExisting(file) }
                        )
                    }
                    pending.forEach { p ->
                        AttachmentThumbnail(
                            model = p.uri,
                            contentDescription = type.name,
                            onClick = { onPreview(p.uri) },
                            onRemove = { onRemovePending(p) },
                            // Optional: only photos taken with the camera get a save-to-gallery button
                            onSaveToGallery = if (p.fromCamera) ({ onSaveToGallery(p) }) else null,
                            savedToGallery = p.uri in gallerySaved
                        )
                    }
                }
            }
        }
    }
}

/** Shown instead of the price fields when the asset was received as a gift. */
@Composable
private fun GiftInfoCard(estimatedValue: BigDecimal?) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "دریافت به‌عنوان هدیه",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                "برای این دارایی هزینه‌ای ثبت نمی‌شود (قیمت خرید صفر). ارزش آن با قیمت روز بازار در پورتفوی شما " +
                    "حساب می‌شود و هر زمان بخواهید می‌توانید آن را بفروشید. سود و زیان پورتفوی بدون احتساب هدایا محاسبه می‌شود.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            if (estimatedValue != null && estimatedValue.signum() > 0) {
                Text(
                    "ارزش تخمینی امروز: ${MoneyUtils.format(estimatedValue)} تومان",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}
