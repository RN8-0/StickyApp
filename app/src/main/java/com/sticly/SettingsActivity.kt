package com.sticly

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import android.content.res.ColorStateList
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import com.sticly.LocaleHelper
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            PreferencesHelper.setNotificationsEnabled(this, true)
            updateNotificationStatus()
            Toast.makeText(this, R.string.notifications_enabled, Toast.LENGTH_SHORT).show()
        } else {
            PreferencesHelper.setNotificationsEnabled(this, false)
            updateNotificationStatus()
            Toast.makeText(this, R.string.notifications_disabled, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setupListeners()
        updateNotificationStatus()
        updateLanguageStatus()
        updateLoginSwitches()
    }

    private fun isUserLoggedIn(): Boolean {
        if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null) return true
        val email = getSharedPreferences("sticky_prefs", MODE_PRIVATE).getString("user_email", "") ?: ""
        return email.trim().isNotEmpty()
    }

    private fun updateLoginSwitches() {
        val isLoggedIn = isUserLoggedIn()
        val switchG = findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchGoogle)
        findViewById<View>(R.id.btnLogin)?.visibility = if (isLoggedIn) View.GONE else View.VISIBLE

        if (isLoggedIn) {
            // CONNECTED -> GREEN
            switchG.isChecked = true
            switchG.isEnabled = true
            switchG.alpha = 1.0f
            val greenColor = ContextCompat.getColor(this, R.color.accent)
            val greenTrack = ContextCompat.getColor(this, R.color.accent_light)
            switchG.thumbTintList = ColorStateList.valueOf(greenColor)
            switchG.trackTintList = ColorStateList.valueOf(greenTrack)
        } else {
            // NOT CONNECTED -> GREY
            switchG.isChecked = false
            switchG.isEnabled = true
            switchG.alpha = 0.7f
            val grayColor = ContextCompat.getColor(this, R.color.text_secondary)
            switchG.thumbTintList = ColorStateList.valueOf(grayColor)
            switchG.trackTintList = ColorStateList.valueOf(grayColor)
        }
    }

    private fun setupListeners() {
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        // Premium
        findViewById<View>(R.id.btnPremiumSettings).setOnClickListener {
            startActivity(Intent(this, PremiumActivity::class.java))
        }

        // Remove Ads
        findViewById<View>(R.id.btnRemoveAds).setOnClickListener {
            if (!PreferencesHelper.isPremium(this)) {
                startActivity(Intent(this, PremiumActivity::class.java))
            } else {
                Toast.makeText(this, R.string.subscription_activated, Toast.LENGTH_SHORT).show()
            }
        }

        // Notifications
        findViewById<View>(R.id.btnNotifications).setOnClickListener {
            val isEnabled = PreferencesHelper.isNotificationsEnabled(this)
            if (!isEnabled) {
                // Turning it ON
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                         PreferencesHelper.setNotificationsEnabled(this, true)
                         updateNotificationStatus()
                         Toast.makeText(this, R.string.notifications_enabled, Toast.LENGTH_SHORT).show()
                    } else {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    PreferencesHelper.setNotificationsEnabled(this, true)
                    updateNotificationStatus()
                    Toast.makeText(this, R.string.notifications_enabled, Toast.LENGTH_SHORT).show()
                }
            } else {
                // Turning it OFF
                PreferencesHelper.setNotificationsEnabled(this, false)
                updateNotificationStatus()
                Toast.makeText(this, R.string.notifications_disabled, Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<View>(R.id.btnLanguage).setOnClickListener {
            showLanguageDialog()
        }

        // Support & Share
        findViewById<View>(R.id.btnContact).setOnClickListener {
            startActivity(Intent(this, ContactActivity::class.java))
        }

        findViewById<View>(R.id.btnSuggest).setOnClickListener {
            startActivity(Intent(this, SuggestActivity::class.java))
        }

        findViewById<View>(R.id.btnRate).setOnClickListener {
            openPlayStore()
        }

        findViewById<View>(R.id.btnShare).setOnClickListener {
            shareApp()
        }

        // Restore Purchases
        findViewById<View>(R.id.btnRestore).setOnClickListener {
            if (isUserLoggedIn()) {
                Toast.makeText(this, R.string.google_already_signed_in, Toast.LENGTH_SHORT).show()
                performRestore()
            } else {
                restoreAfterGoogleLogin = true
                loginWithGoogle()
            }
        }

        // About
        findViewById<View>(R.id.btnAbout).setOnClickListener {
            openWebPage("#about")
        }

        // FAQ
        findViewById<View>(R.id.btnFaq).setOnClickListener {
            openWebPage("#faq")
        }

        // Privacy
        findViewById<View>(R.id.btnPrivacy).setOnClickListener {
            openWebPage("#privacy")
        }

        // Connection switch
        val switchG = findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchGoogle)

        findViewById<View>(R.id.btnLogin)?.setOnClickListener {
            if (!isUserLoggedIn()) loginWithGoogle()
        }

        switchG.setOnClickListener {
            if (isUserLoggedIn()) {
                switchG.isChecked = true
                val email = getSharedPreferences("sticky_prefs", MODE_PRIVATE).getString("user_email", "") ?: ""
                Toast.makeText(this, getString(R.string.google_account_connected) + if (email.isNotEmpty()) " ($email)" else "", Toast.LENGTH_SHORT).show()
            } else {
                switchG.isChecked = true
                loginWithGoogle()
            }
        }
        switchG.setOnCheckedChangeListener(null)
    }

    private fun openWebPage(anchor: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(StickyConfig.legalUrl(anchor)))
        startActivity(intent)
    }

    private lateinit var googleSignInClient: com.google.android.gms.auth.api.signin.GoogleSignInClient

    private fun loginWithGoogle() {
        val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(this, gso)
        googleSignInLauncher.launch(googleSignInClient.signInIntent)
    }

    private val googleSignInLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                if (account == null) {
                    findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchGoogle).isChecked = false
                    updateLoginSwitches()
                    return@registerForActivityResult
                }
                authWithPocketBase(account)
            } catch (e: com.google.android.gms.common.api.ApiException) {
                val msg = when (e.statusCode) {
                    12501 -> null // user cancelled
                    else -> getString(R.string.sign_in_failed)
                }
                if (msg != null) Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchGoogle).isChecked = false
                updateLoginSwitches()
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.sign_in_failed), Toast.LENGTH_SHORT).show()
                findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchGoogle).isChecked = false
                updateLoginSwitches()
            }
        } else {
            findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchGoogle).isChecked = false
            updateLoginSwitches()
        }
    }

    private fun authWithPocketBase(account: com.google.android.gms.auth.api.signin.GoogleSignInAccount) {
        lifecycleScope.launch {
            try {
                // Save profile locally first
                PreferencesHelper.setUserProfile(
                    this@SettingsActivity,
                    account.email,
                    account.displayName,
                    account.photoUrl?.toString()
                )
                // Sync with PocketBase (optional)
                try {
                    val idToken = account.idToken ?: throw Exception("Missing ID token")
                    withContext(Dispatchers.IO) {
                        PocketBaseHelper.authWithOAuth("google", idToken)
                    }
                    PreferencesHelper.setPocketBaseAuth(
                        this@SettingsActivity,
                        PocketBaseHelper.getToken(),
                        PocketBaseHelper.getAuthRecordId()
                    )
                } catch (_: Exception) {}
                val deviceId = PreferencesHelper.getDeviceId(this@SettingsActivity)
                PreferencesHelper.syncUserDataWithPocketBase(this@SettingsActivity, deviceId)
                Toast.makeText(this@SettingsActivity, getString(R.string.signed_in_google), Toast.LENGTH_SHORT).show()
                updateLoginSwitches()
                if (restoreAfterGoogleLogin) {
                    restoreAfterGoogleLogin = false
                    performRestore()
                }
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, getString(R.string.sign_in_failed), Toast.LENGTH_SHORT).show()
                findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchGoogle).isChecked = false
                updateLoginSwitches()
                restoreAfterGoogleLogin = false
            }
        }
    }

    private var billingUIManager: BillingManager? = null
    private var restoreAfterGoogleLogin = false

    private fun performRestore() {
        val loadingToast = Toast.makeText(this, R.string.restoring, Toast.LENGTH_SHORT)
        loadingToast.show()

        if (billingUIManager == null) {
            billingUIManager = BillingManager(this, onPurchaseComplete = { isPremium ->
                if (isPremium) {
                    runOnUiThread {
                        updatePremiumStatus()
                    }
                }
            })
        }

        billingUIManager?.restorePurchases { result ->
            runOnUiThread {
                loadingToast.cancel()
                when (result) {
                    BillingManager.RestoreResult.SUCCESS -> {
                        if (PreferencesHelper.isPremium(this)) {
                            Toast.makeText(this, R.string.restore_success, Toast.LENGTH_SHORT).show()
                            val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                            if (user != null) {
                                PreferencesHelper.syncUserData(this, user.uid)
                            }
                            updatePremiumStatus()
                        } else {
                            Toast.makeText(this, R.string.purchased_packs_restored, Toast.LENGTH_LONG).show()
                        }
                    }
                    BillingManager.RestoreResult.NOT_FOUND -> {
                        Toast.makeText(this, R.string.restore_not_found, Toast.LENGTH_SHORT).show()
                    }
                    BillingManager.RestoreResult.ERROR -> {
                        Toast.makeText(this, R.string.restore_error, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        billingUIManager?.destroy()
    }

    private fun logoutGoogle() {
        if (::googleSignInClient.isInitialized) {
            googleSignInClient.signOut().addOnCompleteListener {
                getSharedPreferences("sticky_prefs", MODE_PRIVATE).edit()
                    .remove("user_email").remove("user_display_name").remove("user_photo_url").apply()
                PreferencesHelper.setPocketBaseAuth(this, null, null)
                Toast.makeText(this, getString(R.string.signed_out_google), Toast.LENGTH_SHORT).show()
                updateNotificationStatus()
                updateLoginSwitches()
            }
        } else {
            getSharedPreferences("sticky_prefs", MODE_PRIVATE).edit()
                .remove("user_email").remove("user_display_name").remove("user_photo_url").apply()
            PreferencesHelper.setPocketBaseAuth(this, null, null)
            Toast.makeText(this, getString(R.string.signed_out), Toast.LENGTH_SHORT).show()
        }
    }


    override fun onResume() {
        super.onResume()
        updatePremiumStatus()
    }

    private fun updatePremiumStatus() {
        val isPremium = PreferencesHelper.isPremium(this)
        findViewById<View>(R.id.btnPremiumSettings)?.visibility = if (isPremium) View.GONE else View.VISIBLE
        
        // Remove Ads switch updates
        val switchRemoveAds = findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchRemoveAds)
        switchRemoveAds.isChecked = isPremium
        // Switch is already not clickable/focusable in XML, so it acts as an indicator
        // If premium, the row click shows "Already active", if not premium it opens purchase page
    }

    private fun updateNotificationStatus() {
        val isEnabled = PreferencesHelper.isNotificationsEnabled(this)
        findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchNotifications).isChecked = isEnabled
        findViewById<TextView>(R.id.tvNotificationStatus).text = if (isEnabled) {
            getString(R.string.notifications_enabled)
        } else {
            getString(R.string.notifications_disabled)
        }
    }

    private fun updateLanguageStatus() {
        findViewById<TextView>(R.id.tvLanguageStatus)?.text = languageName(PreferencesHelper.getLanguage(this))
    }

    private fun showLanguageDialog() {
        val languageNames = arrayOf("🇺🇸 English", "🇹🇷 Türkçe", "🇪🇸 Español", "🇨🇳 简体中文",
            "🇸🇦 العربية", "🇮🇳 हिन्दी", "🇧🇷 Português", "🇫🇷 Français", "🇩🇪 Deutsch", "🇯🇵 日本語")
        val languageCodes = arrayOf("en", "tr", "es", "zh", "ar", "hi", "pt", "fr", "de", "ja")
        val currentLang = PreferencesHelper.getLanguage(this)

        val view = layoutInflater.inflate(R.layout.dialog_language_selector, null)
        val container = view.findViewById<android.widget.LinearLayout>(R.id.llLanguageItems)
        val cancelBtn = view.findViewById<android.widget.TextView>(R.id.tvLangCancel)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(view)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        languageNames.forEachIndexed { i, label ->
            val itemView = layoutInflater.inflate(android.R.layout.simple_list_item_1, container, false)
            val tv = itemView.findViewById<android.widget.TextView>(android.R.id.text1)
            tv.text = label
            tv.textSize = 15.5f
            tv.setPadding(72, 36, 72, 36)
            val isSelected = languageCodes[i] == currentLang
            tv.setTextColor(resources.getColor(if (isSelected) R.color.accent else R.color.text_primary, theme))
            if (isSelected) {
                tv.setTypeface(null, android.graphics.Typeface.BOLD)
                tv.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_check, 0)
                tv.compoundDrawablePadding = 16
            }
            val ripple = android.util.TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
            itemView.setBackgroundResource(ripple.resourceId)
            itemView.setOnClickListener {
                if (languageCodes[i] != currentLang) {
                    PreferencesHelper.setLanguage(this, languageCodes[i])
                    dialog.dismiss()
                    recreate()
                } else {
                    dialog.dismiss()
                }
            }
            container.addView(itemView)
        }

        cancelBtn.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun languageName(code: String): String = when (code) {
        "tr" -> "Türkçe"
        "es" -> "Español"
        "zh" -> "简体中文"
        "ar" -> "العربية"
        "hi" -> "हिन्दी"
        "pt" -> "Português"
        "fr" -> "Français"
        "de" -> "Deutsch"
        "ja" -> "日本語"
        else -> "English"
    }

    private fun shareApp() {
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
        intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_text))
        startActivity(Intent.createChooser(intent, getString(R.string.share_app)))
    }

    private fun openPlayStore() {
        val uri = Uri.parse("market://details?id=$packageName")
        val goToMarket = Intent(Intent.ACTION_VIEW, uri)
        goToMarket.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or
                Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        try {
            startActivity(goToMarket)
        } catch (e: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("http://play.google.com/store/apps/details?id=$packageName")))
        }
    }

}
