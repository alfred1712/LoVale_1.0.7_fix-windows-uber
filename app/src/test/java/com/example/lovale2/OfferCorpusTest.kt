package com.example.lovale2

import com.example.lovale2.domain.TripEvaluator
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class OfferCorpusTest {
    @Test fun anonymizedRealLayoutsRemainReadable() {
        val json = requireNotNull(javaClass.getResourceAsStream("/offer-corpus.json")).bufferedReader().use { it.readText() }
        JsonParser.parseString(json).asJsonArray.forEach { element ->
            val fixture = element.asJsonObject
            val data = TripEvaluator(950.0, 10000.0).extraerDatosDeViaje(fixture.get("text").asString)
            assertTrue(fixture.get("app").asString, data.completeReading)
            assertEquals(fixture.get("fare").asDouble, data.precio, .001)
            assertEquals(fixture.get("km").asDouble, data.distanciaKm + data.pickupDistanceKm, .001)
            assertEquals(fixture.get("minutes").asDouble, data.duracionMin + data.pickupDurationMin, .001)
        }
    }
}
