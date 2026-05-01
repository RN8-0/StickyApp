package com.sticly

import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
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
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.io.File
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
    // Set to true immediately after a rewarded ad completes for a pack.
    // Prevents showing an interstitial right on top of a just-finished rewarded ad.
    private var rewardedJustCompleted = false
    
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

        // View count ve interstitial — tamamen arka planda
        lifecycleScope.launch(Dispatchers.IO) {
            StickerRepository.incrementViewCount(pack.id, pack.isPremium)
        }
        lifecycleScope.launch { AdManager.loadInterstitialAd(this@DetailsActivity) }

        findViewById<android.widget.TextView>(R.id.name).text = pack.localizedName

        btnAction = findViewById(R.id.btnAction)
        btnWatchAd = findViewById(R.id.btnWatchAd)
        premiumButtonsContainer = findViewById(R.id.premiumButtonsContainer)
        customButtonsContainer = findViewById(R.id.customButtonsContainer)
        btnGridAddSticker = findViewById(R.id.btnGridAddSticker)
        btnGridDeleteMode = findViewById(R.id.btnGridDeleteMode)
        btnConfirmDelete = findViewById(R.id.btnConfirmDelete)
        installedIcon = findViewById(R.id.installedIcon)
        btnFixedWhatsApp = findViewById(R.id.btnFixedWhatsApp)

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

        // Arka planda preload başlat — adapter zaten render ediyor, bu sadece cache ısıtma
        lifecycleScope.launch(Dispatchers.IO) {
            preloadAllStickers(pack, displayStickers)
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

        // Arka planda cache'e indir (WhatsApp için gerekli) — preload bittikten sonra
        lifecycleScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(1500) // Preload'un bitmesini bekle
            pack.stickers.forEach { sticker ->
                try {
                    StickerRepository.downloadStickerToCache(this@DetailsActivity, pack.id, sticker.file, storagePath)
                } catch (_: Exception) {}
            }
        }

        // Butonları ayarla
        setupButtons(pack, hasAccess)

        // İlgili paketleri gecikmeli yükle (ilk render'ı bloklamasın)
        rv.post { setupRelatedPacks(pack) }
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
                        StickerRepository.downloadStickerToCache(this@DetailsActivity, pack.id, sticker.file, pack.storagePath)
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
            val btnPublish = findViewById<MaterialButton>(R.id.btnPublishPack)
            if (btnPublish != null) {
                btnPublish.visibility = View.VISIBLE
                checkIfAlreadySubmitted(pack.id) { alreadySubmitted ->
                    if (alreadySubmitted) {
                        btnPublish.text = getString(R.string.publish_pack_already_submitted)
                        btnPublish.isEnabled = false
                    } else {
                        btnPublish.setOnClickListener {
                            val intent = Intent(this, SubmitPackActivity::class.java)
                            intent.putExtra("packId", pack.id)
                            startActivity(intent)
                            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                        }
                    }
                }
            }
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
        tvRelatedTitle?.text = "You May Also Like"
        rvRelated.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        rvRelated.setHasFixedSize(false)
        rvRelated.itemAnimator = null

        val relatedAdapter = PackAdapter(
            items = relatedPacks,
            click = { pack ->
                startActivity(Intent(this, DetailsActivity::class.java).putExtra("id", pack.id))
            },
            onAddClick = { pack ->
                startActivity(Intent(this, DetailsActivity::class.java).putExtra("id", pack.id))
            }
        )
        rvRelated.adapter = relatedAdapter
    }

    private fun launchPremiumPurchase() {
        startActivity(Intent(this, PremiumActivity::class.java))
    }

    private fun checkIfAlreadySubmitted(packId: String, callback: (Boolean) -> Unit) {
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (user == null) { callback(false); return }
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        lifecycleScope.launch {
            try {
                val existing = withContext(Dispatchers.IO) {
                    db.collection("user_submissions")
                        .whereEqualTo("user_id", user.uid)
                        .whereEqualTo("source_pack_id", packId)
                        .whereNotEqualTo("status", "rejected")
                        .get()
                        .await()
                }
                callback(!existing.isEmpty)
            } catch (_: Exception) {
                callback(false)
            }
        }
    }

    private fun showPublishDialog(pack: Pack) {
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(this, getString(R.string.publish_pack_login_required), Toast.LENGTH_SHORT).show()
            return
        }
        if (pack.stickers.size < 3) {
            Toast.makeText(this, getString(R.string.publish_pack_min_stickers), Toast.LENGTH_SHORT).show()
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
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val storage = com.google.firebase.storage.FirebaseStorage.getInstance()
        val submissionId = java.util.UUID.randomUUID().toString()
        val btnPublish = findViewById<MaterialButton>(R.id.btnPublishPack)
        btnPublish?.isEnabled = false
        btnPublish?.text = getString(R.string.publishing)

        lifecycleScope.launch {
            try {
                val stickersList = mutableListOf<Map<String, String>>()
                val basePath = "user_uploads/${user.uid}/$submissionId"
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
                            val ref = storage.reference.child("$basePath/$fileName")
                            ref.putFile(android.net.Uri.fromFile(localFile)).await()
                            val url = ref.downloadUrl.await().toString()
                            stickersList.add(mapOf("name" to sticker.file, "image_url" to url))
                        } else if (sticker.url.isNotEmpty()) {
                            stickersList.add(mapOf("name" to sticker.file, "image_url" to sticker.url))
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    progressBar.progress = 100
                    tvProgress.text = getString(R.string.saving_submission)
                }

                if (stickersList.isEmpty()) {
                    Toast.makeText(this@DetailsActivity, getString(R.string.publish_pack_failed), Toast.LENGTH_SHORT).show()
                    btnPublish?.isEnabled = true
                    btnPublish?.text = getString(R.string.publish_pack)
                    onDone()
                    return@launch
                }

                val submission = hashMapOf(
                    "user_id"        to user.uid,
                    "user_email"     to (user.email ?: ""),
                    "display_name"   to publisherName,
                    "publisher_name" to publisherName,
                    "pack_name"      to packName,
                    "description"    to description,
                    "category"       to category.ifEmpty { "general" },
                    "stickers"       to stickersList,
                    "sticker_count"  to stickersList.size,
                    "source_pack_id" to pack.id,
                    "is_animated"    to pack.isAnimated,
                    "status"         to "pending",
                    "created_at"     to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )

                withContext(Dispatchers.IO) {
                    db.collection("user_submissions").document(submissionId).set(submission).await()
                }

                Toast.makeText(this@DetailsActivity, getString(R.string.publish_pack_success), Toast.LENGTH_LONG).show()
                btnPublish?.text = getString(R.string.publish_pack_already_submitted)
                onDone()

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@DetailsActivity, getString(R.string.publish_pack_failed), Toast.LENGTH_SHORT).show()
                btnPublish?.isEnabled = true
                btnPublish?.text = getString(R.string.publish_pack)
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

        dialog.setContentView(view)

        lockOverlay?.visibility = View.GONE
        unlockHint?.visibility = View.GONE

        // Determine the load source
        val loadSource: Any? = if (packId.startsWith("custom_")) {
            val customFile = CustomStickerManager.getCustomStickerPath(this, packId, sticker.file)
            if (customFile.exists()) customFile else null
        } else {
            val cachedFile = StickerRepository.getCachedStickerPath(this, packId, sticker.file)
            when {
                cachedFile.exists() && cachedFile.length() > 0 -> cachedFile
                sticker.url.isNotEmpty() -> sticker.url
                else -> android.net.Uri.parse("file:///android_asset/$packId/${sticker.file}")
            }
        }

        if (loadSource == null) {
            imageView.setImageResource(R.drawable.transparent_placeholder)
        } else {
            // Animated WebP: must use DATA cache (Glide has no encoder for AnimatedImageDrawable)
            val cacheStrategy = if (isAnimatedPack)
                com.bumptech.glide.load.engine.DiskCacheStrategy.DATA
            else
                com.bumptech.glide.load.engine.DiskCacheStrategy.RESOURCE

            val request = Glide.with(this)
                .load(loadSource)
                .placeholder(R.drawable.transparent_placeholder)
                .diskCacheStrategy(cacheStrategy)
                .error(R.drawable.transparent_placeholder)

            if (!isAnimatedPack) {
                request.override(512, 512)
            }
            request.into(imageView)
        }

        // Tasarımdaki animasyonlu açılış
        view.scaleX = 0.7f
        view.scaleY = 0.7f
        view.alpha = 0f

        view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(250)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.1f))
            .start()

        // Ekrana tıklandığında da kapat (Referans projeyle aynı)
        view.setOnClickListener {
            view.animate()
                .scaleX(0.7f)
                .scaleY(0.7f)
                .alpha(0f)
                .setDuration(200)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction {
                    dialog.dismiss()
                }
                .start()
        }

        dialog.show()
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
                        StickerRepository.downloadStickerToCache(this@DetailsActivity, pack.id, pack.tray, storagePath)
                        downloadedCount.incrementAndGet()
                    } catch (_: Exception) {}

                    pack.stickers.chunked(10).forEach { chunk ->
                        chunk.map { sticker ->
                            async {
                                try {
                                    StickerRepository.downloadStickerToCache(this@DetailsActivity, pack.id, sticker.file, storagePath)
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
            // Dosya boyutu kontrolü - WhatsApp limitleri
            val sizeCheckResult = checkStickerFileSizes(pack)
            if (sizeCheckResult != null) {
                Toast.makeText(this, sizeCheckResult, Toast.LENGTH_LONG).show()
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
        val maxTraySize = 50 * 1024L // 50KB
        val maxSize = if (pack.isAnimated) maxAnimatedSize else maxStaticSize

        val cacheDir = java.io.File(cacheDir, "sticker_cache/${pack.id}")
        if (!cacheDir.exists()) return null // Cache yoksa kontrol etme

        var oversizedCount = 0
        var largestFile = ""
        var largestSize = 0L

        // Sticker dosyalarını kontrol et
        for (sticker in pack.stickers) {
            val file = java.io.File(cacheDir, sticker.file)
            if (file.exists() && file.length() > maxSize) {
                oversizedCount++
                if (file.length() > largestSize) {
                    largestSize = file.length()
                    largestFile = sticker.file
                }
            }
        }

        // Tray dosyasını kontrol et
        val trayFile = cacheDir.listFiles()?.find { it.name.startsWith("tray") && it.name.endsWith(".png") }
        if (trayFile != null && trayFile.length() > maxTraySize) {
            // Tray çok büyük ama bu genellikle sorun değil çünkü StickerProvider 96x96'ya dönüştürüyor
        }

        if (oversizedCount > 0) {
            val limitKB = maxSize / 1024
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
                            StickerRepository.downloadStickerToCache(this@DetailsActivity, pack.id, pack.tray, storagePath)
                            val count = downloadedCount.incrementAndGet()
                            updateProgressText(count, totalFiles)
                        }

                        // Sticker'ları paralel indir (16 adet aynı anda - hızlı indirme)
                        pack.stickers.chunked(16).forEach { chunk ->
                            chunk.map { sticker ->
                                async {
                                    StickerRepository.downloadStickerToCache(this@DetailsActivity, pack.id, sticker.file, storagePath)
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
                            StickerRepository.downloadStickerToCache(this@DetailsActivity, pack.id, pack.tray, storagePath)
                            downloadedCount.incrementAndGet()
                        }

                        // Use larger chunks or all at once for speed
                        pack.stickers.chunked(30).forEach { chunk ->
                            chunk.map { sticker ->
                                async {
                                    StickerRepository.downloadStickerToCache(this@DetailsActivity, pack.id, sticker.file, storagePath)
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

    override fun onDestroy() {
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_ADD = 200
        private const val REQUEST_REMOVE = 201
        private const val REQUEST_ADD_STICKER = 202
    }
}
