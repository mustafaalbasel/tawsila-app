package com.albasel.tawsilaapp.data.model

import androidx.annotation.Keep
import com.google.firebase.database.PropertyName

@Keep
data class Order(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val userPhone: String = "",
    val driverId: String? = null,
    val pickupName: String = "",
    val pickupLat: Double = 0.0,
    val pickupLng: Double = 0.0,
    val destinationName: String = "",
    val destinationLat: Double = 0.0,
    val destinationLng: Double = 0.0,
    val date: String = "",
    val time: String = "",
    val vehicleType: String = "",
    val passengers: Int = 1,
    val originalPassengers: Int = 0,
    val price: Double = 0.0,
    val pointsDiscount: Double = 0.0,
    val promoDiscountAmount: Double = 0.0,
    val rideType: String = "PRIVATE", // PRIVATE or SHARED
    val status: String = "PENDING", // PENDING, ACCEPTED, CONFIRMED, COMPLETED, REJECTED
    val pickupProvince: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val driverName: String? = null,
    val driverPhone: String? = null,
    val driverImage: String? = null,
    val vehiclePlate: String? = null,
    val vehicleColor: String? = null,
    val driverLat: Double? = null,
    val driverLng: Double? = null,
    val rating: Float? = null,
    val feedback: String? = null,
    val userRating: Float? = null,
    val userFeedback: String? = null,
    val tollCount: Int = 0,
    val tollNames: String = "",
    val routeType: String = "DEFAULT",
    val distance: Double = 0.0,
    
    // Financial flags
    @get:PropertyName("isPointsRecorded")
    @set:PropertyName("isPointsRecorded")
    var isPointsRecorded: Boolean = false,
    
    // Automated Waiting/Loading Timestamps
    val driverArrivalTime: Long = 0L,
    val destinationArrivalTime: Long = 0L,
    val customerArrivalTime: Long = 0L,
    val loadingStartTime: Long = 0L,
    val loadingEndTime: Long = 0L,
    val unloadingStartTime: Long = 0L,
    val unloadingEndTime: Long = 0L,
    
    // Reminders
    val lastLoadingReminderTime: Long = 0L,
    val lastUnloadingReminderTime: Long = 0L,
    
    // Cargo Specific
    @get:PropertyName("isCargo")
    @set:PropertyName("isCargo")
    var isCargo: Boolean = false,
    val cargoDetails: String? = null,
    val cargoWeight: Double = 0.0,
    val needsLoading: Boolean = false,
    val needsUnloading: Boolean = false,
    val cargoWaitMinutes: Int = 0,
    val numCartons: Int = 0,
    val selectedLoadingOption: String? = null,
    val selectedUnloadingOption: String? = null,
    val cargoFee: Double = 0.0,
    val loadingFee: Double = 0.0,
    val unloadingFee: Double = 0.0,
    val waitingFee: Double = 0.0,
    val totalLoadingFee: Double = 0.0,
    val totalUnloadingFee: Double = 0.0,
    val gender: String = "ANY", // MALE, FEMALE, ANY

    // Safe Trip Recording Fields
    val tripStartTime: Long = 0L,
    val tripEndTime: Long = 0L,
    val actualTripDuration: Long = 0L,
    @get:PropertyName("routeDeviationDetected")
    @set:PropertyName("routeDeviationDetected")
    var routeDeviationDetected: Boolean = false,
    val routeDeviationDistance: Double = 0.0,
    val routeDeviationDetectedAt: Long = 0L,
    
    // Route Change Request
    val routeChangeRequestedBy: String? = null, // userId
    val routeChangeStatus: String = "NONE", // PENDING, DRIVER_ACCEPTED, DRIVER_REJECTED
    val requestedDestinationName: String? = null,
    val requestedDestinationLat: Double = 0.0,
    val requestedDestinationLng: Double = 0.0,

    // Traffic Monitoring Fields
    val trafficDurationSeconds: Long = 0L,
    val trafficExtraMinutes: Int = 0,
    val trafficExtraFee: Double = 0.0,
    val trafficSpeedLimitKmh: Double = 40.0,
    
    // Cancellation Compensation
    val compensationId: String? = null,
    val compensationAmount: Double = 0.0,
    val isCompensationSettled: Boolean = false
)
