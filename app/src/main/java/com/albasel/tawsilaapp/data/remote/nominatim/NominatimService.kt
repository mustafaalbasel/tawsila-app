package com.albasel.tawsilaapp.data.remote.nominatim

import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface NominatimService {
    @Headers("User-Agent: TawsilaApp")
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("format") format: String = "json",
        @Query("addressdetails") addressDetails: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("accept-language") lang: String = "ar",
        @Query("countrycodes") country: String = "eg"
    ): List<NominatimResponseItem>
}

data class NominatimResponseItem(
    val place_id: Long,
    val licence: String,
    val osm_type: String,
    val osm_id: Long,
    val lat: String,
    val lon: String,
    val display_name: String,
    val address: NominatimAddress?,
    val importance: Double?,
    val type: String?
)

data class NominatimAddress(
    val name: String?,
    val house_number: String?,
    val road: String?,
    val suburb: String?,
    val city: String?,
    val state: String?,
    val country: String?
)
