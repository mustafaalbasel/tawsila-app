package com.albasel.tawsilaapp.data.model

data class ContactMessage(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
