package com.albasel.tawsilaapp.data.map.provider

import android.util.Log
import com.albasel.tawsilaapp.data.model.PlaceResult
import com.albasel.tawsilaapp.util.MapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder

class NominatimPlaceSearchProvider(private val client: OkHttpClient = OkHttpClient()) : PlaceSearchProvider {
    override val providerName: String = "Nominatim"

    override suspend fun search(query: String, userLat: Double?, userLng: Double?, language: String): List<PlaceResult> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return@withContext emptyList()

        try {
            val encodedQuery = URLEncoder.encode("$cleanQuery, مصر", "UTF-8")
            val viewboxParam = if (userLat != null && userLng != null) {
                val minLat = userLat - 0.5
                val maxLat = userLat + 0.5
                val minLng = userLng - 0.5
                val maxLng = userLng + 0.5
                "&viewbox=$minLng,$maxLat,$maxLng,$minLat&bounded=0"
            } else ""

            val url = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&accept-language=$language&addressdetails=1&limit=20$viewboxParam"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "WASALNA-App/1.0 (contact@wasalna.app)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val jsonStr = response.body?.string() ?: return@withContext emptyList()
                val jsonArray = JSONArray(jsonStr)

                val results = mutableListOf<PlaceResult>()
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    val osmId = item.optString("osm_id", i.toString())
                    val displayName = item.optString("display_name", "")
                    val lat = item.getDouble("lat")
                    val lng = item.getDouble("lon")
                    val category = item.optString("type", item.optString("class", "general"))

                    val parts = displayName.split(",")
                    val name = parts.firstOrNull()?.trim() ?: cleanQuery
                    val address = parts.drop(1).take(3).joinToString(", ").trim().ifEmpty { displayName }

                    val dist = if (userLat != null && userLng != null) {
                        MapUtils.calculateDistance(userLat, userLng, lat, lng) * 1000.0
                    } else 0.0

                    results.add(
                        PlaceResult(
                            id = "osm_$osmId",
                            providerId = osmId,
                            provider = providerName,
                            name = name,
                            address = address,
                            latitude = lat,
                            longitude = lng,
                            category = category,
                            distanceMeters = dist
                        )
                    )
                }
                if (userLat != null && userLng != null) {
                    results.sortBy { it.distanceMeters }
                }
                results
            }
        } catch (e: Exception) {
            Log.e("NominatimProvider", "Search error: ${e.message}")
            emptyList()
        }
    }

    override suspend fun nearby(category: String, centerLat: Double, centerLng: Double, radiusMeters: Double, language: String): List<PlaceResult> {
        return search(category, centerLat, centerLng, language)
    }

    override suspend fun reverseGeocode(lat: Double, lng: Double, language: String): PlaceResult? = withContext(Dispatchers.IO) {
        try {
            val url = "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lng&format=json&accept-language=$language&addressdetails=1"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "WASALNA-App/1.0 (contact@wasalna.app)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val jsonStr = response.body?.string() ?: return@withContext null
                val item = org.json.JSONObject(jsonStr)
                val osmId = item.optString("osm_id", "rev")
                val displayName = item.optString("display_name", "")
                val addressObj = item.optJSONObject("address")
                val city = addressObj?.optString("city", addressObj.optString("town", addressObj.optString("suburb", ""))) ?: ""
                val state = addressObj?.optString("state", "") ?: ""

                val name = listOfNotNull(city.ifEmpty { null }, state.ifEmpty { null }).joinToString(" - ").ifEmpty { displayName }

                PlaceResult(
                    id = "osm_rev_$osmId",
                    providerId = osmId,
                    provider = providerName,
                    name = name,
                    address = displayName,
                    latitude = lat,
                    longitude = lng,
                    category = "reverse"
                )
            }
        } catch (e: Exception) {
            Log.e("NominatimProvider", "Reverse geocode error: ${e.message}")
            null
        }
    }
}
