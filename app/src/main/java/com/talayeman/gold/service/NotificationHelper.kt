package com.talayeman.gold.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.talayeman.gold.MainActivity
import com.talayeman.gold.R
import com.talayeman.gold.data.local.AppDatabase
import com.talayeman.gold.data.repository.SettingsRepository
import com.talayeman.gold.domain.model.PriceType
import com.talayeman.gold.util.MoneyUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NotificationHelper {

    const val CHANNEL_PRICE = "price_updates"
    const val CHANNEL_ALERTS = "price_alerts"
    const val CHANNEL_PORTFOLIO = "portfolio"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channels = listOf(
                NotificationChannel(CHANNEL_PRICE, "به‌روزرسانی قیمت طلا", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_ALERTS, "هشدارهای قیمت", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CHANNEL_PORTFOLIO, "پورتفوی طلا", NotificationManager.IMPORTANCE_DEFAULT)
            )
            manager?.createNotificationChannels(channels)
        }
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    suspend fun showDailyPriceNotification(context: Context, db: AppDatabase) {
        withContext(Dispatchers.IO) {
            val settings = SettingsRepository(db.settingsDao())
            val showFinance = settings.get(SettingsRepository.KEY_SHOW_FINANCE_IN_NOTIF) != "false"
            val prices = db.marketPriceDao().getAllPrices()
            // Collect first emission is not possible easily; use a one-shot approach via Room
            // For simplicity, build a generic or detailed message

            val title = "به‌روزرسانی بازار طلا"
            val body = if (showFinance) {
                "قیمت‌های جدید طلا و سکه دریافت شد. برای مشاهده جزئیات وارد برنامه شوید."
            } else {
                "به‌روزرسانی قیمت طلا آماده است."
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_PRICE)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent(context))
                .setAutoCancel(true)
                .build()

            try {
                NotificationManagerCompat.from(context).notify(1001, notification)
            } catch (_: SecurityException) {
                // Permission not granted
            }
        }
    }

    suspend fun showPortfolioNotification(
        context: Context,
        purchase: String,
        current: String,
        profit: String,
        isProfit: Boolean
    ) {
        val settings = SettingsRepository(AppDatabase.getInstance(context).settingsDao())
        val showFinance = settings.get(SettingsRepository.KEY_SHOW_FINANCE_IN_NOTIF) != "false"

        val title = "وضعیت پورتفوی طلا"
        val body = if (showFinance) {
            if (isProfit) {
                "ارزش فعلی: $current تومان | سود تخمینی: $profit تومان"
            } else {
                "ارزش فعلی: $current تومان | ضرر تخمینی: $profit تومان"
            }
        } else {
            "پورتفوی طلای شما به‌روزرسانی شد."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_PORTFOLIO)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent(context))
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(1002, notification)
        } catch (_: SecurityException) {}
    }

    suspend fun checkAndNotifyAlerts(context: Context, db: AppDatabase) {
        // Implementation: load enabled alerts, compare with current prices, notify if triggered
        // Placeholder for full logic — alerts are checked after each price refresh
    }
}
