package com.example.lovale2.domain

import kotlin.math.*

data class JourneyFix(val latitude: Double, val longitude: Double, val accuracy: Double,
    val elapsedMillis: Long, val speed: Double? = null)

fun usableFix(fix: JourneyFix, nowElapsedMillis: Long) = fix.latitude.isFinite() && fix.longitude.isFinite() &&
    fix.latitude in -90.0..90.0 && fix.longitude in -180.0..180.0 && fix.accuracy.isFinite() &&
    fix.accuracy in 0.0..40.0 && nowElapsedMillis - fix.elapsedMillis in 0L..30000L

fun distanceMeters(a: JourneyFix, b: JourneyFix): Double {
    val lat = Math.toRadians(b.latitude - a.latitude)
    val lon = Math.toRadians(b.longitude - a.longitude)
    val h = sin(lat / 2).pow(2) + cos(Math.toRadians(a.latitude)) *
        cos(Math.toRadians(b.latitude)) * sin(lon / 2).pow(2)
    return 6371000.0 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
}

/** Only the last accepted fix lives in memory; no route is stored. */
class JourneyDistance {
    private var anchor: JourneyFix? = null
    private var lastSeen: Long? = null
    var interrupted = false
        private set

    fun add(fix: JourneyFix, nowElapsedMillis: Long): Double {
        if (!usableFix(fix, nowElapsedMillis)) return 0.0
        val seen = lastSeen
        if (seen != null && fix.elapsedMillis <= seen) return 0.0
        lastSeen = fix.elapsedMillis
        val previous = anchor
        if (previous == null) { anchor = fix; return 0.0 }
        val seconds = (fix.elapsedMillis - previous.elapsedMillis) / 1000.0
        if (seconds <= 0) return 0.0
        if (seen != null && fix.elapsedMillis - seen > 90000) { interrupted = true; anchor = fix; return 0.0 }
        val distance = distanceMeters(previous, fix)
        if (distance / seconds > 55) return 0.0
        val noise = max(8.0, (previous.accuracy + fix.accuracy) / 2)
        if (distance < noise || (fix.speed != null && fix.speed < 0.8 && distance < 30)) return 0.0
        anchor = fix
        return distance
    }
}

class HomeArrival(private val home: JourneyFix, alreadyDeparted: Boolean = false) {
    var departed = alreadyDeparted
        private set
    private var arrivalAt: Long? = null
    private var previous: JourneyFix? = null
    fun add(fix: JourneyFix, now: Long): Boolean {
        if (!fix.latitude.isFinite() || !fix.longitude.isFinite() || fix.accuracy !in 0.0..80.0 || now - fix.elapsedMillis !in 0L..30000L) return false
        val old = previous
        if (old != null && fix.elapsedMillis <= old.elapsedMillis) return false
        if (old != null && fix.elapsedMillis - old.elapsedMillis > 90000) arrivalAt = null
        val radius = distanceMeters(home, fix)
        if (radius > 300) departed = true
        val stationary = fix.speed?.let { it.isFinite() && it < 1.5 } ?: (old != null && distanceMeters(old, fix) < 15)
        previous = fix
        if (!departed || radius > 100 || !stationary) { arrivalAt = null; return false }
        val began = arrivalAt ?: fix.elapsedMillis.also { arrivalAt = it }
        return fix.elapsedMillis - began >= 5 * 60000L
    }
}

/** Plain local reference, never sent to a geocoding service. */
fun cleanHomeAddress(value: String) = value.filterNot { it.isISOControl() }.trim().take(160)
