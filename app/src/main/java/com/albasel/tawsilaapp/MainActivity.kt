package com.albasel.tawsilaapp

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import com.albasel.tawsilaapp.data.firebase.FirebaseManager
import com.albasel.tawsilaapp.ui.WasalnaApp
import com.albasel.tawsilaapp.ui.theme.WasalnaAppTheme
import com.albasel.tawsilaapp.util.NotificationHelper
import android.content.Intent

class MainActivity : ComponentActivity() {
    private lateinit var firebaseManager: FirebaseManager
    
    private var initialRouteState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize notification channel and FCM context at startup
        NotificationHelper.createNotificationChannel(this)
        com.albasel.tawsilaapp.util.FCMV1Helper.initContext(this)

        firebaseManager = FirebaseManager(this)
        
        initialRouteState.value = extractRoute(intent)

        // Force solid black status and navigation bars and disable transparency
        // Using -16777216 which is the integer value for Black (0xFF000000)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(-16777216),
            navigationBarStyle = SystemBarStyle.dark(-16777216)
        )

        setContent {
            val systemDark = isSystemInDarkTheme()
            val isDarkTheme by remember { mutableStateOf(systemDark) }

            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { }

            LaunchedEffect(Unit) {
                val permissions = arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                
                val allPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissions + Manifest.permission.POST_NOTIFICATIONS
                } else {
                    permissions
                }
                
                launcher.launch(allPermissions)
            }

            WasalnaAppTheme(darkTheme = isDarkTheme) {
                WasalnaApp(firebaseManager, initialRouteState.value, onRouteConsumed = {
                    initialRouteState.value = null
                })
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Non-blocking asynchronous background execution so main UI thread opens INSTANTLY in 0ms
        Thread {
            try {
                com.albasel.tawsilaapp.service.LocationForegroundService.stopService(applicationContext)
            } catch (e: Exception) {}
        }.start()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        initialRouteState.value = extractRoute(intent)
    }

    private fun extractRoute(intent: Intent?): String? {
        if (intent == null) return null
        return intent.getStringExtra("destination_route")
            ?: intent.getStringExtra("route")
            ?: intent.extras?.getString("destination_route")
            ?: intent.extras?.getString("route")
    }
}
