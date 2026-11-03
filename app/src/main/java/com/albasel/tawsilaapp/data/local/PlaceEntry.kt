package com.albasel.tawsilaapp.data.local

data class PlaceEntry(
    val id: String,
    val nameAr: String,
    val nameEn: String = "",
    val category: String = "Landmark",
    val lat: Double,
    val lng: Double,
    val entranceLat: Double = lat,
    val entranceLng: Double = lng,
    val district: String = "",
    val city: String = "مصر",
    val governorate: String = "مصر",
    val street: String = "",
    val source: String = "Master_DB",
    val verified: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)
