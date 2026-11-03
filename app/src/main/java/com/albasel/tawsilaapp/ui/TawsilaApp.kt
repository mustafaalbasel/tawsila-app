package com.albasel.tawsilaapp.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.albasel.tawsilaapp.R
import com.albasel.tawsilaapp.ui.screens.home.UberDashboardHomeScreen
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.albasel.tawsilaapp.data.firebase.FirebaseManager
import com.albasel.tawsilaapp.data.model.Message
import com.albasel.tawsilaapp.data.model.Order
import com.albasel.tawsilaapp.data.model.User
import com.albasel.tawsilaapp.util.VehicleUtils.isCargoOnlyVehicle
import com.albasel.tawsilaapp.util.VehicleUtils.cleanVehicleName
import com.albasel.tawsilaapp.ui.navigation.Screen
import com.albasel.tawsilaapp.ui.screens.admin.AdminHomeScreen
import com.albasel.tawsilaapp.ui.screens.auth.LoginScreen
import com.albasel.tawsilaapp.ui.screens.auth.RegisterScreen
import com.albasel.tawsilaapp.ui.screens.auth.SplashScreen
import com.albasel.tawsilaapp.ui.screens.city.InsideCityScreen
import com.albasel.tawsilaapp.ui.screens.driver.DriverOrdersScreen
import com.albasel.tawsilaapp.ui.screens.history.RideHistoryScreen
import com.albasel.tawsilaapp.ui.screens.home.HomeScreen
import com.albasel.tawsilaapp.ui.screens.chat.ChatScreen
import com.albasel.tawsilaapp.ui.screens.map.NearestDriversScreen
import com.albasel.tawsilaapp.ui.screens.map.TrackRideScreen
import com.albasel.tawsilaapp.ui.screens.profile.ProfileScreen
import com.albasel.tawsilaapp.ui.screens.wallet.WalletScreen
import com.albasel.tawsilaapp.ui.theme.WasalnaAppTheme
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.messaging.FirebaseMessaging
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WasalnaApp(firebaseManager: FirebaseManager, initialRoute: String? = null, onRouteConsumed: () -> Unit = {}) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val fusedLocationClient = remember { com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context) }
    
    val sharedPrefs = remember { context.getSharedPreferences("tawsila_prefs", Context.MODE_PRIVATE) }
    val gson = remember { Gson() }
    
    var language by remember { mutableStateOf("AR") }
    var lastToggleTime by remember { 
        mutableStateOf(sharedPrefs.getLong("last_online_toggle_time", 0L))
    }
    var lastFirebaseUpdateTime by remember { mutableStateOf(0L) }
    
    var currentUser by remember { mutableStateOf<User?>(null) }
    var isUserLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val userJson = sharedPrefs.getString("user_data", null)
        if (userJson != null) {
            withContext(Dispatchers.Default) {
                try { 
                    val user = gson.fromJson(userJson, User::class.java)
                    withContext(Dispatchers.Main) { 
                        currentUser = user
                        isUserLoaded = true
                    }
                } catch(e: Exception) {
                    withContext(Dispatchers.Main) { isUserLoaded = true }
                }
            }
        } else {
            isUserLoaded = true
        }
    }

    fun saveUserLocally(user: User?) {
        // Optimization: Only update and save if data actually changed
        if (user == currentUser && user != null) return
        
        currentUser = user
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val editor = sharedPrefs.edit()
            if (user != null) {
                val json = gson.toJson(user)
                if (sharedPrefs.getString("user_data", "") == json) return@launch
                
                editor.putString("user_data", json)
                if (user.currentSessionId.isNotEmpty()) {
                    editor.putString("local_session_id", user.currentSessionId)
                }
            } else {
                editor.remove("user_data")
                editor.remove("local_session_id")
                try { FirebaseMessaging.getInstance().deleteToken().await() } catch(e: Exception) {}
            }
            editor.apply()
        }
    }

    LaunchedEffect(currentUser?.id) {
        currentUser?.id?.let { uid ->
            try {
                // Add timeout to prevent hanging on slow network during startup
                kotlinx.coroutines.withTimeoutOrNull(5000L) {
                    val snapshot = firebaseManager.database.child("users").child(uid).get().await()
                    if (!snapshot.exists()) {
                        saveUserLocally(null)
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("TawsilaApp", "Initial user check failed: ${e.message}")
            }
        }
    }

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    // Monitoring for Order Restrictions and Tracking
    var hasPendingOrAcceptedOrder by remember { mutableStateOf(false) }
    var hasOngoingTrip by remember { mutableStateOf(false) }
    val hasActiveTrip by remember { derivedStateOf { hasOngoingTrip } } 
    var activeOrderId by remember { mutableStateOf<String?>(null) }

    // Optimization: Single robust listener for active orders to prevent leaks and hangs
    DisposableEffect(currentUser?.id) {
        val uid = currentUser?.id ?: return@DisposableEffect onDispose {}
        val database = firebaseManager.database
        val ordersRef = database.child("orders")
        
        val userOrders = mutableMapOf<String, String>()
        val driverOrders = mutableMapOf<String, String>()
        val statusListeners = mutableMapOf<String, ValueEventListener>()

        fun refreshFlags() {
            val allOrders = userOrders.values + driverOrders.values
            // Blocking logic: ONLY PENDING or ACCEPTED
            // If confirmed, arrived or started, user CAN make a new order.
            hasPendingOrAcceptedOrder = userOrders.values.any { it == "PENDING" || it == "ACCEPTED" }
            
            // Tracking logic: Any status that isn't terminal
            hasOngoingTrip = allOrders.any { it in listOf("CONFIRMED", "ARRIVED", "STARTED") }
            
            activeOrderId = (userOrders + driverOrders).filter { 
                it.value in listOf("PENDING", "ACCEPTED", "CONFIRMED", "ARRIVED", "STARTED") 
            }.keys.firstOrNull()
        }

        fun attachStatusListener(oid: String, isUser: Boolean) {
            if (statusListeners.containsKey(oid)) return
            val listener = object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    val status = s.getValue(String::class.java) ?: "UNKNOWN"
                    if (isUser) userOrders[oid] = status else driverOrders[oid] = status
                    refreshFlags()
                }
                override fun onCancelled(e: DatabaseError) {}
            }
            statusListeners[oid] = listener
            ordersRef.child(oid).child("status").addValueEventListener(listener)
        }

        fun createActiveOrdersListener(isUser: Boolean) = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val currentKeys = snapshot.children.mapNotNull { it.key }.toSet()
                
                // Cleanup removed orders
                val mapToUpdate = if (isUser) userOrders else driverOrders
                val toRemove = mapToUpdate.keys.filter { !currentKeys.contains(it) }
                
                toRemove.forEach { oid ->
                    statusListeners[oid]?.let { ordersRef.child(oid).child("status").removeEventListener(it) }
                    statusListeners.remove(oid)
                    userOrders.remove(oid)
                    driverOrders.remove(oid)
                }

                if (currentKeys.isEmpty()) {
                    if (isUser) userOrders.clear() else driverOrders.clear()
                    refreshFlags()
                } else {
                    // Add new orders and refresh
                    currentKeys.forEach { oid ->
                        attachStatusListener(oid, isUser)
                    }
                    refreshFlags()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }

        val userIdx = database.child("users").child(uid).child("active_orders")
        val userActiveListener = createActiveOrdersListener(true)

        userIdx.addValueEventListener(userActiveListener)

        onDispose {
            userIdx.removeEventListener(userActiveListener)
            statusListeners.forEach { (oid, listener) -> ordersRef.child(oid).child("status").removeEventListener(listener) }
        }
    }

    LaunchedEffect(currentUser?.id, currentRoute, hasActiveTrip) {
        val user = currentUser ?: run {
            com.albasel.tawsilaapp.service.LocationForegroundService.stopService(context)
            return@LaunchedEffect
        }
        
        if (!user.isOnline) {
            com.albasel.tawsilaapp.service.LocationForegroundService.stopService(context)
            return@LaunchedEffect
        }

        val canActAsDriver = user.role?.uppercase() == "DRIVER" || 
                             user.role?.uppercase() == "ADMIN" || 
                             !user.vehiclePlate.isNullOrEmpty()

        val isTrackingActive = currentRoute?.startsWith("track_ride") == true
        val isOrdersActive = currentRoute?.startsWith("driver_orders") == true
        
        val mode = when {
            isTrackingActive -> com.albasel.tawsilaapp.data.model.LocationMode.ACTIVE_TRIP
            canActAsDriver && hasActiveTrip -> com.albasel.tawsilaapp.data.model.LocationMode.ACTIVE_TRIP
            isOrdersActive -> com.albasel.tawsilaapp.data.model.LocationMode.ORDER_COLLECTION
            canActAsDriver -> com.albasel.tawsilaapp.data.model.LocationMode.NORMAL
            else -> com.albasel.tawsilaapp.data.model.LocationMode.OFFLINE
        }

        if (mode == com.albasel.tawsilaapp.data.model.LocationMode.OFFLINE) {
            try { com.albasel.tawsilaapp.service.LocationForegroundService.stopService(context) } catch (e: Exception) {}
        } else {
            val orderId = if (isTrackingActive) {
                currentRoute?.split("/")?.lastOrNull()?.split("?")?.firstOrNull()
            } else activeOrderId

            try {
                com.albasel.tawsilaapp.service.LocationForegroundService.startService(
                    context = context, userId = user.id, mode = mode, orderId = orderId,
                    province = user.province, vType = user.vehicleType, name = user.name,
                    isAppr = user.isApproved, canDel = user.canDeliver, canCar = user.canCargo
                )
            } catch (e: Exception) {
                Log.e("TawsilaApp", "Error triggering location service: ${e.message}")
            }
        }
    }

    DisposableEffect(currentUser?.id) {
        val uid = currentUser?.id
        if (uid == null) return@DisposableEffect onDispose {}

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    if (!snapshot.exists()) {
                        scope.launch {
                            Toast.makeText(context, if(language=="AR") "هذا الحساب لم يعد متاحاً" else "Account no longer available", Toast.LENGTH_LONG).show()
                            saveUserLocally(null)
                            navController.navigate(Screen.Login.route) { popUpTo(0) }
                        }
                        return
                    }
                    
                    val updatedUser = snapshot.getValue(User::class.java)
                    if (updatedUser != null && updatedUser.id.isNotEmpty()) {
                        // CRITICAL: Prevent "bouncing" state
                        // If we just toggled locally, we ignore the server's isOnline value for 15 seconds
                        // to let the DB catch up and propagate the change properly.
                        val effectiveIsOnline = if (System.currentTimeMillis() - lastToggleTime < 15000) {
                            currentUser?.isOnline ?: updatedUser.isOnline
                        } else {
                            updatedUser.isOnline
                        }

                        val userToCompare = updatedUser.copy(isOnline = effectiveIsOnline)
                        
                        val latestLocalSid = sharedPrefs.getString("local_session_id", "") ?: ""
                        val currentDeviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
                        
                        // Session check...
                        val isDifferentSession = latestLocalSid.isNotEmpty() && 
                                                 updatedUser.currentSessionId.isNotEmpty() && 
                                                 updatedUser.currentSessionId != latestLocalSid
                        
                        val isDifferentDevice = updatedUser.currentDeviceId.isNotEmpty() && 
                                                updatedUser.currentDeviceId != currentDeviceId

                        if (isDifferentSession && isDifferentDevice) {
                            scope.launch {
                                Toast.makeText(context, if(language=="AR") "تم تسجيل الدخول من جهاز آخر" else "Logged in from another device", Toast.LENGTH_LONG).show()
                                saveUserLocally(null)
                                navController.navigate(Screen.Login.route) { popUpTo(0) }
                            }
                            return
                        }
                        
                        // Sensitivity check for points & financials: avoid tiny floating point drift updates
                        val pointsChanged = kotlin.math.abs(updatedUser.points - (currentUser?.points ?: 0.0)) > 0.01
                        val driverPointsChanged = kotlin.math.abs(updatedUser.driverPoints - (currentUser?.driverPoints ?: 0.0)) > 0.01
                        val paidCommChanged = kotlin.math.abs(updatedUser.paidCommission - (currentUser?.paidCommission ?: 0.0)) > 0.01
                        val totalEarningsChanged = kotlin.math.abs(updatedUser.totalEarnings - (currentUser?.totalEarnings ?: 0.0)) > 0.01
                        val walletBalanceChanged = kotlin.math.abs(updatedUser.walletBalance - (currentUser?.walletBalance ?: 0.0)) > 0.01

                        val isCriticalChange = currentUser == null || (
                            updatedUser.name != currentUser?.name ||
                            updatedUser.phone != currentUser?.phone ||
                            updatedUser.role != currentUser?.role ||
                            updatedUser.isApproved != currentUser?.isApproved ||
                            effectiveIsOnline != currentUser?.isOnline ||
                            updatedUser.currentSessionId != currentUser?.currentSessionId ||
                            pointsChanged ||
                            driverPointsChanged ||
                            paidCommChanged ||
                            totalEarningsChanged ||
                            walletBalanceChanged ||
                            updatedUser.fcmToken != currentUser?.fcmToken
                        )

                        if (isCriticalChange) {
                            saveUserLocally(updatedUser.copy(isOnline = effectiveIsOnline))
                        }
                    }
                } catch (e: Exception) {
                    Log.e("TawsilaApp", "Error parsing current user $uid: ${e.message}")
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        
        val ref = firebaseManager.database.child("users").child(uid)
        ref.addValueEventListener(listener)
        
        onDispose {
            ref.removeEventListener(listener)
        }
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var drawerGesturesEnabled by remember { mutableStateOf(true) }
    
    var isDarkTheme by remember { 
        val initial = if (sharedPrefs.contains("dark_theme")) {
            sharedPrefs.getBoolean("dark_theme", false)
        } else {
            // Default to Light Theme (false) for all phones
            sharedPrefs.edit().putBoolean("dark_theme", false).apply()
            false
        }
        mutableStateOf(initial)
    }
    
    var notificationsEnabled by remember { 
        mutableStateOf(sharedPrefs.getBoolean("notifications_enabled", true)) 
    }
    
    // Sync Notification status with Database (FCM Token)
    LaunchedEffect(notificationsEnabled, currentUser?.id) {
        val uid = currentUser?.id ?: return@LaunchedEffect
        val db = firebaseManager.database
        
        sharedPrefs.edit().putBoolean("notifications_enabled", notificationsEnabled).apply()
        
        if (!notificationsEnabled) {
            // Remove token from DB so server stops sending
            db.child("users").child(uid).child("fcmToken").setValue("").await()
        } else {
            // Restore token from FirebaseMessaging
            try {
                val token = com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
                db.child("users").child(uid).child("fcmToken").setValue(token).await()
            } catch (e: Exception) {}
        }
    }
    var showContactDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var selectedDriverForOrder by remember { mutableStateOf<User?>(null) }
    var driverToChooseMode by remember { mutableStateOf<User?>(null) }
    var adminMessage by remember { mutableStateOf<Map<String, Any>?>(null) }
    var adminContactReply by remember { mutableStateOf<Map<String, Any>?>(null) }

    LaunchedEffect(currentUser) {
        currentUser?.let { user ->
            try {
                val reply = firebaseManager.getUserContactReply(user.id)
                if (reply != null) {
                    adminContactReply = reply
                    val replyId = reply["id"]?.toString() ?: ""
                    if (replyId.isNotBlank()) {
                        firebaseManager.markContactReplyOpened(user.id, replyId)
                    }
                }
            } catch (e: Exception) {}
        }
    }

    val isAr = language == "AR"

    val showScaffold = currentRoute != null && currentRoute != Screen.Splash.route && currentRoute != Screen.Login.route && currentRoute != "register"

    // Safety Navigator: Instant 50ms leave splash screen to prevent stuck/freezing when background service is active
    LaunchedEffect(currentRoute) {
        if (currentRoute == Screen.Splash.route) {
            val hasSavedSession = sharedPrefs.getString("user_data", null) != null
            val target = if (currentUser != null || hasSavedSession) Screen.Home.route else Screen.Login.route
            delay(50L) // Instant 50ms transition
            if (navController.currentDestination?.route == Screen.Splash.route) {
                navController.navigate(target) { popUpTo(Screen.Splash.route) { inclusive = true } }
            }
        }
    }

    // Deep-link / Notification Navigation Handler
    LaunchedEffect(initialRoute) {
        if (!initialRoute.isNullOrEmpty() && initialRoute != Screen.Splash.route && 
            currentRoute != null && currentRoute != Screen.Splash.route) {
            try {
                if (drawerState.isOpen) {
                    scope.launch { drawerState.close() }
                }
                drawerState.snapTo(DrawerValue.Closed)
                
                val safeTargetRoute = if (initialRoute.contains("promo") || initialRoute.contains("offer")) Screen.Home.route else initialRoute
                navController.navigate(safeTargetRoute) {
                    launchSingleTop = true
                }
                onRouteConsumed()
            } catch (e: Exception) {
                Log.e("TawsilaApp", "DeepLink navigation failed for: $initialRoute - ${e.message}")
            }
        }
    }

    // Global back button handler: Ensure back always goes to Home Travel if possible
    val isCargo = sharedPrefs.getBoolean("cargo_mode", false)
    BackHandler(enabled = (currentRoute != Screen.Home.route || isCargo) && showScaffold) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else {
            if (isCargo) {
                sharedPrefs.edit().putBoolean("cargo_mode", false).apply()
                // Force recompose by navigating to same route or just letting state update if it was observed
                navController.navigate(Screen.Home.route) { popUpTo(0) }
            } else {
                navController.navigate(Screen.Home.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    LaunchedEffect(currentRoute) {
        // Automatically close sidebar whenever the route changes
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        }
    }

    LaunchedEffect(currentUser?.id) {
        currentUser?.let { user ->
            // Always ensure token is fresh and saved in DB on app start/login
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                if (!token.isNullOrEmpty()) {
                    firebaseManager.database.child("users").child(user.id).child("fcmToken").setValue(token).await()
                }
            } catch(e: Exception) {
                Log.e("TawsilaApp", "Token update error: ${e.message}")
            }
        }
    }

    DisposableEffect(currentUser?.id) {
        val uid = currentUser?.id
        if (uid == null) return@DisposableEffect onDispose {}
        
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!notificationsEnabled) return
                // ROLE CHECK: Only process system notifications if the user is currently an ADMIN
                // This prevents regular users from seeing admin dialogs on shared devices
                if (currentUser?.role?.uppercase() != "ADMIN") return

                snapshot.children.forEach { child ->
                    val id = child.key ?: return@forEach
                    val isRead = sharedPrefs.getString("admin_notif_dialog_$id", "") == "READ"
                    if (!isRead) {
                        val msg = child.value as? Map<String, Any>
                        if (msg != null && msg["type"] == "ADMIN_DIALOG") {
                            adminMessage = msg
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        
        val ref = firebaseManager.database.child("system_notifications").child(uid)
        ref.addValueEventListener(listener)
        
        onDispose {
            ref.removeEventListener(listener)
        }
    }

    val permissionsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    val settingResultLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {}
    var hasPromptedLocationSettings by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val isDriver = currentUser?.role?.uppercase() == "DRIVER"
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        // Camera permission is only requested for DRIVERS, not regular CUSTOMERS
        if (isDriver) {
            permissions.add(Manifest.permission.CAMERA)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        // Only launch if not all granted
        val needsPermission = permissions.any { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (needsPermission) {
            permissionsLauncher.launch(permissions.toTypedArray())
        }

        // Check location settings only ONCE on startup if GPS AND Network providers are both OFF
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        val isGpsOn = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
        val isNetworkOn = locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
        
        if (!hasPromptedLocationSettings && !isGpsOn && !isNetworkOn) {
            hasPromptedLocationSettings = true
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000).build()
            val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest).setAlwaysShow(true)
            val client: SettingsClient = LocationServices.getSettingsClient(context)
            val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())

            task.addOnFailureListener { exception ->
                if (exception is ResolvableApiException) {
                    try {
                        val intentSenderRequest = androidx.activity.result.IntentSenderRequest.Builder(exception.resolution.intentSender).build()
                        settingResultLauncher.launch(intentSenderRequest)
                    } catch (sendEx: Exception) {
                        Log.e("TawsilaApp", "Error launching location resolution", sendEx)
                    }
                }
            }
        }
    }

    LaunchedEffect(currentUser?.id, currentUser?.isOnline) {
        val user = currentUser
        if (user != null && user.isOnline) {
            val canActAsDriver = user.role?.uppercase() == "DRIVER" || 
                                 user.role?.uppercase() == "ADMIN" || 
                                 !user.vehiclePlate.isNullOrEmpty()
            
            if (canActAsDriver) {
                // Ensure immediate visibility on map upon app start/re-entry
                try {
                    fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                        loc?.let {
                            scope.launch(Dispatchers.IO) {
                                firebaseManager.updateLocationLightweight(
                                    user.id, it.latitude, it.longitude, user.province,
                                    user.vehicleType, true, user.name, user.isApproved, 
                                    user.canDeliver, user.canCargo
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("TawsilaApp", "Heartbeat error: ${e.message}")
                }
            }
        }
    }

    WasalnaAppTheme(darkTheme = isDarkTheme) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = showScaffold && (drawerGesturesEnabled || drawerState.isOpen),
            drawerContent = {
                if (showScaffold) {
                    ModalDrawerSheet(
                        modifier = Modifier.width(300.dp).fillMaxHeight(),
                        drawerContainerColor = MaterialTheme.colorScheme.background,
                        drawerShape = RoundedCornerShape(topEnd = 0.dp, bottomEnd = 0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                                Image(painter = painterResource(id = R.drawable.header_bg), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.7f)))))
                                Text("WASALNA", modifier = Modifier.align(Alignment.BottomStart).padding(20.dp), color = Color(0xFFC5A059), fontSize = 28.sp, fontWeight = FontWeight.Black)
                            }
                            Column(modifier = Modifier.padding(12.dp).weight(1f).verticalScroll(rememberScrollState())) {
                                DrawerItem(if(language=="AR") "الرئيسية" else "Home", Icons.Default.Home, Color(0xFF2196F3)) { 
                                    navController.navigate(Screen.Home.route); 
                                    sharedPrefs.edit().putBoolean("cargo_mode", false).apply()
                                    scope.launch { drawerState.close() } 
                                }
                                
                                val isDriverUser = currentUser?.role?.uppercase() == "DRIVER" || 
                                                   (currentUser?.role?.uppercase() == "ADMIN" && !currentUser?.vehiclePlate.isNullOrEmpty())

                                if (isDriverUser && currentUser?.isApproved == true) {
                                    val isOnline = currentUser?.isOnline == true
                                    Surface(
                                        onClick = {
                                            val currentU = currentUser ?: return@Surface
                                            val nextState = !isOnline
                                            val now = System.currentTimeMillis()
                                            lastToggleTime = now
                                            sharedPrefs.edit().putLong("last_online_toggle_time", now).apply()
                                            
                                            // Optimistic local state update
                                            currentUser = currentU.copy(isOnline = nextState)

                                            // Explicit Service Lifecycle Management
                                            if (nextState) {
                                                com.albasel.tawsilaapp.service.LocationForegroundService.startService(
                                                    context, currentU.id, com.albasel.tawsilaapp.data.model.LocationMode.NORMAL,
                                                    province = currentU.province, vType = currentU.vehicleType, name = currentU.name,
                                                    isAppr = currentU.isApproved, canDel = currentU.canDeliver, canCar = currentU.canCargo
                                                )
                                            } else {
                                                com.albasel.tawsilaapp.service.LocationForegroundService.stopService(context)
                                            }
                                            
                                            scope.launch {
                                                try {
                                                    firebaseManager.updateDriverOnlineStatus(currentU.id, nextState)
                                                    // Ensure persistence
                                                    saveUserLocally(currentU.copy(isOnline = nextState))
                                                    val msg = if(nextState) "متصل الآن" else "غير متصل"
                                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                } catch (e: Exception) {
                                                    // Revert on failure
                                                    currentUser = currentU
                                                    Toast.makeText(context, "فشل التحديث", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        color = if(isOnline) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                                    ) {
                                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                Icon(
                                                    if(isOnline) Icons.Default.Wifi else Icons.Default.WifiOff, 
                                                    null, 
                                                    tint = if(isOnline) Color(0xFF2E7D32) else Color.Red,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(Modifier.width(12.dp))
                                                Text(
                                                    if(language=="AR") (if(isOnline) "أنت متصل الآن" else "أنت غير متصل") else (if(isOnline) "Online" else "Offline"),
                                                    fontWeight = FontWeight.Bold,
                                                    color = if(isOnline) Color(0xFF2E7D32) else Color.Red
                                                )
                                            }
                                            Switch(
                                                checked = isOnline, 
                                                onCheckedChange = null, // Surface handles click
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = Color.White,
                                                    checkedTrackColor = Color(0xFF2E7D32),
                                                    uncheckedThumbColor = Color.White,
                                                    uncheckedTrackColor = Color.Red
                                                )
                                            )
                                        }
                                    }
                                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                }

                                if ((isDriverUser || currentUser?.phone == "01098804131") && currentUser?.isApproved == true) {
                                    DrawerItem(if(language=="AR") "الأوردرات المتاحة" else "Available Orders", Icons.AutoMirrored.Filled.FormatListBulleted, Color(0xFFE91E63)) { navController.navigate("driver_orders?tab=0"); scope.launch { drawerState.close() } }
                                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                }
                                DrawerItem(if(language=="AR") "المحفظة" else "Wallet", Icons.Default.AccountBalanceWallet, Color(0xFFFF9800)) { navController.navigate(Screen.Wallet.route); scope.launch { drawerState.close() } }
                                if (currentUser?.role == "ADMIN" || currentUser?.phone == "01098804131" || currentUser?.phone == "01011850314") {
                                    DrawerItem(if(language=="AR") "لوحة التحكم" else "Admin Panel", Icons.Default.AdminPanelSettings, Color(0xFF607D8B)) { navController.navigate(Screen.AdminHome.route); scope.launch { drawerState.close() } }
                                }
                                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                                DrawerItem(if(language=="AR") "الإعدادات" else "Settings", Icons.Default.Settings, Color(0xFF9C27B0)) { 
                                    showSettingsDialog = true
                                    scope.launch { drawerState.close() }
                                }

                                DrawerItem(if(language=="AR") "تواصل معنا" else "Contact Us", Icons.Default.SupportAgent, Color(0xFF00BCD4)) { showContactDialog = true; scope.launch { drawerState.close() } }

                                DrawerItem(if(language=="AR") "خروج" else "Logout", Icons.Default.Logout, Color.Red) { 
                                    currentUser?.let { user ->
                                        scope.launch {
                                            try {
                                                // 1. Force Offline and Clear FCM token in DB
                                                val updates = mapOf(
                                                    "isOnline" to false,
                                                    "fcmToken" to "",
                                                    "currentSessionId" to ""
                                                )
                                                firebaseManager.database.child("users").child(user.id).updateChildren(updates).await()
                                                
                                                // 2. Clear local data
                                                saveUserLocally(null)
                                                withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                    navController.navigate(Screen.Login.route) { popUpTo(0) }
                                                }
                                            } catch (e: Exception) {
                                                saveUserLocally(null)
                                                navController.navigate(Screen.Login.route) { popUpTo(0) }
                                            }
                                        }
                                    } ?: run {
                                        saveUserLocally(null)
                                        navController.navigate(Screen.Login.route) { popUpTo(0) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        ) {
            Box(Modifier.fillMaxSize()) {
                NavHost(navController = navController, startDestination = Screen.Splash.route) {
                    composable(Screen.Splash.route) { 
                        SplashScreen { 
                            sharedPrefs.edit().putBoolean("cargo_mode", false).apply() // Reset to Travel mode on startup
                            
                            // Critical: Wait until user is fully deserialized to avoid race conditions
                            scope.launch {
                                while (!isUserLoaded) { delay(100) }
                                
                                val target = if (currentUser != null) {
                                    if (!initialRoute.isNullOrEmpty() && initialRoute != Screen.Splash.route) {
                                        val r = initialRoute
                                        onRouteConsumed()
                                        r
                                    } else {
                                        Screen.Home.route
                                    }
                                } else {
                                    Screen.Login.route
                                }
                                
                                withContext(Dispatchers.Main) {
                                    navController.navigate(target) { 
                                        popUpTo(Screen.Splash.route) { inclusive = true } 
                                    } 
                                }
                            }
                        } 
                    }
                    composable(Screen.Login.route) { 
                        LoginScreen(
                            firebaseManager, 
                            language, 
                            onLoginSuccess = { user -> saveUserLocally(user); navController.navigate(Screen.Home.route) { popUpTo(Screen.Login.route) { inclusive = true } } }, 
                            onRegisterClick = { navController.navigate("register") }
                        ) 
                    }
                    composable("register") { 
                        RegisterScreen(
                            firebaseManager, 
                            language, 
                            onRegisterSuccess = { user -> saveUserLocally(user); navController.navigate(Screen.Home.route) { popUpTo("register") { inclusive = true } } }, 
                            onBackToLogin = { navController.popBackStack() }
                        ) 
                    }
                    composable(Screen.Home.route) { 
                        UberDashboardHomeScreen(
                            firebaseManager = firebaseManager,
                            language = language,
                            user = currentUser,
                            isDarkMode = isDarkTheme,
                            onMenuClick = { scope.launch { drawerState.open() } },
                            onToggleTheme = { isDarkTheme = !isDarkTheme; sharedPrefs.edit().putBoolean("dark_theme", isDarkTheme).apply() },
                            onSafarClick = {
                                sharedPrefs.edit().putBoolean("cargo_mode", false).apply()
                                navController.navigate("safar_booking?focus=DEST")
                            },
                            onCityClick = { navController.navigate("inside_city") },
                            onNearbyClick = { navController.navigate("nearest_drivers") },
                            onCargoClick = {
                                sharedPrefs.edit().putBoolean("cargo_mode", true).apply()
                                navController.navigate("safar_booking")
                            },
                            onSearchSelect = { title, lat, lng ->
                                val encoded = Uri.encode(title)
                                navController.navigate("safar_booking?focus=DEST&dest=$encoded&lat=$lat&lng=$lng")
                            },
                            onNotificationsClick = { target, sourceId ->
                                try {
                                    if (target.isNotEmpty()) navController.navigate(target)
                                } catch (e: Exception) {}
                            }
                        )
                    }
                    composable(
                        "safar_booking?focus={focus}&dest={dest}&lat={lat}&lng={lng}",
                        arguments = listOf(
                            navArgument("focus") { defaultValue = "none"; nullable = true },
                            navArgument("dest") { defaultValue = ""; nullable = true },
                            navArgument("lat") { defaultValue = "0.0"; nullable = true },
                            navArgument("lng") { defaultValue = "0.0"; nullable = true }
                        )
                    ) { backStackEntry ->
                        val focusParam = backStackEntry.arguments?.getString("focus")
                        val destParam = backStackEntry.arguments?.getString("dest") ?: ""
                        val latParam = backStackEntry.arguments?.getString("lat")?.toDoubleOrNull() ?: 0.0
                        val lngParam = backStackEntry.arguments?.getString("lng")?.toDoubleOrNull() ?: 0.0
                        HomeScreen(
                            firebaseManager = firebaseManager, 
                            language = language, 
                            user = currentUser, 
                            isDarkMode = isDarkTheme,
                            selectedDriver = selectedDriverForOrder,
                            drawerGesturesEnabled = drawerGesturesEnabled,
                            onToggleDrawerGestures = { drawerGesturesEnabled = it },
                            onClearSelectedDriver = { selectedDriverForOrder = null },
                            onMenuClick = { scope.launch { drawerState.open() } },
                            onUpdateUser = { saveUserLocally(it) },
                            onToggleTheme = { isDarkTheme = !isDarkTheme; sharedPrefs.edit().putBoolean("dark_theme", isDarkTheme).apply() },
                            hasBlockingOrder = hasPendingOrAcceptedOrder,
                            initialRoute = focusParam,
                            initialDestinationName = destParam,
                            initialDestinationLat = latParam,
                            initialDestinationLng = lngParam,
                            onCityClick = { navController.navigate("inside_city") },
                            onNearbyClick = { navController.navigate("nearest_drivers") },
                            onNotificationsClick = { target, sourceId -> 
                                try {
                                    if (target.isNotEmpty()) {
                                        // Standardize legacy ride_history and new history deep links
                                        val finalTarget = when {
                                            target.startsWith("ride_history?id=") -> "history?tab=PENDING&orderId=${target.substringAfter("id=")}"
                                            target.startsWith("history?orderId=") -> "history?tab=PENDING&${target.substringAfter("?")}"
                                            target.startsWith("history?tab=") -> target
                                            target == Screen.History.route -> "history?tab=PENDING"
                                            else -> target
                                        }

                                        val isKnownRoute = finalTarget.startsWith("admin_home") || 
                                                          finalTarget.startsWith("chat") || 
                                                          finalTarget.startsWith("track_ride") ||
                                                          finalTarget.startsWith("driver_orders") ||
                                                          finalTarget.startsWith("nearest_drivers") ||
                                                          finalTarget.startsWith("history") ||
                                                          finalTarget.startsWith("contact") ||
                                                          finalTarget == Screen.Home.route ||
                                                          finalTarget == Screen.Profile.route ||
                                                          finalTarget == Screen.Wallet.route

                                        if (finalTarget.startsWith("contact")) {
                                            currentUser?.let { user ->
                                                scope.launch {
                                                    val reply = firebaseManager.getUserContactReply(user.id)
                                                    if (reply != null) {
                                                        adminContactReply = reply
                                                    }
                                                }
                                            }
                                        }
                                        
                                        if (isKnownRoute && !finalTarget.startsWith("contact")) {
                                            navController.navigate(finalTarget) {
                                                launchSingleTop = true
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("TawsilaApp", "Secure Navigation error: ${e.message}")
                                }
                            }
                        ) 
                    }
                    composable(
                        "admin_home?tab={tab}&sub={sub}&targetId={targetId}",
                        arguments = listOf(
                            navArgument("tab") { defaultValue = "0" },
                            navArgument("sub") { defaultValue = "0" },
                            navArgument("targetId") { nullable = true; defaultValue = null }
                        )
                    ) { backStackEntry ->
                        val tab = (backStackEntry.arguments?.getString("tab") ?: "0").toIntOrNull() ?: 0
                        val sub = (backStackEntry.arguments?.getString("sub") ?: "0").toIntOrNull() ?: 0
                        val targetId = backStackEntry.arguments?.getString("targetId")
                        
                        val isAdminUser = currentUser?.role?.uppercase() == "ADMIN" || 
                                          currentUser?.phone == "01098804131" || 
                                          currentUser?.phone == "01011850314" || 
                                          currentUser?.phone == "01098804133"

                        if (isAdminUser) {
                            if (currentUser?.role?.uppercase() != "ADMIN") {
                                currentUser?.id?.let { uid ->
                                    scope.launch {
                                        firebaseManager.database.child("users").child(uid).child("role").setValue("ADMIN")
                                    }
                                }
                            }
                            AdminHomeScreen(firebaseManager, currentUser, initialTab = tab, initialSubTab = sub, targetId = targetId)
                        } else {
                            LaunchedEffect(Unit) { navController.navigate(Screen.Home.route) { popUpTo(0) } }
                        }
                    }
                    composable(
                        "history?tab={tab}&orderId={orderId}",
                        arguments = listOf(
                            navArgument("tab") { defaultValue = "PENDING" },
                            navArgument("orderId") { nullable = true; defaultValue = null }
                        )
                    ) { backStackEntry ->
                        val initialTab = backStackEntry.arguments?.getString("tab") ?: "PENDING"
                        val targetOrderId = backStackEntry.arguments?.getString("orderId")
                        RideHistoryScreen(
                            firebaseManager = firebaseManager, 
                            user = currentUser, 
                            language = language,
                            initialTab = initialTab,
                            initialOrderId = targetOrderId,
                            onChatClick = { tripId -> navController.navigate("chat/$tripId") },
                            onTrackClick = { lat, lng, name, oId -> 
                                navController.navigate("track_ride/$lat/$lng/$name?orderId=$oId") 
                            }
                        ) 
                    }
                    composable(
                        "nearest_drivers?type={type}",
                        arguments = listOf(navArgument("type") { nullable = true; defaultValue = null })
                    ) { backStackEntry ->
                        val initialType = backStackEntry.arguments?.getString("type")
                        NearestDriversScreen(
                            firebaseManager, language, 
                            isDarkMode = isDarkTheme,
                            initialFilter = initialType,
                            onToggleTheme = { 
                                isDarkTheme = !isDarkTheme 
                                sharedPrefs.edit().putBoolean("dark_theme", isDarkTheme).apply()
                            },
                            onToggleDrawerGestures = { drawerGesturesEnabled = it },
                            onDriverSelected = { driver -> 
                                if (currentUser?.isApproved != false) {
                                    val isCargoOnly = isCargoOnlyVehicle(driver.vehicleType)
                                    val canCargo = driver.canCargo || isCargoOnly
                                    val canDeliver = driver.canDeliver && !isCargoOnly

                                    if (canCargo && canDeliver) {
                                        driverToChooseMode = driver
                                    } else if (canCargo) {
                                        sharedPrefs.edit().putBoolean("cargo_mode", true).apply()
                                        selectedDriverForOrder = driver
                                        navController.navigate(Screen.Home.route) { popUpTo(Screen.Home.route) { inclusive = true } } 
                                    } else {
                                        sharedPrefs.edit().putBoolean("cargo_mode", false).apply()
                                        selectedDriverForOrder = driver
                                        navController.navigate(Screen.Home.route) { popUpTo(Screen.Home.route) { inclusive = true } } 
                                    }
                                } else {
                                    Toast.makeText(context, if(language=="AR") "عذراً، حسابك معلق حالياً" else "Account suspended", Toast.LENGTH_LONG).show()
                                }
                            }
                        ) 
                    }
                    composable("inside_city") { 
                        InsideCityScreen(
                            firebaseManager, language, currentUser,
                            isDarkMode = isDarkTheme,
                            hasBlockingOrder = hasPendingOrAcceptedOrder
                        ) 
                    }
                    composable(
                        "driver_orders?tab={tab}&orderId={orderId}", 
                        arguments = listOf(
                            navArgument("tab"){ defaultValue = "0" },
                            navArgument("orderId"){ nullable = true; defaultValue = null }
                        )
                    ) { bse ->
                        val tab = bse.arguments?.getString("tab")?.toIntOrNull() ?: 0
                        val targetOrderId = bse.arguments?.getString("orderId")
                        currentUser?.let { driver ->
                            DriverOrdersScreen(
                                firebaseManager, driver, language, initialTab = tab,
                                initialOrderId = targetOrderId,
                                onChatClick = { tripId -> navController.navigate("chat/$tripId") },
                                onTrackClick = { lat, lng, name, orderId -> 
                                    navController.navigate("track_ride/$lat/$lng/$name?orderId=$orderId") 
                                }
                            ) 
                        }
                    }
                    composable(Screen.Profile.route) { 
                        ProfileScreen(
                            firebaseManager = firebaseManager,
                            user = currentUser,
                            language = language,
                            isDarkMode = isDarkTheme,
                            onMenuClick = { scope.launch { drawerState.open() } },
                            onUpdateUser = { updatedUser ->
                                scope.launch {
                                    firebaseManager.saveUser(updatedUser)
                                    saveUserLocally(updatedUser)
                                }
                            },
                            onLogout = { 
                                currentUser?.let { user ->
                                    scope.launch {
                                        firebaseManager.updateDriverOnlineStatus(user.id, false)
                                        firebaseManager.database.child("users").child(user.id).child("currentSessionId").setValue("").await()
                                    }
                                }
                                saveUserLocally(null)
                                navController.navigate(Screen.Login.route) { popUpTo(0) } 
                            }
                        ) 
                    }
                    composable(Screen.Wallet.route) { 
                        WalletScreen(
                            firebaseManager = firebaseManager, 
                            user = currentUser, 
                            language = language,
                            isDarkMode = isDarkTheme,
                            onMenuClick = { scope.launch { drawerState.open() } }
                        ) 
                    }
                    composable(
                        "track_ride/{lat}/{lng}/{name}?orderId={orderId}", 
                        arguments = listOf(
                            navArgument("lat"){type=NavType.StringType}, 
                            navArgument("lng"){type=NavType.StringType}, 
                            navArgument("name"){type=NavType.StringType},
                            navArgument("orderId"){type=NavType.StringType; nullable = true; defaultValue = null}
                        )
                    ) { 
                        TrackRideScreen(
                            firebaseManager, 
                            it.arguments?.getString("lat")?.toDoubleOrNull()?:0.0, 
                            it.arguments?.getString("lng")?.toDoubleOrNull()?:0.0, 
                            it.arguments?.getString("name")?:"", 
                            language,
                            currentUser,
                            isDarkMode = isDarkTheme,
                            onToggleTheme = { 
                                isDarkTheme = !isDarkTheme 
                                sharedPrefs.edit().putBoolean("dark_theme", isDarkTheme).apply()
                            },
                            onToggleDrawerGestures = { drawerGesturesEnabled = it },
                            orderId = it.arguments?.getString("orderId"),
                            onChatClick = { tripId -> navController.navigate("chat/$tripId") }
                        ) 
                    }

                    composable("chat/{tripId}") { backStackEntry ->
                        val tripId = backStackEntry.arguments?.getString("tripId") ?: ""
                        ChatScreen(
                            firebaseManager = firebaseManager,
                            tripId = tripId,
                            currentUser = currentUser,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }

                val isNavVisibleScreen = currentRoute == Screen.Home.route || 
                    currentRoute?.startsWith("safar_booking") == true ||
                    currentRoute?.startsWith("nearest_drivers") == true || 
                    currentRoute?.startsWith("inside_city") == true || 
                    currentRoute == Screen.History.route ||
                    currentRoute?.startsWith("history") == true ||
                    currentRoute == Screen.Profile.route ||
                    currentRoute?.startsWith("profile") == true ||
                    currentRoute?.startsWith(Screen.Tracking.route) == true ||
                    currentRoute?.startsWith("track_ride") == true

                if (showScaffold && isNavVisibleScreen) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                        ) {
                            val navBg = if (isDarkTheme) Color(0xFF1E1E1E) else Color.White
                            val goldBorderColor = Color(0xFFC5A059).copy(alpha = 0.45f)
                            val activeColor = Color(0xFFD4AF5A)
                            val inactiveColor = if (isDarkTheme) Color(0xFF9AA8B8) else Color.Gray

                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                                    .background(navBg)
                                    .align(Alignment.BottomCenter)
                            )
                            
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                                    .height(62.dp),
                                shape = RoundedCornerShape(20.dp),
                                color = navBg,
                                border = BorderStroke(1.dp, goldBorderColor),
                                shadowElevation = 6.dp
                            ) {
                                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                                    val rHome = Screen.Home.route
                                    val rTrack = "track_ride/none/none/none"
                                    val rHistory = Screen.History.route
                                    val rProfile = Screen.Profile.route
                                    
                                    val isHome = currentRoute == rHome
                                    val isTrack = currentRoute?.startsWith("track_ride") == true || currentRoute?.startsWith(Screen.Tracking.route) == true
                                    val isHistory = currentRoute == rHistory || currentRoute?.startsWith("history") == true
                                    val isProfile = currentRoute == rProfile || currentRoute?.startsWith("profile") == true

                                    // 1. الرئيسية (Far Right in RTL)
                                    NavBarItem(if(isAr) "الرئيسية" else "Home", Icons.Default.Home, isHome, activeColor, inactiveColor) { 
                                        if (!isHome) {
                                            sharedPrefs.edit().putBoolean("cargo_mode", false).apply()
                                            navController.navigate(rHome) { 
                                                popUpTo(navController.graph.findStartDestination().id) { inclusive = false; saveState = true }
                                                launchSingleTop = true 
                                                restoreState = true
                                            }
                                        }
                                    }

                                    // 2. تتبع الرحلة
                                    NavBarItem(if(isAr) "تتبع الرحلة" else "Track Ride", Icons.Default.Map, isTrack, activeColor, inactiveColor) { 
                                        if (!isTrack) {
                                            navController.navigate(rTrack) { 
                                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                launchSingleTop = true 
                                                restoreState = true
                                            } 
                                        }
                                    }

                                    // 3. نشاطي
                                    NavBarItem(if(isAr) "نشاطي" else "Activity", Icons.Default.History, isHistory, activeColor, inactiveColor) { 
                                        if (!isHistory) {
                                            navController.navigate(rHistory) { 
                                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                launchSingleTop = true 
                                                restoreState = true
                                            } 
                                        }
                                    }

                                    // 4. حسابي (Far Left in RTL)
                                    NavBarItem(if(isAr) "حسابي" else "Profile", Icons.Default.AccountCircle, isProfile, activeColor, inactiveColor) { 
                                        if (!isProfile) {
                                            navController.navigate(rProfile) { 
                                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                launchSingleTop = true 
                                                restoreState = true
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

    if (showContactDialog) {
        ContactUsDialog(
            onSend = { msg -> 
                scope.launch {
                    try {
                        firebaseManager.sendContactMessage(currentUser?.name ?: "Unknown", currentUser?.phone ?: "000", msg)
                        showContactDialog = false
                        Toast.makeText(context, if(language=="AR") "تم إرسال الرسالة بنجاح" else "Message sent successfully", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e("TawsilaApp", "Contact error", e)
                    }
                } 
            }, 
            onDismiss = { showContactDialog = false }
        )
    }

    adminMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { 
                adminMessage = null 
                sharedPrefs.edit().putString("admin_notif_dialog_${msg["id"]}", "READ").apply()
            },
            title = { Text(msg["title"]?.toString() ?: (if(language=="AR") "رسالة إدارية" else "Admin Message"), color = Color(0xFFC5A059), fontWeight = FontWeight.Bold) },
            text = { Text(msg["content"]?.toString() ?: "", fontSize = 16.sp) },
            confirmButton = {
                Button(onClick = { 
                    adminMessage = null 
                    sharedPrefs.edit().putString("admin_notif_dialog_${msg["id"]}", "READ").apply()
                }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC5A059))) {
                    Text(if(language=="AR") "حسناً" else "OK", color = Color.White)
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color.White
        )
    }

    adminContactReply?.let { reply ->
        val replyId = reply["id"]?.toString() ?: ""
        val uId = currentUser?.id ?: ""
        AlertDialog(
            onDismissRequest = {
                adminContactReply = null
                if (uId.isNotBlank() && replyId.isNotBlank()) {
                    scope.launch { firebaseManager.deleteContactReply(uId, replyId) }
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SupportAgent, null, tint = Color(0xFFC5A059), modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if(isAr) "رد الإدارة على تواصل معنا" else "Admin Reply", color = Color(0xFFC5A059), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column {
                    Text(reply["reply"]?.toString() ?: "", fontSize = 15.sp, color = Color.Black)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        adminContactReply = null
                        if (uId.isNotBlank() && replyId.isNotBlank()) {
                            scope.launch { firebaseManager.deleteContactReply(uId, replyId) }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC5A059))
                ) {
                    Text(if(isAr) "إغلاق" else "Close", color = Color.White)
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color.White
        )
    }

    driverToChooseMode?.let { driver ->
        AlertDialog(
            onDismissRequest = { driverToChooseMode = null },
            title = { Text(if(language=="AR") "اختر نوع الخدمة" else "Choose Service", color = Color(0xFFC5A059), fontWeight = FontWeight.Bold) },
            text = { Text(if(language=="AR") "هذا السائق يقدم خدمات السفر ونقل البضائع. ماذا تريد؟" else "This driver offers both Travel and Cargo services. What do you need?") },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { 
                            sharedPrefs.edit().putBoolean("cargo_mode", false).apply()
                            selectedDriverForOrder = driver
                            driverToChooseMode = null
                            navController.navigate(Screen.Home.route) { popUpTo(Screen.Home.route) { inclusive = true } } 
                        }, 
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                    ) {
                        Text(if(language=="AR") "سفر / توصيل" else "Travel / Ride", color = Color.White)
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { 
                            sharedPrefs.edit().putBoolean("cargo_mode", true).apply()
                            selectedDriverForOrder = driver
                            driverToChooseMode = null
                            navController.navigate(Screen.Home.route) { popUpTo(Screen.Home.route) { inclusive = true } } 
                        }, 
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF795548))
                    ) {
                        Text(if(language=="AR") "نقل بضائع" else "Cargo Transport", color = Color.White)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { driverToChooseMode = null }) { Text(if(language=="AR") "إلغاء" else "Cancel") }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color.White
        )
    }

    if (showSettingsDialog) {
        val updateUrlState = remember { mutableStateOf<String?>(null) }
        LaunchedEffect(Unit) {
            try {
                val snapshot = firebaseManager.database.child("app_update").get().await()
                updateUrlState.value = snapshot.child("url").value?.toString()
            } catch (e: Exception) {}
        }

        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            icon = { Icon(Icons.Default.Settings, null, tint = Color(0xFFC5A059), modifier = Modifier.size(36.dp)) },
            title = { Text(if(language=="AR") "الإعدادات" else "Settings", fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color.White else Color.Black, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
            text = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val cardBgColor = if (isDarkTheme) Color(0xFFC5A059) else Color(0xFFFAFAFA)
                    val cardTextColor = Color.Black
                    val cardBorder = if (isDarkTheme) BorderStroke(1.dp, Color(0xFFB8860B)) else BorderStroke(1.dp, Color.LightGray.copy(0.4f))

                    // 1. Profile
                    Card(
                        onClick = {
                            showSettingsDialog = false
                            navController.navigate(Screen.Profile.route)
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBgColor, contentColor = cardTextColor),
                        border = cardBorder,
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountCircle, null, tint = Color(0xFF4CAF50))
                            Spacer(Modifier.width(12.dp))
                            Text(if(language=="AR") "الصفحة الشخصية" else "Profile", fontWeight = FontWeight.Bold, color = cardTextColor)
                        }
                    }

                    // 2. Language
                    Card(
                        onClick = { language = if(language == "AR") "EN" else "AR" },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBgColor, contentColor = cardTextColor),
                        border = cardBorder,
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Language, null, tint = Color(0xFF673AB7))
                            Spacer(Modifier.width(12.dp))
                            Text(if(language=="AR") "اللغة (التغيير إلى EN)" else "Language (Switch to AR)", fontWeight = FontWeight.Bold, color = cardTextColor)
                        }
                    }

                    // 3. Dark / Day Mode
                    Card(
                        onClick = {
                            isDarkTheme = !isDarkTheme
                            sharedPrefs.edit().putBoolean("dark_theme", isDarkTheme).apply()
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBgColor, contentColor = cardTextColor),
                        border = cardBorder,
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if(isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode, null, tint = Color(0xFFFF9800))
                            Spacer(Modifier.width(12.dp))
                            Text(if(isDarkTheme) (if(language=="AR") "الوضع النهاري" else "Day Mode") else (if(language=="AR") "الوضع الليلي" else "Dark Mode"), fontWeight = FontWeight.Bold, color = cardTextColor)
                        }
                    }

                    // 4. Notifications Toggle
                    Card(
                        onClick = { notificationsEnabled = !notificationsEnabled },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBgColor, contentColor = cardTextColor),
                        border = cardBorder,
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if(notificationsEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff, null, tint = if(notificationsEnabled) Color(0xFF4CAF50) else Color.Gray)
                            Spacer(Modifier.width(12.dp))
                            Text(if(language=="AR") (if(notificationsEnabled) "تعطيل الإشعارات" else "تفعيل الإشعارات") else (if(notificationsEnabled) "Disable Alerts" else "Enable Alerts"), fontWeight = FontWeight.Bold, color = cardTextColor)
                        }
                    }



                    // 6. Update App
                    if (updateUrlState.value != null) {
                        Card(
                            onClick = {
                                showSettingsDialog = false
                                try {
                                    val url = updateUrlState.value!!
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url.let { if(!it.startsWith("http")) "https://$it" else it })).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch(e: Exception) {}
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBgColor, contentColor = cardTextColor),
                            border = cardBorder,
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.SystemUpdate, null, tint = Color(0xFF2E7D32))
                                Spacer(Modifier.width(12.dp))
                                Text(if(language=="AR") "تحديث التطبيق" else "Update App", fontWeight = FontWeight.Bold, color = cardTextColor)
                            }
                        }
                    }

                    // 7. About Phone / About App
                    var showAboutPhoneDialog by remember { mutableStateOf(false) }
                    var aboutPhoneTextState by remember { mutableStateOf("") }

                    Card(
                        onClick = {
                            scope.launch {
                                aboutPhoneTextState = firebaseManager.getAboutPhoneText()
                                showAboutPhoneDialog = true
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBgColor, contentColor = cardTextColor),
                        border = cardBorder,
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, null, tint = Color(0xFF2196F3))
                            Spacer(Modifier.width(12.dp))
                            Text(if(language=="AR") "حول الهاتف" else "About App", fontWeight = FontWeight.Bold, color = cardTextColor)
                        }
                    }

                    if (showAboutPhoneDialog) {
                        AlertDialog(
                            onDismissRequest = { showAboutPhoneDialog = false },
                            icon = { Icon(Icons.Default.Info, null, tint = Color(0xFFC5A059), modifier = Modifier.size(36.dp)) },
                            title = { Text(if(language=="AR") "حول الهاتف" else "About App", fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color.White else Color.Black) },
                            text = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 280.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    Text(
                                        text = aboutPhoneTextState.ifBlank { if(language=="AR") "وصلنا - تطبيق التوصيل والتنقل الذكي." else "WASALNA App." },
                                        fontSize = 14.sp,
                                        color = if (isDarkTheme) Color.White else Color.Black,
                                        textAlign = TextAlign.Right,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showAboutPhoneDialog = false }) {
                                    Text(if(language=="AR") "إغلاق" else "Close", color = Color(0xFFC5A059), fontWeight = FontWeight.Bold)
                                }
                            },
                            containerColor = if (isDarkTheme) Color(0xFF202020) else Color.White,
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) { Text(if(language=="AR") "إغلاق" else "Close", color = Color(0xFFC5A059), fontWeight = FontWeight.Bold) }
            },
            containerColor = if (isDarkTheme) Color(0xFF202020) else Color.White,
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
fun DrawerItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, iconColor: Color, trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) { Text(label, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); if (trailingIcon != null) Icon(trailingIcon, null, tint = Color.Gray) } },
        selected = false, onClick = onClick, icon = { Icon(icon, null, tint = iconColor, modifier = Modifier.size(24.dp)) },
        modifier = Modifier.padding(vertical = 2.dp), shape = RoundedCornerShape(12.dp)
    )
}

@Composable
fun ContactUsDialog(onSend: (String) -> Unit, onDismiss: () -> Unit) {
    var msg by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, 
        title = { Text("تواصل معنا", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }, 
        text = { 
            OutlinedTextField(
                value = msg, 
                onValueChange = { msg = it }, 
                modifier = Modifier.fillMaxWidth().height(150.dp),
                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                placeholder = { Text("اكتب رسالتك هنا...", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                shape = RoundedCornerShape(12.dp)
            ) 
        }, 
        confirmButton = { 
            Button(onClick = { if(msg.isNotBlank()) onSend(msg) }) { 
                Text("إرسال") 
            } 
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

@Composable
fun NavBarItem(label: String, icon: ImageVector, isSelected: Boolean, activeColor: Color, inactiveColor: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null, // Remove selection ripple/shadow
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon, 
                contentDescription = label, 
                tint = if (isSelected) activeColor else inactiveColor, 
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label, 
                color = if (isSelected) activeColor else inactiveColor, 
                fontSize = 11.sp, 
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                style = androidx.compose.ui.text.TextStyle(
                    shadow = if(isSelected) androidx.compose.ui.graphics.Shadow(
                        color = Color.Black.copy(alpha = 0.2f),
                        offset = androidx.compose.ui.geometry.Offset(2f, 2f),
                        blurRadius = 2f
                    ) else null
                )
            )
        }
    }
}
