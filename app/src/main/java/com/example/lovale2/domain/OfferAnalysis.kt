package com.example.lovale2.domain

import com.example.lovale2.data.settings.AppSettings

fun AppSettings.evaluator() = TripEvaluator(minRateByKm.replace(',', '.').toDoubleOrNull() ?: 950.0,
    minRateByHour.replace(',', '.').toDoubleOrNull() ?: 10000.0)

fun classifyOffer(data: DatosViaje, result: ResultadoEvaluacion, settings: AppSettings): String {
    if (!data.completeReading) return "INCOMPLETE"
    if (result.zonaDetectada != null) return "ZONE"
    val maxPickup = settings.maxPickupDistance.replace(',', '.').toDoubleOrNull() ?: 0.0
    if (maxPickup > 0 && data.pickupDistanceKm > maxPickup) return "RED"
    val km = settings.minRateByKm.replace(',', '.').toDoubleOrNull() ?: 0.0
    val hour = settings.minRateByHour.replace(',', '.').toDoubleOrNull() ?: 0.0
    val ratio = minOf(if (km > 0) result.tarifaPorKm / km else 1.0,
        if (hour > 0) result.tarifaPorHora / hour else 1.0)
    return when { ratio >= 1 -> "GREEN"; ratio >= .85 -> "YELLOW"; else -> "RED" }
}
