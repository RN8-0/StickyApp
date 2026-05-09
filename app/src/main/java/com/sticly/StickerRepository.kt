package com.sticly

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.pow

/**
 * PocketBase'den sticker paketlerini yukleyen repository
 */
object StickerRepository {

    private const val TAG = "StickerRepository"
    private const val STORAGE_PATH = "stickers"
    private const val CACHE_DIR = "sticker_cache"

    // Repository scope for long-running observers and background tasks
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    /**
     * KRITIK: Tüm paketlerin statik cache'i. 
     * StickerProvider (farklı bir thread/zamanlama) buradan veriyi senkron olarak okuyabilir.
     */
    var allPacksCache: List<Pack> = emptyList()
    
    private val _packsUpdateFlow = MutableSharedFlow<List<Pack>>(replay = 1)
    val packsUpdateFlow = _packsUpdateFlow.asSharedFlow()
    private var observerJob: Job? = null
    private var lastObservedSignature: String = ""

    /**
     * Tum paketleri yukler (PocketBase + Ozel + Lokal Assets)
     */
    suspend fun loadPacks(context: Context, forceRefresh: Boolean = false): List<Pack> = withContext(Dispatchers.IO) {
        fun youtubeStyleScore(pack: Pack): Double {
            if (pack.category == "custom") return Double.MAX_VALUE + pack.id.hashCode().mod(1000).toDouble()
            val ageHours = runCatching {
                if (pack.createdAt.isBlank()) 240.0 else {
                    val created = java.time.Instant.parse(pack.createdAt)
                    java.time.Duration.between(created, java.time.Instant.now()).toHours().coerceAtLeast(1).toDouble()
                }
            }.getOrDefault(240.0)
            val engagement = pack.downloadCount * 5.0 + pack.favoriteCount * 4.0 + pack.likeCount * 6.0 + pack.commentCount * 8.0 + pack.viewCount * 0.35
            val freshness = 18.0 / (ageHours + 6.0).pow(0.42)
            val qualityBoost = if (pack.isPopular) 18.0 else 0.0
            val communityBoost = if (pack.source == "user_submission" || pack.publisherUserId.isNotBlank()) 4.0 else 0.0
            return pack.engagementScore.takeIf { it > 0.0 } ?: (engagement + freshness + qualityBoost + communityBoost)
        }
        fun shufflePacks(packs: List<Pack>) = packs.sortedWith(
            compareByDescending<Pack> { youtubeStyleScore(it) }
                .thenBy { it.id.hashCode() }
        )
        
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

            // 2. Paketleri PocketBase'den yukle
            val packsFromPocketBase = loadPacksFromPocketBase()
            if (packsFromPocketBase.isNotEmpty()) {
                Log.d(TAG, "Loaded ${packsFromPocketBase.size} packs from PocketBase")
                allPacks.addAll(packsFromPocketBase)
            }

            // 3. HER ZAMAN lokal asset paketlerini ekle
            val existingIds = allPacks.map { it.id }.toSet()
            val localPacks = try {
                Loader.load(context)?.filter { it.id !in existingIds } ?: emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Error loading local packs: ${e.message}")
                emptyList()
            }
            Log.d(TAG, "Loaded ${localPacks.size} local asset packs")
            allPacks.addAll(localPacks)

            val previousPacks = allPacksCache
            val result = shufflePacks(allPacks)
            clearChangedPackCaches(context, previousPacks, result)
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
                try { PocketBaseHelper.listAllRecords("stickers", perPage = 500) }
                catch (e: Exception) { Log.e(TAG, "PB stickers error: ${e.message}"); emptyList() }
            }
            val premiumJob = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).async {
                try { PocketBaseHelper.listAllRecords("premium_stickers", perPage = 500) }
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
            // Read is_premium from the record field so admin can toggle it without moving collections
            val isPremiumPack = json.optBoolean("is_premium", isPremium)
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
                isPremium = isPremiumPack,
                productId = json.optString("product_id"),
                storagePath = collection,  // actual collection for correct file URL construction
                createdAt = json.optString("created"),
                category = json.optString("category"),
                publisherPhotoUrl = json.optString("publisher_photo_url"),
                downloadCount = json.optInt("download_count", 0),
                fakeDownloadBase = json.optInt("fake_download_base", 0),
                viewCount = json.optInt("view_count", 0),
                favoriteCount = json.optInt("favorite_count", 0),
                likeCount = json.optInt("like_count", 0),
                commentCount = json.optInt("comment_count", 0),
                engagementScore = json.optDouble("engagement_score", 0.0),
                isAnimated = json.optBoolean("is_animated", false),
                isActive = json.optBoolean("is_active", true),
                isPopular = json.optBoolean("is_popular", false),
                priceTRY = json.optString("price_try"),
                priceUSD = json.optString("price_usd"),
                priceEUR = json.optString("price_eur"),
                translations = translations,
                source = json.optString("source"),
                publisherUserId = json.optString("publisher_user_id").ifBlank { json.optString("publisher_email") }
            )
        } catch (e: Exception) {
            Log.e(TAG, "PocketBase pack parse error: ${e.message}")
            null
        }
    }

    /**
     * PocketBase does not have a Firestore-style Android listener here, so poll
     * the live pack list and emit only when visible pack metadata changes.
     */
    fun startObservingPacks(context: Context) {
        if (observerJob?.isActive == true) return
        val appContext = context.applicationContext
        lastObservedSignature = packSignature(allPacksCache)
        observerJob = repositoryScope.launch {
            // Polling kept at 8s — going lower (3s was tried) hammers PocketBase with
            // listAllRecords on two 500-row collections, slowing the admin panel and the
            // image proxy that serves the same backend. 8s is a small regression in
            // edit-propagation lag but keeps the server responsive.
            delay(2_000)
            while (isActive) {
                try {
                    val packs = loadPacks(appContext, forceRefresh = true)
                    val signature = packSignature(packs)
                    if (signature != lastObservedSignature) {
                        lastObservedSignature = signature
                        _packsUpdateFlow.emit(packs)
                        Log.d(TAG, "PocketBase poll update emitted: ${packs.size} packs")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "PocketBase poll failed: ${e.message}")
                }
                delay(8_000)
            }
        }
    }

    fun stopObservingPacks() {
        observerJob?.cancel()
        observerJob = null
    }

    private fun packSignature(packs: List<Pack>): String = packs
        .filter { !it.id.startsWith("custom_") }
        .sortedBy { it.id }
        .joinToString("|") { pack ->
            val files = pack.stickers.joinToString(",") { it.file }
            "${pack.id}:${pack.version}:${pack.name}:${pack.isActive}:${pack.isPremium}:${pack.isAnimated}:${pack.stickers.size}:$files"
        }

    private fun clearChangedPackCaches(context: Context, oldPacks: List<Pack>, newPacks: List<Pack>) {
        if (oldPacks.isEmpty()) return
        val oldById = oldPacks.associateBy { it.id }
        newPacks.forEach { newPack ->
            if (newPack.id.startsWith("custom_")) return@forEach
            val oldPack = oldById[newPack.id] ?: return@forEach
            val oldFiles = oldPack.stickers.map { it.file }
            val newFiles = newPack.stickers.map { it.file }
            if (oldPack.version != newPack.version || oldPack.tray != newPack.tray || oldFiles != newFiles) {
                clearPackCache(context, newPack.id)
            }
        }
        val newIds = newPacks.map { it.id }.toSet()
        oldPacks.filter { !it.id.startsWith("custom_") && it.id !in newIds }.forEach { clearPackCache(context, it.id) }
    }

    fun prepareAnimatedPackCache(context: Context, packId: String) {
        try {
            val cacheDir = File(context.cacheDir, "$CACHE_DIR/$packId")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val marker = File(cacheDir, ".animated")
            if (!marker.exists()) {
                cacheDir.listFiles()?.forEach { file ->
                    if (file.name != ".animated") file.deleteRecursively()
                }
                marker.createNewFile()
            }
        } catch (_: Exception) {}
    }

    private fun markAnimatedCache(context: Context, packId: String) = prepareAnimatedPackCache(context, packId)

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

    private fun normalizeStickerUrl(url: String, packId: String, fileName: String, collection: String = "stickers"): String {
        val trimmed = url.trim()
        val canonicalPocketBaseUrl = "${PocketBaseHelper.PB_URL}/api/files/$collection/$packId/$fileName"

        if (fileName.isNotBlank() && (trimmed.contains("/draft_stickers/") || trimmed.contains("/api/files/draft_stickers/"))) {
            return canonicalPocketBaseUrl
        }

        if (fileName.isNotBlank() && trimmed.contains("/api/files/")) {
            // Preserve any valid PocketBase file URL from the current host.
            // Cross-collection URLs are intentional: approved packs in 'stickers' still
            // serve their image files from the original 'user_submissions' collection.
            val pbHost = PocketBaseHelper.PB_URL.trimEnd('/')
            return if (trimmed.startsWith(pbHost)) trimmed else canonicalPocketBaseUrl
        }

        if (trimmed.contains("firebasestorage.googleapis.com") && fileName.isNotBlank()) {
            return canonicalPocketBaseUrl
        }

        // Already a CDN or PocketBase URL → use as-is
        if (trimmed.contains("sticky-images.46.225.95.201.sslip.io")) return trimmed
        if (trimmed.contains("sslip.io") || trimmed.contains("api/files")) return trimmed

        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        // Build PocketBase file API URL as default
        return canonicalPocketBaseUrl
    }

    suspend fun getDownloadUrl(path: String): String {
        return "${PocketBaseHelper.PB_URL}/api/files/$path"
    }

    /**
     * Firebase Storage URL'ini HIZLI hesaplar (API çağrısı yapmaz)
     * Public read izni olan dosyalar için çalışır
     */
    private val urlCache = java.util.concurrent.ConcurrentHashMap<String, String>(256)

    fun getDirectStorageUrl(storagePath: String, packId: String, fileName: String): String {
        val key = "$storagePath/$packId/$fileName"
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
        directUrl: String = "",
        allowCompression: Boolean = true
    ): File? = withContext(Dispatchers.IO) {
        try {
            val cacheDir = File(context.cacheDir, "$CACHE_DIR/$packId")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            if (!allowCompression) markAnimatedCache(context, packId)
            val isAnimatedPack = File(cacheDir, ".animated").exists()

            val localFile = File(cacheDir, fileName)

            if (localFile.exists() && localFile.length() > 0) {
                // Daha önce indirilmiş ama çok büyükse sıkıştır
                if (allowCompression && !isAnimatedPack && !fileName.startsWith("tray")) compressForWhatsApp(localFile)
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
                            if (allowCompression && !isAnimatedPack && !fileName.startsWith("tray")) compressForWhatsApp(localFile)
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

            if (localFile.exists() && localFile.length() <= 0) localFile.delete()
            null
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
                if (pack.isAnimated) {
                    markAnimatedCache(context, pack.id)
                }

                coroutineScope {
                    // Tray image - ayrı olarak başlat
                    val trayJob = async {
                        downloadStickerToCache(context, pack.id, pack.tray, storagePath, pack.trayUrl)
                    }

                    // Tüm stickerları paralel olarak indir (maksimum 6 eşzamanlı)
                    val stickerJobs = pack.stickers.map { sticker ->
                        async {
                            downloadStickerToCache(context, pack.id, sticker.file, storagePath, sticker.url, allowCompression = !pack.isAnimated)
                        }
                    }

                    // Hepsini bekle ve sonuçları kontrol et
                    val trayFile = trayJob.await()
                    val stickerFiles = stickerJobs.awaitAll()

                    // Ensure tray is also accessible as "tray.webp" so StickerProvider
                    // can route it through the 96x96 PNG conversion for WhatsApp.
                    trayFile?.let {
                        if (it.name != "tray.webp") {
                            val trayWebp = File(it.parentFile!!, "tray.webp")
                            if (!trayWebp.exists()) try { it.copyTo(trayWebp) } catch (_: Exception) {}
                        }
                    }

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
                if (pack.isAnimated) {
                    markAnimatedCache(context, pack.id)
                }

                coroutineScope {
                    // Tray image
                    val trayJob = async {
                        downloadStickerToCache(context, pack.id, pack.tray, storagePath, pack.trayUrl)
                    }

                    // İlk N sticker'ı paralel olarak indir
                    val firstStickers = pack.stickers.take(count)
                    val stickerJobs = firstStickers.map { sticker ->
                        async {
                            downloadStickerToCache(context, pack.id, sticker.file, storagePath, sticker.url, allowCompression = !pack.isAnimated)
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
     * Cache'deki eski/gecersiz dosyalari temizler
     * Sadece PocketBase'deki paketlere ait olmayan cache klasorlerini siler
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
     * PocketBase'den genel odeme ayarlarini (fiyatlar vb.) yukler
     */
    suspend fun getGlobalBillingSettings(): BillingSettings = withContext(Dispatchers.IO) {
        try {
            val records = PocketBaseHelper.listRecords("settings", filter = "key='billing'", perPage = 1)
            if (records.isNotEmpty()) {
                val doc = records.first()
                BillingSettings(
                    priceTRY = doc.optString("price_try", "69,99 TL"),
                    priceUSD = doc.optString("price_usd", "$4.99"),
                    priceEUR = doc.optString("price_eur", "€4.49"),
                    updatedAt = doc.optString("updated_at", "")
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
     * PocketBase'den genisletilmis fiyatlandirma konfigurasyonunu yukler
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun getBillingConfig(): BillingConfig = withContext(Dispatchers.IO) {
        try {
            val records = PocketBaseHelper.listRecords("settings", filter = "key='billing'", perPage = 1)
            if (records.isNotEmpty()) {
                val doc = records.first()

                val plansRaw = doc.optJSONArray("plans")
                val plans = if (plansRaw != null) {
                    (0 until plansRaw.length()).map { i ->
                        val planMap = plansRaw.optJSONObject(i) ?: return@map BillingPlan()
                        BillingPlan(
                            id = planMap.optString("id", ""),
                            name = planMap.optString("name", ""),
                            type = planMap.optString("type", "subscription"),
                            priceTry = planMap.optString("price_try", ""),
                            priceUsd = planMap.optString("price_usd", ""),
                            priceEur = planMap.optString("price_eur", ""),
                            isActive = planMap.optBoolean("is_active", true),
                            trialDays = planMap.optInt("trial_days", 0),
                            discountPercentage = planMap.optInt("discount_percentage", 0)
                        )
                    }
                } else {
                    emptyList()
                }

                BillingConfig(
                    plans = plans,
                    campaignActive = doc.optBoolean("campaign_active", false),
                    campaignName = doc.optString("campaign_name", ""),
                    campaignEndDate = doc.optString("campaign_end_date", ""),
                    updatedAt = doc.optString("updated_at", "")
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
    get() = localizedNameFor(java.util.Locale.getDefault().language)

fun Pack.localizedNameFor(language: String): String {
        val locale = language.substringBefore('-').substringBefore('_').lowercase()

        // 1. Check dynamic translations map (Populated from Gemini)
        val dynamicName = translations[locale]
        if (!dynamicName.isNullOrBlank()) return dynamicName
        if (locale == "en") {
            val englishName = translations["en"].orEmpty().ifBlank { TranslationHelper.translate(name.ifBlank { nameTr }) }
            if (englishName.isNotBlank()) return englishName
        }

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
            val englishFallback = translations["en"].orEmpty().ifBlank { TranslationHelper.translate(name.ifBlank { nameTr }) }
            if (englishFallback.isNotBlank() && englishFallback != name) return englishFallback
            
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
