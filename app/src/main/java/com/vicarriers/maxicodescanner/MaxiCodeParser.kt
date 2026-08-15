package com.vicarriers.maxicodescanner

/**
 * Hardcoded MaxiCode parser.
 *
 * Tracking is always 18 characters: 1Z + 6-char shipper account + 2-char
 * service + the 8 digits that sit immediately after the real 1Z marker.
 * "1Z" inside a postal (e.g. V0T1Z0) is ignored; only 1Z followed by eight
 * digits counts. Account is the field after UPSN. Service is the class-of-
 * service field: skip the first character, accept the next two only when
 * they match the known service-level table.
 */
object MaxiCodeParser {
    const val GS = '\u001D'
    const val RS = '\u001E'
    const val EOT = '\u0004'

    private val postalRegex = Regex("[A-Za-z][0-9][A-Za-z][0-9][A-Za-z][0-9]")
    private val trackingTail = Regex("1Z(\\d{8})", RegexOption.IGNORE_CASE)

    /**
     * Known service levels. Keys are the two characters after the ignored
     * leading class-of-service digit. Values are what goes into the 1Z
     * (`93`/`99`/`02` remap; everything else is used as-is).
     */
    private val serviceLevels = mapOf(
        "20" to "20",
        "04" to "04",
        "14" to "14",
        "17" to "17",
        "67" to "67",
        "68" to "68",
        "91" to "91",
        "93" to "D9",
        "99" to "DG",
        "02" to "DK",
    )

    data class Result(
        val tracking: String?,
        val postalCode: String?,
    )

    fun parse(raw: String): Result = Result(
        tracking = extractTracking(raw),
        postalCode = extractPostal(raw),
    )

    fun extractTracking(raw: String): String? {
        if (raw.isEmpty()) return null

        val compact = raw.filter { !it.isWhitespace() }
        val firstField = compact.split(GS, RS, EOT).firstOrNull().orEmpty()
        if (
            firstField.length == 18 &&
            firstField.startsWith("1Z", ignoreCase = true) &&
            firstField.drop(2).all { it.isLetterOrDigit() }
        ) {
            return firstField.uppercase()
        }

        val last8 = trackingTail.find(raw)?.groupValues?.get(1) ?: return null
        val account = extractAccount(raw) ?: return null
        val service = extractService(raw) ?: return null

        val tracking = "1Z${account.uppercase()}${service.uppercase()}$last8"
        return tracking.takeIf { it.length == 18 }
    }

    /** `1Z02E5D96834489411` → `1Z 02E 5D9 68 3448 9411` */
    fun formatTracking(tracking: String): String {
        val compact = tracking.filter { !it.isWhitespace() }
        if (compact.length != 18) return tracking
        return "${compact.substring(0, 2)} ${compact.substring(2, 5)} " +
            "${compact.substring(5, 8)} ${compact.substring(8, 10)} " +
            "${compact.substring(10, 14)} ${compact.substring(14, 18)}"
    }

    /** Six-character shipper number in the field after UPSN. */
    private fun extractAccount(raw: String): String? {
        val fields = raw.split(GS, RS, EOT).map { it.trim() }
        for ((i, field) in fields.withIndex()) {
            if (field.equals("UPSN", ignoreCase = true)) {
                val next = fields.drop(i + 1).firstOrNull { it.isNotEmpty() } ?: return null
                return sixCharAccount(next)
            }
            if (field.startsWith("UPSN", ignoreCase = true) && field.length >= 10) {
                return sixCharAccount(field.substring(4))
            }
        }
        val upsn = raw.indexOf("UPSN", ignoreCase = true)
        if (upsn < 0) return null
        return takePrintable(raw, upsn + 4, 6)?.let { sixCharAccount(it) }
    }

    private fun sixCharAccount(value: String): String? {
        val account = value.take(6)
        return account.takeIf { it.length == 6 && it.all { ch -> ch.isLetterOrDigit() } }
    }

    /**
     * Class of service is the third AIM data field after format `01`
     * (`postal`, `country`, `class`). The first character is ignored; the
     * next two must be a known service level.
     */
    fun extractService(raw: String): String? {
        val fields = raw.split(GS, RS, EOT).map { it.trim() }.filter { it.isNotEmpty() }
        val formatIndex = fields.indexOfFirst { it == "01" }
        val classField = if (formatIndex >= 0) {
            fields.getOrNull(formatIndex + 3)
        } else {
            val trackingIndex = fields.indexOfFirst { trackingTail.containsMatchIn(it) }
            if (trackingIndex > 0) fields[trackingIndex - 1] else null
        } ?: return null

        if (classField.length < 3) return null
        val code = classField.substring(1, 3)
        return serviceLevels[code]
    }

    private fun isSeparator(ch: Char): Boolean =
        ch == GS || ch == RS || ch == EOT || ch == '\u001C' || ch == '\u001F' || ch.code < 32

    /** Same C# landmarks, but skip AIM/MaxiCode control bytes so they never enter the 1Z. */
    private fun takePrintable(raw: String, start: Int, count: Int): String? {
        if (start < 0 || start >= raw.length) return null
        val out = StringBuilder(count)
        var i = start
        while (i < raw.length && out.length < count) {
            val ch = raw[i++]
            if (!isSeparator(ch)) out.append(ch)
        }
        return out.toString().takeIf { it.length == count }
    }

    fun extractPostal(raw: String): String? {
        raw.split(GS, RS, EOT).forEach { field ->
            postalFromField(field)?.let { return it }
        }
        return postalRegex.find(raw)?.value?.uppercase()
    }

    private fun postalFromField(field: String): String? {
        val trimmed = field.trim()
        if (postalRegex.matches(trimmed)) return trimmed.uppercase()

        if (trimmed.length >= 8) {
            val prefix = trimmed.substring(0, 2)
            val rest = trimmed.substring(2)
            if (prefix.all { it.isDigit() } && postalRegex.matches(rest.take(6))) {
                val candidate = rest.take(6)
                if (postalRegex.matches(candidate)) return candidate.uppercase()
            }
        }

        return postalRegex.find(trimmed)?.value?.uppercase()
    }
}
