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

class DetailsActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    private lateinit var packId: String
    private lateinit var btnAction: MaterialButton
    private lateinit var premiumButtonsContainer: LinearLayout
    private lateinit var btnPremiumBadge: MaterialButton
    private lateinit var btnPrice: MaterialButton
    private lateinit var customButtonsContainer: LinearLayout
    private lateinit var btnGridAdd: MaterialButton
    private lateinit var btnGridUpdate: MaterialButton
    private lateinit var btnGridAddSticker: MaterialButton
    private lateinit var btnGridDeleteMode: MaterialButton
    private lateinit var btnConfirmDelete: ImageButton
    private lateinit var installedIcon: ImageView
    private var currentPack: Pack? = null
    private var billingManager: BillingManager? = null
    private var isDeleteMode = false
    private val selectedIndices = mutableSetOf<Int>()
    private var progressDialog: AlertDialog? = null // Profesyonel yükleme dialoğu
    private var adapter: StickerAdapter? = null
    private var isPackReady = false // Çıkartmalar yüklendi mi kontrolü
    private lateinit var loadingContainer: LinearLayout
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
        // Eski onActivityResult mantığını buraya taşıyoruz
        onActivityResultInternal(req, res, data)
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_details)

        packId = intent.getStringExtra("id") ?: return finish()

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
                // 1. Önce statik cache'e bak (Anında yükleme için)
                var pack = StickerRepository.allPacksCache.find { it.id == packId }
                
                // 2. Eğer cache'de yoksa, lokal assets'ten hızlıca yüklemeyi dene (İlk açılışta gecikmeyi önlemek için)
                if (pack == null) {
                    pack = withContext(Dispatchers.IO) { Loader.get(this@DetailsActivity, packId) }
                }

                if (pack != null) {
                    setupUI(pack)
                } else {
                    // Cache'de ve lokalde yoksa, loading göster ve Firebase'den beklet
                    showLoadingState(true)
                }

                // 3. Arka planda veriyi tazele veya tam listeyi çek (Değişiklik varsa yansısın)
                val packs = withContext(Dispatchers.IO) { StickerRepository.loadPacks(this@DetailsActivity) }
                val updatedPack = packs.find { it.id == packId }
                
                if (updatedPack != null) {
                    // Eğer yeni veri geldiyse veya ilk kez bulduysak UI güncelle
                    if (updatedPack != pack) {
                        setupUI(updatedPack)
                    }
                } else if (pack == null) {
                    // Eğer hiçbir yerde bulunamadıysa hata ver
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
                // Veri geldiyse zaten setupUI loading'i kapatır, hata durumunda da kapatalım
                if (currentPack != null) showLoadingState(false)
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
        loadingContainer = findViewById(R.id.loadingContainer)

        // Başlangıçta içeriği göster, ama butonu hazır olana kadar bekleme (WhatsApp için arka planda inecek)
        btnAction.isEnabled = true 
        showLoadingState(false)

        val rv = findViewById<RecyclerView>(R.id.rv)
        rv.layoutManager = GridLayoutManager(this, 3)

        val hasAccess = PreferencesHelper.hasAccessToPremiumPack(this, pack.id)

        // Premium pakette ve erişim yoksa rastgele 3 çıkartmayı başa al
        val displayStickers = if (pack.isPremium && !hasAccess && pack.stickers.size > 3) {
            // Rastgele 3 çıkartma seç ve başa koy
            val shuffled = pack.stickers.shuffled()
            val first3 = shuffled.take(3)
            val rest = shuffled.drop(3)
            first3 + rest
        } else {
            pack.stickers
        }

        adapter = StickerAdapter(
            packId = pack.id,
            items = displayStickers,
            isPackPremium = pack.isPremium,
            hasAccess = hasAccess,
            storagePath = pack.storagePath,
            selectedPositions = selectedIndices, // Activity'deki set ile bağla
            onStickerClick = { sticker, _ ->
                // Tüm çıkartmalar önizlenebilir (kilit yok)
                showStickerPreview(sticker, false)
            },
            onStickerLongClick = { _, pos ->
                // Sadece custom paketlerde silmeye izin ver
                if (pack.id.startsWith("custom_")) {
                    toggleDeleteMode()
                }
            },
            onSelectionChanged = { count ->
                if (isDeleteMode) {
                    // Seçim sayısını başlıkta göster
                    findViewById<android.widget.TextView>(R.id.name).text = if (count > 0) "Seçilen: $count" else getString(R.string.selection_mode_title)
                }
            }
        )
        rv.adapter = adapter

        // PERFORMANS: Arka planda sticker'ları sessizce indir ve URL'leri kontrol et
        lifecycleScope.launch {
            // 1. Eğer URL'ler eksikse (Firestore'dan direkt geldiyse), URL'leri tek tek al ve anında göster
            if (pack.stickers.any { it.url.isEmpty() }) {
                val storagePath = pack.storagePath
                pack.stickers.forEachIndexed { index, sticker ->
                    launch(Dispatchers.IO) {
                        if (sticker.url.isEmpty()) {
                            sticker.url = StickerRepository.getDownloadUrl("$storagePath/${pack.id}/${sticker.file}")
                            withContext(Dispatchers.Main) {
                                // Sadece bu sticker'ın olduğu satırı güncelle (Verim için)
                                adapter?.notifyItemChanged(index)
                            }
                        }
                    }
                }
            }
            
            // 2. Cache indirme işlemini başlat (WhatsApp'a ekleme hızı için)
            downloadStickersToCache(pack, adapter!!) { success ->
                isPackReady = success
                adapter?.notifyDataSetChanged()
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
     * Loading state'i göster/gizle
     * @param isLoading true ise loading göster, false ise gizle
     */
    private fun showLoadingState(isLoading: Boolean) {
        if (!::loadingContainer.isInitialized || !::btnAction.isInitialized) return

        if (isLoading) {
            loadingContainer.visibility = View.VISIBLE
            btnAction.visibility = View.GONE
        } else {
            loadingContainer.visibility = View.GONE
            // btnAction visibility'si setupButtons tarafından kontrol edilecek
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

        // URL'ler varsa arka planda tümünü paralel indir
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Tüm çıkartmaları paralel olarak tek seferde indir
                    val jobs = pack.stickers.map { sticker ->
                        async {
                            StickerRepository.downloadStickerToCache(this@DetailsActivity, pack.id, sticker.file, pack.storagePath)
                        }
                    }
                    jobs.awaitAll()
                }
                isPackReady = true
                onComplete?.invoke(true)
            } catch (e: Exception) {
                isPackReady = false
                onComplete?.invoke(false)
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
            // Premium paket ve erişim yoksa
            btnAction.visibility = View.GONE
            customButtonsContainer.visibility = View.GONE
            premiumButtonsContainer.visibility = View.VISIBLE
            installedIcon.visibility = View.GONE

            // BillingManager'ı başlat
            billingManager = BillingManager(this) { isPurchased ->
                if (isPurchased) {
                    // Paketi satın alınmış olarak işaretle
                    PreferencesHelper.addPurchasedPack(this, pack.id)
                    Toast.makeText(this, R.string.premium_purchased, Toast.LENGTH_SHORT).show()
                    // Butonları güncelle
                    premiumButtonsContainer.visibility = View.GONE
                    btnAction.visibility = View.VISIBLE
                    updateButton()
                    // Adapter'ı yenile (blur kaldır)
                    recreate()
                }
            }

            // Fiyat butonu rengini tema rengine çek (Primary) - Kullanıcı Talebi
            findViewById<MaterialButton>(R.id.btnPrice).backgroundTintList = 
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary))
            
            // Fiyat metnini dinamik fiyattan çek (Eğer varsa)
            if (pack.priceTRY.isNotEmpty()) {
                val price = pack.priceTRY.trim()
                val formatted = if (price.contains("TL") || price.contains("₺") || price.contains("$") || price.contains("€")) {
                    price.replace("TL", "₺")
                } else {
                    "₺$price"
                }
                findViewById<MaterialButton>(R.id.btnPrice).text = formatted
            }
            
            // Premium Badge ve Price butonlarını PremiumActivity'ye bağla - Kullanıcı Talebi
            findViewById<MaterialButton>(R.id.btnPremiumBadge).setOnClickListener { 
                val intent = Intent(this, PremiumActivity::class.java)
                startActivity(intent)
            }
            findViewById<MaterialButton>(R.id.btnPrice).setOnClickListener { 
                val intent = Intent(this, PremiumActivity::class.java)
                startActivity(intent)
            }
        } else {
            // Normal paket
            btnAction.visibility = View.VISIBLE
            customButtonsContainer.visibility = View.GONE
            premiumButtonsContainer.visibility = View.GONE
            updateButton() // Update button text/icon based on installation status

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
        if (billingManager == null) {
            billingManager = BillingManager(this) { isPurchased ->
                if (isPurchased) {
                    currentPack?.let { pack ->
                        PreferencesHelper.addPurchasedPack(this, pack.id)
                    }
                    Toast.makeText(this, R.string.premium_purchased, Toast.LENGTH_SHORT).show()
                    recreate()
                }
            }
        }
        billingManager?.launchPurchase(this)
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
            val isCurrentlyWhitelisted = withContext(Dispatchers.IO) {
                WhitelistCheck.isWhitelisted(this@DetailsActivity, packId)
            }

            if (isCurrentlyWhitelisted) {
                removeFromWhatsApp()
            } else {
                addToWhatsApp(pack)
            }
        }
    }

    private fun showRemoveInstructionsDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.remove_from_whatsapp)
            .setMessage("WhatsApp'ın kısıtlamaları nedeniyle bir paketi uygulama içinden otomatik olarak silemiyoruz.\n\n" +
                    "Kaldırmak için:\n" +
                    "1. WhatsApp'ı açın\n" +
                    "2. Bir sohbete girin\n" +
                    "3. Çıkartma simgesine ve ardından '+' simgesine dokunun\n" +
                    "4. 'Çıkartmalarım' sekmesinden bu paketi bulun ve çöp kutusuna dokunun.")
            .setPositiveButton("WhatsApp'a Git") { _, _ ->
                try {
                    val intent = packageManager.getLaunchIntentForPackage("com.whatsapp")
                        ?: packageManager.getLaunchIntentForPackage("com.whatsapp.w4b")
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, R.string.whatsapp_not_installed, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateButton() {
        lifecycleScope.launch {
            // WhatsApp'tan gerçek durumu sorgula
            val isWhitelisted = withContext(Dispatchers.IO) {
                WhitelistCheck.isWhitelisted(this@DetailsActivity, packId)
            }

            // Senkronizasyon: Eğer WhatsApp'ta yoksa ama yerelde "yüklü" görünüyorsa yereli temizle
            if (!isWhitelisted && PreferencesHelper.isPackInstalled(this@DetailsActivity, packId)) {
                PreferencesHelper.removeInstalledPack(this@DetailsActivity, packId)
            } else if (isWhitelisted && !PreferencesHelper.isPackInstalled(this@DetailsActivity, packId)) {
                PreferencesHelper.addInstalledPack(this@DetailsActivity, packId)
            }

            if (isWhitelisted) {
                // HER ZAMAN KIRMIZI VE "KALDIR" (Kullanıcı Talebi)
                // Ayrıca yerel olarak "yüklü" değilse bile, WhatsApp'ta varsa yüklü işaretle
                if (!PreferencesHelper.isPackInstalled(this@DetailsActivity, packId)) {
                    PreferencesHelper.addInstalledPack(this@DetailsActivity, packId)
                }

                btnAction.text = getString(R.string.remove_from_whatsapp)
                btnAction.setIconResource(R.drawable.ic_delete)
                btnAction.setTextColor(ContextCompat.getColor(this@DetailsActivity, R.color.white))
                btnAction.setIconTintResource(R.color.white)
                btnAction.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@DetailsActivity, R.color.remove_red))
                installedIcon.visibility = View.VISIBLE
            } else {
                // EKLE MODU (YEŞİL)
                // Ancak, kullanıcı az önce eklediyse (PreferencesHelper.isPackInstalled = true) ve WhitelistCheck henüz false dönüyorsa (gecikme),
                // kullanıcıyı yanıltmamak için "Eklendi" veya geçici olarak "Kaldır" modunda tutabiliriz.
                // Şimdilik WhatsApp gerçeği yansıtmadığı sürece Yeşil dönüyoruz ama kullanıcının kafası karışmasın diye
                // eğer yerelde yüklü görünüyorsa ama WhatsApp'ta yoksa, senkronizasyon sorunu olabilir.
                // Yine de agresif davranıp yeşile dönmek en doğrusu, çünkü tekrar eklemesi gerekebilir.
                
                if (PreferencesHelper.isPackInstalled(this@DetailsActivity, packId)) {
                     // Yerelde yüklü ama WhatsApp'ta yok -> Yerel kaydı sil
                     PreferencesHelper.removeInstalledPack(this@DetailsActivity, packId)
                }

                btnAction.text = getString(R.string.add_to_whatsapp)
                btnAction.setIconResource(R.drawable.ic_add)
                btnAction.setTextColor(ContextCompat.getColor(this@DetailsActivity, R.color.white))
                btnAction.setIconTintResource(R.color.white)
                btnAction.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@DetailsActivity, R.color.accent))
                installedIcon.visibility = View.GONE
            }
        }
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
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
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
        // Eğer indirme henüz bitmediyse kullanıcıya uyarı veriyoruz
        if (!isPackReady) {
            Toast.makeText(this, R.string.stickers_preparing, Toast.LENGTH_SHORT).show()
            return
        }

        // WhatsApp kontrolü
        if (!isWhatsAppInstalled()) {
            Toast.makeText(this, R.string.whatsapp_not_installed, Toast.LENGTH_SHORT).show()
            showWhatsAppNotAvailableDialog()
            return
        }
        
        if (pack.id.startsWith("custom_") && !CustomStickerManager.hasCover(this, pack.id)) {
            Toast.makeText(this, "Lütfen önce bir kapak resmi ayarlayın", Toast.LENGTH_LONG).show()
            return
        }

        // PERFORMANS: Önce cache'de olup olmadığını kontrol et
        if (StickerRepository.isPackCached(this, pack)) {
            // Cache'de var, direkt WhatsApp'a gönder
            android.util.Log.d("DetailsActivity", "Pack already cached, sending directly")
            sendToWhatsApp(pack)
        } else if (pack.trayUrl.isNotEmpty() || pack.stickers.any { it.url.isNotEmpty() }) {
            downloadAndAddToWhatsApp(pack)
        } else {
            sendToWhatsApp(pack)
        }
    }

    private fun removeFromWhatsApp() {
        currentPack?.let { pack ->
            // Kullanıcı talebi üzerine bilgilendirme mesajı kaldırıldı, direkt WhatsApp'a yönlendiriliyor.
            launchWhatsAppRemove(pack)
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
        btnAction.isEnabled = false

        lifecycleScope.launch {
            try {
                android.util.Log.d("DetailsActivity", "Starting pack download: ${pack.id}")
                val storagePath = pack.storagePath

                // Tüm çıkartmaları paralel olarak indir (tray dahil)
                withContext(Dispatchers.IO) {
                    kotlinx.coroutines.coroutineScope {
                        // Tray'i indir
                        val trayJob = async {
                            StickerRepository.downloadStickerToCache(this@DetailsActivity, pack.id, pack.tray, storagePath)
                        }
                        // Sticker'ları paralel indir
                        val stickerJobs = pack.stickers.map { sticker ->
                            async {
                                StickerRepository.downloadStickerToCache(this@DetailsActivity, pack.id, sticker.file, storagePath)
                            }
                        }
                        trayJob.await()
                        stickerJobs.awaitAll()
                    }
                }

                android.util.Log.d("DetailsActivity", "Download success, sending to WhatsApp")
                sendToWhatsApp(pack)
            } catch (e: Exception) {
                android.util.Log.e("DetailsActivity", "Error during download/add: ${e.message}", e)
                Toast.makeText(this@DetailsActivity, R.string.stickers_load_failed, Toast.LENGTH_SHORT).show()
                btnAction.isEnabled = true
                updateButton()
            }
        }
    }

    private fun sendToWhatsApp(pack: Pack) {
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
            // WhatsApp yüklü ama açılamıyorsa (Hesap yok veya sürüm eski)
            Toast.makeText(this, R.string.whatsapp_not_available_title, Toast.LENGTH_SHORT).show()
            showWhatsAppNotAvailableDialog()
            btnAction.isEnabled = true
            updateButton()
        }
    }

    private fun onActivityResultInternal(req: Int, res: Int, data: Intent?) {
        // super.onActivityResult() çağrısına gerek yok, manuel yönetiyoruz

        btnAction.isEnabled = true

        when (req) {
            REQUEST_ADD -> {
                // WhatsApp'tan döndükten sonra gerçek durumu kontrol et
                lifecycleScope.launch {
                    val isWhitelisted = withContext(Dispatchers.IO) {
                        WhitelistCheck.isWhitelisted(this@DetailsActivity, packId)
                    }

                    val wasInstalled = PreferencesHelper.isPackInstalled(this@DetailsActivity, packId)

                    if (isWhitelisted && !wasInstalled) {
                        // Yeni eklendi
                        PreferencesHelper.addInstalledPack(this@DetailsActivity, packId)
                        Toast.makeText(this@DetailsActivity, R.string.pack_added, Toast.LENGTH_SHORT).show()

                        // İndirme sayısını artır (Firebase'e yaz)
                        currentPack?.let { pack ->
                            StickerRepository.incrementDownloadCount(pack.id, pack.isPremium)
                        }

                        // Sticker ekleme sayacını artır
                        val count = PreferencesHelper.incrementStickersAddedCount(this@DetailsActivity)

                        if (!PreferencesHelper.isPremium(this@DetailsActivity)) {
                            if (count % 4 == 0 && !PreferencesHelper.wasPremiumPromoShownForCount(this@DetailsActivity, count)) {
                                PreferencesHelper.markPremiumPromoShown(this@DetailsActivity, count)
                                showPremiumPromoDialog()
                            } else {
                                AdManager.showInterstitial(this@DetailsActivity)
                            }
                        }
                    } else if (!isWhitelisted && wasInstalled) {
                        // Kaldırıldı (kullanıcı WhatsApp'tan kaldırmış olabilir)
                        PreferencesHelper.removeInstalledPack(this@DetailsActivity, packId)
                        showThemedSnackbar(getString(R.string.pack_removed_from_whatsapp))
                    }

                    updateButton()
                }
            }
            REQUEST_REMOVE -> {
                // WhatsApp'tan kaldırma işlemi sonrası kontrol
                lifecycleScope.launch {
                    val isStillWhitelisted = withContext(Dispatchers.IO) {
                        WhitelistCheck.isWhitelisted(this@DetailsActivity, packId)
                    }

                    if (isStillWhitelisted) {
                        // Eğer paket hala yüklüyse ve kullanıcı işlemi tamamladıysa hata var demektir
                        // Veya kullanıcı iptal etti. Her iki durumda da manuel kaldırma bilgisini içeren uyarıyı gösterelim.
                        if (pendingDeletePackId == null) {
                            showPackStillInstalledWarning()
                        }
                    } else {
                        // Başarıyla kaldırıldı
                        PreferencesHelper.removeInstalledPack(this@DetailsActivity, packId)
                        
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
                    updateButton()
                }
            }
            REQUEST_ADD_STICKER -> {
                // StickerMaker'dan dönüldü, eğer paket yüklüyse WhatsApp'ı zorla güncelle
                if (PreferencesHelper.isPackInstalled(this, packId)) {
                    syncPackDataWithProgress(packId, "Yeni çıkartma eklendi, WhatsApp güncelleniyor...")
                }
                // onResume zaten loadPackFromFirebase() çağıracak
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
        billingManager?.destroy()
    }

    companion object {
        private const val REQUEST_ADD = 200
        private const val REQUEST_REMOVE = 201
        private const val REQUEST_ADD_STICKER = 202
    }
}
