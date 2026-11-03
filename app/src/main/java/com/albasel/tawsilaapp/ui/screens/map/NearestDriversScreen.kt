package com.albasel.tawsilaapp.ui.screens.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.albasel.tawsilaapp.R
import com.albasel.tawsilaapp.util.VehicleUtils.cleanVehicleName
import com.albasel.tawsilaapp.util.VehicleUtils.getBaseIconName
import com.albasel.tawsilaapp.data.firebase.FirebaseManager
import com.albasel.tawsilaapp.data.model.User
import com.albasel.tawsilaapp.ui.components.MapLibreCompose
import com.albasel.tawsilaapp.util.AppImageView
import com.albasel.tawsilaapp.util.MapUtils
import com.albasel.tawsilaapp.utils.MapMarkerUtils
import com.google.android.gms.location.LocationServices
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.IconFactory

@Composable
fun NearestDriversScreen(
    firebaseManager: FirebaseManager,
    language: String,
    isDarkMode: Boolean,
    initialFilter: String? = null,
    onToggleTheme: () -> Unit,
    onToggleDrawerGestures: (Boolean) -> Unit = {},
    onDriverSelected: (User) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isAr = language == "AR"
    
    var myPos by remember { mutableStateOf<LatLng?>(null) }
    var drivers by remember { mutableStateOf(emptyList<User>()) }
    
    var mapLibreInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var longPressedDriver by remember { mutableStateOf<User?>(null) }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val styleUrl = if (isDarkMode) 
        com.albasel.tawsilaapp.data.map.config.MapTileProviderConfig.nightTileUrl
    else 
        com.albasel.tawsilaapp.data.map.config.MapTileProviderConfig.dayTileUrl

    DisposableEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            scope.launch {
                try {
                    // Optimized Fast Fix: Try lastLocation first for instant response
                    val lastLoc = fusedLocationClient.lastLocation.await()
                    lastLoc?.let {
                        val ll = LatLng(it.latitude, it.longitude)
                        if (myPos == null) {
                            myPos = ll
                            mapLibreInstance?.animateCamera(CameraUpdateFactory.newLatLngZoom(ll, 16.5))
                        }
                    }

                    // High-accuracy follow up
                    val tokenSource = com.google.android.gms.tasks.CancellationTokenSource()
                    val loc = fusedLocationClient.getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, tokenSource.token).await()
                    loc?.let {
                        val ll = LatLng(it.latitude, it.longitude)
                        myPos = ll
                        // Only auto-pan if user is fairly static or just opened
                        mapLibreInstance?.animateCamera(CameraUpdateFactory.newLatLngZoom(ll, 16.5))
                    }
                } catch (e: Exception) {}
            }
        }
        
        val listener = firebaseManager.listenToOnlineDrivers { updatedDrivers ->
            drivers = updatedDrivers
        }
        
        onDispose {
            firebaseManager.database.child("users").removeEventListener(listener)
        }
    }

    var selectedFilter by remember { mutableStateOf(initialFilter ?: (if(isAr) "الكل" else "All")) }
    val vehicleTypes = remember(isAr) {
        if(isAr) listOf("الكل", "تاكسي", "فان", "ملاكي", "توكتوك", "تريسكل", "باص", "ميكروباص", "موتوسيكل", "دراجة", "ربع نقل", "نصف نقل", "نقل")
        else listOf("All", "Taxi", "Van", "Private", "Tuk-tuk", "Tricycle", "Bus", "Microbus", "Motorcycle", "Bike", "Quarter Truck", "Half Truck", "Truck")
    }

    val filteredDrivers = remember(drivers, selectedFilter, myPos) {
        if (myPos == null) return@remember emptyList<User>()
        
        val stages = listOf(3.0, 5.0, 7.0, 10.0)
        var result = emptyList<User>()
        
        for (radius in stages) {
            val candidates = drivers.filter { d ->
                val dist = MapUtils.calculateDistance(myPos!!.latitude, myPos!!.longitude, d.latitude, d.longitude)
                dist <= radius && d.isApproved 
            }
            
            if (candidates.isNotEmpty()) {
                val typeFiltered = if (selectedFilter == "الكل" || selectedFilter == "All") candidates
                else {
                    val s = cleanVehicleName(selectedFilter)
                    val sBase = getBaseIconName(selectedFilter)
                    
                    candidates.filter { d ->
                        val v = cleanVehicleName(d.vehicleType)
                        val vBase = getBaseIconName(d.vehicleType)
                        
                        v == s || vBase == sBase || v.contains(s) || s.contains(v) ||
                        (sBase == "truck" && vBase.contains("truck")) ||
                        (s == "نقل" && (v.contains("truck") || v.contains("ربع") || v.contains("نصف")))
                    }
                }
                
                if (typeFiltered.isNotEmpty()) {
                    result = typeFiltered
                    break
                }
            }
        }
        result
    }

    // Track the last drawn drivers to avoid flickering
    var lastDrawnDriversHash by remember { mutableIntStateOf(0) }

    LaunchedEffect(filteredDrivers, myPos, mapLibreInstance) {
        val map = mapLibreInstance ?: return@LaunchedEffect
        
        // Use a hash of IDs and Locations to detect real changes
        val currentHash = filteredDrivers.sumOf { it.id.hashCode() + it.latitude.hashCode() + it.longitude.hashCode() }
        if (currentHash == lastDrawnDriversHash && lastDrawnDriversHash != 0) return@LaunchedEffect
        
        lastDrawnDriversHash = currentHash
        map.clear()
        
        // Draw Toll Gates
        val tollIcon = MapMarkerUtils.createTollGateIcon(context)
        MapUtils.TOLL_GATES.forEach { gate ->
            map.addMarker(MarkerOptions()
                .position(LatLng(gate.lat, gate.lng))
                .title(gate.name)
                .icon(tollIcon))
        }

        // User location
        val userLocationIcon = MapMarkerUtils.createBlueUserLocationDotIcon(context)
        myPos?.let { 
            map.addMarker(
                MarkerOptions()
                    .position(it)
                    .title(if(isAr) "موقعي" else "Me")
                    .icon(userLocationIcon)
            ) 
        }
        
        filteredDrivers.forEach { driver ->
            val vBase = getBaseIconName(driver.vehicleType)
            val vSuffix = if(isDarkMode) "2" else "1"
            val resId = context.resources.getIdentifier("ic_$vBase$vSuffix", "drawable", context.packageName).let {
                if (it != 0) it else R.drawable.ic_taxi1
            }
            
            val vehicleIcon = MapMarkerUtils.createVehicleMarker(context, resId, scale = 2.0f, showRipples = false)
            
            map.addMarker(MarkerOptions()
                .position(LatLng(driver.latitude, driver.longitude))
                .title(driver.name)
                .snippet(driver.id) // Store ID in snippet for precise lookup
                .icon(vehicleIcon))
        }
        
        map.setOnMarkerClickListener { marker ->
            // Try to find by ID first (snippet), then name
            val sid = marker.snippet
            val selected = filteredDrivers.find { it.id == sid } ?: filteredDrivers.find { it.name == marker.title }
            
            if (selected != null) {
                scope.launch {
                    val fullUser = firebaseManager.getUser(selected.id)
                    if (fullUser != null) {
                        onDriverSelected(fullUser)
                    }
                }
                true
            } else false
        }

        map.addOnMapLongClickListener { point ->
            // Find the closest driver to the long-press point within ~100 meters
            val selected = filteredDrivers.filter {
                val dist = MapUtils.calculateDistance(it.latitude, it.longitude, point.latitude, point.longitude)
                dist < 0.1 
            }.minByOrNull {
                MapUtils.calculateDistance(it.latitude, it.longitude, point.latitude, point.longitude)
            }

            if (selected != null) {
                scope.launch {
                    val fullUser = firebaseManager.getUser(selected.id)
                    if (fullUser != null) {
                        longPressedDriver = fullUser
                    }
                }
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
                onMapReady = { 
                    mapLibreInstance = it 
                    myPos?.let { pos -> it.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 16.5)) }
                }
            )
        }

        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(color = (if(isDarkMode) Color.Black else Color.White).copy(0.9f), shape = RoundedCornerShape(16.dp), shadowElevation = 4.dp) {
                Text(if(isAr) "السائقين القريبين" else "Nearest Drivers", modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if(isDarkMode) Color.White else Color.Black)
            }
            
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(vehicleTypes) { type ->
                    val isSelected = selectedFilter == type
                    Surface(
                        onClick = { selectedFilter = type },
                        color = if(isSelected) Color(0xFFC5A059) else (if(isDarkMode) Color.Black else Color.White).copy(0.8f),
                        shape = RoundedCornerShape(12.dp),
                        border = if(!isSelected) BorderStroke(1.dp, Color(0xFFC5A059).copy(0.3f)) else null,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text(type, color = if(isSelected) Color.Black else (if(isDarkMode) Color.White else Color.Black), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Controls at bottom right
        Column(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(onClick = onToggleTheme, color = Color.Black.copy(0.8f), shape = CircleShape, modifier = Modifier.size(44.dp), shadowElevation = 6.dp) {
                Box(contentAlignment = Alignment.Center) { Icon(if(isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
            }
            Surface(onClick = { mapLibreInstance?.animateCamera(CameraUpdateFactory.zoomIn()) }, color = if(isDarkMode) Color(0xFF262626) else Color.White, shape = CircleShape, modifier = Modifier.size(44.dp), shadowElevation = 6.dp) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, null, tint = if(isDarkMode) Color.White else Color.Black) }
            }
            Surface(onClick = { mapLibreInstance?.animateCamera(CameraUpdateFactory.zoomOut()) }, color = if(isDarkMode) Color(0xFF262626) else Color.White, shape = CircleShape, modifier = Modifier.size(44.dp), shadowElevation = 6.dp) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Remove, null, tint = if(isDarkMode) Color.White else Color.Black) }
            }
            Surface(
                onClick = { 
                    scope.launch {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                            val tokenSource = com.google.android.gms.tasks.CancellationTokenSource()
                            val loc = fusedLocationClient.getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, tokenSource.token).await() ?: fusedLocationClient.lastLocation.await()
                            loc?.let {
                                val ll = LatLng(it.latitude, it.longitude)
                                myPos = ll
                                mapLibreInstance?.animateCamera(CameraUpdateFactory.newLatLngZoom(ll, 16.5))
                            }
                        }
                    }
                }, 
                color = if(isDarkMode) Color(0xFF262626) else Color.White, 
                shape = CircleShape, 
                modifier = Modifier.size(44.dp), 
                shadowElevation = 6.dp
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.MyLocation, null, tint = Color(0xFF2D8CFF)) }
            }
        }

        longPressedDriver?.let { driver ->
            AlertDialog(
                onDismissRequest = { longPressedDriver = null },
                title = { 
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        val pImg = driver.profileImageDriveId ?: driver.profileImageBase64
                        Box(Modifier.size(80.dp).clip(CircleShape).background(Color.LightGray)) {
                            AppImageView(pImg, Modifier.fillMaxSize())
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(driver.name, fontWeight = FontWeight.Black)
                        Text(driver.vehicleType, fontSize = 12.sp, color = Color.Gray)
                    }
                },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(if(isAr) "هل تريد اختيار هذا السائق؟" else "Do you want to select this driver?")
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { 
                            onDriverSelected(driver)
                            longPressedDriver = null 
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC5A059))
                    ) {
                        Text(if(isAr) "اختيار السائق" else "Select Driver", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { longPressedDriver = null }) {
                        Text(if(isAr) "إلغاء" else "Cancel")
                    }
                }
            )
        }
    }
}
