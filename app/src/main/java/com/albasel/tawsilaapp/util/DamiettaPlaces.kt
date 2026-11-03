package com.albasel.tawsilaapp.util

import android.content.Context
import android.util.Log
import com.albasel.tawsilaapp.data.local.LocalSearchDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DamiettaPOI(
    val nameAr: String,
    val nameEn: String = "",
    val category: String = "Landmark",
    val lat: Double,
    val lng: Double,
    val district: String = "",
    val placeId: String = "",
    val aliases: List<String> = emptyList(),
    val governorate: String = "دمياط",
    val city: String = "دمياط الجديدة",
    val street: String = "",
    val entranceLat: Double = lat,
    val entranceLng: Double = lng,
    val locationSource: String = "GPS_Verified_2026",
    val locationVerified: Boolean = true,
    val confidence: String = "HIGH",
    val verificationDate: String = "2026-10-10"
)

object DamiettaPlaces {
    val POIS = java.util.concurrent.CopyOnWriteArrayList<DamiettaPOI>()
    val drivePois = java.util.concurrent.CopyOnWriteArrayList<DamiettaPOI>()
    val assetPois = java.util.concurrent.CopyOnWriteArrayList<DamiettaPOI>()
    val onPoisUpdated = kotlinx.coroutines.flow.MutableStateFlow(0)

    fun updateDrivePois(newPois: List<DamiettaPOI>) {
        synchronized(this) {
            drivePois.clear()
            drivePois.addAll(newPois)

            newPois.forEach { updatedPoi ->
                val cleanCodeId = updatedPoi.placeId.trim()
                val cleanName = updatedPoi.nameAr.trim()
                val normName = com.albasel.tawsilaapp.util.ArabicUtils.normalize(cleanName)

                val idx = POIS.indexOfFirst { poi ->
                    (cleanCodeId.isNotBlank() && poi.placeId.trim() == cleanCodeId) ||
                    (cleanName.isNotBlank() && poi.nameAr.trim() == cleanName) ||
                    (normName.isNotBlank() && com.albasel.tawsilaapp.util.ArabicUtils.normalize(poi.nameAr.trim()) == normName) ||
                    (Math.abs(poi.lat - updatedPoi.lat) < 0.0001 && Math.abs(poi.lng - updatedPoi.lng) < 0.0001)
                }

                if (idx >= 0) {
                    POIS[idx] = updatedPoi.copy(placeId = cleanCodeId.ifBlank { POIS[idx].placeId })
                } else {
                    POIS.add(0, updatedPoi)
                }
            }
            com.albasel.tawsilaapp.data.repository.impl.SearchRepositoryImpl.clearCache()
            onPoisUpdated.value++
        }
    }

    fun getAllPois(): List<DamiettaPOI> {
        if (assetPois.isEmpty()) {
            return (POIS + drivePois).distinctBy { it.placeId.ifBlank { it.nameAr } }
        }
        val registeredKeys = assetPois.mapTo(HashSet()) { it.placeId.ifBlank { it.nameAr } }
        val registeredNames = assetPois.mapTo(HashSet()) { com.albasel.tawsilaapp.util.ArabicUtils.normalize(it.nameAr) }

        val resultMap = assetPois.associateBy { it.placeId.ifBlank { it.nameAr } }.toMutableMap()

        (drivePois + POIS).forEach { updatedPoi ->
            val key = updatedPoi.placeId.ifBlank { updatedPoi.nameAr }
            val normName = com.albasel.tawsilaapp.util.ArabicUtils.normalize(updatedPoi.nameAr)
            if (registeredKeys.contains(key) || registeredNames.contains(normName)) {
                val matchedKey = resultMap.keys.find { k -> 
                    k == key || com.albasel.tawsilaapp.util.ArabicUtils.normalize(resultMap[k]?.nameAr ?: "") == normName 
                } ?: key
                resultMap[matchedKey] = updatedPoi.copy(placeId = matchedKey)
            }
        }

        return resultMap.values.toList()
    }

    suspend fun loadFromCsvAssetsToDatabase(context: Context) = withContext(Dispatchers.IO) {
        try {
            val t0 = android.os.SystemClock.elapsedRealtime()
            val localDb = LocalSearchDatabase.getInstance(context)
            val inputStream = context.assets.open("destination_routes_112.csv")
            val reader = inputStream.bufferedReader(Charsets.UTF_8)
            val entries = ArrayList<com.albasel.tawsilaapp.data.local.PlaceEntry>(18000)
            val loadedMemoryPois = ArrayList<DamiettaPOI>(18000)

            var rowCounter = 1
            reader.useLines { lines ->
                lines.drop(1).forEach { line ->
                    if (line.isNotBlank()) {
                        val parts = parseFastCsvLine(line)
                        if (parts.size >= 7) {
                            val rawCode = parts[0].trim()
                            val codeId = if (rawCode.toIntOrNull() != null) rawCode else rowCounter.toString()
                            rowCounter++

                            val nameAr = parts[1].trim()
                            val districtRaw = parts.getOrNull(2)?.trim() ?: ""
                            val govRaw = parts.getOrNull(3)?.trim() ?: ""
                            val cityRaw = parts.getOrNull(4)?.trim() ?: ""

                            val district = districtRaw.ifBlank { "مصر" }
                            val governorate = govRaw.ifBlank { "مصر" }
                            val city = cityRaw.ifBlank { nameAr.ifBlank { "مصر" } }

                            val lat = parts[5].toDoubleOrNull() ?: 0.0
                            val lng = parts[6].toDoubleOrNull() ?: 0.0

                            val is13Cols = parts.size >= 13 || (parts.size >= 10 && parts.getOrNull(7)?.toDoubleOrNull() != null)

                            val category = if (is13Cols) parts.getOrNull(9)?.trim()?.ifBlank { "Landmark" } ?: "Landmark"
                                           else parts.getOrNull(7)?.trim()?.ifBlank { "Landmark" } ?: "Landmark"

                            val source = if (is13Cols) parts.getOrNull(10)?.trim()?.ifBlank { "Assets_CSV" } ?: "Assets_CSV"
                                         else parts.getOrNull(8)?.trim()?.ifBlank { "Assets_CSV" } ?: "Assets_CSV"

                            if (nameAr.isNotBlank() && lat in -90.0..90.0 && lng in -180.0..180.0 && (lat != 0.0 || lng != 0.0)) {
                                entries.add(
                                    com.albasel.tawsilaapp.data.local.PlaceEntry(
                                        id = codeId,
                                        nameAr = nameAr,
                                        nameEn = nameAr,
                                        category = category,
                                        lat = lat,
                                        lng = lng,
                                        entranceLat = lat,
                                        entranceLng = lng,
                                        district = district,
                                        governorate = governorate,
                                        city = city,
                                        source = source,
                                        verified = true
                                    )
                                )
                                loadedMemoryPois.add(
                                    DamiettaPOI(
                                        placeId = codeId,
                                        nameAr = nameAr,
                                        nameEn = nameAr,
                                        category = category,
                                        lat = lat,
                                        lng = lng,
                                        entranceLat = lat,
                                        entranceLng = lng,
                                        district = district,
                                        governorate = governorate,
                                        city = city,
                                        locationSource = source,
                                        locationVerified = true
                                    )
                                )
                            }
                        }
                    }
                }
            }

            assetPois.clear()
            assetPois.addAll(loadedMemoryPois)

            val count = localDb.upsertPlacesBatch(entries)
            Log.d("WASALNA_SEARCH_PERF", "DB_ONE_TIME_IMPORT_TIME: loaded=$count, latency=${android.os.SystemClock.elapsedRealtime() - t0}ms")
        } catch (e: Exception) {
            Log.e("DamiettaPlaces", "Error importing CSV to local DB: ${e.message}", e)
        }
    }

    private fun parseFastCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (i in 0 until line.length) {
            val c = line[i]
            if (c == '"') {
                inQuotes = !inQuotes
            } else if (c == ',' && !inQuotes) {
                result.add(sb.toString())
                sb.setLength(0)
            } else {
                sb.append(c)
            }
        }
        result.add(sb.toString())
        return result
    }
}
