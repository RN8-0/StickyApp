package com.sticly

import android.content.Context
import android.content.SharedPreferences

object PreferencesHelper {
    private const val PREFS_NAME = "sticky_prefs"
    private const val KEY_PREMIUM = "is_premium"
    private const val KEY_NOTIFICATIONS = "notifications_enabled"
    private const val KEY_INSTALLED_PACKS = "installed_packs"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Premium
    fun isPremium(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_PREMIUM, false)
    }

    fun setPremium(context: Context, isPremium: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_PREMIUM, isPremium).apply()
    }

    // Notifications
    fun isNotificationsEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_NOTIFICATIONS, true)
    }

    fun setNotificationsEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_NOTIFICATIONS, enabled).apply()
    }

    // Installed Packs
    fun getInstalledPacks(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_INSTALLED_PACKS, emptySet()) ?: emptySet()
    }

    fun addInstalledPack(context: Context, packId: String) {
        val current = getInstalledPacks(context).toMutableSet()
        current.add(packId)
        getPrefs(context).edit().putStringSet(KEY_INSTALLED_PACKS, current).apply()
    }

    fun removeInstalledPack(context: Context, packId: String) {
        val current = getInstalledPacks(context).toMutableSet()
        current.remove(packId)
        getPrefs(context).edit().putStringSet(KEY_INSTALLED_PACKS, current).apply()
    }

    fun isPackInstalled(context: Context, packId: String): Boolean {
        return getInstalledPacks(context).contains(packId)
    }
}
