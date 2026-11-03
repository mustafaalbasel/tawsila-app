package com.albasel.tawsilaapp.data.model

data class PaymentMethod(
    val id: String = "",
    val name: String = "",
    val url: String = "",
    val iconId: String = "",
    val type: String = "LINK" // LINK or PHONE
)
