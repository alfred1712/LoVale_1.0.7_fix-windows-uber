package com.example.lovale2.ui

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Browser-owned session: no credential handling, JS bridge or LoVale data transmission. */
@Composable
fun MovidaScreen() {
    val context = LocalContext.current
    var unavailable by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary)
        Text("Movida Ya", style = MaterialTheme.typography.headlineMedium)
        Text("Tu webapp, a un toque.", style = MaterialTheme.typography.bodyLarge)
        Button(onClick = {
            unavailable = false
            try {
                // No prefetch: the website is contacted only on an explicit tap.
                CustomTabsIntent.Builder().setShowTitle(true).build()
                    .launchUrl(context, Uri.parse("https://app.movidaya.com.ar/app"))
            } catch (_: ActivityNotFoundException) {
                unavailable = true
            } catch (_: SecurityException) {
                unavailable = true
            }
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text("Abrir Movida Ya")
        }
        Text("Cerrá la ventana web para volver a LoVale.", style = MaterialTheme.typography.bodyMedium)
        if (unavailable) Text("No se pudo abrir. Verificá que tengas un navegador habilitado e intentá de nuevo.",
            color = MaterialTheme.colorScheme.error)
    }
}
