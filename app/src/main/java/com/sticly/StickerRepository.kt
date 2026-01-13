package com.sticly

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
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

    /**
     * Firebase Storage'dan tüm paketleri yükler
     */
    suspend fun loadPacks(context: Context): List<Pack> = withContext(Dispatchers.IO) {
        try {
            // Önce Firestore'dan contents.json'u çek
            val packsFromFirestore = loadPacksFromFirestore()
            if (packsFromFirestore.isNotEmpty()) {
                Log.d(TAG, "Loaded ${packsFromFirestore.size} packs from Firestore")
                return@withContext packsFromFirestore
            }

            // Firestore boşsa Storage'dan contents.json'u çek
            val packsFromStorage = loadPacksFromStorage(context)
            if (packsFromStorage.isNotEmpty()) {
                Log.d(TAG, "Loaded ${packsFromStorage.size} packs from Storage")
                return@withContext packsFromStorage
            }

            // Her ikisi de boşsa lokal assets'ten yükle (fallback)
            Log.d(TAG, "Loading from local assets (fallback)")
            return@withContext Loader.load(context)

        } catch (e: Exception) {
            Log.e(TAG, "Error loading packs: ${e.message}")
            // Hata durumunda lokal assets'ten yükle
            return@withContext Loader.load(context)
        }
    }

    /**
     * Firestore'dan paket listesini yükler
     */
    private suspend fun loadPacksFromFirestore(): List<Pack> {
        return try {
            val snapshot = firestore.collection("sticker_packs")
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                try {
                    val data = doc.data ?: return@mapNotNull null
                    Pack(
                        id = doc.id,
                        name = data["name"] as? String ?: "",
                        pub = data["publisher"] as? String ?: "",
                        email = data["publisher_email"] as? String ?: "",
                        privacy = data["privacy_policy_website"] as? String ?: "",
                        license = data["license_agreement_website"] as? String ?: "",
                        version = data["image_data_version"] as? String ?: "1",
                        avoidCache = data["avoid_cache"] as? Boolean ?: false,
                        tray = data["tray_image_file"] as? String ?: "",
                        trayUrl = data["tray_url"] as? String ?: "",
                        stickers = parseStickers(data["stickers"]),
                        isPremium = data["is_premium"] as? Boolean ?: false
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing pack: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading from Firestore: ${e.message}")
            emptyList()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseStickers(data: Any?): List<Sticker> {
        if (data == null) return emptyList()
        return try {
            (data as? List<Map<String, Any>>)?.map { stickerData ->
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
                pack.copy(
                    trayUrl = getDownloadUrl("$STORAGE_PATH/${pack.id}/${pack.tray}"),
                    stickers = pack.stickers.map { sticker ->
                        sticker.copy(
                            url = getDownloadUrl("$STORAGE_PATH/${pack.id}/${sticker.file}")
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
     * Firebase Storage URL'ini alır
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
     * Tek bir paketin URL'lerini yükler
     */
    suspend fun loadPackUrls(pack: Pack): Pack = withContext(Dispatchers.IO) {
        try {
            val trayUrl = if (pack.trayUrl.isEmpty()) {
                getDownloadUrl("$STORAGE_PATH/${pack.id}/${pack.tray}")
            } else pack.trayUrl

            val stickersWithUrls = pack.stickers.map { sticker ->
                if (sticker.url.isEmpty()) {
                    sticker.copy(url = getDownloadUrl("$STORAGE_PATH/${pack.id}/${sticker.file}"))
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
     */
    suspend fun downloadStickerToCache(context: Context, packId: String, fileName: String): File? =
        withContext(Dispatchers.IO) {
            try {
                val cacheDir = File(context.cacheDir, "$CACHE_DIR/$packId")
                if (!cacheDir.exists()) cacheDir.mkdirs()

                val localFile = File(cacheDir, fileName)

                // Zaten cache'de varsa tekrar indirme
                if (localFile.exists() && localFile.length() > 0) {
                    return@withContext localFile
                }

                val storageRef = storage.reference.child("$STORAGE_PATH/$packId/$fileName")
                storageRef.getFile(localFile).await()

                localFile
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading sticker: ${e.message}")
                null
            }
        }

    /**
     * Tüm paketi cache'e indirir (WhatsApp'a eklemek için gerekli)
     */
    suspend fun downloadPackToCache(context: Context, pack: Pack): Boolean =
        withContext(Dispatchers.IO) {
            try {
                // Tray image
                downloadStickerToCache(context, pack.id, pack.tray)

                // Tüm stickerlar
                pack.stickers.forEach { sticker ->
                    downloadStickerToCache(context, pack.id, sticker.file)
                }

                true
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading pack: ${e.message}")
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
}
