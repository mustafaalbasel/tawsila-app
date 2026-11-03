package com.albasel.tawsilaapp.data.model

import androidx.annotation.Keep

@Keep
data class InAppNotification(
    val id: String = "",
    val title: String = "",
    val content: String = "",
    val type: String = "general",
    val sourceId: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
