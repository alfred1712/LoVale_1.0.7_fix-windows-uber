package com.example.lovale2.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.example.lovale2.R
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** Insets are consumed once by MainActivity, including the system keyboard. */
@Composable
fun LoValeNavigation(fuel: @Composable () -> Unit, earnings: @Composable () -> Unit, preferences: @Composable () -> Unit = {}, movida: @Composable () -> Unit = { MovidaScreen() }, home: @Composable () -> Unit) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val states = rememberSaveableStateHolder()
    val labels = listOf("Inicio", "Combustible", "Jornada", "Movida Ya", "Preferencias")
    val icons = listOf(Icons.Default.Home, Icons.Default.LocalGasStation, Icons.Default.Route, Icons.Default.Settings, Icons.Default.Settings)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Window size, rather than device type: reacts to split screen and rotation.
        val rail = maxWidth >= 600.dp && maxHeight >= 280.dp
        val compactLabels = maxWidth / LocalDensity.current.fontScale < 500.dp
        val content: @Composable () -> Unit = {
          Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
           Box(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
            states.SaveableStateProvider(selected) {
                when (selected) { 1 -> fuel(); 2 -> earnings(); 3 -> movida(); 4 -> preferences(); else -> home() }
            }
           }
          }
        }
        if (rail) Row(Modifier.fillMaxSize()) {
            NavigationRail(windowInsets = WindowInsets(0, 0, 0, 0)) {
                labels.forEachIndexed { index, label ->
                    NavigationRailItem(selected = selected == index, onClick = { selected = index },
                        icon = { if (index == 3) MovidaTabIcon(label) else Icon(icons[index], contentDescription = label, tint = MaterialTheme.colorScheme.primary) })
                }
            }
            Box(Modifier.weight(1f)) { content() }
        } else Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        NavigationBar(windowInsets = WindowInsets(0, 0, 0, 0)) {
            labels.forEachIndexed { index, label ->
                NavigationBarItem(selected = selected == index, onClick = { selected = index },
                    modifier = Modifier.semantics { contentDescription = label },
                    icon = { if (index == 3) MovidaTabIcon(null) else Icon(icons[index], contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    label = if (compactLabels) null else ({ Text(label) }))
            }
        }
        }
    }
}

/** Theme rendering of the original mark; green/white strokes become cyan, dark backdrop transparent. */
@Composable
private fun MovidaTabIcon(description: String?) {
    val tint = MaterialTheme.colorScheme.primary
    val filter = remember(tint) {
        ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
            0f, 0f, 0f, 0f, tint.red * 255f,
            0f, 0f, 0f, 0f, tint.green * 255f,
            0f, 0f, 0f, 0f, tint.blue * 255f,
            // Original background green is below 20; preserve antialiased edges of the mark.
            0f, 255f / 235f, 0f, 0f, -20f * 255f / 235f
        )))
    }
    Image(painterResource(R.drawable.movida_logo), contentDescription = description,
        modifier = Modifier.size(32.dp), colorFilter = filter)
}
