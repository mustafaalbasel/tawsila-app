package com.albasel.tawsilaapp.data.model

data class Message(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val tripId: String = "",
    val isRead: Boolean = false
)
