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

        // ASYNC: Disk cache'i IO thread'de yükle — main thread'i bloklamadan
        CoroutineScope(Dispatchers.IO).launch {
            try {
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

        // Firebase'den güncel veriyi arka planda çek + önbellek thumbnail'larını önyükle.
        // Önceki ayar 8×2=16 thumbnail ile çok az kalıyordu; ana sayfada görünür olan yaklaşık
        // 5-6 paket × 5 önizleme = ~30 thumbnail'ı kapsamıyordu, kullanıcı her cold start'ta
        // ağdan tek tek indirme bekliyordu. preloadFeedPacks(15) ilk 30 görüntüyü HIGH önceliklı,
        // kalanı LOW önceliklı olarak paralel indirir.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val packs = StickerRepository.loadPacks(this@StickyApp, forceRefresh = false)
                if (packs.isNotEmpty()) {
                    StickyGlideModule.preloadFeedPacks(this@StickyApp, packs, preloadCount = 15)
                }
            } catch (_: Exception) {}
        }
    }
}
