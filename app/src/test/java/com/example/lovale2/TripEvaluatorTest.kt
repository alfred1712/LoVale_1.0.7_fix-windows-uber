package com.example.lovale2

import com.example.lovale2.domain.DatosViaje
import com.example.lovale2.domain.MotivoEvaluacion
import com.example.lovale2.domain.TripEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripEvaluatorTest {

    private val evaluator = TripEvaluator(tarifaMinimaPorKm = 1000.0, tarifaMinimaPorHora = 0.0)
    private val zonasProhibidas = listOf("Retiro", "Constitución", "Once")

    @Test
    fun viajeRentableYPermitido() {
        val resultado = evaluator.evaluarViaje(DatosViaje(6000.0, 5.0, 15.0, "Palermo"), zonasProhibidas)
        assertTrue(resultado.aprobado)
        assertEquals(MotivoEvaluacion.APROBADO, resultado.motivo)
    }

    @Test
    fun viajeRechazadoPorZonaProhibida() {
        val resultado = evaluator.evaluarViaje(DatosViaje(10000.0, 4.0, 10.0, "Estación Retiro"), zonasProhibidas)
        assertEquals(MotivoEvaluacion.ZONA_EXCLUIDA, resultado.motivo)
    }

    @Test
    fun viajeRechazadoPorTarifaBaja() {
        val resultado = evaluator.evaluarViaje(DatosViaje(3000.0, 5.0, 10.0, "Belgrano"), zonasProhibidas)
        assertEquals(MotivoEvaluacion.TARIFA_KM_BAJA, resultado.motivo)
    }

    @Test
    fun extraePrecioKmYMinDeTextoNormal() {
        val datos = evaluator.extraerDatosDeViaje("$ 4.850 • 6.2 km • 18 min", destino = "Palermo")
        assertEquals(4850.0, datos.precio, 0.01)
        assertEquals(6.2, datos.distanciaKm, 0.01)
        assertEquals(18.0, datos.duracionMin, 0.01)
    }

    @Test
    fun tarifaPorKmYPorHoraSeCalculanSobreElRecorrido() {
        val resultado = evaluator.evaluarViaje(
            DatosViaje(6000.0, 5.0, 15.0, "Palermo"),
            emptyList()
        )
        assertEquals(1200.0, resultado.tarifaPorKm, 0.01)
        assertEquals(24000.0, resultado.tarifaPorHora, 0.01)
    }

    @Test
    fun soportaFormatoArgentinoDeImporte() {
        val datos = evaluator.extraerDatosDeViaje("$ 12.345,50 • 8,5 km • 30 min", "Palermo")
        assertEquals(12345.50, datos.precio, 0.01)
        assertEquals(8.5, datos.distanciaKm, 0.01)
        assertEquals(30.0, datos.duracionMin, 0.01)
    }

    @Test
    fun usaUltimoKmYMinComoRecorridoCuandoHayPickupYViaje() {
        val datos = evaluator.extraerDatosDeViaje(
            "$ 8.000 • 1,2 km • 4 min • 7,5 km • 24 min",
            "Palermo",
            "Caballito"
        )
        assertEquals(8000.0, datos.precio, 0.01)
        assertEquals(7.5, datos.distanciaKm, 0.01)
        assertEquals(24.0, datos.duracionMin, 0.01)
        assertEquals(1.2, datos.pickupDistanceKm, 0.01)
    }

    @Test
    fun detectaZonaEnPickupYDestino() {
        val resultado = evaluator.evaluarViaje(
            DatosViaje(8000.0, 8.0, 20.0, "Palermo", pickup = "Retiro, CABA"),
            zonasProhibidas
        )
        assertEquals(MotivoEvaluacion.ZONA_EXCLUIDA, resultado.motivo)
        assertEquals("Retiro", resultado.zonaPickupDetectada)
    }

    @Test
    fun devuelveCerosSiFaltanDatos() {
        val datos = evaluator.extraerDatosDeViaje("Buscando viaje...", destino = "")
        assertEquals(0.0, datos.precio, 0.01)
        assertEquals(0.0, datos.distanciaKm, 0.01)
    }
    @Test
    fun soportaFormatoUberConPrefijoARS() {
        val datos = evaluator.extraerDatosDeViaje(
            "ARS6,084 A 6 min (1.9 km) Adolfo Alsina, CABA - Balvanera Viaje: 19 min (8.2 km) Combate de Membrillar 381, Valentin Alsina, Lanus"
        )
        assertEquals(6084.0, datos.precio, 0.01)
        assertEquals(8.2, datos.distanciaKm, 0.01)
        assertEquals(19.0, datos.duracionMin, 0.01)
        assertEquals(1.9, datos.pickupDistanceKm, 0.01)
    }

    @Test
    fun soportaFormatoDidiCentroDeViajes() {
        val datos = evaluator.extraerDatosDeViaje(
            "\$8.100 (10 min 2,9 km) Jean Jaures 550, CABA - Balvanera (15 min 6,1 km) Calle Teniente General Donato Alvarez 1584, CABA"
        )
        assertEquals(8100.0, datos.precio, 0.01)
        assertEquals(6.1, datos.distanciaKm, 0.01)
        assertEquals(15.0, datos.duracionMin, 0.01)
        assertEquals(2.9, datos.pickupDistanceKm, 0.01)
    }

    @Test
    fun soportaFormatoCabify() {
        val datos = evaluator.extraerDatosDeViaje(
            "\$3.686 8 min · 1.9 km Caballito - Campichuelo, 662 13 min · 3 km Palermo - Avenida Cordoba, 3933"
        )
        assertEquals(3686.0, datos.precio, 0.01)
        assertEquals(3.0, datos.distanciaKm, 0.01)
        assertEquals(13.0, datos.duracionMin, 0.01)
        assertEquals(1.9, datos.pickupDistanceKm, 0.01)
    }

}

