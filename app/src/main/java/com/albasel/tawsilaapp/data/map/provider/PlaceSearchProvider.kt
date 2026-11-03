package com.albasel.tawsilaapp.data.map.provider

import com.albasel.tawsilaapp.data.model.PlaceResult

interface PlaceSearchProvider {
    val providerName: String
    suspend fun search(query: String, userLat: Double?, userLng: Double?, language: String): List<PlaceResult>
    suspend fun nearby(category: String, centerLat: Double, centerLng: Double, radiusMeters: Double, language: String): List<PlaceResult>
    suspend fun reverseGeocode(lat: Double, lng: Double, language: String): PlaceResult?
}
