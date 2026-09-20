package com.example.lovale2.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.lovale2.data.settings.RideApp

/** Usa el icono real instalado, incluyendo iconos adaptativos, sin descargar imágenes. */
@Composable
fun RideAppIcon(app: RideApp) {
    val context = LocalContext.current
    val icon = remember(app, context) {
        try { context.packageManager.getApplicationIcon(app.packageName).toBitmap(96, 96).asImageBitmap() }
        catch (_: Exception) { null }
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        if (icon != null) Image(icon, contentDescription = app.label, modifier = Modifier.size(36.dp))
        else Text(app.label) // Una app no instalada sigue siendo identificable y seleccionable.
    }
}
