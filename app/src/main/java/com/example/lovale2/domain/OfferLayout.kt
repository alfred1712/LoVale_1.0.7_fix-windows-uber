package com.example.lovale2.domain

/** Geometry from the selected app only; independent of Android for regression tests. */
data class OfferLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int)
data class OfferCard(val lines: List<OfferLine>, val action: OfferLine?) {
    val text get() = lines.joinToString("\n") { it.text }
    val top get() = lines.minOf { it.top }
    val bottom get() = lines.maxOf { it.bottom }
}
object OfferLayout {
    private val header = Regex("(?i)uber\\s*(?:x|priority|comfort|black|green)|express(?: nuevo)?|pago (?:en efectivo|con tarjeta)")
    private val action = Regex("(?i)^(?:aceptar(?: viaje)?|me interesa|viaje disponible)$")
    fun cards(lines: List<OfferLine>): List<OfferCard> {
        val sorted = lines.sortedWith(compareBy<OfferLine> { it.top }.thenBy { it.left })
        val result = mutableListOf<OfferCard>()
        var after = 0
        sorted.forEachIndexed { index, line ->
            if (action.matches(line.text.trim())) {
                val start = (after..index).firstOrNull { header.containsMatchIn(sorted[it].text) }
                    ?: (after..index).firstOrNull { Regex("(?:ARS|\\$)\\s*\\d").containsMatchIn(sorted[it].text) }
                if (start != null) result += OfferCard(sorted.subList(start, index + 1), line)
                after = index + 1
            }
        }
        return result
    }
}
