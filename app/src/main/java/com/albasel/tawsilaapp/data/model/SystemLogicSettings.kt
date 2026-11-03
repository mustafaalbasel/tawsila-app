package com.albasel.tawsilaapp.data.model

import androidx.annotation.Keep

@Keep
data class SystemLogicSettings(
    val driverArrivalRadius: Double = 200.0,
    val customerArrivalRadius: Double = 200.0,
    val gpsConfirmationsNeeded: Int = 3,
    val gpsAccuracyThreshold: Float = 30f,
    val freeWaitingTime: Int = 15,
    val extraWaitingPrice: Double = 5.0,
    val freeLoadingTime: Int = 30,
    val loadingExtraPrice: Double = 5.0,
    val freeUnloadingTime: Int = 30,
    val unloadingExtraPrice: Double = 5.0
)
