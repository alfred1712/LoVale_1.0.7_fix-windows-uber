package com.example.lovale2.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lovale2.data.settings.RideApp

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
    val health by com.example.lovale2.services.MonitoringHealth.state.collectAsState()
    val diagnostic by com.example.lovale2.diagnostics.DiagnosticRecorder.state.collectAsState()
    val selectedApp = settings.selectedApp
    val journey by viewModel.journey.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val locationPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.toggleService(true) }
    fun activate() {
        if (journey.homeLatitude != null && androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            locationPermission.launch(arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION,
                android.Manifest.permission.ACCESS_FINE_LOCATION))
        } else viewModel.toggleService(true)
    }

    var showFilterDialog by remember { mutableStateOf<String?>(null) }
    var tempFilterInput by remember { mutableStateOf("") }

    val primaryAccent = MaterialTheme.colorScheme.primary
    val backgroundDark = MaterialTheme.colorScheme.background
    val cardBackground = MaterialTheme.colorScheme.surfaceContainer
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier = Modifier.fillMaxSize().background(backgroundDark).verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(com.example.lovale2.R.drawable.lovale_mark),
                contentDescription = "LoVale", modifier = Modifier.size(56.dp))
            Spacer(Modifier.width(8.dp))
            Text(text = "Panel de control", color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }


        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBackground),
            border = BorderStroke(1.dp, borderColor)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                Text("Tu app", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RideApp.entries.forEach { app ->
                        FilterChip(
                            selected = selectedApp == app,
                            onClick = {
                                if (settings.serviceActive) viewModel.toggleService(false)
                                viewModel.setSelectedApp(app)
                            },
                            label = { RideAppIcon(app) },
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
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Falta permiso de lectura", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onOpenAccessibilitySettings) { IconLabel(Icons.Default.Accessibility, "Habilitar lectura") }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (!overlayPermissionGranted) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Falta permiso de alertas", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onOpenOverlaySettings) { IconLabel(Icons.Default.Notifications, "Habilitar alertas") }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = if (settings.serviceActive) MaterialTheme.colorScheme.primaryContainer else cardBackground),
            border = BorderStroke(1.dp, if (settings.serviceActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else borderColor)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = when {
                        settings.serviceActive -> "Activo · ${selectedApp?.label ?: "Sin plataforma"}"
                        selectedApp == null -> "Elegí tu app"
                        else -> "Pausado · ${selectedApp.label}"
                    },
                    color = if (settings.serviceActive) MaterialTheme.colorScheme.onPrimaryContainer else primaryAccent,
                    fontSize = 14.sp, fontWeight = FontWeight.Bold
                )
                if (settings.serviceActive) {
                    Text(health.label, color = if (health.warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text(if (journey.running) "Km automáticos" else journey.message,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier.size(70.dp).clip(CircleShape)
                        .background(primaryAccent),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        enabled = settings.serviceActive || selectedApp != null,
                        onClick = { if (settings.serviceActive) viewModel.toggleService(false) else activate() }
                    ) {
                        Icon(
                            imageVector = if (settings.serviceActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (settings.serviceActive) "Pausar" else "Iniciar", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(35.dp)
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
                Text("Tus mínimos", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(10.dp))

                FilterRow(icon = Icons.Default.Schedule, title = "Por hora", value = "$ ${settings.minRateByHour}", isActive = true) {
                    tempFilterInput = settings.minRateByHour
                    showFilterDialog = "hour"
                }
                Spacer(modifier = Modifier.height(6.dp))
                FilterRow(icon = Icons.Default.Route, title = "Por km", value = "$ ${settings.minRateByKm}", isActive = true) {
                    tempFilterInput = settings.minRateByKm
                    showFilterDialog = "km"
                }
                Spacer(modifier = Modifier.height(6.dp))
                FilterRow(icon = Icons.Default.PersonPinCircle, title = "Recogida máx.", value = "${settings.maxPickupDistance} km", isActive = true) {
                    tempFilterInput = settings.maxPickupDistance
                    showFilterDialog = "pickup"
                }
                Spacer(modifier = Modifier.height(6.dp))

                val summaryZones = if (settings.excludedZones.isEmpty()) "Ninguna" else "${settings.excludedZones.size} bloqueadas"
                FilterRow(icon = Icons.Default.LocationOff, title = "Zonas", value = summaryZones, isActive = settings.excludedZones.isNotEmpty(), onClick = onManageZones)
            }
        }
        if (diagnostic.active) Text("Diagnóstico ${if (diagnostic.visual) "con imágenes" else "técnico"} activo",
            color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
        ReadingReportPanel()
        DriverTools(viewModel, showHistory = false)
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
                    onValueChange = { tempFilterInput = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            },
            confirmButton = {
                Button(enabled = tempFilterInput.replace(',', '.').toDoubleOrNull()?.let { it.isFinite() && it >= 0 } == true, onClick = {
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
fun FilterRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String, isActive: Boolean, onClick: () -> Unit) {
    val primaryAccent = MaterialTheme.colorScheme.primary
    Surface(onClick = onClick, shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = primaryAccent, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                Text(text = value, color = if (isActive) primaryAccent else MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
    }
}
