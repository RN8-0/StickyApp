package com.sticly

import android.content.*
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream

class StickerProvider : ContentProvider() {

    companion object {
        // WhatsApp required column names - DO NOT CHANGE
        const val STICKER_PACK_IDENTIFIER = "sticker_pack_identifier"
        const val STICKER_PACK_NAME = "sticker_pack_name"
        const val STICKER_PACK_PUBLISHER = "sticker_pack_publisher"
        const val STICKER_PACK_ICON = "sticker_pack_icon"
        const val ANDROID_PLAY_STORE_LINK = "android_play_store_link"
        const val IOS_APP_DOWNLOAD_LINK = "ios_app_download_link"
        const val PUBLISHER_EMAIL = "sticker_pack_publisher_email"
        const val PUBLISHER_WEBSITE = "sticker_pack_publisher_website"
        const val PRIVACY_POLICY_WEBSITE = "sticker_pack_privacy_policy_website"
        const val LICENSE_AGREEMENT_WEBSITE = "sticker_pack_license_agreement_website"
        const val IMAGE_DATA_VERSION = "image_data_version"
        const val AVOID_CACHE = "whatsapp_will_not_cache_stickers"
        const val ANIMATED_STICKER_PACK = "animated_sticker_pack"

        const val STICKER_FILE_NAME = "sticker_file_name"
        const val STICKER_EMOJI = "sticker_emoji"

        private val METADATA_COLUMNS = arrayOf(
            STICKER_PACK_IDENTIFIER,
            STICKER_PACK_NAME,
            STICKER_PACK_PUBLISHER,
            STICKER_PACK_ICON,
            ANDROID_PLAY_STORE_LINK,
            IOS_APP_DOWNLOAD_LINK,
            PUBLISHER_EMAIL,
            PUBLISHER_WEBSITE,
            PRIVACY_POLICY_WEBSITE,
            LICENSE_AGREEMENT_WEBSITE,
            IMAGE_DATA_VERSION,
            AVOID_CACHE,
            ANIMATED_STICKER_PACK
        )

        private val STICKER_COLUMNS = arrayOf(STICKER_FILE_NAME, STICKER_EMOJI)

        private const val METADATA = "metadata"
        private const val METADATA_CODE = 1
        private const val METADATA_CODE_FOR_SINGLE_PACK = 2
        private const val STICKERS = "stickers"
        private const val STICKERS_CODE = 3
        private const val STICKERS_ASSET = "stickers_asset"
        private const val STICKERS_ASSET_CODE = 4

        private const val CACHE_DIR = "sticker_cache"
    }

    private lateinit var authority: String
    private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH)

    // Cache for Firebase packs
    private var cachedPacks: List<Pack>? = null

    override fun onCreate(): Boolean {
        authority = "${context!!.packageName}.stickers"

        uriMatcher.addURI(authority, METADATA, METADATA_CODE)
        uriMatcher.addURI(authority, "$METADATA/*", METADATA_CODE_FOR_SINGLE_PACK)
        uriMatcher.addURI(authority, "$STICKERS/*", STICKERS_CODE)
        uriMatcher.addURI(authority, "$STICKERS_ASSET/*/*", STICKERS_ASSET_CODE)

        return true
    }

    private fun getAllPacks(): List<Pack> {
        val allPacks = mutableMapOf<String, Pack>()

        // 1. Statik Cache ve Disk Cache Senkronizasyonu
        if (StickerRepository.allPacksCache.isEmpty()) {
            val diskCache = StickerRepository.loadCacheFromDisk(context!!)
            if (diskCache.isNotEmpty()) {
                StickerRepository.allPacksCache = diskCache
            }
        }
        
        // Statik cache'deki tüm paketleri ekle
        StickerRepository.allPacksCache.forEach { allPacks[it.id] = it }

        // 2. Lokal Assets (Lokal paketler her zaman öncelikli ve güvenli)
        Loader.load(context!!).forEach { allPacks[it.id] = it }

        // 3. Custom Paketler (Kullanıcının kendi yaptıkları)
        val customDir = File(context!!.filesDir, "custom_stickers")
        if (customDir.exists() && customDir.isDirectory) {
            customDir.listFiles()?.forEach { packDir ->
                if (packDir.isDirectory && packDir.name.startsWith("custom_")) {
                    CustomStickerManager.toWhatsAppPack(context!!, packDir.name)?.let {
                        allPacks[packDir.name] = it
                    }
                }
            }
        }

        // 4. Fallback: Cache Dizini Taraması (Firebase paketleri için son çare)
        val cacheDir = File(context!!.cacheDir, CACHE_DIR)
        if (cacheDir.exists() && cacheDir.isDirectory) {
            cacheDir.listFiles()?.forEach { packDir ->
                if (packDir.isDirectory && !packDir.name.startsWith("custom_") && !allPacks.containsKey(packDir.name)) {
                    val packId = packDir.name
                    val trayFile = File(packDir, "tray.webp")
                    if (trayFile.exists()) {
                        val stickers = packDir.listFiles { _, name ->
                            name.endsWith(".webp") && name != "tray.webp"
                        }
                            ?.sortedBy { it.name }
                            ?.map { Sticker(file = it.name, emojis = listOf("😊")) }
                            ?: emptyList()

                        if (stickers.isNotEmpty()) {
                            val isAnimated = File(packDir, ".animated").exists()
                            allPacks[packId] = Pack(
                                id = packId,
                                name = packId.replace("_", " ").replaceFirstChar { it.uppercase() },
                                pub = "Sticky",
                                tray = "tray.webp",
                                stickers = stickers,
                                isPremium = false,
                                isAnimated = isAnimated 
                            )
                        }
                    }
                }
            }
        }

        return allPacks.values.toList()
    }

    private fun getPack(identifier: String): Pack? {
        return getAllPacks().find { it.id == identifier }
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?,
                       selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        return when (uriMatcher.match(uri)) {
            METADATA_CODE -> getAllStickerPacks()
            METADATA_CODE_FOR_SINGLE_PACK -> getSingleStickerPack(uri.lastPathSegment!!)
            STICKERS_CODE -> getStickersForPack(uri.lastPathSegment!!)
            else -> null
        }
    }

    private fun getAllStickerPacks(): Cursor {
        val cursor = MatrixCursor(METADATA_COLUMNS)
        getAllPacks().forEach { pack ->
            cursor.addRow(arrayOf(
                pack.id,
                pack.localizedName,
                pack.pub,
                pack.tray,
                "",
                "",
                pack.email,
                "",
                pack.privacy,
                pack.license,
                pack.version,
                if (pack.avoidCache) 1 else 0,
                if (pack.isAnimated) 1 else 0
            ))
        }
        return cursor
    }

    private fun getSingleStickerPack(identifier: String): Cursor {
        val cursor = MatrixCursor(METADATA_COLUMNS)
        getPack(identifier)?.let { pack ->
            cursor.addRow(arrayOf(
                pack.id,
                pack.localizedName,
                pack.pub,
                pack.tray,
                "",
                "",
                pack.email,
                "",
                pack.privacy,
                pack.license,
                pack.version,
                if (pack.avoidCache) 1 else 0,
                if (pack.isAnimated) 1 else 0
            ))
        }
        return cursor
    }

    private fun getStickersForPack(identifier: String): Cursor {
        val cursor = MatrixCursor(STICKER_COLUMNS)
        val pack = getPack(identifier)
        val stickers = pack?.stickers ?: emptyList()
        
        stickers.forEach { sticker ->
            // WhatsApp requires at least one valid emoji - filter out empty strings
            val validEmojis = sticker.emojis?.filter { it.isNotBlank() }
            val emojiString = if (validEmojis.isNullOrEmpty()) "😀" else validEmojis.joinToString(",")
            cursor.addRow(arrayOf(
                sticker.file,
                emojiString
            ))
        }

        // WhatsApp strictly requires at least 3 stickers. If we have 1 or 2, 
        // we can pad the rest with the first sticker so it passes the check.
        if (stickers.isNotEmpty() && stickers.size < 3) {
            val firstSticker = stickers[0]
            val validEmojis = firstSticker.emojis?.filter { it.isNotBlank() }
            val emojiString = if (validEmojis.isNullOrEmpty()) "😀" else validEmojis.joinToString(",")
            val missingCount = 3 - stickers.size
            for (i in 1..missingCount) {
                val dummyFileName = firstSticker.file.replace(".webp", "_copy$i.webp").replace(".png", "_copy$i.png")
                cursor.addRow(arrayOf(dummyFileName, emojiString))
            }
        }

        return cursor
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        if (uriMatcher.match(uri) != STICKERS_ASSET_CODE) return null

        val pathSegments = uri.pathSegments
        if (pathSegments.size != 3) return null

        val identifier = pathSegments[1]
        val requestedFileName = pathSegments[2]
        val fileName = if (requestedFileName.contains("_copy")) {
            requestedFileName.replace(Regex("_copy\\d+"), "")
        } else {
            requestedFileName
        }

        android.util.Log.d("StickerProvider", "openAssetFile: $identifier / $fileName")

        // WhatsApp tray dosyası için özel işlem (Hangi paket olursa olsun PNG 96x96 olmalı)
        if (fileName.startsWith("tray")) {
            val pngFile = getTrayAsPngForWhatsApp(identifier, fileName)
            if (pngFile != null && pngFile.exists()) {
                val pfd = ParcelFileDescriptor.open(pngFile, ParcelFileDescriptor.MODE_READ_ONLY)
                return AssetFileDescriptor(pfd, 0, pngFile.length())
            }
        }

        // 1. Custom paketler için filesDir/custom_stickers kontrol et (KALICI DEPOLAMA)
        if (identifier.startsWith("custom_")) {
            val customFile = File(context!!.filesDir, "custom_stickers/$identifier/$fileName")
            if (customFile.exists()) {
                val pfd = ParcelFileDescriptor.open(customFile, ParcelFileDescriptor.MODE_READ_ONLY)
                return AssetFileDescriptor(pfd, 0, customFile.length())
            }
        }

        // 2. Cache klasöründe ara (Firebase stickerleri veya senkronize edilmiş custom paketler)
        val cacheFile = File(context!!.cacheDir, "$CACHE_DIR/$identifier/$fileName")
        if (cacheFile.exists()) {
            val pfd = ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
            return AssetFileDescriptor(pfd, 0, cacheFile.length())
        }

        // 3. Assets klasöründe ara (lokal stickerleri)
        return try {
            context!!.assets.openFd("$identifier/$fileName")
        } catch (e: Exception) {
            null
        }
    }

    /**
     * WhatsApp için tray dosyasını 96x96 PNG olarak döndürür.
     * WhatsApp uygulama standartları gereği tray MUTLAKA 96x96 PNG olmalıdır.
     * Kaynak dosya yüksek çözünürlüklü (512x512) PNG veya WebP olabilir.
     */
    private fun getTrayAsPngForWhatsApp(identifier: String, originalFileName: String): File? {
        return try {
            android.util.Log.d("StickerProvider", "getTrayAsPngForWhatsApp: $identifier / $originalFileName")

            // Standart PNG çıktı dosyası (cached)
            val outputPngFile = File(context!!.cacheDir, "$CACHE_DIR/$identifier/tray_whatsapp.png")
            if (outputPngFile.exists() && outputPngFile.length() > 0) {
                android.util.Log.d("StickerProvider", "Cached PNG exists: ${outputPngFile.absolutePath}")
                return outputPngFile
            }

            // Kaynak dosyayı bul (PNG veya WebP olabilir)
            var sourceFile: File? = null

            // 1. Önce orijinal dosya adıyla dene (admin panelinden gelen)
            val cacheDir = File(context!!.cacheDir, "$CACHE_DIR/$identifier")
            val originalFile = File(cacheDir, originalFileName)
            if (originalFile.exists() && originalFile.length() > 0) {
                sourceFile = originalFile
                android.util.Log.d("StickerProvider", "Found original tray: ${originalFile.absolutePath}")
            }

            // 2. Cache'de tray ile başlayan dosyaları ara (PNG veya WebP)
            if (sourceFile == null && cacheDir.exists()) {
                sourceFile = cacheDir.listFiles()?.find {
                    it.name.startsWith("tray") && (it.name.endsWith(".png") || it.name.endsWith(".webp"))
                }
                if (sourceFile != null) {
                    android.util.Log.d("StickerProvider", "Found tray by search: ${sourceFile.absolutePath}")
                }
            }

            // 3. Custom paketler için filesDir'da ara
            if (sourceFile == null && identifier.startsWith("custom_")) {
                val customDir = File(context!!.filesDir, "custom_stickers/$identifier")
                if (customDir.exists()) {
                    sourceFile = customDir.listFiles()?.find {
                        it.name.startsWith("tray") && (it.name.endsWith(".png") || it.name.endsWith(".webp"))
                    }
                    if (sourceFile != null) {
                        android.util.Log.d("StickerProvider", "Found custom tray: ${sourceFile.absolutePath}")
                    }
                }
            }

            // 4. SON ÇARE: Herhangi bir sticker'ı tray olarak kullan
            if (sourceFile == null && cacheDir.exists()) {
                sourceFile = cacheDir.listFiles()?.find { it.name.endsWith(".webp") || it.name.endsWith(".png") }
                if (sourceFile != null) {
                    android.util.Log.d("StickerProvider", "Found fallback sticker for tray: ${sourceFile.absolutePath}")
                }
            }

            if (sourceFile == null || !sourceFile.exists()) {
                android.util.Log.e("StickerProvider", "Tray source file not found for $identifier even as fallback")
                return null
            }

            // Kaynak dosyayı decode et
            val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath)
            if (bitmap == null) {
                android.util.Log.e("StickerProvider", "Failed to decode tray bitmap: ${sourceFile.absolutePath}")
                return null
            }

            // 96x96 boyutuna ölçekle (WhatsApp gereksinimi)
            val scaled = Bitmap.createScaledBitmap(bitmap, 96, 96, true)

            // PNG olarak kaydet
            outputPngFile.parentFile?.mkdirs()
            FileOutputStream(outputPngFile).use { out ->
                scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            if (bitmap != scaled) scaled.recycle()
            bitmap.recycle()

            android.util.Log.d("StickerProvider", "PNG created: ${outputPngFile.absolutePath}, size=${outputPngFile.length()}")
            outputPngFile
        } catch (e: Exception) {
            android.util.Log.e("StickerProvider", "Error in getTrayAsPngForAnimated", e)
            null
        }
    }

    override fun getType(uri: Uri): String {
        return when (uriMatcher.match(uri)) {
            METADATA_CODE -> "vnd.android.cursor.dir/vnd.$authority.$METADATA"
            METADATA_CODE_FOR_SINGLE_PACK -> "vnd.android.cursor.item/vnd.$authority.$METADATA"
            STICKERS_CODE -> "vnd.android.cursor.dir/vnd.$authority.$STICKERS"
            STICKERS_ASSET_CODE -> {
                val fileName = uri.lastPathSegment ?: ""
                val identifier = if (uri.pathSegments.size >= 2) uri.pathSegments[1] else ""
                // Tray için daima PNG döndür (WhatsApp standardı)
                if (fileName.startsWith("tray")) {
                    return "image/png"
                }
                "image/webp"
            }
            else -> "image/webp"
        }
    }

    override fun insert(uri: Uri, values: ContentValues?) = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?) = 0
}
