package com.example.lovale2.data.network

import com.example.lovale2.data.model.GeocodingResponse
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface GoogleMapsApiService {
    @GET("maps/api/geocode/json")
    suspend fun getAddressInfo(
        @Query("address") address: String,
        @Query("key") apiKey: String,
        @Query("language") language: String = "es"
    ): GeocodingResponse

    companion object {
        private const val BASE_URL = "https://maps.googleapis.com/"

        fun create(): GoogleMapsApiService {
            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            return retrofit.create(GoogleMapsApiService::class.java)
        }
    }
}