package com.talayeman.gold.worker

import android.content.Context
import androidx.work.*
import com.talayeman.gold.data.local.AppDatabase
import com.talayeman.gold.data.remote.MarketPriceService
import com.talayeman.gold.data.repository.MarketRepository
import com.talayeman.gold.data.repository.SettingsRepository
import com.talayeman.gold.service.NotificationHelper
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class PriceUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getInstance(applicationContext)
        val marketRepo = MarketRepository(db.marketPriceDao(), MarketPriceService())
        val settingsRepo = SettingsRepository(db.settingsDao())

        return try {
            // Remember the previous prices so the notification can tell whether anything changed.
            val previous = marketRepo.getAllPrices().first().associateBy { it.priceType }
            val result = marketRepo.refreshPrices()
            if (result.isSuccess) {
                settingsRepo.set(SettingsRepository.KEY_LAST_PRICE_UPDATE, System.currentTimeMillis().toString())
                // Price-update / profit / loss notifications (each can be disabled in Settings)
                runCatching { NotificationHelper.handlePriceUpdate(applicationContext, db, previous) }
                NotificationHelper.checkAndNotifyAlerts(applicationContext, db)
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        // v2: the refresh interval changed from 6 h to 3 h; a new unique name makes WorkManager
        // pick up the new period (KEEP would silently retain the old 6 h schedule).
        const val WORK_NAME = "price_update_periodic_v2"
        private const val OLD_WORK_NAME = "price_update_periodic"
        const val INTERVAL_HOURS = 3L

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            WorkManager.getInstance(context).cancelUniqueWork(OLD_WORK_NAME)
            val request = PeriodicWorkRequestBuilder<PriceUpdateWorker>(
                INTERVAL_HOURS, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun enqueueOneTime(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<PriceUpdateWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
