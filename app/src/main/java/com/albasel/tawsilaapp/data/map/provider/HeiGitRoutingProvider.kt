package com.albasel.tawsilaapp.data.map.provider

import android.util.Log
import com.albasel.tawsilaapp.data.model.RouteResult
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

class HeiGitRoutingProvider(private val client: OkHttpClient = OkHttpClient()) : RoutingProvider {
    override val providerName: String = "OSRM-Routing"

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val OSRM_DIRECTIONS_URL = "https://router.project-osrm.org/route/v1/driving/"
        private const val VALHALLA_DIRECTIONS_URL = "https://valhalla.wasalna.app/route"
        private const val HEIGIT_DIRECTIONS_URL = "https://api.heigit.org/openrouteservice/v2/directions/driving-car"
    }

    override suspend fun getRoute(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double,
        language: String
    ): List<RouteResult> = withContext(Dispatchers.IO) {
        if (startLat == 0.0 || startLng == 0.0 || endLat == 0.0 || endLng == 0.0) {
            return@withContext emptyList()
        }

        // 1. OSRM Primary Fast Global Driving Engine
        var results = fetchFromOsrmServer(OSRM_DIRECTIONS_URL, startLat, startLng, endLat, endLng)

        // 2. Fast Mirror Fallback (OpenStreetMap DE Mirror) if primary server is busy
        if (results.isEmpty()) {
            results = fetchFromOsrmServer("https://routing.openstreetmap.de/routed-car/route/v1/driving/", startLat, startLng, endLat, endLng)
        }

        // Return ONLY real road geometries (Never return straight 2-point lines)
        return@withContext results.filter { it.polyline.size > 2 }.take(1)
    }

    private fun fetchFromOsrmServer(
        baseUrl: String,
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double
    ): List<RouteResult> {
        try {
            val url = "$baseUrl$startLng,$startLat;$endLng,$endLat?overview=full&geometries=polyline&alternatives=false&steps=true"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "WASALNA-App/1.0 (contact@wasalna.app)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()

                val jsonStr = response.body?.string() ?: return emptyList()
                val jsonObject = JSONObject(jsonStr)
                if (jsonObject.optString("code", "") != "Ok") return emptyList()

                val routes = jsonObject.optJSONArray("routes") ?: return emptyList()
                val result = mutableListOf<RouteResult>()

                for (i in 0 until minOf(1, routes.length())) {
                    val route = routes.getJSONObject(i)
                    val distanceMeters = route.optDouble("distance", 0.0)
                    val durationSeconds = route.optDouble("duration", 0.0)
                    val polylineStr = route.optString("geometry", "")

                    val points = if (polylineStr.isNotEmpty()) {
                        MapUtils.decodePolyline(polylineStr)
                    } else emptyList()

                    if (points.size > 2) {
                        result.add(
                            RouteResult(
                                id = "osrm_primary_route",
                                distanceMeters = distanceMeters,
                                durationSeconds = durationSeconds,
                                polyline = points,
                                steps = emptyList(),
                                provider = "OSRM-Routing"
                            )
                        )
                    }
                }
                return result
            }
        } catch (e: Exception) {
            return emptyList()
        }
    }

    private fun fetchFromValhalla(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double,
        language: String
    ): List<RouteResult> {
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
                put("costing", "auto")
                put("directions_options", JSONObject().apply {
                    put("language", if (isAr) "ar-SA" else "en-US")
                    put("units", "kilometers")
                })
                put("alternates", 1)
            }

            val request = Request.Builder()
                .url(VALHALLA_DIRECTIONS_URL)
                .post(jsonPayload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("User-Agent", "WASALNA-App/1.0 (contact@wasalna.app)")
                .build()

            Log.d("WASALNA_TRACE", "VALHALLA_ROUTE_START: start=[$startLng, $startLat], end=[$endLng, $endLat]")

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("WASALNA_TRACE", "VALHALLA_ROUTE_ERROR: HTTP ${response.code}")
                    return emptyList()
                }

                val jsonStr = response.body?.string() ?: return emptyList()
                val jsonObject = JSONObject(jsonStr)
                val tripObj = jsonObject.optJSONObject("trip") ?: return emptyList()
                val legs = tripObj.optJSONArray("legs") ?: return emptyList()

                val result = mutableListOf<RouteResult>()
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

                if (allPoints.isNotEmpty()) {
                    result.add(
                        RouteResult(
                            id = "valhalla_route_0",
                            distanceMeters = totalDistKm * 1000.0,
                            durationSeconds = totalDurationSec,
                            polyline = allPoints,
                            steps = stepsList,
                            provider = providerName
                        )
                    )
                }
                return result
            }
        } catch (e: Exception) {
            Log.e("WASALNA_TRACE", "VALHALLA_ROUTE_EXCEPTION: ${e.message}")
            return emptyList()
        }
    }

    private fun fetchRouteFromHeiGit(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double,
        language: String
    ): List<RouteResult> {
        val isAr = language == "ar" || language == "AR"

        try {
            val jsonPayload = JSONObject().apply {
                val coords = JSONArray().apply {
                    put(JSONArray().apply {
                        put(startLng)
                        put(startLat)
                    })
                    put(JSONArray().apply {
                        put(endLng)
                        put(endLat)
                    })
                }
                put("coordinates", coords)
                put("language", if (isAr) "ar" else "en")
                put("units", "m")
                put("instructions", true)
            }

            val requestBuilder = Request.Builder()
                .url(HEIGIT_DIRECTIONS_URL)
                .post(jsonPayload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("User-Agent", "WASALNA-App/1.0 (contact@wasalna.app)")

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) return emptyList()

                val jsonStr = response.body?.string() ?: return emptyList()
                val jsonObject = JSONObject(jsonStr)
                val routes = jsonObject.optJSONArray("routes") ?: return emptyList()

                val result = mutableListOf<RouteResult>()
                for (i in 0 until routes.length()) {
                    val route = routes.getJSONObject(i)
                    val summary = route.optJSONObject("summary")
                    val distanceMeters = summary?.optDouble("distance", 0.0) ?: route.optDouble("distance", 0.0)
                    val durationSeconds = summary?.optDouble("duration", 0.0) ?: route.optDouble("duration", 0.0)
                    
                    val geometryStr = route.optString("geometry", "")

                    val points = if (geometryStr.isNotEmpty()) {
                        val try5 = MapUtils.decodePolyline(geometryStr)
                        val firstP = try5.firstOrNull()
                        if (firstP != null && Math.abs(firstP.latitude) <= 90.0 && Math.abs(firstP.longitude) <= 180.0 && Math.abs(firstP.latitude) > 1.0) {
                            try5
                        } else {
                            MapUtils.decodePolyline6(geometryStr)
                        }
                    } else emptyList()

                    result.add(
                        RouteResult(
                            id = "heigit_ors_route_$i",
                            distanceMeters = distanceMeters,
                            durationSeconds = durationSeconds,
                            polyline = points,
                            steps = emptyList(),
                            provider = providerName
                        )
                    )
                }

                return result
            }
        } catch (e: Exception) {
            Log.e("WASALNA_TRACE", "HEIGIT_ROUTE_EXCEPTION: ${e.message}")
            return emptyList()
        }
    }
}
