package com.talayeman.gold.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.talayeman.gold.data.local.AppDatabase
import com.talayeman.gold.data.remote.MarketPriceService
import com.talayeman.gold.data.repository.AssetRepository
import com.talayeman.gold.data.repository.MarketRepository
import com.talayeman.gold.data.repository.SettingsRepository
import com.talayeman.gold.domain.model.*
import com.talayeman.gold.domain.usecase.PortfolioCalculator
import com.talayeman.gold.util.AttachmentStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    val assetRepo = AssetRepository(db.assetDao(), db.attachmentDao())
    val marketRepo = MarketRepository(db.marketPriceDao(), MarketPriceService())
    val settingsRepo = SettingsRepository(db.settingsDao())
    private val portfolioCalculator = PortfolioCalculator()

    val assets: StateFlow<List<Asset>> = assetRepo.getAllAssets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val prices: StateFlow<List<MarketPrice>> = marketRepo.getAllPrices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val priceMap: StateFlow<Map<PriceType, MarketPrice>> = prices
        .map { list -> list.associateBy { it.priceType } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val portfolio: StateFlow<PortfolioSummary> = combine(assets, priceMap) { assetList, priceM ->
        portfolioCalculator.calculate(assetList, priceM)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PortfolioSummary())

    val themeMode: StateFlow<ThemeMode> = settingsRepo.getThemeMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val currency: StateFlow<Currency> = settingsRepo.getCurrency()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Currency.TOMAN)

    val biometricEnabled: StateFlow<Boolean> = settingsRepo.isBiometricEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _refreshError = MutableStateFlow<String?>(null)
    val refreshError: StateFlow<String?> = _refreshError.asStateFlow()

    init {
        refreshPrices()
    }

    fun refreshPrices() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _refreshError.value = null
            val result = marketRepo.refreshPrices()
            if (result.isFailure) {
                _refreshError.value = "خطا در دریافت قیمت. از آخرین قیمت ذخیره‌شده استفاده می‌شود."
            }
            _isRefreshing.value = false
        }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { settingsRepo.setThemeMode(mode) }
    }

    fun setCurrency(c: Currency) {
        viewModelScope.launch { settingsRepo.setCurrency(c) }
    }

    fun setBiometric(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setBiometricEnabled(enabled) }
    }

    fun deleteAsset(id: Long) {
        viewModelScope.launch {
            // Remove the stored image files too (rows are removed by the repository).
            val files = runCatching { assetRepo.getAttachmentsOnce(id) }.getOrDefault(emptyList())
            assetRepo.deleteAsset(id)
            withContext(Dispatchers.IO) {
                files.forEach { AttachmentStorage.delete(getApplication<Application>(), it.filePath) }
            }
        }
    }

    /**
     * Saves the asset, then imports newly picked/captured images into app storage and
     * removes attachments the user deleted in the form.
     * [onSaved] receives the asset id and how many images could not be imported.
     */
    fun saveAssetWithAttachments(
        asset: Asset,
        newImages: List<Pair<Uri, AttachmentType>>,
        removedAttachmentIds: List<Long>,
        onSaved: (Long, Int) -> Unit
    ) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val id = if (asset.id == 0L) {
                assetRepo.insertAsset(asset)
            } else {
                assetRepo.updateAsset(asset)
                asset.id
            }

            removedAttachmentIds.forEach { attId ->
                val att = assetRepo.getAttachmentById(attId)
                if (att != null && att.assetId == id) {
                    assetRepo.deleteAttachment(attId)
                    withContext(Dispatchers.IO) { AttachmentStorage.delete(app, att.filePath) }
                }
            }

            var failed = 0
            newImages.forEach { (uri, type) ->
                val stored = withContext(Dispatchers.IO) {
                    AttachmentStorage.importImage(app, uri, type).also {
                        AttachmentStorage.deleteCameraTemp(app, uri)
                    }
                }
                if (stored == null) {
                    failed++
                } else {
                    assetRepo.addAttachment(
                        Attachment(
                            assetId = id,
                            type = type,
                            filePath = stored.relativePath,
                            fileName = stored.fileName,
                            mimeType = "image/jpeg",
                            fileSize = stored.size
                        )
                    )
                }
            }
            onSaved(id, failed)
        }
    }

    fun saveAsset(asset: Asset, onSaved: (Long) -> Unit) {
        viewModelScope.launch {
            val id = if (asset.id == 0L) {
                assetRepo.insertAsset(asset)
            } else {
                assetRepo.updateAsset(asset)
                asset.id
            }
            onSaved(id)
        }
    }
}
