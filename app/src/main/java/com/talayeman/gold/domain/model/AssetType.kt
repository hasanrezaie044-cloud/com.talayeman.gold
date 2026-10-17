package com.talayeman.gold.domain.model

enum class AssetType(val persianName: String, val isCoin: Boolean = false) {
    GOLD_18K("طلای ۱۸ عیار"),
    GOLD_24K("طلای ۲۴ عیار"),
    JEWELRY("جواهرات"),
    MELTED_GOLD("طلای آب‌شده"),
    /** "بانک طلا": melted gold only, valued with the melted-gold price, entered in grams or milligrams. */
    GOLD_BANK("بانک طلا"),
    GOLD_BAR("شمش طلا"),
    EMAMI("سکه امامی", isCoin = true),
    BAHAR_AZADI("سکه بهار آزادی", isCoin = true),
    HALF_COIN("نیم سکه", isCoin = true),
    QUARTER_COIN("ربع سکه", isCoin = true),
    GRAM_COIN("سکه گرمی", isCoin = true),
    PARSIAN("پارسیان", isCoin = true),
    NON_BANK_COIN("سکه غیر بانکی", isCoin = true),
    CUSTOM_GOLD("طلای سفارشی"),
    CUSTOM_COIN("سکه سفارشی", isCoin = true),
    OTHER("سایر");

    /** Section used to group assets in the Assets tab. */
    val category: AssetCategory
        get() = when {
            isCoin -> AssetCategory.COIN
            this == OTHER -> AssetCategory.OTHER
            else -> AssetCategory.GOLD
        }

    val isGoldBank: Boolean get() = this == GOLD_BANK

    /** Weight units offered in the form: gold bank is entered in grams or milligrams only. */
    val allowedWeightUnits: List<WeightUnit>
        get() = if (isGoldBank) listOf(WeightUnit.GRAM, WeightUnit.MILLIGRAM) else WeightUnit.entries

    /** Gold sold by weight: the unit price is per GRAM. Coins / other items are priced per piece. */
    val pricedPerGram: Boolean get() = category == AssetCategory.GOLD

    companion object {
        fun fromString(value: String): AssetType =
            entries.find { it.name == value } ?: OTHER
    }
}

/** Gold / Coin / Other: how the Assets tab separates asset types. */
enum class AssetCategory(val persianName: String) {
    GOLD("طلا"),
    COIN("سکه"),
    OTHER("سایر")
}

enum class WeightUnit(val persianName: String) {
    SOOT("سوت"),
    MILLIGRAM("میلی‌گرم"),
    GRAM("گرم"),
    MITHQAL("مثقال");

    companion object {
        fun fromString(value: String): WeightUnit =
            entries.find { it.name == value } ?: GRAM
    }
}

enum class PriceType(val persianName: String) {
    GOLD_18K("طلای ۱۸ عیار"),
    GOLD_24K("طلای ۲۴ عیار"),
    MITHQAL("مثقال"),
    MELTED_GOLD("طلای آب‌شده"),
    EMAMI("سکه امامی"),
    BAHAR_AZADI("سکه بهار آزادی"),
    HALF_COIN("نیم سکه"),
    QUARTER_COIN("ربع سکه"),
    GRAM_COIN("سکه گرمی"),
    PARSIAN("پارسیان");

    companion object {
        fun fromString(value: String): PriceType =
            entries.find { it.name == value } ?: GOLD_18K
    }
}

enum class Currency(val persianName: String, val symbol: String) {
    TOMAN("تومان", "تومان"),
    RIAL("ریال", "ریال");

    companion object {
        fun fromString(value: String): Currency =
            entries.find { it.name == value } ?: TOMAN
    }
}

enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}
