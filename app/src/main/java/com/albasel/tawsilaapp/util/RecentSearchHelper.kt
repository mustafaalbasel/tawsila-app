package com.albasel.tawsilaapp.util

import android.content.Context
import com.albasel.tawsilaapp.ui.screens.home.RecentSearchItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object RecentSearchHelper {

    fun getRecentSearches(context: Context): List<RecentSearchItem> {
        val prefs = context.getSharedPreferences("tawsila_prefs", Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("recent_searches_json", null)
        if (!jsonStr.isNullOrBlank()) {
            return try {
                val type = object : TypeToken<List<RecentSearchItem>>() {}.type
                Gson().fromJson(jsonStr, type) ?: emptyList()
            } catch (e: Exception) { emptyList() }
        }
        return listOf(
            RecentSearchItem("الموقف الشرقي", "دمياط الجديدة، محافظة دمياط", 31.4510, 31.6770),
            RecentSearchItem("مستشفى دار الحياة", "دمياط الجديدة - الحي السادس", 31.4505, 31.6765)
        )
    }

    fun saveRecentSearch(context: Context, title: String, subtitle: String = "", lat: Double = 0.0, lng: Double = 0.0) {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank() || cleanTitle.contains("Location") || cleanTitle.contains("موقع العميل")) return

        val prefs = context.getSharedPreferences("tawsila_prefs", Context.MODE_PRIVATE)
        val currentList = getRecentSearches(context)

        val newItem = RecentSearchItem(cleanTitle, subtitle.ifBlank { "دمياط الجديدة، محافظة دمياط" }, lat, lng)
        val updatedList = (listOf(newItem) + currentList).distinctBy { it.title.trim() }.take(5)

        val jsonStr = Gson().toJson(updatedList)
        prefs.edit().putString("recent_searches_json", jsonStr).apply()
    }

    fun deleteRecentSearch(context: Context, item: RecentSearchItem) {
        val prefs = context.getSharedPreferences("tawsila_prefs", Context.MODE_PRIVATE)
        val currentList = getRecentSearches(context)

        val updatedList = currentList.filter { it.title.trim() != item.title.trim() }
        val jsonStr = Gson().toJson(updatedList)
        prefs.edit().putString("recent_searches_json", jsonStr).apply()
    }
}
