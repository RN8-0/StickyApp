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
    private const val KEY_PREMIUM_TYPE = "premium_type" // "subscription", "none"
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
        
        // Sync to Firebase
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (user != null) {
            val data = hashMapOf(
                "is_premium" to isPremium,
                "premium_type" to if (isPremium) getPremiumType(context) else "none",
                "premium_expiry" to if (isPremium) getPremiumExpiry(context) else 0L,
                "email" to (user.email ?: ""),
                "last_sync" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            FirebaseFirestore.getInstance().collection("users").document(user.uid)
                .set(data, SetOptions.merge())
        }
    }

    fun setPremiumWithType(context: Context, type: String, expiryTimestamp: Long = 0L, source: String = "google_play") {
        getPrefs(context).edit()
            .putBoolean(KEY_PREMIUM, true)
            .putString(KEY_PREMIUM_TYPE, type)
            .putLong(KEY_PREMIUM_EXPIRY, expiryTimestamp)
            .apply()

        // Sync to Firebase
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (user != null) {
            val data = hashMapOf(
                "is_premium" to true,
                "premium_type" to type,
                "premium_expiry" to expiryTimestamp,
                "subscription_source" to source,
                "email" to (user.email ?: ""),
                "last_sync" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            FirebaseFirestore.getInstance().collection("users").document(user.uid)
                .set(data, SetOptions.merge())
        }
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
        
        // WhatsApp'a eklenen paket aynı zamanda favorilere eklenir ve Firebase'e senkronize edilir
        addFavoritePack(context, packId)
    }

    fun removeInstalledPack(context: Context, packId: String) {
        val current = HashSet(getInstalledPacks(context))
        current.remove(packId)
        getPrefs(context).edit().putStringSet(KEY_INSTALLED_PACKS, current).commit()
        
        // WhatsApp'tan kaldırılan paket favorilerden silinir ve Firebase'den düşer
        removeFavoritePack(context, packId)
    }

    fun isPackInstalled(context: Context, packId: String): Boolean {
        return getInstalledPacks(context).contains(packId)
    }

    // Purchased Packs (Artık kullanılmıyor ama eski veri bozulmaması için metotlar boş bırakılabilir veya local'de tutulabilir)
    fun getPurchasedPacks(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_PURCHASED_PACKS, emptySet())?.toSet() ?: emptySet()
    }

    fun addPurchasedPack(context: Context, packId: String) {
        val current = HashSet(getPurchasedPacks(context))
        current.add(packId)
        getPrefs(context).edit().putStringSet(KEY_PURCHASED_PACKS, current).commit()
        // Firebase sync removed for purchased packs as per request
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
        
        // Sync to Firebase
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (user != null) {
            FirebaseFirestore.getInstance().collection("users").document(user.uid)
                .update("favorite_packs", com.google.firebase.firestore.FieldValue.arrayUnion(packId))
        }
    }

    fun removeFavoritePack(context: Context, packId: String) {
        val current = HashSet(getFavoritePacks(context))
        current.remove(packId)
        getPrefs(context).edit().putStringSet(KEY_FAVORITE_PACKS, current).commit()

        // Sync to Firebase
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (user != null) {
            FirebaseFirestore.getInstance().collection("users").document(user.uid)
                .update("favorite_packs", com.google.firebase.firestore.FieldValue.arrayRemove(packId))
        }
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
        val defaultLang = java.util.Locale.getDefault().language
        return getPrefs(context).getString(KEY_LANGUAGE, defaultLang) ?: defaultLang
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
        // Method kept for compatibility but body can be disabled if we strictly want no pushes
        // Keeping it for legacy purchase tracking if ever needed, but user said "remove purchased packs section".
        // Use with caution. Since we disabled addPurchasedPack sync, this is the only other entry point.
        // Let's disable it effectively or just leave it but ensure syncUserDataWithFirebase doesn't use it.
        
        /* 
         * DISABLED TO COMPLY WITH USER REQUEST
         */
         /*
        val firestore = FirebaseFirestore.getInstance()
        val data = hashMapOf(
            "pack_id" to packId,
            "purchased_at" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        // ... (Disabled)
        */
    }

    /**
     * ProductId ile satın alınan paket (Legacy support helper)
     */
    fun savePurchasedPackByProductId(context: Context, productId: String, orderId: String?) {
        // Legacy support
    }

    // Firebase'den satın alınan paketleri geri yükle (orderId listesi ile)
    fun restorePurchasedPacksFromFirebase(context: Context, orderIds: List<String>, onComplete: (Boolean) -> Unit) {
         // This reads from "purchased_packs" collection which might still exist.
         // Allowed to read? User said "tekli çıkartma satın alma özelliği kaldırıldı".
         // Restore logic might still be valid for old users.
         // I'll leave the read logic intact as it doesn't POLLUTE the "users" collection which was the complaint.
         // The complaint was about "users" collection having "purchased_packs".
         
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
                        }
                    }
                    if (processedCount == orderIds.size) {
                        onComplete(restoredCount > 0)
                    }
                }
                .addOnFailureListener { e ->
                    processedCount++
                    if (processedCount == orderIds.size) {
                        onComplete(restoredCount > 0)
                    }
                }
        }
    }

    // Realtime Sync
    private var snapshotListener: com.google.firebase.firestore.ListenerRegistration? = null

    fun startRealtimeSync(context: Context, uid: String) {
        if (snapshotListener != null) return

        val firestore = FirebaseFirestore.getInstance()
        snapshotListener = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e(TAG, "Listen failed.", e)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    Log.d(TAG, "Realtime user update received")
                    val remoteIsPremium = snapshot.getBoolean("is_premium") ?: false
                    val type = snapshot.getString("premium_type") ?: "none"
                    val expiry = snapshot.getLong("premium_expiry") ?: 0L
                    
                    // Update local prefs only (do not sync back to avoid loop)
                    updateLocalPremiumStatus(context, remoteIsPremium, type, expiry)
                    
                    // Sync favorites
                    val remoteFavorites = snapshot.get("favorite_packs") as? List<String> ?: emptyList()
                    if (remoteFavorites.isNotEmpty()) {
                        val current = HashSet(getFavoritePacks(context))
                        if (!current.containsAll(remoteFavorites)) {
                             current.addAll(remoteFavorites)
                             getPrefs(context).edit().putStringSet(KEY_FAVORITE_PACKS, current).commit()
                        }
                    }
                }
            }
    }
    
    fun stopRealtimeSync() {
        snapshotListener?.remove()
        snapshotListener = null
    }

    fun updateLocalPremiumStatus(context: Context, isPremium: Boolean, type: String, expiry: Long) {
        getPrefs(context).edit()
            .putBoolean(KEY_PREMIUM, isPremium)
            .putString(KEY_PREMIUM_TYPE, type)
            .putLong(KEY_PREMIUM_EXPIRY, expiry)
            .apply()
    }

    /**
     * Kullanıcı verilerini Firebase ile senkronize eder.
     * Favoriler ve satın alınan paketleri yükler/indirir.
     */
    fun syncUserDataWithFirebase(context: Context, uid: String) {
         // This method is kept for legacy/manual sync if needed, 
         // but startRealtimeSync should be preferred for active session.
         // We can still do the initial Merge here.
         
        val firestore = FirebaseFirestore.getInstance()
        val userDoc = firestore.collection("users").document(uid)

        // 1. Yerel verileri Firebase'e yükle (Merge)
        val localFavorites = getFavoritePacks(context).toList()
        
        // Kullanıcı e-postasını al
        val userEmail = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: ""

        val syncData = hashMapOf(
            "favorite_packs" to com.google.firebase.firestore.FieldValue.arrayUnion(*localFavorites.toTypedArray()),
            // Do NOT overwrite premium status from local to remote if remote is the source of truth!
            // Only sync email and favorites upwards. 
            // Premium status should flow Downstream (Remote -> Local).
            // But if user just bought it locally (BillingManager), local is truth until synced.
            // BillingManager calls setPremium which syncs it.
            // So here we might overwrite remote if we are not careful?
            // Safer to NOT sync premium status UP here, unless we are sure.
            // But setPremium syncs it.
            // Let's keep it minimal here.
            "email" to userEmail,
            "last_sync" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        userDoc.set(syncData, SetOptions.merge())
            .addOnSuccessListener {
                Log.d(TAG, "Local data synced to Firebase for user: $uid")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Sync failed: ${e.message}")
            }
    }
}

