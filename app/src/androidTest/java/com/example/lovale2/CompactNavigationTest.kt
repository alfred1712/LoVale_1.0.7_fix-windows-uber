package com.example.lovale2

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.lovale2.ui.LoValeNavigation
import com.example.lovale2.ui.theme.LoValeTheme
import org.junit.Rule
import org.junit.Test

@org.junit.runner.RunWith(org.junit.runners.Parameterized::class)
class CompactNavigationTest(private val width: Int, private val height: Int, private val font: Float) {
    companion object {
        @JvmStatic @org.junit.runners.Parameterized.Parameters(name = "{0}x{1} font={2}")
        fun windows() = listOf(arrayOf<Any>(320,420,1.5f), arrayOf<Any>(280,360,2f), arrayOf<Any>(640,300,1.3f), arrayOf<Any>(840,600,2f))
    }
    @get:Rule val compose = createComposeRule()
    @Test fun largeTextKeepsTabsReachableWhileContentScrolls() {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(1f, font)) {
                LoValeTheme {
                    Box(Modifier.size(width.dp, height.dp)) {
                        LoValeNavigation(fuel = { Text("Precio visible") }, earnings = { Text("Neto visible") }) {
                            Column(Modifier.verticalScroll(rememberScrollState())) {
                                repeat(30) { Text("Filtro $it", Modifier.padding(12.dp)) }
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Filtro 29").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Combustible").assertIsDisplayed().performClick()
        compose.onNodeWithText("Precio visible").assertIsDisplayed()
        compose.onNodeWithContentDescription("Ganancias").assertIsDisplayed().performClick()
        compose.onNodeWithText("Neto visible").assertIsDisplayed()
        compose.onNodeWithContentDescription("Inicio").performClick()
        compose.onNodeWithText("Filtro 29").assertIsDisplayed()
    }
}
