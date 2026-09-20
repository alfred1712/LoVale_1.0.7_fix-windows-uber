package com.example.lovale2

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.lovale2.domain.AutoFuelSettings
import com.example.lovale2.ui.FuelSelectionDialog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FuelSelectionTest {
    @get:Rule val compose = createComposeRule()
    @Test fun brandChangesProductChoicesAndKeepsValidSelection() {
        var saved: AutoFuelSettings? = null
        compose.setContent { MaterialTheme { FuelSelectionDialog(AutoFuelSettings(), {}, { saved = it }) } }
        compose.onNodeWithText("Empresa: YPF ▾").performClick()
        compose.onNodeWithText("AXION").performClick()
        compose.onNodeWithText("Combustible: Súper ▾").performClick()
        compose.onNodeWithText("Quantium").performClick()
        compose.onNodeWithText("Guardar y consultar").performClick()
        compose.runOnIdle {
            assertEquals("AXION", saved?.brand)
            assertEquals("nafta-quantium", saved?.product)
            assertTrue(requireNotNull(saved).valid)
        }
    }
}
