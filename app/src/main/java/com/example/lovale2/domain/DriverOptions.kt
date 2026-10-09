package com.example.lovale2.domain

import com.example.lovale2.data.settings.AppSettings
import java.util.Calendar

data class TimeRule(val start: Int = 0, val end: Int = 0, val minKm: Double = 0.0, val minHour: Double = 0.0) {
    fun contains(minute: Int) = start != end && if (start < end) minute in start until end else minute >= start || minute < end
}
data class DriverOptions(
    val dailyGoal: Double = 0.0, val weeklyGoal: Double = 0.0, val netGoal: Boolean = false,
    val sound: Boolean = false, val vibration: Boolean = false,
    val quietEnabled: Boolean = false, val quietStart: Int = 0, val quietEnd: Int = 0,
    val timedRates: Boolean = false, val peak: TimeRule = TimeRule(420, 600, 1200.0, 15000.0),
    val night: TimeRule = TimeRule(1320, 360, 1400.0, 18000.0)
) {
    fun validate(): DriverOptions {
        require(listOf(dailyGoal, weeklyGoal, peak.minKm, peak.minHour, night.minKm, night.minHour).all { it.isFinite() && it in 0.0..100_000_000.0 })
        require(listOf(quietStart, quietEnd, peak.start, peak.end, night.start, night.end).all { it in 0..1439 })
        require(!quietEnabled || quietStart != quietEnd)
        require(!timedRates || (peak.start != peak.end && night.start != night.end))
        return this
    }
    fun quietAt(minute: Int = minuteOfDay()) = quietEnabled && TimeRule(quietStart, quietEnd).contains(minute)
    fun effective(settings: AppSettings, minute: Int = minuteOfDay()): AppSettings {
        if (!timedRates) return settings
        val rules = listOf(peak, night).filter { it.contains(minute) }
        if (rules.isEmpty()) return settings
        // If windows overlap, use the stricter minimum for each criterion.
        return settings.copy(minRateByKm = rules.maxOf { it.minKm }.toString(), minRateByHour = rules.maxOf { it.minHour }.toString())
    }
}
fun minuteOfDay(now: Long = System.currentTimeMillis()): Int = Calendar.getInstance().run { timeInMillis = now; get(Calendar.HOUR_OF_DAY) * 60 + get(Calendar.MINUTE) }
fun parseClock(value: String): Int? = Regex("^(\\d{2}):(\\d{2})$").matchEntire(value)?.let {
    val h = it.groupValues[1].toInt(); val m = it.groupValues[2].toInt()
    if (h in 0..23 && m in 0..59) h * 60 + m else null
}
fun clockLabel(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)

/** A displayed total already includes dynamic fare; multipliers are informational only. */
fun surgeLabel(text: String): String {
    val match = Regex("(?i)(?<![\\d.,])([1-9](?:[.,]\\d{1,2})?)\\s*[x×]\\b|\\b[x×]\\s*([1-9](?:[.,]\\d{1,2})?)(?![\\d.,])").find(text)
    val multiplier = match?.let { it.groupValues[1].ifBlank { it.groupValues[2] }.replace(',', '.').toDoubleOrNull() }
    return when {
        multiplier != null && multiplier in 1.0..9.99 -> "Dinámica ${multiplier}x · total mostrado"
        Regex("(?i)turbo|boost|tarifa\\s+base\\s+din[aá]mica|tarifa\\s+din[aá]mica").containsMatchIn(text) -> "Promo/dinámica · total mostrado"
        else -> ""
    }
}
