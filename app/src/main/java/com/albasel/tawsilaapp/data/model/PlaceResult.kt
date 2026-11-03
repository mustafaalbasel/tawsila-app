package com.albasel.tawsilaapp.data.model

import org.maplibre.android.geometry.LatLng

data class PlaceResult(
    val id: String,
    val providerId: String,
    val provider: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val category: String?,
    val distanceMeters: Double = 0.0,
    val sourceUpdatedAt: Long = System.currentTimeMillis()
) {
    val latLng: LatLng
        get() = LatLng(latitude, longitude)
}
