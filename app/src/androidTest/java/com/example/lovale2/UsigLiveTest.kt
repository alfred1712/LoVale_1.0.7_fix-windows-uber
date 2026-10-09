package com.example.lovale2

import com.example.lovale2.data.network.UsigNeighborhoods
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Explicitly authorized offer address; no GPS or screenshot is sent. Requires Internet. */
class UsigLiveTest {
    @Test fun authorizedAddressResolvesAndIsCached() = runBlocking {
        val source = UsigNeighborhoods()
        val address = "Bulnes 2625, C1425DKU CABA, Argentina"
        assertEquals("Palermo", source.resolve(address))
        assertEquals("Palermo", source.cached(address))
        assertNull(source.resolve("Bulnes 2625, Provincia de Buenos Aires"))
    }
}
