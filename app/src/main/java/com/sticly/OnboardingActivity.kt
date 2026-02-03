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
import androidx.viewpager2.widget.ViewPager2
import android.widget.Toast

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
            2 -> billingManager?.getFormattedPrice(BillingManager.PREMIUM_LIFETIME)
            else -> null
        }
    }

    private fun setupViewPager() {
        adapter = OnboardingAdapter(
            onFinish = { onFinish() },
            onPurchase = { planIndex ->
                val sku = when(planIndex) {
                    0 -> BillingManager.PREMIUM_MONTHLY
                    1 -> BillingManager.PREMIUM_YEARLY
                    else -> BillingManager.PREMIUM_LIFETIME
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
                }

                val lastPosition = (adapter?.itemCount ?: 1) - 1
                if (position == lastPosition) {
                    // Premium page: hide Next, keep Skip visible, hide Indicators
                    btnNext.visibility = View.GONE
                    btnSkip.visibility = View.VISIBLE
                    btnSkip.setTextColor(ContextCompat.getColor(this@OnboardingActivity, R.color.accent))
                    indicatorContainer.visibility = View.GONE
                } else {
                    btnNext.visibility = View.VISIBLE
                    btnSkip.visibility = View.VISIBLE
                    btnSkip.setTextColor(android.graphics.Color.WHITE)
                    indicatorContainer.visibility = View.VISIBLE
                    btnNext.setText(R.string.onboarding_next)
                }
            }
        })
    }

    private fun setupButtons() {
        btnNext.setOnClickListener {
            if (viewPager.currentItem < (viewPager.adapter?.itemCount ?: 0) - 1) {
                viewPager.currentItem += 1
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
