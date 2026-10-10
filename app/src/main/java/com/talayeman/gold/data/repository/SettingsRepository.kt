package com.talayeman.gold.data.repository

import com.talayeman.gold.data.local.dao.SettingsDao
import com.talayeman.gold.data.local.entity.AppSettingsEntity
import com.talayeman.gold.domain.model.Currency
import com.talayeman.gold.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val settingsDao: SettingsDao) {

    companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_CURRENCY = "currency"
        const val KEY_BIOMETRIC = "biometric_enabled"
        const val KEY_DAILY_PRICE_NOTIF = "daily_price_notif"
        const val KEY_DAILY_PRICE_TIME = "daily_price_time" // HH:mm
        const val KEY_PROFIT_NOTIF = "profit_loss_notif"
        const val KEY_PROFIT_NOTIF_FREQ = "profit_notif_freq" // daily/weekly
        const val KEY_SHOW_FINANCE_IN_NOTIF = "show_finance_in_notif"
        const val KEY_AUTO_UPDATE = "auto_price_update"
        const val KEY_LAST_PRICE_UPDATE = "last_price_update"

        // Advanced notifications (all default to ON)
        const val KEY_NOTIF_PRICE_UPDATE = "notif_price_update"
        const val KEY_NOTIF_PROFIT = "notif_profit"
        const val KEY_NOTIF_LOSS = "notif_loss"

        // Last portfolio state we notified about (used to avoid spamming)
        const val KEY_LAST_PL_STATE = "last_pl_state"   // PROFIT | LOSS
        const val KEY_LAST_PL_PERCENT = "last_pl_percent"
    }

    fun getThemeMode(): Flow<ThemeMode> =
        settingsDao.getFlow(KEY_THEME).map {
            when (it?.value) {
                "LIGHT" -> ThemeMode.LIGHT
                "DARK" -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        settingsDao.upsert(AppSettingsEntity(KEY_THEME, mode.name))
    }

    fun getCurrency(): Flow<Currency> =
        settingsDao.getFlow(KEY_CURRENCY).map {
            Currency.TOMAN
        }

    suspend fun setCurrency(currency: Currency) {
        settingsDao.upsert(AppSettingsEntity(KEY_CURRENCY, currency.name))
    }

    fun isBiometricEnabled(): Flow<Boolean> =
        settingsDao.getFlow(KEY_BIOMETRIC).map { it?.value == "true" }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        settingsDao.upsert(AppSettingsEntity(KEY_BIOMETRIC, enabled.toString()))
    }

    fun isDailyPriceNotifEnabled(): Flow<Boolean> =
        settingsDao.getFlow(KEY_DAILY_PRICE_NOTIF).map { it?.value != "false" }

    suspend fun setDailyPriceNotif(enabled: Boolean) {
        settingsDao.upsert(AppSettingsEntity(KEY_DAILY_PRICE_NOTIF, enabled.toString()))
    }

    fun getDailyPriceTime(): Flow<String> =
        settingsDao.getFlow(KEY_DAILY_PRICE_TIME).map { it?.value ?: "09:00" }

    suspend fun setDailyPriceTime(time: String) {
        settingsDao.upsert(AppSettingsEntity(KEY_DAILY_PRICE_TIME, time))
    }

    fun showFinanceInNotif(): Flow<Boolean> =
        settingsDao.getFlow(KEY_SHOW_FINANCE_IN_NOTIF).map { it?.value != "false" }

    suspend fun setShowFinanceInNotif(show: Boolean) {
        settingsDao.upsert(AppSettingsEntity(KEY_SHOW_FINANCE_IN_NOTIF, show.toString()))
    }

    /** Boolean setting that is ON unless explicitly stored as "false". */
    fun boolFlow(key: String, defaultValue: Boolean = true): Flow<Boolean> =
        settingsDao.getFlow(key).map { entity ->
            when (entity?.value) {
                "true" -> true
                "false" -> false
                else -> defaultValue
            }
        }

    suspend fun setBool(key: String, value: Boolean) {
        settingsDao.upsert(AppSettingsEntity(key, value.toString()))
    }

    suspend fun getBool(key: String, defaultValue: Boolean = true): Boolean =
        when (settingsDao.get(key)?.value) {
            "true" -> true
            "false" -> false
            else -> defaultValue
        }

    suspend fun get(key: String): String? = settingsDao.get(key)?.value

    suspend fun set(key: String, value: String) {
        settingsDao.upsert(AppSettingsEntity(key, value))
    }
}
