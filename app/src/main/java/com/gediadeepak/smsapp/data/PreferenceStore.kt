package com.gediadeepak.smsapp.data

import android.content.Context
import com.gediadeepak.smsapp.core.*

class PreferenceStore(context: Context) {
    private val storage = context.getSharedPreferences("sandesh_settings", Context.MODE_PRIVATE)
    fun load() = Preferences(
        personalLabel = storage.getString("personal", "Personal") ?: "Personal",
        workLabel = storage.getString("work", "Work") ?: "Work",
        suggestions = storage.getBoolean("suggestions", true),
        theme = when (val saved = storage.getString("theme", "BLUE")) {
            "LIGHT", "SYSTEM" -> ThemeMode.GREEN
            else -> runCatching { ThemeMode.valueOf(saved!!) }.getOrDefault(ThemeMode.BLUE)
        },
        defaultSubscriptionId = storage.getInt("default_subscription", -1).takeIf { it >= 0 },
    )
    fun save(preferences: Preferences) { storage.edit().putString("personal", preferences.personalLabel).putString("work", preferences.workLabel)
        .putBoolean("suggestions", preferences.suggestions).putString("theme", preferences.theme.name)
        .putInt("default_subscription", preferences.defaultSubscriptionId ?: -1).apply() }
}
