package com.albasel.tawsilaapp.data.firebase

import android.content.Context
import android.net.Uri
import android.util.Log
import com.albasel.tawsilaapp.data.model.*
import com.albasel.tawsilaapp.util.DamiettaPOI
import com.albasel.tawsilaapp.util.DamiettaPlaces
import com.albasel.tawsilaapp.util.FCMV1Helper
import com.albasel.tawsilaapp.util.GoogleDriveHelper
import com.albasel.tawsilaapp.util.MapUtils
import com.albasel.tawsilaapp.util.VehicleUtils.cleanVehicleName
import com.albasel.tawsilaapp.util.VehicleUtils.isCargoOnlyVehicle
import com.google.firebase.database.*
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.maplibre.android.geometry.LatLng
import java.io.ByteArrayOutputStream
import java.util.*
import kotlin.math.*

class FirebaseManager(private val context: Context? = null) {
    private val dbUrl = "https://tawsilaapp-f37b1740-default-rtdb.firebaseio.com"
    val database = FirebaseDatabase.getInstance(dbUrl).reference.child("tawsilaappfinal")
    private val scope = CoroutineScope(Dispatchers.Main)

    private val lastLocationUpdates = mutableMapOf<String, LatLng>()
    private val lastLocationTimes = mutableMapOf<String, Long>()
    private val lastHeartbeatTimes = mutableMapOf<String, Long>()

    // --- Helpers ---
    private fun tryToDecrypt(value: String): String? {
        if (value.isEmpty() || value.length < 4 || value.length % 2 != 0) return null
        return try {
            val decrypted = com.albasel.tawsilaapp.util.SecurityUtils.decrypt(value)
            if (decrypted.isNotEmpty() && decrypted.all { it.code in 32..2000 }) decrypted else null
        } catch (e: Exception) { null }
    }

    suspend fun getAdmins(): List<User> = try { 
        val adminMap = mutableMapOf<String, User>()
        
        // 1. Scan users where role == "ADMIN" or phone == "01098804131"
        val usersSnap = database.child("users").get().await()
        for (child in usersSnap.children) {
            val u = child.getValue(User::class.java)
            if (u != null) {
                val key = child.key ?: u.id
                val userObj = u.copy(id = key.ifBlank { u.id })
                if (userObj.role.uppercase() == "ADMIN" || userObj.phone == "01098804131") {
                    val dedupeKey = userObj.id.ifBlank { userObj.phone }
                    adminMap[dedupeKey] = userObj
                }
            }
        }

        // 2. Scan admins index node
        val adminIds = database.child("admins").get().await().children.mapNotNull { it.key }
        adminIds.forEach { uid ->
            getUser(uid)?.let { u ->
                val dedupeKey = u.id.ifBlank { u.phone }
                if (dedupeKey.isNotEmpty()) adminMap[dedupeKey] = u
            }
        }

        adminMap.values.toList()
    } catch (e: Exception) { emptyList() }

    suspend fun runDataMigration() = withContext(Dispatchers.IO) {
        try {
            unifyAndCleanDuplicateUsers()
        } catch (e: Exception) {}
        val usersSnap = database.child("users").get().await()
        usersSnap.children.forEach { child ->
            try {
                val uid = child.key ?: return@forEach
                val updates = mutableMapOf<String, Any>()
                listOf("name", "phone", "address").forEach { field ->
                    child.child(field).getValue(String::class.java)?.let { tryToDecrypt(it)?.let { d -> updates[field] = d } }
                }
                if (updates.isNotEmpty()) database.child("users").child(uid).updateChildren(updates).await()
            } catch (e: Exception) {}
        }
    }

    // --- Authentication ---
    suspend fun isUsernameTaken(un: String): Boolean = try { database.child("usernames").child(un.lowercase()).get().await().exists() } catch(e: Exception) { false }
    suspend fun isPhoneTaken(p: String): Boolean = try { database.child("phones").child(p).get().await().exists() } catch(e: Exception) { false }

    suspend fun registerUser(user: User, password: String): Pair<Boolean, String?> {
        val cleanUsername = user.username.trim().lowercase()
        return try {
            if (isUsernameTaken(cleanUsername)) return false to "Username taken"
            if (isPhoneTaken(user.phone)) return false to "Phone taken"
            val uid = database.child("users").push().key ?: UUID.randomUUID().toString()
            database.child("users").child(uid).setValue(user.copy(id = uid, password = password)).await()
            database.child("usernames").child(cleanUsername).setValue(uid).await()
            database.child("phones").child(user.phone).setValue(uid).await()
            
            if (user.role == "DRIVER") {
                notifyAdminsAboutNewDriver(user.name, isUpdate = false, targetId = uid)
            }
            
            true to null
        } catch (e: Exception) { false to e.message }
    }

    suspend fun loginUser(phone: String, password: String, deviceId: String = ""): Pair<User?, String?> {
        return try {
            val phoneSnapshot = database.child("phones").child(phone).get().await()
            if (!phoneSnapshot.exists()) return null to "Phone not found"
            val uid = phoneSnapshot.getValue(String::class.java) ?: return null to "Error retrieving user ID"
            val userSnapshot = database.child("users").child(uid).get().await()
            val user = userSnapshot.getValue(User::class.java) ?: return null to "Error parsing user data"
            if (user.password == password) {
                val now = System.currentTimeMillis()
                if (user.currentSessionId.isNotEmpty() && (now - user.lastSeen < 60000L) && user.currentDeviceId != deviceId) return null to "ALREADY_LOGGED_IN"
                val newSid = UUID.randomUUID().toString()
                val updates = mutableMapOf<String, Any>("currentSessionId" to newSid, "currentDeviceId" to deviceId, "lastSeen" to now)
                if (user.role?.uppercase() == "DRIVER" || user.role?.uppercase() == "ADMIN" || !user.vehiclePlate.isNullOrEmpty()) updates["isOnline"] = true
                database.child("users").child(uid).updateChildren(updates).await()
                return user.copy(currentSessionId = newSid, currentDeviceId = deviceId, lastSeen = now, isOnline = true) to null
            }
            null to "Wrong password"
        } catch (e: Exception) { null to e.message }
    }

    fun getCurrentUserId(): String? {
        val userJson = context?.getSharedPreferences("tawsila_prefs", Context.MODE_PRIVATE)?.getString("user_data", null) ?: return null
        return try { com.google.gson.Gson().fromJson(userJson, User::class.java).id } catch(e: Exception) { null }
    }

    suspend fun getUser(uid: String): User? = try { database.child("users").child(uid).get().await().getValue(User::class.java) } catch(e: Exception) { null }
    suspend fun saveUser(user: User) { database.child("users").child(user.id).setValue(user).await() }
    suspend fun deleteUser(uid: String) = withContext(Dispatchers.IO) {
        try {
            val u = getUser(uid) ?: return@withContext
            
            // Delete all associated Google Drive images (Profile, ID, Licenses, Receipts)
            val driveIdsToDelete = mutableListOf<String>()
            if (!u.profileImageDriveId.isNullOrEmpty()) driveIdsToDelete.add(u.profileImageDriveId!!)
            if (!u.idCardDriveId.isNullOrEmpty()) driveIdsToDelete.add(u.idCardDriveId!!)
            if (!u.licenseFrontDriveId.isNullOrEmpty()) driveIdsToDelete.add(u.licenseFrontDriveId!!)
            if (!u.licenseBackDriveId.isNullOrEmpty()) driveIdsToDelete.add(u.licenseBackDriveId!!)
            if (!u.paymentReceiptDriveId.isNullOrEmpty()) driveIdsToDelete.add(u.paymentReceiptDriveId!!)
            
            u.receipts?.values?.forEach { driveId ->
                if (driveId.isNotEmpty() && !driveId.startsWith("data:image")) {
                    driveIdsToDelete.add(driveId)
                }
            }

            driveIdsToDelete.distinct().forEach { driveId ->
                try {
                    GoogleDriveHelper.deleteFile(driveId)
                } catch (e: Exception) {
                    Log.e("FirebaseManager", "Error deleting Google Drive file $driveId: ${e.message}")
                }
            }

            if (u.username.isNotEmpty()) database.child("usernames").child(u.username.lowercase()).removeValue()
            if (u.phone.isNotEmpty()) database.child("phones").child(u.phone).removeValue()
            database.child("users").child(uid).removeValue().await()
            database.child("users").child(u.phone).removeValue()
            database.child("commission_payments").child(uid).removeValue()
        } catch (e: Exception) {
            Log.e("FirebaseManager", "Error deleting user: ${e.message}", e)
        }
    }

    // --- Location Optimization ---
    suspend fun updateLocationLightweight(uid: String, lat: Double, lng: Double, prov: String, vehicleType: String, isOnline: Boolean, name: String, isApproved: Boolean = true, canDeliver: Boolean = true, canCargo: Boolean = false) {
        val now = System.currentTimeMillis()
        val ghRegion = com.albasel.tawsilaapp.util.GeoHashUtils.encode(lat, lng, 4) // Coarse region (~40km) for bucket indexing
        val data = mapOf(
            "id" to uid,
            "latitude" to lat,
            "longitude" to lng,
            "lastSeen" to now,
            "isOnline" to isOnline,
            "vehicleType" to vehicleType,
            "province" to prov,
            "name" to name,
            "isApproved" to isApproved,
            "canDeliver" to canDeliver,
            "canCargo" to canCargo,
            "geohash" to ghRegion
        )
        database.child("users").child(uid).updateChildren(data)
    }

    suspend fun updateTripLocation(orderId: String, role: String, lat: Double, lng: Double) {
        val data = mapOf(
            "latitude" to lat,
            "longitude" to lng,
            "timestamp" to System.currentTimeMillis()
        )
        database.child("trip_locations").child(orderId).child(role).setValue(data)
    }

    // --- Custom POIs Realtime Database Sync by Code ID ---
    suspend fun saveCustomPOI(poi: com.albasel.tawsilaapp.util.EgyptPOI) {
        if (poi.nameAr.isBlank()) return
        try {
            val codeId = poi.placeId.ifBlank {
                poi.nameAr.trim().replace(".", "_").replace("#", "_").replace("$", "_").replace("[", "_").replace("]", "_")
            }
            val poiMap = mapOf(
                "id" to codeId,
                "nameAr" to poi.nameAr,
                "nameEn" to poi.nameEn,
                "category" to poi.category,
                "lat" to poi.lat,
                "lng" to poi.lng,
                "district" to poi.district,
                "governorate" to poi.governorate,
                "city" to poi.city,
                "street" to poi.street,
                "entranceLat" to poi.entranceLat,
                "entranceLng" to poi.entranceLng,
                "locationSource" to poi.locationSource,
                "deleted" to false,
                "updatedAt" to System.currentTimeMillis()
            )
            database.child("custom_pois").child(codeId).setValue(poiMap).await()
        } catch (e: Exception) {
            Log.e("FirebaseManager", "Error saving custom POI: ${e.message}")
        }
    }

    suspend fun deleteCustomPOI(nameArOrCodeId: String) {
        if (nameArOrCodeId.isBlank()) return
        try {
            val codeId = nameArOrCodeId.trim().replace(".", "_").replace("#", "_").replace("$", "_").replace("[", "_").replace("]", "_")
            val softDeleteMap = mapOf(
                "id" to codeId,
                "nameAr" to "",
                "nameEn" to "",
                "category" to "deleted",
                "lat" to 0.0,
                "lng" to 0.0,
                "district" to "",
                "deleted" to true,
                "updatedAt" to System.currentTimeMillis()
            )
            database.child("custom_pois").child(codeId).setValue(softDeleteMap).await()
        } catch (e: Exception) {
            Log.e("FirebaseManager", "Error deleting custom POI: ${e.message}")
        }
    }

    fun listenToCustomPOIs(context: Context) {
        try {
            database.child("custom_pois").addChildEventListener(object : com.google.firebase.database.ChildEventListener {
                override fun onChildAdded(snapshot: com.google.firebase.database.DataSnapshot, previousChildName: String?) {
                    handleCustomPoiChange(context, snapshot)
                }
                override fun onChildChanged(snapshot: com.google.firebase.database.DataSnapshot, previousChildName: String?) {
                    handleCustomPoiChange(context, snapshot)
                }
                override fun onChildRemoved(snapshot: com.google.firebase.database.DataSnapshot) {
                    val codeId = snapshot.key ?: return
                    purgeDeletedPoi(context, codeId, "")
                }
                override fun onChildMoved(snapshot: com.google.firebase.database.DataSnapshot, previousChildName: String?) {}
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
        } catch (e: Exception) {
            Log.e("FirebaseManager", "Error listening to custom POIs: ${e.message}")
        }
    }

    private fun handleCustomPoiChange(context: Context, snapshot: com.google.firebase.database.DataSnapshot) {
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                val codeId = snapshot.child("id").getValue(String::class.java) ?: snapshot.key ?: return@launch
                val isDeleted = snapshot.child("deleted").getValue(Boolean::class.java) ?: false
                val nameAr = snapshot.child("nameAr").getValue(String::class.java) ?: ""

                if (isDeleted || nameAr.isBlank()) {
                    purgeDeletedPoi(context, codeId, nameAr)
                } else {
                    val lat = snapshot.child("lat").getValue(Double::class.java) ?: 0.0
                    val lng = snapshot.child("lng").getValue(Double::class.java) ?: 0.0
                    if (lat != 0.0 && lng != 0.0) {
                        val poi = com.albasel.tawsilaapp.util.EgyptPOI(
                            placeId = codeId,
                            nameAr = nameAr,
                            nameEn = snapshot.child("nameEn").getValue(String::class.java) ?: nameAr,
                            category = snapshot.child("category").getValue(String::class.java) ?: "Landmark",
                            lat = lat,
                            lng = lng,
                            district = snapshot.child("district").getValue(String::class.java) ?: "",
                            governorate = snapshot.child("governorate").getValue(String::class.java) ?: "مصر",
                            city = snapshot.child("city").getValue(String::class.java) ?: "مصر",
                            locationSource = "Realtime_Firebase",
                            locationVerified = true
                        )
                        val localDb = com.albasel.tawsilaapp.data.local.LocalSearchDatabase.getInstance(context)
                        localDb.upsertPlacesBatch(listOf(
                            com.albasel.tawsilaapp.data.local.PlaceEntry(
                                id = codeId, nameAr = nameAr, nameEn = poi.nameEn, category = poi.category,
                                lat = lat, lng = lng, entranceLat = lat, entranceLng = lng,
                                district = poi.district, governorate = poi.governorate, city = poi.city,
                                source = "Realtime_Firebase", verified = true
                            )
                        ))
                        com.albasel.tawsilaapp.util.EgyptPlaces.updateDrivePois(listOf(poi))
                        com.albasel.tawsilaapp.data.repository.impl.SearchRepositoryImpl.clearCache()
                        com.albasel.tawsilaapp.util.EgyptPlaces.onPoisUpdated.value++
                    }
                }
            } catch (e: Exception) {
                Log.e("FirebaseManager", "Error handling custom POI change: ${e.message}")
            }
        }
    }

    private fun purgeDeletedPoi(context: Context, codeId: String, nameAr: String) {
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                val localDb = com.albasel.tawsilaapp.data.local.LocalSearchDatabase.getInstance(context)
                if (codeId.isNotBlank()) localDb.deletePlaceByCodeId(codeId)
                if (nameAr.isNotBlank()) localDb.deletePlace(nameAr)

                com.albasel.tawsilaapp.util.EgyptPlaces.POIS.removeAll { it.placeId == codeId || (nameAr.isNotBlank() && it.nameAr == nameAr) }
                com.albasel.tawsilaapp.util.EgyptPlaces.drivePois.removeAll { it.placeId == codeId || (nameAr.isNotBlank() && it.nameAr == nameAr) }
                com.albasel.tawsilaapp.util.EgyptPlaces.assetPois.removeAll { it.placeId == codeId || (nameAr.isNotBlank() && it.nameAr == nameAr) }

                com.albasel.tawsilaapp.data.repository.impl.SearchRepositoryImpl.clearCache()
                com.albasel.tawsilaapp.util.EgyptPlaces.onPoisUpdated.value++
                Log.d("FirebaseManager", "Realtime Purge: Removed POI (codeId=$codeId, nameAr=$nameAr) across RAM and local SQLite instantly.")
            } catch (e: Exception) {
                Log.e("FirebaseManager", "Error purging deleted POI: ${e.message}")
            }
        }
    }

    fun listenToCustomPOIs(onUpdate: (List<DamiettaPOI>) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val poiList = mutableListOf<DamiettaPOI>()
                for (child in snapshot.children) {
                    try {
                        val isDeleted = child.child("deleted").getValue(Boolean::class.java) ?: false
                        val lat = child.child("lat").getValue(Double::class.java) ?: 0.0
                        val lng = child.child("lng").getValue(Double::class.java) ?: 0.0
                        val nameAr = child.child("nameAr").getValue(String::class.java) ?: ""

                        if (isDeleted || (lat == 0.0 && lng == 0.0) || nameAr.isBlank()) {
                            continue
                        }

                        val codeId = child.child("id").getValue(String::class.java) ?: child.key ?: ""
                        val nameEn = child.child("nameEn").getValue(String::class.java) ?: ""
                        val category = child.child("category").getValue(String::class.java) ?: "Landmark"
                        val district = child.child("district").getValue(String::class.java) ?: "دمياط الجديدة"
                        val governorate = child.child("governorate").getValue(String::class.java) ?: "دمياط"
                        val city = child.child("city").getValue(String::class.java) ?: "دمياط الجديدة"
                        val street = child.child("street").getValue(String::class.java) ?: ""
                        val entranceLat = child.child("entranceLat").getValue(Double::class.java) ?: lat
                        val entranceLng = child.child("entranceLng").getValue(Double::class.java) ?: lng

                        poiList.add(
                            DamiettaPOI(
                                placeId = codeId,
                                nameAr = nameAr,
                                nameEn = nameEn,
                                category = category,
                                lat = lat,
                                lng = lng,
                                district = district,
                                governorate = governorate,
                                city = city,
                                street = street,
                                entranceLat = entranceLat,
                                entranceLng = entranceLng,
                                locationSource = "Firebase_Synced",
                                locationVerified = true,
                                confidence = "HIGH"
                            )
                        )
                    } catch (e: Exception) {
                        Log.e("FirebaseManager", "Error parsing custom POI: ${e.message}")
                    }
                }
                onUpdate(poiList)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseManager", "Custom POI listen cancelled: ${error.message}")
            }
        }
        database.child("custom_pois").addValueEventListener(listener)
        return listener
    }

    fun listenToTripLocation(orderId: String, role: String, onUpdate: (Double, Double) -> Unit): ValueEventListener {
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                val lat = s.child("latitude").getValue(Double::class.java) ?: 0.0
                val lng = s.child("longitude").getValue(Double::class.java) ?: 0.0
                if (lat != 0.0 && lng != 0.0) onUpdate(lat, lng)
            }
            override fun onCancelled(e: DatabaseError) {}
        }
        database.child("trip_locations").child(orderId).child(role).addValueEventListener(l)
        return l
    }

    fun listenToOnlineDriversLightweight(onUpdate: (List<DriverLocation>) -> Unit): ValueEventListener {
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                val now = System.currentTimeMillis()
                val list = s.children.mapNotNull { child ->
                    try {
                        val isOnline = child.child("isOnline").getValue(Boolean::class.java) ?: false
                        val lastSeen = child.child("lastSeen").getValue(Long::class.java) ?: 0L
                        // 10 minutes threshold for visibility on map
                        if (isOnline && (now - lastSeen) < 600000) { 
                            DriverLocation(
                                id = child.key ?: "",
                                name = child.child("name").getValue(String::class.java) ?: "", 
                                vehicleType = child.child("vehicleType").getValue(String::class.java) ?: "",
                                lat = child.child("latitude").getValue(Double::class.java) ?: 0.0,
                                lng = child.child("longitude").getValue(Double::class.java) ?: 0.0,
                                lastSeen = lastSeen,
                                isOnline = isOnline,
                                province = child.child("province").getValue(String::class.java) ?: "",
                                isApproved = child.child("isApproved").getValue(Boolean::class.java) ?: true,
                                canDeliver = child.child("canDeliver").getValue(Boolean::class.java) ?: true,
                                canCargo = child.child("canCargo").getValue(Boolean::class.java) ?: false
                            )
                        } else null
                    } catch (e: Exception) { null }
                }
                onUpdate(list)
            }
            override fun onCancelled(e: DatabaseError) {}
        }
        database.child("users").orderByChild("isOnline").equalTo(true).addValueEventListener(l)
        return l
    }

    suspend fun getNearbyDrivers(lat: Double, lng: Double, radiusKm: Double): List<DriverLocation> {
        val snapshot = database.child("users").orderByChild("isOnline").equalTo(true).get().await()
        val now = System.currentTimeMillis()
        return snapshot.children.mapNotNull { child ->
            try {
                val isOnline = child.child("isOnline").getValue(Boolean::class.java) ?: false
                val lastSeen = child.child("lastSeen").getValue(Long::class.java) ?: 0L
                val dLat = child.child("latitude").getValue(Double::class.java) ?: 0.0
                val dLng = child.child("longitude").getValue(Double::class.java) ?: 0.0
                if (isOnline && (now - lastSeen) < 900000 && dLat != 0.0 && dLng != 0.0 && MapUtils.calculateDistance(lat, lng, dLat, dLng) <= radiusKm) {
                    DriverLocation(
                        id = child.key ?: "",
                        name = child.child("name").getValue(String::class.java) ?: "",
                        vehicleType = child.child("vehicleType").getValue(String::class.java) ?: "",
                        lat = dLat,
                        lng = dLng,
                        lastSeen = lastSeen,
                        isOnline = true,
                        province = child.child("province").getValue(String::class.java) ?: "",
                        isApproved = child.child("isApproved").getValue(Boolean::class.java) ?: true,
                        canDeliver = child.child("canDeliver").getValue(Boolean::class.java) ?: true,
                        canCargo = child.child("canCargo").getValue(Boolean::class.java) ?: false
                    )
                } else null
            } catch (e: Exception) { null }
        }
    }

    // --- Location ---
    suspend fun updateUserLocationWithFilters(id: String, lat: Double, lng: Double, prov: String, isHighPrecision: Boolean = false) {
        val now = System.currentTimeMillis()
        val currentLL = LatLng(lat, lng)
        val prevLL = lastLocationUpdates[id]
        val dist = if (prevLL != null) MapUtils.calculateDistance(prevLL.latitude, prevLL.longitude, lat, lng) else 999.0
        
        // Rule: Only update if moved >= 200 meters (0.200 km)
        if (dist < 0.200) return 

        // If moved >= 200m, check time based on precision
        val timeThreshold = if (isHighPrecision) 10000 else 300000 // 10s or 5 mins
        
        if ((now - (lastLocationTimes[id] ?: 0L)) >= timeThreshold) {
            val updates = mutableMapOf<String, Any>("latitude" to lat, "longitude" to lng, "lastSeen" to now)
            if (prov.isNotEmpty()) updates["liveProvince"] = prov
            lastLocationUpdates[id] = currentLL; lastLocationTimes[id] = now
            database.child("users").child(id).updateChildren(updates).await()
        }
    }

    suspend fun updateDriverOnlineStatus(id: String, online: Boolean) { database.child("users").child(id).child("isOnline").setValue(online).await() }

    // --- Orders ---
    suspend fun placeOrder(order: Order) = withContext(Dispatchers.IO) {
        val key = database.child("orders").push().key ?: return@withContext
        var finalOrder = order.copy(id = key)
        getPendingCompensation(order.userId)?.let {
            val amt = (it["amount"] as? Number)?.toDouble() ?: 0.0
            finalOrder = finalOrder.copy(compensationId = it["id"] as? String, compensationAmount = amt)
        }
        
        // Fast multi-location atomic update for order & active indices inside users node
        val updates = mutableMapOf<String, Any?>(
            "orders/$key" to finalOrder,
            "users/${order.userId}/active_orders/$key" to true
        )
        if (order.driverId != null) {
            updates["users/${order.driverId}/active_orders/$key"] = true
        }
        database.updateChildren(updates).await()

        // Background asynchronous FCM notifications to relevant drivers (non-blocking for ultra-fast response)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val targetDriver = finalOrder.driverId ?: finalOrder.driverPhone
                if (!targetDriver.isNullOrEmpty()) {
                    val routeStr = "${finalOrder.pickupName} إلى ${finalOrder.destinationName}"
                    val cName = finalOrder.userName.ifBlank { "عميل" }
                    sendFcmNotification(
                        targetDriver,
                        "العميل $cName اختارك كأقرب سائق",
                        routeStr,
                        "driver_orders?tab=3&orderId=${finalOrder.id}",
                        "DIRECT_ORDER"
                    )
                } else { 
                    notifyRelevantDrivers(finalOrder) 
                }
            } catch (e: Exception) {
                Log.e("FirebaseManager", "Background driver notification error", e)
            }
        }
    }

    private suspend fun notifyRelevantDrivers(order: Order) {
        val onlineDrivers = try { 
            database.child("users").orderByChild("isOnline").equalTo(true).get().await().children.mapNotNull { it.getValue(User::class.java) }
        } catch (e: Exception) { emptyList() }
        
        val vOrder = cleanVehicleName(order.vehicleType)
        val isCityRide = vOrder.contains("توصيل داخل") || vOrder.contains("city ride")
        val routeStr = "${order.pickupName} إلى ${order.destinationName}"
        
        onlineDrivers.forEach { driver ->
            if (driver.isApproved) {
                // Differentiate notification based on gender selection
                val genderMatch = when (order.gender) {
                    "MALE" -> driver.gender == "MALE"
                    "FEMALE" -> driver.gender == "FEMALE"
                    else -> true // If ANY or unspecified, notify all matching vehicle/type
                }
                
                if (!genderMatch) return@forEach

                val dType = cleanVehicleName(driver.vehicleType)
                // Use registered province from profile for City Ride filtering
                val profileProvinceMatch = driver.province.contains(order.pickupProvince) || order.pickupProvince.contains(driver.province)
                // Use live location province for Travel/Cargo distance proximity
                val liveProvinceMatch = driver.liveProvince.contains(order.pickupProvince) || order.pickupProvince.contains(driver.liveProvince)
                
                if (isCityRide) {
                    // Rule: Only Travel drivers (canDeliver) in their REGISTERED province
                    if (driver.canDeliver && profileProvinceMatch) {
                        val isShared = order.rideType == "SHARED"
                        val title = if (isShared) "طلب رحلة مشتركة بالقرب منك" else "طلب رحلة خاصة بالقرب منك"
                        val tab = if (isShared) "2" else "1"
                        sendFcmNotification(driver.id, title, routeStr, "driver_orders?tab=$tab&orderId=${order.id}")
                    }
                } else if (order.isCargo) {
                    if (driver.canCargo) {
                        if (vOrder == dType || dType.contains(vOrder)) {
                            sendFcmNotification(driver.id, "طلب نقل بضائع جديد بالقرب منك", routeStr, "driver_orders?tab=4&orderId=${order.id}")
                        }
                    }
                } else {
                    if (driver.canDeliver) {
                        if (vOrder == dType || dType.contains(vOrder)) {
                            val isShared = order.rideType == "SHARED"
                            val title = if (isShared) "طلب رحلة مشتركة بالقرب منك" else "طلب رحلة خاصة بالقرب منك"
                            val tab = if (isShared) "2" else "1"
                            sendFcmNotification(driver.id, title, routeStr, "driver_orders?tab=$tab&orderId=${order.id}")
                        }
                    }
                }
            }
        }
    }

    suspend fun acceptOrder(oid: String, did: String, maxCap: Int, currentLoad: Int) = withContext(Dispatchers.IO) {
        val d = getUser(did) ?: return@withContext
        val availableSeats = maxCap - currentLoad
        
        var remainingSeatsToOffer = 0
        var acceptedSeats = 0
        var totalOriginalSeats = 0
        var totalPrice = 0.0
        
        database.child("orders").child(oid).runTransaction(object : Transaction.Handler {
            override fun doTransaction(data: MutableData): Transaction.Result {
                val o = data.getValue(Order::class.java) ?: return Transaction.success(data)
                if (o.driverId != null && o.driverId != did) return Transaction.abort()
                
                totalOriginalSeats = o.passengers
                totalPrice = o.price
                
                if (o.rideType == "PRIVATE") {
                    if (o.passengers > availableSeats) return Transaction.abort() // Full or too many
                    data.child("driverId").value = did
                    data.child("driverName").value = d.name
                    data.child("driverPhone").value = d.phone
                    data.child("vehiclePlate").value = d.vehiclePlate
                    data.child("vehicleColor").value = d.vehicleColor
                    data.child("status").value = "ACCEPTED"
                } else {
                    // SHARED logic: Partial acceptance
                    if (availableSeats <= 0) return Transaction.abort()
                    
                    if (o.passengers > availableSeats) {
                        acceptedSeats = availableSeats
                        remainingSeatsToOffer = o.passengers - acceptedSeats
                        
                        // Proportional price splitting
                        val pricePerPerson = totalPrice / totalOriginalSeats
                        val acceptedPrice = acceptedSeats * pricePerPerson
                        
                        data.child("passengers").value = acceptedSeats
                        data.child("price").value = acceptedPrice
                        data.child("driverId").value = did
                        data.child("driverName").value = d.name
                        data.child("driverPhone").value = d.phone
                        data.child("vehiclePlate").value = d.vehiclePlate
                        data.child("vehicleColor").value = d.vehicleColor
                        data.child("status").value = "ACCEPTED"
                    } else {
                        acceptedSeats = o.passengers
                        data.child("driverId").value = did
                        data.child("driverName").value = d.name
                        data.child("driverPhone").value = d.phone
                        data.child("vehiclePlate").value = d.vehiclePlate
                        data.child("vehicleColor").value = d.vehicleColor
                        data.child("status").value = "ACCEPTED"
                    }
                }
                return Transaction.success(data)
            }
            override fun onComplete(e: DatabaseError?, committed: Boolean, s: DataSnapshot?) {
                if (committed) {
                    val o = s?.getValue(Order::class.java) ?: return
                    
                    // Add to driver's active orders index
                    database.child("users").child(did).child("active_orders").child(oid).setValue(true)

                    scope.launch {
                        if (remainingSeatsToOffer > 0) {
                            // Proportional price for remainder
                            val pricePerPerson = totalPrice / totalOriginalSeats
                            val remainingPrice = remainingSeatsToOffer * pricePerPerson
                            
                            // Create NEW order for the rest
                            val newKey = database.child("orders").push().key ?: UUID.randomUUID().toString()
                            val remainderOrder = o.copy(
                                id = newKey, 
                                passengers = remainingSeatsToOffer, 
                                price = remainingPrice,
                                driverId = null, driverName = null, status = "PENDING"
                            )
                            database.child("orders").child(newKey).setValue(remainderOrder)
                            
                            val msg = "تم تحميل $acceptedSeats فرد من الأوردر الخاص بك، ويتبقى $remainingSeatsToOffer فرد ليتم تحميلهم من قبل سائق آخر"
                            sendFcmNotification(o.userId, "WASALNA", msg, "history?tab=PENDING&orderId=$oid")
                        } else {
                            sendFcmNotification(o.userId, "WASALNA", "تم قبول الاوردر الخاص بك من قبل السائق ${d.name}", "history?tab=PENDING&orderId=$oid")
                        }
                    }
                }
            }
        })
    }

    suspend fun finalizeOrders(ids: List<String>, did: String) { 
        val driver = getUser(did)
        val dName = driver?.name ?: ""
        ids.forEach { id -> 
            database.child("orders").child(id).runTransaction(object : Transaction.Handler {
                override fun doTransaction(data: MutableData): Transaction.Result {
                    val o = data.getValue(Order::class.java) ?: return Transaction.success(data)
                    if (o.status == "CONFIRMED") return Transaction.abort() // Already confirmed
                    data.child("status").value = "CONFIRMED"
                    return Transaction.success(data)
                }
                override fun onComplete(e: DatabaseError?, committed: Boolean, s: DataSnapshot?) {
                    if (committed) {
                        val o = s?.getValue(Order::class.java) ?: return
                        scope.launch {
                            // 1. Send Notification
                            sendFcmNotification(o.userId, "WASALNA", "السائق $dName أكد الأوردر وهو في طريقه إليك", "history?tab=CONFIRMED&orderId=$id")
                            
                            // 2. Handle Debt Settlement (Transfer from User to Driver)
                            val userDebtCurrency = o.pointsDiscount.let { if(it < 0) -it else 0.0 }
                            if (userDebtCurrency > 0) {
                                val userPointVal = getPointValue("USER")
                                val pointsToClear = userDebtCurrency / userPointVal
                                
                                // A. Add points to User (making balance 0)
                                incrementUserPoints(o.userId, pointsToClear)
                                val tidU = database.child("users").child(o.userId).child("point_transactions").push().key ?: ""
                                database.child("users").child(o.userId).child("point_transactions").child(tidU).setValue(
                                    PointTransaction(tidU, pointsToClear, "تم دفع المديونية", System.currentTimeMillis())
                                )
                                
                                // B. Deduct points from Driver (since they get cash)
                                incrementUserPoints(did, -pointsToClear) 
                                val tidD = database.child("users").child(did).child("point_transactions").push().key ?: ""
                                database.child("users").child(did).child("point_transactions").child(tidD).setValue(
                                    PointTransaction(tidD, -pointsToClear, "تحصيل مديونية عميل (مستلمة نقداً)", System.currentTimeMillis())
                                )
                            }

                            // 3. Handle Promo Code Discount Compensation (Added to Driver's Wallet Balance / Points)
                            if (o.promoDiscountAmount > 0) {
                                val driverPointVal = getPointValue("DRIVER")
                                val compPoints = o.promoDiscountAmount / driverPointVal

                                incrementDriverPoints(did, compPoints)
                                incrementUserPoints(did, compPoints)

                                val tidComp = database.child("users").child(did).child("point_transactions").push().key ?: ""
                                val compTrans = PointTransaction(
                                    id = tidComp,
                                    amount = compPoints,
                                    reason = "تعويض كود الخصم للرحلة (+ ${String.format(java.util.Locale.ENGLISH, "%.1f", o.promoDiscountAmount)} ج.م)",
                                    timestamp = System.currentTimeMillis(),
                                    type = "ADMIN_ADD"
                                )
                                database.child("users").child(did).child("point_transactions").child(tidComp).setValue(compTrans)
                            }
                        }
                    }
                }
            })
        } 
    }
    suspend fun purgeTripLocationForOrder(orderId: String) {
        if (orderId.isEmpty()) return
        try {
            val rootRef = FirebaseDatabase.getInstance(dbUrl).reference
            rootRef.child("trip_locations").child(orderId).removeValue().await()
            rootRef.child("trip_locations").child(orderId).setValue(null).await()
            database.child("trip_locations").child(orderId).removeValue().await()
            database.child("trip_locations").child(orderId).setValue(null).await()
            try { FirebaseDatabase.getInstance().reference.child("trip_locations").child(orderId).removeValue() } catch (e: Exception) {}
        } catch (e: Exception) {
            Log.e("FirebaseManager", "Error purging trip_location for $orderId: ${e.message}")
        }
    }

    suspend fun deleteOrder(oid: String) { 
        val o = database.child("orders").child(oid).get().await().getValue(Order::class.java)
        database.child("orders").child(oid).removeValue().await() 
        purgeTripLocationForOrder(oid)
        
        // Remove from active orders index
        o?.let {
            database.child("users").child(it.userId).child("active_orders").child(oid).removeValue()
            if (it.driverId != null) {
                database.child("users").child(it.driverId).child("active_orders").child(oid).removeValue()
            }
        }
    }
    suspend fun getPendingOrders(): List<Order> = try { database.child("orders").orderByChild("status").equalTo("PENDING").get().await().children.mapNotNull { it.getValue(Order::class.java) } } catch (e: Exception) { emptyList() }
    suspend fun getUserOrders(uid: String): List<Order> = try { database.child("orders").orderByChild("userId").equalTo(uid).get().await().children.mapNotNull { it.getValue(Order::class.java) } } catch (e: Exception) { emptyList() }
    suspend fun getDriverReservedOrders(did: String): List<Order> = try { database.child("orders").orderByChild("driverId").equalTo(did).get().await().children.mapNotNull { it.getValue(Order::class.java) } } catch (e: Exception) { emptyList() }
    suspend fun getAllOrders(): List<Order> = try { database.child("orders").limitToLast(500).get().await().children.mapNotNull { it.getValue(Order::class.java) } } catch (e: Exception) { emptyList() }

    suspend fun updateOrderEvent(orderId: String, field: String, timestamp: Any = com.google.firebase.database.ServerValue.TIMESTAMP) {
        val eventTime = if (timestamp == com.google.firebase.database.ServerValue.TIMESTAMP) System.currentTimeMillis() else timestamp
        database.child("orders").child(orderId).child(field).runTransaction(object : Transaction.Handler {
            override fun doTransaction(data: MutableData): Transaction.Result {
                if (data.value == null || data.value == 0L) data.value = eventTime
                return Transaction.success(data)
            }
            override fun onComplete(e: DatabaseError?, committed: Boolean, s: DataSnapshot?) {}
        })
    }

    suspend fun completeOrder(oid: String, awardDriverPoints: Boolean = true) = coroutineScope {
        val o = database.child("orders").child(oid).get().await().getValue(Order::class.java) ?: return@coroutineScope
        
        // 1. Instantly set status to COMPLETED
        if (o.status != "COMPLETED") {
            database.child("orders").child(oid).child("status").setValue("COMPLETED")
        }

        // 2. Parallel non-blocking execution for cleanup, financials & points awarding
        launch(Dispatchers.IO) {
            try {
                purgeTripLocationForOrder(oid)
                database.child("group_chats").child(oid).removeValue()
                database.child("notifications").child("chat_$oid").removeValue()
                database.child("orders").child(oid).child("messages").removeValue()

                if (o.driverId != null) {
                    val sharedKey = "shared_${o.driverId}"
                    database.child("group_chats").child(sharedKey).removeValue()
                    database.child("notifications").child("chat_$sharedKey").removeValue()
                }

                database.child("users").child(o.userId).child("active_orders").child(oid).removeValue()
                if (o.driverId != null) {
                    database.child("users").child(o.driverId).child("active_orders").child(oid).removeValue()
                }

                if (o.driverId != null) { 
                    recordOrderFinancials(o, o.driverId, awardDriverPoints)
                    if (o.compensationId != null && !o.isCompensationSettled) settleCompensation(o.compensationId, o.id, o.driverId)
                    
                    database.child("users").child(o.driverId).child("consecutiveCancellations").setValue(0)
                    database.child("users").child(o.userId).child("consecutiveCancellations").setValue(0)
                }
            } catch (e: Exception) {
                Log.e("FirebaseManager", "Error in completeOrder background task: ${e.message}")
            }
        }
    }

    private suspend fun recordOrderFinancials(order: Order, did: String, awardDriverPoints: Boolean) = coroutineScope {
        if (order.isPointsRecorded) return@coroutineScope
        val logic = getSystemLogicSettings()
        var extra = 0.0
        if (order.driverArrivalTime > 0 && order.customerArrivalTime > 0) {
            val w = ((order.customerArrivalTime - order.driverArrivalTime) / 60000).toInt() - if(order.isCargo) logic.freeUnloadingTime else logic.freeWaitingTime
            if (w > 0) extra += w * logic.extraWaitingPrice
        }
        extra += order.trafficExtraFee
        
        val finalTripPrice = order.price + extra
        database.child("orders").child(order.id).updateChildren(mapOf("price" to finalTripPrice, "isPointsRecorded" to true)).await()
        
        // Award Ride Points on Success
        val vOrder = com.albasel.tawsilaapp.util.VehicleUtils.cleanVehicleName(order.vehicleType)
        val isInsideCity = vOrder.contains("توصيل داخل") || vOrder.contains("city ride") || order.rideType == "INSIDE_CITY" || order.destinationName.contains("توصيل داخل")
        
        val pax = maxOf(1, order.passengers)
        val distInKm = maxOf(1.0, order.distance)

        // Points Logic:
        // 1. Inside City: Customer (10 * pax), Driver (10 fixed)
        // 2. Intercity / Travel: Customer (distInKm * pax), Driver (distInKm)
        val customerPoints = if (isInsideCity) (pax * 10.0) else (distInKm * pax)
        val driverPoints = if (isInsideCity) 10.0 else distInKm

        val customerUser = getUser(order.userId) ?: getUser(order.userPhone)
        val customerUid = customerUser?.id ?: order.userId

        val driverUser = getUser(did) ?: getUser(order.driverPhone ?: "")
        val driverUid = driverUser?.id ?: did

        if (customerPoints > 0 && customerUid.isNotBlank()) {
            incrementUserPoints(customerUid, customerPoints)
            if (customerUser?.phone.isNullOrBlank() == false && customerUser!!.phone != customerUid) {
                incrementUserPoints(customerUser.phone, customerPoints)
            }
            val tidU = database.child("users").child(customerUid).child("point_transactions").push().key ?: UUID.randomUUID().toString()
            val descU = if (isInsideCity) "نقاط رحلة داخلية مكتملة ($pax فرد)" else "نقاط رحلة سفر مكتملة (${distInKm.roundToInt()} كم)"
            val pTransU = PointTransaction(tidU, customerPoints, descU, System.currentTimeMillis())
            
            try { database.child("users").child(customerUid).child("point_transactions").child(tidU).setValue(pTransU).await() } catch (e: Exception) {}
            if (customerUser?.phone.isNullOrBlank() == false && customerUser!!.phone != customerUid) {
                try { database.child("users").child(customerUser.phone).child("point_transactions").child(tidU).setValue(pTransU).await() } catch (e: Exception) {}
            }
        }

        if (awardDriverPoints && driverPoints > 0 && driverUid.isNotBlank()) {
            incrementDriverPoints(driverUid, driverPoints)
            incrementUserPoints(driverUid, driverPoints)
            if (driverUser?.phone.isNullOrBlank() == false && driverUser!!.phone != driverUid) {
                incrementDriverPoints(driverUser.phone, driverPoints)
                incrementUserPoints(driverUser.phone, driverPoints)
            }
            val tidD = database.child("users").child(driverUid).child("point_transactions").push().key ?: UUID.randomUUID().toString()
            val descD = if (isInsideCity) "نقاط رحلة داخلية مكتملة (سائق)" else "نقاط رحلة سفر مكتملة (سائق - ${distInKm.roundToInt()} كم)"
            val pTransD = PointTransaction(tidD, driverPoints, descD, System.currentTimeMillis())

            try { database.child("users").child(driverUid).child("point_transactions").child(tidD).setValue(pTransD).await() } catch (e: Exception) {}
            if (driverUser?.phone.isNullOrBlank() == false && driverUser!!.phone != driverUid) {
                try { database.child("users").child(driverUser.phone).child("point_transactions").child(tidD).setValue(pTransD).await() } catch (e: Exception) {}
            }
        }

        // Supplement Amount for Driver if Customer Used Wallet Points
        if (order.pointsDiscount > 0 && driverUid.isNotBlank()) {
            val userPointVal = getPointValue("USER")
            val supplementPoints = if (userPointVal > 0) (order.pointsDiscount / userPointVal) else order.pointsDiscount
            if (supplementPoints > 0) {
                incrementUserPoints(driverUid, supplementPoints)
                val tidSupp = database.child("users").child(driverUid).child("point_transactions").push().key ?: UUID.randomUUID().toString()
                val descSupp = "(عميل) اضافة مبلغ تكميلي للرحلة بسبب استخدام العميل رصيد نقاط المحفظة"
                val pTransSupp = PointTransaction(tidSupp, supplementPoints, descSupp, System.currentTimeMillis())
                try { database.child("users").child(driverUid).child("point_transactions").child(tidSupp).setValue(pTransSupp).await() } catch (e: Exception) {}
            }
        }

        database.child("users").child(did).runTransaction(object : Transaction.Handler {
            override fun doTransaction(data: MutableData): Transaction.Result {
                val u = data.getValue(User::class.java) ?: return Transaction.success(data)
                data.child("totalEarnings").value = u.totalEarnings + finalTripPrice
                return Transaction.success(data)
            }
            override fun onComplete(e: DatabaseError?, committed: Boolean, s: DataSnapshot?) {}
        })
    }

    // --- Compensation ---
    private suspend fun calculateOrderBaseFare(order: Order): Double {
        val vOrder = com.albasel.tawsilaapp.util.VehicleUtils.cleanVehicleName(order.vehicleType)
        val isInsideCity = vOrder.contains("داخل") || order.rideType == "INSIDE_CITY"
        
        if (isInsideCity) {
            val pax = if (order.passengers > 0) order.passengers else 1
            val singlePersonFare = order.price / pax
            return (singlePersonFare / 2.0).coerceAtLeast(1.0)
        }

        val formulas = getPricingFormulas()
        val prices = getVehiclePricesDetail()
        
        val vType = order.vehicleType
        val dist = order.distance
        
        // Try formula first
        val formula = formulas.filter { 
            it.rideCategory == (if(order.isCargo) "CARGO" else "HOME") && 
            dist >= it.minKm && dist <= it.maxKm
        }
        .sortedWith(compareByDescending<PricingFormula> { it.vehicleType == vType }.thenBy { it.vehicleType == "" || it.vehicleType == "الكل" })
        .firstOrNull { it.vehicleType == vType || it.vehicleType == "" || it.vehicleType == "الكل" }
        
        if (formula != null) return formula.baseFare
        
        // Fallback to vehicle price
        return prices[vType]?.baseFare ?: 20.0
    }

    suspend fun cancelOrderWithCompensation(order: Order, reason: String, forcedAmount: Double? = null) {
        val baseFare = forcedAmount ?: calculateOrderBaseFare(order)
        val prevStatus = order.status
        database.child("orders").child(order.id).updateChildren(mapOf(
            "status" to "CANCELLED_BY_USER",
            "compensationAmount" to baseFare,
            "isCompensationSettled" to false,
            "compensationStatus" to "PENDING"
        )).await()
        
        // Remove from active orders index
        database.child("users").child(order.userId).child("active_orders").child(order.id).removeValue()
        if (order.driverId != null) {
            database.child("users").child(order.driverId).child("active_orders").child(order.id).removeValue()
        }
        
        val userPointVal = getPointValue("USER")
        val feePoints = baseFare / userPointVal
        
        // Handle Debt Revert (If cancelled after confirmation, points were already added to user)
        val userDebtCurrency = order.pointsDiscount.let { if(it < 0) -it else 0.0 }
        val debtPoints = userDebtCurrency / userPointVal
        
        // Total transfer: Debt Revert + New Fee
        val isConfirmed = prevStatus == "CONFIRMED"
        val totalTransfer = if (isConfirmed) debtPoints + feePoints else feePoints
        
        // Check for Suspension
        val customer = getUser(order.userId)
        if (isConfirmed) {
            // Case 1: Multi-Debt Suspension
            if ((customer?.points ?: 0.0) < 0) {
                suspendUser(order.userId, "إلغاء رحلة مؤكدة بمديونية سابقة")
            } else {
                // Case 2: Consecutive Confirmed Cancellations (Second time in a row)
                try {
                    val userRef = database.child("users").child(order.userId)
                    val currentCount = userRef.child("consecutiveCancellations").get().await().getValue(Int::class.java) ?: 0
                    val newCount = currentCount + 1
                    
                    if (newCount >= 2) {
                        suspendUser(order.userId, "إلغاء متكرر لرحلات مؤكدة")
                        userRef.child("consecutiveCancellations").setValue(0).await()
                    } else {
                        userRef.child("consecutiveCancellations").setValue(newCount).await()
                    }
                } catch(e: Exception) { Log.e("Firebase", "User suspension logic error", e) }
            }
        }

        // Deduct from Customer
        updatePointsByPhone(order.userPhone, -totalTransfer, if(isConfirmed) "تعويض رحلة حالية ومديونية رحلة سابقة" else "تعويض إلغاء رحلة مؤكدة")
        
        // Refund Customer Used Points if Order Cancelled Before Completion
        if (order.pointsDiscount > 0) {
            val refundPts = order.pointsDiscount / userPointVal
            if (refundPts > 0) {
                incrementUserPoints(order.userId, refundPts)
                val tidR = database.child("users").child(order.userId).child("point_transactions").push().key ?: UUID.randomUUID().toString()
                database.child("users").child(order.userId).child("point_transactions").child(tidR).setValue(
                    PointTransaction(tidR, refundPts, "(عميل) استرجاع نقاط رحلة ملغاة", System.currentTimeMillis())
                ).await()
            }
        }

        // Add to Driver
        order.driverId?.let { did -> 
            incrementUserPoints(did, totalTransfer)
            val tid = database.child("users").child(did).child("point_transactions").push().key ?: ""
            database.child("users").child(did).child("point_transactions").child(tid).setValue(
                PointTransaction(tid, totalTransfer, if(isConfirmed) "تعويض رحلة حالية ومديونية رحلة سابقة من العميل" else "نقاط عميل (تعويض إلغاء)", System.currentTimeMillis())
            ).await()
            
            sendFcmNotification(did, "WASALNA", "العميل ${order.userName} ألغى الرحلة وتم تعويضك بالنقاط", "wallet")
        }
    }


    suspend fun getPendingCompensation(uid: String): Map<String, Any>? = try {
        val snap = database.child("orders").orderByChild("userId").equalTo(uid).get().await()
        snap.children.mapNotNull { child ->
            val isSettled = child.child("isCompensationSettled").getValue(Boolean::class.java) ?: false
            val status = child.child("compensationStatus").getValue(String::class.java) ?: ""
            val amt = child.child("compensationAmount").getValue(Double::class.java) ?: 0.0
            val id = child.key ?: ""
            if (!isSettled && status == "PENDING" && amt > 0) {
                mapOf("id" to id, "amount" to amt, "status" to status)
            } else null
        }.firstOrNull()
    } catch(e: Exception) { null }

    suspend fun settleCompensation(compId: String, sid: String, did: String) {
        try {
            database.child("orders").child(compId).updateChildren(mapOf(
                "isCompensationSettled" to true,
                "compensationStatus" to "SETTLED"
            )).await()
            val snap = database.child("orders").child(compId).get().await()
            val amt = snap.child("compensationAmount").getValue(Double::class.java) ?: 0.0
            getUser(did)?.let { updatePointsByPhone(it.phone, -amt, "تسوية تعويض") }
        } catch (e: Exception) {}
    }

    // --- Points ---
    suspend fun incrementUserPoints(uid: String, amount: Double) { database.child("users").child(uid).child("points").runTransaction(object : Transaction.Handler { override fun doTransaction(d: MutableData): Transaction.Result { d.value = (d.getValue(Double::class.java) ?: 0.0) + amount; return Transaction.success(d) }; override fun onComplete(e: DatabaseError?, c: Boolean, s: DataSnapshot?) {} }) }
    suspend fun incrementDriverPoints(uid: String, amount: Double) { database.child("users").child(uid).child("driverPoints").runTransaction(object : Transaction.Handler { override fun doTransaction(d: MutableData): Transaction.Result { d.value = (d.getValue(Double::class.java) ?: 0.0) + amount; return Transaction.success(d) }; override fun onComplete(e: DatabaseError?, c: Boolean, s: DataSnapshot?) {} }) }

    suspend fun updatePointsByPhone(phone: String, points: Double, reason: String = "تعديل إداري"): Boolean {
        val uid = database.child("phones").child(phone).get().await().getValue(String::class.java) ?: return false
        val user = getUser(uid) ?: return false
        val tid = database.child("users").child(user.id).child("point_transactions").push().key ?: ""
        database.child("users").child(user.id).child("point_transactions").child(tid).setValue(PointTransaction(tid, points, reason, System.currentTimeMillis())).await()
        if (user.role == "DRIVER" || !user.vehiclePlate.isNullOrEmpty()) incrementDriverPoints(user.id, points) else incrementUserPoints(user.id, points)
        return true
    }

    // --- Settings & Admin ---
    // --- In-Memory Settings Cache ---
    @Volatile private var cachedVehiclePrices: Map<String, VehiclePrice>? = null
    @Volatile private var cachedPricingFormulas: List<PricingFormula>? = null
    @Volatile private var cachedSystemLogic: SystemLogicSettings? = null
    @Volatile private var cachedAdminCommission: Double? = null
    @Volatile private var cachedUserPointValue: Double? = null
    @Volatile private var cachedDriverPointValue: Double? = null
    @Volatile private var cachedPaymentLinks: List<PaymentMethod>? = null
    @Volatile private var cachedCityDestinations: List<CityDestination>? = null

    suspend fun getSharedButtonThreshold(): Double = try { database.child("settings").child("shared_button_threshold").get().await().getValue(Double::class.java) ?: 0.0 } catch(e: Exception) { 0.0 }
    suspend fun setSharedButtonThreshold(threshold: Double) { database.child("settings").child("shared_button_threshold").setValue(threshold).await() }

    suspend fun getAboutPhoneText(): String = withContext(Dispatchers.IO) {
        try {
            database.child("settings").child("about_phone_text").get().await().getValue(String::class.java) ?: ""
        } catch (e: Exception) { "" }
    }

    suspend fun setAboutPhoneText(text: String) = withContext(Dispatchers.IO) {
        try {
            database.child("settings").child("about_phone_text").setValue(text).await()
        } catch (e: Exception) {}
    }

    suspend fun getPointValue(role: String): Double {
        val uppercaseRole = role.uppercase()
        if (uppercaseRole == "USER" && cachedUserPointValue != null) return cachedUserPointValue!!
        if (uppercaseRole == "DRIVER" && cachedDriverPointValue != null) return cachedDriverPointValue!!

        return try { 
            val v = database.child("settings").child("point_values").child(role).get().await().getValue(Double::class.java) 
            val result = if (v != null && v > 0) v else 1.0
            if (uppercaseRole == "USER") cachedUserPointValue = result
            if (uppercaseRole == "DRIVER") cachedDriverPointValue = result
            result
        } catch(e: Exception) { 1.0 }
    }

    suspend fun setPointValue(role: String, value: Double) { 
        if (role.uppercase() == "USER") cachedUserPointValue = value
        if (role.uppercase() == "DRIVER") cachedDriverPointValue = value
        database.child("settings").child("point_values").child(role).setValue(value).await() 
    }

    suspend fun getPricingFormulas(): List<PricingFormula> {
        cachedPricingFormulas?.let { return it }
        return try { 
            val list = database.child("settings").child("pricing_formulas").get().await().children.mapNotNull { it.getValue(PricingFormula::class.java) } 
            cachedPricingFormulas = list
            list
        } catch (e: Exception) { emptyList() }
    }

    suspend fun savePricingFormula(f: PricingFormula) { 
        cachedPricingFormulas = null
        val k = if (f.id.isEmpty()) database.child("settings").child("pricing_formulas").push().key ?: "" else f.id
        database.child("settings").child("pricing_formulas").child(k).setValue(f.copy(id = k)).await() 
    }

    suspend fun deletePricingFormula(id: String) { 
        cachedPricingFormulas = null
        database.child("settings").child("pricing_formulas").child(id).removeValue().await() 
    }

    suspend fun getVehiclePricesDetail(): Map<String, VehiclePrice> {
        cachedVehiclePrices?.let { return it }
        return try { 
            val map = database.child("settings").child("vehicle_prices").get().await().children.mapNotNull { it.getValue(VehiclePrice::class.java) }.associateBy { it.type } 
            cachedVehiclePrices = map
            map
        } catch (e: Exception) { emptyMap() }
    }

    suspend fun setVehiclePrice(vp: VehiclePrice) { 
        cachedVehiclePrices = null
        database.child("settings").child("vehicle_prices").child(vp.type).setValue(vp).await() 
    }

    suspend fun getAdminCommission(): Double {
        cachedAdminCommission?.let { return it }
        return try { 
            val comm = database.child("settings").child("commission").get().await().getValue(Double::class.java) ?: 10.0 
            cachedAdminCommission = comm
            comm
        } catch (e: Exception) { 10.0 }
    }

    suspend fun setAdminCommission(p: Double) { 
        cachedAdminCommission = p
        database.child("settings").child("commission").setValue(p).await() 
    }

    suspend fun getSystemLogicSettings(): SystemLogicSettings {
        cachedSystemLogic?.let { return it }
        return try { 
            val logic = database.child("settings").child("system_logic").get().await().getValue(SystemLogicSettings::class.java) ?: SystemLogicSettings() 
            cachedSystemLogic = logic
            logic
        } catch (e: Exception) { SystemLogicSettings() }
    }

    suspend fun saveSystemLogicSettings(s: SystemLogicSettings) { 
        cachedSystemLogic = s
        database.child("settings").child("system_logic").setValue(s).await() 
    }

    suspend fun getPaymentLinks(): List<PaymentMethod> {
        cachedPaymentLinks?.let { return it }
        return try { 
            val list = database.child("settings").child("payment_links").get().await().children.mapNotNull { it.getValue(PaymentMethod::class.java) } 
            cachedPaymentLinks = list
            list
        } catch (e: Exception) { emptyList() }
    }

    suspend fun setPaymentLinks(ls: List<PaymentMethod>) { 
        cachedPaymentLinks = ls
        database.child("settings").child("payment_links").setValue(ls).await() 
    }

    suspend fun addPaymentReceipt(uid: String, did: String, providedDriverName: String? = null) = withContext(Dispatchers.IO) { 
        try {
            val key = database.child("users").child(uid).child("receipts").push().key ?: UUID.randomUUID().toString()
            database.child("users").child(uid).child("receipts").child(key).setValue(did).await()
            if (did.length > 100) {
                database.child("users").child(uid).child("paymentReceiptBase64").setValue(did).await() 
            } else {
                database.child("users").child(uid).child("paymentReceiptDriveId").setValue(did).await() 
            }
            
            val driverName = providedDriverName.takeIf { !it.isNullOrBlank() }
                ?: getUser(uid)?.name?.takeIf { it.isNotBlank() }
                ?: "السائق"
            val title = "إيصال دفع جديد"
            val body = "السائق $driverName قد ارسل وصل دفع بنجاح"
            val route = "admin_home?tab=0&sub=0&targetId=$uid"

            getAdmins().forEach { admin ->
                try {
                    val adminTargetId = admin.id.ifBlank { admin.phone }
                    if (adminTargetId.isNotBlank()) {
                        sendFcmNotification(adminTargetId, title, body, route, "ADMIN_RECEIPT")
                    }
                } catch (e: Exception) {
                    Log.e("FirebaseManager", "Error notifying admin: ${e.message}")
                }
            }

            try {
                val adminTokens = getAllAdminFcmTokens()
                adminTokens.forEach { token ->
                    FCMV1Helper.sendNotificationWithContext(context, token, title, body, route)
                }
            } catch (e: Exception) {
                Log.e("FirebaseManager", "Error broadcasting FCM to admin tokens: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e("FirebaseManager", "Error adding payment receipt: ${e.message}")
        }
    }

    suspend fun deleteReceipt(uid: String, k: String, directDriveId: String? = null) = withContext(Dispatchers.IO) {
        try {
            val fileId = directDriveId.takeIf { !it.isNullOrEmpty() && !it.startsWith("legacy_") && it.length < 100 }
                ?: database.child("users").child(uid).child("receipts").child(k).get().await().getValue(String::class.java)

            if (!fileId.isNullOrEmpty() && fileId.length < 100 && !fileId.startsWith("data:image")) {
                try { GoogleDriveHelper.deleteFile(fileId) } catch (e: Exception) {}
            }
            
            database.child("users").child(uid).child("receipts").child(k).removeValue().await()
            database.child("users").child(uid).child("paymentReceiptDriveId").removeValue().await()
            database.child("users").child(uid).child("paymentReceiptBase64").removeValue().await()
        } catch (e: Exception) {
            Log.e("FirebaseManager", "Error deleting receipt: ${e.message}")
        }
    }

    suspend fun deletePaymentReceipt(did: String, directDriveId: String? = null) = withContext(Dispatchers.IO) {
        try {
            val fileId = directDriveId.takeIf { !it.isNullOrEmpty() && !it.startsWith("legacy_") && it.length < 100 }
                ?: database.child("users").child(did).child("paymentReceiptDriveId").get().await().getValue(String::class.java)

            if (!fileId.isNullOrEmpty() && fileId.length < 100 && !fileId.startsWith("data:image")) {
                try { GoogleDriveHelper.deleteFile(fileId) } catch (e: Exception) {}
            }
            database.child("users").child(did).child("paymentReceiptDriveId").removeValue().await()
            database.child("users").child(did).child("paymentReceiptBase64").removeValue().await()
            database.child("users").child(did).child("receipts").removeValue().await()
        } catch (e: Exception) {
            Log.e("FirebaseManager", "Error deleting payment receipt: ${e.message}")
        }
    }

    suspend fun getCityDestinations(): List<CityDestination> {
        cachedCityDestinations?.let { return it }
        return try { 
            val list = database.child("settings").child("city_destinations").get().await().children.mapNotNull { it.getValue(CityDestination::class.java) } 
            cachedCityDestinations = list
            list
        } catch (e: Exception) { emptyList() }
    }

    suspend fun saveCityDestination(d: CityDestination) { 
        cachedCityDestinations = null
        val k = if (d.id.isEmpty()) database.child("settings").child("city_destinations").push().key ?: "" else d.id
        database.child("settings").child("city_destinations").child(k).setValue(d.copy(id = k)).await() 
    }

    suspend fun deleteCityDestination(id: String) { 
        cachedCityDestinations = null
        database.child("settings").child("city_destinations").child(id).removeValue().await() 
    }
    suspend fun isPhoneBlocked(p: String): Boolean = try { database.child("settings").child("blocked_phones").child(p).get().await().exists() } catch (e: Exception) { false }
    suspend fun blockPhone(p: String) = withContext(Dispatchers.IO) {
        try {
            val cleanP = formatPhoneKey(p)
            database.child("settings").child("blocked_phones").child(cleanP).setValue(true).await()
            if (p.isNotBlank() && p != cleanP) {
                database.child("settings").child("blocked_phones").child(p).setValue(true).await()
            }
            val u = getUser(p)
            if (u != null && u.id.isNotBlank()) {
                val isDriver = u.vehiclePlate.isNotBlank() || u.role.uppercase() == "DRIVER"
                val updates = mutableMapOf<String, Any?>(
                    "approved" to false,
                    "suspendedReason" to "حظر إداري لرقم الهاتف",
                    "isAdmin" to false,
                    "role" to if (isDriver) "DRIVER" else "USER"
                )
                database.child("users").child(u.id).updateChildren(updates).await()
                database.child("users").child(u.id).child("adminPermissions").removeValue().await()
                database.child("admins").child(u.id).removeValue().await()
            }
        } catch (e: Exception) {
            Log.e("FirebaseManager", "Error suspending account on blockPhone: ${e.message}")
        }
    }
    suspend fun unblockPhone(p: String) = withContext(Dispatchers.IO) {
        try {
            val cleanP = formatPhoneKey(p)
            database.child("settings").child("blocked_phones").child(cleanP).removeValue().await()
            if (p.isNotBlank() && p != cleanP) {
                database.child("settings").child("blocked_phones").child(p).removeValue().await()
            }
        } catch (e: Exception) {}
    }
    suspend fun getBlockedPhones(): List<String> = try { database.child("settings").child("blocked_phones").get().await().children.mapNotNull { it.key } } catch (e: Exception) { emptyList() }
    suspend fun approveDriver(uid: String) { 
        val u = getUser(uid)
        val canonicalUid = u?.id ?: uid
        database.child("users").child(canonicalUid).child("approved").setValue(true).await() 
        sendFcmNotification(canonicalUid, "WASALNA", "تم تفعيل حساب السائق الخاص بك بنجاح!", "home")
    }
    suspend fun approveUser(uid: String) { 
        val u = getUser(uid)
        val canonicalUid = u?.id ?: uid
        database.child("users").child(canonicalUid).child("approved").setValue(true).await() 
        sendFcmNotification(canonicalUid, "WASALNA", "تم تفعيل حسابك بنجاح، يمكنك الآن طلب الرحلات!", "home")
    }
    suspend fun getAllAdminFcmTokens(): Set<String> = withContext(Dispatchers.IO) {
        val tokens = mutableSetOf<String>()
        try {
            val admins = getAdmins()
            admins.forEach { admin ->
                if (!admin.fcmToken.isNullOrEmpty()) tokens.add(admin.fcmToken)
            }
            val usersSnap = database.child("users").get().await()
            for (child in usersSnap.children) {
                val u = child.getValue(User::class.java)
                val r = child.child("role").getValue(String::class.java) ?: u?.role ?: ""
                val p = child.child("phone").getValue(String::class.java) ?: u?.phone ?: ""
                val tok = child.child("fcmToken").getValue(String::class.java) ?: u?.fcmToken ?: ""
                if ((r.equals("ADMIN", ignoreCase = true) || p == "01098804131") && tok.isNotEmpty()) {
                    tokens.add(tok)
                }
            }
        } catch (e: Exception) {
            Log.e("FirebaseManager", "getAllAdminFcmTokens error: ${e.message}")
        }
        tokens
    }

    suspend fun suspendDriver(uid: String, r: String) { 
        database.child("users").child(uid).child("approved").setValue(false).await() 
        sendFcmNotification(uid, "تنبيه هام", "تم تعليق حسابك: $r. يرجى التواصل مع الإدارة.")
        val u = getUser(uid)
        val name = u?.name ?: "سائق مجهول"
        val title = if (r.contains("تحديث بيانات")) "تحديث بيانات سائق" else "تعليق حساب سائق"
        val body = if (r.contains("تحديث بيانات")) "السائق $name قد تم تعليق حسابه لتحديث بياناته يرجى المراجعة" else "السائق $name تم تعليق حسابه - السبب: $r، يرجى المراجعة"
        val route = "admin_home?tab=0&sub=2&targetId=$uid"

        getAdmins().forEach { admin ->
            sendFcmNotification(admin.id, title, body, route, "admin_pending")
        }

        scope.launch {
            try {
                val adminTokens = getAllAdminFcmTokens()
                adminTokens.forEach { token ->
                    FCMV1Helper.sendNotificationWithContext(context, token, title, body, route)
                }
            } catch (e: Exception) {}
        }
    }

    suspend fun suspendUser(uid: String, r: String) { 
        database.child("users").child(uid).child("approved").setValue(false).await() 
        sendFcmNotification(uid, "تنبيه هام", "تم تعليق حسابك نتيجة إلغاء رحلتين مؤكدتين متتاليتين. يرجى التواصل مع الإدارة.")
        val u = getUser(uid)
        val name = u?.name ?: "عميل مجهول"
        val title = "تعليق حساب عميل"
        val body = if (r.contains("إلغاء متكرر") || r.contains("متتاليتين")) "العميل $name قد تم تعليق حسابه نتيجة إلغاء أوردرين متتاليين" else "العميل $name قد تم تعليق حسابه لـ $r، يرجى المراجعة"
        val route = "admin_home?tab=0&sub=2&targetId=$uid"

        getAdmins().forEach { admin ->
            sendFcmNotification(admin.id, title, body, route, "admin_pending")
        }

        scope.launch {
            try {
                val adminTokens = getAllAdminFcmTokens()
                adminTokens.forEach { token ->
                    FCMV1Helper.sendNotificationWithContext(context, token, title, body, route)
                }
            } catch (e: Exception) {}
        }
    }
    suspend fun syncCsvToFirebase(fileName: String, csvContent: String) {
        try {
            val key = fileName.replace(".", "_")
            val data = mapOf(
                "fileName" to fileName,
                "csvContent" to csvContent,
                "modifiedTime" to System.currentTimeMillis()
            )
            database.child("google_drive_files").child(key).setValue(data).await()
            Log.d("FirebaseManager", "CSV $fileName synced to Firebase Realtime DB successfully.")
        } catch (e: Exception) {
            Log.e("FirebaseManager", "Error syncing CSV to Firebase: ${e.message}")
        }
    }

    suspend fun fetchCsvFromFirebase(fileName: String): Map<String, Any>? {
        return try {
            val key = fileName.replace(".", "_")
            val snap = database.child("google_drive_files").child(key).get().await()
            if (snap.exists()) {
                val csvContent = snap.child("csvContent").getValue(String::class.java) ?: ""
                val modifiedTime = snap.child("modifiedTime").getValue(Long::class.java) ?: 0L
                mapOf("csvContent" to csvContent, "modifiedTime" to modifiedTime)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun setRoleByPhone(p: String, r: String): Boolean = withContext(Dispatchers.IO) { 
        try {
            val cleanP = formatPhoneKey(p)
            val user = getUser(p) ?: getUser(cleanP) ?: run {
                val usersSnap = database.child("users").get().await()
                var found: User? = null
                for (child in usersSnap.children) {
                    val u = child.getValue(User::class.java)
                    if (u != null && (u.phone == p || formatPhoneKey(u.phone) == cleanP || child.key == p || child.key == cleanP)) {
                        found = u.copy(id = child.key ?: u.id)
                        break
                    }
                }
                found
            }

            if (user != null && user.id.isNotBlank()) { 
                val uid = user.id
                val isDriver = user.vehiclePlate.isNotBlank() || user.role.uppercase() == "DRIVER"
                if (r == "ADMIN") {
                    val updates = mapOf<String, Any?>(
                        "isAdmin" to true,
                        "role" to "ADMIN"
                    )
                    database.child("users").child(uid).updateChildren(updates).await()
                    database.child("admins").child(uid).setValue(true).await()
                    if (user.phone.isNotBlank() && user.phone != uid) {
                        database.child("users").child(user.phone).updateChildren(updates)
                    }
                } else {
                    val targetRole = if (isDriver) "DRIVER" else "USER"
                    val updates = mapOf<String, Any?>(
                        "isAdmin" to false,
                        "role" to targetRole
                    )
                    database.child("users").child(uid).updateChildren(updates).await()
                    database.child("admins").child(uid).removeValue().await()
                    database.child("users").child(uid).child("adminPermissions").removeValue().await()
                    if (user.phone.isNotBlank() && user.phone != uid) {
                        database.child("users").child(user.phone).updateChildren(updates)
                        database.child("users").child(user.phone).child("adminPermissions").removeValue()
                    }
                }
                return@withContext true 
            }
        } catch (e: Exception) {
            Log.e("FirebaseManager", "Error in setRoleByPhone: ${e.message}")
        }
        return@withContext false 
    }
    suspend fun broadcastOffer(txt: String, pts: Double = 0.0, promoCode: String = "", percentage: Double = 0.0, validityDays: Int = 1) { 
        if (txt.isBlank()) return
        try {
            val processedUserIds = HashSet<String>()
            val snap = database.child("users").get().await()
            val now = System.currentTimeMillis()

            val cleanPromoCode = promoCode.uppercase().trim()
            val formattedPromoText = if (cleanPromoCode.isNotBlank() && percentage > 0) {
                "$txt\n🎟️ كود الخصم: $cleanPromoCode | 🏷️ الخصم: ${percentage.toInt()}% | ⏱️ الصلاحية: $validityDays يوم"
            } else if (pts > 0) {
                "$txt (+ $pts نقطة مضافة إلى محفظتك)"
            } else {
                txt
            }

            for (child in snap.children) {
                try {
                    val key = child.key ?: continue
                    val phone = child.child("phone").getValue(String::class.java) ?: ""
                    
                    // Resolve primary UID from phone index to ensure updating the actual logged-in user account
                    var targetUid = key
                    if (phone.isNotBlank()) {
                        try {
                            val mappedUid = database.child("phones").child(phone).get().await().getValue(String::class.java)
                            if (!mappedUid.isNullOrBlank()) {
                                targetUid = mappedUid
                            }
                        } catch (e: Exception) {
                            Log.w("FirebaseManager", "Could not map phone $phone to primary UID, fallback to key $key")
                        }
                    }

                    if (!processedUserIds.contains(targetUid)) {
                        processedUserIds.add(targetUid)
                        
                        if (pts > 0) {
                            // 1. Fetch current primary user record from Firebase Realtime DB
                            val userSnap = database.child("users").child(targetUid).get().await()
                            val role = userSnap.child("role").getValue(String::class.java) ?: "USER"
                            val isDriver = role == "DRIVER" || userSnap.child("vehiclePlate").exists()

                            val currentPoints = userSnap.child("points").getValue(Double::class.java) ?: 0.0
                            val currentDriverPoints = userSnap.child("driverPoints").getValue(Double::class.java) ?: 0.0
                            val currentWallet = userSnap.child("walletBalance").getValue(Double::class.java) ?: 0.0

                            val newPoints = currentPoints + pts
                            val newWallet = currentWallet + pts

                            val userUpdates = mutableMapOf<String, Any>(
                                "points" to newPoints,
                                "walletBalance" to newWallet
                            )

                            if (isDriver) {
                                userUpdates["driverPoints"] = currentDriverPoints + pts
                            }

                            // 2. Synchronous Atomic Update on primary user node
                            database.child("users").child(targetUid).updateChildren(userUpdates).await()

                            // 3. Record Point Transaction History under primary user's point_transactions node
                            val tid = database.child("users").child(targetUid).child("point_transactions").push().key 
                                ?: java.util.UUID.randomUUID().toString()
                            
                            val pTrans = PointTransaction(
                                id = tid,
                                amount = pts,
                                reason = "عرض ترويجي: $txt (+ $pts نقطة)",
                                timestamp = now,
                                type = "ADMIN_ADD"
                            )

                            database.child("users").child(targetUid).child("point_transactions").child(tid).setValue(pTrans).await()
                        }

                        // 4. Send FCM Notification with full details formatted in text body
                        sendFcmNotification(targetUid, "عرض ترويجي جديد 🎁", formattedPromoText, "home?promo=true") 
                    }
                } catch (e: Exception) {
                    Log.e("FirebaseManager", "Error processing offer for user child ${child.key}: ${e.message}")
                }
            }
            Log.d("FirebaseManager", "Broadcast offer finished successfully for ${processedUserIds.size} users.")
        } catch (e: Exception) {
            Log.e("FirebaseManager", "Error in broadcastOffer: ${e.message}", e)
        }
    }

    // --- Promo Codes & Discount Coupons ---
    suspend fun savePromoCode(code: String, percentage: Double, offerText: String, validityDays: Int = 1) {
        if (code.isBlank() || percentage <= 0) return
        val cleanCode = code.uppercase().trim()
        val days = if (validityDays > 0) validityDays else 1
        val now = System.currentTimeMillis()
        val expiry = now + (days * 86400000L)
        val promo = com.albasel.tawsilaapp.data.model.PromoCode(
            code = cleanCode,
            percentage = percentage,
            offerText = offerText,
            timestamp = now,
            expiryTimestamp = expiry,
            validityDays = days,
            isActive = true
        )
        database.child("settings").child("promo_codes").child(cleanCode).setValue(promo).await()
    }

    suspend fun deletePromoCode(code: String) {
        if (code.isBlank()) return
        val cleanCode = code.uppercase().trim()
        try {
            database.child("settings").child("promo_codes").child(cleanCode).removeValue().await()
        } catch (e: Exception) {
            Log.e("FirebaseManager", "Error deleting promo code: ${e.message}")
        }
    }

    fun listenToPromoCodes(onUpdate: (List<com.albasel.tawsilaapp.data.model.PromoCode>) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val now = System.currentTimeMillis()
                val validList = mutableListOf<com.albasel.tawsilaapp.data.model.PromoCode>()
                
                for (child in snapshot.children) {
                    val promo = child.getValue(com.albasel.tawsilaapp.data.model.PromoCode::class.java) ?: continue
                    if (promo.code.isNotBlank() && promo.isActive) {
                        if (now > promo.expiryTimestamp) {
                            // Auto-delete expired promo code from Firebase Realtime DB
                            database.child("settings").child("promo_codes").child(promo.code).removeValue()
                        } else {
                            validList.add(promo)
                        }
                    }
                }
                onUpdate(validList)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        database.child("settings").child("promo_codes").addValueEventListener(listener)
        return listener
    }

    // --- Super Admin Permissions Management (Super Admin: 01098804131) ---
    fun formatPhoneKey(phone: String): String {
        var digits = phone.replace(Regex("[^0-9]"), "")
        if (digits.startsWith("20")) {
            digits = "0" + digits.substring(2)
        }
        if (!digits.startsWith("0") && digits.length == 10) {
            digits = "0$digits"
        }
        return digits
    }

    suspend fun saveAdminPermissions(phoneOrUid: String, permissions: Map<String, Boolean>): Boolean {
        if (phoneOrUid.isBlank()) return false
        val cleanPhone = formatPhoneKey(phoneOrUid)
        
        return try {
            // Resolve target UID from phone index
            var targetUid = phoneOrUid.trim()
            if (cleanPhone.length >= 10) {
                val mappedUid = try { database.child("phones").child(cleanPhone).get().await().getValue(String::class.java) } catch (e: Exception) { null }
                if (!mappedUid.isNullOrBlank()) {
                    targetUid = mappedUid
                }
            }

            // Save under users/$targetUid/adminPermissions (100% permitted by Firebase Rules)
            database.child("users").child(targetUid).child("adminPermissions").setValue(permissions).await()
            Log.d("FirebaseManager", "Admin permissions saved successfully for $targetUid")
            true
        } catch (e: Exception) {
            Log.e("FirebaseManager", "Error saving admin permissions for $phoneOrUid: ${e.message}", e)
            false
        }
    }

    fun listenToAdminPermissions(phoneOrUid: String, onUpdate: (Map<String, Boolean>) -> Unit): ValueEventListener {
        val cleanPhone = formatPhoneKey(phoneOrUid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val map = mutableMapOf<String, Boolean>()
                for (child in snapshot.children) {
                    val key = child.key ?: continue
                    val value = child.getValue(Boolean::class.java) ?: false
                    map[key] = value
                }
                onUpdate(map)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.w("FirebaseManager", "listenToAdminPermissions cancelled: ${error.message}")
            }
        }

        if (cleanPhone.length >= 10) {
            database.child("phones").child(cleanPhone).get().addOnSuccessListener { phoneSnap ->
                val mappedUid = phoneSnap.getValue(String::class.java) ?: phoneOrUid
                database.child("users").child(mappedUid).child("adminPermissions").addValueEventListener(listener)
            }.addOnFailureListener {
                database.child("users").child(phoneOrUid).child("adminPermissions").addValueEventListener(listener)
            }
        } else {
            database.child("users").child(phoneOrUid).child("adminPermissions").addValueEventListener(listener)
        }

        return listener
    }

    suspend fun broadcastAdminNotification(t: String, b: String) { database.child("users").get().await().children.forEach { sendFcmNotification(it.key!!, t, b) } }
    suspend fun setUpdateLink(url: String, ver: String) { 
        database.child("app_update").child("url").setValue(url).await()
        database.child("app_update").child("version").setValue(ver).await()
        
        // Broadcast App Update Notification to ALL Unique Users
        try {
            val notifiedUids = HashSet<String>()
            val snap = database.child("users").get().await()
            for (child in snap.children) {
                val key = child.key ?: continue
                val phone = child.child("phone").getValue(String::class.java) ?: ""
                val userKey = if (phone.isNotBlank()) phone else key
                
                if (!notifiedUids.contains(userKey)) {
                    notifiedUids.add(userKey)
                    sendFcmNotification(
                        key,
                        "تحديث جديد لتطبيق WASALNA 🚀",
                        "هناك إصدار جديد من WASALNA (إصدار $ver). يرجى تحديث التطبيق الآن للحصول على أحدث الخدمات والميزات.",
                        url,
                        "APP_UPDATE"
                    )
                }
            }
        } catch (e: Exception) { Log.e("FirebaseManager", "Error broadcasting update link", e) }
    }
    suspend fun getContactMessages(): List<Map<String, Any>> = try { database.child("contact_messages").get().await().children.mapNotNull { (it.value as? Map<String, Any>) } } catch (e: Exception) { emptyList() }
    suspend fun deleteContactMessage(id: String) { database.child("contact_messages").child(id).removeValue().await() }
    suspend fun sendContactMessage(n: String, p: String, m: String) { val id = database.child("contact_messages").push().key ?: ""; val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""; val msgData = mapOf("id" to id, "userId" to uid, "name" to n, "phone" to p, "message" to m, "timestamp" to System.currentTimeMillis()); database.child("contact_messages").child(id).setValue(msgData).await(); scope.launch(Dispatchers.IO) { val title = "رسالة تواصل جديدة من $n"; val body = "وصلت رسالة تواصل جديدة من العميل $n ($p): $m"; val route = "admin_home?tab=3"; getAdmins().forEach { admin -> val adminTarget = admin.id.ifBlank { admin.phone }; if (adminTarget.isNotBlank()) sendFcmNotification(adminTarget, title, body, route, "contact_msg") }; try { val tokens = getAllAdminFcmTokens(); tokens.forEach { token -> if (token.isNotBlank()) FCMV1Helper.sendNotificationWithContext(context, token, title, body, route) } } catch (e: Exception) {} } }
    suspend fun sendContactReply(userIdOrPhone: String, messageId: String, replyText: String) { if (messageId.isNotBlank()) { try { database.child("contact_messages").child(messageId).child("reply").setValue(replyText).await(); database.child("contact_messages").child(messageId).child("replyTimestamp").setValue(System.currentTimeMillis()).await(); database.child("contact_messages").child(messageId).child("replyOpenedTimestamp").setValue(0L).await() } catch (e: Exception) {} }; val replyId = if (messageId.isNotBlank()) messageId else (database.child("contact_replies").child(userIdOrPhone).push().key ?: "reply_${System.currentTimeMillis()}"); val replyData = mapOf("id" to replyId, "messageId" to messageId, "userId" to userIdOrPhone, "reply" to replyText, "timestamp" to System.currentTimeMillis(), "openedTimestamp" to 0L); try { database.child("contact_replies").child(userIdOrPhone).child(replyId).setValue(replyData).await() } catch (e: Exception) {}; val user = getUser(userIdOrPhone); val actualUid = user?.id?.takeIf { it.isNotBlank() } ?: userIdOrPhone; sendFcmNotification(actualUid, "رد الإدارة على تواصل معنا", replyText, "contact_reply", "contact_reply") }
    suspend fun markContactReplyOpened(userId: String, replyId: String) { val now = System.currentTimeMillis(); try { if (replyId.isNotBlank()) database.child("contact_messages").child(replyId).child("replyOpenedTimestamp").setValue(now).await() } catch (e: Exception) {}; try { database.child("contact_replies").child(userId).child(replyId).child("openedTimestamp").setValue(now).await() } catch (e: Exception) {} }
    suspend fun getUserContactReply(userId: String): Map<String, Any>? { return try { val user = getUser(userId); val userPhone = user?.phone ?: ""; val messagesSnap = database.child("contact_messages").get().await(); for (child in messagesSnap.children) { val msgUserId = child.child("userId").getValue(String::class.java) ?: ""; val msgPhone = child.child("phone").getValue(String::class.java) ?: ""; val reply = child.child("reply").getValue(String::class.java) ?: ""; if (reply.isNotBlank() && (msgUserId == userId || (userPhone.isNotEmpty() && msgPhone == userPhone))) { val msgId = child.key ?: ""; val openedTs = child.child("replyOpenedTimestamp").getValue(Long::class.java) ?: 0L; if (openedTs > 0L) { val now = System.currentTimeMillis(); if (now - openedTs >= 2 * 60 * 60 * 1000L) { deleteContactReply(userId, msgId); return null } }; return mapOf("id" to msgId, "messageId" to msgId, "userId" to userId, "reply" to reply, "timestamp" to (child.child("replyTimestamp").getValue(Long::class.java) ?: System.currentTimeMillis()), "openedTimestamp" to openedTs) } }; var snap = database.child("contact_replies").child(userId).get().await(); if (!snap.exists() && userPhone.isNotEmpty()) { snap = database.child("contact_replies").child(userPhone).get().await() }; val first = snap.children.firstOrNull() ?: return null; val map = first.value as? Map<String, Any> ?: return null; val openedTs = (map["openedTimestamp"] as? Number)?.toLong() ?: 0L; if (openedTs > 0L) { val now = System.currentTimeMillis(); if (now - openedTs >= 2 * 60 * 60 * 1000L) { val replyId = map["id"]?.toString() ?: ""; deleteContactReply(userId, replyId); return null } }; map } catch (e: Exception) { null } }
    suspend fun deleteContactReply(userId: String, replyId: String) { try { if (replyId.isNotBlank()) { database.child("contact_messages").child(replyId).child("reply").removeValue().await(); database.child("contact_messages").child(replyId).child("replyTimestamp").removeValue().await(); database.child("contact_messages").child(replyId).child("replyOpenedTimestamp").removeValue().await() }; database.child("contact_replies").child(userId).child(replyId).removeValue().await(); val userPhone = getUser(userId)?.phone ?: ""; if (userPhone.isNotEmpty()) { database.child("contact_replies").child(userPhone).child(replyId).removeValue().await() } } catch (e: Exception) {} }
    suspend fun notifyAdminsAboutNewDriver(name: String, isUpdate: Boolean = false, targetId: String = "") { 
        scope.launch { 
            val title = if (isUpdate) "تعليق حساب سائق" else "سجل سائق جديد"
            val body = if (isUpdate) "السائق $name قد تم تعليق حسابه لتحديث بياناته يرجى المراجعة" else "هناك سائق جديد باسم $name يرجى المراجعة"
            val route = if (targetId.isNotEmpty()) "admin_home?tab=0&sub=2&targetId=$targetId" else "admin_home?tab=0&sub=2"
            
            getAdmins().forEach { admin -> 
                sendFcmNotification(admin.id, title, body, route, "admin_pending") 
            } 

            try {
                val adminTokens = getAllAdminFcmTokens()
                adminTokens.forEach { token ->
                    FCMV1Helper.sendNotificationWithContext(context, token, title, body, route)
                }
            } catch (e: Exception) {}
        } 
    }

    suspend fun resetUserStats(uidOrPhone: String) = withContext(Dispatchers.IO) {
        try {
            if (uidOrPhone.isEmpty()) return@withContext

            val targetUids = mutableSetOf<String>()
            targetUids.add(uidOrPhone)

            val uidFromPhone = database.child("phones").child(uidOrPhone).get().await().getValue(String::class.java)
            if (!uidFromPhone.isNullOrEmpty()) {
                targetUids.add(uidFromPhone)
            }

            val user = getUser(uidOrPhone) ?: (if (!uidFromPhone.isNullOrEmpty()) getUser(uidFromPhone) else null)
            if (user != null) {
                if (user.id.isNotEmpty()) targetUids.add(user.id)
                if (user.phone.isNotEmpty()) {
                    targetUids.add(user.phone)
                    val altUid = database.child("phones").child(user.phone).get().await().getValue(String::class.java)
                    if (!altUid.isNullOrEmpty()) targetUids.add(altUid)
                }
            }

            val userUpdates = mapOf<String, Any>(
                "totalEarnings" to 0.0,
                "totalCommission" to 0.0,
                "pendingCommission" to 0.0,
                "paidCommission" to 0.0,
                "walletBalance" to 0.0,
                "points" to 0.0,
                "driverPoints" to 0.0,
                "pointsDiscount" to 0.0,
                "consecutiveCancellations" to 0
            )

            // Parallel async execution for maximum ultra-fast speed (< 30ms)
            coroutineScope {
                targetUids.filter { it.isNotEmpty() }.map { key ->
                    async {
                        try { database.child("users").child(key).updateChildren(userUpdates).await() } catch (e: Exception) {}
                        try { database.child("users").child(key).child("points").setValue(0.0).await() } catch (e: Exception) {}
                        try { database.child("users").child(key).child("driverPoints").setValue(0.0).await() } catch (e: Exception) {}
                        try { database.child("users").child(key).child("walletBalance").setValue(0.0).await() } catch (e: Exception) {}
                        try { database.child("users").child(key).child("totalEarnings").setValue(0.0).await() } catch (e: Exception) {}
                        try { database.child("users").child(key).child("pendingCommission").setValue(0.0).await() } catch (e: Exception) {}
                        try { database.child("users").child(key).child("paidCommission").setValue(0.0).await() } catch (e: Exception) {}
                        try { database.child("users").child(key).child("totalCommission").setValue(0.0).await() } catch (e: Exception) {}
                        try { database.child("users").child(key).child("pointsDiscount").setValue(0.0).await() } catch (e: Exception) {}

                        try { database.child("users").child(key).child("active_orders").removeValue() } catch (e: Exception) {}
                        try { database.child("users").child(key).child("point_transactions").removeValue() } catch (e: Exception) {}
                        try { database.child("users").child(key).child("receipts").removeValue() } catch (e: Exception) {}
                        try { database.child("users").child(key).child("paymentReceiptDriveId").removeValue() } catch (e: Exception) {}
                        try { database.child("users").child(key).child("paymentReceiptBase64").removeValue() } catch (e: Exception) {}
                        try { database.child("users").child(key).child("commission_payments").removeValue() } catch (e: Exception) {}
                        try { database.child("point_transactions").child(key).removeValue() } catch (e: Exception) {}
                        try { database.child("commission_payments").child(key).removeValue() } catch (e: Exception) {}
                    }
                }.awaitAll()

                async {
                    try {
                        val ordersSnap = database.child("orders").get().await()
                        ordersSnap.children.forEach { orderChild ->
                            try {
                                val oUserId = orderChild.child("userId").getValue(String::class.java) ?: ""
                                val oDriverId = orderChild.child("driverId").getValue(String::class.java) ?: ""
                                val oUserPhone = orderChild.child("userPhone").getValue(String::class.java) ?: ""
                                val oDriverPhone = orderChild.child("driverPhone").getValue(String::class.java) ?: ""
                                val oId = orderChild.key ?: return@forEach

                                val matchesTarget = targetUids.contains(oUserId) || targetUids.contains(oDriverId) ||
                                                    (oUserPhone.isNotEmpty() && targetUids.contains(oUserPhone)) ||
                                                    (oDriverPhone.isNotEmpty() && targetUids.contains(oDriverPhone))
                                if (matchesTarget) {
                                    orderChild.ref.removeValue()
                                    database.child("trip_locations").child(oId).removeValue()
                                }
                            } catch (e: Exception) {}
                        }
                    } catch (e: Exception) {}
                }.await()
            }
        } catch (e: Exception) {
            Log.e("FirebaseManager", "Error resetting user stats: ${e.message}", e)
        }
    }

    suspend fun cleanOrphanedTripLocations() = withContext(Dispatchers.IO) {
        try {
            val rootRef = FirebaseDatabase.getInstance(dbUrl).reference
            
            // 1. Force wipe root trip_locations
            try { rootRef.child("trip_locations").setValue(null).await() } catch (e: Exception) {}
            try { rootRef.child("trip_locations").removeValue().await() } catch (e: Exception) {}
            
            // 2. Force wipe sub-node tawsilaappfinal/trip_locations
            try { database.child("trip_locations").setValue(null).await() } catch (e: Exception) {}
            try { database.child("trip_locations").removeValue().await() } catch (e: Exception) {}
            
            // 3. Force wipe default instance
            try { FirebaseDatabase.getInstance().reference.child("trip_locations").setValue(null).await() } catch (e: Exception) {}
            try { FirebaseDatabase.getInstance().reference.child("trip_locations").removeValue().await() } catch (e: Exception) {}

            Log.d("FirebaseManager", "COMPLETELY WIPED ALL TRIP_LOCATIONS FROM FIREBASE!")
        } catch (e: Exception) {
            Log.e("FirebaseManager", "Error force wiping trip_locations: ${e.message}")
        }
    }

    suspend fun unifyAndCleanDuplicateUsers() = withContext(Dispatchers.IO) {
        try {
            val usersSnap = database.child("users").get().await()
            val phoneToPrimaryUid = mutableMapOf<String, String>()
            val phoneDuplicateKeys = mutableListOf<String>()

            for (child in usersSnap.children) {
                val key = child.key ?: continue
                val u = child.getValue(User::class.java) ?: continue
                val phone = formatPhoneKey(u.phone.ifBlank { key })

                val isPhoneKey = key.all { it.isDigit() } || key == phone || (key.length <= 11 && !key.startsWith("-"))
                if (isPhoneKey) {
                    phoneDuplicateKeys.add(key)
                } else if (phone.isNotBlank()) {
                    phoneToPrimaryUid[phone] = key
                }
            }

            for (dupKey in phoneDuplicateKeys) {
                try {
                    val dupSnap = database.child("users").child(dupKey).get().await()
                    val u = dupSnap.getValue(User::class.java)
                    val rawPhone = u?.phone?.ifBlank { dupKey } ?: dupKey
                    val phone = formatPhoneKey(rawPhone)

                    var primaryUid = phoneToPrimaryUid[phone]
                    if (primaryUid.isNullOrBlank()) {
                        primaryUid = database.child("phones").child(phone).get().await().getValue(String::class.java)
                    }

                    if (!primaryUid.isNullOrBlank() && primaryUid != dupKey) {
                        database.child("phones").child(phone).setValue(primaryUid).await()
                        database.child("users").child(dupKey).removeValue().await()
                        Log.d("FirebaseManager", "Cleaned duplicate phone node: $dupKey -> Primary UID: $primaryUid")
                    } else if (u != null) {
                        val newPrimaryUid = database.child("users").push().key ?: UUID.randomUUID().toString()
                        val userWithId = u.copy(id = newPrimaryUid)
                        database.child("users").child(newPrimaryUid).setValue(userWithId).await()
                        database.child("phones").child(phone).setValue(newPrimaryUid).await()
                        if (u.username.isNotBlank()) {
                            database.child("usernames").child(u.username.lowercase().trim()).setValue(newPrimaryUid).await()
                        }
                        database.child("users").child(dupKey).removeValue().await()
                        Log.d("FirebaseManager", "Migrated standalone phone node $dupKey -> New Primary UID: $newPrimaryUid")
                    }
                } catch (e: Exception) {
                    Log.e("FirebaseManager", "Error cleaning dupKey $dupKey: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("FirebaseManager", "Error in unifyAndCleanDuplicateUsers: ${e.message}")
        }
    }

    suspend fun cleanAndOrganizeDatabase() = withContext(Dispatchers.IO) {
        try {
            // 1. Remove Unused Legacy Root Nodes (Consolidating online_drivers into users node)
            try { database.child("fcm_notifications").removeValue().await() } catch (e: Exception) {}
            try { database.child("financial_ledger").removeValue().await() } catch (e: Exception) {}
            try { database.child("online_drivers").removeValue().await() } catch (e: Exception) {}

            // 2. Clean Duplicate Phone User Nodes & Incomplete Ghost User Nodes in /users
            // 1. Unify and clean all duplicate user nodes (delete phone-key duplicates, keep primary UID)
            unifyAndCleanDuplicateUsers()

            val usersSnap = database.child("users").get().await()
            usersSnap.children.forEach { child ->
                try {
                    val key = child.key ?: return@forEach
                    val isPhoneKey = key.length <= 11 && !key.startsWith("-") && key.all { it.isDigit() }
                    val name = child.child("name").getValue(String::class.java) ?: ""
                    val phone = child.child("phone").getValue(String::class.java) ?: ""
                    val role = child.child("role").getValue(String::class.java) ?: ""
                    
                    if (isPhoneKey) {
                        database.child("users").child(key).removeValue()
                        database.child("point_transactions").child(key).removeValue()
                    } else if (name.isBlank() || phone.isBlank() || role.isBlank()) {
                        // Delete incomplete ghost user nodes (e.g. missing name, phone, or role)
                        database.child("users").child(key).removeValue()
                        database.child("point_transactions").child(key).removeValue()
                    }
                } catch (e: Exception) {}
            }

            // 3. Clean Cancelled / Rejected Orders & Temporary Trip Locations
            val ordersSnap = database.child("orders").get().await()
            val now = System.currentTimeMillis()
            val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000

            ordersSnap.children.forEach { child ->
                try {
                    val status = child.child("status").getValue(String::class.java) ?: ""
                    val ts = child.child("timestamp").getValue(Long::class.java) ?: 0L
                    val orderId = child.key ?: return@forEach
                    val userId = child.child("userId").getValue(String::class.java) ?: ""
                    val driverId = child.child("driverId").getValue(String::class.java) ?: ""

                    val isCancelled = status.contains("CANCELLED") || status == "REJECTED"
                    val isOld = (now - ts) > thirtyDaysMs

                    if (isCancelled || (status == "COMPLETED" && isOld)) {
                        database.child("orders").child(orderId).removeValue()
                        database.child("trip_locations").child(orderId).removeValue()
                        database.child("messages").child(orderId).removeValue()
                        if (userId.isNotEmpty()) database.child("user_active_orders").child(userId).child(orderId).removeValue()
                        if (driverId.isNotEmpty()) database.child("driver_active_orders").child(driverId).child(orderId).removeValue()
                    }
                } catch (e: Exception) {}
            }

            // 4. Prune Old System Notifications (Keep last 15 per user)
            val notifsSnap = database.child("system_notifications").get().await()
            notifsSnap.children.forEach { userNotifs ->
                try {
                    val uid = userNotifs.key ?: return@forEach
                    val children = userNotifs.children.toList()
                    if (children.size > 15) {
                        children.dropLast(15).forEach { oldNotif ->
                            oldNotif.ref.removeValue()
                        }
                    }
                } catch (e: Exception) {}
            }
            // 5. Purge any orphaned trip_locations entries for non-active orders
            cleanOrphanedTripLocations()

            Log.d("FirebaseManager", "Database clean & organize completed successfully.")
        } catch (e: Exception) {
            Log.e("FirebaseManager", "Error cleaning database: ${e.message}", e)
        }
    }

    // --- Notifications & Messaging ---
    suspend fun sendFcmNotification(uidOrPhone: String, title: String, body: String, target: String = "", type: String = "GENERAL") {
        if (uidOrPhone.isEmpty()) return
        
        val targetUid = if (!uidOrPhone.startsWith("-") && uidOrPhone.length <= 11) {
            database.child("phones").child(uidOrPhone).get().await().getValue(String::class.java) ?: uidOrPhone
        } else uidOrPhone

        val user = getUser(targetUid) ?: (if (targetUid != uidOrPhone) getUser(uidOrPhone) else null)
        val actualUid = user?.id ?: targetUid
        
        val token = user?.fcmToken.takeIf { !it.isNullOrEmpty() }
            ?: database.child("users").child(actualUid).child("fcmToken").get().await().getValue(String::class.java)
            ?: database.child("users").child(uidOrPhone).child("fcmToken").get().await().getValue(String::class.java)

        if (!token.isNullOrEmpty()) {
            FCMV1Helper.sendNotificationWithContext(context, token, title, body, target)
        }
        val nid = database.child("system_notifications").child(actualUid).push().key ?: ""
        database.child("system_notifications").child(actualUid).child(nid).setValue(
            mapOf("id" to nid, "title" to title, "content" to body, "type" to type, "sourceId" to target, "timestamp" to System.currentTimeMillis())
        )
    }
    suspend fun deleteNotification(uid: String, nid: String) { database.child("system_notifications").child(uid).child(nid).removeValue().await() }
    suspend fun clearAllNotifications(uid: String) { database.child("system_notifications").child(uid).removeValue().await() }

    suspend fun markMessagesAsRead(tripId: String, myUserId: String) = withContext(Dispatchers.IO) {
        try {
            val snapGroup = database.child("group_chats").child(tripId).get().await()
            snapGroup.children.forEach { child ->
                val msg = child.getValue(Message::class.java)
                if (msg != null && msg.senderId != myUserId && !msg.isRead) {
                    child.ref.child("isRead").setValue(true)
                }
            }
        } catch (e: Exception) {}
        try {
            val snapOrder = database.child("orders").child(tripId).child("messages").get().await()
            snapOrder.children.forEach { child ->
                val msg = child.getValue(Message::class.java)
                if (msg != null && msg.senderId != myUserId && !msg.isRead) {
                    child.ref.child("isRead").setValue(true)
                }
            }
        } catch (e: Exception) {}
    }

    suspend fun sendMessage(m: Message) = withContext(Dispatchers.IO) {
        val msgId = UUID.randomUUID().toString()
        val msgToSave = m.copy(id = msgId, isRead = false, timestamp = System.currentTimeMillis())
        val cleanTid = m.tripId.trim()

        try {
            database.child("group_chats").child(cleanTid).child(msgId).setValue(msgToSave).await()
        } catch (e: Exception) {
            Log.e("FirebaseManager", "Error writing msg to group_chats/$cleanTid: ${e.message}")
        }

        try {
            database.child("notifications").child("chat_$cleanTid").child(msgId).setValue(msgToSave)
        } catch (e: Exception) {}

        if (cleanTid.startsWith("shared_")) {
            val rawDriverId = cleanTid.removePrefix("shared_")
            if (rawDriverId.isNotBlank()) {
                try {
                    database.child("group_chats").child(rawDriverId).child(msgId).setValue(msgToSave)
                    database.child("notifications").child("chat_$rawDriverId").child(msgId).setValue(msgToSave)
                } catch (e: Exception) {}
            }
        } else {
            try {
                database.child("orders").child(cleanTid).child("messages").child(msgId).setValue(msgToSave)
            } catch (e: Exception) {}
        }

        try {
            val recipientIds = mutableSetOf<String>()
            if (cleanTid.startsWith("shared_")) {
                val driverId = cleanTid.removePrefix("shared_")
                if (driverId.isNotEmpty() && driverId != m.senderId) {
                    recipientIds.add(driverId)
                }
                val ordersSnap = database.child("orders").get().await()
                ordersSnap.children.forEach { child ->
                    val o = child.getValue(Order::class.java)
                    if (o != null && o.driverId == driverId && o.rideType == "SHARED" &&
                        o.status in listOf("ACCEPTED", "CONFIRMED", "ARRIVED", "STARTED", "IN_PROGRESS")) {
                        if (o.userId.isNotEmpty() && o.userId != m.senderId) {
                            recipientIds.add(o.userId)
                        }
                    }
                }
            } else {
                val orderSnap = database.child("orders").child(cleanTid).get().await()
                val o = orderSnap.getValue(Order::class.java)
                if (o != null) {
                    val targetUid = if (m.senderId == o.userId) o.driverId else o.userId
                    if (!targetUid.isNullOrBlank() && targetUid != m.senderId) {
                        recipientIds.add(targetUid)
                    }
                    if (!o.driverId.isNullOrBlank()) {
                        val activeDriverId = o.driverId
                        val ordersSnap = database.child("orders").get().await()
                        ordersSnap.children.forEach { child ->
                            val ord = child.getValue(Order::class.java)
                            if (ord != null && ord.driverId == activeDriverId &&
                                ord.status in listOf("ACCEPTED", "CONFIRMED", "ARRIVED", "STARTED", "IN_PROGRESS")) {
                                if (ord.userId.isNotEmpty() && ord.userId != m.senderId) {
                                    recipientIds.add(ord.userId)
                                }
                            }
                        }
                    }
                }
            }

            val title = "رسالة جديدة 💬"
            val body = "${m.senderName}: ${m.content}"
            val routeStr = "chat/$cleanTid"

            recipientIds.forEach { rid ->
                try {
                    sendFcmNotification(rid, title, body, routeStr, "CHAT")
                } catch (e: Exception) {
                    Log.e("FirebaseManager", "Error notifying chat recipient $rid: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("FirebaseManager", "Error in FCM notification dispatch: ${e.message}")
        }
    }

    fun getMessages(tid: String, onUpdate: (List<Message>) -> Unit): ValueEventListener {
        val msgsMap = java.util.concurrent.ConcurrentHashMap<String, Message>()

        fun emitMerged() {
            val list = msgsMap.values.sortedBy { it.timestamp }
            onUpdate(list)
        }

        val listenerGroup = object : ValueEventListener { 
            override fun onDataChange(s: DataSnapshot) { 
                s.children.mapNotNull { it.getValue(Message::class.java) }.forEach { msg ->
                    if (!msg.id.isNullOrBlank()) msgsMap[msg.id] = msg
                }
                emitMerged()
            } 
            override fun onCancelled(e: DatabaseError) {} 
        }

        val cleanTid = tid.trim()
        database.child("group_chats").child(cleanTid).addValueEventListener(listenerGroup)
        database.child("notifications").child("chat_$cleanTid").addValueEventListener(listenerGroup)
        database.child("orders").child(cleanTid).child("messages").addValueEventListener(listenerGroup)

        val rawId = cleanTid.removePrefix("shared_")
        if (rawId.isNotBlank()) {
            database.child("group_chats").child(rawId).addValueEventListener(listenerGroup)
            database.child("notifications").child("chat_$rawId").addValueEventListener(listenerGroup)
            database.child("orders").child(rawId).child("messages").addValueEventListener(listenerGroup)
        }
        return listenerGroup
    }

    suspend fun undoOrderAcceptance(oid: String) { 
        val o = database.child("orders").child(oid).get().await().getValue(Order::class.java) ?: return
        val prevStatus = o.status
        val prevDriverId = o.driverId
        database.child("orders").child(oid).updateChildren(mapOf("status" to "PENDING", "driverId" to null, "driverName" to null)).await() 
        
        // Remove from driver's active orders index
        if (prevDriverId != null) {
            database.child("users").child(prevDriverId).child("active_orders").child(oid).removeValue()
        }
        
        // We KEEP it in user_active_orders because it's still a pending request.

        if (prevStatus == "CONFIRMED" && prevDriverId != null) {
            try {
                val driverRef = database.child("users").child(prevDriverId)
                val currentCount = driverRef.child("consecutiveCancellations").get().await().getValue(Int::class.java) ?: 0
                val newCount = currentCount + 1
                
                if (newCount >= 2) {
                    suspendDriver(o.driverId, "إلغاء متكرر لرحلات مؤكدة")
                    driverRef.child("consecutiveCancellations").setValue(0).await()
                } else {
                    driverRef.child("consecutiveCancellations").setValue(newCount).await()
                }
            } catch(e: Exception) { Log.e("Firebase", "Driver suspension logic error", e) }
        }
    }
    suspend fun rejectOrder(oid: String) { 
        val o = database.child("orders").child(oid).get().await().getValue(Order::class.java)
        database.child("orders").child(oid).child("status").setValue("REJECTED").await() 
        
        // Remove from active orders index
        o?.let { order ->
            database.child("users").child(order.userId).child("active_orders").child(oid).removeValue()
            if (order.driverId != null) {
                database.child("users").child(order.driverId).child("active_orders").child(oid).removeValue()
            }

            // Send notification to customer if driver rejected their direct nearest driver order request
            val driverName = order.driverName.takeIf { !it.isNullOrEmpty() } ?: "السائق"
            sendFcmNotification(
                order.userId,
                "رفض طلب الرحلة",
                "السائق $driverName قد رفض أوردر أقرب سائق، يرجى اختيار سائق آخر بالقرب منك",
                "nearest_drivers",
                "ORDER_REJECTED"
            )
        }
    }
    
    // --- Utils & Listeners ---
    fun listenToUserLocation(uid: String, onUpdate: (Double, Double) -> Unit): ValueEventListener { 
        val l = object : ValueEventListener { 
            override fun onDataChange(s: DataSnapshot) { 
                val lat = s.child("latitude").getValue(Double::class.java) ?: 0.0
                val lng = s.child("longitude").getValue(Double::class.java) ?: 0.0
                if (lat != 0.0 && lng != 0.0) onUpdate(lat, lng) 
            }
            override fun onCancelled(e: DatabaseError) {} 
        }
        database.child("users").child(uid).addValueEventListener(l)
        return l 
    }
    fun getPointTransactions(uid: String, onUpdate: (List<PointTransaction>) -> Unit) { database.child("users").child(uid).child("point_transactions").addValueEventListener(object : ValueEventListener { override fun onDataChange(s: DataSnapshot) { onUpdate(s.children.mapNotNull { it.getValue(PointTransaction::class.java) }.sortedByDescending { it.timestamp }) }; override fun onCancelled(e: DatabaseError) {} }) }
    
    fun getDriverLocations(onUpdate: (List<User>) -> Unit): ValueEventListener { 
        return listenToOnlineDrivers(onUpdate)
    }
    fun listenToOnlineDrivers(onUpdate: (List<User>) -> Unit): ValueEventListener {
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                val now = System.currentTimeMillis()
                val list = s.children.mapNotNull { child ->
                    try {
                        val isOnline = child.child("isOnline").getValue(Boolean::class.java) ?: false
                        val lastSeen = child.child("lastSeen").getValue(Long::class.java) ?: 0L
                        if (isOnline && (now - lastSeen) < 900000) {
                            User(
                                id = child.key ?: "",
                                name = child.child("name").getValue(String::class.java) ?: "",
                                vehicleType = child.child("vehicleType").getValue(String::class.java) ?: "",
                                latitude = child.child("latitude").getValue(Double::class.java) ?: 0.0,
                                longitude = child.child("longitude").getValue(Double::class.java) ?: 0.0,
                                lastSeen = lastSeen,
                                isOnline = isOnline,
                                isApproved = child.child("isApproved").getValue(Boolean::class.java) ?: true,
                                canDeliver = child.child("canDeliver").getValue(Boolean::class.java) ?: true,
                                canCargo = child.child("canCargo").getValue(Boolean::class.java) ?: false
                            )
                        } else null
                    } catch (e: Exception) { null }
                }
                onUpdate(list)
            }
            override fun onCancelled(e: DatabaseError) {}
        }
        database.child("users").orderByChild("isOnline").equalTo(true).addValueEventListener(l)
        return l
    }
    suspend fun getPointsDiscounts(): Map<Int, Double> = try { database.child("settings").child("points_discounts").get().await().children.associate { (it.key?.toInt() ?: 0) to (it.getValue(Double::class.java) ?: 0.0) } } catch (e: Exception) { emptyMap() }
    suspend fun applyPointsDiscount(uid: String, pts: Double, d: Double) {
        val u = getUser(uid) ?: return
        database.child("users").child(uid).child("points").setValue((u.points - pts).coerceAtLeast(0.0)).await()
    }
    suspend fun rateOrder(oid: String, r: Float, f: String) { 
        database.child("orders").child(oid).updateChildren(mapOf("rating" to r, "feedback" to f)).await() 
        val order = database.child("orders").child(oid).get().await().getValue(Order::class.java)
        if (order != null && order.driverId != null) {
            database.child("users").child(order.driverId).child("ratings").child(oid).setValue(mapOf("rating" to r, "feedback" to f, "timestamp" to System.currentTimeMillis()))
        }
    }
    suspend fun rateUser(oid: String, r: Float, f: String) { 
        database.child("orders").child(oid).updateChildren(mapOf("userRating" to r, "userFeedback" to f)).await() 
        val order = database.child("orders").child(oid).get().await().getValue(Order::class.java)
        if (order != null) {
            database.child("users").child(order.userId).child("ratings").child(oid).setValue(mapOf("rating" to r, "feedback" to f, "timestamp" to System.currentTimeMillis()))
        }
    }

    suspend fun findUserByUidOrPhone(uid: String): User? = withContext(Dispatchers.IO) {
        if (uid.isBlank()) return@withContext null
        try {
            val directSnap = database.child("users").child(uid).get().await()
            val directUser = directSnap.getValue(User::class.java)
            if (directUser != null && directUser.id.isNotEmpty()) return@withContext directUser.copy(id = directSnap.key ?: directUser.id)
            
            val phoneSnap = database.child("phones").child(uid).get().await()
            val mappedUid = phoneSnap.getValue(String::class.java)
            if (!mappedUid.isNullOrBlank()) {
                val mappedUserSnap = database.child("users").child(mappedUid).get().await()
                val mappedUser = mappedUserSnap.getValue(User::class.java)
                if (mappedUser != null && mappedUser.id.isNotEmpty()) return@withContext mappedUser.copy(id = mappedUserSnap.key ?: mappedUser.id)
            }
            
            val allUsersSnap = database.child("users").get().await()
            for (child in allUsersSnap.children) {
                val u = child.getValue(User::class.java)
                if (u != null && (child.key == uid || u.id == uid || u.phone == uid)) {
                    val keyId = child.key ?: u.id
                    return@withContext u.copy(id = keyId.ifEmpty { u.id })
                }
            }
        } catch (e: Exception) {
            Log.e("FirebaseManager", "findUserByUidOrPhone error: ${e.message}")
        }
        null
    }

    fun listenToCommissionPayments(uid: String, phone: String = "", onUpdate: (List<CommissionPayment>) -> Unit) { 
        val keys = setOf(uid, phone).filter { it.isNotBlank() }
        val combinedMap = mutableMapOf<String, List<CommissionPayment>>()
        
        keys.forEach { key ->
            val path1 = database.child("users").child(key).child("commission_payments")
            val path2 = database.child("commission_payments").child(key)

            val parseSnap = { snapshot: DataSnapshot ->
                val list = mutableListOf<CommissionPayment>()
                snapshot.children.forEach { child ->
                    val pId = child.key ?: child.child("id").getValue(String::class.java) ?: ""
                    val amt = when (val v = child.child("amount").value ?: child.value) {
                        is Number -> v.toDouble()
                        is String -> v.toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }
                    val ts = when (val v = child.child("timestamp").value) {
                        is Number -> v.toLong()
                        is String -> v.toLongOrNull() ?: System.currentTimeMillis()
                        else -> System.currentTimeMillis()
                    }
                    if (amt > 0 && pId != "amount" && pId != "timestamp") {
                        list.add(CommissionPayment(pId, amt, ts))
                    }
                }
                list
            }

            path1.addValueEventListener(object : ValueEventListener { 
                override fun onDataChange(s: DataSnapshot) { 
                    combinedMap["${key}_user"] = parseSnap(s)
                    val allCombined = combinedMap.values.flatten().distinctBy { if (it.id.isNotBlank()) it.id else "${it.timestamp}_${it.amount}" }.sortedByDescending { it.timestamp }
                    onUpdate(allCombined)
                } 
                override fun onCancelled(e: DatabaseError) {} 
            })

            path2.addValueEventListener(object : ValueEventListener { 
                override fun onDataChange(s: DataSnapshot) { 
                    combinedMap["${key}_top"] = parseSnap(s)
                    val allCombined = combinedMap.values.flatten().distinctBy { if (it.id.isNotBlank()) it.id else "${it.timestamp}_${it.amount}" }.sortedByDescending { it.timestamp }
                    onUpdate(allCombined)
                } 
                override fun onCancelled(e: DatabaseError) {} 
            })
        }
    }

    fun listenToAllCommissionPayments(onUpdate: (List<com.albasel.tawsilaapp.data.model.DriverPaymentRecord>) -> Unit) {
        var latestUsersSnap: DataSnapshot? = null
        var latestTopPaymentsSnap: DataSnapshot? = null

        fun processAndEmit() {
            val usersSnap = latestUsersSnap ?: return
            val allRecords = mutableListOf<com.albasel.tawsilaapp.data.model.DriverPaymentRecord>()

            // 1. Read from users/{driverKey}/commission_payments
            for (uChild in usersSnap.children) {
                val dKey = uChild.key ?: continue
                val name = uChild.child("name").getValue(String::class.java)
                val phone = uChild.child("phone").getValue(String::class.java)
                val dName = name?.takeIf { it.isNotBlank() } ?: phone ?: "سائق ($dKey)"

                val userPaymentsSnap = uChild.child("commission_payments")
                for (paySnap in userPaymentsSnap.children) {
                    val pId = paySnap.key ?: ""
                    val amt = when (val v = paySnap.child("amount").value ?: paySnap.value) {
                        is Number -> v.toDouble()
                        is String -> v.toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }
                    val ts = when (val v = paySnap.child("timestamp").value) {
                        is Number -> v.toLong()
                        is String -> v.toLongOrNull() ?: System.currentTimeMillis()
                        else -> System.currentTimeMillis()
                    }
                    if (amt > 0 && pId != "amount" && pId != "timestamp") {
                        if (allRecords.none { it.id == pId }) {
                            allRecords.add(com.albasel.tawsilaapp.data.model.DriverPaymentRecord(pId, dKey, dName, amt, ts))
                        }
                    }
                }
            }

            // 2. Also read from top-level commission_payments
            val topSnap = latestTopPaymentsSnap
            if (topSnap != null) {
                val userNamesMap = mutableMapOf<String, String>()
                for (uChild in usersSnap.children) {
                    val key = uChild.key ?: continue
                    val name = uChild.child("name").getValue(String::class.java)
                    val phone = uChild.child("phone").getValue(String::class.java)
                    val displayName = name?.takeIf { it.isNotBlank() } ?: phone ?: "سائق ($key)"
                    userNamesMap[key] = displayName
                    if (!phone.isNullOrBlank()) userNamesMap[phone] = displayName
                }

                for (driverSnap in topSnap.children) {
                    val dKey = driverSnap.key ?: continue
                    val dName = userNamesMap[dKey] ?: "سائق ($dKey)"
                    for (paySnap in driverSnap.children) {
                        val pId = paySnap.key ?: ""
                        val amt = when (val v = paySnap.child("amount").value ?: paySnap.value) {
                            is Number -> v.toDouble()
                            is String -> v.toDoubleOrNull() ?: 0.0
                            else -> 0.0
                        }
                        val ts = when (val v = paySnap.child("timestamp").value) {
                            is Number -> v.toLong()
                            is String -> v.toLongOrNull() ?: System.currentTimeMillis()
                            else -> System.currentTimeMillis()
                        }
                        if (amt > 0 && pId != "amount" && pId != "timestamp") {
                            if (allRecords.none { it.id == pId }) {
                                allRecords.add(com.albasel.tawsilaapp.data.model.DriverPaymentRecord(pId, dKey, dName, amt, ts))
                            }
                        }
                    }
                }
            }

            onUpdate(allRecords.sortedByDescending { it.timestamp })
        }

        database.child("users").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                latestUsersSnap = snapshot
                processAndEmit()
            }
            override fun onCancelled(e: DatabaseError) {}
        })

        database.child("commission_payments").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                latestTopPaymentsSnap = snapshot
                processAndEmit()
            }
            override fun onCancelled(e: DatabaseError) {}
        })
    }

    suspend fun recordCommissionPayment(uid: String, phone: String = "", amt: Double) { 
        val keysToUpdate = setOf(uid, phone).filter { it.isNotBlank() }
        if (keysToUpdate.isEmpty()) return

        val ts = System.currentTimeMillis()
        val primaryKey = keysToUpdate.first()
        val pushRef = database.child("users").child(primaryKey).child("commission_payments").push()
        val pid = pushRef.key ?: UUID.randomUUID().toString()

        val payMap = mapOf<String, Any>(
            "id" to pid,
            "amount" to amt,
            "timestamp" to ts
        )

        keysToUpdate.forEach { k ->
            database.child("users").child(k).child("paidCommission").runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    val current = (currentData.value as? Number)?.toDouble() ?: 0.0
                    currentData.value = (current + amt).coerceAtLeast(0.0)
                    return Transaction.success(currentData)
                }
                override fun onComplete(e: DatabaseError?, committed: Boolean, s: DataSnapshot?) {}
            })

            // Write inside users/{k}/commission_payments AND top-level commission_payments/{k} for complete backwards compatibility
            database.child("users").child(k).child("commission_payments").child(pid).setValue(payMap)
            database.child("commission_payments").child(k).child(pid).setValue(payMap)
        }
    }

    suspend fun updateCommissionPayment(uid: String, phone: String = "", pid: String, oldAmt: Double, newAmt: Double) { 
        val keysToUpdate = setOf(uid, phone).filter { it.isNotBlank() }
        val diff = newAmt - oldAmt

        keysToUpdate.forEach { k ->
            database.child("users").child(k).child("paidCommission").runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    val current = (currentData.value as? Number)?.toDouble() ?: 0.0
                    currentData.value = (current + diff).coerceAtLeast(0.0)
                    return Transaction.success(currentData)
                }
                override fun onComplete(e: DatabaseError?, committed: Boolean, s: DataSnapshot?) {}
            })

            database.child("users").child(k).child("commission_payments").child(pid).child("amount").setValue(newAmt)
            database.child("commission_payments").child(k).child(pid).child("amount").setValue(newAmt)
        }
    }

    suspend fun deleteCommissionPayment(uid: String, phone: String = "", pid: String, amt: Double) { 
        val keysToUpdate = setOf(uid, phone).filter { it.isNotBlank() }

        keysToUpdate.forEach { k ->
            database.child("users").child(k).child("paidCommission").runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    val current = (currentData.value as? Number)?.toDouble() ?: 0.0
                    currentData.value = (current - amt).coerceAtLeast(0.0)
                    return Transaction.success(currentData)
                }
                override fun onComplete(e: DatabaseError?, committed: Boolean, s: DataSnapshot?) {}
            })

            database.child("users").child(k).child("commission_payments").child(pid).removeValue()
            database.child("commission_payments").child(k).child(pid).removeValue()
        }
    }
    suspend fun uploadToDrive(context: Context, uri: Uri, fileName: String): String? = withContext(Dispatchers.IO) { 
        try { 
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val bitmap = BitmapFactory.decodeStream(inputStream) ?: return@withContext null
            
            val maxDimension = 1280
            val scaledBitmap = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
                val ratio = Math.min(maxDimension.toFloat() / bitmap.width, maxDimension.toFloat() / bitmap.height)
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
            } else bitmap

            val bytes = ByteArrayOutputStream().use { baos ->
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 75, baos)
                baos.toByteArray()
            }
            
            var driveId = GoogleDriveHelper.uploadImage(bytes, fileName) 
            if (driveId.isNullOrBlank()) {
                Log.w("FirebaseManager", "Google Drive upload retry attempt...")
                kotlinx.coroutines.delay(1000)
                GoogleDriveHelper.invalidateAccessToken()
                driveId = GoogleDriveHelper.uploadImage(bytes, fileName)
            }
            
            driveId
        } catch (e: Throwable) { 
            Log.e("FirebaseManager", "Error uploading image to Google Drive: ${e.message}", e)
            null 
        } 
    }
}
