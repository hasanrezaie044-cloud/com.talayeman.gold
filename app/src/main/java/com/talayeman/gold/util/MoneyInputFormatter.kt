package com.talayeman.gold.util

/**
 * Pure (Android-free) helpers for monetary text input.
 *
 * The app keeps the *raw* numeric text (ASCII digits and an optional single '.')
 * in state. Thousands separators are added only at presentation time (see
 * [com.talayeman.gold.ui.components.ThousandsSeparatorTransformation]), so the
 * value that reaches [MoneyUtils.parse] / BigDecimal never contains commas.
 */
object MoneyInputFormatter {

    /** Max integer digits accepted in a money field (≈ 999 trillion). */
    const val MAX_INTEGER_DIGITS = 15

    data class Sanitized(val text: String, val cursor: Int)

    /**
     * Normalizes user input: Persian/Arabic digits → ASCII, removes separators and
     * any other character, keeps at most one '.', strips redundant leading zeros.
     * [cursor] is the caret position in [input]; the returned cursor is the
     * equivalent position in the sanitized text, so editing never "jumps".
     */
    fun sanitize(input: String, cursor: Int = input.length, allowDecimal: Boolean = true): Sanitized {
        val normalized = MoneyUtils.normalizeDigits(input)
        val safeCursor = cursor.coerceIn(0, normalized.length)
        val sb = StringBuilder(normalized.length)
        var newCursor = 0
        var seenDot = false
        for (i in normalized.indices) {
            val c = normalized[i]
            val keep = when {
                c in '0'..'9' -> true
                c == '.' && allowDecimal && !seenDot -> { seenDot = true; true }
                else -> false
            }
            if (keep) {
                sb.append(c)
                if (i < safeCursor) newCursor++
            }
        }
        // ".5" -> "0.5"
        if (sb.isNotEmpty() && sb[0] == '.') {
            sb.insert(0, '0')
            newCursor++
        }
        // "0012" -> "12" (but keep "0" and "0.x")
        while (sb.length > 1 && sb[0] == '0' && sb[1] != '.') {
            sb.deleteCharAt(0)
            if (newCursor > 0) newCursor--
        }
        return Sanitized(sb.toString(), newCursor.coerceIn(0, sb.length))
    }

    fun integerDigits(raw: String): Int {
        val dot = raw.indexOf('.')
        return if (dot >= 0) dot else raw.length
    }

    /** Result of grouping with exact caret mappings in both directions. */
    class Grouped internal constructor(
        val formatted: String,
        private val o2t: IntArray,
        private val t2o: IntArray
    ) {
        fun originalToTransformed(offset: Int): Int = o2t[offset.coerceIn(0, o2t.size - 1)]

        fun transformedToOriginal(offset: Int): Int = t2o[offset.coerceIn(0, t2o.size - 1)]
    }

    /**
     * "12500000" -> "12,500,000", "1234.5" -> "1,234.5".
     * Only the integer part is grouped; the fraction is left untouched.
     */
    fun group(raw: String): Grouped {
        val intLen = integerDigits(raw)
        val sb = StringBuilder(raw.length + raw.length / 3)
        val inserted = ArrayList<Boolean>(raw.length + raw.length / 3)
        val o2t = IntArray(raw.length + 1)
        for (i in raw.indices) {
            if (i in 1 until intLen && (intLen - i) % 3 == 0) {
                sb.append(',')
                inserted.add(true)
            }
            o2t[i] = sb.length
            sb.append(raw[i])
            inserted.add(false)
        }
        o2t[raw.length] = sb.length
        val t2o = IntArray(sb.length + 1)
        var orig = 0
        for (t in 0..sb.length) {
            t2o[t] = orig
            if (t < sb.length && !inserted[t]) orig++
        }
        return Grouped(sb.toString(), o2t, t2o)
    }

    /** Convenience: format raw digits for display. */
    fun format(raw: String): String = group(raw).formatted
}
