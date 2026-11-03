package com.albasel.tawsilaapp.data.model

import androidx.annotation.Keep

@Keep
data class PromoCode(
    val code: String = "",
    val percentage: Double = 0.0,
    val offerText: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val expiryTimestamp: Long = System.currentTimeMillis() + 86400000L,
    val validityDays: Int = 1,
    val isActive: Boolean = true
)
