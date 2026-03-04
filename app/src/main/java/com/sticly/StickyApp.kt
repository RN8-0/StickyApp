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

        // SYNC: Disk cache'i hemen yükle (JSON parse, <50ms)
        // Bu sayede MainActivity açıldığında paketler hazır olur
        try {
            val diskPacks = StickerRepository.loadCacheFromDisk(this)
            if (diskPacks.isNotEmpty()) {
                StickerRepository.allPacksCache = diskPacks
            }
        } catch (_: Exception) {}

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
        }, 800)

        // Firebase'den güncel veriyi arka planda çek
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val packs = StickerRepository.loadPacks(this@StickyApp, forceRefresh = false)
                if (packs.isNotEmpty()) {
                    StickyGlideModule.preloadStickerPreviews(this@StickyApp, packs, packCount = 8, stickersPerPack = 2)
                }
            } catch (_: Exception) {}
        }
    }
}
