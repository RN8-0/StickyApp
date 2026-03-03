package com.sticly

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd

/**
 * App Open Ad Manager
 * Günde bir kez, 3. paket açılışında reklam gösterir.
 * Premium kullanıcılara reklam gösterilmez.
 */
class AppOpenAdManager(private val application: Application) :
    Application.ActivityLifecycleCallbacks {

    companion object {
        private const val AD_UNIT_ID = "ca-app-pub-1522897791319993/7665306671"
        private const val AD_EXPIRY_MS = 4 * 60 * 60 * 1000L
        private const val PREFS_NAME = "app_open_ad_prefs"
        private const val KEY_LAST_SHOWN_DATE = "last_shown_date"
    }

    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false
    private var isShowingAd = false
    private var loadTime = 0L
    private var currentActivity: Activity? = null
    private var shownToday = false

    fun init() {
        application.registerActivityLifecycleCallbacks(this)
        // Check if already shown today
        val prefs = application.getSharedPreferences(PREFS_NAME, 0)
        val lastDate = prefs.getString(KEY_LAST_SHOWN_DATE, "") ?: ""
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        shownToday = lastDate == today
        loadAd()
    }

    /** Called from MainActivity when user opens 3rd pack */
    fun tryShowAd() {
        if (shownToday) return
        showAdIfAvailable()
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
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoadingAd = false
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
        if (shownToday) return
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
                loadAd()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                appOpenAd = null
                isShowingAd = false
                loadAd()
            }

            override fun onAdShowedFullScreenContent() {
                isShowingAd = true
                shownToday = true
                val prefs = application.getSharedPreferences(PREFS_NAME, 0)
                val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                prefs.edit().putString(KEY_LAST_SHOWN_DATE, today).apply()
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
