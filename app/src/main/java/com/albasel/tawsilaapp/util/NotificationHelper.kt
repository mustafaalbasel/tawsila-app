package com.albasel.tawsilaapp.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import com.albasel.tawsilaapp.MainActivity
import com.albasel.tawsilaapp.R

object NotificationHelper {
    const val CHANNEL_ID = "wasalna_notifications"
    private const val CHANNEL_NAME = "Wasalna Alerts"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Important updates from WASALNA App"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
            }
            manager.createNotificationChannel(channel)
        }
    }

    @Volatile private var lastShownKey = ""
    @Volatile private var lastShownTime = 0L

    fun showNotification(context: Context, title: String, message: String, route: String? = null, notificationId: Int? = null) {
        val key = "${title.trim()}_${message.trim()}"
        val now = System.currentTimeMillis()
        if (key == lastShownKey && (now - lastShownTime) < 3000) {
            return
        }
        lastShownKey = key
        lastShownTime = now

        createNotificationChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = if (route?.startsWith("http") == true) {
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(route)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                if (route != null) {
                    putExtra("destination_route", route)
                    putExtra("route", route)
                }
            }
        }
        
        val finalId = notificationId ?: (title.trim() + "_" + message.trim()).hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context, 
            finalId, 
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        
        val largeIcon = try {
            android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.ic_notification)
                ?: android.graphics.BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
        } catch (e: Exception) { null }

        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(largeIcon)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)

        manager.notify(finalId, notificationBuilder.build())
    }
}
