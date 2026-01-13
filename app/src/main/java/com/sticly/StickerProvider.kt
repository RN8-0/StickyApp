package com.sticly

import android.content.*
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

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
        // Önce lokal paketleri yükle
        val localPacks = Loader.load(context!!)

        // Cache'deki Firebase paketlerini yükle
        val firebasePacks = getFirebasePacksFromCache()

        // Birleştir (aynı ID varsa Firebase versiyonu öncelikli)
        val allPacks = mutableMapOf<String, Pack>()
        localPacks.forEach { allPacks[it.id] = it }
        firebasePacks.forEach { allPacks[it.id] = it }

        return allPacks.values.toList()
    }

    private fun getFirebasePacksFromCache(): List<Pack> {
        val cacheDir = File(context!!.cacheDir, CACHE_DIR)
        if (!cacheDir.exists()) return emptyList()

        val packs = mutableListOf<Pack>()

        cacheDir.listFiles()?.filter { it.isDirectory }?.forEach { packDir ->
            val packId = packDir.name
            val stickerFiles = packDir.listFiles()?.filter {
                it.name.endsWith(".webp") && it.name != "tray.webp"
            }?.sortedBy { it.name } ?: emptyList()

            if (stickerFiles.size >= 3) {
                val stickers = stickerFiles.map { file ->
                    Sticker(
                        file = file.name,
                        emojis = listOf("😀"),
                        url = ""
                    )
                }

                val trayFile = File(packDir, "tray.webp")
                val trayName = if (trayFile.exists()) "tray.webp" else stickerFiles.firstOrNull()?.name ?: ""

                packs.add(Pack(
                    id = packId,
                    name = packId.replace("_", " ").replaceFirstChar { it.uppercase() },
                    pub = "Sticly",
                    tray = trayName,
                    stickers = stickers,
                    trayUrl = "",
                    isPremium = false
                ))
            }
        }

        return packs
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
                pack.name,
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
                1  // ANIMATED_STICKER_PACK = 1 (animasyonlu)
            ))
        }
        return cursor
    }

    private fun getSingleStickerPack(identifier: String): Cursor {
        val cursor = MatrixCursor(METADATA_COLUMNS)
        getPack(identifier)?.let { pack ->
            cursor.addRow(arrayOf(
                pack.id,
                pack.name,
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
                1  // ANIMATED_STICKER_PACK = 1 (animasyonlu)
            ))
        }
        return cursor
    }

    private fun getStickersForPack(identifier: String): Cursor {
        val cursor = MatrixCursor(STICKER_COLUMNS)
        getPack(identifier)?.stickers?.forEach { sticker ->
            cursor.addRow(arrayOf(
                sticker.file,
                sticker.emojis?.joinToString(",") ?: "😀"
            ))
        }
        return cursor
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        if (uriMatcher.match(uri) != STICKERS_ASSET_CODE) return null

        val pathSegments = uri.pathSegments
        if (pathSegments.size != 3) return null

        val identifier = pathSegments[1]
        val fileName = pathSegments[2]

        // Önce cache klasöründe ara (Firebase stickerleri)
        val cacheFile = File(context!!.cacheDir, "$CACHE_DIR/$identifier/$fileName")
        if (cacheFile.exists()) {
            val pfd = ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
            return AssetFileDescriptor(pfd, 0, cacheFile.length())
        }

        // Cache'de yoksa assets klasöründe ara (lokal stickerleri)
        return try {
            context!!.assets.openFd("$identifier/$fileName")
        } catch (e: Exception) {
            null
        }
    }

    override fun getType(uri: Uri): String {
        return when (uriMatcher.match(uri)) {
            METADATA_CODE -> "vnd.android.cursor.dir/vnd.$authority.$METADATA"
            METADATA_CODE_FOR_SINGLE_PACK -> "vnd.android.cursor.item/vnd.$authority.$METADATA"
            STICKERS_CODE -> "vnd.android.cursor.dir/vnd.$authority.$STICKERS"
            STICKERS_ASSET_CODE -> "image/webp"
            else -> "image/webp"
        }
    }

    override fun insert(uri: Uri, values: ContentValues?) = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?) = 0
}
