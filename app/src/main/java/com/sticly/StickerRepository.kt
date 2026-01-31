package com.sticly

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File

/**
 * Firebase Storage'dan sticker paketlerini yükleyen repository
 */
object StickerRepository {

    private const val TAG = "StickerRepository"
    private const val STORAGE_PATH = "stickers"
    private const val CONTENTS_FILE = "contents.json"
    private const val CACHE_DIR = "sticker_cache"

    private val storage = FirebaseStorage.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    
    // Repository scope for long-running observers and background tasks
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
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

            // 2. Firebase paketlerini yükle
            val packsFromFirestore = loadPacksFromFirestore(forceRefresh)
            if (packsFromFirestore.isNotEmpty()) {
                Log.d(TAG, "Loaded ${packsFromFirestore.size} packs from Firestore")
                allPacks.addAll(packsFromFirestore)
            } else {
                // Firestore boşsa Storage'dan contents.json'u çek
                val packsFromStorage = loadPacksFromStorage(context)
                if (packsFromStorage.isNotEmpty()) {
                    Log.d(TAG, "Loaded ${packsFromStorage.size} packs from Storage")
                    allPacks.addAll(packsFromStorage)
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
            return@withContext result

        } catch (e: Exception) {
            Log.e(TAG, "Error loading packs: ${e.message}")
            // Hata durumunda lokal assets'ten yükle
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

    /**
     * Firestore koleksiyonlarını gerçek zamanlı takip eder
     */
    fun startObservingPacks(context: Context) {
        if (stickersListener != null && premiumStickersListener != null) return

        Log.d(TAG, "Starting real-time observers for Firestore...")

        // Stickers koleksiyonunu dinle
        stickersListener = firestore.collection("stickers")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e(TAG, "Stickers listener failed: ${e.message}")
                    return@addSnapshotListener
                }
                Log.d(TAG, "Stickers collection updated: ${snapshot?.documents?.size} docs")
                triggerRefresh(context)
            }

        // Premium_stickers koleksiyonunu dinle
        premiumStickersListener = firestore.collection("premium_stickers")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e(TAG, "Premium stickers listener failed: ${e.message}")
                    return@addSnapshotListener
                }
                Log.d(TAG, "Premium stickers collection updated: ${snapshot?.documents?.size} docs")
                triggerRefresh(context)
            }
    }

    fun stopObservingPacks() {
        stickersListener?.remove()
        premiumStickersListener?.remove()
        stickersListener = null
        premiumStickersListener = null
    }

    private fun triggerRefresh(context: Context) {
        repositoryScope.launch {
            try {
                // Veriyi her zaman sunucudan (forceRefresh) çekiyoruz ki anlık yansısın
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

            // Normal paketleri yükle (stickers koleksiyonu)
            try {
                val stickersSnapshot = firestore.collection("stickers").get(source).await()
                Log.d(TAG, "Stickers collection: ${stickersSnapshot.documents.size} documents")
                stickersSnapshot.documents.mapNotNull { doc ->
                    parsePackDocument(doc, isPremiumOverride = false)
                }.let { allPacks.addAll(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading stickers collection: ${e.message}")
            }

            // Premium paketleri yükle (premium_stickers koleksiyonu)
            try {
                val premiumSnapshot = firestore.collection("premium_stickers").get(source).await()
                Log.d(TAG, "Premium_stickers collection: ${premiumSnapshot.documents.size} documents")
                premiumSnapshot.documents.mapNotNull { doc ->
                    parsePackDocument(doc, isPremiumOverride = true)
                }.let { allPacks.addAll(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading premium_stickers collection: ${e.message}")
            }

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
            val publisher = (data["publisher"] as? String).takeIf { !it.isNullOrBlank() } ?: "Sticly"
            val email = (data["publisher_email"] as? String).takeIf { !it.isNullOrBlank() } ?: "contact@sticly.com"
            val privacy = (data["privacy_policy_website"] as? String).takeIf { !it.isNullOrBlank() } ?: "https://sticly.com/privacy"

            Pack(
                id = doc.id,
                name = data["name"] as? String ?: doc.id.replace("_", " ").replaceFirstChar { it.uppercase() },
                nameTr = data["name_tr"] as? String ?: "",
                nameZh = data["name_zh"] as? String ?: "",
                nameEs = data["name_es"] as? String ?: "",
                nameAr = data["name_ar"] as? String ?: "",
                nameHi = data["name_hi"] as? String ?: "",
                namePt = data["name_pt"] as? String ?: "",
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
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
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

    /**
     * Firebase Storage'dan contents.json yükler
     */
    private suspend fun loadPacksFromStorage(context: Context): List<Pack> {
        return try {
            val storageRef = storage.reference.child("$STORAGE_PATH/$CONTENTS_FILE")
            val localFile = File(context.cacheDir, CONTENTS_FILE)

            storageRef.getFile(localFile).await()

            val json = localFile.readText()
            val response = Gson().fromJson(json, Response::class.java)

            // URL'leri ekle
            response.packs.map { pack ->
                val storagePath = pack.storagePath
                pack.copy(
                    trayUrl = getDownloadUrl("$storagePath/${pack.id}/${pack.tray}"),
                    stickers = pack.stickers.map { sticker ->
                        sticker.copy(
                            url = getDownloadUrl("$storagePath/${pack.id}/${sticker.file}")
                        )
                    }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading from Storage: ${e.message}")
            emptyList()
        }
    }

    /**
     * Firebase Storage URL'ini alır (yavaş - API çağrısı yapar)
     */
    suspend fun getDownloadUrl(path: String): String {
        return try {
            storage.reference.child(path).downloadUrl.await().toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting download URL for $path: ${e.message}")
            ""
        }
    }

    /**
     * Firebase Storage URL'ini HIZLI hesaplar (API çağrısı yapmaz)
     * Public read izni olan dosyalar için çalışır
     */
    fun getDirectStorageUrl(storagePath: String, packId: String, fileName: String): String {
        val bucket = storage.reference.bucket
        val fullPath = "$storagePath/$packId/$fileName"
        val encodedPath = java.net.URLEncoder.encode(fullPath, "UTF-8")
        return "https://firebasestorage.googleapis.com/v0/b/$bucket/o/$encodedPath?alt=media"
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
        storagePath: String = STORAGE_PATH
    ): File? = withContext(Dispatchers.IO) {
        try {
            val cacheDir = File(context.cacheDir, "$CACHE_DIR/$packId")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val localFile = File(cacheDir, fileName)

            // Zaten cache'de varsa tekrar indirme
            if (localFile.exists() && localFile.length() > 0) {
                return@withContext localFile
            }

            val fullPath = "$storagePath/$packId/$fileName"
            Log.d(TAG, "Downloading: $fullPath")
            val storageRef = storage.reference.child(fullPath)
            storageRef.getFile(localFile).await()

            localFile
        } catch (e: Exception) {
            Log.e(TAG, "Download FAILED: $storagePath/$packId/$fileName - ${e.message}")
            null
        }
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
                        downloadStickerToCache(context, pack.id, pack.tray, storagePath)
                    }

                    // Tüm stickerları paralel olarak indir (maksimum 6 eşzamanlı)
                    val stickerJobs = pack.stickers.map { sticker ->
                        async {
                            downloadStickerToCache(context, pack.id, sticker.file, storagePath)
                        }
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
                        downloadStickerToCache(context, pack.id, pack.tray, storagePath)
                    }

                    // İlk N sticker'ı paralel olarak indir
                    val firstStickers = pack.stickers.take(count)
                    val stickerJobs = firstStickers.map { sticker ->
                        async {
                            downloadStickerToCache(context, pack.id, sticker.file, storagePath)
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

        // Tray ve en az bir sticker var mı kontrol et
        val trayFile = File(cacheDir, pack.tray)
        if (!trayFile.exists()) return false

        return pack.stickers.all { sticker ->
            File(cacheDir, sticker.file).exists()
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
     */
    fun incrementViewCount(packId: String, isPremium: Boolean) {
        try {
            val collection = if (isPremium) "premium_stickers" else "stickers"
            val docRef = firestore.collection(collection).document(packId)

            // FieldValue.increment() kullan - alan yoksa otomatik oluşturur
            docRef.update("view_count", com.google.firebase.firestore.FieldValue.increment(1))
                .addOnSuccessListener {
                    Log.d(TAG, "View count incremented for $packId")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error incrementing view count: ${e.message}")
                    // Alan yoksa set ile oluştur
                    docRef.set(
                        mapOf("view_count" to 1),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error incrementing view count: ${e.message}")
        }
    }

    /**
     * Paket indirme (WhatsApp'a ekleme) sayısını artır
     */
    fun incrementDownloadCount(packId: String, isPremium: Boolean) {
        try {
            val collection = if (isPremium) "premium_stickers" else "stickers"
            val docRef = firestore.collection(collection).document(packId)

            docRef.update("download_count", com.google.firebase.firestore.FieldValue.increment(1))
                .addOnSuccessListener {
                    Log.d(TAG, "Download count incremented for $packId")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error incrementing download count: ${e.message}")
                    docRef.set(
                        mapOf("download_count" to 1),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error incrementing download count: ${e.message}")
        }
    }

    /**
     * Paket favori sayısını artır
     */
    fun incrementFavoriteCount(packId: String, isPremium: Boolean) {
        try {
            val collection = if (isPremium) "premium_stickers" else "stickers"
            val docRef = firestore.collection(collection).document(packId)

            docRef.update("favorite_count", com.google.firebase.firestore.FieldValue.increment(1))
                .addOnSuccessListener {
                    Log.d(TAG, "Favorite count incremented for $packId")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error incrementing favorite count: ${e.message}")
                    docRef.set(
                        mapOf("favorite_count" to 1),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error incrementing favorite count: ${e.message}")
        }
    }

    /**
     * Paket favori sayısını azalt
     */
    fun decrementFavoriteCount(packId: String, isPremium: Boolean) {
        try {
            val collection = if (isPremium) "premium_stickers" else "stickers"
            val docRef = firestore.collection(collection).document(packId)

            docRef.update("favorite_count", com.google.firebase.firestore.FieldValue.increment(-1))
                .addOnSuccessListener {
                    Log.d(TAG, "Favorite count decremented for $packId")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error decrementing favorite count: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error decrementing favorite count: ${e.message}")
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
        
        // 1. Check for native language field matches first
        when (locale) {
            "tr" -> if (nameTr.isNotBlank()) return nameTr
            "zh" -> if (nameZh.isNotBlank()) return nameZh
            "es" -> if (nameEs.isNotBlank()) return nameEs
            "ar" -> if (nameAr.isNotBlank()) return nameAr
            "hi" -> if (nameHi.isNotBlank()) return nameHi
            "pt" -> if (namePt.isNotBlank()) return namePt
        }
        
        // 2. If no native field, apply Global/Turkish logic
        return if (locale == "tr") {
            if (nameTr.isNotBlank()) nameTr else name
        } else {
            // ENGLISH/GLOBAL MODE (for en, zh, es, ar, hi, pt etc.)
            
            // 1. First, try translating 'name' (Global name field)
            val translatedName = TranslationHelper.translate(name)
            if (translatedName != name) return translatedName
            
            // 2. If 'name' is English-like (no Turkish specific chars), trust it as English
            if (name.isNotBlank() && !isLikelyTurkish(name)) return name
            
            // 3. If 'name' is Turkish-like, try 'nameTr' (Local name field)
            if (nameTr.isNotBlank() && !isLikelyTurkish(nameTr)) return nameTr
            
            // 4. Try translating 'nameTr'
            val translatedTr = TranslationHelper.translate(nameTr)
            if (translatedTr != nameTr) return translatedTr
            
            // 5. Final attempt: Deep translation
            val deep = TranslationHelper.translate(name)
            if (deep != name) return deep
            
            // Fallback to name, then nameTr
            if (name.isNotBlank()) name else nameTr
        }
    }

private fun isLikelyTurkish(text: String): Boolean {
    val turkishChars = charArrayOf('ç', 'ğ', 'ı', 'ö', 'ş', 'ü', 'Ç', 'Ğ', 'İ', 'Ö', 'Ş', 'Ü')
    return text.any { it in turkishChars }
}
