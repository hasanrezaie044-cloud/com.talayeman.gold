package com.talayeman.gold

import com.talayeman.gold.data.remote.MarketPriceService
import com.talayeman.gold.util.MarketHours
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class TgjuHistoryParserTest {

    private fun noon(y: Int, m: Int, d: Int) =
        LocalDate.of(y, m, d).atTime(12, 0).atZone(MarketHours.ZONE).toInstant().toEpochMilli()

    @Test
    fun parsesRowsConvertsRialToTomanAndSortsOldestFirst() {
        val body = """
            {"data":[
              ["2,000,000","1,900,000","2,100,000","2,050,000","50,000","<span class=\"high\">2.5</span>","2026/10/12","1405/07/20"],
              ["1,900,000","1,800,000","2,000,000","1,950,000","10,000","0.5","2026/10/11","1405/07/19"]
            ]}
        """.trimIndent()
        val list = MarketPriceService.parseTgjuHistory(body, 100_000)
        assertEquals(2, list.size)
        assertEquals(noon(2026, 10, 11), list[0].first)
        assertEquals(0, BigDecimal("195000").compareTo(list[0].second))
        assertEquals(noon(2026, 10, 12), list[1].first)
        assertEquals(0, BigDecimal("205000").compareTo(list[1].second))
    }

    @Test
    fun acceptsJalaliOnlyDatesAndPersianDigits() {
        val body = """{"data":[["۱٬۰۰۰","۱٬۰۰۰","۱٬۰۰۰","۱٬۲۰۰","۰","۰","1405/07/20"]]}"""
        val list = MarketPriceService.parseTgjuHistory(body, 100_000)
        assertEquals(1, list.size)
        assertEquals(noon(2026, 10, 12), list[0].first)
        assertEquals(0, BigDecimal("120").compareTo(list[0].second))
    }

    @Test
    fun unreadablePayloadIsAnError() {
        try {
            MarketPriceService.parseTgjuHistory("""{"data":[["x","y"]]}""", 100_000)
            fail("expected exception")
        } catch (e: IllegalStateException) {
            // expected
        }
    }
}
