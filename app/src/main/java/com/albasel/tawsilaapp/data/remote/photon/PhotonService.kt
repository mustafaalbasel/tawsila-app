package com.albasel.tawsilaapp.data.remote.photon

import retrofit2.http.GET
import retrofit2.http.Query

interface PhotonService {
    @GET("api/")
    suspend fun search(
        @Query("q") query: String,
        @Query("lat") lat: Double? = null,
        @Query("lon") lon: Double? = null,
        @Query("limit") limit: Int = 15,
        @Query("lang") lang: String = "ar"
    ): PhotonResponse
}

data class PhotonResponse(
    val features: List<PhotonFeature>
)

data class PhotonFeature(
    val geometry: PhotonGeometry,
    val properties: PhotonProperties
)

data class PhotonGeometry(
    val coordinates: List<Double> // [lon, lat]
)

data class PhotonProperties(
    val name: String?,
    val street: String?,
    val city: String?,
    val country: String?,
    val state: String?,
    val postcode: String?,
    val osm_key: String?,
    val osm_value: String?
)
