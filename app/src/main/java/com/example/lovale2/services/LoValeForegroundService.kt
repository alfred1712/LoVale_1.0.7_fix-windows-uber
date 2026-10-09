package com.example.lovale2.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.app.PendingIntent
import android.content.ComponentName
import android.provider.Settings
import android.os.PowerManager
import com.example.lovale2.MainActivity
import com.example.lovale2.data.settings.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import androidx.core.app.NotificationCompat

/**
 * Servicio persistente liviano de LoVale.
 *
 * Desde 1.0.8 el OCR ya NO usa MediaProjection ni ScreenCaptureHolder.
 * La captura/OCR de Uber vive exclusivamente dentro de TripAccessibilityService,
 * que puede limitar la captura a la ventana de Uber (o recortarla en Android 11-13).
 */
class LoValeForegroundService : Service() {

    companion object {
        private const val TAG = "LoValeForegroundService"
        private const val CHANNEL_ID = "lovale_monitor_channel"
        private const val NOTIFICATION_ID = 3001
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lastStatus = ""
    override fun onCreate() {
        super.onCreate()
        iniciarForeground()
        scope.launch {
            delay(1000L)
            SettingsRepository(applicationContext).settingsFlow.collectLatest { settings ->
                if (!settings.serviceActive) { stopSelf(); return@collectLatest }
                while (isActive) {
                    if (com.example.lovale2.data.settings.DriverOptionsStore.get(this@LoValeForegroundService).state.value.quietAt()) {
                        SettingsRepository(applicationContext).setServiceActive(false)
                        TripOverlayService.dismissOffer("horario no molestar")
                        JourneyLocationService.pause(this@LoValeForegroundService)
                        com.example.lovale2.data.settings.SessionSummaryStore.get(this@LoValeForegroundService).stop()
                        stopSelf()
                        return@collectLatest
                    }
                    val expected = ComponentName(this@LoValeForegroundService, TripAccessibilityService::class.java)
                    val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                        .orEmpty().split(':').any { ComponentName.unflattenFromString(it) == expected }
                    MonitoringHealth.update(true, enabled, Settings.canDrawOverlays(this@LoValeForegroundService),
                        getSystemService(PowerManager::class.java).isInteractive)
                    val status = MonitoringHealth.state.value.label
                    if (status != lastStatus) {
                        lastStatus = status
                        try { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(status)) }
                        catch (_: SecurityException) { /* The user can revoke notification permission. */ }
                    }
                    delay(5000L)
                }
            }
        }
        Log.d(TAG, "Servicio persistente iniciado")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // No hacemos OCR ni captura aquí. El AccessibilityService es el único
        // responsable de detectar la plataforma seleccionada y procesar ofertas.
        return START_NOT_STICKY
    }

    private fun iniciarForeground() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Monitoreo LoVale",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }

        val notification = notification("Verificando lectura…")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "No se pudo iniciar el foreground service", t)
            stopSelf()
        }
    }

    private fun notification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(com.example.lovale2.R.drawable.ic_lovale_notification)
        .setContentTitle("LoVale").setContentText(text).setOngoing(true).setOnlyAlertOnce(true)
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        .setPriority(NotificationCompat.PRIORITY_LOW).build()

    override fun onDestroy() {
        scope.cancel()
        Log.d(TAG, "Servicio persistente detenido")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
