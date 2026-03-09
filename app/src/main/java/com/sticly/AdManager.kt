package com.sticly

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import androidx.core.content.ContextCompat

object AdManager {

    private const val TAG = "AdManager"

    // REKLAM KIMLIKLERI
    private const val REWARDED_AD_ID = "ca-app-pub-1522897791319993/7893209835"
    private const val MAKER_NATIVE_AD_ID = "" // Removed: ad unit does not exist in AdMob console
    const val FEED_AD_ID = "ca-app-pub-1522897791319993/6812028035"
    private const val INTERSTITIAL_AD_ID = "ca-app-pub-1522897791319993/8532303812"

    // Rewarded Video
    private var rewardedAd: RewardedAd? = null
    private var isRewardedLoading = false

    private var isInitialized = false
    
    // Preloaded Ad for Maker
    private var preloadedMakerAd: NativeAd? = null
    private var isPreloadingMaker = false

    // Interstitial
    private var interstitialAd: com.google.android.gms.ads.interstitial.InterstitialAd? = null
    private var isInterstitialLoading = false
    private var downloadCount = 0
    private const val INTERSTITIAL_PREFS_NAME = "admob_interstitial_prefs"
    private const val KEY_DOWNLOAD_COUNT = "download_count"

    // Track if any fullscreen ad was shown this session (for promo dialog)
    var adShownThisSession = false
        private set

    fun initialize(context: Context) {
        if (isInitialized) return
        Log.d(TAG, "Initializing AdMob SDK...")

        // Register test device for debug builds
        val isDebug = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebug) {
            val testDeviceIds = listOf(
                "0E598C3296B3C1F5D232CD49C5503D13",
                "032EFB18755DD82041691B320FD89A65"
            )
            val configuration = com.google.android.gms.ads.RequestConfiguration.Builder()
                .setTestDeviceIds(testDeviceIds)
                .build()
            MobileAds.setRequestConfiguration(configuration)
            Log.d(TAG, "Test device IDs registered for debug build")
        }

        MobileAds.initialize(context) { initStatus ->
            isInitialized = true
            Log.d(TAG, "AdMob SDK initialized. Status: ${initStatus.adapterStatusMap}")

            // Unity Ads mediation
            try {
                com.unity3d.ads.UnityAds.initialize(context, "6048973", false)
                Log.d(TAG, "Unity Ads initialized for mediation (Game ID: 6048973)")
            } catch (e: Exception) {
                Log.e(TAG, "Unity Ads initialization failed: ${e.message}")
            }

            // Reklamları yükle
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                loadRewardedAd(context)
                preloadMakerNativeAd(context)
                loadInterstitialAd(context)
            }
        }
    }

    // ========== REWARDED VIDEO ==========

    fun loadRewardedAd(context: Context) {
        if (isRewardedLoading || rewardedAd != null) return
        if (PreferencesHelper.isPremium(context)) return

        isRewardedLoading = true
        Log.d(TAG, "Loading rewarded ad...")

        RewardedAd.load(context, REWARDED_AD_ID, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d(TAG, "Rewarded ad loaded successfully")
                    rewardedAd = ad
                    isRewardedLoading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "Rewarded ad failed to load: ${error.message}")
                    rewardedAd = null
                    isRewardedLoading = false

                    // 5 saniye sonra tekrar dene
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        loadRewardedAd(context)
                    }, 5000)
                }
            })
    }

    fun isRewardedReady(): Boolean = rewardedAd != null

    /**
     * Rewarded video göster.
     * @param onRewarded Kullanıcı ödülü kazanınca çağrılır
     * @param onFailed Reklam gösterilemezse çağrılır
     */
    fun showRewardedAd(activity: Activity, onRewarded: () -> Unit, onFailed: () -> Unit = {}) {
        if (PreferencesHelper.isPremium(activity)) {
            onRewarded()
            return
        }

        val ad = rewardedAd
        if (ad == null) {
            Log.e(TAG, "Rewarded ad not ready")
            onFailed()
            loadRewardedAd(activity)
            return
        }

        var rewardEarned = false

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Rewarded ad dismissed")
                rewardedAd = null
                loadRewardedAd(activity) 
                
                if (rewardEarned) {
                    onRewarded()
                }
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.e(TAG, "Rewarded ad failed to show: ${error.message}")
                rewardedAd = null
                loadRewardedAd(activity)
                onFailed()
            }
        }

        ad.show(activity) { _ ->
            Log.d(TAG, "User earned reward")
            rewardEarned = true
            adShownThisSession = true
        }
    }

    // ========== MAKER NATIVE AD ==========

    fun preloadMakerNativeAd(context: Context) {
        // Disabled: MAKER_NATIVE_AD_ID does not exist in AdMob console
        return
    }

    fun getPreloadedMakerAd(): NativeAd? {
        val ad = preloadedMakerAd
        preloadedMakerAd = null
        return ad
    }

    // ========== NATIVE AD (for Maker screen only) ==========

    enum class NativeAdType {
        MAKER
    }

    fun loadNativeAd(context: Context, type: NativeAdType = NativeAdType.MAKER, onLoaded: (NativeAd) -> Unit) {
        // Disabled: MAKER_NATIVE_AD_ID does not exist in AdMob console
        return
    }

    fun populateNativeAdView(nativeAd: NativeAd, adView: NativeAdView) {
        adView.headlineView = adView.findViewById(R.id.ad_headline)
        adView.bodyView = adView.findViewById(R.id.ad_body)
        adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)
        adView.iconView = adView.findViewById(R.id.ad_app_icon)
        adView.mediaView = adView.findViewById(R.id.ad_media)

        (adView.headlineView as? TextView)?.apply {
            text = nativeAd.headline
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
        }
        
        nativeAd.mediaContent?.let { adView.mediaView?.setMediaContent(it) }

        if (nativeAd.body == null) {
            adView.bodyView?.visibility = View.INVISIBLE
        } else {
            adView.bodyView?.visibility = View.VISIBLE
            (adView.bodyView as? TextView)?.apply {
                text = nativeAd.body
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            }
        }

        if (nativeAd.callToAction == null) {
            adView.callToActionView?.visibility = View.INVISIBLE
        } else {
            adView.callToActionView?.visibility = View.VISIBLE
            (adView.callToActionView as? Button)?.text = nativeAd.callToAction
        }

        if (nativeAd.icon == null) {
            adView.iconView?.visibility = View.GONE
        } else {
            (adView.iconView as? ImageView)?.setImageDrawable(nativeAd.icon?.drawable)
            adView.iconView?.visibility = View.VISIBLE
        }

        adView.setNativeAd(nativeAd)
    }

    // ========== INTERSTITIAL AD ==========

    fun loadInterstitialAd(context: Context) {
        if (isInterstitialLoading || interstitialAd != null) return
        if (PreferencesHelper.isPremium(context)) return

        isInterstitialLoading = true
        com.google.android.gms.ads.interstitial.InterstitialAd.load(
            context, INTERSTITIAL_AD_ID, AdRequest.Builder().build(),
            object : com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: com.google.android.gms.ads.interstitial.InterstitialAd) {
                    interstitialAd = ad
                    isInterstitialLoading = false
                    Log.d(TAG, "Interstitial ad loaded")
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    isInterstitialLoading = false
                    Log.e(TAG, "Interstitial ad failed: ${error.message}")
                }
            })
    }

    /**
     * Her 2 pakette bir interstitial göster (2., 4., 6. indirme).
     * @param onComplete Reklam bittikten veya gösterilemezse çağrılır
     */
    fun showInterstitialIfNeeded(activity: Activity, onComplete: () -> Unit) {
        if (PreferencesHelper.isPremium(activity)) {
            Log.d(TAG, "Interstitial skip: premium user")
            onComplete()
            return
        }

        // Persist download count across app restarts so every 2nd download triggers an ad
        val prefs = activity.getSharedPreferences(INTERSTITIAL_PREFS_NAME, 0)
        downloadCount = prefs.getInt(KEY_DOWNLOAD_COUNT, 0) + 1
        prefs.edit().putInt(KEY_DOWNLOAD_COUNT, downloadCount).apply()

        Log.d(TAG, "Interstitial check: downloadCount=$downloadCount")
        if (downloadCount % 2 != 0) {
            Log.d(TAG, "Interstitial skip: not every 2nd (count=$downloadCount)")
            onComplete()
            return
        }

        val ad = interstitialAd
        if (ad == null) {
            Log.e(TAG, "Interstitial null! Loading new one...")
            loadInterstitialAd(activity)
            onComplete()
            return
        }

        Log.d(TAG, "Showing interstitial ad now!")
        adShownThisSession = true
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Interstitial dismissed")
                interstitialAd = null
                loadInterstitialAd(activity)
                onComplete()
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.e(TAG, "Interstitial show failed: ${error.message}")
                interstitialAd = null
                loadInterstitialAd(activity)
                onComplete()
            }
        }
        ad.show(activity)
    }
}
