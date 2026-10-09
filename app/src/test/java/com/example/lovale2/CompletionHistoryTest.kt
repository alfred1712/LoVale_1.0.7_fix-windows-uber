package com.example.lovale2

import com.example.lovale2.data.settings.*
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class CompletionHistoryTest {
    private val offer = OfferRecord(1000, "DiDi", 8174.0, 786.0, 19618.0, "RED", "TARIFA_KM_BAJA", id = "one")
    @Test fun confirmationIsIdempotentAndOnlyChangesMatchingOffer() {
        val records = listOf(offer, offer.copy(id = "two"))
        val first = recordCompletion(records, "one", true, false, 2000)
        val repeated = recordCompletion(first, "one", true, false, 3000)
        assertEquals(first, repeated)
        assertEquals(2000L, first.first().completedAt)
        assertTrue(first.first().completedAutomatically)
        assertEquals(records.last(), first.last())
    }
    @Test fun uncertaintyDoesNotEnterConfirmedEarnings() {
        val pending = recordCompletion(listOf(offer), "one", false, true, 2000).single()
        assertEquals(0L, pending.completedAt)
        assertTrue(pending.reviewRequired)
        val confirmed = recordCompletion(listOf(pending), "one", true, false, 3000).single()
        assertFalse(confirmed.reviewRequired)
    }
    @Test fun oldHistoryRemainsCompatible() {
        val decoded = Gson().fromJson("""{"timestamp":1000,"platform":"DiDi","price":8174,"rateKm":786,"rateHour":19618,"level":"RED","reason":"TEST","completedAt":0}""", OfferRecord::class.java)
        assertFalse(decoded.completedAutomatically)
        assertFalse(decoded.reviewRequired)
        assertEquals(8174.0, decoded.price, 0.0)
    }
}
