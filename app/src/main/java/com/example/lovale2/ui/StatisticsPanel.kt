package com.example.lovale2.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.lovale2.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

@Composable
fun StatisticsPanel(model: MainViewModel, now: Long) {
    val records by model.offers.collectAsState()
    val today = periodStart(now)
    val week = periodStart(now, true)
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text("Estadísticas y exportación") }
    if (!open) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var period by remember { mutableStateOf("Hoy") }
    var hour by remember { mutableStateOf("Todas las horas") }
    var platform by remember { mutableStateOf("Todas") }
    var neighborhood by remember { mutableStateOf("Todos los barrios") }
    var result by remember { mutableStateOf("") }
    val start = when (period) { "Semana" -> week; "90 días" -> now - 90L * 86400000; else -> today }
    val filtered = records.filter { r -> r.timestamp in start..now && (neighborhood == "Todos los barrios" || r.neighborhood == neighborhood) && (platform == "Todas" || r.platform == platform) &&
        (hour == "Todas las horas" || Calendar.getInstance().apply { timeInMillis = r.timestamp }.get(Calendar.HOUR_OF_DAY) == hour.toInt()) }
    val stat = offerStatistics(filtered)
    var exportRows by remember { mutableStateOf(filtered) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            result = try { withContext(Dispatchers.IO) { requireNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter(Charsets.UTF_8).use { it.write(offersCsv(exportRows)) } }; "CSV guardado" }
            catch (_: Exception) { "No se pudo guardar el CSV" }
        }
    }
    AlertDialog(onDismissRequest = { open = false }, title = { Text("Estadísticas") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ChoiceDropdown("Período", period, listOf("Hoy", "Semana", "90 días")) { period = it }
            ChoiceDropdown("Plataforma", platform, listOf("Todas", "Uber", "Cabify", "DiDi")) { platform = it }
            ChoiceDropdown("Hora local", hour, listOf("Todas las horas") + (0..23).map(Int::toString)) { hour = it }
            ChoiceDropdown("Barrio de destino", neighborhood, listOf("Todos los barrios") + records.mapNotNull { it.neighborhood }.distinct().sorted()) { neighborhood = it }
            Text("${stat.offers} ofertas", style = MaterialTheme.typography.titleMedium)
            Text("Filtro: ${stat.approved} aptas · ${stat.near} cerca · ${stat.rejected} no aptas")
            Text("Promedio ponderado  ${money(stat.rateKm)}/km · ${money(stat.rateHour)}/h")
            Text("Por plataforma", style = MaterialTheme.typography.titleMedium)
            filtered.groupBy { it.platform }.forEach { (name, rows) ->
                val s = offerStatistics(rows)
                Text("$name · ${s.offers} ofertas\n${money(s.rateKm)}/km · ${money(s.rateHour)}/h")
            }
            Text("Turnos", style = MaterialTheme.typography.titleMedium)
            filtered.filter { it.sessionId > 0 }.groupBy { it.sessionId }.entries.sortedByDescending { it.key }.take(10).forEach { (id, rows) ->
                val s = offerStatistics(rows)
                Text("${java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(id))} · ${s.offers} ofertas · ${s.approved} aptas · ${s.rejected} no aptas")
            }
            MoreInformation {
                Text("Las clasificaciones pertenecen al filtro, no prueban que hayas rechazado una oferta. Promedios = suma de tarifas / suma de km o tiempo, incluyendo recogida. Compara solo esta muestra y horario, no toda la zona.")
                Text("Retención: 90 días, hasta 10.000 registros. Los anteriores a 1.0.9 pueden no tener turno o mínimos históricos. CSV abre en Excel; no constituye comprobante fiscal.")
            }
            OutlinedButton(onClick = { exportRows = filtered.toList(); export.launch("LoVale-ofertas.csv") }) { Text("Exportar CSV / Excel") }
            if (result.isNotBlank()) Text(result)
        } }, confirmButton = { TextButton(onClick = { open = false }) { Text("Cerrar") } })
}
