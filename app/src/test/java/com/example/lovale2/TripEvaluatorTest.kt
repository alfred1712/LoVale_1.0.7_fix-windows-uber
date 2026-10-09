package com.example.lovale2

import com.example.lovale2.domain.DatosViaje
import com.example.lovale2.domain.MotivoEvaluacion
import com.example.lovale2.domain.TripEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripEvaluatorTest {
    @Test fun uberDisplayedRatesNeverReplaceFare() {
        val route = "A 6 min (1.6 km) Viaje: 30 min (8.4 km) Aceptar"
        for (rate in listOf("ARS812/km", "ARS812 / k m", "ARS812\n/\nkm", "ARS812 por km", "ARS812∕km", "ARS812⁄km", "ARS99999/h")) {
            for (amounts in listOf("ARS8,123 $rate", "$rate ARS8,123")) {
                val data = evaluator.extraerDatosDeViaje("UberX Exclusivo $amounts $route")
                assertTrue(amounts, data.completeReading)
                assertEquals(8123.0, data.precio, .001)
                val result = evaluator.evaluarViaje(data, emptyList())
                assertEquals(812.3, result.tarifaPorKm, .001)
                assertEquals(13538.333333, result.tarifaPorHora, .001)
                assertTrue(data.priceDiagnostics.contains("unitRates=1"))
            }
            val missing = evaluator.extraerDatosDeViaje("$rate $route")
            assertEquals(0.0, missing.precio, .001)
            assertTrue(!missing.completeReading)
        }
    }


    @Test
    fun tarifasIncluyenPickupEnLasTresPlataformas() {
        val cases = listOf(
            Triple("ARS6,084 A 6 min (1.9 km) Viaje: 19 min (8.2 km)", 6084.0 / 10.1, 6084.0 / 25.0 * 60),
            Triple("$3.686 en app $1.215/km 8 min · 1.9 km 13 min · 3 km", 3686.0 / 4.9, 3686.0 / 21.0 * 60),
            Triple("$8.100 (10 min 2,9 km) (15 min 6,1 km) Aceptar", 8100.0 / 9.0, 8100.0 / 25.0 * 60)
        )
        cases.forEach { (text, expectedKm, expectedHour) ->
            val result = evaluator.evaluarViaje(evaluator.extraerDatosDeViaje(text), emptyList())
            assertEquals(expectedKm, result.tarifaPorKm, 0.001)
            assertEquals(expectedHour, result.tarifaPorHora, 0.001)
        }
    }

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

    @Test
    fun rechazaTresTramosEnUnaLecturaAmbigua() {
        val data = evaluator.extraerDatosDeViaje("ARS8174 4 min 1.5 km Viaje: 25 min 9 km 2 min 9 m")
        org.junit.Assert.assertFalse(data.completeReading)
    }

    @Test
    fun noEmparejaDistanciasAtraviesandoUnaDireccion() {
        val data = evaluator.extraerDatosDeViaje("ARS8174 4 min Calle 1.5 km Viaje: 25 min 9 km")
        org.junit.Assert.assertFalse(data.completeReading)
    }

    @Test
    fun importe8174UsaAmbosTramosConMetrosYDecimales() {
        // Synthetic regression: the user's actual screenshot is still required.
        val data = evaluator.extraerDatosDeViaje("ARS8,174 A 4 min (509 m) Viaje: 25 min (10.9 km)")
        assertTrue(data.completeReading)
        assertEquals(10.9, data.distanciaKm, 0.00001)
        val result = evaluator.evaluarViaje(data, emptyList())
        assertEquals(8174.0 / 11.409, result.tarifaPorKm, 0.00001)
        assertEquals(8174.0 * 60 / 29, result.tarifaPorHora, 0.00001)
    }

    @Test
    fun uberPriority8174IncluyeRecogidaSinSumarElAdicional() {
        val data = evaluator.extraerDatosDeViaje("Uber Priority ARS8,174 Identidad digital verificada 4.92 (1101) " +
            "+ARS958.00 por inicio de viaje prioritario A4 min (1.5 km) Viel, CABA - Caballito " +
            "Viaje: 21 min (8.9 km) Necochea 949, CABA - La Boca Viaje disponible")
        assertTrue(data.completeReading)
        assertEquals(8174.0, data.precio, .001)
        assertEquals(8.9, data.distanciaKm, .001)
        val result = evaluator.evaluarViaje(data, emptyList())
        assertEquals(8174.0 / 10.4, result.tarifaPorKm, .001)
        assertEquals(19617.6, result.tarifaPorHora, .001)
    }

    @Test
    fun noEvaluaKmLeidosComoMetrosDecimales() {
        listOf("8.9 m", "8,9m").forEach { unit ->
            val data = evaluator.extraerDatosDeViaje("ARS8,174 A 4 min (1.5 km) Viaje: 21 min ($unit)")
            org.junit.Assert.assertFalse(data.completeReading)
            assertTrue(data.priceDiagnostics.contains("ambiguousUnit=true"))
        }
    }

    @Test
    fun ignoraElNetoDelPropioOverlayEnCapturaCompleta() {
        val data = evaluator.extraerDatosDeViaje("Neto estimado S $7.696 $5.417/km $19.618/h " +
            "Uber Priority ARS8,174 A4 min (1.5 km) Viaje: 21 min (8.9 km) " +
            "+ARS958.00 por inicio de viaje prioritario")
        assertTrue(data.completeReading)
        assertEquals(8174.0, data.precio, .001)
    }

    @Test
    fun lecturaReal5181ToleraNminSinPerderElViaje() {
        val data = evaluator.extraerDatosDeViaje("UberX ARS5,181 A4 min (0.6 km) " +
            "4127 Avenida Rivadavia Viaje: 16 nmin (5.5 km) Avenida Entre Ríos 2144")
        assertTrue(data.completeReading)
        val result = evaluator.evaluarViaje(data, emptyList())
        assertEquals(5181.0 / 6.1, result.tarifaPorKm, .001)
        assertEquals(15543.0, result.tarifaPorHora, .001)
    }

}
