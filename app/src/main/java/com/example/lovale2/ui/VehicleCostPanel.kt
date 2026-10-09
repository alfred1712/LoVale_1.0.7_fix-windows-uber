package com.example.lovale2.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.lovale2.domain.VehicleCosts
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun money(value: Double) = "$ " + NumberFormat.getNumberInstance(Locale.forLanguageTag("es-AR"))
    .apply { maximumFractionDigits = 0 }.format(value)

@Composable
fun FuelScreen(model: MainViewModel) {
    val settings by model.settings.collectAsState()
    val config = settings.vehicleCosts
    var showSetup by remember { mutableStateOf(false) }
    var priceDialog by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Combustible", style = MaterialTheme.typography.headlineSmall)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (config.fuelPrice > 0) "${money(config.fuelPrice)} /${if (config.fuel == "GNC") "m³" else "L"}" else "Sin precio", style = MaterialTheme.typography.headlineLarge)
                FuelPriceControls(model)
                OutlinedButton(onClick = { priceDialog = true }, enabled = config.fuelPrice > 0, modifier = Modifier.fillMaxWidth()) { IconLabel(Icons.Default.Edit, "Ajustar precio") }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(config.vehicle.ifBlank { "Configurá tu vehículo" }, style = MaterialTheme.typography.titleMedium)
                VehicleThumbnail(config.vehicle)
                Text(if (config.ready) "${config.consumptionPer100Km} ${if (config.fuel == "GNC") "m³" else "L"}/100 km · ${config.fuel}" else "Faltan datos del vehículo")
                OutlinedButton(onClick = { showSetup = true }, modifier = Modifier.fillMaxWidth()) { IconLabel(Icons.Default.DirectionsCar, "Mi vehículo") }
            }
        }
    }
    if (showSetup) VehicleSetup(config, { showSetup = false }) { model.saveVehicle(it); showSetup = false }
    if (priceDialog) {
        var price by remember { mutableFloatStateOf(config.fuelPrice.toFloat()) }
        val maximum = maxOf(1000f, config.fuelPrice.toFloat() * 2)
        AlertDialog(onDismissRequest = { priceDialog = false }, title = { Text("Precio de referencia · ${config.fuel}") },
            text = { Column {
                Text("${money(price.toDouble())} /${if (config.fuel == "GNC") "m³" else "L"}")
                Slider(price, { price = it }, valueRange = 1f..maximum)
                Text("El ajuste manual desactiva la actualización automática. No modifica ofertas anteriores.")
            } }, confirmButton = { TextButton(onClick = {
                model.saveVehicle(config.copy(fuelPrice = price.toDouble(), priceUpdatedAt = System.currentTimeMillis())); priceDialog = false
            }) { Text("Guardar referencia") } }, dismissButton = { TextButton(onClick = { priceDialog = false }) { Text("Cancelar") } })
    }
}

@Composable
fun JourneyScreen(model: MainViewModel) {
    val session by model.sessionSummary.collectAsState()
    val now by produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); delay(60000) }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Tu jornada", style = MaterialTheme.typography.headlineSmall)
        JourneyPanel(model)
        if (session.startedAt > 0) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconLabel(Icons.Default.Assessment, if (session.endedAt > 0) "Última jornada" else "Jornada en curso")
                    Text("${session.offers} ofertas leídas")
                    if (session.interrupted) Text("Sesión interrumpida", color = MaterialTheme.colorScheme.error)
                    MoreInformation {
                        Text("${session.incompleteReads} lecturas incompletas. Una oferta puede tener varias lecturas; este número no cuenta ofertas perdidas.")
                        Text("Ofertas analizadas desde que activaste LoVale. No representa viajes realizados ni ganancias cobradas.")
                    }
                }
            }
        }
        StatisticsPanel(model, now)
        DriverTools(model, showTest = false)
        DiagnosticPanel(model)
        MoreInformation {
            Text("Cómo se estima el desgaste", style = MaterialTheme.typography.titleMedium)
            Text("Combustible = km de recogida + viaje, multiplicados por el consumo del vehículo / 100 y el precio del litro o m³.")
            Text("Desgaste = costo de combustible × porcentaje configurado en Mi vehículo. El valor inicial es 20%: por $1.000 de combustible, se reservan $200 para desgaste.")
            Text("Es una reserva orientativa. LoVale no mide neumáticos, frenos, aceite, reparaciones, antigüedad ni depreciación del auto. No usa el GPS de jornada para este cálculo.")
            Text("Neto estimado = tarifa de la oferta − combustible − reserva de desgaste. Cada viaje conserva los valores usados al detectarlo.")
        }
    }
}

@Composable
internal fun MoreInformation(content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) { IconLabel(if (expanded) Icons.Default.ExpandLess else Icons.Default.Info, if (expanded) "Ocultar" else "Detalles") }
    if (expanded) content()
}

@Composable
internal fun VehicleSetup(original: VehicleCosts, dismiss: () -> Unit, save: (VehicleCosts) -> Unit) {
    var vehicle by remember { mutableStateOf(original.vehicle) }
    var fuel by remember { mutableStateOf(original.fuel) }
    var consumption by remember { mutableStateOf(original.consumptionPer100Km.toString()) }
    var price by remember { mutableStateOf(original.fuelPrice.takeIf { it > 0 }?.toString().orEmpty()) }
    var wear by remember { mutableFloatStateOf(original.wearPercent.toFloat()) }
    var enabled by remember { mutableStateOf(original.enabled || original.vehicle.isBlank()) }
    val context = LocalContext.current
    val draft = VehicleCosts(enabled, vehicle.trim(), fuel, consumption.replace(',', '.').toDoubleOrNull() ?: 0.0,
        price.replace(',', '.').toDoubleOrNull() ?: 0.0, wear.toDouble(),
        if (fuel != original.fuel || price.replace(',', '.').toDoubleOrNull() != original.fuelPrice || original.priceUpdatedAt == 0L) System.currentTimeMillis() else original.priceUpdatedAt)
    AlertDialog(onDismissRequest = dismiss, title = { IconLabel(Icons.Default.DirectionsCar, "Mi vehículo") }, text = {
        Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState())) {
            VehicleThumbnail(vehicle)
            TextButton(onClick = { vehicle = "Fiat Cronos 1.3 MT (MY26)"; fuel = "Nafta"; consumption = "8.0"; if (original.fuel != "Nafta") price = "" }) {
                Text("Usar referencia Fiat Cronos 1.3 manual")
            }
            OutlinedTextField(vehicle, { vehicle = it }, label = { Text("Vehículo / versión") }, singleLine = true)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Nafta", "GNC", "Diésel").forEach { option ->
                FilterChip(fuel == option, { if (fuel != option) { fuel = option; price = "" } }, label = { Text(option) })
            } }
            OutlinedTextField(consumption, { consumption = it }, label = { Text("Consumo ${if (fuel == "GNC") "m³" else "L"}/100 km") }, singleLine = true)
            Text("Cronos 1.3 manual MY26: referencia urbana oficial 8 L/100 km con nafta. Para otros vehículos o GNC, ingresá una referencia adecuada una sola vez.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://cronos.fiat.com.ar/noprecache/ficha-tecnica-cronos.pdf"))) }) { Text("Ver ficha Fiat") }
            OutlinedTextField(price, { price = it }, label = { Text("Precio de referencia $/${if (fuel == "GNC") "m³" else "L"}") }, singleLine = true)

            Text("Reserva para desgaste: ${wear.toInt()}% del costo de combustible")
            Slider(wear, { wear = it }, valueRange = 0f..100f, steps = 19)
            Text("20% es un supuesto inicial de LoVale, no un costo medido ni una referencia oficial. Podés modificarlo. No incluye otros gastos.", style = MaterialTheme.typography.bodySmall)
            Row { Checkbox(enabled, { enabled = it }); Text("Mostrar neto estimado") }
        }
    }, confirmButton = { TextButton(enabled = draft.ready || !enabled, onClick = { save(draft) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } })
}
