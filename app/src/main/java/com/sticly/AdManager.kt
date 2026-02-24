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
    private const val MAKER_NATIVE_AD_ID = "ca-app-pub-1522897791319993/2892861725"

    // Rewarded Video
    private var rewardedAd: RewardedAd? = null
    private var isRewardedLoading = false

    private var isInitialized = false
    
    // Preloaded Ad for Maker
    private var preloadedMakerAd: NativeAd? = null
    private var isPreloadingMaker = false

    fun initialize(context: Context) {
        if (isInitialized) return
        Log.d(TAG, "Initializing AdMob SDK...")

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
        }
    }

    // ========== MAKER NATIVE AD ==========

    fun preloadMakerNativeAd(context: Context) {
        if (PreferencesHelper.isPremium(context) || preloadedMakerAd != null || isPreloadingMaker) return
        
        isPreloadingMaker = true
        val adLoader = AdLoader.Builder(context, MAKER_NATIVE_AD_ID)
            .forNativeAd { ad ->
                preloadedMakerAd = ad
                isPreloadingMaker = false
                Log.d(TAG, "Maker Native Ad preloaded")
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(e: LoadAdError) {
                    isPreloadingMaker = false
                    Log.e(TAG, "Preload Maker Ad Failed: ${e.message}")
                }
            })
            .build()
        adLoader.loadAd(AdRequest.Builder().build())
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
        if (PreferencesHelper.isPremium(context)) return

        if (!isInitialized) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                loadNativeAd(context, type, onLoaded)
            }, 1000)
            return
        }

        // Maker ekranıysa önceden yüklenmiş reklamı kullan
        val preloaded = getPreloadedMakerAd()
        if (preloaded != null) {
            onLoaded(preloaded)
            return
        }

        val adLoader = AdLoader.Builder(context, MAKER_NATIVE_AD_ID)
            .forNativeAd { nativeAd ->
                onLoaded(nativeAd)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(e: LoadAdError) {
                    Log.e(TAG, "Native Ad Error: ${e.message}")
                }
            })
            .withNativeAdOptions(NativeAdOptions.Builder()
                .setMediaAspectRatio(NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_SQUARE)
                .setVideoOptions(VideoOptions.Builder().setStartMuted(true).build())
                .build())
            .build()
        adLoader.loadAd(AdRequest.Builder().build())
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
}
