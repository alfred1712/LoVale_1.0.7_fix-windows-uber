package com.example.lovale2.domain

import java.util.Locale

/** Datos normalizados de una oferta de viaje. */
data class DatosViaje(
    val precio: Double,
    val distanciaKm: Double,
    val duracionMin: Double,
    val destino: String,
    val pickup: String = "",
    val pickupDistanceKm: Double = 0.0
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
        val normalized = texto.replace('\u00A0', ' ')

        val priceMatches = Regex("""(?:\$|ARS)\s*([0-9][0-9.,]*)""", RegexOption.IGNORE_CASE)
            .findAll(normalized).toList()
        val price = priceMatches
            .sortedByDescending { scorePriceContext(normalized, it.range.first, it.range.last) }
            .firstOrNull()
            ?.groupValues?.get(1)
            ?.let(::parseMoney) ?: 0.0

        // Buscamos los pares min/km en el orden visual de la tarjeta. La última
        // pareja representa el recorrido del pasajero; la primera, la recogida.
        val pairRegex = Regex(
            """([0-9]+(?:[.,][0-9]+)?)\s*min(?:[^0-9k]{0,18})([0-9]+(?:[.,][0-9]+)?)\s*(km|m)|([0-9]+(?:[.,][0-9]+)?)\s*(km|m)(?:[^0-9m]{0,18})([0-9]+(?:[.,][0-9]+)?)\s*min""",
            RegexOption.IGNORE_CASE
        )
        val pairs = pairRegex.findAll(normalized).mapNotNull { match ->
            val min = (match.groupValues[1].ifBlank { match.groupValues[6] }).let(::parseDecimal)
            val rawDistance = (match.groupValues[2].ifBlank { match.groupValues[4] }).let(::parseDecimal)
            val unit = (match.groupValues[3].ifBlank { match.groupValues[5] }).lowercase(Locale.ROOT)
            val km = if (unit == "m") rawDistance / 1000.0 else rawDistance
            if (min > 0.0 && km > 0.0) min to km else null
        }.toList()

        val pickupPair = pairs.firstOrNull()
        val tripPair = pairs.lastOrNull()

        return DatosViaje(
            precio = price,
            distanciaKm = tripPair?.second ?: 0.0,
            duracionMin = tripPair?.first ?: 0.0,
            destino = destino,
            pickup = pickup,
            pickupDistanceKm = if (pairs.size >= 2) pickupPair?.second ?: 0.0 else 0.0
        )
    }

    fun evaluarViaje(datos: DatosViaje, zonasExcluidas: List<String>): ResultadoEvaluacion {
        if (datos.distanciaKm <= 0.0) {
            return ResultadoEvaluacion(false, MotivoEvaluacion.DISTANCIA_INVALIDA, 0.0, 0.0)
        }

        val tarifaPorKm = datos.precio / datos.distanciaKm
        val tarifaPorHora = if (datos.duracionMin > 0) {
            (datos.precio / datos.duracionMin) * 60.0
        } else 0.0

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
        val normalizedAddress = normalize(address)
        return excludedZones.firstOrNull { zone ->
            val normalizedZone = normalize(zone).substringBefore("(").trim()
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

    private fun scorePriceContext(text: String, start: Int, end: Int): Int {
        val context = text.substring(maxOf(0, start - 70), minOf(text.length, end + 70))
        var score = 0
        if (Regex("total|ganancia|recibir|oferta|tarifa|viaje", RegexOption.IGNORE_CASE).containsMatchIn(context)) score += 5
        if (Regex("propina|extra|bono|tarifa base", RegexOption.IGNORE_CASE).containsMatchIn(context)) score -= 3
        return score
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
            else -> v.replace(".", "").toDoubleOrNull() ?: 0.0
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
