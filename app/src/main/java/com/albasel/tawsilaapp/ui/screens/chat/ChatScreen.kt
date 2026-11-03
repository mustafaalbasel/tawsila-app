package com.albasel.tawsilaapp.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.albasel.tawsilaapp.data.firebase.FirebaseManager
import com.albasel.tawsilaapp.data.model.Message
import com.albasel.tawsilaapp.data.model.User
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    firebaseManager: FirebaseManager,
    tripId: String,
    currentUser: User?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf(listOf<Message>()) }
    var text by remember { mutableStateOf("") }

    DisposableEffect(tripId) {
        val listener = firebaseManager.getMessages(tripId) { 
            messages = it 
            if (currentUser != null) {
                scope.launch { firebaseManager.markMessagesAsRead(tripId, currentUser.id) }
            }
        }
        onDispose {
            firebaseManager.database.child("group_chats").child(tripId).removeEventListener(listener)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    val titleText = if (tripId.startsWith("shared_")) "محادثة الرحلة المشتركة (مجموعة)" else "محادثة الرحلة"
                    Text(titleText, fontWeight = FontWeight.Bold) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFC5A059), titleContentColor = Color.Black)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom)
            ) {
                items(messages.sortedByDescending { it.timestamp }) { msg ->
                    val isMine = msg.senderId == currentUser?.id
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = if(isMine) Alignment.CenterEnd else Alignment.CenterStart) {
                        Surface(
                            color = if(isMine) Color(0xFFC5A059) else Color(0xFFE9ECEF),
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if(isMine) 16.dp else 0.dp, bottomEnd = if(isMine) 0.dp else 16.dp),
                            modifier = Modifier.widthIn(max = 280.dp)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                if(!isMine) Text(msg.senderName, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                Text(msg.content, color = if(isMine) Color.Black else Color.DarkGray)
                                Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp)), fontSize = 8.sp, modifier = Modifier.align(Alignment.End))
                            }
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    placeholder = { Text("اكتب رسالة...") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                FloatingActionButton(
                    onClick = {
                        if (text.isNotBlank() && currentUser != null) {
                            val inputMsg = text.trim()
                            text = ""
                            scope.launch {
                                try {
                                    val msg = Message(senderId = currentUser.id, senderName = currentUser.name, content = inputMsg, tripId = tripId)
                                    firebaseManager.sendMessage(msg)
                                } catch (e: Throwable) {
                                    android.util.Log.e("ChatScreen", "Error sending message: ${e.message}", e)
                                }
                            }
                        }
                    },
                    containerColor = Color(0xFFC5A059),
                    contentColor = Color.Black,
                    shape = CircleShape,
                    modifier = Modifier.size(50.dp)
                ) { Icon(Icons.Default.Send, null) }
            }
        }
    }
}
