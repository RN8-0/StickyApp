package com.sticly

import android.app.Application
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class StickyApp : Application() {
    companion object {
        var appOpenAdInstance: AppOpenAdManager? = null
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(base))
    }

    override fun onCreate() {
        super.onCreate()
        
        // Karanlık temayı tamamen devre dışı bırak (Hep açık tema)
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)

        // HIZLI BASLATMA: Lokal asset'leri hemen (sync) yukle - cok kucuk JSON, <5ms
        // Bu sayede MainActivity ilk karede mutlaka veri gorecek
        try {
            val localPacks = Loader.load(this)
            if (localPacks.isNotEmpty() && StickerRepository.allPacksCache.isEmpty()) {
                StickerRepository.allPacksCache = localPacks
            }
        } catch (_: Exception) {}

        // ASYNC: Disk cache + ag verisini arka planda yukle (ana thread bloklanmaz)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Disk cache'i oku ve memory cache'i guncelle
                val diskPacks = StickerRepository.loadCacheFromDisk(this@StickyApp)
                if (diskPacks.isNotEmpty()) {
                    StickerRepository.allPacksCache = diskPacks
                }
            } catch (_: Exception) {}

            // Uygulama acilir acilmaz ag verisini + ilk ekran kucuk resimlerini arka planda isit;
            // boylece MainActivity acildiginda Home hazir gelir ve pack onizlemeleri aninda cizilir.
            try {
                val packs = StickerRepository.loadPacks(this@StickyApp, forceRefresh = false)
                val urls = ArrayList<String>()
                packs.filter { !it.id.startsWith("custom_") }.take(10).forEach { pack ->
                    pack.stickers.take(5).forEach { s ->
                        val u = when {
                            s.url.isNotEmpty() -> s.url
                            pack.storagePath.isNotEmpty() ->
                                StickerRepository.getStickerDirectUrl(pack.id, s.file, pack.storagePath)
                            else -> ""
                        }
                        if (u.isNotEmpty()) urls.add(u)
                    }
                }
                urls.forEach { url ->
                    try {
                        com.bumptech.glide.Glide.with(applicationContext)
                            .asFile().load(url)
                            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.DATA)
                            .submit()
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }

        // Preload frequently used Lottie compositions into memory cache (async, non-blocking)
        val lottiesToPreload = listOf("Loading.json", "crown.json")
        lottiesToPreload.forEach { name ->
            com.airbnb.lottie.LottieCompositionFactory.fromAsset(this, name)
        }

        // Start monetization after the first home frames have had time to render.
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                Log.d("StickyApp", "Starting AdManager initialization...")
                AdManager.initialize(this)
                Log.d("StickyApp", "AdManager.initialize() called successfully")

                val adMgr = AppOpenAdManager(this)
                adMgr.init()
                appOpenAdInstance = adMgr
            } catch (e: Exception) {
                Log.e("StickyApp", "AdMob initialization failed: ${e.message}", e)
            }
        }, 8_000)
    }
}
