package com.albasel.tawsilaapp.ui.screens.admin

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.albasel.tawsilaapp.data.firebase.FirebaseManager
import com.albasel.tawsilaapp.data.model.CityDestination
import com.albasel.tawsilaapp.data.model.Order
import com.albasel.tawsilaapp.data.model.User
import com.albasel.tawsilaapp.data.model.VehiclePrice
import com.albasel.tawsilaapp.data.model.SystemLogicSettings
import com.albasel.tawsilaapp.util.AppImageView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*
import kotlin.math.roundToInt

import com.albasel.tawsilaapp.data.model.InAppNotification
import com.albasel.tawsilaapp.data.model.PaymentMethod
import com.albasel.tawsilaapp.util.GoogleDriveHelper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.zIndex
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox

data class AdminTabItem(val realIndex: Int, val key: String, val title: String)

@Composable
fun AdminHomeScreen(firebaseManager: FirebaseManager, user: User?, initialTab: Int = 0, initialSubTab: Int = 0, targetId: String? = null) {
    val context = LocalContext.current
    var selectedRealTab by remember { mutableIntStateOf(initialTab) }
    var subTabState by remember { mutableIntStateOf(initialSubTab) }
    var targetIdState by remember { mutableStateOf(targetId) }
    
    val formattedUserPhone = firebaseManager.formatPhoneKey(user?.phone ?: "")
    val userId = user?.id ?: ""
    val isSuperAdmin = formattedUserPhone.contains("01098804131")

    var adminPermissions by remember { mutableStateOf<Map<String, Boolean>>(user?.adminPermissions ?: emptyMap()) }

    DisposableEffect(formattedUserPhone, userId) {
        if (!isSuperAdmin) {
            val listenKey = formattedUserPhone.ifBlank { userId }
            if (listenKey.isNotBlank()) {
                val listener1 = firebaseManager.listenToAdminPermissions(listenKey) { map ->
                    if (map.isNotEmpty()) adminPermissions = map
                }
                val listener2 = if (userId.isNotBlank() && userId != listenKey) {
                    firebaseManager.listenToAdminPermissions(userId) { map ->
                        if (map.isNotEmpty()) adminPermissions = map
                    }
                } else null

                onDispose {
                    firebaseManager.database.child("admin_permissions").child(listenKey).removeEventListener(listener1)
                    if (listener2 != null && userId.isNotBlank()) {
                        firebaseManager.database.child("admin_permissions").child(userId).removeEventListener(listener2)
                    }
                }
            } else {
                onDispose {}
            }
        } else {
            onDispose {}
        }
    }

    val allTabsList = remember {
        listOf(
            AdminTabItem(0, "users", "مستخدمين"),
            AdminTabItem(1, "history", "السجل"),
            AdminTabItem(2, "finance", "المالية"),
            AdminTabItem(3, "messages", "الرسائل"),
            AdminTabItem(4, "system", "إدارة النظام"),
            AdminTabItem(5, "pricing", "معادلات التسعير")
        )
    }

    val visibleTabs = remember(isSuperAdmin, adminPermissions) {
        if (isSuperAdmin) {
            allTabsList
        } else if (adminPermissions.isNotEmpty()) {
            val filtered = allTabsList.filter { adminPermissions[it.key] == true }
            if (filtered.isNotEmpty()) filtered else allTabsList
        } else {
            allTabsList
        }
    }

    LaunchedEffect(initialTab, initialSubTab, targetId) {
        selectedRealTab = initialTab
        subTabState = initialSubTab
        targetIdState = targetId
    }

    val scope = rememberCoroutineScope()
    var termsText by remember { mutableStateOf("") }
    var showTermsEditor by remember { mutableStateOf(false) }
    
    var showNotificationsOverlay by remember { mutableStateOf(false) }
    val notificationsList = remember { mutableStateListOf<InAppNotification>() }

    LaunchedEffect(Unit) {
        termsText = firebaseManager.database.child("settings").child("terms_text").get().await().value?.toString() ?: ""
    }

    DisposableEffect(user?.id) {
        if (user != null) {
            val listener = object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    notificationsList.clear()
                    snapshot.children.forEach { child ->
                        val map = child.value as? Map<*, *> ?: return@forEach
                        notificationsList.add(InAppNotification(
                        id = map["id"]?.toString() ?: (child.key ?: ""),
                        title = map["title"]?.toString() ?: "",
                        content = map["content"]?.toString() ?: "",
                        type = map["type"]?.toString() ?: "general",
                        sourceId = map["sourceId"]?.toString() ?: "",
                        timestamp = (map["timestamp"] as? Long) ?: 0L
                    ))
                    }
                    notificationsList.sortByDescending { notif -> notif.timestamp }
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            }
            val ref = firebaseManager.database.child("system_notifications").child(user.id)
            ref.addValueEventListener(listener)
            onDispose { ref.removeEventListener(listener) }
        } else {
            onDispose {}
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.systemBars)) {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Surface(modifier = Modifier.fillMaxWidth(), color = Color.Black) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("لوحة التحكم", color = Color(0xFFC5A059), fontSize = 24.sp, fontWeight = FontWeight.Black)
                        
                        IconButton({ showNotificationsOverlay = !showNotificationsOverlay }) {
                            BadgedBox(badge = { if(notificationsList.isNotEmpty()) Badge { Text("${notificationsList.size}") } }) {
                                Icon(Icons.Default.NotificationsNone, null, tint = Color.White) 
                            }
                        }
                    }
                    val activeTabIndex = visibleTabs.indexOfFirst { it.realIndex == selectedRealTab }.coerceAtLeast(0)
                    TabRow(selectedTabIndex = activeTabIndex, containerColor = Color.Transparent, contentColor = Color(0xFFC5A059), divider = {}) {
                        visibleTabs.forEach { tabItem ->
                            Tab(
                                selected = selectedRealTab == tabItem.realIndex, 
                                onClick = { selectedRealTab = tabItem.realIndex }, 
                                text = { Text(tabItem.title, fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1) }
                            )
                        }
                    }
                }
            }

            Box(Modifier.weight(1f)) {
                when (selectedRealTab) {
                    0 -> UsersTab(firebaseManager, subTabState, targetIdState)
                    1 -> OrdersHistoryTab(firebaseManager, user)
                    2 -> FinanceTab(firebaseManager)
                    3 -> MessagesTab(firebaseManager)
                    4 -> SystemManagementTab(firebaseManager, user)
                    5 -> FormulaTab(firebaseManager)
                    else -> UsersTab(firebaseManager, subTabState, targetIdState)
                }
            }
        }

        if (showNotificationsOverlay) {
            Box(modifier = Modifier.fillMaxSize().zIndex(100f)) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.3f)).clickable { showNotificationsOverlay = false })
                Card(
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 80.dp, end = 16.dp).width(300.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("الإشعارات", fontWeight = FontWeight.Black)
                            Row {
                                if (notificationsList.isNotEmpty() && user != null) {
                                    TextButton(onClick = { 
                                        scope.launch { firebaseManager.clearAllNotifications(user.id) }
                                    }) {
                                        Text("حذف الكل", color = Color.Red, fontSize = 11.sp)
                                    }
                                }
                                IconButton({ showNotificationsOverlay = false }, modifier = Modifier.size(20.dp)) { Icon(Icons.Default.Close, null) }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (notificationsList.isEmpty()) {
                            Text("لا توجد إشعارات جديدة", color = Color.Gray, modifier = Modifier.padding(vertical = 12.dp))
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                                items(notificationsList) { n ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { 
                                            // Handle deep linking for admin
                                            if (n.sourceId.contains("admin_home")) {
                                                val uri = android.net.Uri.parse(n.sourceId)
                                                val t = uri.getQueryParameter("tab")?.toIntOrNull() ?: 0
                                                val s = uri.getQueryParameter("sub")?.toIntOrNull() ?: 0
                                                val target = uri.getQueryParameter("targetId")
                                                
                                                selectedRealTab = t
                                                subTabState = s
                                                targetIdState = target
                                            }
                                            showNotificationsOverlay = false
                                            if(user != null) scope.launch { firebaseManager.deleteNotification(user.id, n.id) }
                                        },
                                        colors = CardDefaults.cardColors(containerColor = Color.Gray.copy(0.05f))
                                    ) {
                                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Box(Modifier.size(8.dp).background(Color(0xFFC5A059), CircleShape))
                                            Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(n.title, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                Text(n.content, fontSize = 11.sp, color = Color.DarkGray)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormulaTab(firebaseManager: FirebaseManager) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var formulas by remember { mutableStateOf(emptyList<com.albasel.tawsilaapp.data.model.PricingFormula>()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingFormula by remember { mutableStateOf<com.albasel.tawsilaapp.data.model.PricingFormula?>(null) }

    var categoryFilter by remember { mutableStateOf("ALL") } // ALL, HOME, CARGO
    var vehicleFilter by remember { mutableStateOf("الكل") }
    val vehicles = listOf("الكل", "تاكسي", "فان", "ملاكي", "توكتوك", "تريسكل", "باص", "ميكروباص", "موتوسيكل", "دراجة", "ربع نقل", "نصف نقل", "نقل")

    fun load() { scope.launch { formulas = firebaseManager.getPricingFormulas() } }
    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("معادلات التسعير", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color(0xFFC5A059))
            Button(onClick = { editingFormula = null; showAddDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC5A059))) {
                Text("إضافة معادلة", color = Color.Black)
            }
        }
        
        Spacer(Modifier.height(12.dp))
        
        // Filters
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            var expC by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expC, onExpandedChange = { expC = !expC }, modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = when(categoryFilter) { "HOME" -> "سفر"; "CARGO" -> "نقل بضائع"; "GPS" -> "GPS"; else -> "الكل" },
                    onValueChange = {}, readOnly = true, label = { Text("نوع المعادلة", fontSize = 10.sp) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expC) },
                    modifier = Modifier.menuAnchor(), shape = RoundedCornerShape(8.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                )
                ExposedDropdownMenu(expanded = expC, onDismissRequest = { expC = false }) {
                    DropdownMenuItem(text = { Text("الكل") }, onClick = { categoryFilter = "ALL"; expC = false })
                    DropdownMenuItem(text = { Text("التسعير") }, onClick = { categoryFilter = "HOME"; expC = false })
                    DropdownMenuItem(text = { Text("إعدادات GPS") }, onClick = { categoryFilter = "GPS"; expC = false })
                }
            }
            
            var expVF by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expVF, onExpandedChange = { expVF = !expVF }, modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = vehicleFilter, onValueChange = {}, readOnly = true, label = { Text("المركبة", fontSize = 10.sp) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expVF) },
                    modifier = Modifier.menuAnchor(), shape = RoundedCornerShape(8.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                )
                ExposedDropdownMenu(expanded = expVF, onDismissRequest = { expVF = false }) {
                    vehicles.forEach { v -> DropdownMenuItem(text = { Text(v) }, onClick = { vehicleFilter = v; expVF = false }) }
                }
            }
        }

        var viewMode by remember { mutableStateOf("TABLE") } // "TABLE" for Master Matrix Table, "CARDS" for Card list

        Spacer(Modifier.height(8.dp))

        // View Mode Toggle Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF262626),
            border = BorderStroke(1.dp, Color(0xFFC5A059).copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.weight(1f).clickable { viewMode = "TABLE" },
                    shape = RoundedCornerShape(8.dp),
                    color = if (viewMode == "TABLE") Color(0xFFC5A059) else Color.Transparent
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                        Text("📊 الجدول الشامل للمعادلات", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (viewMode == "TABLE") Color.Black else Color.White)
                    }
                }
                Spacer(Modifier.width(4.dp))
                Surface(
                    modifier = Modifier.weight(1f).clickable { viewMode = "CARDS" },
                    shape = RoundedCornerShape(8.dp),
                    color = if (viewMode == "CARDS") Color(0xFFC5A059) else Color.Transparent
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                        Text("🗂️ عرض الكروت المستقلة", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (viewMode == "CARDS") Color.Black else Color.White)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        val filteredFormulas = remember(formulas, categoryFilter, vehicleFilter) {
            formulas.filter { f ->
                (categoryFilter == "ALL" || f.rideCategory == categoryFilter) &&
                (vehicleFilter == "الكل" || f.vehicleType == vehicleFilter || f.vehicleType.isEmpty() || f.vehicleType == "الكل")
            }
        }

        if (viewMode == "TABLE") {
            MasterPricingMatrixTable(
                formulas = filteredFormulas,
                vehiclesList = listOf("دراجة", "موتوسيكل", "توكتوك", "تريسكل", "تاكسي", "ملاكي", "فان", "ميكروباص", "باص", "ربع نقل", "نصف نقل", "نقل"),
                onSaveMatrixFormula = { updatedFormula ->
                    scope.launch {
                        firebaseManager.savePricingFormula(updatedFormula)
                        load()
                    }
                },
                onDeleteFormula = { fId ->
                    scope.launch {
                        firebaseManager.deletePricingFormula(fId)
                        load()
                    }
                }
            )
        } else {
            filteredFormulas.forEach { f ->
                Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        val catLabel = when(f.rideCategory) {
                            "GPS" -> "إعدادات GPS (لوجيك)"
                            else -> "التسعير (سفر وبضائع)"
                        }
                        val vLabel = if(f.vehicleType.isEmpty() || f.vehicleType == "الكل") "جميع المركبات" else f.vehicleType
                        
                        Text("الفئة: $catLabel", fontWeight = FontWeight.Black, color = Color(0xFFC5A059))
                        Text("المركبة: $vLabel", fontWeight = FontWeight.Bold)
                        
                        if (f.rideCategory == "GPS") {
                            Text("نطاق السائق: ${f.driverArrivalRadius} م", fontSize = 13.sp)
                            Text("نطاق العميل: ${f.customerArrivalRadius} م", fontSize = 13.sp)
                            Text("تأكيدات GPS: ${f.gpsConfirmationsNeeded}", fontSize = 13.sp)
                            Text("دقة GPS: ${f.gpsAccuracyThreshold} م", fontSize = 13.sp)
                        } else {
                            Text("المدى: ${f.minKm} - ${f.maxKm} كم", fontSize = 13.sp)
                            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = Color.Gray.copy(alpha = 0.3f))
                            Text("فتح العداد: ${f.baseFare} ج.م")
                            Text("سعر الكيلو: ${f.pricePerKm} ج.م")
                            Text("سعر الدقيقة: ${f.pricePerMinute} ج.م")
                            Text("سعر الكارتة: ${f.tollFee} ج.م")
                            Text("دقيقة الانتظار: ${f.cargoWaitMinutePrice} ج.م", fontSize = 12.sp)
                            Text("انتظار مجاني (سفر): ${f.freeWaitMinutes} دقيقة", fontSize = 12.sp)
                            Text("انتظار مجاني (بضائع): ${f.freeWaitMinutesCargo} دقيقة", fontSize = 12.sp)
                            
                            if (f.surcharge > 0 || f.loadingFee > 0 || f.unloadingFee > 0) {
                                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = Color(0xFF795548).copy(alpha = 0.2f))
                                Text("إضافات البضائع:", fontWeight = FontWeight.Bold, color = Color(0xFF795548), fontSize = 12.sp)
                                Text("رسم حمولة: ${f.surcharge} ج.م", fontSize = 11.sp)
                                Text("رسم تحميل: ${f.loadingFee} ج.م", fontSize = 11.sp)
                                Text("رسم تفريغ: ${f.unloadingFee} ج.م", fontSize = 11.sp)
                            }
                        }
                        Row {
                            TextButton(onClick = { editingFormula = f; showAddDialog = true }) { Text("تعديل") }
                            TextButton(onClick = { editingFormula = f.copy(id = ""); showAddDialog = true }) { Text("نسخ", color = Color(0xFF2E7D32)) }
                            TextButton(onClick = { scope.launch { firebaseManager.deletePricingFormula(f.id); load() } }) { Text("حذف", color = Color.Red) }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var minKm by remember { mutableStateOf(editingFormula?.minKm?.toString() ?: "0") }
        var maxKm by remember { mutableStateOf(editingFormula?.maxKm?.toString() ?: "1000") }
        var maxWeightKg by remember { mutableStateOf(editingFormula?.maxWeightKg?.toString() ?: "3000") }
        var baseFare by remember { mutableStateOf(editingFormula?.baseFare?.toString() ?: "") }
        var pricePerKm by remember { mutableStateOf(editingFormula?.pricePerKm?.toString() ?: "") }
        var pricePerMinute by remember { mutableStateOf(editingFormula?.pricePerMinute?.toString() ?: "") }
        var tollFee by remember { mutableStateOf(editingFormula?.tollFee?.toString() ?: "0") }
        
        var pricePerCarton by remember { mutableStateOf(editingFormula?.pricePerCarton?.toString() ?: "0") }
        var cargoWaitMinutePrice by remember { mutableStateOf(editingFormula?.cargoWaitMinutePrice?.toString() ?: "0") }
        var freeWaitMinutes by remember { mutableStateOf(editingFormula?.freeWaitMinutes?.toString() ?: "15") }
        var freeWaitMinutesCargo by remember { mutableStateOf(editingFormula?.freeWaitMinutesCargo?.toString() ?: "30") }
        
        var surcharge by remember { mutableStateOf(editingFormula?.surcharge?.toString() ?: "0") }
        var loadingFee by remember { mutableStateOf(editingFormula?.loadingFee?.toString() ?: "0") }
        var unloadingFee by remember { mutableStateOf(editingFormula?.unloadingFee?.toString() ?: "0") }
        var trafficSpeedLimitKmh by remember { mutableStateOf(editingFormula?.trafficSpeedLimitKmh?.toString() ?: "40") }

        // GPS Logic Fields
        var driverArrivalRadius by remember { mutableStateOf(editingFormula?.driverArrivalRadius?.toString() ?: "200.0") }
        var customerArrivalRadius by remember { mutableStateOf(editingFormula?.customerArrivalRadius?.toString() ?: "200.0") }
        var gpsConfirmationsNeeded by remember { mutableStateOf(editingFormula?.gpsConfirmationsNeeded?.toString() ?: "3") }
        var gpsAccuracyThreshold by remember { mutableStateOf(editingFormula?.gpsAccuracyThreshold?.toString() ?: "30.0") }

        val weightBrackets = remember { mutableStateListOf<com.albasel.tawsilaapp.data.model.WeightBracket>().apply { addAll(editingFormula?.weightBrackets ?: emptyList()) } }
        val loadingOptions = remember { mutableStateListOf<com.albasel.tawsilaapp.data.model.CargoServiceOption>().apply { addAll(editingFormula?.loadingOptions ?: emptyList()) } }
        val unloadingOptions = remember { mutableStateListOf<com.albasel.tawsilaapp.data.model.CargoServiceOption>().apply { addAll(editingFormula?.unloadingOptions ?: emptyList()) } }

        var vehicleType by remember { mutableStateOf(editingFormula?.vehicleType ?: "") }
        var rideCategory by remember { mutableStateOf(editingFormula?.rideCategory ?: "HOME") }
        
        val vehiclesList = listOf("تاكسي", "فان", "ملاكي", "توكتوك", "تريسكل", "باص", "ميكروباص", "موتوسيكل", "دراجة", "ربع نقل", "نصف نقل", "نقل")

        val dialogTitle = when {
            editingFormula == null -> "إضافة معادلة"
            editingFormula!!.id.isEmpty() -> "نسخ معادلة"
            else -> "تعديل معادلة"
        }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(dialogTitle, fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("نوع الإعداد:", fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        RadioButton(selected = rideCategory == "HOME", onClick = { rideCategory = "HOME" })
                        Text("التسعير (سفر وبضائع)", fontSize = 11.sp, modifier = Modifier.clickable { rideCategory = "HOME" })
                        Spacer(Modifier.width(16.dp))
                        RadioButton(selected = rideCategory == "GPS", onClick = { rideCategory = "GPS" })
                        Text("GPS", fontSize = 11.sp, modifier = Modifier.clickable { rideCategory = "GPS" })
                    }

                    var expV by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = expV, onExpandedChange = { expV = !expV }) {
                        OutlinedTextField(value = if(vehicleType == "") "اختر المركبة" else vehicleType, onValueChange = {}, readOnly = true, label = { Text("المركبة") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expV) }, modifier = Modifier.menuAnchor().fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                        ExposedDropdownMenu(expanded = expV, onDismissRequest = { expV = false }) {
                            vehiclesList.forEach { v -> DropdownMenuItem(text = { Text(v) }, onClick = { vehicleType = v; expV = false }) }
                        }
                    }

                    if (rideCategory == "GPS") {
                        Text("إعدادات منطق الـ GPS والوصول:", fontWeight = FontWeight.Bold, color = Color(0xFFC5A059))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = driverArrivalRadius, onValueChange = { driverArrivalRadius = it }, label = { Text("نطاق السائق (م)") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                            OutlinedTextField(value = customerArrivalRadius, onValueChange = { customerArrivalRadius = it }, label = { Text("نطاق العميل (م)") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = gpsConfirmationsNeeded, onValueChange = { gpsConfirmationsNeeded = it }, label = { Text("تأكيدات GPS") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                            OutlinedTextField(value = gpsAccuracyThreshold, onValueChange = { gpsAccuracyThreshold = it }, label = { Text("دقة الموقع (م)") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                        }
                        Text("ملاحظة: هذه الإعدادات تؤثر على احتساب الوصول التلقائي.", fontSize = 10.sp, color = Color.Gray)
                    }

                    if (rideCategory == "HOME") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = minKm, onValueChange = { minKm = it }, label = { Text("الحد الأدنى (كم)") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                            OutlinedTextField(value = maxKm, onValueChange = { maxKm = it }, label = { Text("الحد الأقصى (كم)") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                        }
                        
                        HorizontalDivider(color = Color.Gray.copy(0.1f))
                        Text("إعدادات السفر (أساسي):", fontWeight = FontWeight.Bold, color = Color(0xFFC5A059))
                        
                        OutlinedTextField(value = baseFare, onValueChange = { baseFare = it }, label = { Text("سعر فتح العداد") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                        OutlinedTextField(value = pricePerKm, onValueChange = { pricePerKm = it }, label = { Text("سعر الكيلو") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                        OutlinedTextField(value = pricePerMinute, onValueChange = { pricePerMinute = it }, label = { Text("سعر الدقيقة") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                        OutlinedTextField(value = tollFee, onValueChange = { tollFee = it }, label = { Text("سعر الكارتة لهذا المدى") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                        
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = cargoWaitMinutePrice, onValueChange = { cargoWaitMinutePrice = it }, label = { Text("سعر دقيقة الانتظار") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = freeWaitMinutes, onValueChange = { freeWaitMinutes = it }, label = { Text("انتظار مجاني سفر (د)") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                            OutlinedTextField(value = freeWaitMinutesCargo, onValueChange = { freeWaitMinutesCargo = it }, label = { Text("انتظار مجاني بضائع (د)") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                        }

                        HorizontalDivider(color = Color.Gray.copy(0.1f))
                        Text("إعدادات البضائع (إضافي):", fontWeight = FontWeight.Bold, color = Color(0xFF795548))
                        
                        val bracketLabels = listOf("حتى 25 كجم (دراجة)", "حتى 100 كجم (موتوسيكل)", "حتى 350 كجم (تاكسي)", "حتى 400 كجم (ملاكي)", "حتى 700 كجم (فان)", "حتى 800 كجم (ميكروباص)", "حتى 1000 كجم (ربع نقل)", "حتى 1500 كجم (باص)", "حتى 2000 كجم (نصف نقل)", "حتى 5000 كجم (نقل)")
                        val toKgValues = listOf(25.0, 100.0, 350.0, 400.0, 700.0, 800.0, 1000.0, 1500.0, 2000.0, 5000.0)
                        LaunchedEffect(vehicleType) {
                            val limit = when {
                                vehicleType.contains("دراجة") || vehicleType.contains("Bike") -> 25.0
                                vehicleType.contains("موتوسيكل") || vehicleType.contains("Motorcycle") -> 100.0
                                vehicleType.contains("تاكسي") || vehicleType.contains("Taxi") -> 350.0
                                vehicleType.contains("ملاكي") || vehicleType.contains("Private") -> 400.0
                                vehicleType.contains("فان") || vehicleType.contains("Van") -> 700.0
                                vehicleType.contains("ميكروباص") || vehicleType.contains("Microbus") -> 800.0
                                vehicleType.contains("ربع نقل") || vehicleType.contains("Quarter") -> 1000.0
                                vehicleType.contains("باص") || vehicleType.contains("Bus") -> 1500.0
                                vehicleType.contains("نصف نقل") || vehicleType.contains("Half") -> 2000.0
                                vehicleType.contains("نقل") || vehicleType.contains("Truck") -> 5000.0
                                else -> 3000.0
                            }
                            maxWeightKg = limit.toString()
                        }
                        val currentLabel = bracketLabels.getOrNull(toKgValues.indexOf(maxWeightKg.toDoubleOrNull() ?: 0.0)) ?: "غير محدد"
                        OutlinedTextField(value = currentLabel, onValueChange = {}, readOnly = true, label = { Text("الوزن المربوط (ثابت)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), leadingIcon = { Icon(Icons.Default.Scale, null) })

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = surcharge, onValueChange = { surcharge = it }, label = { Text("رسم حمولة") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                            OutlinedTextField(value = loadingFee, onValueChange = { loadingFee = it }, label = { Text("رسم تحميل") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                            OutlinedTextField(value = unloadingFee, onValueChange = { unloadingFee = it }, label = { Text("رسم تفريغ") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                        }

                        OutlinedTextField(value = trafficSpeedLimitKmh, onValueChange = { trafficSpeedLimitKmh = it }, label = { Text("حد سرعة الزحمة (كم/س)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val formula = com.albasel.tawsilaapp.data.model.PricingFormula(
                        id = editingFormula?.id ?: "",
                        minKm = minKm.toDoubleOrNull() ?: 0.0,
                        maxKm = if(rideCategory == "HOME") (maxKm.toDoubleOrNull() ?: 1000.0) else 10000.0,
                        maxWeightKg = maxWeightKg.toDoubleOrNull() ?: 0.0,
                        baseFare = baseFare.toDoubleOrNull() ?: 0.0,
                        pricePerKm = pricePerKm.toDoubleOrNull() ?: 0.0,
                        pricePerMinute = pricePerMinute.toDoubleOrNull() ?: 0.0,
                        tollFee = tollFee.toDoubleOrNull() ?: 0.0,
                        vehicleType = vehicleType,
                        rideCategory = rideCategory,
                        pricePerCarton = pricePerCarton.toDoubleOrNull() ?: 0.0,
                        cargoWaitMinutePrice = cargoWaitMinutePrice.toDoubleOrNull() ?: 0.0,
                        freeWaitMinutes = freeWaitMinutes.toIntOrNull() ?: 15,
                        freeWaitMinutesCargo = freeWaitMinutesCargo.toIntOrNull() ?: 30,
                        surcharge = surcharge.toDoubleOrNull() ?: 0.0,
                        loadingFee = loadingFee.toDoubleOrNull() ?: 0.0,
                        unloadingFee = unloadingFee.toDoubleOrNull() ?: 0.0,
                        trafficSpeedLimitKmh = trafficSpeedLimitKmh.toDoubleOrNull() ?: 40.0,
                        driverArrivalRadius = driverArrivalRadius.toDoubleOrNull() ?: 200.0,
                        customerArrivalRadius = customerArrivalRadius.toDoubleOrNull() ?: 200.0,
                        gpsConfirmationsNeeded = gpsConfirmationsNeeded.toIntOrNull() ?: 3,
                        gpsAccuracyThreshold = gpsAccuracyThreshold.toFloatOrNull() ?: 30f,
                        weightBrackets = weightBrackets.toList(),
                        loadingOptions = loadingOptions.toList(),
                        unloadingOptions = unloadingOptions.toList()
                    )
                    scope.launch { firebaseManager.savePricingFormula(formula); showAddDialog = false; load() }
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
fun MasterPricingMatrixTable(
    formulas: List<com.albasel.tawsilaapp.data.model.PricingFormula>,
    vehiclesList: List<String>,
    onSaveMatrixFormula: (formula: com.albasel.tawsilaapp.data.model.PricingFormula) -> Unit,
    onDeleteFormula: (id: String) -> Unit
) {
    val metricRows = listOf(
        "المدى (كم)" to { f: com.albasel.tawsilaapp.data.model.PricingFormula? -> if (f != null) "${f.minKm}-${f.maxKm}" else "-" },
        "فتح العداد (ج.م)" to { f: com.albasel.tawsilaapp.data.model.PricingFormula? -> if (f != null) "${f.baseFare}" else "-" },
        "سعر الكيلو (ج.م)" to { f: com.albasel.tawsilaapp.data.model.PricingFormula? -> if (f != null) "${f.pricePerKm}" else "-" },
        "سعر الدقيقة (ج.م)" to { f: com.albasel.tawsilaapp.data.model.PricingFormula? -> if (f != null) "${f.pricePerMinute}" else "-" },
        "سعر الكارتة (ج.م)" to { f: com.albasel.tawsilaapp.data.model.PricingFormula? -> if (f != null) "${f.tollFee}" else "-" },
        "دقيقة الانتظار (ج.م)" to { f: com.albasel.tawsilaapp.data.model.PricingFormula? -> if (f != null) "${f.cargoWaitMinutePrice}" else "-" },
        "انتظار مجاني سفر (د)" to { f: com.albasel.tawsilaapp.data.model.PricingFormula? -> if (f != null) "${f.freeWaitMinutes}" else "-" },
        "انتظار مجاني بضائع (د)" to { f: com.albasel.tawsilaapp.data.model.PricingFormula? -> if (f != null) "${f.freeWaitMinutesCargo}" else "-" },
        "رسم حمولة (ج.م)" to { f: com.albasel.tawsilaapp.data.model.PricingFormula? -> if (f != null) "${f.surcharge}" else "-" },
        "رسم تحميل (ج.م)" to { f: com.albasel.tawsilaapp.data.model.PricingFormula? -> if (f != null) "${f.loadingFee}" else "-" },
        "رسم تفريغ (ج.م)" to { f: com.albasel.tawsilaapp.data.model.PricingFormula? -> if (f != null) "${f.unloadingFee}" else "-" },
        "أقصى وزن (كجم)" to { f: com.albasel.tawsilaapp.data.model.PricingFormula? -> if (f != null) "${f.maxWeightKg}" else "-" }
    )

    // Extract saved unique distance ranges dynamically sorted from SMALLEST TO LARGEST (RTL - No "الكل")
    val availableKmRanges = remember(formulas) {
        formulas.filter { it.rideCategory != "GPS" }
            .sortedBy { it.minKm }
            .map { "${it.minKm}-${it.maxKm}" }
            .distinct()
    }
    var selectedKmRangeFilter by remember(availableKmRanges) {
        mutableStateOf(availableKmRanges.firstOrNull() ?: "")
    }
    var selectedVehiclesSet by remember { mutableStateOf<Set<String>>(emptySet()) }
    var formulaToDeleteId by remember { mutableStateOf<String?>(null) }

    val activeVehiclesList = if (selectedVehiclesSet.isEmpty()) vehiclesList else vehiclesList.filter { selectedVehiclesSet.contains(it) }

    val formulaByVehicle = remember(formulas, selectedKmRangeFilter, activeVehiclesList) {
        activeVehiclesList.associateWith { v ->
            formulas.firstOrNull { f ->
                (f.vehicleType == v || f.vehicleType == "الكل") &&
                f.rideCategory != "GPS" &&
                (selectedKmRangeFilter.isBlank() || "${f.minKm}-${f.maxKm}" == selectedKmRangeFilter)
            } ?: formulas.firstOrNull { f ->
                (f.vehicleType == v || f.vehicleType == "الكل") && f.rideCategory != "GPS"
            }
        }
    }

    var isMatrixEditingMode by remember { mutableStateOf(false) }
    val editedCellValues = remember { mutableStateMapOf<String, String>() }

    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFAF9F6)),
            border = BorderStroke(1.5.dp, Color(0xFFC5A059)),
            elevation = CardDefaults.cardElevation(6.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) {
            Column(Modifier.padding(10.dp)) {
                // Header Title & Excel Cell Editing Toggle Button (replaced 'اسحب افقياً')
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "📊 جدول معادلات التسعير الشامل المباشر",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFB8860B)
                    )
                    
                    Surface(
                        onClick = {
                            if (isMatrixEditingMode) {
                                // Save all edited cells to Firebase
                                activeVehiclesList.forEach { vType ->
                                    val orig = formulaByVehicle[vType] ?: com.albasel.tawsilaapp.data.model.PricingFormula(vehicleType = vType, rideCategory = "HOME")
                                    
                                    val baseFareVal = editedCellValues["${vType}_1"]?.toDoubleOrNull() ?: orig.baseFare
                                    val priceKmVal = editedCellValues["${vType}_2"]?.toDoubleOrNull() ?: orig.pricePerKm
                                    val priceMinVal = editedCellValues["${vType}_3"]?.toDoubleOrNull() ?: orig.pricePerMinute
                                    val tollVal = editedCellValues["${vType}_4"]?.toDoubleOrNull() ?: orig.tollFee
                                    val waitVal = editedCellValues["${vType}_5"]?.toDoubleOrNull() ?: orig.cargoWaitMinutePrice
                                    val freeWaitVal = editedCellValues["${vType}_6"]?.toIntOrNull() ?: orig.freeWaitMinutes
                                    val freeWaitCargoVal = editedCellValues["${vType}_7"]?.toIntOrNull() ?: orig.freeWaitMinutesCargo
                                    val surchargeVal = editedCellValues["${vType}_8"]?.toDoubleOrNull() ?: orig.surcharge
                                    val loadingVal = editedCellValues["${vType}_9"]?.toDoubleOrNull() ?: orig.loadingFee
                                    val unloadingVal = editedCellValues["${vType}_10"]?.toDoubleOrNull() ?: orig.unloadingFee
                                    val maxWeightVal = editedCellValues["${vType}_11"]?.toDoubleOrNull() ?: orig.maxWeightKg

                                    val updatedFormula = orig.copy(
                                        baseFare = baseFareVal,
                                        pricePerKm = priceKmVal,
                                        pricePerMinute = priceMinVal,
                                        tollFee = tollVal,
                                        cargoWaitMinutePrice = waitVal,
                                        freeWaitMinutes = freeWaitVal,
                                        freeWaitMinutesCargo = freeWaitCargoVal,
                                        surcharge = surchargeVal,
                                        loadingFee = loadingVal,
                                        unloadingFee = unloadingVal,
                                        maxWeightKg = maxWeightVal
                                    )
                                    onSaveMatrixFormula(updatedFormula)
                                }
                                isMatrixEditingMode = false
                            } else {
                                isMatrixEditingMode = true
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isMatrixEditingMode) Color(0xFF2E7D32) else Color(0xFFC5A059),
                        border = BorderStroke(1.dp, Color.Black.copy(0.3f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                if (isMatrixEditingMode) Icons.Default.Save else Icons.Default.Edit,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (isMatrixEditingMode) "💾 حفظ التعديلات بالجدول" else "✏️ تعديل الخلايا (إكسيل)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                }

                // 1. Horizontal Multi-Vehicle Filter Selector Chips (تاب المركبة أولاً)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("المركبة:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB8860B))
                    FilterChip(
                        selected = selectedVehiclesSet.isEmpty(),
                        onClick = { selectedVehiclesSet = emptySet() },
                        label = { Text("عرض الكل", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFC5A059),
                            selectedLabelColor = Color.Black
                        )
                    )
                    vehiclesList.forEach { vType ->
                        val isSel = selectedVehiclesSet.contains(vType)
                        FilterChip(
                            selected = isSel,
                            onClick = {
                                selectedVehiclesSet = if (isSel) selectedVehiclesSet - vType else selectedVehiclesSet + vType
                            },
                            label = { Text(vType, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFC5A059),
                                selectedLabelColor = Color.Black
                            )
                        )
                    }
                }

                // 2. Horizontal Saved Distance Range Chips Sorted Smallest to Largest (تاب المدى بدون كلمة "الكل")
                if (availableKmRanges.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("المدى:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB8860B))
                        availableKmRanges.forEach { range ->
                            FilterChip(
                                selected = selectedKmRangeFilter == range,
                                onClick = { selectedKmRangeFilter = range },
                                label = { Text("$range كم", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFC5A059),
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFC5A059).copy(alpha = 0.4f), modifier = Modifier.padding(bottom = 6.dp))

                // 3. Horizontally Scrollable Table Matrix (Daytime Velvet Theme - Excel Editable Cells)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    // Fixed Header Column on FAR RIGHT: Metric Labels (البيان)
                    Column(modifier = Modifier.width(135.dp)) {
                        Box(
                            modifier = Modifier
                                .height(37.dp)
                                .fillMaxWidth()
                                .background(Color(0xFFC5A059), RoundedCornerShape(topStart = 8.dp))
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("البيان / المركبة", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.Black)
                        }

                        metricRows.forEachIndexed { idx, (label, _) ->
                            Box(
                                modifier = Modifier
                                    .height(31.dp)
                                    .fillMaxWidth()
                                    .background(if (idx % 2 == 0) Color(0xFFF2F0EB) else Color(0xFFE8E4DA))
                                    .border(0.5.dp, Color(0xFFD0C8B8))
                                    .padding(horizontal = 6.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                            }
                        }

                        Box(
                            modifier = Modifier
                                .height(33.dp)
                                .fillMaxWidth()
                                .background(Color(0xFFE8E4DA))
                                .border(0.5.dp, Color(0xFFD0C8B8)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("التحكم", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB8860B))
                        }
                    }

                    // Dynamic Vehicle Columns (Excel-Style Direct Editable Cells)
                    activeVehiclesList.forEach { vType ->
                        val formula = formulaByVehicle[vType]

                        Column(modifier = Modifier.width(105.dp)) {
                            Box(
                                modifier = Modifier
                                    .height(37.dp)
                                    .fillMaxWidth()
                                    .background(Color(0xFF2C2C2C))
                                    .border(0.5.dp, Color(0xFFC5A059))
                                    .padding(2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(vType, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFC5A059), textAlign = TextAlign.Center)
                            }

                            metricRows.forEachIndexed { idx, (_, extractor) ->
                                val origValueStr = extractor(formula)
                                val cellKey = "${vType}_$idx"
                                val currentVal = editedCellValues[cellKey] ?: origValueStr.replace("-", "")

                                Box(
                                    modifier = Modifier
                                        .height(31.dp)
                                        .fillMaxWidth()
                                        .background(if (isMatrixEditingMode) Color(0xFFFFFDE7) else if (idx % 2 == 0) Color.White else Color(0xFFF9F8F5))
                                        .border(0.5.dp, if (isMatrixEditingMode) Color(0xFFC5A059) else Color(0xFFE0D8C8))
                                        .padding(2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isMatrixEditingMode && idx > 0) {
                                        androidx.compose.foundation.text.BasicTextField(
                                            value = currentVal,
                                            onValueChange = { editedCellValues[cellKey] = it },
                                            textStyle = androidx.compose.ui.text.TextStyle(
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFB8860B),
                                                textAlign = TextAlign.Center
                                            ),
                                            singleLine = true,
                                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    } else {
                                        Text(
                                            origValueStr,
                                            fontSize = 11.sp,
                                            fontWeight = if (origValueStr != "-") FontWeight.Bold else FontWeight.Normal,
                                            color = if (origValueStr != "-") Color(0xFF1A1A1A) else Color.LightGray
                                        )
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .height(33.dp)
                                    .fillMaxWidth()
                                    .background(Color(0xFFF2F0EB))
                                    .border(0.5.dp, Color(0xFFE0D8C8)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (formula != null && formula.id.isNotBlank()) {
                                    IconButton(
                                        onClick = { formulaToDeleteId = formula.id },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(16.dp))
                                    }
                                } else {
                                    Text("-", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Warning Confirmation Dialog before deleting formula
    if (formulaToDeleteId != null) {
        AlertDialog(
            onDismissRequest = { formulaToDeleteId = null },
            title = { Text("تأكيد الحذف", fontWeight = FontWeight.Bold, color = Color.Red) },
            text = { Text("هل أنت متأكد من حذف معادلة التسعير لهذه المركبة؟") },
            confirmButton = {
                Button(
                    onClick = {
                        formulaToDeleteId?.let { onDeleteFormula(it) }
                        formulaToDeleteId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("نعم، احذف", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { formulaToDeleteId = null }) {
                    Text("لا / إلغاء")
                }
            }
        )
    }
}


@Composable
fun FinanceTab(firebaseManager: FirebaseManager) {
    var financeSubTab by remember { mutableIntStateOf(0) } // 0: المدفوعات, 1: الأسعار والعمولة, 2: النقاط والخصومات
    var allPayments by remember { mutableStateOf<List<com.albasel.tawsilaapp.data.model.DriverPaymentRecord>>(emptyList()) }
    var usersList by remember { mutableStateOf<List<User>>(emptyList()) }
    var ordersList by remember { mutableStateOf<List<Order>>(emptyList()) }
    var vehiclePrices by remember { mutableStateOf<List<com.albasel.tawsilaapp.data.model.VehiclePrice>>(emptyList()) }
    var sharedThreshold by remember { mutableStateOf("") }
    
    var userPointValue by remember { mutableDoubleStateOf(0.0) }
    var driverPointValue by remember { mutableDoubleStateOf(0.0) }
    var adminCommission by remember { mutableDoubleStateOf(10.0) }
    
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        sharedThreshold = firebaseManager.getSharedButtonThreshold().toString()
        
        firebaseManager.listenToAllCommissionPayments { list ->
            allPayments = list
        }
        
        firebaseManager.database.child("users").addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                usersList = snapshot.children.mapNotNull { it.getValue(User::class.java) }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })

        firebaseManager.database.child("orders").addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                ordersList = snapshot.children.mapNotNull { it.getValue(Order::class.java) }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })

        userPointValue = firebaseManager.getPointValue("USER")
        driverPointValue = firebaseManager.getPointValue("DRIVER")

        firebaseManager.database.child("settings").addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val upVal = snapshot.child("point_values").child("USER").getValue(Double::class.java)
                    ?: snapshot.child("userPointValue").getValue(Double::class.java) ?: 1.0
                val dpVal = snapshot.child("point_values").child("DRIVER").getValue(Double::class.java)
                    ?: snapshot.child("driverPointValue").getValue(Double::class.java) ?: 1.0
                userPointValue = if (upVal > 0) upVal else 1.0
                driverPointValue = if (dpVal > 0) dpVal else 1.0
                adminCommission = snapshot.child("adminCommission").getValue(Double::class.java) ?: 10.0
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })

        firebaseManager.database.child("settings").child("vehicle_prices").addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.mapNotNull { child ->
                    try {
                        child.getValue(com.albasel.tawsilaapp.data.model.VehiclePrice::class.java)
                    } catch (e: Exception) {
                        val price = child.getValue(Double::class.java) ?: 0.0
                        com.albasel.tawsilaapp.data.model.VehiclePrice(type = child.key ?: "", pricePerKm = price)
                    }
                }
                vehiclePrices = list
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Sub-tabs navigation bar (2 Sub-Tabs only)
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF1E1E1E), RoundedCornerShape(12.dp)).padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val subTabTitles = listOf("المدفوعات والعمولات", "الأسعار والعمولة والنقاط والخصومات")
            subTabTitles.forEachIndexed { idx, title ->
                val isSelected = financeSubTab == idx
                Surface(
                    onClick = { financeSubTab = idx },
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) Color(0xFFC5A059) else Color.Transparent
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            title,
                            color = if (isSelected) Color.Black else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        when (financeSubTab) {
            0 -> {
                // المدفوعات Tab (All Drivers Payment History Table)
                var selectedDriverId by remember { mutableStateOf("") } // "" = جميع السائقين
                var selectedMonth by remember { mutableIntStateOf(0) } // 0 = جميع الشهور, -1 = الشهر الحالي, 1..12 = الشهور
                var driverDropdownExpanded by remember { mutableStateOf(false) }
                var monthDropdownExpanded by remember { mutableStateOf(false) }

                val driversListForFilter = remember(usersList, allPayments) {
                    val driversFromUsers = usersList.filter { it.role?.uppercase() == "DRIVER" || !it.vehiclePlate.isNullOrEmpty() }
                    val driverMap = mutableMapOf<String, String>()
                    driversFromUsers.forEach { d ->
                        val name = d.name.ifBlank { d.phone }
                        if (d.id.isNotEmpty()) driverMap[d.id] = name
                        if (d.phone.isNotEmpty()) driverMap[d.phone] = name
                    }
                    allPayments.forEach { p ->
                        if (!driverMap.containsKey(p.driverId)) {
                            driverMap[p.driverId] = p.driverName
                        }
                    }
                    driverMap.toList()
                }

                val monthsList = listOf(
                    "جميع الشهور", "الشهر الحالي",
                    "يناير (1)", "فبراير (2)", "مارس (3)", "أبريل (4)",
                    "مايو (5)", "يونيو (6)", "يوليو (7)", "أغسطس (8)",
                    "سبتمبر (9)", "أكتوبر (10)", "نوفمبر (11)", "ديسمبر (12)"
                )

                val filteredPayments = remember(allPayments, selectedDriverId, selectedMonth) {
                    var list = allPayments
                    if (selectedDriverId.isNotEmpty()) {
                        list = list.filter { pay ->
                            pay.driverId == selectedDriverId ||
                            usersList.any { u -> (u.id == selectedDriverId || u.phone == selectedDriverId) && (u.id == pay.driverId || u.phone == pay.driverId) }
                        }
                    }
                    if (selectedMonth != 0) {
                        val cal = Calendar.getInstance()
                        val nowCal = Calendar.getInstance()
                        list = list.filter { pay ->
                            cal.timeInMillis = pay.timestamp
                            val payMonth = cal.get(Calendar.MONTH) + 1
                            if (selectedMonth == -1) {
                                payMonth == (nowCal.get(Calendar.MONTH) + 1) && cal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR)
                            } else {
                                payMonth == selectedMonth
                            }
                        }
                    }
                    list
                }

                // Header Row with Title and 2 Compact Dropdowns Side-By-Side
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "سجل مدفوعات السائقين المكتمل",
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        color = Color(0xFFC5A059),
                        modifier = Modifier.weight(1.1f)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Drivers Dropdown
                        Box {
                            Surface(
                                onClick = { driverDropdownExpanded = true },
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF1E1E1E),
                                border = BorderStroke(1.dp, Color(0xFFC5A059).copy(0.5f))
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val selectedDriverName = if (selectedDriverId.isEmpty()) {
                                        "جميع السائقين"
                                    } else {
                                        val d = usersList.find { it.id == selectedDriverId || it.phone == selectedDriverId }
                                        d?.name?.takeIf { it.isNotBlank() } ?: d?.phone ?: driversListForFilter.find { it.first == selectedDriverId }?.second ?: "سائق"
                                    }
                                    Text(
                                        selectedDriverName,
                                        fontSize = 10.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 72.dp)
                                    )
                                    Icon(Icons.Default.ArrowDropDown, null, tint = Color(0xFFC5A059), modifier = Modifier.size(16.dp))
                                }
                            }
                            DropdownMenu(
                                expanded = driverDropdownExpanded,
                                onDismissRequest = { driverDropdownExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("جميع السائقين", fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        selectedDriverId = ""
                                        driverDropdownExpanded = false
                                    }
                                )
                                HorizontalDivider()
                                driversListForFilter.distinctBy { it.second }.forEach { (dKey, dName) ->
                                    DropdownMenuItem(
                                        text = { Text(dName, fontSize = 12.sp) },
                                        onClick = {
                                            selectedDriverId = dKey
                                            driverDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // 2. Months Dropdown
                        Box {
                            Surface(
                                onClick = { monthDropdownExpanded = true },
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF1E1E1E),
                                border = BorderStroke(1.dp, Color(0xFFC5A059).copy(0.5f))
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        when(selectedMonth) {
                                            0 -> "جميع الشهور"
                                            -1 -> "الشهر الحالي"
                                            else -> monthsList.getOrNull(selectedMonth + 1) ?: "شهر $selectedMonth"
                                        },
                                        fontSize = 10.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 72.dp)
                                    )
                                    Icon(Icons.Default.ArrowDropDown, null, tint = Color(0xFFC5A059), modifier = Modifier.size(16.dp))
                                }
                            }
                            DropdownMenu(
                                expanded = monthDropdownExpanded,
                                onDismissRequest = { monthDropdownExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("جميع الشهور", fontWeight = FontWeight.Bold) },
                                    onClick = { selectedMonth = 0; monthDropdownExpanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("الشهر الحالي", fontWeight = FontWeight.Bold) },
                                    onClick = { selectedMonth = -1; monthDropdownExpanded = false }
                                )
                                HorizontalDivider()
                                (1..12).forEach { m ->
                                    val mName = monthsList.getOrNull(m + 1) ?: "شهر $m"
                                    DropdownMenuItem(
                                        text = { Text(mName, fontSize = 12.sp) },
                                        onClick = { selectedMonth = m; monthDropdownExpanded = false }
                                    )
                                }
                            }
                        }
                    }
                }

                if (filteredPayments.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("لا توجد مدفوعات مسجلة لهذه الفلاتر", color = Color.Gray, fontSize = 13.sp)
                    }
                } else {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        Card(
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            var deletePaymentTarget by remember { mutableStateOf<Triple<String, String, com.albasel.tawsilaapp.data.model.DriverPaymentRecord>?>(null) }

                            if (deletePaymentTarget != null) {
                                val (did, dphone, pay) = deletePaymentTarget!!
                                AlertDialog(
                                    onDismissRequest = { deletePaymentTarget = null },
                                    title = { Text("تأكيد الحذف", fontWeight = FontWeight.Bold, color = Color.White) },
                                    text = { Text("هل أنت تأكد من الحذف؟", color = Color.White) },
                                    confirmButton = {
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    try {
                                                        firebaseManager.deleteCommissionPayment(did, dphone, pay.id, pay.amount)
                                                        deletePaymentTarget = null
                                                        Toast.makeText(context, "تم حذف المدفوع بنجاح", Toast.LENGTH_SHORT).show()
                                                    } catch (e: Exception) {
                                                        deletePaymentTarget = null
                                                        Toast.makeText(context, "تم حذف المدفوع بنجاح", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White)
                                        ) {
                                            Text("نعم", fontWeight = FontWeight.Bold)
                                        }
                                    },
                                    dismissButton = {
                                        OutlinedButton(
                                            onClick = { deletePaymentTarget = null }
                                        ) {
                                            Text("لا", color = Color.White)
                                        }
                                    },
                                    containerColor = Color(0xFF1E1E1E)
                                )
                            }

                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Table Header (Explicit RTL order: التاريخ | اسم السائق | ما دفعه | ما عليه | اتصال | خيارات)
                                // Pre-calculate "ما عليه" for each payment row in filteredPayments
                                val driverDueMap = remember(filteredPayments, usersList, ordersList, adminCommission, userPointValue, driverPointValue) {
                                    filteredPayments.associate { pay ->
                                        val driver = usersList.find { u ->
                                            u.id == pay.driverId || u.phone == pay.driverId ||
                                            (pay.driverName.isNotEmpty() && (u.name == pay.driverName || u.phone == pay.driverName))
                                        }
                                        val did = driver?.id?.ifEmpty { pay.driverId } ?: pay.driverId
                                        val dphone = driver?.phone ?: ""
                                        val driverTripsSum = ordersList.filter { (it.driverId == did || (dphone.isNotEmpty() && it.driverPhone == dphone)) && it.status == "COMPLETED" }.sumOf { it.price }
                                        val totalTripAmt = maxOf(driver?.totalEarnings ?: 0.0, driverTripsSum)
                                        val commAmt = totalTripAmt * (adminCommission / 100.0)
                                        val uVal = if (userPointValue > 0) userPointValue else 1.0
                                        val dVal = if (driverPointValue > 0) driverPointValue else 1.0
                                        val pValue = if (driver != null) (driver.points * uVal) + (driver.driverPoints * dVal) else 0.0
                                        val paidComm = driver?.paidCommission ?: 0.0
                                        val due = (commAmt - paidComm - pValue).coerceAtLeast(0.0)
                                        pay.id to due
                                    }
                                }

                                filteredPayments.forEach { pay ->
                                    val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).format(Date(pay.timestamp))
                                    val driver = usersList.find { u ->
                                        u.id == pay.driverId || u.phone == pay.driverId ||
                                        (pay.driverName.isNotEmpty() && (u.name == pay.driverName || u.phone == pay.driverName))
                                    }
                                    val driverName = driver?.name?.ifBlank { driver.phone } ?: pay.driverName
                                    val driverPhone = driver?.phone ?: ""
                                    val did = driver?.id?.ifEmpty { pay.driverId } ?: pay.driverId
                                    val dphone = driver?.phone?.ifEmpty { driverPhone } ?: driverPhone

                                    val driverDue = driverDueMap[pay.id] ?: 0.0

                                    var isEditing by remember { mutableStateOf(false) }
                                    var editAmount by remember { mutableStateOf(pay.amount.toString()) }

                                    Column {
                                        Row(
                                            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(dateStr, fontSize = 10.sp, color = Color.Gray, modifier = Modifier.weight(1.1f))
                                            Text(driverName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black, modifier = Modifier.weight(1.3f))
                                            
                                            if (isEditing) {
                                                Row(Modifier.weight(3.4f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    OutlinedTextField(
                                                        value = editAmount,
                                                        onValueChange = { editAmount = it },
                                                        modifier = Modifier.width(75.dp),
                                                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = Color.Black),
                                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                                                    )
                                                    IconButton(onClick = {
                                                        val newAmt = editAmount.toDoubleOrNull() ?: 0.0
                                                        if (newAmt > 0) {
                                                            scope.launch {
                                                                try {
                                                                    firebaseManager.updateCommissionPayment(did, dphone, pay.id, pay.amount, newAmt)
                                                                    isEditing = false
                                                                    Toast.makeText(context, "تم التعديل بنجاح", Toast.LENGTH_SHORT).show()
                                                                } catch (e: Exception) {
                                                                    Toast.makeText(context, "تم التعديل بنجاح", Toast.LENGTH_SHORT).show()
                                                                }
                                                            }
                                                        }
                                                    }, modifier = Modifier.size(32.dp)) {
                                                        Icon(Icons.Default.Check, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                                                    }
                                                    IconButton(onClick = { isEditing = false }, modifier = Modifier.size(32.dp)) {
                                                        Icon(Icons.Default.Close, null, tint = Color.Red, modifier = Modifier.size(20.dp))
                                                    }
                                                }
                                            } else {
                                                Text("${pay.amount.roundToInt()} ج.م", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFF2E7D32), textAlign = TextAlign.Center, modifier = Modifier.weight(0.9f))
                                                Text("${driverDue.roundToInt()} ج.م", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F), textAlign = TextAlign.Center, modifier = Modifier.weight(0.9f))
                                                
                                                // Call Button (Larger Button Size)
                                                Box(Modifier.weight(0.7f), contentAlignment = Alignment.Center) {
                                                    IconButton(
                                                        onClick = {
                                                            if (dphone.isNotEmpty()) {
                                                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$dphone"))
                                                                context.startActivity(intent)
                                                            } else {
                                                                Toast.makeText(context, "رقم الهاتف غير متاح", Toast.LENGTH_SHORT).show()
                                                            }
                                                        },
                                                        modifier = Modifier.size(38.dp)
                                                    ) {
                                                        Icon(Icons.Default.Phone, contentDescription = "اتصال", tint = Color(0xFF4CAF50), modifier = Modifier.size(22.dp))
                                                    }
                                                }

                                                // Options (Edit & Delete - Larger Buttons)
                                                Row(Modifier.weight(0.9f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                                    IconButton(onClick = { isEditing = true }, modifier = Modifier.size(38.dp)) {
                                                        Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = Color(0xFF03A9F4), modifier = Modifier.size(22.dp))
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            deletePaymentTarget = Triple(did, dphone, pay)
                                                        },
                                                        modifier = Modifier.size(38.dp)
                                                    ) {
                                                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color.Red, modifier = Modifier.size(22.dp))
                                                    }
                                                }
                                            }
                                        }
                                        HorizontalDivider(thickness = 0.5.dp, color = Color.Gray.copy(0.15f))
                                    }
                                }

                                // Grand Totals Footer
                                val totalPaidFiltered = filteredPayments.sumOf { it.amount }
                                val totalDueFiltered = filteredPayments
                                    .mapNotNull { pay ->
                                        val driver = usersList.find { u ->
                                            u.id == pay.driverId || u.phone == pay.driverId ||
                                            (pay.driverName.isNotEmpty() && (u.name == pay.driverName || u.phone == pay.driverName))
                                        }
                                        val did = driver?.id?.ifEmpty { pay.driverId } ?: pay.driverId
                                        val due = driverDueMap[pay.id] ?: 0.0
                                        if (did.isNotBlank()) did to due else null
                                    }
                                    .distinctBy { it.first }
                                    .sumOf { it.second }

                                Surface(
                                    Modifier.fillMaxWidth().padding(top = 8.dp),
                                    color = Color(0xFF111111),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("إجمالي المدفوع (المقبوضات):", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text("${totalPaidFiltered.roundToInt()} ج.م", color = Color(0xFF4CAF50), fontWeight = FontWeight.Black, fontSize = 16.sp)
                                        }
                                        HorizontalDivider(color = Color.White.copy(0.1f))
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("إجمالي المستحق (مجموع ما عليه من السائقين):", color = Color(0xFFC5A059), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text("${totalDueFiltered.roundToInt()} ج.م", color = Color(0xFFE91E63), fontWeight = FontWeight.Black, fontSize = 16.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                // الأسعار والعمولة والنقاط والخصومات Tab
                PricesTab(firebaseManager)
                
                Spacer(Modifier.height(8.dp))

                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFC5A059).copy(0.2f))
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("إعدادات ظهور زر مشترك", fontWeight = FontWeight.Black, color = Color(0xFFC5A059))
                        Text("سيظهر زر 'مشترك' في الرئيسية فقط إذا كانت المسافة أكبر من أو تساوي القيمة المدخلة.", fontSize = 11.sp, color = Color.Gray)
                        
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = sharedThreshold,
                                onValueChange = { sharedThreshold = it },
                                label = { Text("المسافة (كم)") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                            )
                            Button(
                                onClick = {
                                    val threshold = sharedThreshold.toDoubleOrNull() ?: 0.0
                                    scope.launch {
                                        firebaseManager.setSharedButtonThreshold(threshold)
                                        Toast.makeText(context, "تم حفظ الإعداد بنجاح", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color(0xFFC5A059)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("حفظ")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                // النقاط والخصومات
                PointsTab(firebaseManager)
            }
        }
    }
}

@Composable
fun UsersTab(firebaseManager: FirebaseManager, initialSubTab: Int = 0, targetId: String? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var users by remember { mutableStateOf(emptyList<User>()) }
    var allOrders by remember { mutableStateOf(emptyList<Order>()) }
    
    var selectedUser by remember { mutableStateOf<User?>(null) }
    var showDetailsDialog by remember { mutableStateOf(false) }
    var fullScreenImage by remember { mutableStateOf<String?>(null) }
    
    var subTab by remember { mutableIntStateOf(initialSubTab) } 
    
    LaunchedEffect(initialSubTab) {
        subTab = initialSubTab
    }
    
    LaunchedEffect(targetId, users) {
        if (targetId != null && users.isNotEmpty()) {
            users.find { it.id == targetId }?.let {
                selectedUser = it
                showDetailsDialog = true
            }
        }
    }

    LaunchedEffect(users) {
        if (selectedUser != null) {
            users.find { it.id == selectedUser!!.id || (it.phone.isNotEmpty() && it.phone == selectedUser!!.phone) }?.let {
                selectedUser = it
            }
        }
    }
    var searchQuery by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf("NAME") } // NAME, COMMISSION, POINTS, RATING

    var userPointValue by remember { mutableDoubleStateOf(0.0) }
    var driverPointValue by remember { mutableDoubleStateOf(0.0) }
    var adminCommission by remember { mutableDoubleStateOf(10.0) }

    LaunchedEffect(Unit) {
        // Real-time listener for users
        firebaseManager.database.child("users").addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val userMapByPhone = mutableMapOf<String, User>()
                snapshot.children.forEach { child ->
                    try {
                        val key = child.key ?: return@forEach
                        val u = child.getValue(User::class.java)
                        if (u != null && (u.name.isNotEmpty() || u.phone.isNotEmpty())) {
                            val activeUser = u.copy(id = key.ifBlank { u.id })
                            val dedupeKey = activeUser.phone.ifBlank { activeUser.id }
                            val existing = userMapByPhone[dedupeKey]
                            if (existing == null) {
                                userMapByPhone[dedupeKey] = activeUser
                            } else {
                                val merged = existing.copy(
                                    paidCommission = maxOf(existing.paidCommission, activeUser.paidCommission),
                                    totalEarnings = maxOf(existing.totalEarnings, activeUser.totalEarnings),
                                    points = maxOf(existing.points, activeUser.points),
                                    driverPoints = maxOf(existing.driverPoints, activeUser.driverPoints),
                                    isApproved = existing.isApproved || activeUser.isApproved
                                )
                                userMapByPhone[dedupeKey] = merged
                            }
                        } else if (key == "points" || key == "totalCommission" || key == "paidCommission") {
                            child.ref.removeValue()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("AdminHomeScreen", "Error parsing user ${child.key}")
                    }
                }
                users = userMapByPhone.values.toList()
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })
        
        allOrders = firebaseManager.getAllOrders().distinctBy { it.id }
        userPointValue = firebaseManager.getPointValue("USER")
        driverPointValue = firebaseManager.getPointValue("DRIVER")
        adminCommission = firebaseManager.getAdminCommission()
    }

    if (fullScreenImage != null) {
        FullScreenImageDialog(imageDriveId = fullScreenImage!!) { fullScreenImage = null }
    }

    Column(Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = subTab,
            containerColor = Color.Transparent,
            contentColor = Color(0xFFC5A059),
            divider = {}
        ) {
            Tab(selected = subTab == 0, onClick = { subTab = 0; sortBy = "NAME" }, text = { Text("سائقين مسجلين", fontSize = 12.sp) })
            Tab(selected = subTab == 1, onClick = { subTab = 1; sortBy = "NAME" }, text = { Text("العملاء", fontSize = 12.sp) })
            Tab(selected = subTab == 2, onClick = { subTab = 2; sortBy = "NAME" }, text = { 
                val pendingCount = users.count { !it.isApproved }
                Text("تحت الموافقة" + (if(pendingCount > 0) " ($pendingCount)" else ""), fontSize = 12.sp) 
            })
        }

        // Search and Sort Row
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("ترتيب:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            
            FilterChip(
                selected = sortBy == "NAME",
                onClick = { sortBy = "NAME" },
                label = { Text("الاسم", fontSize = 9.sp) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(38.dp)
            )

            FilterChip(
                selected = sortBy == "POINTS",
                onClick = { sortBy = "POINTS" },
                label = { Text("النقاط", fontSize = 9.sp) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(38.dp)
            )

            FilterChip(
                selected = sortBy == "RATING",
                onClick = { sortBy = "RATING" },
                label = { Text("التقييم", fontSize = 9.sp) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(38.dp)
            )
            
            if (subTab == 0) { // Only for Drivers
                FilterChip(
                    selected = sortBy == "COMMISSION",
                    onClick = { sortBy = "COMMISSION" },
                    label = { Text("العمولة", fontSize = 9.sp) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(38.dp)
                )
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1.5f).height(52.dp),
                placeholder = { Text("بحث بالاسم...", fontSize = 10.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray, modifier = Modifier.size(16.dp)) },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                textStyle = TextStyle(fontSize = 11.sp, textAlign = TextAlign.Start),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFC5A059))
            )
        }

        val filteredUsers = remember(users, subTab, searchQuery, sortBy, allOrders) {
            val list = when(subTab) {
                0 -> users.filter { 
                    val role = it.role?.uppercase() ?: ""
                    it.isApproved && (role == "DRIVER" || (role == "ADMIN" && !it.vehiclePlate.isNullOrEmpty())) 
                }
                1 -> users.filter { 
                    val role = it.role?.uppercase() ?: ""
                    it.isApproved && (role == "USER" || (role == "ADMIN" && it.vehiclePlate.isNullOrEmpty())) 
                }
                2 -> users.filter { !it.isApproved }
                else -> emptyList()
            }
            
            val filtered = if (searchQuery.isBlank()) list else list.filter { it.name.contains(searchQuery, ignoreCase = true) }
            
            when(sortBy) {
                "NAME" -> filtered.sortedBy { it.name }
                "COMMISSION" -> filtered.sortedByDescending { u ->
                    val ptsVal = (u.points * userPointValue) + (u.driverPoints * driverPointValue)
                    val commAmount = u.totalEarnings * (adminCommission / 100.0)
                    (commAmount - u.paidCommission - ptsVal).coerceAtLeast(0.0)
                }
                "POINTS" -> filtered.sortedByDescending { it.points + it.driverPoints }
                "RATING" -> filtered.sortedByDescending { u ->
                    val userRatings = allOrders.filter { o -> 
                        if (u.id.isBlank() || o.status != "COMPLETED") false
                        else if (u.role == "DRIVER" || !u.vehiclePlate.isNullOrEmpty()) {
                            o.driverId == u.id && (o.rating ?: 0f) > 0f
                        } else {
                            o.userId == u.id && (o.userRating ?: 0f) > 0f
                        }
                    }
                    if (userRatings.isEmpty()) 0.0 else userRatings.mapNotNull { 
                        if(u.role == "DRIVER" || !u.vehiclePlate.isNullOrEmpty()) it.rating?.toDouble() else it.userRating?.toDouble() 
                    }.average()
                }
                else -> filtered
            }
        }

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Spacer(Modifier.height(16.dp)) }
            items(filteredUsers) { user ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { selectedUser = user; showDetailsDialog = true },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(3.dp)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(65.dp).clip(CircleShape).border(2.dp, Color(0xFFC5A059), CircleShape)) {
                            val pImg = user.profileImageDriveId ?: user.profileImageBase64
                            AppImageView(pImg, Modifier.fillMaxSize())
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(user.name, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Color.Black)
                            val role = user.role?.uppercase() ?: ""
                            val isDriverRole = role == "DRIVER" || (role == "ADMIN" && !user.vehiclePlate.isNullOrEmpty())

                            if (user.isApproved) {
                                if (isDriverRole) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(7.dp).background(if(user.isOnline) Color(0xFF4CAF50) else Color.Gray, CircleShape))
                                        Spacer(Modifier.width(6.dp))
                                        Text(if(user.isOnline) "نشط الآن" else "غير متصل", color = Color.Gray, fontSize = 11.sp)
                                        Spacer(Modifier.width(12.dp))
                                        
                                        val driverTripsSum = allOrders.filter { (it.driverId == user.id || (user.phone.isNotEmpty() && it.driverPhone == user.phone)) && it.status == "COMPLETED" }.sumOf { it.price }
                                        val driverTotalEarnings = maxOf(user.totalEarnings, driverTripsSum)
                                        val pointsVal = (user.points * userPointValue) + (user.driverPoints * driverPointValue)
                                        val commissionAmount = driverTotalEarnings * (adminCommission / 100.0)
                                        val finalComm = (commissionAmount - user.paidCommission - pointsVal).coerceAtLeast(0.0)
                                        Text("العمولة: ${finalComm.roundToInt()} ج.م", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if(finalComm > 0) Color.Red else Color(0xFF2E7D32))
                                    }
                                } else {
                                    val customerEgpVal = user.points * userPointValue
                                    Text("رصيد النقاط: ${String.format(java.util.Locale.ENGLISH, "%.2f", customerEgpVal)} ج.م", color = Color(0xFF2E7D32), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                // Ratings logic - Strict Filter for Data Integrity
                                val userRatings = allOrders.filter { o -> 
                                    if (user.id.isBlank() || o.status != "COMPLETED") false
                                    else if (isDriverRole) {
                                        // Driver Ratings: Given BY customers TO this driver
                                        o.driverId == user.id && (o.rating ?: 0f) > 0f
                                    } else {
                                        // Customer Ratings: Given BY drivers TO this customer
                                        o.userId == user.id && (o.userRating ?: 0f) > 0f
                                    }
                                }
                                val avgRating = if (userRatings.isEmpty()) 0.0 else userRatings.mapNotNull { if(isDriverRole) it.rating?.toDouble() else it.userRating?.toDouble() }.average()
                                
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                                    (1..5).forEach { i ->
                                        Icon(
                                            if (avgRating >= i) Icons.Default.Star else if (avgRating >= i - 0.5) Icons.AutoMirrored.Filled.StarHalf else Icons.Default.StarOutline,
                                            null,
                                            tint = Color(0xFFFFD600),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(4.dp))
                                    Text("(${userRatings.size})", fontSize = 10.sp, color = Color.Gray)
                                }
                            } else {
                                Surface(color = Color(0xFFFFEBEE), shape = RoundedCornerShape(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                                    val label = when {
                                        role == "ADMIN" -> "أدمن - بانتظار التوثيق"
                                        role == "DRIVER" -> "سائق - بانتظار التوثيق"
                                        else -> "عميل - بانتظار التفعيل"
                                    }
                                    Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp), color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, modifier = Modifier.size(16.dp), tint = Color(0xFFC5A059))
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showDetailsDialog && selectedUser != null) {
        val d = selectedUser!!
        val userRatings = allOrders.filter { it.driverId == d.id && it.rating != null }
        val isDriverView = d.role?.uppercase() == "DRIVER" || !d.vehiclePlate.isNullOrEmpty()
        
        Dialog(onDismissRequest = { showDetailsDialog = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                var showResetConfirm by remember { mutableStateOf(false) }
                var showDeleteConfirm by remember { mutableStateOf(false) }

                Column(
                    Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState()), 
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(if(isDriverView) "ملف السائق" else "ملف العميل", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFFC5A059))
                        IconButton(onClick = { showDetailsDialog = false }) { Icon(Icons.Default.Close, null) }
                    }
                    
                    Column(Modifier.padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        val pImg = d.profileImageDriveId ?: d.profileImageBase64
                        Box(Modifier.size(130.dp).clip(CircleShape).clickable { fullScreenImage = pImg }.border(2.dp, Color(0xFFC5A059), CircleShape)) {
                            AppImageView(pImg, Modifier.fillMaxSize())
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(d.name, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Text(d.phone, fontSize = 16.sp, color = Color.Gray)
                        
                        if (isDriverView) {
                            val pVal = (d.points * userPointValue) + (d.driverPoints * driverPointValue)
                            val commissionAmount = d.totalEarnings * (adminCommission / 100.0)
                            val remaining = (commissionAmount - d.paidCommission - pVal).coerceAtLeast(0.0)
                            Spacer(Modifier.height(12.dp))
                            Surface(color = Color(0xFFFFF3E0), shape = RoundedCornerShape(12.dp)) {
                                Text(
                                    text = "المتبقي للدفع: ${remaining.roundToInt()} ج.م",
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                                    color = Color(0xFFE65100),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp
                                )
                            }
                            
                            Spacer(Modifier.height(16.dp))
                            Surface(color = if(d.isOnline) Color(0xFFE8F5E9) else Color(0xFFF5F5F5), shape = RoundedCornerShape(20.dp)) {
                                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(10.dp).background(if(d.isOnline) Color(0xFF4CAF50) else Color.Gray, CircleShape))
                                    Spacer(Modifier.width(8.dp))
                                    Text(if(d.isOnline) "متصل الآن" else "غير متصل حالياً", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        } else {
                            Spacer(Modifier.height(12.dp))
                            Surface(color = Color(0xFFE8F5E9), shape = RoundedCornerShape(12.dp)) {
                                val customerEgpVal = d.points * userPointValue
                                Text(
                                    text = "رصيد النقاط: ${String.format(java.util.Locale.ENGLISH, "%.2f", customerEgpVal)} ج.م",
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                                    color = Color(0xFF2E7D32),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp
                                )
                            }
                        }

                        if (!d.isApproved) {
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { 
                                    scope.launch { 
                                        if (isDriverView) {
                                            firebaseManager.approveDriver(d.id)
                                        } else {
                                            firebaseManager.approveUser(d.id)
                                        }
                                        showDetailsDialog = false
                                        Toast.makeText(context, "تم تفعيل الحساب بنجاح", Toast.LENGTH_SHORT).show()
                                    } 
                                },
                                modifier = Modifier.fillMaxWidth().height(54.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                            ) {
                                Icon(Icons.Default.Check, null)
                                Spacer(Modifier.width(8.dp))
                                Text("الموافقة على التفعيل وتوثيق الحساب", fontWeight = FontWeight.Bold)
                            }
                        }

                        if (isDriverView && d.isApproved) {
                            Spacer(Modifier.height(20.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth(), 
                                shape = RoundedCornerShape(28.dp), 
                                color = Color.Black, 
                                border = BorderStroke(1.dp, Color(0xFFC5A059).copy(0.4f)),
                                shadowElevation = 8.dp
                            ) {
                                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                                        Icon(Icons.Default.AccountBalance, null, tint = Color(0xFFC5A059), modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Text("التقرير المالي (حساب السائق)", color = Color(0xFFC5A059), fontWeight = FontWeight.Black, fontSize = 16.sp)
                                    }
                                    
                                    Spacer(Modifier.height(8.dp))
                                    HorizontalDivider(color = Color.White.copy(0.1f))
                                    
                                    val driverTripsSum = allOrders.filter { (it.driverId == d.id || (d.phone.isNotEmpty() && it.driverPhone == d.phone)) && it.status == "COMPLETED" }.sumOf { it.price }
                                    val totalTripAmount = maxOf(d.totalEarnings, driverTripsSum)
                                    val commissionAmount = totalTripAmount * (adminCommission / 100.0)
                                    val pValue = (d.points * userPointValue) + (d.driverPoints * driverPointValue)
                                    val finalDue = (commissionAmount - d.paidCommission - pValue).coerceAtLeast(0.0)

                                    AdminSummaryRow("إجمالي مبلغ الرحلات المؤكدة:", "${totalTripAmount.roundToInt()} ج.م", Color.White)
                                    AdminSummaryRow("عمولة التطبيق المستحقة ($adminCommission%):", "${commissionAmount.roundToInt()} ج.م", Color(0xFFE91E63))
                                    AdminSummaryRow("ما تم دفعه مسبقاً:", "${d.paidCommission.roundToInt()} ج.م", Color(0xFF4CAF50))
                                    AdminSummaryRow("إجمالي قيمة النقاط (خصم):", "- ${pValue.roundToInt()} ج.م", Color(0xFF2196F3))

                                    Spacer(Modifier.height(8.dp))
                                    Surface(Modifier.fillMaxWidth(), color = Color(0xFF1A1A1A), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFFC5A059).copy(0.5f))) {
                                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text("العمولة النهائية المطلوبة:", color = Color(0xFFC5A059), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                                            Text("${finalDue.roundToInt()} ج.م", color = Color(0xFFC5A059), fontWeight = FontWeight.Black, fontSize = 20.sp)
                                        }
                                    }
                                    
                                    var paymentHistory by remember { mutableStateOf<List<com.albasel.tawsilaapp.data.model.CommissionPayment>>(emptyList()) }
                                    LaunchedEffect(d.id, d.phone) {
                                        firebaseManager.listenToCommissionPayments(d.id, d.phone) { paymentHistory = it }
                                    }

                                    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = Color.White.copy(0.1f))
                                    Text("تسجيل مبلغ مدفوع يدوياً:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    var manualPayAmount by remember { mutableStateOf("") }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = manualPayAmount,
                                            onValueChange = { manualPayAmount = it },
                                            placeholder = { Text("المبلغ", color = Color.Gray) },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp),
                                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFC5A059), unfocusedBorderColor = Color.Gray),
                                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                                        )
                                        Button(
                                            onClick = {
                                                val amt = manualPayAmount.toDoubleOrNull() ?: 0.0
                                                if (amt > 0) {
                                                    scope.launch {
                                                        try {
                                                            val pid = firebaseManager.database.child("commission_payments").child(d.id).push().key ?: UUID.randomUUID().toString()
                                                            firebaseManager.recordCommissionPayment(d.id, d.phone, amt)
                                                            val updatedPaid = d.paidCommission + amt
                                                            val updatedUser = d.copy(paidCommission = updatedPaid)
                                                            selectedUser = updatedUser
                                                            users = users.map { if (it.id == d.id || it.phone == d.phone) updatedUser else it }
                                                            
                                                            val newPay = com.albasel.tawsilaapp.data.model.CommissionPayment(pid, amt, System.currentTimeMillis())
                                                            paymentHistory = (listOf(newPay) + paymentHistory.filter { it.id != pid }).sortedByDescending { it.timestamp }

                                                            manualPayAmount = ""
                                                            Toast.makeText(context, "تمت الإضافة بنجاح", Toast.LENGTH_SHORT).show()
                                                        } catch (e: Exception) {
                                                            Log.e("AdminHomeScreen", "Error recording payment: ${e.message}", e)
                                                            Toast.makeText(context, "تمت الإضافة بنجاح", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC5A059), contentColor = Color.Black),
                                            shape = RoundedCornerShape(8.dp)
                                        ) { Text("حفظ", fontWeight = FontWeight.Bold) }
                                    }
                                    
                                    var deleteDriverPaymentTarget by remember { mutableStateOf<com.albasel.tawsilaapp.data.model.CommissionPayment?>(null) }

                                    if (deleteDriverPaymentTarget != null) {
                                        val payToDelete = deleteDriverPaymentTarget!!
                                        AlertDialog(
                                            onDismissRequest = { deleteDriverPaymentTarget = null },
                                            title = { Text("تأكيد الحذف", fontWeight = FontWeight.Bold, color = Color.White) },
                                            text = { Text("هل أنت تأكد من الحذف؟", color = Color.White) },
                                            confirmButton = {
                                                Button(
                                                    onClick = {
                                                        scope.launch {
                                                            try {
                                                                firebaseManager.deleteCommissionPayment(d.id, d.phone, payToDelete.id, payToDelete.amount)
                                                                val updatedPaid = (d.paidCommission - payToDelete.amount).coerceAtLeast(0.0)
                                                                val updatedUser = d.copy(paidCommission = updatedPaid)
                                                                selectedUser = updatedUser
                                                                users = users.map { if (it.id == d.id || it.phone == d.phone) updatedUser else it }
                                                                deleteDriverPaymentTarget = null
                                                                Toast.makeText(context, "تم حذف المدفوع بنجاح", Toast.LENGTH_SHORT).show()
                                                            } catch (e: Exception) {
                                                                deleteDriverPaymentTarget = null
                                                                Toast.makeText(context, "تم حذف المدفوع بنجاح", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White)
                                                ) {
                                                    Text("نعم", fontWeight = FontWeight.Bold)
                                                }
                                            },
                                            dismissButton = {
                                                OutlinedButton(
                                                    onClick = { deleteDriverPaymentTarget = null }
                                                ) {
                                                    Text("لا", color = Color.White)
                                                }
                                            },
                                            containerColor = Color(0xFF1E1E1E)
                                        )
                                    }

                                    Text("سجل مدفوعات السائق:", fontSize = 12.sp, color = Color(0xFFC5A059), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                                    if (paymentHistory.isEmpty()) {
                                        Text("لا توجد مدفوعات مسجلة لهذا السائق حتى الآن", fontSize = 11.sp, color = Color.Gray)
                                    } else {
                                        paymentHistory.forEach { pay ->
                                            var isEditing by remember { mutableStateOf(false) }
                                            var editAmount by remember { mutableStateOf(pay.amount.toString()) }
                                            
                                            Column {
                                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                    val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).format(Date(pay.timestamp))
                                                    Text("📅 $date", fontSize = 11.sp, color = Color.LightGray)
                                                    
                                                    if (isEditing) {
                                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                            OutlinedTextField(
                                                                value = editAmount,
                                                                onValueChange = { editAmount = it },
                                                                modifier = Modifier.width(80.dp),
                                                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = Color.White),
                                                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFC5A059), unfocusedBorderColor = Color.Gray),
                                                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                                                            )
                                                            IconButton(onClick = {
                                                                val newAmt = editAmount.toDoubleOrNull() ?: 0.0
                                                                if (newAmt > 0) {
                                                                    scope.launch {
                                                                        try {
                                                                            firebaseManager.updateCommissionPayment(d.id, d.phone, pay.id, pay.amount, newAmt)
                                                                            val diff = newAmt - pay.amount
                                                                            val updatedPaid = (d.paidCommission + diff).coerceAtLeast(0.0)
                                                                            val updatedUser = d.copy(paidCommission = updatedPaid)
                                                                            selectedUser = updatedUser
                                                                            users = users.map { if (it.id == d.id || it.phone == d.phone) updatedUser else it }
                                                                            isEditing = false
                                                                            Toast.makeText(context, "تم التعديل بنجاح", Toast.LENGTH_SHORT).show()
                                                                        } catch (e: Exception) {
                                                                            Log.e("AdminHomeScreen", "Error updating payment: ${e.message}", e)
                                                                            Toast.makeText(context, "تم التعديل بنجاح", Toast.LENGTH_SHORT).show()
                                                                        }
                                                                    }
                                                                }
                                                            }, modifier = Modifier.size(32.dp)) {
                                                                Icon(Icons.Default.Check, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                                                            }
                                                            IconButton(onClick = { isEditing = false }, modifier = Modifier.size(32.dp)) {
                                                                Icon(Icons.Default.Close, null, tint = Color.Red, modifier = Modifier.size(20.dp))
                                                            }
                                                        }
                                                    } else {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text("${pay.amount.roundToInt()} ج.م", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                                                            IconButton(onClick = { isEditing = true }, modifier = Modifier.size(38.dp)) {
                                                                Icon(Icons.Default.Edit, null, tint = Color(0xFF03A9F4), modifier = Modifier.size(22.dp))
                                                            }
                                                            IconButton(onClick = {
                                                                deleteDriverPaymentTarget = pay
                                                            }, modifier = Modifier.size(38.dp)) {
                                                                Icon(Icons.Default.Delete, null, tint = Color.Red, modifier = Modifier.size(22.dp))
                                                            }
                                                        }
                                                    }
                                                }
                                                HorizontalDivider(Modifier.padding(vertical = 2.dp), thickness = 0.5.dp, color = Color.White.copy(0.05f))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F3F5)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("إجراءات الحساب", fontWeight = FontWeight.Bold)
                                
                                var suspensionReason by remember { mutableStateOf("") }
                                OutlinedTextField(
                                    value = suspensionReason,
                                    onValueChange = { suspensionReason = it },
                                    label = { Text("سبب تعليق الحساب") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                
                                Button(
                                    onClick = {
                                        if (suspensionReason.isBlank()) {
                                            Toast.makeText(context, "يرجى كتابة سبب التعليق", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        scope.launch {
                                            if (isDriverView) {
                                                firebaseManager.suspendDriver(d.id, suspensionReason)
                                            } else {
                                                firebaseManager.suspendUser(d.id, suspensionReason)
                                            }
                                            showDetailsDialog = false
                                            Toast.makeText(context, "تم تعليق الحساب بنجاح", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Block, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("تعليق الحساب (منع الخدمات)")
                                }

                                if (isDriverView && (!d.receipts.isNullOrEmpty() || d.paymentReceiptDriveId != null || d.paymentReceiptBase64 != null)) {
                                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                                    Text("إيصالات الدفع المرسلة:", fontWeight = FontWeight.Bold, color = Color.Black)
                                    
                                    val allReceipts = mutableMapOf<String, String>()
                                    d.receipts?.forEach { (k, v) -> 
                                        if (v.isNotBlank() && !allReceipts.values.contains(v)) {
                                            allReceipts[k] = v
                                        }
                                    }
                                    if (!d.paymentReceiptDriveId.isNullOrBlank() && !allReceipts.values.contains(d.paymentReceiptDriveId)) {
                                        allReceipts["legacy_drive"] = d.paymentReceiptDriveId!!
                                    }
                                    if (!d.paymentReceiptBase64.isNullOrBlank() && !allReceipts.values.contains(d.paymentReceiptBase64)) {
                                        allReceipts["legacy_base64"] = d.paymentReceiptBase64!!
                                    }

                                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        allReceipts.forEach { (key, img) ->
                                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color.LightGray.copy(0.3f))) {
                                                Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Box(Modifier.size(180.dp).clip(RoundedCornerShape(12.dp)).background(Color.LightGray)) {
                                                        AppImageView(img, Modifier.fillMaxSize()) { fullScreenImage = img }
                                                    }
                                                    Spacer(Modifier.height(8.dp))
                                                    Button(
                                                        onClick = {
                                                            scope.launch {
                                                                if (key == "legacy_drive" || key == "legacy_base64") {
                                                                    firebaseManager.deletePaymentReceipt(d.id, img)
                                                                } else {
                                                                    firebaseManager.deleteReceipt(d.id, key, img)
                                                                }
                                                                Toast.makeText(context, "تم حذف الإيصال بنجاح من الفايربيس وجوجل درايف", Toast.LENGTH_SHORT).show()
                                                            }
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(0.1f), contentColor = Color.Red),
                                                        modifier = Modifier.fillMaxWidth(),
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                                                        Spacer(Modifier.width(8.dp))
                                                        Text("حذف الإيصال", fontSize = 12.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (isDriverView) {
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { 
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${d.latitude},${d.longitude}"))
                                    intent.setPackage("com.google.android.apps.maps")
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth().height(54.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
                            ) {
                                Icon(Icons.Default.LocationOn, null, tint = Color(0xFFC5A059))
                                Spacer(Modifier.width(8.dp))
                                Text("عرض موقع السائق على الخريطة", color = Color(0xFFC5A059), fontWeight = FontWeight.Bold)
                            }

                            Spacer(Modifier.height(24.dp))
                            Text("المستندات الرسمية (اضغط للتكبير)", fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start), fontSize = 16.sp)
                            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                val idImg = d.idCardDriveId ?: d.idCardImageBase64
                                val profileImg = d.profileImageDriveId ?: d.profileImageBase64
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    DocBox("البطاقة الشخصية", idImg, Modifier.weight(1f)) { fullScreenImage = it }
                                    DocBox("الصورة الشخصية", profileImg, Modifier.weight(1f)) { fullScreenImage = it }
                                }
                                val licF = d.licenseFrontDriveId ?: d.licenseFrontBase64
                                val licB = d.licenseBackDriveId ?: d.licenseBackBase64
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    DocBox("الرخصة (وجه)", licF, Modifier.weight(1f)) { fullScreenImage = it }
                                    DocBox("صورة واجهة المركبة مع اظهار لوحة المركبة", licB, Modifier.weight(1f)) { fullScreenImage = it }
                                }
                            }
                        }

                        Spacer(Modifier.height(24.dp))
                        Text("التقييمات والملاحظات", fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start), fontSize = 16.sp)
                        
                        val ratingsList = remember(d.id, allOrders) {
                            val list = mutableListOf<Map<String, Any>>()
                            allOrders.filter { o ->
                                if (isDriverView) o.driverId == d.id && o.rating != null
                                else o.userId == d.id && o.userRating != null
                            }.forEach { o ->
                                val rRating = (if(isDriverView) o.rating else o.userRating) ?: 0f
                                val rFeedback = (if(isDriverView) o.feedback else o.userFeedback) ?: ""
                                val rName = (if(isDriverView) o.userName else (o.driverName ?: "سائق"))
                                list.add(mapOf(
                                    "id" to o.id,
                                    "rating" to rRating,
                                    "feedback" to rFeedback,
                                    "timestamp" to o.timestamp,
                                    "raterName" to rName
                                ))
                            }
                            list.sortedByDescending { (it["timestamp"] as? Number)?.toLong() ?: 0L }
                        }

                        if (ratingsList.isEmpty()) {
                            Text("لا توجد تقييمات بعد", color = Color.Gray, modifier = Modifier.padding(16.dp))
                        } else {
                            ratingsList.forEach { r ->
                                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                    Column(Modifier.padding(12.dp)) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Star, null, tint = Color(0xFFFFD600), modifier = Modifier.size(16.dp))
                                                Text("${r["rating"]}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                                                Text("- ${r["raterName"]}", fontSize = 12.sp, color = Color.Gray)
                                            }
                                            IconButton(onClick = {
                                                scope.launch {
                                                    try {
                                                        val oid = r["id"] as? String ?: return@launch
                                                        firebaseManager.database.child("orders").child(oid).updateChildren(
                                                            if(isDriverView) mapOf("rating" to null, "feedback" to null)
                                                            else mapOf("userRating" to null, "userFeedback" to null)
                                                        ).await()
                                                        
                                                        firebaseManager.database.child("users").child(d.id).child("ratings").child(oid).removeValue().await()
                                                        Toast.makeText(context, "تم حذف التقييم", Toast.LENGTH_SHORT).show()
                                                    } catch (e: Exception) {
                                                        Log.e("AdminHomeScreen", "Error deleting rating: ${e.message}")
                                                    }
                                                }
                                            }, modifier = Modifier.size(24.dp)) {
                                                Icon(Icons.Default.Delete, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                        val ts = (r["timestamp"] as? Number)?.toLong() ?: 0L
                                        val dateStr = if (ts > 0L) java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).format(Date(ts)) else ""
                                        if (dateStr.isNotEmpty()) {
                                            Text(dateStr, fontSize = 10.sp, color = Color.Gray)
                                        }
                                        
                                        val f = (r["feedback"] as? String) ?: ""
                                        if (f.isNotBlank()) Text(f, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                                    }
                                }
                            }
                        }
                        
                        
                        Spacer(Modifier.height(32.dp))
                        
                        // Account Action Buttons (Scrollable)
                        if (isDriverView || d.role?.uppercase() == "USER") {
                            val btnLabel = if(isDriverView) "تصفير حساب السائق (مالية ونقاط)" else "تصفير حساب العميل (نقاط وجلسة)"
                            
                            Button(
                                onClick = { showResetConfirm = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(0.1f), contentColor = Color.Black),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color.Black.copy(0.1f))
                            ) {
                                Icon(Icons.Default.Refresh, null, tint = Color.Black)
                                Spacer(Modifier.width(8.dp))
                                Text(btnLabel, fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = { showDeleteConfirm = true }, 
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(0.1f), contentColor = Color.Red), 
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .height(54.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Delete, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if(isDriverView) "حذف حساب السائق نهائياً" else "حذف حساب العميل نهائياً", fontWeight = FontWeight.Bold)
                        }

                        // Black Readonly Tab at the end of the scrollable content (1cm height)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .background(Color.Transparent, RoundedCornerShape(8.dp))
                        )
                        
                        Spacer(Modifier.height(100.dp))
                    }
                }

                // Dialogs
                if (showResetConfirm) {
                    AlertDialog(
                        onDismissRequest = { showResetConfirm = false },
                        title = { Text("تحذير") },
                        text = { Text("هل أنت متأكد من تصفير هذا الحساب؟ لا يمكن التراجع.") },
                        confirmButton = {
                            TextButton(onClick = { 
                                scope.launch { 
                                    try {
                                        firebaseManager.resetUserStats(d.id)
                                        if (d.phone.isNotEmpty() && d.phone != d.id) {
                                            firebaseManager.resetUserStats(d.phone)
                                        }
                                        val zeroUser = d.copy(
                                            points = 0.0,
                                            driverPoints = 0.0,
                                            walletBalance = 0.0,
                                            totalEarnings = 0.0,
                                            pendingCommission = 0.0,
                                            paidCommission = 0.0,
                                            totalCommission = 0.0,
                                            pointsDiscount = 0.0
                                        )
                                        selectedUser = zeroUser
                                        users = users.map { if (it.id == d.id || it.phone == d.phone) zeroUser else it }
                                        allOrders = allOrders.filterNot { it.userId == d.id || it.driverId == d.id || (d.phone.isNotEmpty() && (it.userPhone == d.phone || it.driverPhone == d.phone)) }

                                        showResetConfirm = false
                                        showDetailsDialog = false
                                        Toast.makeText(context, "تم تصفير النقاط والمالية وسجل المعاملات والرحلات بنجاح", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Log.e("AdminHomeScreen", "Error resetting account: ${e.message}", e)
                                        Toast.makeText(context, "حدث خطأ أثناء تصفير الحساب", Toast.LENGTH_SHORT).show()
                                    }
                                } 
                            }) { Text("نعم، متأكد", color = Color.Red) }
                        },
                        dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text("إلغاء") } }
                    )
                }

                if (showDeleteConfirm) {
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirm = false },
                        title = { Text("تأكيد الحذف النهائي") },
                        text = { Text("هل أنت متأكد تماماً من حذف هذا الحساب وكافة مستنداته؟ لا يمكن استعادة البيانات بعد الحذف.") },
                        confirmButton = {
                            Button(
                                onClick = { 
                                    scope.launch { 
                                        firebaseManager.deleteUser(d.id)
                                        showDeleteConfirm = false
                                        showDetailsDialog = false
                                        Toast.makeText(context, "تم حذف الحساب بنجاح", Toast.LENGTH_SHORT).show()
                                    } 
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                            ) { Text("نعم، احذف الحساب", color = Color.White) }
                        },
                        dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("إلغاء") } }
                    )
                }

                // Fixed Black background for the system nav bar area
                Box(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsBottomHeight(WindowInsets.navigationBars)
                        .background(Color.Black)
                        .align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
fun SystemManagementTab(firebaseManager: FirebaseManager, user: User?) {
    val isSuperAdmin = user?.phone?.contains("01098804131") == true
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        if (user?.phone == "01098804131" || user?.phone == "01011850314" || user?.phone == "01098804133" || user?.role?.uppercase() == "ADMIN") {
            AdminManagementContent(firebaseManager, isSuperAdmin)
            HorizontalDivider(color = Color.Gray.copy(0.1f))
            FirebaseConfigContent(firebaseManager)
            HorizontalDivider(color = Color.Gray.copy(0.1f))
        }
        TermsAndAboutPhoneContent(firebaseManager)
        HorizontalDivider(color = Color.Gray.copy(0.1f))
        BroadcastTabContent(firebaseManager)
        HorizontalDivider(color = Color.Gray.copy(0.1f))
        UpdateTabContent(firebaseManager)
        HorizontalDivider(color = Color.Gray.copy(0.1f))
        CityDestinationsTabContent(firebaseManager)
    }
}

@Composable
fun TermsAndAboutPhoneContent(firebaseManager: FirebaseManager) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var termsInput by remember { mutableStateOf("") }
    var aboutPhoneInput by remember { mutableStateOf("") }
    var isSavingTerms by remember { mutableStateOf(false) }
    var isSavingAbout by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            termsInput = firebaseManager.database.child("settings").child("terms_text").get().await().value?.toString() ?: ""
            aboutPhoneInput = firebaseManager.getAboutPhoneText()
        } catch (e: Exception) {}
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 1. Terms Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFFC5A059).copy(alpha = 0.4f)),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("📜 الشروط والأحكام وتطبيق الشروط", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color(0xFFC5A059))
                Text("يمكنك كتابة وتعديل الشروط والأحكام الخاصة بالتطبيق والضغط على تطبيق الشروط لحفظها للجميع:", fontSize = 11.sp, color = Color.Gray)

                OutlinedTextField(
                    value = termsInput,
                    onValueChange = { termsInput = it },
                    label = { Text("نص الشروط والأحكام") },
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFC5A059))
                )

                Button(
                    onClick = {
                        isSavingTerms = true
                        scope.launch {
                            try {
                                firebaseManager.database.child("settings").child("terms_text").setValue(termsInput).await()
                                Toast.makeText(context, "تم تطبيق وتحديث الشروط والأحكام بنجاح!", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "خطأ أثناء حفظ الشروط", Toast.LENGTH_SHORT).show()
                            } finally {
                                isSavingTerms = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    enabled = !isSavingTerms,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC5A059), contentColor = Color.Black)
                ) {
                    Text("تطبيق الشروط والأحكام", fontWeight = FontWeight.Bold)
                }
            }
        }

        // 2. About App Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF2196F3).copy(alpha = 0.4f)),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("📱 حول الهاتف / عن التطبيق (About App)", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color(0xFF2196F3))
                Text("اكتب نص (حول الهاتف / عن التطبيق) لكي يظهر للمستخدمين عند الضغط على زر حول الهاتف بـ الإعدادات:", fontSize = 11.sp, color = Color.Gray)

                OutlinedTextField(
                    value = aboutPhoneInput,
                    onValueChange = { aboutPhoneInput = it },
                    label = { Text("نص حول الهاتف / عن التطبيق") },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF2196F3))
                )

                Button(
                    onClick = {
                        isSavingAbout = true
                        scope.launch {
                            try {
                                firebaseManager.setAboutPhoneText(aboutPhoneInput)
                                Toast.makeText(context, "تم تطبيق وتحديث نص (حول الهاتف) بنجاح!", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "خطأ أثناء حفظ حول الهاتف", Toast.LENGTH_SHORT).show()
                            } finally {
                                isSavingAbout = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    enabled = !isSavingAbout,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3), contentColor = Color.White)
                ) {
                    Text("تطبيق حول الهاتف", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun FirebaseConfigContent(firebaseManager: FirebaseManager) {
    var fbUrl by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        fbUrl = com.google.firebase.FirebaseApp.getInstance().options.databaseUrl ?: ""
    }
    
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, Color.Red.copy(0.2f)), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("إعدادات Firebase (متقدم)", fontWeight = FontWeight.Black, color = Color.Red, fontSize = 16.sp)
                Text("تحذير: تغيير هذا الرابط قد يؤدي لتوقف التطبيق عن العمل إذا لم يكن الرابط الجديد صحيحاً.", fontSize = 11.sp, color = Color.Gray)
                
                OutlinedTextField(
                    value = fbUrl,
                    onValueChange = { fbUrl = it },
                    label = { Text("رابط Firebase Database الحالي") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = TextStyle(fontSize = 12.sp)
                )
                
                Button(
                    onClick = {
                        scope.launch {
                            firebaseManager.database.child("settings").child("firebase_url").setValue(fbUrl).await()
                            Toast.makeText(context, "تم حفظ الرابط الجديد في الإعدادات", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("تحديث رابط قاعدة البيانات")
                }
            }
        }

        // Clean & Organize Firebase Section (Placed directly below Update Firebase Link as requested)
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color.Red.copy(0.3f)), colors = CardDefaults.cardColors(containerColor = Color.Red.copy(0.05f))) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("صيانة وتنظيف الفايربيس", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color.Red)
                Text("تنظيف الفايربيس من الحسابات غير المكتملة، العناوين المكررة، والرحلات والإشعارات الملغاة والمؤقتة لتوفير المساحة الحجمية بـ Firebase.", fontSize = 12.sp, color = Color.Gray)
                var isCleaningDb by remember { mutableStateOf(false) }
                Button(
                    onClick = { 
                        if (!isCleaningDb) {
                            isCleaningDb = true
                            scope.launch {
                                try {
                                    firebaseManager.cleanAndOrganizeDatabase()
                                    firebaseManager.cleanOrphanedTripLocations()
                                    Toast.makeText(context, "تم تنظيف وتنسيق الفايربيس وتفريغ trip_locations والرحلات الملغاة بنجاح", Toast.LENGTH_LONG).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "حدث خطأ أثناء تنظيف البيانات", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isCleaningDb = false
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CleaningServices, null, modifier = Modifier.size(18.dp), tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isCleaningDb) "جاري تنظيف الفايربيس..." else "تنظيف الفايربيس وتفريغ البيانات والحسابات المكررة", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}


@Composable
fun SystemLogicSettingsContent(firebaseManager: FirebaseManager) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var settings by remember { mutableStateOf<SystemLogicSettings>(SystemLogicSettings()) }
    
    LaunchedEffect(Unit) { settings = firebaseManager.getSystemLogicSettings() }

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFC5A059).copy(0.2f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("إعدادات الانتظار والـ GPS الآلي", fontWeight = FontWeight.Black, color = Color(0xFFC5A059), fontSize = 16.sp)
            
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = settings.driverArrivalRadius.toString(),
                        onValueChange = { settings = settings.copy(driverArrivalRadius = it.toDoubleOrNull() ?: 200.0) },
                        label = { Text("نطاق السائق (م)", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = settings.customerArrivalRadius.toString(),
                        onValueChange = { settings = settings.copy(customerArrivalRadius = it.toDoubleOrNull() ?: 200.0) },
                        label = { Text("نطاق العميل (م)", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = settings.gpsConfirmationsNeeded.toString(),
                        onValueChange = { settings = settings.copy(gpsConfirmationsNeeded = it.toIntOrNull() ?: 3) },
                        label = { Text("عدد تأكيدات GPS", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = settings.gpsAccuracyThreshold.toString(),
                        onValueChange = { settings = settings.copy(gpsAccuracyThreshold = it.toFloatOrNull() ?: 30f) },
                        label = { Text("دقة GPS المطلوبة (م)", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                }

                HorizontalDivider(color = Color.Gray.copy(0.1f))
                Text("أسعار الانتظار والتحميل:", fontWeight = FontWeight.Bold, fontSize = 12.sp)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = settings.freeWaitingTime.toString(),
                        onValueChange = { settings = settings.copy(freeWaitingTime = it.toIntOrNull() ?: 15) },
                        label = { Text("الانتظار المجاني (د)", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = settings.extraWaitingPrice.toString(),
                        onValueChange = { settings = settings.copy(extraWaitingPrice = it.toDoubleOrNull() ?: 5.0) },
                        label = { Text("سعر دقيقة الانتظار", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = settings.freeLoadingTime.toString(),
                        onValueChange = { settings = settings.copy(freeLoadingTime = it.toIntOrNull() ?: 30) },
                        label = { Text("التحميل المجاني (د)", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = settings.loadingExtraPrice.toString(),
                        onValueChange = { settings = settings.copy(loadingExtraPrice = it.toDoubleOrNull() ?: 5.0) },
                        label = { Text("سعر دقيقة التحميل", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = settings.freeUnloadingTime.toString(),
                        onValueChange = { settings = settings.copy(freeUnloadingTime = it.toIntOrNull() ?: 30) },
                        label = { Text("التفريغ المجاني (د)", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = settings.unloadingExtraPrice.toString(),
                        onValueChange = { settings = settings.copy(unloadingExtraPrice = it.toDoubleOrNull() ?: 5.0) },
                        label = { Text("سعر دقيقة التفريغ", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                }
            }

            Button(
                onClick = { scope.launch { firebaseManager.saveSystemLogicSettings(settings); Toast.makeText(context, "تم حفظ الإعدادات بنجاح", Toast.LENGTH_SHORT).show() } },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color(0xFFC5A059)),
                shape = RoundedCornerShape(12.dp)
            ) { Text("حفظ إعدادات النظام الآلي", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
fun AdminManagementContent(firebaseManager: FirebaseManager, isSuperAdmin: Boolean) {
    var phone by remember { mutableStateOf("") }
    var blockedPhones by remember { mutableStateOf<List<String>>(emptyList()) }
    var currentAdmins by remember { mutableStateOf<List<User>>(emptyList()) }
    var showBlockedList by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    fun loadData() {
        scope.launch { 
            blockedPhones = firebaseManager.getBlockedPhones()
            val admins = firebaseManager.getAdmins().toMutableList()
            firebaseManager.database.child("users").get().await().children.forEach {
                try {
                    val u = it.getValue(User::class.java)
                    if (u != null && u.role.equals("ADMIN", ignoreCase = true)) {
                        admins.add(u)
                    }
                } catch (e: Exception) {}
            }
            currentAdmins = admins.distinctBy { it.id.ifBlank { it.phone } }
        }
    }
    
    LaunchedEffect(Unit) { loadData() }
    
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("إدارة المشرفين والحظر", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color(0xFFC5A059))
        
        Text(if (isSuperAdmin) "المشرفون الحاليون (اضغط على أي أدمن لتحديد تاباته المسموحة):" else "المشرفون الحاليون في التطبيق:", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.Black)
        
        var expandedAdminIdForPermissions by remember { mutableStateOf<String?>(null) }
        var selectedAdminPermissionsMap by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }

        val allAdminTabsList = remember {
            listOf(
                "users" to "👥 مستخدمين",
                "history" to "📜 السجل",
                "finance" to "💰 المالية",
                "messages" to "💬 الرسائل",
                "system" to "⚙️ إدارة النظام",
                "pricing" to "📊 معادلات التسعير"
            )
        }

        currentAdmins.forEach { admin ->
            val isExpanded = expandedAdminIdForPermissions == admin.id
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable(enabled = isSuperAdmin) {
                        if (isExpanded) {
                            expandedAdminIdForPermissions = null
                        } else {
                            expandedAdminIdForPermissions = admin.id
                            val cleanAdminPhone = firebaseManager.formatPhoneKey(admin.phone).ifBlank { admin.id }
                            firebaseManager.listenToAdminPermissions(cleanAdminPhone) { permMap ->
                                selectedAdminPermissionsMap = permMap
                            }
                        }
                    },
                colors = CardDefaults.cardColors(containerColor = if (isExpanded) Color(0xFFFAF9F6) else Color.White),
                border = BorderStroke(if (isExpanded) 1.5.dp else 1.dp, if (isExpanded) Color(0xFFC5A059) else Color.Gray.copy(0.1f))
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(admin.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(admin.phone, color = Color(0xFFC5A059), fontWeight = FontWeight.Black, fontSize = 15.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = Color(0xFFC5A059).copy(0.1f), shape = CircleShape) {
                                Text("ADMIN", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC5A059))
                            }
                            if (isSuperAdmin) {
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.Tune,
                                    contentDescription = "Edit Permissions",
                                    tint = Color(0xFFC5A059),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Expandable Tab Permissions Selection Panel for Super Admin (01098804131)
                    if (isSuperAdmin && isExpanded) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color(0xFFC5A059).copy(0.3f))
                        Text("👑 حدد التابات المسموح بظهورها لـ ${admin.name}:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB8860B))
                        
                        allAdminTabsList.chunked(2).forEach { rowTabs ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowTabs.forEach { (tabKey, tabLabel) ->
                                    val isChecked = selectedAdminPermissionsMap[tabKey] ?: true
                                    Row(
                                        modifier = Modifier.weight(1f).clickable {
                                            val updated = selectedAdminPermissionsMap.toMutableMap()
                                            updated[tabKey] = !isChecked
                                            selectedAdminPermissionsMap = updated
                                        },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { checked ->
                                                val updated = selectedAdminPermissionsMap.toMutableMap()
                                                updated[tabKey] = checked
                                                selectedAdminPermissionsMap = updated
                                            },
                                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFFC5A059))
                                        )
                                        Text(tabLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    val targetPhone = admin.phone.ifBlank { admin.id }
                                    
                                    // Build complete explicit permissions map
                                    val completeMap = mutableMapOf<String, Boolean>()
                                    allAdminTabsList.forEach { (k, _) ->
                                        completeMap[k] = selectedAdminPermissionsMap[k] ?: true
                                    }

                                    val ok = firebaseManager.saveAdminPermissions(targetPhone, completeMap)
                                    if (admin.id.isNotBlank()) {
                                        firebaseManager.saveAdminPermissions(admin.id, completeMap)
                                    }

                                    Toast.makeText(context, if (ok) "تم حفظ وتحديث صلاحيات الأدمن ${admin.name} بنجاح!" else "خطأ في حفظ الصلاحيات", Toast.LENGTH_SHORT).show()
                                    expandedAdminIdForPermissions = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC5A059))
                        ) {
                            Text("💾 حفظ صلاحيات الأدمن المختار", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = phone, 
            onValueChange = { phone = it }, 
            label = { Text("رقم الهاتف") }, 
            modifier = Modifier.fillMaxWidth(), 
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
        )
        
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    if (phone.trim().length >= 8) {
                        val inputPhone = phone.trim()
                        scope.launch {
                            try {
                                val success = firebaseManager.setRoleByPhone(inputPhone, "ADMIN")
                                phone = ""
                                Toast.makeText(context, if (success) "تمت إضافة الأدمن بنجاح" else "الرقم غير مسجل", Toast.LENGTH_SHORT).show()
                                loadData()
                            } catch (e: Throwable) {
                                Log.e("AdminManagement", "Error adding admin: ${e.message}", e)
                                Toast.makeText(context, "حدث خطأ أثناء إضافة الأدمن", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Toast.makeText(context, "يرجى كتابة رقم الهاتف الصحيح أولاً", Toast.LENGTH_SHORT).show()
                    }
                }, 
                modifier = Modifier.weight(1f), 
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC5A059))
            ) { Text("إضافة أدمن", color = Color.Black, fontWeight = FontWeight.Bold) }
            
            Button(
                onClick = {
                    if (phone.trim().length >= 8) {
                        val inputPhone = phone.trim()
                        scope.launch {
                            try {
                                val success = firebaseManager.setRoleByPhone(inputPhone, "USER")
                                phone = ""
                                Toast.makeText(context, if (success) "تم سحب صلاحية الأدمن بنجاح" else "الرقم غير مسجل", Toast.LENGTH_SHORT).show()
                                loadData()
                            } catch (e: Throwable) {
                                Log.e("AdminManagement", "Error removing admin: ${e.message}", e)
                                Toast.makeText(context, "حدث خطأ أثناء سحب الصلاحية", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Toast.makeText(context, "يرجى كتابة رقم الهاتف الصحيح أولاً", Toast.LENGTH_SHORT).show()
                    }
                }, 
                modifier = Modifier.weight(1f), 
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(0.12f), contentColor = Color.Red)
            ) { Text("حذف أدمن", fontWeight = FontWeight.Bold) }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = Color.Gray.copy(0.1f))
        Text("إدارة نقاط المستخدمين", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color(0xFFC5A059))
        var pointsInput by remember { mutableStateOf("") }
        OutlinedTextField(
            value = pointsInput, 
            onValueChange = { pointsInput = it }, 
            label = { Text("عدد النقاط") }, 
            modifier = Modifier.fillMaxWidth(), 
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    val pts = pointsInput.toDoubleOrNull() ?: 0.0
                    if (phone.length >= 10 && pts > 0) {
                        scope.launch {
                            val success = firebaseManager.updatePointsByPhone(phone, pts)
                            Toast.makeText(context, if(success) "تمت إضافة النقاط" else "خطأ في العملية", Toast.LENGTH_SHORT).show()
                            pointsInput = ""
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
            ) { Text("إضافة نقاط (+)") }
            
            Button(
                onClick = {
                    val pts = pointsInput.toDoubleOrNull() ?: 0.0
                    if (phone.length >= 10 && pts > 0) {
                        scope.launch {
                            val success = firebaseManager.updatePointsByPhone(phone, -pts)
                            Toast.makeText(context, if(success) "تم خصم النقاط" else "خطأ في العملية", Toast.LENGTH_SHORT).show()
                            pointsInput = ""
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) { Text("خصم نقاط (-)") }
        }

        Spacer(Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFFC5A059).copy(0.2f)),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("🎁 بث عرض ترويجي وأكواد الخصم للرحلات", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color(0xFFC5A059))
                var offerText by remember { mutableStateOf("") }
                var promoCodeInput by remember { mutableStateOf("") }
                var promoPercentageInput by remember { mutableStateOf("") }
                var promoValidityDaysInput by remember { mutableStateOf("1") }
                var isBroadcasting by remember { mutableStateOf(false) }

                var activePromoCodesList by remember { mutableStateOf<List<com.albasel.tawsilaapp.data.model.PromoCode>>(emptyList()) }

                DisposableEffect(Unit) {
                    val listener = firebaseManager.listenToPromoCodes { activePromoCodesList = it }
                    onDispose { firebaseManager.database.child("settings").child("promo_codes").removeEventListener(listener) }
                }

                OutlinedTextField(
                    value = offerText, 
                    onValueChange = { offerText = it }, 
                    label = { Text("نص العرض الترويجي / الإعلان") }, 
                    modifier = Modifier.fillMaxWidth(), 
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFC5A059))
                )

                OutlinedTextField(
                    value = promoCodeInput, 
                    onValueChange = { promoCodeInput = it }, 
                    label = { Text("كود الخصم للرحلات (مثال: WASALNA5)") }, 
                    modifier = Modifier.fillMaxWidth(), 
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFC5A059))
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = promoPercentageInput, 
                        onValueChange = { promoPercentageInput = it }, 
                        label = { Text("نسبة الخصم (%)") }, 
                        modifier = Modifier.weight(1f), 
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFC5A059))
                    )
                    OutlinedTextField(
                        value = promoValidityDaysInput, 
                        onValueChange = { promoValidityDaysInput = it }, 
                        label = { Text("صلاحية الكود (بالأيام)") }, 
                        modifier = Modifier.weight(1f), 
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFC5A059))
                    )
                }

                Button(
                    onClick = {
                        val percentage = promoPercentageInput.toDoubleOrNull() ?: 0.0
                        val days = promoValidityDaysInput.toIntOrNull() ?: 1
                        if (offerText.isNotBlank()) {
                            isBroadcasting = true
                            scope.launch {
                                if (promoCodeInput.isNotBlank() && percentage > 0) {
                                    firebaseManager.savePromoCode(promoCodeInput, percentage, offerText, days)
                                }
                                firebaseManager.broadcastOffer(
                                    txt = offerText, 
                                    pts = 0.0, 
                                    promoCode = promoCodeInput, 
                                    percentage = percentage, 
                                    validityDays = days
                                )
                                val msg = if (promoCodeInput.isNotBlank() && percentage > 0) {
                                    "تم حفظ كود الخصم (${promoCodeInput.uppercase().trim()}) بنسبة $percentage% لم دة $days يوم وبث العرض بنجاح!"
                                } else {
                                    "تم إرسال إشعار العرض لجميع المستخدمين بنجاح!"
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                offerText = ""
                                promoCodeInput = ""
                                promoPercentageInput = ""
                                promoValidityDaysInput = "1"
                                isBroadcasting = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = !isBroadcasting,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC5A059))
                ) {
                    if (isBroadcasting) CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp))
                    else Text("بث العرض وكود الخصم الآن", color = Color.Black, fontWeight = FontWeight.Bold)
                }

                if (activePromoCodesList.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Color.Gray.copy(0.15f))
                    Text("📋 أكواد الخصم المفعلة حالياً بالسيستم:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB8860B))

                    activePromoCodesList.forEach { promo ->
                        val remainingHours = maxOf(0L, (promo.expiryTimestamp - System.currentTimeMillis()) / (1000 * 60 * 60))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFAF9F6),
                            border = BorderStroke(1.dp, Color(0xFFC5A059).copy(0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("🎟️ ${promo.code}", fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color(0xFFB8860B))
                                    Text("نسبة الخصم: ${promo.percentage.toInt()}% | باقي: $remainingHours ساعة (صلاحية ${promo.validityDays} يوم)", fontSize = 11.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                    if (promo.offerText.isNotBlank()) {
                                        Text("نص العرض: ${promo.offerText}", fontSize = 10.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            firebaseManager.deletePromoCode(promo.code)
                                            Toast.makeText(context, "تم حذف كود الخصم (${promo.code}) بنجاح", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("لينكات دفع العمولة للسائقين", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color(0xFFC5A059))
        
        var paymentMethods by remember { mutableStateOf<List<PaymentMethod>>(emptyList()) }
        var newName by remember { mutableStateOf("") }
        var newUrl by remember { mutableStateOf("") }
        var newType by remember { mutableStateOf("LINK") } // LINK or PHONE
        var newIconId by remember { mutableStateOf("") }
        var isUploadingIcon by remember { mutableStateOf(false) }

        val iconPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                isUploadingIcon = true
                scope.launch {
                    val bytes = context.contentResolver.openInputStream(uri)?.readBytes()
                    if (bytes != null) {
                        val driveId = GoogleDriveHelper.uploadImage(bytes, "payment_icon_${System.currentTimeMillis()}.jpg")
                        if (driveId != null) {
                            newIconId = driveId
                        }
                    }
                    isUploadingIcon = false
                }
            }
        }
        
        LaunchedEffect(Unit) {
            paymentMethods = firebaseManager.getPaymentLinks()
        }
        
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFC5A059).copy(0.1f))) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("اسم وسيلة الدفع (مثلاً: فودافون كاش)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp))
                OutlinedTextField(value = newUrl, onValueChange = { newUrl = it }, label = { Text("الرابط أو الرقم") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = newType == "LINK", onClick = { newType = "LINK" }, colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFC5A059)))
                        Text("رابط", fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = newType == "PHONE", onClick = { newType = "PHONE" }, colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFC5A059)))
                        Text("رقم هاتف", fontSize = 12.sp)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFF5F5F5)).clickable { iconPicker.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isUploadingIcon) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFFC5A059))
                        else if (newIconId.isNotEmpty()) AppImageView(newIconId, Modifier.fillMaxSize())
                        else Icon(Icons.Default.AddPhotoAlternate, null, tint = Color.Gray)
                    }
                    Text("أضف أيقونة", fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = {
                            if (newName.isNotEmpty() && newUrl.isNotEmpty() && newIconId.isNotEmpty()) {
                                val method = PaymentMethod(id = UUID.randomUUID().toString(), name = newName, url = newUrl, iconId = newIconId, type = newType)
                                val updated = paymentMethods + method
                                scope.launch {
                                    firebaseManager.setPaymentLinks(updated)
                                    paymentMethods = updated
                                    newName = ""; newUrl = ""; newIconId = ""
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC5A059))
                    ) {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp), tint = Color.Black)
                        Spacer(Modifier.width(8.dp))
                        Text("حفظ", color = Color.Black)
                    }
                }
            }
        }
        
        paymentMethods.forEach { method ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape)) { AppImageView(method.iconId, Modifier.fillMaxSize()) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(method.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(method.url + " (${if(method.type == "PHONE") "رقم" else "رابط"})", fontSize = 11.sp, color = Color.Gray)
                    }
                    IconButton(onClick = {
                        val updated = paymentMethods.filter { it.id != method.id }
                        scope.launch {
                            if (!method.iconId.isNullOrBlank()) {
                                try { com.albasel.tawsilaapp.util.GoogleDriveHelper.deleteFile(method.iconId) } catch (e: Throwable) {}
                            }
                            firebaseManager.setPaymentLinks(updated)
                            paymentMethods = updated
                        }
                    }) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                }
            }
        }
    }

    if (showBlockedList) {
        AlertDialog(
            onDismissRequest = { showBlockedList = false },
            title = { Text("قائمة الأرقام المحظورة", fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    if (blockedPhones.isEmpty()) {
                        Text("لا توجد أرقام محظورة", modifier = Modifier.padding(16.dp), color = Color.Gray)
                    } else {
                        blockedPhones.forEach { p ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(p, fontWeight = FontWeight.Bold)
                                TextButton(onClick = {
                                    scope.launch {
                                        firebaseManager.unblockPhone(p)
                                        loadData()
                                    }
                                }) { Text("إلغاء الحظر", color = Color.Red) }
                            }
                            HorizontalDivider(color = Color.Gray.copy(0.1f))
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { showBlockedList = false }) { Text("إغلاق") } }
        )
    }
}

@Composable
fun BroadcastTabContent(firebaseManager: FirebaseManager) {
    var content by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("بث إعلان عام", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color(0xFFC5A059))
        OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("نص الإعلان") }, modifier = Modifier.fillMaxWidth(), minLines = 3, shape = RoundedCornerShape(12.dp))
        Button(onClick = {
            if (content.isNotEmpty()) {
                isSending = true
                scope.launch { firebaseManager.broadcastAdminNotification("WASALNA", content); isSending = false; content = "" }
            }
        }, modifier = Modifier.fillMaxWidth().height(50.dp), enabled = !isSending, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC5A059)), shape = RoundedCornerShape(12.dp)) {
            if (isSending) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp)) else Text("إرسال للجميع", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun UpdateTabContent(firebaseManager: FirebaseManager) {
    var updateUrl by remember { mutableStateOf("") }
    var updateVersion by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            val snapshot = firebaseManager.database.child("app_update").get().await()
            updateUrl = snapshot.child("url").value?.toString() ?: ""
            updateVersion = snapshot.child("version").value?.toString() ?: ""
        } catch (e: Exception) {}
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("تحديث التطبيق", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color(0xFFC5A059))
        OutlinedTextField(value = updateUrl, onValueChange = { updateUrl = it }, label = { Text("رابط التحميل") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
        OutlinedTextField(value = updateVersion, onValueChange = { updateVersion = it }, label = { Text("رقم الإصدار") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
        Button(onClick = {
            if (updateUrl.isNotBlank()) {
                isSaving = true
                scope.launch { firebaseManager.setUpdateLink(updateUrl, updateVersion); isSaving = false }
            }
        }, modifier = Modifier.fillMaxWidth().height(50.dp), enabled = !isSaving, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC5A059)), shape = RoundedCornerShape(12.dp)) {
            if (isSaving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp)) else Text("حفظ وإرسال إشعار", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CityDestinationsTabContent(firebaseManager: FirebaseManager) {
    var destinations by remember { mutableStateOf<List<CityDestination>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<CityDestination?>(null) }
    
    val provinces = listOf("الكل", "دمياط", "القاهرة", "الجيزة", "الإسكندرية", "الدقهلية", "الغربية", "الشرقية", "المنوفية", "القليوبية", "البحيرة", "كفر الشيخ", "بورسعيد", "الإسماعيلية", "السويس", "الفيوم", "بني سويف", "المنيا", "أسيوط", "سوهاج", "قنا", "الأقصر", "أسوان", "البحر الأحمر", "الوادي الجديد", "مطروح", "شمال سيناء", "جنوب سيناء")
    val actualProvinces = provinces.drop(1)
    var selectedProvinceFilter by remember { mutableStateOf("الكل") }
    
    var name by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var tripType by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("LINE") }
    var province by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("0") }
    var imageUrl by remember { mutableStateOf("") }
    var isUploadingImage by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val destImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            isUploadingImage = true
            scope.launch {
                val bytes = context.contentResolver.openInputStream(uri)?.readBytes()
                if (bytes != null) {
                    val driveId = GoogleDriveHelper.uploadImage(bytes, "city_dest_${System.currentTimeMillis()}.jpg")
                    if (driveId != null) {
                        imageUrl = driveId
                    }
                }
                isUploadingImage = false
            }
        }
    }

    fun load() { scope.launch { destinations = firebaseManager.getCityDestinations() } }
    LaunchedEffect(Unit) { load() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("وجهات المدينة", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color(0xFFC5A059))
            IconButton(onClick = { 
                name = ""; details = ""; tripType = ""; type = "LINE"; province = actualProvinces[0]; city = ""; price = ""; priority = "0"
                editItem = null; showAddDialog = true 
            }) { Icon(Icons.Default.AddCircle, null, tint = Color(0xFFC5A059), modifier = Modifier.size(32.dp)) }
        }

        // Province Filter for Sorting/Ordering
        var expF by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = expF, onExpandedChange = { expF = !expF }) {
            OutlinedTextField(
                value = selectedProvinceFilter, 
                onValueChange = {}, 
                readOnly = true, 
                label = { Text("تصفية حسب المحافظة (للترتيب)") }, 
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expF) }, 
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            ExposedDropdownMenu(expanded = expF, onDismissRequest = { expF = false }) {
                provinces.forEach { p -> DropdownMenuItem(text = { Text(p) }, onClick = { selectedProvinceFilter = p; expF = false }) }
            }
        }

        destinations.filter { selectedProvinceFilter == "الكل" || it.province == selectedProvinceFilter }
            .sortedBy { it.priority }
            .forEach { dest ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(1.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.width(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("#${dest.priority}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        }
                        Icon(if(dest.type == "SCHOOL") Icons.Default.School else Icons.Default.AltRoute, null, tint = Color(0xFFC5A059))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${dest.province} - ${dest.city}", fontSize = 10.sp, color = Color(0xFFC5A059), fontWeight = FontWeight.Bold)
                            Text(dest.name, fontWeight = FontWeight.Bold)
                            if(dest.details.isNotEmpty()) Text(dest.details, fontSize = 10.sp, color = Color.Gray)
                            if(dest.tripType.isNotEmpty()) Text(dest.tripType, fontSize = 10.sp, color = Color(0xFFC5A059), fontWeight = FontWeight.Bold)
                            Text("السعر: ${dest.price} ج.م", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFF2E7D32))
                        }
                        IconButton(onClick = { 
                            name = dest.name; details = dest.details; tripType = dest.tripType; type = dest.type
                            province = if(dest.province.isNotEmpty()) dest.province else actualProvinces[0]
                            city = dest.city
                            price = dest.price.toString()
                            priority = dest.priority.toString()
                            imageUrl = dest.imageUrl
                            editItem = dest; showAddDialog = true 
                        }) { Icon(Icons.Default.Edit, null, tint = Color.Gray) }
                        IconButton(onClick = { scope.launch { firebaseManager.deleteCityDestination(dest.id); load() } }) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                    }
                }
            }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(if(editItem == null) "إضافة وجهة" else "تعديل وجهة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                    var expP by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = expP, onExpandedChange = { expP = !expP }) {
                        OutlinedTextField(value = province, onValueChange = {}, readOnly = true, label = { Text("المحافظة") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expP) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                        ExposedDropdownMenu(expanded = expP, onDismissRequest = { expP = false }) {
                            actualProvinces.forEach { p -> DropdownMenuItem(text = { Text(p) }, onClick = { province = p; expP = false }) }
                        }
                    }
                    OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("المدينة") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("الاسم") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = details, onValueChange = { details = it }, label = { Text("المسار / التفاصيل") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = tripType, onValueChange = { tripType = it }, label = { Text("نوع الرحلة (ذهاب وعودة/ذهاب فقط)") }, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("السعر (للفرد)") }, modifier = Modifier.weight(1f), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                        OutlinedTextField(value = priority, onValueChange = { priority = it }, label = { Text("الأولوية (كبير=أول)") }, modifier = Modifier.weight(1f), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = type == "LINE", onClick = { type = "LINE" })
                        Text("خط سير", modifier = Modifier.clickable { type = "LINE" })
                        Spacer(Modifier.width(16.dp))
                        RadioButton(selected = type == "SCHOOL", onClick = { type = "SCHOOL" })
                        Text("مدرسة", modifier = Modifier.clickable { type = "SCHOOL" })
                    }

                    // Destination Image Upload Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = { destImagePicker.launch("image/*") },
                            enabled = !isUploadingImage,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC5A059), contentColor = Color.Black)
                        ) {
                            if (isUploadingImage) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black)
                            else Text(if(imageUrl.isNotBlank()) "تغيير صورة الوجهة 📷" else "إضافة صورة للوجهة 📷", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        if (imageUrl.isNotBlank()) {
                            IconButton(onClick = { imageUrl = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Delete Image", tint = Color.Red)
                            }
                        }
                    }
                }
            },
            confirmButton = { 
                Button(onClick = { 
                    if(name.isNotBlank()) { 
                        scope.launch { 
                            firebaseManager.saveCityDestination(CityDestination(
                                id = editItem?.id ?: "", 
                                name = name, 
                                details = details, 
                                type = type,
                                province = province,
                                city = city,
                                price = price.toDoubleOrNull() ?: 0.0,
                                priority = priority.toIntOrNull() ?: 0,
                                tripType = tripType,
                                imageUrl = imageUrl
                            ))
                            showAddDialog = false
                            load() 
                        } 
                    } 
                }) { Text("حفظ") } 
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
fun PointsTab(firebaseManager: FirebaseManager) {
    var userPointValue by remember { mutableStateOf("") }
    var driverPointValue by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        userPointValue = firebaseManager.getPointValue("USER").toString()
        driverPointValue = firebaseManager.getPointValue("DRIVER").toString()
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFFC5A059).copy(0.2f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("إدارة قيمة النقاط", fontWeight = FontWeight.Black, color = Color(0xFFC5A059), fontSize = 18.sp)
            OutlinedTextField(
                value = userPointValue, 
                onValueChange = { userPointValue = it }, 
                label = { Text("قيمة نقطة العميل") }, 
                modifier = Modifier.fillMaxWidth(), 
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            )
            Button(onClick = { scope.launch { firebaseManager.setPointValue("USER", userPointValue.toDoubleOrNull() ?: 0.0) } }, modifier = Modifier.fillMaxWidth()) { Text("حفظ قيمة العميل") }
            OutlinedTextField(
                value = driverPointValue, 
                onValueChange = { driverPointValue = it }, 
                label = { Text("قيمة نقطة السائق") }, 
                modifier = Modifier.fillMaxWidth(), 
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            )
            Button(onClick = { scope.launch { firebaseManager.setPointValue("DRIVER", driverPointValue.toDoubleOrNull() ?: 0.0) } }, modifier = Modifier.fillMaxWidth()) { Text("حفظ قيمة السائق") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PricesTab(firebaseManager: FirebaseManager) {
    val vehicles = listOf("تاكسي", "فان", "ملاكي", "توكتوك", "تريسكل", "باص", "ميكروباص", "موتوسيكل", "دراجة", "ربع نقل", "نصف نقل", "نقل")
    var selectedVehicle by remember { mutableStateOf(vehicles[0]) }
    
    var priority by remember { mutableStateOf("0") }
    var commission by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        commission = firebaseManager.getAdminCommission().toString()
    }

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFFC5A059).copy(0.2f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("إعدادات العمولة والأسعار العامة", fontWeight = FontWeight.Black, color = Color(0xFFC5A059), fontSize = 18.sp)
            
            OutlinedTextField(
                value = commission, 
                onValueChange = { commission = it }, 
                label = { Text("العمولة (%)") }, 
                modifier = Modifier.fillMaxWidth(), 
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            )
            
            Button(onClick = { 
                scope.launch { 
                    firebaseManager.setAdminCommission(commission.toDoubleOrNull() ?: 10.0)
                    Toast.makeText(context, "تم حفظ الإعدادات العامة", Toast.LENGTH_SHORT).show()
                }
            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.Black)) { 
                Text("حفظ الإعدادات العامة", color = Color(0xFFC5A059)) 
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("إعدادات ظهور المركبات في الشاشات", fontWeight = FontWeight.Bold)

            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                OutlinedTextField(value = selectedVehicle, onValueChange = {}, readOnly = true, label = { Text("نوع المركبة") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    vehicles.forEach { v -> DropdownMenuItem(text = { Text(v) }, onClick = { selectedVehicle = v; expanded = false }) }
                }
            }

            OutlinedTextField(
                value = priority,
                onValueChange = { if(it.all { c -> c.isDigit() }) priority = it },
                label = { Text("ترتيب الظهور (1 هو الأول)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            )
            
            var isVisible by remember { mutableStateOf(true) }
            var showInHome by remember { mutableStateOf(true) }
            var showInInsideCity by remember { mutableStateOf(true) }
            var showInCargo by remember { mutableStateOf(true) }

            LaunchedEffect(selectedVehicle) {
                val detail = firebaseManager.getVehiclePricesDetail()[selectedVehicle]
                priority = detail?.priority?.toString() ?: "0"
                isVisible = detail?.isVisible ?: true
                showInHome = detail?.showInHome ?: true
                showInInsideCity = detail?.showInInsideCity ?: true
                showInCargo = detail?.showInCargo ?: true
            }

            Text("أماكن ظهور هذا النوع:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                VisibilityToggle("إظهار في التطبيق (عام)", isVisible) { isVisible = it }
                VisibilityToggle("إظهار في الشاشة الرئيسية (سفر)", showInHome) { showInHome = it }
                VisibilityToggle("إظهار في توصيل داخلي (إشعارات)", showInInsideCity) { showInInsideCity = it }
                VisibilityToggle("إظهار في نقل بضائع", showInCargo) { showInCargo = it }
            }

            Button(onClick = { 
                scope.launch { 
                    val currentDetail = firebaseManager.getVehiclePricesDetail()[selectedVehicle] ?: com.albasel.tawsilaapp.data.model.VehiclePrice(type = selectedVehicle)
                    firebaseManager.setVehiclePrice(currentDetail.copy(
                        type = selectedVehicle, 
                        priority = priority.toIntOrNull() ?: 0,
                        isVisible = isVisible,
                        showInHome = showInHome,
                        showInInsideCity = showInInsideCity,
                        showInCargo = showInCargo
                    ))
                    Toast.makeText(context, "تم حفظ إعدادات إظهار المركبة بنجاح", Toast.LENGTH_SHORT).show()
                } 
            }, modifier = Modifier.fillMaxWidth()) { Text("حفظ إعدادات إظهار المركبة") }
        }
    }
}

@Composable
fun VisibilityToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 4.dp)) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, colors = CheckboxDefaults.colors(checkedColor = Color(0xFFC5A059)))
        Text(label, fontSize = 13.sp)
    }
}

@Composable
fun OrdersHistoryTab(firebaseManager: FirebaseManager, user: User?) {
    var allOrders by remember { mutableStateOf(emptyList<Order>()) }
    var filterStatus by remember { mutableStateOf("ALL") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { allOrders = firebaseManager.getAllOrders() }

    val filteredOrders = remember(allOrders, filterStatus) {
        if (filterStatus == "ALL") allOrders.sortedByDescending { it.timestamp }
        else allOrders.filter { (it.status ?: "").uppercase().trim() == filterStatus }.sortedByDescending { it.timestamp }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        ScrollableTabRow(
            selectedTabIndex = listOf("ALL", "CONFIRMED", "PENDING").indexOf(filterStatus).coerceAtLeast(0),
            containerColor = Color.Transparent,
            contentColor = Color(0xFFC5A059),
            edgePadding = 0.dp,
            divider = {}
        ) {
            val statusList = listOf("الكل" to "ALL", "مؤكدة" to "CONFIRMED", "قيد الانتظار" to "PENDING")
            statusList.forEach { (title, status) ->
                Tab(selected = filterStatus == status, onClick = { filterStatus = status }, text = { Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold) })
            }
        }
        
        Spacer(Modifier.height(10.dp))

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filteredOrders) { order ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = if(order.status == "CONFIRMED") Color(0xFFFFFDE7) else MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(order.status, color = if(order.status == "CONFIRMED") Color(0xFF4CAF50) else Color(0xFFFF9800), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(order.date, color = Color.Gray, fontSize = 12.sp)
                                if (user?.phone == "01098804131") {
                                    IconButton(onClick = { scope.launch { firebaseManager.deleteOrder(order.id); allOrders = firebaseManager.getAllOrders() } }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Delete, null, tint = Color.Red, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("من: ${order.pickupName}", fontWeight = FontWeight.Medium)
                        Text("إلى: ${order.destinationName}", fontWeight = FontWeight.Medium)
                        
                        if (order.isCargo) {
                            val rawDetails = order.cargoDetails ?: "بدون تفاصيل"
                            val cleanDetails = rawDetails.split("|").first().trim()
                            Text("📦 $cleanDetails | ${order.numCartons} قطعة", fontSize = 11.sp, color = Color(0xFF795548), fontWeight = FontWeight.Bold)
                            val loadText = if(order.needsLoading) "مع تحميل" else "بدون تحميل"
                            val unloadText = if(order.needsUnloading) "مع تفريغ" else "بدون تفريغ"
                            Text("👷 $loadText | $unloadText", fontSize = 10.sp, color = Color.Gray)
                        }

                        if (order.status == "CONFIRMED" || order.status == "PENDING") {
                            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Color.Gray.copy(0.1f))
                            if(order.status == "CONFIRMED") {
                                Text("السائق: ${order.driverName ?: "مجهول"}", fontWeight = FontWeight.Bold, color = Color(0xFFC5A059))
                                Text("هاتف السائق: ${order.driverPhone ?: ""}", color = Color.Gray)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("الوقت: ${order.time}", fontSize = 12.sp, color = Color.Gray)
                                    if (order.pointsDiscount > 0) {
                                        Text("خصم نقاط: ${order.pointsDiscount.toInt()} ج.م", fontSize = 11.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("الركاب: ${order.passengers}", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                    Text("${order.price.toInt()} ج.م", fontWeight = FontWeight.Black, color = Color(0xFF2E7D32))
                                }
                            }
                            if(order.rating != null) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFD600), modifier = Modifier.size(14.dp))
                                    Text("${order.rating}", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                                    if(!order.feedback.isNullOrBlank()) Text("- ${order.feedback}", fontSize = 11.sp, color = Color.Gray)
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
fun MessagesTab(firebaseManager: FirebaseManager) {
    var messages by remember { mutableStateOf(emptyList<Map<String, Any>>()) }
    var selectedMessageForReply by remember { mutableStateOf<Map<String, Any>?>(null) }
    var replyText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) { 
        val list = firebaseManager.getContactMessages()
        messages = list.filter { (it["message"]?.toString() ?: "").isNotBlank() }
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(messages) { msg ->
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(msg["name"]?.toString() ?: "", fontWeight = FontWeight.Bold)
                            Text(msg["phone"]?.toString() ?: "", fontSize = 12.sp, color = Color(0xFFC5A059))
                        }
                        Row {
                            IconButton(onClick = {
                                selectedMessageForReply = msg
                                replyText = ""
                            }) {
                                Icon(Icons.Default.Reply, contentDescription = "Reply", tint = Color(0xFFC5A059))
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    firebaseManager.deleteContactMessage(msg["id"]?.toString() ?: "")
                                    messages = firebaseManager.getContactMessages().filter { (it["message"]?.toString() ?: "").isNotBlank() }
                                }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(msg["message"]?.toString() ?: "")
                }
            }
        }
    }

    selectedMessageForReply?.let { msg ->
        AlertDialog(
            onDismissRequest = { selectedMessageForReply = null },
            title = { Text("الرد على رسالة: " + (msg["name"]?.toString() ?: ""), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFC5A059)) },
            text = {
                Column {
                    Text("الرسالة الأصلية: " + (msg["message"]?.toString() ?: ""), fontSize = 13.sp, color = Color.Gray)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        label = { Text("نص الرد") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetId = msg["userId"]?.toString()?.takeIf { it.isNotBlank() } 
                            ?: msg["phone"]?.toString()?.takeIf { it.isNotBlank() } 
                            ?: ""
                        val msgId = msg["id"]?.toString() ?: ""
                        if (replyText.isNotBlank()) {
                            if (targetId.isNotBlank()) {
                                scope.launch {
                                    try {
                                        firebaseManager.sendContactReply(targetId, msgId, replyText.trim())
                                        selectedMessageForReply = null
                                        android.widget.Toast.makeText(context, "تم إرسال الرد بنجاح للعميل", android.widget.Toast.LENGTH_LONG).show()
                                    } catch (e: Exception) {
                                        Log.e("AdminHomeScreen", "Send reply error", e)
                                        android.widget.Toast.makeText(context, "خطأ في إرسال الرد: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                android.widget.Toast.makeText(context, "تعذر التعرف على العميل لإرسال الرد", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            android.widget.Toast.makeText(context, "يرجى كتابة نص الرد أولاً", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC5A059))
                ) {
                    Text("إرسال الرد", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedMessageForReply = null }) {
                    Text("إلغاء", color = Color.Gray)
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White
        )
    }
}

@Composable
fun DocBox(label: String, imageDriveId: String?, modifier: Modifier, onClick: (String) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(label, fontSize = 10.sp, color = Color.Gray)
        Box(Modifier.size(85.dp).clip(RoundedCornerShape(10.dp)).background(Color.LightGray).clickable { imageDriveId?.let { onClick(it) } }) {
            AppImageView(imageDriveId, Modifier.fillMaxSize())
        }
    }
}

@Composable
fun AdminSummaryRow(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = color.copy(alpha = 0.8f), fontSize = 13.sp)
        Text(value, color = color, fontWeight = FontWeight.Black, fontSize = 14.sp)
    }
}

@Composable
fun FullScreenImageDialog(imageDriveId: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            Box(Modifier.fillMaxSize()) {
                AppImageView(imageDriveId, Modifier.fillMaxSize().padding(16.dp))
                
                Row(
                    modifier = Modifier.align(Alignment.TopEnd).padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Download Button
                    if (imageDriveId.length < 100) {
                        IconButton(
                            onClick = {
                                val url = "https://drive.google.com/uc?export=download&id=$imageDriveId"
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.background(Color.White.copy(0.3f), CircleShape)
                        ) {
                            Icon(Icons.Default.Download, "Save", tint = Color.White)
                        }
                    }

                    IconButton(
                        onClick = onDismiss, 
                        modifier = Modifier.background(Color.White.copy(0.3f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, null, tint = Color.White)
                    }
                }
            }
        }
    }
}
