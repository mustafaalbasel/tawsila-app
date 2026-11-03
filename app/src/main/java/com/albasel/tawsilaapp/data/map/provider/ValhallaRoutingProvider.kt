package com.albasel.tawsilaapp.data.map.provider

import android.util.Log
import com.albasel.tawsilaapp.data.model.RouteOption
import com.albasel.tawsilaapp.data.model.RouteStep
import com.albasel.tawsilaapp.util.MapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class ValhallaRoutingProvider(
    private val baseUrl: String = "https://valhalla.wasalna.app",
    private val client: OkHttpClient = OkHttpClient()
) {
    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    suspend fun getRoute(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double,
        language: String = "ar",
        costing: String = "auto"
    ): List<RouteOption> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val isAr = language == "ar" || language == "AR"

        try {
            val jsonPayload = JSONObject().apply {
                val locs = JSONArray().apply {
                    put(JSONObject().apply {
                        put("lat", startLat)
                        put("lon", startLng)
                    })
                    put(JSONObject().apply {
                        put("lat", endLat)
                        put("lon", endLng)
                    })
                }
                put("locations", locs)
                put("costing", costing)
                put("directions_options", JSONObject().apply {
                    put("language", if (isAr) "ar-SA" else "en-US")
                    put("units", "kilometers")
                })
                put("alternates", 1)
            }

            val request = Request.Builder()
                .url("$baseUrl/route")
                .post(jsonPayload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("User-Agent", "WASALNA-App/1.0 (contact@wasalna.app)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val jsonStr = response.body?.string() ?: return@withContext emptyList()
                val jsonObject = JSONObject(jsonStr)
                
                val tripObj = jsonObject.optJSONObject("trip") ?: return@withContext emptyList()
                val legs = tripObj.optJSONArray("legs") ?: return@withContext emptyList()
                
                val result = mutableListOf<RouteOption>()
                
                var totalDistKm = 0.0
                var totalDurationSec = 0.0
                val allPoints = mutableListOf<org.maplibre.android.geometry.LatLng>()
                val stepsList = mutableListOf<RouteStep>()

                for (i in 0 until legs.length()) {
                    val leg = legs.getJSONObject(i)
                    val summary = leg.optJSONObject("summary")
                    if (summary != null) {
                        totalDistKm += summary.optDouble("length", 0.0)
                        totalDurationSec += summary.optDouble("time", 0.0)
                    }

                    val shapeStr = leg.optString("shape", "")
                    if (shapeStr.isNotEmpty()) {
                        allPoints.addAll(MapUtils.decodePolyline6(shapeStr))
                    }

                    val maneuvers = leg.optJSONArray("maneuvers")
                    if (maneuvers != null) {
                        for (j in 0 until maneuvers.length()) {
                            val m = maneuvers.getJSONObject(j)
                            val instr = m.optString("instruction", "")
                            val mLength = m.optDouble("length", 0.0)
                            
                            stepsList.add(
                                RouteStep(
                                    instruction = instr,
                                    lat = 0.0,
                                    lng = 0.0,
                                    distance = mLength
                                )
                            )
                        }
                    }
                }

                val detectedTolls = MapUtils.detectTollGates(allPoints)

                result.add(
                    RouteOption(
                        id = "valhalla_route_0",
                        distance = totalDistKm,
                        duration = totalDurationSec,
                        points = allPoints,
                        tollCount = detectedTolls.size,
                        name = if (isAr) "مسار الفالا" else "Valhalla Route",
                        steps = stepsList
                    )
                )

                // Handle alternate routes from Valhalla if present
                val alternates = jsonObject.optJSONArray("alternates")
                if (alternates != null) {
                    for (k in 0 until alternates.length()) {
                        val altTrip = alternates.getJSONObject(k).optJSONObject("trip") ?: continue
                        val altLegs = altTrip.optJSONArray("legs") ?: continue
                        var altDistKm = 0.0
                        var altDurSec = 0.0
                        val altPoints = mutableListOf<org.maplibre.android.geometry.LatLng>()

                        for (l in 0 until altLegs.length()) {
                            val leg = altLegs.getJSONObject(l)
                            val summary = leg.optJSONObject("summary")
                            if (summary != null) {
                                altDistKm += summary.optDouble("length", 0.0)
                                altDurSec += summary.optDouble("time", 0.0)
                            }
                            val shapeStr = leg.optString("shape", "")
                            if (shapeStr.isNotEmpty()) {
                                altPoints.addAll(MapUtils.decodePolyline6(shapeStr))
                            }
                        }

                        val altTolls = MapUtils.detectTollGates(altPoints)
                        result.add(
                            RouteOption(
                                id = "valhalla_route_${k + 1}",
                                distance = altDistKm,
                                duration = altDurSec,
                                points = altPoints,
                                tollCount = altTolls.size,
                                name = if (isAr) "مسار بديل $k" else "Alternative $k",
                                steps = emptyList()
                            )
                        )
                    }
                }

                Log.d("ValhallaRoutingProvider", "Valhalla Route calculated in ${System.currentTimeMillis() - startTime}ms. Dist: $totalDistKm km")
                return@withContext result
            }
        } catch (e: Exception) {
            Log.e("ValhallaRoutingProvider", "Valhalla routing error: ${e.message}")
            return@withContext emptyList()
        }
    }
}
