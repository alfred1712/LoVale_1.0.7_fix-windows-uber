package com.example.lovale2.domain

import com.example.lovale2.data.settings.OfferRecord
import java.util.Calendar
import java.util.TimeZone

data class DailySummary(val trips: Int, val gross: Double, val expenses: Double,
    val net: Double, val missingCosts: Int)

fun dailySummary(records: List<OfferRecord>, now: Long = System.currentTimeMillis(),
                 zone: TimeZone = TimeZone.getDefault()): DailySummary {
    val calendar = Calendar.getInstance(zone).apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val start = calendar.timeInMillis
    calendar.add(Calendar.DAY_OF_MONTH, 1)
    val end = calendar.timeInMillis
    val trips = records.filter { it.completedAt > 0 && it.completedAt in start until end }.distinctBy { it.key }
    val known = trips.filter { it.costs != null }
    return DailySummary(trips.size, trips.sumOf { it.price }, known.sumOf { it.costs?.total ?: 0.0 },
        known.sumOf { it.costs?.net ?: 0.0 }, trips.size - known.size)
}
