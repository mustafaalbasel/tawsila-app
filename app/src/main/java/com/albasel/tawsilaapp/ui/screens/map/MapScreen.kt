package com.albasel.tawsilaapp.ui.screens.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.albasel.tawsilaapp.data.firebase.FirebaseManager
import com.albasel.tawsilaapp.data.model.User
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

@Composable
fun MapScreen(firebaseManager: FirebaseManager) {
    val initialPos = LatLng(30.0444, 31.2357) // Cairo
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialPos, 12f)
    }
    
    var drivers by remember { mutableStateOf(emptyList<User>()) }

    DisposableEffect(Unit) {
        val listener = firebaseManager.getDriverLocations { drivers = it }
        onDispose {
            firebaseManager.database.child("users").removeEventListener(listener)
        }
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState
    ) {
        drivers.forEach { driver ->
            Marker(
                state = MarkerState(position = LatLng(driver.latitude, driver.longitude)),
                title = driver.name,
                snippet = driver.vehicleType
            )
        }
    }
}
