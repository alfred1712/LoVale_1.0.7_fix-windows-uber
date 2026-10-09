package com.example.lovale2.domain

import java.util.Locale

/** Datos normalizados de una oferta de viaje. */
data class DatosViaje(
    val precio: Double,
    val distanciaKm: Double,
    val duracionMin: Double,
    val destino: String,
    val pickup: String = "",
    val pickupDistanceKm: Double = 0.0,
    val pickupDurationMin: Double = 0.0,
    val completeReading: Boolean = true,
    val priceDiagnostics: String = ""
)

enum class MotivoEvaluacion {
    DISTANCIA_INVALIDA, ZONA_EXCLUIDA, TARIFA_KM_BAJA, TARIFA_HORA_BAJA, APROBADO
}

data class ResultadoEvaluacion(
    val aprobado: Boolean,
    val motivo: MotivoEvaluacion,
    val tarifaPorKm: Double,
    val tarifaPorHora: Double,
    val zonaDetectada: String? = null,
    val zonaPickupDetectada: String? = null,
    val zonaDestinoDetectada: String? = null
)

class TripEvaluator(
    private val tarifaMinimaPorKm: Double,
    private val tarifaMinimaPorHora: Double
) {
    fun estimarCostos(datos: DatosViaje, config: VehicleCosts): CostEstimate? = estimateCosts(datos, config)
    /**
     * Lee una única tarjeta/oferta. El servicio de accesibilidad se encarga de
     * separar las tarjetas cuando una pantalla contiene varias (por ejemplo,
     * Centro de viajes de DiDi).
     *
     * Soporta los formatos observados en las capturas:
     * - DiDi: "$8.100 (10 min 2,9 km) ... (15 min 6,1 km)"
     * - Uber: "ARS6,084 ... A 6 min (1.9 km) ... Viaje: 19 min (8.2 km)"
     * - Cabify: "$3.686 ... 8 min · 1.9 km ... 13 min · 3 km"
     */
    fun extraerDatosDeViaje(texto: String, destino: String = "", pickup: String = ""): DatosViaje {
        if (texto.length > 24000) return DatosViaje(0.0, 0.0, 0.0, destino, pickup,
            completeReading = false, priceDiagnostics = "input_too_long")
        val normalized = OfferText.normalize(texto)

        val priceMatches = Regex("""(?:\$|ARS)\s*([0-9][0-9.,]*)""", RegexOption.IGNORE_CASE)
            .findAll(normalized).toList()
        // Rates displayed by the platform are derived values, never the fare.
        val unitRateSuffix = Regex("""^\s*(?:[/⁄∕]|por\s+)\s*(?:km|h|hora|min)\b""", RegexOption.IGNORE_CASE)
        val unitRates = priceMatches.filter { unitRateSuffix.containsMatchIn(normalized.substring(it.range.last + 1)) }
        val fareCandidates = priceMatches
            // Los adicionales del mapa (+ARS 410) no son el importe de la tarjeta.
            .filterNot { normalized.substring(0, it.range.first).trimEnd().endsWith("+") }
            .filterNot { it in unitRates }
            .filterNot { isPromotionalAmount(normalized, it, priceMatches) }
        // A card is now ordered top-to-bottom: headline fare precedes bonus components.
        val price = fareCandidates
            .firstOrNull()
            ?.groupValues?.get(1)
            ?.let(::parseMoney) ?: 0.0

        // Buscamos los pares min/km en el orden visual de la tarjeta. La última
        // pareja representa el recorrido del pasajero; la primera, la recogida.
        val pairRegex = Regex(
            """(?<![\d.,])([0-9]+(?:[.,][0-9]+)?)\s*min(?:[\s()·•,:;\-]{0,18})([0-9]+(?:[.,][0-9]+)?)\s*(km|m)\b|(?<![\d.,])([0-9]+(?:[.,][0-9]+)?)\s*(km|m)(?:[\s()·•,:;\-]{0,18})([0-9]+(?:[.,][0-9]+)?)\s*min\b""",
            RegexOption.IGNORE_CASE
        )
        var ambiguousUnit = false
        val pairs = pairRegex.findAll(normalized).mapNotNull { match ->
            val min = (match.groupValues[1].ifBlank { match.groupValues[6] }).let(::parseDecimal)
            val rawDistance = (match.groupValues[2].ifBlank { match.groupValues[4] }).let(::parseDecimal)
            val unit = (match.groupValues[3].ifBlank { match.groupValues[5] }).lowercase(Locale.ROOT)
            // Fractional meters in a driving offer can be an OCR-dropped k (8.9 km -> 8.9 m).
            // Do not silently multiply: reject this reading and let Accessibility/OCR retry.
            val distanceToken = match.groupValues[2].ifBlank { match.groupValues[4] }
            if (unit == "m" && (distanceToken.contains('.') || distanceToken.contains(','))) ambiguousUnit = true
            val km = if (unit == "m") rawDistance / 1000.0 else rawDistance
            if (min.isFinite() && km.isFinite() && min > 0.0 && km > 0.0) min to km else null
        }.toList()

        val pickupPair = pairs.firstOrNull()
        val tripPair = pairs.lastOrNull()

        return DatosViaje(
            precio = price,
            distanciaKm = tripPair?.second ?: 0.0,
            duracionMin = tripPair?.first ?: 0.0,
            destino = destino,
            pickup = pickup,
            pickupDistanceKm = if (pairs.size >= 2) pickupPair?.second ?: 0.0 else 0.0,
            pickupDurationMin = if (pairs.size >= 2) pickupPair?.first ?: 0.0 else 0.0,
            completeReading = price.isFinite() && price > 0.0 && pairs.size == 2 && !ambiguousUnit &&
                pairs.all { (minutes, km) -> km / minutes * 60 <= 160 },
            priceDiagnostics = "amounts=${priceMatches.size} unitRates=${unitRates.size} excluded=${priceMatches.size - fareCandidates.size} candidates=${fareCandidates.size} ambiguousUnit=$ambiguousUnit routePairs=${pairs.size} routes=${pairs.joinToString { "${it.first}min/${it.second}km" }}"
        )
    }

    fun evaluarViaje(datos: DatosViaje, zonasExcluidas: List<String>): ResultadoEvaluacion {
        if (!datos.precio.isFinite() || datos.precio <= 0.0 || !datos.distanciaKm.isFinite() ||
            !datos.duracionMin.isFinite() || !datos.pickupDistanceKm.isFinite() || !datos.pickupDurationMin.isFinite() ||
            datos.distanciaKm <= 0.0 || datos.pickupDistanceKm < 0.0 || datos.duracionMin < 0.0 || datos.pickupDurationMin < 0.0) {
            return ResultadoEvaluacion(false, MotivoEvaluacion.DISTANCIA_INVALIDA, 0.0, 0.0)
        }

        val distanciaTotal = datos.distanciaKm + datos.pickupDistanceKm.coerceAtLeast(0.0)
        val duracionTotal = datos.duracionMin + datos.pickupDurationMin.coerceAtLeast(0.0)
        val income = datos.precio
        val tarifaPorKm = income / distanciaTotal
        val tarifaPorHora = if (duracionTotal > 0) {
            (income / duracionTotal) * 60.0
        } else 0.0
        if (!distanciaTotal.isFinite() || !duracionTotal.isFinite() || !tarifaPorKm.isFinite() || !tarifaPorHora.isFinite()) {
            return ResultadoEvaluacion(false, MotivoEvaluacion.DISTANCIA_INVALIDA, 0.0, 0.0)
        }

        val zonaPickup = findExcludedZone(datos.pickup, zonasExcluidas)
        val zonaDestino = findExcludedZone(datos.destino, zonasExcluidas)
        val zonaExcluida = zonaDestino ?: zonaPickup

        if (zonaExcluida != null) {
            return ResultadoEvaluacion(false, MotivoEvaluacion.ZONA_EXCLUIDA, tarifaPorKm, tarifaPorHora,
                zonaDetectada = zonaExcluida,
                zonaPickupDetectada = zonaPickup,
                zonaDestinoDetectada = zonaDestino)
        }
        if (tarifaPorKm < tarifaMinimaPorKm) {
            return ResultadoEvaluacion(false, MotivoEvaluacion.TARIFA_KM_BAJA, tarifaPorKm, tarifaPorHora)
        }
        if (tarifaMinimaPorHora > 0 && tarifaPorHora > 0 && tarifaPorHora < tarifaMinimaPorHora) {
            return ResultadoEvaluacion(false, MotivoEvaluacion.TARIFA_HORA_BAJA, tarifaPorKm, tarifaPorHora)
        }
        return ResultadoEvaluacion(true, MotivoEvaluacion.APROBADO, tarifaPorKm, tarifaPorHora)
    }

    private fun findExcludedZone(address: String, excludedZones: List<String>): String? {
        if (address.isBlank()) return null
        val explicit = explicitNeighborhood(address)
        // A CABA street named after a barrio is not proof that the address is inside it.
        if (explicit == null && address.any(Char::isDigit) && Regex("(?i)\\bCABA\\b").containsMatchIn(address)) return null
        val normalizedAddress = normalize(explicit ?: address)
        return excludedZones.firstOrNull { zone ->
            val normalizedZone = normalize(zone.substringBefore("(")).trim()
            if (normalizedZone.isBlank()) return@firstOrNull false
            normalizedZone.split("/", "-", ",").map { it.trim() }
                .filter { it.length >= 3 }
                .any { part -> containsWholeTerm(normalizedAddress, part) }
        }
    }

    private fun containsWholeTerm(text: String, term: String): Boolean {
        val escaped = Regex.escape(term)
        return Regex("(?:^|\\s)$escaped(?:$|\\s|,|-)", RegexOption.IGNORE_CASE).containsMatchIn(text)
    }

    private fun isPromotionalAmount(text: String, amount: MatchResult, amounts: List<MatchResult>): Boolean {
        val index = amounts.indexOf(amount)
        val before = text.substring(if (index > 0) amounts[index - 1].range.last + 1 else 0, amount.range.first)
        val after = text.substring(amount.range.last + 1, amounts.getOrNull(index + 1)?.range?.first ?: text.length)
        if (Regex("""(?i)^\s*(?:adicionales?\b|de\s+tarifa\s+base\b)""").containsMatchIn(after)) return true
        // A display capture can include LoVale's own net estimate; it is never the fare.
        if (Regex("""(?i)\bneto\s+estimado\s*s?\s*$""").containsMatchIn(before)) return true
        val label = "(?:boost\\s*\\+?|turbo|promoci[oó]n|promo|bono|bonificaci[oó]n|adicional|extra|propina)"
        // Etiqueta pegada al importe; no descartar el total solo por tener Turbo en la pantalla.
        val prefix = Regex("""(?i)\b$label\s*(?:(?:de|por|hasta)\s*)?[:·+\-]?\s*$""")
        val suffix = Regex("""(?i)^\s*(?:de|en|por)\s+$label\b""")
        val trailingLabel = index == amounts.lastIndex && Regex("""(?i)^\s*$label\b""").containsMatchIn(after)
        val previousAmountOwnsLabel = index > 0 &&
            Regex("""(?i)^\s*(?:de|en|por)\s+$label\s*$""").matches(before)
        val includedComponent = Regex("""(?i)^\s*incluid[oa]\b""").containsMatchIn(after)
        return (!previousAmountOwnsLabel && prefix.containsMatchIn(before)) || suffix.containsMatchIn(after) || trailingLabel || includedComponent
    }

    private fun parseMoney(value: String): Double {
        val v = value.trim()
        return when {
            v.contains(',') && v.contains('.') -> {
                if (v.lastIndexOf(',') > v.lastIndexOf('.')) v.replace(".", "").replace(',', '.').toDoubleOrNull() ?: 0.0
                else v.replace(",", "").toDoubleOrNull() ?: 0.0
            }
            v.contains(',') -> {
                val decimals = v.substringAfterLast(',')
                // En las ofertas argentinas, "ARS6,084" significa $6.084,
                // mientras que "$12.345,50" sí usa coma decimal.
                if (decimals.length == 1 || decimals.length == 2) {
                    v.replace(".", "").replace(',', '.').toDoubleOrNull() ?: 0.0
                } else {
                    v.replace(",", "").toDoubleOrNull() ?: 0.0
                }
            }
            else -> if (v.contains('.') && v.substringAfterLast('.').length in 1..2) {
                v.toDoubleOrNull() ?: 0.0
            } else v.replace(".", "").toDoubleOrNull() ?: 0.0
        }
    }

    private fun parseDecimal(value: String): Double = value.replace(',', '.').toDoubleOrNull() ?: 0.0

    private fun normalize(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace("á", "a").replace("é", "e").replace("í", "i")
        .replace("ó", "o").replace("ú", "u").replace("ü", "u")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
}
