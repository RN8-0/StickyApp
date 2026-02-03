package com.sticly

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.load.engine.cache.LruResourceCache
import com.bumptech.glide.module.AppGlideModule

/**
 * Glide yapılandırması - daha hızlı yükleme ve daha iyi önbellekleme
 */
@GlideModule
class StickyGlideModule : AppGlideModule() {

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        // Bellek önbelleği - varsayılanın 1.5 katı (daha fazla resmi bellekte tut)
        val memoryCacheSize = Runtime.getRuntime().maxMemory() / 4 // Max memory'nin 1/4'ü
        builder.setMemoryCache(LruResourceCache(memoryCacheSize))

        // Disk önbelleği - 100MB (varsayılan 250MB, sticker'lar için 100MB yeterli)
        builder.setDiskCache(InternalCacheDiskCacheFactory(context, "glide_cache", 100 * 1024 * 1024))
    }

    override fun isManifestParsingEnabled(): Boolean = false

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        // Özel bileşenler eklenebilir
    }
}
