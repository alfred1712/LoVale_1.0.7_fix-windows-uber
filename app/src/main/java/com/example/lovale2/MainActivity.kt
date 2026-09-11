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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLifecycleOwner
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

                if (showZonesScreen) {
                    val viewModel: MainViewModel = viewModel()
                    val settings by viewModel.settings.collectAsState()
                    ForbiddenZonesScreen(
                        forbiddenZones = settings.excludedZones,
                        onAddZone = { viewModel.addExcludedZone(it) },
                        onRemoveZone = { viewModel.removeExcludedZone(it) },
                        onBack = { showZonesScreen = false }
                    )
                } else {
                    MainScreen(
                        onManageZones = { showZonesScreen = true },
                        isAccessibilityServiceEnabled = accessibilityEnabled,
                        onOpenAccessibilitySettings = { openAccessibilitySettings() },
                        overlayPermissionGranted = overlayPermissionGranted,
                        onOpenOverlaySettings = { openOverlaySettings() }
                    )
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
