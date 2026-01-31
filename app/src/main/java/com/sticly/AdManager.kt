package com.sticly

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import androidx.core.content.ContextCompat

object AdManager {

    private const val TAG = "AdManager"

    // REKLAM KIMLIKLERI
    private const val INTERSTITIAL_ID = "ca-app-pub-1522897791319993/6303114381"
    private const val BANNER_ID = "ca-app-pub-1522897791319993/1423689771"
    private const val LIST_NATIVE_AD_ID = "ca-app-pub-1522897791319993/9417073326"
    private const val MAKER_NATIVE_AD_ID = "ca-app-pub-1522897791319993/2892861725"
    private const val FAV_NATIVE_AD_ID = "ca-app-pub-1522897791319993/7323061326"
    private const val MY_STICKERS_NATIVE_AD_ID = "ca-app-pub-1522897791319993/3649030882"

    private var interstitialAd: InterstitialAd? = null
    private var isInitialized = false
    private var isLoading = false
    
    // Preloaded Ad for Maker
    private var preloadedMakerAd: NativeAd? = null
    private var isPreloadingMaker = false

    fun initialize(context: Context) {
        if (isInitialized) return
        Log.d(TAG, "Initializing AdMob SDK...")
        MobileAds.initialize(context) { initStatus ->
            isInitialized = true
            Log.d(TAG, "AdMob SDK initialized. Status: ${initStatus.adapterStatusMap}")
            Log.d(TAG, "User isPremium: ${PreferencesHelper.isPremium(context)}")
            loadInterstitial(context)
            // Initial preload
            preloadMakerNativeAd(context)
        }
    }

    /**
     * Sticker Maker ekranı için reklamı önceden yükle
     */
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

    /**
     * Önceden yüklenmiş reklamı al ve hafızayı temizle
     */
    fun getPreloadedMakerAd(): NativeAd? {
        val ad = preloadedMakerAd
        preloadedMakerAd = null // Bir kez kullanıldıktan sonra temizle
        return ad
    }

    fun loadInterstitial(context: Context) {
        Log.d(TAG, "loadInterstitial called. isLoading: $isLoading, hasAd: ${interstitialAd != null}, isPremium: ${PreferencesHelper.isPremium(context)}")
        if (isLoading || interstitialAd != null) return
        if (PreferencesHelper.isPremium(context)) {
            Log.d(TAG, "User is premium, skipping interstitial load")
            return
        }

        isLoading = true
        Log.d(TAG, "Loading interstitial ad...")
        InterstitialAd.load(context, INTERSTITIAL_ID, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(TAG, "Interstitial ad loaded successfully")
                    interstitialAd = ad
                    isLoading = false
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            interstitialAd = null
                            loadInterstitial(context)
                        }
                    }
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "Interstitial ad failed to load: ${error.message} Code: ${error.code}")
                    interstitialAd = null
                    isLoading = false
                }
            })
    }

    fun showInterstitial(activity: Activity) {
        if (PreferencesHelper.isPremium(activity)) return
        interstitialAd?.show(activity) ?: loadInterstitial(activity)
    }

    fun loadBanner(activity: Activity, adContainer: ViewGroup) {
        if (PreferencesHelper.isPremium(activity)) {
            adContainer.visibility = View.GONE
            return
        }
        val adView = AdView(activity)
        adView.adUnitId = BANNER_ID
        adView.setAdSize(AdSize.BANNER)
        adContainer.removeAllViews()
        adContainer.addView(adView)
        adContainer.visibility = View.VISIBLE
        adView.loadAd(AdRequest.Builder().build())
    }

    enum class NativeAdType {
        LIST, MAKER, FAVORITE, MY_STICKERS
    }

    fun loadNativeAd(context: Context, type: NativeAdType = NativeAdType.LIST, onLoaded: (NativeAd) -> Unit) {
        Log.d(TAG, "loadNativeAd called. Type: $type, isPremium: ${PreferencesHelper.isPremium(context)}, isInitialized: $isInitialized")
        if (PreferencesHelper.isPremium(context)) {
            Log.d(TAG, "User is premium, skipping ad load")
            return
        }
        
        // Eğer Maker ekranıysa ve önceden yüklenmiş reklam varsa onu kullan
        if (type == NativeAdType.MAKER) {
            val preloaded = getPreloadedMakerAd()
            if (preloaded != null) {
                onLoaded(preloaded)
                return
            }
        }

        val adUnitId = when (type) {
            NativeAdType.MAKER -> MAKER_NATIVE_AD_ID
            NativeAdType.FAVORITE -> FAV_NATIVE_AD_ID
            NativeAdType.MY_STICKERS -> MY_STICKERS_NATIVE_AD_ID
            else -> LIST_NATIVE_AD_ID
        }
        
        Log.d(TAG, "Loading Native Ad with ID: $adUnitId")
        val adLoader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { nativeAd ->
                Log.d(TAG, "Native Ad loaded successfully for type: $type")
                onLoaded(nativeAd)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(e: LoadAdError) {
                    Log.e(TAG, "Native Ad Error for $type: ${e.message} Code: ${e.code} Domain: ${e.domain}")
                }
                override fun onAdLoaded() {
                    Log.d(TAG, "Ad loaded event fired for type: $type")
                }
            })
            .withNativeAdOptions(NativeAdOptions.Builder()
                .setMediaAspectRatio(NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_SQUARE)
                .setVideoOptions(VideoOptions.Builder().setStartMuted(true).build())
                .build())
            .build()
        adLoader.loadAd(AdRequest.Builder().build())
        Log.d(TAG, "Ad request sent for type: $type")
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
