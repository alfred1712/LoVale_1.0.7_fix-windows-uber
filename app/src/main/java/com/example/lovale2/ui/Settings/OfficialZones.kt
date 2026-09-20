package com.example.lovale2.ui.Settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Agregado reproducible: tipo=Robo, suma(cantidad) por barrio, CSV GCBA 2025. */
private val officialRobberyCounts = listOf("Palermo" to 3879, "Flores" to 3128,
    "Balvanera" to 2923, "Caballito" to 2381, "Recoleta" to 2184)

@Composable
fun OfficialZones(excluded: List<String>, add: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    TextButton(onClick = { expanded = !expanded }) { Text("Zonas para revisar · referencia 2025") }
    if (expanded) {
        Text("Último año disponible: 2025 · revisión 14/09/2026", style = MaterialTheme.typography.labelMedium)
        Text("Referencia histórica, no riesgo actual. GCBA actualiza estos datos anualmente; aún no encontramos una serie 2026 por barrio.", style = MaterialTheme.typography.bodySmall)
        Text("Análisis de LoVale con datos oficiales: cinco barrios con más robos registrados en 2025 (suma de cantidad). No es un ranking oficial de peligrosidad ni una estimación de riesgo personal.", style = MaterialTheme.typography.bodySmall)
        Text("Las cantidades no están ajustadas por población, superficie o circulación. No se extrapolan a Provincia de Buenos Aires.", style = MaterialTheme.typography.bodySmall)
        officialRobberyCounts.forEach { (name, count) ->
            val zone = "$name (CABA)"
            OutlinedButton(onClick = { add(zone) }, enabled = zone !in excluded, modifier = Modifier.fillMaxWidth()) {
                Text("$name · $count robos · ${if (zone in excluded) "Agregada" else "Agregar"}")
            }
        }
        TextButton(onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://data.buenosaires.gob.ar/dataset/delitos")))
        }) { Text("Ver fuente: GCBA · Delitos · CC BY") }
        Spacer(Modifier.height(8.dp))
    }
}
