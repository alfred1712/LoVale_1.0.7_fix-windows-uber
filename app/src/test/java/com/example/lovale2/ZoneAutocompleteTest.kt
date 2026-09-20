package com.example.lovale2

import com.example.lovale2.domain.*
import org.junit.Assert.*
import org.junit.Test

class ZoneAutocompleteTest {
    private val catalog = listOf("Palermo (CABA)", "Lanús (PBA)", "Núñez (CABA)", "Villa Urquiza (CABA)")

    @Test fun tresLetrasCompletanNombre() {
        assertTrue(ZoneAutocomplete.search("pa", catalog, emptyList()).isEmpty())
        assertEquals(listOf("Palermo (CABA)"), ZoneAutocomplete.search("pal", catalog, emptyList()))
        assertEquals(listOf("Lanús (PBA)"), ZoneAutocomplete.search("LAN", catalog, emptyList()))
        assertEquals(listOf("Núñez (CABA)"), ZoneAutocomplete.search("nune", catalog, emptyList()))
        assertEquals(listOf("Villa Urquiza (CABA)"), ZoneAutocomplete.search("urq", catalog, emptyList()))
    }

    @Test fun noSugiereYaAgregadasNiCoincidenciasSoloEnPartido() {
        assertTrue(ZoneAutocomplete.search("lan", catalog, listOf("Lanus (PBA)")).isEmpty())
        assertTrue(ZoneAutocomplete.search("caba", catalog, emptyList()).isEmpty())
    }

    @Test fun zonaConEtiquetaSeReconoceEnViaje() {
        val evaluator = TripEvaluator(0.0, 0.0)
        val trip = DatosViaje(8000.0, 5.0, 20.0, "Retiro, Buenos Aires")
        assertEquals(MotivoEvaluacion.ZONA_EXCLUIDA,
            evaluator.evaluarViaje(trip, listOf("Retiro (CABA)")).motivo)
        assertEquals(MotivoEvaluacion.ZONA_EXCLUIDA,
            evaluator.evaluarViaje(trip.copy(destino = "Lanús"), listOf("Lanús (Lanús, PBA)")).motivo)
        assertEquals(MotivoEvaluacion.APROBADO,
            evaluator.evaluarViaje(trip.copy(destino = "Palermo"), listOf("Retiro (CABA)")).motivo)
    }
}
