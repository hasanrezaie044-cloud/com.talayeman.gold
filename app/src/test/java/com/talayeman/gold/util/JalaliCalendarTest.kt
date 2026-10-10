package com.talayeman.gold.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class JalaliCalendarTest {
    private fun j(y: Int, m: Int, d: Int) = JalaliCalendar.fromLocalDate(LocalDate.of(y, m, d))

    @Test
    fun knownDatesConvertCorrectly() {
        assertEquals(JalaliDate(1405, 7, 17), j(2026, 10, 9))
        assertEquals(JalaliDate(1404, 1, 1), j(2025, 3, 21))
        assertEquals(JalaliDate(1403, 1, 1), j(2024, 3, 20))
        assertEquals(JalaliDate(1402, 12, 29), j(2024, 3, 19))
        assertEquals(JalaliDate(1378, 10, 11), j(2000, 1, 1))
    }

    @Test
    fun roundTripIsStable() {
        var d = LocalDate.of(2018, 1, 1)
        repeat(3000) {
            val jd = JalaliCalendar.fromLocalDate(d)
            assertEquals(d, jd.toLocalDate())
            d = d.plusDays(1)
        }
    }

    @Test
    fun monthLengths() {
        assertEquals(31, JalaliCalendar.daysInMonth(1405, 1))
        assertEquals(30, JalaliCalendar.daysInMonth(1405, 8))
        assertEquals(30, JalaliCalendar.daysInMonth(1403, 12)) // leap year
        assertEquals(29, JalaliCalendar.daysInMonth(1404, 12))
        assertTrue(JalaliCalendar.isLeapYear(1403))
    }

    @Test
    fun formatting() {
        assertEquals("۱۴۰۵/۰۷/۱۷", JalaliDate(1405, 7, 17).format())
        assertEquals("۱۷ مهر ۱۴۰۵", JalaliDate(1405, 7, 17).formatLong())
    }
}
