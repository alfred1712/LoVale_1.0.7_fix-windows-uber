package com.example.lovale2.services

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.lovale2.data.settings.SettingsRepository
import com.example.lovale2.data.network.NearbyFuelSource
import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class FuelPriceWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val repository = SettingsRepository(applicationContext)
        val settings = repository.settingsFlow.first().autoFuel
        if (!settings.enabled || !settings.valid) return@withContext Result.success()
        try {
            if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED || DriverLocation.recent() == null) {
                if (!isStopped) repository.applyFuelQuote(settings, null, "Ubicación pendiente: abrí Combustible y actualizá. Se conserva el precio anterior")
                return@withContext Result.success()
            }
            val json = NearbyFuelSource.download(settings)
            val position = DriverLocation.recent()
            if (position == null || ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                if (!isStopped) repository.applyFuelQuote(settings, null, "Ubicación vencida; se conserva el precio anterior")
                return@withContext Result.success()
            }
            val now = System.currentTimeMillis()
            val quote = NearbyFuelSource.parse(json, settings, position.latitude, position.longitude, now)?.copy(
                locatedAt = now - (SystemClock.elapsedRealtimeNanos() - position.elapsedRealtimeNanos) / 1000000)
            if (!isStopped) repository.applyFuelQuote(settings, quote, if (quote == null) "Sin precio vigente en la estación más cercana (hasta 50 km); se conserva el anterior" else "Precio de estación cercana actualizado")
            Log.d("LoVale", "FUEL consulta terminada valid=" + (quote != null))
            Result.success()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            Log.w("LoVale", "FUEL consulta falló: ${e.javaClass.simpleName}")
            if (!isStopped) repository.applyFuelQuote(settings, null, if (e is SecurityException) "Fuente sin conexión segura; ajustá el precio manualmente. Se conserva el anterior" else "No se pudo consultar; se conserva la referencia anterior")
            Result.success() // Reintento en la siguiente ventana; no generar bucles de red.
        }
    }

    companion object {
        fun schedule(context: Context, enabled: Boolean, refreshNow: Boolean = false) {
            val manager = WorkManager.getInstance(context)
            if (!enabled) {
                manager.cancelUniqueWork("fuel-price-12h")
                manager.cancelUniqueWork("fuel-price-now")
                return
            }
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            manager.enqueueUniquePeriodicWork("fuel-price-12h", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<FuelPriceWorker>(12, TimeUnit.HOURS)
                    .setInitialDelay(12, TimeUnit.HOURS).setConstraints(constraints).build())
            if (refreshNow) manager.enqueueUniqueWork("fuel-price-now", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<FuelPriceWorker>().setConstraints(constraints).build())
        }
    }
}
