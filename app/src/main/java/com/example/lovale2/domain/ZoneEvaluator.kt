package com.example.lovale2.domain

import com.example.lovale2.data.network.GoogleMapsApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ZoneEvaluator(private val apiKey: String) {

    private val apiService = GoogleMapsApiService.create()

    /**
     * Evalúa si una dirección de la notificación cae dentro de alguna zona prohibida.
     * @param rawAddress El texto crudo extraído de la notificación de Uber, DiDi o Cabify (Ej: "Gral. Urquiza 186, CABA - Balvanera")
     * @param excludedZones Lista de zonas bloqueadas configuradas por el usuario.
     */
    suspend fun isAddressInExcludedZone(rawAddress: String, excludedZones: List<String>): Boolean {
        if (excludedZones.isEmpty() || rawAddress.isBlank()) return false

        return withContext(Dispatchers.IO) {
            try {
                // Añadimos contexto geográfico por defecto si la app opera en Buenos Aires
                val queryAddress = if (!rawAddress.contains("Argentina", ignoreCase = true)) {
                    "$rawAddress, Buenos Aires, Argentina"
                } else {
                    rawAddress
                }

                val response = apiService.getAddressInfo(address = queryAddress, apiKey = apiKey)

                if (response.status == "OK" && response.results.isNotEmpty()) {
                    val components = response.results[0].address_components

                    // Extraemos los componentes clave devueltos por Google (Barrio, Localidad, Ciudad, Partido)
                    val matchedAreas = components.filter { component ->
                        component.types.any { it == "sublocality" || it == "locality" || it == "neighborhood" || it == "administrative_area_level_2" }
                    }.map { it.long_name.lowercase() }

                    // Comparamos con cada zona prohibida del usuario
                    for (zone in excludedZones) {
                        val cleanZone = zone.lowercase().substringBefore("(") // Ej: "Once / Balvanera (CABA)" -> "once / balvanera"

                        // Verificamos si alguna parte de la zona prohibida coincide con los componentes de Google
                        val matchFound = matchedAreas.any { area ->
                            cleanZone.contains(area) || area.contains(cleanZone.trim())
                        }

                        if (matchFound) return@withContext true
                    }
                }
                false
            } catch (e: Exception) {
                // Si falla la red de forma imprevista, por seguridad operativa evitamos bloquear falsos positivos
                false
            }
        }
    }
}