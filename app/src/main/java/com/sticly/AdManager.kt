package com.sticly

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * Reklam yönetim sınıfı
 *
 * Play Store'a yüklemeden önce:
 * 1. AdMob hesabı oluştur
 * 2. Uygulama ekle ve onay al
 * 3. TEST_INTERSTITIAL_ID yerine gerçek ID'yi koy
 * 4. build.gradle'da google-services.json ekle
 */
object AdManager {

    private const val TAG = "AdManager"

    // Test ID - Play Store'a yüklemeden önce gerçek ID ile değiştir!
    // Gerçek ID formatı: ca-app-pub-XXXXXXXXXXXXXXXX/YYYYYYYYYY
    private const val TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"

    private var interstitialAd: InterstitialAd? = null
    private var isInitialized = false
    private var isLoading = false

    /**
     * AdMob'u başlat - Application sınıfında çağır
     */
    fun initialize(context: Context) {
        if (isInitialized) return

        MobileAds.initialize(context) { initializationStatus ->
            isInitialized = true
            Log.d(TAG, "AdMob initialized: ${initializationStatus.adapterStatusMap}")
            // İlk reklamı yükle
            loadInterstitial(context)
        }
    }

    /**
     * Interstitial (geçiş) reklamı yükle
     */
    fun loadInterstitial(context: Context) {
        if (isLoading || interstitialAd != null) return
        if (PreferencesHelper.isPremium(context)) return // Premium kullanıcıya reklam yok

        isLoading = true
        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(
            context,
            TEST_INTERSTITIAL_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(TAG, "Interstitial ad loaded")
                    interstitialAd = ad
                    isLoading = false

                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            Log.d(TAG, "Ad dismissed")
                            interstitialAd = null
                            // Yeni reklam yükle
                            loadInterstitial(context)
                        }

                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            Log.e(TAG, "Ad failed to show: ${error.message}")
                            interstitialAd = null
                        }

                        override fun onAdShowedFullScreenContent() {
                            Log.d(TAG, "Ad showed")
                        }
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "Ad failed to load: ${error.message}")
                    interstitialAd = null
                    isLoading = false
                }
            }
        )
    }

    /**
     * Interstitial reklamı göster
     * Her sticker paketi eklendiğinde çağrılır (premium değilse)
     */
    fun showInterstitial(activity: Activity) {
        if (PreferencesHelper.isPremium(activity)) return

        interstitialAd?.let { ad ->
            ad.show(activity)
        } ?: run {
            Log.d(TAG, "Interstitial ad not ready, loading...")
            loadInterstitial(activity)
        }
    }

    /**
     * Reklam hazır mı kontrol et
     */
    fun isInterstitialReady(): Boolean = interstitialAd != null
}
