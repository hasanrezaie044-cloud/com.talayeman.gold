package com.talayeman.gold

import com.talayeman.gold.util.MarketHours
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class MarketHoursTest {
    private fun tehran(h: Int, m: Int) = ZonedDateTime.of(2026, 10, 14, h, m, 0, 0, MarketHours.ZONE)

    @Test
    fun openBetween11And18() {
        assertTrue(MarketHours.isOpen(tehran(11, 0)))
        assertTrue(MarketHours.isOpen(tehran(14, 30)))
        assertTrue(MarketHours.isOpen(tehran(17, 59)))
    }

    @Test
    fun closedOutsideHours() {
        assertFalse(MarketHours.isOpen(tehran(10, 59)))
        assertFalse(MarketHours.isOpen(tehran(18, 0)))
        assertFalse(MarketHours.isOpen(tehran(23, 0)))
        assertFalse(MarketHours.isOpen(tehran(3, 0)))
    }

    @Test
    fun usesTehranTimeNotDeviceZone() {
        // 12:00 in Tehran is 08:30 UTC -> must still count as open when expressed in UTC.
        val utc = tehran(12, 0).withZoneSameInstant(ZoneId.of("UTC"))
        assertTrue(MarketHours.isOpen(utc))
    }
}
