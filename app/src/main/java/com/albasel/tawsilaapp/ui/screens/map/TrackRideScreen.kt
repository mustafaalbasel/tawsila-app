package com.albasel.tawsilaapp.ui.screens.map

import androidx.compose.foundation.BorderStroke
import android.util.Log
import android.content.Intent
import android.net.Uri
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.location.Geocoder
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.scale
import androidx.core.content.ContextCompat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.albasel.tawsilaapp.data.firebase.FirebaseManager
import com.albasel.tawsilaapp.data.model.CityDestination
import com.albasel.tawsilaapp.data.model.Order
import com.albasel.tawsilaapp.data.model.User
import com.albasel.tawsilaapp.data.model.RouteStep
import com.albasel.tawsilaapp.data.repository.impl.RoutingRepositoryImpl
import com.albasel.tawsilaapp.util.VehicleUtils.getBaseIconName
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.ui.text.style.TextOverflow
import com.albasel.tawsilaapp.ui.components.MapLibreCompose
import com.albasel.tawsilaapp.util.TTSHelper
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolylineOptions
import com.albasel.tawsilaapp.utils.MapMarkerUtils
import com.albasel.tawsilaapp.data.model.SystemLogicSettings
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import org.maplibre.android.camera.CameraUpdateFactory
import com.albasel.tawsilaapp.util.AppImageView
import com.albasel.tawsilaapp.util.MapUtils
import java.util.*
import kotlin.math.*

fun cleanVoiceLocationName(rawName: String): String {
    if (rawName.isBlank()) return ""
    
    // Filter out GIS codes, PlusCodes, and strange alphanumeric codes like Cis93, N98, 6V2H+89, etc.
    val isGisCode = rawName.matches(Regex("(?i)^[a-z]{1,5}\\d+$")) || 
                    rawName.matches(Regex("(?i)^[a-z0-9]{4,8}\\+[a-z0-9]{2,}$")) ||
                    rawName.matches(Regex("^\\d+$"))
                    
    if (isGisCode) return ""
    
    val cleaned = rawName.replace("مصر", "")
        .replace("محافظة دمياط", "")
        .replace("Un-named Road", "")
        .replace("طريق غير مسمى", "")
        .trim(',', ' ', '-')
        
    return if (cleaned.length >= 2 && !cleaned.matches(Regex("(?i)^[a-z]{1,5}\\d+$"))) cleaned else ""
}

suspend fun resolveCleanStreetOrPoiName(
    context: Context,
    pos: LatLng,
    cityDestinations: List<CityDestination>,
    isAr: Boolean
): String {
    // 1. Check if close to a famous POI / Landmark (within 70 meters)
    var closestPoi: CityDestination? = null
    var minDistanceMeters = 70.0
    
    for (poi in cityDestinations) {
        val dist = FloatArray(1)
        if (poi.startLat != 0.0 && poi.startLng != 0.0) {
            android.location.Location.distanceBetween(
                pos.latitude, pos.longitude,
                poi.startLat, poi.startLng,
                dist
            )
            if (dist[0] < minDistanceMeters && poi.name.isNotBlank()) {
                minDistanceMeters = dist[0].toDouble()
                closestPoi = poi
            }
        }
    }
    
    if (closestPoi != null) {
        return if (isAr) "بجوار ${closestPoi.name}" else "near ${closestPoi.name}"
    }

    // 2. Geocoder fallback with cleaning
    return withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale("ar"))
            val addrs = geocoder.getFromLocation(pos.latitude, pos.longitude, 3)
            if (!addrs.isNullOrEmpty()) {
                for (addr in addrs) {
                    val thoroughfare = addr.thoroughfare ?: ""
                    val cleanT = cleanVoiceLocationName(thoroughfare)
                    if (cleanT.isNotBlank()) return@withContext if (cleanT.contains("شارع") || cleanT.contains("دار") || cleanT.contains("حي")) cleanT else "شارع $cleanT"
                    
                    val addressLine = addr.getAddressLine(0) ?: ""
                    val parts = addressLine.split(",")
                    for (part in parts) {
                        val cleanP = cleanVoiceLocationName(part.trim())
                        if (cleanP.isNotBlank()) return@withContext cleanP
                    }
                }
            }
            ""
        } catch (e: Exception) { "" }
    }
}

@Composable
fun TrackRideScreen(
    firebaseManager: FirebaseManager, 
    destLat: Double, 
    destLng: Double, 
    destName: String,
    language: String,
    user: User?,
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
    onToggleDrawerGestures: (Boolean) -> Unit = {},
    orderId: String? = null,
    onChatClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val isAr = language == "AR"
    val scope = rememberCoroutineScope()
    val routingRepo = remember { RoutingRepositoryImpl() }
    
    val currentUserId = user?.id ?: firebaseManager.getCurrentUserId()
    
    var myPos by remember { mutableStateOf(LatLng(30.0444, 31.2357)) } 
    var targetPos by remember { mutableStateOf(LatLng(30.0444, 31.2357)) } 
    
    var destination by remember { mutableStateOf(LatLng(if(destLat != 0.0) destLat else 30.0444, if(destLng != 0.0) destLng else 31.2357)) }
    var destinationName by remember { mutableStateOf(if(destName.isNotEmpty() && destName != "All") destName else (if(isAr) "القاهرة" else "Cairo")) }
    
    var pickupPos by remember { mutableStateOf<LatLng?>(null) }
    var pickupName by remember { mutableStateOf("") }
    
    var selectedOrder by remember { mutableStateOf<Order?>(null) }
    var confirmedOrders by remember { mutableStateOf(emptyList<Order>()) }
    var ordersExpanded by remember { mutableStateOf(false) }
    
    var distanceRemaining by remember { mutableDoubleStateOf(0.0) }
    var timeRemainingMinutes by remember { mutableIntStateOf(0) }
    
    var systemLogic by remember { mutableStateOf<SystemLogicSettings>(SystemLogicSettings()) }
    LaunchedEffect(Unit) { systemLogic = firebaseManager.getSystemLogicSettings() }
    
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var routePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var routeSteps by remember { mutableStateOf<List<RouteStep>>(emptyList()) }
    var currentInstruction by remember { mutableStateOf("") }
    var nextInstruction by remember { mutableStateOf("") }
    var lastAnnouncedStepIndex by remember { mutableIntStateOf(-1) }
    
    var mapLibreInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var isCameraUserControlled by remember { mutableStateOf(false) }
    var peerPos by remember { mutableStateOf<LatLng?>(null) }
    var peerRoutePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var currentSpeedKmh by remember { mutableDoubleStateOf(0.0) }
    var trafficConfirmations by remember { mutableIntStateOf(0) }
    var driverBearing by remember { mutableFloatStateOf(0f) }
    var lastDriverPos by remember { mutableStateOf<LatLng?>(null) }

    // Device Rotation Sensor (Rotation Vector Sensor for device orientation)
    var deviceAzimuthDegrees by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        
        var sensorListener: SensorEventListener? = null
        if (sensorManager != null && rotationSensor != null) {
            val rotationMatrix = FloatArray(9)
            val orientationValues = FloatArray(3)
            
            sensorListener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    if (event != null && event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        SensorManager.getOrientation(rotationMatrix, orientationValues)
                        val rawAzimuth = Math.toDegrees(orientationValues[0].toDouble()).toFloat()
                        val azimuth = (rawAzimuth + 360f) % 360f
                        
                        // Low-pass filter smoothing for sensor jitter
                        val diff = (azimuth - deviceAzimuthDegrees + 540f) % 360f - 180f
                        deviceAzimuthDegrees = (deviceAzimuthDegrees + diff * 0.12f + 360f) % 360f
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sensorManager.registerListener(sensorListener, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
        }
        
        onDispose {
            if (sensorManager != null && sensorListener != null) {
                sensorManager.unregisterListener(sensorListener)
            }
        }
    }



    var driverMarker by remember { mutableStateOf<org.maplibre.android.annotations.Marker?>(null) }
    var customerMarker by remember { mutableStateOf<org.maplibre.android.annotations.Marker?>(null) }
    var destMarker by remember { mutableStateOf<org.maplibre.android.annotations.Marker?>(null) }
    var mainPolylines by remember { mutableStateOf<List<org.maplibre.android.annotations.Polyline>>(emptyList()) }
    var peerPolylines by remember { mutableStateOf<List<org.maplibre.android.annotations.Polyline>>(emptyList()) }
    
    var isSatelliteMode by remember { mutableStateOf(false) }
    val styleUrl = if (isSatelliteMode) 
        com.albasel.tawsilaapp.data.map.config.MapTileProviderConfig.satelliteTileUrl
    else if (isDarkMode) 
        com.albasel.tawsilaapp.data.map.config.MapTileProviderConfig.nightTileUrl
    else 
        com.albasel.tawsilaapp.data.map.config.MapTileProviderConfig.dayTileUrl

    var showDriverInfoDialog by remember { mutableStateOf(false) }
    var selectedParticipantOrder by remember { mutableStateOf<Order?>(null) }
    var selectedCustomerOrderForDialog by remember { mutableStateOf<Order?>(null) }
    var customerMarkersMap by remember { mutableStateOf<Map<String, org.maplibre.android.annotations.Marker>>(emptyMap()) }
    var customerPolylinesMap by remember { mutableStateOf<Map<String, org.maplibre.android.annotations.Polyline>>(emptyMap()) }
    var markerToOrderMap by remember { mutableStateOf<Map<Long, Order>>(emptyMap()) }

    val customerPalette = remember {
        listOf(
            android.graphics.Color.parseColor("#2E7D32"), // Green
            android.graphics.Color.parseColor("#E53935"), // Red
            android.graphics.Color.parseColor("#1A73E8"), // Blue
            android.graphics.Color.parseColor("#FB8C00"), // Orange
            android.graphics.Color.parseColor("#8E24AA"), // Purple
            android.graphics.Color.parseColor("#00ACC1")  // Cyan
        )
    }
    
    var isNavigating by remember { mutableStateOf(false) }
    val hasActiveTrip = selectedOrder != null || confirmedOrders.isNotEmpty()
    var isReportingLocation by remember(hasActiveTrip) { mutableStateOf(hasActiveTrip) }
    var lastStreetName by remember { mutableStateOf("") }

    var showRouteChangeDialog by remember { mutableStateOf(false) }
    var unreadMessagesCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(selectedOrder?.id, currentUserId) {
        val oId = selectedOrder?.id
        if (!oId.isNullOrEmpty() && currentUserId != null) {
            val ref = firebaseManager.database.child("orders").child(oId).child("messages")
            val listener = object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    val msgs = s.children.mapNotNull { it.getValue(com.albasel.tawsilaapp.data.model.Message::class.java) }
                    unreadMessagesCount = msgs.count { it.senderId != currentUserId && !it.isRead }
                }
                override fun onCancelled(e: DatabaseError) {}
            }
            ref.addValueEventListener(listener)
        } else {
            unreadMessagesCount = 0
        }
    }
    var newDestNameInput by remember { mutableStateOf("") }
    
    val isDriver = remember(selectedOrder, currentUserId) { selectedOrder?.driverId == currentUserId }

    val smoothDriverLat = remember { Animatable(0f) }
    val smoothDriverLng = remember { Animatable(0f) }
    val smoothDriverBearing = remember { Animatable(0f) }

    val driverPos = if (isDriver) myPos else peerPos
    LaunchedEffect(driverPos) {
        driverPos?.let {
            launch { smoothDriverLat.animateTo(it.latitude.toFloat(), tween(1200, easing = LinearEasing)) }
            launch { smoothDriverLng.animateTo(it.longitude.toFloat(), tween(1200, easing = LinearEasing)) }
        }
    }

    LaunchedEffect(driverBearing, deviceAzimuthDegrees) {
        val targetAngle = if (isDriver) deviceAzimuthDegrees else driverBearing
        var diff = (targetAngle - smoothDriverBearing.value) % 360
        if (diff > 180) diff -= 360
        if (diff < -180) diff += 360
        smoothDriverBearing.animateTo(smoothDriverBearing.value + diff, tween(300, easing = LinearEasing))
    }

    LaunchedEffect(currentUserId) {
        TTSHelper.init(context)
    }

    DisposableEffect(currentUserId) {
        if (currentUserId != null) {
            val ordersRef = firebaseManager.database.child("orders")
            
            // Internal helper to merge order updates
            fun updateOrders(newOrders: List<Order>, isCustomer: Boolean) {
                scope.launch {
                    val otherTypeOrders = if (isCustomer) confirmedOrders.filter { it.driverId == currentUserId && it.userId != currentUserId }
                                          else confirmedOrders.filter { it.userId == currentUserId && it.driverId != currentUserId }
                    
                    val merged = (newOrders + otherTypeOrders).distinctBy { it.id }.sortedByDescending { it.timestamp }
                    confirmedOrders = merged
                    
                    // Sync selectedOrder (Auto-select active order if present)
                    if (selectedOrder != null) {
                        val updated = merged.find { it.id == selectedOrder!!.id }
                        selectedOrder = updated ?: merged.firstOrNull()
                    } else if (!orderId.isNullOrEmpty() && orderId != "none") {
                        selectedOrder = merged.find { it.id == orderId } ?: merged.firstOrNull()
                    } else {
                        selectedOrder = merged.firstOrNull()
                    }
                }
            }

            // Optimized approach: One listener for User as Customer, and one for User as Driver (if applicable)
            val isDriverUser = user?.role?.uppercase() == "DRIVER" || user?.role?.uppercase() == "ADMIN"
            
            val customerListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val list = snapshot.children.mapNotNull { 
                        try { it.getValue(Order::class.java) } catch(e: Exception) { null }
                    }.filter { o -> 
                        o.status == "ACCEPTED" || o.status == "CONFIRMED" || o.status == "ARRIVED" || o.status == "STARTED"
                    }
                    updateOrders(list, isCustomer = true)
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            
            val driverListener = if (isDriverUser) object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val list = snapshot.children.mapNotNull { 
                        try { it.getValue(Order::class.java) } catch(e: Exception) { null }
                    }.filter { o -> 
                        o.status == "ACCEPTED" || o.status == "CONFIRMED" || o.status == "ARRIVED" || o.status == "STARTED"
                    }
                    updateOrders(list, isCustomer = false)
                }
                override fun onCancelled(error: DatabaseError) {}
            } else null

            val customerQuery = ordersRef.orderByChild("userId").equalTo(currentUserId)
            customerQuery.addValueEventListener(customerListener)
            
            val driverQuery = if (isDriverUser) ordersRef.orderByChild("driverId").equalTo(currentUserId) else null
            driverQuery?.addValueEventListener(driverListener!!)
            
            onDispose {
                customerQuery.removeEventListener(customerListener)
                driverQuery?.let { if (driverListener != null) it.removeEventListener(driverListener) }
            }
        } else {
            onDispose {}
        }
    }

    // New Message Announcement for Driver
    DisposableEffect(selectedOrder?.id) {
        val oId = selectedOrder?.id
        var listener: ValueEventListener? = null
        if (oId != null && isDriver && currentUserId != null) {
            listener = object : ValueEventListener {
                var lastCount = -1
                override fun onDataChange(snapshot: DataSnapshot) {
                    val currentCount = snapshot.childrenCount.toInt()
                    if (lastCount != -1 && currentCount > lastCount) {
                        val lastMsg = snapshot.children.lastOrNull()?.getValue(com.albasel.tawsilaapp.data.model.Message::class.java)
                        if (lastMsg != null && lastMsg.senderId != currentUserId) {
                            val msg = if(isAr) "هناك رسالة جديدة من عميل في رحلتك" else "New message from customer in your trip"
                            TTSHelper.speak(context, msg)
                        }
                    }
                    lastCount = currentCount
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            firebaseManager.database.child("orders").child(oId).child("messages").addValueEventListener(listener)
        }
        onDispose {
            if (oId != null && listener != null) {
                firebaseManager.database.child("orders").child(oId).child("messages").removeEventListener(listener)
            }
        }
    }

    // Rule 1: Centralized LocationCallback for both Driver and Customer during Active Trip
    val locationCallback = remember {
        object : com.google.android.gms.location.LocationCallback() {
            override fun onLocationResult(lr: com.google.android.gms.location.LocationResult) {
                lr.lastLocation?.let { loc ->
                    myPos = LatLng(loc.latitude, loc.longitude)
                    currentSpeedKmh = loc.speed * 3.6.toDouble()
                    // Location update is handled by LocationForegroundService when in this screen
                }
            }
        }
    }

    DisposableEffect(isDriver) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val priority = if (isDriver) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
            val interval = if (isDriver) 3000L else 20000L
            
            val request = com.google.android.gms.location.LocationRequest.Builder(priority, interval).build()
            fusedLocationClient.requestLocationUpdates(request, locationCallback, android.os.Looper.getMainLooper())
        }
        onDispose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    LaunchedEffect(selectedOrder, confirmedOrders) {
        val activeOrder = selectedOrder ?: confirmedOrders.firstOrNull()
        if (activeOrder != null) {
            destination = LatLng(activeOrder.destinationLat, activeOrder.destinationLng)
            destinationName = activeOrder.destinationName
            pickupPos = LatLng(activeOrder.pickupLat, activeOrder.pickupLng)
            pickupName = activeOrder.pickupName
            
            if (currentUserId != null) {
                if (isDriver) {
                    targetPos = destination
                } else {
                    val otherId = if (activeOrder.userId == currentUserId) activeOrder.driverId else activeOrder.userId
                    otherId?.let { tId ->
                        firebaseManager.database.child("users").child(tId).child("latitude").get().await().getValue(Double::class.java)?.let { lat ->
                            firebaseManager.database.child("users").child(tId).child("longitude").get().await().getValue(Double::class.java)?.let { lng ->
                                targetPos = LatLng(lat, lng)
                            }
                        }
                    }
                }
            }
        } else {
            destinationName = if (isAr) "غير محدد بعد" else "Not specified yet"
            pickupName = ""
            destination = LatLng(30.0444, 31.2357)
            pickupPos = null
            routePoints = emptyList()
            peerRoutePoints = emptyList()
        }
    }

    DisposableEffect(selectedOrder) {
        var listener: ValueEventListener? = null
        val otherId = if (selectedOrder?.userId == currentUserId) selectedOrder?.driverId else selectedOrder?.userId
        if (otherId != null) {
            listener = firebaseManager.listenToUserLocation(otherId) { lat, lng ->
                val newPos = LatLng(lat, lng)
                
                // Calculate bearing for driver if this peer is the driver
                val isPeerDriver = otherId == selectedOrder?.driverId
                if (isPeerDriver && lastDriverPos != null) {
                    val dist = com.albasel.tawsilaapp.util.MapUtils.calculateDistance(lastDriverPos!!.latitude, lastDriverPos!!.longitude, newPos.latitude, newPos.longitude)
                    if (dist > 0.002) {
                        driverBearing = com.albasel.tawsilaapp.util.MapUtils.calculateBearing(lastDriverPos!!.latitude, lastDriverPos!!.longitude, newPos.latitude, newPos.longitude).toFloat()
                    }
                }
                
                peerPos = newPos
                if (isPeerDriver) lastDriverPos = newPos
            }
        }
        onDispose {
            otherId?.let { tId -> listener?.let { firebaseManager.database.child("users").child(tId).removeEventListener(it) } }
        }
    }

    // Also track my bearing if I am the driver
    LaunchedEffect(myPos) {
        if (isDriver && lastDriverPos != null) {
            val dist = com.albasel.tawsilaapp.util.MapUtils.calculateDistance(lastDriverPos!!.latitude, lastDriverPos!!.longitude, myPos.latitude, myPos.longitude)
            if (dist > 0.002) {
                driverBearing = com.albasel.tawsilaapp.util.MapUtils.calculateBearing(lastDriverPos!!.latitude, lastDriverPos!!.longitude, myPos.latitude, myPos.longitude).toFloat()
            }
        }
        if (isDriver) lastDriverPos = myPos
    }

    LaunchedEffect(myPos, targetPos, routeSteps, isNavigating) {
        if (isNavigating && routeSteps.isNotEmpty()) {
            val nearestStepIndex = routeSteps.indices.minByOrNull { i ->
                val step = routeSteps[i]
                MapUtils.calculateDistance(myPos.latitude, myPos.longitude, step.lat, step.lng)
            } ?: -1

            if (nearestStepIndex != -1) {
                val upcomingStep = routeSteps.getOrNull(nearestStepIndex + 1) ?: routeSteps[nearestStepIndex]
                currentInstruction = upcomingStep.instruction
                nextInstruction = routeSteps.getOrNull(nearestStepIndex + 2)?.instruction ?: ""

                val distToStep = MapUtils.calculateDistance(myPos.latitude, myPos.longitude, upcomingStep.lat, upcomingStep.lng)
                
                if (distToStep < 0.1 && nearestStepIndex + 1 > lastAnnouncedStepIndex) {
                    TTSHelper.speak(context, upcomingStep.instruction)
                    lastAnnouncedStepIndex = nearestStepIndex + 1
                }
            }
        }
    }

    LaunchedEffect(myPos, peerPos, isNavigating) {
        if (isNavigating && peerPos != null) {
            scope.launch {
                try {
                    val routes = routingRepo.getRoute(myPos.latitude, myPos.longitude, peerPos!!.latitude, peerPos!!.longitude, if(isAr) "ar" else "en")
                    if (routes.isNotEmpty()) {
                        peerRoutePoints = routes[0].points
                    }
                } catch(e: Exception) {}
            }
        } else {
            peerRoutePoints = emptyList()
        }
    }

    var cityDestinations by remember { mutableStateOf<List<CityDestination>>(emptyList()) }
    LaunchedEffect(Unit) {
        cityDestinations = firebaseManager.getCityDestinations()
    }

    LaunchedEffect(myPos, peerPos, isReportingLocation) {
        if (isReportingLocation) {
            val reportPos = if (isDriver) myPos else (peerPos ?: myPos)
            val street = resolveCleanStreetOrPoiName(context, reportPos, cityDestinations, isAr)
            if (street.isNotEmpty() && street != lastStreetName) {
                val prefix = if (isDriver) (if(isAr) "أنت الآن " else "You are now ")
                             else (if(isAr) "السائق الآن " else "The driver is now ")
                val message = if (street.startsWith("في") || street.startsWith("بجوار") || street.startsWith("شارع")) "$prefix$street" else "${prefix}في $street"
                TTSHelper.speak(context, message)
                lastStreetName = street
            }
        }
    }

    LaunchedEffect(myPos, targetPos, destination, isDriver, destName, pickupName, peerPos) {
        scope.launch {
            try {
                // If destName is the pickupName or contains \"موقع\", we are in \"Track Person\" mode
                val isPeerTracking = destName == pickupName || destName.contains("موقع")
                
                val p1: LatLng
                val p2: LatLng
                
                if (isPeerTracking) {
                    p1 = myPos
                    p2 = targetPos
                } else {
                    p1 = if (isDriver) myPos else (peerPos ?: myPos)
                    p2 = destination
                }

                val routes = routingRepo.getRoute(p1.latitude, p1.longitude, p2.latitude, p2.longitude, if(isAr) "ar" else "en")
                if (routes.isNotEmpty()) {
                    val r = routes[0]
                    distanceRemaining = r.distance
                    routePoints = r.points
                    routeSteps = r.steps
                    timeRemainingMinutes = (distanceRemaining * 1.5).toInt()
                }
            } catch(e: Exception) {}
        }
    }

    var lastAutoZoomedOrderId by remember { mutableStateOf<String?>(null) }

    // Proximity Verification Counters
    var driverArrivalConfirmations by remember { mutableIntStateOf(0) }
    var customerArrivalConfirmations by remember { mutableIntStateOf(0) }

    // Driver Proximity Monitoring (Pickup and Destination)
    LaunchedEffect(myPos, peerPos, selectedOrder?.id) {
        val order = selectedOrder ?: return@LaunchedEffect
        val driverCurrentPos = if (isDriver) myPos else peerPos
        if (driverCurrentPos == null) return@LaunchedEffect

        // 1. Pickup Arrival (Triggered when driver within 200m)
        if (order.driverArrivalTime == 0L) {
            val distToPickup = MapUtils.calculateDistance(driverCurrentPos.latitude, driverCurrentPos.longitude, order.pickupLat, order.pickupLng) * 1000.0
            if (distToPickup <= 200.0) {
                driverArrivalConfirmations++
                if (driverArrivalConfirmations >= systemLogic.gpsConfirmationsNeeded) {
                    firebaseManager.updateOrderEvent(order.id, "driverArrivalTime")
                    // Rule 4.A.3: Automatic Loading Start
                    if (order.isCargo && order.needsLoading && order.loadingStartTime == 0L) {
                        firebaseManager.updateOrderEvent(order.id, "loadingStartTime")
                    }
                    driverArrivalConfirmations = 0
                }
            } else {
                driverArrivalConfirmations = 0
            }
        }

        // 1.5 Cargo Protection: Auto Done Loading if driver moves away
        if (order.isCargo && order.needsLoading && order.loadingStartTime > 0L && order.loadingEndTime == 0L) {
            val distToPickup = MapUtils.calculateDistance(driverCurrentPos.latitude, driverCurrentPos.longitude, order.pickupLat, order.pickupLng) * 1000.0
            if (distToPickup >= 250.0) {
                firebaseManager.updateOrderEvent(order.id, "loadingEndTime")
            }
        }

        // 2. Destination Arrival (Triggered when driver within 200m)
        if (order.destinationArrivalTime == 0L && (order.status == "STARTED" || order.status == "CONFIRMED")) {
            val distToDest = MapUtils.calculateDistance(driverCurrentPos.latitude, driverCurrentPos.longitude, order.destinationLat, order.destinationLng) * 1000.0
            if (distToDest <= 200.0) {
                firebaseManager.updateOrderEvent(order.id, "destinationArrivalTime")
                // Rule 11: Register tripEndTime
                firebaseManager.updateOrderEvent(order.id, "tripEndTime")
                
                // Rule 4.B.3: Automatic Unloading Start
                if (order.isCargo && order.unloadingStartTime == 0L) {
                    firebaseManager.updateOrderEvent(order.id, "unloadingStartTime")
                }
            }
        }
        
        // 3. Trip Start Detection (Automatic)
        if (order.tripStartTime == 0L && order.driverArrivalTime > 0L) {
            val distToPickup = MapUtils.calculateDistance(driverCurrentPos.latitude, driverCurrentPos.longitude, order.pickupLat, order.pickupLng) * 1000.0
            // If driver moved > 500m away from pickup after arrival, trip is considered STARTED
            if (distToPickup > 500.0) {
                firebaseManager.updateOrderEvent(order.id, "tripStartTime")
            }
        }

        // 4. Route Deviation Detection
        if (order.tripStartTime > 0L && order.tripEndTime == 0L && routePoints.isNotEmpty() && !order.routeDeviationDetected) {
            // Find closest point on route
            val minDistance = routePoints.minOf { p ->
                MapUtils.calculateDistance(driverCurrentPos.latitude, driverCurrentPos.longitude, p.latitude, p.longitude) * 1000.0
            }
            // If > 800m away from polyline, record deviation
            if (minDistance > 800.0) {
                scope.launch {
                    firebaseManager.database.child("orders").child(order.id).updateChildren(mapOf(
                        "routeDeviationDetected" to true,
                        "routeDeviationDistance" to minDistance,
                        "routeDeviationDetectedAt" to System.currentTimeMillis()
                    ))
                }
            }
        }
    }

    // Traffic Monitoring Loop (Driver Only)
    LaunchedEffect(currentUserId, selectedOrder?.id, isDriver) {
        if (!isDriver || selectedOrder == null) return@LaunchedEffect
        
        while(true) {
            val order = selectedOrder ?: break
            val limit = if(order.trafficSpeedLimitKmh > 0) order.trafficSpeedLimitKmh else 40.0
            
            // Only count if trip has started and not ended, and not in special waiting zones
            val isTripActive = order.tripStartTime > 0 && order.tripEndTime == 0L
            val isSlow = currentSpeedKmh < limit
            
            // Avoid counting during customer arrival, loading, unloading (already have their own fees)
            val isInSpecialZone = (order.driverArrivalTime > 0 && order.customerArrivalTime == 0L) ||
                                  (order.loadingStartTime > 0 && order.loadingEndTime == 0L) ||
                                  (order.unloadingStartTime > 0 && order.unloadingEndTime == 0L)

            if (isTripActive && isSlow && !isInSpecialZone) {
                trafficConfirmations++
                if (trafficConfirmations >= 3) { // 3 consecutive seconds of slow speed to confirm traffic
                    val currentTraffic = order.trafficDurationSeconds
                    firebaseManager.database.child("orders").child(order.id).child("trafficDurationSeconds").setValue(currentTraffic + 1).await()
                    
                    // Calculate and update fee based on free minutes
                    val totalMins = (currentTraffic + 1) / 60
                    val freeMins = 15 // Use system logic or hardcode
                    val pricePerMin = 5.0 // Use system logic
                    
                    val extraMins = (totalMins - freeMins).toInt().coerceAtLeast(0)
                    val extraFee = extraMins * pricePerMin
                    
                    if (extraFee != order.trafficExtraFee) {
                        firebaseManager.database.child("orders").child(order.id).updateChildren(mapOf(
                            "trafficExtraMinutes" to extraMins,
                            "trafficExtraFee" to extraFee
                        )).await()
                    }
                }
            } else {
                trafficConfirmations = 0
            }
            
            delay(1000)
        }
    }

    // Customer Proximity Monitoring (Triggered when customer within 200m)
    LaunchedEffect(myPos, peerPos, selectedOrder?.id) {
        val order = selectedOrder ?: return@LaunchedEffect
        if (order.driverArrivalTime == 0L || order.customerArrivalTime > 0L) return@LaunchedEffect 

        val customerCurrentPos = if (isDriver) peerPos else myPos
        if (customerCurrentPos == null) return@LaunchedEffect

        val distToPickup = MapUtils.calculateDistance(customerCurrentPos.latitude, customerCurrentPos.longitude, order.pickupLat, order.pickupLng) * 1000.0
        
        if (distToPickup <= 200.0) {
            customerArrivalConfirmations++
            if (customerArrivalConfirmations >= systemLogic.gpsConfirmationsNeeded) {
                firebaseManager.updateOrderEvent(order.id, "customerArrivalTime", System.currentTimeMillis())
                customerArrivalConfirmations = 0
            }
        } else {
            customerArrivalConfirmations = 0
        }
    }

    // Cargo Reminders Logic (Driver Side sends to Customer)
    LaunchedEffect(selectedOrder, systemLogic) {
        if (!isDriver) return@LaunchedEffect
        while(true) {
            val order = selectedOrder
            if (order != null && order.isCargo) {
                val now = System.currentTimeMillis()
                
                // 1. Loading Reminder
                if (order.driverArrivalTime > 0 && order.loadingStartTime == 0L && order.loadingEndTime == 0L) {
                    val elapsedMins = (now - order.driverArrivalTime) / 60000
                    if (elapsedMins >= systemLogic.freeLoadingTime) {
                        val lastRem = order.lastLoadingReminderTime
                        if (now - lastRem >= 120000) { // 2 mins
                            firebaseManager.sendFcmNotification(order.userId, "WASALNA", "تذكير: يرجى تأكيد بدء التحميل (انتهى الوقت المجاني)", "track_ride/none/none/none?orderId=${order.id}", "LOADING_REMINDER")
                            firebaseManager.updateOrderEvent(order.id, "lastLoadingReminderTime")
                        }
                    }
                }
                
                // 2. Unloading Reminder
                if (order.destinationArrivalTime > 0 && order.unloadingStartTime == 0L && order.unloadingEndTime == 0L) {
                    val elapsedMins = (now - order.destinationArrivalTime) / 60000
                    if (elapsedMins >= systemLogic.freeUnloadingTime) {
                        val lastRem = order.lastUnloadingReminderTime
                        if (now - lastRem >= 120000) { // 2 mins
                            firebaseManager.sendFcmNotification(order.userId, "WASALNA", "تذكير: يرجى تأكيد بدء التفريغ (انتهى الوقت المجاني)", "track_ride/none/none/none?orderId=${order.id}", "UNLOADING_REMINDER")
                            firebaseManager.updateOrderEvent(order.id, "lastUnloadingReminderTime")
                        }
                    }
                }
            }
            delay(30000) // Check every 30 seconds
        }
    }

    LaunchedEffect(targetPos, destination, pickupPos, routePoints, isDarkMode, myPos, destName, pickupName, peerPos, driverBearing, peerRoutePoints, mapLibreInstance) {
        val map = mapLibreInstance ?: return@LaunchedEffect
        
        val isPeerTracking = destName == pickupName || destName.contains("موقع")

        // Icons (Clean green circular dot for customer, car icon for driver, red flag for destination)
        val carIcon = MapMarkerUtils.createNavigationArrowIcon(context, smoothDriverBearing.value, isDarkMode)
        val customerIcon = MapMarkerUtils.createColoredUserLocationDotIcon(context, android.graphics.Color.parseColor("#2E7D32"))
        val endIcon = MapMarkerUtils.createFlagIcon(context, android.graphics.Color.parseColor("#F44336"))

        // Determine who is who
        val smoothDriverPos = LatLng(smoothDriverLat.value.toDouble(), smoothDriverLng.value.toDouble())
        val customerPos = if (isDriver) peerPos else myPos

        // 1. Driver Marker (Car - Persistent)
        if (smoothDriverLat.value != 0f) {
            val pos = smoothDriverPos
            val currentDMarker = driverMarker
            if (currentDMarker == null) {
                driverMarker = map.addMarker(MarkerOptions().position(pos).title(if(isAr) "السائق" else "Driver").icon(carIcon))
            } else {
                currentDMarker.position = pos
                currentDMarker.icon = carIcon
            }
        }

        val hasActiveTrip = selectedOrder != null || confirmedOrders.isNotEmpty()

        // 2. Customer Marker (Hide pin if driver is at the exact same location < 25m)
        val activeCustomerPos = customerPos ?: if (!isDriver && myPos != null) myPos else null
        if (hasActiveTrip && activeCustomerPos != null) {
            val currentCMarker = customerMarker
            if (currentCMarker == null) {
                customerMarker = map.addMarker(MarkerOptions().position(activeCustomerPos).title(if(isAr) "العميل" else "Customer").icon(customerIcon))
            } else {
                currentCMarker.position = activeCustomerPos
            }
        }
        
        // 3. Destination Marker (End Path Flag - Persistent)
        val activeDest = destination ?: targetPos
        if (hasActiveTrip && activeDest != null) {
            val currentDestMarker = destMarker
            if (currentDestMarker == null) {
                destMarker = map.addMarker(MarkerOptions().position(activeDest).title(destinationName).icon(endIcon))
            } else {
                currentDestMarker.position = activeDest
            }
        } else {
            destMarker?.let { if (map.markers.contains(it)) map.removeMarker(it); destMarker = null }
        }
        
        // Polylines Logic (Main Route)
        if (hasActiveTrip && routePoints.isNotEmpty()) {
            val outerColor = if (isDarkMode) "#00E676" else "#7CC8FF"
            val mainColor = if (isDarkMode) "#2E7D32" else "#5AAEFF"
            
            // Only redraw if path changed
            val pointsHash = routePoints.hashCode()
            if (mainPolylines.isEmpty() || mainPolylines[0].points.hashCode() != pointsHash) {
                mainPolylines.forEach { if(map.polylines.contains(it)) map.removePolyline(it) }
                val newPolys = mutableListOf<org.maplibre.android.annotations.Polyline>()
                newPolys.add(map.addPolyline(PolylineOptions().addAll(routePoints).color(android.graphics.Color.parseColor(outerColor)).width(14f))) 
                newPolys.add(map.addPolyline(PolylineOptions().addAll(routePoints).color(android.graphics.Color.parseColor(mainColor)).width(8f)))
                newPolys.add(map.addPolyline(PolylineOptions().addAll(routePoints).color(android.graphics.Color.WHITE).width(3f)))
                mainPolylines = newPolys
            }
        } else {
            mainPolylines.forEach { if(map.polylines.contains(it)) map.removePolyline(it) }
            mainPolylines = emptyList()
        }

        // 4. Distance Green Line (Driver to Customer)
        if (hasActiveTrip && peerRoutePoints.isNotEmpty() && isNavigating) {
            val peerPointsHash = peerRoutePoints.hashCode()
            if (peerPolylines.isEmpty() || peerPolylines[0].points.hashCode() != peerPointsHash) {
                peerPolylines.forEach { if(map.polylines.contains(it)) map.removePolyline(it) }
                val newPeerPolys = mutableListOf<org.maplibre.android.annotations.Polyline>()
                newPeerPolys.add(map.addPolyline(PolylineOptions().addAll(peerRoutePoints).color(android.graphics.Color.GREEN).width(12f)))
                newPeerPolys.add(map.addPolyline(PolylineOptions().addAll(peerRoutePoints).color(android.graphics.Color.WHITE).width(3f)))
                peerPolylines = newPeerPolys
            }
        } else {
            peerPolylines.forEach { if(map.polylines.contains(it)) map.removePolyline(it) }
            peerPolylines = emptyList()
        }

        if (!isCameraUserControlled && isReportingLocation && driverPos != null) {
            val targetPosForCamera = if (selectedParticipantOrder != null) {
                val poiMatch = com.albasel.tawsilaapp.util.DamiettaPlaces.getAllPois().firstOrNull { it.nameAr.contains(selectedParticipantOrder!!.pickupName) || selectedParticipantOrder!!.pickupName.contains(it.nameAr) }
                val pLat = if (selectedParticipantOrder!!.pickupLat != 0.0) selectedParticipantOrder!!.pickupLat else (poiMatch?.lat ?: 31.4522)
                val pLng = if (selectedParticipantOrder!!.pickupLng != 0.0) selectedParticipantOrder!!.pickupLng else (poiMatch?.lng ?: 31.6762)
                LatLng(pLat, pLng)
            } else driverPos

            val pos = CameraPosition.Builder()
                .target(targetPosForCamera)
                .zoom(17.5)
                .bearing(smoothDriverBearing.value.toDouble())
                .tilt(30.0)
                .build()
            map.animateCamera(CameraUpdateFactory.newCameraPosition(pos), 800)
        }

        map.setOnMarkerClickListener { marker ->
            val clickedOrder = markerToOrderMap[marker.id]
            if (clickedOrder != null) {
                selectedCustomerOrderForDialog = clickedOrder
                true
            } else if (marker.title == (if(isAr) "السائق" else "Driver")) {
                showDriverInfoDialog = true
                true
            } else false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull()
                            if (change != null && change.pressed && !change.previousPressed) {
                                val isFromEdge = change.position.x < 28.dp.toPx()
                                onToggleDrawerGestures(isFromEdge)
                            }
                        }
                    }
                }
        ) {
            MapLibreCompose(
                modifier = Modifier.fillMaxSize(),
                styleUrl = styleUrl,
                onMapReady = { map ->
                    mapLibreInstance = map
                    map.addOnCameraMoveStartedListener { reason ->
                        if (reason == org.maplibre.android.maps.MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                            isCameraUserControlled = true
                        }
                    }
                }
            )
        }

        // Navigation Header
        if (currentInstruction.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(16.dp).align(Alignment.TopCenter),
                color = Color(0xFF2E7D32),
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 8.dp
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Navigation, null, tint = Color.White, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(currentInstruction, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        if (nextInstruction.isNotEmpty()) {
                            Text((if(isAr) "ثم " else "Then ") + nextInstruction, color = Color.White.copy(0.8f), fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Map Control Floating Buttons (Right Side)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End
        ) {
            SmallFloatingActionButton(onClick = { onToggleTheme() }, containerColor = Color.Black, contentColor = Color.White) { Icon(if(isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode, null) }
            
            // Satellite Toggle Button
            SmallFloatingActionButton(
                onClick = { isSatelliteMode = !isSatelliteMode },
                containerColor = if (isSatelliteMode) Color(0xFFC5A059) else Color.Black,
                contentColor = if (isSatelliteMode) Color.Black else Color.White
            ) {
                Icon(if (isSatelliteMode) Icons.Default.Map else Icons.Default.Layers, contentDescription = "Satellite Mode")
            }

            // Location Voice Guidance Speaker Button
            FloatingActionButton(
                onClick = { 
                    isReportingLocation = !isReportingLocation
                    isCameraUserControlled = false
                    if (isReportingLocation && hasActiveTrip) {
                        scope.launch {
                            val reportPos = if (isDriver) myPos else (peerPos ?: myPos)
                            val street = withContext(Dispatchers.IO) {
                                try {
                                    val geocoder = Geocoder(context, Locale("ar"))
                                    val addrs = geocoder.getFromLocation(reportPos.latitude, reportPos.longitude, 1)
                                    if (!addrs.isNullOrEmpty()) {
                                        addrs[0].thoroughfare ?: addrs[0].featureName ?: addrs[0].getAddressLine(0).split(",").firstOrNull() ?: ""
                                    } else ""
                                } catch (e: Exception) { "" }
                            }
                            if (street.isNotEmpty()) {
                                val prefix = if (isDriver) (if(isAr) "أنت الآن في " else "You are now in ")
                                             else (if(isAr) "السائق الآن في " else "The driver is now in ")
                                TTSHelper.speak(context, prefix + street)
                                lastStreetName = street
                            }
                        }
                    } else {
                        TTSHelper.stop()
                    }
                    val centerPos = if(isDriver) myPos else (peerPos ?: myPos)
                    mapLibreInstance?.animateCamera(CameraUpdateFactory.newLatLngZoom(centerPos, 17.0))
                },
                containerColor = if(isReportingLocation) Color(0xFFC5A059) else Color.Black,
                contentColor = if(isReportingLocation) Color.Black else Color.White
            ) { Icon(Icons.Default.LocalTaxi, contentDescription = "Vehicle Guidance") }

            // Zoom In (+) & Zoom Out (-) Buttons
            FloatingActionButton(onClick = { mapLibreInstance?.animateCamera(CameraUpdateFactory.zoomIn()) }, containerColor = Color.White.copy(0.85f), contentColor = Color.Black, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Add, null) }
            FloatingActionButton(onClick = { mapLibreInstance?.animateCamera(CameraUpdateFactory.zoomOut()) }, containerColor = Color.White.copy(0.85f), contentColor = Color.Black, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Remove, null) }

            // Chat Floating Action Button
            val activeTrip = selectedOrder ?: confirmedOrders.firstOrNull()
            if (activeTrip != null) {
                val chatId = if (activeTrip.rideType == "SHARED" && !activeTrip.driverId.isNullOrEmpty()) "shared_${activeTrip.driverId}" else activeTrip.id

                Box {
                    FloatingActionButton(onClick = { onChatClick(chatId) }, containerColor = Color(0xFFC5A059), contentColor = Color.Black) {
                        Icon(Icons.AutoMirrored.Filled.Chat, null)
                    }
                    if (unreadMessagesCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 4.dp, y = (-4).dp)
                                .size(20.dp)
                                .background(Color.Red, CircleShape)
                                .border(1.5.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (unreadMessagesCount > 99) "99+" else unreadMessagesCount.toString(),
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 120.dp, start = 16.dp, end = 16.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.Black.copy(alpha = 0.85f),
            shadowElevation = 8.dp
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Navigation, null, tint = Color(0xFFC5A059))
                        Spacer(Modifier.width(12.dp))
                        val displayTargetName = when {
                            selectedOrder != null -> selectedOrder!!.destinationName
                            confirmedOrders.isNotEmpty() -> confirmedOrders.first().destinationName
                            destName.isNotEmpty() && destName != "All" && destName != "القاهرة" && destName != "Cairo" -> destName
                            else -> if (isAr) "غير محدد بعد" else "Not specified yet"
                        }
                        Text((if(isAr) "الهدف: " else "Target: ") + displayTargetName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                    }

                    // Integrated Selection Button ("تغيير الوجهة")
                    val hasTrip = selectedOrder != null || confirmedOrders.isNotEmpty()
                    if (hasTrip) {
                        var dropdownExpanded by remember { mutableStateOf(false) }
                        val activeDriverName = selectedOrder?.driverName?.ifBlank { "السائق" } ?: confirmedOrders.firstOrNull()?.driverName?.ifBlank { "السائق" } ?: "السائق"
                        val totalFareSum = if (isDriver && confirmedOrders.isNotEmpty()) confirmedOrders.sumOf { it.price } else (selectedOrder?.price ?: 0.0)

                        val liveDistToSelected = remember(selectedParticipantOrder, smoothDriverLat.value, smoothDriverLng.value, myPos, peerPos) {
                            val myLat = myPos?.latitude ?: 31.4522
                            val myLng = myPos?.longitude ?: 31.6762
                            if (selectedParticipantOrder == null) {
                                val dLat = if (smoothDriverLat.value != 0f) smoothDriverLat.value.toDouble() else (peerPos?.latitude ?: myLat)
                                val dLng = if (smoothDriverLng.value != 0f) smoothDriverLng.value.toDouble() else (peerPos?.longitude ?: myLng)
                                MapUtils.calculateDistance(myLat, myLng, dLat, dLng)
                            } else {
                                val ord = selectedParticipantOrder!!
                                val poiMatch = com.albasel.tawsilaapp.util.DamiettaPlaces.getAllPois().firstOrNull { it.nameAr.contains(ord.pickupName) || ord.pickupName.contains(it.nameAr) }
                                val pLat = if (ord.pickupLat != 0.0) ord.pickupLat else (poiMatch?.lat ?: myLat)
                                val pLng = if (ord.pickupLng != 0.0) ord.pickupLng else (poiMatch?.lng ?: myLng)
                                val dLat = if (smoothDriverLat.value != 0f) smoothDriverLat.value.toDouble() else myLat
                                val dLng = if (smoothDriverLng.value != 0f) smoothDriverLng.value.toDouble() else myLng
                                MapUtils.calculateDistance(dLat, dLng, pLat, pLng)
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = (if (isAr) "المسافة بينكما: " else "Distance: ") + String.format(Locale.ENGLISH, "%.2f كم", liveDistToSelected),
                                color = Color(0xFF2E7D32),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                            Box {
                                Button(
                                    onClick = { 
                                        scope.launch {
                                            try {
                                                val ordersRef = firebaseManager.database.child("orders")
                                                val activeDriverId = selectedOrder?.driverId ?: confirmedOrders.firstOrNull()?.driverName?.ifBlank { null } ?: currentUserId
                                                val userSnaps = ordersRef.orderByChild("userId").equalTo(currentUserId).get().await().children
                                                val driverSnaps = if (!activeDriverId.isNullOrBlank()) {
                                                    ordersRef.orderByChild("driverId").equalTo(activeDriverId).get().await().children
                                                } else emptyList()
                                                
                                                val combined = (userSnaps + driverSnaps).mapNotNull { it.getValue(Order::class.java) }
                                                
                                                confirmedOrders = combined.filter { o -> 
                                                    (o.status == "ACCEPTED" || o.status == "CONFIRMED" || o.status == "ARRIVED" || o.status == "STARTED")
                                                }.distinctBy { it.id }.sortedByDescending { it.timestamp }
                                            } catch(e: Exception) {
                                                Log.e("TrackRideScreen", "Switch Ride error: ${e.message}")
                                            }
                                            dropdownExpanded = true 
                                        }
                                    }, 
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(38.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC5A059), contentColor = Color.Black),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    val selectedLabelText = if (selectedParticipantOrder == null) ("🏎️ " + activeDriverName) else selectedParticipantOrder!!.userName.ifBlank { "عميل" }
                                    Text(
                                        text = selectedLabelText, 
                                        fontSize = 11.5.sp, 
                                        fontWeight = FontWeight.Black, 
                                        maxLines = 1, 
                                        overflow = TextOverflow.Ellipsis, 
                                        modifier = Modifier.widthIn(min = 120.dp, max = 220.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(18.dp))
                                }
                            DropdownMenu(
                                expanded = dropdownExpanded, 
                                onDismissRequest = { dropdownExpanded = false },
                                modifier = Modifier.background(Color.Black.copy(alpha = 0.95f)).border(1.dp, Color(0xFFC5A059), RoundedCornerShape(12.dp))
                            ) {
                                // 1. Driver Option (Default / Top)
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "🏎️ " + (if (isAr) "السائق: " else "Driver: ") + activeDriverName,
                                                color = Color(0xFFC5A059),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                            if (isDriver && totalFareSum > 0) {
                                                Spacer(Modifier.width(12.dp))
                                                Text(
                                                    text = "${totalFareSum.toInt()} ج.م",
                                                    color = Color(0xFF2E7D32),
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        selectedParticipantOrder = null
                                        dropdownExpanded = false

                                        val dLat = if (smoothDriverLat.value != 0f) smoothDriverLat.value.toDouble() else (peerPos?.latitude ?: (myPos?.latitude ?: 31.4522))
                                        val dLng = if (smoothDriverLng.value != 0f) smoothDriverLng.value.toDouble() else (peerPos?.longitude ?: (myPos?.longitude ?: 31.6762))
                                        val myLat = myPos?.latitude ?: 31.4522
                                        val myLng = myPos?.longitude ?: 31.6762

                                        val dist = MapUtils.calculateDistance(myLat, myLng, dLat, dLng)
                                        peerRoutePoints = listOf(LatLng(myLat, myLng), LatLng(dLat, dLng))

                                        mapLibreInstance?.animateCamera(
                                            CameraUpdateFactory.newLatLngZoom(LatLng(dLat, dLng), 16.5)
                                        )

                                        val speakMsg = if (isAr) "المسافة بينك وبين السائق $activeDriverName هي ${String.format(Locale.ENGLISH, "%.1f", dist)} كيلومتر"
                                                       else "Distance to driver $activeDriverName is ${String.format(Locale.ENGLISH, "%.1f", dist)} km"

                                        TTSHelper.speak(context, speakMsg)
                                    }
                                )

                                HorizontalDivider(color = Color.White.copy(0.15f))

                                // 2. Customer Options
                                val ordersList = if (confirmedOrders.isNotEmpty()) confirmedOrders else listOfNotNull(selectedOrder)
                                ordersList.forEachIndexed { index, order ->
                                    DropdownMenuItem(
                                        text = {
                                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = order.userName.ifBlank { if(isAr) "عميل" else "Customer" },
                                                        color = Color(0xFF2E7D32),
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Black
                                                    )
                                                    Text(
                                                        text = "${order.price.toInt()} ج.م",
                                                        color = Color(0xFFC5A059),
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = order.pickupName.ifBlank { if(isAr) "موقع العميل" else "Pickup" },
                                                        color = Color(0xFFB8860B),
                                                        fontSize = 11.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f, fill = false)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = "${order.passengers} " + (if(isAr) "فرد" else "pax"),
                                                        color = Color.Gray,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        },
                                        onClick = { 
                                            selectedParticipantOrder = order
                                            lastAutoZoomedOrderId = null
                                            selectedOrder = order
                                            dropdownExpanded = false

                                            val dLat = if (smoothDriverLat.value != 0f) smoothDriverLat.value.toDouble() else (myPos?.latitude ?: 31.4522)
                                            val dLng = if (smoothDriverLng.value != 0f) smoothDriverLng.value.toDouble() else (myPos?.longitude ?: 31.6762)

                                            val poiMatch = com.albasel.tawsilaapp.util.DamiettaPlaces.getAllPois().firstOrNull { it.nameAr.contains(order.pickupName) || order.pickupName.contains(it.nameAr) }
                                            val pLat = if (order.pickupLat != 0.0) order.pickupLat else (poiMatch?.lat ?: 31.4522)
                                            val pLng = if (order.pickupLng != 0.0) order.pickupLng else (poiMatch?.lng ?: 31.6762)

                                            val dist = MapUtils.calculateDistance(dLat, dLng, pLat, pLng)
                                            val name = order.userName.ifBlank { "العميل" }

                                            peerRoutePoints = listOf(LatLng(dLat, dLng), LatLng(pLat, pLng))

                                            mapLibreInstance?.animateCamera(
                                                CameraUpdateFactory.newLatLngZoom(LatLng(pLat, pLng), 16.5)
                                            )

                                            val speakMsg = if (isAr) "المسافة بينك وبين العميل $name هي ${String.format(Locale.ENGLISH, "%.1f", dist)} كيلومتر"
                                                           else "Distance to customer $name is ${String.format(Locale.ENGLISH, "%.1f", dist)} km"

                                            TTSHelper.speak(context, speakMsg)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
                
                // Real-time Automated Waiting/Loading UI
                selectedOrder?.let { order ->
                    val isCargo = order.isCargo
                    
                    if (order.driverArrivalTime > 0L) {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // 1. Waiting Time Section
                            val waitStatus = when {
                                order.customerArrivalTime > 0L -> if(isAr) "العميل وصل" else "Customer Arrived"
                                else -> if(isAr) "السائق ينتظر..." else "Driver Waiting..."
                            }
                            
                            val startTime = order.driverArrivalTime
                            val endTime = if(order.customerArrivalTime > 0L) order.customerArrivalTime else System.currentTimeMillis()
                            val totalWaitMillis = (endTime - startTime).coerceAtLeast(0L)
                            val totalWaitMins = (totalWaitMillis / 60000).toInt()
                            val freeMins = systemLogic.freeWaitingTime
                            val extraWait = (totalWaitMins - freeMins).coerceAtLeast(0)
                            val waitFee = extraWait * systemLogic.extraWaitingPrice

                            Surface(color = Color.White.copy(0.1f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(10.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(waitStatus, color = Color(0xFFC5A059), fontWeight = FontWeight.Bold, fontSize = 11.sp)

                                        if (order.customerArrivalTime == 0L) {
                                            val remainingFree = (freeMins - totalWaitMins).coerceAtLeast(0)
                                            if (remainingFree > 0) {
                                                Text(if(isAr) "الوقت المجاني: ${remainingFree}د" else "Free Time: ${remainingFree}m", color = Color.Green, fontSize = 11.sp)
                                            } else {
                                                Text(if(isAr) "وقت إضافي: ${extraWait}د" else "Extra Time: ${extraWait}m", color = Color(0xFFB8860B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(if(isAr) "إجمالي الوقت: $totalWaitMins دقيقة" else "Total Wait: $totalWaitMins min", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
                                        val fixedTotalFare = if (confirmedOrders.isNotEmpty()) confirmedOrders.sumOf { it.price } else (selectedOrder?.price ?: order.price)
                                        val netPrice = fixedTotalFare.toInt()
                                        Text(if(isAr) "إجمالي سعر الرحلة: $netPrice ج.م" else "Total Fare: $netPrice EGP", color = Color(0xFFC5A059), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                                    }
                                }
                            }

                            // 2. Cargo Specific: Loading/Unloading Timers & Buttons
                            if (isCargo) {
                                val driverPos = if (isDriver) myPos else peerPos
                                val isDriverAtPickup = driverPos != null && MapUtils.calculateDistance(driverPos.latitude, driverPos.longitude, order.pickupLat, order.pickupLng) * 1000.0 <= 200.0
                                val isDriverAtDest = driverPos != null && MapUtils.calculateDistance(driverPos.latitude, driverPos.longitude, order.destinationLat, order.destinationLng) * 1000.0 <= 200.0

                                // A. Loading Timer
                                if (order.loadingStartTime > 0L) {
                                    val lEndTime = if(order.loadingEndTime > 0L) order.loadingEndTime else System.currentTimeMillis()
                                    val lTotalMillis = (lEndTime - order.loadingStartTime).coerceAtLeast(0L)
                                    val lTotalMins = (lTotalMillis / 60000).toInt()
                                    val lFreeMins = systemLogic.freeLoadingTime
                                    val lExtra = (lTotalMins - lFreeMins).coerceAtLeast(0)
                                    val lFee = lExtra * systemLogic.loadingExtraPrice

                                    Surface(color = Color.White.copy(0.05f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(10.dp)) {
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(if(isAr) "وقت التحميل" else "Loading Time", color = Color(0xFF795548), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                                if (order.loadingEndTime == 0L) {
                                                    val rem = (lFreeMins - lTotalMins).coerceAtLeast(0)
                                                    if (rem > 0) Text(if(isAr) "مجاني: ${rem}د" else "Free: ${rem}m", color = Color.Green, fontSize = 11.sp)
                                                    else Text(if(isAr) "إضافي: ${lExtra}د" else "Extra: ${lExtra}m", color = Color.Red, fontSize = 11.sp)
                                                }
                                            }
                                            Text(if(isAr) "الوقت: $lTotalMins د" else "Time: $lTotalMins m", color = Color.White, fontSize = 12.sp)
                                        }
                                    }
                                }

                                // B. Unloading Timer
                                if (order.unloadingStartTime > 0L) {
                                    val uEndTime = if(order.unloadingEndTime > 0L) order.unloadingEndTime else System.currentTimeMillis()
                                    val uTotalMillis = (uEndTime - order.unloadingStartTime).coerceAtLeast(0L)
                                    val uTotalMins = (uTotalMillis / 60000).toInt()
                                    val uFreeMins = systemLogic.freeUnloadingTime
                                    val uExtra = (uTotalMins - uFreeMins).coerceAtLeast(0)
                                    val uFee = uExtra * systemLogic.unloadingExtraPrice

                                    Surface(color = Color.White.copy(0.05f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(10.dp)) {
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(if(isAr) "وقت التفريغ" else "Unloading Time", color = Color(0xFF795548), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                                if (order.unloadingEndTime == 0L) {
                                                    val rem = (uFreeMins - uTotalMins).coerceAtLeast(0)
                                                    if (rem > 0) Text(if(isAr) "مجاني: ${rem}د" else "Free: ${rem}m", color = Color.Green, fontSize = 11.sp)
                                                    else Text(if(isAr) "إضافي: ${uExtra}د" else "Extra: ${uExtra}m", color = Color.Red, fontSize = 11.sp)
                                                }
                                            }
                                            Text(if(isAr) "الوقت: $uTotalMins د" else "Time: $uTotalMins m", color = Color.White, fontSize = 12.sp)
                                        }
                                    }
                                }

                                if (!isDriver && order.needsLoading && order.loadingEndTime == 0L && isDriverAtPickup && !isDriverAtDest) {
                                    Button(
                                        onClick = { scope.launch { firebaseManager.updateOrderEvent(order.id, "loadingEndTime") } },
                                        modifier = Modifier.fillMaxWidth().height(42.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC5A059), contentColor = Color.Black),
                                        shape = RoundedCornerShape(12.dp)
                                    ) { Text(if(isAr) "تم التحميل" else "Done Loading", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                                }

                                if (!isDriver && order.needsUnloading && order.unloadingEndTime == 0L && isDriverAtDest) {
                                    Button(
                                        onClick = { scope.launch { firebaseManager.updateOrderEvent(order.id, "unloadingEndTime") } },
                                        modifier = Modifier.fillMaxWidth().height(42.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC5A059), contentColor = Color.Black),
                                        shape = RoundedCornerShape(12.dp)
                                    ) { Text(if(isAr) "تم التفريغ" else "Done Unloading", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                                }
                            }
                        }
                    }
                }

                // Route Change Request Banner (Driver Side)
                if (isDriver && selectedOrder?.routeChangeStatus == "PENDING") {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        color = Color(0xFFE65100),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("العميل يطلب تغيير الوجهة إلى:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(selectedOrder?.requestedDestinationName ?: "", color = Color.White, fontSize = 14.sp)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { 
                                        scope.launch { 
                                            firebaseManager.database.child("orders").child(selectedOrder!!.id).updateChildren(mapOf(
                                                "routeChangeStatus" to "DRIVER_ACCEPTED", 
                                                "destinationName" to selectedOrder!!.requestedDestinationName, 
                                                "destinationLat" to selectedOrder!!.requestedDestinationLat, 
                                                "destinationLng" to selectedOrder!!.requestedDestinationLng
                                            )) 
                                        } 
                                    },
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                                    shape = RoundedCornerShape(8.dp)
                                ) { Text("موافقة", fontSize = 11.sp, fontWeight = FontWeight.Black) }
                                
                                OutlinedButton(
                                    onClick = { scope.launch { firebaseManager.database.child("orders").child(selectedOrder!!.id).child("routeChangeStatus").setValue("DRIVER_REJECTED") } },
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    border = BorderStroke(1.dp, Color.White),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    shape = RoundedCornerShape(8.dp)
                                ) { Text("رفض", fontSize = 11.sp) }
                            }
                        }
                    }
                }
                
                // Route Change Info (Customer Side)
                if (!isDriver && selectedOrder?.routeChangeStatus == "PENDING") {
                    Text("بانتظار موافقة السائق على تغيير المسار...", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                }

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    TrackInfoItem(if(isAr) "المسافة" else "Dist", String.format(Locale.ENGLISH, "%.2f km", distanceRemaining))
                    TrackInfoItem(if(isAr) "الوقت" else "Time", String.format(Locale.ENGLISH, "%d min", timeRemainingMinutes))
                }
            }
        }

        // Fixed Solid Black Navigation Bar Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                .background(Color.Black)
                .align(Alignment.BottomCenter)
                .zIndex(100f)
        )
    }



    if (showDriverInfoDialog && selectedOrder != null) {
        val o = selectedOrder!!
        val isConfirmed = o.status == "CONFIRMED" || o.status == "ARRIVED" || o.status == "STARTED"
        val isCompleted = o.status == "COMPLETED"
        
        AlertDialog(
            onDismissRequest = { showDriverInfoDialog = false },
            title = { Text(if(isAr) "بيانات السائق" else "Driver Details", fontWeight = FontWeight.Black, color = Color(0xFFC5A059)) },
            text = {
                Column {
                    Text(o.driverName ?: "", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    
                    if (isConfirmed) {
                        Text(o.driverPhone ?: "", color = Color(0xFFC5A059), fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                    } else if (isCompleted) {
                        Text(if(isAr) "تمت الرحلة بنجاح" else "Trip Completed", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    } else {
                        Text(if(isAr) "سيظهر الرقم عند تأكيد الرحلة" else "Phone will show after confirmation", color = Color.Gray, fontSize = 12.sp)
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = Color.LightGray.copy(0.2f))
                    Spacer(Modifier.height(16.dp))
                    
                    DetailRow(if(isAr) "نوع المركبة:" else "Vehicle:", o.vehicleType ?: "", isAr)
                    DetailRow(if(isAr) "رقم اللوحة:" else "Plate:", o.vehiclePlate ?: "", isAr)
                    DetailRow(if(isAr) "لون المركبة:" else "Color:", o.vehicleColor ?: "", isAr)
                    
                    if (isConfirmed) {
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${o.driverPhone}"))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Phone, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if(isAr) "اتصال بالسائق" else "Call Driver", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDriverInfoDialog = false }) { Text(if(isAr) "إغلاق" else "Close", color = Color.Gray) } }
        )
    }

    if (selectedCustomerOrderForDialog != null) {
        val custOrder = selectedCustomerOrderForDialog!!
        val driverLat = if (smoothDriverLat.value != 0f) smoothDriverLat.value.toDouble() else (myPos?.latitude ?: 31.4522)
        val driverLng = if (smoothDriverLng.value != 0f) smoothDriverLng.value.toDouble() else (myPos?.longitude ?: 31.6762)
        val pLat = if (custOrder.pickupLat != 0.0) custOrder.pickupLat else 31.4522
        val pLng = if (custOrder.pickupLng != 0.0) custOrder.pickupLng else 31.6762
        val distToCust = MapUtils.calculateDistance(driverLat, driverLng, pLat, pLng)

        AlertDialog(
            onDismissRequest = { selectedCustomerOrderForDialog = null },
            title = {
                Text(
                    text = custOrder.userName.ifBlank { "تفاصيل العميل" },
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF2E7D32),
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailRow("اسم العميل:", custOrder.userName.ifBlank { "عميل" }, isAr)
                    DetailRow("موقع الانطلاق:", custOrder.pickupName, isAr)
                    DetailRow("الوجهة:", custOrder.destinationName, isAr)
                    DetailRow("عدد الأفراد:", "${custOrder.passengers} فرد", isAr)
                    DetailRow("المبلغ:", "${custOrder.price.toInt()} ج.م", isAr)
                    DetailRow("المسافة بينكما:", String.format(Locale.ENGLISH, "%.2f كم", distToCust), isAr)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedOrder = custOrder
                        selectedCustomerOrderForDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC5A059), contentColor = Color.Black)
                ) { Text("تحديد هذه الرحلة") }
            },
            dismissButton = {
                TextButton(onClick = { selectedCustomerOrderForDialog = null }) {
                    Text("إغلاق", color = Color.Gray)
                }
            }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String, isAr: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Gray, fontSize = 13.sp)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun TrackInfoItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.Gray, fontSize = 11.sp)
        Text(value, color = Color(0xFFC5A059), fontSize = 18.sp, fontWeight = FontWeight.Black)
    }
}
