package com.sticly

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

/**
 * Kullanıcının oluşturduğu özel sticker paketlerini yönetir.
 * Sticker dosyaları filesDir/custom_stickers/ dizinine kaydedilir (KALICI DEPOLAMA)
 * Bu sayede kullanıcının oluşturduğu çıkartmalar sistem tarafından silinmez.
 */
object CustomStickerManager {

    // KRITIK: filesDir kullan - kalıcı depolama, sistem tarafından silinmez
    private const val CUSTOM_DIR = "custom_stickers"
    // Eski cache dizini (migration için)
    private const val OLD_CACHE_DIR = "sticker_cache"
    private const val PACK_INFO_FILE = "pack_info.json"
    private const val TRAY_FILE = "tray.webp"
    private const val STICKER_PREFIX = "sticker_"

    private val gson = Gson()

    // Migration yapıldı mı kontrolü
    private var migrationDone = false

    /**
     * Özel sticker paketleri için data class
     */
    data class CustomPack(
        @SerializedName("id") val id: String,
        @SerializedName("name") val name: String,
        @SerializedName("publisher") val publisher: String = "Sticky Kullanıcısı",
        @SerializedName("sticker_count") var stickerCount: Int = 0,
        @SerializedName("version") var version: Int = 1, // Long timestamp yerine Int counter
        @SerializedName("created_at") val createdAt: Long = System.currentTimeMillis(),
        @SerializedName("is_animated") var isAnimated: Boolean = false
    )

    /**
     * Özel paketlerin saklandığı ana dizini döndürür (filesDir/custom_stickers - KALICI)
     */
    private fun getCustomDir(context: Context): File {
        // Migration işlemini yap (ilk çağrıda)
        migrateFromCacheIfNeeded(context)

        val dir = File(context.filesDir, CUSTOM_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Belirli bir paketin dizinini döndürür
     */
    private fun getPackDir(context: Context, packId: String): File {
        val dir = File(getCustomDir(context), packId)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Eski cache dizininden kalıcı dizine migration yapar
     * Kullanıcının eski çıkartmalarını korur
     */
    private fun migrateFromCacheIfNeeded(context: Context) {
        if (migrationDone) return
        migrationDone = true

        try {
            val oldCacheDir = File(context.cacheDir, OLD_CACHE_DIR)
            val newCustomDir = File(context.filesDir, CUSTOM_DIR)

            if (!oldCacheDir.exists()) return

            // Eski cache'deki custom paketleri bul ve taşı
            oldCacheDir.listFiles()?.forEach { packDir ->
                if (packDir.isDirectory && packDir.name.startsWith("custom_")) {
                    val newPackDir = File(newCustomDir, packDir.name)

                    // Eğer yeni dizinde yoksa taşı
                    if (!newPackDir.exists()) {
                        newCustomDir.mkdirs()
                        packsCache = null
                        packDir.copyRecursively(newPackDir, overwrite = true)
                        packDir.deleteRecursively()
                        android.util.Log.d("CustomStickerManager", "Migrated pack: ${packDir.name}")
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("CustomStickerManager", "Migration error: ${e.message}")
        }
    }

    /**
     * Paketi WhatsApp provider için cache dizinine kopyalar
     * StickerProvider cache dizininden okur, bu yüzden gerekli
     */
    fun syncPackToCache(context: Context, packId: String) {
        try {
            val sourceDir = getPackDir(context, packId)
            val cacheDir = File(context.cacheDir, OLD_CACHE_DIR)
            val destDir = File(cacheDir, packId)

            if (sourceDir.exists()) {
                destDir.mkdirs()
                sourceDir.listFiles()?.forEach { file ->
                    val destFile = File(destDir, file.name)
                    if (!destFile.exists() || file.lastModified() > destFile.lastModified()) {
                        file.copyTo(destFile, overwrite = true)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("CustomStickerManager", "Sync to cache error: ${e.message}")
        }
    }

    /**
     * Tüm custom paketleri cache'e senkronize eder
     */
    fun syncAllPacksToCache(context: Context) {
        getCustomPacks(context).forEach { pack ->
            syncPackToCache(context, pack.id)
        }
    }

    /**
     * Yeni bir özel paket oluşturur
     * @return Oluşturulan paketin ID'si
     */
    fun createPack(context: Context, name: String, isAnimated: Boolean = false): String {
        val packId = "custom_${UUID.randomUUID().toString().take(8)}"
        val packDir = getPackDir(context, packId)

        val pack = CustomPack(
            id = packId,
            name = name.ifEmpty { "Paketim" },
            isAnimated = isAnimated
        )

        // pack_info.json kaydet
        val infoFile = File(packDir, PACK_INFO_FILE)
        writePackInfo(infoFile, pack)

        // Firebase'e özel paket sayısını artır
        PreferencesHelper.incrementCustomPacksCount(context)

        return packId
    }

    /**
     * Pakete sticker ekler - Bitmap'ten (PNG, JPG için)
     * @param bitmap Sticker olarak eklenecek resim (512x512'ye ölçeklenecek)
     * @return Başarılı ise true
     */
    fun addStickerToPack(context: Context, packId: String, bitmap: Bitmap): Boolean {
        val packDir = getPackDir(context, packId)
        val pack = getPackInfo(context, packId) ?: return false

        // WhatsApp limiti: maksimum 30 sticker
        if (pack.stickerCount >= 30) return false

        // Sticker numarası ve dosya adı
        val stickerIndex = pack.stickerCount + 1
        val stickerFile = File(packDir, "${STICKER_PREFIX}${stickerIndex}.webp")

        // 1. Sticker Kaydet (512x512, <100KB)
        val success = saveStickerAsWebP(bitmap, stickerFile, 512)
        if (!success) return false

        // 2. İlk sticker ise tray (kapak) olarak da kaydet (96x96, <50KB)
        if (pack.stickerCount == 0) {
            val trayFile = File(packDir, TRAY_FILE)
            saveStickerAsWebP(bitmap, trayFile, 96)
        }

        // 3. Pack info güncelle ve versiyonu artır (Atomic)
        pack.stickerCount = stickerIndex
        pack.version += 1 // Versiyonu bir artır

        val infoFile = File(packDir, PACK_INFO_FILE)
        writePackInfo(infoFile, pack)

        // 4. Firebase'e toplam stiker sayısını artır
        PreferencesHelper.incrementTotalStickersAdded(context)

        // 5. WhatsApp'ı bildir
        notifyWhatsApp(context, packId)

        return true
    }

    /**
     * URI'den sticker ekler - GIF, PNG, JPG, WebP destekler
     * GIF ve Animated WebP dosyaları animasyonlu olarak kalır
     * @param uri Dosya URI'si
     * @param mimeType Dosya tipi (image/gif, image/webp, image/png, image/jpeg)
     * @return Başarılı ise true
     */
    fun addStickerFromUri(context: Context, packId: String, uri: Uri, mimeType: String?): Boolean {
        val packDir = getPackDir(context, packId)
        val pack = getPackInfo(context, packId) ?: return false

        // WhatsApp limiti: maksimum 30 sticker
        if (pack.stickerCount >= 30) return false

        val stickerIndex = pack.stickerCount + 1
        val stickerFile = File(packDir, "${STICKER_PREFIX}${stickerIndex}.webp")

        val success = when {
            // WebP dosyası - doğrudan kopyala (animated olabilir)
            mimeType == "image/webp" -> {
                copyUriToFile(context, uri, stickerFile)
            }
            // GIF dosyası - doğrudan kopyala ve uzantıyı webp yap
            // WhatsApp animated sticker olarak işaretlendiğinde GIF gibi WebP'leri de kabul eder
            mimeType == "image/gif" -> {
                // GIF'i bitmap olarak yükle ve WebP'ye dönüştür (animasyon kaybolur)
                // TODO: Gelecekte animated GIF -> animated WebP dönüşümü eklenebilir
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    val result = saveStickerAsWebP(bitmap, stickerFile, 512)
                    bitmap.recycle()
                    result
                } else false
            }
            // PNG, JPG veya diğer - bitmap olarak yükle ve WebP'ye dönüştür
            else -> {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    val result = saveStickerAsWebP(bitmap, stickerFile, 512)
                    bitmap.recycle()
                    result
                } else false
            }
        }

        if (!success) return false

        // İlk sticker ise tray olarak da kaydet
        if (pack.stickerCount == 0) {
            val trayFile = File(packDir, TRAY_FILE)
            // Tray için her zaman static WebP kullan (bitmap'ten)
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            if (bitmap != null) {
                saveStickerAsWebP(bitmap, trayFile, 96)
                bitmap.recycle()
            }
        }

        // Pack info güncelle
        pack.stickerCount = stickerIndex
        pack.version += 1

        val infoFile = File(packDir, PACK_INFO_FILE)
        writePackInfo(infoFile, pack)

        // Firebase'e toplam stiker sayısını artır
        PreferencesHelper.incrementTotalStickersAdded(context)

        notifyWhatsApp(context, packId)

        return true
    }

    /**
     * Animated WebP dosyasını doğrudan pakete ekler (animasyon korunur)
     * @param sourceFile Kaynak animated WebP dosyası
     * @return Başarılı ise true
     */
    fun addAnimatedStickerFromFile(context: Context, packId: String, sourceFile: File): Boolean {
        val packDir = getPackDir(context, packId)
        val pack = getPackInfo(context, packId) ?: return false

        if (pack.stickerCount >= 30) return false

        val stickerIndex = pack.stickerCount + 1
        val stickerFile = File(packDir, "${STICKER_PREFIX}${stickerIndex}.webp")

        // Dosyayı kopyala
        val success = try {
            sourceFile.copyTo(stickerFile, overwrite = true)
            true
        } catch (e: Exception) {
            android.util.Log.e("CustomStickerManager", "Error copying animated sticker: ${e.message}")
            false
        }

        if (!success) return false

        // İlk sticker ise tray olarak bitmap versiyonunu kaydet
        if (pack.stickerCount == 0) {
            val trayFile = File(packDir, TRAY_FILE)
            val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath)
            if (bitmap != null) {
                saveStickerAsWebP(bitmap, trayFile, 96)
                bitmap.recycle()
            }
        }

        pack.stickerCount = stickerIndex
        pack.version += 1

        val infoFile = File(packDir, PACK_INFO_FILE)
        writePackInfo(infoFile, pack)

        // Firebase'e toplam stiker sayısını artır
        PreferencesHelper.incrementTotalStickersAdded(context)

        notifyWhatsApp(context, packId)

        return true
    }

    /**
     * URI'den dosyayı kopyalar
     */
    private fun copyUriToFile(context: Context, uri: Uri, destFile: File): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            destFile.exists() && destFile.length() > 0
        } catch (e: Exception) {
            android.util.Log.e("CustomStickerManager", "Error copying URI to file: ${e.message}")
            false
        }
    }

    /**
     * Resmi belirlenen boyutta ve WebP formatında optimize ederek kaydeder.
     * WhatsApp limitlerini (boyut ve dosya boyutu) garantiler.
     */
    private fun saveStickerAsWebP(bitmap: Bitmap, file: File, size: Int): Boolean {
        return try {
            // WhatsApp için KRITIK: Görsel kare (size x size) olmalıdır.
            // Bozulmayı (stretching) önlemek için aspect ratio korunmalı ve şeffaflıkla doldurulmalıdır.
            val finalBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(finalBitmap)
            
            val scale = size.toFloat() / Math.max(bitmap.width, bitmap.height)
            val newWidth = (bitmap.width * scale).toInt()
            val newHeight = (bitmap.height * scale).toInt()
            
            val left = (size - newWidth) / 2
            val top = (size - newHeight) / 2
            
            val destRect = android.graphics.Rect(left, top, left + newWidth, top + newHeight)
            canvas.drawBitmap(bitmap, null, destRect, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))

            FileOutputStream(file).use { out ->
                // WhatsApp için KRITIK: Lossy WebP daha uyumludur.
                // Kalite 80, 100KB sınırının çok altında kalmasını sağlar.
                finalBitmap.compress(Bitmap.CompressFormat.WEBP, 80, out)
            }

            finalBitmap.recycle()
            
            // Dosya boyutu kontrolü
            val fileSizeKB = file.length() / 1024
            android.util.Log.d("CustomStickerManager", "Saved WebP: ${file.name}, Size: ${fileSizeKB}KB")
            
            file.exists() && file.length() > 0
        } catch (e: Exception) {
            android.util.Log.e("CustomStickerManager", "Error saving WebP: ${e.message}")
            false
        }
    }

    /**
     * Paketin kapak fotoğrafını ayarlar
     * @param bitmap Kapak olarak kullanılacak resim (96x96'ya ölçeklenecek)
     */
    fun setPackCover(context: Context, packId: String, bitmap: Bitmap): Boolean {
        val packDir = getPackDir(context, packId)
        val trayFile = File(packDir, TRAY_FILE)
        
        val success = saveStickerAsWebP(bitmap, trayFile, 96)
        if (success) {
            getPackInfo(context, packId)?.let { pack ->
                pack.version += 1
                val infoFile = File(packDir, PACK_INFO_FILE)
                writePackInfo(infoFile, pack)
            }
            notifyWhatsApp(context, packId)
        }
        return success
    }

    /**
     * Paketin kapak fotoğrafı var mı kontrol eder
     */
    fun hasCover(context: Context, packId: String): Boolean {
        val packDir = getPackDir(context, packId)
        val trayFile = File(packDir, TRAY_FILE)
        return trayFile.exists() && trayFile.length() > 0
    }

    /**
     * Paketten sticker siler
     */
    fun removeStickerFromPack(context: Context, packId: String, stickerIndex: Int): Boolean {
        val packDir = getPackDir(context, packId)
        val pack = getPackInfo(context, packId) ?: return false
        
        val stickerFile = File(packDir, "${STICKER_PREFIX}${stickerIndex}.webp")
        if (!stickerFile.exists()) return false
        
        val deleted = stickerFile.delete()
        if (deleted) {
            reorderStickers(packDir, pack.stickerCount)
            pack.stickerCount--
            pack.version += 1
            val infoFile = File(packDir, PACK_INFO_FILE)
            writePackInfo(infoFile, pack)
            notifyWhatsApp(context, packId)
        }
        return deleted
    }

    /**
     * Birden fazla çıkartmayı paketten siler (Toplu silme)
     */
    fun removeStickersFromPack(context: Context, packId: String, indices: List<Int>): Boolean {
        val packDir = getPackDir(context, packId)
        val pack = getPackInfo(context, packId) ?: return false
        
        var deletedCount = 0
        // Numaraları büyükten küçüğe silersek dosya sistemi işlemleri daha güvenli olur
        indices.sortedDescending().forEach { index ->
            val stickerFile = File(packDir, "${STICKER_PREFIX}${index}.webp")
            if (stickerFile.exists() && stickerFile.delete()) {
                deletedCount++
            }
        }
        
        if (deletedCount > 0) {
            // Sticker numaralarını yeniden düzenle
            reorderStickers(packDir, pack.stickerCount)
            
            // Pack info güncelle ve versiyonu güncelle
            pack.stickerCount -= deletedCount
            if (pack.stickerCount < 0) pack.stickerCount = 0
            pack.version += 1
            val infoFile = File(packDir, PACK_INFO_FILE)
            writePackInfo(infoFile, pack)
            
            notifyWhatsApp(context, packId)
            return true
        }
        
        return false
    }

    /**
     * Sticker dosyalarını yeniden numaralandırır
     */
    private fun reorderStickers(packDir: File, currentCount: Int) {
        val stickers = mutableListOf<File>()
        for (i in 1..currentCount) {
            val file = File(packDir, "${STICKER_PREFIX}${i}.webp")
            if (file.exists()) stickers.add(file)
        }
        
        stickers.forEachIndexed { index, file ->
            val newName = "${STICKER_PREFIX}${index + 1}.webp"
            if (file.name != newName) {
                file.renameTo(File(packDir, newName))
            }
        }
    }

    // getCustomPacks() is called straight from dialog-building code on the main thread, so the
    // directory scan + JSON parse used to run on every open. The list is cached and dropped again
    // by writePackInfo()/deletePack(), i.e. by every path that can change it.
    @Volatile private var packsCache: List<CustomPack>? = null

    private fun writePackInfo(infoFile: File, pack: CustomPack) {
        infoFile.writeText(gson.toJson(pack))
        packsCache = null
    }

    /**
     * Tüm özel paketleri listeler
     */
    fun getCustomPacks(context: Context): List<CustomPack> {
        packsCache?.let { return it }
        val customDir = getCustomDir(context)
        val packs = mutableListOf<CustomPack>()

        customDir.listFiles()?.forEach { packDir ->
            // Sadece custom_ ile başlayan paketler özel paketler
            if (packDir.isDirectory && packDir.name.startsWith("custom_")) {
                getPackInfo(context, packDir.name)?.let { pack ->
                    // Geçerli paket mi kontrol et (en az pack_info.json ve dosyaları olmalı)
                    val infoFile = File(packDir, PACK_INFO_FILE)
                    if (infoFile.exists()) {
                        packs.add(pack)
                    }
                }
            }
        }

        return packs.sortedByDescending { it.createdAt }.also { packsCache = it }
    }

    /**
     * Belirli bir paketin bilgilerini döndürür
     */
    fun getPackInfo(context: Context, packId: String): CustomPack? {
        val packDir = getPackDir(context, packId)
        val infoFile = File(packDir, PACK_INFO_FILE)
        
        return if (infoFile.exists()) {
            try {
                gson.fromJson(infoFile.readText(), CustomPack::class.java)
            } catch (e: Exception) {
                null
            }
        } else null
    }

    /**
     * Paketteki tüm sticker dosyalarını döndürür
     */
    fun getStickerFiles(context: Context, packId: String): List<File> {
        val packDir = getPackDir(context, packId)
        val pack = getPackInfo(context, packId) ?: return emptyList()

        val files = mutableListOf<File>()
        for (i in 1..pack.stickerCount) {
            val file = File(packDir, "${STICKER_PREFIX}${i}.webp")
            if (file.exists()) files.add(file)
        }
        return files
    }

    /**
     * Belirli bir sticker dosyasını döndürür
     */
    fun getStickerFile(context: Context, packId: String, fileName: String): File? {
        val packDir = getPackDir(context, packId)
        val file = File(packDir, fileName)
        return if (file.exists()) file else null
    }

    /**
     * Paketin tray resmini döndürür
     */
    fun getTrayFile(context: Context, packId: String): File? {
        val packDir = getPackDir(context, packId)
        val trayFile = File(packDir, TRAY_FILE)
        return if (trayFile.exists()) trayFile else null
    }

    /**
     * Paketi tamamen siler
     */
    fun deletePack(context: Context, packId: String): Boolean {
        val packDir = getPackDir(context, packId)
        val deleted = packDir.deleteRecursively()
        packsCache = null

        if (deleted) {
            // Firebase'de özel paket sayısını azalt
            PreferencesHelper.decrementCustomPacksCount(context)
            notifyWhatsApp(context, packId)
        }

        return deleted
    }

    /**
     * Paketin WhatsApp'a eklenmeye uygun olup olmadığını kontrol eder
     * WhatsApp en az 3 sticker gerektirir
     */
    fun isPackValid(context: Context, packId: String): Boolean {
        val pack = getPackInfo(context, packId) ?: return false
        return pack.stickerCount >= 1
    }

    /**
     * Özel paketi Pack modeline dönüştürür (StickerProvider için)
     */
    fun toWhatsAppPack(context: Context, packId: String): Pack? {
        val customPack = getPackInfo(context, packId) ?: return null
        val packDir = getPackDir(context, packId)
        
        val stickers = mutableListOf<Sticker>()
        for (i in 1..customPack.stickerCount) {
            val file = File(packDir, "${STICKER_PREFIX}${i}.webp")
            if (file.exists() && file.length() > 0) {
                stickers.add(Sticker(file = file.name, emojis = listOf("😊")))
            }
        }

        // Fix stickerCount if it doesn't match actual files
        if (stickers.size != customPack.stickerCount) {
            customPack.stickerCount = stickers.size
            try {
                val infoFile = File(packDir, PACK_INFO_FILE)
                writePackInfo(infoFile, customPack)
            } catch (_: Exception) {}
        }
        android.util.Log.d("CustomStickerManager", "toWhatsAppPack packId=$packId stickerCount=${customPack.stickerCount} actualStickers=${stickers.size} files=${stickers.map { it.file }}")
        
        // Tray dosyası var mı kontrol et, yoksa ilk sticker'ı kullan
        val trayFile = File(packDir, TRAY_FILE)
        val trayName = if (trayFile.exists() && trayFile.length() > 0) {
            TRAY_FILE
        } else if (stickers.isNotEmpty()) {
            // Tray yoksa ilk sticker'ı tray olarak kopyala
            val firstStickerFile = File(packDir, stickers.first().file)
            if (firstStickerFile.exists()) {
                saveStickerAsWebP(BitmapFactory.decodeFile(firstStickerFile.absolutePath), trayFile, 96)
            }
            if (trayFile.exists()) TRAY_FILE else stickers.first().file
        } else {
            return null // Tray ve sticker yok - geçersiz paket
        }
        
        // Publisher'ı string resource'dan al (çoklu dil desteği)
        val publisher = context.getString(R.string.custom_pack_publisher)

        return Pack(
            id = customPack.id,
            name = customPack.name,
            pub = publisher,
            email = "",
            privacy = "",
            license = "",
            version = customPack.version.toString(),
            avoidCache = true, // WhatsApp'ın cache yapmasını engelle
            tray = trayName,
            stickers = stickers,
            isPremium = false,
            isAnimated = customPack.isAnimated
        )
    }

    /**
     * Sticker bitmap'i yükler
     */
    fun loadStickerBitmap(context: Context, packId: String, stickerIndex: Int): Bitmap? {
        val packDir = getPackDir(context, packId)
        val file = File(packDir, "${STICKER_PREFIX}${stickerIndex}.webp")
        return if (file.exists()) {
            BitmapFactory.decodeFile(file.absolutePath)
        } else null
    }

    /**
     * Paketin version numarasını zorla artırır ve WhatsApp'a bildirir
     * Bu, WhatsApp'ın paketi güncellemesini zorlar
     * @return Yeni version numarası, hata durumunda -1
     */
    fun forceUpdateVersion(context: Context, packId: String): Int {
        val packDir = getPackDir(context, packId)
        val pack = getPackInfo(context, packId) ?: return -1

        // Version'ı artır
        pack.version += 1

        // Pack info güncelle
        val infoFile = File(packDir, PACK_INFO_FILE)
        writePackInfo(infoFile, pack)

        android.util.Log.d("CustomStickerManager", "Force updated pack $packId to version ${pack.version}")

        // WhatsApp'a bildir
        notifyWhatsApp(context, packId)

        return pack.version
    }

    /**
     * WhatsApp'a verilerin değiştiğini bildirir
     */
    private fun notifyWhatsApp(context: Context, packId: String) {
        // KRITIK: Önce paketi cache'e senkronize et (StickerProvider cache'den okur)
        syncPackToCache(context, packId)

        val authority = "${context.packageName}.stickers"
        val contentResolver = context.contentResolver

        // 1. Genel metadata değişti bildirimi
        val metadataUri = android.net.Uri.parse("content://$authority/metadata")
        contentResolver.notifyChange(metadataUri, null)

        // 2. Spesifik paket metadata'sı değişti bildirimi
        val packUri = android.net.Uri.parse("content://$authority/metadata/$packId")
        contentResolver.notifyChange(packUri, null)

        // 3. Çıkartma dosyaları değişti bildirimi
        val stickersUri = android.net.Uri.parse("content://$authority/stickers/$packId")
        contentResolver.notifyChange(stickersUri, null)
    }

    /**
     * Custom sticker dosyasının yolunu döndürür (filesDir'dan)
     */
    fun getCustomStickerPath(context: Context, packId: String, fileName: String): File {
        return File(getPackDir(context, packId), fileName)
    }
}
