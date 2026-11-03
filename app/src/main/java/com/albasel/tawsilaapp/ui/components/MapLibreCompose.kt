package com.albasel.tawsilaapp.ui.components

import android.os.Bundle
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.PropertyFactory

@Composable
fun MapLibreCompose(
    modifier: Modifier = Modifier,
    styleUrl: String,
    initialCameraPosition: CameraPosition = CameraPosition.Builder().target(LatLng(30.0444, 31.2357)).zoom(12.0).build(),
    onMapReady: (MapLibreMap) -> Unit = {},
    onStyleLoaded: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Initialize MapLibre
    val mapLibre = remember { 
        try {
            MapLibre.getInstance(context)
        } catch (e: Exception) {
            Log.e("MapLibre", "Failed to get MapLibre instance", e)
        }
        true
    }

    val mapView = remember { MapView(context) }
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }

    fun enable3DBuildings(style: Style) {
        try {
            if (style.getLayer("3d-buildings") == null) {
                val extrusionLayer = FillExtrusionLayer("3d-buildings", "openfreemap")
                extrusionLayer.setSourceLayer("building")
                extrusionLayer.setFilter(Expression.eq(Expression.get("render_3d"), "true"))
                extrusionLayer.setProperties(
                    PropertyFactory.fillExtrusionColor(android.graphics.Color.LTGRAY),
                    PropertyFactory.fillExtrusionHeight(Expression.get("height")),
                    PropertyFactory.fillExtrusionBase(Expression.get("min_height")),
                    PropertyFactory.fillExtrusionOpacity(0.85f)
                )
                style.addLayer(extrusionLayer)
                Log.d("MapLibre", "3D Buildings layer added successfully")
            }
        } catch (e: Exception) {
            Log.e("MapLibre", "Non-critical: Could not add 3D buildings layer", e)
        }
    }

    fun applyStyleToMap(map: MapLibreMap, targetUrl: String, onLoaded: () -> Unit) {
        val styleBuilder = if (targetUrl.contains("google.com") || targetUrl.contains("ArcGIS") || targetUrl.contains("tile/{z}/{y}/{x}")) {
            val tilesArrayJson = if (targetUrl.contains("google.com")) {
                val lyrsParam = if (targetUrl.contains("lyrs=s") || targetUrl.contains("lyrs=y")) "lyrs=s,h" else "lyrs=m"
                """
                [
                  "https://mt0.google.com/vt/$lyrsParam&x={x}&y={y}&z={z}",
                  "https://mt1.google.com/vt/$lyrsParam&x={x}&y={y}&z={z}",
                  "https://mt2.google.com/vt/$lyrsParam&x={x}&y={y}&z={z}",
                  "https://mt3.google.com/vt/$lyrsParam&x={x}&y={y}&z={z}"
                ]
                """.trimIndent()
            } else {
                """
                [
                  "$targetUrl",
                  "${targetUrl.replace("services.", "server.")}"
                ]
                """.trimIndent()
            }

            val jsonString = """
            {
              "version": 8,
              "sources": {
                "fast-satellite-source": {
                  "type": "raster",
                  "tiles": $tilesArrayJson,
                  "tileSize": 256
                }
              },
              "layers": [
                {
                  "id": "fast-satellite-layer",
                  "type": "raster",
                  "source": "fast-satellite-source",
                  "minzoom": 0,
                  "maxzoom": 20
                }
              ]
            }
            """.trimIndent()
            Style.Builder().fromJson(jsonString)
        } else {
            val finalUrl = if (targetUrl.startsWith("http") || targetUrl.startsWith("asset")) targetUrl 
                            else "https://tiles.openfreemap.org/styles/bright"
            Style.Builder().fromUri(finalUrl)
        }

        map.setStyle(styleBuilder) { style ->
            Log.d("MapLibre", "Style loaded: $targetUrl")
            
            // Google Maps Light Style & Arabic Street Labels Priority
            if (!targetUrl.contains("dark") && !targetUrl.contains("ArcGIS") && !targetUrl.contains("google.com")) {
                try {
                    // Background & Land
                    style.getLayer("background")?.setProperties(PropertyFactory.backgroundColor("#f1f3f4"))
                    style.getLayer("landuse_residential")?.setProperties(PropertyFactory.fillColor("#e8eaed"))
                    
                    // Water - Clear Light Blue
                    style.getLayer("water")?.setProperties(PropertyFactory.fillColor("#aadaff"))
                    style.getLayer("waterway")?.setProperties(PropertyFactory.lineColor("#aadaff"))
                    
                    // Parks & Green Areas - Natural Light Green
                    style.getLayer("landuse_park")?.setProperties(PropertyFactory.fillColor("#ceead6"))
                    style.getLayer("landuse_forest")?.setProperties(PropertyFactory.fillColor("#ceead6"))
                    style.getLayer("leisure_park")?.setProperties(PropertyFactory.fillColor("#ceead6"))
                    
                    // Buildings - Very Light Gray
                    style.getLayer("building")?.setProperties(PropertyFactory.fillColor("#e8eaed"), PropertyFactory.fillOutlineColor("#dadce0"))
                    
                    // Roads - Standard Google Colors
                    style.getLayer("road_major_motorway")?.setProperties(PropertyFactory.lineColor("#ffffff"), PropertyFactory.lineGapWidth(2f))
                    style.getLayer("road_major_primary")?.setProperties(PropertyFactory.lineColor("#ffffff"))
                    style.getLayer("road_minor")?.setProperties(PropertyFactory.lineColor("#ffffff"))
                    
                    // Casings
                    style.getLayers().forEach { layer ->
                        if (layer.id.contains("casing")) {
                            layer.setProperties(PropertyFactory.lineColor("#dadce0"))
                        }
                    }

                    // Labels & Places Priority (Arabic name:ar first)
                    style.getLayer("place_city")?.let { layer ->
                        layer.setProperties(
                            PropertyFactory.textSize(16f),
                            PropertyFactory.textColor("#202124"),
                            PropertyFactory.textHaloColor("#ffffff"),
                            PropertyFactory.textHaloWidth(1.5f)
                        )
                    }
                    
                    style.getLayers().forEach { layer ->
                        if (layer.id.contains("label") || layer.id.contains("place") || layer.id.contains("road")) {
                            layer.setProperties(
                                PropertyFactory.textField(
                                    Expression.coalesce(
                                        Expression.get("name:ar"),
                                        Expression.get("name:en"),
                                        Expression.get("name")
                                    )
                                ),
                                PropertyFactory.textColor("#202124"),
                                PropertyFactory.textHaloColor("#ffffff"),
                                PropertyFactory.textHaloWidth(1.2f)
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MapLibre", "Styling error: ${e.message}")
                }
            }
            
            if (!targetUrl.contains("ArcGIS")) {
                enable3DBuildings(style)
            }
            onLoaded()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> { /* already handled below */ }
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        
        mapView.onCreate(null)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    AndroidView(
        factory = { 
            mapView.apply {
                getMapAsync { map ->
                    mapInstance = map
                    applyStyleToMap(map, styleUrl, onStyleLoaded)
                    map.cameraPosition = initialCameraPosition
                    
                    map.uiSettings.apply {
                        isRotateGesturesEnabled = true
                        isTiltGesturesEnabled = true
                        isCompassEnabled = true
                        isLogoEnabled = false
                        isAttributionEnabled = false
                    }
                    
                    onMapReady(map)
                }
            }
        }, 
        modifier = modifier,
        update = { view ->
            mapInstance?.let { map ->
                val currentStyleUrl = map.style?.url
                if (currentStyleUrl != styleUrl) {
                    Log.d("MapLibre", "Updating style to $styleUrl")
                    applyStyleToMap(map, styleUrl, onStyleLoaded)
                }
            }
        }
    )
}
