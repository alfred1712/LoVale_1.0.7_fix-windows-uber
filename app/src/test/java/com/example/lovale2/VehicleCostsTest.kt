package com.example.lovale2

import com.example.lovale2.data.settings.OfferRecord
import com.example.lovale2.data.settings.retainOffers
import com.example.lovale2.domain.*
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class VehicleCostsTest {
    private val config = VehicleCosts(true, "Referencia", "Nafta", 8.0, 1500.0, 20.0, 1L)
    private val data = DatosViaje(8500.0, 10.0, 20.0, "", pickupDistanceKm = 2.0, pickupDurationMin = 5.0)
    @Test fun includesPickupOnceAndKeepsGrossRates() {
        val evaluator = TripEvaluator(950.0, 10000.0)
        val costs = requireNotNull(evaluator.estimarCostos(data, config))
        assertEquals(1440.0, costs.fuel, .001)
        assertEquals(288.0, costs.wear, .001)
        assertEquals(6772.0, costs.net, .001)
        assertEquals(8500.0 / 12, evaluator.evaluarViaje(data, emptyList()).tarifaPorKm, .001)
        assertEquals(20400.0, evaluator.evaluarViaje(data, emptyList()).tarifaPorHora, .001)
    }
    @Test fun incompleteOrUnconfiguredDoesNotInventNet() {
        assertNull(estimateCosts(data, config.copy(enabled = false)))
        assertNull(estimateCosts(data, config.copy(fuelPrice = 0.0)))
        assertNull(estimateCosts(data, config.copy(consumptionPer100Km = Double.NaN)))
        assertNull(estimateCosts(data.copy(completeReading = false), config))
        assertNull(estimateCosts(data.copy(distanciaKm = Double.POSITIVE_INFINITY), config))
    }
    @Test fun negativeNetIsNotClampedAndGncUsesCubicMeters() {
        val costs = requireNotNull(estimateCosts(data.copy(precio = 1000.0), config.copy(fuel = "GNC", consumptionPer100Km = 10.0)))
        assertEquals(1800.0, costs.fuel, .001)
        assertEquals(-1160.0, costs.net, .001)
    }
    private val zone = TimeZone.getTimeZone("America/Argentina/Buenos_Aires")
    private val now = Calendar.getInstance(zone).apply { set(2026, 8, 12, 12, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    private fun record(id: String, completed: Long = now, costs: CostEstimate? = estimateCosts(data, config)) =
        OfferRecord(now, "Uber", 8500.0, 700.0, 20400.0, "GREEN", "APROBADO", id = id, costs = costs, completedAt = completed)
    @Test fun dailySummaryExcludesOffersYesterdayAndDuplicateIds() {
        val a = record("a")
        val summary = dailySummary(listOf(a, a, record("pending", 0), record("old", now - 86400000)), now, zone)
        assertEquals(1, summary.trips)
        assertEquals(8500.0, summary.gross, .001)
        assertEquals(6772.0, summary.net, .001)
    }
    @Test fun unknownCostsAreExplicitAndLocalMidnightStartsNewDay() {
        val summary = dailySummary(listOf(record("unknown", costs = null)), now, zone)
        assertEquals(1, summary.missingCosts)
        assertEquals(0.0, summary.net, .001)
        assertEquals(8500.0, summary.gross, .001)
        assertEquals(0, dailySummary(listOf(record("today")), now + 86400000, zone).trips)
    }
    @Test fun confirmedRecordsSurviveMoreThan200Offers() {
        val pending = (1..250).map { record("p$it", 0) }
        val kept = retainOffers(pending + record("done") + record("expired", now - 32L * 86400000), now)
        assertEquals(201, kept.size)
        assertTrue(kept.any { it.key == "done" })
        assertFalse(kept.any { it.key == "expired" })
    }
    @Test fun legacyHistoryRemainsUnknownAndUnconfirmed() {
        val old = Gson().fromJson("""{"timestamp":123,"platform":"Uber","price":8500,"rateKm":700,"rateHour":20400,"level":"GREEN","reason":"APROBADO"}""", OfferRecord::class.java)
        assertNull(old.costs)
        assertEquals(0L, old.completedAt)
        assertTrue(old.key.isNotBlank())
    }
}
