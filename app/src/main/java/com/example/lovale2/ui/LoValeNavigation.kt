package com.example.lovale2.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** Insets are consumed once by MainActivity, including the system keyboard. */
@Composable
fun LoValeNavigation(fuel: @Composable () -> Unit, earnings: @Composable () -> Unit, home: @Composable () -> Unit) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val states = rememberSaveableStateHolder()
    val labels = listOf("Inicio", "Combustible", "Ganancias")
    val icons = listOf(Icons.Default.Home, Icons.Default.LocalGasStation, Icons.Default.AccountBalanceWallet)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Window size, rather than device type: reacts to split screen and rotation.
        val rail = maxWidth >= 600.dp && maxHeight >= 280.dp
        val compactLabels = maxWidth / LocalDensity.current.fontScale < 300.dp
        val content: @Composable () -> Unit = {
          Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
           Box(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
            states.SaveableStateProvider(selected) {
                when (selected) { 1 -> fuel(); 2 -> earnings(); else -> home() }
            }
           }
          }
        }
        if (rail) Row(Modifier.fillMaxSize()) {
            NavigationRail(windowInsets = WindowInsets(0, 0, 0, 0)) {
                labels.forEachIndexed { index, label ->
                    NavigationRailItem(selected = selected == index, onClick = { selected = index },
                        icon = { Icon(icons[index], contentDescription = label) })
                }
            }
            Box(Modifier.weight(1f)) { content() }
        } else Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        NavigationBar(windowInsets = WindowInsets(0, 0, 0, 0)) {
            labels.forEachIndexed { index, label ->
                NavigationBarItem(selected = selected == index, onClick = { selected = index },
                    modifier = Modifier.semantics { contentDescription = label },
                    icon = { Icon(icons[index], contentDescription = null) },
                    label = if (compactLabels) null else ({ Text(label) }))
            }
        }
        }
    }
}
