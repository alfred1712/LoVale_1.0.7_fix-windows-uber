package com.example.lovale2.services

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.*
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.lovale2.MainActivity
import com.example.lovale2.data.settings.JourneyStore
import com.example.lovale2.domain.*

/** Armed from a visible screen; OCR only signals an already-running service. */
class JourneyLocationService : Service(), LocationListener {
    private val handler = Handler(Looper.getMainLooper())
    private val store by lazy { JourneyStore.get(this) }
    private val manager by lazy { getSystemService(LocationManager::class.java) }
    private var counter = JourneyDistance()
    private var home: HomeArrival? = null
    private var lastGoodAt = 0L
    private var healthy = false
    private val watchdog = object : Runnable {
        override fun run() {
            if (!healthy) return
            if (store.state.value.pauseUntil > 0 && System.currentTimeMillis() >= store.state.value.pauseUntil) { finishJourney(false); return }
            if (store.state.value.running && !store.state.value.waiting && SystemClock.elapsedRealtime() - lastGoodAt > 45000) {
                store.update { it.copy(partial = true, message = "Sin señal GPS · km parciales") }
            }
            handler.postDelayed(this, 15000)
        }
    }
    override fun onCreate() {
        super.onCreate()
        try {
            val nm = getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "Kilómetros de jornada", NotificationManager.IMPORTANCE_LOW))
            val notification = notification("Esperando la primera oferta")
            if (Build.VERSION.SDK_INT >= 29) startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(ID, notification)
            healthy = true
            instance = this
        } catch (e: Exception) {
            store.update { it.copy(running = false, waiting = false, message = "Abrí LoVale y habilitá ubicación precisa") }
            Log.w("LoVale", "JOURNEY no se pudo iniciar: ${e.javaClass.simpleName}")
            stopSelf()
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!healthy) return START_NOT_STICKY
        if (intent?.action == FINISH) { finishJourney(false); return START_NOT_STICKY }
        if (store.state.value.running) return START_STICKY
        val previous = store.state.value
        val resume = previous.startedAt > 0 && previous.endedAt == 0L
        counter = JourneyDistance()
        home = homeDetector()
        lastGoodAt = SystemClock.elapsedRealtime()
        store.update { it.copy(running = true, waiting = !resume, partial = it.partial || resume,
            message = if (resume) "Buscando GPS · jornada reanudada" else "Esperando la primera oferta") }
        handler.removeCallbacks(watchdog)
        handler.post(watchdog)
        if (resume) startGps()
        Log.d("LoVale", "JOURNEY preparado; resume=$resume")
        return START_STICKY
    }
    private fun startGps() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                store.update { it.copy(partial = true, message = "Habilitá ubicación precisa") }
                stopSelf(); return
            }
            val providers = if (Build.VERSION.SDK_INT >= 31 && manager.allProviders.contains("fused") && manager.isProviderEnabled("fused")) listOf("fused")
                else listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { manager.allProviders.contains(it) }
            providers.forEach { provider -> manager.requestLocationUpdates(provider, 10000L, 0f, this, Looper.getMainLooper()) }
            handler.removeCallbacks(watchdog)
            handler.post(watchdog)
            com.example.lovale2.diagnostics.DiagnosticRecorder.event("gps_started", "intervalMs" to 10000)
        } catch (e: Exception) {
            store.update { it.copy(partial = true, message = "Habilitá ubicación precisa y GPS") }
            stopSelf()
        }
    }
    private fun homeDetector(): HomeArrival? {
        val state = store.state.value
        val lat = state.homeLatitude ?: return null
        val lon = state.homeLongitude ?: return null
        return HomeArrival(JourneyFix(lat, lon, 0.0, 0), state.departedHome)
    }
    private fun firstOffer() {
        if (!healthy || !store.state.value.waiting) return
        counter = JourneyDistance()
        lastGoodAt = SystemClock.elapsedRealtime()
        store.update { it.copy(meters = 0.0, startedAt = System.currentTimeMillis(), endedAt = 0, waiting = false,
            partial = false, departedHome = false, message = "Buscando GPS") }
        home = homeDetector()
        startGps()
        getSystemService(NotificationManager::class.java).notify(ID, notification("Contando km hasta llegar a casa"))
        Log.d("LoVale", "JOURNEY inicio por primera oferta válida")
    }
    override fun onLocationChanged(location: Location) {
        com.example.lovale2.diagnostics.DiagnosticRecorder.event("gps_fix", "accuracyMeters" to location.accuracy)
        DriverLocation.publish(location)
        if (!healthy || !store.state.value.running || store.state.value.waiting) return
        val now = SystemClock.elapsedRealtime()
        val fix = JourneyFix(location.latitude, location.longitude,
            if (location.hasAccuracy()) location.accuracy.toDouble() else Double.POSITIVE_INFINITY,
            location.elapsedRealtimeNanos / 1000000, if (location.hasSpeed()) location.speed.toDouble() else null)
        val arrived = home?.add(fix, now) == true
        if (arrived) { finishJourney(true); return }
        if (!usableFix(fix, now)) return
        lastGoodAt = now
        val meters = counter.add(fix, now)
        store.update { it.copy(meters = it.meters + meters, partial = it.partial || counter.interrupted,
            departedHome = home?.departed == true, message = "Contando recorrido GPS") }
    }
    override fun onProviderDisabled(provider: String) {
        if (!store.state.value.waiting) store.update { it.copy(partial = true, message = "GPS desactivado · km parciales") }
    }
    override fun onProviderEnabled(provider: String) = Unit
    @Deprecated("Required for old Android")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    private fun finishJourney(automatic: Boolean) {
        com.example.lovale2.data.settings.SessionSummaryStore.get(this).stop()
        store.update { it.copy(running = false, waiting = false, pauseUntil = 0,
            endedAt = if (it.startedAt > 0) System.currentTimeMillis() else it.endedAt,
            message = if (automatic) "Jornada cerrada · llegaste a casa" else "Jornada finalizada") }
        Log.d("LoVale", "JOURNEY finalizada; automatic=$automatic")
        stopSelf()
    }
    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val end = PendingIntent.getService(this, 1, Intent(this, JourneyLocationService::class.java).setAction(FINISH), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(com.example.lovale2.R.drawable.ic_lovale_notification)
            .setContentTitle("LoVale · kilómetros de jornada").setContentText(text).setContentIntent(open)
            .setOngoing(true).setSilent(true).addAction(0, "Finalizar", end).build()
    }
    override fun onDestroy() {
        healthy = false
        if (instance === this) instance = null
        handler.removeCallbacksAndMessages(null)
        try { manager.removeUpdates(this) } catch (_: Exception) { }
        store.update { if (it.running) it.copy(running = false, waiting = false,
            partial = it.partial || !it.waiting, message = "Contador interrumpido · podés reanudar") else it }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object {
        private const val CHANNEL = "journey_location"
        private const val ID = 3002
        private const val FINISH = "journey.finish"
        @Volatile private var instance: JourneyLocationService? = null
        fun offerDetected() { instance?.let { service -> service.handler.post { service.firstOffer() } } }
        fun start(context: Context) {
            JourneyStore.get(context).update { it.copy(pauseUntil = 0) }
            if (instance != null) return
            val store = JourneyStore.get(context)
            if (store.state.value.homeLatitude == null || store.state.value.homeLongitude == null) {
                store.update { it.copy(message = "Guardá tu casa en Jornada") }; return
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                store.update { it.copy(message = "Habilitá ubicación precisa en Jornada") }; return
            }
            try { ContextCompat.startForegroundService(context, Intent(context, JourneyLocationService::class.java)) }
            catch (e: Exception) {
                store.update { it.copy(message = "Abrí Jornada para preparar el contador") }
                Log.w("LoVale", "JOURNEY inicio rechazado: ${e.javaClass.simpleName}")
            }
        }
        fun homeChanged() { instance?.let { it.home = it.homeDetector() } }
        fun pause(context: Context) {
            val state = JourneyStore.get(context)
            if (state.state.value.running) state.update { it.copy(pauseUntil = System.currentTimeMillis() + 300000,
                message = "Pausa · cierre en 5 min si no reactivás") }
        }
        fun finish(context: Context) {
            val service = instance
            if (service != null) service.handler.post { service.finishJourney(false) }
            else JourneyStore.get(context).update { it.copy(running = false, waiting = false,
                endedAt = if (it.startedAt > 0) System.currentTimeMillis() else 0, message = "Jornada finalizada") }
        }
    }
}
