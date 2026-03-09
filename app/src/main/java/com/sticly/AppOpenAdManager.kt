package com.sticly

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd

/**
 * App Open Ad Manager
 * - Uygulama arka plandan ön plana geldiğinde reklam gösterir (cold start dahil)
 * - Ayrıca 3. paket açılışında da tetiklenebilir (tryShowAd)
 * - Arka minimum süre: 3 saniye (kısa geçişlerde reklam göstermez)
 * - Günde maksimum 3 kez gösterir
 * - Premium kullanıcılara reklam gösterilmez
 */
class AppOpenAdManager(private val application: Application) :
    Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {

    companion object {
        private const val TAG = "AppOpenAd"
        private const val AD_UNIT_ID = "ca-app-pub-1522897791319993/7665306671"
        private const val AD_EXPIRY_MS = 4 * 60 * 60 * 1000L
        private const val PREFS_NAME = "app_open_ad_prefs"
        private const val KEY_LAST_SHOWN_DATE = "last_shown_date"
        private const val KEY_SHOWN_COUNT = "shown_count_today"
        private const val MAX_SHOWS_PER_DAY = 6
        private const val MIN_BACKGROUND_MS = 10000L // 10 saniye arka planda kaldıysa göster
    }

    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false
    private var isShowingAd = false
    private var loadTime = 0L
    private var currentActivity: Activity? = null
    private var backgroundTime = 0L
    private var shownCountToday = 0

    fun init() {
        application.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        // Bugün kaç kez gösterildiğini kontrol et
        val prefs = application.getSharedPreferences(PREFS_NAME, 0)
        val lastDate = prefs.getString(KEY_LAST_SHOWN_DATE, "") ?: ""
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        shownCountToday = if (lastDate == today) prefs.getInt(KEY_SHOWN_COUNT, 0) else 0

        loadAd()
    }

    /** Called from MainActivity when user opens 3rd pack */
    fun tryShowAd() {
        if (shownCountToday >= MAX_SHOWS_PER_DAY) return
        showAdIfAvailable()
    }

    // ProcessLifecycleOwner: Uygulama arka plana gitti
    override fun onStop(owner: LifecycleOwner) {
        backgroundTime = System.currentTimeMillis()
        Log.d(TAG, "App went to background")
    }

    // ProcessLifecycleOwner: Uygulama ön plana geldi
    override fun onStart(owner: LifecycleOwner) {
        val elapsed = System.currentTimeMillis() - backgroundTime
        Log.d(TAG, "App came to foreground, background time: ${elapsed}ms")

        // Minimum süre kontrolü (çok kısa geçişlerde gösterme)
        if (backgroundTime > 0 && elapsed >= MIN_BACKGROUND_MS) {
            if (shownCountToday < MAX_SHOWS_PER_DAY) {
                showAdIfAvailable()
            }
        }
    }

    private fun loadAd() {
        if (isLoadingAd || isAdAvailable()) return
        if (PreferencesHelper.isPremium(application)) return

        isLoadingAd = true
        val request = AdRequest.Builder().build()

        AppOpenAd.load(application, AD_UNIT_ID, request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    isLoadingAd = false
                    loadTime = System.currentTimeMillis()
                    Log.d(TAG, "App open ad loaded")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoadingAd = false
                    Log.d(TAG, "App open ad failed to load: ${error.message}")
                    // Retry after 60 seconds — avoids hammering the server on repeated failures
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        loadAd()
                    }, 60_000L)
                }
            })
    }

    private fun isAdAvailable(): Boolean {
        return appOpenAd != null && !isAdExpired()
    }

    private fun isAdExpired(): Boolean {
        return System.currentTimeMillis() - loadTime > AD_EXPIRY_MS
    }

    private fun showAdIfAvailable() {
        if (isShowingAd) return
        if (shownCountToday >= MAX_SHOWS_PER_DAY) return
        if (PreferencesHelper.isPremium(application)) return

        val activity = currentActivity ?: return

        if (activity is OnboardingActivity || activity is LoginActivity) return

        if (!isAdAvailable()) {
            loadAd()
            return
        }

        val ad = appOpenAd ?: return

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                isShowingAd = false
                loadAd() // Hemen sonrakini yükle
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                appOpenAd = null
                isShowingAd = false
                loadAd()
            }

            override fun onAdShowedFullScreenContent() {
                isShowingAd = true
                shownCountToday++
                val prefs = application.getSharedPreferences(PREFS_NAME, 0)
                val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                prefs.edit()
                    .putString(KEY_LAST_SHOWN_DATE, today)
                    .putInt(KEY_SHOWN_COUNT, shownCountToday)
                    .apply()
                Log.d(TAG, "App open ad shown ($shownCountToday/$MAX_SHOWS_PER_DAY today)")
            }
        }

        isShowingAd = true
        ad.show(activity)
    }

    // Activity Lifecycle Callbacks — mevcut activity'yi takip et
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) { currentActivity = activity }
    override fun onActivityResumed(activity: Activity) { currentActivity = activity }
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity == activity) currentActivity = null
    }
}
