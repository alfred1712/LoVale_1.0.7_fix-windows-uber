package com.example.lovale2.data.model

data class GeocodingResponse(
    val results: List<GeographicResult>,
    val status: String
)

data class GeographicResult(
    val address_components: List<AddressComponent>,
    val formatted_address: String
)

data class AddressComponent(
    val long_name: String,
    val short_name: String,
    val types: List<String>
)