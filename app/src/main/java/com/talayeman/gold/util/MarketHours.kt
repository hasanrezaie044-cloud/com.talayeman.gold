package com.talayeman.gold.util

import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Iranian gold & coin market hours: 11:00 (inclusive) to 18:00 (exclusive), Iran time (Asia/Tehran).
 * Outside this window price updates are NOT fetched (the last stored prices stay in use).
 * The check always uses Tehran time, whatever time zone the phone is set to.
 */
object MarketHours {
    val ZONE: ZoneId = ZoneId.of("Asia/Tehran")
    val OPEN_TIME: LocalTime = LocalTime.of(11, 0)
    val CLOSE_TIME: LocalTime = LocalTime.of(18, 0)

    const val CLOSED_MESSAGE = "بازار بسته است"

    fun isOpen(now: ZonedDateTime = ZonedDateTime.now(ZONE)): Boolean {
        val t = now.withZoneSameInstant(ZONE).toLocalTime()
        return !t.isBefore(OPEN_TIME) && t.isBefore(CLOSE_TIME)
    }
}

/** Returned (as a failed Result) when a price update is requested while the market is closed. */
class MarketClosedException : Exception(MarketHours.CLOSED_MESSAGE)
