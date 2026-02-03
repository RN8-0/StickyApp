package com.sticly

import android.app.Application
import android.content.Context

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
            android.util.Log.d("StickyApp", "Starting AdManager initialization...")
            AdManager.initialize(this)
            android.util.Log.d("StickyApp", "AdManager.initialize() called successfully")
        } catch (e: Exception) {
            android.util.Log.e("StickyApp", "AdMob initialization failed: ${e.message}", e)
        }
    }
}
