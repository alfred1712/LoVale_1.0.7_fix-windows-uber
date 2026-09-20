package com.example.lovale2

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.rules.ExternalResource
import androidx.test.platform.app.InstrumentationRegistry
import android.Manifest
import android.os.Build
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import java.io.File

class NavigationTest {
    @get:Rule(order = 0) val permissions = object : ExternalResource() {
        override fun before() {
            if (Build.VERSION.SDK_INT >= 33) {
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                instrumentation.uiAutomation.grantRuntimePermission(
                    instrumentation.targetContext.packageName, Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Before fun keepScreenOn() {
        compose.runOnUiThread { compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    @Test fun diagnosticControlsAreReachableAndVisualRecordingIsOptIn() {
        compose.onNodeWithText("Ganancias").performClick()
        compose.onNodeWithText("Preparar registro").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Registrar una prueba").assertIsDisplayed()
        if (Build.VERSION.SDK_INT >= 34) compose.onNode(isToggleable()).assertIsOff()
        compose.onNodeWithText("Cancelar").assertIsDisplayed().performClick()
        compose.onNodeWithText("Registrar una prueba").assertDoesNotExist()
    }

    @Test fun tabsSeparateContentAndStayInsideSystemBars() {
        compose.onNodeWithText("Panel de control").assertIsDisplayed()
        compose.onNodeWithText("Mi vehículo").assertDoesNotExist()
        compose.onNodeWithText("Ganancias de hoy").assertDoesNotExist()
        // Compare content bounds with the actual device insets, not a fixed screen size.
        val density = compose.activity.resources.displayMetrics.density
        val insets = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
            ?.getInsets(WindowInsetsCompat.Type.systemBars())
        val title = compose.onNodeWithText("Panel de control").getUnclippedBoundsInRoot()
        val bottom = compose.onNodeWithText("Inicio").getUnclippedBoundsInRoot()
        assertTrue(title.top.value * density >= (insets?.top ?: 0))
        assertTrue(bottom.bottom.value * density <= compose.activity.window.decorView.height - (insets?.bottom ?: 0))
        capture("inicio")
        compose.onNodeWithText("Combustible").performClick()
        compose.onNodeWithText("Mi vehículo").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ganancias de hoy").assertDoesNotExist()
        capture("combustible")
        compose.onNodeWithText("Ganancias").performClick()
        compose.onNodeWithText("Ganancias de hoy").assertIsDisplayed()
        compose.onNodeWithText("Km de jornada · GPS").assertIsDisplayed()
        compose.onNodeWithText("Historial de ofertas").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Mi vehículo").assertDoesNotExist()
        capture("ganancias")
        compose.onNodeWithText("Inicio").performClick()
        compose.onNodeWithText("Panel de control").assertIsDisplayed()
    }

    private fun capture(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(compose.activity.getExternalFilesDir(null), "beta19-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
