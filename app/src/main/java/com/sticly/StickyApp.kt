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
        }

        // Preload frequently used Lottie compositions into memory cache (async, non-blocking)
        val lottiesToPreload = listOf("Loading.json", "crown.json")
        lottiesToPreload.forEach { name ->
            com.airbnb.lottie.LottieCompositionFactory.fromAsset(this, name)
        }

        // AdMob is initialized on demand. Starting it during launch causes visible
        // jank on low/mid devices because the SDK loads Dynamite modules on Main.
    }
}
