package com.albasel.tawsilaapp.util

object VehicleUtils {
    fun cleanVehicleName(name: String?): String {
        var n = (name ?: "").lowercase().trim()
        if (n.startsWith("ال")) n = n.substring(2)
        return n
    }

    fun isCargoOnlyVehicle(type: String?): Boolean {
        val t = cleanVehicleName(type)
        return t.contains("دراجة") || t.contains("bike") || 
               t.contains("تريسكل") || t.contains("ترسكل") || t.contains("tricycle") ||
               t.contains("ربع") || t.contains("quarter") || 
               t.contains("نصف") || t.contains("half") || 
               t.contains("نقل") || t.contains("truck")
    }

    fun getBaseIconName(type: String?): String {
        val t = cleanVehicleName(type)
        return when {
            t.contains("توكتوك") || t.contains("توك") || t.contains("tuk") -> "tuk_tuk"
            t.contains("تريسكل") || t.contains("ترسكل") || t.contains("tricycle") -> "tricycle"
            t.contains("تاكسي") || t.contains("taxi") -> "taxi"
            t.contains("ملاكي") || t.contains("private") -> "privatecar"
            t.contains("فان") || t.contains("van") -> "van"
            t.contains("ميكروباص") || t.contains("microbus") -> "microbus"
            t.contains("باص") || t.contains("bus") -> "bus"
            t.contains("موتوسيكل") || t.contains("moto") || t.contains("motorcycle") -> "moto"
            t.contains("دراجة") || t.contains("bike") -> "bike"
            t.contains("ربع") || t.contains("quarter") -> "quartertruck"
            t.contains("نصف") || t.contains("half") -> "halftruck"
            t.contains("نقل") || t.contains("truck") -> "truck"
            else -> "taxi"
        }
    }
}
