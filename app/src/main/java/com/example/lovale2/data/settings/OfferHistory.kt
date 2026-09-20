package com.example.lovale2.data.settings

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import kotlinx.coroutines.flow.map
import com.example.lovale2.domain.CostEstimate
import com.example.lovale2.domain.VehicleCosts

private val Context.offerHistoryStore by preferencesDataStore("offer_history")

/** Solo métricas, sin direcciones, capturas ni texto OCR. Máximo 200 ofertas. */
data class OfferRecord(val timestamp: Long, val platform: String, val price: Double,
    val rateKm: Double, val rateHour: Double,
    val level: String, val reason: String, val netBasis: Boolean = false,
    val id: String? = java.util.UUID.randomUUID().toString(),
    val totalKm: Double = 0.0, val totalMinutes: Double = 0.0,
    val costs: CostEstimate? = null, val vehicleSnapshot: VehicleCosts? = null,
    val acceptedAt: Long = 0L, val completedAt: Long = 0L, val completedAutomatically: Boolean = false,
    val reviewRequired: Boolean = false) {
    val key: String get() = id ?: "$timestamp|$platform|$price"
}

/** Conserva viajes confirmados 31 días, además de las últimas 200 ofertas. */
fun retainOffers(records: List<OfferRecord>, now: Long): List<OfferRecord> {
    val confirmed = records.filter { it.completedAt > 0 && it.completedAt >= now - 31L * 86400000 }
    val pending = records.filter { it.completedAt == 0L }.take(200)
    return (confirmed + pending).sortedByDescending { it.timestamp }
}

fun recordCompletion(records: List<OfferRecord>, key: String, automatic: Boolean, review: Boolean, now: Long) =
    records.map {
        if (it.key == key && it.completedAt == 0L) it.copy(completedAt = if (automatic) now else 0L,
            completedAutomatically = automatic, reviewRequired = !automatic && review) else it
    }

class OfferHistory(context: Context) {
    private val store = context.applicationContext.offerHistoryStore
    private val key = stringPreferencesKey("records")
    private val gson = Gson()
    private fun decode(value: String?): List<OfferRecord> = try {
        gson.fromJson(value ?: "[]", Array<OfferRecord>::class.java)?.toList().orEmpty()
    } catch (_: Exception) { emptyList() }
    val records = store.data.map { retainOffers(decode(it[key]), System.currentTimeMillis()) }
    suspend fun add(record: OfferRecord) { store.edit { it[key] = gson.toJson(retainOffers(listOf(record) + decode(it[key]), System.currentTimeMillis())) } }
    suspend fun setCompleted(recordKey: String, completed: Boolean) { store.edit { prefs ->
        val now = System.currentTimeMillis()
        prefs[key] = gson.toJson(retainOffers(decode(prefs[key]).map {
            if (it.key == recordKey) it.copy(completedAutomatically = false, reviewRequired = false, completedAt = if (completed) it.completedAt.takeIf { time -> time > 0 } ?: now else 0L) else it
        }, now))
    } }
    suspend fun applyCompletion(recordKey: String, automatic: Boolean, review: Boolean, accepted: Boolean = false, finalPrice: Double? = null) { store.edit { prefs ->
        val now = System.currentTimeMillis()
        prefs[key] = gson.toJson(retainOffers(recordCompletion(decode(prefs[key]).map { record ->
            if (record.key != recordKey) record else {
                val fare = finalPrice?.takeIf { automatic && it.isFinite() && it > 0 } ?: record.price
                record.copy(acceptedAt = if (accepted && record.acceptedAt == 0L) now else record.acceptedAt,
                    price = fare, rateKm = if (record.totalKm > 0) fare / record.totalKm else record.rateKm,
                    rateHour = if (record.totalMinutes > 0) fare * 60 / record.totalMinutes else record.rateHour,
                    costs = record.costs?.let { it.copy(net = fare - it.total) })
            }
        }, recordKey, automatic, review, now), now))
    } }
    suspend fun updateEvaluation(recordKey: String, level: String, reason: String) { store.edit { prefs ->
        prefs[key] = gson.toJson(decode(prefs[key]).map {
            if (it.key == recordKey) it.copy(level = level, reason = if (it.reason == "PICKUP_EXCEDIDO" && level != "ZONE") it.reason else reason) else it
        })
    } }
    suspend fun clear() { store.edit { it.remove(key) } }
}
