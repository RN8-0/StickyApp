package com.sticly

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
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
    private const val KEY_FAVORITES_SYNCED = "favorites_synced" // İlk favori senkronizasyonu yapıldı mı
    private const val KEY_CUSTOM_PACKS_COUNT = "custom_packs_count"
    private const val KEY_TOTAL_STICKERS_ADDED = "total_stickers_added"
    private const val KEY_INITIAL_LOAD_DONE = "initial_load_done"
    private const val KEY_PACKS_SINCE_PROMO = "packs_since_promo"

    // ========== User Profile (Google login via PocketBase) ==========

    fun setUserProfile(context: Context, email: String?, displayName: String?, photoUrl: String?) {
        getPrefs(context).edit()
            .putString("user_email", email ?: "")
            .putString("user_display_name", displayName ?: "")
            .putString("user_photo_url", photoUrl ?: "")
            .apply()
    }

    fun syncUserDataWithPocketBase(context: Context, deviceId: String) {
        val prefs = getPrefs(context)
        val email = prefs.getString("user_email", "") ?: ""
        val displayName = prefs.getString("user_display_name", "") ?: ""
        val photoUrl = prefs.getString("user_photo_url", "") ?: ""
        if (email.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val pbUserId = PocketBaseHelper.getAuthRecordId().orEmpty()
                val filter = buildList {
                    add("email='${escapePb(email)}'")
                    add("device_id='${escapePb(deviceId)}'")
                    if (pbUserId.isNotBlank()) add("user_id='${escapePb(pbUserId)}'")
                }.joinToString(" || ")
                val existing = PocketBaseHelper.listRecords(
                    "user_profiles",
                    filter = filter,
                    perPage = 1
                )
                val data = org.json.JSONObject().apply {
                    put("device_id", deviceId)
                    if (pbUserId.isNotBlank()) {
                        put("uid", pbUserId)
                        put("user_id", pbUserId)
                    }
                    put("email", email)
                    put("display_name", displayName)
                    put("name", displayName)
                    put("photo_url", photoUrl)
                    put("last_sync", java.time.Instant.now().toString())
                }
                if (existing.isEmpty()) {
                    data.put("packs_published", 0)
                    data.put("total_downloads", 0)
                    data.put("total_favorites", 0)
                    PocketBaseHelper.createRecord("user_profiles", data)
                } else {
                    val id = existing.first().getString("id")
                    PocketBaseHelper.updateRecord("user_profiles", id, data)
                }
            } catch (e: Exception) {
                Log.e(TAG, "syncUserDataWithPocketBase failed: ${e.message}")
            }
        }
    }

    // In-memory caches to avoid repeated SharedPreferences disk reads during scrolling
    @Volatile private var installedPacksCache: Set<String>? = null
    @Volatile private var favoritePacksCache: Set<String>? = null

    fun invalidateCaches() {
        installedPacksCache = null
        favoritePacksCache = null
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun escapePb(value: String): String = value.replace("'", "\\'")

    private fun syncCurrentUserProfile(context: Context, data: JSONObject) {
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val uid = user?.uid ?: ""
        val email = user?.email ?: getPrefs(context).getString("user_email", "").orEmpty()
        val deviceId = getDeviceId(context)
        val pbUserId = PocketBaseHelper.getAuthRecordId().orEmpty()

        data.put("uid", uid.ifBlank { pbUserId })
        data.put("user_id", uid.ifBlank { pbUserId.ifBlank { email.ifBlank { deviceId } } })
        data.put("device_id", deviceId)
        if (email.isNotBlank()) data.put("email", email)
        data.put("last_sync", java.time.Instant.now().toString())

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val filter = buildList {
                    if (uid.isNotBlank()) {
                        add("uid='${escapePb(uid)}'")
                        add("user_id='${escapePb(uid)}'")
                    }
                    if (pbUserId.isNotBlank()) {
                        add("uid='${escapePb(pbUserId)}'")
                        add("user_id='${escapePb(pbUserId)}'")
                    }
                    if (email.isNotBlank()) add("email='${escapePb(email)}'")
                    add("device_id='${escapePb(deviceId)}'")
                }.joinToString(" || ")
                val existing = PocketBaseHelper.listRecords("user_profiles", filter = filter, perPage = 1)
                if (existing.isEmpty()) {
                    data.put("created_at", java.time.Instant.now().toString())
                    PocketBaseHelper.createRecord("user_profiles", data)
                } else {
                    PocketBaseHelper.updateRecord("user_profiles", existing.first().getString("id"), data)
                }
            } catch (e: Exception) {
                Log.e(TAG, "PocketBase user profile sync failed: ${e.message}")
            }
        }
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
        
        syncCurrentUserProfile(context, JSONObject().apply {
            put("is_premium", isPremium)
            put("premium_type", if (isPremium) getPremiumType(context) else "none")
            put("premium_expiry", if (isPremium) getPremiumExpiry(context) else 0L)
            put("subscription_source", if (isPremium) "local" else "none")
        })
    }

    fun setPremiumWithType(context: Context, type: String, expiryTimestamp: Long = 0L, source: String = "google_play") {
        getPrefs(context).edit()
            .putBoolean(KEY_PREMIUM, true)
            .putString(KEY_PREMIUM_TYPE, type)
            .putLong(KEY_PREMIUM_EXPIRY, expiryTimestamp)
            .apply()

        syncCurrentUserProfile(context, JSONObject().apply {
            put("is_premium", true)
            put("premium_type", type)
            put("premium_expiry", expiryTimestamp)
            put("subscription_source", source)
        })
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

    /**
     * Her 3 pakette bir mesaj göstermek için sayacı artır
     */
    fun incrementPacksSincePromo(context: Context) {
        val current = getPrefs(context).getInt(KEY_PACKS_SINCE_PROMO, 0)
        getPrefs(context).edit().putInt(KEY_PACKS_SINCE_PROMO, current + 1).apply()
    }

    /**
     * Bilgilendirme mesajı gösterilmeli mi?
     * Kullanıcı 2. paketi indirdikten sonra bir kez gösterilir, sonra sıfırlanır.
     */
    fun shouldShowSupportPromo(context: Context): Boolean {
        if (isPremium(context)) return false
        val count = getPrefs(context).getInt(KEY_PACKS_SINCE_PROMO, 0)
        return count >= 2
    }

    fun resetPacksSincePromo(context: Context) {
        getPrefs(context).edit().putInt(KEY_PACKS_SINCE_PROMO, 0).apply()
    }

    // İlk yükleme ekranı (yüzdelik) gösterildi mi
    fun isInitialLoadDone(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_INITIAL_LOAD_DONE, false)
    }

    fun setInitialLoadDone(context: Context) {
        getPrefs(context).edit().putBoolean(KEY_INITIAL_LOAD_DONE, true).apply()
    }

    fun wasNotificationPermissionAsked(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_NOTIFICATION_PERMISSION_ASKED, false)
    }

    fun setNotificationPermissionAsked(context: Context) {
        getPrefs(context).edit().putBoolean(KEY_NOTIFICATION_PERMISSION_ASKED, true).apply()
    }

    // Installed Packs
    @Synchronized
    fun getInstalledPacks(context: Context): Set<String> {
        installedPacksCache?.let { return it }
        val result = getPrefs(context).getStringSet(KEY_INSTALLED_PACKS, emptySet())?.toSet() ?: emptySet()
        installedPacksCache = result
        return result
    }

    @Synchronized
    fun addInstalledPack(context: Context, packId: String) {
        val current = HashSet(getInstalledPacks(context))
        current.add(packId)
        getPrefs(context).edit().putStringSet(KEY_INSTALLED_PACKS, current).commit()
        installedPacksCache = current
        
        // WhatsApp'a eklenen paket aynı zamanda favorilere eklenir
        if (!packId.startsWith("custom_")) {
            addFavoritePack(context, packId)
        }
    }

    @Synchronized
    fun removeInstalledPack(context: Context, packId: String) {
        val current = HashSet(getInstalledPacks(context))
        current.remove(packId)
        getPrefs(context).edit().putStringSet(KEY_INSTALLED_PACKS, current).commit()
        installedPacksCache = current
        
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
     * Kullanıcının pakete erişimi var mı?
     * Premium abonelik, satın alma VEYA reklam izleyerek geçici kilit açma
     */
    fun hasAccessToPack(context: Context, packId: String): Boolean {
        return isPremium(context) || isPackPurchased(context, packId) || isPackUnlocked(context, packId)
    }

    // ========== REWARDED AD: Süreli Kilit Açma (7 Gün) ==========
    private const val KEY_PACK_UNLOCK_PREFIX = "pack_unlock_"         // legacy permanent key
    private const val KEY_PACK_UNLOCK_EXPIRY_PREFIX = "pack_unlock_exp_"  // new expiry key
    private const val UNLOCK_DURATION_MS = 7 * 24 * 60 * 60 * 1000L  // 7 days

    /**
     * Paketi 7 gün boyunca aç (reklam izledikten sonra).
     * Eski kalıcı kilit varsa üzerine yazar — artık süreli olacak.
     */
    fun unlockPack(context: Context, packId: String) {
        val expiry = System.currentTimeMillis() + UNLOCK_DURATION_MS
        getPrefs(context).edit()
            .putLong(KEY_PACK_UNLOCK_EXPIRY_PREFIX + packId, expiry)
            .remove(KEY_PACK_UNLOCK_PREFIX + packId)  // eski kalıcı anahtarı sil
            .apply()
        Log.d(TAG, "Pack $packId unlocked for 7 days (expires: $expiry)")
    }

    /**
     * Paket reklam izlenerek açıldı mı ve süresi geçmedi mi?
     * Eski kalıcı (Boolean) formata da geriye dönük uyumlu.
     */
    fun isPackUnlocked(context: Context, packId: String): Boolean {
        val prefs = getPrefs(context)

        // Yeni format: expiry timestamp
        val expiry = prefs.getLong(KEY_PACK_UNLOCK_EXPIRY_PREFIX + packId, 0L)
        if (expiry > 0) {
            return System.currentTimeMillis() < expiry
        }

        // Eski format: kalıcı Boolean (migrate edeceğiz ama mevcut kullanıcılar için geçici destek)
        return try {
            val permanent = prefs.getBoolean(KEY_PACK_UNLOCK_PREFIX + packId, false)
            if (permanent) {
                // Yeni formata geç: şu andan itibaren 7 gün ver
                val migratedExpiry = System.currentTimeMillis() + UNLOCK_DURATION_MS
                prefs.edit()
                    .putLong(KEY_PACK_UNLOCK_EXPIRY_PREFIX + packId, migratedExpiry)
                    .remove(KEY_PACK_UNLOCK_PREFIX + packId)
                    .apply()
                true
            } else false
        } catch (e: ClassCastException) {
            val legacyValue = prefs.getLong(KEY_PACK_UNLOCK_PREFIX + packId, 0L)
            if (legacyValue > 0) {
                val migratedExpiry = System.currentTimeMillis() + UNLOCK_DURATION_MS
                prefs.edit()
                    .putLong(KEY_PACK_UNLOCK_EXPIRY_PREFIX + packId, migratedExpiry)
                    .remove(KEY_PACK_UNLOCK_PREFIX + packId)
                    .apply()
                true
            } else false
        }
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
    @Synchronized
    fun getFavoritePacks(context: Context): Set<String> {
        favoritePacksCache?.let { return it }
        val result = getPrefs(context).getStringSet(KEY_FAVORITE_PACKS, emptySet())?.toSet() ?: emptySet()
        favoritePacksCache = result
        return result
    }

    @Synchronized
    fun addFavoritePack(context: Context, packId: String) {
        val current = HashSet(getFavoritePacks(context))
        current.add(packId)
        getPrefs(context).edit().putStringSet(KEY_FAVORITE_PACKS, current).commit()
        favoritePacksCache = current

        // İlk favori ekleme - senkronizasyonu aktifleştir
        markFavoritesSynced(context)

        syncCurrentUserProfile(context, JSONObject().apply {
            put("favorite_packs", JSONArray(current.toList()))
        })
    }

    @Synchronized
    fun removeFavoritePack(context: Context, packId: String) {
        val current = HashSet(getFavoritePacks(context))
        current.remove(packId)
        getPrefs(context).edit().putStringSet(KEY_FAVORITE_PACKS, current).commit()
        favoritePacksCache = current

        syncCurrentUserProfile(context, JSONObject().apply {
            put("favorite_packs", JSONArray(current.toList()))
        })
    }

    fun isPackFavorite(context: Context, packId: String): Boolean {
        return getFavoritePacks(context).contains(packId)
    }

    @Synchronized
    fun toggleFavorite(context: Context, packId: String): Boolean {
        val isFav = isPackFavorite(context, packId)
        if (isFav) {
            removeFavoritePack(context, packId)
        } else {
            addFavoritePack(context, packId)
        }
        return !isFav
    }

    /**
     * Yerel favorileri temizler (ilk kurulumda veya sıfırlama için)
     */
    fun clearLocalFavorites(context: Context) {
        getPrefs(context).edit().remove(KEY_FAVORITE_PACKS).apply()
    }

    /**
     * Yerel kurulu paketleri temizler (ilk kurulumda veya sıfırlama için)
     */
    fun clearLocalInstalledPacks(context: Context) {
        getPrefs(context).edit().remove(KEY_INSTALLED_PACKS).apply()
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

                    // Favorileri senkronize et - SADECE kullanıcı daha önce senkronize edilmişse
                    // İlk kurulumda eski favorileri çekmeyi engelle
                    val wasSyncedBefore = getPrefs(context).getBoolean(KEY_FAVORITES_SYNCED, false)
                    if (wasSyncedBefore) {
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
    }

    /**
     * İlk favori ekleme işleminde çağrılır - senkronizasyonu aktifleştirir
     */
    fun markFavoritesSynced(context: Context) {
        getPrefs(context).edit().putBoolean(KEY_FAVORITES_SYNCED, true).apply()
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

    // ========== YENİ: Kullanıcı İstatistikleri ==========

    /**
     * Cihaz bilgilerini hashmap olarak döndürür
     */
    fun getDeviceInfo(context: Context): HashMap<String, Any> {
        val appVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }

        return hashMapOf(
            "model" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "os_version" to "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "app_version" to appVersion,
            "language" to java.util.Locale.getDefault().language
        )
    }

    /**
     * Toplam eklenen stiker sayısını döndürür
     */
    fun getTotalStickersAdded(context: Context): Int {
        return getPrefs(context).getInt(KEY_TOTAL_STICKERS_ADDED, 0)
    }

    /**
     * Toplam eklenen stiker sayısını artırır ve Firebase'e senkronize eder
     */
    fun incrementTotalStickersAdded(context: Context): Int {
        val newCount = getTotalStickersAdded(context) + 1
        getPrefs(context).edit().putInt(KEY_TOTAL_STICKERS_ADDED, newCount).apply()

        syncCurrentUserProfile(context, JSONObject().apply { put("total_stickers_added", newCount) })
        return newCount
    }

    /**
     * Özel paket sayısını döndürür
     */
    fun getCustomPacksCount(context: Context): Int {
        return getPrefs(context).getInt(KEY_CUSTOM_PACKS_COUNT, 0)
    }

    /**
     * Özel paket sayısını ayarlar ve Firebase'e senkronize eder
     */
    fun setCustomPacksCount(context: Context, count: Int) {
        getPrefs(context).edit().putInt(KEY_CUSTOM_PACKS_COUNT, count).apply()

        syncCurrentUserProfile(context, JSONObject().apply { put("custom_packs_count", count) })
    }

    /**
     * Özel paket sayısını artırır ve Firebase'e senkronize eder
     */
    fun incrementCustomPacksCount(context: Context): Int {
        val newCount = getCustomPacksCount(context) + 1
        setCustomPacksCount(context, newCount)
        return newCount
    }

    /**
     * Özel paket sayısını azaltır ve Firebase'e senkronize eder
     */
    fun decrementCustomPacksCount(context: Context): Int {
        val newCount = maxOf(0, getCustomPacksCount(context) - 1)
        setCustomPacksCount(context, newCount)
        return newCount
    }

    /**
     * Eski çağrı noktaları için adı korunur; veri artık PocketBase user_profiles'a yazılır.
     */
    fun syncUserDataWithFirebase(context: Context, uid: String) {
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val userEmail = currentUser?.email ?: ""
        val displayName = currentUser?.displayName ?: ""
        val photoUrl = currentUser?.photoUrl?.toString() ?: ""
        val localFavorites = getFavoritePacks(context).toList()

        syncCurrentUserProfile(context, JSONObject().apply {
            put("uid", uid)
            put("user_id", uid)
            put("email", userEmail)
            put("display_name", displayName)
            put("name", displayName)
            put("photo_url", photoUrl)
            put("device_info", JSONObject(getDeviceInfo(context) as Map<*, *>))
            put("total_stickers_added", getTotalStickersAdded(context))
            put("custom_packs_count", getCustomPacksCount(context))
            put("favorite_packs", JSONArray(localFavorites))
        })
    }
}

