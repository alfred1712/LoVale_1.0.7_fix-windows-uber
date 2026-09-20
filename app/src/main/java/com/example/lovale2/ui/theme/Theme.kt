package com.example.lovale2.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val LoValeColorScheme = darkColorScheme(
    primary = Copper, onPrimary = Ink,
    primaryContainer = CopperContainer, onPrimaryContainer = Ivory,
    secondary = Sky, onSecondary = Ink,
    secondaryContainer = SkyContainer, onSecondaryContainer = Ivory,
    tertiary = Ivory, onTertiary = Ink,
    tertiaryContainer = PanelRaised, onTertiaryContainer = Ivory,
    background = Ink, onBackground = Ivory,
    surface = Ink, onSurface = Ivory,
    surfaceVariant = PanelRaised, onSurfaceVariant = MutedText,
    surfaceContainerLowest = Ink, surfaceContainerLow = Panel,
    surfaceContainer = Panel, surfaceContainerHigh = PanelRaised,
    surfaceContainerHighest = PanelRaised,
    surfaceDim = Ink, surfaceBright = PanelRaised,
    outline = Border, outlineVariant = PanelRaised,
    error = AlertText, onError = Ink,
    errorContainer = AlertContainer, onErrorContainer = Ivory,
    inverseSurface = Ivory, inverseOnSurface = Ink, inversePrimary = CopperContainer,
    surfaceTint = Copper
)

@Composable
fun LoValeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LoValeColorScheme, typography = Typography, content = content)
}
