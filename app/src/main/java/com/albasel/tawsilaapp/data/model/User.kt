package com.albasel.tawsilaapp.data.model

import androidx.annotation.Keep
import com.google.firebase.database.PropertyName

@Keep
data class User(
    val id: String = "",
    val name: String = "",
    val username: String = "",
    val phone: String = "",
    val password: String = "", // تضاف لتسجيل الدخول اليدوي
    val address: String = "",
    val role: String = "USER", // USER, DRIVER, ADMIN
    
    // Google Drive IDs
    val idCardDriveId: String? = null,
    val licenseFrontDriveId: String? = null,
    val licenseBackDriveId: String? = null,
    val profileImageDriveId: String? = null,
    val paymentReceiptDriveId: String? = null,
    val receipts: Map<String, String>? = null, // Key: PushID, Value: DriveID

    // Legacy Base64 fields (Keep for compatibility)
    val idCardImageBase64: String? = null,
    val licenseFrontBase64: String? = null,
    val licenseBackBase64: String? = null,
    val profileImageBase64: String? = null,
    val paymentReceiptBase64: String? = null,

    val walletBalance: Double = 0.0,
    val totalEarnings: Double = 0.0,
    val totalCommission: Double = 0.0, 
    val paidCommission: Double = 0.0,  
    val pendingCommission: Double = 0.0,
    val points: Double = 0.0, // Customer/User points
    val driverPoints: Double = 0.0, // Driver specific points
    val pointsDiscount: Double = 0.0,
    val vehicleType: String = "",
    val vehiclePlate: String = "",
    val vehicleColor: String = "",
    val busCapacity: Int = 0,
    val province: String = "",
    val liveProvince: String = "",
    val adminPermissions: Map<String, Boolean>? = null,
    @get:PropertyName("approved")
    @set:PropertyName("approved")
    var isApproved: Boolean = true,
    @get:PropertyName("isOnline")
    @set:PropertyName("isOnline")
    var isOnline: Boolean = false,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val lastSeen: Long = 0L,
    val fcmToken: String = "",
    val currentSessionId: String = "",
    val currentDeviceId: String = "",
    
    // Driver Capabilities
    val canDeliver: Boolean = true,
    val canCargo: Boolean = false,
    val canLoad: Boolean = false,
    val canUnload: Boolean = false,
    val gender: String = "MALE" // MALE, FEMALE
)
