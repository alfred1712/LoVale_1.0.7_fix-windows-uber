package com.example.lovale2.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.lovale2.diagnostics.ReadingReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable fun ReadingReportPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch {
            message = try {
                val raw = ReadingReport.read(context)
                if (raw == null) "No hay reporte guardado" else { withContext(Dispatchers.IO) { requireNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter().use { it.write(raw) } }; "Reporte exportado" }
            } catch (_: Exception) { "No se pudo exportar" }
        }
    }
    TextButton(onClick = { confirm = true }) { Text("Reportar error de lectura") }
    if (message.isNotBlank()) Text(message)
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Reporte local") }, text = {
        Text("Guarda la última lectura de los últimos 2 minutos. Puede contener direcciones. No se envía automáticamente. Podés borrarla; si supera 24 h, se elimina al abrir LoVale o consultar el reporte. Reportá estando detenido.")
    }, confirmButton = { TextButton(onClick = { scope.launch {
        message = try { if (ReadingReport.save(context)) "Lectura guardada en este teléfono" else "Sin lectura reciente. Volvé a probar una oferta." } catch (_: Exception) { "No se pudo guardar" }
        confirm = false
    } }) { Text("Guardar lectura") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } })
    MoreInformation {
        TextButton(onClick = { export.launch("LoVale-error-lectura.txt") }) { Text("Exportar reporte guardado") }
        TextButton(onClick = { scope.launch { ReadingReport.clear(context); message = "Reporte borrado" } }) { Text("Borrar reporte") }
    }
}
