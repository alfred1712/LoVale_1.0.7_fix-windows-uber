package com.example.lovale2.ui.Settings

import com.example.lovale2.domain.ZoneAutocomplete
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.CancellationException


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
    val catalog by produceState<List<String>?>(initialValue = null) {
        value = try {
            withContext(Dispatchers.IO) {
                context.assets.open("zonas_caba_pba.txt").bufferedReader().use { reader ->
                    reader.readLines().map { it.trim().removePrefix("\uFEFF") }.filter { it.isNotBlank() }
                }
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { emptyList() }
    }
    val suggestions = remember(query, catalog, forbiddenZones) {
        ZoneAutocomplete.search(query, catalog.orEmpty(), forbiddenZones)
    }
    val isSearching = catalog == null
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Zonas no deseadas") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Volver") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(
                "Bloqueá barrios o localidades de CABA y Buenos Aires. Las direcciones de ofertas sin barrio en CABA se consultan por HTTPS en USIG; no se envían GPS ni capturas.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            NeighborhoodMapPanel(forbiddenZones, onAddZone, onRemoveZone)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Escribí al menos 3 letras...") },
                supportingText = { Text("Desde 3 letras · sin conexión") },
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
                Surface(shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
                    LazyColumn(modifier = Modifier.padding(4.dp).heightIn(max = 220.dp)) {
                        items(suggestions) { sugerencia ->
                            Text(
                                text = sugerencia,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (!forbiddenZones.contains(sugerencia)) onAddZone(sugerencia)
                                    query = ""
                                }.padding(10.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            if (query.trim().length >= 3 && !isSearching && suggestions.isEmpty()) {
                Text("Sin sugerencias disponibles. Podés agregar el nombre completo.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    val name = query.trim()
                    if (forbiddenZones.none { ZoneAutocomplete.key(it) == ZoneAutocomplete.key(name) }) onAddZone(name)
                    query = ""
                }) { Text("Agregar \"${query.trim()}\"") }
            }
            OfficialZones(forbiddenZones, onAddZone)
            Spacer(modifier = Modifier.height(6.dp))

            Text("Zonas bloqueadas (${forbiddenZones.size})", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(6.dp))

            if (forbiddenZones.isEmpty()) {
                Text("Todavía no agregaste ninguna zona.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    forbiddenZones.forEach { zona ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("•", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 10.dp))
                            Text(zona, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onRemoveZone(zona) }) {
                                Icon(Icons.Default.Close, contentDescription = "Eliminar $zona", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}
