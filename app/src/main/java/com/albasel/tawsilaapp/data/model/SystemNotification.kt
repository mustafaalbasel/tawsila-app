package com.albasel.tawsilaapp.data.model

import androidx.annotation.Keep

@Keep
data class SystemNotification(
    val id: String = "",
    val title: String = "",
    val content: String = "",
    val type: String = "GENERAL", // GENERAL, CHAT, ORDER
    val sourceId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
