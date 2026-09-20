package com.example.lovale2
import com.example.lovale2.domain.OfferDismissal
import org.junit.Assert.*
import org.junit.Test
class OfferDismissalTest {
    @Test fun needsTwoIdleReadsAndNeverClosesAnOffer() {
        val state = OfferDismissal()
        assertFalse(state.observe("Estás conectado", false))
        assertFalse(state.observe("Estás conectado ARS8174", true))
        assertFalse(state.observe("Estás conectado", false))
        assertTrue(state.observe("Estás conectado", false))
    }
    @Test fun emptyOrUnreadableScreenDoesNotClose() {
        val state = OfferDismissal()
        repeat(3) { assertFalse(state.observe("", false)) }
        assertFalse(state.observe("Estás conectado", false))
        assertFalse(state.observe("Mapa", false))
        assertFalse(state.observe("Estás conectado", false))
    }
    @Test fun onlyExplicitDismissalButtons() {
        assertTrue(OfferDismissal.isCloseAction("Cerrar oferta"))
        assertFalse(OfferDismissal.isCloseAction("Aceptar"))
        assertFalse(OfferDismissal.isCloseAction("Viaje disponible"))
    }
}
