package com.example.lovale2.domain

import com.google.gson.JsonParser

data class MapPoint(val longitude: Double, val latitude: Double)
data class NeighborhoodPolygon(val name: String, val rings: List<List<MapPoint>>) {
    private fun inside(ring: List<MapPoint>, point: MapPoint): Boolean {
        var result = false
        var previous = ring.lastOrNull() ?: return false
        for (next in ring) {
            if ((next.latitude > point.latitude) != (previous.latitude > point.latitude) &&
                point.longitude < (previous.longitude - next.longitude) * (point.latitude - next.latitude) /
                (previous.latitude - next.latitude) + next.longitude) result = !result
            previous = next
        }
        return result
    }
    fun contains(point: MapPoint): Boolean = rings.firstOrNull()?.let { inside(it, point) } == true && rings.drop(1).none { inside(it, point) }
}
fun readNeighborhoodMap(json: String): List<NeighborhoodPolygon> = JsonParser.parseString(json).asJsonObject.getAsJsonArray("features").map { f ->
    val feature = f.asJsonObject
    val geometry = feature.getAsJsonObject("geometry")
    require(geometry.get("type").asString == "Polygon")
    NeighborhoodPolygon(feature.getAsJsonObject("properties").get("nombre").asString,
        geometry.getAsJsonArray("coordinates").map { ring -> ring.asJsonArray.map { pair ->
            MapPoint(pair.asJsonArray[0].asDouble, pair.asJsonArray[1].asDouble)
        } })
}
