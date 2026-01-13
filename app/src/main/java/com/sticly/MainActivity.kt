package com.sticly

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var drawer: DrawerLayout
    private lateinit var rv: RecyclerView
    private lateinit var adapter: PackAdapter
    private var allPacks: List<Pack> = emptyList()
    private var billingManager: BillingManager? = null

    override fun onCreate(s: Bundle?) {
        applyTheme()
        super.onCreate(s)
        setContentView(R.layout.activity_main)

        // Internet kontrolu - uyarı ver ama uygulamayı engelleme
        if (!NetworkUtils.isOnline(this)) {
            showNoInternetDialog()
            // Lokal içerik ile devam et
        }

        // Billing Manager (emulatörde çalışmayabilir)
        try {
            billingManager = BillingManager(this) { isPurchased ->
                if (isPurchased) {
                    Toast.makeText(this, R.string.premium_purchased, Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            // Billing servisi kullanılamıyor (emulator vb.)
            e.printStackTrace()
        }

        drawer = findViewById(R.id.drawer)
        rv = findViewById(R.id.rv)
        val menuBtn = findViewById<ImageButton>(R.id.menuBtn)
        val searchBox = findViewById<EditText>(R.id.searchBox)
        val btnTheme = findViewById<ImageButton>(R.id.btnTheme)

        // Menu items
        val navFaq = findViewById<LinearLayout>(R.id.navFaq)
        val navAbout = findViewById<LinearLayout>(R.id.navAbout)
        val navPremium = findViewById<LinearLayout>(R.id.navPremium)
        val navContact = findViewById<LinearLayout>(R.id.navContact)
        val navRate = findViewById<LinearLayout>(R.id.navRate)
        val navSuggest = findViewById<LinearLayout>(R.id.navSuggest)
        val navShare = findViewById<LinearLayout>(R.id.navShare)
        val navNotifications = findViewById<LinearLayout>(R.id.navNotifications)
        val navPrivacy = findViewById<LinearLayout>(R.id.navPrivacy)

        // Menu button opens drawer from right
        menuBtn.setOnClickListener {
            drawer.openDrawer(GravityCompat.END)
        }

        // Theme toggle button
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        updateThemeIcon(btnTheme, prefs.getInt("theme", 0))

        btnTheme.setOnClickListener { view ->
            showThemeMenu(view, btnTheme)
        }

        // Menu item click handlers
        navFaq.setOnClickListener {
            drawer.closeDrawers()
            showFaqDialog()
        }

        navAbout.setOnClickListener {
            drawer.closeDrawers()
            showAboutDialog()
        }

        navPremium.setOnClickListener {
            drawer.closeDrawers()
            showPremiumDialog()
        }

        navContact.setOnClickListener {
            drawer.closeDrawers()
            // Modern Contact sayfasına git
            startActivity(Intent(this, ContactActivity::class.java))
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }

        navRate.setOnClickListener {
            drawer.closeDrawers()
            openPlayStore()
        }

        navSuggest.setOnClickListener {
            drawer.closeDrawers()
            showSuggestDialog()
        }

        navShare.setOnClickListener {
            drawer.closeDrawers()
            shareApp()
        }

        navNotifications.setOnClickListener {
            drawer.closeDrawers()
            showNotificationSettings()
        }

        navPrivacy.setOnClickListener {
            drawer.closeDrawers()
            showPrivacyDialog()
        }

        // Setup RecyclerView with linear layout (list)
        rv.layoutManager = LinearLayoutManager(this)

        // Önce lokal paketleri göster (hızlı yükleme)
        allPacks = Loader.load(this)
        adapter = PackAdapter(allPacks) {
            startActivity(Intent(this, DetailsActivity::class.java).putExtra("id", it.id))
        }
        rv.adapter = adapter

        // Firebase'den paketleri yükle (async)
        loadPacksFromFirebase()

        // Search functionality
        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterPacks(s?.toString() ?: "")
            }
        })
    }

    override fun onResume() {
        super.onResume()
        // Refresh adapter when returning from DetailsActivity
        if (::adapter.isInitialized) {
            adapter.notifyDataSetChanged()
        }
    }

    private fun showNoInternetDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.no_internet_title)
            .setMessage("İnternet bağlantısı bulunamadı. Bazı özellikler çalışmayabilir.\n\nLokal sticker paketleri kullanılabilir.")
            .setCancelable(true)
            .setPositiveButton(R.string.retry) { _, _ ->
                if (NetworkUtils.isOnline(this)) {
                    recreate()
                } else {
                    Toast.makeText(this, "Hala internet bağlantısı yok", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.ok) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun loadPacksFromFirebase() {
        // İnternet yoksa Firebase'i deneme
        if (!NetworkUtils.isOnline(this)) {
            return
        }

        lifecycleScope.launch {
            try {
                val firebasePacks = StickerRepository.loadPacks(this@MainActivity)
                if (firebasePacks.isNotEmpty()) {
                    allPacks = firebasePacks
                    adapter.updateList(allPacks)
                }
            } catch (e: Exception) {
                // Firebase yüklenemezse lokal paketler zaten gösteriliyor
                e.printStackTrace()
            }
        }
    }

    private fun filterPacks(query: String) {
        val filtered = if (query.isEmpty()) {
            allPacks
        } else {
            allPacks.filter { it.name.contains(query, ignoreCase = true) }
        }
        adapter.updateList(filtered)
    }

    private fun applyTheme() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        when (prefs.getInt("theme", 0)) {
            0 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            2 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
    }

    private fun updateThemeIcon(btnTheme: ImageButton, themeMode: Int) {
        val icon = when (themeMode) {
            1 -> R.drawable.ic_sun  // Light mode - show sun
            2 -> R.drawable.ic_moon  // Dark mode - show moon
            else -> R.drawable.ic_theme  // System - show theme icon
        }
        btnTheme.setImageResource(icon)
    }

    private fun showThemeMenu(anchor: View, btnTheme: ImageButton) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_theme, popup.menu)

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val currentTheme = prefs.getInt("theme", 0)

        popup.setOnMenuItemClickListener { item ->
            val newTheme = when (item.itemId) {
                R.id.theme_light -> 1
                R.id.theme_dark -> 2
                R.id.theme_system -> 0
                else -> currentTheme
            }

            if (newTheme != currentTheme) {
                changeTheme(newTheme)
            }
            true
        }

        popup.show()
    }

    private fun changeTheme(mode: Int) {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        prefs.edit().putInt("theme", mode).apply()
        recreate()
    }

    private fun showFaqDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.faq_title)
            .setMessage(R.string.faq_content)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.about_title)
            .setMessage(R.string.about_message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun showPremiumDialog() {
        val isPremium = PreferencesHelper.isPremium(this)

        if (isPremium) {
            AlertDialog.Builder(this)
                .setTitle(R.string.premium_title)
                .setMessage(R.string.premium_active)
                .setPositiveButton(R.string.ok, null)
                .show()
            return
        }

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_premium, null)

        AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton(R.string.buy_premium) { _, _ ->
                billingManager?.launchPurchase(this)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openPlayStore() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
        } catch (e: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
        }
    }

    private fun showSuggestDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_suggest, null)
        val suggestionInput = view.findViewById<TextInputEditText>(R.id.inputSuggestion)

        AlertDialog.Builder(this)
            .setTitle(R.string.suggest_title)
            .setView(view)
            .setPositiveButton(R.string.send) { _, _ ->
                val suggestion = suggestionInput.text?.toString()?.trim() ?: ""
                if (suggestion.isNotEmpty()) {
                    sendSuggestionToFirebase(suggestion)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun sendSuggestionToFirebase(suggestion: String) {
        val db = FirebaseFirestore.getInstance()
        val data = hashMapOf(
            "suggestion" to suggestion,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("suggestions")
            .add(data)
            .addOnSuccessListener {
                Toast.makeText(this, R.string.suggestion_sent, Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, R.string.message_error, Toast.LENGTH_SHORT).show()
            }
    }

    private fun shareApp() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
            putExtra(Intent.EXTRA_TEXT, getString(R.string.share_text))
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_app)))
    }

    private fun showNotificationSettings() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_notifications, null)
        val switchNotifications = view.findViewById<SwitchMaterial>(R.id.switchNotifications)

        switchNotifications.isChecked = PreferencesHelper.isNotificationsEnabled(this)

        AlertDialog.Builder(this)
            .setTitle(R.string.notifications_title)
            .setView(view)
            .setPositiveButton(R.string.ok) { _, _ ->
                PreferencesHelper.setNotificationsEnabled(this, switchNotifications.isChecked)
                Toast.makeText(this, R.string.notifications_saved, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showPrivacyDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.privacy_title)
            .setMessage(R.string.privacy_content)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.END)) {
            drawer.closeDrawer(GravityCompat.END)
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        billingManager?.destroy()
    }
}
