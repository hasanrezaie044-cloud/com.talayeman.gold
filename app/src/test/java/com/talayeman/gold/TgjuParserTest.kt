package com.talayeman.gold

import com.talayeman.gold.data.remote.MarketPriceService
import com.talayeman.gold.domain.model.PriceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigDecimal

class TgjuParserTest {

    private fun price(list: List<com.talayeman.gold.domain.model.MarketPrice>, t: PriceType) =
        list.firstOrNull { it.priceType == t }?.price

    private val sample = """
        {"current":{
          "geram18":{"p":"190,542,000","h":"191,000,000","l":"189,000,000","d":"250,000","dp":0.13,"dt":"low","t":"11:49:55"},
          "geram24":{"p":"254,056,000"},
          "mesghal":{"p":"878,000,000"},
          "gold_melted_wholesale":{"p":"877,500,000"},
          "sekee":{"p":"2,100,000,000"},
          "sekeb":{"p":"2,050,000,000"},
          "nim":{"p":"1,150,000,000"},
          "rob":{"p":"650,000,000"},
          "gerami":{"p":"380,000,000"},
          "price_dollar_rl":{"p":"1,000,000"}
        }}
    """.trimIndent()

    @Test
    fun convertsRialToToman() {
        val list = MarketPriceService.parseTgju(sample, 1L)
        assertEquals(0, BigDecimal("19054200").compareTo(price(list, PriceType.GOLD_18K)))
        assertEquals(0, BigDecimal("25405600").compareTo(price(list, PriceType.GOLD_24K)))
        assertEquals(0, BigDecimal("210000000").compareTo(price(list, PriceType.EMAMI)))
        assertEquals(0, BigDecimal("205000000").compareTo(price(list, PriceType.BAHAR_AZADI)))
        assertEquals(0, BigDecimal("115000000").compareTo(price(list, PriceType.HALF_COIN)))
        assertEquals(0, BigDecimal("65000000").compareTo(price(list, PriceType.QUARTER_COIN)))
        assertEquals(0, BigDecimal("38000000").compareTo(price(list, PriceType.GRAM_COIN)))
        assertTrue(list.all { it.source == "tgju" && !it.isCached })
    }

    @Test
    fun derives24kWhenMissing() {
        val json = """{"current":{"geram18":{"p":"18,000,000"}}}"""
        val list = MarketPriceService.parseTgju(json, 1L)
        assertEquals(0, BigDecimal("2400000").compareTo(price(list, PriceType.GOLD_24K)))
        assertNull(price(list, PriceType.EMAMI))
    }

    @Test
    fun implausibleMithqalIsReplacedByDerivedValue() {
        // 18K gram = 1,900,000 toman; a "mesghal" of 1,900,000 toman (same as a gram) is a unit mix-up.
        val json = """{"current":{"geram18":{"p":"19,000,000"},"mesghal":{"p":"19,000,000"}}}"""
        val list = MarketPriceService.parseTgju(json, 1L)
        assertEquals(0, BigDecimal("8755200").compareTo(price(list, PriceType.MITHQAL)))
    }

    @Test
    fun missing18kIsAnError() {
        try {
            MarketPriceService.parseTgju("""{"current":{"sekee":{"p":"1,000"}}}""", 1L)
            fail("expected exception")
        } catch (e: IllegalStateException) {
            // expected
        }
    }

    @Test
    fun invalidStructureIsAnError() {
        try {
            MarketPriceService.parseTgju("""{"foo":1}""", 1L)
            fail("expected exception")
        } catch (e: IllegalStateException) {
            // expected
        }
    }
}
