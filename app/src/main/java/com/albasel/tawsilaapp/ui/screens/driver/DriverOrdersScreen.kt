package com.albasel.tawsilaapp.ui.screens.driver

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.albasel.tawsilaapp.data.firebase.FirebaseManager
import com.albasel.tawsilaapp.data.model.Message
import com.albasel.tawsilaapp.data.model.Order
import com.albasel.tawsilaapp.data.model.User
import com.albasel.tawsilaapp.util.VehicleUtils.cleanVehicleName
import com.albasel.tawsilaapp.util.VehicleUtils.isCargoOnlyVehicle
import org.maplibre.android.geometry.LatLng
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverOrdersScreen(
    firebaseManager: FirebaseManager, 
    driver: User, 
    language: String, 
    initialTab: Int = 0,
    initialOrderId: String? = null,
    onChatClick: (String) -> Unit = {},
    onTrackClick: (Double, Double, String, String?) -> Unit = { _, _, _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isAr = language == "AR"
    
    var pendingOrders by remember { mutableStateOf(emptyList<Order>()) }
    var reservedOrders by remember { mutableStateOf(emptyList<Order>()) }
    var isLoading by remember { mutableStateOf(true) }
    
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    val activeSorts = remember { mutableStateListOf<String>("MY_LOCATION") } // Default sort by "My Location" active

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    fun refresh(showLoader: Boolean = false) {
        scope.launch {
            if (showLoader) isLoading = true
            
            val allPending = try { firebaseManager.getPendingOrders() } catch(e: Exception) { emptyList() }
            val vPrices = try { firebaseManager.getVehiclePricesDetail() } catch(e: Exception) { emptyMap() }
            
            pendingOrders = allPending.filter { 
                try {
                    val vOrder = cleanVehicleName((it.vehicleType ?: "").trim().lowercase())
                    val vDriver = cleanVehicleName((driver.vehicleType ?: "").trim().lowercase())
                    
                    val isCityRide = vOrder.contains("ride") || vOrder.contains("توصيل داخل")
                    val driverTypeInfo = vPrices[driver.vehicleType] ?: vPrices[vDriver]
                    
                    val canShowCityRide = driverTypeInfo?.showInInsideCity ?: true
                    val canShowCargo = driverTypeInfo?.showInCargo ?: true
                    val canShowHome = driverTypeInfo?.showInHome ?: true

                    val orderGender = it.gender ?: "ANY"
                    val shouldShowByGender = if (it.isCargo || isCargoOnlyVehicle(vOrder)) true 
                                             else orderGender == "ANY" || orderGender == driver.gender

                    if (isCityRide) {
                        val supported = listOf("موتوسيكل", "motorcycle", "توكتوك", "tuk-tuk", "تريسكل", "tricycle", "ملاكي", "private", "تاكسي", "taxi", "فان", "van", "ميكروباص", "microbus", "باص", "bus")
                        driver.canDeliver && canShowCityRide && supported.any { vDriver.contains(cleanVehicleName(it)) || vDriver.contains(it) } && shouldShowByGender
                    } else if (it.isCargo || isCargoOnlyVehicle(vOrder)) {
                        val isVehicleMatch = vOrder == vDriver || vOrder.contains(vDriver) || vDriver.contains(vOrder) ||
                                           (vOrder.contains("موتوسيكل") && vDriver.contains("motorcycle")) ||
                                           (vOrder.contains("motorcycle") && vDriver.contains("موتوسيكل")) ||
                                           (vOrder.contains("دراجة") && vDriver.contains("bike")) ||
                                           (vOrder.contains("bike") && vDriver.contains("دراجة")) ||
                                           (vOrder.contains("فان") && vDriver.contains("van")) ||
                                           (vOrder.contains("van") && vDriver.contains("فان")) ||
                                           (vOrder.contains("ربع") && vDriver.contains("quarter")) ||
                                           (vOrder.contains("نقل") && vDriver.contains("truck"))
                        
                        driver.canCargo && canShowCargo && isVehicleMatch
                    } else if (vOrder.contains("ملاكي") || vOrder.contains("private") || vOrder.contains("تاكسي") || vOrder.contains("taxi")) {
                        val isDriverMalakiOrTaxi = vDriver.contains("ملاكي") || vDriver.contains("private") || 
                                                  vDriver.contains("تاكسي") || vDriver.contains("taxi")
                        driver.canDeliver && canShowHome && isDriverMalakiOrTaxi && shouldShowByGender
                    } else {
                        driver.canDeliver && canShowHome && (vOrder == vDriver) && shouldShowByGender
                    }
                } catch (e: Exception) { false }
            }

            reservedOrders = try { 
                firebaseManager.getDriverReservedOrders(driver.id)
                    .filter { it.status == "ACCEPTED" || it.status == "CONFIRMED" || it.status == "ARRIVED" || it.status == "STARTED" } 
            } catch(e: Exception) { emptyList() }
            
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { 
        refresh(showLoader = true)
        while(true) {
            kotlinx.coroutines.delay(5000)
            refresh(showLoader = false)
        }
    }
    
    // Auto-switch to Cargo tab for restricted drivers
    LaunchedEffect(driver.vehicleType) {
        if (isCargoOnlyVehicle(driver.vehicleType) && (selectedTab == 0 || selectedTab == 1)) {
            selectedTab = 2
        }
    }

    val currentTotalPassengers = reservedOrders.sumOf { it.passengers }
    val maxCapacity = if (driver.busCapacity > 0 && (driver.vehicleType.contains("باص") || driver.vehicleType.contains("Bus")) && !driver.vehicleType.contains("ميكرو") && !driver.vehicleType.contains("Micro")) {
        driver.busCapacity
    } else {
        when {
            driver.vehicleType.contains("van") || driver.vehicleType.contains("فان") -> 7
            driver.vehicleType.contains("taxi") || driver.vehicleType.contains("تاكسي") -> 4
            driver.vehicleType.contains("ملاكي") || driver.vehicleType.contains("Private") -> 4
            driver.vehicleType.contains("microbus") || driver.vehicleType.contains("ميكروباص") -> 14
            else -> 1
        }
    }
    
    val hasConfirmedRide = reservedOrders.any { it.status == "CONFIRMED" || it.status == "STARTED" || it.status == "ARRIVED" }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.systemBars)) {
        Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF1F3F5))) {
            Surface(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.12f),
                color = Color.Black,
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
                    Column(modifier = Modifier.align(Alignment.CenterStart)) {
                        Text(
                            if(isAr) "تجميع الأوردرات" else "Orders Hub", 
                            color = Color(0xFFC5A059), fontSize = 18.sp, fontWeight = FontWeight.Black
                        )
                        Text(
                            (if(isAr) "الحمولة الحالية: " else "Load: ") + "$currentTotalPassengers / $maxCapacity", 
                            color = Color.White.copy(0.7f), fontSize = 12.sp
                        )
                    }
                }
            }

            val allTabs = listOf(
                if(isAr) "رحلاتي" else "My Trips",
                if(isAr) "رحلات خاصة" else "Private",
                if(isAr) "رحلات مشتركة" else "Shared",
                if(isAr) "أقرب عميل" else "Nearest Customer",
                if(isAr) "نقل بضائع" else "Cargo"
            )
            
            val isCargoOnly = isCargoOnlyVehicle(driver.vehicleType)
            val visibleIndices = allTabs.indices.filter { !isCargoOnly || (it != 1 && it != 2) }
            val tabIndexForDisplay = visibleIndices.indexOf(selectedTab).coerceAtLeast(0)

            ScrollableTabRow(
                selectedTabIndex = tabIndexForDisplay, 
                containerColor = Color.White, 
                contentColor = Color.Black,
                edgePadding = 16.dp,
                divider = {}
            ) {
                allTabs.forEachIndexed { index, title ->
                    if (visibleIndices.contains(index)) {
                        Tab(selected = selectedTab == index, onClick = { selectedTab = index }) {
                            Text(title, modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.Black) }
            } else {
                val rawOrders = when (selectedTab) {
                    0 -> reservedOrders 
                    1 -> pendingOrders.filter { o -> 
                        val vType = cleanVehicleName(o.vehicleType)
                        val isCargoV = o.isCargo || isCargoOnlyVehicle(vType) || vType.contains("نقل") || vType.contains("بضائع") || vType.contains("cargo")
                        o.rideType == "PRIVATE" && o.driverId.isNullOrEmpty() && !isCargoV 
                    }
                    2 -> pendingOrders.filter { o -> 
                        val vType = cleanVehicleName(o.vehicleType)
                        val isCargoV = o.isCargo || isCargoOnlyVehicle(vType) || vType.contains("نقل") || vType.contains("بضائع") || vType.contains("cargo")
                        o.rideType == "SHARED" && o.driverId.isNullOrEmpty() && !isCargoV 
                    }
                    3 -> pendingOrders.filter { o ->
                        val vType = cleanVehicleName(o.vehicleType)
                        val isCargoV = o.isCargo || isCargoOnlyVehicle(vType) || vType.contains("نقل") || vType.contains("بضائع") || vType.contains("cargo")
                        o.driverId == driver.id && o.status == "PENDING" && !isCargoV
                    }
                    else -> pendingOrders.filter { o -> 
                        val vType = cleanVehicleName(o.vehicleType)
                        val isCargoV = o.isCargo || isCargoOnlyVehicle(vType) || vType.contains("نقل") || vType.contains("بضائع") || vType.contains("cargo")
                        isCargoV && (o.driverId.isNullOrEmpty() || o.driverId == driver.id) 
                    }
                }

                val displayOrders = remember(rawOrders, activeSorts.toList(), driver.province, driver.liveProvince) {
                    var filtered = rawOrders
                    
                    val driverProvClean = driver.province.replace("محافظة", "").trim()
                    val driverLiveCity = driver.liveProvince.replace("محافظة", "").trim()

                    // Rule: City Rides ALWAYS filter by the driver's registered province from their profile
                    filtered = filtered.filter { o ->
                        val vName = cleanVehicleName(o.vehicleType)
                        val isInsideCity = vName.contains("توصيل داخل") || vName.contains("city ride")
                        
                        if (isInsideCity) {
                            val orderProv = o.pickupProvince.replace("محافظة", "").trim()
                            orderProv.contains(driverProvClean) || driverProvClean.contains(orderProv)
                        } else {
                            true // Travel and Cargo use standard optional filters below
                        }
                    }

                    if (activeSorts.contains("LOCATION")) {
                        filtered = filtered.filter { 
                            val orderProv = it.pickupProvince.replace("محافظة", "").trim()
                            if (orderProv.isNotEmpty()) {
                                orderProv.contains(driverProvClean) || driverProvClean.contains(orderProv)
                            } else {
                                it.pickupName.contains(driverProvClean)
                            }
                        }
                    }

                    if (activeSorts.contains("MY_LOCATION")) {
                        filtered = filtered.filter { 
                            val oProv = it.pickupProvince.replace("محافظة", "").trim()
                            val oName = it.pickupName
                            
                            val isCityMatch = driverLiveCity.isNotEmpty() && (driverLiveCity.contains(oProv) || oProv.contains(driverLiveCity) || oName.contains(driverLiveCity))
                            val isRegMatch = driverProvClean.isNotEmpty() && (driverProvClean.contains(oProv) || oProv.contains(driverProvClean) || oName.contains(driverProvClean))
                            
                            val distance = com.albasel.tawsilaapp.util.MapUtils.calculateDistance(
                                driver.latitude, driver.longitude,
                                it.pickupLat, it.pickupLng
                            )
                            isCityMatch || isRegMatch || distance <= 30.0 // Extended range for reliability
                        }
                    }

                    if (activeSorts.isEmpty()) filtered.sortedByDescending { it.timestamp }
                    else {
                        var comparator: Comparator<Order> = compareBy { 0 }
                        activeSorts.forEach { sort ->
                            comparator = when(sort) {
                                "LOCATION", "MY_LOCATION" -> comparator.thenBy { it.pickupName }
                                "DESTINATION" -> comparator.thenBy { it.destinationName }
                                "DATE" -> comparator.thenByDescending { it.date }
                                "TIME" -> comparator.thenByDescending { it.time }
                                else -> comparator
                            }
                        }
                        filtered.sortedWith(comparator)
                    }
                }

                // Auto-scroll to target order
                LaunchedEffect(initialOrderId, displayOrders) {
                    if (!initialOrderId.isNullOrEmpty() && displayOrders.isNotEmpty()) {
                        val idx = displayOrders.indexOfFirst { it.id == initialOrderId }
                        if (idx != -1) {
                            listState.animateScrollToItem(idx)
                        }
                    }
                }

                if (selectedTab == 0 && reservedOrders.any { it.status == "ACCEPTED" }) {
                    Button(
                        onClick = {
                            scope.launch {
                                val ids = reservedOrders.filter { it.status == "ACCEPTED" }.map { it.id }
                                firebaseManager.finalizeOrders(ids, driver.id)
                                refresh()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Text(if(isAr) "تأكيد وبدء الرحلة للمجموعات المختارة" else "Confirm & Start Trip", fontWeight = FontWeight.Bold)
                    }
                }

                if ((selectedTab == 0 || selectedTab == 1 || selectedTab == 2)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if(isAr) "ترتيب:" else "Sort:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val options = listOf(
                                Triple("MY_LOCATION", if(isAr) "موقعي" else "My Location", Icons.Default.MyLocation),
                                Triple("LOCATION", if(isAr) "محافظتي" else "My Province", Icons.Default.LocationOn),
                                Triple("DESTINATION", if(isAr) "الوجهة" else "To", Icons.Default.Flag),
                                Triple("DATE", if(isAr) "التاريخ" else "Date", Icons.Default.CalendarToday),
                                Triple("TIME", if(isAr) "الوقت" else "Time", Icons.Default.Schedule)
                            )
                            items(options) { (id, label, icon) ->
                                val isSelected = activeSorts.contains(id)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { if (isSelected) activeSorts.remove(id) else activeSorts.add(id) },
                                    label = { Text(label, fontSize = 10.sp) },
                                    leadingIcon = { Icon(icon, null, modifier = Modifier.size(14.dp)) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFFC5A059),
                                        selectedLabelColor = Color.Black,
                                        selectedLeadingIconColor = Color.Black
                                    )
                                )
                            }
                        }
                    }
                }

                LazyColumn(state = listState, modifier = Modifier.padding(16.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (displayOrders.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text(if(isAr) "لا توجد أوردرات في هذا القسم" else "No orders here", color = Color.Gray)
                            }
                        }
                    }

                    items(displayOrders) { order ->
                        val hasPrivateRide = reservedOrders.any { it.rideType == "PRIVATE" }
                        val passengersInt = order.passengers
                        
                        val canAcceptPrivate = reservedOrders.isEmpty() && driver.isApproved && passengersInt <= maxCapacity
                        val canLoadShared = !hasPrivateRide && currentTotalPassengers < maxCapacity && !hasConfirmedRide && driver.isApproved
                        
                        OrderCard(
                            order = order, 
                            canLoad = if(order.rideType == "PRIVATE") canAcceptPrivate else canLoadShared, 
                            isReserved = reservedOrders.any { it.id == order.id }, 
                            isPrivateRequest = order.driverId == driver.id,
                            hasConfirmedRide = hasConfirmedRide,
                            hasActivePrivate = hasPrivateRide,
                            isFull = if(order.rideType == "PRIVATE") (passengersInt > maxCapacity) else (currentTotalPassengers >= maxCapacity),
                            language = language, 
                            onAction = { refresh() }, 
                            onChatClick = onChatClick,
                            onTrackClick = onTrackClick,
                            firebaseManager = firebaseManager, 
                            driver = driver,
                            maxCapacity = maxCapacity,
                            currentLoad = currentTotalPassengers,
                            allReservedOrders = reservedOrders
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
fun OrderCard(
    order: Order, 
    canLoad: Boolean, 
    isReserved: Boolean, 
    isPrivateRequest: Boolean, 
    hasConfirmedRide: Boolean,
    hasActivePrivate: Boolean,
    isFull: Boolean,
    language: String, 
    onAction: () -> Unit, 
    onChatClick: (String) -> Unit,
    onTrackClick: (Double, Double, String, String?) -> Unit,
    firebaseManager: FirebaseManager, 
    driver: User,
    maxCapacity: Int,
    currentLoad: Int,
    allReservedOrders: List<Order> = emptyList()
) {
    val scope = rememberCoroutineScope()
    val isAr = language == "AR"
    val context = LocalContext.current
    var isCompleting by remember { mutableStateOf(false) }
    var showUserFeedbackDialog by remember { mutableStateOf(false) }
    
    if (showUserFeedbackDialog) {
        val confirmedPeers = if(order.rideType == "SHARED") {
            allReservedOrders.filter { it.status == "CONFIRMED" || it.status == "STARTED" || it.status == "ARRIVED" }
        } else {
            listOf(order)
        }

        MultiUserFeedbackDialog(
            orders = confirmedPeers,
            isAr = isAr,
            onDismiss = { showUserFeedbackDialog = false },
            onConfirm = { feedbackMap ->
                isCompleting = true
                showUserFeedbackDialog = false
                scope.launch {
                    try {
                        val entries = feedbackMap.entries.toList()
                        entries.forEachIndexed { index, entry ->
                            val (oid, ratingData) = entry
                            val (rating, feedback) = ratingData
                            if (feedback.isNotBlank()) {
                                firebaseManager.rateUser(oid, rating, feedback)
                            }
                            // Award driver points only once for the first order in the shared batch
                            firebaseManager.completeOrder(oid, awardDriverPoints = (index == 0))
                        }
                        onAction()
                    } catch(e: Exception) {
                        isCompleting = false
                    }
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if(isPrivateRequest && !isReserved) Color(0xFFFFF8E1) else Color.White
        ),
        elevation = CardDefaults.cardElevation(if(isPrivateRequest) 6.dp else 2.dp),
        border = if(isPrivateRequest) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC5A059)) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val isCityRide = order.vehicleType.contains("داخلي") || order.vehicleType.contains("Ride")
            if (isCityRide) {
                Surface(
                    color = Color(0xFFE3F2FD),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth()
                ) {
                    Text(
                        if(isAr) "محافظة دمياط - توصيل داخلي" else "City Ride",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = Color(0xFF1976D2),
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Color(0xFFF1F3F5), shape = RoundedCornerShape(8.dp)) {
                    Text(order.vehicleType, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${String.format(Locale.ENGLISH, "%.1f", order.price)} ج.م", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color(0xFF2E7D32))
                    if (order.promoDiscountAmount > 0) {
                        Text(
                            "+ إضافة ${String.format(Locale.ENGLISH, "%.1f", order.promoDiscountAmount)} ج.م (تعويض كود الخصم)",
                            fontSize = 10.sp,
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (order.pointsDiscount != 0.0) {
                        val isRefund = order.pointsDiscount > 0
                        val label = if(isRefund) (if(isAr) "خصم نقاط: " else "Points Disc: ") 
                                    else (if(isAr) "مديونية: " else "Debt: ")
                        val color = if(isRefund) Color(0xFF1976D2) else Color.Red
                        
                        Text(
                            label + (if(order.pointsDiscount < 0) -order.pointsDiscount else order.pointsDiscount).toInt() + " ج.م",
                            fontSize = 10.sp, color = color, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text((if(isAr) "من: " else "From: ") + order.pickupName, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Flag, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text((if(isAr) "إلى: " else "To: ") + order.destinationName, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            
            // Distance Rows
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // 1. Trip Distance
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Straighten, null, tint = Color(0xFFC5A059), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = (if(isAr) "المسافة: " else "Distance: ") + String.format(Locale.ENGLISH, "%.1f كم", order.distance),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFC5A059)
                    )
                }
                
                // 2. Distance to Customer
                if (!order.isCargo) {
                    val driverLat = if (driver.latitude != 0.0) driver.latitude else 31.4522
                    val driverLng = if (driver.longitude != 0.0) driver.longitude else 31.6762

                    val poiMatch = if (order.pickupLat == 0.0 || order.pickupLng == 0.0) {
                        com.albasel.tawsilaapp.util.DamiettaPlaces.getAllPois().firstOrNull { it.nameAr.contains(order.pickupName) || order.pickupName.contains(it.nameAr) }
                    } else null

                    val pLat = if (order.pickupLat != 0.0) order.pickupLat else (poiMatch?.lat ?: 31.4522)
                    val pLng = if (order.pickupLng != 0.0) order.pickupLng else (poiMatch?.lng ?: 31.6762)

                    val distToCustomer = com.albasel.tawsilaapp.util.MapUtils.calculateDistance(driverLat, driverLng, pLat, pLng)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Navigation, null, tint = Color.Red, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = (if(isAr) "المسافة بينك وبين العميل: " else "Distance to Customer: ") + String.format(Locale.ENGLISH, "%.2f كم", distToCustomer),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Red
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, tint = Color(0xFFC5A059), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${order.date} | ${order.time}", 
                        fontSize = 16.sp, // ~1.5x of 11.sp
                        color = Color(0xFFB8860B), // Matte Gold
                        fontWeight = FontWeight.Black
                    )
                }
                if (!order.isCargo) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.People, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("${order.passengers}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            
            if (order.isCargo) {
                Surface(
                    color = Color(0xFF795548).copy(0.1f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth()
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Inventory, null, tint = Color(0xFF795548), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            // Handle both old concatenated string and new clean string
                            val rawDetails = order.cargoDetails ?: (if(isAr) "بدون تفاصيل" else "No details")
                            val cleanDetails = rawDetails.split("|").first().trim()
                            Text("$cleanDetails | ${order.numCartons} " + (if(isAr) "قطعة" else "items"), fontSize = 12.sp, color = Color(0xFF795548), fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val loadText = if(order.needsLoading) (if(isAr) "مع تحميل" else "With Loading") else (if(isAr) "بدون تحميل" else "Without Loading")
                            val unloadText = if(order.needsUnloading) (if(isAr) "مع تفريغ" else "With Unloading") else (if(isAr) "بدون تفريغ" else "Without Unloading")
                            Text("📦 $loadText | $unloadText", fontSize = 11.sp, color = Color.DarkGray)
                            if(order.cargoWaitMinutes > 0) Text("⏳ انتظار: ${order.cargoWaitMinutes} د", fontSize = 11.sp, color = Color.DarkGray)
                        }
                    }
                }
            }
            
            if (isReserved || order.status == "CONFIRMED") {
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Color.Gray.copy(0.1f))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(order.userName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        val isActuallyConfirmed = order.status == "CONFIRMED" || order.status == "ARRIVED" || order.status == "STARTED"
                        if (isActuallyConfirmed) {
                            Text(order.userPhone, color = Color(0xFFC5A059), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        } else {
                            Text(if(isAr) "الرقم متاح بعد التأكيد" else "Phone hidden until confirmation", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                    
                    val isActuallyConfirmed = order.status == "CONFIRMED" || order.status == "ARRIVED" || order.status == "STARTED"
                    if (isActuallyConfirmed) {
                        IconButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${order.userPhone}"))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.background(Color(0xFF4CAF50).copy(0.1f), CircleShape).size(36.dp)
                        ) {
                            Icon(Icons.Default.Phone, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!isReserved) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isPrivateRequest) {
                        Button(
                            onClick = { scope.launch { firebaseManager.rejectOrder(order.id); onAction() } },
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(0.1f), contentColor = Color.Red)
                        ) { Text(if(isAr) "رفض" else "Reject", fontSize = 12.sp) }
                    }
                    Button(
                        onClick = { 
                            scope.launch { 
                                try {
                                    firebaseManager.acceptOrder(order.id, driver.id, maxCapacity, currentLoad)
                                    onAction() 
                                } catch (e: Exception) {
                                    val errorMsg = e.message ?: ""
                                    val arMsg = when {
                                        errorMsg.contains("حجم الركاب") -> errorMsg
                                        errorMsg.contains("السيارة ممتلئة") -> errorMsg
                                        else -> if(isAr) "عذراً، حدث خطأ أثناء قبول الطلب" else "Error accepting order"
                                    }
                                    Toast.makeText(context, arMsg, Toast.LENGTH_LONG).show()
                                }
                            } 
                        },
                        enabled = canLoad,
                        modifier = Modifier.weight(2f).height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC5A059), contentColor = Color.Black)
                    ) {
                        val btnText = when {
                            canLoad -> if(isAr) "قبول الطلب" else "Accept"
                            hasConfirmedRide -> if(isAr) "أنهِ رحلتك الحالية أولاً" else "Finish Current Trip"
                            order.rideType == "PRIVATE" -> if(isAr) "لديك رحلات نشطة" else "Busy"
                            hasActivePrivate -> if(isAr) "لديك رحلة خاصة" else "Has Private Ride"
                            isFull -> if(isAr) "الحمولة مكتملة" else "Full"
                            else -> if(isAr) "غير متاح" else "Unavailable"
                        }
                        Text(
                            text = btnText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (order.status != "ACCEPTED") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            var unreadCount by remember { mutableIntStateOf(0) }
                            val chatId = if (order.rideType == "SHARED" && !order.driverId.isNullOrEmpty() && (order.status == "CONFIRMED" || order.status == "ACCEPTED" || order.status == "ARRIVED" || order.status == "IN_PROGRESS")) "shared_${order.driverId}" else order.id

                            LaunchedEffect(chatId, driver.id) {
                                val ref = firebaseManager.database.child("orders").child(chatId).child("messages")
                                val listener = object : com.google.firebase.database.ValueEventListener {
                                    override fun onDataChange(s: com.google.firebase.database.DataSnapshot) {
                                        unreadCount = s.children.mapNotNull { it.getValue(Message::class.java) }
                                            .count { it.senderId != driver.id && !it.isRead }
                                    }
                                    override fun onCancelled(e: com.google.firebase.database.DatabaseError) {}
                                }
                                ref.addValueEventListener(listener)
                            }

                            Button(
                                onClick = { onChatClick(chatId) },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
                            ) {
                                BadgedBox(badge = { if(unreadCount > 0) Badge { Text("$unreadCount") } }) {
                                    Icon(Icons.AutoMirrored.Filled.Chat, null, tint = Color(0xFFC5A059), modifier = Modifier.size(16.dp))
                                }
                                Spacer(Modifier.width(4.dp))
                                val label = if (order.rideType == "SHARED" && !order.driverId.isNullOrEmpty()) (if(isAr) "محادثة جماعية" else "Group Chat") else (if(isAr) "مراسلة" else "Chat")
                                Text(label, color = Color(0xFFC5A059), fontSize = 10.sp)
                            }

                            Button(
                                onClick = { onTrackClick(order.pickupLat, order.pickupLng, order.pickupName, order.id) },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                            ) {
                                Icon(Icons.Default.PersonPinCircle, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if(isAr) "تتبع العميل" else "Track User", fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                    
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (order.status == "ACCEPTED") {
                            OutlinedButton(
                                onClick = { scope.launch { firebaseManager.undoOrderAcceptance(order.id); onAction() } },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color.Red),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                            ) {
                                Icon(Icons.Default.Undo, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if(isAr) "تراجع" else "Undo", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        if (order.status == "CONFIRMED" || order.status == "ARRIVED" || order.status == "STARTED") {
                            Button(
                                onClick = { 
                                    if (isCompleting) return@Button
                                    showUserFeedbackDialog = true
                                },
                                modifier = Modifier.weight(1.5f).height(44.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                enabled = !isCompleting
                            ) {
                                if (isCompleting) {
                                    androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if(isAr) "تمت الرحلة بنجاح" else "Trip Completed", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun MultiUserFeedbackDialog(
    orders: List<Order>, 
    isAr: Boolean, 
    onDismiss: () -> Unit, 
    onConfirm: (Map<String, Pair<Float, String>>) -> Unit
) {
    val feedbackMap = remember { mutableStateMapOf<String, Pair<Float, String>>().apply {
        orders.forEach { this[it.id] = 5f to "" }
    } }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if(isAr) "إنهاء الرحلة وتقييم العملاء" else "Complete Trip & Rate Users", fontWeight = FontWeight.Black, color = Color(0xFFC5A059)) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                items(orders) { order ->
                    Column(Modifier.padding(vertical = 12.dp)) {
                        Text(order.userName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(Modifier.height(8.dp))
                        
                        val currentData = feedbackMap[order.id] ?: (5f to "")
                        
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            (1..5).forEach { i ->
                                Icon(
                                    if (currentData.first >= i) Icons.Default.Star else Icons.Default.StarOutline,
                                    null,
                                    tint = if (currentData.first >= i) Color(0xFFFFD600) else Color.Gray,
                                    modifier = Modifier.size(28.dp).clickable { 
                                        feedbackMap[order.id] = i.toFloat() to currentData.second
                                    }
                                )
                            }
                        }
                        
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = currentData.second,
                            onValueChange = { feedbackMap[order.id] = currentData.first to it },
                            label = { Text(if(isAr) "ملاحظات على العميل" else "Feedback") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                        )
                        HorizontalDivider(Modifier.padding(top = 16.dp), color = Color.Gray.copy(0.1f))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(feedbackMap.toMap()) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC5A059))) {
                Text(if(isAr) "تأكيد وإنهاء الرحلة" else "Confirm & Finish", color = Color.White)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if(isAr) "إلغاء" else "Cancel") } }
    )
}
