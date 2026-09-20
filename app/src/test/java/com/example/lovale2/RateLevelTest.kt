package com.example.lovale2

import com.example.lovale2.domain.RateLevel
import com.example.lovale2.domain.rateLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class RateLevelTest {
    @Test fun limitesDelOchentaYCincoYCienPorCiento() {
        assertEquals(RateLevel.RED, rateLevel(849.99, 1000.0))
        assertEquals(RateLevel.YELLOW, rateLevel(850.0, 1000.0))
        assertEquals(RateLevel.YELLOW, rateLevel(999.99, 1000.0))
        assertEquals(RateLevel.GREEN, rateLevel(1000.0, 1000.0))
    }
    @Test fun criteriosIndependientes() {
        assertEquals(RateLevel.GREEN, rateLevel(1200.0, 1000.0))
        assertEquals(RateLevel.RED, rateLevel(15000.0, 20000.0))
        assertEquals(RateLevel.YELLOW, rateLevel(18000.0, 20000.0))
    }
    @Test fun minimoDesactivadoODatoAusenteEsNeutro() {
        assertEquals(RateLevel.UNSET, rateLevel(1200.0, 0.0))
        assertEquals(RateLevel.UNSET, rateLevel(Double.NaN, 1000.0))
    }
}
