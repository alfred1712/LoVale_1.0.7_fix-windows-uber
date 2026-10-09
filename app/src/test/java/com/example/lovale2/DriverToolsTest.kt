package com.example.lovale2

import com.example.lovale2.domain.*
import com.example.lovale2.data.settings.AppSettings
import org.junit.Assert.*
import org.junit.Test

class DriverToolsTest {
    @Test fun evaluatesFullPriceOverPickupPlusTrip() {
        val data = DatosViaje(6000.0, 8.0, 20.0, "", pickupDistanceKm = 2.0, pickupDurationMin = 10.0)
        val result = AppSettings().evaluator().evaluarViaje(data, emptyList())
        assertEquals(600.0, result.tarifaPorKm, .001)
        assertEquals(12000.0, result.tarifaPorHora, .001)
    }
    @Test fun partialReadingIsNeverClassifiedAsProfitable() {
        val evaluator = TripEvaluator(1.0, 1.0)
        val data = evaluator.extraerDatosDeViaje("ARS6000 Viaje: 10 min (3 km)")
        assertFalse(data.completeReading)
        assertEquals("INCOMPLETE", classifyOffer(data, evaluator.evaluarViaje(data, emptyList()), AppSettings()))
    }
    @Test fun knownPlatformsHaveCompleteReadings() {
        val evaluator = TripEvaluator(1.0, 1.0)
        listOf("ARS6,084 A 6 min (1.9 km) Viaje: 19 min (8.2 km)",
            "$3.686 en app $1.215/km 8 min · 1.9 km 13 min · 3 km",
            "$8.100 (10 min 2,9 km) (15 min 6,1 km) Aceptar").forEach {
            assertTrue(evaluator.extraerDatosDeViaje(it).completeReading)
        }
    }
    @Test fun classificationKeeps85PercentAndWorstCriterion() {
        val settings = AppSettings(minRateByKm = "100", minRateByHour = "1000", maxPickupDistance = "0")
        val data = DatosViaje(1000.0, 5.0, 20.0, "")
        fun level(km: Double, hour: Double) = classifyOffer(data, ResultadoEvaluacion(false, MotivoEvaluacion.TARIFA_KM_BAJA, km, hour), settings)
        assertEquals("YELLOW", level(85.0, 1000.0))
        assertEquals("RED", level(100.0, 849.0))
        assertEquals("GREEN", level(100.0, 1000.0))
    }
}
