package com.albasel.tawsilaapp.util

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.database.FirebaseDatabase
import android.content.Context
import android.util.Log

class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d("FCM", "Message received from: ${remoteMessage.from}")
        
        val title = remoteMessage.data["title"] ?: remoteMessage.notification?.title ?: "WASALNA"
        val body = remoteMessage.data["content"] ?: remoteMessage.data["body"] ?: remoteMessage.notification?.body ?: ""
        val route = remoteMessage.data["route"] ?: remoteMessage.data["destination_route"]

        // Ensure TTS is initialized and speaks even if app is closed
        if (route?.startsWith("chat") == true) {
            TTSHelper.speak(applicationContext, "$title. $body")
        }

        // Use messageId or content hash as notification ID to prevent duplicates if sent twice
        val notificationId = remoteMessage.messageId?.hashCode() ?: (title + body).hashCode()
        NotificationHelper.showNotification(applicationContext, title, body, route, notificationId)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "New Token: $token")
        
        // Save locally
        val sharedPrefs = getSharedPreferences("tawsila_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("fcm_token", token).apply()
        
        // Update in Database if user is logged in
        val userJson = sharedPrefs.getString("user_data", null)
        if (userJson != null) {
            try {
                val user = com.google.gson.Gson().fromJson(userJson, com.albasel.tawsilaapp.data.model.User::class.java)
                val uid = user.id
                val db = FirebaseDatabase.getInstance("https://tawsilaapp-f37b1740-default-rtdb.firebaseio.com/").reference
                db.child("users").child(uid).child("fcmToken").setValue(token)
            } catch (e: Exception) {
                Log.e("FCM", "Error updating token in DB", e)
            }
        }
    }
}
