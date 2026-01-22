package com.sticly

import android.app.Application
import android.content.Context

class StickyApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(base))
    }

    override fun onCreate() {
        super.onCreate()
        // Reklam sistemini başlat (emulatörde çalışmayabilir)
        try {
            AdManager.initialize(this)
        } catch (e: Exception) {
            // AdMob kullanılamıyor (emulator vb.)
            e.printStackTrace()
        }
    }
}
