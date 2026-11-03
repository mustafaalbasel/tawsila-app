package com.albasel.tawsilaapp.data.repository

import com.albasel.tawsilaapp.data.model.RouteOption

interface RoutingRepository {
    suspend fun getRoute(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double,
        language: String = "ar"
    ): List<RouteOption>
}
