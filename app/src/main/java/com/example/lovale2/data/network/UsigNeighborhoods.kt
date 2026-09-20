package com.example.lovale2.data.network

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.Locale

/** Only explicit CABA street/number addresses. Never sends GPS, screenshots or whole offers. */
class UsigNeighborhoods {
    private data class Entry(val zone: String?, val at: Long)
    private val cache = linkedMapOf<String, Entry>()
    private val mutex = Mutex()
    fun cached(address: String): String? = cache[url(address)]?.takeIf {
        System.currentTimeMillis() - it.at in 0 until 86_400_000L
    }?.zone
    suspend fun resolve(address: String, stillRelevant: () -> Boolean = { true }): String? {
        val url = url(address) ?: return null
        return mutex.withLock {
            if (!stillRelevant()) return@withLock null
            val now = System.currentTimeMillis()
            cache[url]?.takeIf { now - it.at in 0 until (if (it.zone == null) 60_000L else 86_400_000L) }?.let { return@withLock it.zone }
            val zone = withContext(Dispatchers.IO) {
                try { fetch(url) } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { null }
            }
            cache[url] = Entry(zone, System.currentTimeMillis())
            while (cache.size > 128) cache.remove(cache.keys.first())
            zone
        }
    }

    companion object {
        // Strict municipality marker prevents resolving a similarly named street in another city.
        fun url(address: String): String? {
            if (address.length !in 6..180 || address.any(Char::isISOControl)) return null
            if (!Regex("(?i)\\b(?:CABA|Ciudad Aut[oó]noma de Buenos Aires|Capital Federal)\\b").containsMatchIn(address)) return null
            val streetAndNumber = address.substringBefore(',').trim()
            val match = Regex("^([\\p{L}0-9 .'-]{3,100}?)\\s+(\\d{1,5})$").matchEntire(streetAndNumber) ?: return null
            val street = match.groupValues[1].trim()
            if (!street.any(Char::isLetter) || Regex("(?i)\\b(?:y|esquina)\\b").containsMatchIn(street)) return null
            val number = match.groupValues[2].toIntOrNull()?.takeIf { it in 1..20000 } ?: return null
            return "https://ws.usig.buenosaires.gob.ar/datos_utiles?calle=${URLEncoder.encode(street.uppercase(Locale.ROOT), "UTF-8")}&altura=$number"
        }

        fun parse(json: String): String? = try {
            val root = JsonParser.parseString(json)
            val value = if (root.isJsonArray && root.asJsonArray.size() == 1) root.asJsonArray[0] else root
            val obj = value.asJsonObject
            val commune = obj.get("comuna")?.asString.orEmpty()
            val zone = obj.get("barrio")?.asString.orEmpty().trim()
            if (Regex("Comuna (?:[1-9]|1[0-5])").matches(commune) &&
                zone.length in 3..50 && zone.all { it.isLetter() || it == ' ' }) zone else null
        } catch (_: Exception) { null }

        /** Fixed HTTPS host, no redirects, bounded response/time. */
        private fun fetch(url: String): String? {
            val response = Jsoup.connect(url).ignoreContentType(true).followRedirects(false)
                .timeout(1800).maxBodySize(16 * 1024).userAgent("LoVale/1.0").execute()
            return if (response.statusCode() == 200) parse(response.body()) else null
        }
    }
}
