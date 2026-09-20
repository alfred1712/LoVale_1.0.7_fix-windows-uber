package com.example.lovale2

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.lovale2.domain.VehicleCosts
import com.example.lovale2.ui.VehicleSetup
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class VehicleSetupTest {
    @get:Rule val compose = createComposeRule()
    @Test fun setupRequiresPriceAndSavesOneConfiguration() {
        var saved: VehicleCosts? = null
        compose.setContent { MaterialTheme { VehicleSetup(VehicleCosts(), {}, { saved = it }) } }
        compose.onNodeWithText("Guardar").assertIsNotEnabled()
        compose.onNodeWithText("Usar referencia Fiat Cronos 1.3 manual").performClick()
        compose.onNodeWithText("Precio de referencia $/L").performScrollTo().performTextInput("1500")
        compose.onNodeWithText("Guardar").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertTrue(requireNotNull(saved).ready)
            assertEquals(8.0, saved?.consumptionPer100Km ?: 0.0, .001)
            assertEquals(1500.0, saved?.fuelPrice ?: 0.0, .001)
        }
    }
    @Test fun changingFuelDoesNotReusePriceInDifferentUnits() {
        val original = VehicleCosts(true, "Prueba", "Nafta", 8.0, 1500.0, 20.0, 1)
        compose.setContent { MaterialTheme { VehicleSetup(original, {}, {}) } }
        compose.onNodeWithText("GNC").performScrollTo().performClick()
        compose.onNodeWithText("Guardar").assertIsNotEnabled()
        compose.onNodeWithText("Precio de referencia $/m³").performScrollTo().assertExists()
    }
}
