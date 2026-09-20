package com.example.lovale2

import com.example.lovale2.domain.*
import org.junit.Assert.*
import org.junit.Test

class SeptemberRegressionTest {
    private val evaluator = TripEvaluator(950.0, 10000.0)
    @Test fun ocrCurrencyGlyphsAndPickupOneMinute() {
        val uber = evaluator.extraerDatosDeViaje("UberX ARS8,5ll A8 min (2.5 km) Viaje:26 min (15.6 km) Me interesa")
        assertTrue(uber.completeReading)
        assertEquals(8511.0, uber.precio, .001)
        assertEquals(8511.0 / 18.1, evaluator.evaluarViaje(uber, emptyList()).tarifaPorKm, .001)
        val one = evaluator.extraerDatosDeViaje("ARS4,916 Boost+ de ARS1,326.00 incluido Al min (0.2 km) Viaje:16 min (6.4 km)")
        assertTrue(one.completeReading)
        assertEquals(1.0, one.pickupDurationMin, .001)
    }
    @Test fun didiBaseAndBonusNeverReplaceHeadline() {
        val data = evaluator.extraerDatosDeViaje("Pago en efectivo $4.000 $500 adicionales $1.600 de tarifa base dinámica 7 min (2,2 km) 15 min (5,3 km) Aceptar")
        assertTrue(data.completeReading)
        assertEquals(4000.0, data.precio, .001)
        assertEquals(4000.0 / 7.5, evaluator.evaluarViaje(data, emptyList()).tarifaPorKm, .001)
        assertFalse(evaluator.extraerDatosDeViaje("$4.000 7 min (2,2 km) 15 min (53 km)").completeReading)
    }
    @Test fun spatialCardsDoNotMixPricesWhenMlKitReturnsColumns() {
        val lines = listOf(OfferLine("Express Nuevo",0,0,100,20), OfferLine("$9.200",0,30,100,50),
            OfferLine("Express Nuevo",0,220,100,240), OfferLine("$3.700",0,250,100,270),
            OfferLine("Aceptar",0,180,100,200), OfferLine("Aceptar",0,400,100,420),
            OfferLine("5 min 1,1 km",0,70,100,90), OfferLine("29 min 12,1 km",0,120,100,140),
            OfferLine("7 min 1,8 km",0,290,100,310), OfferLine("13 min 3,7 km",0,340,100,360))
        val cards = OfferLayout.cards(lines)
        assertEquals(listOf(9200.0,3700.0), cards.map { evaluator.extraerDatosDeViaje(it.text).precio })
    }
    @Test fun centerAcceptanceCanBeLinkedToClickedCard() {
        val t = CompletedTripTracker()
        t.offered(CompletedTripTracker.Offer("one","didi",9200.0,1000))
        t.offered(CompletedTripTracker.Offer("two","didi",3700.0,1000))
        assertEquals("two",t.click("didi",listOf("Aceptar"),2000,3700.0))
    }
    @Test fun homeArrivalToleratesIndoorFixIntervalsButNotStaleEvidence() {
        val detector = HomeArrival(JourneyFix(0.0,0.0,5.0,0),true)
        for (time in 1000L..241000L step 60000) assertFalse(detector.add(JourneyFix(0.0,0.0,60.0,time,0.0),time))
        assertTrue(detector.add(JourneyFix(0.0,0.0,60.0,301000,0.0),301000))
        val stale = HomeArrival(JourneyFix(0.0,0.0,5.0,0),true)
        stale.add(JourneyFix(0.0,0.0,5.0,1000,0.0),1000)
        assertFalse(stale.add(JourneyFix(0.0,0.0,5.0,401000,0.0),401000))
    }
    @Test fun closesForBothVisibleRejectGlyphs() {
        assertTrue(OfferDismissal.isCloseAction("X"))
        assertTrue(OfferDismissal.isCloseAction("×"))
        assertTrue(OfferDismissal.isCloseAction("Rechazo permitido"))
    }
}
