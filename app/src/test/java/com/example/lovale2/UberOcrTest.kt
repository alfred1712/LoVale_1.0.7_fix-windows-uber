package com.example.lovale2

import com.example.lovale2.domain.OfferText
import com.example.lovale2.domain.TripEvaluator
import org.junit.Assert.*
import org.junit.Test

class UberOcrTest {
    private val evaluator = TripEvaluator(950.0, 10000.0)

    @Test fun capturaBoostIncluido9340NoUsa2373NiLoSumaOtraVez() {
        val text = "UberX Exclusivo ARS9,340 Identidad digital verificada 4.93 (440) " +
            "Boost+ de ARS2,373.00 incluido A 4 min (0.6 km) Don Bosco, CABA - Almagro " +
            "Viaje: 27 min (6.4 km) Avenida del Libertador 4101, CABA - Palermo Aceptar"
        val data = evaluator.extraerDatosDeViaje(text)
        assertEquals(9340.0, data.precio, .001)
        val result = evaluator.evaluarViaje(data, emptyList())
        assertEquals(1334.285714, result.tarifaPorKm, .001)
        assertEquals(18077.419355, result.tarifaPorHora, .001)
    }

    @Test fun capturaRadar7625ConBoost1869Incluido() {
        val text = "Radar de solicitud de viaje 2 UberX ARS7,625 Identidad digital verificada 4.89 (30) " +
            "Boost+ de ARS1,869.00 incluido A 6 min (1.0 km) Yapeyú, CABA - Almagro " +
            "Viaje: 18 min (6.4 km) Juana Manso 719, CABA - Puerto Madero Viaje disponible"
        val data = evaluator.extraerDatosDeViaje(text)
        assertEquals(7625.0, data.precio, .001)
        val result = evaluator.evaluarViaje(data, emptyList())
        assertEquals(1030.405405, result.tarifaPorKm, .001)
        assertEquals(19062.5, result.tarifaPorHora, .001)
    }

    @Test fun boostConEtiquetaOcrParcialNuncaEsPrecioTotal() {
        listOf("Boost + de ARS2,373.00 incluido", "Boost de ARS2,373.00", "ARS2,373.00 incluido").forEach {
            val data = evaluator.extraerDatosDeViaje("ARS9,340 $it A 4 min (0.6 km) Viaje: 27 min (6.4 km)")
            assertEquals(it, 9340.0, data.precio, .001)
        }
    }

    @Test fun turboNoReemplazaImporteDeOfertaSinSignoMas() {
        val route = "A 6 min (1.9 km) Viaje: 19 min (8.2 km) Aceptar"
        listOf("Turbo ARS1,200 UberX ARS6,084 $route",
            "ARS6,084 $route Turbo: ARS1,200",
            "ARS1,200 de Turbo ARS6,084 $route",
            "Promo hasta ARS12,000 UberX ARS6,084 $route",
            "ARS6,084 incluye ARS1,200 de Turbo $route",
            "ARS6,084 $route ARS1,200 Turbo").forEach { text ->
            val data = evaluator.extraerDatosDeViaje(text)
            assertEquals(text, 6084.0, data.precio, .001)
            assertTrue(data.completeReading)
            assertTrue(data.priceDiagnostics.contains("excluded=1"))
        }
    }

    @Test fun soloAdicionalTurboNoSeEvaluaComoPrecioReal() {
        val data = evaluator.extraerDatosDeViaje("Turbo ARS1,200 A 6 min (1.9 km) Viaje: 19 min (8.2 km)")
        assertEquals(0.0, data.precio, .001)
        assertFalse(data.completeReading)
    }

    @Test fun mencionTurboSinImporteAdicionalNoEliminaTotal() {
        val data = evaluator.extraerDatosDeViaje("ARS6,084 incluye Turbo A 6 min (1.9 km) Viaje: 19 min (8.2 km)")
        assertEquals(6084.0, data.precio, .001)
    }

    @Test fun ofertaRealNoConfundeAdicionalDelMapaConPrecio() {
        val text = "ARS8,415 A 4 min (0.8 km) Reserva Reservar UberX Exclusivo " +
            "DNI Verificado 5.00 (24) Viaje: 33 min (6.9 km) a 1-7 min " +
            "1-8 min +ARS 410 Aceptar"
        val trip = evaluator.extraerDatosDeViaje(text)
        assertEquals(8415.0, trip.precio, 0.001)
        assertEquals(0.8, trip.pickupDistanceKm, 0.001)
        assertEquals(6.9, trip.distanciaKm, 0.001)
        assertEquals(33.0, trip.duracionMin, 0.001)
        val result = TripEvaluator(1000.0, 20000.0).evaluarViaje(trip, emptyList())
        assertEquals(4.0, trip.pickupDurationMin, 0.001)
        assertEquals(8415.0 / 37.0 * 60.0, result.tarifaPorHora, 0.001)
        assertEquals(8415.0 / 7.7, result.tarifaPorKm, 0.001)
        assertEquals(com.example.lovale2.domain.MotivoEvaluacion.TARIFA_HORA_BAJA, result.motivo)
    }

    @Test fun adicionalesSolosNoSonPrecioDeViaje() {
        assertEquals(0.0, evaluator.extraerDatosDeViaje("+ ARS 410 +ARS 2,290").precio, 0.001)
        assertEquals(8415.0, evaluator.extraerDatosDeViaje("+ARS 410 ARS8,415 33 min 6.9 km").precio, 0.001)
    }

    @Test fun variantesOcrPasanDetectorYParser() {
        listOf("ARS6,084", "A R S 6 084", "AR$6.084", "ARS 6, 084").forEach { price ->
            val text = "$price\nA 6 m1n (1, 9 k m)\nViaje: 19 m l n (8. 2 km)"
            assertTrue(text, OfferText.isUberCandidate(text))
            val trip = evaluator.extraerDatosDeViaje(text)
            assertEquals(6084.0, trip.precio, 0.001)
            assertEquals(1.9, trip.pickupDistanceKm, 0.001)
            assertEquals(8.2, trip.distanciaKm, 0.001)
            assertEquals(19.0, trip.duracionMin, 0.001)
        }
    }

    @Test fun metrosYOrdenInverso() {
        val trip = evaluator.extraerDatosDeViaje("ARS6084 493m · 2 min Viaje: 8,2 km · 19 min")
        assertEquals(0.493, trip.pickupDistanceKm, 0.001)
        assertEquals(8.2, trip.distanciaKm, 0.001)
    }

    @Test fun cabifyNoUsaTarifaUnitariaComoPrecio() {
        val trip = evaluator.extraerDatosDeViaje("$1.215/km $3.686 en app 8 min · 1.9 km 13 min · 3 km")
        assertEquals(3686.0, trip.precio, 0.001)
        assertEquals(3.0, trip.distanciaKm, 0.001)
    }

    @Test fun importeConCentavosEnPunto() {
        assertEquals(6084.50, evaluator.extraerDatosDeViaje("ARS6084.50 19 min 8.2 km").precio, 0.001)
    }

    @Test fun rechazaPantallasSinOferta() {
        listOf("Estás conectado", "ARS6084 ganancias de hoy", "6 min 1.9 km").forEach {
            assertFalse(OfferText.isUberCandidate(it))
        }
        assertEquals(0.0, evaluator.extraerDatosDeViaje("ARS6084 buscando viaje").distanciaKm, 0.001)
    }
}
