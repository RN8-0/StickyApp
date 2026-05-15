package com.sticly

import android.content.Context
import android.util.Log
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Priority
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.load.engine.cache.LruResourceCache
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Glide yapılandırması - OkHttp bağlantı havuzu + agresif önbellekleme
 */
@GlideModule
class StickyGlideModule : AppGlideModule() {

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        // RAM'in 1/4'ünü bellek cache'e ayır — görseller scroll sırasında anında gelsin
        val memoryCacheSize = Runtime.getRuntime().maxMemory() / 4
        builder.setMemoryCache(LruResourceCache(memoryCacheSize))
        builder.setDiskCache(InternalCacheDiskCacheFactory(context, "glide_cache", 350L * 1024 * 1024))
        builder.setDefaultRequestOptions(
            com.bumptech.glide.request.RequestOptions()
                .format(com.bumptech.glide.load.DecodeFormat.PREFER_ARGB_8888)
        )
    }

    override fun isManifestParsingEnabled(): Boolean = false

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        val client = OkHttpClient.Builder()
            .connectionPool(ConnectionPool(10, 2, TimeUnit.MINUTES))
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
        registry.replace(GlideUrl::class.java, InputStream::class.java, OkHttpUrlLoader.Factory(client))
    }

    companion object {
        private const val TAG = "StickyGlide"

        fun preloadPopularPacks(context: Context, popularPacks: List<Pack>) {
            if (popularPacks.isEmpty()) return

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val urls = mutableListOf<String>()
                    popularPacks.forEach { pack ->
                        if (pack.category != "custom") {
                            pack.stickers.take(5).forEach { sticker ->
                                val url = if (sticker.url.isNotEmpty()) sticker.url
                                else if (pack.storagePath.isNotEmpty()) StickerRepository.getStickerDirectUrl(pack.id, sticker.file, pack.storagePath)
                                else ""
                                if (url.isNotEmpty()) urls.add(url)
                            }
                        }
                    }

                    // 4 paralel indirme — hızlı ön yükleme
                    urls.chunked(4).forEach { batch ->
                        batch.map { url ->
                            async(Dispatchers.IO) {
                                try {
                                    Glide.with(context.applicationContext)
                                        .asFile()
                                        .load(url)
                                        .diskCacheStrategy(DiskCacheStrategy.DATA)
                                        .priority(Priority.LOW)
                                        .submit()
                                        .get(6, TimeUnit.SECONDS)
                                } catch (_: Exception) { null }
                            }
                        }.awaitAll()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Popular packs preload error: ${e.message}")
                }
            }
        }

        /**
         * Ana listedeki ilk birkaç paketi hafifçe ön yükle.
         */
        fun preloadFeedPacks(
            context: Context,
            packs: List<Any>,
            preloadCount: Int = 3,
            stickersPerPack: Int = 3
        ) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val urls = mutableListOf<String>()
                    var count = 0
                    for (item in packs) {
                        if (count >= preloadCount) break
                        val pack = item as? Pack ?: continue
                        if (pack.category == "custom") continue
                        pack.stickers.take(stickersPerPack).forEach { sticker ->
                            val url = if (sticker.url.isNotEmpty()) sticker.url
                            else if (pack.storagePath.isNotEmpty()) StickerRepository.getStickerDirectUrl(pack.id, sticker.file, pack.storagePath)
                            else ""
                            if (url.isNotEmpty()) urls.add(url)
                        }
                        count++
                    }

                    Log.d(TAG, "Preloading ${urls.size} sticker URLs from first $count packs")

                    val highPriority = urls.take(9)
                    val lowPriority = urls.drop(9)

                    highPriority.chunked(3).forEach { batch ->
                        batch.map { url ->
                            async(Dispatchers.IO) {
                                try {
                                    Glide.with(context.applicationContext)
                                        .asFile()
                                        .load(url)
                                        .diskCacheStrategy(DiskCacheStrategy.DATA)
                                        .priority(Priority.HIGH)
                                        .submit()
                                        .get(6, TimeUnit.SECONDS)
                                } catch (_: Exception) { null }
                            }
                        }.awaitAll()
                    }

                    lowPriority.chunked(2).forEach { batch ->
                        batch.map { url ->
                            async(Dispatchers.IO) {
                                try {
                                    Glide.with(context.applicationContext)
                                        .asFile()
                                        .load(url)
                                        .diskCacheStrategy(DiskCacheStrategy.DATA)
                                        .priority(Priority.LOW)
                                        .submit()
                                        .get(8, TimeUnit.SECONDS)
                                } catch (_: Exception) { null }
                            }
                        }.awaitAll()
                    }
                    Log.d(TAG, "Feed preload complete")
                } catch (e: Exception) {
                    Log.e(TAG, "Feed preload error: ${e.message}")
                }
            }
        }

        fun preloadStickerPreviews(context: Context, packs: List<Pack>, packCount: Int = 15, stickersPerPack: Int = 5) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val urls = mutableListOf<String>()

                    packs.take(packCount).forEach { pack ->
                        if (pack.category != "custom") {
                            pack.stickers.take(stickersPerPack).forEach { sticker ->
                                val url = if (sticker.url.isNotEmpty()) sticker.url
                                else if (pack.storagePath.isNotEmpty()) StickerRepository.getStickerDirectUrl(pack.id, sticker.file, pack.storagePath)
                                else ""
                                if (url.isNotEmpty()) urls.add(url)
                            }
                        }
                    }

                    // 4 paralel indirme
                    urls.chunked(4).forEach { batch ->
                        batch.map { url ->
                            async(Dispatchers.IO) {
                                try {
                                    Glide.with(context.applicationContext)
                                        .asFile()
                                        .load(url)
                                        .diskCacheStrategy(DiskCacheStrategy.DATA)
                                        .submit()
                                        .get(6, TimeUnit.SECONDS)
                                } catch (_: Exception) { null }
                            }
                        }.awaitAll()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Download error: ${e.message}")
                }
            }
        }
    }
}
