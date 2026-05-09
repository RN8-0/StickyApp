package com.sticly

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
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
import android.view.ViewGroup
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
import androidx.recyclerview.widget.DiffUtil
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewpager2.widget.ViewPager2
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.messaging.FirebaseMessaging
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.awaitAll
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import android.view.inputmethod.InputMethodManager
import com.google.android.material.button.MaterialButton
import androidx.cardview.widget.CardView
import android.app.Activity

class MainActivity : AppCompatActivity() {

    private lateinit var drawer: DrawerLayout
    private lateinit var rv: RecyclerView
    private lateinit var loadingOverlay: View
    private lateinit var addLoadingOverlay: View
    private lateinit var circularProgressDirect: com.google.android.material.progressindicator.CircularProgressIndicator
    private lateinit var tvDirectAddStatus: TextView
    private lateinit var tvDirectAddSubtitle: TextView
    // skeleton removed
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var mainContent: View
    private lateinit var adapter: PackAdapter
    private lateinit var menuBtn: ImageButton
    private lateinit var toolbarTitle: TextView
    private lateinit var toolbarSubtitle: TextView

    // Empty State
    private lateinit var emptyStateView: View
    // btnCreateFirstSticker removed from layout — FAB (+) is used instead

    // Header Add Button
    private lateinit var btnAddStickerHeader: ImageButton

    // FAB for My Stickers
    private var btnCreateFab: com.google.android.material.floatingactionbutton.FloatingActionButton? = null

    // Bottom Nav
    private lateinit var tabExplore: View
    private lateinit var tabFavorites: View
    private lateinit var tabMyStickers: LinearLayout
    private lateinit var tabAICreate: View
    private lateinit var tabProfile: View
    private lateinit var iconExplore: ImageView
    private lateinit var iconFavorites: ImageView
    private lateinit var iconMyStickers: ImageView
    private lateinit var iconAICreate: ImageView
    private lateinit var iconProfile: ImageView
    private lateinit var textExplore: TextView
    private lateinit var textFavorites: TextView
    private lateinit var textMyStickers: TextView
    private lateinit var textAICreate: TextView
    private lateinit var textProfile: TextView
    private var searchBarLayoutCached: View? = null
    private var btnPremiumHeaderCached: View? = null
    private var navActiveColor = 0
    private var navInactiveColor = 0
    private var lastForegroundPackRefresh = 0L

    // Regional Popular
    private lateinit var regionalPopularContainer: View
    private lateinit var regionalPopularTitle: TextView
    private lateinit var rvRegional: RecyclerView
    private var regionalAdapter: RegionalAdapter? = null

    // New Packs & Trending → Story Circles
    private lateinit var storyContainer: View
    private lateinit var rvStories: RecyclerView
    private var storyAdapter: StoryAdapter? = null

    // AI Inline
    private var aiContentContainer: View? = null
    private var aiSetupDone = false
    private var aiGeneratedBitmap: Bitmap? = null
    private var aiRawBitmap: Bitmap? = null
    private var aiGenerateJob: Job? = null
    private val aiGenerateQueue = java.util.concurrent.ConcurrentLinkedQueue<String>()
    private var aiActiveGenerations = java.util.concurrent.atomic.AtomicInteger(0)
    private val AI_MAX_QUEUE = 4
    private var aiHistoryAdapter: AiHistoryAdapter? = null
    private var aiUpdateGenerateButton: (() -> Unit)? = null

    // Profile
    private var profileContentContainer: View? = null
    private var profileSetupDone = false
    private var profileSocialJson: JSONObject? = null


    // Category Chips
    private lateinit var categoryChipGroup: ChipGroup
    private var currentCategory: String = "all"

    private var allPacks: List<Pack> = emptyList()
    private var billingManager: BillingManager? = null
    private var currentFilter: FilterType = FilterType.ALL
    private var currentSearchQuery: String = ""
    private var pendingDeletePackId: String? = null
    private var pendingDirectAddPack: Pack? = null
    private var wasPackInWhatsAppBeforeDelete = false
    private var waitingForWhatsAppReturn = false
    private var sessionPackOpenCount = 0
    private var promoShownThisSession = false

    // Dinamik kategoriler - Firebase'den paketlerdeki kategorilerden oluşturulur
    private var dynamicCategories = mutableListOf<String>()
    
    // Auto Scroll
    private var autoScrollJob: Job? = null
    private var isUserInteractingWithCarousel = false
    private val snapHelper = androidx.recyclerview.widget.LinearSnapHelper()

    // Session-based rank caching to prevent jumping list order when favorites update
    private val sessionRankScores = mutableMapOf<String, Double>()
    
    // Cache parsed dates to avoid repeated SimpleDateFormat.parse() in sort loops
    private val parsedDateCache = mutableMapOf<String, Long>()

    // In-flight like requests per pack — prevents double-tap from sending duplicate API calls
    private val likeInFlight = mutableSetOf<String>()
    // Submissions deleted by the user — persisted to prefs so deleted packs never reappear across refreshes
    private val deletedSubmissionIds: MutableSet<String> by lazy {
        getSharedPreferences("sticky_prefs", MODE_PRIVATE)
            .getStringSet("deleted_submission_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
    }
    
    // Debounce applyFilters to prevent excessive calls
    private var filterJob: Job? = null
    
    // Deterministic Random Seed for Session
    private val sessionSeed = System.currentTimeMillis()
    
    // Cached shuffle order: once computed for ALL filter, reuse across applyFilters calls
    private var cachedShuffleOrder: List<String>? = null
    
    // Flag to scroll to top after next applyFilters completes (avoids race with async DiffUtil)
    private var pendingScrollToTop = false
    
    // Saved Explore state — full adapter list + scroll position
    private var savedExploreList: List<Any>? = null
    private var savedExploreScrollPos = 0
    private var savedExploreScrollOffset = 0
    
    // Cached format instance (only used on Default dispatcher — single thread safe)
    private val rankDateFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
    private val rankDateFormatFallback = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())

    private fun getOrCalculateRankScore(pack: Pack): Double {
        return sessionRankScores.getOrPut(pack.id) {
            calculateRankScore(pack)
        }
    }

    private fun parseDateCached(dateStr: String): Long {
        return parsedDateCache.getOrPut(dateStr) {
            try { rankDateFormat.parse(dateStr)?.time ?: 0L } catch (_: Exception) {
                try { rankDateFormatFallback.parse(dateStr)?.time ?: 0L } catch (_: Exception) { 0L }
            }
        }
    }

    private fun calculateRankScore(pack: Pack): Double {
        val currentTime = System.currentTimeMillis()
        val downloads = pack.downloadCount.toDouble()
        val views = pack.viewCount.toDouble()
        val favorites = pack.favoriteCount.toDouble()
        val likes = pack.likeCount.toDouble()
        val comments = pack.commentCount.toDouble()
        val fakeBase = pack.fakeDownloadBase.toDouble()

        // 1. Quality Score (Wilson Score Interval - like Reddit/YouTube)
        // Likes and comments are explicit positive signals — fold them into the totalSignals
        // alongside downloads/favorites so the Wilson confidence interval reflects them.
        val totalSignals = downloads + favorites + likes + comments
        val positiveRate = if (views > 0) (totalSignals / views).coerceAtMost(1.0) else 0.0
        val z = 1.96 // 95% confidence
        val n = views.coerceAtLeast(1.0)
        val phat = positiveRate
        val wilsonScore = if (n > 10) {
            (phat + z * z / (2 * n) - z * Math.sqrt((phat * (1 - phat) + z * z / (4 * n)) / n)) / (1 + z * z / n)
        } else {
            phat * 0.5 // Low confidence penalty for new packs with few views
        }

        // 2. Engagement Score (weighted signals)
        // Comments weigh highest — writing one is a stronger signal than a download or like.
        val engagementScore = downloads * 1.0 + favorites * 3.0 + likes * 5.0 + comments * 8.0 + fakeBase * 0.1

        // 3. Time Decay (YouTube-style exponential decay with freshness boost)
        var ageMultiplier = 1.0
        if (pack.createdAt.isNotEmpty()) {
            val createdTime = parseDateCached(pack.createdAt)
            if (createdTime > 0) {
                val diffHours = (currentTime - createdTime).toDouble() / (1000 * 60 * 60)
                val diffDays = diffHours / 24.0
                when {
                    diffDays <= 2 -> ageMultiplier = 4.0    // Brand new: strong boost
                    diffDays <= 7 -> ageMultiplier = 2.5    // This week: good boost
                    diffDays <= 14 -> ageMultiplier = 1.5   // Recent: mild boost
                    diffDays <= 30 -> ageMultiplier = 1.0   // Normal
                    else -> ageMultiplier = 0.85            // Older: slight decay
                }
            }
        }

        // 4. Diversity bonus (animated/premium get slight variety boost)
        val diversityBonus = when {
            pack.isAnimated && pack.isPremium -> 1.15
            pack.isAnimated -> 1.08
            pack.isPremium -> 1.05
            else -> 1.0
        }

        // 5. Popular flag — admin curation acts as a multiplier on top of real engagement
        //    rather than an exclusive filter, so admin picks float up but don't replace real signal.
        val popularBoost = if (pack.isPopular) 1.4 else 1.0

        // 6. Deterministic jitter per session (ensures variety between sessions)
        val jitter = 1.0 + (((pack.id.hashCode().toLong() xor sessionSeed) % 200) / 1000.0)

        // Final Score = (Quality * Engagement * Freshness * Diversity * PopularBoost) with jitter
        return (wilsonScore * 100 + engagementScore) * ageMultiplier * diversityBonus * popularBoost * jitter
    }

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

    private val restoreSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(account.idToken, null)
            com.google.firebase.auth.FirebaseAuth.getInstance().signInWithCredential(credential)
                .addOnCompleteListener(this) { authTask ->
                    if (authTask.isSuccessful) {
                        Toast.makeText(this, "Signed in as ${account.email}. Restoring purchases...", Toast.LENGTH_SHORT).show()
                        billingManager?.restorePurchases { restoreResult ->
                            val msg = when (restoreResult) {
                                BillingManager.RestoreResult.SUCCESS -> { loadPacks(forceRefresh = true); R.string.restore_success }
                                BillingManager.RestoreResult.NOT_FOUND -> R.string.restore_not_found
                                BillingManager.RestoreResult.ERROR -> R.string.restore_error
                            }
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this, R.string.restore_error, Toast.LENGTH_SHORT).show()
                    }
                }
        } catch (e: Exception) {
            Toast.makeText(this, R.string.restore_error, Toast.LENGTH_SHORT).show()
        }
    }

    // AI sign-in: after successful sign-in, sync count from Firebase and allow generation
    private val aiSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(account.idToken, null)
            com.google.firebase.auth.FirebaseAuth.getInstance().signInWithCredential(credential)
                .addOnCompleteListener(this) { authTask ->
                    if (authTask.isSuccessful) {
                        Toast.makeText(this, "✅ Signed in as ${account.email}", Toast.LENGTH_SHORT).show()
                        lifecycleScope.launch {
                            try {
                                val idToken = account.idToken ?: return@launch
                                withContext(Dispatchers.IO) { PocketBaseHelper.authWithOAuth("google", idToken) }
                                PreferencesHelper.setPocketBaseAuth(
                                    this@MainActivity,
                                    PocketBaseHelper.getToken(),
                                    PocketBaseHelper.getAuthRecordId()
                                )
                            } catch (_: Exception) {}
                        }
                        // Sync premium status from Firebase first, then update UI
                        syncPremiumStatus {
                            aiRestoreCount()
                            aiUpdateGenerateButton?.invoke()
                        }
                    } else {
                        Toast.makeText(this, "Sign-in failed", Toast.LENGTH_SHORT).show()
                    }
                }
        } catch (e: Exception) {
            Toast.makeText(this, "Sign-in failed", Toast.LENGTH_SHORT).show()
        }
    }

    @Suppress("DEPRECATION")
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // singleTop: activity already running, just bring to front — no state reset needed
    }

    override fun onCreate(s: Bundle?) {
        // Switch from SplashTheme to normal AppTheme BEFORE setContentView
        setTheme(R.style.AppTheme)
        super.onCreate(s)
        if (PreferencesHelper.isFirstLaunch(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_main)

        // Restore state if activity was recreated
        if (s != null) {
            val filterName = s.getString("currentFilter", "ALL")
            currentFilter = try { FilterType.valueOf(filterName) } catch (_: Exception) { FilterType.ALL }
            currentCategory = s.getString("currentCategory", "all") ?: "all"
            currentSearchQuery = s.getString("currentSearchQuery", "") ?: ""
        }

        // Initialize views FIRST
        initViews()

        // If we have cached data, show it immediately; otherwise show loading spinner
        if (StickerRepository.allPacksCache.isNotEmpty()) {
            displayPacks(StickerRepository.allPacksCache)
        } else {
            // Show loading overlay — hidden by showContent() when first data arrives
            loadingOverlay.visibility = View.VISIBLE
        }

        // Show blocking bottom sheet if no internet
        if (!NetworkUtils.isOnline(this)) {
            showNoInternetBottomSheet()
        }

        // Setup essential UI components
        setupBottomNav()
        setupSearch()
        setupCategoryChips()
        setupDrawerMenu()

        // Defer heavier UI setup to after first frame
        window.decorView.post {
            aiRestoreCount()

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
            val deviceIdForFcm = PreferencesHelper.getDeviceId(this)
            if (deviceIdForFcm.isNotBlank()) {
                val safeTopic = "user_${deviceIdForFcm.replace(Regex("[^a-zA-Z0-9_-]"), "_")}"
                FirebaseMessaging.getInstance().subscribeToTopic(safeTopic)
            }
            PreferencesHelper.restorePocketBaseAuth(this)
            PushTokenManager.refreshAndSync(this)

            // Kullanıcı giriş yapmışsa Firebase ile senkronize et (e-posta dahil)
            val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {
                PreferencesHelper.syncUserData(this, currentUser.uid)
                PreferencesHelper.startRealtimeSync(this, currentUser.uid)
            }
            syncPremiumStatus {
                updateBottomNavUI()
                if (::adapter.isInitialized) applyFilters()
            }

            checkAndRequestNotificationPermission()
            checkInstallationUpdates()

            // Özel paketleri favorilerden temizle (custom paketler favorilerde görünmemeli)
            val favs = PreferencesHelper.getFavoritePacks(this)
            favs.filter { it.startsWith("custom_") }.forEach { PreferencesHelper.removeFavoritePack(this, it) }

            // Premium promo artık indirme sonrası gösteriliyor (2. paketten sonra)
        }

        // Start data loading (will update UI when complete)
        if (NetworkUtils.isOnline(this)) {
            observePacksUpdateFlow()
            loadPacks(forceRefresh = true)
        }
        
        // Delay real-time observer start to avoid cascading reloads during initial load
        lifecycleScope.launch {
            delay(2_500)
            StickerRepository.startObservingPacks(this@MainActivity)
        }
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
        // Ensure status bar matches toolbar color
        window.statusBarColor = androidx.core.content.ContextCompat.getColor(this, R.color.toolbar_bg)
        drawer.setStatusBarBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.toolbar_bg))
        // Prevent swipe-to-open drawer from intercepting bottom nav tab clicks
        drawer.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        rv = findViewById(R.id.rv)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        addLoadingOverlay = findViewById(R.id.addLoadingOverlay)
        circularProgressDirect = findViewById(R.id.circularProgressDirect)
        tvDirectAddStatus = findViewById(R.id.tvDirectAddStatus)
        tvDirectAddSubtitle = findViewById(R.id.tvDirectAddSubtitle)

        swipeRefresh = findViewById(R.id.swipeRefresh)
        menuBtn = findViewById(R.id.menuBtn)
        toolbarTitle = findViewById(R.id.toolbarTitle)
        toolbarSubtitle = findViewById(R.id.toolbarSubtitle)

        // Toolbar'a tıklayınca en üste scroll
        findViewById<View>(R.id.toolbarLayout).setOnClickListener {
            rv.smoothScrollToPosition(0)
        }

        mainContent = findViewById(R.id.mainContent)
        emptyStateView = findViewById(R.id.emptyStateView)
        aiContentContainer = findViewById(R.id.aiContentContainer) // null until ViewStub inflated
        // btnCreateFirstSticker removed from layout
        btnAddStickerHeader = findViewById(R.id.btnAddStickerHeader)
        btnAddStickerHeader.visibility = View.GONE // hidden; FAB is used instead on My Stickers

        // FAB for My Stickers tab
        btnCreateFab = findViewById(R.id.btnCreateFab)
        btnCreateFab?.setOnClickListener {
            showStickerTypeChooser()
        }

        // FAB: scroll-to-top
        val btnScrollToTop = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.btnScrollToTop)
        btnScrollToTop?.setOnClickListener {
            val appBar = findViewById<com.google.android.material.appbar.AppBarLayout>(R.id.mainAppBarLayout)
            appBar?.setExpanded(true, true)
            rv.smoothScrollToPosition(0)
        }
        rv.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                val totalScrolled = (recyclerView.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager)
                    ?.findFirstVisibleItemPosition() ?: 0
                // Only show on the home/ALL tab — Favorites, AI, Custom, Profile etc.
                // each have their own UI and should not show this floating button.
                val visible = currentFilter == FilterType.ALL && totalScrolled >= 6
                btnScrollToTop?.visibility = if (visible) View.VISIBLE else View.GONE
            }
        })

        categoryChipGroup = findViewById(R.id.categoryChipGroup)
        
        regionalPopularContainer = findViewById(R.id.regionalPopularContainer)
        regionalPopularTitle = findViewById(R.id.regionalPopularTitle)
        rvRegional = findViewById(R.id.rvRegional)
        setupRegionalSection()

        storyContainer = findViewById(R.id.storyContainer)
        rvStories = findViewById(R.id.rvStories)
        setupStorySection()

        // Bottom Nav
        tabExplore = findViewById(R.id.tabExplore)
        tabFavorites = findViewById(R.id.tabFavorites)
        tabMyStickers = findViewById(R.id.tabMyStickers)
        tabAICreate = findViewById(R.id.tabAICreate)
        tabProfile = findViewById(R.id.tabProfile)
        iconExplore = findViewById(R.id.iconExplore)
        iconFavorites = findViewById(R.id.iconFavorites)
        iconMyStickers = findViewById(R.id.iconMyStickers)
        iconAICreate = findViewById(R.id.iconAICreate)
        iconProfile = findViewById(R.id.iconProfile)
        textExplore = findViewById(R.id.textExplore)
        textFavorites = findViewById(R.id.textFavorites)
        textMyStickers = findViewById(R.id.textMyStickers)
        textAICreate = findViewById(R.id.textAICreate)
        textProfile = findViewById(R.id.textProfile)
        searchBarLayoutCached = findViewById(R.id.searchBarLayout)
        btnPremiumHeaderCached = findViewById(R.id.btnPremiumHeader)
        navActiveColor = ContextCompat.getColor(this, R.color.bottom_nav_active)
        navInactiveColor = ContextCompat.getColor(this, R.color.bottom_nav_inactive)

        swipeRefresh.isEnabled = false

        rv.layoutManager = LinearLayoutManager(this)
        rv.setHasFixedSize(true)
        rv.setItemViewCacheSize(10)
        rv.itemAnimator = null
        installCenteredListPadding(rv)
        (rv.layoutManager as LinearLayoutManager).initialPrefetchItemCount = 6
        val viewPool = RecyclerView.RecycledViewPool()
        viewPool.setMaxRecycledViews(0, 20) // TYPE_PACK
        viewPool.setMaxRecycledViews(1, 5)  // TYPE_AD
        rv.setRecycledViewPool(viewPool)
        adapter = PackAdapter(
            items = allPacks,
            click = { pack ->
                sessionPackOpenCount++
                if (sessionPackOpenCount == 3) {
                    StickyApp.appOpenAdInstance?.tryShowAd()
                }
                startActivity(Intent(this, DetailsActivity::class.java).putExtra("id", pack.id))
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            },
            onFavoriteChanged = {
                if (currentFilter == FilterType.FAVORITES) applyFilters()
            },
            onDeleteClick = { pack ->
                deleteCustomPack(pack)
            },
            onAddClick = { pack ->
                directAddToWhatsApp(pack)
            },
            onPublisherClick = { pack ->
                showPacksByPublisher(pack.pub)
            },
            onShareClick = { pack ->
                val count = pack.stickers.size
                if (count !in 9..30) {
                    showThemedCountWarning(getString(R.string.publish_pack_count_range))
                } else {
                    startActivity(Intent(this, SubmitPackActivity::class.java).putExtra("packId", pack.id))
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                }
            },
            onLikeClick = onLikeClick@{ pack, vh ->
                if (!SocialRepository.isSignedIn(this)) {
                    android.widget.Toast.makeText(this, getString(R.string.profile_login_required), android.widget.Toast.LENGTH_SHORT).show()
                    return@onLikeClick
                }
                // Block concurrent API calls for the same pack
                if (likeInFlight.contains(pack.id)) return@onLikeClick
                likeInFlight.add(pack.id)

                val wasLiked = SocialRepository.isLocallyLiked(this, pack.id)
                val nowLiked = !wasLiked
                // Update local state immediately so a rapid second tap reads the correct state
                SocialRepository.setLocalLike(this, pack.id, nowLiked)

                // Optimistic UI update
                vh.btnItemLike?.setImageResource(if (nowLiked) R.drawable.ic_thumb_up else R.drawable.ic_thumb_up_outline)
                val color = androidx.core.content.ContextCompat.getColor(this, if (nowLiked) R.color.primary else R.color.text_hint)
                vh.btnItemLike?.setColorFilter(color)
                vh.tvItemLikeCount?.setTextColor(color)
                val optimisticCount = pack.likeCount + if (nowLiked) 1 else -1
                if (optimisticCount > 0) {
                    vh.tvItemLikeCount?.text = optimisticCount.toString()
                    vh.tvItemLikeCount?.visibility = View.VISIBLE
                } else {
                    vh.tvItemLikeCount?.visibility = View.GONE
                }
                lifecycleScope.launch {
                    runCatching { SocialRepository.togglePackLike(this@MainActivity, pack) }
                        .onSuccess { result ->
                            val liked = result.optBoolean("liked", nowLiked)
                            val serverCount = result.optInt("like_count", -1)
                            vh.btnItemLike?.setImageResource(if (liked) R.drawable.ic_thumb_up else R.drawable.ic_thumb_up_outline)
                            val c = androidx.core.content.ContextCompat.getColor(this@MainActivity, if (liked) R.color.primary else R.color.text_hint)
                            vh.btnItemLike?.setColorFilter(c)
                            vh.tvItemLikeCount?.setTextColor(c)
                            if (serverCount >= 0) {
                                // Sync cache so DetailsActivity sees the updated count
                                StickerRepository.allPacksCache = StickerRepository.allPacksCache.map {
                                    if (it.id == pack.id) it.copy(likeCount = serverCount) else it
                                }
                                if (serverCount > 0) {
                                    vh.tvItemLikeCount?.text = serverCount.toString()
                                    vh.tvItemLikeCount?.visibility = View.VISIBLE
                                } else {
                                    vh.tvItemLikeCount?.visibility = View.GONE
                                }
                            }
                        }
                        .onFailure {
                            // Revert local state and UI on failure
                            SocialRepository.setLocalLike(this@MainActivity, pack.id, wasLiked)
                            vh.btnItemLike?.setImageResource(if (wasLiked) R.drawable.ic_thumb_up else R.drawable.ic_thumb_up_outline)
                            val c = androidx.core.content.ContextCompat.getColor(this@MainActivity, if (wasLiked) R.color.primary else R.color.text_hint)
                            vh.btnItemLike?.setColorFilter(c)
                            vh.tvItemLikeCount?.setTextColor(c)
                        }
                    likeInFlight.remove(pack.id)
                }
            }
        )
        rv.adapter = adapter

        menuBtn.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }

        // Toolbar notification bell — shown only on profile tab; opens NotificationsActivity
        findViewById<View>(R.id.toolbarNotificationBtn)?.setOnClickListener {
            clearNotificationBadges()
            startActivity(Intent(this, NotificationsActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }



        btnPremiumHeaderCached?.setOnClickListener {
            startActivity(Intent(this, PremiumActivity::class.java))
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }

        // btnCreateFirstSticker removed — FAB handles this action
    }

    private fun saveExploreState() {
        if (currentFilter == FilterType.ALL || currentFilter == FilterType.PREMIUM) {
            if (::adapter.isInitialized) {
                savedExploreList = adapter.getItems()
            }
            val lm = rv.layoutManager as? LinearLayoutManager ?: return
            savedExploreScrollPos = lm.findFirstVisibleItemPosition()
            val topView = lm.findViewByPosition(savedExploreScrollPos)
            savedExploreScrollOffset = topView?.top ?: 0
        }
    }

    private fun restoreExploreState(): Boolean {
        val saved = savedExploreList ?: return false
        if (saved.isEmpty()) return false
        if (::adapter.isInitialized) {
            adapter.updateList(saved)
            rv.visibility = View.VISIBLE
            emptyStateView.visibility = View.GONE
            (rv.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(
                savedExploreScrollPos, savedExploreScrollOffset
            )
        }
        return true
    }

    private fun setupBottomNav() {
        tabExplore.setOnClickListener {
            try {
                currentFilter = FilterType.ALL
                currentCategory = "all"
                updateBottomNavUI()
                updateCategoryChipSelection()
                categoryChipGroup.visibility = View.VISIBLE
                showHomeSections()
                // Expand app bar when explicitly switching to explore tab
                findViewById<com.google.android.material.appbar.AppBarLayout>(R.id.mainAppBarLayout)?.setExpanded(true, false)
                tabExplore.post {
                    if (!restoreExploreState()) {
                        applyFilters()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("BottomNav", "Explore tab error", e)
            }
        }

        tabFavorites.setOnClickListener {
            try {
                saveExploreState()
                currentFilter = FilterType.FAVORITES
                pendingScrollToTop = true
                updateBottomNavUI()
                tabFavorites.post { applyFilters() }
            } catch (e: Exception) {
                android.util.Log.e("BottomNav", "Favorites tab error", e)
            }
        }

        tabAICreate.setOnClickListener {
            try {
                saveExploreState()
                currentFilter = FilterType.AI
                ensureAiInflated()
                updateBottomNavUI()
                aiContentContainer?.post { aiLoadHistory() }
            } catch (e: Exception) {
                android.util.Log.e("BottomNav", "AI tab error", e)
                // Navigation still happened (currentFilter already set), just reinforce UI
                updateBottomNavUI()
            }
        }

        tabMyStickers.setOnClickListener {
            try {
                saveExploreState()
                currentFilter = FilterType.CUSTOM
                pendingScrollToTop = true
                updateBottomNavUI()
                categoryChipGroup.visibility = View.GONE
                hideHomeSections()
                tabMyStickers.post { applyFilters() }
            } catch (e: Exception) {
                android.util.Log.e("BottomNav", "MyStickers tab error", e)
            }
        }

        tabProfile.setOnClickListener {
            try {
                saveExploreState()
                currentFilter = FilterType.PROFILE
                ensureProfileInflated()
                updateBottomNavUI()
                tabProfile.post { loadProfileData() }
            } catch (e: Exception) {
                android.util.Log.e("BottomNav", "Profile tab error", e)
                updateBottomNavUI()
            }
        }

        updateBottomNavUI()
    }

    private fun updateBottomNavUI() {
        val activeColor = navActiveColor
        val inactiveColor = navInactiveColor

        // Hide the scroll-to-top FAB the moment the user leaves the home/Explore tab —
        // the scroll listener only fires on scroll, so without this the button can stay
        // visible while the user is on Favorites/AI/Custom/Profile.
        if (currentFilter != FilterType.ALL) {
            findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.btnScrollToTop)?.visibility = View.GONE
        }

        // Reset all (iconExplore always white - it sits on purple circular bg)
        iconExplore.setColorFilter(android.graphics.Color.WHITE)
        textExplore.setTextColor(inactiveColor)
        
        iconFavorites.setColorFilter(inactiveColor)
        textFavorites.setTextColor(inactiveColor)

        iconAICreate.setColorFilter(inactiveColor)
        textAICreate.setTextColor(inactiveColor)

        iconMyStickers.setColorFilter(inactiveColor)
        textMyStickers.setTextColor(inactiveColor)

        iconProfile.setColorFilter(inactiveColor)
        textProfile.setTextColor(inactiveColor)

        if (currentFilter == FilterType.AI) {
            // Show AI content, hide everything else
            mainContent.visibility = View.GONE
            emptyStateView.visibility = View.GONE
            searchBarLayoutCached?.visibility = View.GONE
            categoryChipGroup.visibility = View.GONE
            hideHomeSections()
            btnAddStickerHeader.visibility = View.GONE
            btnPremiumHeaderCached?.visibility = if (PreferencesHelper.isPremium(this)) View.GONE else View.VISIBLE
            menuBtn.visibility = View.VISIBLE
            findViewById<View>(R.id.toolbarNotificationContainer)?.visibility = View.GONE
            toolbarTitle.text = "✨ Sticky AI"
            toolbarSubtitle.visibility = View.GONE
            profileContentContainer?.visibility = View.GONE
            aiContentContainer?.visibility = View.VISIBLE

            iconAICreate.setColorFilter(activeColor)
            textAICreate.setTextColor(activeColor)
            return
        }

        if (currentFilter == FilterType.PROFILE) {
            // Show Profile content, hide everything else
            mainContent.visibility = View.GONE
            emptyStateView.visibility = View.GONE
            searchBarLayoutCached?.visibility = View.GONE
            categoryChipGroup.visibility = View.GONE
            hideHomeSections()
            btnAddStickerHeader.visibility = View.GONE
            btnPremiumHeaderCached?.visibility = View.GONE
            menuBtn.visibility = View.VISIBLE
            findViewById<View>(R.id.toolbarNotificationContainer)?.visibility = View.VISIBLE
            toolbarTitle.text = getString(R.string.profile)
            toolbarSubtitle.visibility = View.GONE
            aiContentContainer?.visibility = View.GONE
            profileContentContainer?.visibility = View.VISIBLE
            // FAB (+) yalnızca My Stickers sekmesinde gösterilir
            btnCreateFab?.visibility = View.GONE

            iconProfile.setColorFilter(activeColor)
            textProfile.setTextColor(activeColor)
            return
        }

        // Non-AI/Profile tabs: restore normal UI
        aiContentContainer?.visibility = View.GONE
        profileContentContainer?.visibility = View.GONE
        mainContent.visibility = View.VISIBLE
        val isPremiumUser = PreferencesHelper.isPremium(this)
        btnPremiumHeaderCached?.visibility = View.VISIBLE
        menuBtn.visibility = View.VISIBLE
        findViewById<View>(R.id.toolbarNotificationContainer)?.visibility = View.GONE
        toolbarTitle.text = if (isPremiumUser) "Premium" else getString(R.string.app_name)
        // FAB only on My Stickers
        btnCreateFab?.visibility = View.GONE

        // Activate selected
        when (currentFilter) {
            FilterType.ALL, FilterType.PREMIUM, FilterType.PURCHASED -> {
                searchBarLayoutCached?.visibility = View.VISIBLE
                iconExplore.setColorFilter(android.graphics.Color.WHITE)
                textExplore.setTextColor(activeColor)
                
                menuBtn.setImageResource(R.drawable.ic_menu)
                toolbarSubtitle.visibility = View.GONE
                categoryChipGroup.visibility = View.VISIBLE
                showHomeSections()
            }
            FilterType.FAVORITES -> {
                searchBarLayoutCached?.visibility = View.GONE
                iconFavorites.setColorFilter(activeColor)
                textFavorites.setTextColor(activeColor)
                
                menuBtn.setImageResource(R.drawable.ic_menu)
                toolbarSubtitle.visibility = View.VISIBLE
                toolbarSubtitle.text = getString(R.string.filter_favorites)
                categoryChipGroup.visibility = View.GONE
                hideHomeSections()
            }
            FilterType.INSTALLED -> {
                searchBarLayoutCached?.visibility = View.VISIBLE
                iconFavorites.setColorFilter(activeColor)
                textFavorites.setTextColor(activeColor)
                
                menuBtn.setImageResource(R.drawable.ic_menu)
                toolbarSubtitle.visibility = View.VISIBLE
                toolbarSubtitle.text = getString(R.string.filter_favorites)
                categoryChipGroup.visibility = View.GONE
                hideHomeSections()
            }
            FilterType.CUSTOM -> {
                // My Stickers: hide search bar, show FAB (+)
                searchBarLayoutCached?.visibility = View.GONE
                btnCreateFab?.visibility = View.VISIBLE
                iconMyStickers.setColorFilter(activeColor)
                textMyStickers.setTextColor(activeColor)

                menuBtn.setImageResource(R.drawable.ic_menu)
                toolbarSubtitle.visibility = View.VISIBLE
                toolbarSubtitle.text = getString(R.string.your_stickers)
                categoryChipGroup.visibility = View.GONE
                hideHomeSections()
                ((mainContent as? android.view.ViewGroup)?.getChildAt(0) as? com.google.android.material.appbar.AppBarLayout)?.setExpanded(false, false)
            }
            else -> {
                searchBarLayoutCached?.visibility = View.VISIBLE
            }
        }
        btnAddStickerHeader.visibility = View.GONE
    }

    // ─── AI Inline Logic ────────────────────────────────────────────────

    private fun ensureAiInflated() {
        if (aiContentContainer != null) return
        try {
            val stub = findViewById<android.view.ViewStub>(R.id.aiContentStub)
            stub?.inflate()
        } catch (_: Exception) { /* already inflated */ }
        aiContentContainer = findViewById(R.id.aiContentContainer)
        setupAiInline()
    }

    private fun setupAiInline() {
        if (aiSetupDone) return
        aiSetupDone = true

        val aiEtPrompt = findViewById<EditText>(R.id.aiEtPrompt) ?: return
        val aiBtnGenerate = findViewById<MaterialButton>(R.id.aiBtnGenerate)

        // Update generate button based on sign-in state
        fun updateGenerateButton() {
            val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (user == null) {
                aiBtnGenerate?.text = getString(R.string.profile_sign_in)
                aiBtnGenerate?.setIconResource(R.drawable.ic_google)
            } else {
                aiBtnGenerate?.text = "✨ ${getString(R.string.ai_generate)}"
                aiBtnGenerate?.icon = null
            }
        }
        updateGenerateButton()
        val aiPreviewCard = findViewById<CardView>(R.id.aiPreviewCard)
        val aiIvPreview = findViewById<ImageView>(R.id.aiIvPreview)
        val aiLoadingOverlay = findViewById<View>(R.id.aiLoadingOverlay)
        val aiTvLoadingStatus = findViewById<TextView>(R.id.aiTvLoadingStatus)
        val aiEditButtons = findViewById<View>(R.id.aiEditButtons)
        val aiBtnTryAgain = findViewById<MaterialButton>(R.id.aiBtnTryAgain)
        val aiBtnEdit = findViewById<MaterialButton>(R.id.aiBtnEdit)
        val aiBtnAddToPack = findViewById<MaterialButton>(R.id.aiBtnAddToPack)
        val aiTvError = findViewById<TextView>(R.id.aiTvError)
        val aiTvDailyCounter = findViewById<TextView>(R.id.tvAiDailyCounter)
        // Style chips removed — single high-quality sticker style
        val aiBtnInspireMe = findViewById<View>(R.id.aiBtnInspireMe)
        val aiBtnClosePreview = findViewById<ImageView>(R.id.aiBtnClosePreview)
        fun updateAiDailyCounter() {
            val remaining = aiGetRemainingCount()
            aiTvDailyCounter?.text = if (remaining < 0) {
                "✨ ${getString(R.string.ai_unlimited)}"
            } else {
                "⚡ ${getString(R.string.ai_remaining, remaining, AI_DAILY_FREE_LIMIT)}"
            }
        }

        // Store references so aiSignInLauncher callback can refresh UI
        this.aiUpdateGenerateButton = {
            updateGenerateButton()
            updateAiDailyCounter()
        }

        fun getAiSelectedStyle(): String = ""

        fun setAiGenerating(isGenerating: Boolean) {
            aiPreviewCard?.visibility = View.VISIBLE
            aiLoadingOverlay?.visibility = if (isGenerating) View.VISIBLE else View.GONE
            aiBtnGenerate?.isEnabled = !isGenerating
            if (isGenerating) {
                aiEditButtons?.visibility = View.GONE
                aiBtnAddToPack?.visibility = View.GONE
                aiIvPreview?.setImageDrawable(null)
            }
        }

        fun doGenerate() {
            // Require Google sign-in for AI generation (prevents daily limit bypass via data clear)
            val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (currentUser == null) {
                showAiSignInDialog()
                return
            }
            val prompt = aiEtPrompt.text?.toString()?.trim() ?: ""
            if (prompt.isEmpty()) {
                Toast.makeText(this, R.string.ai_empty_prompt, Toast.LENGTH_SHORT).show()
                return
            }
            if (!aiCanGenerate()) {
                aiShowDailyLimitDialog()
                return
            }
            // Check queue limit
            val active = aiActiveGenerations.get()
            if (active >= AI_MAX_QUEUE) {
                Toast.makeText(this, "⏳ Maximum $AI_MAX_QUEUE generations at once. Please wait.", Toast.LENGTH_SHORT).show()
                return
            }
            // Dismiss keyboard
            currentFocus?.let {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(it.windowToken, 0)
            }
            aiActiveGenerations.incrementAndGet()
            val queuedPrompt = prompt
            val queuedStyle = getAiSelectedStyle()

            // Show generating UI for the first request
            if (aiActiveGenerations.get() == 1) {
                setAiGenerating(true)
            } else {
                Toast.makeText(this, "✨ Queued! (${aiActiveGenerations.get()}/$AI_MAX_QUEUE)", Toast.LENGTH_SHORT).show()
            }

            lifecycleScope.launch {
                try {
                    aiTvError?.visibility = View.GONE

                    withContext(Dispatchers.Main) {
                        aiTvLoadingStatus?.text = getString(R.string.ai_optimizing_prompt)
                    }
                    val optimizedPrompt = aiOptimizePrompt(queuedPrompt, queuedStyle)

                    withContext(Dispatchers.Main) {
                        val queueCount = aiActiveGenerations.get()
                        aiTvLoadingStatus?.text = if (queueCount > 1) "✨ Generating... ($queueCount active)" else "✨ Generating..."
                    }

                    val bitmap = aiGenerateImage(optimizedPrompt) {}

                    if (bitmap != null) {
                        withContext(Dispatchers.Main) {
                            aiTvLoadingStatus?.text = getString(R.string.ai_processing)
                        }
                        val scaledBmp = Bitmap.createScaledBitmap(bitmap, 512, 512, true)
                        aiRawBitmap = scaledBmp
                        aiGeneratedBitmap = scaledBmp

                        val savedPath = aiSaveToHistory(scaledBmp, queuedPrompt)

                        withContext(Dispatchers.Main) {
                            aiIvPreview?.setImageBitmap(scaledBmp)
                            aiPreviewCard?.visibility = View.VISIBLE
                            aiEditButtons?.visibility = View.VISIBLE
                            aiBtnAddToPack?.visibility = View.VISIBLE
                            aiBtnClosePreview?.visibility = View.VISIBLE
                            aiIncrementCount()
                            updateAiDailyCounter()
                            aiContentContainer?.post { aiLoadHistory() }
                        }

                        if (savedPath != null) aiSyncHistoryToFirebase(queuedPrompt, savedPath)
                    } else {
                        withContext(Dispatchers.Main) {
                            aiTvError?.text = "⚠️ Server busy, please try again in a few seconds"
                            aiTvError?.visibility = View.VISIBLE
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        aiTvError?.text = e.message ?: getString(R.string.ai_generation_failed)
                        aiTvError?.visibility = View.VISIBLE
                    }
                } finally {
                    val remaining = aiActiveGenerations.decrementAndGet()
                    withContext(Dispatchers.Main) {
                        if (remaining <= 0) {
                            setAiGenerating(false)
                        } else {
                            aiTvLoadingStatus?.text = "${getString(R.string.ai_generating_image)} ($remaining remaining)"
                        }
                    }
                }
            }
        }

        aiBtnGenerate?.setOnClickListener { doGenerate() }
        aiBtnTryAgain?.setOnClickListener { doGenerate() }

        aiBtnEdit?.setOnClickListener {
            val bmp = aiRawBitmap ?: return@setOnClickListener
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val tempFile = java.io.File(cacheDir, "ai_edit_temp.png")
                    java.io.FileOutputStream(tempFile).use { out ->
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    withContext(Dispatchers.Main) {
                        val intent = Intent(this@MainActivity, StickerMakerActivity::class.java)
                        intent.putExtra("editImageUri", android.net.Uri.fromFile(tempFile).toString())
                        startActivity(intent)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        aiBtnAddToPack?.setOnClickListener {
            aiShowPackPickerDialog()
        }

        // Inspire Me: DeepSeek generates a random creative prompt
        aiBtnInspireMe?.setOnClickListener {
            aiBtnInspireMe.isEnabled = false
            aiBtnInspireMe.alpha = 0.7f
            val spinner = findViewById<View>(R.id.aiInspireSpinner)
            spinner?.visibility = View.VISIBLE
            lifecycleScope.launch {
                val prompt = aiGenerateRandomPrompt()
                aiBtnInspireMe.isEnabled = true
                aiBtnInspireMe.alpha = 1f
                spinner?.visibility = View.GONE
                aiEtPrompt.setText(prompt)
            }
        }

        // Close preview button
        aiBtnClosePreview?.setOnClickListener {
            aiPreviewCard?.visibility = View.GONE
            aiEditButtons?.visibility = View.GONE
            aiBtnAddToPack?.visibility = View.GONE
            aiBtnClosePreview?.visibility = View.GONE
            findViewById<TextView>(R.id.aiTvPromptDisplay)?.visibility = View.GONE
            aiRawBitmap = null
            aiGeneratedBitmap = null
            // Restore Regenerate button for next generation
            aiBtnTryAgain?.text = "🔄 Regenerate"
            aiBtnTryAgain?.icon = null
            aiBtnTryAgain?.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.primary))
            aiBtnTryAgain?.strokeColor = android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(this, R.color.primary))
            aiBtnTryAgain?.setOnClickListener { doGenerate() }
        }

        updateAiDailyCounter()
        // Don't load history here - container is GONE, RecyclerView will have 0 width.
        // History is loaded when AI tab becomes visible (tab click and onResume).
    }

    private fun aiCanGenerate(): Boolean {
        if (PreferencesHelper.isPremium(this)) return true
        aiResetIfNewDay()
        return getSharedPreferences("sticky_prefs", MODE_PRIVATE).getInt(AI_PREFS_COUNT, 0) < AI_DAILY_FREE_LIMIT
    }

    private fun aiGetRemainingCount(): Int {
        if (PreferencesHelper.isPremium(this)) return -1
        aiResetIfNewDay()
        return AI_DAILY_FREE_LIMIT - getSharedPreferences("sticky_prefs", MODE_PRIVATE).getInt(AI_PREFS_COUNT, 0)
    }

    private fun aiIncrementCount() {
        if (PreferencesHelper.isPremium(this)) return
        aiResetIfNewDay()
        val prefs = getSharedPreferences("sticky_prefs", MODE_PRIVATE)
        val newCount = prefs.getInt(AI_PREFS_COUNT, 0) + 1
        prefs.edit()
            .putInt(AI_PREFS_COUNT, newCount)
            .putString(AI_PREFS_DATE, aiTodayString())
            .apply()
        // Sync to Firebase so count survives app uninstall
        aiSyncCountToFirebase(newCount)
    }

    private fun aiResetIfNewDay() {
        val prefs = getSharedPreferences("sticky_prefs", MODE_PRIVATE)
        if ((prefs.getString(AI_PREFS_DATE, "") ?: "") != aiTodayString()) {
            prefs.edit().putInt(AI_PREFS_COUNT, 0).putString(AI_PREFS_DATE, aiTodayString()).apply()
        }
    }

    private fun aiSyncCountToFirebase(count: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser ?: return@launch
                val filter = "uid='${user.uid.replace("'", "\\'")}' || user_id='${user.uid.replace("'", "\\'")}'"
                val data = JSONObject().apply {
                    put("uid", user.uid)
                    put("user_id", user.uid)
                    put("email", user.email ?: "")
                    put("ai_count", count)
                    put("ai_date", aiTodayString())
                    put("last_sync", java.time.Instant.now().toString())
                }
                val existing = PocketBaseHelper.listRecords("user_profiles", filter = filter, perPage = 1)
                if (existing.isEmpty()) PocketBaseHelper.createRecord("user_profiles", data)
                else PocketBaseHelper.updateRecord("user_profiles", existing.first().getString("id"), data)
            } catch (_: Exception) { }
        }
    }

    private fun aiRestoreCount() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser ?: return@launch
                val filter = "uid='${user.uid.replace("'", "\\'")}' || user_id='${user.uid.replace("'", "\\'")}'"
                val existing = PocketBaseHelper.listRecords("user_profiles", filter = filter, perPage = 1)
                if (existing.isNotEmpty()) {
                    val profile = existing.first()
                    val pbDate = profile.optString("ai_date")
                    val pbCount = profile.optInt("ai_count", -1)
                    if (pbDate.isBlank() || pbCount < 0) return@launch
                    val prefs = getSharedPreferences("sticky_prefs", MODE_PRIVATE)
                    val localDate = prefs.getString(AI_PREFS_DATE, "") ?: ""
                    val localCount = prefs.getInt(AI_PREFS_COUNT, 0)
                    if (pbDate == aiTodayString() && pbCount > localCount) {
                        prefs.edit().putInt(AI_PREFS_COUNT, pbCount).putString(AI_PREFS_DATE, pbDate).apply()
                    }
                }
            } catch (_: Exception) { }
        }
    }

    private fun aiTodayString(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private val AI_STICKER_SUFFIX = ", cartoon sticker, white background, bold outlines, vibrant colors, expressive, cute, centered"

    // Skip DeepSeek rewriting — it loses the subject. Just enhance the prompt minimally.
    private suspend fun aiOptimizePrompt(userPrompt: String, style: String): String =
        withContext(Dispatchers.IO) {
            // If user prompt is already detailed (>30 chars), just add sticker suffix
            // If short, use DeepSeek to expand it into a proper sticker description
            if (userPrompt.length > 40) {
                return@withContext "$userPrompt$AI_STICKER_SUFFIX"
            }

            try {
                val apiKey = BuildConfig.DEEPSEEK_API_KEY
                if (apiKey.isEmpty()) return@withContext "$userPrompt$AI_STICKER_SUFFIX"

                val systemMessage = """Expand this short idea into a vivid sticker image prompt (40-60 words max). Output ONLY the prompt.
Keep the EXACT subject the user described. Add: specific colors, facial expression, pose, one funny detail.
End with: cartoon sticker, white background, bold outlines, vibrant colors
Example input: "angry cactus"
Example output: "angry green cactus with bright orange cheeks, wearing a tiny party hat, eyebrow raised high, steam puffs shooting from spines, arms crossed, grumpy frown, cartoon sticker, white background, bold outlines, vibrant neon colors"
NEVER change the subject. NEVER add unrelated characters."""

                val body = JSONObject().apply {
                    put("model", "deepseek-chat")
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply { put("role", "system"); put("content", systemMessage) })
                        put(JSONObject().apply { put("role", "user"); put("content", userPrompt) })
                    })
                    put("max_tokens", 100)
                    put("temperature", 0.7)
                }

                val conn = URL("https://api.deepseek.com/chat/completions").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.doOutput = true
                OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

                if (conn.responseCode == 200) {
                    val response = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                    val content = JSONObject(response).getJSONArray("choices")
                        .getJSONObject(0).getJSONObject("message").getString("content").trim()
                    conn.disconnect()
                    content
                } else {
                    conn.disconnect()
                    "$userPrompt$AI_STICKER_SUFFIX"
                }
            } catch (e: Exception) {
                "$userPrompt$AI_STICKER_SUFFIX"
            }
        }

    private suspend fun aiGenerateRandomPrompt(): String = withContext(Dispatchers.IO) {
        try {
            val apiKey = BuildConfig.DEEPSEEK_API_KEY
            if (apiKey.isEmpty()) return@withContext randomFallbackPrompt()

            val systemMsg = """You are a creative sticker idea generator. Generate ONE detailed, fun, unique sticker concept description.
Rules:
- Output ONLY the sticker description, nothing else (no quotes, no explanation)
- Write 2-4 sentences describing a vivid, funny sticker scene
- Include: character description, emotion/expression, action, accessories/props, and setting details
- Be wildly creative, hilarious, and unexpected
- Make it visual and descriptive so an AI can generate an amazing sticker from it
- Examples of good output:
  "A chubby orange tabby cat wearing a tiny business suit, sitting at a desk piled with coffee cups, looking absolutely exhausted with dark circles under its huge eyes, papers flying everywhere"
  "A dramatic avocado wearing sunglasses and a gold chain, doing a mic drop on a tiny stage while other vegetables in the audience look shocked with their jaws dropping"
  "A tiny hamster in a superhero cape standing on top of a mountain of cheese, flexing its muscles with an absurdly confident expression while lightning strikes behind it"
- Each idea should be completely different and random"""

            val body = JSONObject().apply {
                put("model", "deepseek-chat")
                put("messages", JSONArray().apply {
                    put(JSONObject().apply { put("role", "system"); put("content", systemMsg) })
                    put(JSONObject().apply { put("role", "user"); put("content", "Give me a random detailed sticker idea. Be creative! Seed: ${System.currentTimeMillis()}") })
                })
                put("max_tokens", 100)
                put("temperature", 1.2)
            }

            val conn = URL("https://api.deepseek.com/chat/completions").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.doOutput = true
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

            if (conn.responseCode == 200) {
                val response = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val content = JSONObject(response).getJSONArray("choices")
                    .getJSONObject(0).getJSONObject("message").getString("content").trim()
                conn.disconnect()
                rememberAiInspiredPrompt(content)
            } else {
                conn.disconnect()
                randomFallbackPrompt()
            }
        } catch (_: Exception) {
            randomFallbackPrompt()
        }
    }

    private fun randomFallbackPrompt(): String {
        val subjects = listOf("toast detective", "moon barista", "tiny dragon chef", "sleepy cactus", "glitter robot", "angry cupcake", "wizard lemon", "skateboarding sushi", "opera banana", "pirate marshmallow", "nervous volcano", "disco mushroom", "astronaut jellybean", "royal potato", "paint-splattered ghost", "karate dumpling")
        val actions = listOf("solving a mystery", "spilling rainbow coffee", "guarding a treasure map", "dancing under neon lights", "launching confetti rockets", "arguing with a toaster", "painting stars in the sky", "surfing on a soap bubble", "carrying too many balloons", "celebrating a tiny victory", "dodging flying sprinkles", "posing for a dramatic poster")
        val expressions = listOf("wildly excited", "deeply suspicious", "proud and smug", "panicked but cute", "sleepy and confused", "overconfident", "dramatically shocked", "mischievous")
        val props = listOf("oversized sunglasses", "a tiny crown", "a glowing backpack", "sparkly boots", "a cracked teacup", "a miniature keyboard", "a neon scarf", "a golden spoon", "a space helmet", "a crooked party hat")
        val settings = listOf("inside a pastel arcade", "on a floating cloud kitchen", "in a tiny subway station", "beside a lava lamp waterfall", "at a midnight snack parade", "on a desk covered with sticky notes", "in a pocket-sized castle", "under a shower of candy stars")
        val prefs = getSharedPreferences("sticky_prefs", MODE_PRIVATE)
        val used = prefs.getStringSet("ai_inspire_used_prompts", emptySet())?.toMutableSet() ?: mutableSetOf()
        val last = prefs.getString("ai_inspire_last_prompt", "").orEmpty()
        if (used.size > 80) used.clear()
        repeat(24) {
            val prompt = "A ${expressions.random()} ${subjects.random()} ${actions.random()}, wearing ${props.random()}, ${settings.random()}, bold cartoon sticker style, clean white background, thick outline, vibrant colors"
            if (prompt != last && !used.contains(prompt)) {
                used.add(prompt)
                prefs.edit().putStringSet("ai_inspire_used_prompts", used).putString("ai_inspire_last_prompt", prompt).apply()
                return prompt
            }
        }
        used.clear()
        prefs.edit().putStringSet("ai_inspire_used_prompts", used).putString("ai_inspire_last_prompt", "").apply()
        return randomFallbackPrompt()
    }

    private fun rememberAiInspiredPrompt(prompt: String): String {
        val cleaned = prompt.trim().replace("\"", "")
        if (cleaned.isBlank()) return randomFallbackPrompt()
        val prefs = getSharedPreferences("sticky_prefs", MODE_PRIVATE)
        val used = prefs.getStringSet("ai_inspire_used_prompts", emptySet())?.toMutableSet() ?: mutableSetOf()
        val last = prefs.getString("ai_inspire_last_prompt", "").orEmpty()
        if (cleaned == last || used.contains(cleaned)) return randomFallbackPrompt()
        if (used.size > 80) used.clear()
        used.add(cleaned)
        prefs.edit().putStringSet("ai_inspire_used_prompts", used).putString("ai_inspire_last_prompt", cleaned).apply()
        return cleaned
    }

    private suspend fun aiGenerateImage(prompt: String, onPoll: () -> Unit): Bitmap? =
        withContext(Dispatchers.IO) {
            suspend fun raceProviders(timeoutMs: Long, providers: List<suspend () -> Bitmap?>): Bitmap? =
                kotlinx.coroutines.coroutineScope {
                    val resultChannel = kotlinx.coroutines.channels.Channel<Bitmap?>(providers.size)
                    val jobs = providers.map { provider ->
                        launch(Dispatchers.IO) {
                            val bitmap = kotlinx.coroutines.withTimeoutOrNull(timeoutMs - 1_000) {
                                runCatching { provider() }.getOrNull()
                            }
                            resultChannel.trySend(bitmap)
                        }
                    }

                    val winner = kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
                        repeat(providers.size) {
                            val result = resultChannel.receiveCatching().getOrNull()
                            if (result != null) return@withTimeoutOrNull result
                        }
                        null
                    }
                    jobs.forEach { it.cancel() }
                    resultChannel.close()
                    winner
                }

            raceProviders(
                timeoutMs = 28_000,
                providers = listOf(
                    { aiPollinationsRequest(prompt, "turbo") },
                    { aiPollinationsRequest(prompt, "flux") }
                )
            ) ?: run {
                onPoll()
                kotlinx.coroutines.delay(800)
                raceProviders(
                    timeoutMs = 34_000,
                    providers = listOf(
                        { aiPollinationsRequest(prompt, "turbo") },
                        { aiApiAirforceRequest(prompt, "z-image") },
                        { aiApiAirforceRequest(prompt, "flux-2-dev") },
                        { aiApiAirforceRequest(prompt, "grok-imagine") }
                    )
                )
            }
        }

    private suspend fun aiPollinationsRequest(prompt: String, model: String = "turbo"): Bitmap? =
        withContext(Dispatchers.IO) {
            val encodedPrompt = java.net.URLEncoder.encode(prompt, "UTF-8")
            try {
                android.util.Log.d("AiGenerate", "Pollinations ($model) request")
                val conn = URL("https://image.pollinations.ai/prompt/$encodedPrompt?width=512&height=512&nologo=true&model=$model&seed=${System.currentTimeMillis()}").openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 15000
                conn.readTimeout = 25000
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "StickyApp/1.0")
                val code = conn.responseCode
                android.util.Log.d("AiGenerate", "Pollinations ($model) response: $code")
                if (code == 200) {
                    val bitmap = BitmapFactory.decodeStream(conn.inputStream)
                    conn.disconnect()
                    if (bitmap != null) return@withContext bitmap
                }
                conn.disconnect()
                null
            } catch (e: Exception) {
                android.util.Log.e("AiGenerate", "Pollinations $model failed: ${e.message}")
                null
            }
        }

    private suspend fun aiApiAirforceRequest(prompt: String, model: String): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("AiGenerate", "api.airforce ($model) request")
                val body = JSONObject().apply {
                    put("model", model)
                    put("prompt", prompt)
                    put("size", "512x512")
                    put("n", 1)
                }

                val conn = URL("https://api.airforce/v1/images/generations").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 12000
                conn.readTimeout = 30000
                conn.doOutput = true
                OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

                val code = conn.responseCode
                android.util.Log.d("AiGenerate", "api.airforce ($model) response: $code")

                if (code == 429 || code != 200) { conn.disconnect(); return@withContext null }

                val response = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                conn.disconnect()

                val data = JSONObject(response).optJSONArray("data")
                if (data == null || data.length() == 0) return@withContext null

                val imageUrl = data.getJSONObject(0).optString("url", "")
                if (imageUrl.isEmpty()) return@withContext null

                android.util.Log.d("AiGenerate", "api.airforce ($model) downloading image")
                val imgConn = URL(imageUrl).openConnection() as HttpURLConnection
                imgConn.connectTimeout = 10000
                imgConn.readTimeout = 15000
                imgConn.instanceFollowRedirects = true

                val bitmap = if (imgConn.responseCode == 200) BitmapFactory.decodeStream(imgConn.inputStream) else null
                imgConn.disconnect()
                if (bitmap != null) android.util.Log.d("AiGenerate", "api.airforce ($model) success!")
                bitmap
            } catch (e: Exception) {
                android.util.Log.e("AiGenerate", "api.airforce $model: ${e.message}")
                null
            }
        }

    private suspend fun aiProcessSticker(source: Bitmap): Bitmap =
        withContext(Dispatchers.IO) {
            val scaled = Bitmap.createScaledBitmap(source, 512, 512, true)
            val result = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(512 * 512)
            scaled.getPixels(pixels, 0, 512, 0, 0, 512, 512)
            for (i in pixels.indices) {
                val r = Color.red(pixels[i]); val g = Color.green(pixels[i]); val b = Color.blue(pixels[i])
                if (r > 240 && g > 240 && b > 240) {
                    pixels[i] = Color.TRANSPARENT
                } else if (r > 220 && g > 220 && b > 220) {
                    val alpha = ((255 - r) + (255 - g) + (255 - b)) * 255 / (35 * 3)
                    pixels[i] = Color.argb(alpha.coerceIn(0, 255), r, g, b)
                }
            }
            result.setPixels(pixels, 0, 512, 0, 0, 512, 512)
            if (!scaled.isRecycled && scaled !== source) scaled.recycle()
            result
        }

    private fun aiShowPackPickerDialog() {
        val bitmap = aiGeneratedBitmap ?: return
        val view = layoutInflater.inflate(R.layout.dialog_select_pack, null)
        val rvPacks = view.findViewById<RecyclerView>(R.id.rvPacks)
        val inputPackName = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.inputPackName)
        val btnCreatePack = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCreatePack)

        val packs = CustomStickerManager.getCustomPacks(this).filter { !it.isAnimated }

        val dialog = AlertDialog.Builder(this, R.style.MaterialAlertDialogTheme)
            .setView(view)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        if (packs.isNotEmpty()) {
            rvPacks.visibility = View.VISIBLE
            rvPacks.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
            rvPacks.adapter = AiPackSelectionAdapter(packs) { pack ->
                dialog.dismiss()
                aiSaveStickerToPack(pack.id, bitmap)
            }
        }

        btnCreatePack.setOnClickListener {
            val packName = inputPackName.text?.toString()?.trim() ?: ""
            if (packName.isEmpty()) {
                inputPackName.error = getString(R.string.enter_pack_name)
                return@setOnClickListener
            }
            dialog.dismiss()
            val newPackId = CustomStickerManager.createPack(this, packName, false)
            aiSaveStickerToPack(newPackId, bitmap)
        }

        dialog.show()
    }

    private fun aiSaveStickerToPack(packId: String, bitmap: Bitmap) {
        val aiEtPrompt = findViewById<EditText>(R.id.aiEtPrompt)
        val aiPreviewCard = findViewById<CardView>(R.id.aiPreviewCard)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val success = CustomStickerManager.addStickerToPack(this@MainActivity, packId, bitmap)
                if (success) {
                    // Only refresh the changed pack, not all custom packs
                    val updatedPack = CustomStickerManager.toWhatsAppPack(this@MainActivity, packId)?.copy(category = "custom")
                    if (updatedPack != null) {
                        allPacks = allPacks.map { if (it.id == packId) updatedPack else it }.let { list ->
                            if (list.none { it.id == packId }) list + updatedPack else list
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(this@MainActivity, R.string.ai_saved_success, Toast.LENGTH_SHORT).show()
                        aiGeneratedBitmap = null
                        aiRawBitmap = null
                        aiPreviewCard?.visibility = View.GONE
                        aiEtPrompt?.text?.clear()
                        // Full refresh to show new pack immediately
                        refreshPacks()
                        // Navigate to My Stickers tab
                        currentFilter = FilterType.CUSTOM
                        categoryChipGroup.visibility = View.GONE
                        hideHomeSections()
                        updateBottomNavUI()
                        applyFilters()
                    } else {
                        Toast.makeText(this@MainActivity, R.string.ai_generation_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, e.message ?: getString(R.string.ai_generation_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ─── AI History ─────────────────────────────────────────────────────

    private fun aiGetHistoryDir(): java.io.File {
        val dir = java.io.File(filesDir, "ai_history")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private suspend fun aiSaveToHistory(bitmap: Bitmap, prompt: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val dir = aiGetHistoryDir()
                val fileName = "ai_${System.currentTimeMillis()}.webp"
                val file = java.io.File(dir, fileName)
                java.io.FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.WEBP, 90, out)
                }
                // Save metadata
                val metaFile = java.io.File(dir, "history.json")
                val arr = try {
                    if (metaFile.exists()) JSONArray(metaFile.readText()) else JSONArray()
                } catch (_: Exception) { JSONArray() }
                arr.put(JSONObject().apply {
                    put("file", fileName)
                    put("prompt", prompt)
                    put("time", System.currentTimeMillis())
                })
                metaFile.writeText(arr.toString())
                file.absolutePath
            } catch (_: Exception) { null }
        }

    private suspend fun aiSyncHistoryToFirebase(prompt: String, filePath: String) =
        withContext(Dispatchers.IO) {
            try {
                val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser ?: return@withContext
                PocketBaseHelper.createRecord("ai_history", org.json.JSONObject().apply {
                    put("uid", user.uid)
                    put("prompt", prompt)
                    put("file_name", java.io.File(filePath).name)
                    put("created_at", java.time.Instant.now().toString())
                })
            } catch (_: Exception) { }
        }

    private fun aiLoadHistory() {
        val rvHistory = findViewById<RecyclerView>(R.id.rvAiHistory)
        val tvTitle = findViewById<TextView>(R.id.tvAiHistoryTitle)
        if (rvHistory == null || tvTitle == null) return

        lifecycleScope.launch(Dispatchers.IO) {
            val dir = aiGetHistoryDir()
            val metaFile = java.io.File(dir, "history.json")
            val items = mutableListOf<AiHistoryItem>()
            try {
                if (metaFile.exists()) {
                    val arr = JSONArray(metaFile.readText())
                    for (i in (arr.length() - 1) downTo 0) {
                        val obj = arr.getJSONObject(i)
                        val file = java.io.File(dir, obj.getString("file"))
                        if (file.exists()) {
                            items.add(AiHistoryItem(file.absolutePath, obj.optString("prompt", ""), obj.optLong("time", 0)))
                        }
                    }
                }
            } catch (_: Exception) { }

            // Load sample prompts from manifest
            val sampleItems = mutableListOf<AiHistoryItem>()
            try {
                val manifestJson = assets.open("ai_showcase/manifest.json").bufferedReader().readText()
                val arr = JSONArray(manifestJson)
                val count = minOf(arr.length(), 6)
                android.util.Log.d("AI_SHOWCASE", "Manifest loaded: ${arr.length()} items, showing $count")
                for (i in 0 until count) {
                    val obj = arr.getJSONObject(i)
                    sampleItems.add(AiHistoryItem(
                        "ai_showcase/${obj.getString("id")}.webp",
                        obj.optString("prompt", ""),
                        0L,
                        isAsset = true
                    ))
                }
            } catch (e: Exception) {
                android.util.Log.e("AI_SHOWCASE", "Manifest load failed: ${e.message}", e)
                for (i in 1..6) {
                    sampleItems.add(AiHistoryItem("ai_showcase/${String.format("%02d", i)}.webp", "", 0L, isAsset = true))
                }
            }
            android.util.Log.d("AI_SHOWCASE", "History items: ${items.size}, Sample items: ${sampleItems.size}")

            withContext(Dispatchers.Main) {
                // Show user creations if any
                if (items.isNotEmpty()) {
                    tvTitle.visibility = View.VISIBLE
                    tvTitle.text = getString(R.string.ai_your_creations)
                    rvHistory.visibility = View.VISIBLE
                    rvHistory.isNestedScrollingEnabled = false
                    aiHistoryAdapter = AiHistoryAdapter(items.toMutableList()) { item ->
                        aiShowHistoryItemOptions(item)
                    }
                    val gridLm = androidx.recyclerview.widget.GridLayoutManager(this@MainActivity, 3)
                    rvHistory.layoutManager = gridLm
                    rvHistory.adapter = aiHistoryAdapter
                } else {
                    tvTitle.visibility = View.GONE
                    rvHistory.visibility = View.GONE
                }

                // Always show AI Examples section
                val rvExamples = findViewById<RecyclerView>(R.id.rvAiExamples)
                val tvExamplesTitle = findViewById<TextView>(R.id.tvAiExamplesTitle)
                if (rvExamples != null && tvExamplesTitle != null && sampleItems.isNotEmpty()) {
                    tvExamplesTitle.visibility = View.VISIBLE
                    rvExamples.visibility = View.VISIBLE
                    rvExamples.isNestedScrollingEnabled = false
                    val exAdapter = AiHistoryAdapter(sampleItems.toMutableList()) { item ->
                        aiShowHistoryItemOptions(item)
                    }
                    rvExamples.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this@MainActivity, 3)
                    rvExamples.adapter = exAdapter
                }
            }
        }
    }

    private fun aiShowHistoryItemOptions(item: AiHistoryItem) {
        val bmp: android.graphics.Bitmap? = if (item.isAsset) {
            try {
                val inputStream = assets.open(item.path)
                android.graphics.BitmapFactory.decodeStream(inputStream).also { inputStream.close() }
            } catch (_: Exception) { null }
        } else {
            val file = java.io.File(item.path)
            if (file.exists()) android.graphics.BitmapFactory.decodeFile(file.absolutePath) else null
        }
        if (bmp == null) return

        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.TransparentBottomSheetDialog)
        val sheetView = LayoutInflater.from(this).inflate(R.layout.dialog_ai_preview, null)

        // Image
        sheetView.findViewById<ImageView>(R.id.ivPreview)?.setImageBitmap(bmp)

        // Prompt — full text, tap to copy
        val promptSection = sheetView.findViewById<View>(R.id.promptSection)
        val tvPrompt = sheetView.findViewById<TextView>(R.id.tvPrompt)
        if (item.prompt.isNotEmpty()) {
            promptSection?.visibility = View.VISIBLE
            tvPrompt?.text = item.prompt
            promptSection?.setOnClickListener {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("AI Prompt", item.prompt))
                Toast.makeText(this, "📋 Prompt copied!", Toast.LENGTH_SHORT).show()
            }
        }

        val btnEdit = sheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnUsePrompt)
        val btnAddToPack = sheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAddToPack)
        val btnRegenerate = sheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnRegenerate)
        val btnDelete = sheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDelete)

        // Edit — open sticker editor with this image
        btnEdit?.setOnClickListener {
            dialog.dismiss()
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val tempFile = java.io.File(cacheDir, "ai_edit_temp.png")
                    java.io.FileOutputStream(tempFile).use { out ->
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    withContext(Dispatchers.Main) {
                        val intent = Intent(this@MainActivity, StickerMakerActivity::class.java)
                        intent.putExtra("editImageUri", android.net.Uri.fromFile(tempFile).toString())
                        startActivity(intent)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // Add to Pack
        btnAddToPack?.setOnClickListener {
            aiRawBitmap = bmp
            aiGeneratedBitmap = bmp
            dialog.dismiss()
            aiShowPackPickerDialog()
        }

        // Regenerate — fill prompt and trigger generation
        btnRegenerate?.setOnClickListener {
            if (item.prompt.isNotEmpty()) {
                findViewById<android.widget.EditText>(R.id.aiEtPrompt)?.setText(item.prompt)
            }
            dialog.dismiss()
            // Trigger generation with the same prompt
            findViewById<com.google.android.material.button.MaterialButton>(R.id.aiBtnGenerate)?.performClick()
        }

        // Delete
        btnDelete?.setOnClickListener {
            if (item.isAsset) {
                Toast.makeText(this, "Sample images can't be deleted", Toast.LENGTH_SHORT).show()
            } else {
                aiDeleteHistoryItem(item)
                dialog.dismiss()
            }
        }

        dialog.setContentView(sheetView)
        dialog.behavior.skipCollapsed = true
        dialog.behavior.isFitToContents = true
        dialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        dialog.show()
    }

    private fun aiDeleteHistoryItem(item: AiHistoryItem) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Delete image file
                val file = java.io.File(item.path)
                if (file.exists()) file.delete()

                // Update history.json
                val dir = aiGetHistoryDir()
                val metaFile = java.io.File(dir, "history.json")
                if (metaFile.exists()) {
                    val arr = JSONArray(metaFile.readText())
                    val newArr = JSONArray()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        if (obj.getString("file") != file.name) {
                            newArr.put(obj)
                        }
                    }
                    metaFile.writeText(newArr.toString())
                }
            } catch (_: Exception) { }

            withContext(Dispatchers.Main) {
                // Reload the full history grid
                aiLoadHistory()
            }
        }
    }

    data class AiHistoryItem(val path: String, val prompt: String, val time: Long, val isAsset: Boolean = false)

    inner class AiHistoryAdapter(
        private var items: MutableList<AiHistoryItem>,
        private val onClick: (AiHistoryItem) -> Unit
    ) : RecyclerView.Adapter<AiHistoryAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val iv: ImageView = view as ImageView
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val spacing = 8 // 4dp margin on each side
            val parentWidth = if (parent.width > 0) parent.width else (parent.context.resources.displayMetrics.widthPixels - (32 * parent.context.resources.displayMetrics.density).toInt())
            val itemSize = (parentWidth - spacing * 3 * 2) / 3
            val iv = ImageView(parent.context).apply {
                layoutParams = ViewGroup.MarginLayoutParams(itemSize, itemSize).apply {
                    setMargins(4, 4, 4, 4)
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundResource(R.drawable.bg_sticker_preview_ai)
            }
            return VH(iv)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            if (item.isAsset) {
                com.bumptech.glide.Glide.with(holder.iv)
                    .load(android.net.Uri.parse("file:///android_asset/${item.path}"))
                    .override(256)
                    .centerCrop()
                    .signature(com.bumptech.glide.signature.ObjectKey("v2_${item.path}"))
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.RESOURCE)
                    .into(holder.iv)
            } else {
                com.bumptech.glide.Glide.with(holder.iv)
                    .load(java.io.File(item.path))
                    .centerCrop()
                    .into(holder.iv)
            }
            holder.iv.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = items.size

        fun updateItems(newItems: List<AiHistoryItem>) {
            val oldItems = items
            val newList = newItems.toMutableList()
            val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize() = oldItems.size
                override fun getNewListSize() = newList.size
                override fun areItemsTheSame(o: Int, n: Int) = oldItems[o].path == newList[n].path
                override fun areContentsTheSame(o: Int, n: Int) = oldItems[o] == newList[n]
            }, false)
            items = newList
            diff.dispatchUpdatesTo(this)
        }
    }

    // Pack selection adapter for AI sticker save dialog
    inner class AiPackSelectionAdapter(
        private val packs: List<CustomStickerManager.CustomPack>,
        private val onPackClick: (CustomStickerManager.CustomPack) -> Unit
    ) : RecyclerView.Adapter<AiPackSelectionAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvPackName)
            val tvCount: TextView = view.findViewById(R.id.tvPackCount)
            val ivCover: ImageView = view.findViewById(R.id.ivPackCover)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            return VH(LayoutInflater.from(parent.context).inflate(R.layout.item_pack_selection, parent, false))
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val pack = packs[position]
            holder.tvName.text = pack.name
            holder.tvCount.text = "${pack.stickerCount}${getString(R.string.sticker_count_suffix)}"

            if (pack.stickerCount > 0) {
                val stickerFile = CustomStickerManager.getCustomStickerPath(
                    holder.itemView.context, pack.id, "sticker_1.webp"
                )
                com.bumptech.glide.Glide.with(holder.itemView.context)
                    .load(stickerFile)
                    .placeholder(R.drawable.ic_sticker_placeholder)
                    .error(R.drawable.ic_sticker_placeholder)
                    .into(holder.ivCover)
            } else {
                holder.ivCover.setImageResource(R.drawable.ic_sticker_placeholder)
            }

            holder.itemView.setOnClickListener { onPackClick(pack) }
        }

        override fun getItemCount() = packs.size
    }

    private fun aiShowDailyLimitDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_daily_limit, null)
        view.findViewById<TextView>(R.id.tvLimitMsg).text =
            getString(R.string.ai_daily_limit_msg, AI_DAILY_FREE_LIMIT)

        val dialog = AlertDialog.Builder(this, R.style.MaterialAlertDialogTheme)
            .setView(view)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        view.findViewById<View>(R.id.btnGetPremiumLimit).setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, PremiumActivity::class.java))
        }
        view.findViewById<View>(R.id.btnCancelLimit).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showAiSignInDialog() {
        val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
            com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
        )
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        val client = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(this, gso)
        aiSignInLauncher.launch(client.signInIntent)
    }

    // ─── Profile Logic ──────────────────────────────────────────────────

    private fun ensureProfileInflated() {
        if (profileContentContainer != null) return
        try {
            val stub = findViewById<android.view.ViewStub>(R.id.profileContentStub)
            stub?.inflate()
        } catch (_: Exception) { /* already inflated */ }
        profileContentContainer = findViewById(R.id.profileContentContainer)
        setupProfileContent()
    }

    private fun setupProfileContent() {
        if (profileSetupDone) return
        profileSetupDone = true

        val btnSharePack = findViewById<MaterialButton>(R.id.btnShareStickerPack)
        val btnLogin = findViewById<MaterialButton>(R.id.btnProfileLogin)
        val btnNotifications = findViewById<android.widget.ImageButton>(R.id.btnProfileNotifications)

        btnSharePack?.setOnClickListener {
            val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (user == null) {
                Toast.makeText(this, getString(R.string.profile_login_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            tabMyStickers.performClick()
        }

        btnLogin?.setOnClickListener {
            profileSignIn()
        }

        btnNotifications?.setOnClickListener {
            clearNotificationBadges()
            startActivity(Intent(this, NotificationsActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    private fun isGoogleProfileSignedIn(): Boolean {
        val prefs = getSharedPreferences("sticky_prefs", MODE_PRIVATE)
        return !prefs.getString("user_email", "")?.trim().isNullOrEmpty()
    }

    private fun installCenteredListPadding(list: RecyclerView) {
        val minPadding = (8 * resources.displayMetrics.density).toInt()
        val maxContentWidth = (620 * resources.displayMetrics.density).toInt()
        list.clipToPadding = false
        list.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            val sidePadding = ((view.width - maxContentWidth) / 2).coerceAtLeast(minPadding)
            if (view.paddingLeft != sidePadding || view.paddingRight != sidePadding) {
                view.setPadding(sidePadding, view.paddingTop, sidePadding, view.paddingBottom)
            }
        }
    }

    private fun showEditProfileDialog() {
        val prefs = getSharedPreferences("sticky_prefs", MODE_PRIVATE)
        val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 24.dpToPx(), 24.dpToPx(), 20.dpToPx())
            background = androidx.core.content.ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_dialog_rounded)
        }
        content.addView(TextView(this).apply {
            text = getString(R.string.profile_edit)
            textSize = 22f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(getColor(R.color.text_primary))
        })

        fun styledInput(hintText: String, value: String, singleLine: Boolean = true): EditText = EditText(this).apply {
            hint = hintText
            setText(value)
            setSingleLine(singleLine)
            if (!singleLine) {
                minLines = 2
                maxLines = 4
                setSingleLine(false)
            }
            textSize = 15f
            background = androidx.core.content.ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_modern_input)
            setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 12.dpToPx())
        }

        val nameInput = styledInput(getString(R.string.profile_display_name), prefs.getString("user_display_name", "") ?: "")
        val bioInput = styledInput(getString(R.string.profile_bio_hint), prefs.getString("user_bio", "") ?: "", singleLine = false)
        val showEmail = CheckBox(this).apply {
            text = getString(R.string.profile_show_email)
            isChecked = prefs.getBoolean("user_show_email", true)
            setTextColor(getColor(R.color.text_primary))
        }

        listOf(nameInput, bioInput).forEach { input ->
            content.addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 12.dpToPx() })
        }
        content.addView(showEmail, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 8.dpToPx() })

        val dialog = AlertDialog.Builder(this)
            .setView(content)
            .create()

        fun animateButton(button: View, after: () -> Unit) {
            button.animate().scaleX(0.96f).scaleY(0.96f).alpha(0.85f).setDuration(80).withEndAction {
                button.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(120).withEndAction { after() }.start()
            }.start()
        }

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.END }
        val cancel = MaterialButton(this).apply {
            text = getString(R.string.cancel)
            isAllCaps = false
            backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.chip_bg))
            setTextColor(getColor(R.color.text_primary))
            cornerRadius = 22.dpToPx()
            minHeight = 44.dpToPx()
            setOnClickListener { animateButton(this) { dialog.dismiss() } }
        }
        val save = MaterialButton(this).apply {
            text = getString(R.string.save)
            isAllCaps = false
            setTextColor(getColor(R.color.white))
            backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.primary))
            cornerRadius = 22.dpToPx()
            minHeight = 44.dpToPx()
            setOnClickListener {
                animateButton(this) {
                    isEnabled = false
                    val displayName = nameInput.text.toString().trim()
                    val bio = bioInput.text.toString().trim()
                    val photoUrl = prefs.getString("user_photo_url", "")?.takeIf { it.isNotBlank() } ?: firebaseUser?.photoUrl?.toString().orEmpty()
                    prefs.edit()
                        .putString("user_display_name", displayName)
                        .putString("user_bio", bio)
                        .putBoolean("user_show_email", showEmail.isChecked)
                        .apply()
                    lifecycleScope.launch {
                        runCatching {
                            SocialRepository.updateProfile(this@MainActivity, displayName, bio, showEmail.isChecked, photoUrl)
                        }.onFailure { error ->
                            Snackbar.make(rv, error.message ?: getString(R.string.profile_update_failed), Snackbar.LENGTH_SHORT).show()
                        }
                        loadProfileData()
                        dialog.dismiss()
                    }
                }
            }
        }
        buttons.addView(cancel, LinearLayout.LayoutParams(108.dpToPx(), 44.dpToPx()))
        buttons.addView(save, LinearLayout.LayoutParams(112.dpToPx(), 44.dpToPx()).apply { marginStart = 8.dpToPx() })
        content.addView(buttons, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 12.dpToPx() })
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.window?.setDimAmount(0.38f)
        }
        dialog.show()
    }

    private fun loadProfileData() {
        val deviceId = PreferencesHelper.getDeviceId(this)
        val loginPrompt = findViewById<View>(R.id.profileLoginPrompt)
        profileContentContainer ?: return

        val avatar = findViewById<ImageView>(R.id.profileAvatar)
        val displayName = findViewById<TextView>(R.id.profileDisplayName)
        val email = findViewById<TextView>(R.id.profileEmail)
        val bio = findViewById<TextView>(R.id.profileBio)
        val btnEditProfile = findViewById<MaterialButton>(R.id.btnEditProfile)
        val statsRow = findViewById<View>(R.id.profileStatsRow)
        val btnSharePack = findViewById<MaterialButton>(R.id.btnShareStickerPack)
        val btnNotifications = findViewById<android.widget.ImageButton>(R.id.btnProfileNotifications)
        val packsTitle = findViewById<TextView>(R.id.profilePacksTitle)
        val rvPublished = findViewById<RecyclerView>(R.id.rvPublishedPacks)
        val emptyState = findViewById<View>(R.id.profileEmptyState)
        val adminMessages = findViewById<View>(R.id.adminMessagesContainer)

        val avatarCard = findViewById<View>(R.id.profileAvatarCard)
        if (!isGoogleProfileSignedIn()) {
            loginPrompt?.visibility = View.VISIBLE
            statsRow?.visibility = View.GONE
            btnSharePack?.visibility = View.GONE
            btnNotifications?.visibility = View.GONE
            packsTitle?.visibility = View.GONE
            rvPublished?.visibility = View.GONE
            emptyState?.visibility = View.GONE
            adminMessages?.visibility = View.GONE
            avatarCard?.visibility = View.GONE
            avatar?.visibility = View.GONE
            displayName?.visibility = View.GONE
            bio?.visibility = View.GONE
            btnEditProfile?.visibility = View.GONE
            email?.text = ""
            return
        }
        avatarCard?.visibility = View.VISIBLE

        loginPrompt?.visibility = View.GONE
        statsRow?.visibility = View.VISIBLE
        btnSharePack?.visibility = View.VISIBLE
        btnNotifications?.visibility = View.VISIBLE
        packsTitle?.visibility = View.VISIBLE
        avatar?.visibility = View.VISIBLE
        displayName?.visibility = View.VISIBLE
        bio?.visibility = View.VISIBLE
        btnEditProfile?.visibility = View.VISIBLE

        val prefs = getSharedPreferences("sticky_prefs", MODE_PRIVATE)
        val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val profileEmail = prefs.getString("user_email", "") ?: firebaseUser?.email.orEmpty()
        displayName?.text = prefs.getString("user_display_name", getString(R.string.profile_guest))
        bio?.text = prefs.getString("user_bio", "") ?: ""
        email?.text = if (prefs.getBoolean("user_show_email", true)) profileEmail else ""
        btnEditProfile?.setOnClickListener { showEditProfileDialog() }
        findViewById<TextView>(R.id.statDownloads)?.setOnClickListener { openProfileSocialList("followers") }
        findViewById<TextView>(R.id.statFollowing)?.setOnClickListener { openProfileSocialList("following") }
        findViewById<TextView>(R.id.statFavorites)?.setOnClickListener { openProfileSocialList("likes") }

        val photoUrl = prefs.getString("user_photo_url", null)?.takeIf { it.isNotBlank() } ?: firebaseUser?.photoUrl?.toString()
        if (photoUrl != null) {
            avatar?.let {
                com.bumptech.glide.Glide.with(this)
                    .load(photoUrl)
                    .circleCrop()
                    .placeholder(R.drawable.ic_person)
                    .into(it)
            }
        }

        // Ensure user profile exists on server
        lifecycleScope.launch {
            // Load user submissions FIRST so they always appear regardless of profile fetch errors
            loadUserSubmissionsFromPB(deviceId, firebaseUser?.uid.orEmpty(), profileEmail, rvPublished, emptyState)
            loadAdminNotificationsFromPB(deviceId, firebaseUser?.uid.orEmpty(), profileEmail)

            // Try to fetch/create user profile — failures here should not hide submissions
            runCatching {
                val records = withContext(Dispatchers.IO) {
                    PocketBaseHelper.listRecords("user_profiles", filter = "device_id='$deviceId'")
                }
                val doc = records.firstOrNull()

                if (doc == null) {
                    val profileData = org.json.JSONObject().apply {
                        put("device_id", deviceId)
                        put("display_name", prefs.getString("user_display_name", "") ?: "")
                        put("email", prefs.getString("user_email", "") ?: "")
                        put("photo_url", photoUrl ?: "")
                        put("bio", prefs.getString("user_bio", "") ?: "")
                        put("show_email", prefs.getBoolean("user_show_email", true))
                        put("packs_published", 0)
                        put("total_downloads", 0)
                        put("total_favorites", 0)
                        put("joined_at", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).format(java.util.Date()))
                    }
                    withContext(Dispatchers.IO) {
                        PocketBaseHelper.createRecord("user_profiles", profileData)
                    }
                    withContext(Dispatchers.Main) {
                        findViewById<TextView>(R.id.statPublished)?.text = "0"
                        findViewById<TextView>(R.id.statDownloads)?.text = "0"
                        findViewById<TextView>(R.id.statFollowing)?.text = "0"
                        findViewById<TextView>(R.id.statFavorites)?.text = "0"
                    }
                } else {
                    val published = doc.optInt("packs_published", 0)
                    val serverBio = doc.optString("bio", prefs.getString("user_bio", "") ?: "")
                    val showEmail = doc.optBoolean("show_email", prefs.getBoolean("user_show_email", true))
                    withContext(Dispatchers.Main) {
                        prefs.edit()
                            .putString("user_bio", serverBio)
                            .putBoolean("user_show_email", showEmail)
                            .apply()
                        bio?.text = serverBio
                        email?.text = if (showEmail) profileEmail else ""
                        findViewById<TextView>(R.id.statPublished)?.text = published.toString()
                        findViewById<TextView>(R.id.statDownloads)?.text = "0"
                        findViewById<TextView>(R.id.statFollowing)?.text = "0"
                        findViewById<TextView>(R.id.statFavorites)?.text = "0"
                    }
                }

                val social = runCatching {
                    SocialRepository.fetchPublisherProfile(
                        Pack(id = "", name = "", pub = prefs.getString("user_display_name", "") ?: "", email = profileEmail, publisherUserId = firebaseUser?.uid.orEmpty()),
                        this@MainActivity
                    )
                }.getOrDefault(org.json.JSONObject())

                val stats = social.optJSONObject("stats")
                withContext(Dispatchers.Main) {
                    if (social.length() > 0) profileSocialJson = social
                    stats?.let {
                        findViewById<TextView>(R.id.statPublished)?.text = it.optInt("packs", 0).toString()
                        findViewById<TextView>(R.id.statDownloads)?.text = it.optInt("followers", 0).toString()
                        findViewById<TextView>(R.id.statFollowing)?.text = it.optInt("following", 0).toString()
                        findViewById<TextView>(R.id.statFavorites)?.text = it.optInt("likes", 0).toString()
                    }
                }
            }.onFailure { e ->
                android.util.Log.e("Profile", "Error loading profile metadata: ${e.message}", e)
            }
        }
    }

    private fun loadUserSubmissionsFromPB(deviceId: String, userId: String, email: String, rv: RecyclerView?, emptyState: View?) {
        lifecycleScope.launch {
            try {
                val submissionItems = withContext(Dispatchers.IO) {
                    val records = PocketBaseHelper.listRecords("user_submissions", filter = ownerFilter(deviceId, userId, email))
                    records.mapNotNull { record ->
                        val name = record.optString("pack_name", record.optString("name", "")).takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        val stickers = record.optJSONArray("stickers") ?: record.optJSONArray("sticker_data")
                        val stickerUrls = mutableListOf<String>()
                        if (stickers != null) {
                            for (i in 0 until minOf(stickers.length(), 6)) {
                                val obj = stickers.optJSONObject(i) ?: continue
                                val url = obj.optString("image_url", obj.optString("url", ""))
                                if (url.isNotBlank()) stickerUrls.add(url)
                            }
                        }
                        SubmissionItem(
                            id = record.optString("id"),
                            name = name,
                            status = record.optString("status", "pending"),
                            stickerCount = record.optInt("sticker_count", stickers?.length() ?: 0),
                            rejectionReason = record.optString("rejection_reason").takeIf { it.isNotBlank() },
                            createdAt = record.optString("created_at", record.optString("created", "")).takeIf { it.isNotBlank() },
                            stickerUrls = stickerUrls,
                            storePackId = record.optString("sticker_pack_id").takeIf { it.isNotBlank() }
                        )
                    }

                }
                val social = runCatching {
                    withContext(Dispatchers.IO) {
                        SocialRepository.fetchPublisherProfile(
                            Pack(id = "", name = "", pub = getSharedPreferences("sticky_prefs", MODE_PRIVATE).getString("user_display_name", "") ?: "", email = email, publisherUserId = userId),
                            this@MainActivity
                        )
                    }
                }.getOrDefault(JSONObject())
                val socialPacks = social.optJSONArray("packs") ?: JSONArray()
                val socialItems = (0 until socialPacks.length()).mapNotNull { index ->
                    val pack = socialPacks.optJSONObject(index) ?: return@mapNotNull null
                    val packId = pack.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val stickers = pack.optJSONArray("stickers") ?: JSONArray()
                    SubmissionItem(
                        id = packId,
                        name = pack.optString("name", packId),
                        status = "approved",
                        stickerCount = pack.optInt("sticker_count", stickers.length()),
                        rejectionReason = null,
                        createdAt = pack.optString("created_at", pack.optString("created", "")).takeIf { it.isNotBlank() },
                        stickerUrls = (0 until minOf(stickers.length(), 6)).mapNotNull { stickers.optJSONObject(it)?.optString("url")?.takeIf { url -> url.isNotBlank() } },
                        storePackId = packId,
                        downloadCount = pack.optInt("download_count", 0),
                        favoriteCount = pack.optInt("favorite_count", 0),
                        likeCount = pack.optInt("like_count", 0),
                        commentCount = pack.optInt("comment_count", 0)
                    )
                }
                val knownStoreIds = submissionItems.mapNotNull { it.storePackId }.toSet()
                val knownNames = submissionItems.map { it.name.lowercase() }.toSet()
                val allItems = (submissionItems + socialItems.filter { it.storePackId !in knownStoreIds && it.name.lowercase() !in knownNames })
                    .sortedByDescending { it.createdAt ?: "" }
                val items = allItems.filter { it.id !in deletedSubmissionIds && (it.storePackId == null || it.storePackId !in deletedSubmissionIds) }
                val approvedCount = items.count { it.status == "approved" }
                val stats = social.optJSONObject("stats")
                withContext(Dispatchers.Main) {
                    if (social.length() > 0 && profileSocialJson == null) profileSocialJson = social
                    if (items.isEmpty()) {
                        rv?.visibility = View.GONE
                        emptyState?.visibility = View.VISIBLE
                    } else {
                        rv?.visibility = View.VISIBLE
                        emptyState?.visibility = View.GONE
                        if (rv?.layoutManager == null) rv?.layoutManager = LinearLayoutManager(this@MainActivity)
                        rv?.adapter = SubmissionAdapter(items)
                    }
                    findViewById<TextView>(R.id.statPublished)?.text = maxOf(approvedCount, stats?.optInt("packs", 0) ?: 0).toString()
                    stats?.let {
                        findViewById<TextView>(R.id.statDownloads)?.text = it.optInt("followers", 0).toString()
                        findViewById<TextView>(R.id.statFollowing)?.text = it.optInt("following", 0).toString()
                        findViewById<TextView>(R.id.statFavorites)?.text = it.optInt("likes", 0).toString()
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    rv?.visibility = View.GONE
                    emptyState?.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun openProfileSocialList(type: String) {
        val social = profileSocialJson ?: return
        val title = when (type) {
            "followers" -> getString(R.string.followers)
            "following" -> getString(R.string.following)
            else -> getString(R.string.likes_short)
        }
        val source = when (type) {
            "followers" -> social.optJSONArray("followers") ?: JSONArray()
            "following" -> social.optJSONArray("following") ?: JSONArray()
            else -> social.optJSONArray("packs") ?: JSONArray()
        }
        val items = JSONArray()
        for (i in 0 until source.length()) {
            val item = source.optJSONObject(i) ?: continue
            if (type == "likes" && item.optInt("like_count", 0) <= 0) continue
            items.put(JSONObject().apply {
                put("type", if (type == "likes") "pack" else "profile")
                put("id", if (type == "likes") item.optString("id") else item.optString("id", item.optString("email")))
                put("name", if (type == "likes") item.optString("name") else item.optString("name", item.optString("email")))
                put("subtitle", if (type == "likes") "${item.optInt("like_count", 0)} ${getString(R.string.likes_short)}" else item.optString("email"))
                val firstSticker = item.optJSONArray("stickers")?.optJSONObject(0)
                val image = if (type == "likes") {
                    item.optString("tray_url").ifBlank {
                        firstSticker?.optString("url").orEmpty().ifBlank { firstSticker?.optString("image_url").orEmpty() }
                    }
                } else {
                    item.optString("photo_url").ifBlank { item.optString("photo") }
                        .ifBlank { item.optString("avatar_url") }
                        .ifBlank { item.optString("picture") }
                }
                put("photo", image)
                put("tray_url", item.optString("tray_url"))
                put("image_url", firstSticker?.optString("url").orEmpty())
                put("email", item.optString("email"))
            })
        }
        startActivity(Intent(this, SocialListActivity::class.java).apply {
            putExtra(SocialListActivity.EXTRA_TITLE, title)
            putExtra(SocialListActivity.EXTRA_ITEMS, items.toString())
        })
    }

    private fun ownerFilter(deviceId: String, userId: String, email: String): String {
        fun escape(value: String) = value.replace("'", "\\'")
        return listOfNotNull(
            userId.takeIf { it.isNotBlank() }?.let { "user_id='${escape(it)}'" },
            email.takeIf { it.isNotBlank() }?.let { "user_email='${escape(it)}'" },
            deviceId.takeIf { it.isNotBlank() }?.let { "device_id='${escape(it)}'" }
        ).joinToString(" || ").ifBlank { "device_id='${escape(deviceId)}'" }
    }

    private fun parsePocketBaseTimestamp(value: String): com.google.firebase.Timestamp? {
        if (value.isBlank()) return null
        return try {
            val instant = java.time.Instant.parse(value)
            com.google.firebase.Timestamp(java.util.Date.from(instant))
        } catch (_: Exception) {
            null
        }
    }

    private fun loadUserSubmissions(userId: String, rv: RecyclerView?, emptyState: View?) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val escaped = userId.replace("'", "\\'")
                val records = PocketBaseHelper.listRecords("user_submissions", filter = "user_id='$escaped'")
                withContext(Dispatchers.Main) {
                    if (records.isEmpty()) {
                        rv?.visibility = View.GONE
                        emptyState?.visibility = View.VISIBLE
                    } else {
                        rv?.visibility = View.VISIBLE
                        emptyState?.visibility = View.GONE
                        if (rv?.layoutManager == null) rv?.layoutManager = LinearLayoutManager(this@MainActivity)
                        val items = records.mapNotNull { doc ->
                            val name = doc.optString("pack_name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                            val status = doc.optString("status", "pending")
                            val stickersArr = doc.optJSONArray("stickers")
                            val stickerCount = stickersArr?.length() ?: 0
                            val rejectionReason = doc.optString("rejection_reason").takeIf { it.isNotBlank() }
                            val createdAt = doc.optString("created_at").takeIf { it.isNotBlank() }
                            val storePackId = doc.optString("sticker_pack_id").takeIf { it.isNotBlank() }
                            @Suppress("UNCHECKED_CAST")
                            val stickerUrls = if (stickersArr != null) {
                                (0 until stickersArr.length()).mapNotNull { i ->
                                    stickersArr.optJSONObject(i)?.optString("image_url")
                                }.take(6)
                            } else emptyList()
                            SubmissionItem(doc.optString("id"), name, status, stickerCount, rejectionReason, createdAt, stickerUrls, storePackId)
                        }.sortedByDescending { it.createdAt ?: "" }.filter { it.id !in deletedSubmissionIds }
                        rv?.adapter = SubmissionAdapter(items)

                        val approvedCount = items.count { it.status == "approved" }
                        findViewById<TextView>(R.id.statPublished)?.text = approvedCount.toString()
                    }
                }
            } catch (e: Exception) {
                Log.e("Profile", "Error loading submissions", e)
                withContext(Dispatchers.Main) {
                    rv?.visibility = View.GONE
                    emptyState?.visibility = View.VISIBLE
                }
            }
        }
    }

    // Admin notifications now loaded via loadAdminNotificationsFromPB (PocketBase)

    private fun clearNotificationBadges() {
        profileContentContainer?.rootView?.findViewById<TextView>(R.id.profileNotificationBadge)?.visibility = View.GONE
        findViewById<TextView>(R.id.toolbarNotificationBadge)?.visibility = View.GONE
    }

    private fun loadAdminNotificationsFromPB(deviceId: String, userId: String, email: String) {
        val container = profileContentContainer?.rootView?.findViewById<android.widget.LinearLayout>(R.id.adminMessagesContainer)
        val badge = profileContentContainer?.rootView?.findViewById<TextView>(R.id.profileNotificationBadge)
        val toolbarBadge = findViewById<TextView>(R.id.toolbarNotificationBadge)

        // Always hide the inline admin messages list on profile — messages live in NotificationsActivity.
        container?.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val messages = withContext(Dispatchers.IO) { fetchProfileNotifications(deviceId, userId, email) }
                val unreadCount = messages.count { !it.read }
                withContext(Dispatchers.Main) {
                    badge?.visibility = if (unreadCount > 0) View.VISIBLE else View.GONE
                    badge?.text = unreadCount.coerceAtMost(99).toString()
                    toolbarBadge?.visibility = if (unreadCount > 0) View.VISIBLE else View.GONE
                    toolbarBadge?.text = unreadCount.coerceAtMost(99).toString()
                }
            } catch (e: Exception) {
                Log.w("MainActivity", "Failed to load admin notifications: ${e.message}")
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun loadAdminNotificationsLegacy(deviceId: String, userId: String, email: String) {
        // Legacy adapter setup kept for reference — no longer used since notifications moved to NotificationsActivity.
        val container = profileContentContainer?.rootView?.findViewById<android.widget.LinearLayout>(R.id.adminMessagesContainer)
        val rv = profileContentContainer?.rootView?.findViewById<RecyclerView>(R.id.rvAdminMessages)
        if (container == null || rv == null) return
        lifecycleScope.launch {
            try {
                val messages = withContext(Dispatchers.IO) { fetchProfileNotifications(deviceId, userId, email) }
                withContext(Dispatchers.Main) {
                    if (messages.isEmpty()) {
                        container.visibility = View.GONE
                        return@withContext
                    }
                    container.visibility = View.VISIBLE
                    if (rv.layoutManager == null) rv.layoutManager = LinearLayoutManager(this@MainActivity)
                    rv.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                        override fun getItemCount() = messages.size
                        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                            object : RecyclerView.ViewHolder(LayoutInflater.from(parent.context)
                                .inflate(R.layout.item_admin_message, parent, false)) {}
                        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, pos: Int) {
                            val msg = messages[pos]
                            holder.itemView.findViewById<TextView>(R.id.tvMsgTitle).text = msg.title
                            holder.itemView.findViewById<TextView>(R.id.tvMsgBody).text = msg.body
                            holder.itemView.findViewById<TextView>(R.id.tvMsgDate).text = msg.dateLabel
                            holder.itemView.alpha = if (msg.read) 0.75f else 1f
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("Profile", "Error loading PB notifications", e)
                withContext(Dispatchers.Main) {
                    container.visibility = View.GONE
                }
            }
        }
    }

    private suspend fun fetchProfileNotifications(deviceId: String, userId: String, email: String): List<ProfileNotification> {
        fun escape(value: String) = value.replace("'", "\\'")
        val filters = listOf(userId, email, deviceId)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" || ") { "user_id='${escape(it)}'" }
        if (filters.isBlank()) return emptyList()
        return PocketBaseHelper.listRecords("notifications", filter = filters, perPage = 50)
            .map { record ->
                val timestamp = record.optString("timestamp", record.optString("created", ""))
                val date = parsePocketBaseTimestamp(timestamp)?.toDate()
                ProfileNotification(
                    id = record.optString("id"),
                    title = record.optString("title", getString(R.string.profile_notifications_title)),
                    body = record.optString("body", record.optString("message", "")),
                    read = record.optBoolean("read", false),
                    dateLabel = date?.let { java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()).format(it) }.orEmpty(),
                    timestamp = date?.time ?: 0L
                )
            }
            .sortedByDescending { it.timestamp }
    }

    private fun showProfileNotificationsDialog() {
        val deviceId = PreferencesHelper.getDeviceId(this)
        val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val prefs = getSharedPreferences("sticky_prefs", MODE_PRIVATE)
        val email = prefs.getString("user_email", "") ?: firebaseUser?.email.orEmpty()
        val userId = firebaseUser?.uid.orEmpty()
        lifecycleScope.launch {
            val messages = withContext(Dispatchers.IO) { fetchProfileNotifications(deviceId, userId, email) }
            if (messages.isEmpty()) {
                Toast.makeText(this@MainActivity, getString(R.string.no_notifications), Toast.LENGTH_SHORT).show()
                return@launch
            }
            val content = android.widget.LinearLayout(this@MainActivity).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(32, 12, 32, 8)
            }
            messages.forEach { msg ->
                val title = TextView(this@MainActivity).apply {
                    text = msg.title
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
                    textSize = 15f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
                val body = TextView(this@MainActivity).apply {
                    text = if (msg.dateLabel.isBlank()) msg.body else "${msg.body}\n${msg.dateLabel}"
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                    textSize = 13f
                    setPadding(0, 4, 0, 18)
                }
                content.addView(title)
                content.addView(body)
            }
            android.app.AlertDialog.Builder(this@MainActivity)
                .setTitle(R.string.profile_notifications_title)
                .setView(content)
                .setPositiveButton(R.string.ok, null)
                .show()
            withContext(Dispatchers.IO) {
                messages.filter { !it.read }.forEach { msg ->
                    PocketBaseHelper.updateRecord("notifications", msg.id, org.json.JSONObject().put("read", true))
                }
            }
            loadAdminNotificationsFromPB(deviceId, userId, email)
        }
    }

    private data class ProfileNotification(
        val id: String,
        val title: String,
        val body: String,
        val read: Boolean,
        val dateLabel: String,
        val timestamp: Long
    )

    private data class SubmissionItem(
        val id: String,
        val name: String,
        val status: String,
        val stickerCount: Int,
        val rejectionReason: String?,
        val createdAt: String?,
        val stickerUrls: List<String> = emptyList(),
        val storePackId: String? = null,
        val downloadCount: Int = 0,
        val favoriteCount: Int = 0,
        val likeCount: Int = 0,
        val commentCount: Int = 0
    )

    private inner class SubmissionAdapter(private val items: List<SubmissionItem>) :
        RecyclerView.Adapter<SubmissionAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvSubmissionName)
            val tvStatus: TextView = view.findViewById(R.id.tvSubmissionStatus)
            val tvMeta: TextView = view.findViewById(R.id.tvSubmissionMeta)
            val rejectionContainer: View = view.findViewById(R.id.rejectionContainer)
            val tvRejectionReason: TextView = view.findViewById(R.id.tvRejectionReason)
            val btnDelete: com.google.android.material.button.MaterialButton = view.findViewById(R.id.btnDeleteSubmission)
            val stickerPreviewRow: android.widget.LinearLayout = view.findViewById(R.id.stickerPreviewRow)
            val tvAnalytics: TextView = view.findViewById(R.id.tvAnalytics)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_submission, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.tvName.text = item.name

            val (statusLabel, statusBg, statusTextColor) = when (item.status) {
                "approved"   -> Triple(getString(R.string.status_approved),   0x1A2ECC71, 0xFF2ECC71.toInt())
                "rejected"   -> Triple(getString(R.string.status_rejected),   0x1AE74C3C, 0xFFE74C3C.toInt())
                "flagged"    -> Triple(getString(R.string.status_flagged),    0x1AE67E22, 0xFFE67E22.toInt())
                "processing" -> Triple("Processing 🔄",                       0x1A3498DB, 0xFF3498DB.toInt())
                else         -> Triple(getString(R.string.status_pending),    0x1AF39C12, 0xFFF39C12.toInt())
            }
            holder.tvStatus.text = statusLabel
            holder.tvStatus.setBackgroundColor(statusBg)
            holder.tvStatus.setTextColor(statusTextColor)

            val dateStr = item.createdAt?.let { ts ->
                try {
                    val instant = java.time.Instant.parse(ts)
                    java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
                        .format(java.util.Date.from(instant))
                } catch (_: Exception) { "" }
            } ?: ""
            holder.tvMeta.text = "${item.stickerCount} stickers" + if (dateStr.isNotEmpty()) "  ·  $dateStr" else ""

            // Rejection reason
            if (item.status == "rejected" && !item.rejectionReason.isNullOrBlank()) {
                holder.rejectionContainer.visibility = View.VISIBLE
                holder.tvRejectionReason.text = item.rejectionReason
            } else {
                holder.rejectionContainer.visibility = View.GONE
            }

            // Sticker image preview (up to 6 thumbnails)
            holder.stickerPreviewRow.removeAllViews()
            if (item.stickerUrls.isNotEmpty()) {
                holder.stickerPreviewRow.visibility = View.VISIBLE
                val dp48 = (48 * resources.displayMetrics.density).toInt()
                val dp6  = (6  * resources.displayMetrics.density).toInt()
                item.stickerUrls.forEach { url ->
                    val img = android.widget.ImageView(this@MainActivity).apply {
                        layoutParams = android.widget.LinearLayout.LayoutParams(dp48, dp48).apply { marginEnd = dp6 }
                        scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                        background = androidx.core.content.ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_category_unselected)
                        clipToOutline = true
                    }
                    com.bumptech.glide.Glide.with(this@MainActivity).load(url).centerCrop().into(img)
                    holder.stickerPreviewRow.addView(img)
                }
            } else {
                holder.stickerPreviewRow.visibility = View.GONE
            }

            // Analytics (downloads / favorites / views) for approved packs — from PocketBase
            if (item.status == "approved" && item.storePackId != null) {
                holder.tvAnalytics.visibility = View.VISIBLE
                holder.tvAnalytics.text = "📥 ${item.downloadCount}  ☆ ${item.favoriteCount}  ❤️ ${item.likeCount}  💬 ${item.commentCount}"
                lifecycleScope.launch {
                    runCatching {
                        val record = withContext(Dispatchers.IO) {
                            PocketBaseHelper.getRecord("stickers", item.storePackId)
                        }
                        val dl  = record.optInt("download_count", 0)
                        val fav = record.optInt("favorite_count", 0)
                        val vw  = record.optInt("view_count", 0)
                        val likes = record.optInt("like_count", item.likeCount)
                        val comments = record.optInt("comment_count", item.commentCount)
                        holder.tvAnalytics.text = "📥 $dl  ☆ $fav  ❤️ $likes  💬 $comments  👁 $vw"
                    }.onFailure { holder.tvAnalytics.visibility = View.GONE }
                }
            } else {
                holder.tvAnalytics.visibility = View.GONE
            }


            holder.itemView.setOnClickListener {
                val targetPackId = item.storePackId ?: item.id.takeIf { value -> item.status == "approved" && value.isNotBlank() }
                if (!targetPackId.isNullOrBlank()) {
                    startActivity(Intent(this@MainActivity, DetailsActivity::class.java).putExtra("id", targetPackId))
                }
            }
            // Delete button for ALL statuses
            holder.btnDelete.visibility = View.VISIBLE
            holder.btnDelete.setOnClickListener {
                val msg = if (item.status == "approved")
                    "Delete \"${item.name}\"? This will also remove it from the public sticker store."
                else
                    "Delete \"${item.name}\"? This cannot be undone."
                showModernDeleteDialog(
                    title = "Delete Submission",
                    message = msg,
                    onConfirm = {
                        lifecycleScope.launch {
                            // Mark as deleted immediately so loadProfileData never shows it again
                            deletedSubmissionIds.add(item.id)
                            item.storePackId?.let { deletedSubmissionIds.add(it) }
                            getSharedPreferences("sticky_prefs", MODE_PRIVATE).edit()
                                .putStringSet("deleted_submission_ids", deletedSubmissionIds.toSet()).apply()
                            // Immediately remove from adapter so UI updates instantly
                            val rvPublished = this@MainActivity.findViewById<RecyclerView>(R.id.rvPublishedPacks)
                            rvPublished?.adapter = SubmissionAdapter(items.filter { it.id != item.id && it.storePackId != item.storePackId })

                            withContext(Dispatchers.IO) {
                                val esc = { v: String -> v.replace("'", "\\'") }
                                runCatching {
                                    SocialRepository.deleteSharedPack(this@MainActivity, item.id, item.storePackId)
                                }
                                // Delete from user_submissions by item.id (works for submission items)
                                runCatching { PocketBaseHelper.deleteRecord("user_submissions", item.id) }
                                // For approved packs, also search submission records by sticker_pack_id / pack_name
                                if (item.status == "approved") {
                                    val storeId = item.storePackId ?: item.id
                                    val submFilter = buildList {
                                        add("sticker_pack_id='${esc(storeId)}'")
                                        add("source_pack_id='${esc(storeId)}'")
                                        add("source_pack_id='${esc(item.id)}'")
                                        if (item.name.isNotBlank()) add("pack_name='${esc(item.name)}'")
                                    }.distinct().joinToString(" || ")
                                    runCatching {
                                        val found = PocketBaseHelper.listRecords("user_submissions", filter = submFilter, perPage = 10)
                                        found.forEach { sub ->
                                            runCatching { PocketBaseHelper.deleteRecord("user_submissions", sub.optString("id")) }
                                            deletedSubmissionIds.add(sub.optString("id"))
                                            sub.optString("sticker_pack_id").takeIf { it.isNotBlank() }?.let { deletedSubmissionIds.add(it) }
                                        }
                                        withContext(Dispatchers.Main) {
                                            getSharedPreferences("sticky_prefs", MODE_PRIVATE).edit()
                                                .putStringSet("deleted_submission_ids", deletedSubmissionIds.toSet()).apply()
                                        }
                                    }
                                    // Delete by storePackId if available
                                    if (item.storePackId != null) {
                                        runCatching { PocketBaseHelper.deleteRecord("stickers", item.storePackId) }
                                        runCatching { PocketBaseHelper.deleteRecord("premium_stickers", item.storePackId) }
                                    }
                                    // Also search by source_pack_id / pack_name in case storePackId is unset
                                    val searchParts = mutableListOf<String>()
                                    searchParts.add("source_pack_id='${esc(item.id)}'")
                                    if (item.name.isNotBlank()) {
                                        searchParts.add("(name='${esc(item.name)}' || pack_name='${esc(item.name)}')")
                                    }
                                    val searchFilter = searchParts.joinToString(" || ")
                                    for (collection in listOf("stickers", "premium_stickers")) {
                                        runCatching {
                                            val found = PocketBaseHelper.listRecords(collection, filter = searchFilter, perPage = 10)
                                            found.forEach { PocketBaseHelper.deleteRecord(collection, it.optString("id")) }
                                        }
                                    }
                                }
                                // Remove from global pack cache so Explore tab no longer shows it
                                val targetId = item.storePackId ?: item.id
                                StickerRepository.allPacksCache = StickerRepository.allPacksCache
                                    .filter { it.id != targetId && it.id != item.id }
                            }
                            Toast.makeText(this@MainActivity, "Submission deleted", Toast.LENGTH_SHORT).show()
                            profileSocialJson = null
                            loadProfileData()
                        }
                    }
                )
            }
        }

        override fun getItemCount(): Int = items.size
    }


    private fun showModernDeleteDialog(title: String, message: String, onConfirm: () -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_confirm, null)
        val tvTitle = dialogView.findViewById<android.widget.TextView>(R.id.tvDeleteTitle)
        val tvMessage = dialogView.findViewById<android.widget.TextView>(R.id.tvDeleteMessage)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDeleteCancel)
        val btnDelete = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDeleteConfirm)

        tvTitle.text = title
        tvMessage.text = message

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnDelete.setOnClickListener {
            dialog.dismiss()
            onConfirm()
        }
        dialog.show()
    }

    private val profileSignInLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            try {
                val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                lifecycleScope.launch {
                    try {
                        val idToken = account.idToken ?: throw Exception("Missing ID token")
                        try {
                            withContext(Dispatchers.IO) { PocketBaseHelper.authWithOAuth("google", idToken) }
                            PreferencesHelper.setPocketBaseAuth(
                                this@MainActivity,
                                PocketBaseHelper.getToken(),
                                PocketBaseHelper.getAuthRecordId()
                            )
                        } catch (_: Exception) {
                            // PocketBase sync is optional; continue with local profile
                        }
                        PreferencesHelper.setUserProfile(
                            this@MainActivity,
                            account.email,
                            account.displayName,
                            account.photoUrl?.toString()
                        )
                        PreferencesHelper.syncUserDataWithPocketBase(
                            this@MainActivity,
                            PreferencesHelper.getDeviceId(this@MainActivity)
                        )
                        loadProfileData()
                        runOnUiThread { aiUpdateGenerateButton?.invoke() }
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Sign-in failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: com.google.android.gms.common.api.ApiException) {
                val message = when (e.statusCode) {
                    10 -> "Google sign-in configuration error. Check SHA-1 and OAuth client."
                    7 -> "Network error. Please try again."
                    12500 -> "Google Play Services configuration error."
                    12501 -> null
                    else -> "Sign-in failed: ${e.statusCode}"
                }
                if (message != null) Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Sign-in failed: ${e.message ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun profileSignIn() {
        val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
            com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
        )
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        val client = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(this, gso)
        profileSignInLauncher.launch(client.signInIntent)
    }

    private fun setupRegionalSection() {
        regionalAdapter = RegionalAdapter(
            packs = emptyList(),
            onClick = { pack ->
                sessionPackOpenCount++
                if (sessionPackOpenCount == 3) StickyApp.appOpenAdInstance?.tryShowAd()
                startActivity(Intent(this, DetailsActivity::class.java).putExtra("id", pack.id))
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            },
            onAddClick = { pack ->
                sessionPackOpenCount++
                if (sessionPackOpenCount == 3) StickyApp.appOpenAdInstance?.tryShowAd()
                startActivity(Intent(this, DetailsActivity::class.java).putExtra("id", pack.id))
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
        )
        rvRegional.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvRegional.setHasFixedSize(true)
        rvRegional.setItemViewCacheSize(4)
        rvRegional.itemAnimator = null
        val regionalPool = RecyclerView.RecycledViewPool()
        regionalPool.setMaxRecycledViews(0, 8)
        rvRegional.setRecycledViewPool(regionalPool)
        rvRegional.adapter = regionalAdapter

        // Snap to card edges for smooth swipe
        rvRegional.onFlingListener = null
        snapHelper.attachToRecyclerView(rvRegional)
    }

    private fun updateRegionalPacks(packs: List<Pack>) {
        regionalPopularTitle.text = getString(R.string.popular_stickers)

        // "Popular" is now driven entirely by real engagement (downloads + favorites + likes
        // + comments + recency, weighted in calculateRankScore). The admin's isPopular flag
        // is no longer required to appear here — it acts as a multiplier inside the score so
        // curated picks float to the top, but every active non-custom pack is eligible. This
        // matches the user's expectation: "gerçekten uygulamada en popüler stickerlar gösterilmeli".
        val regionalTopPacks = packs
            .filter { it.isActive && it.category != "custom" }
            .sortedByDescending { getOrCalculateRankScore(it) }
            .take(10)

        if (regionalTopPacks.isEmpty()) {
            regionalPopularContainer.visibility = View.GONE
            return
        }

        if (!hasPreloadedPopular) {
            hasPreloadedPopular = true
            rvRegional.postDelayed({
                StickyGlideModule.preloadPopularPacks(this, regionalTopPacks)
            }, 1200)
        }

        if (currentFilter == FilterType.ALL || currentFilter == FilterType.PREMIUM) {
            regionalPopularContainer.visibility = View.VISIBLE
        } else {
            regionalPopularContainer.visibility = View.GONE
        }

        regionalAdapter?.updateData(regionalTopPacks)
    }

    private fun startAutoScroll() { /* disabled — manual swipe only */ }
    private fun stopAutoScroll() {
        autoScrollJob?.cancel()
        autoScrollJob = null
    }

    private fun setupStorySection() {
        storyAdapter = StoryAdapter(emptyList()) { pack ->
            sessionPackOpenCount++
            if (sessionPackOpenCount == 3) StickyApp.appOpenAdInstance?.tryShowAd()
            startActivity(Intent(this, DetailsActivity::class.java).putExtra("id", pack.id))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
        rvStories.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvStories.setHasFixedSize(true)
        rvStories.setItemViewCacheSize(5)
        rvStories.itemAnimator = null
        rvStories.adapter = storyAdapter
    }

    private fun updateStoryPacks(packs: List<Pack>) {
        val activePacks = packs.filter { it.isActive && it.category != "custom" }

        // Recently added: sorted by creation date (most recent first), up to 20
        val recentPacks = activePacks
            .sortedByDescending { parseDateCached(it.createdAt) }
            .take(20)

        if (recentPacks.isNotEmpty() && (currentFilter == FilterType.ALL || currentFilter == FilterType.PREMIUM)
            && currentSearchQuery.isEmpty()) {
            storyContainer.visibility = View.VISIBLE
            storyAdapter?.updateData(recentPacks)
        } else {
            storyContainer.visibility = View.GONE
        }
    }

    private fun hideHomeSections() {
        regionalPopularContainer.visibility = View.GONE
        storyContainer.visibility = View.GONE
    }

    private fun showHomeSections() {
        if (regionalAdapter?.getRealCount() ?: 0 > 0) regionalPopularContainer.visibility = View.VISIBLE
        if (storyAdapter?.itemCount ?: 0 > 0) storyContainer.visibility = View.VISIBLE
    }




    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun getCategoryEmoji(key: String): String {
        return when (key) {
            "humor" -> "😂"
            "love" -> "❤️"
            "religious" -> "🕌"
            "entertainment" -> "🎭"
            "background" -> "🖼️"
            "morning" -> "🌅"
            "night" -> "🌙"
            "birthday" -> "🎂"
            "congrats" -> "🎉"
            "animals" -> "🐾"
            "sports" -> "⚽"
            "gaming" -> "🎮"
            "movie" -> "🎬"
            "music" -> "🎵"
            "food" -> "🍕"
            "emoji" -> "😀"
            "cars" -> "🏎️"
            "motivation" -> "💪"
            "cute" -> "🥰"
            "text" -> "💬"
            "anime" -> "🎌"
            "memes" -> "🤣"
            "nature" -> "🌿"
            "other" -> "📦"
            else -> "📁"
        }
    }

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

    private val categoryKeyCache = HashMap<String, String>(32)

    private fun normalizeCategoryKey(key: String): String {
        categoryKeyCache[key]?.let { return it }
        val k = key.lowercase(Locale.ROOT).trim()
        val result = when {
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
                if (getCategoryStringRes(k) != 0) k else k.replace(" ", "_")
            }
        }
        categoryKeyCache[key] = result
        return result
    }

    private var lastCategorySet: Set<String> = emptySet()

    private fun setupCategoryChips() {
        if (!::categoryChipGroup.isInitialized) return

        // Firebase'den gelen paketlerdeki benzersiz kategorileri al
        val uniqueCategories = allPacks
            .filter { it.isActive && it.category.isNotBlank() && it.category != "custom" }
            .map { normalizeCategoryKey(it.category) }
            .distinct()
            .sorted()

        // Skip rebuild if categories haven't changed
        val newSet = uniqueCategories.toSet()
        if (newSet == lastCategorySet && categoryChipGroup.childCount > 0) {
            refreshChipStates()
            return
        }
        lastCategorySet = newSet

        categoryChipGroup.removeAllViews()
        dynamicCategories.clear()
        dynamicCategories.addAll(uniqueCategories)

        // === SPECIAL FILTER CHIPS (first in the row) ===
        data class FilterChipInfo(val id: String, val label: String, val filterType: FilterType)
        val filterChips = listOf(
            FilterChipInfo("filter_all", "✨ ${getString(R.string.filter_all)}", FilterType.ALL),
            FilterChipInfo("filter_premium", "👑 Premium", FilterType.PREMIUM)
        )

        filterChips.forEach { info ->
            val isActive = currentFilter == info.filterType && (info.filterType != FilterType.ALL || currentCategory == "all")
            val chip = Chip(this).apply {
                text = info.label
                isCheckable = true
                isChecked = isActive
                tag = info.id
                chipStartPadding = 8.dpToPx().toFloat()
                chipEndPadding = 8.dpToPx().toFloat()
                chipCornerRadius = 50.dpToPx().toFloat()
                chipMinHeight = 28.dpToPx().toFloat()
                textSize = 11f
                typeface = if (isActive) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
                setChipBackgroundColorResource(if (isActive) R.color.accent else R.color.white)
                setTextColor(getColor(if (isActive) R.color.white else R.color.text_primary))
                chipStrokeWidth = if (isActive) 0f else (1.5f * resources.displayMetrics.density)
                chipStrokeColor = android.content.res.ColorStateList.valueOf(
                    if (isActive) getColor(R.color.accent) else getColor(R.color.primary_light)
                )
                setOnClickListener {
                    currentFilter = info.filterType
                    currentCategory = "all"
                    applyFilters()
                    updateBottomNavUI()
                    refreshChipStates()
                }
            }
            categoryChipGroup.addView(chip)
        }

        // === DYNAMIC CATEGORY CHIPS (after filter chips) ===
        dynamicCategories.forEach { categoryKey ->
            val isActive = currentFilter == FilterType.ALL && currentCategory == categoryKey
            val chip = Chip(this).apply {
                val resId = getCategoryStringRes(categoryKey)
                val label = if (resId != 0) {
                    getString(resId)
                } else {
                    val dynResId = resources.getIdentifier("category_$categoryKey", "string", packageName)
                    if (dynResId != 0) getString(dynResId) else {
                        val emoji = getCategoryEmoji(categoryKey)
                        "$emoji " + categoryKey.replace("_", " ").split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
                    }
                }
                text = label
                isCheckable = true
                isChecked = isActive
                tag = categoryKey
                chipStartPadding = 8.dpToPx().toFloat()
                chipEndPadding = 8.dpToPx().toFloat()
                chipCornerRadius = 50.dpToPx().toFloat()
                chipMinHeight = 28.dpToPx().toFloat()
                textSize = 11f
                typeface = if (isActive) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
                setChipBackgroundColorResource(if (isActive) R.color.accent else R.color.white)
                setTextColor(getColor(if (isActive) R.color.white else R.color.text_primary))
                chipStrokeWidth = if (isActive) 0f else (1.5f * resources.displayMetrics.density)
                chipStrokeColor = android.content.res.ColorStateList.valueOf(
                    if (isActive) getColor(R.color.accent) else getColor(R.color.primary_light)
                )
                setOnClickListener {
                    currentFilter = FilterType.ALL
                    currentCategory = categoryKey
                    applyFilters()
                    updateBottomNavUI()
                    refreshChipStates()
                }
            }
            categoryChipGroup.addView(chip)
        }
    }

    private fun refreshChipStates() {
        for (i in 0 until categoryChipGroup.childCount) {
            val chip = categoryChipGroup.getChildAt(i) as? Chip ?: continue
            val tag = chip.tag as? String ?: continue
            val isActive = when {
                tag == "filter_all" -> currentFilter == FilterType.ALL && currentCategory == "all"
                tag == "filter_premium" -> currentFilter == FilterType.PREMIUM
                else -> currentFilter == FilterType.ALL && currentCategory == tag
            }
            chip.isChecked = isActive
            chip.setChipBackgroundColorResource(if (isActive) R.color.accent else R.color.white)
            chip.setTextColor(getColor(if (isActive) R.color.white else R.color.text_primary))
            chip.typeface = if (isActive) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
            chip.chipStrokeWidth = if (isActive) 0f else (1.5f * resources.displayMetrics.density)
            chip.chipStrokeColor = android.content.res.ColorStateList.valueOf(
                if (isActive) getColor(R.color.accent) else getColor(R.color.primary_light)
            )
        }
    }

    private fun updateCategoryChipSelection() {
        refreshChipStates()
    }

    private fun setupFavoritesChips() {
        categoryChipGroup.removeAllViews()

        data class FavChipInfo(val id: String, val label: String, val filterType: FilterType)
        val chips = listOf(
            FavChipInfo("fav_favorites", "❤️ ${getString(R.string.filter_favorites)}", FilterType.FAVORITES),
            FavChipInfo("fav_downloads", "📥 ${getString(R.string.filter_downloads)}", FilterType.INSTALLED)
        )

        chips.forEach { info ->
            val isActive = currentFilter == info.filterType
            val chip = Chip(this).apply {
                text = info.label
                isCheckable = true
                isChecked = isActive
                tag = info.id
                chipStartPadding = 8.dpToPx().toFloat()
                chipEndPadding = 8.dpToPx().toFloat()
                chipCornerRadius = 50.dpToPx().toFloat()
                chipMinHeight = 28.dpToPx().toFloat()
                textSize = 11f
                typeface = if (isActive) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
                setChipBackgroundColorResource(if (isActive) R.color.accent else R.color.white)
                setTextColor(getColor(if (isActive) R.color.white else R.color.text_primary))
                chipStrokeWidth = if (isActive) 0f else (1.5f * resources.displayMetrics.density)
                chipStrokeColor = android.content.res.ColorStateList.valueOf(
                    if (isActive) getColor(R.color.accent) else getColor(R.color.primary_light)
                )
                setOnClickListener {
                    currentFilter = info.filterType
                    applyFilters()
                    updateBottomNavUI()
                }
            }
            categoryChipGroup.addView(chip)
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

    }

    private fun setupSearch() {
        val searchBox = findViewById<EditText>(R.id.searchBox)
        val btnSearchClear = findViewById<View>(R.id.btnSearchClear)

        var searchJob: Job? = null
        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchJob?.cancel()
                val query = s?.toString().orEmpty()
                btnSearchClear?.visibility = if (query.isNotBlank()) View.VISIBLE else View.GONE
                if (query.isNotBlank()) hideHomeSections()
                searchJob = lifecycleScope.launch {
                    delay(400) // Debounce
                    val wasSearching = currentSearchQuery.isNotBlank()
                    currentSearchQuery = query
                    applyFilters()
                    if (!wasSearching && currentSearchQuery.isNotBlank()) {
                        findViewById<RecyclerView>(R.id.rv)?.scrollToPosition(0)
                    }
                    // If search returns no results, silently refresh from server once
                    if (currentSearchQuery.isNotEmpty() && ::adapter.isInitialized && adapter.getItems().isEmpty()) {
                        delay(300)
                        loadPacks(forceRefresh = true)
                    }
                }
            }
        })

        btnSearchClear?.setOnClickListener { clearButton ->
            searchJob?.cancel()
            searchBox.setText("")
            currentSearchQuery = ""
            clearButton.visibility = View.GONE
            applyFilters()
            if (currentFilter == FilterType.ALL || currentFilter == FilterType.PREMIUM) showHomeSections()
        }

        searchBox.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(searchBox.windowToken, 0)
                searchBox.clearFocus()
                true
            } else false
        }

        // Keep typed search stable when focus changes; only refresh sections for an empty query.
        searchBox.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && searchBox.text.isNullOrEmpty()) {
                currentSearchQuery = ""
                applyFilters()
            }
        }

        // Detect keyboard dismiss and clear search focus
        val rootView = window.decorView.rootView
        val keyboardRect = android.graphics.Rect()
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            rootView.getWindowVisibleDisplayFrame(keyboardRect)
            val screenHeight = rootView.height
            val keypadHeight = screenHeight - keyboardRect.bottom
            if (keypadHeight < screenHeight * 0.15 && searchBox.hasFocus()) {
                searchBox.clearFocus()
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
                    applyFilters()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // WhatsApp installation status (runs on IO with 500ms delay, lightweight)
        checkInstallationUpdates()
        
        startAutoScroll()

        // Reload AI history when on AI tab
        if (currentFilter == FilterType.AI) {
            aiContentContainer?.post { aiLoadHistory() }
        }

        // Reload profile data when on Profile tab
        if (currentFilter == FilterType.PROFILE && profileContentContainer != null) {
            loadProfileData()
        }

        syncPremiumStatus {
            updateBottomNavUI()
            if (::adapter.isInitialized) applyFilters()
        }

        if (NetworkUtils.isOnline(this)) {
            val now = System.currentTimeMillis()
            if (now - lastForegroundPackRefresh > 8_000) {
                lastForegroundPackRefresh = now
                loadPacks(forceRefresh = true)
            }
        }

        // Ensure pack list is visible after returning from background
        // (some OEMs like TECNO aggressively reclaim memory)
        if (::adapter.isInitialized && adapter.getItems().isEmpty() && allPacks.isNotEmpty()
            && currentFilter != FilterType.AI && currentFilter != FilterType.PROFILE) {
            applyFilters()
        } else if (allPacks.isEmpty() && currentFilter != FilterType.AI && currentFilter != FilterType.PROFILE) {
            loadPacks()
        }

        // Only reload custom packs if user is specifically on CUSTOM tab
        if (::adapter.isInitialized && currentFilter == FilterType.CUSTOM) {
            lifecycleScope.launch {
                val updatedPacks = withContext(Dispatchers.IO) {
                    val customPacks = CustomStickerManager.getCustomPacks(this@MainActivity).mapNotNull { cp ->
                        val pack = CustomStickerManager.toWhatsAppPack(this@MainActivity, cp.id)?.copy(category = "custom")
                        if (pack != null && pack.stickers.isNotEmpty()) pack else null
                    }
                    val firebasePacks = allPacks.filter { it.category != "custom" }
                    firebasePacks + customPacks
                }
                allPacks = updatedPacks.distinctBy { it.id }
                applyFilters()
            }
        }

        // Show ad promo once per session after first fullscreen ad is shown
        if (!promoShownThisSession && !PreferencesHelper.isPremium(this) && AdManager.adShownThisSession) {
            promoShownThisSession = true
            showPremiumPromoDialog()
        }
    }

    override fun onPause() {
        super.onPause()
        stopAutoScroll()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("currentFilter", currentFilter.name)
        outState.putString("currentCategory", currentCategory)
        outState.putString("currentSearchQuery", currentSearchQuery)
        // Save scroll position
        val lm = rv.layoutManager as? LinearLayoutManager
        outState.putInt("scrollPosition", lm?.findFirstVisibleItemPosition() ?: 0)
        val topView = lm?.findViewByPosition(lm.findFirstVisibleItemPosition())
        outState.putInt("scrollOffset", topView?.top ?: 0)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        val filterName = savedInstanceState.getString("currentFilter", "ALL")
        currentFilter = try { FilterType.valueOf(filterName) } catch (_: Exception) { FilterType.ALL }
        currentCategory = savedInstanceState.getString("currentCategory", "all") ?: "all"
        currentSearchQuery = savedInstanceState.getString("currentSearchQuery", "") ?: ""
        val scrollPos = savedInstanceState.getInt("scrollPosition", 0)
        val scrollOffset = savedInstanceState.getInt("scrollOffset", 0)

        // Restore UI state after data is loaded
        updateBottomNavUI()
        if (currentSearchQuery.isNotEmpty()) {
            searchBarLayoutCached?.visibility = View.VISIBLE
        }
        // Restore scroll position after adapter update
        rv.post {
            (rv.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(scrollPos, scrollOffset)
        }
    }

    private var noInternetDialog: com.google.android.material.bottomsheet.BottomSheetDialog? = null

    private fun showNoInternetBottomSheet() {
        if (noInternetDialog?.isShowing == true) return
        // Make sure content is visible even without internet (empty state shown)
        showContent()

        noInternetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.RoundedBottomSheetDialog).apply {
            setContentView(R.layout.bottom_sheet_no_internet)
            setCancelable(false)
            setCanceledOnTouchOutside(false)

            findViewById<View>(R.id.btnRetryConnection)?.setOnClickListener {
                if (NetworkUtils.isOnline(this@MainActivity)) {
                    dismiss()
                    noInternetDialog = null
                    loadPacks(forceRefresh = true)
                } else {
                    Toast.makeText(this@MainActivity, R.string.still_no_internet, Toast.LENGTH_SHORT).show()
                }
            }

            show()
        }
    }

    private fun dismissNoInternetDialog() {
        noInternetDialog?.dismiss()
        noInternetDialog = null
    }

    private fun syncPremiumStatus(onComplete: () -> Unit) {
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val docId = user?.uid ?: PreferencesHelper.getDeviceId(this)

        if (docId.isEmpty()) {
            onComplete()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val escaped = docId.replace("'", "\\'")
                val email = (user?.email ?: getSharedPreferences("sticky_prefs", MODE_PRIVATE).getString("user_email", "") ?: "").replace("'", "\\'")
                val pbUserId = PocketBaseHelper.getAuthRecordId().orEmpty().replace("'", "\\'")
                val filters = mutableListOf("uid='$escaped'", "user_id='$escaped'", "device_id='$escaped'")
                if (email.isNotBlank()) filters.add("email='$email'")
                if (pbUserId.isNotBlank()) {
                    filters.add("uid='$pbUserId'")
                    filters.add("user_id='$pbUserId'")
                }
                val filter = filters.joinToString(" || ")
                val existing = PocketBaseHelper.listRecords("user_profiles", filter = filter, perPage = 1)
                if (existing.isNotEmpty()) {
                    val profile = existing.first()
                    val isPremium = profile.optBoolean("is_premium", false)
                    val premiumType = profile.optString("premium_type", "none")
                    val premiumExpiry = parsePremiumExpiry(profile.opt("premium_expiry"))
                    withContext(Dispatchers.Main) {
                        if (isPremium) PreferencesHelper.updateLocalPremiumStatus(this@MainActivity, true, premiumType, premiumExpiry)
                        else PreferencesHelper.updateLocalPremiumStatus(this@MainActivity, false, "none", 0L)
                    }
                }
            } catch (_: Exception) {
            } finally {
                withContext(Dispatchers.Main) { onComplete() }
            }
        }
    }

    private fun parsePremiumExpiry(raw: Any?): Long {
        return when (raw) {
            is Number -> raw.toLong()
            is String -> raw.toLongOrNull() ?: runCatching { java.time.Instant.parse(raw).toEpochMilli() }.getOrDefault(0L)
            else -> 0L
        }
    }

    private var hasPreloadedOnce = false
    private var hasPreloadedPopular = false

    private fun displayPacks(packs: List<Pack>) {
        val distinctPacks = packs.distinctBy { it.id }
        val oldSignature = allPacks.sortedBy { it.id }.joinToString("|") { "${it.id}:${it.version}:${it.stickers.size}:${it.isActive}" }
        val newSignature = distinctPacks.sortedBy { it.id }.joinToString("|") { "${it.id}:${it.version}:${it.stickers.size}:${it.isActive}" }
        if (oldSignature != newSignature) savedExploreList = null
        allPacks = distinctPacks
        setupCategoryChips()
        updateRegionalPacks(packs)
        updateStoryPacks(packs)
        applyFilters()
        showContent()
    }

    private fun loadPacks(forceRefresh: Boolean = false) {
        lifecycleScope.launch {
            try {
                // 1. Memory cache ANINDA göster
                if (StickerRepository.allPacksCache.isNotEmpty()) {
                    displayPacks(StickerRepository.allPacksCache)
                } else {
                    // Disk cache'ten oku (sadece memory boşsa)
                    showSkeleton()
                    val diskPacks = withContext(Dispatchers.IO) {
                        StickerRepository.loadCacheFromDisk(this@MainActivity)
                    }
                    if (diskPacks.isNotEmpty()) {
                        StickerRepository.allPacksCache = diskPacks
                        displayPacks(diskPacks)
                    }
                }

                // 2. PocketBase'den güncel veriyi çek (arka planda). Açılışta disk cache gösterildiyse
                // forceRefresh=false eski cache'i tekrar döndürür; bu yüzden arka plan senkronu her zaman sunucuya gider.
                val loadedPacks = withContext(Dispatchers.IO) {
                    StickerRepository.loadPacks(this@MainActivity, forceRefresh = true)
                }

                if (loadedPacks.isNotEmpty()) {
                    displayPacks(loadedPacks)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private var contentShown = false

    private fun showSkeleton() {
        // skeleton removed — no-op
    }

    private fun hideSkeleton() {
        // skeleton removed — no-op
    }

    private fun showContent() {
        if (contentShown) return
        contentShown = true
        hideSkeleton()
        loadingOverlay.visibility = View.GONE
        mainContent.visibility = View.VISIBLE
        mainContent.alpha = 1f
        swipeRefresh.visibility = View.VISIBLE
        rv.visibility = View.VISIBLE
    }

    private var lastPacksUpdateTime = 0L

    private fun observePacksUpdateFlow() {
        lifecycleScope.launch {
            StickerRepository.packsUpdateFlow.collect { updatedPacks ->
                // Debounce rapid updates (at most once every 2s)
                val now = System.currentTimeMillis()
                if (now - lastPacksUpdateTime < 2000) return@collect
                lastPacksUpdateTime = now
                Log.d("MainActivity", "Real-time update received: ${updatedPacks.size} packs")
                displayPacks(updatedPacks)
            }
        }
    }

    private fun refreshPacks() {
        loadPacks(forceRefresh = true)
    }

    enum class FilterType { ALL, INSTALLED, PREMIUM, FAVORITES, PURCHASED, CUSTOM, AI, PROFILE }

    private fun showPacksByPublisher(publisherName: String) {
        if (publisherName.isBlank()) return
        val filtered = allPacks.filter { it.pub.equals(publisherName, ignoreCase = true) }
        if (filtered.isEmpty()) return
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(publisherName)
            .create()
        val rv = androidx.recyclerview.widget.RecyclerView(this).apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@MainActivity)
            setPadding(0, 16, 0, 16)
            adapter = PackAdapter(
                items = filtered,
                click = { pack ->
                    dialog.dismiss()
                    startActivity(android.content.Intent(this@MainActivity, DetailsActivity::class.java).putExtra("id", pack.id))
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                },
                onAddClick = { pack -> directAddToWhatsApp(pack) }
            )
        }
        dialog.setView(rv)
        dialog.show()
    }

    private fun applyFilters() {
        filterJob?.cancel()
        // Capture adapter snapshot on Main before switching to background
        val oldList = if (::adapter.isInitialized) adapter.getItems() else emptyList<Any>()
        filterJob = lifecycleScope.launch(Dispatchers.Default) {
            var filtered = allPacks

            if (currentSearchQuery.isNotEmpty()) {
                val query = currentSearchQuery.lowercase(Locale.getDefault())
                filtered = filtered.filter {
                    listOf(
                        it.localizedName,
                        it.name,
                        it.pub,
                        it.email,
                        it.publisherUserId,
                        it.source,
                        it.category
                    ).any { value -> value.lowercase(Locale.getDefault()).contains(query) }
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
                FilterType.PREMIUM -> filtered.filter { it.isPremium && it.category != "custom" && !it.id.startsWith("custom_") }
                FilterType.FAVORITES -> filtered.filter { PreferencesHelper.isPackFavorite(this@MainActivity, it.id) }
                FilterType.CUSTOM -> filtered.filter { it.id.startsWith("custom_") && it.stickers.isNotEmpty() }
                else -> filtered
            }

            // Main list: use cached shuffle order for session stability
            // Only compute shuffle once; new packs get appended at the end
            val currentCache = cachedShuffleOrder
            val shuffled: List<Pack>
            if (currentCache != null) {
                val orderMap = currentCache.withIndex().associate { it.value to it.index }
                val (known, newPacks) = filtered.partition { it.id in orderMap }
                val sortedKnown = known.sortedBy { orderMap[it.id] ?: Int.MAX_VALUE }
                shuffled = sortedKnown + newPacks.shuffled(java.util.Random(sessionSeed))
                // Update cache with new packs appended
                if (newPacks.isNotEmpty()) {
                    cachedShuffleOrder = shuffled.map { it.id }
                }
            } else {
                shuffled = filtered.shuffled(java.util.Random(sessionSeed))
                cachedShuffleOrder = shuffled.map { it.id }
            }

            // Interleave premium/free to prevent same-type clustering
            val premium = shuffled.filter { it.isPremium }.toMutableList()
            val free = shuffled.filter { !it.isPremium }.toMutableList()
            val sorted = mutableListOf<Pack>()
            var consecutivePremium = 0
            var consecutiveFree = 0
            val pIdx = intArrayOf(0)
            val fIdx = intArrayOf(0)
            while (pIdx[0] < premium.size || fIdx[0] < free.size) {
                // Pick next pack, avoiding more than 2 consecutive of same type
                val pickPremium = when {
                    pIdx[0] >= premium.size -> false
                    fIdx[0] >= free.size -> true
                    consecutivePremium >= 2 -> false
                    consecutiveFree >= 2 -> true
                    else -> {
                        // Maintain original ratio
                        val premRatio = premium.size.toFloat() / shuffled.size
                        sorted.count { it.isPremium }.toFloat() / sorted.size.coerceAtLeast(1) < premRatio
                    }
                }
                if (pickPremium) {
                    sorted.add(premium[pIdx[0]++])
                    consecutivePremium++
                    consecutiveFree = 0
                } else {
                    sorted.add(free[fIdx[0]++])
                    consecutiveFree++
                    consecutivePremium = 0
                }
            }

            // Insert banner ads every 10 packs (only in ALL/PREMIUM filters, not for premium users)
            val withAds = mutableListOf<Any>()
            val userIsPremium = PreferencesHelper.isPremium(this@MainActivity)
            if (!userIsPremium && (currentFilter == FilterType.ALL || currentFilter == FilterType.PREMIUM)) {
                var adSlot = 0
                sorted.forEachIndexed { index, pack ->
                    withAds.add(pack)
                    if ((index + 1) % 10 == 0) {
                        withAds.add(BannerAdPlaceholder(adSlot++))
                    }
                }
            } else {
                withAds.addAll(sorted)
            }

            // Calculate Diff on Background (oldList captured on Main before coroutine launch)
            val newList: List<Any> = withAds
            
            val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                 override fun getOldListSize() = oldList.size
                 override fun getNewListSize() = newList.size
                 override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                     val old = oldList[oldItemPosition]
                     val new = newList[newItemPosition]
                     if (old is Pack && new is Pack) return old.id == new.id
                     if (old is BannerAdPlaceholder && new is BannerAdPlaceholder) return old.slotIndex == new.slotIndex
                     return false
                 }
                 override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                     val old = oldList[oldItemPosition]
                     val new = newList[newItemPosition]
                     if (old is BannerAdPlaceholder && new is BannerAdPlaceholder) return true
                     return old == new
                 }
            }, false) // detectMoves=false for faster DiffUtil computation

            withContext(Dispatchers.Main) {
                if (sorted.isEmpty()) {
                    if (currentFilter == FilterType.CUSTOM) {
                        rv.visibility = View.GONE
                        emptyStateView.visibility = View.VISIBLE
                        findViewById<TextView>(R.id.emptyStateText).setText(R.string.no_custom_packs)
                        // FAB (+) is available at bottom-right — no inline button needed
                    } else if (currentFilter == FilterType.FAVORITES) {
                        rv.visibility = View.GONE
                        emptyStateView.visibility = View.VISIBLE
                        findViewById<TextView>(R.id.emptyStateText).setText(R.string.no_favorites_yet)
                    } else if (currentFilter == FilterType.INSTALLED) {
                        // Installed but empty?
                        rv.visibility = View.VISIBLE
                        emptyStateView.visibility = View.GONE
                        if (::adapter.isInitialized) adapter.updateListWithDiff(newList, diffResult)
                    } else {
                         // Default empty handling
                        rv.visibility = View.VISIBLE
                        emptyStateView.visibility = View.GONE
                        if (::adapter.isInitialized) adapter.updateListWithDiff(newList, diffResult)
                    }
                } else {
                    rv.visibility = View.VISIBLE
                    emptyStateView.visibility = View.GONE
                    if (::adapter.isInitialized) adapter.updateListWithDiff(newList, diffResult)
                }

                // Update home sections visibility - search results should be the only content while typing.
                if (currentSearchQuery.isBlank() && (currentFilter == FilterType.ALL || currentFilter == FilterType.PREMIUM)) {
                    showHomeSections()
                } else {
                    hideHomeSections()
                }

                // İlk ekran çizildikten sonra küçük bir ön yükleme yap; açılışta main thread'i boğmasın.
                if (!hasPreloadedOnce && newList.isNotEmpty()) {
                    hasPreloadedOnce = true
                    rv.postDelayed({
                        StickyGlideModule.preloadFeedPacks(this@MainActivity, newList, 12)
                    }, 1500)
                }

                // Scroll handling after adapter update
                if (pendingScrollToTop) {
                    pendingScrollToTop = false
                    rv.scrollToPosition(0)
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.END)) {
            drawer.closeDrawer(GravityCompat.END)
        } else if (currentFilter == FilterType.CUSTOM || currentFilter == FilterType.FAVORITES || currentFilter == FilterType.PROFILE) {
            currentFilter = FilterType.ALL
            applyFilters()
            updateBottomNavUI()
            categoryChipGroup.visibility = View.VISIBLE
            updateCategoryChipSelection()
        } else {
            super.onBackPressed()
        }
    }






    private fun showThemedCountWarning(message: String) {
        val snackbar = com.google.android.material.snackbar.Snackbar.make(rv, message, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
        val view = snackbar.view
        val lp = view.layoutParams as? android.widget.FrameLayout.LayoutParams
        val margin = (16 * resources.displayMetrics.density).toInt()
        lp?.setMargins(margin, 0, margin, margin)
        lp?.let { view.layoutParams = it }
        val bg = android.graphics.drawable.GradientDrawable().apply {
            setColor(androidx.core.content.ContextCompat.getColor(this@MainActivity, R.color.primary))
            cornerRadius = (24 * resources.displayMetrics.density)
        }
        view.background = bg
        snackbar.setTextColor(android.graphics.Color.WHITE)
        snackbar.show()
    }

    private fun showFaqDialog() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(StickyConfig.legalUrl("#faq"))))
    }

    private fun showAboutDialog() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(StickyConfig.legalUrl("#about"))))
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
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(StickyConfig.legalUrl("#privacy"))))
    }

    private fun showLanguageDialog() {
        val languages = arrayOf("🇺🇸 English", "🇹🇷 Türkçe", "🇪🇸 Español", "🇨🇳 简体中文",
            "🇸🇦 العربية", "🇮🇳 हिन्दी", "🇧🇷 Português", "🇫🇷 Français", "🇩🇪 Deutsch", "🇯🇵 日本語")
        val codes = arrayOf("en", "tr", "es", "zh", "ar", "hi", "pt", "fr", "de", "ja")
        val currentLang = PreferencesHelper.getLanguage(this)

        val view = layoutInflater.inflate(R.layout.dialog_language_selector, null)
        val container = view.findViewById<android.widget.LinearLayout>(R.id.llLanguageItems)
        val cancelBtn = view.findViewById<android.widget.TextView>(R.id.tvLangCancel)

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        languages.forEachIndexed { i, label ->
            val itemView = layoutInflater.inflate(android.R.layout.simple_list_item_1, container, false)
            val tv = itemView.findViewById<android.widget.TextView>(android.R.id.text1)
            tv.text = label
            tv.textSize = 15.5f
            tv.setPadding(72, 36, 72, 36)
            val isSelected = codes[i] == currentLang
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
                if (codes[i] != currentLang) {
                    PreferencesHelper.setLanguage(this, codes[i])
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

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    private fun restorePurchases() {
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            // Already signed in — restore via billing
            if (billingManager == null) {
                Toast.makeText(this, R.string.restore_error, Toast.LENGTH_SHORT).show()
                return
            }
            Toast.makeText(this, "Signed in as ${currentUser.email ?: currentUser.displayName}. Restoring...", Toast.LENGTH_SHORT).show()
            billingManager?.restorePurchases { result ->
                val messageRes = when (result) {
                    BillingManager.RestoreResult.SUCCESS -> {
                        loadPacks(forceRefresh = true)
                        R.string.restore_success
                    }
                    BillingManager.RestoreResult.NOT_FOUND -> {
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions?package=${packageName}")))
                        } catch (_: Exception) {}
                        R.string.restore_not_found
                    }
                    BillingManager.RestoreResult.ERROR -> R.string.restore_error
                }
                Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
            }
        } else {
            // Not signed in — launch Google sign-in
            val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
            val client = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(this, gso)
            restoreSignInLauncher.launch(client.signInIntent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        StickerRepository.stopObservingPacks()
        billingManager?.destroy()
        PreferencesHelper.stopRealtimeSync()
    }

    private fun showStickerTypeChooser() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.TransparentBottomSheetDialog)
        val view = layoutInflater.inflate(R.layout.dialog_sticker_type_chooser, null)
        dialog.setContentView(view)

        // Normal (Static) → StickerMakerActivity with photo picker
        view.findViewById<View>(R.id.optionNormal).setOnClickListener {
            dialog.dismiss()
            @Suppress("DEPRECATION")
            startActivityForResult(
                Intent(this, StickerMakerActivity::class.java).putExtra("skipTypeSelection", true),
                REQUEST_STICKER_MAKER
            )
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }

        // Animated → AnimatedStickerActivity (video picker)
        view.findViewById<View>(R.id.optionAnimated).setOnClickListener {
            dialog.dismiss()
            @Suppress("DEPRECATION")
            startActivityForResult(
                Intent(this, AnimatedStickerActivity::class.java),
                REQUEST_STICKER_MAKER
            )
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }

        dialog.show()
    }

    companion object {
        private const val REQUEST_DELETE_PACK = 2001
        private const val REQUEST_STICKER_MAKER = 2002
        private const val REQUEST_DIRECT_ADD_PACK = 2003
        private const val AI_DAILY_FREE_LIMIT = 5
        private const val AI_PREFS_COUNT = "ai_gen_count"
        private const val AI_PREFS_DATE = "ai_gen_date"
    }

    private fun isWhatsAppInstalled(): Boolean {
        return try {
            packageManager.getPackageInfo("com.whatsapp", 0)
            true
        } catch (_: Exception) {
            try {
                packageManager.getPackageInfo("com.whatsapp.w4b", 0)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun directAddToWhatsApp(pack: Pack) {
        if (pack.stickers.isEmpty()) {
            Toast.makeText(this, R.string.pack_empty_error, Toast.LENGTH_LONG).show()
            return
        }

        if (!isWhatsAppInstalled()) {
            Toast.makeText(this, R.string.whatsapp_not_installed, Toast.LENGTH_SHORT).show()
            return
        }

        val hasAccess = !pack.isPremium || PreferencesHelper.hasAccessToPack(this, pack.id)
        if (!hasAccess) {
            showRewardedAdForDirectAdd(pack)
        } else {
            prepareAndSendToWhatsApp(pack, skipInterstitial = false)
        }
    }

    private fun showRewardedAdForDirectAdd(pack: Pack) {
        if (!AdManager.isRewardedReady()) {
            Toast.makeText(this, R.string.ad_loading_please_wait, Toast.LENGTH_SHORT).show()
            AdManager.loadRewardedAd(this)
            addLoadingOverlay.visibility = View.VISIBLE
            lifecycleScope.launch {
                var waited = 0
                while (!AdManager.isRewardedReady() && waited < 12) {
                    delay(250)
                    waited++
                }
                addLoadingOverlay.visibility = View.GONE
                if (AdManager.isRewardedReady()) {
                    showRewardedAdForDirectAdd(pack)
                } else {
                    Toast.makeText(this@MainActivity, R.string.ad_not_available, Toast.LENGTH_SHORT).show()
                    PreferencesHelper.unlockPack(this@MainActivity, pack.id)
                    prepareAndSendToWhatsApp(pack, skipInterstitial = true)
                }
            }
            return
        }

        AdManager.showRewardedAd(this,
            onRewarded = {
                PreferencesHelper.unlockPack(this, pack.id)
                prepareAndSendToWhatsApp(pack, skipInterstitial = true)
            },
            onFailed = {
                Toast.makeText(this, R.string.ad_failed_try_again, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun prepareAndSendToWhatsApp(pack: Pack, skipInterstitial: Boolean) {
        addLoadingOverlay.visibility = View.VISIBLE
        circularProgressDirect.progress = 0
        tvDirectAddStatus.text = "0%"
        tvDirectAddSubtitle.text = getString(R.string.stickers_preparing)

        val totalFiles = pack.stickers.size + 1
        val downloadedCount = java.util.concurrent.atomic.AtomicInteger(0)
        fun updateProgress() {
            val count = downloadedCount.get()
            val percent = (count * 100) / totalFiles
            tvDirectAddStatus.text = "$percent%"
            tvDirectAddSubtitle.text = getString(R.string.stickers_preparing)
            ObjectAnimator.ofInt(circularProgressDirect, "progress", circularProgressDirect.progress, percent).apply {
                duration = 200
                start()
            }
        }

        lifecycleScope.launch {
            val success = try {
                withContext(Dispatchers.IO) {
                    val currentPacks = StickerRepository.allPacksCache
                    val packsToSave = if (currentPacks.any { it.id == pack.id }) currentPacks else currentPacks + pack
                    StickerRepository.saveCacheToDisk(this@MainActivity, packsToSave)

                    // Zaten cache'de varsa direkt geç
                    if (StickerRepository.isPackCached(this@MainActivity, pack)) {
                        downloadedCount.set(totalFiles)
                        true
                    } else {
                        val storagePath = pack.storagePath
                        coroutineScope {
                            // Tray
                            val trayJob = async {
                                StickerRepository.downloadStickerToCache(this@MainActivity, pack.id, pack.tray, storagePath, pack.trayUrl)
                                val c = downloadedCount.incrementAndGet()
                                withContext(Dispatchers.Main) { updateProgress() }
                            }
                            // Sticker'ları paralel indir
                            pack.stickers.chunked(6).forEach { chunk ->
                                chunk.map { sticker ->
                                    async {
                                        StickerRepository.downloadStickerToCache(this@MainActivity, pack.id, sticker.file, storagePath, sticker.url, allowCompression = !pack.isAnimated)
                                        val c = downloadedCount.incrementAndGet()
                                        withContext(Dispatchers.Main) { updateProgress() }
                                    }
                                }.awaitAll()
                            }
                            trayJob.await()
                        }
                        downloadedCount.get() >= totalFiles
                    }
                }
            } catch (e: Exception) {
                Log.e("WhatsAppAdd", "Download error: ${e.message}")
                false
            }

            // %100 göster
            tvDirectAddStatus.text = "100%"
            tvDirectAddSubtitle.text = getString(R.string.pack_ready)
            ObjectAnimator.ofInt(circularProgressDirect, "progress", circularProgressDirect.progress, 100).apply {
                duration = 150
                start()
            }
            delay(400)

            addLoadingOverlay.visibility = View.GONE
            if (!success) {
                Toast.makeText(this@MainActivity, R.string.stickers_load_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            launchDirectWhatsApp(pack, skipInterstitial)
        }
    }

    private fun launchDirectWhatsApp(pack: Pack, skipInterstitial: Boolean) {
        val launch = {
            val intent = Intent().apply {
                action = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
                putExtra("sticker_pack_id", pack.id)
                putExtra("sticker_pack_authority", "${packageName}.stickers")
                putExtra("sticker_pack_name", pack.localizedName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                pendingDirectAddPack = pack
                @Suppress("DEPRECATION")
                startActivityForResult(intent, REQUEST_DIRECT_ADD_PACK)
            } catch (e: Exception) {
                pendingDirectAddPack = null
                Toast.makeText(this, R.string.whatsapp_not_available_title, Toast.LENGTH_SHORT).show()
            }
        }
        if (!PreferencesHelper.isPremium(this) && !skipInterstitial) {
            AdManager.showInterstitialIfNeeded(this) { launch() }
        } else {
            launch()
        }
    }

    private fun deleteCustomPack(pack: Pack) {
        confirmAndDirectDelete(pack)
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
                Toast.makeText(this@MainActivity, R.string.pack_not_in_whatsapp_delete_local, Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this@MainActivity, R.string.whatsapp_not_installed, Toast.LENGTH_SHORT).show()
                confirmAndDirectDelete(pack)
            }
        }
    }

    private fun deletePackAndPocketBase(packId: String) {
        lifecycleScope.launch {
            // 1. PocketBase'deki user_submissions kaydını bul ve sil
            withContext(Dispatchers.IO) {
                try {
                    val escapedId = packId.replace("'", "\\'")
                    val submissions = PocketBaseHelper.listAllRecords(
                        "user_submissions",
                        filter = "source_pack_id='$escapedId'"
                    )
                    for (sub in submissions) {
                        val subId = sub.optString("id")
                        val storePackId = sub.optString("sticker_pack_id", "")
                        val subStatus = sub.optString("status", "")

                        // Onaylanmışsa stickers/premium_stickers koleksiyonundan da sil
                        if (subStatus == "approved" && storePackId.isNotBlank()) {
                            runCatching {
                                PocketBaseHelper.deleteRecord("stickers", storePackId)
                            }
                            runCatching {
                                PocketBaseHelper.deleteRecord("premium_stickers", storePackId)
                            }
                        }

                        // user_submissions kaydını sil
                        runCatching {
                            PocketBaseHelper.deleteRecord("user_submissions", subId)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("DeletePack", "PB deletion skipped: ${e.message}")
                }
            }

            // 2. Worker üzerinden cascade sil dene (sosyal veriler için)
            withContext(Dispatchers.IO) {
                runCatching {
                    SocialRepository.deleteSharedPack(
                        this@MainActivity,
                        null,
                        packId
                    )
                }
            }
        }
    }

    private fun confirmAndDirectDelete(pack: Pack) {
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_delete_pack)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.85).toInt(),
            android.view.WindowManager.LayoutParams.WRAP_CONTENT
        )

        dialog.findViewById<android.widget.Button>(R.id.btnDeleteCancel).setOnClickListener {
            dialog.dismiss()
        }
        dialog.findViewById<android.widget.Button>(R.id.btnDeleteConfirm).setOnClickListener {
            dialog.dismiss()
            if (CustomStickerManager.deletePack(this, pack.id)) {
                PreferencesHelper.removeInstalledPack(this, pack.id)
                deletePackAndPocketBase(pack.id)
                Toast.makeText(this, R.string.pack_deleted_success, Toast.LENGTH_SHORT).show()
                refreshPacks()
            }
        }
        dialog.show()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_DIRECT_ADD_PACK) {
            val pack = pendingDirectAddPack ?: return
            pendingDirectAddPack = null
            if (resultCode == Activity.RESULT_OK) {
                PreferencesHelper.addInstalledPack(this@MainActivity, pack.id)
                Toast.makeText(this@MainActivity, R.string.pack_added, Toast.LENGTH_SHORT).show()
                StickerRepository.incrementDownloadCount(pack.id, pack.isPremium)
                PreferencesHelper.incrementStickersAddedCount(this@MainActivity)
                PreferencesHelper.incrementPacksSincePromo(this@MainActivity)
                adapter.notifyDataSetChanged()
            }
            lifecycleScope.launch {
                delay(300)
                val isWhitelisted = withContext(Dispatchers.IO) { WhitelistCheck.isWhitelisted(this@MainActivity, pack.id) }
                if (isWhitelisted) {
                    PreferencesHelper.addInstalledPack(this@MainActivity, pack.id)
                    adapter.notifyDataSetChanged()
                }
            }
            return
        }

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
                        deletePackAndPocketBase(packId)
                        Toast.makeText(this@MainActivity, R.string.pack_deleted_success, Toast.LENGTH_SHORT).show()
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
                allPacks = (firebasePacks + customPacks).distinctBy { it.id }

                // Switch to My Stickers tab to show the newly added sticker
                currentFilter = FilterType.CUSTOM
                categoryChipGroup.visibility = View.GONE
                hideHomeSections()
                updateBottomNavUI()
                applyFilters()
            }
        }
    }

    private fun showPremiumPromoDialog() {
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_premium_promo)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            android.view.WindowManager.LayoutParams.WRAP_CONTENT
        )

        val btnGetPremium = dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnGetPremium)
        val btnMaybeLater = dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnMaybeLater)
        
        // Sadece bilgilendirme amaçlı, premiuma yönlendirme yok
        btnMaybeLater.visibility = View.GONE
        
        btnGetPremium.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}
