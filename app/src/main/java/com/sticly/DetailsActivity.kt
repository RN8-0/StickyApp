package com.sticly

import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.Animatable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.webkit.WebView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import android.animation.ValueAnimator
import android.animation.ObjectAnimator
import android.util.Log
import com.google.android.material.progressindicator.CircularProgressIndicator

class DetailsActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    private lateinit var packId: String
    private var isAnimatedPack: Boolean = false
    private lateinit var btnAction: MaterialButton
    private lateinit var btnWatchAd: MaterialButton
    private lateinit var premiumButtonsContainer: LinearLayout
    private lateinit var customButtonsContainer: LinearLayout
    private lateinit var btnGridAddSticker: MaterialButton
    private lateinit var btnGridDeleteMode: MaterialButton
    private lateinit var btnConfirmDelete: ImageButton
    private lateinit var installedIcon: ImageView
    private var btnFixedWhatsApp: MaterialButton? = null
    private var currentPack: Pack? = null
    private var isDeleteMode = false
    private val selectedIndices = mutableSetOf<Int>()
    private var progressDialog: AlertDialog? = null // Profesyonel yükleme dialoğu
    private var adapter: StickerAdapter? = null
    private var isPackReady = false // Çıkartmalar yüklendi mi kontrolü
    private var isDownloading = false // İndirme devam ediyor mu
    private var hasLoadedOnce = false // İlk yükleme tamamlandı mı
    private var currentProgress = 0 // Mevcut progress yüzdesi
    private var progressAnimator: ValueAnimator? = null
    private var pendingDeletePackId: String? = null
    private var wasPackInWhatsAppBeforeDelete = false
    private var waitingForWhatsAppReturn = false
    private var autoAddConsumed = false
    // Set to true immediately after a rewarded ad completes for a pack.
    // Prevents showing an interstitial right on top of a just-finished rewarded ad.
    private var rewardedJustCompleted = false

    // Track current social counts for accurate UI updates without stale pack data
    private var currentPackLikeCount = 0
    private var currentPackCommentCount = 0
    private var currentPackFavoriteCount = 0
    private var packLikeInteracted = false
    private var packLikeInFlight = false
    
    // Modern Activity Result API Launchers
    private val addPackLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        handleWAActivityResult(REQUEST_ADD, result.resultCode, result.data)
    }

    private val removePackLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        handleWAActivityResult(REQUEST_REMOVE, result.resultCode, result.data)
    }

    private val addStickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        handleWAActivityResult(REQUEST_ADD_STICKER, result.resultCode, result.data)
    }

    private fun handleWAActivityResult(req: Int, res: Int, data: Intent?) {
        // Activity yeniden yaratılmış olabilir, btnAction initialize kontrolü
        if (!::btnAction.isInitialized) return
        onActivityResultInternal(req, res, data)
    }

    private var professionalLoadingOverlay: View? = null
    private var tvOverlayLoadingText: TextView? = null
    private var tvDynamicStatus: TextView? = null
    private var circularProgress: CircularProgressIndicator? = null
    private var loadingOverlayInflated = false

    private fun ensureLoadingOverlay() {
        if (!loadingOverlayInflated) {
            loadingOverlayInflated = true
            val stub = findViewById<android.view.ViewStub>(R.id.loadingOverlayStub)
            stub?.inflate()
            professionalLoadingOverlay = findViewById(R.id.professionalLoadingOverlay)
            tvOverlayLoadingText = findViewById(R.id.tvOverlayLoadingText)
            tvDynamicStatus = findViewById(R.id.tvDynamicStatus)
            circularProgress = findViewById(R.id.circularProgress)
        }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_details)

        packId = intent.getStringExtra("id") ?: return finish()

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        // Durum çubuğunu ve üst barı tek renk yap
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = ContextCompat.getColor(this, R.color.toolbar_bg)
        
        // İlk yüklemeyi onResume halledecek, burada yapmaya gerek yok
        setupEdgeToEdge()
    }

    private fun setupEdgeToEdge() {
        val toolbarLayout = findViewById<View>(R.id.toolbarLayout)
        val bottomContainer = findViewById<View>(R.id.bottomContainer)
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.details_root)) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            toolbarLayout?.setPadding(toolbarLayout.paddingLeft, systemBars.top, toolbarLayout.paddingRight, toolbarLayout.paddingBottom)
            bottomContainer?.setPadding(bottomContainer.paddingLeft, bottomContainer.paddingTop, bottomContainer.paddingRight, systemBars.bottom)
            
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        // WhatsApp'tan geri döndüğümüzde buton durumunu güncelle
        if (::btnAction.isInitialized) {
            updateButton()
        }
        
        // İlk açılışta zaten çalışıyor, sadece geri dönüşlerde tekrar yükle
        if (hasLoadedOnce) {
            // Custom paketlerde sticker eklenmiş/silinmiş olabilir
            if (packId.startsWith("custom_")) {
                loadPackFromFirebase()
            }
            // Firebase paketleri için tekrar yüklemeye gerek yok, cache zaten güncel
        } else {
            hasLoadedOnce = true
            loadPackFromFirebase()
        }
    }

    private fun loadPackFromFirebase() {
        lifecycleScope.launch {
            try {
                var pack: Pack? = null
                
                // Custom paketler için HER ZAMAN güncel veriyi çek (yeni sticker eklendi olabilir)
                if (packId.startsWith("custom_")) {
                    pack = withContext(Dispatchers.IO) {
                        CustomStickerManager.toWhatsAppPack(this@DetailsActivity, packId)
                    }
                    if (pack != null) {
                        setupUI(pack)
                        showLoadingState(false)
                        return@launch // Custom paket için işlem tamam
                    }
                }
                
                // Firebase paketleri için cache'e bak
                pack = StickerRepository.allPacksCache.find { it.id == packId }

                // Eğer hala yoksa, lokal assets'ten hızlıca yüklemeyi dene
                if (pack == null) {
                    pack = withContext(Dispatchers.IO) { Loader.get(this@DetailsActivity, packId) }
                }

                if (pack != null) {
                    setupUI(pack)
                } else {
                    // Cache'de ve lokalde yoksa, loading göster
                    showLoadingState(true)
                }

                // Arka planda veriyi tazele (Firebase paketleri için) — ayrı coroutine
                lifecycleScope.launch {
                    try {
                        val packs = withContext(Dispatchers.IO) { StickerRepository.loadPacks(this@DetailsActivity, forceRefresh = false) }
                        val updatedPack = packs.find { it.id == packId }
                        
                        if (updatedPack != null && pack != null) {
                            val changed = updatedPack.stickers.size != pack.stickers.size ||
                                    updatedPack.isPremium != pack.isPremium ||
                                    updatedPack.isActive != pack.isActive ||
                                    updatedPack.localizedName != pack.localizedName
                            if (changed) {
                                setupUI(updatedPack)
                            }
                        } else if (updatedPack != null && pack == null) {
                            setupUI(updatedPack)
                        } else if (updatedPack == null && pack == null) {
                            android.util.Log.e("DetailsActivity", "Pack not found: $packId")
                            Toast.makeText(this@DetailsActivity, R.string.pack_not_found, Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("DetailsActivity", "Error refreshing pack: ${e.message}", e)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("DetailsActivity", "Error loading pack: ${e.message}", e)
                if (currentPack == null) {
                    Toast.makeText(this@DetailsActivity, R.string.pack_load_failed, Toast.LENGTH_SHORT).show()
                    finish()
                }
            } finally {
                // Sadece eğer indirme başlamamışsa loading'i kapat
                if (currentPack != null && !isDownloading) showLoadingState(false)
            }
        }
    }

    private fun setupUI(pack: Pack) {
        currentPack = pack
        isAnimatedPack = pack.isAnimated
        isPackReady = false // Reset state when setting up new pack

        if (pack.isAnimated && !pack.id.startsWith("custom_")) {
            StickerRepository.prepareAnimatedPackCache(this, pack.id)
        }

        // View count ve interstitial — tamamen arka planda
        lifecycleScope.launch(Dispatchers.IO) {
            StickerRepository.incrementViewCount(pack.id, pack.isPremium)
        }
        lifecycleScope.launch { AdManager.loadInterstitialAd(this@DetailsActivity) }

        findViewById<android.widget.TextView>(R.id.name).text = pack.localizedName
        setupPublisherStrip(pack)

        btnAction = findViewById(R.id.btnAction)
        btnWatchAd = findViewById(R.id.btnWatchAd)
        premiumButtonsContainer = findViewById(R.id.premiumButtonsContainer)
        customButtonsContainer = findViewById(R.id.customButtonsContainer)
        btnGridAddSticker = findViewById(R.id.btnGridAddSticker)
        btnGridDeleteMode = findViewById(R.id.btnGridDeleteMode)
        btnConfirmDelete = findViewById(R.id.btnConfirmDelete)
        installedIcon = findViewById(R.id.installedIcon)
        btnFixedWhatsApp = findViewById(R.id.btnFixedWhatsApp)
        setupPackSocialActions(pack)

        val toolbarLayout = findViewById<View>(R.id.toolbarLayout)
        val bottomContainer = findViewById<View>(R.id.bottomContainer)
        val tvName = findViewById<android.widget.TextView>(R.id.name)
        
        val toolbarColor = ContextCompat.getColor(this, R.color.toolbar_bg)
        val primaryColor = ContextCompat.getColor(this, R.color.primary)
        
        toolbarLayout.setBackgroundColor(toolbarColor)
        window.statusBarColor = toolbarColor
        window.navigationBarColor = Color.BLACK
        bottomContainer.setBackgroundColor(Color.TRANSPARENT)
        
        if (pack.isPremium) {
            tvName.setTextColor(Color.WHITE)
            circularProgress?.setIndicatorColor(ContextCompat.getColor(this, R.color.premium_gold))
            findViewById<View>(R.id.premiumIconToolbar).visibility = View.VISIBLE
        } else {
            tvName.setTextColor(ContextCompat.getColor(this, R.color.text_on_primary_secondary))
            circularProgress?.setIndicatorColor(primaryColor)
            findViewById<View>(R.id.premiumIconToolbar).visibility = View.GONE
        }

        // Başlangıçta içeriği göster
        btnAction.isEnabled = true
        showLoadingState(false)

        val rv = findViewById<RecyclerView>(R.id.rv)
        rv.layoutManager = GridLayoutManager(this, 3).apply {
            initialPrefetchItemCount = 6
        }
        rv.setHasFixedSize(true)
        rv.setItemViewCacheSize(20)
        rv.itemAnimator = null
        rv.setRecycledViewPool(RecyclerView.RecycledViewPool().apply { setMaxRecycledViews(0, 30) })

        val hasAccess = !pack.isPremium || PreferencesHelper.hasAccessToPack(this, pack.id)
        val storagePath = pack.storagePath

        // Pre-compute URLs synchronously (pure string construction, no network) so adapter
        // can immediately start Glide requests without waiting for a background coroutine.
        if (!pack.id.startsWith("custom_")) {
            pack.stickers.forEach { sticker ->
                if (sticker.url.isEmpty()) {
                    sticker.url = StickerRepository.getStickerDirectUrl(pack.id, sticker.file, storagePath)
                }
            }
        }

        // Kilitli paketlerde rastgele 3 çıkartmayı başa al
        val displayStickers = if (!hasAccess && pack.stickers.size > 3) {
            val shuffled = pack.stickers.shuffled()
            shuffled.take(3) + shuffled.drop(3)
        } else {
            pack.stickers
        }

        adapter = StickerAdapter(
            packId = pack.id,
            items = displayStickers,
            isPackPremium = pack.isPremium,
            hasAccess = hasAccess,
            storagePath = pack.storagePath,
            isAnimated = pack.isAnimated,
            selectedPositions = selectedIndices,
            onStickerClick = { sticker, _ ->
                val isLocked = !PreferencesHelper.hasAccessToPack(this, pack.id)
                showStickerPreview(sticker, isLocked)
            },
            onStickerLongClick = { _, _ ->
                if (pack.id.startsWith("custom_")) {
                    toggleDeleteMode()
                }
            },
            onSelectionChanged = { count ->
                if (isDeleteMode) {
                    findViewById<android.widget.TextView>(R.id.name).text = if (count > 0) "${getString(R.string.selection_count, count)}" else getString(R.string.selection_mode_title)
                }
            }
        )
        rv.adapter = adapter
        isPackReady = true
        rv.post { preloadStickerThumbnails(pack, displayStickers) }

        // Butonları ayarla
        setupButtons(pack, hasAccess)

        if (!autoAddConsumed && intent.getBooleanExtra(EXTRA_AUTO_ADD_TO_WHATSAPP, false)) {
            autoAddConsumed = true
            btnAction.post { addToWhatsApp(pack) }
        }

        // İlgili paketleri gecikmeli yükle (ilk render'ı bloklamasın)
        lifecycleScope.launch {
            delay(900)
            if (!isFinishing && !isDestroyed) setupRelatedPacks(pack)
        }
    }

    private fun preloadStickerThumbnails(pack: Pack, stickers: List<Sticker>) {
        if (stickers.isEmpty() || isDestroyed || isFinishing) return
        val glide = Glide.with(this)

        fun sourceFor(sticker: Sticker): Any? {
            if (pack.id.startsWith("custom_")) {
                return CustomStickerManager.getCustomStickerPath(this, pack.id, sticker.file)
                    .takeIf { it.exists() && it.length() > 0 }
            }
            val cachedFile = StickerRepository.getCachedStickerPath(this, pack.id, sticker.file)
            return when {
                cachedFile.exists() && cachedFile.length() > 0 -> cachedFile
                sticker.url.isNotBlank() -> sticker.url
                pack.storagePath.isNotBlank() -> StickerRepository.getStickerDirectUrl(pack.id, sticker.file, pack.storagePath)
                else -> android.net.Uri.parse("file:///android_asset/${pack.id}/${sticker.file}")
            }
        }

        fun preloadBatch(batch: List<Sticker>) {
            batch.forEach { sticker ->
                val source = sourceFor(sticker) ?: return@forEach
                // Sadece kaynak veriyi cache'e ısıt — hafif statik decode yeterli;
                // grid'in animasyonlu yüklemesi bu cache'ten beslenir.
                glide.asDrawable()
                    .load(source)
                    .override(256, 256)
                    .priority(com.bumptech.glide.Priority.LOW)
                    .dontAnimate()
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.AUTOMATIC)
                    .preload(256, 256)
            }
        }

        findViewById<RecyclerView>(R.id.rv).postDelayed({
            if (!isDestroyed && !isFinishing) preloadBatch(stickers.take(12))
        }, 180)
        findViewById<RecyclerView>(R.id.rv).postDelayed({
            if (!isDestroyed && !isFinishing) preloadBatch(stickers.drop(12).take(24))
        }, 900)
    }

    private fun toggleDeleteMode() {
        isDeleteMode = !isDeleteMode

        // Silme modu aktifken adapter'ı güncelle
        adapter?.setDeleteMode(isDeleteMode)

        if (isDeleteMode) {
            // Silme moduna girildi
            findViewById<android.widget.TextView>(R.id.name).text = getString(R.string.selection_mode_title)
            // Silme butonu hemen görünür (seçim olmasa bile)
            btnConfirmDelete.visibility = View.VISIBLE
        } else {
            // Silme modundan çıkıldı
            selectedIndices.clear()
            findViewById<android.widget.TextView>(R.id.name).text = currentPack?.localizedName
            btnConfirmDelete.visibility = View.GONE
        }
    }

    /**
     * Tüm çıkartmaları Glide ile memory cache'e preload et
     * Bu sayede RecyclerView bind olduğunda görseller anında görünür
     */
    private fun preloadAllStickers(pack: Pack, stickers: List<Sticker>) {
        // Preload işlemini arka planda paralel yap
        lifecycleScope.launch(Dispatchers.IO) {
            val glide = Glide.with(applicationContext)
            val storagePath = pack.storagePath

            // İlk 6 çıkartmayı öncelikli yükle (görünen 2 satır)
            val priorityStickers = stickers.take(6)
            val restStickers = stickers.drop(9)

            // Öncelikli olanları paralel yükle
            priorityStickers.map { sticker ->
                async {
                    try {
                        preloadSingleSticker(glide, pack, sticker, storagePath)
                    } catch (_: Exception) {}
                }
            }.awaitAll()

            // Geri kalanları arka planda yükle
            restStickers.forEach { sticker ->
                try {
                    preloadSingleSticker(glide, pack, sticker, storagePath)
                } catch (_: Exception) {}
            }
        }
    }

    private fun preloadSingleSticker(
        glide: com.bumptech.glide.RequestManager,
        pack: Pack,
        sticker: Sticker,
        storagePath: String
    ) {
        when {
            pack.id.startsWith("custom_") -> {
                val customFile = CustomStickerManager.getCustomStickerPath(applicationContext, pack.id, sticker.file)
                if (customFile.exists()) {
                    glide.load(customFile)
                        .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                        .submit(384, 384).get()
                }
            }
            else -> {
                val cachedFile = StickerRepository.getCachedStickerPath(applicationContext, pack.id, sticker.file)
                when {
                    cachedFile.exists() && cachedFile.length() > 0 -> {
                        glide.load(cachedFile)
                            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                            .submit(384, 384).get()
                    }
                    sticker.url.isNotEmpty() -> {
                        glide.load(sticker.url)
                            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.DATA)
                            .submit(384, 384).get()
                    }
                    storagePath.isNotEmpty() -> {
                        val directUrl = StickerRepository.getStickerDirectUrl(pack.id, sticker.file, storagePath)
                        glide.load(directUrl)
                            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.DATA)
                            .submit(384, 384).get()
                    }
                }
            }
        }
    }

    /**
     * Loading state'i göster/gizle
     * @param isLoading true ise loading göster, false ise gizle
     */
    private fun showLoadingState(isLoading: Boolean) {
        if (isLoading) ensureLoadingOverlay()
        val overlay = professionalLoadingOverlay ?: return

        if (isLoading) {
            circularProgress?.progress = 0
            tvOverlayLoadingText?.text = "0%"
            tvDynamicStatus?.let {
                it.text = ""
                it.visibility = View.VISIBLE
            }
            if (currentPack?.isPremium == true) {
                circularProgress?.setIndicatorColor(ContextCompat.getColor(this, R.color.premium_gold))
            }
            overlay.setBackgroundColor(Color.parseColor("#80000000"))
            overlay.visibility = View.VISIBLE
        } else {
            overlay.visibility = View.GONE
        }
    }

    private fun confirmDelete() {
        val selectedIndices = adapter?.selectedPositions?.toList()?.map { it + 1 } ?: return
        if (selectedIndices.isEmpty()) {
            toggleDeleteMode()
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_delete_sticker, null)
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(dialogView)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            android.view.WindowManager.LayoutParams.WRAP_CONTENT
        )

        dialogView.findViewById<TextView>(R.id.tvDeleteTitle).text = getString(R.string.delete_mode)
        dialogView.findViewById<TextView>(R.id.tvDeleteMsg).text =
            "Are you sure you want to delete ${selectedIndices.size} stickers? This will also update WhatsApp."

        dialogView.findViewById<View>(R.id.btnConfirmDelete).setOnClickListener {
            dialog.dismiss()
            if (CustomStickerManager.removeStickersFromPack(this, packId, selectedIndices)) {
                Toast.makeText(this, getString(R.string.stickers_deleted), Toast.LENGTH_SHORT).show()
                toggleDeleteMode()
                loadPackFromFirebase()
            } else {
                Toast.makeText(this, getString(R.string.error_delete_failed), Toast.LENGTH_SHORT).show()
                toggleDeleteMode()
            }
        }

        dialogView.findViewById<View>(R.id.btnCancelDelete).setOnClickListener {
            dialog.dismiss()
            toggleDeleteMode()
        }

        dialog.show()
    }

    private fun showDeletePackDialog(pack: Pack) {
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
                WhitelistCheck.isWhitelisted(this@DetailsActivity, pack.id)
            }

            val intent = Intent().apply {
                action = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
                putExtra("sticker_pack_id", pack.id)
                putExtra("sticker_pack_authority", "${packageName}.stickers")
                putExtra("sticker_pack_name", pack.localizedName)
            }

            try {
                removePackLauncher.launch(intent)
            } catch (e: Exception) {
                pendingDeletePackId = null
                waitingForWhatsAppReturn = false
                Toast.makeText(this@DetailsActivity, getString(R.string.whatsapp_not_installed), Toast.LENGTH_SHORT).show()
                confirmAndDirectDelete(pack)
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
                Toast.makeText(this, getString(R.string.pack_deleted_success), Toast.LENGTH_SHORT).show()
                finish()
            }
        }
        dialog.show()
    }

    private fun showDeleteStickerDialog(packId: String, index: Int) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_delete_sticker, null)
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(dialogView)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            android.view.WindowManager.LayoutParams.WRAP_CONTENT
        )

        dialogView.findViewById<View>(R.id.btnConfirmDelete).setOnClickListener {
            dialog.dismiss()
            if (CustomStickerManager.removeStickerFromPack(this, packId, index + 1)) {
                Toast.makeText(this, getString(R.string.sticker_deleted), Toast.LENGTH_SHORT).show()
                loadPackFromFirebase()
            } else {
                Toast.makeText(this, getString(R.string.sticker_delete_failed), Toast.LENGTH_SHORT).show()
            }
        }

        dialogView.findViewById<View>(R.id.btnCancelDelete).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    /**
     * Çıkartmaları cache'e indir
     * @param pack Paket
     * @param adapter Adapter
     * @param onComplete İndirme tamamlandığında çağrılacak callback (başarılı mı?)
     */
    private fun downloadStickersToCache(pack: Pack, adapter: StickerAdapter, onComplete: ((Boolean) -> Unit)? = null) {
        // Zaten hazırsa (custom/local) hemen bitir
        if (pack.stickers.isEmpty() || pack.stickers.all { it.url.isEmpty() }) {
            isPackReady = true
            onComplete?.invoke(true)
            return
        }

        // Her sticker indiğinde anında güncelle
        lifecycleScope.launch {
            var successCount = 0
            val total = pack.stickers.size

            // Paralel indir ama her biri tamamlandığında anında güncelle
            pack.stickers.forEachIndexed { index, sticker ->
                launch(Dispatchers.IO) {
                    try {
                        StickerRepository.downloadStickerToCache(
                            this@DetailsActivity,
                            pack.id,
                            sticker.file,
                            pack.storagePath,
                            sticker.url,
                            allowCompression = !pack.isAnimated
                        )
                        successCount++
                        // İndirme tamamlandığında hemen bu sticker'ı güncelle
                        withContext(Dispatchers.Main) {
                            adapter.notifyItemChanged(index)
                        }
                    } catch (e: Exception) {
                        // Hata olsa bile devam et
                    }
                    // Tümü tamamlandıysa callback çağır
                    if (successCount == total) {
                        withContext(Dispatchers.Main) {
                            isPackReady = true
                            onComplete?.invoke(true)
                        }
                    }
                }
            }
        }
    }

    private fun setupButtons(pack: Pack, hasAccess: Boolean) {
        val isCustom = pack.category == "custom" || pack.id.startsWith("custom_")
        val btnPublishTop = findViewById<android.widget.ImageButton>(R.id.btnPublishPackTop)

        // Ortak click listener'lar
        btnWatchAd.setOnClickListener {
            showRewardedAdForPack(pack)
        }
        
        val btnPremiumBadge = findViewById<MaterialButton>(R.id.btnPremiumBadge)
        btnPremiumBadge.setOnClickListener {
            startActivity(Intent(this, PremiumActivity::class.java))
        }

        btnAction.setOnClickListener {
            handleButtonClick(pack)
        }

        // For custom packs, use the fixed bottom WhatsApp button
        if (isCustom) {
            btnFixedWhatsApp?.visibility = View.VISIBLE
            btnFixedWhatsApp?.setOnClickListener {
                // Always send to WhatsApp (both add and update use the same intent)
                addToWhatsApp(pack)
            }
        }

        if (isCustom) {
            installedIcon.visibility = View.GONE // Custom packs don't use this icon
            btnPublishTop?.visibility = View.VISIBLE

            // Custom pack add sticker, delete vs.
            btnGridAddSticker.setOnClickListener {
                val isAnimatedPack = pack.isAnimated || CustomStickerManager.getPackInfo(this, pack.id)?.isAnimated == true
                val intent = if (isAnimatedPack) {
                    Intent(this, AnimatedStickerActivity::class.java)
                } else {
                    Intent(this, StickerMakerActivity::class.java).putExtra("skipTypeSelection", true)
                }
                intent.putExtra("packId", pack.id)
                addStickerLauncher.launch(intent)
            }
            
            btnGridDeleteMode.setOnClickListener { toggleDeleteMode() }
            btnConfirmDelete.setOnClickListener { confirmDelete() }

            // Silme butonu için uzun basma (tüm paketi sil)
            btnGridDeleteMode.setOnLongClickListener {
                showDeletePackDialog(pack)
                true
            }

            // Publish to Store button
            checkIfAlreadySubmitted(pack.id) { alreadySubmitted ->
                if (alreadySubmitted) {
                    btnPublishTop?.isEnabled = true
                    btnPublishTop?.alpha = 1f
                    btnPublishTop?.setOnClickListener {
                        Toast.makeText(this, getString(R.string.publish_pack_already_submitted), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val publishClick = View.OnClickListener {
                        if (pack.stickers.size !in 9..30) {
                            Toast.makeText(this, getString(R.string.publish_pack_count_range), Toast.LENGTH_SHORT).show()
                            return@OnClickListener
                        }
                        val intent = Intent(this, SubmitPackActivity::class.java)
                        intent.putExtra("packId", pack.id)
                        startActivity(intent)
                        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                    }
                    btnPublishTop?.isEnabled = true
                    btnPublishTop?.alpha = 1f
                    btnPublishTop?.setOnClickListener(publishClick)
                }
            }
        } else {
            btnPublishTop?.visibility = View.GONE
        }

        // Her durumda UI'ı WhatsApp senkronizasyonu ile güncelle
        updateButton()
        btnAction.isEnabled = true
        showLoadingState(false)
    }

    private fun setupRelatedPacks(currentPack: Pack) {
        val section = findViewById<View>(R.id.relatedPacksSection)
        val rvRelated = findViewById<RecyclerView>(R.id.rvRelatedPacks)

        // Custom paketlerde ilgili paketleri gösterme
        if (currentPack.id.startsWith("custom_")) {
            section.visibility = View.GONE
            return
        }

        val allPacks = StickerRepository.allPacksCache
        if (allPacks.isEmpty()) {
            section.visibility = View.GONE
            return
        }

        // Rastgele 25 paket göster (mevcut paket hariç)
        val relatedPacks = allPacks.filter {
            it.id != currentPack.id && it.isActive && !it.id.startsWith("custom_") &&
            it.stickers.isNotEmpty()
        }.shuffled().take(25)

        if (relatedPacks.isEmpty()) {
            section.visibility = View.GONE
            return
        }

        section.visibility = View.VISIBLE
        val tvRelatedTitle = findViewById<TextView>(R.id.tvRelatedTitle)
        tvRelatedTitle?.text = getString(R.string.related_packs)
        rvRelated.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        rvRelated.setHasFixedSize(false)
        rvRelated.itemAnimator = null
        installCenteredListPadding(rvRelated)

        val relatedAdapter = PackAdapter(
            items = relatedPacks,
            click = { pack ->
                startActivity(Intent(this, DetailsActivity::class.java).putExtra("id", pack.id))
            },
            onAddClick = { pack ->
                addToWhatsApp(pack)
            }
        )
        rvRelated.adapter = relatedAdapter
    }

    private fun installCenteredListPadding(list: RecyclerView) {
        val minPadding = 8.dp()
        val maxContentWidth = 620.dp()
        list.clipToPadding = false
        list.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            val sidePadding = ((view.width - maxContentWidth) / 2).coerceAtLeast(minPadding)
            if (view.paddingLeft != sidePadding || view.paddingRight != sidePadding) {
                view.setPadding(sidePadding, view.paddingTop, sidePadding, view.paddingBottom)
            }
        }
    }

    private fun launchPremiumPurchase() {
        startActivity(Intent(this, PremiumActivity::class.java))
    }

    private fun setupPublisherStrip(pack: Pack) {
        val isCustom = pack.category == "custom" || pack.id.startsWith("custom_")
        val strip = findViewById<LinearLayout>(R.id.publisherStrip) ?: return
        if (isCustom) {
            strip.visibility = View.GONE
            return
        }
        val avatar = findViewById<ImageView>(R.id.publisherAvatar)
        val identityContainer = findViewById<View>(R.id.publisherIdentityContainer)
        val name = findViewById<TextView>(R.id.tvPublisherName)
        val hint = findViewById<TextView>(R.id.tvPublisherHint)
        val followButton = findViewById<MaterialButton>(R.id.btnFollowPublisher)
        followButton?.visibility = View.GONE
        strip.visibility = View.VISIBLE

        val publisherName = pack.pub.ifBlank { getString(R.string.app_name) }
        if (publisherName.equals("Sticky", ignoreCase = true)) {
            avatar?.visibility = View.VISIBLE
            identityContainer?.visibility = View.VISIBLE
            avatar?.setImageResource(R.mipmap.ic_launcher_round)
            name?.text = getString(R.string.app_name)
            hint?.visibility = View.GONE
            strip.setOnClickListener(null)
            strip.isClickable = false
            avatar?.setOnClickListener(null)
            avatar?.isClickable = false
            return
        }

        avatar?.visibility = View.VISIBLE
        identityContainer?.visibility = View.VISIBLE
        hint?.visibility = View.GONE
        name?.text = publisherName
        val clickListener = View.OnClickListener { openPublisherProfile(pack) }
        strip.isClickable = true
        strip.setOnClickListener(clickListener)
        avatar?.setOnClickListener(clickListener)

        if (pack.email.isNotBlank()) {
            lifecycleScope.launch {
                val profile = withContext(Dispatchers.IO) {
                    try {
                        PocketBaseHelper.listRecords("user_profiles", filter = "email='${pack.email.replace("'", "\\'")}'", perPage = 1).firstOrNull()
                    } catch (_: Exception) { null }
                }
                val photoUrl = profile?.optString("photo_url").orEmpty()
                    .ifBlank { profile?.optString("avatar_url").orEmpty() }
                    .ifBlank { profile?.optString("picture").orEmpty() }
                    .ifBlank { pack.publisherPhotoUrl }
                val displayName = profile?.let { it.optString("display_name", it.optString("name", pack.pub)) }.orEmpty()
                if (displayName.isNotBlank()) name?.text = displayName
                if (photoUrl.isNotBlank() && avatar != null) {
                    Glide.with(this@DetailsActivity).load(photoUrl).circleCrop().placeholder(R.drawable.ic_person).into(avatar)
                }
            }
        }
    }

    private fun isFollowingPublisher(email: String): Boolean {
        if (email.isBlank()) return false
        return getSharedPreferences("sticky_prefs", MODE_PRIVATE)
            .getStringSet("followed_publishers", emptySet())
            ?.contains(email) == true
    }

    private fun togglePublisherFollow(email: String) {
        if (email.isBlank()) return
        val prefs = getSharedPreferences("sticky_prefs", MODE_PRIVATE)
        val current = prefs.getStringSet("followed_publishers", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (current.contains(email)) current.remove(email) else current.add(email)
        prefs.edit().putStringSet("followed_publishers", current).apply()
    }

    private fun updateFollowButton(button: MaterialButton?, email: String) {
        button ?: return
        val following = isFollowingPublisher(email)
        button.text = if (following) "Following" else "Follow"
        button.alpha = if (following) 0.75f else 1f
    }

    private fun escapePb(value: String): String = value.replace("'", "\\'")

    private suspend fun ensurePackCanBeSubmittedAgain(
        sourcePackId: String,
        userId: String,
        userEmail: String,
        deviceId: String,
        signature: String,
        stickerCount: Int
    ) {
        val records = runCatching {
            PocketBaseHelper.listAllRecords(
                "user_submissions",
                filter = "source_pack_id='${escapePb(sourcePackId)}'",
                perPage = 200
            )
        }.getOrElse { throw IllegalStateException(getString(R.string.publish_pack_already_submitted)) }
        val userKeys = listOf(userId, userEmail, deviceId).map { it.trim().lowercase() }.filter { it.isNotBlank() }
        val alreadySubmitted = records.any { record ->
            val ownerKeys = listOf(record.optString("user_id"), record.optString("user_email"), record.optString("device_id"))
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() }
            userKeys.any { ownerKeys.contains(it) }
        }
        if (alreadySubmitted) throw IllegalStateException(getString(R.string.publish_pack_already_submitted))
    }

    private fun setupPackSocialActions(pack: Pack) {
        packLikeInteracted = false
        packLikeInFlight = false
        val summaryView = findViewById<TextView>(R.id.tvPackSocialSummary)
        val likeBtn = findViewById<LinearLayout>(R.id.btnPackLike)
        val commentBtn = findViewById<LinearLayout>(R.id.btnPackComments)
        val tvLikeCount = findViewById<TextView>(R.id.tvPackLikeCount)
        val tvCommentCount = findViewById<TextView>(R.id.tvPackCommentCount)

        // Counters under Add to WhatsApp removed as requested
        summaryView?.visibility = View.GONE

        if (pack.id.startsWith("custom_")) {
            likeBtn?.visibility = View.GONE
            commentBtn?.visibility = View.GONE
            return
        }

        likeBtn?.visibility = View.VISIBLE
        commentBtn?.visibility = View.VISIBLE

        // Initialize tracked counts from pack data
        currentPackLikeCount = pack.likeCount
        currentPackCommentCount = pack.commentCount
        currentPackFavoriteCount = pack.favoriteCount

        tvLikeCount?.text = formatCompactNumber(currentPackLikeCount)
        tvCommentCount?.text = formatCompactNumber(currentPackCommentCount)

        applyLikeVisualToLayout(likeBtn, SocialRepository.isLocallyLiked(this, pack.id))

        likeBtn?.setOnClickListener {
            if (!requireSocialSignIn()) return@setOnClickListener
            if (packLikeInFlight) return@setOnClickListener
            packLikeInFlight = true
            packLikeInteracted = true

            val wasLiked = SocialRepository.isLocallyLiked(this, pack.id)
            val nowLiked = !wasLiked
            // Optimistic update — instant visual feedback before server responds
            SocialRepository.setLocalLike(this, pack.id, nowLiked)
            applyLikeVisualToLayout(likeBtn, nowLiked)
            currentPackLikeCount = (currentPackLikeCount + if (nowLiked) 1 else -1).coerceAtLeast(0)
            tvLikeCount?.text = formatCompactNumber(currentPackLikeCount)
            animateLikeButton(likeBtn)

            lifecycleScope.launch {
                runCatching { SocialRepository.togglePackLike(this@DetailsActivity, pack) }
                    .onSuccess { result ->
                        val liked = result.optBoolean("liked", false)
                        applyLikeVisualToLayout(likeBtn, liked)
                        val serverCount = result.optInt("like_count", -1)
                        if (serverCount >= 0) {
                            currentPackLikeCount = serverCount
                            tvLikeCount?.text = formatCompactNumber(currentPackLikeCount)
                            // Sync cache so home page shows updated count
                            StickerRepository.allPacksCache = StickerRepository.allPacksCache.map {
                                if (it.id == pack.id) it.copy(likeCount = serverCount) else it
                            }
                        }
                    }
                    .onFailure {
                        // Revert on failure
                        SocialRepository.setLocalLike(this@DetailsActivity, pack.id, wasLiked)
                        applyLikeVisualToLayout(likeBtn, wasLiked)
                        currentPackLikeCount = (currentPackLikeCount + if (wasLiked) 1 else -1).coerceAtLeast(0)
                        tvLikeCount?.text = formatCompactNumber(currentPackLikeCount)
                        showThemedSnackbar(it.message ?: getString(R.string.error_generic))
                    }
                delay(1000) // 1 second cooldown — prevents rapid double-tap race condition
                packLikeInFlight = false
            }
        }

        commentBtn?.setOnClickListener {
            showCommentsSheet(pack)
        }

        // Report button in publisher strip (next to like/comment)
        findViewById<LinearLayout>(R.id.btnPackReport)?.setOnClickListener {
            showReportDialog(pack)
        }

        lifecycleScope.launch {
            runCatching { SocialRepository.fetchPackSocial(this@DetailsActivity, pack.id) }.onSuccess { social ->
                // Only update liked visual if user hasn't already tapped — prevents race condition overwrite
                if (!packLikeInteracted) {
                    applyLikeVisualToLayout(likeBtn, social.optBoolean("liked", false))
                }
                currentPackLikeCount = social.optInt("like_count", currentPackLikeCount)
                currentPackCommentCount = social.optInt("comment_count", currentPackCommentCount)
                tvLikeCount?.text = formatCompactNumber(currentPackLikeCount)
                tvCommentCount?.text = formatCompactNumber(currentPackCommentCount)
            }
        }
    }

    private fun applyLikeVisualToLayout(layout: LinearLayout?, liked: Boolean) {
        layout ?: return
        val icon = layout.getChildAt(0) as? ImageView
        val text = layout.getChildAt(1) as? TextView
        // Liked → bright gold. Not liked → solid white (matches the rest of the toolbar icons).
        val color = ContextCompat.getColor(this, if (liked) R.color.premium_gold else R.color.white)
        if (icon != null) {
            // XML uses app:tint (AppCompat supportImageTintList) — must override via ImageViewCompat,
            // otherwise the supportImageTintList stays at the XML value of @color/white.
            androidx.core.widget.ImageViewCompat.setImageTintList(icon, android.content.res.ColorStateList.valueOf(color))
            icon.setColorFilter(color)
        }
        text?.setTextColor(color)
    }

    private fun formatCompactNumber(count: Int): String {
        return when {
            count >= 1000000 -> String.format("%.1fM", count / 1000000.0)
            count >= 1000 -> String.format("%.1fK", count / 1000.0)
            else -> count.toString()
        }
    }

    private fun engagementSummary(likes: Int, favorites: Int, comments: Int): String =
        "$likes ${getString(R.string.likes_short)} / $comments ${getString(R.string.comments).lowercase()} / $favorites favorites"

    private fun roundedDrawable(color: Int, radiusDp: Int, strokeColor: Int? = null, strokeWidthDp: Int = 1): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusDp.dp().toFloat()
            setColor(color)
            strokeColor?.let { setStroke(strokeWidthDp.dp(), it) }
        }

    private fun applyLikeVisual(button: MaterialButton, liked: Boolean) {
        val color = ContextCompat.getColor(this, if (liked) R.color.primary else R.color.text_hint)
        button.iconTint = ColorStateList.valueOf(color)
        button.strokeColor = ColorStateList.valueOf(color)
        button.rippleColor = ColorStateList.valueOf(color)
        button.alpha = if (liked) 1f else 0.92f
    }

    private fun requireSocialSignIn(): Boolean {
        if (SocialRepository.isSignedIn(this)) return true
        Toast.makeText(this, getString(R.string.profile_login_required), Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, SettingsActivity::class.java))
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        return false
    }

    private fun animateLikeButton(button: View) {
        button.animate().cancel()
        button.scaleX = 0.82f
        button.scaleY = 0.82f
        button.animate()
            .scaleX(1.15f)
            .scaleY(1.15f)
            .setDuration(120)
            .withEndAction {
                button.animate().scaleX(1f).scaleY(1f).setDuration(140).setInterpolator(OvershootInterpolator()).start()
            }
            .start()
    }

    private fun showCommentsSheet(pack: Pack) {
        val sheet = BottomSheetDialog(this, R.style.TransparentBottomSheetDialog)
        sheet.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val view = layoutInflater.inflate(R.layout.sheet_comments, null)

        val commentsContainer = view.findViewById<LinearLayout>(R.id.commentsContainer)
        val loadingFrame = view.findViewById<android.widget.FrameLayout>(R.id.commentsLoadingFrame)
        val tvCommentCount = view.findViewById<TextView>(R.id.tvCommentCount)
        val btnClose = view.findViewById<ImageView>(R.id.btnCloseComments)
        val replyBanner = view.findViewById<LinearLayout>(R.id.replyBanner)
        val tvReplyingTo = view.findViewById<TextView>(R.id.tvReplyingTo)
        val btnCancelReply = view.findViewById<ImageView>(R.id.btnCancelReply)
        val ivInputAvatar = view.findViewById<ImageView>(R.id.ivInputAvatar)
        val etInput = view.findViewById<android.widget.EditText>(R.id.etCommentInput)
        val btnSend = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSendComment)

        btnClose.setOnClickListener { sheet.dismiss() }

        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val photoUrl = currentUser?.photoUrl?.toString()
        if (!photoUrl.isNullOrBlank()) {
            Glide.with(this).load(photoUrl).circleCrop().placeholder(R.drawable.ic_person).into(ivInputAvatar)
        } else {
            ivInputAvatar.setColorFilter(ContextCompat.getColor(this, R.color.modern_primary))
        }

        var replyToCommentId: String? = null

        btnCancelReply.setOnClickListener {
            replyToCommentId = null
            etInput.hint = getString(R.string.comment_hint)
            replyBanner.visibility = View.GONE
        }

        fun updateSendButton() {
            val hasText = etInput.text.toString().trim().isNotBlank()
            btnSend.isEnabled = hasText
            btnSend.alpha = if (hasText) 1f else 0.5f
        }

        etInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updateSendButton() }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        fun refreshComments() {
            commentsContainer.removeAllViews()
            loadingFrame.visibility = View.VISIBLE
            lifecycleScope.launch {
                runCatching { SocialRepository.fetchComments(this@DetailsActivity, pack.id) }
                    .onSuccess { comments ->
                        loadingFrame.visibility = View.GONE
                        tvCommentCount.text = comments.length().toString()
                        currentPackCommentCount = comments.length()
                        findViewById<TextView>(R.id.tvPackCommentCount)?.text = formatCompactNumber(currentPackCommentCount)
                        bindComments(commentsContainer, comments, pack.id) { commentId, author ->
                            replyToCommentId = commentId
                            tvReplyingTo.text = getString(R.string.reply_to_user, author)
                            replyBanner.visibility = View.VISIBLE
                            etInput.requestFocus()
                        }
                    }
                    .onFailure {
                        loadingFrame.visibility = View.GONE
                        tvCommentCount.text = "0"
                        bindComments(commentsContainer, JSONArray(), pack.id) { _, _ -> }
                    }
            }
        }

        btnSend.setOnClickListener {
            if (!requireSocialSignIn()) return@setOnClickListener
            val body = etInput.text.toString().trim()
            if (body.isBlank()) return@setOnClickListener
            btnSend.isEnabled = false
            btnSend.alpha = 0.4f
            lifecycleScope.launch {
                val parentId = replyToCommentId
                runCatching {
                    if (parentId == null) SocialRepository.addComment(this@DetailsActivity, pack, body)
                    else SocialRepository.addCommentReply(this@DetailsActivity, parentId, pack.id, body)
                }.onSuccess { result ->
                    val viewer = SocialRepository.currentUser(this@DetailsActivity)
                    val now = java.time.Instant.now().toString()
                    etInput.setText("")
                    replyToCommentId = null
                    etInput.hint = getString(R.string.comment_hint)
                    replyBanner.visibility = View.GONE
                    updateSendButton()
                    currentPackCommentCount++
                    findViewById<TextView>(R.id.tvPackCommentCount)?.text = formatCompactNumber(currentPackCommentCount)
                    if (parentId == null) {
                        val newComment = JSONObject().apply {
                            put("id", result.optJSONObject("comment")?.optString("id") ?: "")
                            put("display_name", viewer.name.ifBlank { viewer.email })
                            put("photo_url", viewer.photoUrl)
                            put("body", body)
                            put("like_count", 0)
                            put("liked", false)
                            put("replies", JSONArray())
                            put("created_at", now)
                        }
                        val newView = buildCommentRow(newComment, pack.id) { cId, author ->
                            replyToCommentId = cId
                            tvReplyingTo.text = getString(R.string.reply_to_user, author)
                            replyBanner.visibility = View.VISIBLE
                            etInput.requestFocus()
                        }
                        commentsContainer.addView(newView, 0)
                        tvCommentCount.text = currentPackCommentCount.toString()
                    } else {
                        val replyData = result.optJSONObject("reply") ?: result
                        val newReply = JSONObject().apply {
                            put("id", replyData.optString("id", ""))
                            put("display_name", viewer.name.ifBlank { viewer.email })
                            put("photo_url", viewer.photoUrl)
                            put("body", body)
                            put("like_count", 0)
                            put("liked", false)
                            put("created_at", now)
                        }
                        for (i in 0 until commentsContainer.childCount) {
                            val child = commentsContainer.getChildAt(i)
                            if (child.tag == parentId) {
                                val repliesContainer = child.findViewById<LinearLayout>(R.id.repliesContainer)
                                val replyView = buildReplyRow(newReply, pack.id, parentId) { cId, author ->
                                    replyToCommentId = cId
                                    tvReplyingTo.text = getString(R.string.reply_to_user, author)
                                    replyBanner.visibility = View.VISIBLE
                                    etInput.requestFocus()
                                }
                                repliesContainer.addView(replyView)
                                break
                            }
                        }
                    }
                }.onFailure { showThemedSnackbar(it.message ?: getString(R.string.error_generic)) }
                btnSend.isEnabled = true
                btnSend.alpha = 1f
            }
        }

        sheet.setContentView(view)
        sheet.setOnShowListener {
            refreshComments()
            val bottomSheet = sheet.findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let {
                val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(it)
                it.background = ContextCompat.getDrawable(this, R.drawable.bg_bottom_sheet_white_rounded)
                val sheetHeight = (resources.displayMetrics.heightPixels * 0.48).toInt()
                it.layoutParams = it.layoutParams.apply { height = sheetHeight }
                behavior.peekHeight = sheetHeight
                behavior.isFitToContents = true
                behavior.skipCollapsed = true
                behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            }
        }
        sheet.show()
    }

    private fun bindComments(container: LinearLayout, comments: JSONArray, packId: String, onReply: (String, String) -> Unit) {
        container.removeAllViews()
        if (comments.length() == 0) {
            val emptyView = layoutInflater.inflate(R.layout.view_empty_comments, container, false)
            container.addView(emptyView)
            return
        }
        for (index in 0 until comments.length()) {
            val comment = comments.optJSONObject(index) ?: continue
            val commentView = buildCommentRow(comment, packId, onReply)
            container.addView(commentView)
        }
    }

    private fun buildCommentRow(comment: JSONObject, packId: String, onReply: (String, String) -> Unit): View {
        val author = comment.optString("display_name").ifBlank { comment.optString("user_email", "Sticky user") }
        val photoUrl = comment.optString("photo_url").ifBlank { comment.optString("avatar_url", "") }
        val commentId = comment.optString("id")
        val isLiked = comment.optBoolean("liked", false)
        var likeCount = comment.optInt("like_count", 0)

        val row = layoutInflater.inflate(R.layout.item_comment, null, false)
        val ivAvatar = row.findViewById<ImageView>(R.id.ivAvatar)
        val tvAuthor = row.findViewById<TextView>(R.id.tvAuthor)
        val tvBody = row.findViewById<TextView>(R.id.tvBody)
        val tvTime = row.findViewById<TextView>(R.id.tvTime)
        val btnLike = row.findViewById<LinearLayout>(R.id.btnLikeComment)
        val ivLikeIcon = row.findViewById<ImageView>(R.id.ivLikeIcon)
        val tvLikeCount = row.findViewById<TextView>(R.id.tvLikeCount)
        val tvReply = row.findViewById<TextView>(R.id.tvReply)
        val repliesContainer = row.findViewById<LinearLayout>(R.id.repliesContainer)

        tvAuthor.text = author
        tvBody.text = comment.optString("body")
        tvLikeCount.text = if (likeCount > 0) likeCount.toString() else ""
        tvTime.text = formatRelativeTime(comment.optString("created_at", comment.optString("created", "")))

        val likeColor = if (isLiked) R.color.primary else R.color.text_hint
        ivLikeIcon.setColorFilter(ContextCompat.getColor(this, likeColor))
        tvLikeCount.setTextColor(ContextCompat.getColor(this, likeColor))

        ivAvatar.imageTintList = null
        if (photoUrl.isNotBlank()) {
            Glide.with(this).load(photoUrl).circleCrop().placeholder(R.drawable.ic_person).into(ivAvatar)
        }

        btnLike.setOnClickListener {
            if (!requireSocialSignIn()) return@setOnClickListener
            animateLike(ivLikeIcon)
            lifecycleScope.launch {
                runCatching { SocialRepository.toggleCommentLike(this@DetailsActivity, commentId, packId) }
                    .onSuccess { result ->
                        val nowLiked = result.optBoolean("liked", false)
                        likeCount = result.optInt("like_count", likeCount)
                        val color = if (nowLiked) R.color.primary else R.color.text_hint
                        ivLikeIcon.setColorFilter(ContextCompat.getColor(this@DetailsActivity, color))
                        tvLikeCount.setTextColor(ContextCompat.getColor(this@DetailsActivity, color))
                        tvLikeCount.text = if (likeCount > 0) likeCount.toString() else ""
                    }
                    .onFailure { showThemedSnackbar(it.message ?: getString(R.string.error_generic)) }
            }
        }

        val tvTranslatedBody = row.findViewById<TextView>(R.id.tvTranslatedBody)
        val tvTranslate = row.findViewById<TextView>(R.id.tvTranslate)
        var translatedText: String? = null
        tvTranslate.setOnClickListener {
            if (translatedText != null) {
                if (tvTranslatedBody.visibility == View.VISIBLE) {
                    tvTranslatedBody.visibility = View.GONE
                    tvTranslate.text = getString(R.string.translate)
                } else {
                    tvTranslatedBody.visibility = View.VISIBLE
                    tvTranslate.text = getString(R.string.show_original)
                }
                return@setOnClickListener
            }
            tvTranslate.text = getString(R.string.translating)
            tvTranslate.isEnabled = false
            lifecycleScope.launch {
                val lang = PreferencesHelper.getLanguage(this@DetailsActivity)
                val result = SocialRepository.translateText(comment.optString("body"), lang)
                translatedText = result
                tvTranslatedBody.text = result
                tvTranslatedBody.visibility = View.VISIBLE
                tvTranslate.text = getString(R.string.show_original)
                tvTranslate.isEnabled = true
            }
        }

        row.tag = commentId

        // Long-press → delete (author only). The auth check is enforced by the worker too,
        // but hide the option for non-authors to avoid a confusing 403 toast.
        val viewer = SocialRepository.currentUser(this)
        val isAuthor = comment.optString("user_id").equals(viewer.id, ignoreCase = true) ||
            (viewer.email.isNotBlank() && comment.optString("user_email").equals(viewer.email, ignoreCase = true))
        if (isAuthor) {
            row.setOnLongClickListener {
                val dialogView = layoutInflater.inflate(R.layout.dialog_delete_confirm, null)
                dialogView.findViewById<TextView>(R.id.tvDeleteTitle).text = getString(R.string.comment_delete_title)
                dialogView.findViewById<TextView>(R.id.tvDeleteMessage).text = getString(R.string.comment_delete_message)
                val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnDeleteCancel)
                val btnConfirm = dialogView.findViewById<MaterialButton>(R.id.btnDeleteConfirm)
                btnCancel.text = getString(android.R.string.cancel)
                btnConfirm.text = getString(R.string.delete)
                val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
                    .setView(dialogView)
                    .create()
                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                btnCancel.setOnClickListener { dialog.dismiss() }
                btnConfirm.setOnClickListener {
                    dialog.dismiss()
                    // Optimistic remove: hide row immediately so the user sees the action take
                    // effect even if the worker is slow. The session-level deletedCommentIds
                    // set in SocialRepository keeps it hidden across refreshes.
                    SocialRepository.markCommentDeleted(commentId)
                    val parent = row.parent as? ViewGroup
                    parent?.removeView(row)
                    lifecycleScope.launch {
                        runCatching { SocialRepository.deleteComment(this@DetailsActivity, commentId, packId) }
                            .onFailure { showThemedSnackbar(it.message ?: getString(R.string.error_generic)) }
                    }
                }
                dialog.show()
                true
            }
        }
        tvReply.setOnClickListener { onReply(commentId, author) }

        val replies = comment.optJSONArray("replies") ?: JSONArray()
        repliesContainer.removeAllViews()
        for (i in 0 until replies.length()) {
            val item = replies.optJSONObject(i) ?: continue
            val replyView = buildReplyRow(item, packId, commentId, onReply)
            repliesContainer.addView(replyView)
        }

        return row
    }

    private fun animateLike(view: View) {
        ObjectAnimator.ofPropertyValuesHolder(
            view,
            android.animation.PropertyValuesHolder.ofFloat("scaleX", 1f, 1.45f, 1f),
            android.animation.PropertyValuesHolder.ofFloat("scaleY", 1f, 1.45f, 1f)
        ).apply {
            duration = 280
            interpolator = OvershootInterpolator(2f)
        }.start()
    }

    private fun buildReplyRow(item: JSONObject, packId: String, parentCommentId: String, onReply: (String, String) -> Unit): View {
        val replyAuthor = item.optString("display_name", "Sticky user")
        val replyId = item.optString("id")
        val replyPhoto = item.optString("photo_url", item.optString("avatar_url", ""))
        val replyLiked = item.optBoolean("liked", false)
        var replyLikeCount = item.optInt("like_count", 0)

        val row = layoutInflater.inflate(R.layout.item_reply, null, false)
        val ivAvatar = row.findViewById<ImageView>(R.id.ivReplyAvatar)
        val tvAuthor = row.findViewById<TextView>(R.id.tvReplyAuthor)
        val tvBody = row.findViewById<TextView>(R.id.tvReplyBody)
        val tvTime = row.findViewById<TextView>(R.id.tvReplyTime)
        val btnLike = row.findViewById<LinearLayout>(R.id.btnLikeReply)
        val ivLikeIcon = row.findViewById<ImageView>(R.id.ivReplyLikeIcon)
        val tvLikeCount = row.findViewById<TextView>(R.id.tvReplyLikeCount)
        val tvReply = row.findViewById<TextView>(R.id.tvReplyReply)

        tvAuthor.text = replyAuthor
        tvBody.text = item.optString("body", "")
        tvLikeCount.text = if (replyLikeCount > 0) replyLikeCount.toString() else ""
        tvTime.text = formatRelativeTime(item.optString("created_at", item.optString("created", "")))

        val likeColor = if (replyLiked) R.color.primary else R.color.text_hint
        ivLikeIcon.setColorFilter(ContextCompat.getColor(this, likeColor))
        tvLikeCount.setTextColor(ContextCompat.getColor(this, likeColor))

        ivAvatar.imageTintList = null
        if (replyPhoto.isNotBlank()) {
            Glide.with(this).load(replyPhoto).circleCrop().placeholder(R.drawable.ic_person).into(ivAvatar)
        }

        btnLike.setOnClickListener {
            if (!requireSocialSignIn()) return@setOnClickListener
            animateLike(ivLikeIcon)
            lifecycleScope.launch {
                runCatching { SocialRepository.toggleReplyLike(this@DetailsActivity, replyId, packId) }
                    .onSuccess { res ->
                        val nowLiked = res.optBoolean("liked", false)
                        replyLikeCount = res.optInt("like_count", replyLikeCount)
                        val color = if (nowLiked) R.color.primary else R.color.text_hint
                        ivLikeIcon.setColorFilter(ContextCompat.getColor(this@DetailsActivity, color))
                        tvLikeCount.setTextColor(ContextCompat.getColor(this@DetailsActivity, color))
                        tvLikeCount.text = if (replyLikeCount > 0) replyLikeCount.toString() else ""
                    }
                    .onFailure { showThemedSnackbar(it.message ?: getString(R.string.error_generic)) }
            }
        }

        val tvReplyTranslatedBody = row.findViewById<TextView>(R.id.tvReplyTranslatedBody)
        val tvReplyTranslate = row.findViewById<TextView>(R.id.tvReplyTranslate)
        var replyTranslated: String? = null
        tvReplyTranslate.setOnClickListener {
            if (replyTranslated != null) {
                if (tvReplyTranslatedBody.visibility == View.VISIBLE) {
                    tvReplyTranslatedBody.visibility = View.GONE
                    tvReplyTranslate.text = getString(R.string.translate)
                } else {
                    tvReplyTranslatedBody.visibility = View.VISIBLE
                    tvReplyTranslate.text = getString(R.string.show_original)
                }
                return@setOnClickListener
            }
            tvReplyTranslate.text = getString(R.string.translating)
            tvReplyTranslate.isEnabled = false
            lifecycleScope.launch {
                val lang = PreferencesHelper.getLanguage(this@DetailsActivity)
                val result = SocialRepository.translateText(item.optString("body"), lang)
                replyTranslated = result
                tvReplyTranslatedBody.text = result
                tvReplyTranslatedBody.visibility = View.VISIBLE
                tvReplyTranslate.text = getString(R.string.show_original)
                tvReplyTranslate.isEnabled = true
            }
        }

        tvReply.setOnClickListener { onReply(parentCommentId, replyAuthor) }

        return row
    }

    private fun formatRelativeTime(raw: String): String {
        if (raw.isBlank()) return ""
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val date = sdf.parse(raw) ?: return ""
            val diff = System.currentTimeMillis() - date.time
            val mins = diff / 60000
            when {
                mins < 1 -> getString(R.string.just_now)
                mins < 60 -> "${mins}m"
                mins < 1440 -> "${mins / 60}h"
                mins < 10080 -> "${mins / 1440}d"
                else -> "${mins / 10080}w"
            }
        } catch (_: Exception) { "" }
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    private fun openPublisherProfile(pack: Pack) {
        // Sticky publisher (default content) — don't open profile page
        if (pack.pub.isBlank() || pack.pub.equals("Sticky", ignoreCase = true)) return
        val intent = Intent(this, PublisherProfileActivity::class.java).apply {
            putExtra(PublisherProfileActivity.EXTRA_PUBLISHER_ID,
                pack.publisherUserId.ifBlank { pack.email })
            putExtra(PublisherProfileActivity.EXTRA_PUBLISHER_NAME, pack.pub)
            putExtra(PublisherProfileActivity.EXTRA_PUBLISHER_PHOTO, pack.publisherPhotoUrl)
            putExtra(PublisherProfileActivity.EXTRA_PUBLISHER_EMAIL, pack.email)
        }
        startActivity(intent)
    }

    private fun showPublisherProfileDialog(pack: Pack) {
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 24, 36, 8)
        }
        val title = TextView(this).apply {
            text = pack.pub
            textSize = 18f
            setTextColor(ContextCompat.getColor(this@DetailsActivity, R.color.text_primary))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        val emailText = TextView(this).apply {
            text = pack.email
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@DetailsActivity, R.color.text_secondary))
            setPadding(0, 4, 0, 12)
        }
        val packsText = TextView(this).apply {
            text = "Loading packs..."
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@DetailsActivity, R.color.text_secondary))
        }
        view.addView(title)
        if (pack.email.isNotBlank()) view.addView(emailText)
        view.addView(packsText)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Publisher profile")
            .setView(view)
            .setPositiveButton(getString(R.string.view_profile)) { _, _ -> openPublisherProfile(pack) }
            .setNegativeButton(R.string.ok, null)
            .show()

        if (pack.email.isNotBlank()) {
            lifecycleScope.launch {
                val packs = withContext(Dispatchers.IO) {
                    try {
                        PocketBaseHelper.listRecords("stickers", filter = "publisher_email='${pack.email.replace("'", "\\'")}' && is_active=true", perPage = 20)
                    } catch (_: Exception) { emptyList() }
                }
                packsText.text = if (packs.isEmpty()) {
                    "No other public packs yet."
                } else {
                    packs.joinToString("\n") { "• ${it.optString("name", it.optString("pack_name", "Pack"))}" }
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.text = getString(R.string.view_profile)
            }
        }
    }

    private fun checkIfAlreadySubmitted(packId: String, callback: (Boolean) -> Unit) {
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val prefs = getSharedPreferences("user_prefs", MODE_PRIVATE)
        val email = user?.email ?: prefs.getString("user_email", "").orEmpty()
        if (user == null && email.isBlank()) { callback(false); return }
        lifecycleScope.launch {
            try {
                val existing = withContext(Dispatchers.IO) {
                    val userKeys = listOf(user?.uid.orEmpty(), email, PreferencesHelper.getDeviceId(this@DetailsActivity))
                        .map { it.trim().lowercase() }
                        .filter { it.isNotBlank() }
                    PocketBaseHelper.listAllRecords(
                        "user_submissions",
                        filter = "source_pack_id='${escapePb(packId)}'",
                        perPage = 200
                    ).filter { record ->
                        val ownerKeys = listOf(record.optString("user_id"), record.optString("user_email"), record.optString("device_id"))
                            .map { it.trim().lowercase() }
                            .filter { it.isNotBlank() }
                        userKeys.any { ownerKeys.contains(it) }
                    }
                }
                callback(existing.isNotEmpty())
            } catch (_: Exception) {
                callback(true)
            }
        }
    }

    private fun showPublishDialog(pack: Pack) {
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(this, getString(R.string.publish_pack_login_required), Toast.LENGTH_SHORT).show()
            return
        }
        if (pack.stickers.size !in 9..30) {
            Toast.makeText(this, getString(R.string.publish_pack_count_range), Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.RoundedBottomSheetDialog)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_publish, null)
        dialog.setContentView(view)

        val etPackName      = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etPackName)
        val etPublisher     = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etPublisherName)
        val etDesc          = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etDescription)
        val spinnerCategory = view.findViewById<android.widget.AutoCompleteTextView>(R.id.spinnerCategory)
        val tilPackName     = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilPackName)
        val tilPublisher    = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilPublisherName)
        val progressSection = view.findViewById<android.view.View>(R.id.progressSection)
        val tvProgress      = view.findViewById<android.widget.TextView>(R.id.tvProgressLabel)
        val progressBar     = view.findViewById<android.widget.ProgressBar>(R.id.publishProgressBar)
        val btnCancel       = view.findViewById<MaterialButton>(R.id.btnCancelPublish)
        val btnSubmit       = view.findViewById<MaterialButton>(R.id.btnSubmitPublish)

        val categories = listOf("General", "Animals", "Memes", "Emotions", "Sports", "Love", "Food", "Nature", "Art", "Pop Culture", "Games", "Music", "Travel", "Holidays", "Other")
        val catAdapter = android.widget.ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categories)
        spinnerCategory.setAdapter(catAdapter)
        spinnerCategory.setText("General", false)

        etPackName.setText(pack.localizedName)
        etPublisher.setText(user.displayName ?: "")

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSubmit.setOnClickListener {
            val packName      = etPackName.text?.toString()?.trim() ?: ""
            val publisherName = etPublisher.text?.toString()?.trim() ?: ""
            val description   = etDesc.text?.toString()?.trim() ?: ""
            val category      = spinnerCategory.text?.toString()?.trim()?.lowercase() ?: "general"

            tilPackName.error  = null
            tilPublisher.error = null

            if (packName.isEmpty()) {
                tilPackName.error = getString(R.string.field_required); return@setOnClickListener
            }
            if (publisherName.isEmpty()) {
                tilPublisher.error = getString(R.string.field_required); return@setOnClickListener
            }

            btnSubmit.isEnabled = false
            btnCancel.isEnabled = false
            dialog.setCancelable(false)
            progressSection.visibility = android.view.View.VISIBLE

            uploadAndSubmitPack(pack, packName, publisherName, description, category, progressBar, tvProgress) {
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun uploadAndSubmitPack(
        pack: Pack,
        packName: String,
        publisherName: String,
        description: String,
        category: String,
        progressBar: android.widget.ProgressBar,
        tvProgress: android.widget.TextView,
        onDone: () -> Unit
    ) {
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser ?: return
        val stickers = pack.stickers

        lifecycleScope.launch {
            try {
                val stickerEntries = mutableListOf<JSONObject>()
                val uploadFiles = mutableListOf<PocketBaseHelper.UploadFile>()
                val total = stickers.size

                withContext(Dispatchers.IO) {
                    stickers.forEachIndexed { index, sticker ->
                        withContext(Dispatchers.Main) {
                            progressBar.progress = ((index) * 100 / total)
                            tvProgress.text = getString(R.string.uploading_stickers) + " ${index + 1}/$total"
                        }
                        val localFile = CustomStickerManager.getCustomStickerPath(this@DetailsActivity, pack.id, sticker.file)
                        if (localFile.exists()) {
                            val fileName = "sticker_${index + 1}.webp"
                            uploadFiles.add(PocketBaseHelper.UploadFile("images", fileName, "image/webp", localFile.readBytes()))
                            stickerEntries.add(JSONObject().apply {
                                put("name", sticker.file)
                                put("image_file", fileName)
                                put("pending_upload_index", uploadFiles.lastIndex)
                            })
                        } else if (sticker.url.isNotEmpty()) {
                            stickerEntries.add(JSONObject().apply {
                                put("name", sticker.file)
                                put("image_file", sticker.file)
                                put("image_url", sticker.url)
                                put("url", sticker.url)
                            })
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    progressBar.progress = 100
                    tvProgress.text = getString(R.string.saving_submission)
                }

                if (stickerEntries.isEmpty()) {
                    Toast.makeText(this@DetailsActivity, getString(R.string.publish_pack_failed), Toast.LENGTH_SHORT).show()
                    onDone()
                    return@launch
                }

                val sourceSignature = stickerEntries.joinToString("|") { entry ->
                    val localFile = CustomStickerManager.getCustomStickerPath(this@DetailsActivity, pack.id, entry.optString("name"))
                    if (localFile.exists()) "${entry.optString("name")}:${localFile.length()}:${localFile.lastModified()}" else "${entry.optString("name")}:${entry.optString("url")}" 
                }
                withContext(Dispatchers.IO) {
                    ensurePackCanBeSubmittedAgain(
                        pack.id,
                        user.uid,
                        user.email ?: "",
                        PreferencesHelper.getDeviceId(this@DetailsActivity),
                        sourceSignature,
                        stickerEntries.size
                    )
                }

                val fields = mapOf(
                    "device_id" to PreferencesHelper.getDeviceId(this@DetailsActivity),
                    "user_id" to user.uid,
                    "user_email" to (user.email ?: ""),
                    "display_name" to publisherName,
                    "publisher_name" to publisherName,
                    "pack_name" to packName,
                    "name" to packName,
                    "description" to description,
                    "category" to category.ifEmpty { "general" },
                    "stickers" to "[]",
                    "sticker_count" to stickerEntries.size.toString(),
                    "source_pack_id" to pack.id,
                    "is_animated" to pack.isAnimated.toString(),
                    "note" to "source_signature=$sourceSignature",
                    "status" to "pending",
                    "created_at" to java.time.Instant.now().toString()
                )

                withContext(Dispatchers.IO) {
                    val created = PocketBaseHelper.createMultipartRecord("user_submissions", fields, uploadFiles)
                    val recordId = created.getString("id")
                    val uploadedImages = created.optJSONArray("images") ?: JSONArray()
                    val stickersJson = JSONArray()
                    stickerEntries.forEach { entry ->
                        if (entry.has("pending_upload_index")) {
                            val uploadIndex = entry.optInt("pending_upload_index", -1)
                            val uploadedFile = if (uploadIndex >= 0 && uploadIndex < uploadedImages.length()) uploadedImages.optString(uploadIndex) else entry.optString("image_file")
                            val url = PocketBaseHelper.getFileUrl("user_submissions", recordId, uploadedFile)
                            entry.remove("pending_upload_index")
                            entry.put("image_file", uploadedFile)
                            entry.put("image_url", url)
                            entry.put("url", url)
                        }
                        stickersJson.put(entry)
                    }
                    runCatching {
                        PocketBaseHelper.updateRecord("user_submissions", recordId, JSONObject().apply {
                            put("stickers", stickersJson)
                            put("sticker_count", stickersJson.length())
                        })
                    }.onFailure {
                        android.util.Log.w("DetailsActivity", "Submission created, sticker metadata update skipped: ${it.message}")
                    }
                }

                Toast.makeText(this@DetailsActivity, getString(R.string.publish_pack_success), Toast.LENGTH_LONG).show()
                onDone()

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@DetailsActivity, e.message ?: getString(R.string.publish_pack_failed), Toast.LENGTH_SHORT).show()
                onDone()
            }
        }
    }

    private fun handleButtonClick(pack: Pack) {
        lifecycleScope.launch {
            val isInstalled = withContext(Dispatchers.IO) {
                WhitelistCheck.isWhitelisted(this@DetailsActivity, pack.id)
            }
            
            if (isInstalled) {
                // Paket zaten yüklü, Paylaş butonuna dönüştü
                sharePack(pack)
            } else {
                // Ekleme işlemi: Reklamlı devam et
                addToWhatsApp(pack)
            }
        }
    }

    private fun sharePack(pack: Pack) {
        val shareText = getString(R.string.share_pack_text, pack.localizedName)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, pack.localizedName)
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_pack)))
    }

    private fun showRemoveInstructionsDialog() {
        val message = getString(R.string.pack_still_installed_message)
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.remove_instructions_title)
            .setMessage(message)
            .setPositiveButton(R.string.go_to_whatsapp) { _, _ ->
                try {
                    val intent = packageManager.getLaunchIntentForPackage("com.whatsapp")
                        ?: packageManager.getLaunchIntentForPackage("com.whatsapp.w4b")
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, R.string.whatsapp_not_installed, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.ok, null)
            .show()
    }

    private fun updateButton() {
        // Önce WhatsApp'tan gerçek durumu kontrol et, sonra UI'ı güncelle
        lifecycleScope.launch {
            val isWhitelisted = withContext(Dispatchers.IO) {
                WhitelistCheck.isWhitelisted(this@DetailsActivity, packId)
            }

            val localInstalled = PreferencesHelper.isPackInstalled(this@DetailsActivity, packId)

            // Senkronizasyon - WhatsApp durumuna göre yerel durumu güncelle
            if (!isWhitelisted && localInstalled) {
                PreferencesHelper.removeInstalledPack(this@DetailsActivity, packId)
            } else if (isWhitelisted && !localInstalled) {
                PreferencesHelper.addInstalledPack(this@DetailsActivity, packId)
            }

            // UI'ı WhatsApp durumuna göre güncelle
            updateButtonUI(isWhitelisted)
        }
    }

    private fun updateButtonUI(isInstalled: Boolean) {
        if (!::btnAction.isInitialized) return
        btnAction.isEnabled = true
        val buttonHeight = (48 * resources.displayMetrics.density).toInt()
        val cornerRadius = (12 * resources.displayMetrics.density).toInt()

        val pack = currentPack
        val isCustom = pack != null && (pack.category == "custom" || pack.id.startsWith("custom_"))

        if (isInstalled) {
            // Eğer WhatsApp'ta zaten yüklü ise
            if (isCustom) {
                btnAction.visibility = View.GONE
                btnFixedWhatsApp?.visibility = View.VISIBLE
                btnFixedWhatsApp?.text = getString(R.string.update_on_whatsapp)
                btnFixedWhatsApp?.setIconResource(R.drawable.ic_whatsapp_small)
                btnFixedWhatsApp?.alpha = 1f
            } else {
                btnAction.visibility = View.VISIBLE
            }
            premiumButtonsContainer.visibility = View.GONE
            customButtonsContainer.visibility = if (isCustom) View.VISIBLE else View.GONE
            
            btnAction.text = getString(R.string.share_pack)
            btnAction.setIconResource(R.drawable.ic_share)
            btnAction.iconTint = ContextCompat.getColorStateList(this, R.color.white)
            btnAction.setTextColor(ContextCompat.getColor(this, R.color.white))
            btnAction.backgroundTintList = ContextCompat.getColorStateList(this, R.color.primary)
            btnAction.cornerRadius = cornerRadius
            if (!isCustom) installedIcon.visibility = View.VISIBLE
        } else {
            val hasAccess = pack != null && (
                !pack.isPremium || PreferencesHelper.hasAccessToPack(this, pack.id)
            )

            if (!hasAccess) {
                // Kilitli durum: btnAction gizli kalsın, premiumButtonsContainer görünür (sadece premium paketler)
                btnAction.visibility = View.GONE
                premiumButtonsContainer.visibility = View.VISIBLE
                customButtonsContainer.visibility = if (isCustom) View.VISIBLE else View.GONE
                if (isCustom) btnFixedWhatsApp?.visibility = View.GONE
            } else if (pack != null && pack.isPremium && !PreferencesHelper.isPremium(this)) {
                // Premium paket, reklam ile açılmış ama kullanıcı premium üye değil — iki buton göster
                btnAction.visibility = View.GONE
                premiumButtonsContainer.visibility = View.VISIBLE
                customButtonsContainer.visibility = if (isCustom) View.VISIBLE else View.GONE

                // Sol buton: WhatsApp'a ekle
                val btnLeft = findViewById<MaterialButton>(R.id.btnWatchAd)
                btnLeft.text = getString(R.string.add_short)
                btnLeft.setIconResource(R.drawable.ic_whatsapp_small)
                btnLeft.iconTint = ContextCompat.getColorStateList(this, R.color.white)
                btnLeft.setTextColor(ContextCompat.getColor(this, R.color.white))
                btnLeft.backgroundTintList = ContextCompat.getColorStateList(this, R.color.primary)
                btnLeft.setOnClickListener { handleButtonClick(pack) }
            } else {
                // Normal paket, henüz WhatsApp'a eklenmemiş durum
                premiumButtonsContainer.visibility = View.GONE
                
                if (isCustom) {
                    btnAction.visibility = View.GONE
                    btnFixedWhatsApp?.visibility = View.VISIBLE
                    btnFixedWhatsApp?.text = getString(R.string.add_to_whatsapp)
                    btnFixedWhatsApp?.setIconResource(R.drawable.ic_whatsapp_small)
                    btnFixedWhatsApp?.alpha = 1f
                } else {
                    btnAction.visibility = View.VISIBLE
                }
                customButtonsContainer.visibility = if (isCustom) View.VISIBLE else View.GONE
                
                btnAction.text = getString(R.string.add_to_whatsapp)
                btnAction.setIconResource(R.drawable.ic_whatsapp_small)
                btnAction.iconTint = ContextCompat.getColorStateList(this, R.color.white)
                btnAction.setTextColor(ContextCompat.getColor(this, R.color.white))
                btnAction.backgroundTintList = ContextCompat.getColorStateList(this, R.color.primary)
            }

            btnAction.cornerRadius = cornerRadius
            installedIcon.visibility = View.GONE
        }

        // Boyutları ayarla
        btnAction.iconPadding = (8 * resources.displayMetrics.density).toInt()
        btnAction.iconGravity = com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START
        val params = btnAction.layoutParams
        params.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT
        params.height = buttonHeight
        btnAction.layoutParams = params
    }

    private fun showStickerPreview(sticker: Sticker, isLocked: Boolean = false) {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val previewBgColor = ContextCompat.getColor(this@DetailsActivity, R.color.preview_bg)
        val toolbarColor = ContextCompat.getColor(this@DetailsActivity, R.color.primary)
        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(previewBgColor))
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            statusBarColor = toolbarColor
            navigationBarColor = Color.BLACK
        }

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_sticker_preview, null)
        val imageView = view.findViewById<ImageView>(R.id.previewImage)
        val lockOverlay = view.findViewById<ImageView>(R.id.lockOverlay)
        val unlockHint = view.findViewById<android.widget.TextView>(R.id.unlockHint)
        var fallbackWebView: WebView? = null

        dialog.setContentView(view)

        lockOverlay?.visibility = View.GONE
        unlockHint?.visibility = View.GONE

        val animatedPreview = isAnimatedPack ||
            sticker.file.contains(".webp", ignoreCase = true) ||
            sticker.file.contains(".gif", ignoreCase = true) ||
            sticker.url.contains(".webp", ignoreCase = true) ||
            sticker.url.contains(".gif", ignoreCase = true)

        // Animated previews must use the original source first. Old local cache files
        // can be static/optimized first frames from previous versions.
        val cachedFile = StickerRepository.getCachedStickerPath(this, packId, sticker.file)
        val directUrl = currentPack?.storagePath
            ?.takeIf { it.isNotBlank() }
            ?.let { StickerRepository.getStickerDirectUrl(packId, sticker.file, it) }
        val loadSource: Any? = if (packId.startsWith("custom_")) {
            val customFile = CustomStickerManager.getCustomStickerPath(this, packId, sticker.file)
            if (customFile.exists()) customFile else null
        } else if (animatedPreview) {
            when {
                sticker.url.isNotEmpty() -> sticker.url
                !directUrl.isNullOrBlank() -> directUrl
                cachedFile.exists() && cachedFile.length() > 0 -> cachedFile
                else -> android.net.Uri.parse("file:///android_asset/$packId/${sticker.file}")
            }
        } else {
            when {
                cachedFile.exists() && cachedFile.length() > 0 -> cachedFile
                sticker.url.isNotEmpty() -> sticker.url
                !directUrl.isNullOrBlank() -> directUrl
                else -> android.net.Uri.parse("file:///android_asset/$packId/${sticker.file}")
            }
        }
        Log.d(
            "DetailsActivity",
            "Preview click pack=$packId file=${sticker.file} isAnimatedPack=$isAnimatedPack animatedPreview=$animatedPreview source=${loadSource?.javaClass?.simpleName} url=${sticker.url.take(80)}"
        )

        // Önizlemeyi kapatan ortak fonksiyon — hem boş alana hem de animasyonlu
        // sticker'ın üstüne dokununca çalışır.
        val dismissWithAnimation = {
            val closeAnimation = view.animate()
                .alpha(0f)
                .setDuration(if (animatedPreview) 120 else 200)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction { dialog.dismiss() }
            if (!animatedPreview) {
                closeAnimation.scaleX(0.7f).scaleY(0.7f)
            }
            closeAnimation.start()
        }

        val startPreviewLoad = {
            if (loadSource == null) {
                imageView.setImageResource(R.drawable.transparent_placeholder)
            } else if (animatedPreview) {
                imageView.setImageDrawable(null)
                showAnimatedPreviewFallback(view as ViewGroup, imageView, loadSource) {
                    dismissWithAnimation()
                }?.let {
                    fallbackWebView = it
                }
            } else {
                Glide.with(this@DetailsActivity)
                    .load(loadSource)
                    .placeholder(R.drawable.transparent_placeholder)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.RESOURCE)
                    .dontTransform()
                    .error(R.drawable.transparent_placeholder)
                    .override(512, 512)
                    .into(imageView)
            }
        }

        dialog.setOnDismissListener {
            (imageView.drawable as? Animatable)?.stop()
            fallbackWebView?.stopLoading()
            fallbackWebView?.loadUrl("about:blank")
            fallbackWebView?.destroy()
            fallbackWebView = null
            Glide.with(this@DetailsActivity).clear(imageView)
            imageView.setImageDrawable(null)
        }
        dialog.setOnShowListener {
            imageView.post { startPreviewLoad() }
        }

        // Animated sticker oynarken parent scale animasyonu decode/render işlemini takabiliyor.
        view.alpha = 0f
        if (animatedPreview) {
            view.scaleX = 1f
            view.scaleY = 1f
            view.animate()
                .alpha(1f)
                .setDuration(120)
                .start()
        } else {
            view.scaleX = 0.7f
            view.scaleY = 0.7f
            view.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(250)
                .setInterpolator(android.view.animation.OvershootInterpolator(1.1f))
                .start()
        }

        // Ekrana tıklandığında da kapat (Referans projeyle aynı)
        view.setOnClickListener { dismissWithAnimation() }

        dialog.show()
    }

    private fun showAnimatedPreviewFallback(
        container: ViewGroup,
        imageView: ImageView,
        loadSource: Any,
        onTap: () -> Unit
    ): WebView? {
        val src = when (loadSource) {
            is String -> loadSource
            is File -> android.net.Uri.fromFile(loadSource).toString()
            is android.net.Uri -> loadSource.toString()
            else -> return null
        }
        val safeSrc = src
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        imageView.visibility = View.GONE
        val existing = container.findViewWithTag<WebView>("animated_preview_webview")
        val webView = existing ?: WebView(this).apply {
            tag = "animated_preview_webview"
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = false
            settings.domStorageEnabled = false
            settings.loadsImagesAutomatically = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            isLongClickable = false
            container.addView(
                this,
                android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }

        webView.visibility = View.VISIBLE
        webView.isLongClickable = false
        webView.setOnClickListener { onTap() }

        // WebView'in kendi tıklama olayı güvenilir tetiklenmiyor; üstüne saydam bir
        // katman koyup ekranın herhangi bir yerine dokununca önizleme kapansın.
        val tapCatcher = container.findViewWithTag<View>("animated_preview_tap")
            ?: View(this).apply {
                tag = "animated_preview_tap"
                isClickable = true
                container.addView(
                    this,
                    android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
            }
        tapCatcher.bringToFront()
        tapCatcher.setOnClickListener { onTap() }

        webView.loadDataWithBaseURL(
            null,
            """
            <html>
              <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                  html,body{margin:0;width:100%;height:100%;background:transparent;overflow:hidden;}
                  body{display:flex;align-items:center;justify-content:center;}
                  img{max-width:82vw;max-height:82vh;object-fit:contain;}
                </style>
              </head>
              <body><img src="$safeSrc"></body>
            </html>
            """.trimIndent(),
            "text/html",
            "UTF-8",
            null
        )
        Log.d("DetailsActivity", "Animated preview fallback WebView fileSource=${loadSource::class.java.simpleName}")
        return webView
    }

    private fun startAnimatedPreview(imageView: ImageView) {
        val start = Runnable {
            val drawable = imageView.drawable
            drawable?.setVisible(true, true)
            (drawable as? Animatable)?.start()
        }
        imageView.post(start)
        imageView.postDelayed(start, 120)
        imageView.postDelayed(start, 350)
    }

    private fun isWhatsAppInstalled(): Boolean {
        val packageManager = packageManager
        return try {
            packageManager.getPackageInfo("com.whatsapp", 0)
            true
        } catch (e: Exception) {
            try {
                packageManager.getPackageInfo("com.whatsapp.w4b", 0)
                true
            } catch (e2: Exception) {
                false
            }
        }
    }

    private fun addToWhatsApp(pack: Pack) {
        // Çıkartma sayısı kontrolü
        if (pack.stickers.isEmpty()) {
            Toast.makeText(this, R.string.pack_empty_error, Toast.LENGTH_LONG).show()
            return
        }

        // WhatsApp kontrolü
        if (!isWhatsAppInstalled()) {
            Toast.makeText(this, R.string.whatsapp_not_installed, Toast.LENGTH_SHORT).show()
            showWhatsAppNotAvailableDialog()
            return
        }

        val hasAccess = !pack.isPremium || PreferencesHelper.hasAccessToPack(this, pack.id)

        if (!hasAccess) {
            // KİLİTLİ → Rewarded Video göster
            showRewardedAdForPack(pack)
        } else {
            // AÇILMIŞ → Doğrudan ekle
            proceedToWhatsApp(pack)
        }
    }

    /**
     * Premium pack için rewarded video göster, izlenince 24 saat aç
     */
    private fun showRewardedAdForPack(pack: Pack) {
        if (!AdManager.isRewardedReady()) {
            // Reklam henüz yüklenmemiş, yüklemeyi başlat ve kullanıcıya bildir
            Toast.makeText(this, R.string.ad_loading_please_wait, Toast.LENGTH_SHORT).show()
            AdManager.loadRewardedAd(this)
            
            // 3 saniye bekle, hazır olursa göster
            showLoadingState(true)
            tvOverlayLoadingText?.text = getString(R.string.ad_preparing)
            lifecycleScope.launch {
                var waited = 0
                while (!AdManager.isRewardedReady() && waited < 12) {
                    delay(250)
                    waited++
                }
                showLoadingState(false)
                
                if (AdManager.isRewardedReady()) {
                    showRewardedAdForPack(pack) // Tekrar çağır, bu sefer hazır
                } else {
                    // Reklam yüklenmedi, yine de açalım (kullanıcıyı cezalandırmayalım)
                    Toast.makeText(this@DetailsActivity, R.string.ad_not_available, Toast.LENGTH_SHORT).show()
                    PreferencesHelper.unlockPack(this@DetailsActivity, pack.id)
                updateButton()
                updateButtonUI(false) // Installed değil henüz
                
                // Hemen WhatsApp sürecini başlat
                proceedToWhatsApp(pack)
                }
            }
            return
        }

        AdManager.showRewardedAd(this,
            onRewarded = {
                // Ödül kazanıldı → Pack'i aç
                rewardedJustCompleted = true
                PreferencesHelper.unlockPack(this, pack.id)
                updateButton()
                // Sticker'ları WhatsApp'a ekle
                proceedToWhatsApp(pack)
            },
            onFailed = {
                Toast.makeText(this, R.string.ad_failed_try_again, Toast.LENGTH_SHORT).show()
            }
        )
    }

    /**
     * WhatsApp'a sticker ekleme işlemi (reklamsız, doğrudan)
     */
    private fun proceedToWhatsApp(pack: Pack) {
        showLoadingState(true)
        if (circularProgress != null) {
            circularProgress?.setIndicatorColor(ContextCompat.getColor(this, R.color.primary))
            circularProgress?.progress = 0
        }
        tvOverlayLoadingText?.text = "0%"
        tvDynamicStatus?.text = getString(R.string.stickers_preparing)

        lifecycleScope.launch {
            val totalFiles = pack.stickers.size + 1
            val downloadedCount = AtomicInteger(0)

            // Progress tracking coroutine for smooth animation
            val progressJob = launch(Dispatchers.Main) {
                var displayedProgress = 0
                while (displayedProgress < 100) {
                    val actualProgress = (downloadedCount.get() * 100) / totalFiles
                    if (displayedProgress < actualProgress) {
                        // Smoothly increment displayed progress
                        displayedProgress += (actualProgress - displayedProgress).coerceAtLeast(1).coerceAtMost(5)
                        tvOverlayLoadingText?.text = "$displayedProgress%"
                        if (circularProgress != null) {
                            circularProgress?.progress = displayedProgress
                        }
                    }
                    if (actualProgress == 100 && displayedProgress >= 100) break
                    delay(16) // roughly 60 FPS
                }
            }

            val prepareJob = async(Dispatchers.IO) {
                // Metadata'yı diske kaydet
                val currentPacks = StickerRepository.allPacksCache
                val packsToSave = if (currentPacks.any { it.id == pack.id }) {
                    currentPacks
                } else {
                    currentPacks + pack
                }
                StickerRepository.saveCacheToDisk(this@DetailsActivity, packsToSave)

                // Sticker'ları indir
                if (!StickerRepository.isPackCached(this@DetailsActivity, pack)) {
                    Log.d("DetailsActivity", "Caching stickers dynamic...")
                    val storagePath = pack.storagePath
                    try {
                        StickerRepository.downloadStickerToCache(this@DetailsActivity, pack.id, pack.tray, storagePath, pack.trayUrl)
                        downloadedCount.incrementAndGet()
                    } catch (_: Exception) {}

                    pack.stickers.chunked(10).forEach { chunk ->
                        chunk.map { sticker ->
                            async {
                                try {
                                    StickerRepository.downloadStickerToCache(this@DetailsActivity, pack.id, sticker.file, storagePath, sticker.url, allowCompression = !pack.isAnimated)
                                    downloadedCount.incrementAndGet()
                                } catch (_: Exception) {}
                            }
                        }.awaitAll()
                    }
                } else {
                    // Already cached, set progress to 100
                    downloadedCount.set(totalFiles)
                }
            }

            try {
                prepareJob.await()
                progressJob.join() // Wait for smooth progress to finish
                
                val sizeCheckResult = checkStickerFileSizes(pack)
                if (sizeCheckResult != null) {
                    showLoadingState(false)
                    Toast.makeText(this@DetailsActivity, sizeCheckResult, Toast.LENGTH_LONG).show()
                } else {
                    // Short delay for the user to see 100%
                    delay(300)
                    showLoadingState(false)
                    sendToWhatsApp(pack)
                }
            } catch (e: Exception) {
                showLoadingState(false)
                Toast.makeText(this@DetailsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }



    /**
     * WhatsApp intent'ini HEMEN başlat (metadata zaten hazırlanmış durumda)
     */
    private fun launchWhatsAppIntentImmediately(pack: Pack) {
        val intent = Intent().apply {
            action = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
            putExtra("sticker_pack_id", pack.id)
            putExtra("sticker_pack_authority", "${packageName}.stickers")
            putExtra("sticker_pack_name", pack.localizedName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            Log.d("DetailsActivity", "Launching WhatsApp immediately after ad closed")
            addPackLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e("DetailsActivity", "Intent error: ${e.message}", e)
            Toast.makeText(this, R.string.whatsapp_not_available_title, Toast.LENGTH_SHORT).show()
            showWhatsAppNotAvailableDialog()
        }
    }

    private fun proceedToAddToWhatsApp(pack: Pack) {

        if (pack.id.startsWith("custom_") && !CustomStickerManager.hasCover(this, pack.id)) {
            Toast.makeText(this, R.string.pack_cover_error, Toast.LENGTH_LONG).show()
            return
        }

        // PERFORMANS: Önce cache'de olup olmadığını kontrol et
        if (StickerRepository.isPackCached(this, pack)) {
            // Boyut sorunu varsa cache'i temizle ve yeniden indir
            val sizeCheckResult = checkStickerFileSizes(pack)
            if (sizeCheckResult != null) {
                android.util.Log.d("DetailsActivity", "Cache too large, clearing and re-downloading")
                StickerRepository.clearPackCache(this, pack.id)
                downloadAndAddToWhatsAppWithProgress(pack)
                return
            }
            // Cache'de var, direkt WhatsApp'a gönder
            android.util.Log.d("DetailsActivity", "Pack already cached, sending directly")
            sendToWhatsApp(pack)
        } else if (pack.trayUrl.isNotEmpty() || pack.stickers.any { it.url.isNotEmpty() }) {
            // İndirme gerekli - progress ile indir
            downloadAndAddToWhatsAppWithProgress(pack)
        } else {
            sendToWhatsApp(pack)
        }
    }

    /**
     * Sticker dosya boyutlarını kontrol et - WhatsApp limitlerine uygun mu?
     * @return Hata mesajı veya null (sorun yoksa)
     */
    private fun checkStickerFileSizes(pack: Pack): String? {
        val maxStaticSize = 100 * 1024L // 100KB
        val maxAnimatedSize = 500 * 1024L // 500KB

        val cacheDir = java.io.File(cacheDir, "sticker_cache/${pack.id}")
        if (!cacheDir.exists()) return null // Cache yoksa kontrol etme

        var oversizedCount = 0
        var largestSize = 0L

        // Sticker dosyalarını kontrol et
        for (sticker in pack.stickers) {
            val file = java.io.File(cacheDir, sticker.file)
            if (!file.exists()) continue
            val fileSize = file.length()
            // Dosya animated mı? pack.isAnimated flag'i yanlış olabilir (taşıma sırasında);
            // BitmapFactory null döndürüyorsa dosya animated WebP'dir → 500KB limiti uygula
            val isAnimatedFile = pack.isAnimated ||
                (fileSize > maxStaticSize && android.graphics.BitmapFactory.decodeFile(file.absolutePath) == null)
            val limit = if (isAnimatedFile) maxAnimatedSize else maxStaticSize
            if (fileSize > limit) {
                oversizedCount++
                if (fileSize > largestSize) {
                    largestSize = fileSize
                }
            }
        }

        if (oversizedCount > 0) {
            val limitKB = if (pack.isAnimated) maxAnimatedSize / 1024 else maxStaticSize / 1024
            val largestKB = largestSize / 1024
            return getString(R.string.sticker_size_error, oversizedCount, limitKB, largestKB)
        }

        return null
    }

    private fun removeFromWhatsApp() {
        currentPack?.let { pack ->
            // Yerel durumu temizle
            PreferencesHelper.removeInstalledPack(this, pack.id)
            updateButton()
            
            // Kullanıcıyı direkt inten'le gönderince (ENABLE_STICKER_PACK) WhatsApp "böyle bir paket yok" 
            // diyerek hata verebiliyor (özellikle manuel silinmişse). 
            // Bu yüzden direkt yönlendirme yerine rehberlik diyaloğunu gösteriyoruz.
            showRemoveInstructionsDialog()
        }
    }

    /**
     * WhatsApp'a kaldırma intent'i gönder
     */
    private fun launchWhatsAppRemove(pack: Pack) {
        val intent = Intent().apply {
            action = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
            putExtra("sticker_pack_id", pack.id)
            putExtra("sticker_pack_authority", "${packageName}.stickers")
            putExtra("sticker_pack_name", pack.localizedName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            removePackLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            // WhatsApp Business dene
            try {
                intent.setPackage("com.whatsapp.w4b")
                removePackLauncher.launch(intent)
            } catch (e2: ActivityNotFoundException) {
                showWhatsAppNotAvailableDialog()
            }
        } catch (e: SecurityException) {
            // WhatsApp'a erişim engellendi
            showWhatsAppNotAvailableDialog()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.whatsapp_not_available_title, Toast.LENGTH_SHORT).show()
            showWhatsAppNotAvailableDialog()
        }
    }

    /**
     * WhatsApp kullanılamıyor dialog'u göster
     */
    private fun showWhatsAppNotAvailableDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.whatsapp_not_available_title)
            .setMessage(R.string.whatsapp_not_available_message)
            .setPositiveButton(R.string.play_store) { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=com.whatsapp")))
                } catch (e: Exception) {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=com.whatsapp")))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * Paket hâlâ yüklü uyarısı göster
     */
    private fun showPackStillInstalledWarning() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.pack_still_installed_title)
            .setMessage(R.string.pack_still_installed_message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun downloadAndAddToWhatsApp(pack: Pack) {
        downloadAndAddToWhatsAppWithProgress(pack)
    }

    /**
     * Paket açıldığında arka planda indirmeyi başlat
     */
    private fun startBackgroundDownload(pack: Pack) {
        if (isDownloading) return
        isDownloading = true
        
        // Butonu yükleniyor moduna al (üstüne overlay gelecek)
        btnAction.isEnabled = false
        showLoadingState(true)
        tvDynamicStatus?.text = getString(R.string.downloading_assets)
        if (circularProgress != null) {
            circularProgress?.setIndicatorColor(ContextCompat.getColor(this, R.color.primary))
        }

        val totalFiles = pack.stickers.size + 1
        val downloadedCount = AtomicInteger(0)

        lifecycleScope.launch {
            try {
                android.util.Log.d("DetailsActivity", "Starting background download: ${pack.id}")
                val storagePath = pack.storagePath

                // Paralel indirme - Yüksek paralellik ile hızlı indirme
                withContext(Dispatchers.IO) {
                    kotlinx.coroutines.coroutineScope {
                        // Tray'i indir
                        val trayJob = async {
                            StickerRepository.downloadStickerToCache(this@DetailsActivity, pack.id, pack.tray, storagePath, pack.trayUrl)
                            val count = downloadedCount.incrementAndGet()
                            updateProgressText(count, totalFiles)
                        }

                        // Sticker'ları paralel indir (16 adet aynı anda - hızlı indirme)
                        pack.stickers.chunked(16).forEach { chunk ->
                            chunk.map { sticker ->
                                async {
                                    StickerRepository.downloadStickerToCache(this@DetailsActivity, pack.id, sticker.file, storagePath, sticker.url, allowCompression = !pack.isAnimated)
                                    val count = downloadedCount.incrementAndGet()
                                    updateProgressText(count, totalFiles)
                                }
                            }.awaitAll()
                        }
                        trayJob.await()
                    }
                }

                // %100 göster ve kısa bir süre bekle
                runOnUiThread {
                    tvOverlayLoadingText?.text = "100%"
                    tvDynamicStatus?.text = getString(R.string.pack_ready)
                    circularProgress?.let { cp ->
                        ObjectAnimator.ofInt(cp, "progress", cp.progress, 100).apply {
                            duration = 150
                            start()
                        }
                    }
                }
                delay(400)

                android.util.Log.d("DetailsActivity", "Background download complete: ${pack.id}")
                isPackReady = true
                isDownloading = false

                // Butonu normal hale getir
                runOnUiThread {
                    updateButton()
                    btnAction.isEnabled = true
                    showLoadingState(false)
                }

            } catch (e: Exception) {
                android.util.Log.e("DetailsActivity", "Background download error: ${e.message}", e)
                isDownloading = false
                runOnUiThread {
                    Toast.makeText(this@DetailsActivity, R.string.stickers_load_failed, Toast.LENGTH_SHORT).show()
                    updateButton()
                    btnAction.isEnabled = true
                    showLoadingState(false)
                }
            }
        }
    }

    private fun updateProgressText(count: Int, total: Int) {
        val percent = (count * 100) / total
        runOnUiThread {
            tvOverlayLoadingText?.text = "$percent%"
            if (tvDynamicStatus?.text.isNullOrEmpty()) {
                tvDynamicStatus?.text = getString(R.string.downloading_assets)
            }
            circularProgress?.let { cp ->
                ObjectAnimator.ofInt(cp, "progress", cp.progress, percent).apply {
                    duration = 200
                    start()
                }
            }
        }
    }

    /**
     * Çıkartmaları indir ve butonda ilerleme göster (butona tıklanınca)
     */
    private fun downloadAndAddToWhatsAppWithProgress(pack: Pack) {
        // Eğer zaten indirme devam ediyorsa, bekle
        if (isDownloading) {
            Toast.makeText(this, R.string.stickers_preparing, Toast.LENGTH_SHORT).show()
            return
        }

        // Eğer zaten hazırsa direkt gönder
        if (isPackReady || StickerRepository.isPackCached(this, pack)) {
            // Dosya boyutu kontrolü
            val sizeCheckResult = checkStickerFileSizes(pack)
            if (sizeCheckResult != null) {
                Toast.makeText(this, sizeCheckResult, Toast.LENGTH_LONG).show()
                return
            }
            sendToWhatsApp(pack)
            return
        }

        // Hazır değilse ve indirme de başlamamışsa, indirmeyi başlat ve tamamlanınca gönder
        btnAction.isEnabled = false
        currentProgress = 0
        tvOverlayLoadingText?.text = "0%"
        tvDynamicStatus?.text = getString(R.string.downloading_assets)
        showLoadingState(true)
        if (circularProgress != null) {
            circularProgress?.setIndicatorColor(ContextCompat.getColor(this, R.color.primary))
        }

        val totalFiles = pack.stickers.size + 1
        val downloadedCount = AtomicInteger(0)

        lifecycleScope.launch {
            try {
                // UI update coroutine for smooth 60fps percentage
                val progressJob = launch(Dispatchers.Main) {
                    var displayedProgress = 0
                    while (displayedProgress < 100) {
                        val actualProgress = (downloadedCount.get() * 100) / totalFiles
                        if (displayedProgress < actualProgress) {
                            // Step smoothly up to actual progress
                            val step = (actualProgress - displayedProgress).coerceAtLeast(1).coerceAtMost(3)
                            displayedProgress += step
                            tvOverlayLoadingText?.text = "$displayedProgress%"
                            if (circularProgress != null) {
                                circularProgress?.progress = displayedProgress
                            }
                        }
                        if (displayedProgress == 100 || (actualProgress == 100 && displayedProgress >= 99)) {
                            tvOverlayLoadingText?.text = "100%"
                            circularProgress?.progress = 100
                            break
                        }
                        delay(16) // roughly 60 FPS
                    }
                }

                val storagePath = pack.storagePath

                withContext(Dispatchers.IO) {
                    kotlinx.coroutines.coroutineScope {
                        val trayJob = async {
                            StickerRepository.downloadStickerToCache(this@DetailsActivity, pack.id, pack.tray, storagePath, pack.trayUrl)
                            downloadedCount.incrementAndGet()
                        }

                        // Use larger chunks or all at once for speed
                        pack.stickers.chunked(30).forEach { chunk ->
                            chunk.map { sticker ->
                                async {
                                    StickerRepository.downloadStickerToCache(this@DetailsActivity, pack.id, sticker.file, storagePath, sticker.url, allowCompression = !pack.isAnimated)
                                    downloadedCount.incrementAndGet()
                                }
                            }.awaitAll()
                        }

                        trayJob.await()
                    }
                }

                // Ensure it counts to 100 if loop missed
                downloadedCount.set(totalFiles)
                progressJob.join() // Wait for smooth counter to finish
                
                isPackReady = true

                // Dosya boyutu kontrolü
                val sizeCheckResult = checkStickerFileSizes(pack)
                if (sizeCheckResult != null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@DetailsActivity, sizeCheckResult, Toast.LENGTH_LONG).show()
                        progressAnimator?.cancel()
                        updateButton()
                        btnAction.isEnabled = true
                        showLoadingState(false)
                    }
                    return@launch
                }

                sendToWhatsApp(pack)

            } catch (e: Exception) {
                android.util.Log.e("DetailsActivity", "Download error: ${e.message}", e)
                Toast.makeText(this@DetailsActivity, R.string.stickers_load_failed, Toast.LENGTH_SHORT).show()
                progressAnimator?.cancel()
                updateButton()
                btnAction.isEnabled = true
                showLoadingState(false)
            }
        }
    }

    private fun sendToWhatsApp(pack: Pack) {
        // Loading overlay'i kapat - WhatsApp hemen açılacak
        showLoadingState(false)
        Log.d("DetailsActivity", "sendToWhatsApp called for pack=${pack.id}, isPremium=${pack.isPremium}")

        // Premium kullanıcılar hiç reklam görmez.
        // Rewarded izlendikten hemen sonra interstitial gösterme (çift tam ekran kötü UX).
        // Diğer tüm durumlarda (ücretsiz ve premium paket) interstitial göster.
        if (!PreferencesHelper.isPremium(this)) {
            val skipForReward = rewardedJustCompleted
            rewardedJustCompleted = false
            if (skipForReward) {
                launchWhatsAppIntent(pack)
                return
            }
            // Prepare WhatsApp intent in background WHILE ad is showing
            val intentDeferred = lifecycleScope.async(Dispatchers.IO) {
                val currentPacks = StickerRepository.allPacksCache
                val packsToSave = if (currentPacks.any { it.id == pack.id }) {
                    currentPacks
                } else {
                    currentPacks + pack
                }
                StickerRepository.saveCacheToDisk(this@DetailsActivity, packsToSave)
                Intent().apply {
                    action = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
                    putExtra("sticker_pack_id", pack.id)
                    putExtra("sticker_pack_authority", "${packageName}.stickers")
                    putExtra("sticker_pack_name", pack.localizedName)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }

            AdManager.showInterstitialIfNeeded(this) {
                // Ad dismissed — launch WhatsApp instantly
                lifecycleScope.launch {
                    try {
                        val intent = intentDeferred.await()
                        addPackLauncher.launch(intent)
                    } catch (e: Exception) {
                        Log.e("DetailsActivity", "Intent error: ${e.message}", e)
                        Toast.makeText(this@DetailsActivity, R.string.whatsapp_not_available_title, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            launchWhatsAppIntent(pack)
        }
    }

    private fun launchWhatsAppIntent(pack: Pack) {
        val intent = Intent().apply {
            action = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
            putExtra("sticker_pack_id", pack.id)
            putExtra("sticker_pack_authority", "${packageName}.stickers")
            putExtra("sticker_pack_name", pack.localizedName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        lifecycleScope.launch {
            try {
                Log.d("DetailsActivity", "Preparing to launch WhatsApp intent for pack: ${pack.id}")

                // KRITIK: Provider'ın veriyi bulabilmesi için cache'i diske kaydet
                // UI'ı dondurmamak için IO thread'inde yap
                withContext(Dispatchers.IO) {
                    val currentPacks = StickerRepository.allPacksCache
                    val packsToSave = if (currentPacks.any { it.id == pack.id }) {
                        currentPacks
                    } else {
                        currentPacks + pack
                    }
                    StickerRepository.saveCacheToDisk(this@DetailsActivity, packsToSave)
                }

                Log.d("DetailsActivity", "Launching addPackLauncher immediately...")
                addPackLauncher.launch(intent)
            } catch (e: Exception) {
                Log.e("DetailsActivity", "Intent error: ${e.message}", e)
                Toast.makeText(this@DetailsActivity, R.string.whatsapp_not_available_title, Toast.LENGTH_SHORT).show()
                showWhatsAppNotAvailableDialog()
            }
        }
    }

    private fun onActivityResultInternal(req: Int, res: Int, data: Intent?) {
        // super.onActivityResult() çağrısına gerek yok, manuel yönetiyoruz
        if (!::btnAction.isInitialized) return

        btnAction.isEnabled = true

        when (req) {
            REQUEST_ADD -> {
                // Önce hızlıca UI'ı güncelle (kullanıcı beklemesin)
                // resultCode RESULT_OK ise büyük ihtimalle eklendi
                if (res == Activity.RESULT_OK) {
                    PreferencesHelper.addInstalledPack(this@DetailsActivity, packId)
                    updateButtonUI(true)
                    Toast.makeText(this@DetailsActivity, R.string.pack_added, Toast.LENGTH_SHORT).show()

                    // İndirme sayısını artır (Firebase'e yaz)
                    currentPack?.let { pack ->
                        StickerRepository.incrementDownloadCount(pack.id, pack.isPremium)
                    }

                    // Sticker ekleme sayacını artır (analiz için)
                    PreferencesHelper.incrementStickersAddedCount(this@DetailsActivity)

                    // Paket ekleme sayacını artır (Bilgilendirme mesajı için)
                    PreferencesHelper.incrementPacksSincePromo(this@DetailsActivity)

                    // Viral paylaşım teşviki — her 2. başarılı eklemede göster
                    val addCount = PreferencesHelper.getStickersAddedCount(this@DetailsActivity)
                    if (addCount % 2 == 0) {
                        showSharePromptDialog()
                    }
                }

                // Arka planda WhatsApp'tan gerçek durumu doğrula
                lifecycleScope.launch {
                    delay(300) // WhatsApp'ın ContentProvider'ı güncellemesi için kısa bekle
                    val isWhitelisted = withContext(Dispatchers.IO) {
                        WhitelistCheck.isWhitelisted(this@DetailsActivity, packId)
                    }

                    val wasInstalled = PreferencesHelper.isPackInstalled(this@DetailsActivity, packId)

                    if (!isWhitelisted && wasInstalled) {
                        // Aslında eklenmemiş veya kaldırılmış
                        PreferencesHelper.removeInstalledPack(this@DetailsActivity, packId)
                        updateButtonUI(false)
                        showThemedSnackbar(getString(R.string.pack_removed_from_whatsapp))
                    } else if (isWhitelisted && !wasInstalled) {
                        // Eklendi ama kaydetmemişiz
                        PreferencesHelper.addInstalledPack(this@DetailsActivity, packId)
                        updateButtonUI(true)

                        // Promo sayacını artır (WhatsApp RESULT_OK döndürmeden ekleme)
                        PreferencesHelper.incrementPacksSincePromo(this@DetailsActivity)
                    }
                }
            }
            REQUEST_REMOVE -> {
                // Önce hızlıca UI'ı güncelle
                if (res == Activity.RESULT_OK) {
                    PreferencesHelper.removeInstalledPack(this@DetailsActivity, packId)
                    updateButtonUI(false)
                }

                // Arka planda WhatsApp'tan gerçek durumu doğrula
                lifecycleScope.launch {
                    delay(300)
                    val isStillWhitelisted = withContext(Dispatchers.IO) {
                        WhitelistCheck.isWhitelisted(this@DetailsActivity, packId)
                    }

                    if (isStillWhitelisted) {
                        // Paket hala yüklü - UI'ı geri al
                        PreferencesHelper.addInstalledPack(this@DetailsActivity, packId)
                        updateButtonUI(true)
                        if (pendingDeletePackId == null) {
                            showPackStillInstalledWarning()
                        }
                    } else {
                        // Başarıyla kaldırıldı
                        PreferencesHelper.removeInstalledPack(this@DetailsActivity, packId)
                        updateButtonUI(false)

                        // Eğer bu bir 'silme' işleminin parçasıysa, şimdi dosyaları sil
                        if (pendingDeletePackId == packId) {
                            if (CustomStickerManager.deletePack(this@DetailsActivity, packId)) {
                                Toast.makeText(this@DetailsActivity, "Paket başarıyla silindi", Toast.LENGTH_SHORT).show()
                                finish()
                            }
                        } else {
                            showThemedSnackbar(getString(R.string.pack_removed_from_whatsapp))
                        }
                    }

                    pendingDeletePackId = null
                }
            }
            REQUEST_ADD_STICKER -> {
                // Only sync if sticker was actually added (RESULT_OK)
                if (res == Activity.RESULT_OK) {
                    // StickerMaker'dan dönüldü, veriyi hemen tazele ki buton güncellensin
                    loadPackFromFirebase()
                    
                    btnAction.isEnabled = true
                    updateButton()
                } else {
                    // User cancelled, just re-enable button
                    btnAction.isEnabled = true
                    updateButton()
                }
            }
        }
    }

    private fun showSharePromptDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_share_prompt, null)

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialogView.findViewById<View>(R.id.btnShareNow)?.setOnClickListener {
            dialog.dismiss()
            val packName = currentPack?.localizedName ?: "Sticky"
            val shareText = getString(R.string.share_pack_text, packName)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, packName)
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share_pack)))
        }

        dialogView.findViewById<View>(R.id.btnShareLater)?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    private fun showPremiumPromoDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_premium_promo, null)

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialogView.findViewById<View>(R.id.btnGetPremium)?.setOnClickListener {
            dialog.dismiss()
            launchPremiumPurchase()
        }

        dialogView.findViewById<View>(R.id.btnMaybeLater)?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    private fun showThemedSnackbar(message: String) {
        val rootView = findViewById<View>(android.R.id.content)
        val snackbar = Snackbar.make(rootView, message, Snackbar.LENGTH_SHORT)
        snackbar.setBackgroundTint(ContextCompat.getColor(this, R.color.card_bg))
        snackbar.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        snackbar.show()
    }

    private fun showReportDialog(pack: Pack) {
        val reportView = layoutInflater.inflate(R.layout.dialog_report, null)
        val reasons = intArrayOf(
            R.string.ai_report_offensive, R.string.ai_report_inappropriate,
            R.string.ai_report_hate, R.string.ai_report_violence,
            R.string.ai_report_spam, R.string.ai_report_other
        )
        val optionIds = intArrayOf(
            R.id.reportOption1, R.id.reportOption2, R.id.reportOption3,
            R.id.reportOption4, R.id.reportOption5, R.id.reportOption6
        )

        val dialog = AlertDialog.Builder(this, R.style.MaterialAlertDialogTheme)
            .setView(reportView)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        for (i in reasons.indices) {
            reportView.findViewById<View>(optionIds[i])?.setOnClickListener {
                dialog.dismiss()
                aiSendReport(getString(reasons[i]), pack)
            }
        }

        dialog.show()
    }

    private fun aiSendReport(reason: String, pack: Pack) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val workerUrl = PocketBaseHelper.WORKER_URL
                    val body = JSONObject().apply {
                        put("type", "ai_report")
                        put("reason", reason)
                        put("pack_id", pack.id)
                        put("pack_name", pack.localizedName)
                        put("pack_image", pack.trayUrl)
                        put("timestamp", System.currentTimeMillis())
                    }
                    val conn = URL("$workerUrl/api/report").openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    conn.doOutput = true
                    OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
                    conn.responseCode
                    conn.disconnect()
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DetailsActivity, R.string.ai_report_sent, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DetailsActivity, R.string.ai_report_error, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    companion object {
        const val EXTRA_AUTO_ADD_TO_WHATSAPP = "auto_add_to_whatsapp"
        private const val REQUEST_ADD = 200
        private const val REQUEST_REMOVE = 201
        private const val REQUEST_ADD_STICKER = 202
    }
}
