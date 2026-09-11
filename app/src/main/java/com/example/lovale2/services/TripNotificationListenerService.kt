package com.example.lovale2.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.lovale2.data.settings.RideApp
import com.example.lovale2.data.settings.SettingsRepository
import com.example.lovale2.domain.DatosViaje
import com.example.lovale2.domain.MotivoEvaluacion
import com.example.lovale2.domain.ResultadoEvaluacion
import com.example.lovale2.domain.TripEvaluator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

class TripNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "LoValeNotif"
        private const val ALERT_CHANNEL_ID = "viajes_notif_channel_id"
        private const val ALERT_NOTIFICATION_ID = 2002
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val ofertasProcesadas = LinkedHashMap<String, Long>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName?.lowercase(Locale.ROOT) ?: return

        // Identificar si la notificación pertenece a Uber, Cabify o DiDi
        val app = when {
            packageName.contains("ubercab") -> RideApp.UBER
            packageName.contains("cabify") -> RideApp.CABIFY
            packageName.contains("didi") -> RideApp.DIDI
            else -> return
        }

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        val textoCompleto = "$title $text $bigText".trim()
        if (textoCompleto.isBlank()) return

        Log.d(TAG, "Notificación detectada de ${app.label}: $textoCompleto")

        // Procesar la oferta extraída de la notificación
        serviceScope.launch {
            val repository = SettingsRepository(applicationContext)
            // Aquí evaluamos si coincide con los filtros del usuario
            analizarYMostrarNotificacion(app, textoCompleto, packageName)
        }
    }

    private fun analizarYMostrarNotificacion(app: RideApp, textoOferta: String, packageName: String) {
        val tripEvaluator = TripEvaluator(
            tarifaMinimaPorKm = 950.0,
            tarifaMinimaPorHora = 0.0
        )

        // Parsear datos básicos del texto de la notificación de Uber/DiDi/Cabify
        val datos = tripEvaluator.extraerDatosDeViaje(textoOferta, "", "")

        if (datos.precio <= 0.0) {
            // Si la notificación no trae precio explícito pero menciona la alerta de viaje
            if (textoOferta.contains("viaje", ignoreCase = true) || textoOferta.contains("solicitud", ignoreCase = true)) {
                Log.d(TAG, "Alerta de viaje detectada por notificación de ${app.label}, pero sin datos de precio claros.")
            }
            return
        }

        val evaluacion = tripEvaluator.evaluarViaje(datos, emptyList())

        val fingerprint = "$packageName|${datos.precio}|${datos.distanciaKm}"
        val ahora = System.currentTimeMillis()
        if (ofertasProcesadas.containsKey(fingerprint) && ahora - ofertasProcesadas[fingerprint]!! < 5000L) {
            return
        }
        ofertasProcesadas[fingerprint] = ahora

        dispararAlerta(evaluacion, datos)
    }

    private fun dispararAlerta(evaluacion: ResultadoEvaluacion, datos: DatosViaje) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(ALERT_CHANNEL_ID, "Alertas de Viajes (Notificaciones)", NotificationManager.IMPORTANCE_HIGH)
            )
        }

        val titulo = if (evaluacion.aprobado) "¡Viaje Rentable (Notif)!" else "Viaje Descartado"
        val mensaje = "Precio: $${datos.precio} | Distancia: ${datos.distanciaKm} km"

        notificationManager.notify(
            ALERT_NOTIFICATION_ID,
            NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(titulo)
                .setContentText(mensaje)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
        )
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}