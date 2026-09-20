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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import com.example.lovale2.domain.VehicleCosts
import com.example.lovale2.domain.AutoFuelSettings
import com.example.lovale2.domain.FuelQuote
import com.google.gson.Gson

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
    val autoConfirmTrips: Boolean = true,
    val selectedApp: RideApp? = null,
    val minRateByHour: String = "10000",
    val minRateByKm: String = "950",
    val maxPickupDistance: String = "2",
    val excludedZones: List<String> = emptyList(),
    val vehicleCosts: VehicleCosts = VehicleCosts(),
    val autoFuel: AutoFuelSettings = AutoFuelSettings()
)

class SettingsRepository(private val context: Context) {
    companion object { private val activeInProcess = MutableStateFlow(false) }
    private val gson = Gson()
    private fun readAuto(prefs: Preferences): AutoFuelSettings = try {
        gson.fromJson(prefs[Keys.AUTO_FUEL], AutoFuelSettings::class.java)?.takeIf { it.valid } ?: AutoFuelSettings()
    } catch (_: Exception) { AutoFuelSettings() }

    private object Keys {
        val AUTO_CONFIRM = booleanPreferencesKey("auto_confirm_trips")
        val SERVICE_ACTIVE = booleanPreferencesKey("service_active")
        val SELECTED_APP = stringPreferencesKey("selected_ride_app")
        val MIN_RATE_HOUR = stringPreferencesKey("min_rate_hour")
        val MIN_RATE_KM = stringPreferencesKey("min_rate_km")
        val MAX_PICKUP = stringPreferencesKey("max_pickup")
        val EXCLUDED_ZONES = stringSetPreferencesKey("excluded_zones_key")
        val COSTS_ENABLED = booleanPreferencesKey("vehicle_costs_enabled")
        val VEHICLE = stringPreferencesKey("vehicle_model")
        val FUEL = stringPreferencesKey("vehicle_fuel")
        val CONSUMPTION = stringPreferencesKey("vehicle_consumption")
        val FUEL_PRICE = stringPreferencesKey("vehicle_fuel_price")
        val WEAR = stringPreferencesKey("vehicle_wear_percent")
        val PRICE_DATE = stringPreferencesKey("vehicle_price_date")
        val AUTO_FUEL = stringPreferencesKey("automatic_fuel_v1")
    }

    val settingsFlow: Flow<AppSettings> = combine(context.dataStore.data, activeInProcess) { prefs, active ->
        AppSettings(
            serviceActive = active,
            autoConfirmTrips = prefs[Keys.AUTO_CONFIRM] ?: true,
            selectedApp = RideApp.fromStored(prefs[Keys.SELECTED_APP]),
            minRateByHour = prefs[Keys.MIN_RATE_HOUR] ?: "10000",
            minRateByKm = prefs[Keys.MIN_RATE_KM] ?: "950",
            maxPickupDistance = prefs[Keys.MAX_PICKUP] ?: "2",
            excludedZones = (prefs[Keys.EXCLUDED_ZONES] ?: emptySet()).toList(),
            vehicleCosts = VehicleCosts(prefs[Keys.COSTS_ENABLED] ?: false,
                prefs[Keys.VEHICLE].orEmpty(), prefs[Keys.FUEL] ?: "Nafta",
                prefs[Keys.CONSUMPTION]?.toDoubleOrNull() ?: 8.0,
                prefs[Keys.FUEL_PRICE]?.toDoubleOrNull() ?: 0.0,
                prefs[Keys.WEAR]?.toDoubleOrNull() ?: 20.0,
                prefs[Keys.PRICE_DATE]?.toLongOrNull() ?: 0L),
            autoFuel = readAuto(prefs)
        )
    }

    suspend fun setServiceActive(active: Boolean) {
        if (!active && activeInProcess.value) com.example.lovale2.diagnostics.DiagnosticRecorder.stop("monitoreo pausado")
        activeInProcess.value = active
        context.dataStore.edit { it.remove(Keys.SERVICE_ACTIVE) }
    }
    suspend fun setAutoConfirmTrips(enabled: Boolean) = context.dataStore.edit { it[Keys.AUTO_CONFIRM] = enabled }
    suspend fun saveVehicleCosts(value: VehicleCosts) = context.dataStore.edit {
        require(!value.enabled || value.ready)
        val auto = readAuto(it)
        if (value.fuel != (it[Keys.FUEL] ?: "Nafta") || value.fuelPrice != (it[Keys.FUEL_PRICE]?.toDoubleOrNull() ?: 0.0)) {
            it[Keys.AUTO_FUEL] = gson.toJson(auto.copy(enabled = false, revision = java.util.UUID.randomUUID().toString(),
                station = "", sourceUrl = "", publishedAt = 0, stationDistanceKm = null, locatedAt = 0, status = "Referencia manual; actualización automática desactivada"))
        }
        it[Keys.COSTS_ENABLED] = value.enabled
        it[Keys.VEHICLE] = value.vehicle
        it[Keys.FUEL] = value.fuel
        it[Keys.CONSUMPTION] = value.consumptionPer100Km.toString()
        it[Keys.FUEL_PRICE] = value.fuelPrice.toString()
        it[Keys.WEAR] = value.wearPercent.toString()
        it[Keys.PRICE_DATE] = value.priceUpdatedAt.toString()
    }
    suspend fun configureAutoFuel(value: AutoFuelSettings) = context.dataStore.edit { prefs ->
        require(value.valid)
        val previous = readAuto(prefs)
        val changed = previous.brand != value.brand || previous.product != value.product ||
            previous.province != value.province || previous.locality != value.locality ||
            (value.enabled && (prefs[Keys.FUEL] ?: "Nafta") != value.selectedProduct?.fuel)
        val next = value.copy(revision = java.util.UUID.randomUUID().toString())
        prefs[Keys.AUTO_FUEL] = gson.toJson(if (changed) next.copy(checkedAt = 0, publishedAt = 0, station = "", sourceUrl = "", stationDistanceKm = null, locatedAt = 0, status = "Esperando consulta") else next)
        if (changed) {
            val fuel = requireNotNull(value.selectedProduct).fuel
            if ((prefs[Keys.FUEL] ?: "Nafta") != fuel) prefs[Keys.COSTS_ENABLED] = false
            prefs[Keys.FUEL] = fuel
            prefs[Keys.FUEL_PRICE] = "0.0"
            prefs[Keys.PRICE_DATE] = "0"
        }
    }

    suspend fun applyFuelQuote(request: AutoFuelSettings, quote: FuelQuote?, status: String) = context.dataStore.edit { prefs ->
        val current = readAuto(prefs)
        if (!current.enabled || current.revision != request.revision) return@edit
        val older = quote != null && com.example.lovale2.domain.isOlderFuelQuote(current, quote)
        val accepted = quote?.takeUnless { older }
        if (accepted != null) {
            prefs[Keys.FUEL_PRICE] = accepted.price.toString()
            prefs[Keys.PRICE_DATE] = accepted.publishedAt.toString()
        }
        prefs[Keys.AUTO_FUEL] = gson.toJson(current.copy(checkedAt = System.currentTimeMillis(),
            publishedAt = accepted?.publishedAt ?: current.publishedAt,
            station = accepted?.station ?: current.station, sourceUrl = accepted?.sourceUrl ?: current.sourceUrl,
            stationDistanceKm = accepted?.stationDistanceKm ?: current.stationDistanceKm,
            locatedAt = accepted?.locatedAt ?: current.locatedAt,
            status = if (older) "La fuente devolvió un dato anterior; se conserva el último" else status))
    }
    suspend fun setSelectedApp(app: RideApp?) {
        activeInProcess.value = false
        com.example.lovale2.diagnostics.DiagnosticRecorder.stop("cambio de plataforma")
        context.dataStore.edit { prefs ->
        prefs.remove(Keys.SERVICE_ACTIVE)
        if (app == null) prefs.remove(Keys.SELECTED_APP) else prefs[Keys.SELECTED_APP] = app.name
        }
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
