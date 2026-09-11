package com.example.lovale2.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.lovale2.R

class TripOverlayService : Service() {

    companion object {
        private const val TAG = "LoVale"
        private const val FGS_CHANNEL_ID = "overlay_fgs_channel_id"
        private const val FGS_NOTIFICATION_ID = 2001
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null

    private lateinit var tvStatus: TextView
    private lateinit var tvPrice: TextView
    private lateinit var tvPricePerKm: TextView
    private lateinit var tvPricePerHour: TextView
    private lateinit var tvTripDetails: TextView
    private lateinit var tvPickup: TextView
    private lateinit var tvDestination: TextView
    private lateinit var tvForbiddenZoneWarning: TextView
    private lateinit var btnClose: Button

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        // Requerido: todo servicio arrancado con startForegroundService() DEBE
        // llamar a startForeground() en los primeros segundos, o el sistema
        // mata el proceso con ForegroundServiceDidNotStartInTimeException.
        iniciarComoForegroundService()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val layoutParamsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutParamsType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 100
        }

        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_alert, null)

        floatingView?.let { view ->
            tvStatus = view.findViewById(R.id.tvStatus)
            tvPrice = view.findViewById(R.id.tvPrice)
            tvPricePerKm = view.findViewById(R.id.tvPricePerKm)
            tvPricePerHour = view.findViewById(R.id.tvPricePerHour)
            tvTripDetails = view.findViewById(R.id.tvTripDetails)
            tvPickup = view.findViewById(R.id.tvPickup)
            tvDestination = view.findViewById(R.id.tvDestination)
            tvForbiddenZoneWarning = view.findViewById(R.id.tvForbiddenZoneWarning)
            btnClose = view.findViewById(R.id.btnClose)

            btnClose.setOnClickListener { stopSelf() }
        }

        try {
            windowManager?.addView(floatingView, params)
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo agregar la ventana flotante (¿falta permiso 'Mostrar sobre otras apps'?)", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val price = it.getDoubleExtra("EXTRA_PRICE", 0.0)
            val rateKm = it.getDoubleExtra("EXTRA_RATE_KM", 0.0)
            val rateHour = it.getDoubleExtra("EXTRA_RATE_HOUR", 0.0)
            val km = it.getDoubleExtra("EXTRA_DISTANCE_KM", 0.0)
            val minutes = it.getDoubleExtra("EXTRA_DURATION_MIN", 0.0)
            val pickup = it.getStringExtra("EXTRA_PICKUP") ?: ""
            val destination = it.getStringExtra("EXTRA_DESTINATION") ?: ""
            val zoneDest = it.getStringExtra("EXTRA_ZONE_DESTINATION") ?: ""
            val zonePickup = it.getStringExtra("EXTRA_ZONE_PICKUP") ?: ""
            val isProfitable = it.getBooleanExtra("EXTRA_IS_PROFITABLE", false)
            val motivo = it.getStringExtra("EXTRA_MOTIVO") ?: ""

            if (!::tvStatus.isInitialized) {
                Log.w(TAG, "onStartCommand llegó antes de que la vista esté lista, se ignora este evento")
                return START_NOT_STICKY
            }

            tvStatus.text = if (isProfitable) "¡VIAJE RENTABLE! ✓" else "VIAJE NO RENTABLE ✕"
            floatingView?.setBackgroundColor(if (isProfitable) 0xFF2E7D32.toInt() else 0xFFC62828.toInt())

            tvPrice.text = "Precio: $${String.format("%.0f", price)}"
            tvPricePerKm.text = "Tarifa/Km: $${String.format("%.2f", rateKm)}"
            tvPricePerHour.text = "Tarifa/Hora: $${String.format("%.2f", rateHour)}"
            tvTripDetails.text = "Distancia: ${String.format("%.1f", km)} km | Duración: ${String.format("%.0f", minutes)} min"

            tvPickup.text = if (pickup.isNotBlank()) "Recogida: $pickup" else "Recogida: -"
            tvDestination.text = if (destination.isNotBlank()) "Destino: $destination" else "Destino: -"

            if (motivo == "ZONA_EXCLUIDA" || zoneDest.isNotEmpty() || zonePickup.isNotEmpty()) {
                val zonaAfectada = if (zoneDest.isNotEmpty()) zoneDest else zonePickup
                tvForbiddenZoneWarning.visibility = View.VISIBLE
                tvForbiddenZoneWarning.text = "⚠ Zona no deseada: $zonaAfectada"
            } else {
                tvForbiddenZoneWarning.visibility = View.GONE
            }
        }
        return START_NOT_STICKY
    }

    private fun iniciarComoForegroundService() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    FGS_CHANNEL_ID,
                    "Overlay de viajes LoVale",
                    NotificationManager.IMPORTANCE_MIN
                )
            )
        }

        val notification = NotificationCompat.Builder(this, FGS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("LoVale está mostrando la evaluación del viaje")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    FGS_NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(FGS_NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo iniciar TripOverlayService como foreground service", e)
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (floatingView != null) {
            try {
                windowManager?.removeView(floatingView)
            } catch (e: Exception) {
                Log.e(TAG, "Error al remover la ventana flotante", e)
            }
            floatingView = null
        }
    }
}
