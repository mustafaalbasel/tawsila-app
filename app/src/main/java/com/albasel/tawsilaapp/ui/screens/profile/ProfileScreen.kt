package com.albasel.tawsilaapp.ui.screens.profile

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.albasel.tawsilaapp.data.firebase.FirebaseManager
import com.albasel.tawsilaapp.data.model.User
import com.albasel.tawsilaapp.util.AppImageView
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    firebaseManager: FirebaseManager,
    user: User?,
    language: String,
    isDarkMode: Boolean,
    onMenuClick: () -> Unit,
    onUpdateUser: (User) -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isAr = language == "AR"
    
    val themeBg = if (isDarkMode) Color(0xFF121212) else Color.White
    val cardBg = if (isDarkMode) Color(0xFF1E1E1E) else Color(0xFFF5F5F5)
    val textColor = if (isDarkMode) Color.White else Color.Black

    var isEditMode by remember { mutableStateOf(false) }
    
    // States for editing
    var name by remember { mutableStateOf(user?.name ?: "") }
    var province by remember { mutableStateOf(user?.province ?: "") }
    var vehicleType by remember { mutableStateOf(user?.vehicleType ?: "") }
    var vehiclePlate by remember { mutableStateOf(user?.vehiclePlate ?: "") }
    var vehicleColor by remember { mutableStateOf(user?.vehicleColor ?: "") }
    var busCapacity by remember { mutableStateOf(user?.busCapacity?.toString() ?: "0") }
    var gender by remember { mutableStateOf(user?.gender ?: "MALE") }
    var canDeliver by remember { mutableStateOf(user?.canDeliver ?: true) }
    var canCargo by remember { mutableStateOf(user?.canCargo ?: false) }
    var password by remember { mutableStateOf(user?.password ?: "") }
    
    // Temp Image States (Hold selection until Save)
    var tempProfileUri by remember { mutableStateOf<Uri?>(null) }
    var tempIdCardUri by remember { mutableStateOf<Uri?>(null) }
    var tempLicFrontUri by remember { mutableStateOf<Uri?>(null) }
    var tempLicBackUri by remember { mutableStateOf<Uri?>(null) }

    var isSaving by remember { mutableStateOf(false) }
    var fullScreenImage by remember { mutableStateOf<String?>(null) }

    val isDriverUi = user?.role?.uppercase() == "DRIVER" || (!user?.vehiclePlate.isNullOrEmpty() && user?.vehiclePlate?.isNotBlank() == true)

    var vehiclePrices by remember { mutableStateOf<Map<String, com.albasel.tawsilaapp.data.model.VehiclePrice>>(emptyMap()) }
    LaunchedEffect(Unit) {
        vehiclePrices = firebaseManager.getVehiclePricesDetail()
    }

    // Sync state only when NOT editing to prevent losing user input due to lastSeen background updates
    // Optimization: Only trigger when CRITICAL data changes (exclude lastSeen/location)
    LaunchedEffect(
        user?.id, user?.name, user?.province, user?.vehicleType, user?.vehiclePlate, 
        user?.vehicleColor, user?.busCapacity, user?.gender, user?.password, 
        user?.canDeliver, user?.canCargo, isEditMode
    ) {
        if (!isEditMode) {
            user?.let {
                name = it.name
                province = it.province.replace("محافظة", "").trim()
                vehicleType = it.vehicleType
                vehiclePlate = it.vehiclePlate
                vehicleColor = it.vehicleColor
                busCapacity = it.busCapacity.toString()
                gender = it.gender
                password = it.password
                canDeliver = it.canDeliver
                canCargo = it.canCargo
            }
        }
    }

    val provinces = listOf("القاهرة", "الإسكندرية", "الجيزة", "الدقهلية", "البحر الأحمر", "البحيرة", "الفيوم", "الغربية", "الإسماعيلية", "المنوفية", "المنيا", "القليوبية", "الوادي الجديد", "الشرقية", "السويس", "أسوان", "أسيوط", "بني سويف", "بور سعيد", "دمياط", "جنوب سيناء", "كفر الشيخ", "مطروح", "الأقصر", "قنا", "شمال سيناء", "سوهاج")
    
    val vehicleList = remember(language, vehiclePrices) {
        val staticList = if(isAr) listOf("تاكسي", "فان", "ملاكي", "توكتوك", "تريسكل", "باص", "ميكروباص", "موتوسيكل", "دراجة", "ربع نقل", "نصف نقل", "نقل")
                         else listOf("Taxi", "Van", "Private", "Tuk-tuk", "Tricycle", "Bus", "Microbus", "Motorcycle", "Bike", "Quarter Truck", "Half Truck", "Truck")
        staticList.filter { vehiclePrices[it]?.isVisible ?: true }
    }

    val isCargoOnly = remember(vehicleType) {
        listOf("دراجة", "bike", "ربع نقل", "quarter truck", "نصف نقل", "half truck", "نقل", "truck")
            .any { vehicleType.lowercase().contains(it) }
    }

    LaunchedEffect(isCargoOnly, isEditMode) {
        if (isEditMode && isCargoOnly) {
            canDeliver = false
            canCargo = true
        }
    }

    // Image pickers
    val profilePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> 
        uri?.let { tempProfileUri = it }
    }

    val idCardPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> 
        uri?.let { tempIdCardUri = it }
    }

    val licenseFrontPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> 
        uri?.let { tempLicFrontUri = it }
    }

    val licenseBackPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> 
        uri?.let { tempLicBackUri = it }
    }

    if (fullScreenImage != null) {
        FullScreenImageDialog(imageDriveId = fullScreenImage!!) { fullScreenImage = null }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if(isAr) "الملف الشخصي" else "Profile", color = Color(0xFFC5A059), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onMenuClick) { Icon(Icons.Default.Menu, null, tint = Color(0xFFC5A059)) } },
                actions = { IconButton(onLogout) { Icon(Icons.Default.Logout, null, tint = Color.Red) } },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = themeBg)
            )
        },
        containerColor = themeBg
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            
            Box(Modifier.size(130.dp).clip(CircleShape).clickable { 
                fullScreenImage = tempProfileUri?.toString() ?: (user?.profileImageDriveId ?: user?.profileImageBase64) 
            }.background(cardBg)) {
                AppImageView(tempProfileUri ?: user?.profileImageDriveId ?: user?.profileImageBase64, Modifier.fillMaxSize().clip(CircleShape))
                Surface(Modifier.fillMaxSize(), shape = CircleShape, color = Color.Transparent, border = BorderStroke(2.dp, Color(0xFFC5A059))) {}
                if (isEditMode) {
                    Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).size(36.dp).background(Color(0xFFC5A059), CircleShape).clickable { profilePicker.launch("image/*") }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.CameraAlt, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(user?.name ?: "", color = textColor, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text("@${user?.username ?: ""}", color = Color(0xFFC5A059), fontWeight = FontWeight.Bold)

            Spacer(Modifier.height(32.dp))

            // NAME FIELD
            ProfileField(if(isAr) "الاسم بالكامل" else "Full Name", name, Icons.Default.Person, isDarkMode, enabled = isEditMode) { name = it }
            ProfileField(if(isAr) "رقم الهاتف" else "Phone Number", user?.phone ?: "", Icons.Default.Phone, isDarkMode, enabled = false) { }

            // GENDER SELECTION
            if (isDriverUi) {
                Spacer(Modifier.height(8.dp))
                Text(if(isAr) "النوع:" else "Gender:", color = textColor, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val genderOptions = listOf("MALE" to (if(isAr) "رجل" else "Male"), "FEMALE" to (if(isAr) "امرأة" else "Female"))
                    genderOptions.forEach { (key, label) ->
                        val isSelected = gender == key
                        Surface(
                            onClick = { if(isEditMode) gender = key },
                            modifier = Modifier.weight(1f).height(45.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = if(isSelected) Color(0xFFC5A059) else cardBg,
                            border = if(isSelected) BorderStroke(2.dp, Color(0xFFC5A059)) else null
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(label, color = if(isSelected) Color.White else textColor, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // PROVINCE DROP-DOWN (High Performance Fix)
            var provExpanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                OutlinedTextField(
                    value = province, onValueChange = {}, readOnly = true, enabled = isEditMode,
                    label = { Text(if(isAr) "المحافظة" else "Province", color = if(isEditMode) Color(0xFFC5A059) else Color.Gray, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.LocationCity, null, tint = if(isEditMode) Color(0xFFC5A059) else Color.Gray) },
                    trailingIcon = { if(isEditMode) Icon(if(provExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown, null, tint = Color(0xFFC5A059)) },
                    shape = RoundedCornerShape(16.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(color = textColor, fontSize = 16.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textColor, unfocusedTextColor = textColor, disabledTextColor = textColor,
                        focusedBorderColor = Color(0xFFC5A059), unfocusedBorderColor = if(isDarkMode) Color.DarkGray else Color(0xFFC5A059).copy(alpha = 0.3f),
                        disabledBorderColor = if(isDarkMode) Color.DarkGray else Color(0xFFC5A059).copy(alpha = 0.3f)
                    )
                )
                if (isEditMode) {
                    Box(Modifier.matchParentSize().clickable { provExpanded = true })
                    DropdownMenu(
                        expanded = provExpanded,
                        onDismissRequest = { provExpanded = false },
                        modifier = Modifier.background(themeBg, RoundedCornerShape(24.dp)).fillMaxWidth(0.9f).heightIn(max = 400.dp).border(1.dp, Color(0xFFC5A059).copy(0.3f), RoundedCornerShape(24.dp)),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        provinces.forEach { p ->
                            DropdownMenuItem(
                                text = { 
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if(isDarkMode) Color.White.copy(0.05f) else Color.Black.copy(0.05f))
                                            .padding(horizontal = 16.dp, vertical = 10.dp)
                                    ) {
                                        Text(p, color = textColor, fontWeight = FontWeight.ExtraBold) 
                                    }
                                }, 
                                onClick = { province = p; provExpanded = false },
                                modifier = Modifier.background(Color.Transparent)
                            )
                        }
                    }
                }
            }

            if (isDriverUi) {
                ProfileField(if(isAr) "رقم لوحة المركبة" else "Plate Number", vehiclePlate, Icons.Default.DirectionsCar, isDarkMode, isEditMode) { vehiclePlate = it }
                
                // VEHICLE COLOR DROP-DOWN (High Performance Fix)
                var colorExpanded by remember { mutableStateOf(false) }
                val colorMap = listOf(
                    "أبيض" to Color.White, "أسود" to Color.Black, "رمادي" to Color.Gray, "فضي" to Color(0xFFC0C0C0), "أحمر" to Color.Red,
                    "أزرق" to Color.Blue, "كحلي" to Color(0xFF000080), "بني" to Color(0xFF8B4513), "بيج" to Color(0xFFF5F5DC), "أخضر" to Color.Green,
                    "برتقالي" to Color(0xFFFFA500), "بنفسجي" to Color(0xFF800080), "ذهبي" to Color(0xFFFFD700), "وردي" to Color(0xFFFFC0CB), "سماوي" to Color(0xFF87CEEB)
                )
                val colors = if(isAr) listOf("أبيض", "أسود", "رمادي", "فضي", "أحمر", "أزرق", "كحلي", "بني", "بيج", "أخضر", "برتقالي", "بنفسجي", "ذهبي", "وردي", "سماوي")
                             else listOf("White", "Black", "Gray", "Silver", "Red", "Blue", "Navy", "Brown", "Beige", "Green", "Orange", "Purple", "Gold", "Pink", "Cyan")

                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    OutlinedTextField(
                        value = vehicleColor, onValueChange = {}, readOnly = true, enabled = isEditMode,
                        label = { Text(if(isAr) "لون المركبة" else "Vehicle Color", color = if(isEditMode) Color(0xFFC5A059) else Color.Gray, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(), 
                        leadingIcon = { Icon(Icons.Default.Palette, null, tint = if(isEditMode) Color(0xFFC5A059) else Color.Gray) },
                        trailingIcon = { if(isEditMode) Icon(if(colorExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown, null, tint = Color(0xFFC5A059)) },
                        shape = RoundedCornerShape(16.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(color = textColor, fontSize = 16.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textColor, unfocusedTextColor = textColor, disabledTextColor = textColor,
                            focusedBorderColor = Color(0xFFC5A059), unfocusedBorderColor = if(isDarkMode) Color.DarkGray else Color(0xFFC5A059).copy(alpha = 0.3f),
                            disabledBorderColor = if(isDarkMode) Color.DarkGray else Color(0xFFC5A059).copy(alpha = 0.3f)
                        )
                    )
                    if (isEditMode) {
                        Box(Modifier.matchParentSize().clickable { colorExpanded = true })
                        DropdownMenu(
                            expanded = colorExpanded,
                            onDismissRequest = { colorExpanded = false },
                            modifier = Modifier.background(if(isDarkMode) Color.Black else Color.White, RoundedCornerShape(24.dp)).fillMaxWidth(0.9f).heightIn(max = 400.dp).border(1.dp, Color(0xFFC5A059).copy(0.3f), RoundedCornerShape(24.dp)),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            colors.forEach { c ->
                                val cColor = colorMap.find { it.first == c }?.second ?: Color.Black
                                DropdownMenuItem(
                                    text = { 
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(Color(0xFFC5A059))
                                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                        ) {
                                            Box(Modifier.size(24.dp).clip(CircleShape).background(cColor).border(1.dp, Color.Black.copy(0.4f), CircleShape))
                                            Spacer(Modifier.width(16.dp))
                                            Text(c, color = Color.Black, fontWeight = FontWeight.Black, fontSize = 16.sp)
                                        }
                                    }, 
                                    onClick = { vehicleColor = c; colorExpanded = false },
                                    modifier = Modifier.background(Color.Transparent)
                                )
                            }
                        }
                    }
                }

                // VEHICLE TYPE DROP-DOWN (High Performance Fix)
                var vehicleExpanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    OutlinedTextField(
                        value = vehicleType, onValueChange = {}, readOnly = true, enabled = isEditMode,
                        label = { Text(if(isAr) "نوع المركبة" else "Vehicle Type", color = if(isEditMode) Color(0xFFC5A059) else Color.Gray, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.LocalTaxi, null, tint = if(isEditMode) Color(0xFFC5A059) else Color.Gray) },
                        trailingIcon = { if(isEditMode) Icon(if(vehicleExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown, null, tint = Color(0xFFC5A059)) },
                        shape = RoundedCornerShape(16.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(color = textColor, fontSize = 16.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textColor, unfocusedTextColor = textColor, disabledTextColor = textColor,
                            focusedBorderColor = Color(0xFFC5A059), unfocusedBorderColor = if(isDarkMode) Color.DarkGray else Color(0xFFC5A059).copy(alpha = 0.3f),
                            disabledBorderColor = if(isDarkMode) Color.DarkGray else Color(0xFFC5A059).copy(alpha = 0.3f)
                        )
                    )
                    if (isEditMode) {
                        Box(Modifier.matchParentSize().clickable { vehicleExpanded = true })
                        DropdownMenu(
                            expanded = vehicleExpanded,
                            onDismissRequest = { vehicleExpanded = false },
                            modifier = Modifier.background(themeBg, RoundedCornerShape(24.dp)).fillMaxWidth(0.9f).heightIn(max = 400.dp).border(1.dp, Color(0xFFC5A059).copy(0.3f), RoundedCornerShape(24.dp)),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            vehicleList.forEach { v ->
                                DropdownMenuItem(
                                    text = { 
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if(isDarkMode) Color.White.copy(0.05f) else Color.Black.copy(0.05f))
                                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                        ) {
                                            Text(v, color = textColor, fontWeight = FontWeight.ExtraBold) 
                                        }
                                    }, 
                                    onClick = { 
                                        vehicleType = v
                                        if (!v.contains("باص") && !v.contains("Bus")) {
                                            busCapacity = "0"
                                        }
                                        vehicleExpanded = false 
                                    },
                                    modifier = Modifier.background(Color.Transparent)
                                )
                            }
                        }
                    }
                }

                if ((vehicleType.contains("باص") || vehicleType.contains("Bus")) && !vehicleType.contains("ميكرو") && !vehicleType.contains("Micro")) {
                    Spacer(Modifier.height(8.dp))
                    var capExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        OutlinedTextField(
                            value = busCapacity, onValueChange = {}, readOnly = true, enabled = isEditMode,
                            label = { Text(if(isAr) "سعة الباص (ركاب)" else "Bus Capacity", color = if(isEditMode) Color(0xFFC5A059) else Color.Gray, fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.Default.Groups, null, tint = if(isEditMode) Color(0xFFC5A059) else Color.Gray) },
                            trailingIcon = { if(isEditMode) Icon(if(capExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown, null, tint = Color(0xFFC5A059)) },
                            shape = RoundedCornerShape(16.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(color = textColor, fontSize = 16.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = textColor, unfocusedTextColor = textColor, disabledTextColor = textColor,
                                focusedBorderColor = Color(0xFFC5A059), unfocusedBorderColor = if(isDarkMode) Color.DarkGray else Color(0xFFC5A059).copy(alpha = 0.3f),
                                disabledBorderColor = if(isDarkMode) Color.DarkGray else Color(0xFFC5A059).copy(alpha = 0.3f)
                            )
                        )
                        if (isEditMode) {
                            Box(Modifier.matchParentSize().clickable { capExpanded = true })
                            DropdownMenu(
                                expanded = capExpanded,
                                onDismissRequest = { capExpanded = false },
                                modifier = Modifier.background(themeBg, RoundedCornerShape(24.dp)).fillMaxWidth(0.9f).heightIn(max = 300.dp).border(1.dp, Color(0xFFC5A059).copy(0.3f), RoundedCornerShape(24.dp)),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                (1..50).forEach { i ->
                                    DropdownMenuItem(
                                        text = { 
                                            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                                                Text(i.toString(), color = textColor, fontWeight = FontWeight.ExtraBold) 
                                            }
                                        }, 
                                        onClick = { busCapacity = i.toString(); capExpanded = false }
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = if (isAr) "أقصى عدد أفراد 50" else "Max 50 passengers",
                        fontSize = 12.sp,
                        color = Color(0xFFC5A059),
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, bottom = 8.dp),
                        textAlign = TextAlign.Start
                    )
                }

                // SERVICES CHECKBOXES
                Spacer(Modifier.height(16.dp))
                Text(if(isAr) "خيارات الخدمات:" else "Service Options:", fontWeight = FontWeight.Bold, color = textColor, fontSize = 14.sp, modifier = Modifier.fillMaxWidth())
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (!isCargoOnly) {
                        Checkbox(checked = canDeliver, onCheckedChange = { if(isEditMode) canDeliver = it }, enabled = isEditMode, colors = CheckboxDefaults.colors(checkedColor = Color(0xFFC5A059)))
                        Text(if(isAr) "توصيل (ركاب)" else "Delivery", color = textColor, fontSize = 12.sp)
                        Spacer(Modifier.width(16.dp))
                    }
                    Checkbox(checked = canCargo, onCheckedChange = { if(isEditMode && !isCargoOnly) canCargo = it }, enabled = isEditMode && !isCargoOnly, colors = CheckboxDefaults.colors(checkedColor = Color(0xFFC5A059)))
                    Text(if(isAr) "نقل بضائع" else "Cargo", color = textColor, fontSize = 12.sp)
                }
            }

            // PASSWORD
            var passwordVisible by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = password, onValueChange = { password = it }, enabled = isEditMode,
                label = { Text(if(isAr) "كلمة المرور" else "Password", color = if(isEditMode) Color(0xFFC5A059) else Color.Gray, fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                leadingIcon = { Icon(Icons.Default.Lock, null, tint = if(isEditMode) Color(0xFFC5A059) else Color.Gray) },
                trailingIcon = { if (isEditMode) { IconButton(onClick = { passwordVisible = !passwordVisible }) { Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null, tint = Color.Gray) } } },
                shape = RoundedCornerShape(16.dp),
                textStyle = androidx.compose.ui.text.TextStyle(color = textColor, fontSize = 16.sp),
                visualTransformation = if (!passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = textColor, unfocusedTextColor = textColor, disabledTextColor = textColor,
                    focusedBorderColor = Color(0xFFC5A059), unfocusedBorderColor = if(isDarkMode) Color.DarkGray else Color(0xFFC5A059).copy(alpha = 0.3f),
                    disabledBorderColor = if(isDarkMode) Color.DarkGray else Color(0xFFC5A059).copy(alpha = 0.3f)
                )
            )

            if (isDriverUi) {
                Spacer(Modifier.height(24.dp))
                Text(if(isAr) "المستندات والوثائق" else "Documents", color = textColor, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DocBox(if(isAr) "الصورة الشخصية" else "Profile Photo", user?.profileImageDriveId ?: user?.profileImageBase64, tempProfileUri, isEditMode, isDarkMode, Modifier.weight(1f), { profilePicker.launch("image/*") }) { fullScreenImage = it }
                    Spacer(Modifier.width(8.dp))
                    DocBox(if(isAr) "البطاقة الشخصية" else "ID Card", user?.idCardDriveId ?: user?.idCardImageBase64, tempIdCardUri, isEditMode, isDarkMode, Modifier.weight(1f), { idCardPicker.launch("image/*") }) { fullScreenImage = it }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DocBox(if(isAr) "الرخصة (وجه)" else "License Front", user?.licenseFrontDriveId ?: user?.licenseFrontBase64, tempLicFrontUri, isEditMode, isDarkMode, Modifier.weight(1f), { licenseFrontPicker.launch("image/*") }) { fullScreenImage = it }
                    Spacer(Modifier.width(8.dp))
                    DocBox(if(isAr) "واجهة المركبة مع لوحة المركبة" else "Vehicle Front with Plate", user?.licenseBackDriveId ?: user?.licenseBackBase64, tempLicBackUri, isEditMode, isDarkMode, Modifier.weight(1f), { licenseBackPicker.launch("image/*") }) { fullScreenImage = it }
                }
            }

            Spacer(Modifier.height(40.dp))

            if (isSaving) {
                CircularProgressIndicator(color = Color(0xFFC5A059))
            } else {
                Button(
                    onClick = {
                        if (!isEditMode) { isEditMode = true } else {
                            if (isDriverUi && (vehicleType.isEmpty() || vehiclePlate.isEmpty() || vehicleColor.isEmpty())) {
                                Toast.makeText(context, if(isAr) "يرجى إكمال بيانات المركبة" else "Please complete vehicle info", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isSaving = true
                            scope.launch {
                                user?.let {
                                    var updated = it.copy(
                                        name = name, province = province, vehicleType = vehicleType.trim(), 
                                        vehiclePlate = vehiclePlate.trim(), vehicleColor = vehicleColor.trim(),
                                        busCapacity = busCapacity.toIntOrNull() ?: 0,
                                        gender = gender, password = password, canDeliver = canDeliver, canCargo = canCargo
                                    )

                                    var criticalPhotoChanged = false

                                    // Upload and update photos if changed
                                    tempProfileUri?.let { uri ->
                                        val oldId = it.profileImageDriveId ?: it.profileImageBase64
                                        val fileName = "profile_${it.username}.jpg"
                                        try { com.albasel.tawsilaapp.util.GoogleDriveHelper.deleteFileByName(fileName) } catch (e: Throwable) {}
                                        if (oldId != null && oldId.length < 100) { try { com.albasel.tawsilaapp.util.GoogleDriveHelper.deleteFile(oldId) } catch (e: Throwable) {} }
                                        
                                        val newId = firebaseManager.uploadToDrive(context, uri, fileName)
                                        if (newId != null) {
                                            updated = updated.copy(profileImageDriveId = newId, profileImageBase64 = null)
                                            if (isDriverUi) criticalPhotoChanged = true
                                        }
                                    }

                                    tempIdCardUri?.let { uri ->
                                        val oldId = it.idCardDriveId ?: it.idCardImageBase64
                                        val fileName = "id_${it.username}.jpg"
                                        try { com.albasel.tawsilaapp.util.GoogleDriveHelper.deleteFileByName(fileName) } catch (e: Throwable) {}
                                        if (oldId != null && oldId.length < 100) { try { com.albasel.tawsilaapp.util.GoogleDriveHelper.deleteFile(oldId) } catch (e: Throwable) {} }
                                        
                                        val newId = firebaseManager.uploadToDrive(context, uri, fileName)
                                        if (newId != null) {
                                            updated = updated.copy(idCardDriveId = newId, idCardImageBase64 = null)
                                            criticalPhotoChanged = true
                                        }
                                    }

                                    tempLicFrontUri?.let { uri ->
                                        val oldId = it.licenseFrontDriveId ?: it.licenseFrontBase64
                                        val fileName = "lic_f_${it.username}.jpg"
                                        try { com.albasel.tawsilaapp.util.GoogleDriveHelper.deleteFileByName(fileName) } catch (e: Throwable) {}
                                        if (oldId != null && oldId.length < 100) { try { com.albasel.tawsilaapp.util.GoogleDriveHelper.deleteFile(oldId) } catch (e: Throwable) {} }
                                        
                                        val newId = firebaseManager.uploadToDrive(context, uri, fileName)
                                        if (newId != null) {
                                            updated = updated.copy(licenseFrontDriveId = newId, licenseFrontBase64 = null)
                                            criticalPhotoChanged = true
                                        }
                                    }

                                    tempLicBackUri?.let { uri ->
                                        val oldId = it.licenseBackDriveId ?: it.licenseBackBase64
                                        val fileName = "lic_b_${it.username}.jpg"
                                        try { com.albasel.tawsilaapp.util.GoogleDriveHelper.deleteFileByName(fileName) } catch (e: Throwable) {}
                                        if (oldId != null && oldId.length < 100) { try { com.albasel.tawsilaapp.util.GoogleDriveHelper.deleteFile(oldId) } catch (e: Throwable) {} }
                                        
                                        val newId = firebaseManager.uploadToDrive(context, uri, fileName)
                                        if (newId != null) {
                                            updated = updated.copy(licenseBackDriveId = newId, licenseBackBase64 = null)
                                            criticalPhotoChanged = true
                                        }
                                    }

                                    val anyVehicleDataChanged = it.vehicleType.trim() != vehicleType.trim() || 
                                                                it.vehiclePlate.trim() != vehiclePlate.trim() || 
                                                                it.vehicleColor.trim() != vehicleColor.trim() ||
                                                                it.busCapacity != (busCapacity.toIntOrNull() ?: 0) ||
                                                                it.gender != gender || criticalPhotoChanged
                                    
                                    val isCriticalChanged = (isDriverUi || !vehiclePlate.isEmpty()) && anyVehicleDataChanged

                                    if (isCriticalChanged) {
                                        updated = updated.copy(isApproved = false)
                                    }
                                    
                                    firebaseManager.saveUser(updated)
                                    
                                    if (isDriverUi && isCriticalChanged) {
                                        firebaseManager.sendFcmNotification(it.id, "تنبيه هام", "تم تعليق حسابك لتحديث بياناتك الشخصية، يرجى الانتظار لحين مراجعة الإدارة.")
                                        firebaseManager.notifyAdminsAboutNewDriver(name, isUpdate = true, targetId = it.id)
                                        Toast.makeText(context, if(isAr) "تم تعليق الحساب للمراجعة بسبب تعديل البيانات" else "Account suspended for review", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, if(isAr) "تم حفظ التعديلات بنجاح" else "Saved successfully", Toast.LENGTH_SHORT).show()
                                    }
                                    
                                    onUpdateUser(updated)
                                    
                                    // Clear temp states after success to show Google Drive image
                                    tempProfileUri = null
                                    tempIdCardUri = null
                                    tempLicFrontUri = null
                                    tempLicBackUri = null
                                }
                                isEditMode = false
                                isSaving = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC5A059))
                ) {
                    Text(if(!isEditMode) (if(isAr) "تعديل" else "Edit") else (if(isAr) "حفظ" else "Save"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                Spacer(Modifier.height(16.dp))

                var showDeleteConfirm by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.Red.copy(0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                ) {
                    Icon(Icons.Default.DeleteForever, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if(isAr) "حذف الحساب نهائياً" else "Delete Account Forever", fontWeight = FontWeight.Bold)
                }

                if (showDeleteConfirm) {
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirm = false },
                        title = { Text(if(isAr) "حذف الحساب" else "Delete Account") },
                        text = { Text(if(isAr) "هل أنت متأكد من حذف حسابك نهائياً؟ لا يمكن التراجع عن هذا الإجراء." else "Are you sure you want to delete your account forever? This action cannot be undone.") },
                        confirmButton = {
                            Button(onClick = { scope.launch { user?.let { firebaseManager.deleteUser(it.id) }; onLogout() } }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                                Text(if(isAr) "تأكيد الحذف" else "Confirm Delete")
                            }
                        },
                        dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text(if(isAr) "إلغاء" else "Cancel") } }
                    )
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
fun DocBox(label: String, imageDriveId: String?, tempUri: Uri?, isEditMode: Boolean, isDarkMode: Boolean, modifier: Modifier, onEdit: () -> Unit, onClick: (String) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(label, fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
        Box(contentAlignment = Alignment.TopEnd) {
            Box(Modifier.size(height = 100.dp, width = 140.dp).clip(RoundedCornerShape(12.dp)).background(if(isDarkMode) Color(0xFF222222) else Color(0xFFF0F0F0)).clickable { 
                onClick(tempUri?.toString() ?: imageDriveId ?: "") 
            }) {
                AppImageView(tempUri ?: imageDriveId, Modifier.fillMaxSize())
            }
            if (isEditMode) {
                IconButton(onClick = onEdit, modifier = Modifier.padding(4.dp).size(30.dp).background(Color(0xFFC5A059), CircleShape)) {
                    Icon(Icons.Default.Edit, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun FullScreenImageDialog(imageDriveId: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            Box(Modifier.fillMaxSize()) {
                AppImageView(imageDriveId, Modifier.fillMaxSize().padding(16.dp), contentScale = ContentScale.Fit)
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(24.dp).background(Color.White.copy(0.3f), CircleShape)) {
                    Icon(Icons.Default.Close, null, tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun ProfileField(label: String, value: String, icon: ImageVector, isDarkMode: Boolean, enabled: Boolean = true, onValueChange: (String) -> Unit) {
    val textColor = if (isDarkMode) Color.White else Color.Black
    OutlinedTextField(
        value = value, onValueChange = onValueChange, enabled = enabled,
        label = { Text(label, color = if(enabled) Color(0xFFC5A059) else Color.Gray, fontSize = 12.sp) },
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        leadingIcon = { Icon(icon, null, tint = if(enabled) Color(0xFFC5A059) else Color.Gray) },
        shape = RoundedCornerShape(16.dp),
        textStyle = androidx.compose.ui.text.TextStyle(color = textColor, fontSize = 16.sp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = textColor, unfocusedTextColor = textColor, disabledTextColor = textColor,
            focusedBorderColor = Color(0xFFC5A059), unfocusedBorderColor = if(isDarkMode) Color.DarkGray else Color(0xFFC5A059).copy(alpha = 0.3f),
            disabledBorderColor = if(isDarkMode) Color.DarkGray else Color(0xFFC5A059).copy(alpha = 0.3f)
        )
    )
}
