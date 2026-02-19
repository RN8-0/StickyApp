package com.sticly

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var indicatorContainer: LinearLayout
    private lateinit var btnNext: Button
    private lateinit var btnSkip: Button
    private var billingManager: BillingManager? = null
    private var adapter: OnboardingAdapter? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        PreferencesHelper.setNotificationsEnabled(this, isGranted)
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.onAttach(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        viewPager = findViewById(R.id.viewPager)
        indicatorContainer = findViewById(R.id.indicatorContainer)
        btnNext = findViewById(R.id.btnNext)
        btnSkip = findViewById(R.id.btnSkip)

        setupBilling()
        setupViewPager()
        setupButtons()

        // Kullanıcı onboarding'de gezinirken arka planda verileri önceden yükle
        preloadDataInBackground()
    }

    private var isDataPreloaded = false

    /**
     * Onboarding sırasında arka planda Firebase verilerini ve görselleri önceden yükle
     */
    private fun preloadDataInBackground() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val packs = StickerRepository.loadPacks(this@OnboardingActivity, forceRefresh = false)

                if (packs.isNotEmpty()) {
                    val popularPacks = packs
                        .filter { it.isActive && it.category != "custom" }
                        .sortedByDescending { it.downloadCount }
                        .take(10)

                    StickyGlideModule.preloadPopularPacks(this@OnboardingActivity, popularPacks)
                    StickyGlideModule.preloadStickerPreviews(this@OnboardingActivity, packs, packCount = 30, stickersPerPack = 5)
                }
                isDataPreloaded = true
            } catch (e: Exception) {
                android.util.Log.e("OnboardingActivity", "Background preload error: ${e.message}")
                isDataPreloaded = true
            }
        }
    }

    private fun setupBilling() {
        billingManager = BillingManager(
            context = this,
            onPurchaseComplete = { isPremium ->
                if (isPremium) {
                    Toast.makeText(this, R.string.premium_purchased, Toast.LENGTH_SHORT).show()
                    onFinish()
                }
            },
            onBillingReady = {
                // Google Play fiyatları yüklendi - adapter'ı güncelle
                runOnUiThread {
                    adapter?.updatePrices()
                }
                // Otomatik olarak satın alımları geri yükle (arka planda sessizce)
                restorePurchasesSilently()
            }
        )
    }

    private fun restorePurchasesSilently() {
        billingManager?.restorePurchases { result ->
            if (result == BillingManager.RestoreResult.SUCCESS) {
                // Premium kullanıcı bulundu - doğrudan ana sayfaya yönlendir
                runOnUiThread {
                    if (PreferencesHelper.isPremium(this)) {
                        onFinish()
                    }
                }
            }
        }
    }

    private fun getPriceForPlan(planIndex: Int): String? {
        return when (planIndex) {
            0 -> billingManager?.getFormattedPrice(BillingManager.PREMIUM_MONTHLY)
            1 -> billingManager?.getFormattedPrice(BillingManager.PREMIUM_YEARLY)
            else -> null
        }
    }

    private fun setupViewPager() {
        adapter = OnboardingAdapter(
            onFinish = { onFinish() },
            onPurchase = { planIndex ->
                val sku = when(planIndex) {
                    0 -> BillingManager.PREMIUM_MONTHLY
                    else -> BillingManager.PREMIUM_YEARLY
                }
                billingManager?.launchPurchase(this, sku)
            },
            getPriceForPlan = { planIndex -> getPriceForPlan(planIndex) }
        )
        viewPager.adapter = adapter

        // Simple Parallax Transformer
        viewPager.setPageTransformer { page, position ->
            val absPos = Math.abs(position)
            page.apply {
                val visual = findViewById<View>(R.id.visualContainer)
                val title = findViewById<View>(R.id.tvTitle)
                val desc = findViewById<View>(R.id.tvDesc)

                if (visual != null) {
                    visual.translationX = position * (width / 2.5f)
                    visual.alpha = 1 - absPos
                }

                if (title != null) {
                    title.translationX = position * (width / 1.5f)
                    title.alpha = 1 - absPos
                }

                if (desc != null) {
                    desc.translationX = position * (width / 1.2f)
                    desc.alpha = 1 - absPos
                }
            }
        }

        // Setup Indicators
        val adapterItemCount = adapter?.itemCount ?: return
        val indicators = arrayOfNulls<ImageView>(adapterItemCount)
        val layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(12, 0, 12, 0)
        }

        indicatorContainer.removeAllViews()
        for (i in indicators.indices) {
            indicators[i] = ImageView(this)
            indicators[i]?.apply {
                setImageDrawable(ContextCompat.getDrawable(this@OnboardingActivity, R.drawable.carousel_indicator_inactive))
                this.layoutParams = layoutParams
            }
            indicatorContainer.addView(indicators[i])
        }

        val updateIndicators = { position: Int ->
            for (i in indicators.indices) {
                indicators[i]?.setImageDrawable(
                    ContextCompat.getDrawable(
                        this@OnboardingActivity,
                        if (i == position) R.drawable.carousel_indicator_active else R.drawable.carousel_indicator_inactive
                    )
                )
            }
        }

        updateIndicators(0)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateIndicators(position)

                if (position == 2) {
                    checkNotificationPermission()
                    // Last page
                    btnNext.setText(R.string.onboarding_start)
                } else {
                    btnNext.setText(R.string.onboarding_next)
                }

                btnNext.visibility = View.VISIBLE
                btnSkip.visibility = View.VISIBLE
                btnSkip.setTextColor(android.graphics.Color.WHITE)
                indicatorContainer.visibility = View.VISIBLE
            }
        })
    }

    private fun setupButtons() {
        btnNext.setOnClickListener {
            val itemCount = viewPager.adapter?.itemCount ?: 0
            if (viewPager.currentItem < itemCount - 1) {
                viewPager.currentItem += 1
            } else {
                onFinish()
            }
        }

        btnSkip.setOnClickListener {
            onFinish()
        }
    }

    private fun onFinish() {
        PreferencesHelper.setFirstLaunchComplete(this)
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        billingManager?.destroy()
    }
}
