package com.example.lovale2.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.*
import android.os.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
fun JourneyPanel(model: MainViewModel) {
    val state by model.journey.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var homeAddress by rememberSaveable(state.homeAddress) { mutableStateOf(state.homeAddress) }
    var configure by remember { mutableStateOf(false) }
    var pendingHome by remember { mutableStateOf(false) }
    var locating by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf("") }
    fun locateHome() {
        scope.launch {
            locating = true
            feedback = "Buscando ubicación de casa…"
            try {
                val fix = withTimeout(45000) { currentHomeLocation(context) }
                model.saveHome(fix.latitude, fix.longitude, homeAddress)
                feedback = "Casa guardada en este teléfono"
                configure = false
            } catch (e: TimeoutCancellationException) { feedback = "Sin ubicación precisa. Probá cerca de una ventana." }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { feedback = "Activá GPS y ubicación precisa para guardar casa." }
            finally { locating = false }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            if (pendingHome) locateHome() else model.prepareJourney()
        } else feedback = "Se necesita ubicación precisa. Podés habilitarla en Ajustes de Android."
    }
    fun request(home: Boolean) {
        pendingHome = home
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (home) locateHome() else model.prepareJourney()
        } else permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IconLabel(Icons.Default.Route, "Km de jornada · GPS")
            Text(String.format(Locale.forLanguageTag("es-AR"), "%.1f km", state.meters / 1000), style = MaterialTheme.typography.headlineLarge)
            Text(state.message, style = MaterialTheme.typography.bodyMedium)

            if (state.homeLatitude != null) Text("Casa · ${state.homeAddress.ifBlank { "Dirección sin completar" }}", style = MaterialTheme.typography.bodyMedium)
            if (state.partial) Text("GPS interrumpido · total parcial", color = MaterialTheme.colorScheme.error)
            if (state.running) {
                OutlinedButton(onClick = model::finishJourney, modifier = Modifier.fillMaxWidth()) { IconLabel(Icons.Default.StopCircle, "Finalizar jornada") }
            } else if (state.homeLatitude != null) {
                Button(onClick = { request(false) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.startedAt > 0 && state.endedAt == 0L) "Reanudar" else "Preparar")
                }
            }
            TextButton(onClick = { configure = !configure }, enabled = !locating) {
                IconLabel(Icons.Default.Home, if (state.homeLatitude == null) "Guardar casa" else "Mi casa")
            }
            if (configure) {
                OutlinedTextField(homeAddress, { homeAddress = it.filterNot(Char::isISOControl).take(160) },
                    label = { Text("Dirección de casa") }, singleLine = true, enabled = !locating, modifier = Modifier.fillMaxWidth())
                Text("Referencia local. El cierre usa el punto GPS guardado.", style = MaterialTheme.typography.bodySmall)
                if (state.homeLatitude != null) TextButton(onClick = { model.saveHomeAddress(homeAddress); feedback = "Dirección guardada" }) { Text("Guardar dirección") }
                if (state.homeLatitude != null) Text("Casa guardada · solo se reemplaza al tocar Actualizar casa aquí")
                Text("Usa ubicación incluso con otra app abierta o la pantalla apagada, desde la primera oferta hasta volver a casa. Guarda solo el total y tu casa en este teléfono.")
                Text("Estando en casa, guardá tu ubicación una vez. La jornada cierra tras 5 minutos detenido a menos de 100 m, después de haberte alejado 300 m.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { request(true) }, enabled = !locating, modifier = Modifier.fillMaxWidth()) { Text(if (locating) "Buscando GPS…" else if (state.homeLatitude == null) "Guardar casa aquí" else "Actualizar casa aquí") }
                if (state.enabled) TextButton(onClick = model::disableJourney) { Text("Desactivar contador") }
            }
            if (feedback.isNotBlank()) Text(feedback, style = MaterialTheme.typography.bodySmall)
            MoreInformation {
                if (state.startedAt > 0) Text("Desde ${java.text.SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(java.util.Date(state.startedAt))}")
                Text("Incluye recogidas, viajes, tramos vacíos y regreso, desde la primera oferta leída (aunque no la aceptes). Al activar LoVale se prepara automáticamente. Al pausar LoVale, la jornada se conserva 5 minutos. Si reactivás con otra plataforma, continúa el mismo total.")
                Text("Distancia estimada por GPS, independiente de los km de ofertas y del neto. Sin señal no se inventan kilómetros. Si Android interrumpe el servicio, reanudá desde aquí.")
            }
        }
    }
}

/** One foreground fix, cancelled when the screen leaves composition or after timeout. */
private suspend fun currentHomeLocation(context: Context): Location = withContext(Dispatchers.Main.immediate) {
    suspendCancellableCoroutine { continuation ->
        val manager = context.getSystemService(LocationManager::class.java)
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (!location.hasAccuracy() || location.accuracy > 40 ||
                    SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos !in 0L..30000000000L) return
                try { manager.removeUpdates(this) } catch (_: Exception) { }
                if (continuation.isActive) continuation.resume(location)
            }
            override fun onProviderDisabled(provider: String) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            @Deprecated("Required for old Android")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        continuation.invokeOnCancellation { try { manager.removeUpdates(listener) } catch (_: Exception) { } }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            continuation.resumeWithException(SecurityException("Ubicación precisa requerida"))
            return@suspendCancellableCoroutine
        }
        try { manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper()) }
        catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
    }
}
