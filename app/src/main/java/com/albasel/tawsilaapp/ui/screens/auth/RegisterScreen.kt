package com.albasel.tawsilaapp.ui.screens.auth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.albasel.tawsilaapp.data.firebase.FirebaseManager
import com.albasel.tawsilaapp.util.VehicleUtils.getBaseIconName
import com.albasel.tawsilaapp.data.model.User
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream

data class VehicleInfoData(val name: String, val iconRes: Int, val capacity: Int, val maxCargoWeightKg: Double, val color: Color, val basePrice: Double)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    firebaseManager: FirebaseManager, 
    language: String, 
    onRegisterSuccess: (User) -> Unit, 
    onBackToLogin: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isAr = language == "AR"

    var name by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf("USER") }
    var vehicleType by rememberSaveable { mutableStateOf("") }
    var vehiclePlate by rememberSaveable { mutableStateOf("") }
    var vehicleColor by rememberSaveable { mutableStateOf("") }
    var busCapacity by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf("MALE") }
    
    var canDeliver by rememberSaveable { mutableStateOf(true) }
    var canCargo by rememberSaveable { mutableStateOf(false) }
    var canLoad by rememberSaveable { mutableStateOf(false) }
    var canUnload by rememberSaveable { mutableStateOf(false) }

    var province by rememberSaveable { mutableStateOf("") }
    var provinceExpanded by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    var usernameStatus by remember { mutableStateOf<String?>(null) }
    var isCheckingUsername by remember { mutableStateOf(false) }

    var phoneStatus by remember { mutableStateOf<String?>(null) }
    var isCheckingPhone by remember { mutableStateOf(false) }

    LaunchedEffect(username) {
        if (username.length >= 3) {
            isCheckingUsername = true
            delay(500)
            val taken = firebaseManager.isUsernameTaken(username)
            usernameStatus = if (taken) "TAKEN" else "AVAILABLE"
            isCheckingUsername = false
        } else { usernameStatus = null }
    }

    LaunchedEffect(phone) {
        if (phone.length == 11 && phone.startsWith("01")) {
            isCheckingPhone = true
            delay(300)
            val taken = firebaseManager.isPhoneTaken(phone)
            phoneStatus = if (taken) "TAKEN" else "AVAILABLE"
            isCheckingPhone = false
        } else { 
            phoneStatus = null
            isCheckingPhone = false
        }
    }

    var vehiclePrices by remember { mutableStateOf<Map<String, com.albasel.tawsilaapp.data.model.VehiclePrice>>(emptyMap()) }
    LaunchedEffect(Unit) {
        vehiclePrices = firebaseManager.getVehiclePricesDetail()
    }

    val isDarkMode = isSystemInDarkTheme()
    val vehicleList = remember(language, vehiclePrices, isDarkMode) {
        val suffix = if (isDarkMode) "2" else "1"
        fun getRes(name: String): Int {
            val base = getBaseIconName(name)
            return context.resources.getIdentifier("ic_$base$suffix", "drawable", context.packageName).let {
                if (it != 0) it else com.albasel.tawsilaapp.R.drawable.ic_taxi1
            }
        }

        val staticList = listOf(
            VehicleInfoData(if(isAr) "تاكسي" else "Taxi", getRes("taxi"), 4, 250.0, Color(0xFFFFD600), 8.0),
            VehicleInfoData(if(isAr) "فان" else "Van", getRes("van"), 7, 500.0, Color(0xFFFF9800), 12.0),
            VehicleInfoData(if(isAr) "ملاكي" else "Private", getRes("privatecar"), 4, 300.0, Color(0xFF2196F3), 10.0),
            VehicleInfoData(if(isAr) "توكتوك" else "Tuk-tuk", getRes("tuk_tuk"), 3, 150.0, Color(0xFFFFC107), 5.0),
            VehicleInfoData(if(isAr) "تريسكل" else "Tricycle", getRes("tricycle"), 2, 300.0, Color(0xFFFF9800), 8.0),
            VehicleInfoData(if(isAr) "باص" else "Bus", getRes("bus"), 50, 0.0, Color(0xFF2196F3), 15.0),
            VehicleInfoData(if(isAr) "ميكروباص" else "Microbus", getRes("microbus"), 14, 0.0, Color(0xFF2196F3), 15.0),
            VehicleInfoData(if(isAr) "موتوسيكل" else "Motorcycle", getRes("moto"), 1, 50.0, Color(0xFF4CAF50), 5.0),
            VehicleInfoData(if(isAr) "دراجة" else "Bike", getRes("bike"), 1, 25.0, Color(0xFF8BC34A), 3.0),
            VehicleInfoData(if(isAr) "ربع نقل" else "Quarter Truck", getRes("quartertruck"), 2, 1000.0, Color(0xFF795548), 15.0),
            VehicleInfoData(if(isAr) "نصف نقل" else "Half Truck", getRes("halftruck"), 3, 3000.0, Color(0xFF607D8B), 20.0),
            VehicleInfoData(if(isAr) "نقل" else "Truck", getRes("truck"), 3, 10000.0, Color(0xFF212121), 30.0)
        )
        staticList.filter { vehiclePrices[it.name]?.isVisible ?: true }
    }

    val provinces = listOf("القاهرة", "الإسكندرية", "الجيزة", "الدقهلية", "البحر الأحمر", "البحيرة", "الفيوم", "الغربية", "الإسماعيلية", "المنوفية", "المنيا", "القليوبية", "الوادي الجديد", "الشرقية", "السويس", "أسوان", "أسيوط", "بني سويف", "بور سعيد", "دمياط", "جنوب سيناء", "كفر الشيخ", "مطروح", "الأقصر", "قنا", "شمال سيناء", "سوهاج")

    var idCardUri by remember { mutableStateOf<Uri?>(null) }
    var licenseFrontUri by remember { mutableStateOf<Uri?>(null) }
    var licenseBackUri by remember { mutableStateOf<Uri?>(null) }
    var profileUri by remember { mutableStateOf<Uri?>(null) }
    var profileImageBytes by rememberSaveable { mutableStateOf<ByteArray?>(null) }

    var acceptedTerms by rememberSaveable { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var termsText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        scope.launch {
            termsText = firebaseManager.database.child("settings").child("terms_text").get().await().value?.toString() ?: ""
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            profileImageBytes = outputStream.toByteArray()
        }
    }
    val profilePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> profileUri = uri }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) cameraLauncher.launch(null) else Toast.makeText(context, if(isAr) "يجب الموافقة على صلاحية الكاميرا" else "Camera permission required", Toast.LENGTH_SHORT).show()
    }

    val idCardLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> idCardUri = uri }
    val licenseFrontLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> licenseFrontUri = uri }
    val licenseBackLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> licenseBackUri = uri }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(modifier = Modifier.size(90.dp), shape = RoundedCornerShape(24.dp), color = Color(0xFFC5A059)) {
                Box(contentAlignment = Alignment.Center) { Text("T", color = Color.Black, fontSize = 50.sp, fontWeight = FontWeight.Black) }
            }
            Spacer(Modifier.height(16.dp))
            Text(if(isAr) "إنشاء حساب جديد" else "Create Account", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFC5A059))

            Spacer(modifier = Modifier.height(32.dp))

            RegisterTextField(name, { name = it }, if(isAr) "الاسم بالكامل" else "Full Name", Icons.Default.Person)
            
            Column {
                RegisterTextField(username, { username = it.replace(" ", "").lowercase() }, if(isAr) "اسم المستخدم" else "Username", Icons.Default.AccountCircle)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)) {
                    if (isCheckingUsername) { CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = Color(0xFFC5A059)); Spacer(Modifier.width(8.dp)) }
                    Text(text = when { usernameStatus == "TAKEN" -> if(isAr) "مسجل بالفعل" else "Taken"; usernameStatus == "AVAILABLE" -> if(isAr) "متاح" else "Available"; else -> if(isAr) "* إنجليزي فقط بدون مسافات" else "* English only, no spaces" }, fontSize = 11.sp, color = when(usernameStatus) { "TAKEN" -> Color.Red; "AVAILABLE" -> Color(0xFF2E7D32); else -> Color.LightGray.copy(0.7f) })
                }
            }

            Column {
                RegisterTextField(
                    phone, 
                    { input -> 
                        val filtered = input.replace(" ", "")
                        if (filtered.all { c -> c.isDigit() } && filtered.length <= 11) {
                            phone = filtered
                        } 
                    }, 
                    if(isAr) "رقم الهاتف" else "Phone Number", 
                    Icons.Default.Phone, 
                    KeyboardType.Number
                )
                
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)) {
                    if (isCheckingPhone) { 
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = Color(0xFFC5A059))
                        Spacer(Modifier.width(8.dp)) 
                    }
                    
                    val textToShow = when {
                        phone.isNotEmpty() && (!phone.startsWith("01") || phone.length < 11) -> 
                            if (isAr) "رقم الهاتف غير صحيح" else "Invalid phone number"
                        phoneStatus == "TAKEN" -> 
                            if (isAr) "⚠️ رقم الهاتف مسجل مسبقاً!" else "⚠️ Phone number already registered"
                        phoneStatus == "AVAILABLE" -> 
                            if (isAr) "✅ رقم الهاتف متاح وغير مسجل" else "✅ Phone number available"
                        else -> ""
                    }
                    
                    val textColor = when {
                        phone.isNotEmpty() && (!phone.startsWith("01") || phone.length < 11) -> Color.Red
                        phoneStatus == "TAKEN" -> Color.Red
                        phoneStatus == "AVAILABLE" -> Color(0xFF2E7D32)
                        else -> Color.Transparent
                    }
                    
                    if (textToShow.isNotEmpty()) {
                        Text(text = textToShow, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textColor)
                    }
                }
            }

            RegisterTextField(password, { password = it.replace(" ", "") }, if(isAr) "كلمة المرور" else "Password", Icons.Default.Lock, isPassword = true)
            RegisterTextField(confirmPassword, { confirmPassword = it.replace(" ", "") }, if(isAr) "تأكيد كلمة المرور" else "Confirm", Icons.Default.Lock, isPassword = true)

            if (role == "USER") {
                DocumentUploadButton(if(isAr) "صورة شخصية (اختياري)" else "Profile Photo (Optional)", profileUri != null) { profilePicker.launch("image/*") }
            }

            Spacer(modifier = Modifier.height(16.dp))
            ExposedDropdownMenuBox(expanded = provinceExpanded, onExpandedChange = { provinceExpanded = it }, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(value = province, onValueChange = {}, readOnly = true, label = { Text(if(isAr) "المحافظة" else "Province", color = Color(0xFFC5A059), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }, modifier = Modifier.fillMaxWidth().menuAnchor(), leadingIcon = { Icon(Icons.Default.LocationCity, null, tint = Color(0xFFC5A059)) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(provinceExpanded) }, shape = RoundedCornerShape(16.dp), textStyle = TextStyle(textAlign = TextAlign.Center, color = Color.White, fontSize = 16.sp), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFFC5A059), unfocusedBorderColor = Color.Gray))
                ExposedDropdownMenu(
                    expanded = provinceExpanded, 
                    onDismissRequest = { provinceExpanded = false }, 
                    modifier = Modifier
                        .background(Color.Black)
                        .heightIn(max = 400.dp)
                ) { 
                    provinces.forEach { p -> 
                        DropdownMenuItem(
                            text = { Text(p, color = Color(0xFFC5A059), fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }, 
                            onClick = { province = p; provinceExpanded = false },
                            modifier = Modifier.fillMaxWidth().background(Color.Transparent)
                        )
                        if (p != provinces.last()) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = Color.Gray.copy(alpha = 0.3f))
                    } 
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RoleSelectionButton(if(isAr) "عميل" else "User", role == "USER", Modifier.weight(1f)) { role = "USER" }
                RoleSelectionButton(if(isAr) "سائق" else "Driver", role == "DRIVER", Modifier.weight(1f)) { role = "DRIVER" }
            }

            if (role == "DRIVER") {
                Spacer(Modifier.height(24.dp))
                LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(vehicleList) { v ->
                        val isSelected = vehicleType == v.name
                        Surface(
                            onClick = { 
                                vehicleType = v.name
                                if (!v.name.contains("باص") && !v.name.contains("Bus")) {
                                    busCapacity = ""
                                }
                            },
                            modifier = Modifier.size(100.dp),
                            shape = RoundedCornerShape(24.dp),
                            color = if (isSelected) Color(0xFFC5A059) else Color.DarkGray.copy(alpha = 0.5f),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFC5A059)) else null,
                            shadowElevation = if (isSelected) 8.dp else 2.dp
                        ) {
                            Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Image(painter = painterResource(id = v.iconRes), contentDescription = null, modifier = Modifier.size(50.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                                Spacer(Modifier.height(10.dp))
                                Text(v.name, color = if(isSelected) Color.Black else Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }

                // Gender Selection
                Spacer(Modifier.height(16.dp))
                Text(if(isAr) "النوع:" else "Gender:", color = Color.White, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val genderOptions = listOf(
                        "MALE" to (if(isAr) "ذكر" else "Male"),
                        "FEMALE" to (if(isAr) "أنثى" else "Female")
                    )
                    genderOptions.forEach { (key, label) ->
                        val isSelected = gender == key
                        Surface(
                            onClick = { gender = key },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = if(isSelected) Color(0xFFC5A059) else Color.DarkGray.copy(alpha = 0.5f),
                            border = if(isSelected) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFC5A059)) else null
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(label, color = if(isSelected) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                
                val isBike = vehicleType.contains("دراجة") || vehicleType.lowercase().contains("bike")
                
                if (!isBike) {
                    RegisterTextField(vehiclePlate, { vehiclePlate = it }, if(isAr) "رقم لوحة المركبة" else "Vehicle Plate Number", Icons.Default.DirectionsCar)
                }
                
                var colorExpanded by remember { mutableStateOf(false) }
                val colorMap = listOf(
                    "أبيض" to Color.White, "أسود" to Color.Black, "رمادي" to Color.Gray, "فضي" to Color(0xFFC0C0C0), "أحمر" to Color.Red, "أزرق" to Color.Blue, "كحلي" to Color(0xFF000080), "بني" to Color(0xFF8B4513), "بيج" to Color(0xFFF5F5DC), "أخضر" to Color.Green, "برتقالي" to Color(0xFFFFA500), "بنفسجي" to Color(0xFF800080), "ذهبي" to Color(0xFFFFD700), "وردي" to Color(0xFFFFC0CB), "سماوي" to Color(0xFF87CEEB), "White" to Color.White, "Black" to Color.Black, "Gray" to Color.Gray, "Silver" to Color(0xFFC0C0C0), "Red" to Color.Red, "Blue" to Color.Blue, "Navy" to Color(0xFF000080), "Brown" to Color(0xFF8B4513), "Beige" to Color(0xFFF5F5DC), "Green" to Color.Green, "Orange" to Color(0xFFFFA500), "Purple" to Color(0xFF800080), "Gold" to Color(0xFFFFD700), "Pink" to Color(0xFFFFC0CB), "Cyan" to Color(0xFF87CEEB)
                )
                
                val colors = if(isAr) listOf("أبيض", "أسود", "رمادي", "فضي", "أحمر", "أزرق", "كحلي", "بني", "بيج", "أخضر", "برتقالي", "بنفسجي", "ذهبي", "وردي", "سماوي")
                             else listOf("White", "Black", "Gray", "Silver", "Red", "Blue", "Navy", "Brown", "Beige", "Green", "Orange", "Purple", "Gold", "Pink", "Cyan")
                
                ExposedDropdownMenuBox(
                    expanded = colorExpanded,
                    onExpandedChange = { colorExpanded = it },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    OutlinedTextField(
                        value = vehicleColor, onValueChange = {}, readOnly = true,
                        label = { Text(if(isAr) "لون المركبة" else "Vehicle Color", color = Color(0xFFC5A059), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        leadingIcon = { Icon(Icons.Default.Palette, null, tint = Color(0xFFC5A059)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(colorExpanded) },
                        shape = RoundedCornerShape(16.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, textAlign = TextAlign.Center, fontSize = 16.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFC5A059),
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = colorExpanded,
                        onDismissRequest = { colorExpanded = false },
                        modifier = Modifier.background(Color.Black)
                    ) {
                        colors.forEach { c ->
                            val cColor = colorMap.find { it.first == c }?.second ?: Color.White
                            
                            val isWhite = c == "أبيض" || c == "White"
                            
                            DropdownMenuItem(
                                text = { 
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFFC5A059))
                                            .then(if(isWhite) Modifier.border(1.dp, Color.Black.copy(0.3f), RoundedCornerShape(12.dp)) else Modifier)
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Box(
                                            Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(cColor)
                                                .border(1.dp, Color.Black.copy(0.3f), CircleShape)
                                        )
                                        Spacer(Modifier.width(16.dp))
                                        Text(c, color = Color.Black, fontWeight = FontWeight.Black, fontSize = 16.sp)
                                    }
                                }, 
                                onClick = { vehicleColor = c; colorExpanded = false },
                                modifier = Modifier.fillMaxWidth().background(Color.Transparent)
                            )
                        }
                    }
                }

                val isCargoOnly = listOf("دراجة", "bike", "ربع نقل", "quarter truck", "نصف نقل", "half truck", "نقل", "truck")
                    .any { vehicleType.lowercase().contains(it) }

                if (isCargoOnly) {
                    // Force Cargo-only for restricted vehicles
                    canDeliver = false
                    canCargo = true
                }

                if ((vehicleType.contains("باص") || vehicleType.contains("Bus")) && !vehicleType.contains("ميكرو") && !vehicleType.contains("Micro")) {
                    Spacer(Modifier.height(16.dp))
                    var capExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = capExpanded,
                        onExpandedChange = { capExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = busCapacity,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(if (isAr) "حمولة الباص (ركاب)" else "Bus Capacity") },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            leadingIcon = { Icon(Icons.Default.Groups, null, tint = Color(0xFFC5A059)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(capExpanded) },
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFC5A059), unfocusedBorderColor = Color.Gray
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = capExpanded,
                            onDismissRequest = { capExpanded = false },
                            modifier = Modifier.background(Color.Black).heightIn(max = 300.dp)
                        ) {
                            (1..50).forEach { i ->
                                DropdownMenuItem(
                                    text = { Text(i.toString(), color = Color(0xFFC5A059), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                                    onClick = {
                                        busCapacity = i.toString()
                                        capExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Text(
                        text = if (isAr) "أقصى عدد أفراد 50" else "Max 50 passengers",
                        fontSize = 12.sp,
                        color = Color(0xFFC5A059),
                        modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text(if(isAr) "خيارات الخدمات:" else "Service Options:", fontWeight = FontWeight.Bold, color = Color.White)
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (!isCargoOnly) {
                        Checkbox(checked = canDeliver, onCheckedChange = { canDeliver = it }, colors = CheckboxDefaults.colors(checkedColor = Color(0xFFC5A059)))
                        Text(if(isAr) "توصيل (ركاب)" else "Delivery (Rides)", color = Color.White, fontSize = 12.sp)
                        Spacer(Modifier.width(16.dp))
                    }
                    Checkbox(checked = canCargo, onCheckedChange = { if(!isCargoOnly) canCargo = it }, enabled = !isCargoOnly, colors = CheckboxDefaults.colors(checkedColor = Color(0xFFC5A059)))
                    Text(if(isAr) "نقل بضائع" else "Cargo", color = Color.White, fontSize = 12.sp)
                }

                Spacer(Modifier.height(24.dp))
                DocumentUploadButton(
                    if(isAr) "الصورة الشخصية" else "Profile Photo", 
                    profileUri != null
                ) { profilePicker.launch("image/*") }
                
                DocumentUploadButton(if(isAr) "صورة البطاقة" else "ID Card", idCardUri != null) { idCardLauncher.launch("image/*") }
                
                if (!isBike) {
                    DocumentUploadButton(if(isAr) "الرخصة (وجه)" else "License (F)", licenseFrontUri != null) { licenseFrontLauncher.launch("image/*") }
                    DocumentUploadButton(if(isAr) "صورة واجهة المركبة مع اظهار لوحة المركبة" else "Vehicle Front View with License Plate", licenseBackUri != null) { licenseBackLauncher.launch("image/*") }
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { acceptedTerms = !acceptedTerms }) {
                Checkbox(checked = acceptedTerms, onCheckedChange = { acceptedTerms = it }, colors = CheckboxDefaults.colors(checkedColor = Color(0xFFC5A059)))
                Text(if(isAr) "أوافق على الشروط والأحكام" else "I accept the Terms and Conditions", color = Color.White, fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    if(isAr) "الشروط" else "Terms",
                    color = Color(0xFFC5A059),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { showTermsDialog = true }
                )
            }

            if (showTermsDialog) {
                AlertDialog(
                    onDismissRequest = { showTermsDialog = false },
                    title = {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if(isAr) "الشروط والأحكام" else "Terms & Conditions", fontWeight = FontWeight.Black)
                            IconButton(onClick = { showTermsDialog = false }) { Icon(Icons.Default.Close, null) }
                        }
                    },
                    text = {
                        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
                                Text(
                                    termsText, 
                                    fontSize = 14.sp, 
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Right, 
                                    style = androidx.compose.ui.text.TextStyle(
                                        textDirection = androidx.compose.ui.text.style.TextDirection.ContentOrRtl
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = { acceptedTerms = true; showTermsDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC5A059))) {
                            Text(if(isAr) "موافق" else "Accept", color = Color.Black)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
            if (isLoading) { CircularProgressIndicator(color = Color(0xFFC5A059)) } else {
                Button(
                    enabled = acceptedTerms,
                    onClick = {
                    val isDriver = role == "DRIVER"
                    val isBikeSelection = isDriver && (vehicleType.contains("دراجة") || vehicleType.lowercase().contains("bike"))
                    
                    val missingDriverDocs = isDriver && (
                        idCardUri == null || (profileUri == null && profileImageBytes == null) || vehicleColor.isEmpty() || vehicleType.isEmpty() ||
                        (!isBikeSelection && (licenseFrontUri == null || licenseBackUri == null || vehiclePlate.isEmpty()))
                    )
                    
                    if (name.isEmpty() || username.isEmpty() || phone.isEmpty() || password.isEmpty() || confirmPassword.isEmpty() || province.isEmpty() || missingDriverDocs) { 
                        val msg = if (missingDriverDocs) {
                            if(isAr) "يرجى تحميل كافة الصور وإكمال بيانات المركبة" else "Please upload all photos and complete vehicle info"
                        } else {
                            if(isAr) "أكمل كافة البيانات المطلوبة" else "Fill all required fields"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        return@Button 
                    }
                    if (phone.length != 11 || !phone.startsWith("01") || !phone.all { it.isDigit() }) {
                        Toast.makeText(context, if(isAr) "رقم الهاتف يجب أن يبدأ بـ 01 ويتكون من 11 رقمًا بالضبط" else "Phone number must start with 01 and be 11 digits", Toast.LENGTH_LONG).show()
                        return@Button
                    }
                    if (password.contains(" ") || confirmPassword.contains(" ")) {
                        Toast.makeText(context, if(isAr) "كلمة المرور لا يجب أن تحتوي على مسافات" else "Password cannot contain spaces", Toast.LENGTH_LONG).show()
                        return@Button
                    }
                    if (usernameStatus == "TAKEN") { Toast.makeText(context, if(isAr) "اسم المستخدم غير متاح" else "Username not available", Toast.LENGTH_SHORT).show(); return@Button }
                    if (phoneStatus == "TAKEN") { Toast.makeText(context, if(isAr) "رقم الهاتف مسجل بالفعل" else "Phone already taken", Toast.LENGTH_SHORT).show(); return@Button }
                    if (password != confirmPassword) { Toast.makeText(context, if(isAr) "كلمة المرور غير متطابقة" else "Passwords do not match", Toast.LENGTH_SHORT).show(); return@Button }
                    isLoading = true
                    scope.launch {
                        try {
                            val pImgDef = async(kotlinx.coroutines.Dispatchers.IO) {
                                if (profileUri != null) {
                                    firebaseManager.uploadToDrive(context, profileUri!!, "profile_${username}.jpg")
                                } else null
                            }
                            
                            val idImgDef = async(kotlinx.coroutines.Dispatchers.IO) { idCardUri?.let { firebaseManager.uploadToDrive(context, it, "id_${username}.jpg") } }
                            val lfImgDef = async(kotlinx.coroutines.Dispatchers.IO) { licenseFrontUri?.let { firebaseManager.uploadToDrive(context, it, "lic_f_${username}.jpg") } }
                            val lbImgDef = async(kotlinx.coroutines.Dispatchers.IO) { licenseBackUri?.let { firebaseManager.uploadToDrive(context, it, "lic_b_${username}.jpg") } }

                            val pImg = pImgDef.await()
                            val idImg = idImgDef.await()
                            val lfImg = lfImgDef.await()
                            val lbImg = lbImgDef.await()
                            
                            val newSessionId = java.util.UUID.randomUUID().toString()
                            val user = User(
                                id="", name=name, username=username, phone=phone, 
                                password=password, 
                                role=role,
                                vehicleType=vehicleType, 
                                vehiclePlate = vehiclePlate,
                                vehicleColor = vehicleColor,
                                busCapacity = busCapacity.toIntOrNull() ?: 0,
                                province=province,
                                profileImageDriveId=pImg, idCardDriveId=idImg, 
                                licenseFrontDriveId=lfImg, licenseBackDriveId=lbImg,
                                isApproved = role == "USER", 
                                isOnline = (role == "DRIVER" || role == "ADMIN"),
                                currentSessionId = newSessionId,
                                canDeliver = canDeliver,
                                canCargo = canCargo,
                                canLoad = canLoad,
                                canUnload = canUnload,
                                gender = gender
                            )
                            val result = firebaseManager.registerUser(user, password)
                            if (result.first) {
                                if (role == "DRIVER") {
                                    firebaseManager.notifyAdminsAboutNewDriver(user.name, false)
                                }
                                val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
                                val (loggedInUser, _) = firebaseManager.loginUser(phone, password, deviceId)
                                if (loggedInUser != null) {
                                    Toast.makeText(context, if(isAr) "تم التسجيل والدخول بنجاح" else "Registered & Logged in successfully", Toast.LENGTH_LONG).show()
                                    onRegisterSuccess(loggedInUser)
                                } else {
                                    Toast.makeText(context, if(isAr) "تم التسجيل بنجاح، يرجى تسجيل الدخول" else "Registered successfully, please login", Toast.LENGTH_LONG).show()
                                    onBackToLogin()
                                }
                            } else {
                                Toast.makeText(context, result.second ?: "Error", Toast.LENGTH_LONG).show()
                                isLoading = false
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Upload Error: ${e.message}", Toast.LENGTH_LONG).show()
                            isLoading = false
                        }
                    }
                }, modifier = Modifier.fillMaxWidth().height(65.dp), shape = RoundedCornerShape(20.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC5A059))) { Text(if(isAr) "إنشاء الحساب" else "Create Account", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White) }
            }
            TextButton(onClick = onBackToLogin) { 
                Text(if(isAr) "دخول" else "Login", color = Color(0xFFC5A059), fontSize = 18.sp, fontWeight = FontWeight.Bold) 
            }
            // Invisible label to push the button up
            Text("", modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun DocumentUploadButton(label: String, isUploaded: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, 
        modifier = modifier.fillMaxWidth().height(58.dp).padding(top = 8.dp), 
        shape = RoundedCornerShape(12.dp), 
        colors = ButtonDefaults.outlinedButtonColors(contentColor = if(isUploaded) Color.Green else Color(0xFFC5A059))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) { 
            Icon(if(isUploaded) Icons.Default.CheckCircle else Icons.Default.FileUpload, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(label + if(isUploaded) " (تم)" else "", fontSize = 14.sp, fontWeight = FontWeight.Bold) 
        }
    }
}

@Composable
fun RegisterTextField(value: String, onValueChange: (String) -> Unit, label: String, icon: ImageVector, keyboardType: KeyboardType = KeyboardType.Text, isPassword: Boolean = false) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = Color(0xFFC5A059), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        leadingIcon = { Icon(icon, null, tint = Color(0xFFC5A059)) },
        trailingIcon = { if (isPassword) { IconButton(onClick = { passwordVisible = !passwordVisible }) { Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null, tint = Color.Gray) } } },
        shape = RoundedCornerShape(16.dp),
        singleLine = true,
        textStyle = TextStyle(textAlign = TextAlign.Center, color = Color.White, fontSize = 16.sp),
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFFC5A059), unfocusedBorderColor = Color.Gray)
    )
}

@Composable
fun RoleSelectionButton(label: String, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) Color(0xFFC5A059) else Color.DarkGray.copy(alpha = 0.5f),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFC5A059)) else null
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = if (isSelected) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}
