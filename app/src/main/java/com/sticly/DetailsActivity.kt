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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import android.animation.ValueAnimator
import android.util.Log

class DetailsActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    private lateinit var packId: String
    private lateinit var btnAction: MaterialButton
    private lateinit var premiumButtonsContainer: LinearLayout
    private lateinit var customButtonsContainer: LinearLayout
    private lateinit var btnGridAdd: MaterialButton
    private lateinit var btnGridUpdate: MaterialButton
    private lateinit var btnGridAddSticker: MaterialButton
    private lateinit var btnGridDeleteMode: MaterialButton
    private lateinit var btnConfirmDelete: ImageButton
    private lateinit var installedIcon: ImageView
    private var currentPack: Pack? = null
    private var isDeleteMode = false
    private val selectedIndices = mutableSetOf<Int>()
    private var progressDialog: AlertDialog? = null // Profesyonel yükleme dialoğu
    private var adapter: StickerAdapter? = null
    private var isPackReady = false // Çıkartmalar yüklendi mi kontrolü
    private var isDownloading = false // İndirme devam ediyor mu
    private var currentProgress = 0 // Mevcut progress yüzdesi
    private var progressAnimator: ValueAnimator? = null
    private var pendingDeletePackId: String? = null
    private var wasPackInWhatsAppBeforeDelete = false
    private var waitingForWhatsAppReturn = false
    
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

    private lateinit var professionalLoadingOverlay: View
    private lateinit var tvOverlayLoadingText: TextView

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_details)

        packId = intent.getStringExtra("id") ?: return finish()

        professionalLoadingOverlay = findViewById(R.id.professionalLoadingOverlay)
        tvOverlayLoadingText = findViewById(R.id.tvOverlayLoadingText)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        findViewById<ImageButton>(R.id.btnHome).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

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
        
        // KRITIK: Veriyi taze yükle (StickerMaker'dan dönüldüğünde veya değişiklik olduğunda)
        // onCreate içindeki ilk yüklemeyi burada da yapıyoruz, çakışmayı önlemek için bayrak eklenebilir
        // ama lifecycleScope zaten thread-safe çalıştığı için sorun teşkil etmez.
        loadPackFromFirebase()
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

                // Arka planda veriyi tazele (Firebase paketleri için)
                val packs = withContext(Dispatchers.IO) { StickerRepository.loadPacks(this@DetailsActivity, forceRefresh = false) }
                val updatedPack = packs.find { it.id == packId }
                
                if (updatedPack != null) {
                    if (updatedPack != pack) {
                        setupUI(updatedPack)
                    }
                } else if (pack == null) {
                    android.util.Log.e("DetailsActivity", "Pack not found: $packId")
                    Toast.makeText(this@DetailsActivity, R.string.pack_not_found, Toast.LENGTH_SHORT).show()
                    finish()
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
        isPackReady = false // Reset state when setting up new pack

        // Görüntülenme sayısını artır (Firebase'e yaz)
        StickerRepository.incrementViewCount(pack.id, pack.isPremium)

        findViewById<android.widget.TextView>(R.id.name).text = pack.localizedName

        btnAction = findViewById(R.id.btnAction)
        premiumButtonsContainer = findViewById(R.id.premiumButtonsContainer)
        customButtonsContainer = findViewById(R.id.customButtonsContainer)
        btnGridAdd = findViewById(R.id.btnGridAdd)
        btnGridUpdate = findViewById(R.id.btnGridUpdate)
        btnGridAddSticker = findViewById(R.id.btnGridAddSticker)
        btnGridDeleteMode = findViewById(R.id.btnGridDeleteMode)
        btnConfirmDelete = findViewById(R.id.btnConfirmDelete)
        installedIcon = findViewById(R.id.installedIcon)

        // Başlangıçta içeriği göster, ama butonu hazır olana kadar bekleme (WhatsApp için arka planda inecek)
        btnAction.isEnabled = false 
        showLoadingState(true)
        tvOverlayLoadingText.text = getString(R.string.pack_loading)

        val rv = findViewById<RecyclerView>(R.id.rv)
        rv.layoutManager = GridLayoutManager(this, 3)

        val hasAccess = PreferencesHelper.hasAccessToPremiumPack(this, pack.id)
        val storagePath = pack.storagePath

        // ÖNCELİKLE: Tüm URL'leri hesapla (adapter oluşturmadan ÖNCE!)
        pack.stickers.forEach { sticker ->
            if (sticker.url.isEmpty()) {
                sticker.url = StickerRepository.getStickerDirectUrl(pack.id, sticker.file, storagePath)
            }
        }

        // Premium pakette ve erişim yoksa rastgele 3 çıkartmayı başa al
        val displayStickers = if (pack.isPremium && !hasAccess && pack.stickers.size > 3) {
            val shuffled = pack.stickers.shuffled()
            val first3 = shuffled.take(3)
            val rest = shuffled.drop(3)
            first3 + rest
        } else {
            pack.stickers
        }

        // KRITIK: Tüm çıkartmaları Glide ile preload et (anında görünmeleri için)
        // Bu sayede RecyclerView bind olduğunda görseller zaten memory cache'de olacak
        preloadAllStickers(pack, displayStickers)

        // Şimdi adapter oluştur - URL'ler HAZIR
        adapter = StickerAdapter(
            packId = pack.id,
            items = displayStickers,
            isPackPremium = pack.isPremium,
            hasAccess = hasAccess,
            storagePath = pack.storagePath,
            selectedPositions = selectedIndices,
            onStickerClick = { sticker, _ ->
                showStickerPreview(sticker, false)
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

        // Arka planda cache'e indir (WhatsApp için gerekli)
        lifecycleScope.launch(Dispatchers.IO) {
            pack.stickers.forEach { sticker ->
                try {
                    StickerRepository.downloadStickerToCache(this@DetailsActivity, pack.id, sticker.file, storagePath)
                } catch (_: Exception) {}
            }
        }

        // Butonları ayarla
        setupButtons(pack, hasAccess)

        // Eski FAB gizle
        findViewById<View>(R.id.fabAddSticker).visibility = View.GONE
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

            // İlk 9 çıkartmayı öncelikli yükle (görünen alan)
            val priorityStickers = stickers.take(9)
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
                        .submit(256, 256).get()
                }
            }
            else -> {
                val cachedFile = StickerRepository.getCachedStickerPath(applicationContext, pack.id, sticker.file)
                when {
                    cachedFile.exists() && cachedFile.length() > 0 -> {
                        glide.load(cachedFile)
                            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                            .submit(256, 256).get()
                    }
                    sticker.url.isNotEmpty() -> {
                        glide.load(sticker.url)
                            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.DATA)
                            .submit(256, 256).get()
                    }
                    storagePath.isNotEmpty() -> {
                        val directUrl = StickerRepository.getStickerDirectUrl(pack.id, sticker.file, storagePath)
                        glide.load(directUrl)
                            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.DATA)
                            .submit(256, 256).get()
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
        if (!::professionalLoadingOverlay.isInitialized) return

        if (isLoading) {
            professionalLoadingOverlay.visibility = View.VISIBLE
        } else {
            professionalLoadingOverlay.visibility = View.GONE
        }
    }

    private fun confirmDelete() {
        val selectedIndices = adapter?.selectedPositions?.toList()?.map { it + 1 } ?: return
        if (selectedIndices.isEmpty()) {
            toggleDeleteMode()
            return
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_mode)
            .setMessage("${selectedIndices.size} adet çıkartmayı silmek istediğinize emin misiniz?\n\nWhatsApp'tan da güncellenecektir.")
            .setPositiveButton(R.string.yes) { _, _ ->
                if (CustomStickerManager.removeStickersFromPack(this, packId, selectedIndices)) {
                    Toast.makeText(this, "Çıkartmalar silindi", Toast.LENGTH_SHORT).show()

                    // Silme modunu kapat
                    toggleDeleteMode()

                    // Paketi yeniden yükle
                    loadPackFromFirebase()

                    // WhatsApp'a ekliyse güncelleme ekranını aç
                    if (PreferencesHelper.isPackInstalled(this, packId)) {
                        currentPack?.let { pack ->
                            forceUpdateWhatsApp(pack)
                        }
                    }
                } else {
                    Toast.makeText(this, "Silme işlemi başarısız", Toast.LENGTH_SHORT).show()
                    toggleDeleteMode()
                }
            }
            .setNegativeButton(R.string.no) { _, _ -> toggleDeleteMode() }
            .show()
    }

    private fun showDeletePackDialog(pack: Pack) {
        lifecycleScope.launch {
            val isWhitelisted = withContext(Dispatchers.IO) {
                WhitelistCheck.isWhitelisted(this@DetailsActivity, pack.id)
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
                Toast.makeText(this@DetailsActivity, "WhatsApp yüklü değil", Toast.LENGTH_SHORT).show()
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
                    finish()
                }
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun showDeleteStickerDialog(packId: String, index: Int) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Çıkartmayı Sil")
            .setMessage("Bu çıkartmayı silmek istediğinize emin misiniz?\n\n(Bu işlem WhatsApp'tan da kaldırılmasını tetikleyecektir)")
            .setPositiveButton(R.string.yes) { _, _ ->
                if (CustomStickerManager.removeStickerFromPack(this, packId, index + 1)) {
                    Toast.makeText(this, "Çıkartma silindi ve WhatsApp zorunlu güncellemeye tetiklendi.", Toast.LENGTH_SHORT).show()
                    // Sayfayı yenile
                    recreate()
                } else {
                    Toast.makeText(this, "Çıkartma silinemedi", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.no, null)
            .show()
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
        // Özel paket mi kontrol et
        val isCustom = pack.category == "custom" || pack.id.startsWith("custom_")

        if (isCustom) {
            btnAction.visibility = View.GONE
            premiumButtonsContainer.visibility = View.GONE
            customButtonsContainer.visibility = View.VISIBLE
            installedIcon.visibility = View.GONE // Custom packs don't use this icon

            // Custom pack buttons
            btnGridAdd.visibility = View.GONE // Ana butona (btnAction) taşıdık
            (findViewById<View>(R.id.btnGridAdd).parent as? View)?.visibility = View.GONE
            
            btnAction.visibility = View.VISIBLE
            updateButton() // Ekle/Güncelle durumunu ayarlar
            
            btnAction.setOnClickListener {
                handleButtonClick(pack)
            }

            btnGridUpdate.setOnClickListener {
                forceUpdateWhatsApp(pack)
            }
            
            btnGridAddSticker.setOnClickListener {
                val intent = Intent(this, StickerMakerActivity::class.java)
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
        } else if (pack.isPremium && !hasAccess) {
            // Premium paket ve erişim yoksa - sadece Premium abonelik butonu göster
            btnAction.visibility = View.GONE
            customButtonsContainer.visibility = View.GONE
            premiumButtonsContainer.visibility = View.VISIBLE
            installedIcon.visibility = View.GONE

            // Fiyat butonunu gizle - artık tek tek satış yok
            findViewById<MaterialButton>(R.id.btnPrice).visibility = View.GONE

            // Premium Badge -> PremiumActivity (abonelik seçenekleri)
            val btnPremiumBadge = findViewById<MaterialButton>(R.id.btnPremiumBadge)
            // Tam genişlik yap (fiyat butonu gizli olduğu için)
            (btnPremiumBadge.layoutParams as? android.widget.LinearLayout.LayoutParams)?.let {
                it.weight = 2f
                it.marginEnd = 0
                btnPremiumBadge.layoutParams = it
            }
            btnPremiumBadge.setOnClickListener {
                val intent = Intent(this, PremiumActivity::class.java)
                startActivity(intent)
            }

            // Premium paketler için de yükleme ekranı göster
            val isCached = StickerRepository.isPackCached(this, pack)
            if (!isCached && !isDownloading) {
                startBackgroundDownload(pack)
            } else {
                showLoadingState(false)
            }
        } else {
            // Normal paket veya premium pakette erişim var
            btnAction.visibility = View.VISIBLE
            customButtonsContainer.visibility = View.GONE
            premiumButtonsContainer.visibility = View.GONE

            // Paket zaten yüklü mü kontrol et
            val isInstalled = PreferencesHelper.isPackInstalled(this, pack.id)
            val isCached = StickerRepository.isPackCached(this, pack)

            if (!isInstalled && !isCached && !isDownloading) {
                // Cache'de yok ve yüklü değil - otomatik indirmeyi başlat
                startBackgroundDownload(pack)
            } else {
                // Zaten yüklü veya cache'de var - butonu güncelle
                // Bu WhatsApp ile senkronize edecek
                updateButton()
                btnAction.isEnabled = true
                showLoadingState(false)
            }

            btnAction.setOnClickListener {
                handleButtonClick(pack)
            }
        }
    }

    private fun updateWhatsApp(pack: Pack) {
        // WhatsApp yüklü mü kontrol et
        if (!isWhatsAppInstalled()) {
            Toast.makeText(this, R.string.whatsapp_not_installed, Toast.LENGTH_SHORT).show()
            return
        }

        // KRITIK: Version bilgisinin String olduğundan ve değiştiğinden emin ol
        android.util.Log.d("DetailsActivity", "Updating WhatsApp for Pack: ${pack.id} Version: ${pack.version}")

        // WhatsApp'a ekle intent'ini tekrar gönder (WhatsApp eğer yüklüyse günceller/açık tutar)
        val intent = Intent().apply {
            action = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
            putExtra("sticker_pack_id", pack.id)
            putExtra("sticker_pack_authority", "${packageName}.stickers")
            putExtra("sticker_pack_name", pack.localizedName)
            // KRITIK: WhatsApp'ın provider'dan okuyabilmesi için izin ver
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // Bu intent ile WhatsApp direkt paket detayını açar, eğer versiyon farklıysa "Güncelle" butonu çıkar
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "WhatsApp güncellenemedi: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Paketi zorla günceller ve WhatsApp'a gönderir
     * Version numarasını artırarak WhatsApp'ın "Güncelle" göstermesini sağlar
     */
    private fun forceUpdateWhatsApp(pack: Pack) {
        // WhatsApp yüklü mü kontrol et
        if (!isWhatsAppInstalled()) {
            Toast.makeText(this, R.string.whatsapp_not_installed, Toast.LENGTH_SHORT).show()
            return
        }

        // Paket WhatsApp'a ekli mi kontrol et
        if (!PreferencesHelper.isPackInstalled(this, pack.id)) {
            Toast.makeText(this, "Çıkartma paketi WhatsApp'a ekli değil", Toast.LENGTH_SHORT).show()
            return
        }

        // Sadece custom paketler için version artır
        if (pack.id.startsWith("custom_")) {
            val newVersion = CustomStickerManager.forceUpdateVersion(this, pack.id)
            if (newVersion > 0) {
                android.util.Log.d("DetailsActivity", "Force updated pack ${pack.id} to version $newVersion")
                Toast.makeText(this, "Paket güncellendi (v$newVersion)", Toast.LENGTH_SHORT).show()
            }
        }

        // WhatsApp'a intent gönder
        val intent = Intent().apply {
            action = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
            putExtra("sticker_pack_id", pack.id)
            putExtra("sticker_pack_authority", "${packageName}.stickers")
            putExtra("sticker_pack_name", pack.localizedName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            addPackLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "WhatsApp açılamadı: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchPremiumPurchase() {
        startActivity(Intent(this, PremiumActivity::class.java))
    }


    /**
     * Veriyi repository üzerinden tazeler ve WhatsApp senkronizasyonunu tetikler (UX odaklı)
     */
    private fun syncPackDataWithProgress(packId: String, message: String) {
        // Profesyonel Loading Göster
        progressDialog = AlertDialog.Builder(this)
            .setMessage(message)
            .setCancelable(false)
            .create()
        progressDialog?.show()

        lifecycleScope.launch {
            try {
                // 1. Veriyi tazele (IO) - ForceRefresh=true ile önbelleği baypas et
                val packs = withContext(Dispatchers.IO) { StickerRepository.loadPacks(this@DetailsActivity, forceRefresh = true) }
                val updatedPack = packs.find { it.id == packId }
                
                if (updatedPack != null) {
                    currentPack = updatedPack
                    // 2. WhatsApp'ı tetikle
                    updateWhatsApp(updatedPack)
                }
                
                delay(800) // UX: İşlemin yapıldığı hissini ver
                
                // 3. UI Kapat ve Yenile
                progressDialog?.dismiss()
                if (isDeleteMode) {
                    toggleDeleteMode()
                    recreate()
                } else {
                    updateButton()
                }
            } catch (e: Exception) {
                progressDialog?.dismiss()
                Toast.makeText(this@DetailsActivity, "Senkronizasyon hatası", Toast.LENGTH_SHORT).show()
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
        val shareText = "Check out this '${pack.localizedName}' sticker pack! \n\nDownload Sticky app: https://play.google.com/store/apps/details?id=$packageName"
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
        btnAction.isEnabled = true // KRITIK: Butonun tıklanabilir olduğundan emin ol

        // Modern, ince buton tasarımı
        val buttonHeight = (48 * resources.displayMetrics.density).toInt()
        val cornerRadius = (12 * resources.displayMetrics.density).toInt()

        if (isInstalled) {
            btnAction.text = getString(R.string.share_pack)
            btnAction.setIconResource(R.drawable.ic_share)
            btnAction.setTextColor(ContextCompat.getColor(this@DetailsActivity, R.color.white))
            btnAction.setIconTintResource(R.color.white)
            btnAction.backgroundTintList = ContextCompat.getColorStateList(this, R.color.primary)
            btnAction.cornerRadius = cornerRadius
            installedIcon.visibility = View.VISIBLE
        } else {
            btnAction.text = getString(R.string.add_to_whatsapp)
            btnAction.setIconResource(R.drawable.ic_whatsapp_small)
            btnAction.iconTint = ContextCompat.getColorStateList(this, R.color.white)
            btnAction.setTextColor(ContextCompat.getColor(this, R.color.white))

            // Uygulama yeşili (primary) ve modern buton tasarımı
            btnAction.backgroundTintList = ContextCompat.getColorStateList(this, R.color.primary)
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
            // Status bar ana sayfadaki toolbar ile aynı renk olacak
            statusBarColor = toolbarColor
            navigationBarColor = previewBgColor
        }

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_sticker_preview, null)
        val imageView = view.findViewById<ImageView>(R.id.previewImage)
        val lockOverlay = view.findViewById<ImageView>(R.id.lockOverlay)
        val unlockHint = view.findViewById<android.widget.TextView>(R.id.unlockHint)

        dialog.setContentView(view)

        // Kilitli ise blur uygula ve kilit göster
        val blurTransform = if (isLocked) {
            com.bumptech.glide.request.RequestOptions()
                .transform(jp.wasabeef.glide.transformations.BlurTransformation(20, 3))
        } else {
            com.bumptech.glide.request.RequestOptions()
        }

        // Lock overlay ve hint göster/gizle
        lockOverlay?.visibility = if (isLocked) View.VISIBLE else View.GONE
        unlockHint?.visibility = if (isLocked) View.VISIBLE else View.GONE

        val cachedFile = StickerRepository.getCachedStickerPath(this, packId, sticker.file)
        when {
            cachedFile.exists() && cachedFile.length() > 0 -> {
                Glide.with(this)
                    .load(cachedFile)
                    .signature(com.bumptech.glide.signature.ObjectKey(cachedFile.lastModified()))
                    //.skipMemoryCache(true)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE) // Local file, use memory cache only
                    .apply(blurTransform)
                    .into(imageView)
            }
            sticker.url.isNotEmpty() -> {
                val cacheSignature = com.bumptech.glide.signature.ObjectKey(sticker.url)
                Glide.with(this)
                    .load(sticker.url)
                    .signature(cacheSignature)
                    .placeholder(R.drawable.transparent_placeholder) // Add placeholder while loading
                    .error(R.drawable.transparent_placeholder) // Add error placeholder
                    //.skipMemoryCache(true)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.DATA)
                    .apply(blurTransform)
                    .into(imageView)
            }
            else -> {
                try {
                    val path = "$packId/${sticker.file}"
                    val stream = assets.open(path)
                    val bitmap = BitmapFactory.decodeStream(stream)
                    stream.close()
                    Glide.with(this)
                        .load(bitmap)
                        .apply(blurTransform)
                        .into(imageView)
                } catch (e: Exception) {
                    imageView.setImageResource(R.drawable.transparent_placeholder)
                }
            }
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
                    if (isLocked) {
                        launchPremiumPurchase()
                    }
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
        // Çıkartma sayısı kontrolü - boş paket
        if (pack.stickers.isEmpty()) {
            Toast.makeText(this, R.string.pack_empty_error, Toast.LENGTH_LONG).show()
            return
        }

        // WhatsApp için minimum 3 çıkartma gerekli
        if (pack.stickers.size < 3) {
            Toast.makeText(this, R.string.pack_min_stickers_error, Toast.LENGTH_LONG).show()
            return
        }

        // WhatsApp kontrolü
        if (!isWhatsAppInstalled()) {
            Toast.makeText(this, R.string.whatsapp_not_installed, Toast.LENGTH_SHORT).show()
            showWhatsAppNotAvailableDialog()
            return
        }

        // Reklam Gösterimi (Eğer Premium değilse)
        if (!PreferencesHelper.isPremium(this)) {
            // Önce loading göster (reklam hazırlanana kadar)
            showLoadingState(true)
            tvOverlayLoadingText.text = getString(R.string.ad_preparing)

            lifecycleScope.launch {
                // Reklam hazır değilse bekle (maksimum 6 saniye)
                var waitCount = 0
                while (!AdManager.isInterstitialReady() && waitCount < 24) {
                    delay(250)
                    waitCount++
                    // Her 1 saniyede bir yüklemeyi zorla
                    if (waitCount % 4 == 0 && !AdManager.isInterstitialReady()) {
                       AdManager.loadInterstitial(this@DetailsActivity)
                    }
                }

                if (!AdManager.isInterstitialReady()) {
                    Log.d("DetailsActivity", "Ad not ready after 6s, proceeding without ad")
                }

                AdManager.showInterstitialWithCallback(this@DetailsActivity) {
                    showLoadingState(false)
                    // Reklam kapandıktan veya hata verdikten sonra asıl işleme devam et
                    proceedToAddToWhatsApp(pack)
                }
            }
        } else {
            // Premium ise direkt devam et
            proceedToAddToWhatsApp(pack)
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
        tvOverlayLoadingText.text = getString(R.string.loading_percent, 0)
        showLoadingState(true)

        val totalFiles = pack.stickers.size + 1
        val downloadedCount = AtomicInteger(0)

        lifecycleScope.launch {
            try {
                android.util.Log.d("DetailsActivity", "Starting background download: ${pack.id}")
                val storagePath = pack.storagePath

                // İlk bir progress verelim (%5) ki takılı kalmış gibi görünmesin
                runOnUiThread { tvOverlayLoadingText.text = getString(R.string.loading_percent, 5) }

                // Paralel indirme
                withContext(Dispatchers.IO) {
                    kotlinx.coroutines.coroutineScope {
                        // Tray'i indir
                        val trayJob = async {
                            StickerRepository.downloadStickerToCache(this@DetailsActivity, pack.id, pack.tray, storagePath)
                            val count = downloadedCount.incrementAndGet()
                            updateProgressText(count, totalFiles)
                        }

                        // Sticker'ları paralel indir (4 adet aynı anda)
                        pack.stickers.chunked(4).forEach { chunk ->
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

                // %100 yap ve kapat
                runOnUiThread {
                    tvOverlayLoadingText.text = getString(R.string.loading_percent, 100)
                }
                delay(300)

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
            tvOverlayLoadingText.text = getString(R.string.loading_percent, percent)
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
        tvOverlayLoadingText.text = getString(R.string.loading_percent, 0)
        showLoadingState(true)

        val totalFiles = pack.stickers.size + 1
        val downloadedCount = AtomicInteger(0)

        fun animateProgressTo(targetPercent: Int) {
            progressAnimator?.cancel()
            progressAnimator = ValueAnimator.ofInt(currentProgress, targetPercent).apply {
                duration = 150
                addUpdateListener { animator ->
                    currentProgress = animator.animatedValue as Int
                    tvOverlayLoadingText.text = getString(R.string.loading_percent, currentProgress)
                }
                start()
            }
        }

        lifecycleScope.launch {
            try {
                val storagePath = pack.storagePath

                withContext(Dispatchers.IO) {
                    kotlinx.coroutines.coroutineScope {
                        val trayJob = async {
                            StickerRepository.downloadStickerToCache(this@DetailsActivity, pack.id, pack.tray, storagePath)
                            val count = downloadedCount.incrementAndGet()
                            val percent = (count * 100) / totalFiles
                            withContext(Dispatchers.Main) { animateProgressTo(percent) }
                        }

                        pack.stickers.chunked(4).forEach { chunk ->
                            chunk.map { sticker ->
                                async {
                                    StickerRepository.downloadStickerToCache(this@DetailsActivity, pack.id, sticker.file, storagePath)
                                    val count = downloadedCount.incrementAndGet()
                                    val percent = (count * 100) / totalFiles
                                    withContext(Dispatchers.Main) { animateProgressTo(percent) }
                                }
                            }.awaitAll()
                        }

                        trayJob.await()
                    }
                }

                withContext(Dispatchers.Main) { animateProgressTo(100) }
                delay(200)

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
        // Overlay loading göster (sadece indirme gerekiyorsa veya ilk kezse)
        showLoadingState(true)
        tvOverlayLoadingText.text = getString(R.string.preparing_sticker)

        lifecycleScope.launch {
            // Reklam zaten addToWhatsApp başında gösterildi
            // WhatsApp intent'ini hemen tetikle
            launchWhatsAppIntent(pack)
            
            // Kullanıcı WhatsApp'a geçene kadar loading'i tut (görsel süreklilik için)
            delay(1500) 
            showLoadingState(false)
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
                
                Log.d("DetailsActivity", "Metadata saved to disk, waiting for transition...")
                delay(300) // Reklam sonrası sistemin toparlanması için bekleme
                
                Log.d("DetailsActivity", "Launching addPackLauncher...")
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
                // StickerMaker'dan dönüldü, veriyi hemen tazele ki buton güncellensin
                loadPackFromFirebase()
                
                // Eğer paket yüklüyse WhatsApp'ı zorla güncelle (Süreç bittiğinde butonu aktif etmeyi unutma)
                if (PreferencesHelper.isPackInstalled(this, packId)) {
                    syncPackDataWithProgress(packId, "Yeni çıkartma eklendi, WhatsApp güncelleniyor...")
                } else {
                    btnAction.isEnabled = true
                    updateButton()
                }
            }
        }
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
