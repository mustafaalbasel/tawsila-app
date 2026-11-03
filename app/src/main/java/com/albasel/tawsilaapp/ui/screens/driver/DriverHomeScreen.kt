package com.albasel.tawsilaapp.ui.screens.driver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.albasel.tawsilaapp.data.firebase.FirebaseManager
import com.albasel.tawsilaapp.data.model.User
import kotlinx.coroutines.launch

@Composable
fun DriverHomeScreen(firebaseManager: FirebaseManager, driverId: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var driverData by remember { mutableStateOf<User?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(driverId) {
        if (driverId.isBlank()) return@LaunchedEffect
        firebaseManager.database.child("users").child(driverId)
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    try {
                        driverData = snapshot.getValue(User::class.java)
                    } catch (e: Exception) {
                        android.util.Log.e("DriverHomeScreen", "Data conversion error: ${e.message}")
                    } finally {
                        isLoading = false
                    }
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) { isLoading = false }
            })
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8F9FA))
                .verticalScroll(rememberScrollState())
        ) {
            // Driver Stats Header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondary,
                shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("حساب السائق", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(16.dp))
                    
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        EarningCard("إجمالي الأرباح", "${driverData?.totalEarnings ?: 0.0} ج.م", Icons.Default.Payments, Modifier.weight(1f))
                        EarningCard("نسبة التطبيق", "${driverData?.pendingCommission ?: 0.0} ج.م", Icons.Default.AccountBalance, Modifier.weight(1f))
                    }
                }
            }

            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("الإجراءات السريعة", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                
                DriverActionItem("سداد مديونية التطبيق", "ادفع النسبة المستحقة عبر فودافون كاش", Icons.Default.AccountBalanceWallet)
                DriverActionItem("تعديل بيانات المركبة", "تحديث نوع السيارة أو الصور", Icons.Default.DirectionsCar)
                DriverActionItem("سجل الرحلات المكتملة", "عرض كافة الأوردرات التي قمت بتحميلها", Icons.Default.History)
                
                Spacer(Modifier.height(24.dp))
                
                // Toggle Online Status
                var isOnline by remember { mutableStateOf(false) }
                LaunchedEffect(driverData?.isOnline) {
                    if (driverData != null) {
                        isOnline = driverData!!.isOnline
                    }
                }
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isOnline) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isOnline) "أنت متصل الآن" else "أنت غير متصل", 
                                    fontWeight = FontWeight.ExtraBold, 
                                    fontSize = 20.sp,
                                    color = if (isOnline) Color(0xFF2E7D32) else Color.Red
                                )
                                Text(
                                    text = if (isOnline) "يظهر موقعك للعملاء على الخريطة" else "موقعك مخفي حالياً عن الجميع", 
                                    fontSize = 12.sp, 
                                    color = Color.Gray
                                )
                            }
                            
                            // High-visibility Switch
                            Switch(
                                checked = isOnline, 
                                onCheckedChange = { nextState ->
                                    isOnline = nextState
                                    scope.launch {
                                        try {
                                            firebaseManager.updateDriverOnlineStatus(driverId, nextState)
                                            val msg = if(nextState) "تم تفعيل وضع الاتصال" else "تم إيقاف وضع الاتصال"
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            isOnline = !nextState // Revert on error
                                        }
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF2E7D32),
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = Color.Red
                                )
                            )
                        }
                        
                        if (!isOnline) {
                            Spacer(Modifier.height(12.dp))
                            Surface(
                                color = Color.Red.copy(0.1f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "تنبيه: لن تصلك أي طلبات جديدة وأنت غير متصل",
                                    modifier = Modifier.padding(8.dp),
                                    color = Color.Red,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EarningCard(title: String, value: String, icon: ImageVector, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(0.15f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(8.dp))
            Text(value, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Text(title, color = Color.White.copy(0.8f), fontSize = 11.sp)
        }
    }
}

@Composable
fun DriverActionItem(title: String, subtitle: String, icon: ImageVector) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(subtitle, fontSize = 12.sp, color = Color.Gray)
            }
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.ChevronLeft, null, tint = Color.LightGray)
        }
    }
}
