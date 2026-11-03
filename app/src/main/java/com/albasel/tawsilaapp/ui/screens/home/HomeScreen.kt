package com.albasel.tawsilaapp.ui.screens.home

import android.Manifest
import android.content.Context
import androidx.compose.foundation.BorderStroke
import android.content.pm.PackageManager
import android.graphics.*
import android.location.Address
import android.location.Geocoder
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.albasel.tawsilaapp.R
import com.albasel.tawsilaapp.data.local.LocalSearchDatabase
import com.albasel.tawsilaapp.util.DamiettaPlaces
import com.albasel.tawsilaapp.util.DamiettaPOI
import kotlinx.coroutines.CoroutineExceptionHandler
import com.albasel.tawsilaapp.util.VehicleUtils.isCargoOnlyVehicle
import com.albasel.tawsilaapp.util.VehicleUtils.cleanVehicleName
import com.albasel.tawsilaapp.util.VehicleUtils.getBaseIconName
import com.albasel.tawsilaapp.ui.theme.*
import com.albasel.tawsilaapp.data.firebase.FirebaseManager
import com.albasel.tawsilaapp.data.model.*
import com.albasel.tawsilaapp.data.repository.impl.SearchRepositoryImpl
import com.albasel.tawsilaapp.data.repository.impl.RoutingRepositoryImpl
import com.albasel.tawsilaapp.ui.components.MapLibreCompose
import com.google.android.gms.location.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.detectTransformGestures
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import com.albasel.tawsilaapp.utils.MapMarkerUtils
import com.albasel.tawsilaapp.util.MapUtils
import com.albasel.tawsilaapp.ui.components.WheelPicker
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.text.SimpleDateFormat
import kotlinx.coroutines.tasks.await
import java.util.*
import kotlin.math.*

@Immutable
data class VehicleInfoData(
    val name: String, 
    val iconRes: Int,
    val capacity: Int, 
    val maxCargoWeightKg: Double,
    val iconColor: Color,
    val ratePerKm: Double
)

@Immutable
data class SearchSuggestion(
    val name: String,
    val address: String,
    val distance: Double,
    val latLng: LatLng,
    val category: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    firebaseManager: FirebaseManager, 
    language: String, 
    user: User?, 
    isDarkMode: Boolean, 
    selectedDriver: User? = null,
    drawerGesturesEnabled: Boolean = true,
    onToggleDrawerGestures: (Boolean) -> Unit = {},
    onMenuClick: () -> Unit, 
    onUpdateUser: (User) -> Unit,
    onToggleTheme: () -> Unit, 
    hasBlockingOrder: Boolean = false,
    onClearSelectedDriver: () -> Unit = {},
    onNotificationsClick: (String, String?) -> Unit = { _, _ -> },
    onCityClick: () -> Unit = {},
    onNearbyClick: () -> Unit = {},
    initialRoute: String? = null,
    initialDestinationName: String? = null,
    initialDestinationLat: Double = 0.0,
    initialDestinationLng: Double = 0.0
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDateTimePickerDialog by remember { mutableStateOf(false) }
    
    val destFocusRequester = remember { FocusRequester() }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    LaunchedEffect(initialRoute) {
        if (initialRoute == "DEST" || initialRoute?.contains("focus=DEST") == true) {
            delay(300)
            try {
                destFocusRequester.requestFocus()
                keyboardController?.show()
            } catch (e: Exception) {}
        }
    }
    
    val isAr = language == "AR"
    val sharedPrefs = remember { context.getSharedPreferences("tawsila_prefs", Context.MODE_PRIVATE) }
    
    // Cargo Mode Logic
    var isCargoMode by remember { mutableStateOf(sharedPrefs.getBoolean("cargo_mode", false)) }
    
    // Sync cargo mode from preferences periodically or on entry
    DisposableEffect(Unit) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == "cargo_mode") {
                isCargoMode = prefs.getBoolean("cargo_mode", false)
            }
        }
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    
    val searchRepo = remember { SearchRepositoryImpl(context) }
    val routingRepo = remember { RoutingRepositoryImpl() }

    val egyptCities = remember {
        listOf(
            "القاهرة", "Cairo", "الإسكندرية", "Alexandria", "الجيزة", "Giza", "دمياط", "Damietta",
            "المنصورة", "Mansoura", "طنطا", "Tanta", "الزقازيق", "Zagazig", "الفيوم", "Fayoum",
            "أسيوط", "Assiut", "سوهاج", "Sohag", "الأقصر", "Luxor", "أسوان", "Aswan",
            "شرم الشيخ", "Sharm El-Sheikh", "الغردقة", "Hurghada", "بور سعيد", "Port Said",
            "السويس", "Suez", "المحلة", "Mahalla", "المنيا", "Minya", "قنا", "Qena",
            "بني سويف", "Beni Suef", "شبين الكوم", "Shibin El Kom", "بنها", "Banha",
            "كفر الشيخ", "Kafr El Sheikh", "دمنهور", "Damanhur", "مرسى مطروح", "Marsa Matruh",
            "الإسماعيلية", "Ismailia", "رأس البر", "Ras El Bar", "بلبيس", "Belbeis",
            "العاشر من رمضان", "10th of Ramadan", "مدينة نصر", "Nasr City", "التجمع", "Tagamoa",
            "دمياط الجديدة", "Damietta New City"
        )
    }

    var activeRouteData by remember { mutableStateOf<RouteOption?>(null) }
    var lastRoutingParams by remember { mutableStateOf<String?>(null) }
    var lastAnimatedRouteParams by remember { mutableStateOf<String?>(null) }
    var routingRequestId by remember { mutableLongStateOf(0L) }
    var isRouteLoading by remember { mutableStateOf(false) }
    var lastMyLocationClickTime by remember { mutableLongStateOf(0L) }

    var animatedCarBearing by remember { mutableFloatStateOf(0f) }
    var pointsDiscount by remember { mutableDoubleStateOf(0.0) }
    var pointValueUser by remember { mutableDoubleStateOf(1.0) }
    LaunchedEffect(Unit) { pointValueUser = firebaseManager.getPointValue("USER") }

    var pickupName by rememberSaveable { mutableStateOf("") }
    var pickupCity by rememberSaveable { mutableStateOf("") }
    var pickupProvinceState by rememberSaveable { mutableStateOf("") }
    var destinationName by rememberSaveable { mutableStateOf("") }
    var destinationCity by rememberSaveable { mutableStateOf("") }
    var pickupLatLng by remember { mutableStateOf<LatLng?>(null) }
    var destinationLatLng by remember { mutableStateOf<LatLng?>(null) }
    var currentLocation by remember { mutableStateOf<LatLng?>(null) }
    var calculatedDistance by remember { mutableDoubleStateOf(0.0) }
    
    var recentSearchesList by remember { mutableStateOf<List<RecentSearchItem>>(emptyList()) }

    LaunchedEffect(Unit) {
        val jsonStr = sharedPrefs.getString("recent_searches_json", null)
        if (!jsonStr.isNullOrBlank()) {
            try {
                val type = object : com.google.gson.reflect.TypeToken<List<RecentSearchItem>>() {}.type
                recentSearchesList = com.google.gson.Gson().fromJson(jsonStr, type) ?: emptyList()
            } catch (e: Exception) {}
        } else {
            recentSearchesList = listOf(
                RecentSearchItem("الموقف الغربي الجديد بدمياط الجديدة", "مدينة دمياط الجديدة، محافظة دمياط", 31.4280, 31.6500),
                RecentSearchItem("الموقف الشرقي (داخلي دمياط)", "دمياط الجديدة، محافظة دمياط", 31.4510, 31.6770)
            )
        }
    }

    fun saveRecentSearch(title: String, subtitle: String, lat: Double, lng: Double) {
        if (title.isBlank()) return
        val newItem = RecentSearchItem(title, subtitle, lat, lng)
        val updated = (listOf(newItem) + recentSearchesList).distinctBy { it.title.trim() }.take(5)
        recentSearchesList = updated
        val jsonStr = com.google.gson.Gson().toJson(updated)
        sharedPrefs.edit().putString("recent_searches_json", jsonStr).apply()
    }

    fun deleteRecentSearch(item: RecentSearchItem) {
        val updated = recentSearchesList.filter { it.title != item.title }
        recentSearchesList = updated
        val jsonStr = com.google.gson.Gson().toJson(updated)
        sharedPrefs.edit().putString("recent_searches_json", jsonStr).apply()
    }
    

    var lastAutoZoomedLatLng by remember { mutableStateOf<LatLng?>(null) }
    var animatedCarMarker by remember { mutableStateOf<org.maplibre.android.annotations.Marker?>(null) }
    var destinationMarker by remember { mutableStateOf<org.maplibre.android.annotations.Marker?>(null) }
    var pickupMarker by remember { mutableStateOf<org.maplibre.android.annotations.Marker?>(null) }
    var routePolylines by remember { mutableStateOf<List<org.maplibre.android.annotations.Polyline>>(emptyList()) }
    var searchResultMarker by remember { mutableStateOf<org.maplibre.android.annotations.Marker?>(null) }
    var lastPolylineHash by remember { mutableIntStateOf(0) }
    var mapStyleLoaded by remember { mutableStateOf(false) }
    
    var vehicleType by remember { mutableStateOf("") } // Initially empty, will be auto-selected below
    var requestedGender by remember { mutableStateOf("MALE") }
    var rideType by remember { mutableStateOf("PRIVATE") } 
    var passengers by remember { mutableStateOf("1") }
    
    // Cargo Mode States
    var cargoDetails by remember { mutableStateOf("") }
    var includesLoading by remember { mutableStateOf(false) }
    var includesUnloading by remember { mutableStateOf(false) }
    var cargoWaitMins by remember { mutableStateOf("0") }
    var cargoItemCount by remember { mutableStateOf("1") }
    
    var lastResolvedPickupName by remember { mutableStateOf("") }
    var lastResolvedDestinationName by remember { mutableStateOf("") }

    LaunchedEffect(initialDestinationName, initialDestinationLat, initialDestinationLng) {
        if (!initialDestinationName.isNullOrBlank()) {
            destinationName = initialDestinationName
            lastResolvedDestinationName = initialDestinationName
            if (initialDestinationLat != 0.0 && initialDestinationLng != 0.0) {
                destinationLatLng = LatLng(initialDestinationLat, initialDestinationLng)
            }
        }
    }
    
    var simulatedBearing by remember { mutableStateOf(0f) }
    LaunchedEffect(vehicleType) {
        simulatedBearing = 0f
        val steps = 8
        for (i in 1..steps) {
            delay(60)
            simulatedBearing = (i * (360f / steps)) % 360f
        }
        simulatedBearing = 0f // Final aesthetic angle
    }

    var selectedCargoStandard by remember { mutableStateOf<CargoStandard?>(null) }
    var cargoStandards by remember { mutableStateOf<List<CargoStandard>>(emptyList()) }
    var pricingFormulas by remember { mutableStateOf<List<PricingFormula>>(emptyList()) }
    var vehiclePrices by remember { mutableStateOf<Map<String, VehiclePrice>>(emptyMap()) }
    var systemLogic by remember { mutableStateOf(com.albasel.tawsilaapp.data.model.SystemLogicSettings()) }
    var sharedButtonThreshold by remember { mutableDoubleStateOf(0.0) }

    var activePromoCodes by remember { mutableStateOf<List<com.albasel.tawsilaapp.data.model.PromoCode>>(emptyList()) }
    DisposableEffect(Unit) {
        val listener = firebaseManager.listenToPromoCodes { activePromoCodes = it }
        onDispose { firebaseManager.database.child("settings").child("promo_codes").removeEventListener(listener) }
    }

    var showPromoOfferDialog by remember { mutableStateOf(false) }
    var currentPromoDialogData by remember { mutableStateOf<com.albasel.tawsilaapp.data.model.PromoCode?>(null) }

    LaunchedEffect(initialRoute, activePromoCodes) {
        if (initialRoute?.contains("promo") == true || initialRoute?.contains("offer") == true) {
            val matched = activePromoCodes.firstOrNull()
            currentPromoDialogData = matched ?: com.albasel.tawsilaapp.data.model.PromoCode(
                code = matched?.code ?: "",
                percentage = matched?.percentage ?: 0.0,
                offerText = "عرض ترويجي جديد 🎁 (أدخل كود الخصم في تأكيد الرحلة)",
                validityDays = matched?.validityDays ?: 1
            )
            showPromoOfferDialog = true
        }
    }

    var isResolvingMyLocation by remember { mutableStateOf(false) }

    // Admin POI Edit & Create States
    var showAdminPoiEditDialog by remember { mutableStateOf(false) }
    var adminEditOriginalName by remember { mutableStateOf("") }
    var adminEditNameInput by remember { mutableStateOf("") }
    var adminEditLatInput by remember { mutableStateOf("") }
    var adminEditLngInput by remember { mutableStateOf("") }
    var adminEditDistrictInput by remember { mutableStateOf("") }
    var adminEditIsPickupTarget by remember { mutableStateOf<Boolean?>(null) }

    var showAdminCreatePoiDialog by remember { mutableStateOf(false) }
    var adminNewPoiNameInput by remember { mutableStateOf("") }
    var adminNewPoiLatInput by remember { mutableStateOf("") }
    var adminNewPoiLngInput by remember { mutableStateOf("") }
    var adminNewPoiDistrictInput by remember { mutableStateOf("") }
    var adminSelectedCategory by remember { mutableStateOf("residential") }
    var adminIsExistingPoi by remember { mutableStateOf(false) }

    var customPoisVersion by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        DamiettaPlaces.onPoisUpdated.collect { version ->
            if (version > 0) {
                customPoisVersion++
                Log.d("HomeScreen", "DamiettaPlaces onPoisUpdated triggered: customPoisVersion=$customPoisVersion")
            }
        }
    }

    // Master Single-Source Places Initialization & Background Drive Sync
    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            val localDb = LocalSearchDatabase.getInstance(context)
            if (!localDb.isDatabaseInitialized() || localDb.getRecordCount() < 17000) {
                val t0 = android.os.SystemClock.elapsedRealtime()
                DamiettaPlaces.loadFromCsvAssetsToDatabase(context)
                Log.d("WASALNA_SEARCH_PERF", "LOCAL_DB_ONE_TIME_INITIALIZATION_COMPLETE: latency=${android.os.SystemClock.elapsedRealtime() - t0}ms")
            }
            com.albasel.tawsilaapp.util.GoogleDriveHelper.checkAndUpdateLocalCsvFromDrive(context)
            firebaseManager.cleanOrphanedTripLocations()
        }
        firebaseManager.listenToCustomPOIs { syncedPois ->
            scope.launch(Dispatchers.IO) {
                val localDb = LocalSearchDatabase.getInstance(context)
                if (syncedPois.isNotEmpty()) {
                    val entries = syncedPois.map { syncedPoi ->
                        val codeId = syncedPoi.placeId.ifBlank { localDb.getCodeIdForPlace(syncedPoi.nameAr) ?: (localDb.getMaxCodeId() + 1).toString() }
                        com.albasel.tawsilaapp.data.local.PlaceEntry(
                            id = codeId,
                            nameAr = syncedPoi.nameAr,
                            nameEn = syncedPoi.nameEn,
                            category = syncedPoi.category,
                            lat = syncedPoi.lat,
                            lng = syncedPoi.lng,
                            entranceLat = syncedPoi.entranceLat,
                            entranceLng = syncedPoi.entranceLng,
                            district = syncedPoi.district,
                            city = syncedPoi.city,
                            governorate = syncedPoi.governorate,
                            source = "Realtime_Firebase",
                            verified = true
                        )
                    }
                    localDb.upsertPlacesBatch(entries)
                    DamiettaPlaces.updateDrivePois(syncedPois)
                }
            }
        }
    }

    // Optimize initial load: defer non-critical data
    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val vPrices = firebaseManager.getVehiclePricesDetail()
                val pFormulas = firebaseManager.getPricingFormulas()
                val logic = firebaseManager.getSystemLogicSettings()
                val threshold = firebaseManager.getSharedButtonThreshold()
                withContext(Dispatchers.Main) {
                    vehiclePrices = vPrices
                    pricingFormulas = pFormulas
                    systemLogic = logic
                    sharedButtonThreshold = threshold
                }
            } catch (e: Exception) {
                Log.e("HomeScreen", "Background init error", e)
            }
        }
    }

    val vehicleList = remember(language, vehiclePrices, isCargoMode, selectedDriver, selectedCargoStandard, isDarkMode) {
        if (vehiclePrices.isEmpty()) return@remember emptyList<VehicleInfoData>()
        
        val suffix = if (isDarkMode) "2" else "1"
        fun getRes(name: String): Int {
            val base = getBaseIconName(name)
            return context.resources.getIdentifier("ic_$base$suffix", "drawable", context.packageName).let {
                if (it != 0) it else R.drawable.ic_taxi1
            }
        }

        fun getArabicVehicleKey(name: String): String {
            return when (name.lowercase()) {
                "private", "ملاكي" -> "ملاكي"
                "taxi", "تاكسي" -> "تاكسي"
                "van", "فان" -> "فان"
                "bus", "باص" -> "باص"
                "microbus", "ميكروباص" -> "ميكروباص"
                "motorcycle", "موتوسيكل" -> "موتوسيكل"
                "bike", "دراجة" -> "دراجة"
                "tuk-tuk", "tuk tuk", "تكتك", "توكتوك" -> "توكتوك"
                "tricycle", "ترسكل", "تريسكل" -> "تريسكل"
                "quarter truck", "ربع نقل" -> "ربع نقل"
                "half truck", "نصف نقل" -> "نصف نقل"
                "truck", "نقل" -> "نقل"
                else -> name
            }
        }

        val staticList = listOf(
            VehicleInfoData(if(isAr) "ملاكي" else "Private", getRes("privatecar"), 4, 400.0, PremiumGold, 10.0),
            VehicleInfoData(if(isAr) "تاكسي" else "Taxi", getRes("taxi"), 4, 350.0, PremiumGold, 8.0),
            VehicleInfoData(if(isAr) "فان" else "Van", getRes("van"), 7, 700.0, PremiumGold, 12.0),
            VehicleInfoData(if(isAr) "توكتوك" else "Tuk-tuk", getRes("tuk_tuk"), 3, 150.0, PremiumGold, 5.0),
            VehicleInfoData(if(isAr) "تريسكل" else "Tricycle", getRes("tricycle"), 2, 300.0, PremiumGold, 8.0),
            VehicleInfoData(if(isAr) "باص" else "Bus", getRes("bus"), 50, 1500.0, PremiumGold, 15.0),
            VehicleInfoData(if(isAr) "ميكروباص" else "Microbus", getRes("microbus"), 14, 800.0, PremiumGold, 15.0),
            VehicleInfoData(if(isAr) "موتوسيكل" else "Motorcycle", getRes("moto"), 1, 100.0, PremiumGold, 5.0),
            VehicleInfoData(if(isAr) "دراجة" else "Bike", getRes("bike"), 1, 25.0, PremiumGold, 3.0),
            VehicleInfoData(if(isAr) "ربع نقل" else "Quarter Truck", getRes("quartertruck"), 2, 1000.0, PremiumGold, 15.0),
            VehicleInfoData(if(isAr) "نصف نقل" else "Half Truck", getRes("halftruck"), 3, 2000.0, PremiumGold, 20.0),
            VehicleInfoData(if(isAr) "نقل" else "Truck", getRes("truck"), 3, 5000.0, PremiumGold, 30.0)
        )
        
        val filtered = staticList.filter { v -> 
            val arKey = getArabicVehicleKey(v.name)
            val vp = vehiclePrices[arKey] ?: vehiclePrices[v.name]
            val isVisible = vp?.isVisible ?: true
            
            if (isCargoMode) {
                isVisible && (vp?.showInCargo ?: true)
            } else {
                isVisible && (vp?.showInHome ?: true)
            }
        }.map { v -> 
            val arKey = getArabicVehicleKey(v.name)
            val vp = vehiclePrices[arKey] ?: vehiclePrices[v.name]
            v.copy(ratePerKm = vp?.pricePerKm ?: v.ratePerKm)
        }.sortedBy { 
            val arKey = getArabicVehicleKey(it.name)
            (vehiclePrices[arKey] ?: vehiclePrices[it.name])?.priority ?: 999 
        }

        if (selectedDriver != null) {
            filtered.filter { it.name == selectedDriver.vehicleType }
        } else {
            filtered
        }
    }

    // Auto-select weight based on vehicle for Cargo mode
    LaunchedEffect(vehicleType, cargoStandards, isCargoMode) {
        if (isCargoMode && vehicleType.isNotEmpty() && cargoStandards.isNotEmpty()) {
            val vInfo = vehicleList.find { it.name == vehicleType }
            vInfo?.let { v ->
                // Find the closest matching bracket in cargoStandards
                val bestMatch = cargoStandards.minByOrNull { abs(it.maxWeight - v.maxCargoWeightKg) }
                if (bestMatch != null) {
                    selectedCargoStandard = bestMatch
                }
            }
        }
    }

    // Auto-select first vehicle when list changes or current selection becomes invalid
    LaunchedEffect(vehicleList, isCargoMode) {
        if (vehicleList.isNotEmpty()) {
            val currentValid = vehicleList.any { it.name == vehicleType }
            if (!currentValid || vehicleType.isEmpty()) {
                vehicleType = vehicleList[0].name
            }
        } else {
            vehicleType = ""
        }
    }



    DisposableEffect(Unit) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val list = mutableListOf<CargoStandard>()
                    if (snapshot.exists()) {
                        if (snapshot.hasChild("weightBrackets")) {
                            // Complex new structure
                            val complex = snapshot.getValue(CargoStandards::class.java)
                            complex?.weightBrackets?.forEach { wb ->
                                list.add(CargoStandard(wb.id, wb.fromKg, wb.toKg, wb.surcharge, wb.loadingFee, wb.unloadingFee))
                            }
                        } else if (snapshot.hasChildren()) {
                            // Flat list structure (old version)
                            snapshot.children.forEach { child ->
                                // Skip if the key is not a push ID (e.g. metadata)
                                if (child.key == "weightBrackets" || child.key == "loadingOptions") return@forEach
                                
                                try {
                                    val s = child.getValue(CargoStandard::class.java)
                                    if (s != null) {
                                        list.add(if (s.id.isEmpty() || s.id != child.key) s.copy(id = child.key ?: "") else s)
                                    }
                                } catch (e: Exception) {
                                    Log.e("HomeScreen", "Skip child ${child.key}: ${e.message}")
                                }
                            }
                        }
                    }
                    cargoStandards = list
                    if (selectedCargoStandard == null && list.isNotEmpty()) {
                        selectedCargoStandard = list[0]
                    }
                } catch (e: Exception) {
                    Log.e("HomeScreen", "Global error loading cargo standards", e)
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("HomeScreen", "DB Cancelled", error.toException())
            }
        }
        val ref = firebaseManager.database.child("settings").child("cargo_standards")
        ref.addValueEventListener(listener)
        
        scope.launch { pricingFormulas = firebaseManager.getPricingFormulas() }
        
        onDispose { ref.removeEventListener(listener) }
    }
    
    var isGpsFixed by remember { mutableStateOf(false) }
    var isVerifyingLocations by remember { mutableStateOf(false) }
    var showOrderSummary by remember { mutableStateOf(false) }
    
    var routeOptions by remember { mutableStateOf(emptyList<RouteOption>()) }
    var selectedRouteIndex by remember { mutableIntStateOf(0) }
    var detectedTolls by remember { mutableStateOf(emptyList<com.albasel.tawsilaapp.util.TollGate>()) }

    val tripTimeMinutes = remember(calculatedDistance) { (calculatedDistance * 1.5).toInt().coerceAtLeast(1) }
    
    val instantlyEstimatedPrice = remember(calculatedDistance, pickupLatLng, destinationLatLng, vehicleType, isCargoMode, pricingFormulas, vehiclePrices, includesLoading, includesUnloading, cargoItemCount, cargoWaitMins, selectedCargoStandard, detectedTolls, tripTimeMinutes, rideType, passengers) {
        val vp = vehiclePrices[vehicleType] ?: com.albasel.tawsilaapp.data.model.VehiclePrice(vehicleType, 10.0, 0.0, 0.0, 0.0)
        
        // Fast Estimation: Use straight line distance if detailed route hasn't loaded yet
        val distanceToUse = if (calculatedDistance > 0.1) {
            calculatedDistance
        } else if (pickupLatLng != null && destinationLatLng != null) {
            // Factor of 1.4 to account for city road curves
            com.albasel.tawsilaapp.util.MapUtils.calculateDistance(pickupLatLng!!.latitude, pickupLatLng!!.longitude, destinationLatLng!!.latitude, destinationLatLng!!.longitude) * 1.4
        } else {
            0.0
        }

        // Find formula based on category and distance
        val formula = pricingFormulas.filter { 
            it.rideCategory == "HOME" && 
            distanceToUse >= it.minKm && 
            distanceToUse <= it.maxKm
        }
        .sortedWith(compareByDescending<com.albasel.tawsilaapp.data.model.PricingFormula> { it.vehicleType == vehicleType }.thenBy { it.vehicleType == "" || it.vehicleType == "الكل" })
        .firstOrNull { it.vehicleType == vehicleType || it.vehicleType == "" || it.vehicleType == "الكل" }
        
        val baseFare = formula?.baseFare ?: vp.baseFare
        val pricePerKm = formula?.pricePerKm ?: vp.pricePerKm
        val pricePerMinute = formula?.pricePerMinute ?: vp.pricePerMinute
        val formulaTollFee = formula?.tollFee ?: vp.tollFee

        val timeMinutes = if (calculatedDistance > 0.1) tripTimeMinutes.toDouble() else (distanceToUse * 1.5)
        val effectiveTollCount = max(1, detectedTolls.size)
        val tollFeeTotal = effectiveTollCount * formulaTollFee
        
        // First 1 km is covered by Base Fare (فتح العداد), distance billing starts from 2nd kilometer
        val billableKm = (distanceToUse - 1.0).coerceAtLeast(0.0)
        
        if (isCargoMode) {
            // Cargo Logic (Uses Travel formula as base + supplementary fees)
            val surcharge = formula?.surcharge ?: 0.0
            val items = cargoItemCount.toIntOrNull() ?: 1
            val lFee = if(includesLoading) (formula?.loadingFee ?: 0.0) * items else 0.0
            val uFee = if(includesUnloading) (formula?.unloadingFee ?: 0.0) * items else 0.0
            val waitFee = 0.0
            
            val raw = baseFare + (billableKm * pricePerKm) + (timeMinutes * pricePerMinute) + tollFeeTotal + surcharge + lFee + uFee + waitFee
            raw.toInt()
        } else {
            val passengersInt = (passengers.toIntOrNull() ?: 1).coerceAtLeast(1)
            val selectedVInfo = vehicleList.find { it.name == vehicleType }
            val capacity = (selectedVInfo?.capacity ?: 4).coerceAtLeast(1)
            
            val totalRaw = baseFare + (billableKm * pricePerKm) + (timeMinutes * pricePerMinute) + tollFeeTotal
            
            if (rideType == "SHARED") {
                // Formula: (Total Car Price / Capacity) * Selected Passengers
                val pricePerPax = totalRaw / capacity
                (pricePerPax * passengersInt).toInt()
            } else {
                val vCount = ceil(passengersInt.toDouble() / capacity.toDouble()).toInt().coerceAtLeast(1)
                (totalRaw * vCount).toInt()
            }
        }
    }
    
    var focusedField by remember { mutableStateOf<String?>(null) } 


    val fastDistance = remember(pickupLatLng, destinationLatLng) {
        if (pickupLatLng != null && destinationLatLng != null) {
            com.albasel.tawsilaapp.util.MapUtils.calculateDistance(pickupLatLng!!.latitude, pickupLatLng!!.longitude, destinationLatLng!!.latitude, destinationLatLng!!.longitude) * 1.4
        } else 0.0
    }

    // Load Egypt Places - Expanded & Localized with Real-Time Admin Sync
    val allEgyptPlaces = remember(customPoisVersion) {
        val customList = listOf(
            SearchSuggestion("دمياط القديمة", "مركز دمياط - محافظة دمياط", 0.0, LatLng(31.4175, 31.8144), "city"),
            SearchSuggestion("دمياط القديمه", "مركز دمياط - محافظة دمياط", 0.0, LatLng(31.4175, 31.8144), "city"),
            SearchSuggestion("مدينة دمياط", "دمياط القديمة - محافظة دمياط", 0.0, LatLng(31.4175, 31.8144), "city"),
            SearchSuggestion("المركز", "دمياط القديمة", 0.0, LatLng(31.4175, 31.8144), "city"),
            SearchSuggestion("المنصورة الجديدة", "New Mansoura City", 0.0, LatLng(31.4880, 31.4550), "city"),
            SearchSuggestion("جامعة المنصورة الجديدة", "New Mansoura University", 0.0, LatLng(31.4850, 31.4500), "university"),
            SearchSuggestion("بنزينة مصر - المنصورة الجديدة", "Misr Petroleum - New Mansoura", 0.0, LatLng(31.4890, 31.4580), "gas"),
            SearchSuggestion("مدرسه المنصورة الجديدة الرسمية", "New Mansoura Official School", 0.0, LatLng(31.4840, 31.4480), "school"),
            SearchSuggestion("العاصمة الإدارية الجديدة", "New Administrative Capital", 0.0, LatLng(29.9800, 31.7500), "city"),
            SearchSuggestion("البرج الأيقوني", "Iconic Tower - New Capital", 0.0, LatLng(30.0100, 31.7600), "landmark"),
            SearchSuggestion("مسجد الفتاح العليم", "Al-Fattah Al-Alim Mosque - New Capital", 0.0, LatLng(30.0200, 31.6800), "mosque"),
            SearchSuggestion("كاتدرائية ميلاد المسيح", "Nativity of Christ Cathedral - New Capital", 0.0, LatLng(30.0050, 31.7300), "church"),
            SearchSuggestion("مدينة العلمين الجديدة", "New Alamein City", 0.0, LatLng(30.8350, 28.9450), "city"),
            SearchSuggestion("أبراج العلمين", "Alamein Towers", 0.0, LatLng(30.8400, 28.9500), "landmark"),
            SearchSuggestion("الأكاديمية العربية - العلمين", "AASTMT - New Alamein", 0.0, LatLng(30.8300, 28.9600), "university"),
            SearchSuggestion("دار مصر 1", "دمياط الجديدة - الحي السادس (بجوار مستشفى دار الحياة)", 0.0, LatLng(31.4508, 31.6760), "residential"),
            SearchSuggestion("دار مصر 2", "دمياط الجديدة - الحي السادس (بعد الموقف الشرقي - قبالة جامعة الأزهر على اليمين)", 0.0, LatLng(31.4535, 31.6795), "residential"),
            SearchSuggestion("الموقف الغربي بدمياط الجديدة", "دمياط الجديدة - الحي الرابع", 0.0, LatLng(31.4280, 31.6500), "transport"),
            SearchSuggestion("الموقف الشرقي بدمياط الجديدة", "دمياط الجديدة - الحي السادس", 0.0, LatLng(31.4510, 31.6770), "transport"),
            SearchSuggestion("جامعة الأزهر بدمياط الجديدة", "دمياط الجديدة - قبالة دار مصر 2 على طريق الميناء", 0.0, LatLng(31.4525, 31.6780), "university"),
            SearchSuggestion("المدرسة اليابانية", "دمياط الجديدة - الحي السادس", 0.0, LatLng(31.4545, 31.6940), "school"),
            SearchSuggestion("سكن مصر", "Damietta New City - دمياط الجديدة", 0.0, LatLng(31.4450, 31.6800), "residential"),
            SearchSuggestion("جامعة دمياط", "Damietta University - دمياط الجديدة", 0.0, LatLng(31.4402, 31.6601), "university"),
            SearchSuggestion("مستشفى دمياط العام", "Damietta General Hospital", 0.0, LatLng(31.4180, 31.8130), "hospital"),
            SearchSuggestion("كلية التربية النوعية", "دمياط الجديدة", 0.0, LatLng(31.4380, 31.6620), "university"),
            SearchSuggestion("كلية التربية النوعية بدمياط الجديدة", "دمياط الجديدة", 0.0, LatLng(31.4380, 31.6620), "university"),
            SearchSuggestion("مستشفى دار الحياة", "دمياط الجديدة - بالقرب من الموقف الشرقي", 0.0, LatLng(31.4505, 31.6765), "hospital"),
            SearchSuggestion("مستشفى دار الحياة بدمياط الجديدة", "دمياط الجديدة - بالقرب من الموقف الشرقي", 0.0, LatLng(31.4505, 31.6765), "hospital"),
            SearchSuggestion("نادي دمياط الرياضي", "Damietta Sporting Club", 0.0, LatLng(31.4167, 31.8167), "club"),
            SearchSuggestion("مول العرب", "Mall of Arabia - Giza", 0.0, LatLng(30.0075, 30.9733), "mall"),
            SearchSuggestion("مول مصر", "Mall of Egypt - Giza", 0.0, LatLng(29.9723, 31.0183), "mall"),
            SearchSuggestion("سيتي ستارز", "City Stars - Cairo", 0.0, LatLng(30.0732, 31.3451), "mall"),
            SearchSuggestion("مطار القاهرة الدولي", "Cairo International Airport", 0.0, LatLng(30.1219, 31.4056), "airport"),
            SearchSuggestion("مطار برج العرب", "Borg El Arab Airport", 0.0, LatLng(30.9178, 29.6964), "airport"),
            SearchSuggestion("القاهرة", "Cairo, Egypt", 0.0, LatLng(30.0591, 31.2341), "city"),
            SearchSuggestion("الإسكندرية", "Alexandria, Egypt", 0.0, LatLng(31.2001, 29.9187), "city"),
            SearchSuggestion("الجيزة", "Giza, Egypt", 0.0, LatLng(30.0131, 31.2089), "city"),
            SearchSuggestion("دمياط", "Damietta, Egypt", 0.0, LatLng(31.4175, 31.8144), "city"),
            SearchSuggestion("المنصورة", "Mansoura, Egypt", 0.0, LatLng(31.0409, 31.3785), "city"),
            SearchSuggestion("بورسعيد", "Port Said, Egypt", 0.0, LatLng(31.2653, 32.3019), "city"),
            SearchSuggestion("طنطا", "Tanta, Egypt", 0.0, LatLng(30.7865, 31.0004), "city"),
            SearchSuggestion("الزقازيق", "Zagazig, Egypt", 0.0, LatLng(30.5877, 31.5020), "city"),
            SearchSuggestion("شرم الشيخ", "Sharm El-Sheikh, Egypt", 0.0, LatLng(27.9158, 34.3300), "city"),
            SearchSuggestion("الغردقة", "Hurghada, Egypt", 0.0, LatLng(27.2579, 33.8116), "city")
        )
        val egyptPlaces = com.albasel.tawsilaapp.data.local.EgyptPlacesData.allPlaces.map { item ->
            SearchSuggestion(item.name, item.address, item.distance, item.latLng, item.category)
        }
        val damiettaPois = com.albasel.tawsilaapp.util.DamiettaPlaces.POIS.map { poi ->
            SearchSuggestion(poi.nameAr, poi.district.ifEmpty { "دمياط" }, 0.0, LatLng(poi.lat, poi.lng), poi.category)
        }
        (customList + egyptPlaces + damiettaPois)
            .distinctBy { com.albasel.tawsilaapp.util.ArabicUtils.normalize(it.name) }
    }

    var isSatelliteMode by remember { mutableStateOf(false) }
    var mapLibreInstance by remember { mutableStateOf<MapLibreMap?>(null) }

    LaunchedEffect(pickupLatLng, destinationLatLng, pickupName, destinationName) {
        if (pickupLatLng == null || destinationLatLng == null || 
            (pickupName.isNotBlank() && pickupName != lastResolvedPickupName) ||
            (destinationName.isNotBlank() && destinationName != lastResolvedDestinationName)) {
            calculatedDistance = 0.0
            routePolylines.forEach { mapLibreInstance?.removePolyline(it) }
            routePolylines = emptyList()
        }
    }
    var mapPoiMarkers by remember { mutableStateOf<List<org.maplibre.android.annotations.Marker>>(emptyList()) }
    val markerToPoiMap = remember { mutableMapOf<Long, SearchSuggestion>() }
    var currentMapZoom by remember { mutableFloatStateOf(12.0f) }
    var selectedPoiForDialog by remember { mutableStateOf<SearchSuggestion?>(null) }

    // Clear POI signs/markers from map canvas
    LaunchedEffect(mapLibreInstance, isSatelliteMode) {
        val map = mapLibreInstance ?: return@LaunchedEffect
        mapPoiMarkers.forEach { try { map.removeMarker(it) } catch(e: Exception) {} }
        mapPoiMarkers = emptyList()
        markerToPoiMap.clear()
    }

    // Control road numbers / highway shields (10, 37, 21...) so they ONLY appear at car location zoom (~16.0+)
    LaunchedEffect(mapLibreInstance, mapStyleLoaded) {
        val map = mapLibreInstance ?: return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect
        try {
            style.layers.forEach { layer ->
                val id = layer.id.lowercase()
                if (id.contains("shield") || id.contains("ref") || id.contains("number") || id.contains("route_label")) {
                    layer.minZoom = 15.5f
                }
            }
        } catch (e: Exception) {
            Log.e("HomeScreen", "Error adjusting road shield layers", e)
        }
    }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val styleUrl = if (isSatelliteMode)
        com.albasel.tawsilaapp.data.map.config.MapTileProviderConfig.satelliteTileUrl
    else if (isDarkMode) 
        com.albasel.tawsilaapp.data.map.config.MapTileProviderConfig.nightTileUrl
    else 
        com.albasel.tawsilaapp.data.map.config.MapTileProviderConfig.dayTileUrl

    // Handle Selected Driver Lock
    val initialCalendar = remember {
        val cal = Calendar.getInstance()
        if (cal.get(Calendar.MINUTE) > 0) {
            cal.add(Calendar.HOUR_OF_DAY, 1)
        }
        cal.set(Calendar.MINUTE, 0)
        cal
    }
    
    var selectedDate by remember { 
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(initialCalendar.time)) 
    }
    
    // Auto-select gender if driver is pre-selected
    LaunchedEffect(selectedDriver) {
        selectedDriver?.let {
            requestedGender = if (it.gender == "FEMALE") "FEMALE" else "MALE"
        }
    }

    var showNotificationsOverlay by remember { mutableStateOf(false) }
    val notificationsList = remember { mutableStateListOf<InAppNotification>() }

    // Rotation sensor for real-time smooth compass turning of car icon
    var deviceAzimuthDegrees by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? android.hardware.SensorManager
        val rotationSensor = sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_ROTATION_VECTOR)
        
        var sensorListener: android.hardware.SensorEventListener? = null
        if (sensorManager != null && rotationSensor != null) {
            val rotationMatrix = FloatArray(9)
            val orientationValues = FloatArray(3)
            
            sensorListener = object : android.hardware.SensorEventListener {
                override fun onSensorChanged(event: android.hardware.SensorEvent?) {
                    if (event != null && event.sensor.type == android.hardware.Sensor.TYPE_ROTATION_VECTOR) {
                        android.hardware.SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        android.hardware.SensorManager.getOrientation(rotationMatrix, orientationValues)
                        val rawAzimuth = Math.toDegrees(orientationValues[0].toDouble()).toFloat()
                        val azimuth = (rawAzimuth + 360f) % 360f
                        
                        val diff = (azimuth - deviceAzimuthDegrees + 540f) % 360f - 180f
                        deviceAzimuthDegrees = (deviceAzimuthDegrees + diff * 0.12f + 360f) % 360f
                        animatedCarBearing = deviceAzimuthDegrees
                    }
                }
                override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
            }
            sensorManager.registerListener(sensorListener, rotationSensor, android.hardware.SensorManager.SENSOR_DELAY_GAME)
        }
        
        onDispose {
            if (sensorManager != null && sensorListener != null) {
                sensorManager.unregisterListener(sensorListener)
            }
        }
    }

    // 1. Map Marker Synchronization - Manages the 3D Car Marker (Smooth Interpolation)
    val markerLat = remember { Animatable(currentLocation?.latitude?.toFloat() ?: 0f) }
    val markerLng = remember { Animatable(currentLocation?.longitude?.toFloat() ?: 0f) }
    val markerBearing = remember { Animatable(animatedCarBearing) }

    LaunchedEffect(currentLocation) {
        currentLocation?.let {
            launch { markerLat.animateTo(it.latitude.toFloat(), tween(1200, easing = LinearEasing)) }
            launch { markerLng.animateTo(it.longitude.toFloat(), tween(1200, easing = LinearEasing)) }
        }
    }

    LaunchedEffect(animatedCarBearing) {
        var diff = (animatedCarBearing - markerBearing.value) % 360
        if (diff > 180) diff -= 360
        if (diff < -180) diff += 360
        markerBearing.animateTo(markerBearing.value + diff, tween(800, easing = LinearEasing))
    }

    LaunchedEffect(markerLat.value, markerLng.value, markerBearing.value, isDarkMode, mapLibreInstance, mapStyleLoaded) {
        val map = mapLibreInstance ?: return@LaunchedEffect
        if (map.style == null || !map.style!!.isFullyLoaded) return@LaunchedEffect
        
        val pos = LatLng(markerLat.value.toDouble(), markerLng.value.toDouble())
        val carIcon = MapMarkerUtils.createNavigationArrowIcon(context, markerBearing.value, isDarkMode)
        
        val current = animatedCarMarker
        if (current != null && map.markers.contains(current)) {
            current.position = pos
            current.icon = carIcon
        } else {
            animatedCarMarker = map.addMarker(MarkerOptions()
                .position(pos)
                .icon(carIcon)
                .title(if(isAr) "موقعك" else "Your Location")
            )
        }
    }

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
    var selectedTime by remember { 
        val hour = initialCalendar.get(Calendar.HOUR)
        val displayHour = if (hour == 0) 12 else hour
        val amPm = if(initialCalendar.get(Calendar.AM_PM) == Calendar.AM) (if(isAr) "صباحاً" else "AM") else (if(isAr) "مساءً" else "PM")
        
        mutableStateOf(String.format(Locale.ENGLISH, "%02d:00 %s", displayHour, amPm))
    }

    LaunchedEffect(selectedDriver) {
        if (selectedDriver != null) {
            selectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date())
            val cal = Calendar.getInstance()
            // If minutes are > 0, we move to the next full hour
            if (cal.get(Calendar.MINUTE) > 0) {
                cal.add(Calendar.HOUR_OF_DAY, 1)
            }
            cal.set(Calendar.MINUTE, 0)
            
            val hour = cal.get(Calendar.HOUR)
            val displayHour = if (hour == 0) 12 else hour
            val amPm = if(cal.get(Calendar.AM_PM) == Calendar.AM) (if(isAr) "صباحاً" else "AM") else (if(isAr) "مساءً" else "PM")
            
            selectedTime = String.format(Locale.ENGLISH, "%02d:00 %s", displayHour, amPm)
            
            // Lock vehicle type to selected driver's type
            val dType = selectedDriver.vehicleType
            if (dType.isNotEmpty()) {
                vehicleType = dType
            }
            rideType = "PRIVATE"
        }
    }

    // Smart Suggestion Engine & Auto-Search - Refined for speed and precision
    fun normalizeAr(s: String) = com.albasel.tawsilaapp.util.ArabicUtils.normalize(s)
    fun strictAr(s: String) = normalizeAr(s).replace(" ", "")

    val activeSearchQuery = remember(focusedField, pickupName, destinationName) {
        if (focusedField == "PICKUP") pickupName.split(" - ").first().trim()
        else if (focusedField == "DEST") destinationName.split(" - ").first().trim()
        else ""
    }

    val activeSuggestions by produceState(
        initialValue = emptyList<com.albasel.tawsilaapp.data.repository.PlaceSuggestion>(),
        key1 = activeSearchQuery,
        key2 = currentLocation
    ) {
        if (activeSearchQuery.length >= 1) {
            value = searchRepo.searchPlaces(
                activeSearchQuery,
                currentLocation?.latitude,
                currentLocation?.longitude,
                if (isAr) "ar" else "en"
            )
        } else {
            value = emptyList()
        }
    }



    LaunchedEffect(Unit) {
        firebaseManager.listenToCustomPOIs(context)
    }

    var searchGeneration by remember { mutableIntStateOf(0) }

    var lastAnimatedDestLatLng by remember { mutableStateOf<LatLng?>(null) }
    var lastAnimatedPickupLatLng by remember { mutableStateOf<LatLng?>(null) }

    // Auto-animate Map Camera ONLY ONCE when Destination or Pickup LatLng is FIRST resolved or changed to a new location
    LaunchedEffect(destinationLatLng, pickupLatLng) {
        val dest = destinationLatLng
        val pickup = pickupLatLng
        
        val isNewDest = dest != null && dest != lastAnimatedDestLatLng
        val isNewPickup = pickup != null && pickup != lastAnimatedPickupLatLng

        if (isNewDest || isNewPickup) {
            mapLibreInstance?.let { map ->
                if (dest != null && pickup != null && pickup != dest) {
                    try {
                        val bounds = org.maplibre.android.geometry.LatLngBounds.Builder()
                            .include(pickup)
                            .include(dest)
                            .build()
                        map.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngBounds(bounds, 120))
                    } catch (e: Exception) {
                        map.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(dest, 15.5))
                    }
                } else if (dest != null) {
                    map.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(dest, 15.5))
                } else if (pickup != null) {
                    map.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(pickup, 15.5))
                }
                lastAnimatedDestLatLng = dest
                lastAnimatedPickupLatLng = pickup
            }
        }
    }

    LaunchedEffect(pickupName) {
        val cleanQuery = pickupName.split(" - ").first().trim()
        if (cleanQuery.length < 2 || cleanQuery.contains("Location") || cleanQuery.contains("موقع")) return@LaunchedEffect
        
        val gen = ++searchGeneration
        delay(80)
        if (gen != searchGeneration) return@LaunchedEffect

        try {
            val res = searchRepo.searchPlaces(cleanQuery, currentLocation?.latitude, currentLocation?.longitude, if(isAr) "ar" else "en")
            val validResult = res.firstOrNull { p -> p.lat != 0.0 && p.lng != 0.0 }
            if (validResult != null && gen == searchGeneration) {
                val targetLatLng = LatLng(validResult.lat, validResult.lng)
                pickupLatLng = targetLatLng
                lastResolvedPickupName = cleanQuery
                Log.d("WASALNA_TRACE", "SEARCH_RESULT_SELECTED (PICKUP): ${validResult.name} -> LatLng(${validResult.lat}, ${validResult.lng})")
            }
        } catch (e: Exception) {
            Log.e("HomeScreen", "Auto-search pickup error: ${e.message}")
        }
    }

    // Automatic Search logic for Destination - Updates map automatically
    LaunchedEffect(destinationName) {
        val cleanQuery = destinationName.split(" - ").first().trim()
        if (cleanQuery.length < 2 || cleanQuery.contains("Location") || cleanQuery.contains("موقع")) return@LaunchedEffect
        
        val gen = ++searchGeneration
        delay(80)
        if (gen != searchGeneration) return@LaunchedEffect

        try {
            val res = searchRepo.searchPlaces(cleanQuery, currentLocation?.latitude, currentLocation?.longitude, if(isAr) "ar" else "en")
            val validResult = res.firstOrNull { p -> p.lat != 0.0 && p.lng != 0.0 }
            if (validResult != null && gen == searchGeneration) {
                val targetLatLng = LatLng(validResult.lat, validResult.lng)
                destinationLatLng = targetLatLng
                lastResolvedDestinationName = cleanQuery

                // Automatically save resolved search into Recent Search History
                com.albasel.tawsilaapp.util.RecentSearchHelper.saveRecentSearch(
                    context,
                    cleanQuery,
                    validResult.address,
                    validResult.lat,
                    validResult.lng
                )
                
                // Ensure pickupLatLng is automatically set to current location if missing
                if (pickupLatLng == null && currentLocation != null) {
                    pickupLatLng = currentLocation
                }
                
                Log.d("WASALNA_TRACE", "SEARCH_RESULT_SELECTED (DESTINATION): ${validResult.name} -> LatLng(${validResult.lat}, ${validResult.lng})")
            }
        } catch (e: Exception) {
            Log.e("HomeScreen", "Auto-search destination error: ${e.message}")
        }
    }

    LaunchedEffect(currentLocation) {
        currentLocation?.let { ll ->
            if (pickupName.contains("Location") || pickupName.contains("موقع")) {
                if (pickupLatLng == null || com.albasel.tawsilaapp.util.MapUtils.calculateDistance(pickupLatLng!!.latitude, pickupLatLng!!.longitude, ll.latitude, ll.longitude) > 0.015) {
                    pickupLatLng = ll
                }
            }
            if (destinationName.contains("Location") || destinationName.contains("موقع")) {
                if (destinationLatLng == null || com.albasel.tawsilaapp.util.MapUtils.calculateDistance(destinationLatLng!!.latitude, destinationLatLng!!.longitude, ll.latitude, ll.longitude) > 0.015) {
                    destinationLatLng = ll
                }
            }
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    suspend fun updateAddressFromLocation(lat: Double, lng: Double) {
        withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale("ar"))
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                val addr = addresses?.firstOrNull()
                val city = addr?.locality ?: addr?.subAdminArea ?: ""
                val province = addr?.adminArea ?: ""
                withContext(Dispatchers.Main) {
                    if (pickupCity.isEmpty()) pickupCity = city
                    pickupProvinceState = province
                    if (destinationCity.isEmpty()) destinationCity = city
                    if (pickupName.isEmpty() || pickupName.contains("موقع") || pickupName.contains("Location")) {
                        val baseLocName = if(isAr) "موقع العميل" else "Customer Location"
                        pickupName = if (province.isNotEmpty()) "$baseLocName - $province" else baseLocName
                    }
                    
                    // Auto-pan to location disabled to allow manual control
                }
            } catch (e: Exception) {}
        }
    }
    // Auto-detect GPS if it was off then turned on
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    scope.launch {
                        try {
                            // Immediate high-priority check
                            val tokenSource = com.google.android.gms.tasks.CancellationTokenSource()
                            val loc = fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.token).await()
                                ?: fusedLocationClient.lastLocation.await()
                            loc?.let { 
                                val ll = LatLng(it.latitude, it.longitude)
                                currentLocation = ll
                                if (pickupLatLng == null || pickupName.isEmpty() || pickupName.contains("Location") || pickupName.contains("موقع")) {
                                    pickupLatLng = ll
                                    updateAddressFromLocation(it.latitude, it.longitude)
                                }
                                isGpsFixed = true
                            }
                        } catch (e: Exception) {}
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            scope.launch {
                try {
                    // 1. Get last known immediately for speed
                    fusedLocationClient.lastLocation.await()?.let { loc ->
                        val ll = LatLng(loc.latitude, loc.longitude)
                        currentLocation = ll
                        if (loc.hasBearing()) animatedCarBearing = loc.bearing
                        if (pickupLatLng == null) pickupLatLng = ll
                        updateAddressFromLocation(loc.latitude, loc.longitude)
                    }

                    // 2. Continuous high-accuracy attempts for precision
                    repeat(5) {
                        val tokenSource = com.google.android.gms.tasks.CancellationTokenSource()
                        val loc = fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.token).await()
                        loc?.let { 
                            val ll = LatLng(it.latitude, it.longitude)
                            currentLocation = ll
                            if (it.hasBearing()) animatedCarBearing = it.bearing
                            if (pickupLatLng == null) pickupLatLng = ll
                            updateAddressFromLocation(it.latitude, it.longitude)
                            isGpsFixed = true
                            return@launch
                        }
                        delay(1200)
                    }
                } catch (e: Exception) { Log.e("HomeScreen", "GPS Error", e) }
            }
        }
    }

    fun fitRouteToVisibleMap() {
        val p1 = pickupLatLng ?: return
        val p2 = destinationLatLng ?: return
        val map = mapLibreInstance ?: return

        val tPan0 = android.os.SystemClock.elapsedRealtime()
        Log.d("WASALNA_PERF", "CAMERA_PAN_START_TIME: t0=$tPan0, p1=$p1, p2=$p2")
        try {
            val boundsBuilder = LatLngBounds.Builder()
            boundsBuilder.include(p1)
            boundsBuilder.include(p2)
            val bounds = boundsBuilder.build()

            val density = context.resources.displayMetrics.density
            val bPaddingPx = (220 * density).toInt()
            val tPaddingPx = (70 * density).toInt()
            val sPaddingPx = (40 * density).toInt()

            map.animateCamera(
                CameraUpdateFactory.newLatLngBounds(bounds, sPaddingPx, tPaddingPx, sPaddingPx, bPaddingPx),
                200
            )
            val tPan1 = android.os.SystemClock.elapsedRealtime()
            Log.d("WASALNA_PERF", "CAMERA_PAN_END_TIME: latency=${tPan1 - tPan0}ms")
        } catch (e: Exception) {
            Log.e("HomeScreen", "fitRouteToVisibleMap error: ${e.message}")
        }
    }

    DisposableEffect(Unit) {
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    val ll = LatLng(loc.latitude, loc.longitude)
                    
                    // Calculate bearing from movement if sensor doesn't provide it
                    if (loc.hasBearing()) {
                        animatedCarBearing = loc.bearing
                    } else if (currentLocation != null) {
                        val prev = currentLocation!!
                        val dist = MapUtils.calculateDistance(prev.latitude, prev.longitude, ll.latitude, ll.longitude)
                        if (dist > 0.002) { // Only update bearing if moved > 2m to avoid jitter
                            animatedCarBearing = MapUtils.calculateBearing(prev.latitude, prev.longitude, ll.latitude, ll.longitude).toFloat()
                        }
                    }
                    
                    currentLocation = ll
                    
                    if (!isGpsFixed) {
                        isGpsFixed = true
                        if (pickupLatLng == null || pickupName.isEmpty() || pickupName.contains("موقع") || pickupName.contains("Location")) {
                            pickupLatLng = ll
                            // Automatically resolve address and set as pickup to trigger routing if destination exists
                            scope.launch { updateAddressFromLocation(ll.latitude, ll.longitude) }
                        }
                    }
                }
            }
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // Faster interval for better responsiveness
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
                .setMinUpdateIntervalMillis(1000)
                .build()
            fusedLocationClient.requestLocationUpdates(request, callback, android.os.Looper.getMainLooper())
        }
        onDispose { fusedLocationClient.removeLocationUpdates(callback) }
    }

    // STABLE ROUTING ENGINE - Professional Real-Time Behavior (NO map.clear())
    LaunchedEffect(pickupName, destinationName, pickupLatLng, destinationLatLng, selectedRouteIndex, vehicleType, isDarkMode, mapLibreInstance, mapStyleLoaded) {
        val map = mapLibreInstance ?: return@LaunchedEffect
        if (map.style == null || !map.style!!.isFullyLoaded) return@LaunchedEffect
        
        val pName = pickupName.trim()
        val dName = destinationName.trim()
        
        // RULE 1, 2, 3: If EITHER pickup OR destination text is empty, DO NOT MOVE CAMERA & DO NOT DRAW ROUTE
        if (pName.isEmpty() || dName.isEmpty() ||
            pickupLatLng == null || destinationLatLng == null ||
            pickupLatLng!!.latitude !in -90.0..90.0 || pickupLatLng!!.longitude !in -180.0..180.0 ||
            destinationLatLng!!.latitude !in -90.0..90.0 || destinationLatLng!!.longitude !in -180.0..180.0) {
            
            destinationMarker?.let { if(map.markers.contains(it)) map.removeMarker(it) }
            destinationMarker = null
            pickupMarker?.let { if(map.markers.contains(it)) map.removeMarker(it) }
            pickupMarker = null
            routePolylines.forEach { if(map.polylines.contains(it)) map.removePolyline(it) }
            routePolylines = emptyList()
            activeRouteData = null
            calculatedDistance = 0.0
            lastPolylineHash = 0
            lastRoutingParams = null
            lastAnimatedRouteParams = null
            return@LaunchedEffect
        }
        
        // RULE 4, 5, 6: BOTH Pickup AND Destination have valid text & coordinates!
        val currentParams = "${pickupLatLng?.latitude},${pickupLatLng?.longitude}_${destinationLatLng?.latitude},${destinationLatLng?.longitude}_${selectedRouteIndex}"

        if (lastRoutingParams == currentParams && routePolylines.isNotEmpty()) {
            return@LaunchedEffect
        }

        if (pickupLatLng == null || destinationLatLng == null ||
            (pickupLatLng!!.latitude == 0.0 && pickupLatLng!!.longitude == 0.0) ||
            (destinationLatLng!!.latitude == 0.0 && destinationLatLng!!.longitude == 0.0)) {
            isRouteLoading = false
            routeOptions = emptyList()
            activeRouteData = null
            calculatedDistance = 0.0
            return@LaunchedEffect
        }

        // --- INSTANT ZERO-LATENCY MAP MOTION (< 50ms) ---
        // 1. Calculate instant distance estimate so UI price updates immediately
        val estDistKm = MapUtils.calculateDistance(pickupLatLng!!.latitude, pickupLatLng!!.longitude, destinationLatLng!!.latitude, destinationLatLng!!.longitude) * 1.35
        if (calculatedDistance == 0.0) {
            calculatedDistance = estDistKm
        }

        // 2. Add/Update Pickup & Destination Pins instantly on map
        val isPickupMyLocation = pName.contains("موقع") || pName.contains("Location")
        val isDestMyLocation = dName.contains("موقع") || dName.contains("Location")

        val isCarAtPickup = if (currentLocation != null && pickupLatLng != null) {
            MapUtils.calculateDistance(currentLocation!!.latitude, currentLocation!!.longitude, pickupLatLng!!.latitude, pickupLatLng!!.longitude) < 0.025
        } else false

        if (!isCarAtPickup && pickupLatLng != null) {
            if (pickupMarker != null && map.markers.contains(pickupMarker)) {
                pickupMarker!!.position = pickupLatLng!!
                pickupMarker!!.title = if(isAr) "البداية: $pickupName" else "Start: $pickupName"
            } else {
                pickupMarker?.let { try { map.removeMarker(it) } catch(e: Exception) {} }
                val greenPin = MapMarkerUtils.createLocationPinIcon(context, android.graphics.Color.parseColor("#2E7D32"))
                pickupMarker = map.addMarker(MarkerOptions().position(pickupLatLng!!).icon(greenPin).title(if(isAr) "البداية: $pickupName" else "Start: $pickupName"))
            }
        } else {
            pickupMarker?.let { try { map.removeMarker(it) } catch(e: Exception) {} }
            pickupMarker = null
        }

        if (destinationMarker != null && map.markers.contains(destinationMarker)) {
            destinationMarker!!.position = destinationLatLng!!
            destinationMarker!!.title = if(isAr) "النهاية: $destinationName" else "End: $destinationName"
        } else if (!isDestMyLocation) {
            destinationMarker?.let { try { map.removeMarker(it) } catch(e: Exception) {} }
            val goldPin = MapMarkerUtils.createLocationPinIcon(context, android.graphics.Color.parseColor("#C5A059"))
            destinationMarker = map.addMarker(MarkerOptions().position(destinationLatLng!!).icon(goldPin).title(if(isAr) "النهاية: $destinationName" else "End: $destinationName"))
        }

        // 3. Animate camera INSTANTLY in 50ms to show both pins centered on screen
        val routeCoordsParams = "${pickupLatLng?.latitude}_${pickupLatLng?.longitude}_${destinationLatLng?.latitude}_${destinationLatLng?.longitude}"
        if (lastAnimatedRouteParams != routeCoordsParams) {
            lastAnimatedRouteParams = routeCoordsParams
            fitRouteToVisibleMap()
        }

        isRouteLoading = true
        val reqId = System.currentTimeMillis()
        routingRequestId = reqId
        
        scope.launch {
            try {
                val tRoute0 = android.os.SystemClock.elapsedRealtime()
                Log.d("WASALNA_PERF", "ROUTE_CALC_START_TIME: t0=$tRoute0, pickup=$pickupLatLng, destination=$destinationLatLng")
                val routes = routingRepo.getRoute(
                    pickupLatLng!!.latitude, pickupLatLng!!.longitude,
                    destinationLatLng!!.latitude, destinationLatLng!!.longitude,
                    if(isAr) "ar" else "en"
                )
                val tRoute1 = android.os.SystemClock.elapsedRealtime()
                Log.d("WASALNA_PERF", "ROUTE_CALC_END_TIME: latency=${tRoute1 - tRoute0}ms, count=${routes.size}")
                
                if (reqId == routingRequestId && routes.isNotEmpty()) {
                    routeOptions = routes
                    // Prioritize primary main-road route heavily used by drivers (OSRM Primary Driving Route)
                    val finalIndex = if (selectedRouteIndex in routes.indices) selectedRouteIndex else 0
                    val chosen = routes[finalIndex]
                    selectedRouteIndex = finalIndex
                    
                    activeRouteData = chosen
                    lastRoutingParams = currentParams
                    calculatedDistance = chosen.distance

                    Log.d("WASALNA_TRACE", "ROUTING_RESPONSE: distance=${chosen.distance} km, duration=${chosen.duration / 60.0} min, points=${chosen.points.size}")

                    // Remove any search result markers when a route is active to avoid ghost icons under the car
                    searchResultMarker?.let { map.removeMarker(it) }
                    searchResultMarker = null
                    
                    val routePoints = chosen.points
                    val currentHash = routePoints.hashCode() + isDarkMode.hashCode()
                    
                    Log.d("WASALNA_ROUTING", "ROUTING_DEBUG_MAP_DRAW_CALLED=YES")
                    Log.d("WASALNA_ROUTING", "ROUTING_DEBUG_MAP_DRAW_POINTS=${routePoints.size}")
                    Log.d("WASALNA_ROUTING", "ROUTING_DEBUG_MAP_DRAW_FIRST_POINT=${routePoints.firstOrNull()?.let { "${it.latitude},${it.longitude}" } ?: "NONE"}")
                    Log.d("WASALNA_ROUTING", "ROUTING_DEBUG_MAP_DRAW_LAST_POINT=${routePoints.lastOrNull()?.let { "${it.latitude},${it.longitude}" } ?: "NONE"}")

                    // Update Route Lines on MapLibre (Single Flat Matte Blue Line)
                    routePolylines.forEach { try { map.removePolyline(it) } catch(e: Exception) {} }
                    
                    val matteBlueColor = android.graphics.Color.parseColor("#1976D2") 
                    val newPolylines = mutableListOf<org.maplibre.android.annotations.Polyline>()
                    
                    if (routePoints.size > 2) {
                        newPolylines.add(map.addPolyline(PolylineOptions().addAll(routePoints).color(matteBlueColor).width(7f)))
                        Log.d("WASALNA_TRACE", "ROUTE_DRAWN: road polyline updated on MapLibre with ${routePoints.size} points")
                    }
                    
                    routePolylines = newPolylines
                    lastPolylineHash = currentHash
                } else if (reqId == routingRequestId) {
                    routeOptions = emptyList()
                    activeRouteData = null
                    calculatedDistance = 0.0
                    routePolylines.forEach { try { map.removePolyline(it) } catch(e: Exception) {} }
                    routePolylines = emptyList()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, if(isAr) "تعذر حساب الطريق، حاول مرة أخرى" else "Unable to calculate route, please try again", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("HomeScreen", "Route error", e)
            } finally {
                if (reqId == routingRequestId) isRouteLoading = false
            }
        }
    }

    val sheetBg = if (isDarkMode) Color(0xFF121212) else Color.White
    val cardBg = if (isDarkMode) Color(0xFF262626) else Color(0xFFF3F3F3)
    val textColor = if (isDarkMode) Color.White else Color.Black

    var nearestDriverDist by remember { mutableDoubleStateOf(0.0) }
    var nearestDriverTime by remember { mutableIntStateOf(0) }
    var onlineDrivers by remember { mutableStateOf(emptyList<User>()) }

    DisposableEffect(Unit) {
        val listener = firebaseManager.listenToOnlineDrivers { drivers ->
            onlineDrivers = drivers
        }
        onDispose {
            firebaseManager.database.child("users").removeEventListener(listener)
        }
    }

    LaunchedEffect(currentLocation, onlineDrivers) {
        currentLocation?.let { myPos ->
            val closest = onlineDrivers.filter { it.latitude != 0.0 }.minByOrNull { d ->
                com.albasel.tawsilaapp.util.MapUtils.calculateDistance(myPos.latitude, myPos.longitude, d.latitude, d.longitude)
            }
            if (closest != null) {
                nearestDriverDist = com.albasel.tawsilaapp.util.MapUtils.calculateDistance(myPos.latitude, myPos.longitude, closest.latitude, closest.longitude)
                nearestDriverTime = (nearestDriverDist * 2.0).toInt().coerceAtLeast(1)
            } else {
                nearestDriverDist = 0.0
            }
        }
    }

    // Voice Input Integration
    var activeVoiceField by remember { mutableStateOf<String?>(null) }
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            // Use the first (highest confidence) full phrase instead of joining, 
            // as EXTRA_RESULTS typically contains full alternative transcriptions.
            val spokenText = data?.get(0) ?: ""
            if (spokenText.isNotEmpty()) {
                if (activeVoiceField == "PICKUP") {
                    pickupName = spokenText
                } else if (activeVoiceField == "DEST") {
                    destinationName = spokenText
                }
            }
        }
    }

    fun startVoiceInput(field: String) {
        focusedField = field // CRITICAL: Set focus so LaunchedEffects for auto-search/map-move trigger
        activeVoiceField = field
        val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if(isAr) "ar-EG" else "en-US")
        }
        try {
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Speech recognizer not available", Toast.LENGTH_SHORT).show()
        }
    }

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(initialValue = SheetValue.Expanded)
    )
    
    // Measure sheet top position dynamically for perfect image alignment
    var sheetExpandedOffsetPx by remember { mutableFloatStateOf(0f) }
    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 260.dp,
        sheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        sheetContainerColor = sheetBg,
        sheetContent = {
            val maxSheetHeight = if (isCargoMode) 910.dp else 850.dp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxSheetHeight)
                    .onGloballyPositioned { coords ->
                        if (scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded && sheetExpandedOffsetPx == 0f) {
                            sheetExpandedOffsetPx = coords.positionInRoot().y
                        }
                    }
                    .border(1.dp, Color(0xFFC5A059).copy(alpha = 0.4f), RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 122.dp)) {
                    // Custom Drag Handle - Matte Gold
                    Box(
                        modifier = Modifier
                            .padding(top = 10.dp, bottom = 4.dp)
                            .width(40.dp)
                            .height(5.dp)
                            .background(Color(0xFFC5A059).copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                            .align(Alignment.CenterHorizontally)
                    )

                    if (selectedDriver != null) {
                        CompositionLocalProvider(LocalLayoutDirection provides (if(isAr) LayoutDirection.Rtl else LayoutDirection.Ltr)) {
                            Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), color = cardBg, shape = RoundedCornerShape(12.dp)) {
                                Column(Modifier.padding(12.dp).fillMaxWidth()) {
                                    Text(if(isAr) "سائق مختار:" else "Selected Driver:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        Text(selectedDriver.name, fontWeight = FontWeight.ExtraBold, color = Color(0xFFC5A059), fontSize = 18.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        IconButton(onClearSelectedDriver) { Icon(Icons.Default.Close, null, tint = Color.Red) }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    UberStyleSearchSheet(
                        pickupName = pickupName,
                        destinationName = destinationName,
                        destinationLatLng = destinationLatLng,
                        focusedField = focusedField,
                        suggestions = activeSuggestions,
                        onSuggestionSelect = { suggestion ->
                            val targetLatLng = LatLng(suggestion.lat, suggestion.lng)
                            if (focusedField == "PICKUP") {
                                pickupLatLng = targetLatLng
                                pickupName = suggestion.name
                                lastResolvedPickupName = suggestion.name
                            } else {
                                destinationLatLng = targetLatLng
                                destinationName = suggestion.name
                                lastResolvedDestinationName = suggestion.name
                            }
                            focusedField = null
                        },
                        onPickupChange = { 
                            pickupName = it
                            calculatedDistance = 0.0
                            if (it.isEmpty()) {
                                pickupLatLng = null
                            }
                        },
                        onDestinationChange = { 
                            destinationName = it
                            calculatedDistance = 0.0
                            if (it.isEmpty()) {
                                destinationLatLng = null
                            }
                        },
                        onFocusField = { focusedField = it },
                        isAr = isAr,
                        onMyLocationClick = {
                            val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            
                            if (!fineGranted && !coarseGranted) {
                                Toast.makeText(context, if (isAr) "يرجى التكرم بتفعيل صلاحية تحديد الموقع للجهاز" else "Please enable location permission", Toast.LENGTH_SHORT).show()
                                return@UberStyleSearchSheet
                            }

                            if (isResolvingMyLocation) return@UberStyleSearchSheet
                            
                            val t0 = System.currentTimeMillis()
                            Log.d("WASALNA_PERF", "LOCATION_CLICK_START: t0=$t0")

                            val now = System.currentTimeMillis()
                            if (now - lastMyLocationClickTime < 500) {
                                // Double Tap: Fit map camera to show full route bounds
                                try {
                                    val p1 = pickupLatLng
                                    val p2 = destinationLatLng
                                    if (p1 != null && p2 != null && mapLibreInstance != null) {
                                        val bounds = LatLngBounds.Builder().include(p1).include(p2).build()
                                        mapLibreInstance?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120), 300)
                                    } else if (p1 != null && mapLibreInstance != null) {
                                        mapLibreInstance?.animateCamera(CameraUpdateFactory.newLatLngZoom(p1, 16.5), 300)
                                    }
                                } catch (e: Exception) { Log.e("HomeScreen", "Double tap error: ${e.message}") }
                            } else {
                                isResolvingMyLocation = true
                                scope.launch {
                                    try {
                                        val loc = withContext(Dispatchers.IO) {
                                            try {
                                                val cancelToken = com.google.android.gms.tasks.CancellationTokenSource()
                                                fusedLocationClient.getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, cancelToken.token).await()
                                                    ?: fusedLocationClient.lastLocation.await()
                                            } catch (e: Exception) {
                                                fusedLocationClient.lastLocation.await()
                                            }
                                        }

                                        val t1 = System.currentTimeMillis()
                                        Log.d("WASALNA_PERF", "LOCATION_GPS_FIX: latency=${t1 - t0}ms")

                                        val targetLL = if (loc != null && loc.latitude in -90.0..90.0 && loc.longitude in -180.0..180.0 && (loc.latitude != 0.0 || loc.longitude != 0.0)) {
                                            LatLng(loc.latitude, loc.longitude)
                                        } else currentLocation ?: LatLng(31.4522, 31.6762) // Dar Misr 2 Default

                                        currentLocation = targetLL
                                        pickupLatLng = targetLL
                                        pickupName = if (isAr) "موقع العميل" else "Customer Location"
                                        lastResolvedPickupName = pickupName
                                        isGpsFixed = true

                                        if (destinationLatLng != null) {
                                            calculatedDistance = MapUtils.calculateDistance(targetLL.latitude, targetLL.longitude, destinationLatLng!!.latitude, destinationLatLng!!.longitude) * 1.35
                                            fitRouteToVisibleMap()
                                        } else {
                                            try {
                                                mapLibreInstance?.animateCamera(CameraUpdateFactory.newLatLngZoom(targetLL, 16.5), 300)
                                            } catch (e: Exception) {}
                                        }

                                        // Resolve city/province safely in background thread with Isolated Handler
                                        val geoHandler = CoroutineExceptionHandler { _, throwable -> Log.e("HomeScreen", "Geocoder error: ${throwable.message}") }
                                        launch(Dispatchers.IO + geoHandler) {
                                            try {
                                                if (Geocoder.isPresent()) {
                                                    val geocoder = Geocoder(context, Locale("ar"))
                                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                                        geocoder.getFromLocation(targetLL.latitude, targetLL.longitude, 1) { addresses ->
                                                            val addr = addresses.firstOrNull()
                                                            val city = addr?.locality ?: addr?.subAdminArea ?: ""
                                                            val province = addr?.adminArea ?: ""
                                                            if (city.isNotEmpty()) pickupCity = city
                                                            if (province.isNotEmpty()) pickupProvinceState = province
                                                        }
                                                    } else {
                                                        @Suppress("DEPRECATION")
                                                        val addresses = geocoder.getFromLocation(targetLL.latitude, targetLL.longitude, 1)
                                                        val addr = addresses?.firstOrNull()
                                                        val city = addr?.locality ?: addr?.subAdminArea ?: ""
                                                        val province = addr?.adminArea ?: ""
                                                        withContext(Dispatchers.Main) {
                                                            if (city.isNotEmpty()) pickupCity = city
                                                            if (province.isNotEmpty()) pickupProvinceState = province
                                                        }
                                                    }
                                                }
                                            } catch (e: Exception) { Log.e("HomeScreen", "Geocoder error: ${e.message}") }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("HomeScreen", "MyLocation error: ${e.message}", e)
                                        Toast.makeText(context, if (isAr) "تعذر الحصول على موقعك حالياً، يرجى اختيار موقعك على الخريطة" else "Unable to fetch current location", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isResolvingMyLocation = false
                                    }
                                }
                            }
                            lastMyLocationClickTime = now
                        },
                        isDarkMode = isDarkMode,
                        onVoiceClick = { startVoiceInput(it) },
                        onSearchClick = { 
                            val fieldToSearch = focusedField ?: "DEST"
                            val query = if(fieldToSearch == "PICKUP") pickupName else destinationName
                            if (query.length >= 2) {
                                scope.launch {
                                    val t0 = android.os.SystemClock.elapsedRealtime()
                                    Log.d("WASALNA_CAMERA_PERF", "SEARCH_CLICK_START: query=$query, field=$fieldToSearch, t0=$t0")
                                    try {
                                        val qStrict = strictAr(query)
                                        val biasLat = currentLocation?.latitude
                                        val biasLng = currentLocation?.longitude

                                        val res = searchRepo.searchPlaces(query, biasLat, biasLng, if(isAr) "ar" else "en")
                                        val t1 = android.os.SystemClock.elapsedRealtime()
                                        Log.d("WASALNA_CAMERA_PERF", "SEARCH_SOLVED: latency=${t1 - t0}ms, count=${res.size}")

                                        val validResult = res.firstOrNull { place -> place.lat != 0.0 && place.lng != 0.0 }
                                        if (validResult != null) {
                                            val targetLatLng = LatLng(validResult.lat, validResult.lng)
                                            if (fieldToSearch == "PICKUP") {
                                                pickupLatLng = targetLatLng
                                                lastResolvedPickupName = query
                                            } else {
                                                destinationLatLng = targetLatLng
                                                lastResolvedDestinationName = query
                                            }
                                            focusedField = null
                                            Log.d("WASALNA_CAMERA_PERF", "COORDS_RESOLVED: field=$fieldToSearch, latLng=$targetLatLng, latency=${android.os.SystemClock.elapsedRealtime() - t0}ms")

                                            mapLibreInstance?.let { map ->
                                                searchResultMarker?.let { map.removeMarker(it) }
                                            }
                                        } else {
                                            if (fieldToSearch == "PICKUP") pickupLatLng = null else destinationLatLng = null
                                            calculatedDistance = 0.0
                                            Toast.makeText(context, if(isAr) "لم يتم العثور على هذا المكان، حاول كتابة اسم المكان أو العنوان بشكل أوضح." else "Place not found, please type the name or address more clearly.", Toast.LENGTH_LONG).show()
                                        }
                                    } catch (e: Exception) {
                                        Log.e("HomeScreen", "Search error", e)
                                    }
                                }
                            }
                        },
                        onSwapClick = {
                            val tempN = pickupName; pickupName = destinationName; destinationName = tempN
                            val tempL = pickupLatLng; pickupLatLng = destinationLatLng; destinationLatLng = tempL
                            
                            val tempR = lastResolvedPickupName; lastResolvedPickupName = lastResolvedDestinationName; lastResolvedDestinationName = tempR
                            
                            isGpsFixed = false
                        },
                        onLaterClick = { if(selectedDriver == null) showDateTimePickerDialog = true },
                        recentSearches = recentSearchesList,
                        onRecentSelect = { item ->
                            destinationName = item.title
                            if (item.lat != 0.0 && item.lng != 0.0) {
                                destinationLatLng = LatLng(item.lat, item.lng)
                                lastResolvedDestinationName = item.title
                            }
                            focusedField = null
                        },
                        onRecentDelete = { item ->
                            deleteRecentSearch(item)
                        },
                        onSafarClick = {
                            sharedPrefs.edit().putBoolean("cargo_mode", false).apply()
                            isCargoMode = false
                        },
                        onCityClick = { onCityClick() },
                        onNearbyClick = { onNearbyClick() },
                        onCargoClick = {
                            isCargoMode = !isCargoMode
                            sharedPrefs.edit().putBoolean("cargo_mode", isCargoMode).apply()
                        },
                        isCargoMode = isCargoMode,
                        destFocusRequester = destFocusRequester
                    )
                    
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 0.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (vehicleList.isEmpty()) {
                                Text(
                                    if(isAr) "عذراً، لا توجد مركبات متاحة حالياً" else "Sorry, no vehicles available right now",
                                    modifier = Modifier.fillMaxWidth().padding(6.dp),
                                    textAlign = TextAlign.Center,
                                    color = Color.Gray,
                                    fontSize = 13.sp
                                )
                            } else {
                                LazyRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(vehicleList) { v ->
                                        VehicleDesignCard(
                                            vehicle = v,
                                            isSelected = vehicleType == v.name,
                                            isLocked = selectedDriver != null,
                                            isDarkMode = isDarkMode,
                                            isCargoMode = isCargoMode,
                                            onClick = {
                                                vehicleType = v.name
                                                val currentP = passengers.toIntOrNull() ?: 1
                                                if (currentP > v.capacity) passengers = v.capacity.toString()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (!isCargoMode) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp), 
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            var pExpanded by remember { mutableStateOf(false) }
                            
                            // 1. Passengers Button (1/4 of width -> weight 2)
                            Box(modifier = Modifier.weight(2f)) {
                                if (selectedDriver == null) {
                                    RideOptionButton(
                                        icon = Icons.Default.Groups,
                                        label = "$passengers " + (if(isAr) "أفراد" else "Pax"),
                                        isSelected = false,
                                        isDarkMode = isDarkMode,
                                        height = 40.dp,
                                        modifier = Modifier.fillMaxWidth()
                                    ) { pExpanded = true }

                                    val currentVehicleCapacity = vehicleList.find { it.name == vehicleType }?.capacity ?: 14
                                    DropdownMenu(
                                        expanded = pExpanded, 
                                        onDismissRequest = { pExpanded = false }, 
                                        offset = androidx.compose.ui.unit.DpOffset(0.dp, (-(currentVehicleCapacity * 50 + 10)).dp),
                                        modifier = Modifier.background(if(isDarkMode) Color(0xFF262626) else Color.White, RoundedCornerShape(24.dp)),
                                        shape = RoundedCornerShape(24.dp)
                                    ) {
                                        (1..currentVehicleCapacity).forEach { n ->
                                            DropdownMenuItem(text = { Text("$n " + (if(isAr) "أفراد" else "Pax"), color = Color(0xFFC5A059), fontWeight = FontWeight.Bold) }, onClick = { passengers = n.toString(); pExpanded = false })
                                        }
                                    }
                                } else {
                                    Spacer(Modifier.fillMaxWidth())
                                }
                            }

                            // 2. Shared Button (1/4 of width -> weight 2)
                            val isSharedEnabled = selectedDriver == null && calculatedDistance >= sharedButtonThreshold
                            Box(modifier = Modifier.weight(2f).padding(horizontal = 1.dp)) {
                                RideOptionButton(
                                    icon = Icons.Default.Groups,
                                    label = if(isAr) "مشتركة" else "Shared",
                                    isSelected = rideType == "SHARED",
                                    isDarkMode = isDarkMode,
                                    height = 40.dp,
                                    enabled = isSharedEnabled,
                                    modifier = Modifier.fillMaxWidth()
                                ) { rideType = "SHARED" }
                            }
                            
                            if (!isSharedEnabled && rideType == "SHARED") {
                                rideType = "PRIVATE"
                            }

                            // 3. Private Button (1/4 of width -> weight 2)
                            Box(modifier = Modifier.weight(2f).padding(horizontal = 1.dp)) {
                                RideOptionButton(
                                    icon = Icons.Default.Person,
                                    label = if(isAr) "خاصة" else "Private",
                                    isSelected = rideType == "PRIVATE" || selectedDriver != null,
                                    isDarkMode = isDarkMode,
                                    height = 40.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) { 
                                    if (selectedDriver == null) rideType = "PRIVATE" 
                                }
                            }

                            // 4. Male Driver Button (Increased to 64x64dp)
                            Box(modifier = Modifier.padding(horizontal = 2.dp), contentAlignment = Alignment.Center) {
                                if (selectedDriver == null || selectedDriver.gender != "FEMALE") {
                                    RideOptionButton(
                                        painter = painterResource(R.drawable.male_icon),
                                        label = "",
                                        isSelected = requestedGender == "MALE",
                                        isDarkMode = isDarkMode,
                                        activeColor = Color(0xFF2196F3),
                                        useOriginalColor = true,
                                        noBorder = true,
                                        height = 64.dp,
                                        modifier = Modifier.size(64.dp)
                                    ) { if(selectedDriver == null) requestedGender = if(requestedGender == "MALE") "ANY" else "MALE" }
                                }
                            }

                            // 5. Female Driver Button (Increased to 64x64dp)
                            Box(modifier = Modifier.padding(horizontal = 2.dp), contentAlignment = Alignment.Center) {
                                if (selectedDriver == null || selectedDriver.gender == "FEMALE") {
                                    RideOptionButton(
                                        painter = painterResource(R.drawable.female_icon),
                                        label = "",
                                        isSelected = requestedGender == "FEMALE",
                                        isDarkMode = isDarkMode,
                                        activeColor = Color(0xFFFF4081),
                                        useOriginalColor = true,
                                        noBorder = true,
                                        height = 64.dp,
                                        modifier = Modifier.size(64.dp)
                                    ) { if(selectedDriver == null) requestedGender = if(requestedGender == "FEMALE") "ANY" else "FEMALE" }
                                }
                            }
                        }
                    } else {
                        // Cargo Mode: Professional Compact Layout
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CompositionLocalProvider(LocalLayoutDirection provides (if(isAr) LayoutDirection.Rtl else LayoutDirection.Ltr)) {
                                val goldColor = Color(0xFFC5A059)
                                
                                OutlinedTextField(
                                    value = cargoDetails,
                                    onValueChange = { cargoDetails = it },
                                    placeholder = { Text(if(isAr) "ماذا ستشحن ؟ (كتب هدايا أثاث)" else "What are you shipping? (Books, gifts, furniture)", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = goldColor.copy(0.6f)) },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true,
                                    maxLines = 1,
                                    textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center, fontSize = 13.sp, color = goldColor, fontWeight = FontWeight.Bold),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = goldColor,
                                        unfocusedBorderColor = goldColor.copy(0.5f),
                                        focusedTextColor = goldColor,
                                        unfocusedTextColor = goldColor
                                    )
                                )

                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    val fieldHeight = 56.dp
                                    
                                // 1. Qty
                                OutlinedTextField(
                                    value = cargoItemCount,
                                    onValueChange = { if (it.all { char -> char.isDigit() }) cargoItemCount = it },
                                    label = { Text(if(isAr) "القطع" else "Qty", fontSize = 9.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = goldColor) },
                                    modifier = Modifier.weight(1f).height(fieldHeight),
                                    shape = RoundedCornerShape(10.dp),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = goldColor),
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = goldColor,
                                        unfocusedBorderColor = goldColor.copy(0.5f),
                                        focusedTextColor = goldColor,
                                        unfocusedTextColor = goldColor,
                                        focusedLabelColor = goldColor,
                                        unfocusedLabelColor = goldColor
                                    )
                                )

                                    // 2. Load
                                    var expL by remember { mutableStateOf(false) }
                                    Box(modifier = Modifier.weight(1.2f).height(fieldHeight)) {
                                        OutlinedTextField(
                                            value = if (includesLoading) (if(isAr) "نعم" else "Yes") else (if(isAr) "لا" else "No"),
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text(if(isAr) "تحميل" else "Loading", fontSize = 9.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = goldColor) },
                                            trailingIcon = { Icon(if(expL) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = goldColor, modifier = Modifier.size(14.dp)) },
                                            modifier = Modifier.fillMaxWidth().height(fieldHeight),
                                            shape = RoundedCornerShape(10.dp),
                                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = goldColor),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = goldColor,
                                                unfocusedBorderColor = goldColor.copy(0.5f),
                                                focusedTextColor = goldColor,
                                                unfocusedTextColor = goldColor,
                                                focusedLabelColor = goldColor,
                                                unfocusedLabelColor = goldColor
                                            ),
                                            enabled = true
                                        )
                                        Box(Modifier.matchParentSize().clickable { expL = true })

                                        DropdownMenu(
                                            expanded = expL, 
                                            onDismissRequest = { expL = false }, 
                                            modifier = Modifier.background(sheetBg).width(165.dp).border(1.5.dp, goldColor, RoundedCornerShape(16.dp)),
                                            shape = RoundedCornerShape(16.dp)
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text(if(isAr) "بدون تحميل" else "No Loading", fontWeight = FontWeight.Bold, color = goldColor) }, 
                                                onClick = { includesLoading = false; expL = false }
                                            )
                                            HorizontalDivider(color = goldColor.copy(0.2f), thickness = 1.dp, modifier = Modifier.padding(vertical = 2.dp))
                                            DropdownMenuItem(
                                                text = { Text(if(isAr) "مع تحميل" else "Include Loading", fontWeight = FontWeight.Bold, color = goldColor) }, 
                                                onClick = { includesLoading = true; expL = false }
                                            )
                                        }
                                    }

                                    // 3. Unload
                                    var expU by remember { mutableStateOf(false) }
                                    Box(modifier = Modifier.weight(1.2f).height(fieldHeight)) {
                                        OutlinedTextField(
                                            value = if (includesUnloading) (if(isAr) "نعم" else "Yes") else (if(isAr) "لا" else "No"),
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text(if(isAr) "تفريغ" else "Unloading", fontSize = 9.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = goldColor) },
                                            trailingIcon = { Icon(if(expU) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = goldColor, modifier = Modifier.size(14.dp)) },
                                            modifier = Modifier.fillMaxWidth().height(fieldHeight),
                                            shape = RoundedCornerShape(10.dp),
                                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = goldColor),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = goldColor,
                                                unfocusedBorderColor = goldColor.copy(0.5f),
                                                focusedTextColor = goldColor,
                                                unfocusedTextColor = goldColor,
                                                focusedLabelColor = goldColor,
                                                unfocusedLabelColor = goldColor
                                            ),
                                            enabled = true
                                        )
                                        Box(Modifier.matchParentSize().clickable { expU = true })

                                        DropdownMenu(
                                            expanded = expU, 
                                            onDismissRequest = { expU = false }, 
                                            modifier = Modifier.background(sheetBg).width(165.dp).border(1.5.dp, goldColor, RoundedCornerShape(16.dp)),
                                            shape = RoundedCornerShape(16.dp)
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text(if(isAr) "بدون تفريغ" else "No Unloading", fontWeight = FontWeight.Bold, color = goldColor) }, 
                                                onClick = { includesUnloading = false; expU = false }
                                            )
                                            HorizontalDivider(color = goldColor.copy(0.2f), thickness = 1.dp, modifier = Modifier.padding(vertical = 2.dp))
                                            DropdownMenuItem(
                                                text = { Text(if(isAr) "مع تفريغ" else "Include Unloading", fontWeight = FontWeight.Bold, color = goldColor) }, 
                                                onClick = { includesUnloading = true; expU = false }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        InfoActionIcon(Icons.Default.AccountBalanceWallet, if(isAr) "الدفع" else "Payment", if(isAr) "نقداً" else "Cash", isDarkMode)
                        InfoActionIcon(Icons.Default.AccessTime, if(isAr) "الوصول" else "Arrival", "$tripTimeMinutes min", isDarkMode)
                        InfoActionIcon(Icons.Default.Timeline, if(isAr) "المسافة" else "Distance", String.format("%.1f km", calculatedDistance), isDarkMode)
                        InfoActionIcon(Icons.Default.Payments, if(isAr) "السعر" else "Price", "$instantlyEstimatedPrice ج.م", isDarkMode)
                    }

                    Spacer(Modifier.height(8.dp))

                    val gradient = Brush.horizontalGradient(
                        colors = listOf(Color(0xFFE6AF2E), Color(0xFFD4AF37), Color(0xFFC5A059))
                    )
                    
                    Surface(
                        onClick = { 
                            if (pickupLatLng == null || destinationLatLng == null) {
                                Toast.makeText(context, if(isAr) "يرجى تحديد نقطة الانطلاق والوصول" else "Select pickup and destination", Toast.LENGTH_SHORT).show()
                                return@Surface
                            }
                            if (user?.isApproved == false) {
                                Toast.makeText(context, if(isAr) "عذراً، حسابك معلق بانتظار المراجعة من الإدارة" else "Sorry, your account is suspended", Toast.LENGTH_LONG).show()
                                return@Surface
                            }
                            if (hasBlockingOrder) {
                                Toast.makeText(context, if(isAr) "عذراً، لديك طلب قيد الانتظار أو القبول حالياً" else "You already have a pending/accepted order", Toast.LENGTH_LONG).show()
                                return@Surface
                            }
                            showOrderSummary = true
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        enabled = user?.isApproved != false && vehicleType.isNotEmpty() && instantlyEstimatedPrice > 0,
                        color = Color.Transparent,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(if(user?.isApproved == false || vehicleType.isEmpty() || instantlyEstimatedPrice <= 0) SolidColor(Color.Gray.copy(0.5f)) else gradient),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isVerifyingLocations) {
                                CircularProgressIndicator(Modifier.size(24.dp), color = Color.White)
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    if (user?.isApproved != false && vehicleType.isNotEmpty()) {
                                        Icon(Icons.Default.KeyboardDoubleArrowRight, null, tint = Color.White.copy(0.7f), modifier = Modifier.size(24.dp))
                                        Spacer(Modifier.weight(1f))
                                    }
                                    
                                    Text(
                                        if(isAr) "احجز الآن" else "Book Now", 
                                        fontWeight = FontWeight.Black, 
                                        color = Color.White, 
                                        fontSize = 18.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )

                                    if (user?.isApproved != false && vehicleType.isNotEmpty()) {
                                        Spacer(Modifier.weight(1f))
                                        Icon(Icons.Default.KeyboardDoubleArrowLeft, null, tint = Color.White.copy(0.7f), modifier = Modifier.size(24.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        sheetDragHandle = null,
        content = {
            val density = androidx.compose.ui.platform.LocalDensity.current
            
            // Safe offset calculation for Background Image sliding & Alpha fade
            val sheetMotionData by remember {
                derivedStateOf {
                    try {
                        val currentOffsetPx = try { scaffoldState.bottomSheetState.requireOffset() } catch (e: Exception) { 1000f }
                        val expandedOffsetPx = if (sheetExpandedOffsetPx > 0) sheetExpandedOffsetPx else (120f * density.density)
                        
                        // Height/Peek logic: Assume peek is at ~screenHeight - 260dp
                        // We'll calculate alpha based on movement. 
                        // Full fade out over ~300dp of travel
                        val fadeDistancePx = 300f * density.density
                        val diffPx = (currentOffsetPx - expandedOffsetPx).coerceAtLeast(0f)
                        
                        val progress = (diffPx / fadeDistancePx).coerceIn(0f, 1f)
                        
                        val yOffset = with(density) { diffPx.toDp() }
                        val alpha = 1f - progress
                        
                        Pair(yOffset, alpha)
                    } catch (e: Exception) { Pair(0.dp, 1f) }
                }
            }
            
            val backgroundImageHeight by remember {
                derivedStateOf {
                    if (sheetExpandedOffsetPx > 0) {
                        //طرف الصورة من تحت يبقي تحت طرف الستارة العلوي بحدود 2 سنتيمتر (approx 75dp)
                        with(density) { sheetExpandedOffsetPx.toDp() + 75.dp } 
                    } else {
                        450.dp 
                    }
                }
            }

            Box(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull()
                                    if (change != null && change.pressed && !change.previousPressed) {
                                        val isFromEdge = change.position.x < 28.dp.toPx()
                                        onToggleDrawerGestures(isFromEdge)
                                    }
                                }
                            }
                        }
                ) {
                    MapLibreCompose(
                        modifier = Modifier.fillMaxSize(), 
                        styleUrl = styleUrl, 
                        onMapReady = { map ->
                            mapLibreInstance = map
                            currentMapZoom = map.cameraPosition.zoom.toFloat()
                            
                            map.addOnCameraIdleListener {
                                currentMapZoom = map.cameraPosition.zoom.toFloat()
                            }

                            // Enable Admin Long-Click on Map to Create / Edit / Delete Place at pressed coordinates
                            map.addOnMapLongClickListener { latLng ->
                                if (user?.role?.uppercase() == "ADMIN") {
                                    scope.launch {
                                        try {
                                            val screenPoint = map.projection.toScreenLocation(latLng)
                                            val features = map.queryRenderedFeatures(screenPoint)
                                            var featureName = ""
                                            for (feat in features) {
                                                val nameProp = feat.getStringProperty("name:ar") 
                                                    ?: feat.getStringProperty("name") 
                                                    ?: feat.getStringProperty("name:en")
                                                if (!nameProp.isNullOrBlank() && nameProp != "موقع" && nameProp != "طريق") {
                                                    featureName = nameProp.trim()
                                                    break
                                                }
                                            }

                                            val closestPoi = DamiettaPlaces.POIS.minByOrNull { poi ->
                                                val dLat = poi.lat - latLng.latitude
                                                val dLng = poi.lng - latLng.longitude
                                                (dLat * dLat + dLng * dLng)
                                            }?.takeIf { poi ->
                                                val dLat = Math.abs(poi.lat - latLng.latitude)
                                                val dLng = Math.abs(poi.lng - latLng.longitude)
                                                (dLat < 0.002 && dLng < 0.002) || (featureName.isNotBlank() && poi.nameAr.contains(featureName))
                                            }

                                            val finalName = featureName.ifEmpty { closestPoi?.nameAr ?: "" }
                                            val finalLat = closestPoi?.lat ?: latLng.latitude
                                            val finalLng = closestPoi?.lng ?: latLng.longitude
                                            val finalDistrict = closestPoi?.district ?: "دمياط الجديدة"
                                            val finalCat = closestPoi?.category ?: "landmark"

                                            adminEditOriginalName = closestPoi?.nameAr ?: ""
                                            adminNewPoiNameInput = finalName
                                            adminNewPoiLatInput = String.format(java.util.Locale.ENGLISH, "%.7f", finalLat)
                                            adminNewPoiLngInput = String.format(java.util.Locale.ENGLISH, "%.7f", finalLng)
                                            adminNewPoiDistrictInput = finalDistrict
                                            adminSelectedCategory = com.albasel.tawsilaapp.utils.MapMarkerUtils.resolveCategoryKey(finalCat, finalName)
                                            adminIsExistingPoi = (closestPoi != null)
                                            showAdminCreatePoiDialog = true
                                        } catch (e: Exception) {
                                            Log.e("HomeScreen", "Long click error: ${e.message}")
                                        }
                                    }
                                    true
                                } else {
                                    false
                                }
                            }

                            // Enable Admin Direct Click on Pickup or Destination Marker to Edit Name & Coordinates
                            map.setOnMarkerClickListener { marker ->
                                val isAdmin = user?.role?.uppercase() == "ADMIN"
                                val isPickupMarker = pickupMarker != null && marker.id == pickupMarker?.id
                                val isDestMarker = destinationMarker != null && marker.id == destinationMarker?.id
                                
                                if (isAdmin && (isPickupMarker || isDestMarker)) {
                                    val targetName = if (isPickupMarker) pickupName else destinationName
                                    adminEditIsPickupTarget = isPickupMarker
                                    adminEditOriginalName = targetName
                                    adminEditNameInput = targetName
                                    val targetLatLng = if (isPickupMarker) pickupLatLng else destinationLatLng
                                    adminEditLatInput = String.format(java.util.Locale.ENGLISH, "%.7f", targetLatLng?.latitude ?: 0.0)
                                    adminEditLngInput = String.format(java.util.Locale.ENGLISH, "%.7f", targetLatLng?.longitude ?: 0.0)
                                    adminEditDistrictInput = "دمياط الجديدة"
                                    showAdminPoiEditDialog = true
                                    true
                                } else {
                                    val clickedPoi = markerToPoiMap[marker.id]
                                    if (clickedPoi != null) {
                                        selectedPoiForDialog = clickedPoi
                                        true
                                    } else {
                                        false
                                    }
                                }
                            }
                        },
                        onStyleLoaded = { mapStyleLoaded = !mapStyleLoaded } // Toggle to trigger effects
                    )
                    
                    // Main Background Image - Anchored to screen top, fades & slides UP as sheet slides DOWN
                    Image(
                        painter = painterResource(id = R.drawable.main_page),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(backgroundImageHeight)
                            .offset(y = -sheetMotionData.first)
                            .graphicsLayer { alpha = sheetMotionData.second },
                        contentScale = ContentScale.FillWidth,
                        alignment = Alignment.TopCenter
                    )
                }
                
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = (if(isDarkMode) Color.Black else Color.White).copy(0.9f), shape = CircleShape, shadowElevation = 4.dp) { IconButton({ onMenuClick() }) { Icon(Icons.Default.Menu, null, tint = if(isDarkMode) Color.White else Color.Black) } }
                    }
                    Surface(color = (if(isDarkMode) Color.Black else Color.White).copy(0.9f), shape = CircleShape, shadowElevation = 4.dp) {
                        IconButton({ showNotificationsOverlay = !showNotificationsOverlay }) {
                            BadgedBox(badge = { if(notificationsList.isNotEmpty()) Badge { Text("${notificationsList.size}") } }) {
                                Icon(Icons.Default.NotificationsNone, null, tint = if(isDarkMode) Color.White else Color.Black) 
                            }
                        }
                    }
                }

                Column(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val fabBg = if(isDarkMode) Color(0xFF1C2430) else Color.White
                    val fabBorder = if(isDarkMode) Color(0xFF2E3C4F) else Color.Transparent
                    val fabIconTint = if(isDarkMode) Color.White else Color.Black
                    
                    Surface(
                        onClick = onToggleTheme, 
                        color = if(isDarkMode) Color(0xFF1F2937) else Color.Black.copy(0.8f), 
                        shape = CircleShape, 
                        modifier = Modifier.size(44.dp), 
                        shadowElevation = 6.dp,
                        border = if(isDarkMode) BorderStroke(1.dp, fabBorder) else null
                    ) {
                        Box(contentAlignment = Alignment.Center) { Icon(if(isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode, null, tint = if(isDarkMode) Color(0xFFFFD76A) else Color.White, modifier = Modifier.size(20.dp)) }
                    }
                    // Satellite Layer Toggle Button
                    Surface(
                        onClick = { 
                            isSatelliteMode = !isSatelliteMode
                            Toast.makeText(
                                context, 
                                if (isSatelliteMode) 
                                    (if(isAr) "تم تفعيل نمط الأقمار الصناعية (Satellite View)" else "Satellite View Enabled") 
                                else 
                                    (if(isAr) "تم تفعيل الخريطة العادية" else "Map View Enabled"), 
                                Toast.LENGTH_SHORT
                            ).show()
                        }, 
                        color = if (isSatelliteMode) Color(0xFFC5A059) else fabBg, 
                        shape = CircleShape, 
                        modifier = Modifier.size(44.dp), 
                        shadowElevation = 6.dp,
                        border = if(isDarkMode) BorderStroke(1.dp, fabBorder) else null
                    ) {
                        Box(contentAlignment = Alignment.Center) { 
                            Icon(
                                Icons.Default.Layers, 
                                null, 
                                tint = if (isSatelliteMode) Color.White else fabIconTint, 
                                modifier = Modifier.size(20.dp)
                            ) 
                        }
                    }
                    Surface(
                        onClick = { mapLibreInstance?.animateCamera(CameraUpdateFactory.zoomIn()) }, 
                        color = fabBg, 
                        shape = CircleShape, 
                        modifier = Modifier.size(44.dp), 
                        shadowElevation = 6.dp,
                        border = if(isDarkMode) BorderStroke(1.dp, fabBorder) else null
                    ) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, null, tint = fabIconTint) }
                    }
                    Surface(
                        onClick = { mapLibreInstance?.animateCamera(CameraUpdateFactory.zoomOut()) }, 
                        color = fabBg, 
                        shape = CircleShape, 
                        modifier = Modifier.size(44.dp), 
                        shadowElevation = 6.dp,
                        border = if(isDarkMode) BorderStroke(1.dp, fabBorder) else null
                    ) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Remove, null, tint = fabIconTint) }
                    }

                    // Route Focus Button (Directly ABOVE MyLocation Button)
                    Surface(
                        onClick = { 
                            if (pickupLatLng != null && destinationLatLng != null) {
                                fitRouteToVisibleMap()
                            } else if (pickupLatLng != null && mapLibreInstance != null) {
                                mapLibreInstance?.animateCamera(CameraUpdateFactory.newLatLngZoom(pickupLatLng!!, 16.5))
                            } else {
                                Toast.makeText(context, if(isAr) "يرجى تحديد الموقع والوجهة أولاً" else "Select pickup and destination first", Toast.LENGTH_SHORT).show()
                            }
                        },
                        color = fabBg, 
                        shape = CircleShape, 
                        modifier = Modifier.size(44.dp), 
                        shadowElevation = 6.dp,
                        border = if(isDarkMode) BorderStroke(1.dp, fabBorder) else null
                    ) {
                        Box(contentAlignment = Alignment.Center) { 
                            Icon(Icons.Default.Route, null, tint = Color(0xFFC5A059), modifier = Modifier.size(22.dp)) 
                        }
                    }

                    Surface(
                        onClick = { 
                            scope.launch {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                    val tokenSource = com.google.android.gms.tasks.CancellationTokenSource()
                                    val loc = fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.token).await() ?: fusedLocationClient.lastLocation.await()
                                    loc?.let {
                                        val ll = LatLng(it.latitude, it.longitude)
                                        currentLocation = ll
                                        mapLibreInstance?.animateCamera(CameraUpdateFactory.newLatLngZoom(ll, 16.5))
                                    }
                                }
                            }
                        },
                        color = fabBg, 
                        shape = CircleShape, 
                        modifier = Modifier.size(44.dp), 
                        shadowElevation = 6.dp,
                        border = if(isDarkMode) BorderStroke(1.dp, fabBorder) else null
                    ) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.MyLocation, null, tint = Color(0xFF4EA8FF)) }
                    }
                }
            }
        }
    )

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
                                        if (n.title.contains("عرض") || n.title.contains("خصم") || n.type == "wallet" || n.title.contains("🎁")) {
                                            val matchedPromo = activePromoCodes.firstOrNull { n.content.contains(it.code, ignoreCase = true) } ?: activePromoCodes.firstOrNull()
                                            currentPromoDialogData = matchedPromo ?: com.albasel.tawsilaapp.data.model.PromoCode(
                                                code = matchedPromo?.code ?: "",
                                                percentage = matchedPromo?.percentage ?: 0.0,
                                                offerText = n.content,
                                                validityDays = matchedPromo?.validityDays ?: 1
                                            )
                                            showPromoOfferDialog = true
                                        } else {
                                            val targetRoute = if (!n.sourceId.isNullOrEmpty()) n.sourceId else n.type
                                            if (targetRoute.isNotEmpty()) {
                                                onNotificationsClick(targetRoute, targetRoute)
                                            }
                                        }
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
                                        IconButton(onClick = {
                                            if(user != null) scope.launch { firebaseManager.deleteNotification(user.id, n.id) }
                                        }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Delete, null, tint = Color.Red.copy(0.6f), modifier = Modifier.size(16.dp))
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

    val displayPoints = remember(user, onlineDrivers) {
        // Calculate based on sync logic or actual completed history visibility
        user?.points ?: 0.0
    }

    if (showDateTimePickerDialog) {
        AlertDialog(
            onDismissRequest = { showDateTimePickerDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth(0.95f),
            content = {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isDarkMode) Color(0xFF121212) else Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    com.albasel.tawsilaapp.ui.components.SideBySideDateTimePickerUI(
                        isAr = isAr,
                        isDarkMode = isDarkMode,
                        selectedDate = selectedDate,
                        selectedTime = selectedTime,
                        showMinutes = rideType == "PRIVATE" || selectedDriver != null,
                        onDateChange = { selectedDate = it },
                        onTimeChange = { selectedTime = it },
                        onConfirm = { showDateTimePickerDialog = false },
                        onCancel = { showDateTimePickerDialog = false }
                    )
                }
            }
        )
    }

    if (showOrderSummary) {
        val estimatedPrice = remember { mutableDoubleStateOf(instantlyEstimatedPrice.toDouble()) }
        var discountInput by remember { mutableStateOf("") }
        var pointValue by remember { mutableDoubleStateOf(0.0) }
        var tollFeeAmount by remember { mutableDoubleStateOf(0.0) }
        var vehicleCount by remember { mutableIntStateOf(1) }
        var freeWaitingMinsState by remember { mutableIntStateOf(15) }

        LaunchedEffect(Unit) {
            val prices = firebaseManager.getVehiclePricesDetail()
            
            val formula = pricingFormulas.filter { 
                it.rideCategory == "HOME" && 
                calculatedDistance >= it.minKm && 
                calculatedDistance <= it.maxKm
            }
            .sortedWith(compareByDescending<PricingFormula> { it.vehicleType == vehicleType }.thenBy { it.vehicleType == "" || it.vehicleType == "الكل" })
            .firstOrNull { it.vehicleType == vehicleType || it.vehicleType == "" || it.vehicleType == "الكل" }
            
            freeWaitingMinsState = if(isCargoMode) (formula?.freeWaitMinutesCargo ?: 30) else (formula?.freeWaitMinutes ?: 15)
            pointValue = firebaseManager.getPointValue("USER")
        }

        if (showPromoOfferDialog && currentPromoDialogData != null) {
            AlertDialog(
                onDismissRequest = { showPromoOfferDialog = false },
                shape = RoundedCornerShape(20.dp),
                containerColor = if (isDarkMode) Color(0xFF1F1F1F) else Color.White,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🎁", fontSize = 22.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isAr) "عرض ترويجي وكود خصم" else "Promo Offer & Discount Code", fontWeight = FontWeight.Bold, color = Color(0xFFC5A059), fontSize = 16.sp)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(currentPromoDialogData!!.offerText, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor)
                        if (currentPromoDialogData!!.code.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFC5A059).copy(0.15f),
                                border = BorderStroke(1.dp, Color(0xFFC5A059))
                            ) {
                                Column(Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(if (isAr) "كود الخصم للرحلات:" else "Promo Discount Code:", fontSize = 11.sp, color = Color.Gray)
                                    Text(currentPromoDialogData!!.code, fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFFB8860B))
                                    Text(if (isAr) "نسبة الخصم: ${currentPromoDialogData!!.percentage.toInt()}%" else "Discount: ${currentPromoDialogData!!.percentage.toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                    Text(if (isAr) "مدة الصلاحية: ${currentPromoDialogData!!.validityDays} يوم" else "Valid for: ${currentPromoDialogData!!.validityDays} days", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showPromoOfferDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC5A059))
                    ) {
                        Text(if (isAr) "حسناً / استخدام الكود" else "OK / Use Code", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // Re-calculate price on input changes
        LaunchedEffect(cargoItemCount, includesLoading, includesUnloading, cargoWaitMins, selectedCargoStandard) {
            if (!isCargoMode) return@LaunchedEffect
            val formulas = firebaseManager.getPricingFormulas()
            val prices = firebaseManager.getVehiclePricesDetail()
            val vp = prices[vehicleType] ?: VehiclePrice(vehicleType, 10.0, 0.0, 0.0, 0.0)
            
            val formula = formulas.filter { 
                it.rideCategory == "HOME" && 
                calculatedDistance >= it.minKm && 
                calculatedDistance <= it.maxKm
            }
            .sortedWith(compareByDescending<PricingFormula> { it.vehicleType == vehicleType }.thenBy { it.vehicleType == "" || it.vehicleType == "الكل" })
            .firstOrNull { it.vehicleType == vehicleType || it.vehicleType == "" || it.vehicleType == "الكل" }
            
            val baseFare = formula?.baseFare ?: vp.baseFare
            val pricePerKm = formula?.pricePerKm ?: vp.pricePerKm
            val pricePerMinute = formula?.pricePerMinute ?: vp.pricePerMinute
            val currentTollFee = formula?.tollFee ?: vp.tollFee
            val tollFeeAmountLocal = max(1, detectedTolls.size) * currentTollFee
            
            val surcharge = formula?.surcharge ?: 0.0
            val items = cargoItemCount.toIntOrNull() ?: 1
            val lFee = if(includesLoading) (formula?.loadingFee ?: 0.0) * items else 0.0
            val uFee = if(includesUnloading) (formula?.unloadingFee ?: 0.0) * items else 0.0
            
            val waitMinsInput = cargoWaitMins.toIntOrNull() ?: (formula?.freeWaitMinutes ?: 15)
            val freeMins = formula?.freeWaitMinutes ?: 15
            val waitPrice = formula?.cargoWaitMinutePrice ?: 0.0
            val waitFee = max(0, waitMinsInput - freeMins) * waitPrice
            
            val billableKmCargo = (calculatedDistance - 1.0).coerceAtLeast(0.0)
            val raw = baseFare + (billableKmCargo * pricePerKm) + (billableKmCargo * 1.4 * pricePerMinute) + tollFeeAmountLocal + surcharge + lFee + uFee + waitFee
            estimatedPrice.doubleValue = raw.toInt().toDouble()
        }

        val currentPoints = user?.points ?: 0.0
        val hasDebt = currentPoints < 0
        
        // Auto-initialize discount input if debt exists
        LaunchedEffect(user?.points, pointValue) {
            if (hasDebt && pointValue > 0) {
                val debtVal = -currentPoints * pointValue
                discountInput = String.format(Locale.ENGLISH, "-%.1f", debtVal)
            }
        }

        var promoCodeInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showOrderSummary = false },
            title = { Text(if(isAr) "تأكيد طلب الرحلة" else "Confirm Booking", fontWeight = FontWeight.Black, color = Color(0xFFC5A059)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                    OrderSummaryItem(Icons.Default.Place, (if(isAr) "من: " else "From: ") + pickupName, Color(0xFFC5A059))
                    OrderSummaryItem(Icons.Default.Flag, (if(isAr) "إلى: " else "To: ") + destinationName, Color(0xFFC5A059))
                    OrderSummaryItem(Icons.Default.Straighten, (if(isAr) "المسافة: " else "Distance: ") + String.format(Locale.ENGLISH, "%.1f كم", if(calculatedDistance > 0.1) calculatedDistance else fastDistance), Color(0xFFC5A059), isBold = true)
                    
                    if (isCargoMode) {
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Text(if(isAr) "تفاصيل الشحن:" else "Cargo Details:", fontWeight = FontWeight.Bold, color = Color(0xFF795548))
                        
                        val selectedV = vehicleList.find { it.name == vehicleType }
                        val maxW = selectedV?.maxCargoWeightKg?.toInt() ?: 0
                        OrderSummaryItem(Icons.Default.Scale, (if(isAr) "حمولة المركبة: " else "Vehicle Capacity: ") + (if(isAr) "حتى $maxW كجم" else "Up to $maxW kg"), Color(0xFF795548))
                        
                        if (cargoDetails.isNotBlank()) {
                            OrderSummaryItem(Icons.Default.Description, (if(isAr) "تفاصيل الشحنة: " else "Cargo Details: ") + cargoDetails, Color(0xFF795548))
                        }

                        OrderSummaryItem(Icons.Default.Numbers, (if(isAr) "عدد القطع: " else "Number of Items: ") + cargoItemCount, Color(0xFF795548))
                        
                        if (includesLoading || includesUnloading) {
                            val services = mutableListOf<String>()
                            if (includesLoading) services.add(if(isAr) "تحميل" else "Loading")
                            if (includesUnloading) services.add(if(isAr) "تفريغ" else "Unloading")
                            OrderSummaryItem(Icons.Default.Build, (if(isAr) "الخدمات الإضافية: " else "Additional Services: ") + services.joinToString(" + "), Color(0xFF795548))
                        }
                        
                        if ((cargoWaitMins.toIntOrNull() ?: 0) > 0) {
                            OrderSummaryItem(Icons.Default.Timer, (if(isAr) "دقائق الانتظار: " else "Wait Minutes: ") + cargoWaitMins, Color(0xFF795548))
                        }
                    } else {
                        OrderSummaryItem(Icons.Default.Groups, (if(isAr) "عدد الأفراد: " else "Passengers: ") + passengers, Color(0xFFC5A059))
                        if (vehicleCount > 1) {
                            OrderSummaryItem(Icons.Default.DirectionsCar, (if(isAr) "عدد السيارات المطلوبة: " else "Number of vehicles: ") + vehicleCount, Color.Red, isBold = true)
                        }
                    }

                    // Add Gender Row
                    val genderLabel = if (requestedGender == "MALE") (if(isAr) "رجل" else "Male")
                                      else if (requestedGender == "FEMALE") (if(isAr) "امرأة" else "Female")
                                      else (if(isAr) "أي نوع" else "Any")
                    val genderIcon = if (requestedGender == "FEMALE") Icons.Default.Female else Icons.Default.Male
                    OrderSummaryItem(genderIcon, (if(isAr) "نوع السائق: " else "Driver Gender: ") + genderLabel, if(requestedGender == "FEMALE") Color(0xFFFF4081) else Color(0xFF2196F3))

                    if (detectedTolls.size > 1) {
                        OrderSummaryItem(Icons.Default.CreditCard, (if(isAr) "عدد الكارتات: " else "Tolls: ") + "${detectedTolls.size} (${detectedTolls.joinToString(", ") { it.name }})", Color(0xFF9C27B0))
                    }

                    val freeWaitMins = freeWaitingMinsState
                    OrderSummaryItem(Icons.Default.Timer, (if(isAr) "وقت الانتظار المجاني: " else "Free Waiting: ") + "$freeWaitMins دقيقة", Color(0xFF2E7D32))
                    
                    val footerText = if (isCargoMode) {
                        if(isAr) "يتم زيادة سعر الرحلة إذا تخطت الوقت المجاني في التحميل أو التفريغ" 
                        else "Price increases if free loading or unloading time is exceeded"
                    } else {
                        if(isAr) "يتم حساب تسعيرة إضافية لكل دقيقة تتخطى وقت الانتظار المجاني" 
                        else "Extra fees apply for waiting beyond free time"
                    }
                    Text(footerText, fontSize = 9.sp, color = Color.Gray, modifier = Modifier.padding(start = 32.dp))

                    OrderSummaryItem(Icons.Default.Event, "$selectedDate | $selectedTime", Color(0xFFC5A059))
                    
                    val pointsDiscountAmount = discountInput.toDoubleOrNull() ?: 0.0
                    val debtToPay = if(hasDebt) -currentPoints * pointValue else 0.0
                    var activePromoCodes by remember { mutableStateOf<List<com.albasel.tawsilaapp.data.model.PromoCode>>(emptyList()) }
                    DisposableEffect(Unit) {
                        val listener = firebaseManager.listenToPromoCodes { activePromoCodes = it }
                        onDispose { firebaseManager.database.child("settings").child("promo_codes").removeEventListener(listener) }
                    }

                    val appliedPromo = remember(promoCodeInput, activePromoCodes) {
                        val clean = promoCodeInput.uppercase().trim()
                        activePromoCodes.firstOrNull { it.code.uppercase().trim() == clean }
                    }

                    val promoPercentage = appliedPromo?.percentage ?: 0.0
                    val baseTripPrice = max(1.0, estimatedPrice.doubleValue - pointsDiscountAmount)
                    val promoDiscountAmount = if (promoPercentage > 0) (baseTripPrice * (promoPercentage / 100.0)) else 0.0
                    val rawFinalPrice = max(1.0, baseTripPrice - promoDiscountAmount)
                    // Floor final price to lower integer (e.g. 27.5 -> 27)
                    val finalPrice = kotlin.math.floor(rawFinalPrice).toInt()

                    val maxCash = if(hasDebt) 0.0 else (user?.points ?: 0.0) * pointValue
                    val userWalletBalanceEgp = maxOf(0.0, (user?.points ?: 0.0) * pointValue)
                    val walletBalanceFormatted = String.format(Locale.ENGLISH, "%.1f", userWalletBalanceEgp)
                    
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountBalanceWallet, null, tint = if(hasDebt) Color.Red else Color(0xFFC5A059), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(if(isAr) "استخدام نقاط المحفظة " else "Use Wallet Points ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if(hasDebt) Color.Red else textColor)
                        Text("(رصيد المحفظة: $walletBalanceFormatted ج.م)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                    }
                    OutlinedTextField(
                        value = discountInput,
                        onValueChange = { 
                            if (!hasDebt) {
                                val amount = it.toDoubleOrNull() ?: 0.0
                                if(amount <= maxCash) discountInput = it 
                            }
                        },
                        readOnly = hasDebt,
                        modifier = Modifier.fillMaxWidth().height(55.dp),
                        placeholder = { Text(if(isAr) "أدخل مبلغ الخصم" else "Enter discount amount", fontSize = 12.sp) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = if(hasDebt) Color.Red else textColor,
                            unfocusedTextColor = if(hasDebt) Color.Red else textColor
                        )
                    )

                    // Show Promo Code Input Box ONLY if there are active promo codes registered by Admin!
                    if (activePromoCodes.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = promoCodeInput,
                            onValueChange = { promoCodeInput = it },
                            modifier = Modifier.fillMaxWidth().height(55.dp),
                            placeholder = { Text(if(isAr) "كود الخصم (أدخل الكود هنا)" else "Promo Code", fontSize = 12.sp) },
                            trailingIcon = {
                                if (appliedPromo != null) {
                                    Text("✓ ${appliedPromo!!.percentage.toInt()}%", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (appliedPromo != null) Color(0xFF2E7D32) else Color(0xFFC5A059),
                                focusedTextColor = textColor,
                                unfocusedTextColor = textColor
                            )
                        )
                        if (appliedPromo != null) {
                            Text(
                                if (isAr) "✓ كود خصم مقبول (خصم ${appliedPromo!!.percentage}% للرحلة)" else "✓ Coupon applied (${appliedPromo!!.percentage}% off)",
                                color = Color(0xFF2E7D32),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        OrderSummaryItem(Icons.Default.Payments, if(isAr) "السعر النهائي:" else "Final Price:", Color(0xFFC5A059), isBold = true)
                        Text("${finalPrice} ج.م", fontWeight = FontWeight.Black, color = Color.Black, fontSize = 20.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val discountAmount = discountInput.toDoubleOrNull() ?: 0.0
                        val currentPoints = user?.points ?: 0.0
                        val hasDebt = currentPoints < 0
                        val debtToPay = if(hasDebt) -currentPoints * pointValue else 0.0
                        
                        showOrderSummary = false; isVerifyingLocations = true
                        scope.launch {
                            try {
                                // Points Deduction (Positive Balance Only)
                                if (!hasDebt && discountAmount > 0 && user != null) {
                                    launch(Dispatchers.IO) {
                                        val pts = discountAmount / pointValue
                                        firebaseManager.incrementUserPoints(user.id, -pts)
                                        val tidU = firebaseManager.database.child("users").child(user.id).child("point_transactions").push().key ?: UUID.randomUUID().toString()
                                        val pTransU = PointTransaction(tidU, -pts, "(عميل) خصم نقاط لاستخدامها بالرحلة", System.currentTimeMillis())
                                        firebaseManager.database.child("users").child(user.id).child("point_transactions").child(tidU).setValue(pTransU)
                                    }
                                }
                                
                                val finalPrice = if (hasDebt) {
                                    estimatedPrice.doubleValue + debtToPay
                                } else {
                                    max(0.0, estimatedPrice.doubleValue - discountAmount)
                                }

                                val currentDiscountAmount = discountInput.toDoubleOrNull() ?: 0.0
                                val matchedPromo = activePromoCodes.firstOrNull { promoCodeInput.isNotBlank() && it.code.uppercase().trim() == promoCodeInput.uppercase().trim() }
                                val promoPercentageVal = matchedPromo?.percentage ?: 0.0
                                val baseTripVal = max(1.0, estimatedPrice.doubleValue - currentDiscountAmount)
                                val currentPromoDiscountVal = if (promoPercentageVal > 0) (baseTripVal * (promoPercentageVal / 100.0)) else 0.0
                                val calcFinalPrice = kotlin.math.floor(max(1.0, baseTripVal - currentPromoDiscountVal)).toInt()

                                val order = Order(
                                    userId = user?.id ?: "", userName = user?.name ?: "", userPhone = user?.phone ?: "", 
                                    pickupName = pickupName, pickupLat = pickupLatLng!!.latitude, pickupLng = pickupLatLng!!.longitude, 
                                    destinationName = destinationName, destinationLat = destinationLatLng!!.latitude, destinationLng = destinationLatLng!!.longitude, 
                                    vehicleType = vehicleType, rideType = rideType, passengers = passengers.toIntOrNull() ?: 1, 
                                    status = "PENDING", 
                                    price = calcFinalPrice.toDouble(), 
                                    pointsDiscount = if(hasDebt) -debtToPay else currentDiscountAmount, 
                                    promoDiscountAmount = currentPromoDiscountVal,
                                    date = selectedDate, time = selectedTime, timestamp = System.currentTimeMillis(), 
                                    pickupProvince = pickupProvinceState,
                                    driverId = selectedDriver?.id, driverName = selectedDriver?.name,
                                    tollCount = detectedTolls.size,
                                    tollNames = detectedTolls.joinToString(", ") { it.name },
                                    distance = calculatedDistance,
                                    isCargo = isCargoMode,
                                    cargoDetails = cargoDetails,
                                    numCartons = cargoItemCount.toIntOrNull() ?: 1,
                                    needsLoading = includesLoading,
                                    needsUnloading = includesUnloading,
                                    cargoWaitMinutes = cargoWaitMins.toIntOrNull() ?: 0,
                                    cargoWeight = vehicleList.find { it.name == vehicleType }?.maxCargoWeightKg ?: 0.0,
                                    trafficSpeedLimitKmh = vehiclePrices[vehicleType]?.trafficSpeedLimitKmh ?: 40.0,
                                    gender = requestedGender
                                )
                                firebaseManager.placeOrder(order); Toast.makeText(context, if(isAr) "تم طلب الرحلة بنجاح!" else "Ride booked successfully!", Toast.LENGTH_LONG).show()
                            } catch (e: Exception) { Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show() } finally { isVerifyingLocations = false }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC5A059), contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(if(isAr) "تأكيد" else "Confirm", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showOrderSummary = false }) { Text(if(isAr) "تعديل" else "Edit") } },
            shape = RoundedCornerShape(28.dp),
            containerColor = Color.White
        )
    }

    if (selectedPoiForDialog != null) {
        val poi = selectedPoiForDialog!!
        AlertDialog(
            onDismissRequest = { selectedPoiForDialog = null },
            icon = {
                Icon(
                    Icons.Default.Place, 
                    null, 
                    tint = Color(0xFFC5A059), 
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    poi.name, 
                    fontWeight = FontWeight.Black, 
                    fontSize = 18.sp, 
                    color = if(isDarkMode) Color.White else Color.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        poi.address, 
                        color = Color.Gray, 
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    
                    // TOP Button: Pickup / الموقع (من أين؟)
                    Button(
                        onClick = {
                            pickupName = poi.name
                            pickupLatLng = poi.latLng
                            lastResolvedPickupName = poi.name
                            selectedPoiForDialog = null
                            mapLibreInstance?.animateCamera(CameraUpdateFactory.newLatLngZoom(poi.latLng, 16.0))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC5A059), contentColor = Color.Black),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.MyLocation, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (isAr) "تعيين كموقع انطلاق (من أين؟)" else "Set as Pickup", fontWeight = FontWeight.Bold)
                    }
                    
                    Spacer(Modifier.height(10.dp))
                    
                    // BOTTOM Button: Destination / الوجهة (إلى أين؟)
                    Button(
                        onClick = {
                            destinationName = poi.name
                            destinationLatLng = poi.latLng
                            lastResolvedDestinationName = poi.name
                            selectedPoiForDialog = null
                            mapLibreInstance?.animateCamera(CameraUpdateFactory.newLatLngZoom(poi.latLng, 16.0))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.Navigation, null, tint = Color(0xFFC5A059), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (isAr) "تعيين كوجهة (إلى أين؟)" else "Set as Destination", fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    val isAdminUser = user?.phone == "01098804131" || user?.phone == "01011850314" || user?.phone == "01098804133" || user?.role?.uppercase() == "ADMIN"
                    if (isAdminUser) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                adminEditOriginalName = poi.name
                                adminNewPoiNameInput = poi.name
                                adminNewPoiLatInput = String.format(java.util.Locale.ENGLISH, "%.7f", poi.latLng.latitude)
                                adminNewPoiLngInput = String.format(java.util.Locale.ENGLISH, "%.7f", poi.latLng.longitude)
                                adminNewPoiDistrictInput = poi.address.ifBlank { "دمياط الجديدة" }
                                adminIsExistingPoi = true
                                selectedPoiForDialog = null
                                showAdminCreatePoiDialog = true
                            },
                            border = BorderStroke(1.dp, Color(0xFFC5A059)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(Icons.Default.EditLocation, null, tint = Color(0xFFC5A059), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (isAr) "✏️ تعديل / حذف هذا الموقع (للأدمن)" else "Edit / Delete Place (Admin)", fontWeight = FontWeight.Bold, color = Color(0xFFC5A059))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { selectedPoiForDialog = null },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isAr) "إلغاء" else "Cancel", color = Color.Gray)
                }
            },
            containerColor = if (isDarkMode) Color(0xFF242424) else Color.White,
            shape = RoundedCornerShape(24.dp)
        )
    }

    val markerCategoryOptions = listOf(
        "residential" to "🏢 كمباوند / سكن",
        "health" to "🏥 مستشفى / مركز طبي",
        "education" to "🎓 جامعة / مدرسة / تعليم",
        "shopping" to "🛒 مول / تجاري / ماركت",
        "food" to "🍕 مطعم / مأكولات",
        "cafe" to "☕ كافيه / مقهى",
        "transport" to "🚌 موقف / مواصلات / مطار",
        "sports" to "⚽ نادي / ملعب / رياضي",
        "mosque" to "🕌 مسجد / دار عبادة",
        "beach" to "🏖️ شاطئ / منتزه / ترفيه",
        "government" to "🏛️ مصلحة حكومية / مجمع محاكم",
        "bank" to "🏦 بنك / صرافة",
        "gas_station" to "⛽ محطة بنزين / خدمات",
        "industrial" to "🏭 منطقة صناعية / مصنع",
        "police" to "🚨 مركز شرطة / مرور",
        "landmark" to "📍 علامة مميزة عامة"
    )

    // 1. Admin Edit Dialog for Pickup / Destination Markers
    if (showAdminPoiEditDialog) {
        AdminPoiEditDialogComposed(
            adminEditIsPickupTarget = adminEditIsPickupTarget,
            adminEditNameInput = adminEditNameInput,
            onNameChange = { adminEditNameInput = it },
            adminEditLatInput = adminEditLatInput,
            onLatChange = { adminEditLatInput = it },
            adminEditLngInput = adminEditLngInput,
            onLngChange = { adminEditLngInput = it },
            adminEditDistrictInput = adminEditDistrictInput,
            onDistrictChange = { adminEditDistrictInput = it },
            adminSelectedCategory = adminSelectedCategory,
            onCategoryChange = { adminSelectedCategory = it },
            adminEditOriginalName = adminEditOriginalName,
            pickupName = pickupName,
            destinationName = destinationName,
            markerCategoryOptions = markerCategoryOptions,
            isAr = isAr,
            onDismiss = { showAdminPoiEditDialog = false },
            onSave = { newName, newLat, newLng, category, district ->
                val oldTargetName = adminEditOriginalName.ifBlank { if (adminEditIsPickupTarget == true) pickupName else destinationName }
                if (adminEditIsPickupTarget == true) {
                    pickupName = newName
                    pickupLatLng = LatLng(newLat, newLng)
                    lastResolvedPickupName = newName
                } else {
                    destinationName = newName
                    destinationLatLng = LatLng(newLat, newLng)
                    lastResolvedDestinationName = newName
                }

                scope.launch(Dispatchers.IO) {
                    try {
                        val localDb = LocalSearchDatabase.getInstance(context)
                        val realCodeId = localDb.getCodeIdForPlace(oldTargetName)
                            ?: localDb.getCodeIdForPlace(newName)
                            ?: (DamiettaPlaces.POIS.find { it.nameAr == oldTargetName || it.nameAr == newName })?.placeId?.takeIf { it.isNotBlank() }
                            ?: (localDb.getMaxCodeId() + 1).toString()

                        com.albasel.tawsilaapp.util.GoogleDriveHelper.removeCodeIdFromDeleted(context, realCodeId)
                        com.albasel.tawsilaapp.util.GoogleDriveHelper.removeCodeIdFromDeleted(context, newName)
                        if (oldTargetName.isNotBlank()) com.albasel.tawsilaapp.util.GoogleDriveHelper.removeCodeIdFromDeleted(context, oldTargetName)

                        com.albasel.tawsilaapp.util.GoogleDriveHelper.recordEditedCodeId(context, realCodeId)
                        com.albasel.tawsilaapp.util.GoogleDriveHelper.recordEditedCodeId(context, newName)
                        if (oldTargetName.isNotBlank()) com.albasel.tawsilaapp.util.GoogleDriveHelper.recordEditedCodeId(context, oldTargetName)

                        if (oldTargetName.isNotBlank() && oldTargetName != newName) {
                            localDb.deletePlace(oldTargetName)
                            firebaseManager.deleteCustomPOI(oldTargetName)
                        }
                        localDb.deletePlaceByCodeId(realCodeId)

                        val resolvedGov = if (district.contains("الإسكندرية")) "الإسكندرية" else if (district.contains("القاهرة")) "القاهرة" else if (district.contains("الجيزة")) "الجيزة" else if (district.contains("قنا")) "قنا" else if (district.isNotBlank()) district else "دمياط"
                        val resolvedCity = if (district.isNotBlank()) district else "دمياط الجديدة"

                        val updatedPoi = com.albasel.tawsilaapp.util.EgyptPOI(
                            placeId = realCodeId,
                            nameAr = newName,
                            nameEn = newName,
                            category = category,
                            lat = newLat,
                            lng = newLng,
                            entranceLat = newLat,
                            entranceLng = newLng,
                            district = district,
                            governorate = resolvedGov,
                            city = resolvedCity,
                            locationSource = "Admin_Edited",
                            locationVerified = true,
                            confidence = "HIGH"
                        )

                        com.albasel.tawsilaapp.util.EgyptPlaces.POIS.removeAll { poi ->
                            poi.placeId == realCodeId || (oldTargetName.isNotBlank() && poi.nameAr == oldTargetName) || poi.nameAr == newName
                        }
                        com.albasel.tawsilaapp.util.EgyptPlaces.POIS.add(0, updatedPoi)

                        localDb.upsertPlacesBatch(listOf(
                            com.albasel.tawsilaapp.data.local.PlaceEntry(
                                id = realCodeId, nameAr = newName, nameEn = newName, category = category,
                                lat = newLat, lng = newLng, entranceLat = newLat, entranceLng = newLng,
                                district = district, governorate = resolvedGov, city = resolvedCity,
                                source = "Admin_Edited", verified = true
                            )
                        ))
                        try { firebaseManager.saveCustomPOI(updatedPoi) } catch (e: Exception) {}
                        com.albasel.tawsilaapp.util.EgyptPlaces.onPoisUpdated.value++
                        try { com.albasel.tawsilaapp.util.GoogleDriveHelper.syncMapPlacesToGoogleDrive(context) } catch (e: Exception) {}
                    } catch (e: Exception) {}
                }

                mapLibreInstance?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(newLat, newLng), 16.5))
                showAdminPoiEditDialog = false
                Toast.makeText(context, if (isAr) "تم تحديث وحفظ موقع \"$newName\" بنجاح!" else "Location updated!", Toast.LENGTH_SHORT).show()
            },
            onDelete = {
                val targetName = adminEditNameInput.ifBlank { adminEditOriginalName }.trim()

                scope.launch(Dispatchers.IO) {
                    try {
                        val localDb = LocalSearchDatabase.getInstance(context)
                        val realCodeId = localDb.getCodeIdForPlace(targetName)
                            ?: (com.albasel.tawsilaapp.util.EgyptPlaces.POIS.find { it.nameAr == targetName })?.placeId
                            ?: ""

                        if (realCodeId.isNotBlank()) {
                            localDb.deletePlaceByCodeId(realCodeId)
                            com.albasel.tawsilaapp.util.GoogleDriveHelper.recordDeletedCodeId(context, realCodeId)
                        }
                        if (targetName.isNotBlank()) {
                            localDb.deletePlace(targetName)
                            firebaseManager.deleteCustomPOI(targetName)
                        }

                        // Complete, permanent purge across all in-memory lists (POIS, drivePois, assetPois)
                        com.albasel.tawsilaapp.util.EgyptPlaces.POIS.removeAll { poi ->
                            (realCodeId.isNotBlank() && poi.placeId == realCodeId) || (targetName.isNotBlank() && poi.nameAr == targetName)
                        }
                        com.albasel.tawsilaapp.util.EgyptPlaces.drivePois.removeAll { poi ->
                            (realCodeId.isNotBlank() && poi.placeId == realCodeId) || (targetName.isNotBlank() && poi.nameAr == targetName)
                        }
                        com.albasel.tawsilaapp.util.EgyptPlaces.assetPois.removeAll { poi ->
                            (realCodeId.isNotBlank() && poi.placeId == realCodeId) || (targetName.isNotBlank() && poi.nameAr == targetName)
                        }

                        // Clear search cache & notify UI instantly
                        com.albasel.tawsilaapp.data.repository.impl.SearchRepositoryImpl.clearCache()
                        com.albasel.tawsilaapp.util.EgyptPlaces.onPoisUpdated.value++

                        com.albasel.tawsilaapp.util.GoogleDriveHelper.syncMapPlacesToGoogleDrive(context)
                    } catch (e: Exception) {}
                }

                if (adminEditIsPickupTarget == true) {
                    try { pickupMarker?.let { mapLibreInstance?.removeMarker(it) } } catch (e: Exception) {}
                    pickupMarker = null; pickupLatLng = null; pickupName = ""
                } else if (adminEditIsPickupTarget == false) {
                    try { destinationMarker?.let { mapLibreInstance?.removeMarker(it) } } catch (e: Exception) {}
                    destinationMarker = null; destinationLatLng = null; destinationName = ""; calculatedDistance = 0.0; routeOptions = emptyList()
                }
                showAdminPoiEditDialog = false
                Toast.makeText(context, if (isAr) "تم حذف المكان بنجاح!" else "Location deleted!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // 2. Admin Create / Edit / Delete Place Dialog on Map Long-Click
    if (showAdminCreatePoiDialog) {
        AdminPoiCreateDialogComposed(
            adminIsExistingPoi = adminIsExistingPoi,
            adminNewPoiNameInput = adminNewPoiNameInput,
            onNameChange = { adminNewPoiNameInput = it },
            adminNewPoiLatInput = adminNewPoiLatInput,
            onLatChange = { adminNewPoiLatInput = it },
            adminNewPoiLngInput = adminNewPoiLngInput,
            onLngChange = { adminNewPoiLngInput = it },
            adminNewPoiDistrictInput = adminNewPoiDistrictInput,
            onDistrictChange = { adminNewPoiDistrictInput = it },
            adminSelectedCategory = adminSelectedCategory,
            onCategoryChange = { adminSelectedCategory = it },
            markerCategoryOptions = markerCategoryOptions,
            isAr = isAr,
            onDismiss = { showAdminCreatePoiDialog = false },
            onSave = { newName, newLat, newLng, category, district ->
                val newLatLng = LatLng(newLat, newLng)
                scope.launch(Dispatchers.IO) {
                    try {
                        val localDb = LocalSearchDatabase.getInstance(context)
                        val realCodeId = if (adminIsExistingPoi) {
                            val targetOrigName = adminEditOriginalName.ifBlank { newName }
                            localDb.getCodeIdForPlace(targetOrigName)
                                ?: localDb.getCodeIdForPlace(newName)
                                ?: (com.albasel.tawsilaapp.util.EgyptPlaces.POIS.find { it.nameAr == targetOrigName || it.nameAr == newName })?.placeId?.takeIf { it.isNotBlank() }
                                ?: (localDb.getMaxCodeId() + 1).toString()
                        } else {
                            (localDb.getMaxCodeId() + 1).toString()
                        }

                        com.albasel.tawsilaapp.util.GoogleDriveHelper.removeCodeIdFromDeleted(context, realCodeId)
                        com.albasel.tawsilaapp.util.GoogleDriveHelper.removeCodeIdFromDeleted(context, newName)
                        if (adminIsExistingPoi) {
                            val targetOrigName = adminEditOriginalName.ifBlank { newName }
                            if (targetOrigName.isNotBlank()) com.albasel.tawsilaapp.util.GoogleDriveHelper.removeCodeIdFromDeleted(context, targetOrigName)
                        }

                        com.albasel.tawsilaapp.util.GoogleDriveHelper.recordEditedCodeId(context, realCodeId)
                        com.albasel.tawsilaapp.util.GoogleDriveHelper.recordEditedCodeId(context, newName)

                        val resolvedGov = if (district.contains("الإسكندرية")) "الإسكندرية" else if (district.contains("القاهرة")) "القاهرة" else if (district.contains("الجيزة")) "الجيزة" else if (district.contains("قنا")) "قنا" else if (district.isNotBlank()) district else "دمياط"
                        val resolvedCity = if (district.isNotBlank()) district else "دمياط الجديدة"

                        val createdPoi = com.albasel.tawsilaapp.util.EgyptPOI(
                            placeId = realCodeId, nameAr = newName, nameEn = newName,
                            category = category, lat = newLat, lng = newLng,
                            entranceLat = newLat, entranceLng = newLng,
                            district = district, governorate = resolvedGov, city = resolvedCity,
                            locationSource = if (adminIsExistingPoi) "Admin_Edited" else "Admin_Created",
                            locationVerified = true, confidence = "HIGH"
                        )

                        if (adminIsExistingPoi) {
                            val targetOrigName = adminEditOriginalName.ifBlank { newName }
                            com.albasel.tawsilaapp.util.EgyptPlaces.POIS.removeAll { poi -> poi.placeId == realCodeId || (targetOrigName.isNotBlank() && poi.nameAr == targetOrigName) || poi.nameAr == newName }
                            localDb.deletePlaceByCodeId(realCodeId)
                            if (targetOrigName.isNotBlank()) localDb.deletePlace(targetOrigName)
                            if (newName != targetOrigName) localDb.deletePlace(newName)
                        }

                        com.albasel.tawsilaapp.util.EgyptPlaces.POIS.add(0, createdPoi)
                        localDb.upsertPlacesBatch(listOf(
                            com.albasel.tawsilaapp.data.local.PlaceEntry(
                                id = realCodeId, nameAr = newName, nameEn = newName, category = category,
                                lat = newLat, lng = newLng, entranceLat = newLat, entranceLng = newLng,
                                district = district, governorate = resolvedGov, city = resolvedCity,
                                source = if (adminIsExistingPoi) "Admin_Edited" else "Admin_Created", verified = true
                            )
                        ))
                        try { firebaseManager.saveCustomPOI(createdPoi) } catch (e: Exception) {}
                        try { com.albasel.tawsilaapp.util.GoogleDriveHelper.syncMapPlacesToGoogleDrive(context) } catch (e: Exception) {}
                    } catch (e: Exception) {}
                }

                destinationName = newName
                destinationLatLng = newLatLng
                lastResolvedDestinationName = newName
                mapLibreInstance?.animateCamera(CameraUpdateFactory.newLatLngZoom(newLatLng, 16.5))

                showAdminCreatePoiDialog = false
                Toast.makeText(context, if (isAr) "تم حفظ المكان \"$newName\" ونشره بنجاح!" else "Place saved!", Toast.LENGTH_LONG).show()
            },
            onDelete = if (adminIsExistingPoi) {
                {
                    val targetName = adminNewPoiNameInput.trim()
                    com.albasel.tawsilaapp.util.EgyptPlaces.POIS.removeAll { poi -> poi.nameAr == targetName }
                    scope.launch(Dispatchers.IO) {
                        firebaseManager.deleteCustomPOI(targetName)
                        com.albasel.tawsilaapp.util.GoogleDriveHelper.recordEditedCodeId(context, targetName)
                        com.albasel.tawsilaapp.util.GoogleDriveHelper.syncMapPlacesToGoogleDrive(context)
                    }
                    if (destinationName == targetName) {
                        destinationMarker?.let { mapLibreInstance?.removeMarker(it) }
                        destinationMarker = null; destinationLatLng = null; destinationName = ""; calculatedDistance = 0.0; routeOptions = emptyList()
                    }
                    if (pickupName == targetName) {
                        pickupMarker?.let { mapLibreInstance?.removeMarker(it) }
                        pickupMarker = null; pickupLatLng = null; pickupName = ""
                    }
                    showAdminCreatePoiDialog = false
                    Toast.makeText(context, if (isAr) "تم حذف المكان بنجاح!" else "Place deleted!", Toast.LENGTH_SHORT).show()
                }
            } else null
        )
    }
}

@Immutable
data class RecentSearchItem(
    val title: String,
    val subtitle: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0
)

@Composable
fun ServicePillCard(
    icon: ImageVector,
    title: String,
    isSelected: Boolean,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (isSelected) Color(0xFFC5A059) else if (isDarkMode) Color(0xFF262626) else Color(0xFFF3F3F3)
    val iconColor = if (isSelected) Color.Black else Color(0xFFC5A059)
    val textColor = if (isSelected) Color.Black else if (isDarkMode) Color.White else Color.Black

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = bg,
        border = BorderStroke(1.dp, Color(0xFFC5A059).copy(alpha = 0.35f)),
        modifier = modifier.height(72.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun UberStyleSearchSheet(
    pickupName: String,
    destinationName: String,
    destinationLatLng: LatLng? = null,
    focusedField: String? = null,
    suggestions: List<com.albasel.tawsilaapp.data.repository.PlaceSuggestion> = emptyList(),
    recentSearches: List<RecentSearchItem> = emptyList(),
    onRecentSelect: (RecentSearchItem) -> Unit = {},
    onRecentDelete: (RecentSearchItem) -> Unit = {},
    onSuggestionSelect: (com.albasel.tawsilaapp.data.repository.PlaceSuggestion) -> Unit = {},
    onPickupChange: (String) -> Unit,
    onDestinationChange: (String) -> Unit,
    onFocusField: (String?) -> Unit,
    onMyLocationClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSwapClick: () -> Unit,
    onVoiceClick: (String) -> Unit,
    onLaterClick: () -> Unit = {},
    onSafarClick: () -> Unit = {},
    onCityClick: () -> Unit = {},
    onNearbyClick: () -> Unit = {},
    onCargoClick: () -> Unit = {},
    isCargoMode: Boolean = false,
    destFocusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    isDarkMode: Boolean,
    isAr: Boolean
) {
    val cardBg = if(isDarkMode) Color(0xFF262626) else Color(0xFFF3F3F3)
    val textColor = if(isDarkMode) Color.White else Color.Black
    val context = LocalContext.current
    var closedQueryByField by remember { mutableStateOf<Pair<String?, String>>("" to "") }
    
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth(0.96f).padding(vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = cardBg), modifier = Modifier.weight(1f)) {
                    Column(Modifier.padding(8.dp)) {
                        // Pickup
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if(pickupName.isNotEmpty()) Toast.makeText(context, pickupName, Toast.LENGTH_LONG).show() }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.DirectionsCar, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(2.dp))
                            TextField(
                                value = pickupName, 
                                onValueChange = {
                                    closedQueryByField = "" to ""
                                    if (focusedField != "PICKUP") onFocusField("PICKUP")
                                    onPickupChange(it)
                                }, 
                                placeholder = { Text(if(isAr) "من أين؟" else "Pickup", fontSize = 14.sp, modifier = Modifier.fillMaxWidth(), textAlign = if(isAr) TextAlign.Right else TextAlign.Left) }, 
                                modifier = Modifier.weight(1f).height(52.dp).onFocusChanged { 
                                    if (it.isFocused) {
                                        closedQueryByField = "" to ""
                                        if (focusedField != "PICKUP") onFocusField("PICKUP") 
                                    }
                                }, 
                                colors = TextFieldDefaults.colors(unfocusedContainerColor = Color.Transparent, focusedContainerColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, focusedTextColor = textColor, unfocusedTextColor = textColor),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, textAlign = if(isAr) TextAlign.Right else TextAlign.Left, textDirection = androidx.compose.ui.text.style.TextDirection.ContentOrRtl),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSearchClick() }),
                                trailingIcon = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (pickupName.isNotEmpty()) {
                                            IconButton(onClick = { onPickupChange("") }, modifier = Modifier.size(22.dp)) {
                                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                        IconButton(onClick = { onVoiceClick("PICKUP") }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Mic, null, tint = Color(0xFFC5A059), modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            )
                            IconButton(onMyLocationClick) { Icon(Icons.Default.MyLocation, null, tint = Color(0xFF2196F3)) }
                        }
                        
                        HorizontalDivider(modifier = Modifier.padding(start = 28.dp), color = Color.LightGray.copy(0.5f))
                        
                        // Destination
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if(destinationName.isNotEmpty()) Toast.makeText(context, destinationName, Toast.LENGTH_LONG).show() }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Place, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(2.dp))
                            TextField(
                                value = destinationName, 
                                onValueChange = {
                                    closedQueryByField = "" to ""
                                    if (focusedField != "DEST") onFocusField("DEST")
                                    onDestinationChange(it)
                                }, 
                                placeholder = { Text(if(isAr) "إلى أين؟" else "Where to?", fontSize = 14.sp, modifier = Modifier.fillMaxWidth(), textAlign = if(isAr) TextAlign.Right else TextAlign.Left) }, 
                                modifier = (if (destFocusRequester != null) Modifier.weight(1f).height(52.dp).focusRequester(destFocusRequester) else Modifier.weight(1f).height(52.dp)).onFocusChanged { 
                                    if (it.isFocused) {
                                        closedQueryByField = "" to ""
                                        if (focusedField != "DEST") onFocusField("DEST") 
                                    }
                                }, 
                                colors = TextFieldDefaults.colors(unfocusedContainerColor = Color.Transparent, focusedContainerColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, focusedTextColor = textColor, unfocusedTextColor = textColor),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, textAlign = if(isAr) TextAlign.Right else TextAlign.Left, textDirection = androidx.compose.ui.text.style.TextDirection.ContentOrRtl),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSearchClick() }),
                                trailingIcon = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (destinationName.isNotEmpty()) {
                                            IconButton(onClick = { onDestinationChange("") }, modifier = Modifier.size(22.dp)) {
                                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                        IconButton(onClick = { onVoiceClick("DEST") }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Mic, null, tint = Color(0xFFC5A059), modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            )
                            IconButton(onSwapClick) { Icon(Icons.Default.SwapVert, null, tint = Color.Gray) }
                        }
                        
                        // Fixed Height Container for Status Warning
                        Box(modifier = Modifier.fillMaxWidth().height(22.dp).padding(start = 28.dp, top = 2.dp)) {
                            if (destinationName.trim().length >= 2 && destinationLatLng == null && !destinationName.contains("موقع")) {
                                Text(
                                    if (isAr) "⚠️ مكان الوجهة غير مسجل على الخريطة (0.0 km)" else "⚠️ Destination not registered (0.0 km)",
                                    color = Color(0xFFD32F2F),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                // "لاحقاً" Button
                Surface(
                    onClick = onLaterClick,
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFC5A059),
                    modifier = Modifier.width(68.dp).height(105.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.CalendarMonth, null, tint = Color.Black, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(if(isAr) "لاحقاً" else "Later", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                    }
                }
            }

        }

        // Floating Overlay Popup for Search Suggestions (Height increased 1.5x to ~165dp)
        val currentActiveQuery = if (focusedField == "PICKUP") pickupName.trim() else destinationName.trim()
        val isClosedForCurrentQuery = (closedQueryByField.first == focusedField && closedQueryByField.second == currentActiveQuery)

        if (focusedField != null && suggestions.isNotEmpty() && !isClosedForCurrentQuery) {
            val solidBgColor = if (isDarkMode) Color(0xFF1D1D1D) else Color(0xFFFFFFFF)
            androidx.compose.ui.window.Popup(
                alignment = Alignment.TopCenter,
                offset = androidx.compose.ui.unit.IntOffset(0, 275),
                onDismissRequest = {
                    closedQueryByField = focusedField to currentActiveQuery
                    onFocusField(null)
                },
                properties = androidx.compose.ui.window.PopupProperties(
                    focusable = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true, // Tap anywhere outside dismisses suggestions card
                    clippingEnabled = false
                )
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = solidBgColor,
                    shadowElevation = 24.dp,
                    tonalElevation = 0.dp,
                    modifier = Modifier
                        .fillMaxWidth(0.96f)
                        .heightIn(max = 205.dp) // Height increased by 1 cm (~40dp) as requested
                        .background(solidBgColor, RoundedCornerShape(16.dp))
                        .border(1.5.dp, Color(0xFFC5A059), RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(solidBgColor)
                            .padding(vertical = 2.dp)
                    ) {
                        // Compact Header containing ONLY 'X' Close Button matching its exact height
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            IconButton(
                                onClick = {
                                    closedQueryByField = focusedField to currentActiveQuery
                                    onFocusField(null)
                                },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }

                        // Scrollable List of Suggestions
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 205.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            suggestions.forEachIndexed { idx, suggestion ->
                                Card(
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = cardBg),
                                    border = BorderStroke(1.dp, Color(0xFFC5A059).copy(alpha = 0.35f)),
                                    elevation = CardDefaults.cardElevation(2.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            closedQueryByField = focusedField to currentActiveQuery
                                            onSuggestionSelect(suggestion)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Icon(Icons.Default.AccessTime, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                            Text(
                                                text = suggestion.name,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 15.sp,
                                                color = textColor,
                                                textAlign = TextAlign.Right,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (suggestion.address.isNotBlank() && suggestion.address != "مصر") {
                                                Text(
                                                    text = suggestion.address,
                                                    fontSize = 12.sp,
                                                    color = Color.Gray,
                                                    textAlign = TextAlign.Right,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Icon(Icons.Default.Place, contentDescription = null, tint = Color(0xFFC5A059), modifier = Modifier.size(22.dp))
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

@Composable
fun RideOptionButton(
    icon: ImageVector? = null,
    painter: androidx.compose.ui.graphics.painter.Painter? = null,
    label: String,
    isSelected: Boolean,
    isDarkMode: Boolean,
    activeColor: Color = Color(0xFFC5A059),
    useOriginalColor: Boolean = false,
    noBorder: Boolean = false,
    height: androidx.compose.ui.unit.Dp = 46.dp,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(height)
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
            .clickable(
                enabled = enabled,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(24.dp),
        color = if (isSelected && !noBorder) activeColor else if (isDarkMode) Color(0xFF262626) else Color.White,
        border = if (!isSelected && !noBorder) BorderStroke(1.dp, if (isDarkMode) Color.DarkGray else Color.LightGray.copy(0.3f)) else null,
        shadowElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (isSelected && noBorder) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.88f) // Further shortened diameter (approx 1/4 mm less than 0.91f)
                        .background(activeColor.copy(alpha = 0.6f), CircleShape)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = if (noBorder && label.isEmpty()) Modifier.fillMaxSize() else Modifier.padding(horizontal = 8.dp)
            ) {
                if (painter != null) {
                    Image(
                        painter = painter,
                        contentDescription = null,
                        modifier = if (noBorder && label.isEmpty()) Modifier.fillMaxSize() else Modifier.size(18.dp),
                        contentScale = if (noBorder && label.isEmpty()) ContentScale.FillBounds else ContentScale.Fit,
                        colorFilter = if (useOriginalColor) null else androidx.compose.ui.graphics.ColorFilter.tint(if (isSelected && !noBorder) Color.Black else activeColor)
                    )
                } else if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected && !noBorder) Color.Black else activeColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (label.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = label,
                        color = if (isSelected && !noBorder) Color.Black else activeColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun VehicleDesignCard(
    vehicle: VehicleInfoData,
    isSelected: Boolean,
    isLocked: Boolean,
    isDarkMode: Boolean,
    isCargoMode: Boolean,
    onClick: () -> Unit
) {
    Box(modifier = Modifier.width(185.dp).height(160.dp).padding(2.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    enabled = !isLocked,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
        ) {
            // Info Row (Top Left: Capacity/Weight | Top Center: Name | Top Right: Selection)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp, start = 8.dp, end = 8.dp)
            ) {
                // Left: Capacity or Weight
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    if (isCargoMode) {
                        Text(
                            text = "${vehicle.maxCargoWeightKg.toInt()}",
                            fontSize = 12.sp,
                            color = if(isSelected) Color(0xFFC5A059) else Color.Gray,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(2.dp))
                        Icon(
                            Icons.Default.Scale,
                            null,
                            modifier = Modifier.size(14.dp),
                            tint = if(isSelected) Color(0xFFC5A059) else Color.Gray
                        )
                    } else {
                        Text(
                            text = "${vehicle.capacity}",
                            fontSize = 12.sp,
                            color = if(isSelected) Color(0xFFC5A059) else Color.Gray,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(2.dp))
                        Icon(
                            Icons.Default.Person,
                            null,
                            modifier = Modifier.size(14.dp),
                            tint = if(isSelected) Color(0xFFC5A059) else Color.Gray
                        )
                    }
                }

                // Name (Top Right above vehicle in Cargo Mode)
                Text(
                    text = vehicle.name,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = if(isSelected) Color(0xFFC5A059) else (if(isDarkMode) Color.White else Color.Black),
                    modifier = Modifier.align(if(isCargoMode) Alignment.CenterEnd else Alignment.Center)
                )
            }

            // Center: Image (1.5x Enlarged 175dp)
            Image(
                painter = painterResource(vehicle.iconRes),
                contentDescription = null,
                modifier = Modifier
                    .size(175.dp)
                    .align(Alignment.Center)
                    .padding(top = 18.dp),
                alpha = if(isLocked && !isSelected) 0.4f else 1f
            )
        }
    }
}

@Composable
fun InfoActionIcon(icon: ImageVector, label: String, sub: String, isDarkMode: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, null, modifier = Modifier.size(13.dp), tint = Color(0xFFC5A059))
            Spacer(Modifier.width(3.dp))
            Text(label, fontSize = 9.sp, color = (if(isDarkMode) Color.White else Color.Black).copy(alpha = 0.6f), maxLines = 1)
        }
        Spacer(Modifier.height(2.dp))
        Text(sub, fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFFC5A059), textAlign = TextAlign.Center, maxLines = 1)
    }
}



@Composable
fun TripOptionButton(
    icon: ImageVector? = null,
    label: String,
    isSelected: Boolean,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier,
    activeColor: Color = Color(0xFFC5A059),
    painter: androidx.compose.ui.graphics.painter.Painter? = null,
    useOriginalColor: Boolean = false,
    noBorder: Boolean = false,
    height: androidx.compose.ui.unit.Dp = 34.dp,
    onClick: () -> Unit
) {
    // Determine content and background colors
    val isInternalHighlight = useOriginalColor && noBorder
    val contentColor = if(isSelected && !isInternalHighlight) activeColor else activeColor
    val bgColor = if(noBorder) Color.Transparent else (if(isDarkMode) Color(0xFF262626) else androidx.compose.ui.graphics.Color.White)
    val borderColor = if(noBorder) Color.Transparent else if(isSelected) activeColor else (if(isDarkMode) androidx.compose.ui.graphics.Color.DarkGray else androidx.compose.ui.graphics.Color.LightGray.copy(0.3f))
    val borderThickness = if(isSelected) 1.8.dp else 1.dp // Rule: Increased selection border by ~0.8dp (0.25mm)

    Box(
        modifier = modifier
            .height(height)
            .clip(if(noBorder) CircleShape else RoundedCornerShape(12.dp))
            .background(bgColor)
            .then(if(noBorder) Modifier else Modifier.border(borderThickness, borderColor, RoundedCornerShape(12.dp)))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxHeight().padding(horizontal = 4.dp)
        ) {
            if (painter != null) {
                if (useOriginalColor) {
                    // Internal selection highlight for gender icons - strictly contained
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxHeight()) {
                        if (isSelected) {
                            Surface(
                                modifier = Modifier
                                    .width(58.dp) 
                                    .height(if(noBorder) 52.dp else 36.dp)
                                    .padding(top = 4.dp)
                                    .align(Alignment.Center),
                                shape = CircleShape,
                                color = activeColor.copy(alpha = 0.6f)
                            ) {}
                        }
                        Image(
                            painter = painter, 
                            contentDescription = null, 
                            modifier = Modifier.size(if(noBorder) 42.dp else 28.dp).graphicsLayer {
                                if (noBorder) {
                                    scaleX = 1.3f
                                    scaleY = 1.3f
                                }
                            }, 
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                    }
                } else {
                    Icon(painter, null, tint = contentColor, modifier = Modifier.size(if(noBorder) 32.dp else 16.dp))
                }
            } else if (icon != null) {
                Icon(icon, null, tint = contentColor, modifier = Modifier.size(16.dp))
            }

            if (label.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                Text(label, color = contentColor, fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 1) 
            }
        }
    }
}

@Composable
fun OrderSummaryItem(
    icon: ImageVector? = null,
    text: String,
    iconColor: Color,
    isBold: Boolean = false,
    fontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
    painter: androidx.compose.ui.graphics.painter.Painter? = null,
    textColor: Color = Color.Black
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (painter != null) {
            Icon(painter, null, tint = iconColor, modifier = Modifier.size(16.dp))
        } else if (icon != null) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(text, fontSize = fontSize, fontWeight = if(isBold) FontWeight.Bold else FontWeight.Normal, color = textColor)
    }
}

@Composable
private fun GenderIconToggle(isMale: Boolean, isSelected: Boolean, isDarkMode: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(54.dp),
        shape = CircleShape,
        color = Color.Transparent,
        border = null,
        shadowElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(if (isMale) R.drawable.male_icon else R.drawable.female_icon),
                contentDescription = null,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

@Composable
fun AdminPoiEditDialogComposed(
    adminEditIsPickupTarget: Boolean?,
    adminEditNameInput: String,
    onNameChange: (String) -> Unit,
    adminEditLatInput: String,
    onLatChange: (String) -> Unit,
    adminEditLngInput: String,
    onLngChange: (String) -> Unit,
    adminEditDistrictInput: String,
    onDistrictChange: (String) -> Unit,
    adminSelectedCategory: String,
    onCategoryChange: (String) -> Unit,
    adminEditOriginalName: String,
    pickupName: String,
    destinationName: String,
    markerCategoryOptions: List<Pair<String, String>>,
    isAr: Boolean,
    onDismiss: () -> Unit,
    onSave: (newName: String, newLat: Double, newLng: Double, category: String, district: String) -> Unit,
    onDelete: () -> Unit
) {
    val targetTitle = if (adminEditIsPickupTarget == true) (if (isAr) "تعديل موقع الانطلاق (للأدمن)" else "Edit Pickup Location (Admin)") else (if (isAr) "تعديل موقع الوجهة (للأدمن)" else "Edit Destination Location (Admin)")
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.EditLocation, null, tint = Color(0xFFC5A059), modifier = Modifier.size(36.dp)) },
        title = { Text(targetTitle, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = adminEditNameInput,
                    onValueChange = onNameChange,
                    label = { Text(if (isAr) "اسم المكان" else "Place Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = adminEditLatInput,
                    onValueChange = { input -> onLatChange(input.filter { c -> c.isDigit() || c == '.' || c == '-' }) },
                    label = { Text(if (isAr) "خط العرض (Latitude)" else "Latitude") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = adminEditLngInput,
                    onValueChange = { input -> onLngChange(input.filter { c -> c.isDigit() || c == '.' || c == '-' }) },
                    label = { Text(if (isAr) "خط الطول (Longitude)" else "Longitude") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = adminEditDistrictInput,
                    onValueChange = onDistrictChange,
                    label = { Text(if (isAr) "المحافظة / المدينة / الحي" else "Governorate / City / District") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(4.dp))
                Text(if (isAr) "اختر شكل/رمز العلامة على الخريطة:" else "Select Marker Style:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC5A059))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    items(markerCategoryOptions) { (catKey, catLabel) ->
                        val isSelected = adminSelectedCategory == catKey
                        FilterChip(
                            selected = isSelected,
                            onClick = { onCategoryChange(catKey) },
                            label = { Text(catLabel, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFC5A059),
                                selectedLabelColor = Color.Black
                            )
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onDelete,
                    border = BorderStroke(1.dp, Color.Red),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, null, tint = Color.Red, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isAr) "🗑️ حذف هذا المكان من الخريطة والبيانات" else "🗑️ Delete Location", fontWeight = FontWeight.Bold, color = Color.Red)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newLat = adminEditLatInput.toDoubleOrNull() ?: 0.0
                    val newLng = adminEditLngInput.toDoubleOrNull() ?: 0.0
                    val newName = adminEditNameInput.trim().ifEmpty { "موقع معدل" }
                    val district = adminEditDistrictInput.trim().ifEmpty { "دمياط الجديدة" }
                    onSave(newName, newLat, newLng, adminSelectedCategory, district)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC5A059), contentColor = Color.Black)
            ) { Text(if (isAr) "حفظ التعديلات" else "Save Changes", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (isAr) "إلغاء" else "Cancel") }
        }
    )
}

@Composable
fun AdminPoiCreateDialogComposed(
    adminIsExistingPoi: Boolean,
    adminNewPoiNameInput: String,
    onNameChange: (String) -> Unit,
    adminNewPoiLatInput: String,
    onLatChange: (String) -> Unit,
    adminNewPoiLngInput: String,
    onLngChange: (String) -> Unit,
    adminNewPoiDistrictInput: String,
    onDistrictChange: (String) -> Unit,
    adminSelectedCategory: String,
    onCategoryChange: (String) -> Unit,
    markerCategoryOptions: List<Pair<String, String>>,
    isAr: Boolean,
    onDismiss: () -> Unit,
    onSave: (newName: String, newLat: Double, newLng: Double, category: String, district: String) -> Unit,
    onDelete: (() -> Unit)?
) {
    val dialogTitle = if (adminIsExistingPoi) (if (isAr) "تعديل / حذف مكان مسجل (للأدمن)" else "Edit / Delete Place (Admin)") else (if (isAr) "إنشاء مكان جديد على الخريطة (للأدمن)" else "Create New Place (Admin)")
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (adminIsExistingPoi) Icons.Default.EditLocation else Icons.Default.AddLocation, null, tint = Color(0xFFC5A059), modifier = Modifier.size(36.dp)) },
        title = { Text(dialogTitle, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (adminIsExistingPoi) (if (isAr) "تم جلب بيانات المكان المسجل وإحداثياته:" else "Registered place details & coordinates:") else (if (isAr) "تم تحديد الإحداثيات الدقيقة للنقطة المضغوطة:" else "Exact coordinates prefilled from map tap:"),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                OutlinedTextField(
                    value = adminNewPoiNameInput,
                    onValueChange = onNameChange,
                    label = { Text(if (isAr) "اسم المكان" else "Place Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = adminNewPoiLatInput,
                    onValueChange = { input -> onLatChange(input.filter { c -> c.isDigit() || c == '.' || c == '-' }) },
                    label = { Text(if (isAr) "خط العرض (Latitude)" else "Latitude") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = adminNewPoiLngInput,
                    onValueChange = { input -> onLngChange(input.filter { c -> c.isDigit() || c == '.' || c == '-' }) },
                    label = { Text(if (isAr) "خط الطول (Longitude)" else "Longitude") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = adminNewPoiDistrictInput,
                    onValueChange = onDistrictChange,
                    label = { Text(if (isAr) "المحافظة / المدينة / الحي" else "Governorate / City / District") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(4.dp))
                Text(if (isAr) "اختر شكل/رمز العلامة على الخريطة:" else "Select Marker Style:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC5A059))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    items(markerCategoryOptions) { (catKey, catLabel) ->
                        val isSelected = adminSelectedCategory == catKey
                        FilterChip(
                            selected = isSelected,
                            onClick = { onCategoryChange(catKey) },
                            label = { Text(catLabel, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFC5A059),
                                selectedLabelColor = Color.Black
                            )
                        )
                    }
                }

                if (onDelete != null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onDelete,
                        border = BorderStroke(1.dp, Color.Red),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, null, tint = Color.Red, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (isAr) "🗑️ حذف هذا المكان من الخريطة للجميع" else "🗑️ Delete Place", fontWeight = FontWeight.Bold, color = Color.Red)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newLat = adminNewPoiLatInput.toDoubleOrNull() ?: 0.0
                    val newLng = adminNewPoiLngInput.toDoubleOrNull() ?: 0.0
                    val newName = adminNewPoiNameInput.trim().ifEmpty { "مكان جديد" }
                    val district = adminNewPoiDistrictInput.trim().ifEmpty { "دمياط الجديدة" }
                    onSave(newName, newLat, newLng, adminSelectedCategory, district)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC5A059), contentColor = Color.Black)
            ) { Text(if (isAr) "حفظ وتحديث المكان" else "Save Place", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (isAr) "إلغاء" else "Cancel") }
        }
    )
}

