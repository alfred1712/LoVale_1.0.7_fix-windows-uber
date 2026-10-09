package com.example.lovale2.data.network

import com.example.lovale2.domain.*
import com.google.gson.JsonParser
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Full brand/product result set; driver coordinates never leave the device. */
object NearbyFuelSource {
    const val SOURCE = "https://datos.energia.gob.ar/dataset/1c181390-5045-475e-94dc-410429be4b17"
    // Never follow an HTTPS-to-HTTP downgrade, even for public prices.
    private const val API = "https://datos.energia.gob.ar/api/3/action/datastore_search"
    private const val RESOURCE = "80ac25de-a44a-4445-9215-090cf55cfda5"
    private fun brand(settings: AutoFuelSettings) = if (settings.brand == "SHELL") "SHELL C.A.P.S.A." else settings.brand
    private fun product(settings: AutoFuelSettings) = when (settings.selectedProduct?.category) {
        "nafta-super" -> 2; "nafta-premium" -> 3; "gnc" -> 6; "gasoil-grado-2" -> 19; "gasoil-grado-3" -> 21
        else -> error("Combustible inválido")
    }
    fun url(settings: AutoFuelSettings): String {
        require(settings.valid)
        val filters = "{\"empresabandera\":\"${brand(settings)}\",\"idproducto\":\"${product(settings)}\"}"
        val fields = "idempresa,empresa,direccion,localidad,provincia,precio,fecha_vigencia,empresabandera,idproducto,latitud,longitud,idtipohorario"
        return "$API?resource_id=$RESOURCE&limit=10000&fields=$fields&filters=${URLEncoder.encode(filters, "UTF-8")}"
    }
    fun download(settings: AutoFuelSettings): String {
        val response = Jsoup.connect(url(settings)).ignoreContentType(true).userAgent("LoVale/1.0")
            .timeout(25000).maxBodySize(4 * 1024 * 1024).followRedirects(false).execute()
        if (response.statusCode() in 300..399) throw SecurityException("La fuente redirige la conexión; precio sin verificar")
        require(response.statusCode() == 200) { "Fuel API HTTP ${response.statusCode()}; redirect=${response.header("Location")}" }
        return response.body()
    }
    fun parse(json: String, settings: AutoFuelSettings, latitude: Double, longitude: Double, now: Long): FuelQuote? {
        require(settings.valid && latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0)
        val root = JsonParser.parseString(json).asJsonObject
        require(root.get("success")?.asBoolean == true)
        val result = root.getAsJsonObject("result")
        val records = result.getAsJsonArray("records")
        require(result.get("total").asInt == records.size()) { "Incomplete station list" }
        val dateParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).apply {
            isLenient = false; timeZone = TimeZone.getTimeZone("America/Argentina/Buenos_Aires")
        }
        data class Row(val id: String, val price: Double, val date: Long, val distance: Double, val station: String, val hour: Int)
        val driver = JourneyFix(latitude, longitude, 0.0, 0)
        val rows = records.mapNotNull { item ->
            try {
                val row = item.asJsonObject
                if (row.get("empresabandera").asString != brand(settings) || row.get("idproducto").asInt != product(settings)) return@mapNotNull null
                val lat = row.get("latitud").asDouble
                val lon = row.get("longitud").asDouble
                if (!lat.isFinite() || !lon.isFinite() || lat !in -56.0..-21.0 || lon !in -74.0..-53.0) return@mapNotNull null
                val distance = distanceMeters(driver, JourneyFix(lat, lon, 0.0, 0)) / 1000
                if (distance > 50) return@mapNotNull null
                val price = row.get("precio").asDouble
                if (!price.isFinite() || price !in 1.0..100000.0) return@mapNotNull null
                val date = dateParser.parse(row.get("fecha_vigencia").asString.replace(' ', 'T'))?.time ?: return@mapNotNull null
                if (date > now) return@mapNotNull null
                Row(row.get("idempresa").asString, price, date, distance,
                    "${row.get("direccion").asString} · ${row.get("localidad").asString}".take(220), row.get("idtipohorario").asInt)
            } catch (_: Exception) { null }
        }
        // Pick proximity before freshness: never silently substitute a farther station.
        val nearest = rows.minWithOrNull(compareBy<Row> { it.distance }.thenBy { it.id }) ?: return null
        val current = rows.filter { it.id == nearest.id }.groupBy { it.hour }.values.map { it.maxBy { row -> row.date } }
        if (current.any { now - it.date > 90L * 86400000 }) return null
        val chosen = current.maxBy { it.price }
        return FuelQuote(chosen.price, chosen.date, chosen.station, SOURCE, nearest.distance)
    }
}
