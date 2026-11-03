package com.albasel.tawsilaapp.data.map.provider

import android.util.Log
import com.albasel.tawsilaapp.data.model.PlaceResult
import com.albasel.tawsilaapp.util.MapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

class PeliasGeocodingProvider(private val client: OkHttpClient = OkHttpClient()) {
    companion object {
        private const val PHOTON_SEARCH_URL = "https://photon.komoot.io/api"
    }

    suspend fun search(
        query: String,
        userLat: Double? = null,
        userLng: Double? = null,
        language: String = "ar"
    ): List<PlaceResult> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.length < 2) return@withContext emptyList()

        var results = fetchFromPhoton(cleanQuery, userLat, userLng, language)
        
        // Strip common Egyptian descriptive prefixes if initial search returns empty
        if (results.isEmpty()) {
            val stripped = cleanQuery.replace(Regex("^(قرية|مدينة|مركز|حي|شارع|ش|مستشفى|محطة|موقف)\\s+"), "").trim()
            if (stripped.length >= 2 && stripped != cleanQuery) {
                results = fetchFromPhoton(stripped, userLat, userLng, language)
            }
        }

        return@withContext results
    }

    private fun fetchFromPhoton(
        cleanQuery: String,
        userLat: Double?,
        userLng: Double?,
        language: String
    ): List<PlaceResult> {
        try {
            val encodedQuery = URLEncoder.encode("$cleanQuery, Egypt", "UTF-8")
            var url = "$PHOTON_SEARCH_URL/?q=$encodedQuery&lang=$language"
            if (userLat != null && userLng != null && userLat != 0.0 && userLng != 0.0) {
                url += "&lat=$userLat&lon=$userLng"
            }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "WASALNA-App/1.0 (contact@wasalna.app)")
                .build()

            Log.d("WASALNA_TRACE", "PHOTON_SEARCH_START: query=$cleanQuery, url=$url")

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("WASALNA_TRACE", "PHOTON_SEARCH_ERROR: HTTP ${response.code}")
                    return emptyList()
                }

                val jsonStr = response.body?.string() ?: return emptyList()
                val jsonObject = JSONObject(jsonStr)
                val features = jsonObject.optJSONArray("features") ?: return emptyList()

                val results = mutableListOf<PlaceResult>()
                for (i in 0 until features.length()) {
                    val feat = features.getJSONObject(i)
                    val geom = feat.optJSONObject("geometry") ?: continue
                    val coords = geom.optJSONArray("coordinates") ?: continue
                    if (coords.length() < 2) continue

                    val lng = coords.getDouble(0) // Longitude FIRST in GeoJSON
                    val lat = coords.getDouble(1) // Latitude SECOND in GeoJSON

                    if (lat == 0.0 || lng == 0.0) continue

                    val props = feat.optJSONObject("properties") ?: JSONObject()
                    val name = props.optString("name").takeIf { it.isNotBlank() }
                        ?: props.optString("street").takeIf { it.isNotBlank() }
                        ?: props.optString("city").takeIf { it.isNotBlank() }
                        ?: cleanQuery

                    val city = props.optString("city", "")
                    val state = props.optString("state", "")
                    val country = props.optString("country", "مصر")
                    val fullAddress = listOf(name, city, state, country).filter { it.isNotBlank() }.distinct().joinToString(" - ")

                    val dist = if (userLat != null && userLng != null && userLat != 0.0 && userLng != 0.0) {
                        MapUtils.calculateDistance(userLat, userLng, lat, lng) * 1000.0
                    } else 0.0

                    results.add(
                        PlaceResult(
                            id = "photon_$i",
                            providerId = "photon_$i",
                            provider = "Photon-Komoot",
                            name = name,
                            address = fullAddress,
                            latitude = lat,
                            longitude = lng,
                            category = props.optString("type", "general"),
                            distanceMeters = dist
                        )
                    )
                }

                Log.d("WASALNA_TRACE", "PHOTON_SEARCH_SUCCESS: count=${results.size}")
                return results
            }
        } catch (e: Exception) {
            Log.e("WASALNA_TRACE", "PHOTON_SEARCH_EXCEPTION: ${e.message}")
            return emptyList()
        }
    }
}
