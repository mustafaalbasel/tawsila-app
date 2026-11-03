package com.albasel.tawsilaapp.data.model

import org.maplibre.android.geometry.LatLng

data class RouteResult(
    val id: String,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val polyline: List<LatLng>,
    val steps: List<RouteStep> = emptyList(),
    val provider: String,
    val calculatedAt: Long = System.currentTimeMillis()
)
