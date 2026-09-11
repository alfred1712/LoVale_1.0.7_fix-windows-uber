package com.example.lovale2.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.lovale2.R

/** Overlay compacto de LoVale 1.0.8. */
class TripOverlayService : Service() {

    companion object {
        private const val TAG = "LoVale"
        private const val FGS_CHANNEL_ID = "overlay_fgs_channel_id"
        private const val FGS_NOTIFICATION_ID = 2001
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private lateinit var tvStatus: TextView
    private lateinit var tvZone: TextView
    private lateinit var btnClose: ImageButton

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        iniciarComoForegroundService()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val layoutParamsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val width = (resources.displayMetrics.widthPixels * 0.72f).toInt()
        val params = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutParamsType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(72)
        }

        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_alert, null)
        floatingView?.let { view ->
            tvStatus = view.findViewById(R.id.tvStatus)
            tvZone = view.findViewById(R.id.tvZone)
            btnClose = view.findViewById(R.id.btnClose)
            btnClose.setOnClickListener { stopSelf() }
        }

        try {
            windowManager?.addView(floatingView, params)
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo agregar la ventana flotante", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || !::tvStatus.isInitialized) return START_NOT_STICKY

        val level = intent.getStringExtra("EXTRA_PROFITABILITY_LEVEL") ?: "RED"
        val zone = intent.getStringExtra("EXTRA_ZONE_LABEL").orEmpty()

        val (title, background) = when (level) {
            "GREEN" -> "RENTABLE" to Color.rgb(46, 125, 50)
            "YELLOW" -> "CASI RENTABLE" to Color.rgb(245, 166, 35)
            "ZONE" -> "ZONA NO DESEADA" to Color.rgb(198, 40, 40)
            else -> "NO RENTABLE" to Color.rgb(198, 40, 40)
        }

        tvStatus.text = title
        tvZone.visibility = if (level == "ZONE") View.VISIBLE else View.GONE
        tvZone.text = if (zone.isNotBlank()) zone else "Zona excluida"

        val backgroundDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(18).toFloat()
            setColor(background)
        }
        floatingView?.background = backgroundDrawable

        Log.d(TAG, "OVERLAY visible: $level")
        return START_NOT_STICKY
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

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
            .setContentTitle("LoVale")
            .setContentText("Evaluando viaje")
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
        if (floatingView != null) {
            try {
                windowManager?.removeView(floatingView)
            } catch (e: Exception) {
                Log.e(TAG, "Error al remover la ventana flotante", e)
            }
            floatingView = null
        }
        super.onDestroy()
    }
}
