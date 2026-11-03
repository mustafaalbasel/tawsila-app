package com.albasel.tawsilaapp.data.map.proxy

import android.util.Log
import com.albasel.tawsilaapp.data.map.provider.HeiGitRoutingProvider
import com.albasel.tawsilaapp.data.map.provider.PeliasGeocodingProvider
import com.albasel.tawsilaapp.data.map.provider.PlaceSearchProvider
import com.albasel.tawsilaapp.data.map.provider.RoutingProvider
import com.albasel.tawsilaapp.data.model.PlaceResult
import com.albasel.tawsilaapp.data.model.RouteResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

class PeliasPlaceSearchAdapter : PlaceSearchProvider {
    private val provider = PeliasGeocodingProvider()
    override val providerName: String = "Pelias-HeiGIT"

    override suspend fun search(
        query: String,
        userLat: Double?,
        userLng: Double?,
        language: String
    ): List<PlaceResult> {
        return provider.search(query, userLat, userLng, language)
    }

    override suspend fun nearby(
        category: String,
        centerLat: Double,
        centerLng: Double,
        radiusMeters: Double,
        language: String
    ): List<PlaceResult> {
        return provider.search(category, centerLat, centerLng, language)
    }

    override suspend fun reverseGeocode(
        lat: Double,
        lng: Double,
        language: String
    ): PlaceResult? {
        val res = provider.search("location", lat, lng, language)
        return res.firstOrNull()
    }
}

object MapBackendProxy {
    private const val TAG = "MapBackendProxy"

    var activeSearchProvider: PlaceSearchProvider = PeliasPlaceSearchAdapter()
    var activeRoutingProvider: RoutingProvider = HeiGitRoutingProvider()

    // 1. Caches
    private data class CacheEntry<T>(val data: T, val timestamp: Long = System.currentTimeMillis())
    private val searchCache = ConcurrentHashMap<String, CacheEntry<List<PlaceResult>>>()
    private val routeCache = ConcurrentHashMap<String, CacheEntry<List<RouteResult>>>()

    private const val SEARCH_TTL_MS = 24 * 60 * 60 * 1000L // 24 Hours
    private const val ROUTE_TTL_MS = 12 * 60 * 60 * 1000L  // 12 Hours

    // 2. Request Deduplication
    private val activeDeduplicationMutex = Mutex()
    private val activeRequests = ConcurrentHashMap<String, CompletableDeferred<Any?>>()

    // 3. Rate Limiting per User
    private val userRateLimitMutex = Mutex()
    private val userRequestTimestamps = ConcurrentHashMap<String, MutableList<Long>>()
    private const val MAX_REQUESTS_PER_MINUTE = 20

    suspend fun checkRateLimit(userId: String): Boolean {
        userRateLimitMutex.withLock {
            val now = System.currentTimeMillis()
            val timestamps = userRequestTimestamps.getOrPut(userId) { mutableListOf() }
            timestamps.removeAll { now - it > 60_000 }
            if (timestamps.size >= MAX_REQUESTS_PER_MINUTE) {
                return false
            }
            timestamps.add(now)
            return true
        }
    }

    suspend fun search(query: String, userLat: Double?, userLng: Double?, language: String): List<PlaceResult> {
        val normQuery = query.trim().lowercase()
        if (normQuery.isEmpty()) return emptyList()

        val regionCell = if (userLat != null && userLng != null) {
            "${(userLat * 100).toInt()}_${(userLng * 100).toInt()}"
        } else "global"

        val cacheKey = "search_${normQuery}_${regionCell}_$language"

        // Check Cache
        searchCache[cacheKey]?.let { entry ->
            if (System.currentTimeMillis() - entry.timestamp < SEARCH_TTL_MS) {
                Log.d(TAG, "Search Cache HIT for: $normQuery")
                return entry.data
            } else {
                searchCache.remove(cacheKey)
            }
        }

        // Deduplication
        return executeDeduplicated(cacheKey) {
            val results = activeSearchProvider.search(normQuery, userLat, userLng, language)
            if (results.isNotEmpty()) {
                searchCache[cacheKey] = CacheEntry(results)
            }
            results
        } as List<PlaceResult>
    }

    suspend fun getRoute(startLat: Double, startLng: Double, endLat: Double, endLng: Double, language: String): List<RouteResult> {
        val routeKey = "route_${(startLat * 100000).toLong()}_${(startLng * 100000).toLong()}_${(endLat * 100000).toLong()}_${(endLng * 100000).toLong()}_$language"

        // Check Cache
        routeCache[routeKey]?.let { entry ->
            if (System.currentTimeMillis() - entry.timestamp < ROUTE_TTL_MS) {
                Log.d(TAG, "Route Cache HIT for key: $routeKey")
                return entry.data
            } else {
                routeCache.remove(routeKey)
            }
        }

        // Deduplication
        return executeDeduplicated(routeKey) {
            val results = activeRoutingProvider.getRoute(startLat, startLng, endLat, endLng, language)
            if (results.isNotEmpty()) {
                routeCache[routeKey] = CacheEntry(results)
            }
            results
        } as List<RouteResult>
    }

    suspend fun reverseGeocode(lat: Double, lng: Double, language: String): PlaceResult? {
        val cacheKey = "rev_${(lat * 1000).toInt()}_${(lng * 1000).toInt()}_$language"
        searchCache[cacheKey]?.let { entry ->
            if (System.currentTimeMillis() - entry.timestamp < SEARCH_TTL_MS && entry.data.isNotEmpty()) {
                return entry.data.first()
            }
        }

        val res = activeSearchProvider.reverseGeocode(lat, lng, language)
        if (res != null) {
            searchCache[cacheKey] = CacheEntry(listOf(res))
        }
        return res
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> executeDeduplicated(key: String, block: suspend () -> T): T {
        var deferred: CompletableDeferred<Any?>? = null
        var isOwner = false

        activeDeduplicationMutex.withLock {
            val existing = activeRequests[key]
            if (existing != null) {
                deferred = existing
            } else {
                val newDeferred = CompletableDeferred<Any?>()
                activeRequests[key] = newDeferred
                deferred = newDeferred
                isOwner = true
            }
        }

        if (!isOwner) {
            Log.d(TAG, "Request Deduplicated for key: $key")
            return deferred!!.await() as T
        }

        try {
            val result = block()
            deferred!!.complete(result)
            return result
        } catch (e: Exception) {
            deferred!!.completeExceptionally(e)
            throw e
        } finally {
            activeDeduplicationMutex.withLock {
                activeRequests.remove(key)
            }
        }
    }
}
