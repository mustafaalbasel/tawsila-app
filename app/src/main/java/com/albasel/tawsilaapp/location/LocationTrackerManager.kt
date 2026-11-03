package com.albasel.tawsilaapp.location

import com.albasel.tawsilaapp.data.model.LocationMode

/**
 * Platform-agnostic Location Tracker Manager interface.
 * Android implementation uses LocationForegroundService with FusedLocationProviderClient.
 * iOS implementation uses CoreLocation CLLocationManager with background updates enabled.
 */
interface LocationTrackerManager {
    fun startTracking(
        userId: String,
        mode: LocationMode,
        orderId: String? = null,
        province: String = "",
        vehicleType: String = "",
        userName: String = "",
        isApproved: Boolean = true,
        canDeliver: Boolean = true,
        canCargo: Boolean = false
    )
    fun stopTracking()
    fun isTrackingActive(): Boolean
}
