package com.example.lovale2.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class JourneyState(val enabled: Boolean = false, val meters: Double = 0.0,
    val startedAt: Long = 0, val endedAt: Long = 0, val running: Boolean = false,
    val waiting: Boolean = false, val pauseUntil: Long = 0, val partial: Boolean = false, val message: String = "Contador desactivado",
    val homeAddress: String = "", val homeLatitude: Double? = null, val homeLongitude: Double? = null, val departedHome: Boolean = false)

/** Stores totals locally, never GPS points. A process restart is an interruption. */
class JourneyStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("journey_metrics", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(JourneyState(
        pauseUntil = prefs.getLong("pauseUntil", 0), enabled = prefs.getBoolean("enabled", false), meters = Double.fromBits(prefs.getLong("meters", 0)),
        startedAt = prefs.getLong("started", 0), endedAt = prefs.getLong("ended", 0),
        partial = prefs.getBoolean("partial", false) || prefs.getBoolean("running", false),
        message = if (prefs.getBoolean("running", false)) "Interrumpido · reanudá el contador" else prefs.getString("message", null) ?: "Contador desactivado",
        homeAddress = prefs.getString("homeAddress", "").orEmpty(),
        homeLatitude = if (prefs.contains("homeLat")) Double.fromBits(prefs.getLong("homeLat", 0)) else null,
        homeLongitude = if (prefs.contains("homeLon")) Double.fromBits(prefs.getLong("homeLon", 0)) else null,
        departedHome = prefs.getBoolean("departed", false)))
    val state = mutable.asStateFlow()
    @Synchronized fun update(change: (JourneyState) -> JourneyState) {
        val next = change(mutable.value)
        if (next == mutable.value) return
        val editor = prefs.edit().putString("homeAddress", next.homeAddress).putBoolean("enabled", next.enabled).putLong("meters", next.meters.toBits())
            .putLong("started", next.startedAt).putLong("ended", next.endedAt)
            .putLong("pauseUntil", next.pauseUntil).putBoolean("running", next.running).putBoolean("partial", next.partial)
            .putString("message", next.message).putBoolean("departed", next.departedHome)
        if (next.homeLatitude != null && next.homeLongitude != null) {
            editor.putLong("homeLat", next.homeLatitude.toBits()).putLong("homeLon", next.homeLongitude.toBits())
        } else editor.remove("homeLat").remove("homeLon")
        editor.apply()
        mutable.value = next
    }
    companion object {
        @Volatile private var instance: JourneyStore? = null
        fun get(context: Context): JourneyStore = instance ?: synchronized(this) {
            instance ?: JourneyStore(context.applicationContext).also { instance = it }
        }
    }
}
