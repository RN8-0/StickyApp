package com.sticly

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resumeWithException

class PremiumActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    private var billingManager: BillingManager? = null
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var billingConfig: BillingConfig? = null
    private var selectedPlan = BillingManager.PREMIUM_MONTHLY // Default: monthly

    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "is_premium" || key == "premium_type") {
            runOnUiThread { updateUI() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_premium)

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
                updatePricesFromGooglePlay()
            }
        )

        setupViews()
        setupFeatures()
        loadBillingConfig()

        getSharedPreferences("sticky_prefs", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(prefsListener)
        syncPremiumFromFirebase()
        updateUI()
        setupEdgeToEdge()
    }

    private fun setupViews() {
        // Close button
        findViewById<View>(R.id.btnClose).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }

        // Subscribe button
        findViewById<MaterialButton>(R.id.btnSubscribe).setOnClickListener {
            billingManager?.launchPurchase(this, selectedPlan)
        }

        // View all plans
        findViewById<TextView>(R.id.tvPlanSwitcher).setOnClickListener {
            showPlansBottomSheet()
        }

        // Footer links
        findViewById<View>(R.id.btnTerms).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://sticky-privacy-legal.web.app/#terms")))
        }

        findViewById<View>(R.id.btnPrivacyPolicy).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://sticky-privacy-legal.web.app/#privacy")))
        }

        findViewById<View>(R.id.btnRestore).setOnClickListener {
            restorePurchases()
        }
    }

    private fun setupFeatures() {
        // Feature 1: Ad-Free
        setupFeature(
            R.id.featureAdFree,
            R.drawable.ic_no_ads,
            R.string.feature_adfree_title,
            R.string.feature_adfree_desc
        )

        // Feature 2: Premium Packs
        setupFeature(
            R.id.featurePremiumPacks,
            R.drawable.ic_premium,
            R.string.feature_premium_title,
            R.string.feature_premium_desc
        )

        // Feature 3: AI Background Removal
        setupFeature(
            R.id.featureAIBg,
            R.drawable.ic_ai_bg,
            R.string.feature_ai_title,
            R.string.feature_ai_desc
        )

        // Feature 4: Sticker Maker
        setupFeature(
            R.id.featureStickerMaker,
            R.drawable.ic_sticker_maker,
            R.string.feature_maker_title,
            R.string.feature_maker_desc
        )

        // Feature 5: Priority Support
        setupFeature(
            R.id.featureSupport,
            R.drawable.ic_support,
            R.string.feature_support_title,
            R.string.feature_support_desc
        )
    }

    private fun setupFeature(viewId: Int, iconRes: Int, titleRes: Int, descRes: Int) {
        val featureView = findViewById<View>(viewId)
        featureView.findViewById<ImageView>(R.id.imgFeatureIcon).setImageResource(iconRes)
        featureView.findViewById<TextView>(R.id.tvFeatureTitle).setText(titleRes)
        featureView.findViewById<TextView>(R.id.tvFeatureDesc).setText(descRes)
    }

    private fun showPlansBottomSheet() {
        val bottomSheet = BottomSheetDialog(this, R.style.TransparentBottomSheetDialog)
        val view = layoutInflater.inflate(R.layout.dialog_plans, null)
        bottomSheet.setContentView(view)

        val monthlyPrice = billingManager?.getFormattedPrice(BillingManager.PREMIUM_MONTHLY) ?: getString(R.string.price_loading)
        val yearlyPrice = billingManager?.getFormattedPrice(BillingManager.PREMIUM_YEARLY) ?: getString(R.string.price_loading)

        // Update subtitle with monthly price
        view.findViewById<TextView>(R.id.tvSubtitle).text = getString(R.string.free_trial_subtitle)

        // Yearly plan
        view.findViewById<TextView>(R.id.tvYearlyPrice).text = "$yearlyPrice/${getString(R.string.period_year)}"
        view.findViewById<View>(R.id.cardYearly).setOnClickListener {
            selectedPlan = BillingManager.PREMIUM_YEARLY
            updateCardSelection(view, true)
        }

        // Monthly plan
        view.findViewById<TextView>(R.id.tvMonthlyPrice).text = "$monthlyPrice/${getString(R.string.period_month)}"
        view.findViewById<View>(R.id.cardMonthly).setOnClickListener {
            selectedPlan = BillingManager.PREMIUM_MONTHLY
            updateCardSelection(view, false)
        }

        // Continue button
        view.findViewById<View>(R.id.btnContinue).setOnClickListener {
            bottomSheet.dismiss()
            billingManager?.launchPurchase(this, selectedPlan)
        }

        // Footer links
        view.findViewById<View>(R.id.btnDialogTerms).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://sticky-privacy-legal.web.app/#terms")))
        }
        view.findViewById<View>(R.id.btnDialogPrivacy).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://sticky-privacy-legal.web.app/#privacy")))
        }
        view.findViewById<View>(R.id.btnDialogRestore).setOnClickListener {
            bottomSheet.dismiss()
            restorePurchases()
        }

        // Default selection is yearly (best value)
        selectedPlan = BillingManager.PREMIUM_YEARLY

        bottomSheet.show()
    }

    private fun updateCardSelection(view: View, isYearlySelected: Boolean) {
        val cardYearly = view.findViewById<View>(R.id.cardYearly).findViewById<View>(android.R.id.content)?.parent as? View
            ?: view.findViewById<View>(R.id.cardYearly)
        val cardMonthly = view.findViewById<View>(R.id.cardMonthly).findViewById<View>(android.R.id.content)?.parent as? View
            ?: view.findViewById<View>(R.id.cardMonthly)

        // Visual feedback - update backgrounds
        val yearlyInner = (cardYearly as? androidx.cardview.widget.CardView)?.getChildAt(0)
        val monthlyInner = (cardMonthly as? androidx.cardview.widget.CardView)?.getChildAt(0)

        yearlyInner?.setBackgroundResource(if (isYearlySelected) R.drawable.bg_plan_card_selected else R.drawable.bg_plan_card)
        monthlyInner?.setBackgroundResource(if (!isYearlySelected) R.drawable.bg_plan_card_selected else R.drawable.bg_plan_card)
    }

    private fun updatePricesFromGooglePlay() {
        val monthlyPrice = billingManager?.getFormattedPrice(BillingManager.PREMIUM_MONTHLY)

        // Calculate trial end date (today + 3 days)
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, 3)
        val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
        val trialEndDate = dateFormat.format(calendar.time)

        // Update main CTA button - Sticker.ly style (large title, small subtitle)
        val btnSubscribe = findViewById<MaterialButton>(R.id.btnSubscribe)
        if (monthlyPrice != null) {
            val title = getString(R.string.free_trial_btn_title)
            val subtitle = getString(R.string.free_trial_btn_subtitle, monthlyPrice, getString(R.string.period_month))
            val fullText = "$title\n$subtitle"

            val spannable = SpannableString(fullText)
            // Title - large (18sp)
            spannable.setSpan(AbsoluteSizeSpan(18, true), 0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            // Subtitle - small (12sp)
            spannable.setSpan(AbsoluteSizeSpan(12, true), title.length + 1, fullText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            btnSubscribe.text = spannable
        }

        // Update trial info with dynamic date
        val tvTrialInfo = findViewById<TextView>(R.id.tvTrialInfo)
        if (monthlyPrice != null) {
            tvTrialInfo.text = getString(R.string.trial_info, trialEndDate, monthlyPrice, getString(R.string.period_month))
        }
    }

    private fun setupEdgeToEdge() {
        val root = findViewById<View>(R.id.premium_root)
        val btnClose = findViewById<View>(R.id.btnClose)
        val bottomCTA = findViewById<View>(R.id.bottomCTA)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Adjust close button margin for status bar
            val closeParams = btnClose.layoutParams as androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
            closeParams.topMargin = systemBars.top + 16
            btnClose.layoutParams = closeParams

            // Adjust bottom CTA for navigation bar
            bottomCTA.setPadding(
                bottomCTA.paddingLeft,
                bottomCTA.paddingTop,
                bottomCTA.paddingRight,
                systemBars.bottom + 24
            )

            insets
        }
    }

    private fun updateUI() {
        val isPremium = PreferencesHelper.isPremium(this)
        val premiumType = PreferencesHelper.getPremiumType(this)

        val featuresContainer = findViewById<View>(R.id.featuresContainer)
        val premiumActiveContainer = findViewById<View>(R.id.premiumActiveContainer)
        val bottomCTA = findViewById<View>(R.id.bottomCTA)
        val tvSlogan = findViewById<TextView>(R.id.tvSlogan)
        val footerLinks = findViewById<View>(R.id.footerLinks)
        val tvTrialInfo = findViewById<View>(R.id.tvTrialInfo)

        if (isPremium) {
            tvSlogan.text = getString(R.string.premium_subscriber_desc)
            featuresContainer.visibility = View.GONE
            premiumActiveContainer.visibility = View.VISIBLE
            bottomCTA.visibility = View.GONE
            footerLinks.visibility = View.GONE
            tvTrialInfo.visibility = View.GONE

            // Set premium type text
            val tvPremiumType = findViewById<TextView>(R.id.tvPremiumType)
            when (premiumType) {
                "subscription" -> {
                    val expiry = PreferencesHelper.getPremiumExpiry(this)
                    val remainingDays = ((expiry - System.currentTimeMillis()) / (24 * 60 * 60 * 1000)).toInt()
                    tvPremiumType.text = if (remainingDays > 60) {
                        getString(R.string.premium_yearly_member)
                    } else {
                        getString(R.string.premium_monthly_member)
                    }
                }
                else -> tvPremiumType.text = getString(R.string.premium_active)
            }

            animateCrown()
        } else {
            tvSlogan.text = getString(R.string.premium_slogan)
            featuresContainer.visibility = View.VISIBLE
            premiumActiveContainer.visibility = View.GONE
            bottomCTA.visibility = View.VISIBLE
            footerLinks.visibility = View.VISIBLE
            tvTrialInfo.visibility = View.VISIBLE
        }
    }

    private fun animateCrown() {
        val crownView = findViewById<ImageView>(R.id.imgPremiumCrown)
        crownView?.let {
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
                billingConfig = StickerRepository.getBillingConfig()
            } catch (e: Exception) {
                // Firebase unavailable - keep default values
            }
        }
    }

    private fun restorePurchases() {
        if (billingManager == null) {
            Toast.makeText(this, R.string.restore_error, Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, R.string.restoring, Toast.LENGTH_SHORT).show()
        billingManager?.restorePurchases { result ->
            val messageRes = when (result) {
                BillingManager.RestoreResult.SUCCESS -> {
                    updateUI()
                    R.string.restore_success
                }
                BillingManager.RestoreResult.NOT_FOUND -> R.string.restore_not_found
                BillingManager.RestoreResult.ERROR -> R.string.restore_error
            }
            Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
        }
    }

    private fun syncPremiumFromFirebase() {
        val user = FirebaseAuth.getInstance().currentUser
        val docId = user?.uid ?: PreferencesHelper.getDeviceId(this)

        if (docId.isEmpty()) return

        activityScope.launch(Dispatchers.IO) {
            try {
                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val doc = firestore.collection("users").document(docId).get().await()

                if (doc.exists()) {
                    val isPremium = doc.getBoolean("is_premium") ?: false
                    val premiumType = doc.getString("premium_type") ?: "none"
                    val premiumExpiry = doc.getLong("premium_expiry") ?: 0L

                    if (isPremium) {
                        PreferencesHelper.updateLocalPremiumStatus(this@PremiumActivity, true, premiumType, premiumExpiry)
                    } else {
                        PreferencesHelper.updateLocalPremiumStatus(this@PremiumActivity, false, "none", 0L)
                    }

                    launch(Dispatchers.Main) {
                        updateUI()
                    }
                }
            } catch (e: Exception) {
                // Silent fail
            }
        }
    }

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T {
        return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            addOnSuccessListener { cont.resume(it, null) }
            addOnFailureListener { cont.resumeWithException(it) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        getSharedPreferences("sticky_prefs", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(prefsListener)
        activityScope.cancel()
        billingManager?.destroy()
    }
}
