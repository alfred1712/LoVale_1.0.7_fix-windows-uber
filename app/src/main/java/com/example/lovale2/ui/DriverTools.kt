package com.example.lovale2.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.lovale2.data.settings.AppSettings
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun DriverTools(model: MainViewModel, showTest: Boolean = true, showHistory: Boolean = true) {
    var panel by remember { mutableStateOf<String?>(null) }
    var testText by remember { mutableStateOf("ARS6,084\nA 6 min (1.9 km)\nViaje: 19 min (8.2 km)") }
    var message by remember { mutableStateOf("") }
    var testDestination by remember { mutableStateOf("") }
    val records by model.offers.collectAsState()
    Spacer(Modifier.height(12.dp))
    listOfNotNull("Probar una oferta".takeIf { showTest }, "Historial de ofertas".takeIf { showHistory }).forEach { title ->
        OutlinedButton(onClick = { panel = title }, modifier = Modifier.fillMaxWidth()) { IconLabel(if (title == "Probar una oferta") Icons.Default.PlayCircle else Icons.Default.History, title) }
    }
    if (panel != null && panel != "Borrar historial") AlertDialog(onDismissRequest = { panel = null }, title = { Text(panel.orEmpty()) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                when (panel) {
                    "Probar una oferta" -> {
                        Text("Probá tus filtros sin activar el monitoreo.")
                        OutlinedTextField(testText, { testText = it }, label = { Text("Texto de oferta") }, minLines = 4)
                        OutlinedTextField(testDestination, { testDestination = it }, label = { Text("Zona de destino (opcional)") })
                        Button(onClick = { message = model.testOffer(testText, testDestination) }) { Text("Mostrar prueba") }
                        Text(message)
                    }
                    else -> {
                        Text("Revisá los viajes pendientes al terminar la jornada.")
                        if (records.isEmpty()) Text("Todavía no hay ofertas registradas.")
                        records.forEach { record ->
                            HorizontalDivider()
                            Text("${record.platform} · ${SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(java.util.Date(record.timestamp))}")
                            Text("$ %.0f · %.0f /km · %.0f /h".format(record.price, record.rateKm, record.rateHour))
                            if (record.netBasis) Text("Registro anterior: tarifas calculadas con costos de beta14")
                            record.costs?.let { Text("Gastos estimados: $ %.0f · Neto estimado: $ %.0f".format(it.total, it.net)) }
                            if (record.acceptedAt > 0 && record.completedAt == 0L) Text("Aceptado · pendiente de finalización")
                            if (record.completedAutomatically) Text("Confirmado automáticamente")
                            if (record.reviewRequired && record.completedAt == 0L) Text("Revisar finalización", color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { model.setCompleted(record.key, record.completedAt == 0L) }) {
                                Text(if (record.completedAt > 0) "Realizado ✓ · Deshacer" else "Marcar realizado")
                            }
                            Text("${record.level} · ${record.reason.replace('_', ' ')}")
                        }
                        if (records.isNotEmpty()) TextButton(onClick = { panel = "Borrar historial" }) { Text("Borrar historial") }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { panel = null }) { Text("Cerrar") } })
    if (panel == "Borrar historial") AlertDialog(onDismissRequest = { panel = "Historial de ofertas" },
        title = { Text("¿Borrar todas las ofertas guardadas?") }, confirmButton = { TextButton(onClick = { model.clearHistory(); panel = "Historial de ofertas" }) { Text("Borrar") } },
        dismissButton = { TextButton(onClick = { panel = "Historial de ofertas" }) { Text("Cancelar") } })
}
