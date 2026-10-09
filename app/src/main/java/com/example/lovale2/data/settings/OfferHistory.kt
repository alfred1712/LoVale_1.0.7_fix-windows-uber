package com.example.lovale2.data.settings

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import com.example.lovale2.domain.CostEstimate
import com.example.lovale2.domain.VehicleCosts

private val Context.offerHistoryStore by preferencesDataStore("offer_history")

/** Solo métricas, sin direcciones, capturas ni texto OCR. Hasta 10.000 ofertas, 90 días. */
data class OfferRecord(val timestamp: Long, val platform: String, val price: Double,
    val rateKm: Double, val rateHour: Double,
    val level: String, val reason: String, val netBasis: Boolean = false,
    val id: String? = java.util.UUID.randomUUID().toString(),
    val totalKm: Double = 0.0, val totalMinutes: Double = 0.0,
    val costs: CostEstimate? = null, val vehicleSnapshot: VehicleCosts? = null,
    val acceptedAt: Long = 0L, val completedAt: Long = 0L, val completedAutomatically: Boolean = false,
    val reviewRequired: Boolean = false, val sessionId: Long = 0L,
    val targetKm: Double = 0.0, val targetHour: Double = 0.0, val surge: String? = "", val neighborhood: String? = null) {
    val key: String get() = id ?: "$timestamp|$platform|$price"
}

/** Retención acotada para comparativos: 90 días y hasta 10.000 registros. */
fun retainOffers(records: List<OfferRecord>, now: Long): List<OfferRecord> {
    return records.distinctBy { it.key }.filter { maxOf(it.timestamp, it.completedAt) >= now - 90L * 86400000 }
        .sortedByDescending { it.timestamp }.take(10000)
}

fun recordCompletion(records: List<OfferRecord>, key: String, automatic: Boolean, review: Boolean, now: Long) =
    records.map {
        if (it.key == key && it.completedAt == 0L) it.copy(completedAt = if (automatic) now else 0L,
            completedAutomatically = automatic, reviewRequired = !automatic && review) else it
    }

class OfferHistory(private val context: Context) {
    private val store = context.applicationContext.offerHistoryStore
    private val key = stringPreferencesKey("records")
    private val gson = Gson()
    private fun decode(value: String?): List<OfferRecord> = try {
        gson.fromJson(value ?: "[]", Array<OfferRecord>::class.java)?.toList().orEmpty()
    } catch (_: Exception) { emptyList() }
    val records = store.data.map { retainOffers(decode(it[key]), System.currentTimeMillis()) }.flowOn(Dispatchers.Default)
    suspend fun add(record: OfferRecord) { store.edit { it[key] = gson.toJson(retainOffers(listOf(record) + decode(it[key]), System.currentTimeMillis())) } }
    suspend fun setCompleted(recordKey: String, completed: Boolean) { store.edit { prefs ->
        val now = System.currentTimeMillis()
        prefs[key] = gson.toJson(retainOffers(decode(prefs[key]).map {
            if (it.key == recordKey) it.copy(completedAutomatically = false, reviewRequired = false, completedAt = if (completed) it.completedAt.takeIf { time -> time > 0 } ?: now else 0L) else it
        }, now))
    }; com.example.lovale2.services.LoValeWidget.refresh(context) }
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
    }; com.example.lovale2.services.LoValeWidget.refresh(context) }
    suspend fun updateEvaluation(recordKey: String, level: String, reason: String, neighborhood: String? = null) { store.edit { prefs ->
        prefs[key] = gson.toJson(decode(prefs[key]).map {
            if (it.key == recordKey) it.copy(level = level, neighborhood = neighborhood ?: it.neighborhood, reason = if (it.reason == "PICKUP_EXCEDIDO" && level != "ZONE") it.reason else reason) else it
        })
    }; com.example.lovale2.services.LoValeWidget.refresh(context) }
    suspend fun clear() { store.edit { it.remove(key) }; com.example.lovale2.services.LoValeWidget.refresh(context) }
}
