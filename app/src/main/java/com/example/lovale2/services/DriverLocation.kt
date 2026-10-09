package com.example.lovale2.services

import android.content.Context
import android.location.*
import android.os.*
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Ephemeral recent position; neither persisted nor uploaded for fuel lookups. */
object DriverLocation {
    @Volatile private var latest: Location? = null
    fun publish(location: Location) {
        if (valid(location)) latest = Location(location)
    }
    private fun valid(location: Location) = location.hasAccuracy() && location.accuracy in 0f..100f &&
        location.latitude.isFinite() && location.longitude.isFinite() &&
        SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos in 0L..120000000000L
    fun recent(): Location? = latest?.takeIf { valid(it) }?.let { Location(it) }
    suspend fun capture(context: Context): Location = withContext(Dispatchers.Main.immediate) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) throw SecurityException("Ubicación precisa requerida")
        recent()?.let { return@withContext it }
        withTimeout(30000) {
            suspendCancellableCoroutine { continuation ->
                val manager = context.getSystemService(LocationManager::class.java)
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (!valid(location) || SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos > 30000000000L) return
                        try { manager.removeUpdates(this) } catch (_: Exception) { }
                        publish(location)
                        if (continuation.isActive) continuation.resume(Location(location))
                    }
                    override fun onProviderDisabled(provider: String) = Unit
                    override fun onProviderEnabled(provider: String) = Unit
                    @Deprecated("Required for old Android")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                }
                continuation.invokeOnCancellation { try { manager.removeUpdates(listener) } catch (_: Exception) { } }
                try {
                    var providers = 0
                    for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                        if (manager.isProviderEnabled(provider)) {
                            manager.requestLocationUpdates(provider, 1000L, 0f, listener, Looper.getMainLooper()); providers++
                        }
                    }
                    check(providers > 0) { "Location disabled" }
                } catch (e: SecurityException) {
                    try { manager.removeUpdates(listener) } catch (_: Exception) { }
                    if (continuation.isActive) continuation.resumeWithException(e)
                } catch (e: Exception) {
                    try { manager.removeUpdates(listener) } catch (_: Exception) { }
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        }
    }
}
