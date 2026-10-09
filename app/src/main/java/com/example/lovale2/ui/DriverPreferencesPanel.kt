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

@Composable
fun DriverPreferencesPanel(model: MainViewModel) {
    val options by model.options.collectAsState()
    val settings by model.settings.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(options) }
    var feedback by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<ConfigurationBackup?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            feedback = try { val json = encodeConfiguration(settings, options); withContext(Dispatchers.IO) {
                requireNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter().use { it.write(json) }
            }; "Respaldo guardado" } catch (_: Exception) { "No se pudo guardar" }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try { pending = withContext(Dispatchers.IO) {
                val text = requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                    val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(4096)
                    while (true) { val n = input.read(buffer); if (n < 0) break; require(out.size() + n <= 128000); out.write(buffer, 0, n) }
                    out.toString("UTF-8")
                }; decodeConfiguration(text)
            } } catch (_: Exception) { feedback = "Respaldo inválido o demasiado grande. No se cambió nada." }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Preferencias", style = MaterialTheme.typography.headlineSmall)
            if (options.quietAt()) Text("No molestar · monitoreo pausado", color = MaterialTheme.colorScheme.primary)
            Text("Alertas", style = MaterialTheme.typography.titleMedium)
            OptionSwitch("Sonido", draft.sound) { draft = draft.copy(sound = it) }
            OptionSwitch("Vibración", draft.vibration) { draft = draft.copy(vibration = it) }
            Text("Respeta el silencio y No molestar de Android.", style = MaterialTheme.typography.bodySmall)
            OptionSwitch("No molestar programado", draft.quietEnabled) { draft = draft.copy(quietEnabled = it) }
            if (draft.quietEnabled) {
                ClockChoice("Desde", draft.quietStart) { draft = draft.copy(quietStart = it) }
                ClockChoice("Hasta", draft.quietEnd) { draft = draft.copy(quietEnd = it) }
                Text("Pausa LoVale; no lo reactiva automáticamente. El contador aplica su pausa de 5 minutos.", style = MaterialTheme.typography.bodySmall)
            }
            OptionSwitch("Mínimos por horario", draft.timedRates) { draft = draft.copy(timedRates = it) }
            if (draft.timedRates) {
                RuleEditor("Hora pico", draft.peak) { draft = draft.copy(peak = it) }
                RuleEditor("Madrugada", draft.night) { draft = draft.copy(night = it) }
                Text("Hora local del teléfono. Fuera de estas franjas usa Tus mínimos; si se superponen usa los más exigentes.", style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider()
            Text("Respaldo de configuración", style = MaterialTheme.typography.titleMedium)
            Text("Tarifas, zonas, plataforma y preferencias guardadas. No incluye casa, GPS, historial ni diagnósticos.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { export.launch("LoVale-configuracion.json") }) { Text("Exportar configuración") }
            TextButton(onClick = { import.launch(arrayOf("application/json", "text/plain")) }) { Text("Restaurar configuración") }
            if (feedback.isNotBlank()) Text(feedback)
        Button(modifier = Modifier.fillMaxWidth(), onClick = {
        try {
            val next = draft.validate()
            model.saveOptions(next); feedback = "Preferencias guardadas"
        } catch (_: Exception) { feedback = "Revisá montos y horarios: inicio y fin deben ser distintos." }
    }) { Text("Guardar") }
    }
    pending?.let { backup -> AlertDialog(onDismissRequest = { pending = null }, title = { Text("¿Restaurar configuración?") },
        text = { Text("Se reemplazarán tarifas, ${backup.zones.size} zonas y preferencias. El monitoreo quedará pausado.") },
        confirmButton = { TextButton(onClick = { scope.launch {
            try { model.restoreConfiguration(backup); pending = null; draft = backup.options; feedback = "Configuración restaurada" }
            catch (_: Exception) { pending = null; feedback = "No se pudo restaurar" }
        } }) { Text("Restaurar") } }, dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancelar") } }) }
}

@Composable private fun OptionSwitch(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(checked, change)
    }
}
@Composable private fun ClockChoice(label: String, value: Int, save: (Int) -> Unit) {
    // Half-hour choices avoid invalid times and keyboard entry while configuring schedules.
    ChoiceDropdown(label, clockLabel(value), (0..47).map { clockLabel(it * 30) }) { parseClock(it)?.let(save) }
}
@Composable private fun RuleEditor(label: String, rule: TimeRule, save: (TimeRule) -> Unit) {
    Text(label, style = MaterialTheme.typography.titleSmall)
    ClockChoice("Inicio", rule.start) { save(rule.copy(start = it)) }
    ClockChoice("Fin", rule.end) { save(rule.copy(end = it)) }
    var km by remember { mutableStateOf(rule.minKm.toString()) }
    var hour by remember { mutableStateOf(rule.minHour.toString()) }
    OutlinedTextField(km, { km = it.take(12); save(rule.copy(minKm = it.replace(',', '.').toDoubleOrNull() ?: Double.NaN)) }, label = { Text("Mínimo $/km") }, singleLine = true)
    OutlinedTextField(hour, { hour = it.take(12); save(rule.copy(minHour = it.replace(',', '.').toDoubleOrNull() ?: Double.NaN)) }, label = { Text("Mínimo $/h") }, singleLine = true)
}
