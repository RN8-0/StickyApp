package com.sticly

import android.content.Context
import android.util.Log
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Priority
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.load.engine.cache.LruResourceCache
import com.bumptech.glide.module.AppGlideModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Glide yapılandırması - daha hızlı yükleme ve daha iyi önbellekleme
 */
@GlideModule
class StickyGlideModule : AppGlideModule() {

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        // Bellek önbelleği - varsayılanın 1.5 katı (daha fazla resmi bellekte tut)
        val memoryCacheSize = Runtime.getRuntime().maxMemory() / 4 // Max memory'nin 1/4'ü
        builder.setMemoryCache(LruResourceCache(memoryCacheSize))

        // Disk önbelleği - 150MB (sticker önizlemeleri için daha fazla alan)
        builder.setDiskCache(InternalCacheDiskCacheFactory(context, "glide_cache", 150 * 1024 * 1024))
    }

    override fun isManifestParsingEnabled(): Boolean = false

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        // Özel bileşenler eklenebilir
    }

    companion object {
        private const val TAG = "StickyGlide"

        /**
         * Popüler paketlerin çıkartma önizlemelerini EN YÜKSEK öncelikle yükle
         * Bu fonksiyon uygulama açılır açılmaz çağrılmalı
         */
        fun preloadPopularPacks(context: Context, popularPacks: List<Pack>) {
            if (popularPacks.isEmpty()) return

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    Log.d(TAG, "Preloading ${popularPacks.size} popular packs with IMMEDIATE priority...")

                    popularPacks.forEach { pack ->
                        if (pack.category != "custom") {
                            pack.stickers.take(5).forEach { sticker ->
                                val url = if (sticker.url.isNotEmpty()) {
                                    sticker.url
                                } else if (pack.storagePath.isNotEmpty()) {
                                    StickerRepository.getStickerDirectUrl(pack.id, sticker.file, pack.storagePath)
                                } else ""

                                if (url.isNotEmpty()) {
                                    try {
                                        Glide.with(context.applicationContext)
                                            .load(url)
                                            .diskCacheStrategy(DiskCacheStrategy.DATA)
                                            .priority(Priority.IMMEDIATE)
                                            .preload(200, 200)
                                    } catch (e: Exception) { }
                                }
                            }
                        }
                    }

                    Log.d(TAG, "Popular packs preload completed")
                } catch (e: Exception) {
                    Log.e(TAG, "Popular packs preload error: ${e.message}")
                }
            }
        }

        /**
         * Ana liste paketlerinin çıkartma önizlemelerini NORMAL öncelikle yükle
         * Popüler paketler yüklendikten sonra bunlar yüklenecek
         */
        fun preloadStickerPreviews(context: Context, packs: List<Pack>, packCount: Int = 8, stickersPerPack: Int = 4) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val urlsToPreload = mutableListOf<String>()

                    // İlk N paketten ilk M sticker URL'sini topla
                    packs.take(packCount).forEach { pack ->
                        if (pack.category != "custom") {
                            pack.stickers.take(stickersPerPack).forEach { sticker ->
                                val url = if (sticker.url.isNotEmpty()) {
                                    sticker.url
                                } else if (pack.storagePath.isNotEmpty()) {
                                    StickerRepository.getStickerDirectUrl(pack.id, sticker.file, pack.storagePath)
                                } else ""

                                if (url.isNotEmpty()) {
                                    urlsToPreload.add(url)
                                }
                            }
                        }
                    }

                    Log.d(TAG, "Preloading ${urlsToPreload.size} sticker previews with NORMAL priority...")

                    // Arka planda preload et - NORMAL öncelik
                    urlsToPreload.forEach { url ->
                        try {
                            Glide.with(context.applicationContext)
                                .load(url)
                                .diskCacheStrategy(DiskCacheStrategy.DATA)
                                .priority(Priority.NORMAL)
                                .preload(120, 120)
                        } catch (e: Exception) { }
                    }

                    Log.d(TAG, "Preload completed for ${urlsToPreload.size} stickers")
                } catch (e: Exception) {
                    Log.e(TAG, "Preload error: ${e.message}")
                }
            }
        }
    }
}
