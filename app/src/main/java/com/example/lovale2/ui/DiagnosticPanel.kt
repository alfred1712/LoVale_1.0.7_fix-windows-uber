package com.example.lovale2.ui

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.lovale2.diagnostics.DiagnosticRecorder
import kotlinx.coroutines.launch

@Composable
fun DiagnosticPanel(model: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by model.settings.collectAsState()
    val state by DiagnosticRecorder.state.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    var visual by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { DiagnosticRecorder.initialize(context) }
    val exportHealth = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try { com.example.lovale2.diagnostics.ReadingHealthLog.export(context, uri); result = "Métricas exportadas" }
            catch (_: Exception) { result = "No hay métricas disponibles o no se pudo exportar" }
            finally { busy = false }
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try { DiagnosticRecorder.export(context, uri); result = "Registro exportado" }
            catch (_: Exception) { result = "No se pudo exportar. Intentá nuevamente." }
            finally { busy = false }
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IconLabel(Icons.Default.BugReport, "Diagnóstico de prueba")
            Text("Lectura: métricas automáticas de 7 días. Sin imágenes ni direcciones.", style = MaterialTheme.typography.bodySmall)
            TextButton(enabled = !busy, onClick = { exportHealth.launch("LoVale-lectura-7dias.zip") }) { Text("Exportar métricas de lectura") }
            Text(state.message, style = MaterialTheme.typography.bodyMedium)
            if (state.active) {
                Button(onClick = { DiagnosticRecorder.stop() }, modifier = Modifier.fillMaxWidth()) { Text("Finalizar registro") }
            } else {
                OutlinedButton(onClick = { confirm = true }, enabled = !busy && settings.selectedApp != null,
                    modifier = Modifier.fillMaxWidth()) { Text("Preparar registro") }
            }
            if (state.available && !state.active) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = !busy, onClick = { export.launch("LoVale-diagnostico.zip") }) { Text("Exportar") }
                    TextButton(enabled = !busy, onClick = {
                        scope.launch {
                            busy = true
                            try { DiagnosticRecorder.delete(); result = "Registro borrado" }
                            catch (_: Exception) { result = "No se pudo borrar el registro" }
                            finally { busy = false }
                        }
                    }) { Text("Borrar") }
                }
            }
            if (result.isNotBlank()) Text(result)
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Registrar una prueba") }, text = {
        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
            Text("Guardá el registro al terminar, con el vehículo detenido. No necesitás contar ofertas ni tocar LoVale mientras conducís.")
            Text("Registro local de lectura, cálculo y consumo. Máximo 1 hora / 50 MB. Reemplaza la prueba anterior. Se elimina al volver a acceder después de 24 h.")
            if (Build.VERSION.SDK_INT >= 34) {
                Row { Checkbox(visual, { visual = it }); Text("Incluir imágenes y texto de ${settings.selectedApp?.label}") }
                if (visual) Text("Captura su ventana visible cada 5 s: puede incluir mapas y direcciones. Consume batería y puede omitir ofertas breves. Solo se comparte al exportarlo.")
            }
            Text("Después, activá el monitoreo desde Inicio. Se detiene al pausarlo o cambiar de app.")
        }
    }, confirmButton = { TextButton(onClick = {
        settings.selectedApp?.let { DiagnosticRecorder.start(context, it.packageName, visual && Build.VERSION.SDK_INT >= 34) }
        result = ""; confirm = false
    }) { Text("Preparar") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } })
}
