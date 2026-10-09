package com.example.lovale2.domain

import com.example.lovale2.data.settings.OfferRecord
import java.util.Calendar
import java.util.Locale

data class OfferStatistics(val offers: Int, val approved: Int, val near: Int, val rejected: Int,
    val accepted: Int, val completed: Int, val rateKm: Double, val rateHour: Double,
    val gross: Double, val net: Double, val missingCosts: Int, val belowTargetGap: Double)

fun offerStatistics(records: List<OfferRecord>): OfferStatistics {
    val unique = records.distinctBy { it.key }
    val valid = unique.filter { it.price.isFinite() && it.price > 0 && it.totalKm.isFinite() && it.totalKm > 0 && it.totalMinutes.isFinite() && it.totalMinutes > 0 }
    val done = unique.filter { it.completedAt > 0 }
    fun ratio(divisor: Double, multiplier: Double = 1.0) = if (divisor > 0) valid.sumOf { it.price } * multiplier / divisor else 0.0
    return OfferStatistics(unique.size, unique.count { it.level == "GREEN" }, unique.count { it.level == "YELLOW" },
        unique.count { it.level in listOf("RED", "ZONE") }, unique.count { it.acceptedAt > 0 }, done.size,
        ratio(valid.sumOf { it.totalKm }), ratio(valid.sumOf { it.totalMinutes }, 60.0),
        done.sumOf { it.price }, done.sumOf { it.costs?.net ?: 0.0 }, done.count { it.costs == null },
        valid.filter { it.level in listOf("RED", "ZONE") && it.acceptedAt == 0L && it.completedAt == 0L }.sumOf {
            (maxOf(it.targetKm * it.totalKm, it.targetHour * it.totalMinutes / 60) - it.price).coerceAtLeast(0.0)
        })
}
fun periodStart(now: Long, week: Boolean = false): Long = Calendar.getInstance().apply {
    timeInMillis = now
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    if (week) add(Calendar.DAY_OF_MONTH, -((get(Calendar.DAY_OF_WEEK) + 5) % 7))
}.timeInMillis

fun csvCell(value: String): String {
    val safe = value.replace(Regex("[\\r\\n\\t]"), " ")
    val protected = if (safe.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) "'$safe" else safe
    return "\"${protected.replace("\"", "\"\"")}\""
}
/** UTF-8 BOM and semicolon delimiter for Excel, amounts use decimal comma without grouping. */
fun offersCsv(records: List<OfferRecord>): String = buildString {
    append('\uFEFF')
    append("fecha_oferta;plataforma;importe;km_totales;min_totales;precio_km;precio_hora;clasificacion;gastos_estimados_oferta;neto_estimado_oferta;dinamica;turno\r\n")
    val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
    fun number(value: Double) = if (value.isFinite()) String.format(Locale.ROOT, "%.2f", value).replace('.', ',') else ""
    records.distinctBy { it.key }.forEach { r ->
        append(listOf(csvCell(date.format(java.util.Date(r.timestamp))), csvCell(r.platform), number(r.price), number(r.totalKm), number(r.totalMinutes),
            number(r.rateKm), number(r.rateHour), csvCell(r.level),
            r.costs?.let { number(it.total) }.orEmpty(), r.costs?.let { number(it.net) }.orEmpty(), csvCell(r.surge.orEmpty()), r.sessionId.toString()).joinToString(";"))
        append("\r\n")
    }
}
