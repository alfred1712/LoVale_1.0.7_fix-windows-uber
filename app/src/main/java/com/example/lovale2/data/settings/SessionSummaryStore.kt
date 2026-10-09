package com.example.lovale2.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SessionSummary(val startedAt: Long = 0, val endedAt: Long = 0, val offers: Int = 0,
    val incompleteReads: Int = 0, val interrupted: Boolean = false)

/** Small local counters; incomplete reads are explicitly not a count of missed offers. */
class SessionSummaryStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("monitor_summary", Context.MODE_PRIVATE)
    private val previousStart = prefs.getLong("start", 0)
    private val previousEnd = prefs.getLong("end", 0)
    private val mutable = MutableStateFlow(SessionSummary(previousStart,
        if (previousStart > 0 && previousEnd == 0L) prefs.getLong("updated", previousStart) else previousEnd,
        prefs.getInt("offers", 0), prefs.getInt("incomplete", 0), previousStart > 0 && previousEnd == 0L))
    val state = mutable.asStateFlow()
    @Synchronized fun start() {
        if (mutable.value.startedAt > 0 && mutable.value.endedAt == 0L) return
        save(SessionSummary(startedAt = System.currentTimeMillis()))
    }
    @Synchronized fun stop() {
        if (mutable.value.startedAt > 0 && mutable.value.endedAt == 0L) save(mutable.value.copy(endedAt = System.currentTimeMillis()))
    }
    @Synchronized fun offer() {
        if (mutable.value.startedAt > 0 && mutable.value.endedAt == 0L) save(mutable.value.copy(offers = mutable.value.offers + 1))
    }
    @Synchronized fun incomplete() {
        if (mutable.value.startedAt > 0 && mutable.value.endedAt == 0L) save(mutable.value.copy(incompleteReads = mutable.value.incompleteReads + 1))
    }
    private fun save(next: SessionSummary) {
        prefs.edit().putLong("start", next.startedAt).putLong("end", next.endedAt)
            .putLong("updated", System.currentTimeMillis()).putInt("offers", next.offers).putInt("incomplete", next.incompleteReads).apply()
        mutable.value = next
    }
    companion object {
        @Volatile private var instance: SessionSummaryStore? = null
        fun get(context: Context) = instance ?: synchronized(this) {
            instance ?: SessionSummaryStore(context.applicationContext).also { instance = it }
        }
    }
}
