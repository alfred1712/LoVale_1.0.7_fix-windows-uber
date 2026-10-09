package com.example.lovale2

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

/** Uses UI actions only, so it can also exercise the locally signed R8 build. */
class ReleaseRuntimeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun optimizedBuildCanEvaluateOfferAndOpenStoredData() {
        compose.runOnUiThread { compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        compose.onNodeWithText("Probar una oferta").performScrollTo().performClick()
        compose.onNodeWithText("Mostrar prueba").performScrollTo().performClick()
        compose.onNodeWithText("Prueba mostrada durante 9 segundos. No se guarda en el historial.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cerrar").performClick()
        compose.onNodeWithContentDescription("Combustible").performClick()
        compose.onNodeWithText("Mi vehículo").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Jornada").performClick()
        compose.onNodeWithText("Historial de ofertas").performScrollTo().performClick()
        compose.onNodeWithText("Ofertas analizadas; no son viajes realizados.").assertIsDisplayed()
        compose.onNodeWithText("Cerrar").performClick()
    }
}
