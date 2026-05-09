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
                // PocketBase'den guncel veriyi cek
                val packs = StickerRepository.loadPacks(this@StickyApp, forceRefresh = false)
                if (packs.isNotEmpty()) {
                    StickyGlideModule.preloadStickerPreviews(this@StickyApp, packs, packCount = 8, stickersPerPack = 2)
                }
            } catch (_: Exception) {}
        }

        // Preload frequently used Lottie compositions into memory cache (async, non-blocking)
        val lottiesToPreload = listOf("Loading.json", "crown.json")
        lottiesToPreload.forEach { name ->
            com.airbnb.lottie.LottieCompositionFactory.fromAsset(this, name)
        }

        // Reklam sistemini gecikmeli başlat (ilk karelerin hızlı render olması için)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                Log.d("StickyApp", "Starting AdManager initialization...")
                AdManager.initialize(this)
                Log.d("StickyApp", "AdManager.initialize() called successfully")

                // App Open Ad — günde bir kez, 3. paket açılışında
                val adMgr = AppOpenAdManager(this)
                adMgr.init()
                appOpenAdInstance = adMgr
            } catch (e: Exception) {
                Log.e("StickyApp", "AdMob initialization failed: ${e.message}", e)
            }
        }, 2500)
    }
}
