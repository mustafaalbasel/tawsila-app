package com.albasel.tawsilaapp.util

import org.maplibre.android.geometry.LatLng
import kotlin.math.*

data class TollGate(val id: String, val name: String, val lat: Double, val lng: Double)

object MapUtils {
    val TOLL_GATES = listOf(
        TollGate("card_1", "كارتة طريق مصر إسماعيلية", 30.20659, 31.67047),
        TollGate("card_2", "كارتة الإسكندرية - طريق القاهرة الإسكندرية الصحراوي", 30.9719833, 29.8255167),
        TollGate("card_3", "كارتة طريق روض الفرج - اتجاه", 30.10666, 31.00178),
        TollGate("card_4", "كارتة طريق روض الفرج - اتجاه آخر", 30.10736, 31.002),
        TollGate("card_5", "كارتة طريق الزعفرانة - اتجاه", 29.10053, 32.65044),
        TollGate("card_6", "كارتة طريق الزعفرانة - اتجاه آخر", 29.10079, 32.65004),
        TollGate("card_7", "بوابات الكارتة - أسيوط", 27.4011, 31.51895),
        // Additional Gates
        TollGate("card_8", "كارتة طريق السويس - بوابات مدينتي", 30.1017, 31.6425),
        TollGate("card_9", "كارتة طريق العين السخنة", 29.9328, 31.4647),
        TollGate("card_10", "كارتة الطريق الإقليمي - اتجاه السويس", 30.1258, 31.7892),
        TollGate("card_11", "كارتة طريق الضبعة", 30.1345, 30.8231),
        TollGate("card_12", "كارتة طريق الجلالة", 29.6123, 32.4156),
        TollGate("card_13", "كارتة القاهرة الإسماعيلية (بوابات الشروق)", 30.1450, 31.6210),
        TollGate("card_14", "كارتة بوابات طريق بلبيس", 30.2980, 31.5870),
        TollGate("card_15", "كارتة طريق شبرا بنها الحر", 30.2310, 31.2580),
        TollGate("card_16", "كارتة طريق القاهرة السويس (بوابات مدينتي 2)", 30.0890, 31.6850),
        TollGate("card_17", "كارتة طريق الكريمات (بني سويف)", 29.2150, 31.2210),
        TollGate("card_18", "كارتة طريق أسيوط الغربي", 29.2500, 31.0500),
        TollGate("card_19", "كارتة طريق القاهرة بورسعيد", 30.9500, 32.2500),
        TollGate("card_20", "كارتة طريق الإسكندرية مطروح", 31.1180, 29.5300),
        TollGate("card_21", "كارتة طريق الإسماعيلية بورسعيد", 30.8200, 32.2800),
        TollGate("card_22", "كارتة طريق السويس السخنة", 29.8500, 32.4200),
        TollGate("card_23", "كارتة طريق وادي النطرون العلمين", 30.6500, 29.8500),
        TollGate("card_24", "كارتة طريق بنها المنصورة", 30.4500, 31.2800),
        TollGate("card_25", "كارتة طريق طنطا كفر الشيخ", 30.9200, 30.9800),
        TollGate("card_26", "كارتة طريق المنصورة دمياط (شربين)", 31.1800, 31.5200),
        TollGate("card_27", "كارتة محور تحيا مصر (روض الفرج)", 30.1200, 31.1800),
        TollGate("card_28", "كارتة طريق القاهرة الفيوم", 29.8500, 30.9200),
        TollGate("card_29", "كارتة طريق أسيوط الغربي - بوابة 2", 28.5200, 30.8500),
        TollGate("card_30", "كارتة طريق بورسعيد دمياط (بوابة الرسوة)", 31.2200, 32.2200)
    )

    fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0
        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat
            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng
            poly.add(LatLng(lat.toDouble() / 1E5, lng.toDouble() / 1E5))
        }
        return poly
    }

    fun decodePolyline6(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0
        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat
            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng
            poly.add(LatLng(lat.toDouble() / 1E6, lng.toDouble() / 1E6))
        }
        return poly
    }

    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    fun calculateMaxGeometryDiff(points1: List<LatLng>, points2: List<LatLng>): Double {
        if (points1.isEmpty() || points2.isEmpty()) return 0.0
        var maxDiffKm = 0.0
        val samplePoints = if (points1.size > 50) points1.filterIndexed { idx, _ -> idx % (points1.size / 50) == 0 } else points1
        for (p1 in samplePoints) {
            val minDistanceToP2 = points2.minOf { p2 -> calculateDistance(p1.latitude, p1.longitude, p2.latitude, p2.longitude) }
            if (minDistanceToP2 > maxDiffKm) maxDiffKm = minDistanceToP2
        }
        return maxDiffKm * 1000.0 // meters
    }

    /**
     * Detects toll gates along a polyline.
     * A gate is detected if any point in the polyline is within [thresholdKm] from it.
     */
    fun detectTollGates(points: List<LatLng>, thresholdKm: Double = 0.5): List<TollGate> {
        val detected = mutableListOf<TollGate>()
        for (gate in TOLL_GATES) {
            val isNear = points.any { p -> 
                calculateDistance(p.latitude, p.longitude, gate.lat, gate.lng) <= thresholdKm 
            }
            if (isNear) detected.add(gate)
        }
        return detected
    }

    /**
     * Extracts names of toll gates along a polyline.
     */
    fun getTollNames(points: List<LatLng>, thresholdKm: Double = 0.5): List<String> {
        return detectTollGates(points, thresholdKm).map { it.name }
    }

    fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        
        val bearing = atan2(y, x)
        return (Math.toDegrees(bearing) + 360.0) % 360.0
    }
}
