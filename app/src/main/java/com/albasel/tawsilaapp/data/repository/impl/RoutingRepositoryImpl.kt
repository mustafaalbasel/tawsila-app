package com.albasel.tawsilaapp.data.repository.impl

import android.util.Log
import android.util.LruCache
import com.albasel.tawsilaapp.data.map.provider.HeiGitRoutingProvider
import com.albasel.tawsilaapp.data.model.RouteOption
import com.albasel.tawsilaapp.data.repository.RoutingRepository
import com.albasel.tawsilaapp.util.MapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class RoutingRepositoryImpl : RoutingRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(4000, TimeUnit.MILLISECONDS)
        .readTimeout(4000, TimeUnit.MILLISECONDS)
        .writeTimeout(4000, TimeUnit.MILLISECONDS)
        .build()

    private val heiGitProvider = HeiGitRoutingProvider(client)
    private val routeCache = LruCache<String, List<RouteOption>>(50)

    override suspend fun getRoute(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double,
        language: String
    ): List<RouteOption> = withContext(Dispatchers.IO) {
        if (startLat == 0.0 || startLng == 0.0 || endLat == 0.0 || endLng == 0.0) {
            return@withContext emptyList()
        }

        val cacheKey = "${(startLat * 10000).toInt()}_${(startLng * 10000).toInt()}_${(endLat * 10000).toInt()}_${(endLng * 10000).toInt()}_$language"
        routeCache.get(cacheKey)?.let {
            Log.d("WASALNA_PERF", "ROUTE_CACHE_HIT: key=$cacheKey, routes=${it.size}")
            return@withContext it
        }

        val t0 = System.currentTimeMillis()
        Log.d("WASALNA_PERF", "ROUTE_REQUEST_START: start=[$startLat, $startLng], end=[$endLat, $endLng]")

        try {
            val heiGitResults = heiGitProvider.getRoute(startLat, startLng, endLat, endLng, language)
            
            if (heiGitResults.isNotEmpty()) {
                val routes = heiGitResults.mapIndexed { i, res ->
                    val distKm = res.distanceMeters / 1000.0
                    val detectedTolls = MapUtils.detectTollGates(res.polyline)
                    RouteOption(
                        id = res.id,
                        distance = distKm,
                        duration = res.durationSeconds,
                        points = res.polyline,
                        tollCount = detectedTolls.size,
                        name = if (i == 0) "الأسرع" else "مسار بديل",
                        steps = res.steps
                    )
                }
                
                routeCache.put(cacheKey, routes)
                val t1 = System.currentTimeMillis()
                Log.d("WASALNA_PERF", "ROUTE_FETCH_SUCCESS: latency=${t1 - t0}ms, points=${routes.firstOrNull()?.points?.size}")
                return@withContext routes
            }
        } catch (e: Exception) {
            Log.e("WASALNA_PERF", "ROUTE_FETCH_ERROR: ${e.message}", e)
        }

        emptyList()
    }
}
