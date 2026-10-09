package com.example.lovale2

import com.example.lovale2.data.network.VehiclePhotos
import org.junit.Assert.*
import org.junit.Test

class VehiclePhotoModelTest {
    @Test fun matchesModelDespiteVersionAndAccents() {
        assertEquals("Fiat Cronos", VehiclePhotos.modelFor("Fiat Cronos Precision CVT 2027"))
        assertEquals("Citroën C3", VehiclePhotos.modelFor("citroen C3 1.6"))
        assertEquals("Toyota Corolla Cross", VehiclePhotos.modelFor("Toyota Corolla Cross híbrido"))
    }
    @Test fun doesNotGuessAnUnknownOrPartialModel() {
        assertNull(VehiclePhotos.modelFor("Ford"))
        assertNull(VehiclePhotos.modelFor("Ford Kuga"))
        assertNull(VehiclePhotos.modelFor("Fiat CronosExtra"))
        assertNull(VehiclePhotos.modelFor(""))
    }
}
