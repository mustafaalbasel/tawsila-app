package com.albasel.tawsilaapp.data.model

import androidx.annotation.Keep

@Keep
data class PricingFormula(
    val id: String = "",
    val minKm: Double = 0.0,
    val maxKm: Double = 1000.0,
    val baseFare: Double = 0.0,
    val pricePerKm: Double = 0.0,
    val pricePerMinute: Double = 0.0,
    val tollFee: Double = 0.0,
    val vehicleType: String = "",
    val rideCategory: String = "HOME",
    
    // Cargo Specific
    val cargoWaitMinutePrice: Double = 0.0,
    val freeWaitMinutes: Int = 0, // Legacy/Travel
    val freeWaitMinutesCargo: Int = 0,
    val cargoStandardId: String? = null,
    val surcharge: Double = 0.0,
    val loadingFee: Double = 0.0,
    val unloadingFee: Double = 0.0,
    
    // Traffic Speed limit
    val trafficSpeedLimitKmh: Double = 40.0,
    
    // GPS Logic Specific (If rideCategory == "GPS")
    val driverArrivalRadius: Double = 200.0,
    val customerArrivalRadius: Double = 200.0,
    val gpsConfirmationsNeeded: Int = 3,
    val gpsAccuracyThreshold: Float = 30f,
    
    // Compatibility
    val maxWeightKg: Double = 0.0,
    val pricePerCarton: Double = 0.0,
    
    // Admin specific lists
    val weightBrackets: List<WeightBracket> = emptyList(),
    val loadingOptions: List<CargoServiceOption> = emptyList(),
    val unloadingOptions: List<CargoServiceOption> = emptyList()
)
