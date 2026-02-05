package com.sticly

import android.Manifest
import android.util.Log
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewpager2.widget.ViewPager2
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : AppCompatActivity() {

    private lateinit var drawer: DrawerLayout
    private lateinit var rv: RecyclerView
    private lateinit var loadingOverlay: View
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var mainContent: View
    private lateinit var adapter: PackAdapter
    private lateinit var btnFilter: ImageButton
    private lateinit var menuBtn: ImageButton
    private lateinit var toolbarTitle: TextView
    private lateinit var toolbarSubtitle: TextView

    // Empty State
    private lateinit var emptyStateView: View
    private lateinit var btnCreateFirstSticker: View

    // Header Add Button
    private lateinit var btnAddStickerHeader: ImageButton

    // Bottom Nav
    private lateinit var tabExplore: View
    private lateinit var tabFavorites: View
    private lateinit var tabMyStickers: LinearLayout
    private lateinit var iconExplore: ImageView
    private lateinit var iconFavorites: ImageView
    private lateinit var iconMyStickers: ImageView
    private lateinit var textExplore: TextView
    private lateinit var textFavorites: TextView
    private lateinit var textMyStickers: TextView

    // Regional Popular
    private lateinit var regionalPopularContainer: View
    private lateinit var regionalPopularTitle: TextView
    private lateinit var rvRegional: RecyclerView
    private var regionalAdapter: RegionalAdapter? = null


    // Category Chips
    private lateinit var categoryChipGroup: ChipGroup
    private var currentCategory: String = "all"

    private var allPacks: List<Pack> = emptyList()
    private var billingManager: BillingManager? = null
    private var currentFilter: FilterType = FilterType.ALL
    private var currentSearchQuery: String = ""
    private var pendingDeletePackId: String? = null
    private var wasPackInWhatsAppBeforeDelete = false
    private var waitingForWhatsAppReturn = false

    // Dinamik kategoriler - Firebase'den paketlerdeki kategorilerden oluşturulur
    private var dynamicCategories = mutableListOf<String>()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            PreferencesHelper.setNotificationsEnabled(this, true)
            Toast.makeText(this, R.string.notifications_enabled, Toast.LENGTH_SHORT).show()
        } else {
            PreferencesHelper.setNotificationsEnabled(this, false)
            Toast.makeText(this, R.string.notifications_disabled, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        if (PreferencesHelper.isFirstLaunch(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_main)

        if (!NetworkUtils.isOnline(this)) {
            showNoInternetDialog()
        }

        try {
            billingManager = BillingManager(
                context = this,
                onPurchaseComplete = { isPurchased ->
                    if (isPurchased) {
                        Toast.makeText(this, R.string.premium_purchased, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        FirebaseMessaging.getInstance().subscribeToTopic("stickers")

        initViews()
        setupBottomNav()
        setupCategoryChips()
        setupDrawerMenu()
        setupSearch()

        loadPacksFromFirebase()
        AdManager.loadInterstitial(this)
        StickerRepository.startObservingPacks(this)
        observePacksUpdateFlow()
        checkAndRequestNotificationPermission()
        setupEdgeToEdge()

        // İlk açılışta ve her girişte WhatsApp durumunu doğrula
        checkInstallationUpdates()
    }

    private fun setupEdgeToEdge() {
        val toolbarLayout = findViewById<View>(R.id.toolbarLayout)
        val bottomNav = findViewById<View>(R.id.bottomNav)
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.drawer)) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            toolbarLayout?.setPadding(toolbarLayout.paddingLeft, systemBars.top, toolbarLayout.paddingRight, toolbarLayout.paddingBottom)
            bottomNav?.setPadding(bottomNav.paddingLeft, bottomNav.paddingTop, bottomNav.paddingRight, systemBars.bottom)
            
            insets
        }
    }

    private fun initViews() {
        drawer = findViewById(R.id.drawer)
        rv = findViewById(R.id.rv)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        menuBtn = findViewById(R.id.menuBtn)
        toolbarTitle = findViewById(R.id.toolbarTitle)
        toolbarSubtitle = findViewById(R.id.toolbarSubtitle)
        btnFilter = findViewById(R.id.btnFilter)
        mainContent = findViewById(R.id.mainContent)
        emptyStateView = findViewById(R.id.emptyStateView)
        btnCreateFirstSticker = findViewById(R.id.btnCreateFirstSticker)
        btnAddStickerHeader = findViewById(R.id.btnAddStickerHeader)
        
        btnAddStickerHeader.setOnClickListener {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(this, StickerMakerActivity::class.java), REQUEST_STICKER_MAKER)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }

        categoryChipGroup = findViewById(R.id.categoryChipGroup)
        
        regionalPopularContainer = findViewById(R.id.regionalPopularContainer)
        regionalPopularTitle = findViewById(R.id.regionalPopularTitle)
        rvRegional = findViewById(R.id.rvRegional)
        setupRegionalSection()

        // Bottom Nav
        tabExplore = findViewById(R.id.tabExplore)
        tabFavorites = findViewById(R.id.tabFavorites)
        tabMyStickers = findViewById(R.id.tabMyStickers)
        iconExplore = findViewById(R.id.iconExplore)
        iconFavorites = findViewById(R.id.iconFavorites)
        iconMyStickers = findViewById(R.id.iconMyStickers)
        textExplore = findViewById(R.id.textExplore)
        textFavorites = findViewById(R.id.textFavorites)
        textMyStickers = findViewById(R.id.textMyStickers)

        swipeRefresh.isEnabled = false

        rv.layoutManager = LinearLayoutManager(this)
        rv.setItemViewCacheSize(30)
        rv.setHasFixedSize(true)
        adapter = PackAdapter(allPacks, { pack ->
            startActivity(Intent(this, DetailsActivity::class.java).putExtra("id", pack.id))
        }, {
            if (currentFilter == FilterType.FAVORITES) applyFilters()
        }, { pack ->
            deleteCustomPack(pack)
        })
        rv.adapter = adapter

        menuBtn.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }

        btnFilter.setOnClickListener { showFilterMenu(it) }

        val btnPremiumHeader = findViewById<ImageButton>(R.id.btnPremiumHeader)
        btnPremiumHeader.setOnClickListener {
            startActivity(Intent(this, PremiumActivity::class.java))
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }

        btnCreateFirstSticker.setOnClickListener {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(this, StickerMakerActivity::class.java), REQUEST_STICKER_MAKER)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }
    }

    private fun setupBottomNav() {
        tabExplore.setOnClickListener {
            currentFilter = FilterType.ALL
            currentCategory = "all"
            applyFilters()
            updateBottomNavUI()
            updateCategoryChipSelection()
            categoryChipGroup.visibility = View.VISIBLE
            regionalPopularContainer.visibility = if (regionalAdapter?.itemCount ?: 0 > 0) View.VISIBLE else View.GONE
        }

        tabFavorites.setOnClickListener {
            currentFilter = FilterType.FAVORITES
            applyFilters()
            updateBottomNavUI()
            categoryChipGroup.visibility = View.GONE
            regionalPopularContainer.visibility = View.GONE
        }

        tabMyStickers.setOnClickListener {
            currentFilter = FilterType.CUSTOM
            applyFilters()
            updateBottomNavUI()
            categoryChipGroup.visibility = View.GONE
            regionalPopularContainer.visibility = View.GONE
        }

        updateBottomNavUI()
    }

    private fun updateBottomNavUI() {
        val activeColor = ContextCompat.getColor(this, R.color.bottom_nav_active)
        val inactiveColor = ContextCompat.getColor(this, R.color.bottom_nav_inactive)

        // Reset all
        iconExplore.setColorFilter(inactiveColor)
        textExplore.setTextColor(inactiveColor)
        
        iconFavorites.setColorFilter(inactiveColor)
        textFavorites.setTextColor(inactiveColor)

        iconMyStickers.setColorFilter(inactiveColor)
        textMyStickers.setTextColor(inactiveColor)

        // Activate selected
        when (currentFilter) {
            FilterType.ALL, FilterType.INSTALLED, FilterType.PREMIUM, FilterType.PURCHASED -> {
                iconExplore.setColorFilter(activeColor)
                textExplore.setTextColor(activeColor)
                
                menuBtn.setImageResource(R.drawable.ic_menu)
                toolbarSubtitle.visibility = View.GONE
                btnFilter.visibility = View.VISIBLE
                categoryChipGroup.visibility = View.VISIBLE
                regionalPopularContainer.visibility = if (regionalAdapter?.itemCount ?: 0 > 0) View.VISIBLE else View.GONE
            }
            FilterType.FAVORITES -> {
                iconFavorites.setColorFilter(activeColor)
                textFavorites.setTextColor(activeColor)
                
                menuBtn.setImageResource(R.drawable.ic_menu)
                toolbarSubtitle.visibility = View.VISIBLE
                toolbarSubtitle.text = getString(R.string.filter_favorites)
                btnFilter.visibility = View.GONE
                categoryChipGroup.visibility = View.GONE
                regionalPopularContainer.visibility = View.GONE
            }
            FilterType.CUSTOM -> {
                iconMyStickers.setColorFilter(activeColor)
                textMyStickers.setTextColor(activeColor)
                
                menuBtn.setImageResource(R.drawable.ic_menu)
                toolbarSubtitle.visibility = View.VISIBLE
                toolbarSubtitle.text = getString(R.string.your_stickers)
                btnFilter.visibility = View.GONE
                categoryChipGroup.visibility = View.GONE
                regionalPopularContainer.visibility = View.GONE
                btnAddStickerHeader.visibility = View.VISIBLE
            }
        }
        
        if (currentFilter != FilterType.CUSTOM) {
            btnAddStickerHeader.visibility = View.GONE
        }
    }



    private fun setupRegionalSection() {
        regionalAdapter = RegionalAdapter(
            pages = emptyList(),
            onClick = { pack ->
                startActivity(Intent(this, DetailsActivity::class.java).putExtra("id", pack.id))
            },
            onAddClick = { pack ->
                // Detay sayfasına gönder veya direkt ekle (Sticker.ly tarzı detay daha mantıklı)
                startActivity(Intent(this, DetailsActivity::class.java).putExtra("id", pack.id))
            }
        )
        rvRegional.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvRegional.adapter = regionalAdapter
    }

    private fun updateRegionalPacks(packs: List<Pack>) {
        val locale = Locale.getDefault()
        var country = getString(R.string.category_all) // Fallback
        
        try {
            country = locale.getDisplayCountry(Locale("tr"))
            if (country.isEmpty()) country = locale.displayCountry
            if (country.isEmpty()) country = "Türkiye" // Hard fallback for typical users
        } catch (e: Exception) {}

        regionalPopularTitle.text = "🏆 $country bölgesindeki en popülerler"

        // En popüler 10 paketi al (özel paketler hariç)
        val regionalTopPacks = packs
            .filter { it.isActive && it.category != "custom" }
            .sortedByDescending { it.downloadCount }
            .take(10)

        if (regionalTopPacks.isEmpty()) {
            regionalPopularContainer.visibility = View.GONE
            return
        }

        if (currentFilter == FilterType.ALL && currentCategory == "all") {
            regionalPopularContainer.visibility = View.VISIBLE
        } else {
            regionalPopularContainer.visibility = View.GONE
        }
        
        // Paketleri ikili grupla
        val pages = mutableListOf<Pair<Pack, Pack?>>()
        for (i in regionalTopPacks.indices step 2) {
            val top = regionalTopPacks[i]
            val bottom = if (i + 1 < regionalTopPacks.size) regionalTopPacks[i + 1] else null
            pages.add(top to bottom)
        }
        
        regionalAdapter?.updateData(pages)
    }




    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun getCategoryStringRes(key: String): Int {
        return when (key) {
            "all" -> R.string.category_all
            "humor" -> R.string.category_humor
            "love" -> R.string.category_love
            "religious" -> R.string.category_religious
            "entertainment" -> R.string.category_entertainment
            "background" -> R.string.category_background
            "morning" -> R.string.category_morning
            "night" -> R.string.category_night
            "birthday" -> R.string.category_birthday
            "congrats" -> R.string.category_congrats
            "animals" -> R.string.category_animals
            "sports" -> R.string.category_sports
            "gaming" -> R.string.category_gaming
            "movie" -> R.string.category_movie
            "music" -> R.string.category_music
            "food" -> R.string.category_food
            "emoji" -> R.string.category_emoji
            "cars" -> R.string.category_cars
            "motivation" -> R.string.category_motivation
            "cute" -> R.string.category_cute
            "text" -> R.string.category_text
            "anime" -> R.string.category_anime
            "memes" -> R.string.category_memes
            "nature" -> R.string.category_nature
            "other" -> R.string.category_other
            else -> 0
        }
    }

    private fun normalizeCategoryKey(key: String): String {
        val k = key.lowercase(Locale.ROOT).trim()
        return when {
            k == "all" || k == "tümü" || k == "todo" || k.contains("tüm kategoriler") || k.contains("all categories") -> "all"
            k.contains("mizah") || k.contains("komik") || k.contains("humor") || k.contains("funny") -> "humor"
            k.contains("aşk") || k.contains("love") || k.contains("amor") -> "love"
            k.contains("dini") || k.contains("religious") -> "religious"
            k.contains("eğlence") || k.contains("entertainment") -> "entertainment"
            k.contains("arka plan") || k.contains("background") -> "background"
            k.contains("günaydın") || k.contains("morning") -> "morning"
            k.contains("iyi geceler") || k.contains("night") -> "night"
            k.contains("doğum günü") || k.contains("birthday") -> "birthday"
            k.contains("tebrik") || k.contains("congrats") -> "congrats"
            k.contains("hayvan") || k.contains("animal") -> "animals"
            k.contains("spor") || k.contains("sport") -> "sports"
            k.contains("oyun") || k.contains("gaming") -> "gaming"
            k.contains("film") || k.contains("dizi") || k.contains("movie") || k.contains("series") -> "movie"
            k.contains("müzik") || k.contains("music") -> "music"
            k.contains("yemek") || k.contains("food") -> "food"
            k.contains("emoji") -> "emoji"
            k.contains("araba") || k.contains("car") -> "cars"
            k.contains("motivasyon") || k.contains("motivation") -> "motivation"
            k.contains("sevimli") || k.contains("cute") -> "cute"
            k.contains("metin") || k.contains("yazı") || k.contains("text") -> "text"
            k.contains("anime") -> "anime"
            k.contains("meme") -> "memes"
            k.contains("doğa") || k.contains("nature") -> "nature"
            else -> {
                // Eğer key zaten bir id ise (örn: "music"), onu döndür
                if (getCategoryStringRes(k) != 0) k else k.replace(" ", "_")
            }
        }
    }

    private fun setupCategoryChips() {
        if (!::categoryChipGroup.isInitialized) return
        categoryChipGroup.removeAllViews()

        // Firebase'den gelen paketlerdeki benzersiz kategorileri al
        dynamicCategories.clear()
        dynamicCategories.add("all") // "Tümü" her zaman ilk sırada

        val uniqueCategories = allPacks
            .filter { it.isActive && it.category.isNotBlank() && it.category != "custom" }
            .map { normalizeCategoryKey(it.category) }
            .distinct()
            .sorted()

        dynamicCategories.addAll(uniqueCategories)

        dynamicCategories.forEach { categoryKey ->
            val chip = Chip(this).apply {
                val resId = getCategoryStringRes(categoryKey)
                if (resId != 0) {
                    text = getString(resId)
                } else {
                    // getCategoryStringRes bulamadıysa getIdentifier'ı dene
                    val dynResId = resources.getIdentifier("category_$categoryKey", "string", packageName)
                    text = if (dynResId != 0) getString(dynResId) else {
                        // Son çare: kelimeyi capitalize et ve varsa emoji ekle (çok kaba bir fallback)
                        categoryKey.replace("_", " ").split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
                    }
                }
                isCheckable = true
                isChecked = categoryKey == currentCategory
                tag = categoryKey
                
                // Emoji desteği ve görsel için padding/margin ayarları (isteğe bağlı)
                chipStartPadding = 8.dpToPx().toFloat()
                chipEndPadding = 8.dpToPx().toFloat()
                
                setChipBackgroundColorResource(if (categoryKey == currentCategory) R.color.accent else R.color.chip_bg)
                setTextColor(getColor(if (categoryKey == currentCategory) R.color.white else R.color.text_primary))
                chipStrokeWidth = 0f
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        currentCategory = categoryKey
                        setChipBackgroundColorResource(R.color.accent)
                        setTextColor(getColor(R.color.white))
                        applyFilters()
                    } else {
                        setChipBackgroundColorResource(R.color.chip_bg)
                        setTextColor(getColor(R.color.text_primary))
                    }
                }
            }
            categoryChipGroup.addView(chip)
        }
    }

    private fun updateCategoryChipSelection() {
        for (i in 0 until categoryChipGroup.childCount) {
            val chip = categoryChipGroup.getChildAt(i) as? Chip
            chip?.isChecked = chip?.tag == currentCategory
        }
    }

    private fun setupDrawerMenu() {
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

        navFaq.setOnClickListener { drawer.closeDrawers(); showFaqDialog() }
        navAbout.setOnClickListener { drawer.closeDrawers(); showAboutDialog() }
        navPremium.setOnClickListener {
            drawer.closeDrawers()
            startActivity(Intent(this, PremiumActivity::class.java))
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }
        navContact.setOnClickListener {
            drawer.closeDrawers()
            startActivity(Intent(this, ContactActivity::class.java))
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }
        navRate.setOnClickListener { drawer.closeDrawers(); openPlayStore() }
        navSuggest.setOnClickListener {
            drawer.closeDrawers()
            startActivity(Intent(this, SuggestActivity::class.java))
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }
        navShare.setOnClickListener { drawer.closeDrawers(); shareApp() }
        navNotifications.setOnClickListener { drawer.closeDrawers(); showNotificationSettings() }
        navPrivacy.setOnClickListener { drawer.closeDrawers(); showPrivacyDialog() }
        navRestorePurchases.setOnClickListener { drawer.closeDrawers(); restorePurchases() }
        navRestorePurchases.setOnClickListener { drawer.closeDrawers(); restorePurchases() }
    }

    private fun setupSearch() {
        val searchBox = findViewById<EditText>(R.id.searchBox)

        var searchJob: Job? = null
        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(300) // Debounce
                    currentSearchQuery = s?.toString() ?: ""
                    applyFilters()
                }
            }
        })

        searchBox.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val query = searchBox.text.toString().trim()
                if (query.isNotEmpty()) {
                    PreferencesHelper.addSearchHistory(this, query)
                }
                val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(searchBox.windowToken, 0)
                true
            } else false
        }

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

        searchBox.clearFocus()
    }

    /**
     * WhatsApp'tan silinen ama bizde yüklü görünen paketleri temizle
     */
    private fun checkInstallationUpdates() {
        lifecycleScope.launch(Dispatchers.IO) {
            // WhatsApp'ın ContentProvider'ının hazır olması için bazen küçük bir bekleme gerekebilir
            delay(500)
            
            val installedPackIds = PreferencesHelper.getInstalledPacks(this@MainActivity)
            if (installedPackIds.isEmpty()) return@launch

            var changed = false
            val currentList = installedPackIds.toMutableSet()
            
            installedPackIds.forEach { packId ->
                val stillInWhatsApp = WhitelistCheck.isWhitelisted(this@MainActivity, packId)
                if (!stillInWhatsApp) {
                    PreferencesHelper.removeInstalledPack(this@MainActivity, packId)
                    changed = true
                }
            }

            if (changed) {
                withContext(Dispatchers.Main) {
                    if (::adapter.isInitialized) {
                        adapter.notifyDataSetChanged()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        
        // Sticker Maker için reklamı önceden yükle (Anında gelmesi için)
        AdManager.preloadMakerNativeAd(this)

        // WhatsApp durumunu güncelle
        checkInstallationUpdates()

        if (::adapter.isInitialized) {
            lifecycleScope.launch {
                val customPacks = CustomStickerManager.getCustomPacks(this@MainActivity).mapNotNull { cp ->
                    val pack = CustomStickerManager.toWhatsAppPack(this@MainActivity, cp.id)?.copy(category = "custom")
                    if (pack != null && pack.stickers.isNotEmpty()) pack else null
                }
                val firebasePacks = allPacks.filter { it.category != "custom" }
                allPacks = firebasePacks + customPacks
                applyFilters()
            }
        }
    }

    override fun onPause() {
        super.onPause()
    }

    private fun showNoInternetDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.no_internet_title)
            .setMessage("Internet connection not found. Some features may not work.\n\nLocal sticker packs can be used.")
            .setCancelable(true)
            .setPositiveButton(R.string.retry) { _, _ ->
                if (NetworkUtils.isOnline(this)) {
                    recreate()
                } else {
                    Toast.makeText(this, "Still no internet connection", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.ok) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun loadPacksFromFirebase(forceRefresh: Boolean = false) {
        lifecycleScope.launch {
            try {
                val loadedPacks = StickerRepository.loadPacks(this@MainActivity, forceRefresh)

                if (loadedPacks.isNotEmpty()) {
                    val firebasePackIds = loadedPacks.filter { it.category != "custom" }.map { it.id }.toSet()
                    
                    // KRITIK: Dosya işlemlerini IO thread'ine taşı (Donmayı önler)
                    withContext(Dispatchers.IO) {
                        StickerRepository.cleanupInvalidCache(this@MainActivity, firebasePackIds)
                        loadedPacks.filter { it.category != "custom" }.forEach { pack ->
                            StickerRepository.updatePackCache(this@MainActivity, pack)
                        }
                    }

                    allPacks = loadedPacks
                    setupCategoryChips() // Kategorileri güncelle
                    applyFilters()
                    updateRegionalPacks(loadedPacks)
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
                mainContent.visibility = View.VISIBLE
                mainContent.alpha = 0f
                mainContent.animate().alpha(1f).setDuration(200).start()
                
                swipeRefresh.visibility = View.VISIBLE
            }
            .start()
    }

    private fun observePacksUpdateFlow() {
        lifecycleScope.launch {
            StickerRepository.packsUpdateFlow.collect { updatedPacks ->
                Log.d("MainActivity", "Real-time update received: ${updatedPacks.size} packs")
                allPacks = updatedPacks
                setupCategoryChips() // Kategorileri güncelle
                applyFilters()
                updateRegionalPacks(updatedPacks)
            }
        }
    }

    private fun refreshPacks() {
        loadPacksFromFirebase(forceRefresh = true)
    }

    enum class FilterType { ALL, INSTALLED, PREMIUM, FAVORITES, PURCHASED, CUSTOM }

    private fun applyFilters() {
        lifecycleScope.launch(Dispatchers.Default) {
            var filtered = allPacks

            if (currentSearchQuery.isNotEmpty()) {
                val query = currentSearchQuery.lowercase(Locale.getDefault())
                filtered = filtered.filter {
                    it.localizedName.lowercase(Locale.getDefault()).contains(query) ||
                    it.pub.lowercase(Locale.getDefault()).contains(query)
                }
            }

            if (currentFilter != FilterType.INSTALLED && currentFilter != FilterType.PURCHASED) {
                filtered = filtered.filter { it.isActive }
            }

            filtered = when (currentFilter) {
                FilterType.ALL -> {
                    var result = filtered.filter { it.category != "custom" && !it.id.startsWith("custom_") }
                    if (currentCategory != "all") {
                        result = result.filter { normalizeCategoryKey(it.category) == currentCategory }
                    }
                    result
                }
                FilterType.INSTALLED -> filtered.filter { PreferencesHelper.isPackInstalled(this@MainActivity, it.id) }
                FilterType.PREMIUM -> filtered.filter { it.isPremium }
                FilterType.FAVORITES -> filtered.filter { PreferencesHelper.isPackFavorite(this@MainActivity, it.id) }
                FilterType.PURCHASED -> filtered.filter { PreferencesHelper.hasAccessToPremiumPack(this@MainActivity, it.id) }
                FilterType.CUSTOM -> filtered.filter { (it.category == "custom" || it.id.startsWith("custom_")) && it.stickers.isNotEmpty() }
            }

            // Profesyonel Sıralama Algoritması
            val currentTime = System.currentTimeMillis()
            fun rankScore(pack: Pack): Double {
                val downloads = pack.downloadCount.toDouble()
                val views = pack.viewCount.toDouble()
                val favorites = pack.favoriteCount.toDouble()
                val cvr = if (views > 0) downloads / views else 0.0
                val engagementScore = downloads + (favorites * 5.0)
                var freshnessMultiplier = 1.0
                if (pack.createdAt.isNotEmpty()) {
                    try {
                        val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                        val createdDate = format.parse(pack.createdAt)
                        if (createdDate != null) {
                            val diffDays = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(currentTime - createdDate.time)
                            if (diffDays <= 7) freshnessMultiplier = 3.5
                        }
                    } catch (e: Exception) {}
                }
                return (engagementScore * (1.0 + cvr)) * freshnessMultiplier
            }

            // Premium paketleri ayır ve her 2-4 pakette bir araya serpiştir
            val sorted = if (currentFilter == FilterType.ALL) {
                val freePacks = filtered.filter { !it.isPremium }.sortedByDescending { rankScore(it) }
                val premiumPacks = filtered.filter { it.isPremium }.sortedByDescending { rankScore(it) }.toMutableList()
                premiumPacks.shuffle() // Her yenilemede farklı sıra

                val merged = mutableListOf<Pack>()
                var freeIndex = 0
                var premiumIndex = 0
                var nextPremiumGap = (2..4).random()
                var sinceLastPremium = 0

                while (freeIndex < freePacks.size || premiumIndex < premiumPacks.size) {
                    if (premiumIndex < premiumPacks.size && sinceLastPremium >= nextPremiumGap) {
                        merged.add(premiumPacks[premiumIndex++])
                        nextPremiumGap = (2..4).random()
                        sinceLastPremium = 0
                    } else if (freeIndex < freePacks.size) {
                        merged.add(freePacks[freeIndex++])
                        sinceLastPremium++
                    } else {
                        // Kalan premium paketleri ekle
                        merged.add(premiumPacks[premiumIndex++])
                    }
                }
                merged
            } else {
                filtered.sortedByDescending { rankScore(it) }
            }

            // Reklamları listeye enjekte et
            val itemsWithAds = mutableListOf<Any>()
            val isPremium = PreferencesHelper.isPremium(this@MainActivity)
            
            if (sorted.isNotEmpty() && !isPremium) {
                if (currentFilter == FilterType.FAVORITES) {
                    if (sorted.size >= 2) {
                        sorted.forEachIndexed { index, pack ->
                            itemsWithAds.add(pack)
                            if (index == 1) itemsWithAds.add("AD_FAVORITE_PLACEHOLDER")
                        }
                    } else itemsWithAds.addAll(sorted)
                } else if (currentFilter == FilterType.CUSTOM) {
                    if (sorted.isNotEmpty()) {
                        sorted.forEachIndexed { index, pack ->
                            itemsWithAds.add(pack)
                            if (index == 0) itemsWithAds.add("AD_MY_STICKERS_PLACEHOLDER")
                        }
                    } else itemsWithAds.addAll(sorted)
                } else {
                    var nextAdGap = (3..6).random()
                    var itemsSinceLastAd = 0
                    sorted.forEach { pack ->
                        itemsWithAds.add(pack)
                        itemsSinceLastAd++
                        if (itemsSinceLastAd >= nextAdGap) {
                            itemsWithAds.add("AD_LIST_PLACEHOLDER")
                            nextAdGap = (3..6).random()
                            itemsSinceLastAd = 0
                        }
                    }
                }
            } else {
                itemsWithAds.addAll(sorted)
            }

            withContext(Dispatchers.Main) {
                if (sorted.isEmpty()) {
                    if (currentFilter == FilterType.CUSTOM) {
                        rv.visibility = View.GONE
                        emptyStateView.visibility = View.VISIBLE
                        findViewById<TextView>(R.id.emptyStateText).setText(R.string.no_custom_packs)
                        btnCreateFirstSticker.visibility = View.VISIBLE
                    } else if (currentFilter == FilterType.FAVORITES) {
                        rv.visibility = View.GONE
                        emptyStateView.visibility = View.VISIBLE
                        findViewById<TextView>(R.id.emptyStateText).setText(R.string.no_favorites_yet)
                        btnCreateFirstSticker.visibility = View.GONE
                    } else if (currentFilter == FilterType.INSTALLED) {
                        // Installed but empty?
                        rv.visibility = View.VISIBLE
                        emptyStateView.visibility = View.GONE
                        adapter.updateList(itemsWithAds)
                    } else {
                         // Default empty handling
                        rv.visibility = View.VISIBLE
                        emptyStateView.visibility = View.GONE
                        adapter.updateList(itemsWithAds)
                    }
                } else {
                    rv.visibility = View.VISIBLE
                    emptyStateView.visibility = View.GONE
                    adapter.updateList(itemsWithAds)
                }

                // Popüler bölümün görünürlüğünü güncelle
                if (currentFilter == FilterType.ALL && currentCategory == "all" && (regionalAdapter?.itemCount ?: 0) > 0) {
                    regionalPopularContainer.visibility = View.VISIBLE
                } else {
                    regionalPopularContainer.visibility = View.GONE
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.END)) {
            drawer.closeDrawer(GravityCompat.END)
        } else if (currentFilter == FilterType.CUSTOM || currentFilter == FilterType.FAVORITES) {
            currentFilter = FilterType.ALL
            applyFilters()
            updateBottomNavUI()
            categoryChipGroup.visibility = View.VISIBLE
            updateCategoryChipSelection()
        } else {
            super.onBackPressed()
        }
    }

    private fun showFilterMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, R.string.filter_all)
        popup.menu.add(0, 2, 1, R.string.filter_installed)
        popup.menu.add(0, 3, 2, R.string.filter_premium)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    currentFilter = FilterType.ALL
                    currentCategory = "all"
                    updateCategoryChipSelection()
                }
                2 -> currentFilter = FilterType.INSTALLED
                3 -> currentFilter = FilterType.PREMIUM
            }
            applyFilters()
            updateFilterIcon()
            updateBottomNavUI()
            true
        }

        popup.show()
    }

    private fun updateFilterIcon() {
        val isFilterActive = currentFilter != FilterType.ALL
        val tintColor = if (isFilterActive) getColor(R.color.accent) else getColor(R.color.text_secondary)
        btnFilter.setColorFilter(tintColor)
    }

    private fun showSearchHistory(searchBox: EditText) {
        val history = PreferencesHelper.getSearchHistory(this)
        if (history.isEmpty()) return

        val popup = PopupMenu(this, searchBox)
        popup.menu.add(0, -1, 0, getString(R.string.recent_searches)).isEnabled = false
        history.forEachIndexed { index, query -> popup.menu.add(0, index, index + 1, query) }
        popup.menu.add(0, 999, history.size + 2, getString(R.string.clear_history))

        popup.setOnMenuItemClickListener { item ->
            when {
                item.itemId == 999 -> {
                    PreferencesHelper.clearSearchHistory(this)
                    Toast.makeText(this, "Search history cleared", Toast.LENGTH_SHORT).show()
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

    private fun showFaqDialog() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://sticky-privacy-legal.web.app/#faq")))
    }

    private fun showAboutDialog() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://sticky-privacy-legal.web.app/#about")))
    }

    private fun openPlayStore() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
        } catch (e: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
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

        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true

        switchNotifications.isChecked = PreferencesHelper.isNotificationsEnabled(this) && hasPermission

        AlertDialog.Builder(this)
            .setTitle(R.string.notifications_title)
            .setView(view)
            .setPositiveButton(R.string.ok) { _, _ ->
                if (switchNotifications.isChecked) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                            PreferencesHelper.setNotificationsEnabled(this, true)
                            Toast.makeText(this, R.string.notifications_enabled, Toast.LENGTH_SHORT).show()
                        } else {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    } else {
                        PreferencesHelper.setNotificationsEnabled(this, true)
                        Toast.makeText(this, R.string.notifications_enabled, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    PreferencesHelper.setNotificationsEnabled(this, false)
                    Toast.makeText(this, R.string.notifications_disabled, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (PreferencesHelper.wasNotificationPermissionAsked(this)) return
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                PreferencesHelper.setNotificationPermissionAsked(this)
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        PreferencesHelper.setFirstLaunchComplete(this)
    }

    private fun showPrivacyDialog() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://sticky-privacy-legal.web.app/#privacy")))
    }

    private fun showLanguageDialog() {
        val languages = arrayOf("English", "Türkçe", "简体中文", "Español", "\u200Eالعربية", "हिन्दी", "Português")
        val codes = arrayOf("en", "tr", "zh", "es", "ar", "hi", "pt")
        val currentLang = PreferencesHelper.getLanguage(this)
        val selectedIndex = codes.indexOf(currentLang).takeIf { it >= 0 } ?: 0

        AlertDialog.Builder(this)
            .setTitle(R.string.select_language)
            .setSingleChoiceItems(languages, selectedIndex) { dialog, which ->
                val selectedCode = codes[which]
                if (selectedCode != currentLang) {
                    PreferencesHelper.setLanguage(this, selectedCode)
                    recreate()
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
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
                    loadPacksFromFirebase(forceRefresh = true)
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
        StickerRepository.stopObservingPacks()
        billingManager?.destroy()
    }

    companion object {
        private const val REQUEST_DELETE_PACK = 2001
        private const val REQUEST_STICKER_MAKER = 2002
    }

    private fun deleteCustomPack(pack: Pack) {
        lifecycleScope.launch {
            val isWhitelisted = withContext(Dispatchers.IO) {
                WhitelistCheck.isWhitelisted(this@MainActivity, pack.id)
            }

            if (isWhitelisted) {
                showDeleteOptionsDialog(pack)
            } else {
                confirmAndDirectDelete(pack)
            }
        }
    }

    private fun showDeleteOptionsDialog(pack: Pack) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.TransparentBottomSheetDialog)
        val view = layoutInflater.inflate(R.layout.dialog_delete_options, null)
        dialog.setContentView(view)

        view.findViewById<TextView>(R.id.tvTitle).text = pack.localizedName
        
        view.findViewById<View>(R.id.cardDeleteLocal).setOnClickListener {
            dialog.dismiss()
            confirmAndDirectDelete(pack)
        }
        
        view.findViewById<View>(R.id.cardDeleteWhatsApp).setOnClickListener {
            dialog.dismiss()
            triggerWhatsAppRemove(pack)
        }
        
        view.findViewById<Button>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun triggerWhatsAppRemove(pack: Pack) {
        pendingDeletePackId = pack.id
        waitingForWhatsAppReturn = true
        
        lifecycleScope.launch {
            wasPackInWhatsAppBeforeDelete = withContext(Dispatchers.IO) {
                WhitelistCheck.isWhitelisted(this@MainActivity, pack.id)
            }

            if (!wasPackInWhatsAppBeforeDelete) {
                Toast.makeText(this@MainActivity, "Paket WhatsApp'ta ekli değil. Sadece uygulamadan siliniyor.", Toast.LENGTH_SHORT).show()
                confirmAndDirectDelete(pack)
                return@launch
            }

            val intent = Intent().apply {
                action = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
                putExtra("sticker_pack_id", pack.id)
                putExtra("sticker_pack_authority", "${packageName}.stickers")
                putExtra("sticker_pack_name", pack.localizedName)
            }

            try {
                @Suppress("DEPRECATION")
                startActivityForResult(intent, REQUEST_DELETE_PACK)
            } catch (e: Exception) {
                pendingDeletePackId = null
                waitingForWhatsAppReturn = false
                Toast.makeText(this@MainActivity, "WhatsApp yüklü değil", Toast.LENGTH_SHORT).show()
                confirmAndDirectDelete(pack)
            }
        }
    }

    private fun confirmAndDirectDelete(pack: Pack) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_pack)
            .setMessage(R.string.delete_pack_confirm)
            .setPositiveButton(R.string.yes) { _, _ ->
                if (CustomStickerManager.deletePack(this, pack.id)) {
                    PreferencesHelper.removeInstalledPack(this, pack.id)
                    Toast.makeText(this, "Paket başarıyla silindi", Toast.LENGTH_SHORT).show()
                    refreshPacks()
                }
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_DELETE_PACK) {
            val packId = pendingDeletePackId ?: return
            val wasInWhatsApp = wasPackInWhatsAppBeforeDelete
            pendingDeletePackId = null
            waitingForWhatsAppReturn = false
            wasPackInWhatsAppBeforeDelete = false

            lifecycleScope.launch {
                val isStillInWhatsApp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    WhitelistCheck.isWhitelisted(this@MainActivity, packId)
                }

                if (wasInWhatsApp && !isStillInWhatsApp) {
                    if (CustomStickerManager.deletePack(this@MainActivity, packId)) {
                        PreferencesHelper.removeInstalledPack(this@MainActivity, packId)
                        Toast.makeText(this@MainActivity, "Sticker pack deleted", Toast.LENGTH_SHORT).show()
                        refreshPacks()
                    }
                }
            }
        }

        // Handle sticker maker result - refresh custom packs immediately and switch to My Stickers
        if (requestCode == REQUEST_STICKER_MAKER && resultCode == RESULT_OK) {
            lifecycleScope.launch {
                val customPacks = CustomStickerManager.getCustomPacks(this@MainActivity).mapNotNull { cp ->
                    CustomStickerManager.toWhatsAppPack(this@MainActivity, cp.id)?.copy(category = "custom")
                }
                val firebasePacks = allPacks.filter { it.category != "custom" }
                allPacks = firebasePacks + customPacks

                // Switch to My Stickers tab to show the newly added sticker
                currentFilter = FilterType.CUSTOM
                categoryChipGroup.visibility = View.GONE
                regionalPopularContainer.visibility = View.GONE
                updateBottomNavUI()
                applyFilters()
            }
        }
    }
}
