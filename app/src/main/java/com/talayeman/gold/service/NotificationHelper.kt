package com.talayeman.gold.service

import android.annotation.SuppressLint
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
import com.talayeman.gold.data.remote.MarketPriceService
import com.talayeman.gold.data.repository.AssetRepository
import com.talayeman.gold.data.repository.MarketRepository
import com.talayeman.gold.data.repository.SettingsRepository
import com.talayeman.gold.domain.model.MarketPrice
import com.talayeman.gold.domain.model.PriceType
import com.talayeman.gold.domain.usecase.PortfolioCalculator
import com.talayeman.gold.util.JalaliCalendar
import com.talayeman.gold.util.MoneyUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode

object NotificationHelper {

    const val CHANNEL_PRICE = "price_updates"
    const val CHANNEL_ALERTS = "price_alerts"
    const val CHANNEL_PORTFOLIO = "portfolio"

    private const val ID_DAILY = 1001
    private const val ID_PRICE_UPDATE = 1010
    private const val ID_PROFIT_LOSS = 1011

    /** Portfolio profit/loss is re-announced only if it moved by at least this many percentage points. */
    private val PL_REANNOUNCE_STEP = BigDecimal.ONE

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channels = listOf(
                NotificationChannel(CHANNEL_PRICE, "به‌روزرسانی قیمت طلا", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_ALERTS, "هشدارهای قیمت", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CHANNEL_PORTFOLIO, "سود و زیان پورتفوی", NotificationManager.IMPORTANCE_DEFAULT)
            )
            manager?.createNotificationChannels(channels)
        }
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Posts a notification. [genericText] is what the LOCK SCREEN shows (never financial data);
     * [text] is the full message shown in the shade when the user allows financial details.
     */
    @SuppressLint("MissingPermission")
    private fun post(
        context: Context,
        channel: String,
        id: Int,
        title: String,
        text: String,
        genericText: String,
        showDetails: Boolean
    ) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val body = if (showDetails) text else genericText
        val publicVersion = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_gold)
            .setContentTitle(title)
            .setContentText(genericText)
            .build()
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_gold)
            .setContentTitle(title)
            .setContentText(body.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent(context))
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS revoked in the meantime
        }
    }

    suspend fun showDailyPriceNotification(context: Context, db: AppDatabase) {
        withContext(Dispatchers.IO) {
            val settings = SettingsRepository(db.settingsDao())
            val showFinance = settings.getBool(SettingsRepository.KEY_SHOW_FINANCE_IN_NOTIF, true)
            post(
                context, CHANNEL_PRICE, ID_DAILY,
                title = "به‌روزرسانی بازار طلا",
                text = "قیمت‌های جدید طلا و سکه دریافت شد. برای مشاهده جزئیات وارد برنامه شوید.",
                genericText = "به‌روزرسانی قیمت طلا آماده است.",
                showDetails = showFinance
            )
        }
    }

    /**
     * Called by the background worker right after a SUCCESSFUL price refresh.
     * Sends (each one can be switched off in Settings):
     *  1. "prices updated" – only if at least one price actually changed,
     *  2. "profit" / 3. "loss" – only when the portfolio flips between profit and loss, or its
     *     profit/loss percentage moved by at least 1 point since the last notification (no spam).
     * Sold / gifted assets are ignored (the calculator only counts active assets).
     */
    suspend fun handlePriceUpdate(
        context: Context,
        db: AppDatabase,
        previous: Map<PriceType, MarketPrice>
    ) = withContext(Dispatchers.IO) {
        val settings = SettingsRepository(db.settingsDao())
        val showFinance = settings.getBool(SettingsRepository.KEY_SHOW_FINANCE_IN_NOTIF, true)
        val current = MarketRepository(db.marketPriceDao(), MarketPriceService())
            .getAllPrices().first().associateBy { it.priceType }
        if (current.isEmpty()) return@withContext

        // ---- 1) price update ----
        if (settings.getBool(SettingsRepository.KEY_NOTIF_PRICE_UPDATE, true)) {
            val changed = current.any { (type, p) ->
                val old = previous[type]?.price
                old == null || old.compareTo(p.price) != 0
            }
            if (changed) {
                val lines = listOf(PriceType.GOLD_18K, PriceType.GOLD_24K, PriceType.EMAMI).mapNotNull { type ->
                    val p = current[type] ?: return@mapNotNull null
                    val old = previous[type]?.price
                    val change = if (old != null && old.signum() > 0 && old.compareTo(p.price) != 0) {
                        val pct = p.price.subtract(old).multiply(BigDecimal(100))
                            .divide(old, 2, RoundingMode.HALF_UP)
                        "  (" + (if (pct.signum() > 0) "▲ " else "▼ ") + pct.abs().toPlainString() + "٪)"
                    } else ""
                    "${type.persianName}: ${MoneyUtils.format(p.price, true)}$change"
                }
                val time = JalaliCalendar.formatDateTime(System.currentTimeMillis())
                post(
                    context, CHANNEL_PRICE, ID_PRICE_UPDATE,
                    title = "قیمت‌ها به‌روز شد",
                    text = (lines + "زمان: $time").joinToString("\n"),
                    genericText = "قیمت‌های طلا و سکه به‌روزرسانی شد.",
                    showDetails = showFinance
                )
            }
        }

        // ---- 2) / 3) profit or loss ----
        val profitOn = settings.getBool(SettingsRepository.KEY_NOTIF_PROFIT, true)
        val lossOn = settings.getBool(SettingsRepository.KEY_NOTIF_LOSS, true)
        if (!profitOn && !lossOn) return@withContext

        val assets = AssetRepository(db.assetDao(), db.attachmentDao()).getAllAssets().first()
        val summary = PortfolioCalculator().calculate(assets, current)
        if (summary.assetCount == 0 ||
            summary.totalPurchaseCost.signum() <= 0 ||
            summary.currentEstimatedValue.signum() <= 0
        ) return@withContext

        val pl = summary.profitLoss
        val pct = summary.profitLossPercent
        val state = when {
            pl.signum() > 0 -> "PROFIT"
            pl.signum() < 0 -> "LOSS"
            else -> return@withContext
        }
        if ((state == "PROFIT" && !profitOn) || (state == "LOSS" && !lossOn)) return@withContext

        val lastState = settings.get(SettingsRepository.KEY_LAST_PL_STATE)
        val lastPct = settings.get(SettingsRepository.KEY_LAST_PL_PERCENT)?.toBigDecimalOrNull()
        val shouldNotify = lastState != state || lastPct == null ||
            pct.subtract(lastPct).abs().compareTo(PL_REANNOUNCE_STEP) >= 0
        if (!shouldNotify) return@withContext

        val isProfit = state == "PROFIT"
        val amount = MoneyUtils.format(pl.abs(), true)
        val sign = if (isProfit) "+" else "−"
        post(
            context, CHANNEL_PORTFOLIO, ID_PROFIT_LOSS,
            title = if (isProfit) "سود پورتفوی \uD83D\uDCC8" else "زیان پورتفوی \uD83D\uDCC9",
            text = listOf(
                "قیمت خرید: ${MoneyUtils.format(summary.totalPurchaseCost, true)}",
                "ارزش فعلی: ${MoneyUtils.format(summary.currentEstimatedValue, true)}",
                (if (isProfit) "سود تخمینی: " else "زیان تخمینی: ") + "$sign$amount (${pct.abs().toPlainString()}٪)"
            ).joinToString("\n"),
            genericText = "وضعیت پورتفوی طلای شما تغییر کرد.",
            showDetails = showFinance
        )
        settings.set(SettingsRepository.KEY_LAST_PL_STATE, state)
        settings.set(SettingsRepository.KEY_LAST_PL_PERCENT, pct.toPlainString())
    }

    suspend fun checkAndNotifyAlerts(context: Context, db: AppDatabase) {
        // Price alerts have no creation UI yet; evaluation will be added together with that screen.
    }
}
