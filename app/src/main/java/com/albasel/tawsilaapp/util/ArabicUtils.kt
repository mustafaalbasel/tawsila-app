package com.albasel.tawsilaapp.util

import java.util.*

object ArabicUtils {
    fun normalize(text: String?): String {
        if (text == null) return ""
        var normalized = text.trim()
        // Remove Arabic Tashkeel
        normalized = normalized.replace(Regex("[\u064B-\u0652]"), "")
        // Normalize Alef variants: أ, إ, آ -> ا
        normalized = normalized.replace(Regex("[أإآ]"), "ا")
        // Normalize Taa Marbouta / Haa: ة -> ه
        normalized = normalized.replace("ة", "ه")
        // Normalize Yaa / Alef Maqsura: ى -> ي
        normalized = normalized.replace("ى", "ي")
        // Normalize Eastern Arabic numbers
        normalized = normalized
            .replace("٠", "0")
            .replace("١", "1")
            .replace("٢", "2")
            .replace("٣", "3")
            .replace("٤", "4")
            .replace("٥", "5")
            .replace("٦", "6")
            .replace("٧", "7")
            .replace("٨", "8")
            .replace("٩", "9")
        return normalized.lowercase(Locale.ROOT)
    }

    /**
     * Strip leading 'ال' for prefix-free word comparison
     */
    fun stripAlefLam(word: String): String {
        val norm = normalize(word)
        return if (norm.startsWith("ال") && norm.length > 3) norm.substring(2) else norm
    }

    /**
     * Smart Arabic word-by-word matching tolerant to typos, Alef/Hamza (أ/إ/آ/ا), ة/ه, ى/ي, and 'ال' prefix
     */
    fun smartMatch(query: String, target: String): Boolean {
        val nQuery = expandVoiceAndTypoVariants(query)
        val nTarget = expandVoiceAndTypoVariants(target)
        if (nQuery.isBlank()) return true

        val queryWords = nQuery.split(" ").filter { it.isNotBlank() }
        val targetWords = nTarget.split(" ").filter { it.isNotBlank() }

        return queryWords.all { qWord ->
            val qClean = normalize(qWord)
            val qBare = stripAlefLam(qWord)

            targetWords.any { tWord ->
                val tClean = normalize(tWord)
                val tBare = stripAlefLam(tWord)

                tClean.contains(qClean) || qClean.contains(tClean) ||
                (qBare.length >= 3 && tBare.contains(qBare)) ||
                (qClean.length >= 4 && tClean.length >= 4 && levenshteinDistance(qClean, tClean) <= 1)
            } || nTarget.contains(qClean)
        }
    }

    fun expandVoiceAndTypoVariants(text: String): String {
        var res = normalize(text)
        res = res.replace(Regex("\\bواحد\\b|\\bواحده\\b|\\bالاول\\b|\\bالاولى\\b|\\bالأول\\b|\\bالأولى\\b"), "1")
        res = res.replace(Regex("\\bاتنين\\b|\\bاثنان\\b|\\bاثنين\\b|\\bالثاني\\b|\\bالتاني\\b|\\bالثانيه\\b|\\bالتانيه\\b"), "2")
        res = res.replace(Regex("\\bتلاتة\\b|\\bتلاته\\b|\\bثلاثة\\b|\\bثلاثه\\b|\\bالثالث\\b|\\bالتالت\\b|\\bالثالثه\\b|\\bالتالثه\\b"), "3")
        res = res.replace(Regex("\\bاربعة\\b|\\bاربعه\\b|\\bالرابع\\b|\\bالرابعة\\b"), "4")
        res = res.replace(Regex("\\bخمسة\\b|\\bخمسه\\b|\\bالخامس\\b|\\bالخامسة\\b"), "5")
        res = res.replace(Regex("\\bستة\\b|\\bسته\\b|\\bالسادس\\b|\\bالسادسة\\b"), "6")
        res = res.replace(Regex("\\bسبعة\\b|\\bسبعه\\b|\\bالسابع\\b|\\bالسابعة\\b"), "7")
        res = res.replace(Regex("\\bتمانية\\b|\\bثمانية\\b|\\bثمانيه\\b|\\bالثامن\\b|\\bالثامنة\\b"), "8")
        res = res.replace(Regex("\\bتسعة\\b|\\bتسعه\\b|\\bالتاسع\\b|\\bالتاسعة\\b"), "9")
        res = res.replace(Regex("\\bعشرة\\b|\\bعشره\\b|\\bالعاشر\\b|\\bالعاشرة\\b"), "10")
        return res.trim()
    }

    fun stripCommonPrefixDescriptors(text: String): String {
        var res = expandVoiceAndTypoVariants(text)
        res = res.replace(Regex("\\bكومباوند\\b|\\bكمبوند\\b|\\bمشروع\\b|\\bمنطقه\\b|\\bمنطقة\\b|\\bمجمع\\b"), "")
        return res.trim()
    }

    fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[s1.length][s2.length]
    }
}
