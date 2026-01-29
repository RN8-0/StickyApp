package com.sticly

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PremiumActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    private var billingManager: BillingManager? = null
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var billingConfig: BillingConfig? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_premium)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""

        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Initialize Billing Manager
        billingManager = BillingManager(
            context = this,
            onPurchaseComplete = { isPurchased ->
                if (isPurchased) {
                    Toast.makeText(this, R.string.premium_purchased, Toast.LENGTH_SHORT).show()
                    updateUI()
                }
            },
            onBillingReady = {
                // Google Play products loaded — refresh prices on UI
                updatePricesFromGooglePlay()
            }
        )

        setupComparisonRows()
        setupPlanCards()
        loadBillingConfig()

        findViewById<View>(R.id.btnPrivacyPolicy).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://sticky-privacy-legal.web.app/#privacy"))
            startActivity(intent)
        }

        updateUI()
        setupEdgeToEdge()
    }

    private fun setupComparisonRows() {
        // Ads Row
        val rowAds = findViewById<View>(R.id.rowAds)
        rowAds.findViewById<TextView>(R.id.tvFeatureName).setText(R.string.premium_ads)
        rowAds.findViewById<TextView>(R.id.tvFreeValue).setText(R.string.var_label)
        rowAds.findViewById<TextView>(R.id.tvPremiumValue).setText(R.string.yok_label)
        rowAds.findViewById<ImageView>(R.id.imgPremiumCheck).visibility = View.GONE
        rowAds.findViewById<TextView>(R.id.tvPremiumValue).visibility = View.VISIBLE

        // Packs Row
        val rowPacks = findViewById<View>(R.id.rowPacks)
        rowPacks.findViewById<TextView>(R.id.tvFeatureName).setText(R.string.premium_sticker_packs)
        rowPacks.findViewById<TextView>(R.id.tvFreeValue).setText(R.string.kilitli_label)
        rowPacks.findViewById<TextView>(R.id.tvPremiumValue).setText(R.string.acik_label)

        // AI Row
        val rowAI = findViewById<View>(R.id.rowAI)
        rowAI.findViewById<TextView>(R.id.tvFeatureName).setText(R.string.premium_ai_removal)
        rowAI.findViewById<TextView>(R.id.tvFreeValue).setText(R.string.acik_label)
        rowAI.findViewById<TextView>(R.id.tvPremiumValue).setText(R.string.acik_label)

        // Create Row
        val rowCreate = findViewById<View>(R.id.rowCreate)
        rowCreate.findViewById<TextView>(R.id.tvFeatureName).setText(R.string.priority_support)
        rowCreate.findViewById<TextView>(R.id.tvFreeValue).setText(R.string.acik_label)
        rowCreate.findViewById<TextView>(R.id.tvPremiumValue).setText(R.string.acik_label)
    }

    private fun setupPlanCards() {
        val cardMonthly = findViewById<View>(R.id.cardMonthly)
        val cardYearly = findViewById<View>(R.id.cardYearly)
        val cardLifetime = findViewById<View>(R.id.cardLifetime)

        // Aylık Plan
        setupPlanCard(
            card = cardMonthly,
            icon = "📅",
            name = getString(R.string.plan_monthly).replace("📅 ", ""),
            subtitle = getString(R.string.plan_monthly_subtitle),
            badge = "🎁 " + getString(R.string.plan_monthly_trial),
            priceNote = getString(R.string.plan_monthly_note),
            showBadge = true,
            onClick = { billingManager?.launchPurchase(this, BillingManager.PREMIUM_MONTHLY) }
        )

        // Yıllık Plan
        setupPlanCard(
            card = cardYearly,
            icon = "⭐",
            name = getString(R.string.plan_yearly).replace("⭐ ", ""),
            subtitle = getString(R.string.plan_yearly_subtitle),
            badge = "🏆 " + getString(R.string.save_percentage),
            priceNote = getString(R.string.plan_yearly_note),
            showBadge = true,
            onClick = { billingManager?.launchPurchase(this, BillingManager.PREMIUM_YEARLY) }
        )

        // Ömür Boyu Plan
        setupPlanCard(
            card = cardLifetime,
            icon = "👑",
            name = getString(R.string.plan_lifetime).replace("👑 ", ""),
            subtitle = getString(R.string.plan_lifetime_subtitle),
            badge = "💎 " + getString(R.string.one_time_billing),
            priceNote = getString(R.string.plan_lifetime_note),
            showBadge = true,
            onClick = { billingManager?.launchPurchase(this, BillingManager.PREMIUM_LIFETIME) }
        )
    }

    private fun setupPlanCard(
        card: View,
        icon: String,
        name: String,
        subtitle: String,
        badge: String,
        priceNote: String,
        showBadge: Boolean,
        onClick: () -> Unit
    ) {
        card.findViewById<TextView>(R.id.tvPlanIcon).text = icon
        card.findViewById<TextView>(R.id.tvPlanName).text = name
        card.findViewById<TextView>(R.id.tvPlanPrice).text = getString(R.string.price_loading)

        // Alt açıklama
        card.findViewById<TextView>(R.id.tvPlanSubtitle)?.text = subtitle

        // Fiyat altı notu
        card.findViewById<TextView>(R.id.tvPriceNote)?.apply {
            text = priceNote
            visibility = if (priceNote.isNotEmpty()) View.VISIBLE else View.GONE
        }

        val badgeView = card.findViewById<TextView>(R.id.tvBadge)
        if (showBadge && badge.isNotEmpty()) {
            badgeView.text = badge
            badgeView.visibility = View.VISIBLE
        } else {
            badgeView.visibility = View.GONE
        }

        card.setOnClickListener { onClick() }
    }

    private fun updatePricesFromGooglePlay() {
        val monthlyPrice = billingManager?.getFormattedPrice(BillingManager.PREMIUM_MONTHLY)
        val yearlyPrice = billingManager?.getFormattedPrice(BillingManager.PREMIUM_YEARLY)
        val lifetimePrice = billingManager?.getFormattedPrice(BillingManager.PREMIUM_LIFETIME)

        val loadingText = getString(R.string.price_loading)

        // Aylık fiyat - /ay formatında
        findViewById<View>(R.id.cardMonthly)?.findViewById<TextView>(R.id.tvPlanPrice)?.text =
            if (monthlyPrice != null) "$monthlyPrice/${getString(R.string.period_month)}" else loadingText

        // Yıllık fiyat - /yıl formatında
        val cardYearly = findViewById<View>(R.id.cardYearly)
        cardYearly?.findViewById<TextView>(R.id.tvPlanPrice)?.text =
            if (yearlyPrice != null) "$yearlyPrice/${getString(R.string.period_year)}" else loadingText
        // Üstü çizili eski fiyat gösterimi kaldırıldı
        cardYearly?.findViewById<TextView>(R.id.tvOldPrice)?.visibility = View.GONE

        // Ömür boyu fiyat
        val cardLifetime = findViewById<View>(R.id.cardLifetime)
        cardLifetime?.findViewById<TextView>(R.id.tvPlanPrice)?.text = lifetimePrice ?: loadingText
        // Üstü çizili eski fiyat gösterimi kaldırıldı
        cardLifetime?.findViewById<TextView>(R.id.tvOldPrice)?.visibility = View.GONE
    }

    private fun setupEdgeToEdge() {
        val toolbar = findViewById<View>(R.id.toolbar)
        val root = findViewById<View>(R.id.premium_root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            toolbar?.setPadding(toolbar.paddingLeft, systemBars.top, toolbar.paddingRight, toolbar.paddingBottom)
            insets
        }
    }

    private fun updateUI() {
        val isPremium = PreferencesHelper.isPremium(this)
        val premiumType = PreferencesHelper.getPremiumType(this)

        val plansContainer = findViewById<View>(R.id.plansContainer)
        val premiumActiveContainer = findViewById<View>(R.id.premiumActiveContainer)
        val cardMonthly = findViewById<View>(R.id.cardMonthly)
        val cardYearly = findViewById<View>(R.id.cardYearly)
        val cardLifetime = findViewById<View>(R.id.cardLifetime)
        val tvHeaderTitle = findViewById<TextView>(R.id.tvHeaderTitle)
        val tvHeaderDesc = findViewById<TextView>(R.id.tvHeaderDesc)

        if (isPremium) {
            // Premium kullanıcı için başlığı değiştir
            tvHeaderTitle?.text = getString(R.string.premium_subscriber_title)
            tvHeaderDesc?.text = getString(R.string.premium_subscriber_desc)

            when (premiumType) {
                "lifetime" -> {
                    // Ömür boyu premium - tüm planları gizle, premium aktif ekranı göster
                    plansContainer.visibility = View.GONE
                    premiumActiveContainer.visibility = View.VISIBLE

                    // Premium tipi göster
                    findViewById<TextView>(R.id.tvPremiumType)?.text = getString(R.string.premium_lifetime_member)

                    // Taç animasyonu
                    animateCrown()
                }
                "subscription" -> {
                    // Abonelik aktif - hangi abonelik olduğunu kontrol et
                    val expiry = PreferencesHelper.getPremiumExpiry(this)
                    val now = System.currentTimeMillis()
                    val remainingDays = ((expiry - now) / (24 * 60 * 60 * 1000)).toInt()

                    // Yıllık mı aylık mı anlamak için kalan günlere bak
                    val isYearly = remainingDays > 60 // 60 günden fazla kaldıysa yıllık

                    if (isYearly) {
                        // Yıllık abone - aylık ve yıllık gizle, sadece ömür boyu göster
                        plansContainer.visibility = View.VISIBLE
                        premiumActiveContainer.visibility = View.GONE
                        cardMonthly.visibility = View.GONE
                        cardYearly.visibility = View.GONE
                        cardLifetime.visibility = View.VISIBLE

                        // Başlık güncelle
                        findViewById<TextView>(R.id.tvSelectPlan)?.text = getString(R.string.upgrade_to_lifetime)
                    } else {
                        // Aylık abone - aylık gizle, yıllık ve ömür boyu göster
                        plansContainer.visibility = View.VISIBLE
                        premiumActiveContainer.visibility = View.GONE
                        cardMonthly.visibility = View.GONE
                        cardYearly.visibility = View.VISIBLE
                        cardLifetime.visibility = View.VISIBLE

                        // Başlık güncelle
                        findViewById<TextView>(R.id.tvSelectPlan)?.text = getString(R.string.upgrade_to_yearly)
                    }
                }
                else -> {
                    // Premium ama tip belirsiz - güvenli tarafta kal
                    plansContainer.visibility = View.GONE
                    premiumActiveContainer.visibility = View.VISIBLE
                    findViewById<TextView>(R.id.tvPremiumType)?.text = getString(R.string.premium_active)
                    animateCrown()
                }
            }
        } else {
            // Premium değil - normal başlık
            tvHeaderTitle?.text = getString(R.string.premium_remove_limits)
            tvHeaderDesc?.text = getString(R.string.premium_remove_limits_desc)

            // Tüm planları göster
            plansContainer.visibility = View.VISIBLE
            premiumActiveContainer.visibility = View.GONE
            cardMonthly.visibility = View.VISIBLE
            cardYearly.visibility = View.VISIBLE
            cardLifetime.visibility = View.VISIBLE

            // Başlık normal
            findViewById<TextView>(R.id.tvSelectPlan)?.text = getString(R.string.select_plan)
        }
    }

    private fun animateCrown() {
        val crownView = findViewById<ImageView>(R.id.imgPremiumCrown)
        crownView?.let {
            // Pulse animasyonu
            val pulseAnim = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
            pulseAnim.duration = 1000
            pulseAnim.repeatMode = android.view.animation.Animation.REVERSE
            pulseAnim.repeatCount = android.view.animation.Animation.INFINITE
            it.startAnimation(pulseAnim)
        }
    }

    private fun loadBillingConfig() {
        activityScope.launch {
            try {
                val config = StickerRepository.getBillingConfig()
                billingConfig = config

                // Trial gün sayısını Firebase'den al
                val monthlyPlan = config.plans.find { it.id == BillingManager.PREMIUM_MONTHLY }
                if (monthlyPlan != null && monthlyPlan.trialDays > 0) {
                    val trialBadge = findViewById<View>(R.id.cardMonthly)?.findViewById<TextView>(R.id.tvBadge)
                    trialBadge?.text = getString(R.string.trial_days_format, monthlyPlan.trialDays)
                }
            } catch (e: Exception) {
                // Firebase unavailable - keep default values
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
        billingManager?.destroy()
    }
}
