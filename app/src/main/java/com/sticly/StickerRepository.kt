package com.sticly

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * PocketBase'den sticker paketlerini yükleyen repository
 */
object StickerRepository {

    private const val TAG = "StickerRepository"
    private const val STORAGE_PATH = "stickers"
    private const val CONTENTS_FILE = "contents.json"
    private const val CACHE_DIR = "sticker_cache"
    private const val STICKY_IMAGES_BASE = "https://sticky-images.46.225.95.201.sslip.io/stickers"

    private val firestore = FirebaseFirestore.getInstance()
    
    // Repository scope for long-running observers and background tasks
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    /**
     * KRITIK: Tüm paketlerin statik cache'i. 
     * StickerProvider (farklı bir thread/zamanlama) buradan veriyi senkron olarak okuyabilir.
     */
    var allPacksCache: List<Pack> = emptyList()
    
    private val _packsUpdateFlow = MutableSharedFlow<List<Pack>>(replay = 1)
    val packsUpdateFlow = _packsUpdateFlow.asSharedFlow()

    private var cachedFirestorePacks: List<Pack>? = null
    private var lastCacheTime: Long = 0
    private const val CACHE_EXPIRY = 1 * 60 * 1000 // 1 minute
    
    private var stickersListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var premiumStickersListener: com.google.firebase.firestore.ListenerRegistration? = null

    /**
     * Tüm paketleri yükler (Firebase + Özel + Lokal Assets)
     */
    suspend fun loadPacks(context: Context, forceRefresh: Boolean = false): List<Pack> = withContext(Dispatchers.IO) {
        // Helper: Paketleri karışık sırala (ID hash'ine göre tutarlı sıralama)
        fun shufflePacks(packs: List<Pack>) = packs.sortedBy { it.id.hashCode() }
        
        if (!forceRefresh && allPacksCache.isNotEmpty()) {
            return@withContext allPacksCache
        }

        val allPacks = mutableListOf<Pack>()

        try {
            // 1. Kullanıcının oluşturduğu özel paketleri yükle
            val customPacks = CustomStickerManager.getCustomPacks(context).mapNotNull { cp ->
                CustomStickerManager.toWhatsAppPack(context, cp.id)?.copy(category = "custom")
            }
            allPacks.addAll(customPacks)
            Log.d(TAG, "Loaded ${customPacks.size} custom packs")

            // 2. Paketleri yükle: Önce PocketBase, yoksa Firestore, yoksa Storage
            val packsFromPocketBase = loadPacksFromPocketBase()
            if (packsFromPocketBase.isNotEmpty()) {
                Log.d(TAG, "Loaded ${packsFromPocketBase.size} packs from PocketBase")
                allPacks.addAll(packsFromPocketBase)
            } else {
                val packsFromFirestore = loadPacksFromFirestore(forceRefresh)
                if (packsFromFirestore.isNotEmpty()) {
                    Log.d(TAG, "Loaded ${packsFromFirestore.size} packs from Firestore")
                    allPacks.addAll(packsFromFirestore)
                } else {
                    // İkisi de boşsa Storage'dan contents.json'u çek
                    val packsFromStorage = loadPacksFromStorage(context)
                    if (packsFromStorage.isNotEmpty()) {
                        Log.d(TAG, "Loaded ${packsFromStorage.size} packs from Storage")
                        allPacks.addAll(packsFromStorage)
                    }
                }
            }

            // 3. HER ZAMAN lokal asset paketlerini ekle (Firebase ile çakışmayanları)
            val existingIds = allPacks.map { it.id }.toSet()
            val localPacks = try {
                Loader.load(context)?.filter { it.id !in existingIds } ?: emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Error loading local packs: ${e.message}")
                emptyList()
            }
            Log.d(TAG, "Loaded ${localPacks.size} local asset packs")
            allPacks.addAll(localPacks)

            val result = shufflePacks(allPacks)
            allPacksCache = result // Statik cache'i güncelle
            saveCacheToDisk(context, result) // Diske kaydet (Provider için)
            return@withContext result

        } catch (e: Exception) {
            Log.e(TAG, "Error loading packs: ${e.message}")
            // Hata durumunda disk cache'ini dene
            val diskCache = loadCacheFromDisk(context)
            if (diskCache.isNotEmpty()) {
                allPacksCache = diskCache
                return@withContext diskCache
            }
            
            // Disk de boşsa lokal assets'ten yükle
            val localPacks = try {
                Loader.load(context) ?: emptyList()
            } catch (ex: Exception) {
                emptyList()
            }
            val result = shufflePacks(localPacks + allPacks)
            allPacksCache = result // Statik cache'i güncelle
            return@withContext result
        }
    }

    fun saveCacheToDisk(context: Context, packs: List<Pack>) {
        try {
            val file = File(context.filesDir, "packs_cache.json")
            file.writeText(Gson().toJson(packs))
        } catch (e: Exception) {
            Log.e(TAG, "Error saving cache to disk: ${e.message}")
        }
    }

    fun loadCacheFromDisk(context: Context): List<Pack> {
        return try {
            val file = File(context.filesDir, "packs_cache.json")
            if (file.exists()) {
                val json = file.readText()
                Gson().fromJson(json, Array<Pack>::class.java).toList()
            } else emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading cache from disk: ${e.message}")
            emptyList()
        }
    }

    // ============================================================================
    // POCKETBASE LOADING
    // ============================================================================

    private suspend fun loadPacksFromPocketBase(): List<Pack> = withContext(Dispatchers.IO) {
        val allPacks = mutableListOf<Pack>()
        try {
            val stickersJob = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).async {
                try { PocketBaseHelper.listRecords("stickers", perPage = 500) }
                catch (e: Exception) { Log.e(TAG, "PB stickers error: ${e.message}"); emptyList() }
            }
            val premiumJob = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).async {
                try { PocketBaseHelper.listRecords("premium_stickers", perPage = 500) }
                catch (e: Exception) { Log.e(TAG, "PB premium error: ${e.message}"); emptyList() }
            }
            stickersJob.await().mapNotNull { parsePocketBasePack(it, false) }.let { allPacks.addAll(it) }
            premiumJob.await().mapNotNull { parsePocketBasePack(it, true) }.let { allPacks.addAll(it) }
            Log.d(TAG, "PocketBase: ${allPacks.size} packs loaded")
        } catch (e: Exception) {
            Log.e(TAG, "PocketBase loading failed: ${e.message}")
        }
        allPacks
    }

    private fun parsePocketBasePack(json: org.json.JSONObject, isPremium: Boolean): Pack? {
        return try {
            val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
            val collection = if (isPremium) "premium_stickers" else "stickers"
            val stickersJson = json.optJSONArray("stickers") ?: return null
            val stickers = (0 until stickersJson.length()).mapNotNull { i ->
                val s = stickersJson.optJSONObject(i) ?: return@mapNotNull null
                val file = s.optString("image_file").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val url = normalizeStickerUrl(s.optString("url"), id, file, collection)
                val emojisArr = s.optJSONArray("emojis")
                val emojis = emojisArr?.let { arr -> (0 until arr.length()).map { arr.optString(it) } }
                Sticker(file = file, emojis = emojis, url = url)
            }
            if (stickers.isEmpty()) return null
            val translations = mutableMapOf<String, String>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key.startsWith("name_")) {
                    val value = json.optString(key)
                    if (value.isNotBlank()) translations[key.substringAfter("name_")] = value
                }
            }
            val primaryName = json.optString("name").ifBlank {
                json.optString("pack_name").ifBlank { id }
            }
            Pack(
                id = id,
                name = primaryName,
                nameTr = json.optString("name_tr").ifBlank { json.optString("name_turkish") },
                nameZh = json.optString("name_zh").ifBlank { json.optString("name_chinese") },
                nameEs = json.optString("name_es").ifBlank { json.optString("name_spanish") },
                nameAr = json.optString("name_ar").ifBlank { json.optString("name_arabic") },
                nameHi = json.optString("name_hi").ifBlank { json.optString("name_hindi") },
                namePt = json.optString("name_pt").ifBlank { json.optString("name_portuguese") },
                pub = json.optString("publisher").ifBlank { json.optString("publisher_name").ifBlank { "Sticky" } },
                email = json.optString("publisher_email").ifBlank { "contact@sticky.com" },
                privacy = json.optString("privacy_policy_website"),
                license = json.optString("license_agreement_website"),
                version = json.optString("image_data_version").ifBlank { "1" },
                avoidCache = json.optBoolean("avoid_cache", false),
                tray = json.optString("tray_image_file").ifBlank { "tray.webp" },
                trayUrl = normalizeStickerUrl(json.optString("tray_url"), id, json.optString("tray_image_file").ifBlank { "tray.webp" }, collection),
                stickers = stickers,
                isPremium = isPremium,
                productId = json.optString("product_id"),
                storagePath = if (isPremium) "premium_stickers" else "stickers",
                createdAt = json.optString("created"),
                category = json.optString("category"),
                publisherPhotoUrl = json.optString("publisher_photo_url"),
                downloadCount = json.optInt("download_count", 0),
                fakeDownloadBase = json.optInt("fake_download_base", 0),
                viewCount = json.optInt("view_count", 0),
                favoriteCount = json.optInt("favorite_count", 0),
                isAnimated = json.optBoolean("is_animated", false),
                isActive = json.optBoolean("is_active", true),
                isPopular = json.optBoolean("is_popular", false),
                priceTRY = json.optString("price_try"),
                priceUSD = json.optString("price_usd"),
                priceEUR = json.optString("price_eur"),
                translations = translations
            )
        } catch (e: Exception) {
            Log.e(TAG, "PocketBase pack parse error: ${e.message}")
            null
        }
    }

    /**
     * Firestore gerçek zamanlı takip - devre dışı (Firestore kaldırıldı, PocketBase kullanılıyor)
     */
    fun startObservingPacks(context: Context) {
        // Firestore kaldırıldı - gereksiz listener başlatma
        Log.d(TAG, "startObservingPacks: Firestore kaldırıldığından listener başlatılmıyor")
    }

    fun stopObservingPacks() {
        stickersListener?.remove()
        premiumStickersListener?.remove()
        stickersListener = null
        premiumStickersListener = null
    }

    // Debounce real-time refresh to avoid cascading reloads
    private var lastRefreshTime = 0L
    private var pendingRefreshJob: Job? = null

    private fun triggerRefresh(context: Context) {
        pendingRefreshJob?.cancel()
        pendingRefreshJob = repositoryScope.launch {
            // Debounce: wait 5 seconds before refreshing (coalesce rapid updates)
            delay(5000)
            val now = System.currentTimeMillis()
            if (now - lastRefreshTime < 10_000) return@launch // Skip if refreshed recently
            lastRefreshTime = now
            try {
                val packs = withContext(Dispatchers.IO) {
                    loadPacks(context, forceRefresh = true)
                }
                _packsUpdateFlow.emit(packs)
                Log.d(TAG, "Real-time refresh successful: ${packs.size} packs emitted")
            } catch (e: Exception) {
                Log.e(TAG, "Error during real-time refresh: ${e.message}")
            }
        }
    }

    /**
     * Firestore'dan paket listesini yükler
     */
    private suspend fun loadPacksFromFirestore(forceRefresh: Boolean = false): List<Pack> {
        // Return cache if valid
        if (!forceRefresh && cachedFirestorePacks != null && System.currentTimeMillis() - lastCacheTime < CACHE_EXPIRY) {
            Log.d(TAG, "Returning cached Firestore packs (${cachedFirestorePacks?.size})")
            return cachedFirestorePacks!!
        }

        val allPacks = mutableListOf<Pack>()
        // İlk açılışta hızlı yüklenme için cache kullan, forceRefresh varsa sunucudan çek
        val source = if (forceRefresh) com.google.firebase.firestore.Source.SERVER else com.google.firebase.firestore.Source.DEFAULT

        try {
            Log.d(TAG, "Loading packs from Firestore using source: ${source.name}...")

            // Normal ve Premium paketleri PARALEL yükle (2x hızlı)
            val stickersDeferred = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).async {
                try {
                    val snapshot = firestore.collection("stickers").get(source).await()
                    Log.d(TAG, "Stickers collection: ${snapshot.documents.size} documents")
                    snapshot.documents.mapNotNull { doc -> parsePackDocument(doc, isPremiumOverride = false) }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading stickers collection: ${e.message}")
                    emptyList()
                }
            }
            val premiumDeferred = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).async {
                try {
                    val snapshot = firestore.collection("premium_stickers").get(source).await()
                    Log.d(TAG, "Premium_stickers collection: ${snapshot.documents.size} documents")
                    snapshot.documents.mapNotNull { doc -> parsePackDocument(doc, isPremiumOverride = true) }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading premium_stickers collection: ${e.message}")
                    emptyList()
                }
            }

            allPacks.addAll(stickersDeferred.await())
            allPacks.addAll(premiumDeferred.await())

            // Eski koleksiyonu da kontrol et (geriye uyumluluk)
            if (allPacks.isEmpty()) {
                try {
                    val oldSnapshot = firestore.collection("sticker_packs").get(source).await()
                    Log.d(TAG, "Old sticker_packs collection: ${oldSnapshot.documents.size} documents")
                    oldSnapshot.documents.mapNotNull { doc ->
                        parsePackDocument(doc, isPremiumOverride = null)
                    }.let { allPacks.addAll(it) }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading sticker_packs collection: ${e.message}")
                }
            }

            Log.d(TAG, "Total packs loaded: ${allPacks.size}")
            
            // Update cache
            if (allPacks.isNotEmpty()) {
                cachedFirestorePacks = allPacks
                lastCacheTime = System.currentTimeMillis()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error loading from Firestore: ${e.message}")
        }

        return allPacks
    }

    private fun parsePackDocument(doc: com.google.firebase.firestore.DocumentSnapshot, isPremiumOverride: Boolean?): Pack? {
        return try {
            val data = doc.data ?: return null
            val stickers = parseStickers(data["stickers"])
            val isPremium = isPremiumOverride ?: (data["isPremium"] as? Boolean ?: false)

            val isActive = data["is_active"] as? Boolean ?: true
            Log.d(TAG, "Pack ${doc.id}: ${stickers.size} stickers, isPremium: $isPremium, isActive: $isActive")
            if (stickers.isNotEmpty()) {
                Log.d(TAG, "First sticker URL: ${stickers.first().url.take(80)}...")
            }

            // WhatsApp zorunlu alanlar için varsayılan değerler
            val publisher = (data["publisher"] as? String).takeIf { !it.isNullOrBlank() } ?: "Sticky"
            val email = (data["publisher_email"] as? String).takeIf { !it.isNullOrBlank() } ?: "contact@sticky.com"
            val privacy = (data["privacy_policy_website"] as? String).takeIf { !it.isNullOrBlank() } ?: "https://sticky.com/privacy"

            val translations = data.filterKeys { it.startsWith("name_") }
                .mapValues { it.value?.toString() ?: "" }
                .mapKeys { it.key.substringAfter("name_") }

            Pack(
                id = doc.id,
                name = data["name"] as? String ?: doc.id.replace("_", " ").replaceFirstChar { it.uppercase() },
                nameTr = data["name_tr"] as? String ?: "",
                nameZh = data["name_zh"] as? String ?: "",
                nameEs = data["name_es"] as? String ?: "",
                nameAr = data["name_ar"] as? String ?: "",
                nameHi = data["name_hi"] as? String ?: "",
                namePt = data["name_pt"] as? String ?: "",
                translations = translations,
                pub = publisher,
                email = email,
                privacy = privacy,
                license = data["license_agreement_website"] as? String ?: "",
                version = data["image_data_version"] as? String ?: "1",
                avoidCache = data["avoid_cache"] as? Boolean ?: false,
                tray = data["tray_image_file"] as? String ?: "tray.webp",
                trayUrl = data["tray_url"] as? String ?: "",
                stickers = stickers,
                isPremium = isPremium,
                productId = data["product_id"] as? String ?: "",
                storagePath = "stickers", // Tüm dosyalar tek klasörde
                createdAt = when (val time = data["created_at"]) {
                    is com.google.firebase.Timestamp -> {
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
                        sdf.format(time.toDate())
                    }
                    is String -> time
                    else -> ""
                },
                category = data["category"] as? String ?: "",
                downloadCount = (data["download_count"] as? Long)?.toInt() ?: 0,
                fakeDownloadBase = (data["fake_download_base"] as? Long)?.toInt() ?: 0,
                viewCount = (data["view_count"] as? Long)?.toInt() ?: 0,
                favoriteCount = (data["favorite_count"] as? Long)?.toInt() ?: 0,
                isAnimated = (data["is_animated"] as? Boolean) ?: (data["animated_sticker_pack"] as? Boolean) ?: false,
                isActive = data["is_active"] as? Boolean ?: true,
                isPopular = data["is_popular"] as? Boolean ?: false,
                priceTRY = data["price_try"] as? String ?: "",
                priceUSD = data["price_usd"] as? String ?: "",
                priceEUR = data["price_eur"] as? String ?: ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing pack ${doc.id}: ${e.message}")
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseStickers(data: Any?): List<Sticker> {
        if (data == null) return emptyList()
        return try {
            (data as? List<*>)?.mapNotNull { item ->
                val stickerData = item as? Map<String, Any> ?: return@mapNotNull null
                Sticker(
                    file = stickerData["image_file"] as? String ?: "",
                    emojis = (stickerData["emojis"] as? List<String>),
                    url = stickerData["url"] as? String ?: ""
                )
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun normalizeStickerUrl(url: String, packId: String, fileName: String, collection: String = "stickers"): String {
        val trimmed = url.trim()
        // Already a CDN or PocketBase URL → use as-is
        if (trimmed.contains("sticky-images.46.225.95.201.sslip.io")) return trimmed
        if (trimmed.contains("sslip.io") || trimmed.contains("api/files")) return trimmed

        if (trimmed.contains("firebasestorage.googleapis.com")) {
            // Migrate Firebase URLs to PocketBase file API
            return "${PocketBaseHelper.PB_URL}/api/files/$collection/$packId/$fileName"
        }

        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        // Build PocketBase file API URL as default
        return "${PocketBaseHelper.PB_URL}/api/files/$collection/$packId/$fileName"
    }

    /**
     * Legacy: Previously loaded from Firebase Storage. Now returns empty (PocketBase is primary).
     */
    @Suppress("UnusedPrivateMember")
    private suspend fun loadPacksFromStorage(context: Context): List<Pack> = emptyList()

    suspend fun getDownloadUrl(path: String): String {
        return "${PocketBaseHelper.PB_URL}/api/files/$path"
    }

    /**
     * Firebase Storage URL'ini HIZLI hesaplar (API çağrısı yapmaz)
     * Public read izni olan dosyalar için çalışır
     */
    private val urlCache = java.util.concurrent.ConcurrentHashMap<String, String>(256)

    fun getDirectStorageUrl(storagePath: String, packId: String, fileName: String): String {
        val key = "$packId/$fileName"
        urlCache[key]?.let { return it }
        val url = "${PocketBaseHelper.PB_URL}/api/files/$storagePath/$packId/$fileName"
        urlCache[key] = url
        return url
    }

    /**
     * Sticker için direkt URL döner
     */
    fun getStickerDirectUrl(packId: String, fileName: String, storagePath: String = STORAGE_PATH): String {
        return getDirectStorageUrl(storagePath, packId, fileName)
    }

    /**
     * Tek bir paketin URL'lerini yükler
     */
    suspend fun loadPackUrls(pack: Pack): Pack = withContext(Dispatchers.IO) {
        try {
            val storagePath = pack.storagePath

            val trayUrl = if (pack.trayUrl.isEmpty()) {
                getDownloadUrl("$storagePath/${pack.id}/${pack.tray}")
            } else pack.trayUrl

            val stickersWithUrls = pack.stickers.map { sticker ->
                if (sticker.url.isEmpty()) {
                    sticker.copy(url = getDownloadUrl("$storagePath/${pack.id}/${sticker.file}"))
                } else sticker
            }

            pack.copy(trayUrl = trayUrl, stickers = stickersWithUrls)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading pack URLs: ${e.message}")
            pack
        }
    }

    /**
     * Sticker dosyasını cache'e indirir ve yolunu döner
     * Dosyalar upload_stickers.py tarafından zaten doğru boyutta yükleniyor
     */
    suspend fun downloadStickerToCache(
        context: Context,
        packId: String,
        fileName: String,
        storagePath: String = STORAGE_PATH,
        directUrl: String = ""
    ): File? = withContext(Dispatchers.IO) {
        try {
            val cacheDir = File(context.cacheDir, "$CACHE_DIR/$packId")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val localFile = File(cacheDir, fileName)

            if (localFile.exists() && localFile.length() > 0) {
                // Daha önce indirilmiş ama çok büyükse sıkıştır
                if (!fileName.startsWith("tray")) compressForWhatsApp(localFile)
                return@withContext localFile
            }

            // Build candidate URLs: try primary URL first, then PocketBase file API fallback
            val primaryUrl = normalizeStickerUrl(directUrl, packId, fileName, storagePath)
            val pbFallback1 = "${PocketBaseHelper.PB_URL}/api/files/$storagePath/$packId/$fileName"
            val pbFallback2 = "${PocketBaseHelper.PB_URL}/api/files/stickers/$packId/$fileName"
            val candidateUrls = linkedSetOf(primaryUrl, pbFallback1, pbFallback2).filter { it.isNotBlank() }

            for (downloadUrl in candidateUrls) {
                try {
                    val conn = java.net.URL(downloadUrl).openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 15_000
                    conn.readTimeout = 30_000
                    conn.instanceFollowRedirects = true
                    conn.connect()
                    if (conn.responseCode == 200) {
                        conn.inputStream.use { input -> localFile.outputStream().use { input.copyTo(it) } }
                        conn.disconnect()
                        if (localFile.exists() && localFile.length() > 0) {
                            Log.d(TAG, "Downloaded via URL: $downloadUrl")
                            if (!fileName.startsWith("tray")) compressForWhatsApp(localFile)
                            return@withContext localFile
                        }
                    } else {
                        Log.w(TAG, "HTTP ${conn.responseCode} for $downloadUrl")
                        conn.disconnect()
                    }
                } catch (urlEx: Exception) {
                    Log.w(TAG, "URL download failed: $downloadUrl - ${urlEx.message}")
                }
            }

            localFile
        } catch (e: Exception) {
            Log.e(TAG, "Download FAILED: $storagePath/$packId/$fileName - ${e.message}")
            null
        }
    }

    private fun compressForWhatsApp(file: File) {
        val maxSize = 100 * 1024L // WhatsApp statik sticker limiti 100KB
        if (file.length() <= maxSize) return

        val original = BitmapFactory.decodeFile(file.absolutePath) ?: return // animated WebP → null → atla

        val targetSize = if (original.width > 512 || original.height > 512) {
            val scale = 512f / maxOf(original.width, original.height)
            Bitmap.createScaledBitmap(original, (original.width * scale).toInt(), (original.height * scale).toInt(), true)
        } else original

        var quality = 85
        while (quality >= 30) {
            val baos = ByteArrayOutputStream()
            @Suppress("DEPRECATION")
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
            targetSize.compress(format, quality, baos)
            if (baos.size() <= maxSize) {
                file.writeBytes(baos.toByteArray())
                Log.d(TAG, "Compressed ${file.name}: ${file.length()/1024}KB at quality=$quality")
                break
            }
            quality -= 10
        }

        if (targetSize !== original) targetSize.recycle()
        original.recycle()
    }

    /**
     * Tüm paketi cache'e indirir (WhatsApp'a eklemek için gerekli)
     * Paralel indirme ile hızlandırılmış versiyon
     */
    suspend fun downloadPackToCache(context: Context, pack: Pack): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val storagePath = pack.storagePath

                coroutineScope {
                    // Tray image - ayrı olarak başlat
                    val trayJob = async {
                        downloadStickerToCache(context, pack.id, pack.tray, storagePath, pack.trayUrl)
                    }

                    // Tüm stickerları paralel olarak indir (maksimum 6 eşzamanlı)
                    val stickerJobs = pack.stickers.map { sticker ->
                        async {
                            downloadStickerToCache(context, pack.id, sticker.file, storagePath, sticker.url)
                        }
                    }

                    // KRITIK: Reconstructed provider fallback için animated bilgisini işaretle
                    if (pack.isAnimated) {
                        try {
                            File(File(context.cacheDir, "$CACHE_DIR/${pack.id}"), ".animated").createNewFile()
                        } catch (_: Exception) {}
                    }

                    // Hepsini bekle ve sonuçları kontrol et
                    val trayFile = trayJob.await()
                    val stickerFiles = stickerJobs.awaitAll()

                    // KRITIK: Eğer herhangi bir dosya indirilemezse başarısız say
                    if (trayFile == null || stickerFiles.any { it == null }) {
                         Log.e(TAG, "Download failed: tray=$trayFile, stickersCount=${stickerFiles.filterNotNull().size}/${stickerFiles.size}")
                         false
                    } else {
                        true
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading pack: ${e.message}")
                false
            }
        }

    /**
     * İlk N çıkartmayı öncelikli olarak indir (hızlı görüntüleme için)
     */
    suspend fun downloadFirstStickers(context: Context, pack: Pack, count: Int = 6): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val storagePath = pack.storagePath

                coroutineScope {
                    // Tray image
                    val trayJob = async {
                        downloadStickerToCache(context, pack.id, pack.tray, storagePath, pack.trayUrl)
                    }

                    // İlk N sticker'ı paralel olarak indir
                    val firstStickers = pack.stickers.take(count)
                    val stickerJobs = firstStickers.map { sticker ->
                        async {
                            downloadStickerToCache(context, pack.id, sticker.file, storagePath, sticker.url)
                        }
                    }

                    val trayFile = trayJob.await()
                    val stickerFiles = stickerJobs.awaitAll()

                    if (trayFile == null || stickerFiles.any { it == null }) {
                        false
                    } else {
                        true
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading first stickers: ${e.message}")
                false
            }
        }

    /**
     * Cache'deki sticker dosyasının yolunu döner
     */
    fun getCachedStickerPath(context: Context, packId: String, fileName: String): File {
        return File(context.cacheDir, "$CACHE_DIR/$packId/$fileName")
    }

    /**
     * Paketin cache'de olup olmadığını kontrol eder
     */
    fun isPackCached(context: Context, pack: Pack): Boolean {
        val cacheDir = File(context.cacheDir, "$CACHE_DIR/${pack.id}")
        if (!cacheDir.exists()) return false

        // Tray kontrolü
        val trayFile = File(cacheDir, pack.tray)
        if (!trayFile.exists() || trayFile.length() <= 0) return false

        // Sticker kontrolü
        return pack.stickers.all { sticker ->
            val f = File(cacheDir, sticker.file)
            f.exists() && f.length() > 0
        }
    }

    /**
     * Belirli bir paketin cache'ini temizler
     */
    fun clearPackCache(context: Context, packId: String) {
        try {
            val cacheDir = File(context.cacheDir, "$CACHE_DIR/$packId")
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
                Log.d(TAG, "Cleared cache for pack: $packId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache for pack $packId: ${e.message}")
        }
    }

    /**
     * Tüm sticker cache'ini temizler
     */
    fun clearAllCache(context: Context) {
        try {
            val cacheDir = File(context.cacheDir, CACHE_DIR)
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
                Log.d(TAG, "Cleared all sticker cache")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing all cache: ${e.message}")
        }
    }

    /**
     * Cache'deki eski/geçersiz dosyaları temizler
     * Sadece Firestore'daki paketlere ait olmayan cache klasörlerini siler
     */
    fun cleanupInvalidCache(context: Context, validPackIds: Set<String>) {
        try {
            val cacheDir = File(context.cacheDir, CACHE_DIR)
            if (!cacheDir.exists()) return

            cacheDir.listFiles()?.forEach { packDir ->
                if (packDir.isDirectory && packDir.name !in validPackIds) {
                    packDir.deleteRecursively()
                    Log.d(TAG, "Removed invalid cache: ${packDir.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up cache: ${e.message}")
        }
    }

    /**
     * Paketin cache'ini günceller - eski dosyaları siler, yeni dosyaları tutar
     */
    fun updatePackCache(context: Context, pack: Pack) {
        try {
            val cacheDir = File(context.cacheDir, "$CACHE_DIR/${pack.id}")
            if (!cacheDir.exists()) return

            // Geçerli dosya isimleri
            val validFiles = mutableSetOf(pack.tray)
            pack.stickers.forEach { validFiles.add(it.file) }

            // Cache'deki dosyaları kontrol et ve geçersiz olanları sil
            cacheDir.listFiles()?.forEach { file ->
                if (file.name !in validFiles) {
                    file.delete()
                    Log.d(TAG, "Removed outdated cache file: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating pack cache: ${e.message}")
        }
    }

    // ============================================================================
    // ISTATISTIK FONKSIYONLARI
    // ============================================================================

    /**
     * Paket görüntülenme sayısını artır
     * NOT: Custom (yerel) paketler için istatistik tutulmaz
     */
    fun incrementViewCount(packId: String, isPremium: Boolean) {
        // Custom paketler için istatistik tutma - sadece yerel cihazda kalmalı
        if (packId.startsWith("custom_")) {
            Log.d(TAG, "Skipping view count for custom pack: $packId")
            return
        }

        try {
            val collection = if (isPremium) "premium_stickers" else "stickers"
            incrementWorkerCounter(collection, packId, "view_count", 1)
        } catch (e: Exception) {
            Log.e(TAG, "Error incrementing view count: ${e.message}")
        }
    }

    /**
     * Paket indirme (WhatsApp'a ekleme) sayısını artır
     * NOT: Custom (yerel) paketler için istatistik tutulmaz
     */
    fun incrementDownloadCount(packId: String, isPremium: Boolean) {
        // Custom paketler için istatistik tutma - sadece yerel cihazda kalmalı
        if (packId.startsWith("custom_")) {
            Log.d(TAG, "Skipping download count for custom pack: $packId")
            return
        }

        try {
            val collection = if (isPremium) "premium_stickers" else "stickers"
            incrementWorkerCounter(collection, packId, "download_count", 1)
        } catch (e: Exception) {
            Log.e(TAG, "Error incrementing download count: ${e.message}")
        }
    }

    /**
     * Paket favori sayısını artır
     * NOT: Custom (yerel) paketler için istatistik tutulmaz
     */
    fun incrementFavoriteCount(packId: String, isPremium: Boolean) {
        // Custom paketler için istatistik tutma - sadece yerel cihazda kalmalı
        if (packId.startsWith("custom_")) {
            Log.d(TAG, "Skipping favorite count for custom pack: $packId")
            return
        }

        try {
            val collection = if (isPremium) "premium_stickers" else "stickers"
            incrementWorkerCounter(collection, packId, "favorite_count", 1)
        } catch (e: Exception) {
            Log.e(TAG, "Error incrementing favorite count: ${e.message}")
        }
    }

    /**
     * Paket favori sayısını azalt
     * NOT: Custom (yerel) paketler için istatistik tutulmaz
     */
    fun decrementFavoriteCount(packId: String, isPremium: Boolean) {
        // Custom paketler için istatistik tutma - sadece yerel cihazda kalmalı
        if (packId.startsWith("custom_")) {
            Log.d(TAG, "Skipping favorite decrement for custom pack: $packId")
            return
        }

        try {
            val collection = if (isPremium) "premium_stickers" else "stickers"
            incrementWorkerCounter(collection, packId, "favorite_count", -1)
        } catch (e: Exception) {
            Log.e(TAG, "Error decrementing favorite count: ${e.message}")
        }
    }

    private fun incrementWorkerCounter(collection: String, packId: String, field: String, delta: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val body = org.json.JSONObject().apply {
                    put("collection", collection)
                    put("packId", packId)
                    put("field", field)
                    put("delta", delta)
                }
                val conn = (java.net.URL("${PocketBaseHelper.WORKER_URL}/api/stats/increment").openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 10_000
                    readTimeout = 10_000
                }
                try {
                    java.io.OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
                    if (conn.responseCode !in 200..299) {
                        val errorBody = try { conn.errorStream?.bufferedReader()?.readText() } catch (_: Exception) { null }
                        Log.w(TAG, "Counter update skipped for $collection/$packId $field: ${conn.responseCode} $errorBody")
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Counter update skipped for $collection/$packId $field: ${e.message}")
            }
        }
    }

    /**
     * Firestore'dan genel ödeme ayarlarını (fiyatlar vb.) yükler
     */
    suspend fun getGlobalBillingSettings(): BillingSettings = withContext(Dispatchers.IO) {
        try {
            val doc = firestore.collection("settings").document("billing")
                .get(com.google.firebase.firestore.Source.SERVER).await()

            if (doc.exists()) {
                BillingSettings(
                    priceTRY = doc.getString("price_try") ?: "69,99 TL",
                    priceUSD = doc.getString("price_usd") ?: "$4.99",
                    priceEUR = doc.getString("price_eur") ?: "€4.49",
                    updatedAt = doc.getTimestamp("updated_at")?.toDate()?.toString() ?: ""
                )
            } else {
                BillingSettings()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading billing settings: ${e.message}")
            BillingSettings()
        }
    }

    /**
     * Firestore'dan genisletilmis fiyatlandirma konfigurasyonunu yukler
     * settings/billing dokumanindaki plans arrayini ve kampanya bilgisini okur
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun getBillingConfig(): BillingConfig = withContext(Dispatchers.IO) {
        try {
            val doc = firestore.collection("settings").document("billing")
                .get(com.google.firebase.firestore.Source.SERVER).await()

            if (doc.exists()) {
                val data = doc.data ?: return@withContext BillingConfig()

                val plansRaw = data["plans"] as? List<Map<String, Any>> ?: emptyList()
                val plans = plansRaw.map { planMap ->
                    BillingPlan(
                        id = planMap["id"] as? String ?: "",
                        name = planMap["name"] as? String ?: "",
                        type = planMap["type"] as? String ?: "subscription",
                        priceTry = planMap["price_try"] as? String ?: "",
                        priceUsd = planMap["price_usd"] as? String ?: "",
                        priceEur = planMap["price_eur"] as? String ?: "",
                        isActive = planMap["is_active"] as? Boolean ?: true,
                        trialDays = (planMap["trial_days"] as? Long)?.toInt() ?: 0,
                        discountPercentage = (planMap["discount_percentage"] as? Long)?.toInt() ?: 0
                    )
                }

                BillingConfig(
                    plans = plans,
                    campaignActive = data["campaign_active"] as? Boolean ?: false,
                    campaignName = data["campaign_name"] as? String ?: "",
                    campaignEndDate = data["campaign_end_date"] as? String ?: "",
                    updatedAt = doc.getTimestamp("updated_at")?.toDate()?.toString() ?: ""
                )
            } else {
                BillingConfig()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading billing config: ${e.message}")
            BillingConfig()
        }
    }
}

/**
 * Simple helper to handle common translations as a fallback
 */
object TranslationHelper {
    private val translationMap = mapOf(
        "tatlı bebekler" to "Cute Babies",
        "komik kedi" to "Funny Cat",
        "günaydın" to "Good Morning",
        "teşekkürler" to "Thanks",
        "aşk" to "Love",
        "bebek" to "Baby",
        "romatizm" to "Rheumatism",
        "romantizm" to "Romanticism",
        "okula git" to "Go to school",
        "boş konuşma" to "Empty Talk",
        "mizah" to "Humor",
        "karışık" to "Mixed",
        "dizi" to "Series",
        "film" to "Movie",
        "reklam" to "Ad",
        "spor" to "Sports",
        "oyun" to "Game",
        "salak işler" to "Foolish Business",
        "değişik olaylar" to "Strange Events",
        "değişik" to "Different",
        "olaylar" to "Events",
        "salak" to "Silly",
        "aptal" to "Stupid",
        "komik" to "Funny",
        "sözler" to "Quotes",
        "deli" to "Crazy"
    )

    fun translate(text: String): String {
        val lower = text.lowercase()
        // 1. Check exact map
        translationMap[lower]?.let { return it }
        
        // 2. Check contains
        for ((tr, en) in translationMap) {
            if (lower.contains(tr)) {
                return text.replace(tr, en, ignoreCase = true)
            }
        }
        
        // No translation found
        return text
    }
}

/**
 * Paketin mevcut dile uygun ismini döner
 */
val Pack.localizedName: String
    get() {
        val locale = java.util.Locale.getDefault().language
        
        // 1. Check dynamic translations map (Populated from Gemini)
        val dynamicName = translations[locale]
        if (!dynamicName.isNullOrBlank()) return dynamicName

        // 2. Check legacy hardcoded fields
        when (locale) {
            "tr" -> if (nameTr.isNotBlank()) return nameTr
            "zh" -> if (nameZh.isNotBlank()) return nameZh
            "es" -> if (nameEs.isNotBlank()) return nameEs
            "ar" -> if (nameAr.isNotBlank()) return nameAr
            "hi" -> if (nameHi.isNotBlank()) return nameHi
            "pt" -> if (namePt.isNotBlank()) return namePt
        }
        
        // 3. If no match, use primary name or English fallback
        return if (locale == "tr") {
            if (nameTr.isNotBlank()) nameTr else name
        } else {
            // ENGLISH/GLOBAL MODE (for en, zh, es, ar, hi, pt etc.)
            
            // 1. Try name directly if it looks non-Turkish
            if (name.isNotBlank() && !isLikelyTurkish(name)) return name
            
            // 2. Fallback to Turkish name if it looks like English (rare case)
            if (nameTr.isNotBlank() && !isLikelyTurkish(nameTr)) return nameTr
            
            
            // Fallback to name, then nameTr
            if (name.isNotBlank()) name else nameTr
        }
    }

private fun isLikelyTurkish(text: String): Boolean {
    val turkishChars = charArrayOf('ç', 'ğ', 'ı', 'ö', 'ş', 'ü', 'Ç', 'Ğ', 'İ', 'Ö', 'Ş', 'Ü')
    return text.any { it in turkishChars }
}
