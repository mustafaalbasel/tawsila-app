package com.albasel.tawsilaapp.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.albasel.tawsilaapp.R
import com.albasel.tawsilaapp.data.firebase.FirebaseManager
import com.albasel.tawsilaapp.data.model.LocationMode
import com.albasel.tawsilaapp.util.MapUtils
import com.google.android.gms.location.*
import kotlinx.coroutines.*

class LocationForegroundService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var firebaseManager: FirebaseManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var locationThread: android.os.HandlerThread? = null

    private var currentUserId: String? = null
    private var currentMode = LocationMode.NORMAL
    private var currentOrderId: String? = null
    private var userProvince: String = ""
    private var vehicleType: String = ""
    private var userName: String = ""
    private var isApproved: Boolean = true
    private var canDeliver: Boolean = true
    private var canCargo: Boolean = false

    private var lastFirebaseLat = 0.0
    private var lastFirebaseLng = 0.0
    private var lastFirebaseTime = 0L

    companion object {
        const val CHANNEL_ID = "tawsila_location_channel"
        const val NOTIFICATION_ID = 991
        const val ACTION_STOP_SERVICE = "com.albasel.tawsilaapp.action.STOP_SERVICE"
        @Volatile
        var isRunning = false
        
        fun startService(context: Context, userId: String, mode: LocationMode, orderId: String? = null, province: String = "", vType: String = "", name: String = "", isAppr: Boolean = true, canDel: Boolean = true, canCar: Boolean = false) {
            try {
                val intent = Intent(context, LocationForegroundService::class.java).apply {
                    putExtra("userId", userId)
                    putExtra("mode", mode.name)
                    putExtra("orderId", orderId)
                    putExtra("province", province)
                    putExtra("vehicleType", vType)
                    putExtra("userName", name)
                    putExtra("isApproved", isAppr)
                    putExtra("canDeliver", canDel)
                    putExtra("canCargo", canCar)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e("LocationService", "Service start error: ${e.message}")
            }
        }

        fun stopService(context: Context) {
            try {
                context.stopService(Intent(context, LocationForegroundService::class.java))
            } catch (e: Exception) {
                Log.e("LocationService", "Service stop error: ${e.message}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        val thread = android.os.HandlerThread("LocationGpsThread")
        thread.start()
        locationThread = thread

        firebaseManager = FirebaseManager(applicationContext)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        updateNotification(LocationMode.NORMAL)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            // Pause notification temporarily for current session - KEEP DRIVER ONLINE!
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val userId = intent?.getStringExtra("userId")
        val modeStr = intent?.getStringExtra("mode")
        val orderId = intent?.getStringExtra("orderId")
        val province = intent?.getStringExtra("province") ?: ""
        val vType = intent?.getStringExtra("vehicleType") ?: ""
        val name = intent?.getStringExtra("userName") ?: ""
        val isAppr = intent?.getBooleanExtra("isApproved", true) ?: true
        val canDel = intent?.getBooleanExtra("canDeliver", true) ?: true
        val canCar = intent?.getBooleanExtra("canCargo", false) ?: false

        if (userId != null && modeStr != null) {
            val mode = LocationMode.valueOf(modeStr)
            if (mode == LocationMode.OFFLINE) {
                stopSelf()
                return START_NOT_STICKY
            }
            
            updateNotification(mode)
            
            if (currentUserId != userId || currentMode != mode || currentOrderId != orderId) {
                currentUserId = userId
                currentMode = mode
                currentOrderId = orderId
                userProvince = province
                vehicleType = vType
                userName = name
                isApproved = isAppr
                canDeliver = canDel
                canCargo = canCar
                startLocationUpdates()
            }
        } else {
            stopSelf()
        }

        return START_STICKY
    }

    private fun startLocationUpdates() {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }

        // High frequency GPS to detect 200m movement accurately
        val gpsInterval = if (currentMode == LocationMode.ACTIVE_TRIP) 5000L else 30000L

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, gpsInterval)
            .setMinUpdateIntervalMillis(gpsInterval / 2)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    handleNewLocation(loc.latitude, loc.longitude)
                }
            }
        }

        try {
            val looper = locationThread?.looper ?: android.os.Looper.getMainLooper()
            fusedLocationClient.requestLocationUpdates(request, locationCallback!!, looper)
        } catch (e: SecurityException) {
            Log.e("LocationService", "Permission error: ${e.message}")
        }
    }

    private fun handleNewLocation(lat: Double, lng: Double) {
        val uid = currentUserId ?: return
        val now = System.currentTimeMillis()
        
        val dist = MapUtils.calculateDistance(lastFirebaseLat, lastFirebaseLng, lat, lng)
        val timeDiff = now - lastFirebaseTime

        // Speed & Visibility Fix: Update every 3 minutes REGARDLESS of movement
        // to keep the driver visible on the map (lastSeen within threshold).
        // The 200m rule still applies for high-frequency updates in other modes.
        val isTimeForHeartbeat = timeDiff >= 180000L && lastFirebaseTime != 0L
        
        if (dist < 0.200 && !isTimeForHeartbeat && lastFirebaseTime != 0L) return

        val shouldUpdate = when (currentMode) {
            LocationMode.NORMAL -> timeDiff >= 180000L // 3 mins
            LocationMode.ORDER_COLLECTION -> timeDiff >= 15000L // 15s
            LocationMode.ACTIVE_TRIP -> timeDiff >= 10000L // 10s
            else -> false
        }

        if (shouldUpdate || lastFirebaseTime == 0L) {
            lastFirebaseLat = lat
            lastFirebaseLng = lng
            lastFirebaseTime = now
            
            serviceScope.launch {
                // Update Lightweight Node (for nearby map)
                firebaseManager.updateLocationLightweight(uid, lat, lng, userProvince, vehicleType, true, userName, isApproved, canDeliver, canCargo)
                
                // Update Trip Node if active (for order tracking)
                currentOrderId?.let { oid ->
                    val isDriver = vehicleType.isNotEmpty()
                    val role = if (isDriver) "driver" else "customer"
                    firebaseManager.updateTripLocation(oid, role, lat, lng)
                }
                
                // General status update in main users node
                firebaseManager.updateUserLocationWithFilters(uid, lat, lng, userProvince, currentMode != LocationMode.NORMAL)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Location Service", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(mode: LocationMode) {
        val title = "WASALNA"
        val body = when (mode) {
            LocationMode.NORMAL -> "نظام الموقع يعمل في الخلفية"
            LocationMode.ORDER_COLLECTION -> "جاري تجميع الأوردرات وتحديث الموقع"
            LocationMode.ACTIVE_TRIP -> "جاري تتبع الرحلة النشطة"
            else -> "جاري تحديث الموقع"
        }

        val stopIntent = Intent(this, LocationForegroundService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIcon = try {
            android.graphics.BitmapFactory.decodeResource(resources, R.drawable.ic_notification)
                ?: android.graphics.BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
        } catch (_: Exception) { null }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(largeIcon)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "إيقاف الخدمة", stopPendingIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        locationThread?.quitSafely()
        serviceJob.cancel()
        super.onDestroy()
    }
}
