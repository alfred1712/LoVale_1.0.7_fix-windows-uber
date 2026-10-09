package com.example.lovale2

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import com.example.lovale2.ui.LoValeNavigation
import com.example.lovale2.ui.FuelScreen
import com.example.lovale2.ui.JourneyScreen
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.material3.Surface
import androidx.core.view.WindowCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lovale2.services.TripAccessibilityService
import com.example.lovale2.ui.MainScreen
import com.example.lovale2.ui.MainViewModel
import com.example.lovale2.ui.Settings.ForbiddenZonesScreen
import com.example.lovale2.ui.theme.LoValeTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* el usuario ya decidió, no hace falta hacer nada más */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            LoValeTheme {
                var showZonesScreen by remember { mutableStateOf(false) }
                var accessibilityEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled()) }
                var overlayPermissionGranted by remember { mutableStateOf(Settings.canDrawOverlays(this@MainActivity)) }

                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            accessibilityEnabled = isAccessibilityServiceEnabled()
                            overlayPermissionGranted = Settings.canDrawOverlays(this@MainActivity)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                BackHandler(showZonesScreen) { showZonesScreen = false }
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                        val model: MainViewModel = viewModel()
                        if (showZonesScreen) {
                            val settings by model.settings.collectAsState()
                            ForbiddenZonesScreen(
                                forbiddenZones = settings.excludedZones,
                                onAddZone = { model.addExcludedZone(it) },
                                onRemoveZone = { model.removeExcludedZone(it) },
                                onBack = { showZonesScreen = false }
                            )
                        } else {
                            LoValeNavigation(fuel = { FuelScreen(model) }, earnings = { JourneyScreen(model) },
                                preferences = { com.example.lovale2.ui.DriverPreferencesPanel(model) }) {
                                MainScreen(
                                    onManageZones = { showZonesScreen = true },
                                    isAccessibilityServiceEnabled = accessibilityEnabled,
                                    onOpenAccessibilitySettings = { openAccessibilitySettings() },
                                    overlayPermissionGranted = overlayPermissionGranted,
                                    onOpenOverlaySettings = { openOverlaySettings() },
                                    viewModel = model
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(this, TripAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            if (ComponentName.unflattenFromString(splitter.next()) == expected) return true
        }
        return false
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openOverlaySettings() {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")))
    }
}
