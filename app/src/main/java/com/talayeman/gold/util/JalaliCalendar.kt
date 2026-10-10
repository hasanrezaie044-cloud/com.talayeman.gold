package com.talayeman.gold.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A date in the Persian (Jalali / Solar Hijri) calendar. */
data class JalaliDate(val year: Int, val month: Int, val day: Int) {
    val monthName: String get() = JalaliCalendar.MONTH_NAMES[month - 1]

    /** 1405/07/17 (Persian digits). */
    fun format(): String = JalaliCalendar.toPersianDigits("%04d/%02d/%02d".format(year, month, day))

    /** ۱۷ مهر ۱۴۰۵ */
    fun formatLong(): String = JalaliCalendar.toPersianDigits("$day $monthName $year")

    fun toLocalDate(): LocalDate {
        val g = JalaliCalendar.jalaliToGregorian(year, month, day)
        return LocalDate.of(g[0], g[1], g[2])
    }
}

/**
 * Persian calendar helpers (no external library). Dates are stored in the database as epoch
 * milliseconds, exactly as before; only the *presentation* and the date picker are Jalali.
 */
object JalaliCalendar {
    val MONTH_NAMES = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    // java.time.DayOfWeek: MONDAY=1 .. SUNDAY=7
    private val WEEKDAY_NAMES = mapOf(
        6 to "شنبه", 7 to "یکشنبه", 1 to "دوشنبه", 2 to "سه‌شنبه",
        3 to "چهارشنبه", 4 to "پنجشنبه", 5 to "جمعه"
    )

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val PERSIAN_DIGITS = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

    fun toPersianDigits(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) sb.append(if (c in '0'..'9') PERSIAN_DIGITS[c - '0'] else c)
        return sb.toString()
    }

    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): IntArray {
        val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + gdm[gm - 1]
        var jy = -1595 + (33 * (days / 12053))
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm = if (days < 186) 1 + days / 31 else 7 + (days - 186) / 30
        val jd = 1 + (if (days < 186) days % 31 else (days - 186) % 30)
        return intArrayOf(jy, jm, jd)
    }

    fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): IntArray {
        val jy2 = jy + 1595
        var days = -355668 + (365 * jy2) + ((jy2 / 33) * 8) + (((jy2 % 33) + 3) / 4) + jd +
            (if (jm < 7) (jm - 1) * 31 else ((jm - 7) * 30) + 186)
        var gy = 400 * (days / 146097)
        days %= 146097
        if (days > 36524) {
            days--
            gy += 100 * (days / 36524)
            days %= 36524
            if (days >= 365) days++
        }
        gy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            gy += (days - 1) / 365
            days = (days - 1) % 365
        }
        var gd = days + 1
        val leap = (gy % 4 == 0 && gy % 100 != 0) || gy % 400 == 0
        val sal = intArrayOf(0, 31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gm = 0
        while (gm < 13 && gd > sal[gm]) {
            gd -= sal[gm]
            gm++
        }
        return intArrayOf(gy, gm, gd)
    }

    fun fromLocalDate(d: LocalDate): JalaliDate {
        val j = gregorianToJalali(d.year, d.monthValue, d.dayOfMonth)
        return JalaliDate(j[0], j[1], j[2])
    }

    fun fromMillis(millis: Long): JalaliDate =
        fromLocalDate(Instant.ofEpochMilli(millis).atZone(zone).toLocalDate())

    fun today(): JalaliDate = fromLocalDate(LocalDate.now(zone))

    fun isLeapYear(year: Int): Boolean = daysInMonth(year, 12) == 30

    fun daysInMonth(year: Int, month: Int): Int = when {
        month in 1..6 -> 31
        month in 7..11 -> 30
        else -> {
            // Esfand has 30 days only in leap years: check by round-tripping day 30.
            val g = jalaliToGregorian(year, 12, 30)
            val back = gregorianToJalali(g[0], g[1], g[2])
            if (back[0] == year && back[1] == 12 && back[2] == 30) 30 else 29
        }
    }

    /** Epoch millis (device time zone) at 12:00 of the given Jalali date. */
    fun toMillis(date: JalaliDate): Long =
        date.toLocalDate().atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    /** Keeps the time-of-day of [original] while moving it to [date]. */
    fun withDate(original: Long, date: JalaliDate): Long {
        val time = Instant.ofEpochMilli(original).atZone(zone).toLocalTime()
        return date.toLocalDate().atTime(time).atZone(zone).toInstant().toEpochMilli()
    }

    fun weekdayName(millis: Long): String {
        val dow = Instant.ofEpochMilli(millis).atZone(zone).dayOfWeek.value
        return WEEKDAY_NAMES.getValue(dow)
    }

    /** ۱۴۰۵/۰۷/۱۷ */
    fun formatDate(millis: Long): String = fromMillis(millis).format()

    /** ۱۴۰۵/۰۷/۱۷  ۱۴:۳۰ */
    fun formatDateTime(millis: Long): String {
        val t = Instant.ofEpochMilli(millis).atZone(zone)
        return "${formatDate(millis)}  " + toPersianDigits("%02d:%02d".format(t.hour, t.minute))
    }

    fun formatTime(millis: Long): String {
        val t = Instant.ofEpochMilli(millis).atZone(zone)
        return toPersianDigits("%02d:%02d".format(t.hour, t.minute))
    }

    /** پنجشنبه ۱۷ مهر ۱۴۰۵ */
    fun formatFull(millis: Long): String = "${weekdayName(millis)} ${fromMillis(millis).formatLong()}"

    fun todayFull(): String = formatFull(System.currentTimeMillis())
}
