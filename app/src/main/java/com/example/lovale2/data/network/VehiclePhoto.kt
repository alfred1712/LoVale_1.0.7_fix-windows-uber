package com.example.lovale2.data.network

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.util.Locale

data class VehiclePhoto(val bitmap: Bitmap, val model: String, val credit: String, val source: String)

/** Only curated model names leave the device. No free-form vehicle text, GPS or trip data. */
object VehiclePhotos {
    private val models = listOf("Fiat Cronos", "Fiat Argo", "Fiat Siena", "Fiat Palio", "Fiat Uno",
        "Toyota Corolla", "Toyota Etios", "Toyota Yaris", "Toyota Hilux", "Toyota Corolla Cross",
        "Chevrolet Onix", "Chevrolet Prisma", "Chevrolet Cruze", "Chevrolet Corsa", "Chevrolet Spin",
        "Volkswagen Gol", "Volkswagen Voyage", "Volkswagen Polo", "Volkswagen Virtus", "Volkswagen Vento",
        "Renault Logan", "Renault Sandero", "Renault Fluence", "Renault Duster", "Renault Clio",
        "Peugeot 208", "Peugeot 207", "Peugeot 308", "Peugeot 408", "Citroën C3", "Citroën C4",
        "Ford Ka", "Ford Fiesta", "Ford Focus", "Ford EcoSport", "Nissan Versa", "Nissan Sentra",
        "Nissan March", "Honda City", "Honda Fit", "Honda Civic", "Hyundai Accent", "Kia Rio")
    private fun normalize(value: String) = java.text.Normalizer.normalize(value.lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]+"), " ").trim()
    fun modelFor(vehicle: String): String? {
        val value = normalize(vehicle)
        return models.sortedByDescending { it.length }.firstOrNull {
            val key = normalize(it)
            value == key || value.startsWith("$key ")
        }
    }
    private val mutex = Mutex()
    private var lastKey: String? = null
    private var lastAt = 0L
    private var lastPhoto: VehiclePhoto? = null
    private fun encoded(value: String) = URLEncoder.encode(value, "UTF-8")
    private fun request(url: String, max: Int): ByteArray {
        val response = Jsoup.connect(url).ignoreContentType(true).followRedirects(false)
            .timeout(4000).maxBodySize(max + 1)
            .userAgent("LoVale/1.0.9 (vehicle thumbnails; github.com/alfred1712/LoVale_1.0.7_fix-windows-uber)").execute()
        require(response.statusCode() == 200)
        return response.bodyAsBytes().also { require(it.size <= max) }
    }
    suspend fun load(context: Context, vehicle: String): VehiclePhoto? {
        val model = modelFor(vehicle) ?: return null
        return withContext(Dispatchers.IO) { mutex.withLock {
            if (lastKey == model && System.currentTimeMillis() - lastAt < 60_000) return@withLock lastPhoto
            val directory = File(context.cacheDir, "vehicle-photo").apply { mkdirs() }
            val metadata = File(directory, "metadata.json")
            val picture = File(directory, "thumbnail.jpg")
            fun read(): VehiclePhoto? {
                if (!metadata.exists() || !picture.exists()) return null
                val json = JsonParser.parseString(metadata.readText()).asJsonObject
                if (json["model"]?.asString != model) return null
                val bitmap = BitmapFactory.decodeFile(picture.path) ?: return null
                return VehiclePhoto(bitmap, model, json["credit"].asString, json["source"].asString)
            }
            val result = try {
                read() ?: run {
                    val page = JsonParser.parseString(String(request("https://en.wikipedia.org/w/api.php?action=query&format=json&formatversion=2&prop=pageimages&piprop=name&pilicense=free&titles=${encoded(model)}", 65536), Charsets.UTF_8))
                        .asJsonObject.getAsJsonObject("query").getAsJsonArray("pages")[0].asJsonObject
                    val file = page["pageimage"]?.asString ?: return@run null
                    val info = JsonParser.parseString(String(request("https://commons.wikimedia.org/w/api.php?action=query&format=json&formatversion=2&prop=imageinfo&iiprop=url%7Cextmetadata&iiurlwidth=320&titles=${encoded("File:$file")}", 131072), Charsets.UTF_8))
                        .asJsonObject.getAsJsonObject("query").getAsJsonArray("pages")[0].asJsonObject
                        .getAsJsonArray("imageinfo")?.get(0)?.asJsonObject ?: return@run null
                    val ext = info.getAsJsonObject("extmetadata")
                    fun field(name: String) = ext.getAsJsonObject(name)?.get("value")?.asString.orEmpty()
                    val license = field("LicenseShortName")
                    require(license.startsWith("CC BY") || license == "CC0" || license == "Public domain")
                    val author = Jsoup.parse(field("Artist")).text()
                    require(author.isNotBlank() && author.length <= 240)
                    val source = info["descriptionurl"].asString
                    val image = info["thumburl"]?.asString ?: return@run null
                    require(URI(source).scheme == "https" && URI(source).host == "commons.wikimedia.org")
                    require(URI(image).scheme == "https" && URI(image).host in setOf("upload.wikimedia.org", "thumb.wikimedia.org"))
                    val bytes = request(image, 524288)
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    require(bounds.outWidth in 1..1024 && bounds.outHeight in 1..1024)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@run null
                    val credit = "$author · $license"
                    // Single cached thumbnail keeps disk use bounded; metadata is written last.
                    metadata.delete()
                    picture.writeBytes(bytes)
                    metadata.writeText(com.google.gson.JsonObject().apply {
                        addProperty("model", model); addProperty("credit", credit); addProperty("source", source)
                    }.toString())
                    VehiclePhoto(bitmap, model, credit, source)
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { null }
            lastKey = model; lastAt = System.currentTimeMillis(); lastPhoto = result
            result
        } }
    }
}
