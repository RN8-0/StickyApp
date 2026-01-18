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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var drawer: DrawerLayout
    private lateinit var rv: RecyclerView
    private lateinit var loadingOverlay: View
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var adapter: PackAdapter
    private lateinit var btnFilter: ImageButton
    private lateinit var menuBtn: ImageButton
    private lateinit var toolbarTitle: TextView
    private lateinit var toolbarSubtitle: TextView
    private lateinit var fabAddCustom: com.google.android.material.floatingactionbutton.FloatingActionButton
    private var allPacks: List<Pack> = emptyList()
    private var billingManager: BillingManager? = null
    private var currentFilter: FilterType = FilterType.ALL
    private var currentSearchQuery: String = ""
    private var pendingDeletePackId: String? = null
    private var waitingForWhatsAppReturn = false

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
        loadingOverlay = findViewById(R.id.loadingOverlay)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        menuBtn = findViewById(R.id.menuBtn)
        toolbarTitle = findViewById(R.id.toolbarTitle)
        toolbarSubtitle = findViewById(R.id.toolbarSubtitle)
        val searchBox = findViewById<EditText>(R.id.searchBox)
        val btnTheme = findViewById<ImageButton>(R.id.btnTheme)
        btnFilter = findViewById(R.id.btnFilter)

        // Pull to Refresh ayarları
        swipeRefresh.setColorSchemeResources(R.color.accent, R.color.primary)
        swipeRefresh.setOnRefreshListener { refreshPacks() }

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
        val navRestorePurchases = findViewById<LinearLayout>(R.id.navRestorePurchases)

        // Menu button opens drawer from right OR goes back
        menuBtn.setOnClickListener {
            if (currentFilter == FilterType.CUSTOM) {
                // Geri butonu gibi davran
                currentFilter = FilterType.ALL
                applyFilters()
            } else {
                drawer.openDrawer(GravityCompat.END)
            }
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

        navRestorePurchases.setOnClickListener {
            drawer.closeDrawers()
            restorePurchases()
        }

        // Setup RecyclerView with linear layout (list)
        rv.layoutManager = LinearLayoutManager(this)

        // Adapter initialization
        adapter = PackAdapter(allPacks, { pack ->
            startActivity(Intent(this, DetailsActivity::class.java).putExtra("id", pack.id))
        }, {
            if (currentFilter == FilterType.FAVORITES) applyFilters()
        }, { pack ->
            // Silme butonu tıklandı - WhatsApp'tan kaldır ve uygulamadan sil
            deleteCustomPack(pack)
        })
        rv.adapter = adapter

        // FAB Logic
        val fabMain = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabMain)
        val fabOverlay = findViewById<View>(R.id.fabOverlay)
        val fabMenuContainer = findViewById<View>(R.id.fabMenuContainer)
        val fabActionCreate = findViewById<View>(R.id.fabActionCreate)
        val fabActionMyStickers = findViewById<View>(R.id.fabActionMyStickers)
        fabAddCustom = findViewById(R.id.fabAddCustom)

        var isFabOpen = false
        
        fun toggleFabMenu() {
            isFabOpen = !isFabOpen
            if (isFabOpen) {
                fabOverlay.visibility = View.VISIBLE
                fabMenuContainer.visibility = View.VISIBLE
                fabMenuContainer.alpha = 0f
                fabMenuContainer.translationY = 50f
                fabMenuContainer.animate().alpha(1f).translationY(0f).setDuration(250).start()
                fabMain.animate().rotation(45f).setDuration(250).start()
            } else {
                fabOverlay.visibility = View.GONE
                fabMenuContainer.animate().alpha(0f).translationY(50f).setDuration(250).withEndAction {
                    fabMenuContainer.visibility = View.GONE
                }.start()
                fabMain.animate().rotation(0f).setDuration(250).start()
            }
        }
        
        fabMain.setOnClickListener { toggleFabMenu() }
        fabOverlay.setOnClickListener { toggleFabMenu() }
        
        // Çıkartma Yap seçeneği
        fabActionCreate.setOnClickListener {
            toggleFabMenu()
            startActivity(Intent(this, StickerMakerActivity::class.java))
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }
        
        // Çıkartmalarınız seçeneği
        fabActionMyStickers.setOnClickListener {
            toggleFabMenu()
            currentFilter = FilterType.CUSTOM
            applyFilters()
            // Custom modunda fabAddCustom'u göster
            fabAddCustom.visibility = View.VISIBLE
            if (allPacks.none { it.category == "custom" }) {
                Toast.makeText(this, "Henüz hiç çıkartma paketin yok. + butonuna tıklayarak oluştur!", Toast.LENGTH_LONG).show()
            }
        }
        
        // fabAddCustom - Hızlı çıkartma oluşturma (Custom görünümündeyken görünen buton)
        fabAddCustom.setOnClickListener {
            startActivity(Intent(this, StickerMakerActivity::class.java))
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }

        // Firebase'den paketleri yükle
        loadPacksFromFirebase()

        // Search functionality
        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentSearchQuery = s?.toString() ?: ""
                applyFilters()
            }
        })

        // Arama yapıldığında (enter tuşu) geçmişe kaydet
        searchBox.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val query = searchBox.text.toString().trim()
                if (query.isNotEmpty()) {
                    PreferencesHelper.addSearchHistory(this, query)
                }
                // Klavyeyi kapat
                val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(searchBox.windowToken, 0)
                true
            } else false
        }

        // Arama kutusuna focus gelince geçmiş aramaları göster
        searchBox.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && searchBox.text.isNullOrEmpty()) {
                showSearchHistory(searchBox)
            }
        }

        searchBox.setOnClickListener {
            if (searchBox.text.isNullOrEmpty()) {
                showSearchHistory(searchBox)
            }
        }

        // Filter button click
        btnFilter.setOnClickListener { showFilterMenu(it) }
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
        lifecycleScope.launch {
            try {
                // Tüm paketleri (Firebase + Özel) tek seferde yükle
                val loadedPacks = StickerRepository.loadPacks(this@MainActivity)
                
                if (loadedPacks.isNotEmpty()) {
                    // Firebase paketlerinin ID'lerini al (cache temizliği için)
                    val firebasePackIds = loadedPacks.filter { it.category != "custom" }.map { it.id }.toSet()

                    // Eski/geçersiz cache'leri temizle (sadece Firebase paketleri için)
                    StickerRepository.cleanupInvalidCache(this@MainActivity, firebasePackIds)

                    // Firebase paketleri için cache'i güncelle
                    loadedPacks.filter { it.category != "custom" }.forEach { pack ->
                        StickerRepository.updatePackCache(this@MainActivity, pack)
                    }

                    allPacks = loadedPacks
                    applyFilters()
                }
                showContent()
            } catch (e: Exception) {
                e.printStackTrace()
                showContent()
            }
        }
    }

    private fun showContent() {
        loadingOverlay.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction {
                loadingOverlay.visibility = View.GONE
                swipeRefresh.visibility = View.VISIBLE
                swipeRefresh.alpha = 0f
                swipeRefresh.animate().alpha(1f).setDuration(200).start()
            }
            .start()
    }



    private fun refreshPacks() {
        swipeRefresh.isRefreshing = true
        loadPacksFromFirebase()
        swipeRefresh.postDelayed({ swipeRefresh.isRefreshing = false }, 1000)
    }

    enum class FilterType { ALL, INSTALLED, PREMIUM, FAVORITES, PURCHASED, CUSTOM }

    private fun applyFilters() {
        var filtered = allPacks
        
        // Önce arama filtresi uygula
        if (currentSearchQuery.isNotEmpty()) {
            val query = currentSearchQuery.lowercase(Locale.getDefault())
            filtered = filtered.filter { 
                it.name.lowercase(Locale.getDefault()).contains(query) || 
                it.pub.lowercase(Locale.getDefault()).contains(query)
            }
        }
        
        // Sonra kategori filtresi uygula
        filtered = when (currentFilter) {
            FilterType.ALL -> filtered.filter { it.category != "custom" } // Özel paketleri genel listeden GİZLE
            FilterType.INSTALLED -> filtered.filter { PreferencesHelper.isPackInstalled(this, it.id) }
            FilterType.PREMIUM -> filtered.filter { it.isPremium }
            FilterType.FAVORITES -> filtered.filter { PreferencesHelper.isPackFavorite(this, it.id) }
            FilterType.PURCHASED -> filtered.filter { PreferencesHelper.hasAccessToPremiumPack(this, it.id) }
            FilterType.CUSTOM -> filtered.filter { it.category == "custom" }
        }
        
        // "Çıkartmalarınız" kısmında geri butonu/ev butonu ve fabAddCustom göster
        if (currentFilter == FilterType.CUSTOM) {
             menuBtn.setImageResource(R.drawable.ic_back)
             // Uygulama adı kalıyor, alt başlık gösteriliyor
             toolbarTitle.text = getString(R.string.app_name)
             toolbarSubtitle.visibility = View.VISIBLE
             btnFilter.visibility = View.INVISIBLE
             fabAddCustom.visibility = View.VISIBLE
             findViewById<View>(R.id.fabMain).visibility = View.GONE
        } else {
             menuBtn.setImageResource(R.drawable.ic_menu)
             toolbarTitle.text = getString(R.string.app_name)
             toolbarSubtitle.visibility = View.GONE
             btnFilter.visibility = View.VISIBLE
             fabAddCustom.visibility = View.GONE
             findViewById<View>(R.id.fabMain).visibility = View.VISIBLE
        }
        
        // Sıralama (yeni paketler önce) ve liste güncelleme
        adapter.updateList(filtered)
        
        // Boş durum mesajı (gerekirse eklenebilir)
    } 
    
    // onBackPressed ile de geri dönmeyi sağla
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.END)) {
            drawer.closeDrawer(GravityCompat.END)
        } else if (currentFilter == FilterType.CUSTOM) {
            currentFilter = FilterType.ALL
            applyFilters()
        } else {
            super.onBackPressed()
        }
    }

    private fun showFilterMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 0, 0, R.string.filter_all)
        popup.menu.add(0, 1, 1, R.string.filter_favorites)
        popup.menu.add(0, 2, 2, R.string.filter_installed)
        popup.menu.add(0, 3, 3, R.string.filter_premium)
        popup.menu.add(0, 4, 4, R.string.filter_purchased)

        popup.setOnMenuItemClickListener { item ->
            currentFilter = when (item.itemId) {
                0 -> FilterType.ALL
                1 -> FilterType.FAVORITES
                2 -> FilterType.INSTALLED
                3 -> FilterType.PREMIUM
                4 -> FilterType.PURCHASED
                else -> FilterType.ALL
            }
            applyFilters()
            updateFilterIcon()
            true
        }

        popup.show()
    }

    private fun updateFilterIcon() {
        // Filtre aktifse ikonu vurgula
        val isFilterActive = currentFilter != FilterType.ALL
        val tintColor = if (isFilterActive) {
            getColor(R.color.accent)
        } else {
            getColor(android.R.color.white)
        }
        btnFilter.setColorFilter(tintColor)
    }

    private fun showSearchHistory(searchBox: EditText) {
        val history = PreferencesHelper.getSearchHistory(this)
        if (history.isEmpty()) return

        val popup = PopupMenu(this, searchBox)

        // Son aramalar başlığı
        popup.menu.add(0, -1, 0, getString(R.string.recent_searches)).isEnabled = false

        // Arama geçmişi
        history.forEachIndexed { index, query ->
            popup.menu.add(0, index, index + 1, query)
        }

        // Geçmişi temizle
        popup.menu.add(0, 999, history.size + 2, getString(R.string.clear_history))

        popup.setOnMenuItemClickListener { item ->
            when {
                item.itemId == 999 -> {
                    PreferencesHelper.clearSearchHistory(this)
                    Toast.makeText(this, "Arama geçmişi temizlendi", Toast.LENGTH_SHORT).show()
                }
                item.itemId >= 0 && item.itemId < history.size -> {
                    searchBox.setText(history[item.itemId])
                    searchBox.setSelection(searchBox.text.length)
                }
            }
            true
        }

        popup.show()
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
        val now = Date()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("tr", "TR"))
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale("tr", "TR"))

        val data = hashMapOf(
            "suggestion" to suggestion,
            "timestamp" to System.currentTimeMillis(),
            "date" to dateFormat.format(now),
            "time" to timeFormat.format(now)
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

    private fun showDeletePackDialog(pack: Pack) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_pack)
            .setMessage(R.string.delete_pack_confirm)
            .setPositiveButton(R.string.yes) { _, _ ->
                if (CustomStickerManager.deletePack(this, pack.id)) {
                    // Yükleme bilgisini temizle
                    PreferencesHelper.removeInstalledPack(this, pack.id)
                    refreshPacks()
                    Toast.makeText(this, "Paket silindi ve WhatsApp zorunlu güncellemeye tetiklendi. WhatsApp'ın yenilemesi birkaç saniye sürebilir.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Paket silinemedi", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.no, null)
            .show()
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
                    // Listeyi güncelle
                    adapter.notifyDataSetChanged()
                    R.string.restore_success
                }
                BillingManager.RestoreResult.NOT_FOUND -> R.string.restore_not_found
                BillingManager.RestoreResult.ERROR -> R.string.restore_error
            }
            Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        billingManager?.destroy()
    }

    companion object {
        private const val REQUEST_DELETE_PACK = 2001
    }

    /**
     * Özel paketi siler - WhatsApp bottom sheet açılır
     */
    private fun deleteCustomPack(pack: Pack) {
        pendingDeletePackId = pack.id
        waitingForWhatsAppReturn = true

        // WhatsApp sticker pack ekranını aç
        val intent = Intent().apply {
            action = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
            putExtra("sticker_pack_id", pack.id)
            putExtra("sticker_pack_authority", "${packageName}.stickers")
            putExtra("sticker_pack_name", pack.name)
        }

        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_DELETE_PACK)
        } catch (e: Exception) {
            pendingDeletePackId = null
            waitingForWhatsAppReturn = false
            Toast.makeText(this, "WhatsApp yüklü değil", Toast.LENGTH_SHORT).show()
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_DELETE_PACK && pendingDeletePackId != null) {
            // WhatsApp'tan döndük - paketi uygulamadan sil
            val packId = pendingDeletePackId!!
            if (CustomStickerManager.deletePack(this, packId)) {
                PreferencesHelper.removeInstalledPack(this, packId)
                Toast.makeText(this, "Çıkartma paketi silindi", Toast.LENGTH_SHORT).show()
                refreshPacks()
            }
            pendingDeletePackId = null
            waitingForWhatsAppReturn = false
        }
    }
}
