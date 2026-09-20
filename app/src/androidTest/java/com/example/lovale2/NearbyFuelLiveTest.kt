package com.example.lovale2

import com.example.lovale2.data.network.NearbyFuelSource
import com.example.lovale2.domain.AutoFuelSettings
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class NearbyFuelLiveTest {
    @Test fun officialApiHasFullGeolocatedResults() {
        // Public test coordinate only; never reads or changes driver location/settings.
        val settings = AutoFuelSettings(enabled = true)
        val json = NearbyFuelSource.download(settings)
        val result = JsonParser().parse(json).asJsonObject.getAsJsonObject("result")
        val rows = result.getAsJsonArray("records")
        assertTrue(rows.size() > 0)
        assertEquals(result.get("total").asInt, rows.size())
        assertTrue(rows.any { !it.asJsonObject.get("latitud").isJsonNull })
        val quote = NearbyFuelSource.parse(json, settings, -34.6037, -58.3816, System.currentTimeMillis())
        // A nearby station may legitimately have no recent published price.
        if (quote != null) {
            assertTrue(quote.price > 0)
            assertTrue(requireNotNull(quote.stationDistanceKm) <= 50)
            assertTrue(quote.sourceUrl.startsWith("https://datos.energia.gob.ar/"))
        }
    }
}
