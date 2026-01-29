package com.sticly

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.UUID

object PreferencesHelper {
    private const val TAG = "PreferencesHelper"
    private const val PREFS_NAME = "sticky_prefs"
    private const val KEY_PREMIUM = "is_premium"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_NOTIFICATIONS = "notifications_enabled"
    private const val KEY_INSTALLED_PACKS = "installed_packs"
    private const val KEY_PURCHASED_PACKS = "purchased_packs"
    private const val KEY_STICKERS_ADDED_COUNT = "stickers_added_count"
    private const val KEY_PREMIUM_PROMO_SHOWN = "premium_promo_shown"
    private const val KEY_FAVORITE_PACKS = "favorite_packs"
    private const val KEY_SEARCH_HISTORY = "search_history"
    private const val KEY_FIRST_LAUNCH = "is_first_launch"
    private const val KEY_NOTIFICATION_PERMISSION_ASKED = "notification_permission_asked"
    private const val KEY_LANGUAGE = "app_language"
    private const val KEY_PREMIUM_TYPE = "premium_type" // "subscription", "lifetime", "none"
    private const val KEY_PREMIUM_EXPIRY = "premium_expiry" // timestamp in millis

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Premium
    fun isPremium(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_PREMIUM, false)
    }

    fun setPremium(context: Context, isPremium: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_PREMIUM, isPremium).apply()
        if (!isPremium) {
            setPremiumType(context, "none")
            setPremiumExpiry(context, 0L)
        }
    }

    fun setPremiumWithType(context: Context, type: String, expiryTimestamp: Long = 0L) {
        getPrefs(context).edit()
            .putBoolean(KEY_PREMIUM, true)
            .putString(KEY_PREMIUM_TYPE, type)
            .putLong(KEY_PREMIUM_EXPIRY, expiryTimestamp)
            .apply()
    }

    fun getPremiumType(context: Context): String {
        return getPrefs(context).getString(KEY_PREMIUM_TYPE, "none") ?: "none"
    }

    fun setPremiumType(context: Context, type: String) {
        getPrefs(context).edit().putString(KEY_PREMIUM_TYPE, type).apply()
    }

    fun getPremiumExpiry(context: Context): Long {
        return getPrefs(context).getLong(KEY_PREMIUM_EXPIRY, 0L)
    }

    fun setPremiumExpiry(context: Context, expiry: Long) {
        getPrefs(context).edit().putLong(KEY_PREMIUM_EXPIRY, expiry).apply()
    }

    // Notifications
    fun isNotificationsEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_NOTIFICATIONS, true)
    }

    fun setNotificationsEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_NOTIFICATIONS, enabled).apply()
    }

    // First Launch / İlk Açılış
    fun isFirstLaunch(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_FIRST_LAUNCH, true)
    }

    fun setFirstLaunchComplete(context: Context) {
        getPrefs(context).edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
    }

    fun wasNotificationPermissionAsked(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_NOTIFICATION_PERMISSION_ASKED, false)
    }

    fun setNotificationPermissionAsked(context: Context) {
        getPrefs(context).edit().putBoolean(KEY_NOTIFICATION_PERMISSION_ASKED, true).apply()
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

    // Language
    fun getLanguage(context: Context): String {
        return getPrefs(context).getString(KEY_LANGUAGE, "") ?: ""
    }

    fun setLanguage(context: Context, languageCode: String) {
        getPrefs(context).edit().putString(KEY_LANGUAGE, languageCode).apply()
    }

    // Device ID - Satın alma senkronizasyonu için benzersiz cihaz kimliği
    fun getDeviceId(context: Context): String {
        var deviceId = getPrefs(context).getString(KEY_DEVICE_ID, null)
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString()
            getPrefs(context).edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }
        return deviceId
    }

    // Firebase'e satın alınan paket kaydet (orderId bazlı - restore için)
    fun savePurchasedPackToFirebase(context: Context, packId: String, orderId: String? = null) {
        val firestore = FirebaseFirestore.getInstance()

        val data = hashMapOf(
            "pack_id" to packId,
            "purchased_at" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        // orderId varsa ekle (Google Play purchase orderId)
        if (!orderId.isNullOrEmpty()) {
            data["order_id"] = orderId
        }

        // Device ID'yi de kaydet (fallback için)
        data["device_id"] = getDeviceId(context)

        // orderId varsa onu kullan, yoksa device_id + packId kullan
        val docId = if (!orderId.isNullOrEmpty()) {
            orderId
        } else {
            "${getDeviceId(context)}_${packId}"
        }

        firestore.collection("purchased_packs")
            .document(docId)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                // Log.d(TAG, "Purchase saved to Firebase: $packId")
            }
            .addOnFailureListener { e ->
                // Log.e(TAG, "Error saving purchase to Firebase: ${e.message}")
            }
    }

    /**
     * ProductId ile satın alınan paketi kaydet.
     * Firebase'den productId'ye sahip paketi bulup packId'yi kaydeder.
     */
    fun savePurchasedPackByProductId(context: Context, productId: String, orderId: String?) {
        val firestore = FirebaseFirestore.getInstance()

        // Premium stickers koleksiyonunda productId ile eşleşen paketi bul
        firestore.collection("premium_stickers")
            .whereEqualTo("product_id", productId)
            .get()
            .addOnSuccessListener { snapshot ->
                val doc = snapshot.documents.firstOrNull()
                if (doc != null) {
                    val packId = doc.id
                    addPurchasedPack(context, packId)
                    Log.d(TAG, "Found and saved pack: $packId for productId: $productId")

                    // Firebase'e de kaydet (restore için)
                    savePurchasedPackToFirebase(context, packId, orderId)
                } else {
                    Log.w(TAG, "No pack found with productId: $productId")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error finding pack by productId: ${e.message}")
            }
    }

    // Firebase'den satın alınan paketleri geri yükle (orderId listesi ile)
    fun restorePurchasedPacksFromFirebase(context: Context, orderIds: List<String>, onComplete: (Boolean) -> Unit) {
        if (orderIds.isEmpty()) {
            onComplete(false)
            return
        }

        val firestore = FirebaseFirestore.getInstance()
        var restoredCount = 0
        var processedCount = 0

        for (orderId in orderIds) {
            firestore.collection("purchased_packs")
                .document(orderId)
                .get()
                .addOnSuccessListener { doc ->
                    processedCount++
                    if (doc.exists()) {
                        val packId = doc.getString("pack_id")
                        if (packId != null && !isPackPurchased(context, packId)) {
                            addPurchasedPack(context, packId)
                            restoredCount++
                            // Log.d(TAG, "Restored purchase from Firebase: $packId")
                        }
                    }
                    if (processedCount == orderIds.size) {
                        // Log.d(TAG, "Restored $restoredCount packs from Firebase")
                        onComplete(restoredCount > 0)
                    }
                }
                .addOnFailureListener { e ->
                    processedCount++
                    // Log.e(TAG, "Error restoring purchase from Firebase: ${e.message}")
                    if (processedCount == orderIds.size) {
                        onComplete(restoredCount > 0)
                    }
                }
        }
    }
}
