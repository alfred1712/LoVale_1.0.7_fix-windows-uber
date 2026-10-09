package com.example.lovale2.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.lovale2.data.network.VehiclePhoto
import com.example.lovale2.data.network.VehiclePhotos

@Composable
fun VehicleThumbnail(vehicle: String) {
    val context = LocalContext.current
    val model = VehiclePhotos.modelFor(vehicle)
    // Keying the composition prevents showing the previous car while the next one loads.
    key(model) {
        var loading by remember { mutableStateOf(model != null) }
        val photo by produceState<VehiclePhoto?>(null, model) {
            value = VehiclePhotos.load(context.applicationContext, vehicle)
            loading = false
        }
        val current = photo
        if (current != null) {
            Image(current.bitmap.asImageBitmap(), contentDescription = "Foto de ${current.model}",
                modifier = Modifier.fillMaxWidth().height(112.dp), contentScale = ContentScale.Fit)
            Text("Foto ilustrativa · puede variar año/versión", style = MaterialTheme.typography.labelSmall)
            TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(current.source))) } }) {
                Text(current.credit, style = MaterialTheme.typography.labelSmall)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.DirectionsCar, contentDescription = null, modifier = Modifier.size(40.dp))
                Text(if (loading) "Cargando foto…" else "Foto no disponible", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
