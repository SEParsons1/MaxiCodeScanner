package com.vicarriers.maxicodescanner

/**
 * Phone camera accepts only Code 128, and only when one frame contains both:
 * the UPS tracking barcode and the SSI-Sort postal barcode (420 / 421).
 */
internal data class PhoneLabelScan(
    val tracking: String,
    val postalCode: String,
)

internal object PhoneCode128Pair {
    private val trackingRegex = Regex("^1Z[A-Z0-9]{16}$", RegexOption.IGNORE_CASE)
    private val postalPrefixesByLength = mapOf(9 to "420", 12 to "421")

    fun isPostalBarcode(rawValue: String?): Boolean {
        if (rawValue == null) return false
        val requiredPrefix = postalPrefixesByLength[rawValue.length] ?: return false
        return rawValue.startsWith(requiredPrefix)
    }

    fun trackingFromBarcode(rawValue: String?): String? {
        val compact = rawValue?.filter { !it.isWhitespace() } ?: return null
        return compact.uppercase().takeIf { trackingRegex.matches(it) }
    }

    fun postalFromBarcode(rawValue: String?): String? {
        if (!isPostalBarcode(rawValue) || rawValue == null) return null
        return when (rawValue.length) {
            9 -> rawValue.substring(3).uppercase()
            12 -> rawValue.substring(6).uppercase()
            else -> null
        }
    }

    fun pair(values: Iterable<String?>): PhoneLabelScan? {
        val tracking = values.firstNotNullOfOrNull(::trackingFromBarcode) ?: return null
        val postalCode = values.firstNotNullOfOrNull(::postalFromBarcode) ?: return null
        return PhoneLabelScan(tracking, postalCode)
    }
}
