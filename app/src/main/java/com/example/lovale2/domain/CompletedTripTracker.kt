package com.example.lovale2.domain

/** Conservative session-only evidence. It never treats an offer disappearing as completion.
 * All timestamps are monotonic. No rider names or addresses are retained.
 */
class CompletedTripTracker {
    data class Offer(val id: String, val platform: String, val fare: Double, val at: Long)
    data class Decision(val id: String, val completed: Boolean, val reason: String, val finalPrice: Double? = null)
    private val offers = linkedMapOf<String, Offer>()
    private var accepted: Offer? = null
    private var acceptedAt = 0L
    private var started = false
    private var finishAt: Long? = null
    private var finalFare: Double? = null
    private var finalAt = 0L
    val awaitingFinal: Boolean get() = accepted != null && started
    fun isFinalScreen(text: String) = Regex("\\bviaje (?:finalizado|completado|terminado)\\b").containsMatchIn(normalize(text))
    fun clearVisibleOffers() { offers.clear() }
    fun offered(offer: Offer) {
        offers.entries.removeAll { offer.at - it.value.at > 30000 }
        offers[offer.id] = offer
        while (offers.size > 10) offers.remove(offers.keys.first())
    }
    fun click(platform: String, labels: List<String>, now: Long, cardFare: Double? = null): String? {
        val accepting = labels.any { normalize(it) in setOf("aceptar", "aceptar viaje", "aceptar solicitud", "me interesa") }
        if (accepted != null && accepting && now - acceptedAt > 1500) {
            // A subsequent acceptance supersedes unresolved tracking. The old offer remains for review.
            accepted = null; started = false; finalFare = null; finishAt = null
        }
        if (accepted != null) {
            if (accepted?.platform == platform && started && labels.any {
                normalize(it) in setOf("finalizar viaje", "terminar viaje", "finalizar el viaje", "completar viaje")
            }) finishAt = now
            return null
        }
        if (!accepting) return null
        val candidates = offers.values.filter { it.platform == platform && now - it.at in 0..30000 && (cardFare == null || kotlin.math.abs(it.fare - cardFare) < .01) }
        // A click without a unique associated offer is insufficient evidence.
        if (candidates.size != 1) return null
        accepted = candidates.single()
        acceptedAt = now
        finishAt = null
        started = false
        finalFare = null
        offers.clear()
        return accepted?.id
    }
    fun observe(platform: String, text: String, now: Long): Decision? {
        if (accepted == null && Regex("\\b(?:llegue a la recogida|llegaste a la recogida|iniciar viaje|iniciar el viaje|recoger a)\\b").containsMatchIn(normalize(text))) {
            val id = click(platform, listOf("Aceptar"), now)
            if (id != null) return Decision(id, false, "aceptado; pantalla de recogida")
        }
        val offer = accepted ?: return null
        if (offer.platform != platform) return null
        if (now - acceptedAt !in 0..6 * 3600000L) return reset("seguimiento vencido")
        val value = normalize(text)
        if (Regex("\\b(?:viaje cancelado|solicitud cancelada|cancelaste el viaje|pasajero cancelo)\\b").containsMatchIn(value)) {
            accepted = null; finalFare = null; started = false
            return Decision(offer.id, false, "cancelado")
        }
        if (Regex("\\b(?:viaje en curso|viaje iniciado|desliza para (?:finalizar|terminar)(?: el)? viaje|finalizar viaje|terminar viaje)\\b").containsMatchIn(value)) started = true
        val completed = Regex("\\bviaje (?:finalizado|completado|terminado)\\b").containsMatchIn(value)
        if (!completed || !started || now - acceptedAt < 15000 || (finishAt != null && now - (finishAt ?: now) !in 0..120000)) { finalFare = null; return null }
        val amounts = Regex("(?:total(?: del viaje)?|importe del viaje|tarifa final)\\s*:?\\s*((?:ARS|\\$)\\s*[0-9][0-9.,]*)", RegexOption.IGNORE_CASE)
            .findAll(OfferText.normalize(text)).map { TripEvaluator(0.0, 0.0).extraerDatosDeViaje(it.groupValues[1]).precio }
            .filter { it.isFinite() && it > 0 }.distinct().toList()
        if (amounts.size != 1) { finalFare = null; return null }
        val fare = amounts.single()
        if (finalFare != fare || now - finalAt > 10000) {
            finalFare = fare; finalAt = now; return null
        }
        if (now - finalAt < 750) return null
        accepted = null; finalFare = null; started = false
        val matches = kotlin.math.abs(fare - offer.fare) < 0.01
        return Decision(offer.id, matches || finishAt != null, if (matches) "aceptación + inicio + dos finales con tarifa coincidente" else if (finishAt != null) "tarifa final verificada" else "tarifa final distinta; revisar", fare)
    }
    fun reset(reason: String): Decision? {
        val previous = accepted
        offers.clear(); accepted = null; started = false; finalFare = null; finishAt = null
        return previous?.let { Decision(it.id, false, reason) }
    }
    private fun normalize(text: String) = ZoneAutocomplete.key(text)
}
