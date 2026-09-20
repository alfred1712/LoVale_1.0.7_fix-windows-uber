package com.example.lovale2.domain


data class FuelProduct(val label: String, val slug: String, val category: String, val fuel: String)
object FuelCatalog {
    val brands = listOf("YPF", "AXION", "SHELL", "PUMA")
    fun products(brand: String): List<FuelProduct> {
        val premium = when (brand) {
            "YPF" -> "Infinia" to "nafta-infinia"
            "AXION" -> "Quantium" to "nafta-quantium"
            "SHELL" -> "V-Power Nafta" to "nafta-v-power"
            else -> "Nafta Premium" to "nafta-premium"
        }
        val diesel = when (brand) { "YPF" -> "infinia-diesel"; "AXION" -> "quantium-diesel"; "SHELL" -> "v-power-diesel"; else -> "gasoil-grado-3" }
        return listOf(FuelProduct("Súper", "nafta-super", "nafta-super", "Nafta"),
            FuelProduct(premium.first, premium.second, "nafta-premium", "Nafta"),
            FuelProduct("GNC", "gnc", "gnc", "GNC"),
            FuelProduct("Diésel grado 2", "gasoil-grado-2", "gasoil-grado-2", "Diésel"),
            FuelProduct("Diésel premium", diesel, "gasoil-grado-3", "Diésel"))
    }
}

data class AutoFuelSettings(
    val enabled: Boolean = false, val brand: String = "YPF", val product: String = "nafta-super",
    val province: String = "capital-federal", val locality: String = "capital-federal",
    val revision: String = "", val checkedAt: Long = 0L, val publishedAt: Long = 0L,
    val station: String = "", val sourceUrl: String = "", val status: String = "Sin consultar",
    val stationDistanceKm: Double? = null, val locatedAt: Long = 0L
) {
    val selectedProduct: FuelProduct? get() = FuelCatalog.products(brand).firstOrNull { it.slug == product }
    val valid: Boolean get() = brand in FuelCatalog.brands && selectedProduct != null &&
        Regex("[a-z0-9]+(?:-[a-z0-9]+)*").matches(province) && Regex("[a-z0-9]+(?:-[a-z0-9]+)*").matches(locality)
}

data class FuelQuote(val price: Double, val publishedAt: Long, val station: String, val sourceUrl: String,
    val stationDistanceKm: Double? = null, val locatedAt: Long = 0L)

fun isOlderFuelQuote(current: AutoFuelSettings, quote: FuelQuote): Boolean =
    (quote.station == current.station && quote.sourceUrl == current.sourceUrl && quote.publishedAt < current.publishedAt) ||
        (quote.locatedAt > 0 && quote.locatedAt < current.locatedAt)
