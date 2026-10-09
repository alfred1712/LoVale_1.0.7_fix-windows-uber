package com.example.lovale2.domain

import com.example.lovale2.data.settings.AppSettings
import com.example.lovale2.data.settings.RideApp
import com.google.gson.Gson
import com.google.gson.JsonParser

data class ConfigurationBackup(val version: Int = 1, val minKm: String, val minHour: String,
    val pickup: String, val zones: List<String>, val platform: String?, val options: DriverOptions)

fun encodeConfiguration(settings: AppSettings, options: DriverOptions): String = Gson().toJson(ConfigurationBackup(
    minKm = settings.minRateByKm, minHour = settings.minRateByHour, pickup = settings.maxPickupDistance,
    zones = settings.excludedZones, platform = settings.selectedApp?.name, options = options))

fun decodeConfiguration(text: String): ConfigurationBackup {
    require(text.length <= 128000) { "Archivo demasiado grande" }
    val root = JsonParser.parseString(text).asJsonObject
    require(root.get("version")?.asInt == 1) { "Versión de respaldo no compatible" }
    fun number(key: String, max: Double): String {
        val value = root.get(key)?.asString?.replace(',', '.')?.toDoubleOrNull()
        require(value != null && value.isFinite() && value in 0.0..max) { "Valor inválido: $key" }
        return value.toString()
    }
    val zones = root.getAsJsonArray("zones").map { it.asString.trim() }
    require(zones.size <= 500 && zones.all { it.length in 1..100 && it.none(Char::isISOControl) }) { "Zonas inválidas" }
    val platform = root.get("platform")?.takeUnless { it.isJsonNull }?.asString
    require(platform == null || RideApp.fromStored(platform) != null) { "Plataforma inválida" }
    val options = Gson().fromJson(root.get("options"), DriverOptions::class.java) ?: DriverOptions()
    return ConfigurationBackup(minKm = number("minKm", 100000000.0), minHour = number("minHour", 100000000.0),
        pickup = number("pickup", 1000.0), zones = zones.distinct(), platform = platform, options = options.validate())
}
