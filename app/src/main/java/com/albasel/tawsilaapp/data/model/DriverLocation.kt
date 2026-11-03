package com.albasel.tawsilaapp.data.model

data class DriverLocation(
    val id: String = "",
    val name: String = "",
    val vehicleType: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val lastSeen: Long = 0L,
    val isOnline: Boolean = false,
    val province: String = "",
    val isApproved: Boolean = true,
    val canDeliver: Boolean = true,
    val canCargo: Boolean = false
)
