package com.albasel.tawsilaapp.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.*

object TTSHelper {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingText: String? = null

    fun init(context: Context) {
        if (tts == null) {
            // Use applicationContext to avoid memory leaks and ensure it lives outside activities
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = tts?.setLanguage(Locale("ar"))
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e("TTSHelper", "Arabic language not supported")
                    } else {
                        isInitialized = true
                        pendingText?.let {
                            speakInternal(it)
                            pendingText = null
                        }
                    }
                } else {
                    Log.e("TTSHelper", "Initialization failed")
                }
            }
        }
    }

    fun speak(context: Context, text: String) {
        if (tts == null) {
            pendingText = text
            init(context)
        } else if (!isInitialized) {
            pendingText = text
        } else {
            speakInternal(text)
        }
    }

    fun cleanTextForVoice(raw: String): String {
        var t = raw
        // Remove zip codes, long numeric sequences, or technical strings like "ثلاثة ملايين" or "3000000"
        t = t.replace(Regex("\\b\\d{4,}\\b"), "")
        t = t.replace(Regex("ثلاثة ملايين|مليون|3000000|4000000|5000000"), "")
        t = t.replace(Regex("قسم|مركز|محافظة|جمهورية|جمهورية مصر العربية"), "")
        t = t.replace(Regex("Unnamed Road|طريق غير مسمى|شارع غير مسمى"), "الشارع الحالي")
        t = t.replace(Regex("\\s+"), " ").trim()
        return t
    }

    private fun speakInternal(text: String) {
        val cleanText = cleanTextForVoice(text)
        if (cleanText.isNotBlank()) {
            tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "TTS_ID_${System.currentTimeMillis()}")
        }
    }

    fun stop() {
        tts?.stop()
        pendingText = null
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
