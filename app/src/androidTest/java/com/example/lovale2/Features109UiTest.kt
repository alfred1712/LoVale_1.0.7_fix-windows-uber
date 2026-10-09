package com.example.lovale2

import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.lovale2.data.settings.DriverOptionsStore
import com.example.lovale2.domain.readNeighborhoodMap
import com.example.lovale2.services.LoValeWidget
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class Features109UiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun awake() { compose.runOnUiThread { compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } }
    @Test fun preferencesAndOfferStatisticsAreReachable() {
        val store = DriverOptionsStore.get(compose.activity)
        val before = store.state.value
        try {
            compose.onNodeWithContentDescription("Preferencias").performClick()
            compose.onNodeWithText("Alertas").assertIsDisplayed()
            compose.onNodeWithText("Guardar").performScrollTo().performClick()
            compose.onNodeWithText("Meta diaria $").assertDoesNotExist()
            compose.onNodeWithContentDescription("Jornada").performClick()
            compose.onNodeWithText("Tu jornada").assertIsDisplayed()
            compose.onNodeWithText("Confirmación automática · en prueba").assertDoesNotExist()
            compose.onNodeWithText("Estadísticas y exportación").performScrollTo().performClick()
            compose.onNodeWithText("Estadísticas").assertIsDisplayed()
            compose.onNodeWithText("Exportar CSV / Excel").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Cerrar").performClick()
        } finally { compose.runOnUiThread { store.save(before) } }
    }
    @Test fun mapLoadsAllOfficialNeighborhoodsAndCanOpen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val polygons = context.assets.open("barrios_caba.geojson").bufferedReader().use { readNeighborhoodMap(it.readText()) }
        assertEquals(48, polygons.size)
        assertTrue(polygons.all { it.rings.isNotEmpty() && it.rings.first().size > 3 })
        compose.onNodeWithText("Zonas").performScrollTo().performClick()
        compose.onNodeWithText("Mapa de barrios · CABA").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("Mapa de polígonos de CABA").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Mapa de polígonos de CABA").performTouchInput { click(center) }
        compose.waitUntil(5000) {
            compose.onAllNodes(hasText("Excluir barrio") or hasText("Permitir barrio")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Cerrar mapa").performClick()
    }
    @Test fun widgetInflatesWithRealSummaryWithoutNeedingAHost() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val views = LoValeWidget.views(context)
        compose.runOnUiThread {
            val root = views.apply(context, FrameLayout(context))
            assertTrue(root.findViewById<TextView>(R.id.widget_summary).text.contains("ofertas analizadas"))
            assertEquals("Pausado", root.findViewById<TextView>(R.id.widget_status).text.toString())
        }
    }
}
