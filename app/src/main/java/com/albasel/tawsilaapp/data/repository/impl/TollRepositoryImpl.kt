package com.albasel.tawsilaapp.data.repository.impl

import com.albasel.tawsilaapp.data.repository.TollGate
import com.albasel.tawsilaapp.data.repository.TollRepository
import com.albasel.tawsilaapp.util.MapUtils

class TollRepositoryImpl : TollRepository {
    private val knownTollGates = listOf(
        TollGate("بوابة الكيلو 26 - طريق القاهرة السويس", 30.012345, 31.456789),
        TollGate("بوابة الكيلو 80 - طريق القاهرة السويس", 30.056789, 31.523456),
        TollGate("بوابة الكيلو 26 - طريق القاهرة الإسكندرية الصحراوي", 30.123456, 30.987654),
        TollGate("بوابة الكيلو 95 - طريق القاهرة الإسكندرية الصحراوي", 30.234567, 30.876543),
        TollGate("بوابة الكيلو 160 - طريق القاهرة الإسكندرية الصحراوي", 30.345678, 30.765432),
        TollGate("بوابة الكيلو 20 - طريق القاهرة الإسماعيلية", 30.456789, 31.654321),
        TollGate("بوابة الكيلو 70 - طريق القاهرة الإسماعيلية", 30.567890, 31.543210),
        TollGate("بوابة الكيلو 30 - طريق القاهرة العين السخنة", 29.987654, 31.876543),
        TollGate("بوابة الكيلو 90 - طريق القاهرة العين السخنة", 29.876543, 31.765432),
        TollGate("بوابة الكيلو 15 - طريق القاهرة الواحات", 30.123987, 31.234567),
        TollGate("بوابة الكيلو 50 - طريق القاهرة الواحات", 30.234876, 31.123456),
        TollGate("بوابة الكيلو 40 - طريق القاهرة الإسكندرية الزراعي", 30.345987, 31.012345),
        TollGate("بوابة الكيلو 100 - طريق القاهرة الإسكندرية الزراعي", 30.456876, 30.901234),
        TollGate("بوابة الكيلو 25 - طريق القاهرة الفيوم", 29.987123, 31.234876),
        TollGate("بوابة الكيلو 60 - طريق القاهرة الفيوم", 29.876234, 31.123987),
        TollGate("بوابة الكيلو 35 - طريق الإسكندرية مطروح الساحلي", 31.123456, 29.876543),
        TollGate("بوابة الكيلو 120 - طريق الإسكندرية مطروح الساحلي", 31.234567, 29.765432),
        TollGate("بوابة الكيلو 200 - طريق الإسكندرية مطروح الساحلي", 31.345678, 29.654321),
        TollGate("بوابة الكيلو 45 - طريق القاهرة بني سويف", 29.876543, 31.123456),
        TollGate("بوابة الكيلو 90 - طريق القاهرة بني سويف", 29.765432, 31.012345),
        TollGate("بوابة الكيلو 30 - طريق القاهرة أسيوط الصحراوي الغربي", 29.654321, 30.987654),
        TollGate("بوابة الكيلو 120 - طريق القاهرة أسيوط الصحراوي الغربي", 29.543210, 30.876543),
        TollGate("بوابة الكيلو 250 - طريق القاهرة أسيوط الصحراوي الغربي", 29.432109, 30.765432),
        TollGate("بوابة الكيلو 40 - طريق الإسكندرية العلمين", 31.123987, 29.876123),
        TollGate("بوابة الكيلو 100 - طريق الإسكندرية العلمين", 31.234876, 29.765987)
    )

    override suspend fun getTollGatesOnRoute(points: List<Pair<Double, Double>>): List<TollGate> {
        // Simple proximity check for each known toll gate against route points.
        // A more advanced solution would use segment intersection.
        return knownTollGates.filter { toll ->
            points.any { p -> 
                MapUtils.calculateDistance(toll.lat, toll.lng, p.first, p.second) < 0.2 // 200 meters proximity
            }
        }
    }
}
