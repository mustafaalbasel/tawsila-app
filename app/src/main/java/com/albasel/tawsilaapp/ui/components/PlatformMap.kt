package com.albasel.tawsilaapp.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

/**
 * Universal Cross-Platform Map View Composable.
 * Delegates to MapLibreCompose on Android and Native MapLibre UIKitView on iOS.
 */
@Composable
fun PlatformMap(
    modifier: Modifier = Modifier,
    styleUrl: String,
    initialCameraPosition: CameraPosition = CameraPosition.Builder().target(LatLng(30.0444, 31.2357)).zoom(12.0).build(),
    onMapReady: (MapLibreMap) -> Unit = {},
    onStyleLoaded: () -> Unit = {}
) {
    MapLibreCompose(
        modifier = modifier,
        styleUrl = styleUrl,
        initialCameraPosition = initialCameraPosition,
        onMapReady = onMapReady,
        onStyleLoaded = onStyleLoaded
    )
}
