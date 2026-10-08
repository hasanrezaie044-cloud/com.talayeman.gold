package com.talayeman.gold

import com.talayeman.gold.util.MoneyInputFormatter
import com.talayeman.gold.util.MoneyUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class MoneyInputFormatterTest {

    @Test
    fun groupsThousands() {
        assertEquals("1,000", MoneyInputFormatter.format("1000"))
        assertEquals("1,000,000", MoneyInputFormatter.format("1000000"))
        assertEquals("12,500,000", MoneyInputFormatter.format("12500000"))
        assertEquals("87,500,000", MoneyInputFormatter.format("87500000"))
        assertEquals("999", MoneyInputFormatter.format("999"))
        assertEquals("1,234.567", MoneyInputFormatter.format("1234.567"))
        assertEquals("", MoneyInputFormatter.format(""))
    }

    @Test
    fun caretMappingRoundTrips() {
        listOf("", "1", "1000", "12500000", "87500000", "1234.5", "0.25").forEach { raw ->
            val g = MoneyInputFormatter.group(raw)
            for (o in 0..raw.length) {
                val t = g.originalToTransformed(o)
                assertTrue(t in 0..g.formatted.length)
                assertEquals(o, g.transformedToOriginal(t))
            }
            for (t in 0..g.formatted.length) {
                assertTrue(g.transformedToOriginal(t) in 0..raw.length)
            }
        }
    }

    @Test
    fun sanitizeAcceptsPersianDigitsAndStripsSeparators() {
        assertEquals("85000000", MoneyInputFormatter.sanitize("۸۵۰۰۰۰۰۰").text)
        assertEquals("85000000", MoneyInputFormatter.sanitize("85,000,000").text)
        assertEquals("12", MoneyInputFormatter.sanitize("0012").text)
        assertEquals("0.5", MoneyInputFormatter.sanitize(".5").text)
        assertEquals("1.23", MoneyInputFormatter.sanitize("1.2.3").text)
        assertEquals("123", MoneyInputFormatter.sanitize("1.23", allowDecimal = false).text)
        assertEquals(2, MoneyInputFormatter.sanitize("85,000,000", cursor = 3).cursor)
    }

    @Test
    fun parseNeverSeesFormatting() {
        assertEquals(0, BigDecimal("87500000").compareTo(MoneyUtils.parse("87,500,000")))
        assertEquals(0, BigDecimal("87500000").compareTo(MoneyUtils.parse("۸۷,۵۰۰,۰۰۰")))
        assertEquals(0, BigDecimal("1.5").compareTo(MoneyUtils.parse("۱٫۵")))
        assertEquals("", MoneyUtils.toInputString(BigDecimal.ZERO))
        assertEquals("1500000", MoneyUtils.toInputString(BigDecimal("1500000.00")))
        assertEquals("1500.5", MoneyUtils.toInputString(BigDecimal("1500.50")))
    }
}
