package com.example.lovale2.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lo_vale_settings")

enum class RideApp(val label: String, val packageName: String) {
    UBER("Uber", "com.ubercab.driver"),
    CABIFY("Cabify", "com.cabify.driver"),
    DIDI("DiDi", "com.didiglobal.driver");

    companion object {
        fun fromStored(value: String?): RideApp? = entries.firstOrNull { it.name == value }
    }
}

data class AppSettings(
    val serviceActive: Boolean = false,
    val selectedApp: RideApp? = null,
    val minRateByHour: String = "10000",
    val minRateByKm: String = "950",
    val maxPickupDistance: String = "2",
    val excludedZones: List<String> = emptyList()
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val SERVICE_ACTIVE = booleanPreferencesKey("service_active")
        val SELECTED_APP = stringPreferencesKey("selected_ride_app")
        val MIN_RATE_HOUR = stringPreferencesKey("min_rate_hour")
        val MIN_RATE_KM = stringPreferencesKey("min_rate_km")
        val MAX_PICKUP = stringPreferencesKey("max_pickup")
        val EXCLUDED_ZONES = stringSetPreferencesKey("excluded_zones_key")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            serviceActive = prefs[Keys.SERVICE_ACTIVE] ?: false,
            selectedApp = RideApp.fromStored(prefs[Keys.SELECTED_APP]),
            minRateByHour = prefs[Keys.MIN_RATE_HOUR] ?: "10000",
            minRateByKm = prefs[Keys.MIN_RATE_KM] ?: "950",
            maxPickupDistance = prefs[Keys.MAX_PICKUP] ?: "2",
            excludedZones = (prefs[Keys.EXCLUDED_ZONES] ?: emptySet()).toList()
        )
    }

    suspend fun setServiceActive(active: Boolean) = context.dataStore.edit { it[Keys.SERVICE_ACTIVE] = active }
    suspend fun setSelectedApp(app: RideApp?) = context.dataStore.edit { prefs ->
        if (app == null) prefs.remove(Keys.SELECTED_APP) else prefs[Keys.SELECTED_APP] = app.name
    }
    suspend fun setMinRateByHour(value: String) = context.dataStore.edit { it[Keys.MIN_RATE_HOUR] = value }
    suspend fun setMinRateByKm(value: String) = context.dataStore.edit { it[Keys.MIN_RATE_KM] = value }
    suspend fun setMaxPickupDistance(value: String) = context.dataStore.edit { it[Keys.MAX_PICKUP] = value }

    suspend fun addExcludedZone(zone: String) = context.dataStore.edit { prefs ->
        prefs[Keys.EXCLUDED_ZONES] = (prefs[Keys.EXCLUDED_ZONES] ?: emptySet()) + zone
    }

    suspend fun removeExcludedZone(zone: String) = context.dataStore.edit { prefs ->
        prefs[Keys.EXCLUDED_ZONES] = (prefs[Keys.EXCLUDED_ZONES] ?: emptySet()) - zone
    }
}
