package com.albasel.tawsilaapp.data.model

import androidx.annotation.Keep

@Keep
data class CargoStandard(
    val id: String = "",
    val minWeight: Double = 0.0,
    val maxWeight: Double = 0.0,
    val surcharge: Double = 0.0,
    val loadingFee: Double = 0.0,
    val unloadingFee: Double = 0.0
)

@Keep
data class WeightBracket(
    val id: String = "",
    val label: String = "",
    val fromKg: Double = 0.0,
    val toKg: Double = 0.0,
    val surcharge: Double = 0.0,
    val loadingFee: Double = 0.0,
    val unloadingFee: Double = 0.0
)

@Keep
data class CargoServiceOption(
    val id: String = "",
    val label: String = "",
    val basePrice: Double = 0.0
)

@Keep
data class CargoStandards(
    val weightBrackets: List<WeightBracket> = emptyList(),
    val loadingOptions: List<CargoServiceOption> = emptyList(),
    val unloadingOptions: List<CargoServiceOption> = emptyList()
)
