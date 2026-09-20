package com.example.lovale2

import com.example.lovale2.domain.*
import org.junit.Assert.*
import org.junit.Test

class FiniteTripTest {
    private val evaluator = TripEvaluator(950.0, 10000.0)
    @Test fun overflowCannotBecomeCompleteOffer() {
        val data = evaluator.extraerDatosDeViaje("ARS${"9".repeat(400)} A 4 min (1.5 km) Viaje: 21 min (8.9 km)")
        assertFalse(data.completeReading)
        assertFalse(evaluator.evaluarViaje(data, emptyList()).aprobado)
    }
    @Test fun invalidValuesCannotPassThresholds() {
        val valid = DatosViaje(8174.0, 8.9, 21.0, "", pickupDistanceKm = 1.5, pickupDurationMin = 4.0)
        listOf(valid.copy(distanciaKm = Double.NaN), valid.copy(precio = Double.POSITIVE_INFINITY),
            valid.copy(pickupDistanceKm = -1.0), valid.copy(duracionMin = Double.NaN),
            valid.copy(distanciaKm = Double.MIN_VALUE, pickupDistanceKm = 0.0)).forEach {
            val result = evaluator.evaluarViaje(it, emptyList())
            assertFalse(result.aprobado)
            assertTrue(result.tarifaPorKm.isFinite())
            assertTrue(result.tarifaPorHora.isFinite())
        }
    }
    @Test fun oversizedInputIsRejectedBeforeExpensiveParsing() {
        assertFalse(evaluator.extraerDatosDeViaje("ARS1 ".repeat(5000)).completeReading)
    }
}
