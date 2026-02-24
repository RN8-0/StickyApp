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
 * Kullanıcı uygulamaya her döndüğünde (arka plandan ön plana) reklam gösterir.
 * Premium kullanıcılara reklam gösterilmez.
 */
class AppOpenAdManager(private val application: Application) :
    Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {

    companion object {
        private const val TAG = "AppOpenAdManager"
        private const val AD_UNIT_ID = "ca-app-pub-1522897791319993/7665306671"
        // Reklam 4 saatten eski ise yeniden yükle
        private const val AD_EXPIRY_MS = 4 * 60 * 60 * 1000L
        // Uygulama açıldıktan sonra minimum bekleme süresi (ilk açılışta hemen gösterme)
        private const val COLD_START_DELAY_MS = 8000L
    }

    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false
    private var isShowingAd = false
    private var loadTime = 0L
    private var currentActivity: Activity? = null
    private var appStartTime = System.currentTimeMillis()

    fun init() {
        application.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        loadAd()
    }

    /** Uygulama ön plana geldiğinde çağrılır */
    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
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
                    Log.d(TAG, "App Open Ad loaded successfully")
                    appOpenAd = ad
                    isLoadingAd = false
                    loadTime = System.currentTimeMillis()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "App Open Ad failed to load: ${error.message}")
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
        if (PreferencesHelper.isPremium(application)) return

        // İlk açılışta hemen reklam gösterme — kullanıcı deneyimini bozar
        if (System.currentTimeMillis() - appStartTime < COLD_START_DELAY_MS) {
            Log.d(TAG, "Skipping ad — cold start grace period")
            return
        }

        val activity = currentActivity ?: return

        // Onboarding veya Login ekranlarında gösterme
        if (activity is OnboardingActivity || activity is LoginActivity) {
            Log.d(TAG, "Skipping ad — onboarding/login screen")
            return
        }

        if (!isAdAvailable()) {
            Log.d(TAG, "Ad not available, loading new one")
            loadAd()
            return
        }

        val ad = appOpenAd ?: return

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "App Open Ad dismissed")
                appOpenAd = null
                isShowingAd = false
                loadAd() // Bir sonraki gösterim için yükle
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.e(TAG, "App Open Ad failed to show: ${error.message}")
                appOpenAd = null
                isShowingAd = false
                loadAd()
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "App Open Ad shown")
                isShowingAd = true
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
