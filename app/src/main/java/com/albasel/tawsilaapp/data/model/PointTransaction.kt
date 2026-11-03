package com.albasel.tawsilaapp.data.model

data class PointTransaction(
    val id: String = "",
    val amount: Double = 0.0,
    val reason: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val type: String = "EARNED" // EARNED, SPENT, ADMIN_ADD, ADMIN_SUB
)
