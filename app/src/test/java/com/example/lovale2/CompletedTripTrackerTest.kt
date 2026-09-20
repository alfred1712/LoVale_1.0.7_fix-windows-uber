package com.example.lovale2

import com.example.lovale2.domain.CompletedTripTracker
import org.junit.Assert.*
import org.junit.Test

class CompletedTripTrackerTest {
    private val platform = "com.didiglobal.driver"
    private val finalText = "Viaje finalizado Total del viaje: ARS8,174"
    private fun accepted() = CompletedTripTracker().apply {
        offered(CompletedTripTracker.Offer("one", platform, 8174.0, 1000))
        click(platform, listOf("Aceptar"), 2000)
    }
    @Test fun requiresAcceptanceStartFinishActionAndTwoMatchingFinals() {
        val tracker = accepted()
        assertNull(tracker.observe(platform, "Viaje en curso", 10000))
        tracker.click(platform, listOf("Finalizar viaje"), 100000)
        assertNull(tracker.observe(platform, finalText, 101000))
        val decision = tracker.observe(platform, finalText, 102000)
        assertEquals("one", decision?.id)
        assertTrue(decision?.completed == true)
        assertNull(tracker.observe(platform, finalText, 103000))
    }
    @Test fun disappearingAndCancelledOffersNeverCountAsCompleted() {
        val tracker = accepted()
        assertNull(tracker.observe(platform, "Buscando viajes", 30000))
        val cancelled = tracker.observe(platform, "Viaje cancelado", 40000)
        assertEquals(false, cancelled?.completed)
        assertNull(tracker.observe(platform, finalText, 60000))
    }
    @Test fun twoMatchingReceiptsAfterStartDoNotRequireAnAccessibleFinishButton() {
        val tracker = accepted()
        tracker.observe(platform, "Viaje en curso", 10000)
        assertNull(tracker.observe(platform, finalText, 101000))
        assertTrue(tracker.observe(platform, finalText, 102000)?.completed == true)
        val other = accepted()
        other.click(platform, listOf("Finalizar viaje"), 100000)
        assertNull(other.observe(platform, finalText, 101000))
        assertNull(other.observe(platform, finalText, 102000))
    }
    @Test fun ambiguousOfferAndOtherPlatformCannotBeLinked() {
        val tracker = CompletedTripTracker()
        tracker.offered(CompletedTripTracker.Offer("one", platform, 8174.0, 1000))
        tracker.offered(CompletedTripTracker.Offer("two", platform, 7000.0, 1500))
        tracker.click(platform, listOf("Aceptar"), 2000)
        assertNull(tracker.reset("pause"))
        val other = accepted()
        assertNull(other.observe("com.ubercab.driver", "Viaje en curso", 10000))
        other.click("com.ubercab.driver", listOf("Finalizar viaje"), 100000)
        assertNull(other.observe("com.ubercab.driver", finalText, 102000))
    }
    @Test fun changedFinalFareUsesVerifiedReceiptAfterFinishAction() {
        val tracker = accepted()
        tracker.observe(platform, "Viaje en curso", 10000)
        tracker.click(platform, listOf("Finalizar viaje"), 100000)
        tracker.observe(platform, "Viaje completado Total: $9.000", 101000)
        assertEquals(9000.0, tracker.observe(platform, "Viaje completado Total: $9.000", 102000)?.finalPrice ?: 0.0, .01)
    }
    @Test fun resetAndOldEvidenceCannotConfirm() {
        val tracker = accepted()
        assertEquals("one", tracker.reset("pausa")?.id)
        assertNull(tracker.observe(platform, finalText, 102000))
        val old = accepted()
        old.observe(platform, "Viaje en curso", 10000)
        old.click(platform, listOf("Finalizar viaje"), 100000)
        assertNull(old.observe(platform, finalText, 250000))
        assertNull(old.observe(platform, finalText, 251000))
    }
}
