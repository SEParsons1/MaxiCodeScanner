package com.vicarriers.maxicodescanner

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/**
 * Renders C0 / DEL bytes the way Notepad++ "Show All Characters" does:
 * a compact boxed label (GS, RS, EOT, …) inline with the printable text.
 */
fun visualizeControlCharacters(raw: String): AnnotatedString = buildAnnotatedString {
    raw.forEach { ch ->
        val code = ch.code
        if (code in 0..31 || code == 127) {
            withStyle(controlStyle) {
                append(controlLabel(code))
            }
        } else {
            append(ch)
        }
    }
}

private val controlStyle = SpanStyle(
    color = Color(0xFF000080),
    background = Color(0xFFD4D0C8),
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontSize = 12.sp,
    letterSpacing = 0.sp,
)

private fun controlLabel(code: Int): String = when (code) {
    0 -> "NUL"
    1 -> "SOH"
    2 -> "STX"
    3 -> "ETX"
    4 -> "EOT"
    5 -> "ENQ"
    6 -> "ACK"
    7 -> "BEL"
    8 -> "BS"
    9 -> "HT"
    10 -> "LF"
    11 -> "VT"
    12 -> "FF"
    13 -> "CR"
    14 -> "SO"
    15 -> "SI"
    16 -> "DLE"
    17 -> "DC1"
    18 -> "DC2"
    19 -> "DC3"
    20 -> "DC4"
    21 -> "NAK"
    22 -> "SYN"
    23 -> "ETB"
    24 -> "CAN"
    25 -> "EM"
    26 -> "SUB"
    27 -> "ESC"
    28 -> "FS"
    29 -> "GS"
    30 -> "RS"
    31 -> "US"
    127 -> "DEL"
    else -> "U+%04X".format(code)
}
