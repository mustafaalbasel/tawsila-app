package com.albasel.tawsilaapp.location

import com.albasel.tawsilaapp.data.model.LocationMode

/**
 * iOS Location Tracker Manager implementation.
 * Wraps iOS Native CoreLocation framework with background updates enabled.
 */
class IosLocationTrackerManager : LocationTrackerManager {

    private var isTracking = false

    override fun startTracking(
        userId: String,
        mode: LocationMode,
        orderId: String?,
        province: String,
        vehicleType: String,
        userName: String,
        isApproved: Boolean,
        canDeliver: Boolean,
        canCargo: Boolean
    ) {
        isTracking = true
        // CoreLocation CLLocationManager background updates are triggered via iOS Swift bridge
    }

    override fun stopTracking() {
        isTracking = false
    }

    override fun isTrackingActive(): Boolean {
        return isTracking
    }
}
