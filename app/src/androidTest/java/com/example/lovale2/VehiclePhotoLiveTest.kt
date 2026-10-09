package com.example.lovale2

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.lovale2.data.network.VehiclePhotos
import com.example.lovale2.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Explicit live-provider check; requires an already configured supported vehicle and Internet. */
class VehiclePhotoLiveTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun configuredVehiclePhotoLoadsAndIsVisibleWithAttribution() {
        compose.runOnUiThread { compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        val vehicle = runBlocking { SettingsRepository(compose.activity).settingsFlow.first().vehicleCosts.vehicle }
        val model = requireNotNull(VehiclePhotos.modelFor(vehicle)) { "Configure a supported vehicle before this live test" }
        val photo = runBlocking { VehiclePhotos.load(compose.activity, vehicle) }
        compose.onNodeWithContentDescription("Combustible").performClick()
        assertNotNull("The provider or cached thumbnail must be available", photo)
        assertEquals(model, photo?.model)
        assertTrue(requireNotNull(photo).bitmap.width in 1..1024)
        compose.waitUntil(15000) { compose.onAllNodesWithContentDescription("Foto de $model").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Foto de $model").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(photo.credit).performScrollTo().assertIsDisplayed()
        File(compose.activity.getExternalFilesDir(null), "beta2-vehiculo.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithContentDescription("Preferencias").performClick()
        compose.onNodeWithText("Alertas").assertIsDisplayed()
        File(compose.activity.getExternalFilesDir(null), "beta2-preferencias.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
