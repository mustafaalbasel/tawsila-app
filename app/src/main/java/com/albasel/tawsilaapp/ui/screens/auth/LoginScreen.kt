package com.albasel.tawsilaapp.ui.screens.auth

import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.albasel.tawsilaapp.data.firebase.FirebaseManager
import com.albasel.tawsilaapp.data.model.User
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun LoginScreen(
    firebaseManager: FirebaseManager,
    language: String = "AR",
    onLoginSuccess: (User) -> Unit,
    onRegisterClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var phone by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val isAr = language == "AR"
    
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        AnimatedVisibility(visible = visible, enter = fadeIn(tween(800)) + scaleIn(tween(800))) {
            Surface(modifier = Modifier.size(110.dp), shape = RoundedCornerShape(32.dp), color = Color(0xFFC5A059)) {
                Box(contentAlignment = Alignment.Center) {
                    Text("T", fontSize = 65.sp, fontWeight = FontWeight.Black, color = Color.Black)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        Text(if(isAr) "تسجيل الدخول" else "Login", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFC5A059))
        Text(if(isAr) "مرحباً بك مجدداً في وصلنا" else "Welcome back to WASALNA", fontSize = 16.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(48.dp))

        LoginTextField(
            value = phone,
            onValueChange = { input -> val filtered = input.replace(" ", ""); if (filtered.all { it.isDigit() } && filtered.length <= 11) phone = filtered },
            label = if(isAr) "رقم الهاتف" else "Phone Number",
            icon = Icons.Default.Phone,
            keyboardType = KeyboardType.Number
        )

        LoginTextField(
            value = password,
            onValueChange = { password = it.replace(" ", "") },
            label = if(isAr) "كلمة المرور" else "Password",
            icon = Icons.Default.Lock,
            isPassword = true
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (isLoading) {
            CircularProgressIndicator(color = Color(0xFFC5A059))
        } else {
            Button(
                onClick = {
                    if (phone.isEmpty() || password.isEmpty()) {
                        Toast.makeText(context, if(isAr) "يرجى إدخال البيانات" else "Please enter all data", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isLoading = true
                    scope.launch {
                        if (firebaseManager.isPhoneBlocked(phone)) {
                            Toast.makeText(context, if(isAr) "عذراً، هذا الحساب محظور من الدخول" else "Sorry, this account is blocked", Toast.LENGTH_LONG).show()
                            isLoading = false
                            return@launch
                        }

                        val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
                        val (user, error) = firebaseManager.loginUser(phone, password, deviceId)
                        if (user != null) {
                            // Automatic CSV Map Sync on Login (GlobalScope ensures navigation won't cancel sync)
                            @Suppress("OPT_IN_USAGE")
                            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                                try {
                                    com.albasel.tawsilaapp.util.GoogleDriveHelper.checkAndUpdateLocalCsvFromDrive(context, force = true)
                                } catch (e: Exception) {
                                    Log.e("LoginScreen", "Login Map CSV Sync Error: ${e.message}")
                                }
                            }
                            // Fetch token immediately to ensure it's saved in DB
                            try {
                                val token = com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
                                firebaseManager.database.child("users").child(user.id).child("fcmToken").setValue(token).await()
                                onLoginSuccess(user.copy(fcmToken = token, isOnline = user.role?.uppercase() == "DRIVER" || user.role?.uppercase() == "ADMIN"))
                            } catch(e: Exception) {
                                onLoginSuccess(user.copy(isOnline = user.role?.uppercase() == "DRIVER" || user.role?.uppercase() == "ADMIN"))
                            }
                        } else {
                            val msg = when(error) {
                                "ALREADY_LOGGED_IN" -> if(isAr) "هذا الحساب مفتوح حالياً في هاتف آخر" else "This account is currently open on another phone"
                                "Phone not found" -> if(isAr) "رقم الهاتف غير مسجل" else "Phone number not found"
                                "Wrong password" -> if(isAr) "كلمة المرور غير صحيحة" else "Incorrect password"
                                else -> error ?: (if(isAr) "خطأ في تسجيل الدخول" else "Login error")
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(65.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC5A059))
            ) {
                Text(if(isAr) "دخول" else "Login", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if(isAr) "لا تملك حساب؟" else "Don't have an account?", color = Color.Gray)
            TextButton(onClick = onRegisterClick) {
                Text(if(isAr) "سجل الآن" else "Register Now", color = Color(0xFFC5A059), fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun LoginTextField(value: String, onValueChange: (String) -> Unit, label: String, icon: ImageVector, keyboardType: KeyboardType = KeyboardType.Text, isPassword: Boolean = false) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = Color(0xFFC5A059), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        leadingIcon = { Icon(icon, null, tint = Color(0xFFC5A059)) },
        trailingIcon = {
            if (isPassword) {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null, tint = Color.Gray)
                }
            }
        },
        shape = RoundedCornerShape(16.dp),
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
            focusedBorderColor = Color(0xFFC5A059), unfocusedBorderColor = Color.Gray
        )
    )
}
