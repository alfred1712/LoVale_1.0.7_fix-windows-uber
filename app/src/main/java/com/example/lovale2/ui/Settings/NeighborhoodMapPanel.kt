package com.example.lovale2.ui.Settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.lovale2.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun NeighborhoodMapPanel(excluded: List<String>, add: (String) -> Unit, remove: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text("Mapa de barrios · CABA") }
    if (!open) return
    val context = LocalContext.current
    val polygons by produceState<List<NeighborhoodPolygon>>(emptyList()) {
        value = withContext(Dispatchers.IO) { context.assets.open("barrios_caba.geojson").bufferedReader().use { readNeighborhoodMap(it.readText()) } }
    }
    var selected by remember { mutableStateOf<NeighborhoodPolygon?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val points = remember(polygons) { polygons.flatMap { it.rings.flatten() } }
    val minX = points.minOfOrNull { it.longitude } ?: 0.0
    val maxX = points.maxOfOrNull { it.longitude } ?: 1.0
    val minY = points.minOfOrNull { it.latitude } ?: 0.0
    val maxY = points.maxOfOrNull { it.latitude } ?: 1.0
    fun blocked(name: String) = excluded.firstOrNull { ZoneAutocomplete.key(it.substringBefore('(')) == ZoneAutocomplete.key(name) }
    val ink = MaterialTheme.colorScheme.surfaceVariant
    val danger = MaterialTheme.colorScheme.error.copy(alpha = .65f)
    val highlight = MaterialTheme.colorScheme.primary
    val border = MaterialTheme.colorScheme.onSurface
    AlertDialog(onDismissRequest = { open = false }, title = { Text("Elegí un barrio") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tocá para seleccionar · pellizcá para ampliar", style = MaterialTheme.typography.bodySmall)
            Canvas(Modifier.fillMaxWidth().height(280.dp).clipToBounds().semantics {
                contentDescription = if (polygons.isEmpty()) "Cargando mapa" else "Mapa de polígonos de CABA"
            }
                .pointerInput(polygons, zoom, pan) { detectTapGestures { tap ->
                    val x = (tap.x - size.width / 2 - pan.x) / zoom + size.width / 2
                    val y = (tap.y - size.height / 2 - pan.y) / zoom + size.height / 2
                    val point = MapPoint(minX + x / size.width * (maxX - minX), maxY - y / size.height * (maxY - minY))
                    selected = polygons.firstOrNull { it.contains(point) }
                } }.pointerInput(Unit) { detectTransformGestures { _, shift, scale, _ ->
                    zoom = (zoom * scale).coerceIn(1f, 5f)
                    pan = if (zoom == 1f) Offset.Zero else Offset((pan.x + shift.x).coerceIn(-size.width * 2f, size.width * 2f), (pan.y + shift.y).coerceIn(-size.height * 2f, size.height * 2f))
                } }) {
                polygons.forEach { polygon ->
                    val path = Path().apply {
                        fillType = PathFillType.EvenOdd
                        polygon.rings.forEach { ring ->
                            ring.forEachIndexed { i, p ->
                                val x = (((p.longitude - minX) / (maxX - minX) * size.width - size.width / 2) * zoom + size.width / 2 + pan.x).toFloat()
                                val y = (((maxY - p.latitude) / (maxY - minY) * size.height - size.height / 2) * zoom + size.height / 2 + pan.y).toFloat()
                                if (i == 0) moveTo(x, y) else lineTo(x, y)
                            }; close()
                        }
                    }
                    drawPath(path, if (selected == polygon) highlight else if (blocked(polygon.name) != null) danger else ink)
                    drawPath(path, border, style = Stroke(1f))
                }
            }
            selected?.let { polygon ->
                val existing = blocked(polygon.name)
                Text(polygon.name, style = MaterialTheme.typography.titleMedium)
                Button(onClick = { if (existing == null) add("${polygon.name} (CABA)") else remove(existing) }) {
                    Text(if (existing == null) "Excluir barrio" else "Permitir barrio")
                }
            }
            TextButton(onClick = { zoom = 1f; pan = Offset.Zero }) { Text("Ver todo") }
            Text("Polígonos GCBA · CC BY 2.5 AR · julio 2026. Rojo = excluido. Fuera de CABA usá el listado.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = { open = false }) { Text("Cerrar mapa") } })
}
