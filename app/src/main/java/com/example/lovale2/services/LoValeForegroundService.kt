package com.example.lovale2.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
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

    override fun onCreate() {
        super.onCreate()
        iniciarForeground()
        Log.d(TAG, "Servicio persistente iniciado")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // No hacemos OCR ni captura aquí. El AccessibilityService es el único
        // responsable de detectar la plataforma seleccionada y procesar ofertas.
        return START_STICKY
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

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("LoVale")
            .setContentText("Monitoreo activo")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

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

    override fun onDestroy() {
        Log.d(TAG, "Servicio persistente detenido")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
