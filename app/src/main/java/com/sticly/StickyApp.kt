package com.sticly

import android.app.Application

class StickyApp : Application() {
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
