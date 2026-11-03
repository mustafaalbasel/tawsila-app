package com.albasel.tawsilaapp.util

import android.content.Context
import android.util.Base64
import android.util.Log
import com.albasel.tawsilaapp.R
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

object FCMV1Helper {
    private const val TAG = "FCMV1Helper"
    private const val PROJECT_ID = "tawsilaapp-f37b1740"
    private val FCM_URL = "https://fcm.googleapis.com/v1/projects/$PROJECT_ID/messages:send"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient()

    private var cachedToken: String? = null
    private var tokenExpiryTime: Long = 0L
    private var appContext: Context? = null

    fun initContext(context: Context) {
        appContext = context.applicationContext
    }

    private fun decryptSecret(encodedB64: String, mask: Byte = 0x42): String {
        return try {
            val bytes = Base64.decode(encodedB64, Base64.NO_WRAP)
            val result = ByteArray(bytes.size)
            for (i in bytes.indices) {
                result[i] = (bytes[i].toInt() xor mask.toInt()).toByte()
            }
            String(result, Charsets.UTF_8)
        } catch (e: Exception) { "" }
    }

    private fun getObfuscatedClientEmail(): String {
        return decryptSecret("JCswJyAjMSdvIyYvKywxJilvJCAxNCECNiM1MSsuIyMyMm8kcXUgc3V2cmwrIy9sJTEnMDQrIScjISEtNyw2bCEtLw==")
    }

    private fun getObfuscatedPrivateKeyPem(): String {
        return decryptSecret("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")
    }

    private suspend fun getAccessToken(context: Context? = null): String? = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            if (cachedToken != null && now < tokenExpiryTime - 60_000) {
                return@withContext cachedToken
            }

            val ctx = context ?: appContext
            var clientEmail: String? = null
            var privateKeyPem: String? = null

            if (ctx != null) {
                try {
                    val rawId = ctx.resources.getIdentifier("service_account", "raw", ctx.packageName)
                    if (rawId != 0) {
                        val jsonStr = ctx.resources.openRawResource(rawId).bufferedReader().use { it.readText() }
                        val jsonObject = JsonParser.parseString(jsonStr).asJsonObject
                        if (jsonObject.has("client_email")) clientEmail = jsonObject.get("client_email").asString
                        if (jsonObject.has("private_key")) privateKeyPem = jsonObject.get("private_key").asString
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Raw service account read exception: ${e.message}")
                }
            }

            if (clientEmail.isNullOrBlank()) clientEmail = getObfuscatedClientEmail()
            if (privateKeyPem.isNullOrBlank()) privateKeyPem = getObfuscatedPrivateKeyPem()

            val jwt = generateJwtAssertion(clientEmail, privateKeyPem)

            val formBody = FormBody.Builder()
                .add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                .add("assertion", jwt)
                .build()

            val request = Request.Builder()
                .url("https://oauth2.googleapis.com/token")
                .post(formBody)
                .build()

            client.newCall(request).execute().use { response ->
                val respBody = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val respJson = JsonParser.parseString(respBody).asJsonObject
                    val token = respJson.get("access_token").asString
                    val expiresIn = respJson.get("expires_in")?.asLong ?: 3600L
                    cachedToken = token
                    tokenExpiryTime = now + (expiresIn * 1000)
                    Log.d(TAG, "OAuth2 Token acquired successfully!")
                    token
                } else {
                    Log.e(TAG, "OAuth2 Token Fetch Failed: Code ${response.code}, Body: $respBody")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "OAuth2 AccessToken Error: ${e.message}", e)
            null
        }
    }

    private fun generateJwtAssertion(clientEmail: String, privateKeyPem: String): String {
        val now = System.currentTimeMillis() / 1000
        val exp = now + 3600

        val header = JsonObject()
        header.addProperty("alg", "RS256")
        header.addProperty("typ", "JWT")

        val claims = JsonObject()
        claims.addProperty("iss", clientEmail)
        claims.addProperty("scope", "https://www.googleapis.com/auth/cloud-platform")
        claims.addProperty("aud", "https://oauth2.googleapis.com/token")
        claims.addProperty("exp", exp)
        claims.addProperty("iat", now)

        val headerBase64 = base64UrlEncode(header.toString().toByteArray(Charsets.UTF_8))
        val claimsBase64 = base64UrlEncode(claims.toString().toByteArray(Charsets.UTF_8))

        val signatureInput = "$headerBase64.$claimsBase64"
        val privateKey = getPrivateKeyFromPem(privateKeyPem)

        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(privateKey)
        signature.update(signatureInput.toByteArray(Charsets.UTF_8))
        val signatureBytes = signature.sign()
        val signatureBase64 = base64UrlEncode(signatureBytes)

        return "$signatureInput.$signatureBase64"
    }

    private fun getPrivateKeyFromPem(pem: String): PrivateKey {
        val cleanPem = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\n", "")
            .replace("\n", "")
            .replace("\r", "")
            .trim()
        val keyBytes = Base64.decode(cleanPem, Base64.DEFAULT)
        val spec = PKCS8EncodedKeySpec(keyBytes)
        val kf = KeyFactory.getInstance("RSA")
        return kf.generatePrivate(spec)
    }

    private fun base64UrlEncode(bytes: ByteArray): String {
        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }

    suspend fun sendNotificationWithContext(context: Context?, token: String, title: String, body: String, route: String? = null) {
        withContext(Dispatchers.IO) {
            try {
                if (token.isEmpty()) return@withContext
                val ctx = context ?: appContext ?: return@withContext

                val accessToken = getAccessToken(ctx) ?: return@withContext

                val root = JsonObject()
                val message = JsonObject()
                message.addProperty("token", token)

                // High priority for Android OS
                val android = JsonObject()
                android.addProperty("priority", "high")
                message.add("android", android)

                // Data payload ONLY - MyFirebaseMessagingService handles showing exactly ONE notification!
                val data = JsonObject()
                data.addProperty("title", title)
                data.addProperty("content", body)
                data.addProperty("body", body)
                data.addProperty("route", route ?: "")
                data.addProperty("destination_route", route ?: "")
                message.add("data", data)

                root.add("message", message)

                val requestBody = root.toString().toRequestBody(JSON_MEDIA_TYPE)
                val request = Request.Builder()
                    .url(FCM_URL)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    val respStr = response.body?.string() ?: ""
                    Log.d(TAG, "FCM V1 Response Code: ${response.code}, Body: $respStr")
                }
            } catch (e: Exception) {
                Log.e(TAG, "FCM V1 Send Error: ${e.message}", e)
            }
        }
    }

    suspend fun sendNotification(token: String, title: String, body: String, route: String? = null) {
        sendNotificationWithContext(null, token, title, body, route)
    }
}
