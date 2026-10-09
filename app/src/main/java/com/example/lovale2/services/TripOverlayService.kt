package com.example.lovale2.services

import com.example.lovale2.diagnostics.DiagnosticRecorder
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import java.text.NumberFormat
import java.util.Locale
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.lovale2.R
import com.example.lovale2.domain.RateLevel
import com.example.lovale2.domain.rateLevel

/** Overlay compacto de LoVale 1.0.8. */
class TripOverlayService : Service() {

    companion object {
        private const val TAG = "LoVale"
        private var instance: TripOverlayService? = null
        fun isCenterVisible(): Boolean = instance?.currentOffer == "central"
        fun isOfferVisible(): Boolean = instance?.let { it.currentOffer != null && !it.testOverlay } == true
        fun updateZone(offerId: String, known: Boolean, zone: String, level: String) {
            val service = instance ?: return
            if (service.closing || service.testOverlay) return
            fun update(intent: Intent) {
                intent.putExtra("EXTRA_ZONE_KNOWN", known)
                intent.putExtra("EXTRA_ZONE_LABEL", zone)
                intent.putExtra("EXTRA_PROFITABILITY_LEVEL", level)
            }
            if (service.currentOffer == offerId) {
                val intent = service.lastIntent ?: return
                update(intent)
                service.onStartCommand(intent, 0, 0)
            } else if (service.currentOffer == "central") {
                val cards = service.lastCards.map { Intent(it) }
                val card = cards.firstOrNull { it.getStringExtra("EXTRA_OFFER_ID") == offerId } ?: return
                update(card)
                service.showCards(cards, refresh = true)
            }
        }
        fun dismissOffer(reason: String) {
            instance?.let { service ->
                if (!service.testOverlay) {
                    Log.d(TAG, "OVERLAY cierre por $reason")
                    DiagnosticRecorder.event("overlay_close", "offerId" to service.currentOffer, "reason" to reason)
                    service.closing = true
                    service.stopSelf()
                }
            }
        }
        private const val FGS_CHANNEL_ID = "overlay_fgs_channel_id"
        private const val FGS_NOTIFICATION_ID = 2001
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var windowAdded = false
    private val cardViews = mutableListOf<View>()
    private var cardsKey = ""
    private var closing = false
    private var lastIntent: Intent? = null
    private var lastCards: List<Intent> = emptyList()
    private var params: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())
    private var currentOffer: String? = null
    private var testOverlay = false
    private var fare = 0.0
    private var offerPackage = ""
    private var offerShownAt = 0L
    private val dismiss = Runnable {
        DiagnosticRecorder.event("overlay_close", "offerId" to currentOffer, "reason" to "timeout")
        Log.d(TAG, "OVERLAY cerrado: timeout 9s")
        closing = true
        stopSelf()
    }
    private lateinit var tvStatus: TextView
    private lateinit var tvZone: TextView
    private lateinit var tvRateKm: TextView
    private lateinit var tvEstimatedNet: TextView

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        iniciarComoForegroundService()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val layoutParamsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val width = minOf(dp(156), resources.displayMetrics.widthPixels - dp(16))
        val params = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutParamsType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val saved = getSharedPreferences("overlay_position", MODE_PRIVATE)
            x = saved.getInt("x", dp(8)).coerceIn(0, (resources.displayMetrics.widthPixels - width).coerceAtLeast(0))
            y = saved.getInt("y", dp(48)).coerceIn(0, (resources.displayMetrics.heightPixels - dp(180)).coerceAtLeast(0))
        }

        this.params = params
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_alert, null)
        floatingView?.let { view ->
            tvStatus = view.findViewById(R.id.tvStatus)
            tvZone = view.findViewById(R.id.tvZone)
            tvRateKm = view.findViewById(R.id.tvRateKm)
            tvEstimatedNet = view.findViewById(R.id.tvEstimatedNet)
            var downX = 0f; var downY = 0f; var initialX = 0; var initialY = 0; var dragging = false
            val slop = android.view.ViewConfiguration.get(this).scaledTouchSlop
            view.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX; downY = event.rawY; initialX = params.x; initialY = params.y; dragging = false
                        true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - downX; val dy = event.rawY - downY
                        if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > slop) dragging = true
                        if (dragging) {
                            params.x = (initialX + dx.toInt()).coerceIn(0, (resources.displayMetrics.widthPixels - view.width).coerceAtLeast(0))
                            params.y = (initialY + dy.toInt()).coerceIn(0, (resources.displayMetrics.heightPixels - view.height - dp(32)).coerceAtLeast(0))
                            try { windowManager?.updateViewLayout(view, params) } catch (_: Exception) { }
                        }
                        true
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        if (dragging) getSharedPreferences("overlay_position", MODE_PRIVATE).edit().putInt("x", params.x).putInt("y", params.y).apply()
                        else view.performClick()
                        true
                    }
                    android.view.MotionEvent.ACTION_CANCEL -> true
                    else -> false
                }
            }
            view.setOnClickListener { DiagnosticRecorder.event("overlay_close", "offerId" to currentOffer, "reason" to "toque"); closing = true; stopSelf() }
        }

        try {
            windowManager?.addView(floatingView, params)
            windowAdded = true
        } catch (e: Exception) {
            DiagnosticRecorder.event("overlay_error", "type" to e.javaClass.simpleName)
            Log.e(TAG, "No se pudo agregar la ventana flotante", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || !::tvStatus.isInitialized || !windowAdded) return START_NOT_STICKY

        @Suppress("DEPRECATION")
        val cards = intent.getParcelableArrayListExtra<Intent>("EXTRA_CARDS")
        if (cards != null) { showCards(cards); return START_NOT_STICKY }
        clearCards()
        lastIntent = Intent(intent)
        floatingView?.visibility = View.VISIBLE
        testOverlay = intent.getBooleanExtra("EXTRA_TEST", false)
        val level = intent.getStringExtra("EXTRA_PROFITABILITY_LEVEL") ?: "RED"
        val zone = intent.getStringExtra("EXTRA_ZONE_LABEL").orEmpty()
        val offer = intent.getStringExtra("EXTRA_OFFER_ID") ?: "$level|$zone"
        fare = intent.getDoubleExtra("EXTRA_FARE", 0.0)
        offerPackage = intent.getStringExtra("EXTRA_PLATFORM_PACKAGE").orEmpty()
        if (currentOffer != offer) {
            offerShownAt = System.currentTimeMillis()
            currentOffer = offer
            handler.removeCallbacks(dismiss)
            handler.postDelayed(dismiss, 9000L)
        }

        val title = when (level) {
            "GREEN" -> "RENTABLE"
            "YELLOW" -> "CASI RENTABLE"
            "ZONE" -> "ZONA NO DESEADA"
            else -> "NO RENTABLE"
        }

        val formatter = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-AR")).apply {
            maximumFractionDigits = 0
        }
        fun rate(key: String, unit: String): String {
            val value = intent.getDoubleExtra(key, Double.NaN)
            return if (value.isFinite()) "$ ${formatter.format(value)} /$unit" else "— /$unit"
        }
        tvStatus.text = rate("EXTRA_RATE_KM", "km")
        val estimatedNet = intent.getDoubleExtra("EXTRA_ESTIMATED_NET", Double.NaN)
        tvEstimatedNet.visibility = if (estimatedNet.isFinite() && level != "INCOMPLETE") View.VISIBLE else View.GONE
        tvEstimatedNet.text = "Neto estimado\n$ ${formatter.format(estimatedNet.takeIf { it.isFinite() } ?: 0.0)}"
        tvEstimatedNet.setTextColor(if (estimatedNet < 0) Color.rgb(255, 138, 128) else getColor(R.color.lovale_ivory))
        tvRateKm.text = rate("EXTRA_RATE_HOUR", "h")
        tvZone.text = when {
            level == "ZONE" -> if (zone.isNotBlank()) "Zona no deseada · $zone" else "Zona no deseada"
            intent.getBooleanExtra("EXTRA_ZONE_KNOWN", false) -> "Zona deseada"
            else -> "Zona sin verificar"
        }
        fun colorFor(rateKey: String, minKey: String): Int = when (
            rateLevel(intent.getDoubleExtra(rateKey, Double.NaN), intent.getDoubleExtra(minKey, 0.0))
        ) {
            RateLevel.GREEN -> Color.rgb(105, 240, 174)
            RateLevel.YELLOW -> Color.rgb(255, 213, 79)
            RateLevel.RED -> Color.rgb(255, 138, 128)
            RateLevel.UNSET -> getColor(R.color.lovale_muted)
        }
        tvStatus.setTextColor(colorFor("EXTRA_RATE_KM", "EXTRA_MIN_KM"))
        tvRateKm.setTextColor(colorFor("EXTRA_RATE_HOUR", "EXTRA_MIN_HOUR"))
        val pickupExceeded = intent.getBooleanExtra("EXTRA_PICKUP_EXCEEDED", false)
        if (pickupExceeded) tvZone.append(" · Pickup excedido")
        if (level == "INCOMPLETE") {
            tvStatus.text = "Lectura incompleta"
            tvRateKm.text = "Sin evaluar"
            tvZone.text = "Faltan datos del viaje"
        }
        if (intent.getBooleanExtra("EXTRA_TEST", false)) tvZone.text = "PRUEBA · ${tvZone.text}"
        tvZone.setTextColor(if (level == "ZONE" || pickupExceeded) Color.rgb(255, 138, 128) else getColor(R.color.lovale_muted))
        floatingView?.contentDescription = "$title. ${if (tvEstimatedNet.visibility == View.VISIBLE) tvEstimatedNet.text else ""}. ${tvStatus.text}. ${tvRateKm.text}. ${tvZone.text}. Arrastrar para mover. Tocar para cerrar"

        val backgroundDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(12).toFloat()
            setColor(getColor(R.color.lovale_ink))
            setStroke(dp(1), getColor(R.color.lovale_copper))
        }
        floatingView?.background = backgroundDrawable

        DiagnosticRecorder.event("overlay_visible", "offerId" to currentOffer, "level" to level, "test" to testOverlay)
        Log.d(TAG, "OVERLAY visible: $level")
        return START_NOT_STICKY
    }

    private fun clearCards() {
        cardsKey = ""
        cardViews.forEach { try { windowManager?.removeView(it) } catch (_: Exception) { } }
        cardViews.clear()
    }
    private fun showCards(cards: List<Intent>, refresh: Boolean = false) {
        lastCards = cards.map { Intent(it) }
        val key = cards.joinToString { "${it.getDoubleExtra("EXTRA_FARE", 0.0)}|${it.getDoubleExtra("EXTRA_RATE_KM", 0.0)}|${it.getDoubleExtra("EXTRA_RATE_HOUR", 0.0)}|${it.getStringExtra("EXTRA_PROFITABILITY_LEVEL")}|${it.getDoubleExtra("EXTRA_MIN_KM",0.0)}|${it.getDoubleExtra("EXTRA_MIN_HOUR",0.0)}|${it.getBooleanExtra("EXTRA_ZONE_KNOWN",false)}|${it.getIntExtra("EXTRA_CARD_TOP",0)}|${it.getIntExtra("EXTRA_CARD_RIGHT",0)}" }
        if (cardsKey == key && cardViews.isNotEmpty()) { if (!refresh) { handler.removeCallbacks(dismiss); handler.postDelayed(dismiss,9000) }; return }
        clearCards()
        cardsKey = key
        floatingView?.visibility = View.GONE
        currentOffer = "central"
        testOverlay = false
        if (!refresh) { handler.removeCallbacks(dismiss); handler.postDelayed(dismiss, 9000) }
        val format = NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-AR"))
        cards.forEach { card ->
            val label = TextView(this).apply {
                textSize = 12f
                setPadding(dp(6), dp(3), dp(6), dp(3))
                val km = "${format.format(card.getDoubleExtra("EXTRA_RATE_KM", 0.0))}/km"
                val hour = "${format.format(card.getDoubleExtra("EXTRA_RATE_HOUR", 0.0))}/h"
                val zone = if (card.getStringExtra("EXTRA_PROFITABILITY_LEVEL") == "ZONE") "Zona excluida"
                    else if (card.getBooleanExtra("EXTRA_ZONE_KNOWN", false)) "Zona ✓" else "Zona ?"
                val line = "$ ${format.format(card.getDoubleExtra("EXTRA_FARE", 0.0))} · $km\n$hour · $zone"
                val styled = android.text.SpannableString(line)
                listOf(Triple(km,"EXTRA_RATE_KM","EXTRA_MIN_KM"),Triple(hour,"EXTRA_RATE_HOUR","EXTRA_MIN_HOUR")).forEach { (label,value,minimum) ->
                    val color = when(rateLevel(card.getDoubleExtra(value,0.0), card.getDoubleExtra(minimum,0.0))) {
                        RateLevel.GREEN -> Color.rgb(105,240,174); RateLevel.YELLOW -> Color.rgb(255,213,79)
                        RateLevel.RED -> Color.rgb(255,138,128); RateLevel.UNSET -> getColor(R.color.lovale_muted)
                    }
                    val start = line.indexOf(label)
                    styled.setSpan(android.text.style.ForegroundColorSpan(color),start,start+label.length,0)
                }
                if (zone == "Zona excluida") styled.setSpan(android.text.style.ForegroundColorSpan(Color.rgb(255,138,128)), line.indexOf(zone),line.length,0)
                text = styled
                setTextColor(getColor(R.color.lovale_ivory))
                background = GradientDrawable().apply { setColor(getColor(R.color.lovale_ink)); cornerRadius = dp(8).toFloat() }
            }
            val lp = WindowManager.LayoutParams(dp(155), WindowManager.LayoutParams.WRAP_CONTENT,
                requireNotNull(params).type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                x = card.getIntExtra("EXTRA_CARD_RIGHT", resources.displayMetrics.widthPixels) - dp(160)
                y = card.getIntExtra("EXTRA_CARD_TOP", 0)
            }
            try {
                windowManager?.addView(label, lp)
                cardViews += label
                DiagnosticRecorder.event("overlay_visible", "test" to card.getBooleanExtra("EXTRA_TEST", false))
            } catch (e: Exception) {
                DiagnosticRecorder.event("overlay_error")
                Log.w(TAG, "OVERLAY central: ${e.javaClass.simpleName}")
            }
        }
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
            .setSmallIcon(com.example.lovale2.R.drawable.ic_lovale_notification)
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

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (cardViews.isNotEmpty()) { stopSelf(); return }
        val view = floatingView ?: return
        val lp = params ?: return
        lp.width = minOf(dp(156), resources.displayMetrics.widthPixels - dp(16))
        lp.x = lp.x.coerceIn(0, (resources.displayMetrics.widthPixels - lp.width).coerceAtLeast(0))
        lp.y = lp.y.coerceIn(0, (resources.displayMetrics.heightPixels - view.height - dp(32)).coerceAtLeast(0))
        try { windowManager?.updateViewLayout(view,lp) } catch (_: Exception) { }
    }

    override fun onDestroy() {
        windowAdded = false
        if (instance === this) instance = null
        handler.removeCallbacksAndMessages(null)
        clearCards()
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
