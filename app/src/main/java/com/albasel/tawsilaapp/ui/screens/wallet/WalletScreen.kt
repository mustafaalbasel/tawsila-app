package com.albasel.tawsilaapp.ui.screens.wallet

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.albasel.tawsilaapp.data.firebase.FirebaseManager
import com.albasel.tawsilaapp.data.model.User
import com.albasel.tawsilaapp.data.model.Order
import com.albasel.tawsilaapp.data.model.PointTransaction
import com.albasel.tawsilaapp.data.model.PaymentMethod
import com.albasel.tawsilaapp.util.GoogleDriveHelper
import com.albasel.tawsilaapp.util.AppImageView
import kotlinx.coroutines.launch
import java.util.*
import java.text.SimpleDateFormat
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    firebaseManager: FirebaseManager,
    user: User?,
    language: String,
    isDarkMode: Boolean,
    onMenuClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isAr = language == "AR"
    
    var pointsDiscounts by remember { mutableStateOf(emptyMap<Int, Double>()) }
    var pointValue by remember { mutableDoubleStateOf(0.0) }
    var adminCommission by remember { mutableDoubleStateOf(10.0) }
    var paymentMethods by remember { mutableStateOf<List<PaymentMethod>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    
    var showPickerOptions by remember { mutableStateOf(false) }
    
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && user != null) {
            isLoading = true
            scope.launch {
                try {
                    val driveId = firebaseManager.uploadToDrive(context, uri, "receipt_${user.id}_${System.currentTimeMillis()}.jpg")
                    if (driveId != null) {
                        firebaseManager.addPaymentReceipt(user.id, driveId, user.name)
                        Toast.makeText(context, if(isAr) "تم إرسال الإيصال بنجاح" else "Receipt sent successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, if(isAr) "فشل رفع الصورة، يرجى المحاولة مرة أخرى" else "Failed to upload image", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isLoading = false
                }
            }
        }
    }

    // Advanced Camera setup for high quality
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && photoUri != null && user != null) {
            isLoading = true
            scope.launch {
                try {
                    val driveId = firebaseManager.uploadToDrive(context, photoUri!!, "receipt_${user.id}_${System.currentTimeMillis()}.jpg")
                    if (driveId != null) {
                        firebaseManager.addPaymentReceipt(user.id, driveId, user.name)
                        Toast.makeText(context, if(isAr) "تم إرسال الإيصال بنجاح" else "Receipt sent successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, if(isAr) "فشل رفع الصورة، يرجى المحاولة مرة أخرى" else "Failed to upload image", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isLoading = false
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            try {
                val file = java.io.File(context.cacheDir, "temp_receipt_${System.currentTimeMillis()}.jpg")
                file.parentFile?.mkdirs()
                photoUri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                cameraLauncher.launch(photoUri!!)
            } catch (e: Exception) {
                Toast.makeText(context, "Error opening camera: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else Toast.makeText(context, if(isAr) "يجب الموافقة على صلاحية الكاميرا" else "Camera permission required", Toast.LENGTH_SHORT).show()
    }

    var transactions by remember { mutableStateOf<List<PointTransaction>>(emptyList()) }
    var confirmedOrders by remember { mutableStateOf<List<Order>>(emptyList()) }
    var customerPointValue by remember { mutableDoubleStateOf(0.0) }
    var driverPointValue by remember { mutableDoubleStateOf(0.0) }

    LaunchedEffect(user?.id) {
        pointsDiscounts = firebaseManager.getPointsDiscounts()
        paymentMethods = firebaseManager.getPaymentLinks()
        adminCommission = firebaseManager.getAdminCommission()
        
        user?.id?.let { uid ->
            customerPointValue = firebaseManager.getPointValue("USER")
            driverPointValue = firebaseManager.getPointValue("DRIVER")
            
            firebaseManager.getPointTransactions(uid) { list ->
                scope.launch {
                    val isDriverRole = user.role.uppercase() == "DRIVER" || (user.role.uppercase() == "ADMIN" && !user.vehiclePlate.isNullOrEmpty())
                    val all = if (isDriverRole) firebaseManager.getDriverReservedOrders(uid) else firebaseManager.getUserOrders(uid)
                    // Only show COMPLETED rides in the wallet records as per request
                    confirmedOrders = all.filter { it.status == "COMPLETED" }
                    transactions = list.sortedByDescending { it.timestamp }
                }
            }
        }
    }

    val themeBg = if (isDarkMode) Color(0xFF121212) else Color.White
    val cardBg = if (isDarkMode) Color(0xFF1E1E1E) else Color(0xFFF5F5F5)
    val textColor = if (isDarkMode) Color.White else Color.Black
    val subTextColor = if (isDarkMode) Color.Gray else Color.DarkGray

    // Role Check
    val isActuallyDriver = user?.role?.uppercase() == "DRIVER" || 
                           user?.role?.uppercase() == "ADMIN" || 
                           !user?.vehiclePlate.isNullOrEmpty()

    // Calculation Logic
    val confirmedTripSum = confirmedOrders.sumOf { it.price }
    val totalTripAmount = maxOf(user?.totalEarnings ?: 0.0, confirmedTripSum)

    val rawCustomerPoints = user?.points ?: 0.0
    val customerPoints = if (transactions.isEmpty() && rawCustomerPoints < 0) 0.0 else rawCustomerPoints
    val driverPoints = user?.driverPoints ?: 0.0

    val customerWalletValue = customerPoints * customerPointValue
    val driverWalletValue = driverPoints * driverPointValue
    val totalWalletValue = customerWalletValue + driverWalletValue
    val totalPointsCount = customerPoints + driverPoints
    
    val commissionAmount = totalTripAmount * (adminCommission / 100.0)
    val finalDue = (commissionAmount - (user?.paidCommission ?: 0.0) - totalWalletValue).coerceAtLeast(0.0)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if(isAr) "محفظة النقاط والمالية" else "Wallet & Finance", color = Color(0xFFC5A059), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onMenuClick) { Icon(Icons.Default.Menu, null, tint = Color(0xFFC5A059)) } },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = themeBg
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            
            // --- 1. Points Summary Card ---
            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = cardBg) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if(isAr) "إجمالي رصيد النقاط (بالجنية)" else "Total Points Balance (EGP)", color = subTextColor, fontSize = 13.sp)
                    Text(String.format(Locale.ENGLISH, "%.2f", totalWalletValue) + (if(isAr) " ج.م" else " EGP"), color = Color(0xFFC5A059), fontSize = 38.sp, fontWeight = FontWeight.Black)
                    
                    Spacer(Modifier.height(16.dp))
                    
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Driver Points Sub-Card (Visible only for Drivers/Admins)
                        if (isActuallyDriver) {
                            Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), color = Color.Black.copy(0.05f), border = BorderStroke(1.dp, Color.Gray.copy(0.1f))) {
                                Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(if(isAr) "نقاط السائق" else "Driver Pts", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Text(String.format(Locale.ENGLISH, "%.1f", driverPoints), color = Color(0xFFC5A059), fontSize = 18.sp, fontWeight = FontWeight.Black)
                                    Text("≈ ${String.format(Locale.ENGLISH, "%.1f", driverWalletValue)} ج.م", color = Color(0xFF2E7D32), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        // User Points Sub-Card (Always Visible)
                        Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), color = Color.Black.copy(0.05f), border = BorderStroke(1.dp, Color.Gray.copy(0.1f))) {
                            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if(isAr) "نقاط العميل" else "User Pts", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(String.format(Locale.ENGLISH, "%.1f", customerPoints), color = Color(0xFF2196F3), fontSize = 18.sp, fontWeight = FontWeight.Black)
                                Text("≈ ${String.format(Locale.ENGLISH, "%.1f", customerWalletValue)} ج.م", color = Color(0xFF2E7D32), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (isActuallyDriver) {
                // --- 2. Financial Summary Tab ---
                Spacer(Modifier.height(24.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(), 
                    shape = RoundedCornerShape(28.dp), 
                    color = Color.Black, 
                    border = BorderStroke(1.dp, Color(0xFFC5A059).copy(0.4f)),
                    shadowElevation = 8.dp
                ) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.AccountBalance, null, tint = Color(0xFFC5A059), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(if(isAr) "حساب السائق" else "Driver Account", color = Color(0xFFC5A059), fontWeight = FontWeight.Black, fontSize = 16.sp)
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = Color.White.copy(0.1f))
                        Spacer(Modifier.height(16.dp))

                        SummaryRow(if(isAr) "إجمالي مبلغ الرحلات المؤكدة:" else "Total Confirmed Trips:", "${totalTripAmount.roundToInt()} ج.م", Color.White)
                        SummaryRow(if(isAr) "عمولة التطبيق المستحقة ($adminCommission%):" else "App Commission Due:", "${commissionAmount.roundToInt()} ج.م", Color(0xFFE91E63))
                        SummaryRow(if(isAr) "ما تم دفعه مسبقاً:" else "Total Already Paid:", "${user?.paidCommission?.roundToInt() ?: 0} ج.م", Color(0xFF4CAF50))
                        SummaryRow(if(isAr) "إجمالي قيمة النقاط (خصم):" else "Total Points Discount:", "- ${totalWalletValue.roundToInt()} ج.م", Color(0xFF2196F3))

                        Spacer(Modifier.height(16.dp))
                        Surface(Modifier.fillMaxWidth(), color = Color(0xFF1A1A1A), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFFC5A059).copy(0.5f))) {
                            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(if(isAr) "المبلغ النهائي المطلوب سداده:" else "Final Net Due Amount:", color = Color(0xFFC5A059), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                                Text("${finalDue.roundToInt()} ج.م", color = Color(0xFFC5A059), fontWeight = FontWeight.Black, fontSize = 24.sp)
                            }
                        }

                        // Payment Methods & Receipt Button
                        Spacer(Modifier.height(24.dp))
                        Text(if(isAr) "اختر وسيلة الدفع وأرسل الوصل:" else "Payment & Send Receipt:", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))

                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1.5f)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    paymentMethods.forEach { method ->
                                        Surface(
                                            modifier = Modifier.size(44.dp),
                                            shape = CircleShape,
                                            color = Color.White,
                                            border = BorderStroke(1.dp, Color(0xFFC5A059).copy(0.5f)),
                                            onClick = {
                                                try {
                                                    val intent = if (method.type == "PHONE") Intent(Intent.ACTION_DIAL, Uri.parse("tel:${method.url}"))
                                                                 else Intent(Intent.ACTION_VIEW, Uri.parse(method.url))
                                                    context.startActivity(intent)
                                                } catch(e: Exception) {}
                                            }
                                        ) {
                                            Box(Modifier.padding(8.dp)) { AppImageView(method.iconId, Modifier.fillMaxSize()) }
                                        }
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    try {
                                        galleryLauncher.launch("image/*")
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC5A059), contentColor = Color.Black),
                                modifier = Modifier.height(48.dp).weight(1.3f)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if(isAr) "إرسال وصل" else "Send Receipt",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // --- 3. Redeem Points Section ---
            Spacer(Modifier.height(24.dp))
            
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                pointsDiscounts.toList().sortedBy { it.first }.forEach { (pts, disc) ->
                    val canAfford = totalPointsCount >= pts
                    Surface(
                        onClick = { if(canAfford && !isLoading) { isLoading = true; scope.launch { firebaseManager.applyPointsDiscount(user!!.id, pts.toDouble(), disc); isLoading = false } } },
                        enabled = canAfford && !isLoading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = if(canAfford) Color(0xFFC5A059) else cardBg,
                        border = if(!canAfford) BorderStroke(1.dp, Color.Gray.copy(0.2f)) else null
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, null, tint = if(canAfford) Color.Black else Color.Gray)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("$pts " + (if(isAr) "نقطة" else "Pts"), color = if(canAfford) Color.Black else textColor, fontWeight = FontWeight.Bold)
                                Text("${disc.toInt()} ج.م خصم", color = if(canAfford) Color.Black.copy(0.7f) else subTextColor, fontSize = 11.sp)
                            }
                            if(canAfford) Icon(Icons.Default.ChevronRight, null, tint = Color.Black)
                        }
                    }
                }
            }

            if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), color = Color(0xFFC5A059))

            // --- 4. History Section ---
            Spacer(Modifier.height(32.dp))
            Text(if(isAr) "سجل العمليات والنقاط" else "Transaction & Points History", color = textColor, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))

            if (transactions.isEmpty()) {
                Text(if(isAr) "لا توجد حركات بعد" else "No history yet", color = subTextColor, modifier = Modifier.padding(20.dp))
            } else {
                transactions.forEach { trans ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = cardBg)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                val isDriverLog = trans.reason.contains("(سائق)")
                                val isCustomerLog = trans.reason.contains("(عميل)")
                                
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isDriverLog || isCustomerLog) {
                                        Surface(
                                            color = (if(isDriverLog) Color(0xFFC5A059) else Color(0xFF2196F3)).copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                if(isDriverLog) (if(isAr) "سائق" else "Driver") else (if(isAr) "عميل" else "Customer"),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if(isDriverLog) Color(0xFFC5A059) else Color(0xFF2196F3)
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text(trans.reason.replace("(سائق) ", "").replace("(عميل) ", ""), fontWeight = FontWeight.Bold, color = textColor, fontSize = 13.sp, maxLines = 1)
                                }
                                
                                Text(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).format(Date(trans.timestamp)), fontSize = 10.sp, color = subTextColor)
                                
                                // Show Trip Amount for Driver Trips
                                if (isActuallyDriver && trans.amount > 0 && trans.reason.contains("رحلة")) {
                                    val tripAmount = confirmedOrders.find { o -> 
                                        trans.reason.contains(o.pickupName) || trans.reason.contains(o.destinationName)
                                    }?.let { (it.price + it.pointsDiscount).toInt() }
                                    
                                    if (tripAmount != null) {
                                        Text(if(isAr) "مبلغ الرحلة: $tripAmount ج.م" else "Trip Amount: $tripAmount EGP", color = Color(0xFF2E7D32), fontWeight = FontWeight.Black, fontSize = 11.sp)
                                    }
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                val color = if(trans.amount >= 0) Color(0xFF2E7D32) else Color.Red
                                Text((if(trans.amount >= 0) "+" else "") + String.format(Locale.ENGLISH, "%.1f", trans.amount), color = color, fontWeight = FontWeight.Black, fontSize = 16.sp)
                                Text(if(isAr) "نقطة" else "pts", fontSize = 9.sp, color = color)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(100.dp))
        }
    }

    if (showPickerOptions) {
        AlertDialog(
            onDismissRequest = { showPickerOptions = false },
            title = { Text(if(isAr) "إرسال إيصال دفع" else "Send Payment Receipt", fontWeight = FontWeight.Bold) },
            text = { Text(if(isAr) "اختر مصدر الصورة" else "Choose image source") },
            confirmButton = {
                Button(onClick = { 
                    showPickerOptions = false
                    if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        try {
                            val file = java.io.File(context.cacheDir, "temp_receipt_${System.currentTimeMillis()}.jpg")
                            file.parentFile?.mkdirs()
                            photoUri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                            cameraLauncher.launch(photoUri!!)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    } else permissionLauncher.launch(android.Manifest.permission.CAMERA)
                }) {
                    Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if(isAr) "كاميرا" else "Camera")
                }
            },
            dismissButton = {
                Button(onClick = { 
                    showPickerOptions = false
                    try {
                        galleryLauncher.launch("image/*")
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) {
                    Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if(isAr) "استديو" else "Gallery")
                }
            }
        )
    }
}

@Composable
fun SummaryRow(label: String, value: String, valueColor: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Gray, fontSize = 12.sp)
        Text(value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}
