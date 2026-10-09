package com.example.lovale2.domain

/** Referencias configuradas una vez; no representan gastos efectivamente pagados. */
data class VehicleCosts(
    val enabled: Boolean = false,
    val vehicle: String = "",
    val fuel: String = "Nafta",
    val consumptionPer100Km: Double = 8.0,
    val fuelPrice: Double = 0.0,
    val wearPercent: Double = 20.0,
    val priceUpdatedAt: Long = 0L
) {
    val ready: Boolean get() = enabled && vehicle.isNotBlank() && fuel in listOf("Nafta", "GNC", "Diésel") &&
        consumptionPer100Km.isFinite() && consumptionPer100Km in 0.1..50.0 &&
        fuelPrice.isFinite() && fuelPrice in 0.1..100000.0 && wearPercent.isFinite() && wearPercent in 0.0..100.0
}

data class CostEstimate(val fuel: Double, val wear: Double, val net: Double) {
    val total: Double get() = fuel + wear
}

fun estimateCosts(data: DatosViaje, config: VehicleCosts): CostEstimate? {
    if (!config.ready || !data.completeReading || !data.precio.isFinite() || data.precio <= 0 ||
        !data.distanciaKm.isFinite() || data.distanciaKm <= 0 || !data.pickupDistanceKm.isFinite() || data.pickupDistanceKm < 0) return null
    val fuel = (data.distanciaKm + data.pickupDistanceKm) * config.consumptionPer100Km / 100 * config.fuelPrice
    val wear = fuel * config.wearPercent / 100
    return CostEstimate(fuel, wear, data.precio - fuel - wear).takeIf { it.total.isFinite() && it.net.isFinite() }
}
