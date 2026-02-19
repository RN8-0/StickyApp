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
        } catch (e: Exception) {
            Log.e("StickyApp", "AdMob initialization failed: ${e.message}", e)
        }

        // Firebase paketlerini EN ERKEN ANDA yüklemeye başla
        // Kullanıcı onboarding/login ekranlarındayken veriler arka planda inecek
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("StickyApp", "Starting early pack preload...")
                val packs = StickerRepository.loadPacks(this@StickyApp, forceRefresh = false)
                Log.d("StickyApp", "Early preload done: ${packs.size} packs loaded")
                if (packs.isNotEmpty()) {
                    StickyGlideModule.preloadStickerPreviews(this@StickyApp, packs, packCount = 30, stickersPerPack = 5)
                }
            } catch (e: Exception) {
                Log.e("StickyApp", "Early preload error: ${e.message}")
            }
        }
    }
}
