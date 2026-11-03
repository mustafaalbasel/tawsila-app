package com.albasel.tawsilaapp.ui.screens.history

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.albasel.tawsilaapp.data.model.VehiclePrice
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideHistoryScreen(
    firebaseManager: FirebaseManager, 
    user: User?, 
    language: String,
    initialTab: String = "PENDING",
    initialOrderId: String? = null,
    onChatClick: (String) -> Unit = {},
    onTrackClick: (Double, Double, String, String?) -> Unit = { _, _, _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isAr = language == "AR"
    var allOrders by remember { mutableStateOf(emptyList<Order>()) }
    var filterStatus by remember { mutableStateOf(initialTab) }
    var sortBy by remember { mutableStateOf("DATE") } // DATE or LOCATION
    var isLoading by remember { mutableStateOf(true) }
    
    var showDriverDetails by remember { mutableStateOf<User?>(null) }
    var showRatingDialog by remember { mutableStateOf<Order?>(null) }
    var orderToCancelConfirm by remember { mutableStateOf<Order?>(null) }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    fun load() {
        if (user != null) {
            scope.launch {
                val userOrders = firebaseManager.getUserOrders(user.id)
                val driverOrders = firebaseManager.getDriverReservedOrders(user.id)
                allOrders = (userOrders + driverOrders).distinctBy { it.id }
                isLoading = false
            }
        }
    }

    LaunchedEffect(user?.id) { 
        load() 
        while(true) {
            kotlinx.coroutines.delay(10000)
            load()
        }
    }

    val filteredOrders = remember(allOrders, filterStatus, sortBy) {
        val list = if (filterStatus == "PENDING") {
            allOrders.filter { it.status == "PENDING" || it.status == "ACCEPTED" }
        } else {
            allOrders.filter { it.status == "CONFIRMED" || it.status == "ARRIVED" || it.status == "STARTED" || it.status == "COMPLETED" }
        }
        
        when(sortBy) {
            "DATE" -> list.sortedByDescending { it.timestamp }
            "LOCATION" -> list.sortedBy { it.pickupName }
            else -> list.sortedByDescending { it.timestamp }
        }
    }

    LaunchedEffect(initialOrderId, filteredOrders) {
        if (!initialOrderId.isNullOrEmpty() && filteredOrders.isNotEmpty()) {
            val idx = filteredOrders.indexOfFirst { it.id == initialOrderId }
            if (idx != -1) {
                listState.animateScrollToItem(idx)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.systemBars)) {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Surface(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.12f), 
                color = Color.Black, 
                shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp), 
                shadowElevation = 12.dp
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if(isAr) "سجل الرحلات" else "Ride History", 
                        color = Color(0xFFC5A059), 
                        fontSize = 22.sp, 
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }

            ScrollableTabRow(
                selectedTabIndex = listOf("PENDING", "CONFIRMED").indexOf(filterStatus).coerceAtLeast(0),
                containerColor = Color.Transparent,
                contentColor = Color(0xFFC5A059),
                edgePadding = 16.dp,
                divider = {}
            ) {
                val statusList = listOf(
                    (if(isAr) "قيد الانتظار" else "PENDING") to "PENDING",
                    (if(isAr) "مؤكدة" else "CONFIRMED") to "CONFIRMED"
                )
                statusList.forEach { (title, status) ->
                    Tab(selected = filterStatus == status, onClick = { filterStatus = status }, text = { Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold) })
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if(isAr) "ترتيب حسب:" else "Sort by:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                FilterChip(
                    selected = sortBy == "DATE",
                    onClick = { sortBy = "DATE" },
                    label = { Text(if(isAr) "التاريخ" else "Date", fontSize = 10.sp) },
                    leadingIcon = { Icon(Icons.Default.Schedule, null, Modifier.size(14.dp)) }
                )
                FilterChip(
                    selected = sortBy == "LOCATION",
                    onClick = { sortBy = "LOCATION" },
                    label = { Text(if(isAr) "الموقع" else "Location", fontSize = 10.sp) },
                    leadingIcon = { Icon(Icons.Default.LocationOn, null, Modifier.size(14.dp)) }
                )
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFFC5A059)) }
            } else if (filteredOrders.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = if(isAr) "لا توجد رحلات حالياً" else "No rides found", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.padding(horizontal = 16.dp).weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp)
                ) {
                    items(filteredOrders) { order ->
                        val isActuallyDriver = order.driverId == user?.id
                        HistoryCard(
                            order = order, 
                            isAr = isAr, 
                            isDriver = isActuallyDriver,
                            currentUser = user,
                            onChatClick = onChatClick,
                            onTrackClick = onTrackClick,
                            onDetailsClick = { 
                                scope.launch {
                                    order.driverId?.let { showDriverDetails = firebaseManager.getUser(it) }
                                }
                            },
                            onRateClick = { showRatingDialog = order },
                            onDelete = { load() },
                            onCancelClick = { orderToCancelConfirm = order },
                            onConfirmTrip = { 
                                scope.launch {
                                    firebaseManager.finalizeOrders(listOf(order.id), order.driverId ?: "")
                                    load()
                                }
                            },
                            firebaseManager = firebaseManager
                        )
                    }
                }
            }
        }
    }

    if (orderToCancelConfirm != null) {
        val o = orderToCancelConfirm!!
        val isConfirmed = o.status == "CONFIRMED"
        AlertDialog(
            onDismissRequest = { orderToCancelConfirm = null },
            title = { Text(if(isAr) "تأكيد الإلغاء" else "Confirm Cancellation", fontWeight = FontWeight.Bold) },
            text = { 
                Text(
                    if(isConfirmed) {
                        if(isAr) "سيتم خصم مبلغ فتح العداد من محفظتك. هل أنت متأكد؟" else "Meter opening fee will be deducted from your wallet. Are you sure?"
                    } else {
                        if(isAr) "هل أنت متأكد من إلغاء الطلب؟" else "Are you sure you want to cancel?"
                    }
                ) 
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toCancel = o
                        orderToCancelConfirm = null
                        scope.launch {
                            try {
                                if (toCancel.status == "CONFIRMED") {
                                    firebaseManager.cancelOrderWithCompensation(toCancel, "إلغاء رحلة مؤكدة من العميل")
                                } else {
                                    firebaseManager.deleteOrder(toCancel.id)
                                }
                                load()
                            } catch(e: Exception) {}
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text(if(isAr) "نعم" else "Yes") }
            },
            dismissButton = { 
                TextButton(onClick = { orderToCancelConfirm = null }) { 
                    Text(if(isAr) "لا" else "No", color = Color.Gray) 
                } 
            }
        )
    }

    if (showRatingDialog != null) {
        RatingDialog(
            isAr = isAr,
            onDismiss = { showRatingDialog = null },
            onConfirm = { rating, feedback ->
                scope.launch {
                    firebaseManager.rateOrder(showRatingDialog!!.id, rating, feedback)
                    showRatingDialog = null
                    load()
                    Toast.makeText(context, if(isAr) "شكراً لتقييمك" else "Thanks for rating", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showDriverDetails != null) {
        val driver = showDriverDetails!!
        AlertDialog(
            onDismissRequest = { showDriverDetails = null },
            title = { 
                Text(
                    text = if(isAr) "بيانات السائق والمركبة" else "Driver & Vehicle Details", 
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFC5A059)
                ) 
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(driver.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(driver.phone, color = Color.Gray, fontSize = 16.sp)
                    
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = Color.LightGray.copy(0.5f))
                    Spacer(Modifier.height(16.dp))
                    
                    DetailRow(if(isAr) "نوع المركبة:" else "Vehicle:", driver.vehicleType, isAr)
                    DetailRow(if(isAr) "رقم اللوحة:" else "Plate:", driver.vehiclePlate, isAr)
                    DetailRow(if(isAr) "لون المركبة:" else "Color:", driver.vehicleColor, isAr)

                    Spacer(Modifier.height(24.dp))
                    
                    Button(
                        onClick = { 
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${driver.phone}"))
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
            },
            confirmButton = { 
                TextButton(onClick = { showDriverDetails = null }) { 
                    Text(if(isAr) "إغلاق" else "Close", color = Color.Gray) 
                } 
            }
        )
    }
}

@Composable
fun DetailRow(label: String, value: String, isAr: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontWeight = FontWeight.Medium, color = Color.Gray, fontSize = 13.sp)
        Text(text = value, fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 13.sp)
    }
}

@Composable
fun HistoryCard(
    order: Order, 
    isAr: Boolean, 
    isDriver: Boolean, 
    currentUser: User?,
    onChatClick: (String) -> Unit,
    onTrackClick: (Double, Double, String, String?) -> Unit,
    onDetailsClick: () -> Unit,
    onRateClick: () -> Unit,
    onDelete: () -> Unit,
    onCancelClick: () -> Unit = {},
    onConfirmTrip: () -> Unit = {},
    firebaseManager: FirebaseManager
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(4.dp),
        border = if(order.status == "CONFIRMED" || order.status == "ACCEPTED") BorderStroke(1.dp, Color(0xFFC5A059)) else null
    ) {
        Column(Modifier.padding(20.dp)) {
            val isActuallyDriver = order.driverId == firebaseManager.getCurrentUserId()
            val roleLabel = if(isActuallyDriver) (if(isAr) "دورك: سائق" else "Role: Driver")
                            else (if(isAr) "دورك: عميل" else "Role: Customer")
            val roleColor = if(isActuallyDriver) Color(0xFFC5A059) else Color(0xFF2196F3)
            
            Surface(
                color = roleColor.copy(0.1f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Text(
                    text = roleLabel,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    color = roleColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
            }

            if (isActuallyDriver && order.status == "ACCEPTED") {
                Button(
                    onClick = onConfirmTrip,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(if(isAr) "تأكيد وبدء الرحلة الآن" else "Confirm & Start Trip", fontWeight = FontWeight.Bold)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Color.Gray.copy(0.1f), shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Event, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(order.date, fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(order.status, isAr)
                }
            }
            
            Spacer(Modifier.height(20.dp))

            Row(Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 4.dp)) {
                    Box(Modifier.size(10.dp).background(Color(0xFF4CAF50), CircleShape))
                    Box(Modifier.width(2.dp).height(35.dp).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF4CAF50), Color.Red))))
                    Box(Modifier.size(10.dp).background(Color.Red, CircleShape))
                }
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text(if(isAr) "نقطة الانطلاق" else "Pickup", fontSize = 10.sp, color = Color.Gray)
                        Text(order.pickupName, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    Column {
                        Text(if(isAr) "وجهة الوصول" else "Destination", fontSize = 10.sp, color = Color.Gray)
                        Text(order.destinationName, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }

            if (order.isCargo) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Color.LightGray.copy(0.2f))
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val rawDetails = order.cargoDetails ?: (if(isAr) "بدون تفاصيل" else "No details")
                    val cleanDetails = rawDetails.split("|").first().trim()
                    
                    Text(
                        text = "📦 $cleanDetails | ${order.numCartons} " + (if(isAr) "قطعة" else "items"),
                        fontSize = 11.sp, color = Color.DarkGray, fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val loadText = if(order.needsLoading) (if(isAr) "مع تحميل" else "With Loading") else (if(isAr) "بدون تحميل" else "Without Loading")
                        val unloadText = if(order.needsUnloading) (if(isAr) "مع تفريغ" else "With Unloading") else (if(isAr) "بدون تفريغ" else "Without Unloading")
                        Text("✅ $loadText | $unloadText", fontSize = 10.sp, color = Color(0xFF2E7D32))
                        if(order.cargoWaitMinutes > 0) Text("⏱️ " + (if(isAr) "انتظار: " else "Wait: ") + "${order.cargoWaitMinutes} د", fontSize = 10.sp, color = Color.Gray)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = Color.LightGray.copy(0.3f))
            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(order.time, fontSize = 12.sp, color = Color.Gray)
                }

                if (order.isCargo) {
                    Surface(color = Color(0xFF795548).copy(0.1f), shape = RoundedCornerShape(8.dp)) {
                        val displayW = if(order.cargoWeight > 0) order.cargoWeight.toInt() else 0
                        Text(
                            text = (if(isAr) "حتى $displayW كجم" else "Up to $displayW kg"),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF795548)
                        )
                    }
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${order.price.toInt()} ج.م",
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        color = Color(0xFF2E7D32)
                    )
                    if (order.tollCount > 0) {
                        Text(
                            text = (if(isAr) "الكارتة: " else "Tolls: ") + order.tollNames,
                            fontSize = 10.sp, color = Color(0xFF1976D2), fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End
                        )
                    }
                    if (order.pointsDiscount != 0.0) {
                        val isRefund = order.pointsDiscount > 0
                        val label = if(isActuallyDriver) (if(isRefund) (if(isAr) "استرداد: " else "Refund: ") else (if(isAr) "خصم: " else "Disc: ")) 
                                    else (if(isRefund) (if(isAr) "خصم: " else "Disc: ") else (if(isAr) "مديونية: " else "Debt: "))
                        val color = if(!isActuallyDriver && !isRefund) Color.Red else if(isActuallyDriver && isRefund) Color(0xFF1976D2) else Color(0xFFD32F2F)

                        Text(
                            text = label + (if(order.pointsDiscount < 0) -order.pointsDiscount else order.pointsDiscount).toInt() + " ج.م",
                            fontSize = 11.sp, color = color, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if ((order.status == "PENDING" || order.status == "ACCEPTED" || order.status == "CONFIRMED") && !isActuallyDriver) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onCancelClick,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(0.1f), contentColor = Color.Red)
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if(isAr) "إلغاء الطلب" else "Cancel Order", fontWeight = FontWeight.Bold)
                }
            }

            if (order.status == "CONFIRMED" || order.status == "ACCEPTED" || order.status == "ARRIVED" || order.status == "STARTED" || order.status == "COMPLETED" || (order.status == "PENDING" && order.driverId != null)) {
                Spacer(Modifier.height(20.dp))
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)), shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        val name = if(isActuallyDriver) order.userName else (order.driverName ?: (if(isAr) "جاري البحث..." else "Searching..."))
                        val phone = if(isActuallyDriver) order.userPhone else (order.driverPhone ?: "")
                        
                        Box(Modifier.size(40.dp).background(Color(0xFFC5A059).copy(0.2f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(if(isActuallyDriver) Icons.Default.Person else Icons.Default.LocalTaxi, null, tint = Color(0xFFC5A059), modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            
                            val showFullDetails = (order.status == "CONFIRMED" || order.status == "ARRIVED" || order.status == "STARTED")

                            if (showFullDetails) {
                                if(phone.isNotEmpty()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(phone, fontSize = 11.sp, color = Color.Gray)
                                        Spacer(Modifier.width(8.dp))
                                        IconButton(
                                            onClick = {
                                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                                                context.startActivity(intent)
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Phone, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                                
                                if (!isActuallyDriver && !order.vehiclePlate.isNullOrEmpty()) {
                                    Text(
                                        text = (if(isAr) "لوحة: " else "Plate: ") + order.vehiclePlate + " | " + (if(isAr) "لون: " else "Color: ") + (order.vehicleColor ?: ""),
                                        fontSize = 10.sp, 
                                        fontWeight = FontWeight.Bold, 
                                        color = Color(0xFFC5A059)
                                    )
                                }
                            } else if (order.status == "COMPLETED") {
                                Text(if(isAr) "تمت الرحلة" else "Trip Completed", fontSize = 10.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                            } else {
                                Text(if(isAr) "البيانات مخفية حتى التأكيد" else "Details hidden until confirmation", fontSize = 10.sp, color = Color.Gray)
                            }
                        }
                        
                        if (!isActuallyDriver && order.driverId != null && (order.status == "CONFIRMED" || order.status == "ARRIVED" || order.status == "STARTED")) {
                            IconButton(onClick = onDetailsClick, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Info, null, tint = Color(0xFFC5A059), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }

                val showActionButtons = if(isActuallyDriver) {
                    order.status != "COMPLETED" && order.status != "PENDING"
                } else {
                    order.status == "CONFIRMED" || order.status == "ARRIVED" || order.status == "STARTED"
                }

                if (showActionButtons) {
                    Spacer(Modifier.height(16.dp))

                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!isActuallyDriver && order.isCargo) {
                            val isDriverAtPickup = order.driverArrivalTime > 0L
                            val isDriverAtDest = order.destinationArrivalTime > 0L

                            if (order.needsLoading && order.loadingEndTime == 0L && isDriverAtPickup && !isDriverAtDest) {
                                Button(
                                    onClick = { scope.launch { firebaseManager.updateOrderEvent(order.id, "loadingEndTime") } },
                                    modifier = Modifier.fillMaxWidth().height(48.dp).padding(bottom = 8.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC5A059), contentColor = Color.Black)
                                ) {
                                    Icon(Icons.Default.Inventory, null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(if(isAr) "تم التحميل" else "Done Loading", fontWeight = FontWeight.Bold)
                                }
                            }

                            if (order.needsUnloading && order.unloadingEndTime == 0L && isDriverAtDest) {
                                Button(
                                    onClick = { scope.launch { firebaseManager.updateOrderEvent(order.id, "unloadingEndTime") } },
                                    modifier = Modifier.fillMaxWidth().height(48.dp).padding(bottom = 8.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC5A059), contentColor = Color.Black)
                                ) {
                                    Icon(Icons.Default.Inventory, null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(if(isAr) "تم التفريغ" else "Done Unloading", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            var unreadCount by remember { mutableIntStateOf(0) }
                            val chatId = if (order.rideType == "SHARED" && !order.driverId.isNullOrEmpty() && (order.status == "CONFIRMED" || order.status == "ACCEPTED" || order.status == "ARRIVED" || order.status == "IN_PROGRESS")) "shared_${order.driverId}" else order.id

                            LaunchedEffect(chatId, currentUser?.id) {
                                if (currentUser != null) {
                                    val ref = firebaseManager.database.child("orders").child(chatId).child("messages")
                                    val listener = object : com.google.firebase.database.ValueEventListener {
                                        override fun onDataChange(s: com.google.firebase.database.DataSnapshot) {
                                            unreadCount = s.children.mapNotNull { it.getValue(Message::class.java) }
                                                .count { it.senderId != currentUser.id && !it.isRead }
                                        }
                                        override fun onCancelled(e: com.google.firebase.database.DatabaseError) {}
                                    }
                                    ref.addValueEventListener(listener)
                                }
                            }

                            Button(
                                onClick = { onChatClick(chatId) },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White)
                            ) {
                                BadgedBox(badge = { if(unreadCount > 0) Badge { Text("$unreadCount") } }) {
                                    Icon(Icons.AutoMirrored.Filled.Chat, null, tint = Color(0xFFC5A059), modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(4.dp))
                                val label = if (order.rideType == "SHARED" && !order.driverId.isNullOrEmpty()) (if(isAr) "محادثة جماعية" else "Group Chat") else (if(isAr) "مراسلة" else "Chat")
                                Text(label, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }

                        val trackLabel = if (isActuallyDriver) (if(isAr) "موقع العميل" else "User Loc") else (if(isAr) "موقع السائق" else "Driver Loc")
                        FilledTonalButton(
                            onClick = {
                                if (isActuallyDriver) {
                                    onTrackClick(order.pickupLat, order.pickupLng, order.pickupName, order.id)
                                } else {
                                    onTrackClick(order.driverLat ?: 0.0, order.driverLng ?: 0.0, order.driverName ?: "", order.id)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF2196F3).copy(0.1f), contentColor = Color(0xFF2196F3))
                        ) {
                            Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(trackLabel, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                } else if (order.status == "COMPLETED" && !isActuallyDriver && order.rating == null) {
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onRateClick,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC5A059), contentColor = Color.Black)
                    ) {
                        Icon(Icons.Default.Star, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if(isAr) "تقييم الرحلة" else "Rate Trip", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun RatingDialog(isAr: Boolean, onDismiss: () -> Unit, onConfirm: (Float, String) -> Unit) {
    var rating by remember { mutableFloatStateOf(5f) }
    var feedback by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if(isAr) "تقييم السائق" else "Rate Driver", fontWeight = FontWeight.Black) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..5).forEach { i ->
                        Icon(
                            imageVector = if (i <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            tint = Color(0xFFFFD600),
                            modifier = Modifier.size(32.dp).clickable { rating = i.toFloat() }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = feedback,
                    onValueChange = { feedback = it },
                    label = { Text(if(isAr) "ملاحظاتك" else "Feedback") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { Button(onClick = { onConfirm(rating, feedback) }) { Text(if(isAr) "إرسال" else "Send") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if(isAr) "إلغاء" else "Cancel") } }
    )
}

@Composable
fun StatusBadge(status: String, isAr: Boolean) {
    val color = when(status.uppercase().trim()) {
        "CONFIRMED", "COMPLETED" -> Color(0xFF4CAF50)
        "PENDING", "ACCEPTED" -> Color(0xFFFF9800)
        "CANCELLED", "REJECTED_BY_DRIVER" -> Color.Red
        else -> Color.Gray
    }
    
    val displayText = if (isAr) {
        when(status.uppercase().trim()) {
            "PENDING" -> "قيد الانتظار"
            "CONFIRMED" -> "مؤكدة"
            "ACCEPTED" -> "تحت الحجز"
            "CANCELLED" -> "ملغي"
            "STARTED" -> "بدأت"
            "ARRIVED" -> "وصل"
            else -> status
        }
    } else status

    Surface(color = color.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
        Text(
            text = displayText,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
