package com.sticly

import android.app.Application
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class StickyApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(base))
    }

    override fun onCreate() {
        super.onCreate()
        
        // Karanlık temayı tamamen devre dışı bırak (Hep açık tema)
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)

        // Reklam sistemini başlat
        try {
            Log.d("StickyApp", "Starting AdManager initialization...")
            AdManager.initialize(this)
            Log.d("StickyApp", "AdManager.initialize() called successfully")

            // App Open Ad — uygulamaya her dönüşte reklam gösterir
            AppOpenAdManager(this).init()
            Log.d("StickyApp", "AppOpenAdManager initialized")
        } catch (e: Exception) {
            Log.e("StickyApp", "AdMob initialization failed: ${e.message}", e)
        }

        // PRE-WARM: Load disk cache synchronously so MainActivity has data instantly
        // This is fast (~10-50ms for JSON read) and eliminates the loading screen on warm starts
        try {
            val diskPacks = StickerRepository.loadCacheFromDisk(this)
            if (diskPacks.isNotEmpty()) {
                StickerRepository.allPacksCache = diskPacks
                Log.d("StickyApp", "Disk cache pre-warmed: ${diskPacks.size} packs")
            }
        } catch (e: Exception) {
            Log.e("StickyApp", "Disk cache pre-warm error: ${e.message}")
        }

        // Firebase paketlerini EN ERKEN ANDA yüklemeye başla
        // Kullanıcı onboarding/login ekranlarındayken veriler arka planda inecek
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("StickyApp", "Starting early pack preload...")
                val packs = StickerRepository.loadPacks(this@StickyApp, forceRefresh = false)
                Log.d("StickyApp", "Early preload done: ${packs.size} packs loaded")
                if (packs.isNotEmpty()) {
                    StickyGlideModule.preloadStickerPreviews(this@StickyApp, packs, packCount = 10, stickersPerPack = 3)
                }
            } catch (e: Exception) {
                Log.e("StickyApp", "Early preload error: ${e.message}")
            }
        }
    }
}
