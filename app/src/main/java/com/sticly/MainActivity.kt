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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
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
    private lateinit var adapter: PackAdapter
    private lateinit var btnFilter: ImageButton
    private lateinit var menuBtn: ImageButton
    private lateinit var toolbarTitle: TextView
    private lateinit var toolbarSubtitle: TextView

    // Bottom Nav
    private lateinit var tabExplore: View
    private lateinit var tabFavorites: View
    private lateinit var tabCreate: View
    private lateinit var tabMyStickers: View
    private lateinit var iconExplore: ImageView
    private lateinit var iconFavorites: ImageView
    private lateinit var iconCreate: ImageView
    private lateinit var iconMyStickers: ImageView
    private lateinit var textExplore: TextView
    private lateinit var textFavorites: TextView
    private lateinit var textCreate: TextView
    private lateinit var textMyStickers: TextView

    // Carousel
    private lateinit var topStickersCarousel: ViewPager2
    private lateinit var carouselIndicator: LinearLayout
    private lateinit var carouselContainer: View
    private var carouselAdapter: TopStickerAdapter? = null
    private var carouselHandler: Handler? = null
    private var carouselRunnable: Runnable? = null

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
        applyTheme()
        super.onCreate(s)
        setContentView(R.layout.activity_main)

        if (!NetworkUtils.isOnline(this)) {
            showNoInternetDialog()
        }

        try {
            billingManager = BillingManager(this) { isPurchased ->
                if (isPurchased) {
                    Toast.makeText(this, R.string.premium_purchased, Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        FirebaseMessaging.getInstance().subscribeToTopic("stickers")

        initViews()
        setupBottomNav()
        setupCarousel()
        setupCategoryChips()
        setupDrawerMenu()
        setupSearch()

        loadPacksFromFirebase()
        StickerRepository.startObservingPacks(this)
        observePacksUpdateFlow()
        checkAndRequestNotificationPermission()
        setupEdgeToEdge()
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

        topStickersCarousel = findViewById(R.id.topStickersCarousel)
        carouselIndicator = findViewById(R.id.carouselIndicator)
        carouselContainer = findViewById(R.id.carouselContainer)
        categoryChipGroup = findViewById(R.id.categoryChipGroup)

        // Bottom Nav
        tabExplore = findViewById(R.id.tabExplore)
        tabFavorites = findViewById(R.id.tabFavorites)
        tabCreate = findViewById(R.id.tabCreate)
        tabMyStickers = findViewById(R.id.tabMyStickers)
        iconExplore = findViewById(R.id.iconExplore)
        iconFavorites = findViewById(R.id.iconFavorites)
        iconCreate = findViewById(R.id.iconCreate)
        iconMyStickers = findViewById(R.id.iconMyStickers)
        textExplore = findViewById(R.id.textExplore)
        textFavorites = findViewById(R.id.textFavorites)
        textCreate = findViewById(R.id.textCreate)
        textMyStickers = findViewById(R.id.textMyStickers)

        swipeRefresh.setColorSchemeResources(R.color.accent)
        swipeRefresh.setOnRefreshListener { refreshPacks() }

        rv.layoutManager = LinearLayoutManager(this)
        adapter = PackAdapter(allPacks, { pack ->
            startActivity(Intent(this, DetailsActivity::class.java).putExtra("id", pack.id))
        }, {
            if (currentFilter == FilterType.FAVORITES) applyFilters()
        }, { pack ->
            deleteCustomPack(pack)
        })
        rv.adapter = adapter

        menuBtn.setOnClickListener {
            if (currentFilter == FilterType.CUSTOM || currentFilter == FilterType.FAVORITES) {
                currentFilter = FilterType.ALL
                applyFilters()
                updateBottomNavUI()
            } else {
                drawer.openDrawer(GravityCompat.END)
            }
        }

        val btnTheme = findViewById<ImageButton>(R.id.btnTheme)
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        updateThemeIcon(btnTheme, prefs.getInt("theme", 0))
        btnTheme.setOnClickListener { showThemeMenu(it, btnTheme) }

        btnFilter.setOnClickListener { showFilterMenu(it) }
    }

    private fun setupBottomNav() {
        tabExplore.setOnClickListener {
            currentFilter = FilterType.ALL
            currentCategory = "all"
            applyFilters()
            updateBottomNavUI()
            updateCategoryChipSelection()
            carouselContainer.visibility = View.VISIBLE
        }

        tabFavorites.setOnClickListener {
            currentFilter = FilterType.FAVORITES
            applyFilters()
            updateBottomNavUI()
            carouselContainer.visibility = View.GONE
        }

        tabCreate.setOnClickListener {
            startActivity(Intent(this, StickerMakerActivity::class.java))
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }

        tabMyStickers.setOnClickListener {
            currentFilter = FilterType.CUSTOM
            applyFilters()
            updateBottomNavUI()
            carouselContainer.visibility = View.GONE
            if (allPacks.none { it.category == "custom" }) {
                Toast.makeText(this, R.string.no_custom_packs, Toast.LENGTH_SHORT).show()
            }
        }

        updateBottomNavUI()
    }

    private fun updateBottomNavUI() {
        val activeColor = ContextCompat.getColor(this, R.color.bottom_nav_active)
        val inactiveColor = ContextCompat.getColor(this, R.color.bottom_nav_inactive)

        // Reset all
        iconExplore.setColorFilter(inactiveColor)
        textExplore.setTextColor(inactiveColor)
        textExplore.setTypeface(null, android.graphics.Typeface.NORMAL)

        iconFavorites.setColorFilter(inactiveColor)
        textFavorites.setTextColor(inactiveColor)
        textFavorites.setTypeface(null, android.graphics.Typeface.NORMAL)

        iconCreate.setColorFilter(inactiveColor)
        textCreate.setTextColor(inactiveColor)
        textCreate.setTypeface(null, android.graphics.Typeface.NORMAL)

        iconMyStickers.setColorFilter(inactiveColor)
        textMyStickers.setTextColor(inactiveColor)
        textMyStickers.setTypeface(null, android.graphics.Typeface.NORMAL)

        // Activate selected
        when (currentFilter) {
            FilterType.ALL, FilterType.INSTALLED, FilterType.PREMIUM, FilterType.PURCHASED -> {
                iconExplore.setColorFilter(activeColor)
                textExplore.setTextColor(activeColor)
                textExplore.setTypeface(null, android.graphics.Typeface.BOLD)
                menuBtn.setImageResource(R.drawable.ic_menu)
                toolbarSubtitle.visibility = View.GONE
                btnFilter.visibility = View.VISIBLE
                carouselContainer.visibility = View.VISIBLE
            }
            FilterType.FAVORITES -> {
                iconFavorites.setColorFilter(activeColor)
                textFavorites.setTextColor(activeColor)
                textFavorites.setTypeface(null, android.graphics.Typeface.BOLD)
                menuBtn.setImageResource(R.drawable.ic_back)
                toolbarSubtitle.visibility = View.VISIBLE
                toolbarSubtitle.text = getString(R.string.filter_favorites)
                btnFilter.visibility = View.INVISIBLE
            }
            FilterType.CUSTOM -> {
                iconMyStickers.setColorFilter(activeColor)
                textMyStickers.setTextColor(activeColor)
                textMyStickers.setTypeface(null, android.graphics.Typeface.BOLD)
                menuBtn.setImageResource(R.drawable.ic_back)
                toolbarSubtitle.visibility = View.VISIBLE
                toolbarSubtitle.text = getString(R.string.your_stickers)
                btnFilter.visibility = View.INVISIBLE
            }
        }
    }

    private fun setupCarousel() {
        carouselAdapter = TopStickerAdapter { pack ->
            startActivity(Intent(this, DetailsActivity::class.java).putExtra("id", pack.id))
        }
        topStickersCarousel.adapter = carouselAdapter
        // Sadece aktif kartın görünmesi için transformer kaldırıldı.

        topStickersCarousel.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateCarouselIndicator(position)
            }
        })
    }

    private fun updateCarousel(packs: List<Pack>) {
        val topPacks = packs
            .filter { it.isActive && it.category != "custom" }
            .sortedByDescending { it.downloadCount }
            .take(5)

        carouselAdapter?.updatePacks(topPacks)
        setupCarouselIndicator(topPacks.size)
        startAutoScroll(topPacks.size)
    }

    private fun setupCarouselIndicator(count: Int) {
        carouselIndicator.removeAllViews()
        for (i in 0 until count) {
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(8.dpToPx(), 8.dpToPx()).apply {
                    marginStart = 4.dpToPx()
                    marginEnd = 4.dpToPx()
                }
                setBackgroundResource(R.drawable.carousel_indicator_inactive)
            }
            carouselIndicator.addView(dot)
        }
        if (count > 0) updateCarouselIndicator(0)
    }

    private fun updateCarouselIndicator(position: Int) {
        for (i in 0 until carouselIndicator.childCount) {
            val dot = carouselIndicator.getChildAt(i)
            dot.setBackgroundResource(
                if (i == position) R.drawable.carousel_indicator_active
                else R.drawable.carousel_indicator_inactive
            )
        }
    }

    private fun startAutoScroll(itemCount: Int) {
        stopAutoScroll()
        if (itemCount <= 1) return

        carouselHandler = Handler(Looper.getMainLooper())
        carouselRunnable = object : Runnable {
            override fun run() {
                val current = topStickersCarousel.currentItem
                val next = if (current >= itemCount - 1) 0 else current + 1
                topStickersCarousel.setCurrentItem(next, true)
                carouselHandler?.postDelayed(this, 3500)
            }
        }
        carouselHandler?.postDelayed(carouselRunnable!!, 3500)
    }

    private fun stopAutoScroll() {
        carouselRunnable?.let { carouselHandler?.removeCallbacks(it) }
        carouselHandler = null
        carouselRunnable = null
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
        val navLanguage = findViewById<LinearLayout>(R.id.navLanguage)

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
        navLanguage.setOnClickListener { drawer.closeDrawers(); showLanguageDialog() }
    }

    private fun setupSearch() {
        val searchBox = findViewById<EditText>(R.id.searchBox)

        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentSearchQuery = s?.toString() ?: ""
                applyFilters()
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

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) {
            lifecycleScope.launch {
                val customPacks = CustomStickerManager.getCustomPacks(this@MainActivity).mapNotNull { cp ->
                    CustomStickerManager.toWhatsAppPack(this@MainActivity, cp.id)?.copy(category = "custom")
                }
                val firebasePacks = allPacks.filter { it.category != "custom" }
                allPacks = firebasePacks + customPacks
                applyFilters()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        stopAutoScroll()
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
                    StickerRepository.cleanupInvalidCache(this@MainActivity, firebasePackIds)
                    loadedPacks.filter { it.category != "custom" }.forEach { pack ->
                        StickerRepository.updatePackCache(this@MainActivity, pack)
                    }
                    allPacks = loadedPacks
                    setupCategoryChips() // Kategorileri güncelle
                    applyFilters()
                    updateCarousel(loadedPacks)
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

    private fun observePacksUpdateFlow() {
        lifecycleScope.launch {
            StickerRepository.packsUpdateFlow.collect { updatedPacks ->
                Log.d("MainActivity", "Real-time update received: ${updatedPacks.size} packs")
                allPacks = updatedPacks
                setupCategoryChips() // Kategorileri güncelle
                applyFilters()
                updateCarousel(updatedPacks)
            }
        }
    }

    private fun refreshPacks() {
        swipeRefresh.isRefreshing = true
        loadPacksFromFirebase(forceRefresh = true)
        swipeRefresh.postDelayed({ swipeRefresh.isRefreshing = false }, 1000)
    }

    enum class FilterType { ALL, INSTALLED, PREMIUM, FAVORITES, PURCHASED, CUSTOM }

    private fun applyFilters() {
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
                var result = filtered.filter { it.category != "custom" }
                if (currentCategory != "all") {
                    result = result.filter { normalizeCategoryKey(it.category) == currentCategory }
                }
                result
            }
            FilterType.INSTALLED -> filtered.filter { PreferencesHelper.isPackInstalled(this, it.id) }
            FilterType.PREMIUM -> filtered.filter { it.isPremium }
            FilterType.FAVORITES -> filtered.filter { PreferencesHelper.isPackFavorite(this, it.id) }
            FilterType.PURCHASED -> filtered.filter { PreferencesHelper.hasAccessToPremiumPack(this, it.id) }
            FilterType.CUSTOM -> filtered.filter { it.category == "custom" }
        }

        // Profesyonel Sıralama Algoritması (YouTube/Play Store Benzeri)
        // Skor = ( (İndirme + Favori*5) * (1 + CVR) ) * Yenilik_Bonusu
        val sorted = filtered.sortedByDescending { pack ->
            val downloads = pack.downloadCount.toDouble()
            val views = pack.viewCount.toDouble()
            val favorites = pack.favoriteCount.toDouble()
            
            // 1. Verimlilik (CVR)
            val cvr = if (views > 0) downloads / views else 0.0
            
            // 2. Etkileşim Skoru (Favoriler indirmeden 5 kat daha değerli)
            val engagementScore = downloads + (favorites * 5.0)
            
            // 3. Yenilik Bonusu (Son 7 gün içindeyse skoru 3.5 katına çıkart - Daha belirgin olması için artırıldı)
            var freshnessMultiplier = 1.0
            if (pack.createdAt.isNotEmpty()) {
                try {
                    val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    val createdDate = format.parse(pack.createdAt)
                    if (createdDate != null) {
                        val diffDays = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - createdDate.time)
                        if (diffDays <= 7) freshnessMultiplier = 3.5 
                    }
                } catch (e: Exception) {}
            }
            
            val finalScore = (engagementScore * (1.0 + cvr)) * freshnessMultiplier
            Log.d("Ranking", "Pack: ${pack.name} | DL: $downloads | Fav: $favorites | CVR: ${String.format("%.2f", cvr)} | Fresh: $freshnessMultiplier | SCORE: ${String.format("%.1f", finalScore)}")
            finalScore
        }

        adapter.updateList(sorted)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.END)) {
            drawer.closeDrawer(GravityCompat.END)
        } else if (currentFilter == FilterType.CUSTOM || currentFilter == FilterType.FAVORITES) {
            currentFilter = FilterType.ALL
            applyFilters()
            updateBottomNavUI()
        } else {
            super.onBackPressed()
        }
    }

    private fun showFilterMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 2, 0, R.string.filter_installed)
        popup.menu.add(0, 3, 1, R.string.filter_premium)
        popup.menu.add(0, 4, 2, R.string.filter_purchased)

        popup.setOnMenuItemClickListener { item ->
            currentFilter = when (item.itemId) {
                2 -> FilterType.INSTALLED
                3 -> FilterType.PREMIUM
                4 -> FilterType.PURCHASED
                else -> currentFilter
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
            1 -> R.drawable.ic_sun
            2 -> R.drawable.ic_moon
            else -> R.drawable.ic_theme
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
                prefs.edit().putInt("theme", newTheme).apply()
                recreate()
            }
            true
        }
        popup.show()
    }

    private fun showFaqDialog() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://arain-0.github.io/sticky-privacy/#faq")))
    }

    private fun showAboutDialog() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://arain-0.github.io/sticky-privacy/#about")))
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
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://arain-0.github.io/sticky-privacy/#privacy")))
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
        stopAutoScroll()
        StickerRepository.stopObservingPacks()
        billingManager?.destroy()
    }

    companion object {
        private const val REQUEST_DELETE_PACK = 2001
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

        if (requestCode == REQUEST_DELETE_PACK && pendingDeletePackId != null) {
            val packId = pendingDeletePackId!!
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
    }
}
