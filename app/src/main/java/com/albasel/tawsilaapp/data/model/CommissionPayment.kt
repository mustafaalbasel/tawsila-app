package com.albasel.tawsilaapp.data.model

data class CommissionPayment(
    val id: String = "",
    val amount: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis()
)
