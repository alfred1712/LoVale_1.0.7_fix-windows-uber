package com.example.lovale2.ui.Settings

import android.location.Geocoder
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private val zonasSugeridas = listOf(
    "Retiro (CABA)", "Constitución (CABA)", "Once (CABA)",
    "La Matanza (PBA)", "Lanús (PBA)", "Avellaneda (PBA)", "Quilmes (PBA)"
)

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ForbiddenZonesScreen(
    forbiddenZones: List<String>,
    onAddZone: (String) -> Unit,
    onRemoveZone: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        if (query.length < 3) { suggestions = emptyList(); return@LaunchedEffect }
        isSearching = true
        suggestions = withContext(Dispatchers.IO) { buscarZonasCabaPba(context, query) }
        isSearching = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Zonas no deseadas") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Volver") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                "Agregá barrios o localidades de CABA y Provincia de Buenos Aires. Si el destino de un viaje coincide, la app lo marcará como no deseado.",
                style = MaterialTheme.typography.bodyMedium, color = Color.Gray
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Buscar barrio o localidad...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (isSearching) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (suggestions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Color(0xFF1E2D4A)), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(4.dp)) {
                        suggestions.forEach { sugerencia ->
                            Text(
                                text = sugerencia,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (!forbiddenZones.contains(sugerencia)) onAddZone(sugerencia)
                                    query = ""; suggestions = emptyList()
                                }.padding(10.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Sugeridas", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(6.dp))

            FlowRow(modifier = Modifier.fillMaxWidth()) {
                zonasSugeridas.forEach { zona ->
                    val yaAgregada = forbiddenZones.contains(zona)
                    AssistChip(
                        onClick = { if (!yaAgregada) onAddZone(zona) },
                        label = { Text(zona) },
                        modifier = Modifier.padding(end = 6.dp, bottom = 6.dp),
                        enabled = !yaAgregada
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Zonas bloqueadas (${forbiddenZones.size})", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(6.dp))

            if (forbiddenZones.isEmpty()) {
                Text("Todavía no agregaste ninguna zona.", color = Color.Gray)
            } else {
                LazyColumn {
                    items(forbiddenZones) { zona ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("• $zona")
                            IconButton(onClick = { onRemoveZone(zona) }) {
                                Icon(Icons.Default.Close, contentDescription = "Eliminar", tint = Color(0xFFFF5252))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buscarZonasCabaPba(context: android.content.Context, query: String): List<String> {
    return try {
        val geocoder = Geocoder(context, Locale.getDefault())
        @Suppress("DEPRECATION")
        val direcciones = geocoder.getFromLocationName("$query, Buenos Aires, Argentina", 8)

        direcciones.orEmpty()
            .filter { (it.adminArea ?: "").contains("Buenos Aires", ignoreCase = true) }
            .mapNotNull { address ->
                val localidad = address.subAdminArea ?: address.locality
                val barrio = address.subLocality ?: address.thoroughfare
                when {
                    barrio != null && localidad != null && barrio != localidad -> "$barrio ($localidad)"
                    localidad != null -> localidad
                    barrio != null -> barrio
                    else -> null
                }
            }
            .distinct()
    } catch (e: Exception) {
        emptyList()
    }
}
