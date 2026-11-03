package com.albasel.tawsilaapp.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.api.client.http.InputStreamContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.Permission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

object GoogleDriveHelper {

    private const val TAG = "GoogleDriveHelper"
    private const val FOLDER_ID = "1ZMn0MkUPckxumF3GNgcVwilM4h-49sFP"
    private const val MASTER_SPREADSHEET_ID = "1shOeCa6PKh33stcWiOC63TXSq3UeXntfQK-TVM1VGWY" // WASALNA LOCATIONS (Master Backup - 17,207 places)
    private const val DELTA_SPREADSHEET_ID = "1wNgLy_zienecCjZpBCFy4qjzsoCl9ljDwS-ONT7JPEk"  // WASALNA UPDATES (Delta Updates - for User App Sync)
    private const val CLIENT_ID = "781082747890-6271sh7b8gd61sk24t8hquug9dk061ta.apps.googleusercontent.com"

    private fun decryptSecret(encodedB64: String, mask: Byte = 0x42): String {
        return try {
            val bytes = android.util.Base64.decode(encodedB64, android.util.Base64.NO_WRAP)
            val result = ByteArray(bytes.size)
            for (i in bytes.indices) {
                result[i] = (bytes[i].toInt() xor mask.toInt()).toByte()
            }
            String(result, Charsets.UTF_8)
        } catch (e: Exception) { "" }
    }

    private fun getClientSecret(): String {
        val dec = decryptSecret("BQ0BERIabzpyKQkyIBIGLwwLDDYSbzMXNTQyeykLbzgaOAo=")
        return dec
    }

    private fun getRefreshToken(): String {
        val dec = decryptSecret("c21tcnYODwQOdQ53JxESBwElGwsDEAMDBQMTEQw1BG8OewswBioROzVzO3stNncuFiEOcTJxcgwWEzgjFiMaNjQaCC1zFy4xDS51NzcQcAwlLyNvBnQWDwUrCiE4ChobLQkBOgksDw==")
        return dec
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val transport = com.google.api.client.http.javanet.NetHttpTransport()
    private val jsonFactory = com.google.api.client.json.gson.GsonFactory.getDefaultInstance()
    
    private var cachedToken: String? = null
    private var tokenExpiry: Long = 0

    fun invalidateAccessToken() {
        cachedToken = null
        tokenExpiry = 0
    }

    private suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (cachedToken != null && now < tokenExpiry) {
            return@withContext cachedToken
        }

        try {
            val formBody = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("client_secret", getClientSecret())
                .add("refresh_token", getRefreshToken())
                .add("grant_type", "refresh_token")
                .build()

            val request = Request.Builder()
                .url("https://oauth2.googleapis.com/token")
                .post(formBody)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    val token = json.optString("access_token")
                    val expiresIn = json.optLong("expires_in", 3600)
                    
                    if (token.isNotEmpty()) {
                        cachedToken = token
                        tokenExpiry = now + (expiresIn - 60) * 1000 // Buffer of 60 seconds
                        Log.d(TAG, "Access Token obtained and cached")
                        return@withContext token
                    }
                }
                Log.e(TAG, "Token Error: ${response.code} - $body")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network Error getting token: ${e.message}")
        }
        null
    }

    suspend fun uploadImage(imageBytes: ByteArray, fileName: String): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Preparing to upload via Direct OkHttp REST API: $fileName (${imageBytes.size} bytes)")
            val token = getAccessToken() ?: run {
                Log.e(TAG, "Failed to get access token for Drive upload")
                return@withContext null
            }

            val metadataJson = org.json.JSONObject().apply {
                put("name", fileName)
                put("parents", org.json.JSONArray().put(FOLDER_ID))
            }.toString()

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("metadata", "metadata.json", metadataJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .addFormDataPart("file", fileName, imageBytes.toRequestBody("image/jpeg".toMediaType()))
                .build()

            val request = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id")
                .addHeader("Authorization", "Bearer $token")
                .post(requestBody)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val respBody = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val jsonObj = org.json.JSONObject(respBody)
                    val fileId = jsonObj.optString("id")
                    if (fileId.isNotBlank()) {
                        Log.d(TAG, "Successfully uploaded image to Google Drive folder $FOLDER_ID! File ID: $fileId")
                        makeFilePublic(token, fileId)
                        return@withContext fileId
                    }
                } else {
                    Log.e(TAG, "Google Drive Upload Error: HTTP ${response.code} - $respBody")
                    if (response.code == 401) {
                        invalidateAccessToken()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL DRIVE UPLOAD ERROR: ${e.message}", e)
        }
        null
    }

    private fun makeFilePublic(accessToken: String, fileId: String) {
        try {
            val permJson = org.json.JSONObject().apply {
                put("role", "reader")
                put("type", "anyone")
            }.toString()

            val request = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId/permissions")
                .addHeader("Authorization", "Bearer $accessToken")
                .post(permJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            okHttpClient.newCall(request).execute().close()
        } catch (e: Exception) {
            Log.e(TAG, "Non-critical error setting permission: ${e.message}")
        }
    }

    private suspend fun uploadViaGoogleScript(fileName: String, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val scriptUrl = "https://script.google.com/macros/s/AKfycbz_WASALNA_DRIVE_SYNC_V2/exec"
            val formBody = FormBody.Builder()
                .add("folder_id", FOLDER_ID)
                .add("filename", fileName)
                .add("content", content)
                .build()

            val request = Request.Builder()
                .url(scriptUrl)
                .post(formBody)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    Log.d(TAG, "Google Script Upload Success for $fileName: $body")
                    return@withContext true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Google Script Upload Error: ${e.message}")
        }
        false
    }

    suspend fun uploadTextFile(textContent: String, fileName: String, targetFolderId: String = FOLDER_ID): String? = withContext(Dispatchers.IO) {
        try {
            val accessToken = getAccessToken()
            if (accessToken == null) {
                Log.e(TAG, "Cannot upload $fileName: Access Token is null")
                return@withContext null
            }

            val authHeader = "Bearer $accessToken"

            // 1. Find existing file ID in target folder safely using HttpUrl
            val searchUrl = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
                .addQueryParameter("q", "'$targetFolderId' in parents and name = '$fileName' and trashed = false")
                .addQueryParameter("fields", "files(id,name)")
                .addQueryParameter("supportsAllDrives", "true")
                .addQueryParameter("includeItemsFromAllDrives", "true")
                .build()

            val listReq = Request.Builder()
                .url(searchUrl)
                .addHeader("Authorization", authHeader)
                .get()
                .build()

            var existingFileId: String? = null
            okHttpClient.newCall(listReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bodyStr = resp.body?.string() ?: ""
                    val json = JSONObject(bodyStr)
                    val files = json.optJSONArray("files")
                    if (files != null && files.length() > 0) {
                        existingFileId = files.getJSONObject(0).optString("id")
                    }
                } else {
                    Log.e(TAG, "Search file error for $fileName: ${resp.code}")
                }
            }

            val mediaType = "text/csv; charset=UTF-8".toMediaType()
            val textBytes = textContent.toByteArray(Charsets.UTF_8)
            val requestBody = textBytes.toRequestBody(mediaType)

            if (!existingFileId.isNullOrEmpty()) {
                // Update existing file content via PATCH media update
                val patchUrl = "https://www.googleapis.com/upload/drive/v3/files/$existingFileId".toHttpUrl().newBuilder()
                    .addQueryParameter("uploadType", "media")
                    .addQueryParameter("supportsAllDrives", "true")
                    .build()

                val patchReq = Request.Builder()
                    .url(patchUrl)
                    .addHeader("Authorization", authHeader)
                    .patch(requestBody)
                    .build()

                okHttpClient.newCall(patchReq).execute().use { resp ->
                    val bodyStr = resp.body?.string() ?: ""
                    if (resp.isSuccessful) {
                        Log.d(TAG, "Successfully updated Drive file $fileName (ID: $existingFileId)")
                        return@withContext existingFileId
                    } else {
                        Log.e(TAG, "Failed PATCH Drive file $fileName: ${resp.code} $bodyStr")
                    }
                }
            } else {
                // Create new file metadata first
                val metaJson = JSONObject().apply {
                    put("name", fileName)
                    put("parents", org.json.JSONArray().put(targetFolderId))
                    put("mimeType", "text/csv")
                }

                val metaReqBody = metaJson.toString().toRequestBody("application/json; charset=UTF-8".toMediaType())
                val createUrl = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
                    .addQueryParameter("supportsAllDrives", "true")
                    .build()

                val createReq = Request.Builder()
                    .url(createUrl)
                    .addHeader("Authorization", authHeader)
                    .post(metaReqBody)
                    .build()

                var newFileId: String? = null
                okHttpClient.newCall(createReq).execute().use { resp ->
                    val bodyStr = resp.body?.string() ?: ""
                    if (resp.isSuccessful) {
                        val json = JSONObject(bodyStr)
                        newFileId = json.optString("id")
                    } else {
                        Log.e(TAG, "Failed creating metadata for $fileName: ${resp.code} $bodyStr")
                    }
                }

                if (!newFileId.isNullOrEmpty()) {
                    // Upload content to created file
                    val patchUrl = "https://www.googleapis.com/upload/drive/v3/files/$newFileId".toHttpUrl().newBuilder()
                        .addQueryParameter("uploadType", "media")
                        .addQueryParameter("supportsAllDrives", "true")
                        .build()

                    val patchReq = Request.Builder()
                        .url(patchUrl)
                        .addHeader("Authorization", authHeader)
                        .patch(requestBody)
                        .build()

                    okHttpClient.newCall(patchReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            Log.d(TAG, "Successfully created & uploaded Drive file $fileName (ID: $newFileId)")
                            return@withContext newFileId
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in uploadTextFile for $fileName: ${e.message}", e)
        }
        return@withContext null
    }

    suspend fun downloadCsvDirectFromDrive(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val accessToken = getAccessToken() ?: return@withContext false
            val credentials = com.google.auth.oauth2.AccessToken(accessToken, null)
            val requestInitializer = com.google.auth.http.HttpCredentialsAdapter(
                com.google.auth.oauth2.GoogleCredentials.create(credentials)
            )

            val driveService = Drive.Builder(transport, jsonFactory, requestInitializer)
                .setApplicationName("TawsilaApp")
                .build()

            val query = "'$FOLDER_ID' in parents and name = 'destination_routes_112.csv' and trashed = false"
            val driveFiles = driveService.files().list().setQ(query).execute().files

            if (!driveFiles.isNullOrEmpty()) {
                val driveFile = driveFiles[0]
                val outputStream = ByteArrayOutputStream()
                driveService.files().get(driveFile.id).executeMediaAndDownloadTo(outputStream)
                val csvContent = outputStream.toString("UTF-8")

                if (csvContent.isNotBlank()) {
                    val reader = csvContent.reader().buffered()
                    val downloadedPois = ArrayList<EgyptPOI>(18000)
                    val existingNames = HashSet<String>(18000)

                    EgyptPlaces.POIS.forEach { poi ->
                        downloadedPois.add(poi)
                        existingNames.add(poi.nameAr.trim().lowercase())
                    }

                    var loadedCount = 0
                    reader.useLines { lines ->
                        lines.drop(1).forEach { line ->
                            if (line.isNotBlank()) {
                                val parts = parseFastCsvLine(line)
                                if (parts.size >= 7) {
                                    val nameAr = parts[1].trim()
                                    val nameKey = nameAr.lowercase()

                                    if (nameAr.isNotBlank() && !existingNames.contains(nameKey)) {
                                        val lat = parts[5].toDoubleOrNull() ?: 0.0
                                        val lng = parts[6].toDoubleOrNull() ?: 0.0
                                        val eLat = parts.getOrNull(7)?.toDoubleOrNull() ?: lat
                                        val eLng = parts.getOrNull(8)?.toDoubleOrNull() ?: lng

                                        if (lat != 0.0 && lng != 0.0) {
                                            existingNames.add(nameKey)
                                            downloadedPois.add(
                                                EgyptPOI(
                                                    nameAr = nameAr,
                                                    nameEn = nameAr,
                                                    category = "Landmark",
                                                    lat = lat,
                                                    lng = lng,
                                                    entranceLat = eLat,
                                                    entranceLng = eLng,
                                                    district = parts.getOrNull(2) ?: "مصر",
                                                    governorate = parts.getOrNull(3) ?: "مصر",
                                                    city = parts.getOrNull(4) ?: "مصر",
                                                    locationSource = "Google_Drive_Direct",
                                                    locationVerified = true,
                                                    confidence = "HIGH"
                                                )
                                            )
                                            loadedCount++
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Thread-safe update of Drive POIs
                    EgyptPlaces.updateDrivePois(downloadedPois)

                    Log.d(TAG, "Direct Google Drive CSV Loaded safely! Added $loadedCount places. Total POIs: ${EgyptPlaces.getAllPois().size}")
                    return@withContext true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading CSV directly from Google Drive: ${e.message}")
        }
        return@withContext false
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

    private fun parseCsvLineToPoi(parts: List<String>, defaultCodeId: String): EgyptPOI? {
        if (parts.size < 7) return null

        val rawCode = parts[0].trim()
        val codeId = if (rawCode.isNotBlank()) rawCode else defaultCodeId
        val nameAr = parts[1].trim()

        val districtRaw = parts.getOrNull(2)?.trim() ?: ""
        val govRaw = parts.getOrNull(3)?.trim() ?: ""
        val cityRaw = parts.getOrNull(4)?.trim() ?: ""

        val district = districtRaw.ifBlank { "مصر" }
        val governorate = govRaw.ifBlank { "مصر" }
        val city = cityRaw.ifBlank { nameAr.ifBlank { "مصر" } }

        val lat = parts[5].toDoubleOrNull() ?: 0.0
        val lng = parts[6].toDoubleOrNull() ?: 0.0

        // Detect 13-column vs 11-column format
        val is13Cols = parts.size >= 13 || (parts.size >= 10 && parts.getOrNull(7)?.toDoubleOrNull() != null)

        val eLat = if (is13Cols) parts.getOrNull(7)?.toDoubleOrNull() ?: lat else lat
        val eLng = if (is13Cols) parts.getOrNull(8)?.toDoubleOrNull() ?: lng else lng

        val category = if (is13Cols) parts.getOrNull(9)?.trim()?.ifBlank { "Landmark" } ?: "Landmark"
                       else parts.getOrNull(7)?.trim()?.ifBlank { "Landmark" } ?: "Landmark"

        val source = if (is13Cols) parts.getOrNull(10)?.trim()?.ifBlank { "Google_Drive_Delta" } ?: "Google_Drive_Delta"
                     else parts.getOrNull(8)?.trim()?.ifBlank { "Google_Drive_Delta" } ?: "Google_Drive_Delta"

        val isDeletedStr = if (is13Cols) parts.getOrNull(12)?.trim() ?: "false"
                           else parts.getOrNull(10)?.trim() ?: "false"

        val isDeleted = isDeletedStr.equals("true", ignoreCase = true)

        if (codeId.isBlank()) return null

        return EgyptPOI(
            placeId = codeId,
            nameAr = nameAr,
            nameEn = nameAr,
            category = category,
            lat = lat,
            lng = lng,
            entranceLat = eLat,
            entranceLng = eLng,
            district = district,
            governorate = governorate,
            city = city,
            locationSource = source,
            locationVerified = !isDeleted,
            confidence = "HIGH"
        )
    }

    private suspend fun applyDeltaCsvToLocalDb(context: Context, deltaFile: java.io.File) = withContext(Dispatchers.IO) {
        if (!deltaFile.exists() || !deltaFile.canRead()) return@withContext
        try {
            val localDb = com.albasel.tawsilaapp.data.local.LocalSearchDatabase.getInstance(context)
            val reader = deltaFile.bufferedReader(Charsets.UTF_8)
            val upsertList = ArrayList<com.albasel.tawsilaapp.data.local.PlaceEntry>()
            val updatedPois = ArrayList<EgyptPOI>()

            reader.useLines { lines ->
                lines.drop(1).forEach { line ->
                    if (line.isNotBlank()) {
                        val parts = parseFastCsvLine(line)
                        val poi = parseCsvLineToPoi(parts, "")
                        if (poi != null) {
                            val codeId = poi.placeId
                            val isDeleted = !poi.locationVerified || poi.nameAr.isBlank() || (poi.lat == 0.0 && poi.lng == 0.0)

                            if (isDeleted) {
                                localDb.deletePlaceByCodeId(codeId)
                                if (poi.nameAr.isNotBlank()) localDb.deletePlace(poi.nameAr)
                                EgyptPlaces.POIS.removeAll { it.placeId == codeId || (poi.nameAr.isNotBlank() && it.nameAr == poi.nameAr) }
                                EgyptPlaces.drivePois.removeAll { it.placeId == codeId || (poi.nameAr.isNotBlank() && it.nameAr == poi.nameAr) }
                                EgyptPlaces.assetPois.removeAll { it.placeId == codeId || (poi.nameAr.isNotBlank() && it.nameAr == poi.nameAr) }
                                com.albasel.tawsilaapp.data.repository.impl.SearchRepositoryImpl.clearCache()
                                EgyptPlaces.onPoisUpdated.value++
                            } else {
                                val entry = com.albasel.tawsilaapp.data.local.PlaceEntry(
                                    id = codeId,
                                    nameAr = poi.nameAr,
                                    nameEn = poi.nameEn,
                                    category = poi.category,
                                    lat = poi.lat,
                                    lng = poi.lng,
                                    entranceLat = poi.entranceLat,
                                    entranceLng = poi.entranceLng,
                                    district = poi.district,
                                    governorate = poi.governorate,
                                    city = poi.city,
                                    source = poi.locationSource,
                                    verified = true
                                )
                                upsertList.add(entry)
                                updatedPois.add(poi)
                            }
                        }
                    }
                }
            }

            if (upsertList.isNotEmpty()) {
                localDb.upsertPlacesBatch(upsertList)
                EgyptPlaces.updateDrivePois(updatedPois)
            }
            Log.d(TAG, "Delta CSV applied successfully by Code ID (${upsertList.size} updated).")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying Delta CSV: ${e.message}")
        }
    }

    suspend fun checkAndUpdateLocalCsvFromDrive(context: Context, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        try {
            val localCsvFile = java.io.File(context.filesDir, "wasalna_admin_updates.csv")
            var csvContentDownloaded = ""

            // Export URLs to attempt downloading
            val exportUrls = listOf(
                "https://docs.google.com/spreadsheets/d/$DELTA_SPREADSHEET_ID/export?format=csv&gid=1320169620",
                "https://docs.google.com/spreadsheets/d/$DELTA_SPREADSHEET_ID/export?format=csv",
                "https://docs.google.com/spreadsheets/d/$MASTER_SPREADSHEET_ID/export?format=csv"
            )

            // Retry up to 3 times on app launch
            for (attempt in 1..3) {
                for (url in exportUrls) {
                    try {
                        val req = Request.Builder()
                            .url(url)
                            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                            .get()
                            .build()

                        okHttpClient.newCall(req).execute().use { resp ->
                            if (resp.isSuccessful) {
                                val bodyStr = resp.body?.string() ?: ""
                                if (bodyStr.isNotBlank() && !bodyStr.startsWith("<!DOCTYPE") && bodyStr.contains(",")) {
                                    csvContentDownloaded = bodyStr
                                    Log.d(TAG, "Successfully downloaded delta CSV on attempt $attempt from $url!")
                                    break
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Download attempt $attempt error for $url: ${e.message}")
                    }
                    if (csvContentDownloaded.isNotBlank()) break
                }
                if (csvContentDownloaded.isNotBlank()) break
                kotlinx.coroutines.delay(1000L * attempt)
            }

            // Fallback via OAuth token if public download failed
            if (csvContentDownloaded.isBlank()) {
                try {
                    val accessToken = getAccessToken()
                    if (accessToken != null) {
                        val exportUrl = "https://www.googleapis.com/drive/v3/files/$DELTA_SPREADSHEET_ID/export?mimeType=text/csv"
                        val dlReq = Request.Builder().url(exportUrl).addHeader("Authorization", "Bearer $accessToken").get().build()
                        okHttpClient.newCall(dlReq).execute().use { resp ->
                            if (resp.isSuccessful) {
                                csvContentDownloaded = resp.body?.string() ?: ""
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Google Sheet OAuth download error: ${e.message}")
                }
            }

            // Fallback via Firebase Cloud Backup
            if (csvContentDownloaded.isBlank()) {
                val fbManager = com.albasel.tawsilaapp.data.firebase.FirebaseManager(context)
                val fbDelta = fbManager.fetchCsvFromFirebase("wasalna_admin_updates.csv")
                if (fbDelta != null) {
                    val content = fbDelta["csvContent"]?.toString() ?: ""
                    if (content.isNotBlank()) {
                        csvContentDownloaded = content
                    }
                }
            }

            // Save & Apply Delta CSV to Local Database & Memory POIs by Code ID
            if (csvContentDownloaded.isNotBlank()) {
                localCsvFile.writeText(csvContentDownloaded, Charsets.UTF_8)
                applyDeltaCsvToLocalDb(context, localCsvFile)
                Log.d(TAG, "Delta CSV applied successfully on startup!")
                return@withContext true
            } else if (localCsvFile.exists()) {
                applyDeltaCsvToLocalDb(context, localCsvFile)
                return@withContext true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in checkAndUpdateLocalCsvFromDrive: ${e.message}", e)
        }
        return@withContext false
    }

    fun recordEditedCodeId(context: Context, codeId: String) {
        val clean = codeId.trim()
        if (clean.isNotBlank()) {
            val prefs = context.getSharedPreferences("edited_pois_prefs", Context.MODE_PRIVATE)
            val currentSet = prefs.getStringSet("edited_code_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
            currentSet.add(clean)
            prefs.edit().putStringSet("edited_code_ids", currentSet).apply()
        }
    }

    fun recordDeletedCodeId(context: Context, codeId: String) {
        val clean = codeId.trim()
        if (clean.isNotBlank()) {
            val prefs = context.getSharedPreferences("edited_pois_prefs", Context.MODE_PRIVATE)
            val currentSet = prefs.getStringSet("deleted_code_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
            currentSet.remove("")
            currentSet.add(clean)
            prefs.edit().putStringSet("deleted_code_ids", currentSet).apply()
        }
    }

    fun removeCodeIdFromDeleted(context: Context, codeId: String) {
        val clean = codeId.trim()
        if (clean.isNotBlank()) {
            val prefs = context.getSharedPreferences("edited_pois_prefs", Context.MODE_PRIVATE)
            val currentSet = prefs.getStringSet("deleted_code_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
            currentSet.remove("")
            currentSet.remove(clean)
            val normName = com.albasel.tawsilaapp.util.ArabicUtils.normalize(clean)
            if (normName.isNotBlank()) currentSet.remove(normName)
            prefs.edit().putStringSet("deleted_code_ids", currentSet).apply()
        }
    }

    private fun getEditedCodeIds(context: Context): Set<String> {
        val prefs = context.getSharedPreferences("edited_pois_prefs", Context.MODE_PRIVATE)
        return prefs.getStringSet("edited_code_ids", emptySet()) ?: emptySet()
    }

    private fun getDeletedCodeIds(context: Context): Set<String> {
        val prefs = context.getSharedPreferences("edited_pois_prefs", Context.MODE_PRIVATE)
        val set = prefs.getStringSet("deleted_code_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        set.remove("")
        return set
    }

    suspend fun syncMapPlacesToGoogleDrive(context: Context) = withContext(Dispatchers.IO) {
        try {
            val localDb = com.albasel.tawsilaapp.data.local.LocalSearchDatabase.getInstance(context)
            val dbPlaces = localDb.getAllPlacesList()
            val memoryPois = DamiettaPlaces.getAllPois()
            val editedIds = getEditedCodeIds(context)
            
            // 0. Load previously deleted Code IDs from local saved delta CSV file as well
            val persistentDeletedIds = getDeletedCodeIds(context).toMutableSet()
            persistentDeletedIds.remove("")
            try {
                val deltaFile = java.io.File(context.filesDir, "wasalna_admin_updates.csv")
                if (deltaFile.exists() && deltaFile.canRead()) {
                    deltaFile.bufferedReader(Charsets.UTF_8).useLines { lines ->
                        lines.drop(1).forEach { line ->
                            if (line.isNotBlank()) {
                                val parts = parseFastCsvLine(line)
                                val poi = parseCsvLineToPoi(parts, "")
                                if (poi != null) {
                                    val isDel = !poi.locationVerified || poi.nameAr.isBlank() || (poi.lat == 0.0 && poi.lng == 0.0) || poi.locationSource.contains("Deleted")
                                    if (isDel && poi.placeId.trim().toIntOrNull() != null && poi.placeId.trim().isNotBlank()) {
                                        persistentDeletedIds.add(poi.placeId.trim())
                                        recordDeletedCodeId(context, poi.placeId.trim())
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {}

            val deletedIds = persistentDeletedIds

            fun isDeletedCheck(codeId: String): Boolean {
                val cleanId = codeId.trim()
                if (cleanId.isBlank() || cleanId.toIntOrNull() == null) return false
                return deletedIds.contains(cleanId)
            }

            val poiMapByCodeId = LinkedHashMap<String, EgyptPOI>()
            val nameToCodeIdMap = HashMap<String, String>()

            // 1. Base dataset ALWAYS loaded from assets CSV to guarantee 17,207 places are preserved
            try {
                val inputStream = context.assets.open("destination_routes_112.csv")
                inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    var rowCounter = 1
                    lines.drop(1).forEach { line ->
                        if (line.isNotBlank()) {
                            val parts = parseFastCsvLine(line)
                            val poi = parseCsvLineToPoi(parts, rowCounter.toString())
                            if (poi != null) {
                                rowCounter++
                                val isDel = isDeletedCheck(poi.placeId)
                                poiMapByCodeId[poi.placeId] = if (isDel) {
                                    poi.copy(locationVerified = false, locationSource = "Admin_Deleted", nameAr = "", lat = 0.0, lng = 0.0)
                                } else {
                                    poi
                                }
                                if (poi.nameAr.isNotBlank()) {
                                    nameToCodeIdMap[poi.nameAr] = poi.placeId
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading base assets CSV in syncMapPlacesToGoogleDrive: ${e.message}")
            }

            // 2. Overlay SQLite DB POIs over base POIs ONLY if numeric Code ID matches and NOT already deleted
            dbPlaces.forEach { entry ->
                val codeId = entry.id.trim()
                val nameKey = entry.nameAr.trim()
                val targetCodeId = if (codeId.toIntOrNull() != null) codeId else (nameToCodeIdMap[nameKey] ?: "")
                if (targetCodeId.toIntOrNull() != null) {
                    val wasAlreadyDeleted = poiMapByCodeId[targetCodeId]?.let { !it.locationVerified || it.locationSource.contains("Deleted") } ?: false
                    val isDel = wasAlreadyDeleted || isDeletedCheck(targetCodeId) || !entry.verified || entry.source.contains("Deleted")

                    poiMapByCodeId[targetCodeId] = EgyptPOI(
                        placeId = targetCodeId,
                        nameAr = if (isDel) "" else entry.nameAr,
                        nameEn = if (isDel) "" else entry.nameEn,
                        category = if (isDel) "" else entry.category,
                        lat = if (isDel) 0.0 else entry.lat,
                        lng = if (isDel) 0.0 else entry.lng,
                        entranceLat = if (isDel) 0.0 else entry.lat,
                        entranceLng = if (isDel) 0.0 else entry.lng,
                        district = if (isDel) "" else entry.district,
                        governorate = if (isDel) "" else entry.governorate,
                        city = if (isDel) "" else entry.city,
                        locationSource = if (isDel) "Admin_Deleted" else entry.source,
                        locationVerified = !isDel
                    )
                    if (nameKey.isNotBlank()) {
                        nameToCodeIdMap[nameKey] = targetCodeId
                    }
                }
            }

            // 3. Overlay Memory POIs (EgyptPlaces.getAllPois()) ONLY if NOT already deleted
            memoryPois.forEach { poi ->
                val cleanId = poi.placeId.trim()
                val cleanName = poi.nameAr.trim()
                val targetCodeId = if (cleanId.toIntOrNull() != null) cleanId else (nameToCodeIdMap[cleanName] ?: "")

                if (targetCodeId.toIntOrNull() != null) {
                    val wasAlreadyDeleted = poiMapByCodeId[targetCodeId]?.let { !it.locationVerified || it.locationSource.contains("Deleted") } ?: false
                    val isDel = wasAlreadyDeleted || isDeletedCheck(targetCodeId) || !poi.locationVerified || poi.locationSource.contains("Deleted")

                    poiMapByCodeId[targetCodeId] = EgyptPOI(
                        placeId = targetCodeId,
                        nameAr = if (isDel) "" else poi.nameAr,
                        nameEn = if (isDel) "" else poi.nameEn,
                        category = if (isDel) "" else poi.category.toString(),
                        lat = if (isDel) 0.0 else poi.lat,
                        lng = if (isDel) 0.0 else poi.lng,
                        entranceLat = if (isDel) 0.0 else poi.lat,
                        entranceLng = if (isDel) 0.0 else poi.lng,
                        district = if (isDel) "" else poi.district,
                        governorate = if (isDel) "" else poi.governorate,
                        city = if (isDel) "" else poi.city,
                        locationSource = if (isDel) "Admin_Deleted" else poi.locationSource,
                        locationVerified = !isDel
                    )
                    if (cleanName.isNotBlank()) {
                        nameToCodeIdMap[cleanName] = targetCodeId
                    }
                }
            }

            // 4. FINAL GUARANTEE: Force ALL deletedIds from SharedPreferences to remain STRICTLY DELETED
            deletedIds.forEach { delId ->
                val cleanDelId = delId.trim()
                if (cleanDelId.toIntOrNull() != null && poiMapByCodeId.containsKey(cleanDelId)) {
                    poiMapByCodeId[cleanDelId] = EgyptPOI(
                        placeId = cleanDelId,
                        nameAr = "",
                        nameEn = "",
                        category = "",
                        lat = 0.0,
                        lng = 0.0,
                        entranceLat = 0.0,
                        entranceLng = 0.0,
                        district = "",
                        governorate = "",
                        city = "",
                        locationSource = "Admin_Deleted",
                        locationVerified = false
                    )
                }
            }

            val poiList = poiMapByCodeId.values.filter { it.placeId.toIntOrNull() != null }.sortedBy { it.placeId.toIntOrNull() ?: 0 }

            // 1. Build Full Master CSV String with ALL places (11 columns)
            val sbMasterCsv = StringBuilder()
            sbMasterCsv.append("م,اسم الوجهة,المنطقة/الحي,المحافظة,المدينة,خط العرض (Lat),خط الطول (Lng),التصنيف,المصدر,حالة التحقق,محذوف\n")

            poiList.forEachIndexed { index, poi ->
                val codeId = poi.placeId.ifBlank { (index + 1).toString() }
                val isDeleted = !poi.locationVerified || poi.nameAr.isBlank() || (poi.lat == 0.0 && poi.lng == 0.0) || poi.locationSource.contains("Deleted")

                if (isDeleted) {
                    sbMasterCsv.append("${codeId},\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"Admin_Deleted\",\"\",\"true\"\n")
                } else {
                    val safeDistrict = poi.district.ifBlank { "مصر" }
                    val safeGov = poi.governorate.ifBlank { "مصر" }
                    val safeCity = poi.city.ifBlank { poi.nameAr.ifBlank { "مصر" } }
                    val safeCat = if (poi.category.isBlank()) "Landmark" else poi.category
                    sbMasterCsv.append("${codeId},\"${poi.nameAr}\",\"${safeDistrict}\",\"${safeGov}\",\"${safeCity}\",${poi.lat},${poi.lng},\"${safeCat}\",\"${poi.locationSource}\",\"مؤكد\",\"false\"\n")
                }
            }
            val masterText = sbMasterCsv.toString()

            // 2. Build Delta Updates CSV File with ONLY Admin created/edited/deleted places by Code ID (11 columns)
            val customDeltaPlaces = poiList.filter { poi ->
                val cleanId = poi.placeId.trim()
                val idNum = cleanId.toIntOrNull() ?: 0

                (editedIds.contains(cleanId) && cleanId.toIntOrNull() != null) ||
                (deletedIds.contains(cleanId) && cleanId.toIntOrNull() != null) ||
                poi.locationSource.contains("Admin") ||
                idNum > 17207
            }.sortedBy { it.placeId.toIntOrNull() ?: 0 }

            val sbDeltaCsv = StringBuilder()
            sbDeltaCsv.append("م,اسم الوجهة,المنطقة/الحي,المحافظة,المدينة,خط العرض (Lat),خط الطول (Lng),التصنيف,المصدر,حالة التحقق,محذوف\n")

            customDeltaPlaces.forEachIndexed { index, poi ->
                val codeId = poi.placeId.ifBlank { (17029 + index).toString() }
                val isDeleted = !poi.locationVerified || poi.nameAr.isBlank() || (poi.lat == 0.0 && poi.lng == 0.0) || poi.locationSource.contains("Deleted")

                if (isDeleted) {
                    sbDeltaCsv.append("${codeId},\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"Admin_Deleted\",\"\",\"true\"\n")
                } else {
                    val safeDistrict = poi.district.ifBlank { "مصر" }
                    val safeGov = poi.governorate.ifBlank { "مصر" }
                    val safeCity = poi.city.ifBlank { poi.nameAr.ifBlank { "مصر" } }
                    val safeCat = if (poi.category.isBlank()) "Landmark" else poi.category
                    sbDeltaCsv.append("${codeId},\"${poi.nameAr}\",\"${safeDistrict}\",\"${safeGov}\",\"${safeCity}\",${poi.lat},${poi.lng},\"${safeCat}\",\"${poi.locationSource}\",\"مؤكد\",\"false\"\n")
                }
            }

            val deltaText = sbDeltaCsv.toString()

            // Save local file copies
            try {
                val deltaFile = java.io.File(context.filesDir, "wasalna_admin_updates.csv")
                deltaFile.writeText(deltaText, Charsets.UTF_8)
                val masterFile = java.io.File(context.filesDir, "destination_routes_112.csv")
                masterFile.writeText(masterText, Charsets.UTF_8)
            } catch (e: Exception) {}

            // Export public CSV copies into device's Downloads directory
            try {
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                if (downloadsDir != null && (downloadsDir.exists() || downloadsDir.mkdirs())) {
                    val publicDeltaFile = java.io.File(downloadsDir, "wasalna_admin_updates.csv")
                    publicDeltaFile.writeText(deltaText, Charsets.UTF_8)

                    val publicMasterFile = java.io.File(downloadsDir, "destination_routes_112.csv")
                    publicMasterFile.writeText(masterText, Charsets.UTF_8)

                    Log.d(TAG, "Exported public CSV copies to Downloads folder: ${downloadsDir.absolutePath}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error exporting CSV to Downloads folder: ${e.message}")
            }

            // Update Delta Spreadsheet (WASALNA UPDATES) and Master Backup Sheet (WASALNA LOCATIONS) with 3 retries
            var sheetUpdated = false
            var lastUploadError = ""
            val mediaType = "text/csv; charset=UTF-8".toMediaType()

            for (attempt in 1..3) {
                try {
                    val accessToken = getAccessToken()
                    if (accessToken != null) {
                        // 1. Update Delta Updates Sheet (WASALNA UPDATES)
                        val deltaUrl = "https://www.googleapis.com/upload/drive/v3/files/$DELTA_SPREADSHEET_ID".toHttpUrl().newBuilder()
                            .addQueryParameter("uploadType", "media")
                            .addQueryParameter("supportsAllDrives", "true")
                            .build()

                        val deltaBody = deltaText.toByteArray(Charsets.UTF_8).toRequestBody(mediaType)
                        
                        var deltaSuccess = false
                        for (httpMethod in listOf("PATCH", "PUT")) {
                            val reqBuilder = Request.Builder().url(deltaUrl).addHeader("Authorization", "Bearer $accessToken")
                            val req = if (httpMethod == "PATCH") reqBuilder.patch(deltaBody).build() else reqBuilder.put(deltaBody).build()

                            okHttpClient.newCall(req).execute().use { resp ->
                                val respBody = resp.body?.string() ?: ""
                                if (resp.isSuccessful) {
                                    deltaSuccess = true
                                    sheetUpdated = true
                                    Log.d(TAG, "Delta Google Sheet (WASALNA UPDATES) updated successfully via $httpMethod on attempt $attempt!")
                                } else {
                                    if (resp.code == 401) invalidateAccessToken()
                                    lastUploadError = "Delta $httpMethod ${resp.code}: $respBody"
                                    Log.e(TAG, "Failed updating Delta Google Sheet via $httpMethod on attempt $attempt: ${resp.code} - $respBody")
                                }
                            }
                            if (deltaSuccess) break
                        }

                        // 2. Update Master Backup Sheet (WASALNA LOCATIONS) - Protect dataset with size safety guard
                        if (poiList.size >= 17000) {
                            val masterUrl = "https://www.googleapis.com/upload/drive/v3/files/$MASTER_SPREADSHEET_ID".toHttpUrl().newBuilder()
                                .addQueryParameter("uploadType", "media")
                                .addQueryParameter("supportsAllDrives", "true")
                                .build()

                            val masterBody = masterText.toByteArray(Charsets.UTF_8).toRequestBody(mediaType)

                            var masterSuccess = false
                            for (httpMethod in listOf("PATCH", "PUT")) {
                                val reqBuilder = Request.Builder().url(masterUrl).addHeader("Authorization", "Bearer $accessToken")
                                val req = if (httpMethod == "PATCH") reqBuilder.patch(masterBody).build() else reqBuilder.put(masterBody).build()

                                okHttpClient.newCall(req).execute().use { resp ->
                                    val respBody = resp.body?.string() ?: ""
                                    if (resp.isSuccessful) {
                                        masterSuccess = true
                                        Log.d(TAG, "Master Google Sheet (WASALNA LOCATIONS) updated successfully via $httpMethod on attempt $attempt!")
                                    } else {
                                        if (resp.code == 401) invalidateAccessToken()
                                        Log.e(TAG, "Failed updating Master Google Sheet via $httpMethod on attempt $attempt: ${resp.code} - $respBody")
                                    }
                                }
                                if (masterSuccess) break
                            }
                        } else {
                            Log.e(TAG, "SAFETY GUARD: poiList size is ${poiList.size} < 17000. Skipping Master Backup Sheet update!")
                        }

                        if (sheetUpdated) break
                    } else {
                        lastUploadError = "OAuth token error"
                    }
                } catch (e: Exception) {
                    lastUploadError = e.message ?: "Network Exception"
                    Log.e(TAG, "Error updating Google Spreadsheets on attempt $attempt: ${e.message}")
                }
                kotlinx.coroutines.delay(1000L * attempt)
            }

            // Sync both file strings to Firebase Realtime Database
            try {
                val fbManager = com.albasel.tawsilaapp.data.firebase.FirebaseManager(context)
                fbManager.syncCsvToFirebase("destination_routes_112.csv", masterText)
                fbManager.syncCsvToFirebase("wasalna_admin_updates.csv", deltaText)
            } catch (e: Exception) {}

            withContext(Dispatchers.Main) {
                if (sheetUpdated) {
                    android.widget.Toast.makeText(context, "تم تحديث الشيتين (WASALNA UPDATES & LOCATIONS) والفايربيس بنجاح!", android.widget.Toast.LENGTH_LONG).show()
                } else {
                    android.widget.Toast.makeText(context, "تم الحفظ محلياً وفي الفايربيس! (الشيت: ${lastUploadError.take(60)})", android.widget.Toast.LENGTH_LONG).show()
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error syncing map places to Drive: ${e.message}", e)
        }
    }

    suspend fun deleteFile(fileId: String): Boolean = withContext(Dispatchers.IO) {
        if (fileId.isBlank() || fileId.startsWith("data:image") || fileId.startsWith("http") || fileId.length > 100) return@withContext false
        try {
            val token = getAccessToken() ?: return@withContext false
            val request = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId")
                .addHeader("Authorization", "Bearer $token")
                .delete()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 404) {
                    Log.d(TAG, "Successfully deleted file $fileId from Google Drive")
                    return@withContext true
                } else {
                    Log.e(TAG, "Delete File $fileId Error: HTTP ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting file $fileId from Drive: ${e.message}")
        }
        false
    }

    suspend fun deleteFileByName(fileName: String, targetFolderId: String = FOLDER_ID): Boolean = withContext(Dispatchers.IO) {
        if (fileName.isBlank()) return@withContext false
        try {
            val token = getAccessToken() ?: return@withContext false
            val searchUrl = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
                .addQueryParameter("q", "'$targetFolderId' in parents and name = '$fileName' and trashed = false")
                .addQueryParameter("fields", "files(id,name)")
                .build()

            val searchReq = Request.Builder()
                .url(searchUrl)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            okHttpClient.newCall(searchReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val jsonStr = resp.body?.string() ?: ""
                    val filesArray = org.json.JSONObject(jsonStr).optJSONArray("files")
                    if (filesArray != null) {
                        for (i in 0 until filesArray.length()) {
                            val fId = filesArray.getJSONObject(i).optString("id")
                            if (fId.isNotBlank()) {
                                deleteFile(fId)
                                Log.d(TAG, "Deleted existing file by name '$fileName' (ID: $fId) from Drive")
                            }
                        }
                    }
                }
            }
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting file by name '$fileName': ${e.message}")
            false
        }
    }
}
