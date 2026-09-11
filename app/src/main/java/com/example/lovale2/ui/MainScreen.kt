package com.example.lovale2.ui

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lovale2.data.settings.RideApp
import com.example.lovale2.services.ScreenCaptureHolder

@Composable
fun MainScreen(
    onManageZones: () -> Unit,
    isAccessibilityServiceEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    overlayPermissionGranted: Boolean,
    onOpenOverlaySettings: () -> Unit,
    viewModel: MainViewModel = viewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val selectedApp = settings.selectedApp

    var showFilterDialog by remember { mutableStateOf<String?>(null) }
    var tempFilterInput by remember { mutableStateOf("") }

    val primaryCyan = Color(0xFF00E5FF)
    val backgroundDark = Color(0xFF070D15)
    val cardBackground = Color(0xFF0E1726)
    val borderColor = Color(0xFF1E2D4A)

    val context = LocalContext.current

    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            // Guardamos el permiso concedido
            ScreenCaptureHolder.resultCode = result.resultCode
            ScreenCaptureHolder.resultData = result.data
            ScreenCaptureHolder.isCapturing = true

            Toast.makeText(context, "¡Captura OCR activada!", Toast.LENGTH_SHORT).show()

            // Encendemos automáticamente el servicio principal
            viewModel.toggleService(true)
        } else {
            Toast.makeText(context, "Permiso de captura denegado", Toast.LENGTH_SHORT).show()
            viewModel.toggleService(false)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(backgroundDark).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Panel de control", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
        Text(text = "Configura tus filtros y monitorea tus viajes", color = Color(0xFF8A99AD), fontSize = 12.sp, modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBackground),
            border = BorderStroke(1.dp, borderColor)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                Text("Aplicación a monitorear", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (selectedApp == null) "Elegí una plataforma antes de activar LoVale." else "LoVale solo procesará ${selectedApp.label} cuando esté en primer plano.",
                    color = Color(0xFF8A99AD), fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RideApp.entries.forEach { app ->
                        FilterChip(
                            selected = selectedApp == app,
                            onClick = {
                                if (settings.serviceActive) viewModel.toggleService(false)
                                viewModel.setSelectedApp(app)
                            },
                            label = { Text(app.label, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        if (!isAccessibilityServiceEnabled) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF3A1414)),
                border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("El servicio de accesibilidad no está activado", color = Color(0xFFFF8A80), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Sin esto, la app no puede leer las pantallas de Uber/Cabify/DiDi.", color = Color(0xFF8A99AD), fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onOpenAccessibilitySettings) {
                        Text("Habilitar ahora")
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (!overlayPermissionGranted) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF3A2A14)),
                border = BorderStroke(1.dp, Color(0xFFFFB74D).copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Falta permiso para la ventana flotante", color = Color(0xFFFFCC80), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("LoVale puede detectar viajes, pero no podrá mostrar el resultado encima de Uber/Cabify/DiDi hasta habilitarlo.", color = Color(0xFFB0BEC5), fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onOpenOverlaySettings) { Text("Habilitar ventana flotante") }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Botón para activar la captura OCR en pantalla ubicado justo encima del servicio principal
        Button(
            onClick = {
                val mediaProjectionManager = context.getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
                mediaProjectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = primaryCyan)
        ) {
            Text("Activar Captura OCR (Uber/DiDi)", color = Color.Black, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = if (settings.serviceActive) Color(0xFF0A2E1F) else cardBackground),
            border = BorderStroke(1.dp, if (settings.serviceActive) Color(0xFF00E676).copy(alpha = 0.5f) else borderColor)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = when {
                        settings.serviceActive -> "Estado: Activo — ${selectedApp?.label ?: "Sin plataforma"}"
                        selectedApp == null -> "Estado: Seleccioná una plataforma"
                        else -> "Estado: Pausado — ${selectedApp.label}"
                    },
                    color = if (settings.serviceActive) Color(0xFF00E676) else primaryCyan,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier.size(70.dp).clip(CircleShape)
                        .background(if (settings.serviceActive) Color(0xFF00E676) else primaryCyan),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        enabled = settings.serviceActive || selectedApp != null,
                        onClick = { viewModel.toggleService(!settings.serviceActive) }
                    ) {
                        Icon(
                            imageVector = if (settings.serviceActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Iniciar", tint = Color.Black, modifier = Modifier.size(35.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = cardBackground),
            border = BorderStroke(1.dp, borderColor)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                Text("Filtros inteligentes", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(10.dp))

                FilterRow(title = "Por hora", value = "$ ${settings.minRateByHour}", isActive = true) {
                    tempFilterInput = settings.minRateByHour
                    showFilterDialog = "hour"
                }
                Spacer(modifier = Modifier.height(6.dp))
                FilterRow(title = "Por km", value = "$ ${settings.minRateByKm}", isActive = true) {
                    tempFilterInput = settings.minRateByKm
                    showFilterDialog = "km"
                }
                Spacer(modifier = Modifier.height(6.dp))
                FilterRow(title = "Pickup máx.", value = "${settings.maxPickupDistance} km", isActive = true) {
                    tempFilterInput = settings.maxPickupDistance
                    showFilterDialog = "pickup"
                }
                Spacer(modifier = Modifier.height(6.dp))

                val summaryZones = if (settings.excludedZones.isEmpty()) "Sin zonas bloqueadas" else "${settings.excludedZones.size} zonas excluidas"
                FilterRow(title = "Zonas no deseadas", value = summaryZones, isActive = settings.excludedZones.isNotEmpty(), onClick = onManageZones)
            }
        }
    }

    if (showFilterDialog != null) {
        val titulo = when (showFilterDialog) {
            "hour" -> "Tarifa mínima por hora"
            "km" -> "Tarifa mínima por km"
            else -> "Distancia máx. de recogida (km)"
        }
        AlertDialog(
            onDismissRequest = { showFilterDialog = null },
            title = { Text(titulo) },
            text = {
                OutlinedTextField(
                    value = tempFilterInput,
                    onValueChange = { tempFilterInput = it.filter { c -> c.isDigit() } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            },
            confirmButton = {
                Button(onClick = {
                    when (showFilterDialog) {
                        "hour" -> viewModel.setMinRateByHour(tempFilterInput)
                        "km" -> viewModel.setMinRateByKm(tempFilterInput)
                        "pickup" -> viewModel.setMaxPickupDistance(tempFilterInput)
                    }
                    showFilterDialog = null
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { showFilterDialog = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
fun FilterRow(title: String, value: String, isActive: Boolean, onClick: () -> Unit) {
    val primaryCyan = Color(0xFF00E5FF)
    Surface(onClick = onClick, shape = RoundedCornerShape(10.dp), color = Color(0xFF07111D), border = BorderStroke(1.dp, Color(0xFF1E2D4A))) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = Color(0xFF8A99AD), fontSize = 11.sp)
                Text(text = value, color = if (isActive) primaryCyan else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
        }
    }
}

fun verificarYPedirPermisoNotificaciones(context: android.content.Context) {
    val listenerContenido = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    val packageName = context.packageName
    if (listenerContenido == null || !listenerContenido.contains(packageName)) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        context.startActivity(intent)
    }
}