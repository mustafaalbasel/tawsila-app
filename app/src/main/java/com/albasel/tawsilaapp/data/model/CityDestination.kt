package com.albasel.tawsilaapp.data.model

data class CityDestination(
    val id: String = "",
    val name: String = "",
    val details: String = "",
    val type: String = "LINE", // "LINE" or "SCHOOL"
    val province: String = "",
    val city: String = "",
    val price: Double = 0.0,
    val startLat: Double = 0.0,
    val startLng: Double = 0.0,
    val endLat: Double = 0.0,
    val endLng: Double = 0.0,
    val priority: Int = 0,
    val tripType: String = "", // e.g., "ذهاب فقط", "ذهاب وعودة"
    val imageUrl: String = "" // Google Drive Image File ID or URL
)
