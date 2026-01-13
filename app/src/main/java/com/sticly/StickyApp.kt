package com.sticly

import android.app.Application

class StickyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Reklam sistemini başlat
        AdManager.initialize(this)
    }
}
