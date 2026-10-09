package com.example.lovale2.services

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HealthStatus(val label: String = "Pausado", val warning: Boolean = false)

/** Reports observed faults, never infers missed offers from an idle screen. */
object MonitoringHealth {
    val readings = com.example.lovale2.domain.ReadingWatch()
    @Volatile var connected = false
        private set
    @Volatile private var lastEvent = 0L
    @Volatile private var ocrSince = 0L
    @Volatile private var failures = 0
    @Volatile private var lastFailure = 0L
    private val mutable = MutableStateFlow(HealthStatus())
    val state = mutable.asStateFlow()
    fun connected(value: Boolean) { connected = value; if (!value) ocrSince = 0 }
    fun event() { connected = true; lastEvent = SystemClock.elapsedRealtime() }
    fun ocrStarted() { ocrSince = SystemClock.elapsedRealtime() }
    fun ocrFinished(success: Boolean) {
        ocrSince = 0
        if (success) failures = 0 else { failures++; lastFailure = SystemClock.elapsedRealtime() }
    }
    fun paused() { readings.reset(); failures = 0; ocrSince = 0; lastEvent = 0; mutable.value = HealthStatus() }
    fun update(active: Boolean, permission: Boolean, overlay: Boolean, interactive: Boolean, now: Long = SystemClock.elapsedRealtime()) {
        mutable.value = if (active && readings.warning(now)) HealthStatus("Lectura cambió: revisá actualización o reportá el error", true) else assess(active, permission && connected, overlay, interactive,
            lastEvent > 0 && now - lastEvent < 15000,
            (ocrSince > 0 && now - ocrSince > 15000) || (failures >= 3 && now - lastFailure < 30000))
    }
    fun assess(active: Boolean, reader: Boolean, overlay: Boolean, interactive: Boolean, recentEvent: Boolean, failed: Boolean): HealthStatus = when {
        !active -> HealthStatus()
        !reader -> HealthStatus("Lectura desconectada · revisá el permiso", true)
        !overlay -> HealthStatus("Sin permiso de ventana flotante", true)
        !interactive -> HealthStatus("Pantalla apagada · lectura en espera")
        failed -> HealthStatus("No se pudo leer · revisá LoVale", true)
        recentEvent -> HealthStatus("Recibiendo actividad")
        else -> HealthStatus("Conectado · esperando actividad")
    }
}
