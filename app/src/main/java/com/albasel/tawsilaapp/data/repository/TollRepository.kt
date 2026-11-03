package com.albasel.tawsilaapp.data.repository

import com.albasel.tawsilaapp.data.model.RouteOption

data class TollGate(
    val name: String,
    val lat: Double,
    val lng: Double
)

interface TollRepository {
    /**
     * Checks if the given route intersects with any toll gates.
     * Returns a list of toll gates crossed.
     */
    suspend fun getTollGatesOnRoute(points: List<Pair<Double, Double>>): List<TollGate>
}
