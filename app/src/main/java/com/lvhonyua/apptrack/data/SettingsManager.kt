package com.lvhonyua.apptrack.data

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SUPABASE_URL = "supabase_url"
        private const val KEY_SUPABASE_ANON_KEY = "supabase_anon_key"
        private const val KEY_TABLE_NAME = "supabase_table_name"
        private const val KEY_APP_PASSWORD = "app_password"
        private const val KEY_PASSWORD_ENABLED = "is_password_enabled"
    }

    var supabaseUrl: String
        get() = prefs.getString(KEY_SUPABASE_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SUPABASE_URL, value).apply()

    var supabaseAnonKey: String
        get() = prefs.getString(KEY_SUPABASE_ANON_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SUPABASE_ANON_KEY, value).apply()

    var tableName: String
        get() = prefs.getString(KEY_TABLE_NAME, "locations") ?: "locations"
        set(value) = prefs.edit().putString(KEY_TABLE_NAME, value).apply()

    var appPassword: String
        get() = prefs.getString(KEY_APP_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_APP_PASSWORD, value).apply()

    var isPasswordEnabled: Boolean
        get() = prefs.getBoolean(KEY_PASSWORD_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_PASSWORD_ENABLED, value).apply()

    fun isConfigured(): Boolean = supabaseUrl.isNotEmpty() && supabaseAnonKey.isNotEmpty()
}
