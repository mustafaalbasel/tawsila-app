package com.albasel.tawsilaapp.data.map.provider

import com.albasel.tawsilaapp.data.model.RouteResult

interface RoutingProvider {
    val providerName: String
    suspend fun getRoute(startLat: Double, startLng: Double, endLat: Double, endLng: Double, language: String): List<RouteResult>
}
