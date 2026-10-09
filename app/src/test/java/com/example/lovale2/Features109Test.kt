package com.example.lovale2

import com.example.lovale2.domain.*
import com.example.lovale2.data.settings.*
import org.junit.Assert.*
import org.junit.Test

class Features109Test {
    @Test fun overnightQuietHoursHaveExclusiveEnd() {
        val options = DriverOptions(quietEnabled = true, quietStart = 1320, quietEnd = 360).validate()
        assertTrue(options.quietAt(1320)); assertTrue(options.quietAt(0)); assertTrue(options.quietAt(359))
        assertFalse(options.quietAt(360)); assertFalse(options.quietAt(1319))
    }
    @Test fun overlappingRatesUseStricterCriterionAndFallbackOutside() {
        val options = DriverOptions(timedRates = true, peak = TimeRule(300, 600, 1000.0, 20000.0), night = TimeRule(1320, 360, 1400.0, 17000.0))
        val settings = AppSettings()
        assertEquals("1400.0", options.effective(settings, 330).minRateByKm)
        assertEquals("20000.0", options.effective(settings, 330).minRateByHour)
        assertEquals(settings, options.effective(settings, 900))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsNonFiniteGoals() { DriverOptions(dailyGoal = Double.NaN).validate() }
    @Test(expected = IllegalArgumentException::class) fun rejectsAllDayAmbiguousQuietWindow() { DriverOptions(quietEnabled = true).validate() }
    @Test fun clockValidation() { assertEquals(1439, parseClock("23:59")); assertNull(parseClock("24:00")); assertNull(parseClock("3:90")) }
    @Test fun backupRoundTripHasNoHomeOrActiveState() {
        val settings = AppSettings(serviceActive = true, selectedApp = RideApp.DIDI, excludedZones = listOf("Palermo (CABA)"))
        val json = encodeConfiguration(settings, DriverOptions(dailyGoal = 50000.0))
        val decoded = decodeConfiguration(json)
        assertEquals(settings.excludedZones, decoded.zones)
        assertEquals("DIDI", decoded.platform)
        assertFalse(json.contains("serviceActive")); assertFalse(json.contains("home"))
        assertEquals(50000.0, decoded.options.dailyGoal, 0.0)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsNegativeImportedRates() {
        decodeConfiguration(encodeConfiguration(AppSettings(minRateByKm = "-1"), DriverOptions()))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsFutureBackupVersion() {
        decodeConfiguration(encodeConfiguration(AppSettings(), DriverOptions()).replace("\"version\":1", "\"version\":999"))
    }
    @Test fun statisticsAreWeightedAndUnconfirmedOffersAreNotEarnings() {
        val a = OfferRecord(1000, "Uber", 1000.0, 1000.0, 6000.0, "RED", "TEST", id = "a", totalKm = 1.0, totalMinutes = 10.0, targetKm = 1500.0)
        val b = a.copy(id = "b", price = 9000.0, totalKm = 19.0, totalMinutes = 50.0, completedAt = 2000, level = "GREEN")
        val result = offerStatistics(listOf(a, b, a))
        assertEquals(2, result.offers); assertEquals(500.0, result.rateKm, 0.0); assertEquals(10000.0, result.rateHour, 0.0)
        assertEquals(9000.0, result.gross, 0.0); assertEquals(0.0, result.net, 0.0); assertEquals(1, result.missingCosts)
        assertEquals(500.0, result.belowTargetGap, 0.0)
    }
    @Test fun csvDescribesOffersInsteadOfCompletedEarnings() {
        val header = offersCsv(emptyList()).lineSequence().first()
        assertTrue(header.contains("neto_estimado_oferta"))
        assertFalse(header.contains("finalizado"))
        assertFalse(header.contains("aceptado"))
    }
    @Test fun csvNeutralizesFormulasAndEscapesQuotes() {
        assertEquals("\"'=1+1\"", csvCell("=1+1"))
        assertEquals("\"'  @cmd\"", csvCell("  @cmd"))
        assertEquals("\"a\"\"b\"", csvCell("a\"b"))
        val row = OfferRecord(1000, "Uber", 1000.5, 100.0, 6000.0, "GREEN", "OK")
        assertTrue(offersCsv(listOf(row)).startsWith("\uFEFFfecha_oferta;"))
        assertTrue(offersCsv(listOf(row)).contains("1000,50"))
    }
    @Test fun surgeNeverMultipliesDisplayedFare() {
        val text = "UberX 2,5x ARS8,174 A 4 min (1.5 km) Viaje: 21 min (8.9 km) Aceptar"
        assertTrue(surgeLabel(text).contains("2.5x"))
        assertEquals(8174.0, TripEvaluator(950.0, 10000.0).extraerDatosDeViaje(text).precio, 0.0)
        assertEquals("", surgeLabel("UberX ARS8,174"))
    }
    @Test fun idleTimeNeverClaimsOutdatedAppAndSuccessResetsWarning() {
        val watch = ReadingWatch()
        watch.observe(false, false, 0); watch.observe(false, false, 300000); assertFalse(watch.warning(300000))
        watch.observe(true, false, 300000); watch.observe(true, false, 360000); watch.observe(true, false, 420000)
        assertTrue(watch.warning(420000)); assertFalse(watch.warning(460000))
        watch.observe(true, true, 420100); assertFalse(watch.warning(420100))
    }
    @Test fun polygonHandlesHolesAndOutsidePoints() {
        val square = listOf(MapPoint(0.0,0.0), MapPoint(4.0,0.0), MapPoint(4.0,4.0), MapPoint(0.0,4.0))
        val hole = listOf(MapPoint(1.0,1.0), MapPoint(3.0,1.0), MapPoint(3.0,3.0), MapPoint(1.0,3.0))
        val polygon = NeighborhoodPolygon("Test", listOf(square, hole))
        assertTrue(polygon.contains(MapPoint(.5,.5))); assertFalse(polygon.contains(MapPoint(2.0,2.0))); assertFalse(polygon.contains(MapPoint(5.0,2.0)))
    }
    @Test fun streetNamedAfterBarrioDoesNotExcludeWrongArea() {
        val evaluator = TripEvaluator(0.0,0.0)
        val trip = DatosViaje(5000.0, 5.0, 10.0, "Constitución 123, CABA")
        assertNull(evaluator.evaluarViaje(trip, listOf("Constitución (CABA)")).zonaDetectada)
        assertNotNull(evaluator.evaluarViaje(trip.copy(destino = "Calle 123, CABA - Constitución"), listOf("Constitución (CABA)")).zonaDetectada)
    }
}
