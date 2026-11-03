package com.albasel.tawsilaapp.ui.screens.city

import android.Manifest
import android.content.Context
import android.location.Geocoder
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import com.albasel.tawsilaapp.data.model.PointTransaction
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.albasel.tawsilaapp.R
import com.albasel.tawsilaapp.data.firebase.FirebaseManager
import com.albasel.tawsilaapp.data.model.CityDestination
import com.albasel.tawsilaapp.data.model.Order
import com.albasel.tawsilaapp.data.model.VehiclePrice
import com.albasel.tawsilaapp.ui.theme.PremiumGold
import com.albasel.tawsilaapp.util.VehicleUtils.getBaseIconName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.roundToInt

suspend fun geocodeAddress(context: Context, address: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
    try { Geocoder(context, Locale("ar")).getFromLocationName(address, 1)?.get(0)?.let { it.latitude to it.longitude } } catch (e: Exception) { null }
}

@Immutable
data class VehicleInfoDataInside(
    val name: String, 
    val iconRes: Int,
    val capacity: Int, 
    val maxCargoWeightKg: Double,
    val iconColor: Color,
    val ratePerKm: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsideCityScreen(
    firebaseManager: FirebaseManager, 
    language: String, 
    user: com.albasel.tawsilaapp.data.model.User?,
    isDarkMode: Boolean,
    hasBlockingOrder: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isAr = language == "AR"
    
    val themeBg = if (isDarkMode) Color(0xFF121212) else Color(0xFFFAFAF7)
    val cardBg = if (isDarkMode) Color(0xFF1E1E1E) else Color.White
    val textColor = if (isDarkMode) Color.White else Color.Black

    var destinationName by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var passengers by remember { mutableStateOf("1") }
    var rideType by remember { mutableStateOf("SHARED") }
    var vehicleType by remember { mutableStateOf("") }
    
    var selectedDate by remember { mutableStateOf<String>(SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date())) }
    var selectedTime by remember { 
        val cal = Calendar.getInstance()
        if (cal.get(Calendar.MINUTE) > 0) cal.add(Calendar.HOUR_OF_DAY, 1)
        cal.set(Calendar.MINUTE, 0)
        val timeStr = SimpleDateFormat("hh:00 ", Locale.ENGLISH).format(cal.time)
        val amPm = if(cal.get(Calendar.AM_PM) == Calendar.AM) (if(isAr) "صباحاً" else "AM") else (if(isAr) "مساءً" else "PM")
        mutableStateOf(timeStr + amPm)
    }
    
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showDateTimePickerDialog by remember { mutableStateOf(false) }
    
    var isLoading by remember { mutableStateOf(false) }
    var isLoadingLines by remember { mutableStateOf(true) }
    var destinations by remember { mutableStateOf<List<CityDestination>>(emptyList()) }
    var currentProvince by remember { mutableStateOf("") }
    var userLat by remember { mutableDoubleStateOf(0.0) }
    var userLng by remember { mutableDoubleStateOf(0.0) }
    
    var calculatedDistValue by remember { mutableDoubleStateOf(0.0) }
    var finalPickupName by remember { mutableStateOf("") }
    var finalDestName by remember { mutableStateOf("") }
    var pickupCoord by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var destCoord by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var vehiclePrices by remember { mutableStateOf<Map<String, VehiclePrice>>(emptyMap()) }

    LaunchedEffect(Unit) {
        destinations = firebaseManager.getCityDestinations()
        vehiclePrices = firebaseManager.getVehiclePricesDetail()
        isLoadingLines = false
        try {
            val fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) {
                        userLat = loc.latitude
                        userLng = loc.longitude
                        scope.launch {
                            try {
                                val geocoder = android.location.Geocoder(context, java.util.Locale("ar"))
                                val addrs = geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
                                if (!addrs.isNullOrEmpty()) currentProvince = addrs[0].adminArea ?: ""
                            } catch (e: Exception) {}
                        }
                    }
                }
            }
        } catch (e: Exception) {}
    }

    val vehicleList = remember(isAr, vehiclePrices, isDarkMode) {
        if (vehiclePrices.isEmpty()) return@remember emptyList<VehicleInfoDataInside>()
        
        val suffix = if (isDarkMode) "2" else "1"
        fun getRes(name: String): Int {
            val base = getBaseIconName(name)
            val id = context.resources.getIdentifier("ic_$base$suffix", "drawable", context.packageName)
            return if (id != 0) id else R.drawable.ic_taxi1
        }
        
        val staticList = listOf(
            VehicleInfoDataInside(if(isAr) "ملاكي" else "Private", getRes("privatecar"), 4, 400.0, PremiumGold, 10.0),
            VehicleInfoDataInside(if(isAr) "تاكسي" else "Taxi", getRes("taxi"), 4, 350.0, PremiumGold, 8.0),
            VehicleInfoDataInside(if(isAr) "فان" else "Van", getRes("van"), 7, 700.0, PremiumGold, 12.0),
            VehicleInfoDataInside(if(isAr) "توكتوك" else "Tuk-tuk", getRes("tuk_tuk"), 3, 150.0, PremiumGold, 5.0),
            VehicleInfoDataInside(if(isAr) "تريسكل" else "Tricycle", getRes("tricycle"), 2, 300.0, PremiumGold, 8.0),
            VehicleInfoDataInside(if(isAr) "باص" else "Bus", getRes("bus"), 50, 1500.0, PremiumGold, 15.0),
            VehicleInfoDataInside(if(isAr) "ميكروباص" else "Microbus", getRes("microbus"), 14, 800.0, PremiumGold, 15.0),
            VehicleInfoDataInside(if(isAr) "موتوسيكل" else "Motorcycle", getRes("moto"), 1, 100.0, PremiumGold, 5.0),
            VehicleInfoDataInside(if(isAr) "دراجة" else "Bike", getRes("bike"), 1, 25.0, PremiumGold, 3.0),
            VehicleInfoDataInside(if(isAr) "ربع نقل" else "Quarter Truck", getRes("quartertruck"), 2, 1000.0, PremiumGold, 15.0),
            VehicleInfoDataInside(if(isAr) "نصف نقل" else "Half Truck", getRes("halftruck"), 3, 2000.0, PremiumGold, 20.0),
            VehicleInfoDataInside(if(isAr) "نقل" else "Truck", getRes("truck"), 3, 5000.0, PremiumGold, 30.0)
        )
        
        staticList.filter { v -> 
            val vp = vehiclePrices[v.name]
            (vp?.isVisible ?: true) && (vp?.showInHome ?: true)
        }.sortedBy { vehiclePrices[it.name]?.priority ?: 999 }
    }

    LaunchedEffect(vehicleList) {
        if (vehicleType.isEmpty() && vehicleList.isNotEmpty()) vehicleType = vehicleList[0].name
    }

    fun calculateDist(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.systemBars)) { 
        Column(modifier = Modifier.fillMaxSize().background(themeBg)) {
            // 1. Luxury Header
            Surface(
                modifier = Modifier.fillMaxWidth().height(95.dp), 
                color = Color.Black, 
                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(0.12f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Place, contentDescription = null, tint = Color(0xFFC5A059), modifier = Modifier.size(20.dp))
                            }
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFFC5A059), modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "WASALNA وصلنا",
                                    color = Color(0xFFC5A059),
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if(isAr) "توصيل داخل المدينة" else "Inside City Ride",
                                color = Color.White.copy(0.9f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(0.12f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.MyLocation, contentDescription = null, tint = Color(0xFFC5A059), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // 2. Pill Switcher (مشتركة | خاصة) - "مشتركة" is default
                Surface(
                    shape = RoundedCornerShape(30.dp),
                    color = if(isDarkMode) Color(0xFF1E1E1E) else Color(0xFFF0EFEA),
                    border = BorderStroke(1.dp, Color(0xFFC5A059).copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Segment 1 (Default): مشتركة
                        val isSharedSelected = rideType == "SHARED"
                        Surface(
                            onClick = { rideType = "SHARED" },
                            shape = RoundedCornerShape(26.dp),
                            color = if(isSharedSelected) Color(0xFFC5A059) else Color.Transparent,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(Icons.Default.Groups, null, tint = if(isSharedSelected) Color.Black else Color(0xFFC5A059), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if(isAr) "مشتركة" else "Shared", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if(isSharedSelected) Color.Black else Color(0xFFC5A059))
                            }
                        }

                        // Segment 2: خاصة
                        val isPrivateSelected = rideType == "PRIVATE"
                        Surface(
                            onClick = { rideType = "PRIVATE" },
                            shape = RoundedCornerShape(26.dp),
                            color = if(isPrivateSelected) Color(0xFFC5A059) else Color.Transparent,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(Icons.Default.Person, null, tint = if(isPrivateSelected) Color.Black else Color(0xFFC5A059), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if(isAr) "خاصة" else "Private", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if(isPrivateSelected) Color.Black else Color(0xFFC5A059))
                            }
                        }
                    }
                }

                // Passenger Count Dropdown for SHARED mode
                if (rideType == "SHARED") {
                    Spacer(Modifier.height(8.dp))
                    var pExpanded by remember { mutableStateOf(false) }
                    Box(Modifier.fillMaxWidth(0.5f)) {
                        Surface(
                            onClick = { pExpanded = true }, 
                            color = if(isDarkMode) Color(0xFF1E1E1E) else Color.White, 
                            shape = RoundedCornerShape(20.dp), 
                            modifier = Modifier.fillMaxWidth().height(38.dp),
                            border = BorderStroke(1.dp, Color(0xFFC5A059).copy(alpha = 0.5f))
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                Icon(Icons.Default.Groups, null, modifier = Modifier.size(16.dp), tint = Color(0xFFC5A059))
                                Spacer(Modifier.width(6.dp))
                                Text((if(isAr) "عدد الأفراد: " else "Pax: ") + passengers, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFC5A059))
                                Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(16.dp), tint = Color(0xFFC5A059))
                            }
                        }
                        DropdownMenu(
                            expanded = pExpanded, 
                            onDismissRequest = { pExpanded = false },
                            modifier = Modifier.background(if(isDarkMode) Color(0xFF1E1E1E) else Color.White, RoundedCornerShape(20.dp)),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            (1..14).forEach { n -> 
                                DropdownMenuItem(
                                    text = { Text("$n " + (if(isAr) "أفراد" else "Pax"), color = Color(0xFFC5A059), fontWeight = FontWeight.Bold) },
                                    onClick = { passengers = n.toString(); pExpanded = false }
                                ) 
                            }
                        }
                    }
                }

                if (rideType == "PRIVATE") {
                    Spacer(Modifier.height(10.dp))
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(vehicleList) { v ->
                                VehicleDesignCardInside(
                                    vehicle = v,
                                    isSelected = vehicleType == v.name,
                                    isDarkMode = isDarkMode,
                                    onClick = { vehicleType = v.name }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Date & Time Card
                Surface(
                    onClick = { showDateTimePickerDialog = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = if (isDarkMode) Color(0xFF1E1E1E) else Color.White,
                    border = BorderStroke(1.dp, Color(0xFFC5A059).copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CalendarMonth, null, tint = Color(0xFFC5A059), modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("$selectedDate  $selectedTime", fontSize = 14.sp, color = if(isDarkMode) Color.White else Color.DarkGray, fontWeight = FontWeight.Bold)
                        }
                        Icon(Icons.Default.UnfoldMore, null, tint = Color(0xFFC5A059), modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Search Bar ("ابحث عن وجهتك")
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(if(isAr) "ابحث عن وجهتك..." else "Search destination...", fontSize = 13.sp, color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Color(0xFFC5A059)) },
                    trailingIcon = { Icon(Icons.Default.Mic, null, tint = Color(0xFFC5A059)) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = if(isDarkMode) Color(0xFF1E1E1E) else Color.White,
                        unfocusedContainerColor = if(isDarkMode) Color(0xFF1E1E1E) else Color.White,
                        focusedBorderColor = Color(0xFFC5A059),
                        unfocusedBorderColor = Color(0xFFC5A059).copy(alpha = 0.4f)
                    )
                )

                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if(isAr) "أكثر الوجهات المحُددة" else "Most Popular Destinations", fontWeight = FontWeight.Black, fontSize = 15.sp, color = textColor)
                }
            }

            Box(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Column(Modifier.fillMaxSize()) {
                    if (isLoadingLines) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFFC5A059)) }
                    } else {
                        val filteredDestinations = remember(destinations, user, searchQuery) {
                            val userProvince = user?.province ?: ""
                            var list = if (userProvince.isEmpty()) destinations
                            else destinations.filter { it.province.isEmpty() || it.province.contains(userProvince) || userProvince.contains(it.province) }
                            if (searchQuery.isNotBlank()) {
                                list = list.filter { it.name.contains(searchQuery, ignoreCase = true) || it.details.contains(searchQuery, ignoreCase = true) || it.city.contains(searchQuery, ignoreCase = true) }
                            }
                            list.sortedBy { it.priority }
                        }

                        if (filteredDestinations.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(if (isAr) "لا توجد نتائج مطابقة لبحثك" else "No matching destinations found", color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
                            }
                        } else {
                            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(filteredDestinations) { dest ->
                                    val isSelected = destinationName == dest.name
                                    val estDistKm = if(dest.price > 0) (dest.price * 1.2).roundToInt() else 12
                                    val estTimeMin = maxOf(10, (estDistKm * 2))

                                    Card(
                                        modifier = Modifier.fillMaxWidth().clickable { destinationName = dest.name },
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = if(isSelected) Color(0xFFC5A059) else if(isDarkMode) Color(0xFF1E1E1E) else Color.White),
                                        border = if(isSelected) BorderStroke(1.5.dp, Color(0xFFB8860B)) else BorderStroke(1.dp, Color(0xFFC5A059).copy(0.25f)),
                                        elevation = CardDefaults.cardElevation(2.dp)
                                    ) {
                                        Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                Surface(color = if(isSelected) Color.White.copy(0.25f) else Color(0xFFC5A059).copy(0.12f), shape = CircleShape, modifier = Modifier.size(40.dp)) {
                                                    Box(contentAlignment = Alignment.Center) { Icon(if(dest.type == "SCHOOL") Icons.Default.School else Icons.Default.LocationOn, null, tint = if(isSelected) Color.White else Color(0xFFC5A059), modifier = Modifier.size(20.dp)) }
                                                }
                                                Spacer(Modifier.width(12.dp))
                                                Column {
                                                    Text(dest.name, fontWeight = FontWeight.Black, fontSize = 14.sp, color = if(isSelected) Color.Black else (if(isDarkMode) Color.White else Color.Black))
                                                    Spacer(Modifier.height(2.dp))
                                                    Text(
                                                        text = if (dest.province.isNotEmpty()) "${dest.province} - ${dest.city}" else dest.city.ifBlank { "دمياط الجديدة" }, 
                                                        fontSize = 11.sp, 
                                                        color = if(isSelected) Color.Black.copy(0.7f) else Color.Gray, 
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Spacer(Modifier.height(2.dp))
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(Icons.Default.AccessTime, null, tint = if(isSelected) Color.Black.copy(0.7f) else Color(0xFFC5A059), modifier = Modifier.size(12.dp))
                                                        Spacer(Modifier.width(4.dp))
                                                        Text("$estTimeMin دقيقة  |  $estDistKm كم", fontSize = 11.sp, color = if(isSelected) Color.Black.copy(0.8f) else Color(0xFFC5A059), fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.ChevronRight, null, tint = if(isSelected) Color.Black else Color(0xFFC5A059), modifier = Modifier.size(22.dp))
                                                if (dest.imageUrl.isNotBlank()) {
                                                    Spacer(Modifier.width(8.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(12.dp),
                                                        modifier = Modifier.size(54.dp),
                                                        border = BorderStroke(1.dp, Color(0xFFC5A059).copy(alpha = 0.4f))
                                                    ) {
                                                        com.albasel.tawsilaapp.util.AppImageView(
                                                            data = dest.imageUrl,
                                                            modifier = Modifier.fillMaxSize(),
                                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }

            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp).padding(bottom = 64.dp)) {
                val isSuspended = user?.isApproved == false
                val selectedDestPrice = remember(destinationName, destinations) { destinations.find { it.name == destinationName }?.price ?: 0.0 }
                val gradient = Brush.horizontalGradient(colors = listOf(Color(0xFFE6AF2E), Color(0xFFD4AF37), Color(0xFFC5A059)))

                Surface(
                    onClick = {
                        if (destinationName.isBlank()) { Toast.makeText(context, if(isAr) "يرجى اختيار الوجهة" else "Select destination", Toast.LENGTH_SHORT).show(); return@Surface }
                        if (hasBlockingOrder) {
                            Toast.makeText(context, if(isAr) "عذراً، لديك طلب قيد الانتظار أو القبول حالياً" else "You already have a pending/accepted order", Toast.LENGTH_LONG).show()
                            return@Surface
                        }
                        isLoading = true
                        scope.launch {
                            val selectedDest = destinations.find { it.name == destinationName }
                            if (selectedDest == null) { isLoading = false; return@launch }
                            val lineName = selectedDest.name
                            val rawDetails = selectedDest.details
                            val startPoint = if (rawDetails.contains(" - ")) rawDetails.split(" - ").first().trim() else (if(isAr) "بداية الخط" else "Start of line")
                            val endPoint = if (rawDetails.contains(" - ")) rawDetails.split(" - ").last().trim().removeSuffix(".") else selectedDest.name
                            finalPickupName = "$lineName - $startPoint"
                            finalDestName = "$lineName - $endPoint"
                            pickupCoord = userLat to userLng
                            
                            // Fast Geocoding with timeout for instant response
                            if (destCoord == null) {
                                try {
                                    kotlinx.coroutines.withTimeout(500) {
                                        destCoord = geocodeAddress(context, "$endPoint ${selectedDest.city} ${selectedDest.province}")
                                    }
                                } catch (e: Exception) { destCoord = null }
                            }
                            calculatedDistValue = if (pickupCoord != null && destCoord != null) calculateDist(pickupCoord!!.first, pickupCoord!!.second, destCoord!!.first, destCoord!!.second) else 0.0
                            isLoading = false; showConfirmDialog = true
                        }
                    },
                    enabled = !isSuspended && selectedDestPrice > 0,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(54.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = Color.Transparent,
                ) {
                    Box(modifier = Modifier.fillMaxSize().background(if(isSuspended || selectedDestPrice <= 0) SolidColor(Color.Gray.copy(0.5f)) else gradient), contentAlignment = Alignment.Center) {
                        if (isLoading) { CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp)) }
                        else {
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                Icon(Icons.Default.MenuBook, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                val btnText = if(isAr) (if(isSuspended) "الحساب معلق" else if(selectedDestPrice <= 0) "اختر وجهة على الخريطة" else "احجز الآن") else (if(isSuspended) "Account Suspended" else if(selectedDestPrice <= 0) "Select Destination on Map" else "Book Now")
                                Text(btnText, fontWeight = FontWeight.Black, color = Color.Black, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDateTimePickerDialog) {
        AlertDialog(
            onDismissRequest = { showDateTimePickerDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth(0.95f),
            content = {
                Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = if (isDarkMode) Color(0xFF121212) else Color.White), modifier = Modifier.fillMaxWidth()) {
                    com.albasel.tawsilaapp.ui.components.SideBySideDateTimePickerUI(
                        isAr = isAr, 
                        isDarkMode = isDarkMode, 
                        selectedDate = selectedDate, 
                        selectedTime = selectedTime, 
                        showMinutes = rideType == "PRIVATE",
                        onDateChange = { selectedDate = it }, 
                        onTimeChange = { selectedTime = it }, 
                        onConfirm = { showDateTimePickerDialog = false }, 
                        onCancel = { showDateTimePickerDialog = false }
                    )
                }
            }
        )
    }

    if (showConfirmDialog) {
        val initialPrice = remember(destinationName, passengers, vehicleType, rideType, destinations, vehicleList) {
            val selectedDest = destinations.find { it.name == destinationName }
            val unitPrice = selectedDest?.price ?: 10.0
            val raw = if (rideType == "SHARED") {
                (passengers.toIntOrNull() ?: 1) * unitPrice
            } else {
                val selectedV = vehicleList.find { it.name == vehicleType }
                (selectedV?.capacity ?: 4) * unitPrice
            }
            (raw * 10).roundToInt() / 10.0
        }
        val estimatedPrice = remember(initialPrice) { mutableDoubleStateOf(initialPrice) }
        var discountInput by remember { mutableStateOf("") }
        var pointValue by remember { mutableDoubleStateOf(0.0) }

        LaunchedEffect(rideType, passengers, vehicleType, destinationName) {
            pointValue = firebaseManager.getPointValue("USER")
        }

        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(if(isAr) "تأكيد الرحلة" else "Confirm Booking", fontWeight = FontWeight.Black, color = Color(0xFFC5A059)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryItem("📍", (if(isAr) "من: " else "From: ") + finalPickupName, isDarkMode)
                    SummaryItem("🏁", (if(isAr) "إلى: " else "To: ") + finalDestName, isDarkMode)
                    if (rideType == "SHARED") SummaryItem("👥", (if(isAr) "عدد الأفراد: " else "Passengers: ") + passengers, isDarkMode)
                    else SummaryItem("🚗", (if(isAr) "نوع المركبة: " else "Vehicle: ") + vehicleType, isDarkMode)
                    SummaryItem("📅", "$selectedDate | $selectedTime", isDarkMode)
                    
                    val userPoints = user?.points ?: 0.0
                    val isDebt = userPoints < 0
                    val currentPoints = user?.points ?: 0.0
                    val hasDebt = currentPoints < 0
                    LaunchedEffect(user?.points, pointValue) { if (hasDebt && pointValue > 0) discountInput = String.format(Locale.ENGLISH, "-%.1f", -currentPoints * pointValue) }

                    if (user != null) {
                        val userWalletBalanceEgp = maxOf(0.0, userPoints * pointValue)
                        val walletBalanceFormatted = String.format(Locale.ENGLISH, "%.1f", userWalletBalanceEgp)

                        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = if(isDarkMode) Color.DarkGray else Color.LightGray)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = if(isAr) "استخدام نقاط المحفظة " else "Use wallet points ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if(hasDebt) Color.Red else textColor)
                            Text(text = "(رصيد المحفظة: $walletBalanceFormatted ج.م)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                        }
                        OutlinedTextField(
                            value = discountInput,
                            onValueChange = { if (!hasDebt) { val amount = it.toDoubleOrNull() ?: 0.0; if(amount <= (userPoints * pointValue)) discountInput = it } },
                            readOnly = hasDebt,
                            modifier = Modifier.fillMaxWidth().height(55.dp),
                            placeholder = { Text(if(isAr) "أدخل المبلغ المستقطع" else "Enter amount", fontSize = 12.sp) },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = if(hasDebt) Color.Red else textColor, unfocusedTextColor = if(hasDebt) Color.Red else textColor, cursorColor = Color(0xFFC5A059), focusedBorderColor = Color(0xFFC5A059), unfocusedBorderColor = if(isDarkMode) Color.DarkGray else Color.LightGray)
                        )
                    }
                    val finalPrice = max(0.0, estimatedPrice.doubleValue - (discountInput.toDoubleOrNull() ?: 0.0))
                    SummaryItem("💰", (if(isAr) "السعر النهائي: " else "Final Price: ") + "${finalPrice} ج.م", isDarkMode, isHighlight = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val discountAmount = discountInput.toDoubleOrNull() ?: 0.0
                        val userPoints = user?.points ?: 0.0
                        val isDebt = userPoints < 0
                        val debtAmount = if(isDebt) -userPoints * pointValue else 0.0
                        showConfirmDialog = false; isLoading = true
                        scope.launch {
                            val userPhone = user?.phone ?: ""
                            if (firebaseManager.isPhoneBlocked(userPhone)) { Toast.makeText(context, "تم حظر حسابك", Toast.LENGTH_LONG).show(); delay(2000); (context as? android.app.Activity)?.finishAffinity(); return@launch }
                            if (!isDebt && discountAmount > 0 && user != null) {
                                launch(Dispatchers.IO) {
                                    val pts = discountAmount / pointValue
                                    firebaseManager.incrementUserPoints(user.id, -pts)
                                    val tidU = firebaseManager.database.child("users").child(user.id).child("point_transactions").push().key ?: UUID.randomUUID().toString()
                                    val pTransU = PointTransaction(tidU, -pts, "(عميل) خصم نقاط لاستخدامها بالرحلة", System.currentTimeMillis())
                                    firebaseManager.database.child("users").child(user.id).child("point_transactions").child(tidU).setValue(pTransU)
                                }
                            }
                            
                            val selectedDest = destinations.find { it.name == destinationName }
                            val orderProv = selectedDest?.province ?: currentProvince
                            val finalPrice = max(0.0, estimatedPrice.doubleValue - discountAmount + debtAmount)
                            val order = Order(
                                pickupName = finalPickupName, pickupLat = pickupCoord?.first ?: 0.0, pickupLng = pickupCoord?.second ?: 0.0, 
                                destinationName = finalDestName, destinationLat = destCoord?.first ?: 0.0, destinationLng = destCoord?.second ?: 0.0, 
                                date = selectedDate, time = selectedTime, 
                                passengers = if(rideType == "SHARED") (passengers.toIntOrNull() ?: 1) else (vehicleList.find { it.name == vehicleType }?.capacity ?: 4), 
                                vehicleType = if(rideType == "SHARED") (if(isAr) "توصيل داخل المدينة" else "City Ride") else vehicleType, 
                                price = finalPrice, pointsDiscount = if(isDebt) -discountAmount else discountAmount, 
                                userId = user?.id ?: "", userName = user?.name ?: "", userPhone = userPhone, 
                                status = "PENDING", rideType = rideType, pickupProvince = orderProv, timestamp = System.currentTimeMillis(), distance = calculatedDistValue, trafficSpeedLimitKmh = 40.0
                            )
                            firebaseManager.placeOrder(order)
                            isLoading = false
                            Toast.makeText(context, if(isAr) "تم إرسال طلبك بنجاح!" else "Order sent successfully!", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC5A059), contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(if(isAr) "تأكيد" else "Confirm", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showConfirmDialog = false }) { Text(if(isAr) "تعديل" else "Edit", color = Color(0xFFC5A059)) } },
            shape = RoundedCornerShape(28.dp),
            containerColor = if(isDarkMode) Color(0xFF1E1E1E) else Color.White
        )
    }
}

@Composable
fun SummaryItem(icon: String, text: String, isDarkMode: Boolean, isHighlight: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 16.sp)
        Spacer(Modifier.width(12.dp))
        Text(text = text, fontSize = if(isHighlight) 18.sp else 14.sp, fontWeight = if(isHighlight) FontWeight.Black else FontWeight.Medium, color = if(isHighlight) Color(0xFFC5A059) else if(isDarkMode) Color.LightGray else Color.DarkGray)
    }
}

@Composable
fun RideTypeButtonInside(label: String, isSelected: Boolean, isDarkMode: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, 
        modifier = modifier.height(44.dp), 
        shape = RoundedCornerShape(24.dp), 
        color = if(isSelected) Color(0xFFC5A059) else if(isDarkMode) Color(0xFF1E1E1E) else Color.White,
        border = if(!isSelected) BorderStroke(1.dp, if(isDarkMode) Color.DarkGray else Color.LightGray.copy(0.3f)) else null,
        shadowElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
                Icon(imageVector = if(label.contains("خاص")) Icons.Default.Person else Icons.Default.Groups, contentDescription = null, tint = if(isSelected) Color.Black else Color(0xFFC5A059), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(text = label, color = if(isSelected) Color.Black else Color(0xFFC5A059), fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun VehicleDesignCardInside(
    vehicle: VehicleInfoDataInside,
    isSelected: Boolean,
    isDarkMode: Boolean,
    onClick: () -> Unit
) {
    Box(modifier = Modifier.width(160.dp).height(150.dp).padding(4.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp, start = 8.dp, end = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Text(
                        text = "${vehicle.capacity}",
                        fontSize = 12.sp,
                        color = if(isSelected) Color(0xFFC5A059) else Color.Gray,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        Icons.Default.Person,
                        null,
                        modifier = Modifier.size(14.dp),
                        tint = if(isSelected) Color(0xFFC5A059) else Color.Gray
                    )
                }

                Text(
                    text = vehicle.name,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = if(isSelected) Color(0xFFC5A059) else (if(isDarkMode) Color.White else Color.Black),
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Image(
                painter = painterResource(vehicle.iconRes),
                contentDescription = null,
                modifier = Modifier
                    .size(135.dp)
                    .align(Alignment.Center)
                    .padding(top = 18.dp)
            )
        }
    }
}
