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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application)

    val settings: StateFlow<AppSettings> = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    init {
        // El estado Activo es de sesión, no debe sobrevivir al cierre/reinicio de LoVale.
        // Al abrir la app siempre empieza pausada y el usuario decide cuándo monitorear.
        viewModelScope.launch {
            repository.setServiceActive(false)
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
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (_: Throwable) {
                viewModelScope.launch { repository.setServiceActive(false) }
            }
        } else {
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
