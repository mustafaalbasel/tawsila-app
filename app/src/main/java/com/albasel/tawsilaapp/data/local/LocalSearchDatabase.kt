package com.albasel.tawsilaapp.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.albasel.tawsilaapp.data.repository.PlaceSuggestion
import com.albasel.tawsilaapp.util.ArabicUtils
import com.albasel.tawsilaapp.util.MapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalSearchDatabase(private val context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "wasalna_master_places_v16.db"
        private const val DB_VERSION = 16
        private const val TABLE_NAME = "master_places"
        private const val FTS_TABLE = "master_places_fts"
        private const val PREFS_NAME = "wasalna_db_prefs"
        private const val KEY_INITIALIZED = "master_db_initialized_v16"
        private const val TAG = "LocalSearchDatabase"

        @Volatile private var INSTANCE: LocalSearchDatabase? = null

        fun getInstance(context: Context): LocalSearchDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LocalSearchDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE_NAME (
                id TEXT PRIMARY KEY,
                name_ar TEXT NOT NULL,
                name_en TEXT,
                category TEXT,
                lat REAL NOT NULL,
                lng REAL NOT NULL,
                entrance_lat REAL,
                entrance_lng REAL,
                district TEXT,
                city TEXT,
                governorate TEXT,
                street TEXT,
                source TEXT,
                verified INTEGER DEFAULT 1,
                updated_at INTEGER
            )
        """)

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_places_coords ON $TABLE_NAME(lat, lng)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_places_name_ar ON $TABLE_NAME(name_ar)")

        try {
            db.execSQL("""
                CREATE VIRTUAL TABLE IF NOT EXISTS $FTS_TABLE USING fts5(
                    place_id,
                    name_ar,
                    name_en,
                    district,
                    city,
                    governorate,
                    norm_text
                )
            """)
        } catch (e: Exception) {
            Log.w(TAG, "FTS5 table creation skipped (not supported on device): ${e.message}")
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        try { db.execSQL("DROP TABLE IF EXISTS $FTS_TABLE") } catch (e: Exception) {}
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        prefs.edit().putBoolean(KEY_INITIALIZED, false).apply()
        onCreate(db)
    }

    suspend fun isDatabaseInitialized(): Boolean = withContext(Dispatchers.IO) {
        prefs.getBoolean(KEY_INITIALIZED, false)
    }

    suspend fun getMaxCodeId(): Int = withContext(Dispatchers.IO) {
        try {
            val dbPlaces = getAllPlacesList()
            val dbMax = dbPlaces.mapNotNull { it.id.trim().toIntOrNull() }.maxOrNull() ?: 17207
            val memPlaces = com.albasel.tawsilaapp.util.DamiettaPlaces.getAllPois()
            val memMax = memPlaces.mapNotNull { it.placeId.trim().toIntOrNull() }.maxOrNull() ?: 17207
            return@withContext maxOf(dbMax, memMax, 17207)
        } catch (e: Exception) {
            17207
        }
    }

    suspend fun getCodeIdForPlace(nameAr: String): String? = withContext(Dispatchers.IO) {
        val cleanName = nameAr.trim()
        if (cleanName.isBlank()) return@withContext null
        try {
            val db = readableDatabase
            // 1. Exact match
            db.rawQuery("SELECT id FROM $TABLE_NAME WHERE name_ar = ? OR name_en = ? LIMIT 1", arrayOf(cleanName, cleanName)).use { cursor ->
                if (cursor.moveToFirst()) {
                    val valId = cursor.getString(0)
                    if (!valId.isNullOrBlank()) return@withContext valId
                }
            }
            // 2. Flexible match
            db.rawQuery("SELECT id FROM $TABLE_NAME WHERE name_ar LIKE ? LIMIT 1", arrayOf("%$cleanName%")).use { cursor ->
                if (cursor.moveToFirst()) {
                    val valId = cursor.getString(0)
                    if (!valId.isNullOrBlank()) return@withContext valId
                }
            }
        } catch (e: Exception) {}
        null
    }

    suspend fun deletePlaceByCodeId(codeId: String): Int = withContext(Dispatchers.IO) {
        if (codeId.isBlank()) return@withContext 0
        try {
            val db = writableDatabase
            val count = db.delete(TABLE_NAME, "id = ?", arrayOf(codeId.trim()))
            try { db.delete(FTS_TABLE, "place_id = ?", arrayOf(codeId.trim())) } catch (e: Exception) {}
            count
        } catch (e: Exception) { 0 }
    }

    suspend fun getRecordCount(): Long = withContext(Dispatchers.IO) {
        try {
            val db = readableDatabase
            db.rawQuery("SELECT COUNT(*) FROM $TABLE_NAME", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else 0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    private var ftsSupportedCache: Boolean? = null

    private fun isFtsSupported(db: SQLiteDatabase): Boolean {
        if (ftsSupportedCache != null) return ftsSupportedCache!!
        ftsSupportedCache = try {
            db.rawQuery("SELECT 1 FROM $FTS_TABLE LIMIT 1", null).use { true }
        } catch (e: Exception) {
            false
        }
        return ftsSupportedCache!!
    }

    suspend fun upsertPlacesBatch(entries: List<PlaceEntry>): Int = withContext(Dispatchers.IO) {
        if (entries.isEmpty()) return@withContext 0
        val db = writableDatabase
        var insertedCount = 0

        val insertSql = """
            INSERT OR REPLACE INTO $TABLE_NAME (
                id, name_ar, name_en, category, lat, lng, entrance_lat, entrance_lng,
                district, city, governorate, street, source, verified, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        val statement = db.compileStatement(insertSql)

        db.beginTransaction()
        try {
            entries.forEach { entry ->
                if (entry.lat !in -90.0..90.0 || entry.lng !in -180.0..180.0 || entry.nameAr.isBlank()) {
                    return@forEach
                }

                val stableId = entry.id.ifBlank {
                    "${entry.nameAr.trim().lowercase()}_${(entry.lat * 10000).toInt()}_${(entry.lng * 10000).toInt()}"
                }

                statement.clearBindings()
                statement.bindString(1, stableId)
                statement.bindString(2, entry.nameAr.trim())
                statement.bindString(3, entry.nameEn.trim())
                statement.bindString(4, entry.category)
                statement.bindDouble(5, entry.lat)
                statement.bindDouble(6, entry.lng)
                statement.bindDouble(7, if (entry.entranceLat != 0.0) entry.entranceLat else entry.lat)
                statement.bindDouble(8, if (entry.entranceLng != 0.0) entry.entranceLng else entry.lng)
                statement.bindString(9, entry.district.trim())
                statement.bindString(10, entry.city.trim())
                statement.bindString(11, entry.governorate.trim())
                statement.bindString(12, entry.street.trim())
                statement.bindString(13, entry.source)
                statement.bindLong(14, if (entry.verified) 1L else 0L)
                statement.bindLong(15, entry.updatedAt)

                statement.executeInsert()
                insertedCount++
            }

            db.setTransactionSuccessful()
            prefs.edit().putBoolean(KEY_INITIALIZED, true).apply()
            Log.d(TAG, "Batch upserted $insertedCount places into master database successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Batch upsert error: ${e.message}", e)
        } finally {
            db.endTransaction()
        }
        insertedCount
    }

    suspend fun deletePlace(placeIdOrName: String): Boolean = withContext(Dispatchers.IO) {
        if (placeIdOrName.isBlank()) return@withContext false
        val db = writableDatabase
        var deleted = false
        db.beginTransaction()
        try {
            val rows = db.delete(TABLE_NAME, "id = ? OR name_ar = ?", arrayOf(placeIdOrName, placeIdOrName))
            try { db.delete(FTS_TABLE, "place_id = ? OR name_ar = ?", arrayOf(placeIdOrName, placeIdOrName)) } catch (e: Exception) {}
            db.setTransactionSuccessful()
            deleted = rows > 0
            Log.d(TAG, "Deleted place $placeIdOrName from master database ($rows rows).")
        } catch (e: Exception) {
            Log.e(TAG, "Delete error: ${e.message}", e)
        } finally {
            db.endTransaction()
        }
        deleted
    }

    private fun getWordSqlLikePatterns(rawWord: String): List<String> {
        val norm = ArabicUtils.normalize(rawWord)
        val bare = ArabicUtils.stripAlefLam(rawWord)
        val raw = rawWord.trim().lowercase()

        val patterns = mutableSetOf<String>()
        if (raw.isNotBlank()) patterns.add("%$raw%")
        if (norm.isNotBlank()) patterns.add("%$norm%")
        if (bare.isNotBlank()) patterns.add("%$bare%")

        if (norm.endsWith("ه") || norm.endsWith("ة") || raw.endsWith("ه") || raw.endsWith("ة")) {
            val base = if (norm.length > 1) norm.substring(0, norm.length - 1) else norm
            if (base.isNotBlank()) patterns.add("%${base}_%")
        }

        if (norm.startsWith("ا") || raw.startsWith("أ") || raw.startsWith("إ") || raw.startsWith("آ")) {
            val base = if (norm.length > 1) norm.substring(1) else norm
            if (base.isNotBlank()) patterns.add("%_${base}%")
        }

        return patterns.toList()
    }

    suspend fun searchPlaces(
        query: String,
        nearLat: Double? = null,
        nearLng: Double? = null,
        limit: Int = 30
    ): List<PlaceSuggestion> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.length < 2) return@withContext emptyList()

        val results = mutableListOf<ScoredResult>()
        val db = readableDatabase

        val rawWords = cleanQuery.split(" ").filter { it.isNotBlank() }
        val whereClauses = mutableListOf<String>()
        val queryArgs = mutableListOf<String>()

        rawWords.forEach { word ->
            val wordPatterns = getWordSqlLikePatterns(word)
            if (wordPatterns.isNotEmpty()) {
                val subClauses = mutableListOf<String>()
                wordPatterns.forEach { pattern ->
                    subClauses.add("name_ar LIKE ? OR district LIKE ? OR city LIKE ? OR name_en LIKE ?")
                    queryArgs.add(pattern)
                    queryArgs.add(pattern)
                    queryArgs.add(pattern)
                    queryArgs.add(pattern)
                }
                whereClauses.add("(${subClauses.joinToString(" OR ")})")
            }
        }

        val sql = if (whereClauses.isNotEmpty()) {
            "SELECT * FROM $TABLE_NAME WHERE ${whereClauses.joinToString(" AND ")} LIMIT 200"
        } else {
            "SELECT * FROM $TABLE_NAME LIMIT 200"
        }

        try {
            db.rawQuery(sql, queryArgs.toTypedArray()).use { cursor ->
                val nameArIdx = cursor.getColumnIndex("name_ar")
                val nameEnIdx = cursor.getColumnIndex("name_en")
                val catIdx = cursor.getColumnIndex("category")
                val latIdx = cursor.getColumnIndex("lat")
                val lngIdx = cursor.getColumnIndex("lng")
                val eLatIdx = cursor.getColumnIndex("entrance_lat")
                val eLngIdx = cursor.getColumnIndex("entrance_lng")
                val districtIdx = cursor.getColumnIndex("district")
                val cityIdx = cursor.getColumnIndex("city")
                val govIdx = cursor.getColumnIndex("governorate")
                val sourceIdx = cursor.getColumnIndex("source")

                while (cursor.moveToNext()) {
                    val pLat = cursor.getDouble(latIdx)
                    val pLng = cursor.getDouble(lngIdx)
                    if (pLat !in -90.0..90.0 || pLng !in -180.0..180.0 || (pLat == 0.0 && pLng == 0.0)) continue

                    val pELat = cursor.getDouble(eLatIdx).let { if (it != 0.0) it else pLat }
                    val pELng = cursor.getDouble(eLngIdx).let { if (it != 0.0) it else pLng }

                    val nameAr = cursor.getString(nameArIdx) ?: ""
                    val nameEn = cursor.getString(nameEnIdx) ?: ""
                    val category = cursor.getString(catIdx) ?: "Landmark"
                    val district = cursor.getString(districtIdx) ?: ""
                    val city = cursor.getString(cityIdx) ?: ""
                    val governorate = cursor.getString(govIdx) ?: ""
                    val source = cursor.getString(sourceIdx) ?: "Local"

                    val displayName = nameAr.ifBlank { nameEn.ifBlank { "موقع غير مسمى" } }
                    val fullCandidateText = "$displayName $district $city $governorate"

                    if (ArabicUtils.smartMatch(cleanQuery, fullCandidateText)) {
                        val normName = ArabicUtils.normalize(displayName)
                        val normQ = ArabicUtils.normalize(cleanQuery)

                        var score = 1000.0
                        if (normName == normQ) score += 100000.0
                        else if (normName.startsWith(normQ)) score += 50000.0
                        else if (normName.contains(normQ)) score += 10000.0

                        if (nearLat != null && nearLng != null && nearLat != 0.0 && nearLng != 0.0) {
                            val distKm = MapUtils.calculateDistance(nearLat, nearLng, pLat, pLng)
                            score += (300.0 - (distKm * 5.0)).coerceAtLeast(0.0)
                        }

                        val formattedAddress = listOfNotNull(
                            city.ifBlank { null },
                            district.ifBlank { null },
                            governorate.ifBlank { null }
                        ).distinct().joinToString(" — ")

                        results.add(
                            ScoredResult(
                                PlaceSuggestion(
                                    name = displayName,
                                    address = formattedAddress.ifBlank { "مصر" },
                                    lat = pELat,
                                    lng = pELng,
                                    distance = if (nearLat != null && nearLng != null) MapUtils.calculateDistance(nearLat, nearLng, pLat, pLng) * 1000.0 else 0.0,
                                    category = category,
                                    source = source
                                ),
                                score
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search error: ${e.message}", e)
        }

        results.sortedByDescending { it.score }
            .map { it.suggestion }
            .distinctBy { ArabicUtils.normalize(it.name) }
            .take(limit)
    }

    suspend fun getAllPlacesList(): List<PlaceEntry> = withContext(Dispatchers.IO) {
        val list = mutableListOf<PlaceEntry>()
        val db = readableDatabase
        try {
            db.rawQuery("SELECT * FROM $TABLE_NAME LIMIT 20000", null).use { cursor ->
                val idIdx = cursor.getColumnIndex("id")
                val nameArIdx = cursor.getColumnIndex("name_ar")
                val nameEnIdx = cursor.getColumnIndex("name_en")
                val catIdx = cursor.getColumnIndex("category")
                val latIdx = cursor.getColumnIndex("lat")
                val lngIdx = cursor.getColumnIndex("lng")
                val eLatIdx = cursor.getColumnIndex("entrance_lat")
                val eLngIdx = cursor.getColumnIndex("entrance_lng")
                val districtIdx = cursor.getColumnIndex("district")
                val cityIdx = cursor.getColumnIndex("city")
                val govIdx = cursor.getColumnIndex("governorate")
                val streetIdx = cursor.getColumnIndex("street")
                val sourceIdx = cursor.getColumnIndex("source")
                val verIdx = cursor.getColumnIndex("verified")
                val updatedIdx = cursor.getColumnIndex("updated_at")

                while (cursor.moveToNext()) {
                    val id = if (idIdx >= 0) cursor.getString(idIdx) ?: "" else ""
                    val nameAr = if (nameArIdx >= 0) cursor.getString(nameArIdx) ?: "" else ""
                    val nameEn = if (nameEnIdx >= 0) cursor.getString(nameEnIdx) ?: "" else ""
                    val cat = if (catIdx >= 0) cursor.getString(catIdx) ?: "Landmark" else "Landmark"
                    val lat = if (latIdx >= 0) cursor.getDouble(latIdx) else 0.0
                    val lng = if (lngIdx >= 0) cursor.getDouble(lngIdx) else 0.0
                    val eLat = if (eLatIdx >= 0) cursor.getDouble(eLatIdx) else lat
                    val eLng = if (eLngIdx >= 0) cursor.getDouble(eLngIdx) else lng
                    val dist = if (districtIdx >= 0) cursor.getString(districtIdx) ?: "" else ""
                    val city = if (cityIdx >= 0) cursor.getString(cityIdx) ?: "" else ""
                    val gov = if (govIdx >= 0) cursor.getString(govIdx) ?: "" else ""
                    val street = if (streetIdx >= 0) cursor.getString(streetIdx) ?: "" else ""
                    val src = if (sourceIdx >= 0) cursor.getString(sourceIdx) ?: "Local" else "Local"
                    val ver = if (verIdx >= 0) cursor.getInt(verIdx) == 1 else true
                    val updated = if (updatedIdx >= 0) cursor.getLong(updatedIdx) else 0L

                    list.add(
                        PlaceEntry(
                            id = id,
                            nameAr = nameAr,
                            nameEn = nameEn,
                            category = cat,
                            lat = lat,
                            lng = lng,
                            entranceLat = eLat,
                            entranceLng = eLng,
                            district = dist,
                            city = city,
                            governorate = gov,
                            street = street,
                            source = src,
                            verified = ver,
                            updatedAt = updated
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "GetAllPlaces error: ${e.message}", e)
        }
        list
    }

    private data class ScoredResult(val suggestion: PlaceSuggestion, val score: Double)
}
