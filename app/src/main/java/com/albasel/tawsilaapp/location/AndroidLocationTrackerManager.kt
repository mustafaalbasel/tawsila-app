package com.albasel.tawsilaapp.location

import android.content.Context
import com.albasel.tawsilaapp.data.model.LocationMode
import com.albasel.tawsilaapp.service.LocationForegroundService

class AndroidLocationTrackerManager(private val context: Context) : LocationTrackerManager {

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
        LocationForegroundService.startService(
            context = context,
            userId = userId,
            mode = mode,
            orderId = orderId,
            province = province,
            vType = vehicleType,
            name = userName,
            isAppr = isApproved,
            canDel = canDeliver,
            canCar = canCargo
        )
    }

    override fun stopTracking() {
        LocationForegroundService.stopService(context)
    }

    override fun isTrackingActive(): Boolean {
        return LocationForegroundService.isRunning
    }
}
