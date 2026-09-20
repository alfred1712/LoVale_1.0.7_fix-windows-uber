package com.example.lovale2.ui

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.lovale2.data.settings.AppSettings
import com.example.lovale2.data.settings.RideApp
import com.example.lovale2.data.settings.SettingsRepository
import com.example.lovale2.services.LoValeForegroundService
import com.example.lovale2.services.TripOverlayService
import com.example.lovale2.data.settings.OfferHistory
import com.example.lovale2.domain.evaluator
import com.example.lovale2.domain.classifyOffer
import com.example.lovale2.domain.VehicleCosts
import com.example.lovale2.domain.AutoFuelSettings
import com.example.lovale2.services.FuelPriceWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val journeyStore = com.example.lovale2.data.settings.JourneyStore.get(application)
    val journey = journeyStore.state
    private val sessionStore = com.example.lovale2.data.settings.SessionSummaryStore.get(application)
    val sessionSummary = sessionStore.state
    fun setAutoConfirm(enabled: Boolean) = viewModelScope.launch { repository.setAutoConfirmTrips(enabled) }
    fun saveHome(latitude: Double, longitude: Double, address: String = journey.value.homeAddress) {
        require(latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0)
        journeyStore.update { it.copy(homeAddress = com.example.lovale2.domain.cleanHomeAddress(address), homeLatitude = latitude, homeLongitude = longitude, enabled = true, message = "Casa guardada") }
        com.example.lovale2.services.JourneyLocationService.homeChanged()
    }
    fun saveHomeAddress(address: String) {
        journeyStore.update { it.copy(homeAddress = com.example.lovale2.domain.cleanHomeAddress(address)) }
    }
    fun prepareJourney() {
        journeyStore.update { it.copy(enabled = true) }
        com.example.lovale2.services.JourneyLocationService.start(getApplication())
    }
    fun finishJourney() = com.example.lovale2.services.JourneyLocationService.finish(getApplication())
    fun disableJourney() {
        finishJourney()
        journeyStore.update { it.copy(enabled = false) }
    }

    private val repository = SettingsRepository(application)
    private val history = OfferHistory(application)
    val offers = history.records.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun clearHistory() = viewModelScope.launch { history.clear() }
    fun setCompleted(key: String, value: Boolean) = viewModelScope.launch { history.setCompleted(key, value) }
    fun saveVehicle(value: VehicleCosts) = viewModelScope.launch {
        repository.saveVehicleCosts(value)
        FuelPriceWorker.schedule(getApplication(), repository.settingsFlow.first().autoFuel.enabled)
    }
    fun saveAutoFuel(value: AutoFuelSettings) = viewModelScope.launch {
        repository.configureAutoFuel(value)
        FuelPriceWorker.schedule(getApplication(), value.enabled, value.enabled)
    }
    fun refreshFuel() = FuelPriceWorker.schedule(getApplication(), settings.value.autoFuel.enabled, true)
    fun testOffer(text: String, destination: String = ""): String {
        val context = getApplication<Application>()
        if (!android.provider.Settings.canDrawOverlays(context)) return "Habilitá el permiso de ventana flotante."
        val active = settings.value
        val evaluator = active.evaluator()
        val data = evaluator.extraerDatosDeViaje(text, destination)
        val result = evaluator.evaluarViaje(data, active.excludedZones)
        val level = classifyOffer(data, result, active)
        val intent = Intent(context, TripOverlayService::class.java).apply {
            evaluator.estimarCostos(data, active.vehicleCosts)?.let { putExtra("EXTRA_ESTIMATED_NET", it.net) }
            putExtra("EXTRA_TEST", true)
            putExtra("EXTRA_OFFER_ID", "test-${System.nanoTime()}")
            putExtra("EXTRA_PROFITABILITY_LEVEL", level)
            putExtra("EXTRA_ZONE_LABEL", result.zonaDetectada)
            putExtra("EXTRA_RATE_KM", if (data.completeReading) result.tarifaPorKm else Double.NaN)
            putExtra("EXTRA_RATE_HOUR", if (data.completeReading) result.tarifaPorHora else Double.NaN)
            putExtra("EXTRA_MIN_KM", active.minRateByKm.replace(',', '.').toDoubleOrNull() ?: 0.0)
            putExtra("EXTRA_MIN_HOUR", active.minRateByHour.replace(',', '.').toDoubleOrNull() ?: 0.0)
            putExtra("EXTRA_PICKUP_EXCEEDED", (active.maxPickupDistance.replace(',', '.').toDoubleOrNull() ?: 0.0).let { it > 0 && data.pickupDistanceKm > it })
        }
        return try {
            ContextCompat.startForegroundService(context, intent)
            "Prueba mostrada durante 9 segundos. No se guarda en el historial."
        } catch (e: Exception) { "No se pudo mostrar la prueba: ${e.javaClass.simpleName}" }
    }

    val settings: StateFlow<AppSettings> = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    init {
        // El estado Activo es de sesión, no debe sobrevivir al cierre/reinicio de LoVale.
        // Al abrir la app siempre empieza pausada y el usuario decide cuándo monitorear.
        viewModelScope.launch {
            repository.setServiceActive(false)
            if (!journey.value.running) sessionStore.stop()
            FuelPriceWorker.schedule(getApplication(), repository.settingsFlow.first().autoFuel.enabled)
            getApplication<Application>().stopService(
                Intent(getApplication(), LoValeForegroundService::class.java)
            )
        }
    }

    fun toggleService(active: Boolean) {
        viewModelScope.launch { repository.setServiceActive(active) }

        val context = getApplication<Application>()
        val serviceIntent = Intent(context, LoValeForegroundService::class.java)
        if (active) {
            sessionStore.start()
            // Activating monitoring also arms the saved journey; never reset an ongoing total.
            if (journey.value.homeLatitude != null) prepareJourney()
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (_: Throwable) {
                sessionStore.stop()
                finishJourney()
                viewModelScope.launch { repository.setServiceActive(false) }
            }
        } else {
            if (!journey.value.running) sessionStore.stop()
            com.example.lovale2.services.MonitoringHealth.paused()
            com.example.lovale2.services.JourneyLocationService.pause(context)
            context.stopService(Intent(context, TripOverlayService::class.java))
            context.stopService(serviceIntent)
        }
    }

    fun setSelectedApp(app: RideApp) = viewModelScope.launch { repository.setSelectedApp(app) }
    fun setMinRateByHour(value: String) = viewModelScope.launch { repository.setMinRateByHour(value) }
    fun setMinRateByKm(value: String) = viewModelScope.launch { repository.setMinRateByKm(value) }
    fun setMaxPickupDistance(value: String) = viewModelScope.launch { repository.setMaxPickupDistance(value) }
    fun addExcludedZone(zone: String) = viewModelScope.launch { repository.addExcludedZone(zone) }
    fun removeExcludedZone(zone: String) = viewModelScope.launch { repository.removeExcludedZone(zone) }
}
