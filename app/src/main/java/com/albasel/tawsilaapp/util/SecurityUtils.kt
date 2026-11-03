package com.albasel.tawsilaapp.util

import android.os.Build

object SecurityUtils {
    // Dynamic key derived from device fingerprint and package to prevent static APK key extraction
    private val DYNAMIC_KEY: String by lazy {
        (Build.FINGERPRINT + "com.albasel.tawsilaapp").take(32)
    }

    fun decrypt(encryptedData: String): String {
        return try {
            val data = hexToBytes(encryptedData)
            val result = ByteArray(data.size)
            for (i in data.indices) {
                result[i] = (data[i].toInt() xor DYNAMIC_KEY[i % DYNAMIC_KEY.length].toInt()).toByte()
            }
            String(result)
        } catch (e: Exception) {
            ""
        }
    }

    fun encrypt(data: String): String {
        val result = ByteArray(data.length)
        for (i in data.indices) {
            result[i] = (data[i].toInt() xor DYNAMIC_KEY[i % DYNAMIC_KEY.length].toInt()).toByte()
        }
        return bytesToHex(result)
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val result = ByteArray(hex.length / 2)
        for (i in 0 until hex.length step 2) {
            result[i / 2] = hex.substring(i, i + 2).toInt(16).toByte()
        }
        return result
    }
}
