package com.example.lovale2.ui

import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.lovale2.services.DriverLocation
import kotlinx.coroutines.*
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
import com.example.lovale2.domain.AutoFuelSettings
import com.example.lovale2.domain.FuelCatalog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FuelPriceControls(model: MainViewModel) {
    val settings by model.settings.collectAsState()
    val auto = settings.autoFuel
    var show by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var locating by remember { mutableStateOf(false) }
    var locationStatus by remember { mutableStateOf("") }
    suspend fun locate(refresh: Boolean) {
        locating = true
        locationStatus = "Buscando ubicación…"
        try {
            DriverLocation.capture(context)
            locationStatus = ""
            if (refresh) model.refreshFuel()
        } catch (e: TimeoutCancellationException) { locationStatus = "Sin señal GPS · reintentá" }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { locationStatus = "Activá el GPS" }
        finally { locating = false }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) scope.launch { locate(true) }
        else locationStatus = "Se necesita ubicación precisa; podés habilitarla en Ajustes de Android."
    }
    LaunchedEffect(auto.enabled, auto.revision) {
        if (auto.enabled && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locate(auto.stationDistanceKm == null || System.currentTimeMillis() - auto.checkedAt >= 12 * 3600000L || auto.status.startsWith("Ubicación"))
        }
    }
    Text("${auto.brand} · ${auto.selectedProduct?.label ?: settings.vehicleCosts.fuel}", style = MaterialTheme.typography.titleMedium)
    Text(if (auto.enabled) "Auto · cada 12 h" else "Precio manual", style = MaterialTheme.typography.bodyMedium)
    if (settings.vehicleCosts.priceUpdatedAt > 0 && System.currentTimeMillis() - settings.vehicleCosts.priceUpdatedAt > 14L * 86400000) {
        Text("Precio de hace más de 14 días", color = MaterialTheme.colorScheme.error)
    }
    OutlinedButton(onClick = { show = true }, modifier = Modifier.fillMaxWidth()) { IconLabel(Icons.Default.LocalGasStation, "Elegir combustible") }
    if (auto.enabled) {

        Button(onClick = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) scope.launch { locate(true) }
            else permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
        }, enabled = !locating, modifier = Modifier.fillMaxWidth()) { IconLabel(Icons.Default.Refresh, if (locating) "Buscando GPS…" else "Actualizar") }
    }
    if (locationStatus.isNotBlank()) Text(locationStatus, style = MaterialTheme.typography.bodySmall)
    if (auto.stationDistanceKm != null) {

        IconLabel(Icons.Default.LocationOn, "Estación a %.1f km · última consulta".format(auto.stationDistanceKm))
    }
    if (auto.status.isNotBlank() && auto.status != "Consulta correcta") Text(auto.status, style = MaterialTheme.typography.bodySmall)
    MoreInformation {
        if (auto.station.isNotBlank()) Text(auto.station)
        Text("Fuente: Secretaría de Energía. Cercanía en línea recta, no por ruta. La ubicación se usa localmente.")
        if (auto.publishedAt > 0) Text("Precio publicado: ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(auto.publishedAt))}")
        if (auto.checkedAt > 0) Text("Último intento: ${SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(auto.checkedAt))}")
        if (auto.locatedAt > 0) Text("Ubicación de la consulta: ${SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(auto.locatedAt))}")
        Text("Se consulta cada 12 h con ubicación de menos de 2 minutos y precisión de hasta 100 m. Sin ella se conserva el precio anterior; abrí esta pestaña y actualizá. Se usa el mayor precio vigente entre los horarios de la estación. No se guarda ni envía tu ubicación.")
        if ((auto.sourceUrl.startsWith("https://combustibles.ar/") || auto.sourceUrl.startsWith("https://datos.energia.gob.ar/"))) TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(auto.sourceUrl))) }) { Text("Ver fuente") }
    }
    if (show) FuelSelectionDialog(auto, { show = false }) { model.saveAutoFuel(it); show = false }
}

@Composable
internal fun FuelSelectionDialog(original: AutoFuelSettings, dismiss: () -> Unit, save: (AutoFuelSettings) -> Unit) {
    var brand by remember { mutableStateOf(original.brand) }
    var product by remember { mutableStateOf(original.product) }
    var automatic by remember { mutableStateOf(original.enabled) }
    val products = FuelCatalog.products(brand)
    val draft = original.copy(enabled = automatic, brand = brand, product = product)
    AlertDialog(onDismissRequest = dismiss, title = { Text("Precio de combustible") }, text = {
        Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState())) {
            ChoiceDropdown("Empresa", brand, FuelCatalog.brands) { brand = it; product = "nafta-super" }
            ChoiceDropdown("Combustible", products.firstOrNull { it.slug == product }?.label.orEmpty(), products.map { it.label }) {
                product = products.first { p -> p.label == it }.slug
            }
            Row { Checkbox(automatic, { automatic = it }); Text("Actualizar automáticamente cada 12 horas") }
            Text("Elegimos la estación más cercana a tu ubicación actual, dentro de 50 km. Hace falta permiso de ubicación precisa.", style = MaterialTheme.typography.bodySmall)
            Text("Si no hay ubicación o precio vigente, se conserva la referencia anterior. GNC: $/m³; nafta y diésel: $/L.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(enabled = draft.valid, onClick = { save(draft) }) { Text("Guardar y consultar") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } })
}

@Composable
internal fun ChoiceDropdown(label: String, selected: String, choices: List<String>, change: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("$label: $selected ▾") }
        DropdownMenu(expanded, { expanded = false }, modifier = Modifier.heightIn(max = 260.dp)) {
            choices.forEach { choice -> DropdownMenuItem(text = { Text(choice) }, onClick = { change(choice); expanded = false }) }
        }
    }
}
