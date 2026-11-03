package com.albasel.tawsilaapp.data.model

import org.maplibre.android.geometry.LatLng

data class RouteStep(
    val instruction: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val distance: Double = 0.0
)

data class RouteOption(
    val id: String = "",
    val distance: Double = 0.0,
    val duration: Double = 0.0, // seconds
    val points: List<LatLng> = emptyList(),
    var tollCount: Int = 0,
    val name: String = "",
    val steps: List<RouteStep> = emptyList()
)
