package com.example.lovale2

import com.example.lovale2.data.network.UsigNeighborhoods
import com.example.lovale2.domain.DatosViaje
import com.example.lovale2.domain.MotivoEvaluacion
import com.example.lovale2.domain.TripEvaluator
import org.junit.Assert.*
import org.junit.Test

class UsigNeighborhoodsTest {
    @Test fun onlyStreetAndNumberLeaveDevice() {
        assertEquals("https://ws.usig.buenosaires.gob.ar/datos_utiles?calle=BULNES&altura=2625",
            UsigNeighborhoods.url("Bulnes 2625, C1425DKU CABA, Argentina"))
        assertTrue(requireNotNull(UsigNeighborhoods.url("Quintino Bocayuva 144, C1208 CABA, Argentina")).contains("QUINTINO+BOCAYUVA"))
    }
    @Test fun rejectsMissingMunicipalityOrAmbiguousAddress() {
        listOf("Bulnes 2625", "Bulnes 2625, Provincia de Buenos Aires", "Bulnes y Santa Fe, CABA",
            "Bulnes, CABA", "Bulnes 0, CABA", "Bulnes 2625\nCABA", "https://evil.test/ 123, CABA")
            .forEach { assertNull(it, UsigNeighborhoods.url(it)) }
    }
    @Test fun requiresUnambiguousCabaResponse() {
        assertEquals("Palermo", UsigNeighborhoods.parse("""{"comuna":"Comuna 14","barrio":"Palermo"}"""))
        assertNull(UsigNeighborhoods.parse("ERROR: No se pudo normalizar"))
        assertNull(UsigNeighborhoods.parse("""{"barrio":"Palermo"}"""))
        assertNull(UsigNeighborhoods.parse("""[{"comuna":"Comuna 14","barrio":"Palermo"},{"comuna":"Comuna 2","barrio":"Recoleta"}]"""))
        assertNull(UsigNeighborhoods.parse("""{"comuna":"Comuna 99","barrio":"Palermo"}"""))
    }
    @Test fun resolvedNeighborhoodChangesZoneWithoutChangingRates() {
        val evaluator = TripEvaluator(950.0, 10000.0)
        val trip = DatosViaje(4400.0, 4.7, 15.0, "Bulnes 2625, CABA", pickupDistanceKm = .125, pickupDurationMin = 2.0)
        val original = evaluator.evaluarViaje(trip, listOf("Palermo (CABA)"))
        val resolved = evaluator.evaluarViaje(trip.copy(destino = trip.destino + " - Palermo"), listOf("Palermo (CABA)"))
        assertEquals(MotivoEvaluacion.ZONA_EXCLUIDA, resolved.motivo)
        assertEquals(original.tarifaPorKm, resolved.tarifaPorKm, 0.0)
        assertEquals(original.tarifaPorHora, resolved.tarifaPorHora, 0.0)
    }
}
