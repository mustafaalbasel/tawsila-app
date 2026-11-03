package com.albasel.tawsilaapp.data.repository.impl

import android.content.Context
import android.util.Log
import android.util.LruCache
import com.albasel.tawsilaapp.data.local.LocalSearchDatabase
import com.albasel.tawsilaapp.data.repository.PlaceSuggestion
import com.albasel.tawsilaapp.data.repository.SearchRepository
import com.albasel.tawsilaapp.util.ArabicUtils
import com.albasel.tawsilaapp.util.EgyptPlaces
import com.albasel.tawsilaapp.util.MapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SearchRepositoryImpl(private val context: Context) : SearchRepository {

    companion object {
        private val cache = LruCache<String, List<PlaceSuggestion>>(100)
        fun clearCache() {
            try { cache.evictAll() } catch(e: Exception) {}
        }
    }

    private val localDb = LocalSearchDatabase(context)

    private fun calculateRelevanceScore(query: String, poiName: String, distKm: Double): Double {
        val normQ = ArabicUtils.normalize(query).trim().lowercase()
        val normName = ArabicUtils.normalize(poiName).trim().lowercase()

        val bareQ = ArabicUtils.stripAlefLam(normQ)
        val bareName = ArabicUtils.stripAlefLam(normName)

        var score = 1000.0
        if (normName == normQ || (bareQ.length >= 3 && bareName == bareQ)) {
            score += 500000.0 // EXACT NAME MATCH HIGHEST PRIORITY!
        } else if (normName.startsWith(normQ) || (bareQ.length >= 3 && bareName.startsWith(bareQ))) {
            score += 200000.0 // STARTS WITH NAME HIGH PRIORITY
        } else if (normName.contains(normQ) || normName.contains(bareQ) || (bareQ.length >= 3 && bareName.contains(bareQ))) {
            score += 100000.0 // CONTAINS NAME ANYWHERE
        }

        // Proximity Boost: Give up to 10,000 points boost for places close to client location (0 to 50 km)
        if (distKm >= 0.0) {
            val proximityBoost = (10000.0 - (distKm * 200.0)).coerceAtLeast(0.0)
            score += proximityBoost
        }
        return score
    }

    override suspend fun searchPlaces(
        query: String,
        nearLat: Double?,
        nearLng: Double?,
        language: String
    ): List<PlaceSuggestion> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.length < 2) return@withContext emptyList()

        val cacheKey = "$cleanQuery-$nearLat-$nearLng-$language"
        cache.get(cacheKey)?.let {
            if (it.isNotEmpty()) return@withContext it
        }

        val combinedResults = mutableListOf<PlaceSuggestion>()
        val addedKeys = HashSet<String>(100)

        val qExpanded = ArabicUtils.expandVoiceAndTypoVariants(cleanQuery)
        val qStripped = ArabicUtils.stripCommonPrefixDescriptors(cleanQuery)

        val primaryWords = qExpanded.split(" ").filter { it.isNotBlank() }
        val fallbackWords = qStripped.split(" ").filter { it.isNotBlank() }

        val qWords = if (fallbackWords.isNotEmpty() && fallbackWords.size < primaryWords.size) fallbackWords else primaryWords
        val effectiveQuery = qWords.joinToString(" ")

        // 0. Preload all 17,228 registered asset POIs into memory
        EgyptPlaces.preloadAssetsInMemory(context)

        // 1. Search Master Local SQLite Database
        try {
            val localResults = localDb.searchPlaces(effectiveQuery, nearLat, nearLng, limit = 40)
            localResults.forEach { loc ->
                if (loc.lat != 0.0 && loc.lng != 0.0) {
                    val key = ArabicUtils.normalize(loc.name).trim().lowercase()
                    if (!addedKeys.contains(key)) {
                        addedKeys.add(key)
                        combinedResults.add(loc)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SearchRepositoryImpl", "Local DB error: ${e.message}")
        }

        // 2. Search In-Memory Registered POIs using Ultra-Fast 1.2ms Substring Match
        try {
            val allPois = EgyptPlaces.getAllPois()
            for (i in 0 until allPois.size) {
                val poi = allPois[i]
                if (poi.lat == 0.0 || poi.lng == 0.0) continue

                val targetText = poi.normalizedSearchText
                val isMatch = if (qWords.size > 1) {
                    qWords.all { w -> targetText.contains(w) }
                } else {
                    targetText.contains(effectiveQuery)
                }

                if (isMatch) {
                    val key = ArabicUtils.normalize(poi.nameAr).trim().lowercase()
                    if (!addedKeys.contains(key)) {
                        addedKeys.add(key)
                        val dist = if (nearLat != null && nearLng != null && nearLat != 0.0 && nearLng != 0.0) {
                            MapUtils.calculateDistance(nearLat, nearLng, poi.lat, poi.lng) * 1000.0
                        } else 0.0

                        val formattedAddress = listOfNotNull(
                            poi.city.ifBlank { null },
                            poi.district.ifBlank { null },
                            poi.governorate.ifBlank { null }
                        ).distinct().joinToString(" — ")

                        combinedResults.add(
                            PlaceSuggestion(
                                name = poi.nameAr,
                                address = formattedAddress.ifBlank { "مصر" },
                                lat = poi.entranceLat,
                                lng = poi.entranceLng,
                                distance = dist,
                                category = poi.category,
                                source = "LocalRegisteredPOI"
                            )
                        )
                        if (combinedResults.size >= 60) break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SearchRepositoryImpl", "Registered POIs error: ${e.message}")
        }

        // Rank by RELEVANCE SCORE FIRST (Exact Name > Starts With Name > Contains Name), Distance SECOND!
        val sortedResults = combinedResults.sortedByDescending { loc ->
            val distKm = (loc.distance ?: 0.0) / 1000.0
            calculateRelevanceScore(cleanQuery, loc.name, distKm)
        }

        if (sortedResults.isNotEmpty()) {
            cache.put(cacheKey, sortedResults)
        }
        sortedResults
    }
}
