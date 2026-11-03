package com.albasel.tawsilaapp.data.model

data class DriverPaymentRecord(
    val id: String = "",
    val driverId: String = "",
    val driverName: String = "",
    val amount: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis()
)
