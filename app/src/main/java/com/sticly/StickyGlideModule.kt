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
        val memoryCacheSize = Runtime.getRuntime().maxMemory() / 8
        builder.setMemoryCache(LruResourceCache(memoryCacheSize))
        builder.setDiskCache(InternalCacheDiskCacheFactory(context, "glide_cache", 150 * 1024 * 1024))
    }

    override fun isManifestParsingEnabled(): Boolean = false

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        val client = OkHttpClient.Builder()
            .connectionPool(ConnectionPool(6, 2, TimeUnit.MINUTES))
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
                            pack.stickers.take(3).forEach { sticker ->
                                val url = if (sticker.url.isNotEmpty()) sticker.url
                                else if (pack.storagePath.isNotEmpty()) StickerRepository.getStickerDirectUrl(pack.id, sticker.file, pack.storagePath)
                                else ""
                                if (url.isNotEmpty()) urls.add(url)
                            }
                        }
                    }

                    // 4 paralel indirme — daha az GC baskısı
                    urls.chunked(4).forEach { batch ->
                        batch.map { url ->
                            async(Dispatchers.IO) {
                                try {
                                    Glide.with(context.applicationContext)
                                        .asFile()
                                        .load(url)
                                        .diskCacheStrategy(DiskCacheStrategy.DATA)
                                        .priority(Priority.IMMEDIATE)
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

        fun preloadStickerPreviews(context: Context, packs: List<Pack>, packCount: Int = 8, stickersPerPack: Int = 2) {
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

                    // 4 paralel indirme — hafif GC baskısı
                    urls.chunked(4).forEach { batch ->
                        batch.map { url ->
                            async(Dispatchers.IO) {
                                try {
                                    Glide.with(context.applicationContext)
                                        .asFile()
                                        .load(url)
                                        .diskCacheStrategy(DiskCacheStrategy.DATA)
                                        .submit()
                                        .get(8, TimeUnit.SECONDS)
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
