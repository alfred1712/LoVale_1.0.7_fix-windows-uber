package com.example.lovale2.domain

/** Normalización compartida por detector, rutas y parser; no inventa dígitos. */
object OfferText {
    private val moneyGlyphs = Regex("""(?i)(ARS|\$)\s*([0-9][0-9.,Il|Oo]*)(?![a-zA-Z])""")
    private val oneMinute = Regex("""(?i)\bA\s*[Il|]\s+min\b""")
    // Compiled once; Regex matching is safe for concurrent readers.
    private val currency = Regex("""(?i)\bA\s*R\s*[S$]""")
    private val kilometers = Regex("""(?i)\bk\s*m\b""")
    private val minutes = Regex("""(?i)\b(?:n\s*)?m\s*[i1l]\s*n\b""")
    private val decimalSpacing = Regex("""(?<=\d)\s*([.,])\s*(?=\d)""")
    private val groupedMoney = Regex("""(?i)(ARS|\$)\s*(\d{1,3})\s+(\d{3})(?!\d)""")
    private val whitespace = Regex("""\s+""")
    private val hasMoney = Regex("""(?i)(?:ARS|\$)\s*\d""")
    private val hasMinutes = Regex("""(?i)\d\s*min\b""")
    private val hasDistance = Regex("""(?i)\d\s*(?:km|m)\b""")

    fun normalize(raw: String): String = raw
        .replace('\u00a0', ' ')
        .replace(currency, "ARS")
        .replace(moneyGlyphs) { match -> match.groupValues[1] + match.groupValues[2]
            .replace('I', '1').replace('i', '1').replace('L', '1').replace('l', '1').replace('|', '1').replace('O', '0').replace('o', '0') }
        .replace(oneMinute, "A 1 min")
        .replace(kilometers, "km")
        .replace(minutes, "min")
        .replace(decimalSpacing, "$1")
        .replace(groupedMoney) {
            "${it.groupValues[1]}${it.groupValues[2]}${it.groupValues[3]}"
        }
        .replace(whitespace, " ").trim()

    fun isUberCandidate(text: String): Boolean {
        val value = normalize(text)
        return hasMoney.containsMatchIn(value) &&
            hasMinutes.containsMatchIn(value) &&
            hasDistance.containsMatchIn(value)
    }
}
