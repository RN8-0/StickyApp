package com.sticly

import android.content.Context
import android.content.SharedPreferences

object PreferencesHelper {
    private const val PREFS_NAME = "sticky_prefs"
    private const val KEY_PREMIUM = "is_premium"
    private const val KEY_NOTIFICATIONS = "notifications_enabled"
    private const val KEY_INSTALLED_PACKS = "installed_packs"
    private const val KEY_PURCHASED_PACKS = "purchased_packs"
    private const val KEY_STICKERS_ADDED_COUNT = "stickers_added_count"
    private const val KEY_PREMIUM_PROMO_SHOWN = "premium_promo_shown"
    private const val KEY_FAVORITE_PACKS = "favorite_packs"
    private const val KEY_SEARCH_HISTORY = "search_history"

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
        return getPrefs(context).getStringSet(KEY_INSTALLED_PACKS, emptySet())?.toSet() ?: emptySet()
    }

    fun addInstalledPack(context: Context, packId: String) {
        val current = HashSet(getInstalledPacks(context))
        current.add(packId)
        getPrefs(context).edit().putStringSet(KEY_INSTALLED_PACKS, current).commit()
    }

    fun removeInstalledPack(context: Context, packId: String) {
        val current = HashSet(getInstalledPacks(context))
        current.remove(packId)
        getPrefs(context).edit().putStringSet(KEY_INSTALLED_PACKS, current).commit()
    }

    fun isPackInstalled(context: Context, packId: String): Boolean {
        return getInstalledPacks(context).contains(packId)
    }

    // Purchased Packs (Satın alınmış premium sticker paketleri)
    fun getPurchasedPacks(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_PURCHASED_PACKS, emptySet())?.toSet() ?: emptySet()
    }

    fun addPurchasedPack(context: Context, packId: String) {
        val current = HashSet(getPurchasedPacks(context))
        current.add(packId)
        getPrefs(context).edit().putStringSet(KEY_PURCHASED_PACKS, current).commit()
    }

    fun isPackPurchased(context: Context, packId: String): Boolean {
        return getPurchasedPacks(context).contains(packId)
    }

    /**
     * Kullanıcının premium pakete erişimi var mı?
     * Premium abonelik varsa veya paketi satın almışsa true döner
     */
    fun hasAccessToPremiumPack(context: Context, packId: String): Boolean {
        return isPremium(context) || isPackPurchased(context, packId)
    }

    // Sticker Ekleme Sayacı
    fun getStickersAddedCount(context: Context): Int {
        return getPrefs(context).getInt(KEY_STICKERS_ADDED_COUNT, 0)
    }

    fun incrementStickersAddedCount(context: Context): Int {
        val newCount = getStickersAddedCount(context) + 1
        getPrefs(context).edit().putInt(KEY_STICKERS_ADDED_COUNT, newCount).apply()
        return newCount
    }

    /**
     * Premium promo gösterildi mi? (Her 4 sticker'da bir göster, ama aynı oturumda tekrar gösterme)
     */
    fun shouldShowPremiumPromo(context: Context): Boolean {
        if (isPremium(context)) return false
        val count = getStickersAddedCount(context)
        // İlk sticker'dan sonra ve her 4 sticker'da bir göster
        return count > 0 && count % 4 == 0
    }

    fun markPremiumPromoShown(context: Context, count: Int) {
        getPrefs(context).edit().putInt(KEY_PREMIUM_PROMO_SHOWN, count).apply()
    }

    fun wasPremiumPromoShownForCount(context: Context, count: Int): Boolean {
        return getPrefs(context).getInt(KEY_PREMIUM_PROMO_SHOWN, -1) == count
    }

    // Favorite Packs
    fun getFavoritePacks(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_FAVORITE_PACKS, emptySet())?.toSet() ?: emptySet()
    }

    fun addFavoritePack(context: Context, packId: String) {
        val current = HashSet(getFavoritePacks(context))
        current.add(packId)
        getPrefs(context).edit().putStringSet(KEY_FAVORITE_PACKS, current).commit()
    }

    fun removeFavoritePack(context: Context, packId: String) {
        val current = HashSet(getFavoritePacks(context))
        current.remove(packId)
        getPrefs(context).edit().putStringSet(KEY_FAVORITE_PACKS, current).commit()
    }

    fun isPackFavorite(context: Context, packId: String): Boolean {
        return getFavoritePacks(context).contains(packId)
    }

    fun toggleFavorite(context: Context, packId: String): Boolean {
        val isFav = isPackFavorite(context, packId)
        if (isFav) {
            removeFavoritePack(context, packId)
        } else {
            addFavoritePack(context, packId)
        }
        return !isFav
    }

    // Search History
    fun getSearchHistory(context: Context): List<String> {
        val historyString = getPrefs(context).getString(KEY_SEARCH_HISTORY, "") ?: ""
        return if (historyString.isEmpty()) emptyList() else historyString.split("||")
    }

    fun addSearchHistory(context: Context, query: String) {
        if (query.isBlank()) return
        val current = getSearchHistory(context).toMutableList()
        current.remove(query) // Varsa eski kaydı sil
        current.add(0, query) // Başa ekle
        val trimmed = current.take(10) // Max 10 kayıt
        getPrefs(context).edit().putString(KEY_SEARCH_HISTORY, trimmed.joinToString("||")).apply()
    }

    fun clearSearchHistory(context: Context) {
        getPrefs(context).edit().remove(KEY_SEARCH_HISTORY).apply()
    }
}
