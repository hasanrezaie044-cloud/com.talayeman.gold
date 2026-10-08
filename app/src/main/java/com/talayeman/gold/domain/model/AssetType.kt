package com.talayeman.gold.domain.model

enum class AssetType(val persianName: String, val isCoin: Boolean = false) {
    GOLD_18K("طلای ۱۸ عیار"),
    GOLD_24K("طلای ۲۴ عیار"),
    JEWELRY("جواهرات"),
    MELTED_GOLD("طلای آب‌شده"),
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

    companion object {
        fun fromString(value: String): AssetType =
            entries.find { it.name == value } ?: OTHER
    }
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
