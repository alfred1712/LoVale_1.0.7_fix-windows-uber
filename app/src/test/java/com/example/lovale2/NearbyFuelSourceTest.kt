package com.example.lovale2

import com.example.lovale2.data.network.NearbyFuelSource
import com.example.lovale2.domain.AutoFuelSettings
import com.example.lovale2.domain.FuelQuote
import com.example.lovale2.domain.isOlderFuelQuote
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat

class NearbyFuelSourceTest {
    private val settings = AutoFuelSettings(enabled = true)
    private val now = requireNotNull(SimpleDateFormat("yyyy-MM-dd").parse("2026-09-13")).time
    private fun row(id: Int = 1, lat: Double = -34.601, price: Int = 2000, date: String = "2026-09-12T10:00:00", brand: String = "YPF", product: Int = 2, hour: Int = 2) =
        """{"idempresa":$id,"empresa":"Estación","direccion":"Calle $id","localidad":"CABA","provincia":"CAPITAL FEDERAL","precio":$price,"fecha_vigencia":"$date","empresabandera":"$brand","idproducto":$product,"latitud":$lat,"longitud":-58.4,"idtipohorario":$hour}"""
    private fun response(vararg rows: String) = """{"success":true,"result":{"total":${rows.size},"records":[${rows.joinToString()}]}}"""
    private fun parse(vararg rows: String) = NearbyFuelSource.parse(response(*rows), settings, -34.6, -58.4, now)
    @Test fun selectsNearestNotCheapestOrNewest() {
        val quote = requireNotNull(parse(row(price = 2300, date = "2026-09-01T00:00:00"), row(2, -34.62, price = 1700)))
        assertEquals(2300.0, quote.price, .01)
        assertTrue(quote.station.startsWith("Calle 1"))
        assertEquals(.111, requireNotNull(quote.stationDistanceKm), .002)
    }
    @Test fun honorsBrandAndProduct() {
        val quote = requireNotNull(parse(row(1, -34.6001, brand = "AXION"), row(2, -34.6002, product = 6), row(3, -34.61)))
        assertTrue(quote.station.startsWith("Calle 3"))
    }
    @Test fun staleClosestDoesNotSilentlyChooseFartherOne() {
        assertNull(parse(row(date = "2025-01-01T00:00:00"), row(2, -34.62)))
    }
    @Test fun rejectsUnknownCoordinatesAndStationsOutsideRadius() {
        assertNull(parse(row(lat = 0.0), row(2, -35.5)))
    }
    @Test fun takesLatestPerScheduleThenConservativePrice() {
        val quote = requireNotNull(parse(row(price = 4000, date = "2026-08-01T00:00:00"), row(price = 2000), row(price = 2200, hour = 1)))
        assertEquals(2200.0, quote.price, .01)
    }
    @Test(expected = IllegalArgumentException::class) fun refusesPartialResultSet() {
        NearbyFuelSource.parse(response(row()).replace("\"total\":1", "\"total\":20"), settings, -34.6, -58.4, now)
    }
    @Test fun officialShellMappingAndRequestContainsNoDriverCoordinates() {
        val url = NearbyFuelSource.url(settings.copy(brand = "SHELL"))
        assertTrue(url.startsWith("https://"))
        assertTrue(url.contains("SHELL+C.A.P.S.A."))
        assertFalse(url.contains("-34.6"))
        assertFalse(url.contains("-58.4"))
    }
    @Test fun movingToAnotherStationCanUseItsEarlierPublication() {
        val current = settings.copy(station = "A", sourceUrl = "source", publishedAt = 200, locatedAt = 100)
        assertFalse(isOlderFuelQuote(current, FuelQuote(2000.0, 150, "B", "source", 1.0, 120)))
        assertTrue(isOlderFuelQuote(current, FuelQuote(2000.0, 150, "A", "source", 1.0, 120)))
    }
    @Test fun lateLocationResponseCannotReplaceNewerLookup() {
        val current = settings.copy(locatedAt = 200)
        assertTrue(isOlderFuelQuote(current, FuelQuote(2000.0, now, "B", "source", 1.0, 100)))
    }
}
