package com.albasel.tawsilaapp.data.repository

data class PlaceSuggestion(
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val distance: Double? = null,
    val category: String? = null,
    val source: String = ""
)

interface SearchRepository {
    suspend fun searchPlaces(
        query: String,
        nearLat: Double? = null,
        nearLng: Double? = null,
        language: String = "ar"
    ): List<PlaceSuggestion>
}
