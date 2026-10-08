package com.talayeman.gold.worker

import android.content.Context
import androidx.work.*
import com.talayeman.gold.data.local.AppDatabase
import com.talayeman.gold.data.repository.MarketRepository
import com.talayeman.gold.data.repository.SettingsRepository
import com.talayeman.gold.data.remote.MarketPriceService
import com.talayeman.gold.service.NotificationHelper
import java.util.Calendar
import java.util.concurrent.TimeUnit

class DailyPriceNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getInstance(applicationContext)
        val settingsRepo = SettingsRepository(db.settingsDao())
        val enabled = settingsRepo.get(SettingsRepository.KEY_DAILY_PRICE_NOTIF) != "false"
        if (!enabled) return Result.success()

        // Try refresh first
        val marketRepo = MarketRepository(db.marketPriceDao(), MarketPriceService())
        marketRepo.refreshPrices()

        NotificationHelper.showDailyPriceNotification(applicationContext, db)
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "daily_price_notification"

        fun schedule(context: Context, hour: Int = 9, minute: Int = 0) {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
            }
            val delay = target.timeInMillis - now.timeInMillis

            val request = PeriodicWorkRequestBuilder<DailyPriceNotificationWorker>(
                24, TimeUnit.HOURS
            )
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
