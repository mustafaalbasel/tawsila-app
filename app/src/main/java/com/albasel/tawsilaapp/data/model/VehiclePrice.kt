package com.albasel.tawsilaapp.data.model

import androidx.annotation.Keep

@Keep
data class VehiclePrice(
    val type: String = "",
    val pricePerKm: Double = 0.0,
    val pricePerMinute: Double = 0.0,
    val baseFare: Double = 0.0,
    val tollFee: Double = 0.0,
    val isVisible: Boolean = true,
    val showInHome: Boolean = true,
    val showInInsideCity: Boolean = true,
    val showInCargo: Boolean = true,
    val priority: Int = 0,
    val trafficSpeedLimitKmh: Double = 40.0,
    
    // Compatibility fields for Admin/Registration
    val maxWeightKg: Double = 0.0,
    val pricePerCarton: Double = 0.0
)
