package com.albasel.tawsilaapp.ui.screens.home

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.albasel.tawsilaapp.R
import com.albasel.tawsilaapp.data.firebase.FirebaseManager
import com.albasel.tawsilaapp.data.model.InAppNotification
import com.albasel.tawsilaapp.data.model.User
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun UberDashboardHomeScreen(
    firebaseManager: FirebaseManager,
    language: String,
    user: User?,
    isDarkMode: Boolean,
    onMenuClick: () -> Unit,
    onToggleTheme: () -> Unit,
    onSafarClick: () -> Unit,
    onCityClick: () -> Unit,
    onNearbyClick: () -> Unit,
    onCargoClick: () -> Unit,
    onSearchSelect: (String, Double, Double) -> Unit,
    onNotificationsClick: (String, String?) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isAr = language == "AR"
    val sharedPrefs = remember { context.getSharedPreferences("tawsila_prefs", Context.MODE_PRIVATE) }

    var showDateTimePickerDialog by remember { mutableStateOf(false) }
    var showNotificationsOverlay by remember { mutableStateOf(false) }
    val notificationsList = remember { mutableStateListOf<InAppNotification>() }

    var selectedDate by remember { mutableStateOf(java.text.SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date())) }
    var selectedTime by remember { 
        val cal = Calendar.getInstance()
        if (cal.get(Calendar.MINUTE) > 0) cal.add(Calendar.HOUR_OF_DAY, 1)
        cal.set(Calendar.MINUTE, 0)
        val timeStr = java.text.SimpleDateFormat("hh:00 ", Locale.ENGLISH).format(cal.time)
        val amPm = if(cal.get(Calendar.AM_PM) == Calendar.AM) (if(isAr) "صباحاً" else "AM") else (if(isAr) "مساءً" else "PM")
        mutableStateOf(timeStr + amPm)
    }

    // Notifications Listener
    DisposableEffect(user?.id) {
        if (user != null) {
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val rawList = snapshot.children.mapNotNull { child ->
                        val map = child.value as? Map<*, *> ?: return@mapNotNull null
                        InAppNotification(
                            id = map["id"]?.toString() ?: (child.key ?: ""),
                            title = map["title"]?.toString() ?: "",
                            content = map["content"]?.toString() ?: "",
                            type = map["type"]?.toString() ?: "general",
                            sourceId = map["sourceId"]?.toString() ?: "",
                            timestamp = (map["timestamp"] as? Long) ?: 0L
                        )
                    }
                    val uniqueList = rawList.distinctBy { "${it.title.trim()}_${it.content.trim()}" }
                    notificationsList.clear()
                    notificationsList.addAll(uniqueList.sortedByDescending { it.timestamp })
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            val ref = firebaseManager.database.child("system_notifications").child(user.id)
            ref.addValueEventListener(listener)
            onDispose { ref.removeEventListener(listener) }
        } else {
            onDispose {}
        }
    }

    // Recent Searches State
    var recentSearchesList by remember { mutableStateOf<List<RecentSearchItem>>(emptyList()) }
    var recentItemToDelete by remember { mutableStateOf<RecentSearchItem?>(null) }
    var selectedSearchItemForChoice by remember { mutableStateOf<RecentSearchItem?>(null) }
    var activeRecentSearchTitle by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        recentSearchesList = com.albasel.tawsilaapp.util.RecentSearchHelper.getRecentSearches(context)
    }

    fun deleteRecentSearch(item: RecentSearchItem) {
        com.albasel.tawsilaapp.util.RecentSearchHelper.deleteRecentSearch(context, item)
        recentSearchesList = com.albasel.tawsilaapp.util.RecentSearchHelper.getRecentSearches(context)
    }

    val bg = if (isDarkMode) Color(0xFF121212) else Color(0xFFFAFAF7)
    val cardBg = if (isDarkMode) Color(0xFF1E1E1E) else Color.White
    val textColor = if (isDarkMode) Color.White else Color.Black
    val goldColor = Color(0xFFC5A059)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Top Bar Header Logo & Icons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onMenuClick,
                    shape = CircleShape,
                    color = cardBg,
                    shadowElevation = 3.dp,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Menu, null, tint = textColor, modifier = Modifier.size(20.dp))
                    }
                }

                // Calligraphy WASALNA Logo
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "وصلنا",
                        color = goldColor,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "WASALNA",
                        color = goldColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        onClick = onToggleTheme,
                        shape = CircleShape,
                        color = cardBg,
                        shadowElevation = 3.dp,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                null,
                                tint = if (isDarkMode) Color(0xFFFFD76A) else Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = { showNotificationsOverlay = !showNotificationsOverlay },
                        shape = CircleShape,
                        color = cardBg,
                        shadowElevation = 3.dp,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            BadgedBox(badge = { if(notificationsList.isNotEmpty()) Badge { Text("${notificationsList.size}") } }) {
                                Icon(Icons.Default.NotificationsNone, null, tint = textColor, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            // 2. Top Search Bar ("إلى أين تذهب؟")
            Surface(
                onClick = onSafarClick,
                shape = RoundedCornerShape(26.dp),
                color = cardBg,
                border = BorderStroke(1.5.dp, goldColor.copy(alpha = 0.5f)),
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth().height(58.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(Icons.Default.Mic, null, tint = goldColor, modifier = Modifier.size(22.dp))
                    Text(
                        text = if (isAr) "إلى أين تذهب؟" else "Where to?",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                }
            }

            // 3. Welcome Greeting Text
            val userName = user?.name?.ifBlank { "أحمد" } ?: "أحمد"
            Column(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
                Text(
                    text = if (isAr) "مرحبا, $userName" else "Hello, $userName",
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    color = textColor,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (isAr) "كيف تود التنقل اليوم؟" else "How would you like to travel today?",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 4. 4 Luxury Vertical Service Cards (سفر | توصيل داخلي | قريب مني | نقل بضائع)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Card 1: سفر (Travel)
                    LuxuryServiceCard(
                        titleEn = "Travel",
                        titleAr = "سفر",
                        iconRes = R.drawable.ic_travel,
                        isSelected = true,
                        isDarkMode = isDarkMode,
                        modifier = Modifier.weight(1f),
                        onClick = onSafarClick
                    )

                    // Card 2: توصيل داخلي (Delivery)
                    LuxuryServiceCard(
                        titleEn = "Delivery",
                        titleAr = "توصيل داخلي",
                        iconRes = R.drawable.ic_delivery,
                        isSelected = false,
                        isDarkMode = isDarkMode,
                        modifier = Modifier.weight(1f),
                        onClick = onCityClick
                    )

                    // Card 3: قريب مني (Nearby)
                    LuxuryServiceCard(
                        titleEn = "Nearby",
                        titleAr = "قريب مني",
                        iconRes = R.drawable.ic_nearby,
                        isSelected = false,
                        isDarkMode = isDarkMode,
                        modifier = Modifier.weight(1f),
                        onClick = onNearbyClick
                    )

                    // Card 4: نقل بضائع (Cargo)
                    LuxuryServiceCard(
                        titleEn = "Cargo",
                        titleAr = "نقل بضائع",
                        iconRes = R.drawable.ic_cargo,
                        isSelected = false,
                        isDarkMode = isDarkMode,
                        modifier = Modifier.weight(1f),
                        onClick = onCargoClick
                    )
                }
            }

            // 5. Previous Destinations Section ("الوجهات السابقة")
            Column(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
                Text(
                    text = if (isAr) "الوجهات السابقة" else "Recent Destinations",
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = textColor,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                )

                if (recentSearchesList.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 237.dp) // Height enlarged by 3 mm (~12dp) as requested
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        recentSearchesList.take(3).forEach { item ->
                            val isSelected = activeRecentSearchTitle == item.title
                            val itemBg = if (isSelected) goldColor else cardBg
                            val itemTextColor = if (isSelected) Color.Black else textColor
                            val itemSubtextColor = if (isSelected) Color.Black.copy(alpha = 0.85f) else Color.Gray
                            val itemIconColor = if (isSelected) Color.Black else Color.Gray
                            val itemPinColor = if (isSelected) Color.Black else goldColor

                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = itemBg),
                                border = BorderStroke(1.dp, goldColor.copy(alpha = 0.4f)),
                                elevation = CardDefaults.cardElevation(2.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            activeRecentSearchTitle = item.title
                                            selectedSearchItemForChoice = item
                                        },
                                        onLongClick = { recentItemToDelete = item }
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Icon(Icons.Default.AccessTime, contentDescription = null, tint = itemIconColor, modifier = Modifier.size(18.dp))
                                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                        Text(
                                            text = item.title,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 14.5.sp,
                                            color = itemTextColor,
                                            textAlign = TextAlign.Right,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (item.subtitle.isNotBlank()) {
                                            Text(
                                                text = item.subtitle,
                                                fontSize = 11.5.sp,
                                                color = itemSubtextColor,
                                                textAlign = TextAlign.Right,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Icon(Icons.Default.Place, contentDescription = null, tint = itemPinColor, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(2.dp))

            // 6. Luxury Promo Banner ("خصومات وأسعار لا تقبل المنافسة")
            // Shifted 2 mm higher (~8dp higher)
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .padding(bottom = 18.dp)
            ) {
                val bannerGradient = Brush.horizontalGradient(
                    colors = listOf(Color(0xFFE6AF2E), Color(0xFFD4AF37), Color(0xFFC5A059))
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(bannerGradient, RoundedCornerShape(22.dp))
                        .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_mainunder),
                            contentDescription = null,
                            modifier = Modifier.size(82.dp)
                        )

                        Column(
                            horizontalAlignment = Alignment.End, 
                            verticalArrangement = Arrangement.Top,
                            modifier = Modifier.weight(1f).padding(top = 4.dp, start = 4.dp, end = 2.dp)
                        ) {
                            Text(
                                text = if (isAr) "خصومات وأسعار لا تقبل المنافسة" else "Unbeatable Prices & Discounts",
                                fontWeight = FontWeight.Black,
                                fontSize = 17.5.sp,
                                color = Color.White,
                                textAlign = TextAlign.Right,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "WASALNA",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color.White.copy(0.9f)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "وصلنا",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // Notifications Overlay Dialog
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
                            Text(if(isAr) "الإشعارات" else "Alerts", fontWeight = FontWeight.Black)
                            Row {
                                if (notificationsList.isNotEmpty() && user != null) {
                                    TextButton(onClick = { 
                                        scope.launch { firebaseManager.clearAllNotifications(user.id) }
                                    }) {
                                        Text(if(isAr) "حذف الكل" else "Clear All", color = Color.Red, fontSize = 11.sp)
                                    }
                                }
                                IconButton({ showNotificationsOverlay = false }, modifier = Modifier.size(20.dp)) { Icon(Icons.Default.Close, null) }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (notificationsList.isEmpty()) {
                            Text(if(isAr) "لا توجد إشعارات جديدة" else "No alerts", color = Color.Gray, modifier = Modifier.padding(vertical = 12.dp))
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                                items(notificationsList) { n ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { 
                                            showNotificationsOverlay = false
                                            val targetRoute = if (!n.sourceId.isNullOrEmpty()) n.sourceId else n.type
                                            if (targetRoute.isNotEmpty()) {
                                                onNotificationsClick(targetRoute, targetRoute)
                                            }
                                        },
                                        colors = CardDefaults.cardColors(containerColor = Color.Gray.copy(0.05f))
                                    ) {
                                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Box(Modifier.size(8.dp).background(Color(0xFFC5A059), CircleShape))
                                            Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(n.title, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                Text(n.content, fontSize = 11.sp, color = Color.Gray)
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

    // Long Click Delete Dialog
    recentItemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { recentItemToDelete = null },
            title = { Text(if (isAr) "حذف من سجل البحث" else "Delete from History", fontWeight = FontWeight.Bold, color = Color(0xFFC5A059)) },
            text = { Text(if (isAr) "هل تريد حذف (${item.title}) من سجل البحث؟" else "Delete (${item.title}) from search history?", color = textColor) },
            confirmButton = {
                Button(
                    onClick = {
                        deleteRecentSearch(item)
                        recentItemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text(if (isAr) "حذف" else "Delete", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { recentItemToDelete = null }) {
                    Text(if (isAr) "إلغاء" else "Cancel", color = Color.Gray)
                }
            },
            containerColor = if (isDarkMode) Color(0xFF1E1E1E) else Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Service Choice Dialog for Recent Search Item (Safar vs Cargo)
    selectedSearchItemForChoice?.let { item ->
        AlertDialog(
            onDismissRequest = { selectedSearchItemForChoice = null },
            title = { 
                Text(
                    text = if (isAr) "اختر نوع الخدمة للوجهة" else "Choose Service Type", 
                    fontWeight = FontWeight.Black, 
                    color = Color(0xFFC5A059),
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                ) 
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = item.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = textColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    
                    // Option 1: Safar / Ride ✈️
                    Button(
                        onClick = {
                            val title = item.title
                            val lat = item.lat
                            val lng = item.lng
                            selectedSearchItemForChoice = null
                            sharedPrefs.edit().putBoolean("cargo_mode", false).apply()
                            onSearchSelect(title, lat, lng)
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC5A059), contentColor = Color.Black)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Flight, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (isAr) "سفر / توصيل ✈️" else "Safar / Ride", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                    // Option 2: Cargo Transport 📦
                    Button(
                        onClick = {
                            val title = item.title
                            val lat = item.lat
                            val lng = item.lng
                            selectedSearchItemForChoice = null
                            sharedPrefs.edit().putBoolean("cargo_mode", true).apply()
                            onSearchSelect(title, lat, lng)
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100), contentColor = Color.White)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocalShipping, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (isAr) "نقل بضائع 📦" else "Cargo Transport", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedSearchItemForChoice = null }) {
                    Text(if (isAr) "إلغاء" else "Cancel", color = Color.Gray)
                }
            },
            containerColor = if (isDarkMode) Color(0xFF1E1E1E) else Color.White,
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Date & Time Picker Dialog
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
                        showMinutes = true,
                        onDateChange = { selectedDate = it }, 
                        onTimeChange = { selectedTime = it }, 
                        onConfirm = { showDateTimePickerDialog = false }, 
                        onCancel = { showDateTimePickerDialog = false }
                    )
                }
            }
        )
    }
}

@Composable
fun LuxuryServiceCard(
    titleEn: String,
    titleAr: String,
    iconRes: Int,
    isSelected: Boolean,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (isSelected) Color(0xFFC5A059) else if (isDarkMode) Color(0xFF1E1E1E) else Color.White
    val textColor = if (isSelected) Color.Black else if (isDarkMode) Color.White else Color.Black

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = bg,
        border = BorderStroke(1.5.dp, Color(0xFFC5A059).copy(alpha = 0.5f)),
        shadowElevation = 4.dp,
        modifier = modifier.height(138.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(72.dp).padding(top = 4.dp)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 4.dp)) {
                Text(
                    text = titleEn,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Black,
                    color = textColor
                )
                Text(
                    text = titleAr,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor.copy(alpha = 0.85f)
                )
            }
        }
    }
}
