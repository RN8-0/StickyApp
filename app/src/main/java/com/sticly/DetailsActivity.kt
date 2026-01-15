package com.sticly

import android.app.Activity
import android.app.Dialog
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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DetailsActivity : AppCompatActivity() {

    private lateinit var packId: String
    private lateinit var btnAction: MaterialButton
    private lateinit var premiumButtonsContainer: LinearLayout
    private lateinit var btnPremiumBadge: MaterialButton
    private lateinit var btnPrice: MaterialButton
    private lateinit var installedIcon: ImageView
    private var currentPack: Pack? = null
    private var billingManager: BillingManager? = null

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_details)

        packId = intent.getStringExtra("id") ?: return finish()

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Her zaman Firebase'den yükle (URL'ler için)
        loadPackFromFirebase()
    }

    override fun onResume() {
        super.onResume()
        // WhatsApp'tan geri döndüğümüzde buton durumunu güncelle
        if (::btnAction.isInitialized) {
            updateButton()
        }
    }

    private fun loadPackFromFirebase() {
        lifecycleScope.launch {
            try {
                val packs = StickerRepository.loadPacks(this@DetailsActivity)
                android.util.Log.d("DetailsActivity", "Loaded ${packs.size} packs")
                packs.forEach { p ->
                    android.util.Log.d("DetailsActivity", "Pack: ${p.id}, isPremium: ${p.isPremium}, stickers: ${p.stickers.size}, hasUrls: ${p.stickers.any { it.url.isNotEmpty() }}")
                }
                val pack = packs.find { it.id == packId }
                if (pack != null) {
                    android.util.Log.d("DetailsActivity", "Found pack: ${pack.id}, stickers: ${pack.stickers.size}")
                    pack.stickers.take(3).forEach { s ->
                        android.util.Log.d("DetailsActivity", "Sticker: ${s.file}, url: ${s.url.take(50)}...")
                    }
                    setupUI(pack)
                } else {
                    Toast.makeText(this@DetailsActivity, R.string.pack_not_found, Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                android.util.Log.e("DetailsActivity", "Error: ${e.message}", e)
                Toast.makeText(this@DetailsActivity, R.string.pack_load_failed, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun setupUI(pack: Pack) {
        currentPack = pack

        findViewById<android.widget.TextView>(R.id.name).text = pack.name

        btnAction = findViewById(R.id.btnAction)
        premiumButtonsContainer = findViewById(R.id.premiumButtonsContainer)
        btnPremiumBadge = findViewById(R.id.btnPremiumBadge)
        btnPrice = findViewById(R.id.btnPrice)
        installedIcon = findViewById(R.id.installedIcon)

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

        val adapter = StickerAdapter(
            packId = pack.id,
            items = displayStickers,
            isPackPremium = pack.isPremium,
            hasAccess = hasAccess,
            storagePath = pack.storagePath
        ) { sticker, _ ->
            // Tüm çıkartmalar önizlenebilir (kilit yok)
            showStickerPreview(sticker, false)
        }
        rv.adapter = adapter

        // Çıkartmaları arka planda cache'e indir
        downloadStickersToCache(pack, adapter)

        // Butonları ayarla
        setupButtons(pack, hasAccess)
    }

    private fun downloadStickersToCache(pack: Pack, adapter: StickerAdapter) {
        // URL'ler varsa arka planda cache'e indir
        if (pack.stickers.any { it.url.isNotEmpty() }) {
            lifecycleScope.launch {
                // Önce ilk 6 çıkartmayı hızlıca indir (görünen alan için)
                withContext(Dispatchers.IO) {
                    StickerRepository.downloadFirstStickers(this@DetailsActivity, pack, 6)
                }
                // İlk 6 indirildikten sonra adapter'ı güncelle
                adapter.notifyDataSetChanged()

                // Sonra geri kalanları arka planda indir
                withContext(Dispatchers.IO) {
                    StickerRepository.downloadPackToCache(this@DetailsActivity, pack)
                }
                // Tamamlandığında tekrar güncelle
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun setupButtons(pack: Pack, hasAccess: Boolean) {
        // Premium paket ve erişim yoksa
        if (pack.isPremium && !hasAccess) {
            // Normal butonu gizle, premium butonları göster
            btnAction.visibility = View.GONE
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

            // Her iki butona da tıklandığında satın alma başlat
            btnPremiumBadge.setOnClickListener { launchPremiumPurchase() }
            btnPrice.setOnClickListener { launchPremiumPurchase() }
        } else {
            // Normal butonları göster
            btnAction.visibility = View.VISIBLE
            premiumButtonsContainer.visibility = View.GONE
            updateButton()

            btnAction.setOnClickListener {
                handleButtonClick(pack)
            }
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

    private fun handleButtonClick(pack: Pack) {
        // Her zaman güncel durumu kontrol et
        val isCurrentlyInstalled = PreferencesHelper.isPackInstalled(this, packId)

        if (isCurrentlyInstalled) {
            // Kaldır
            removeFromWhatsApp()
        } else {
            // Ekle
            addToWhatsApp(pack)
        }
    }

    private fun updateButton() {
        // Her zaman PreferencesHelper'dan güncel durumu al
        val isInstalled = PreferencesHelper.isPackInstalled(this, packId)

        if (isInstalled) {
            btnAction.text = getString(R.string.remove_from_whatsapp)
            btnAction.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.remove_red))
            installedIcon.visibility = View.VISIBLE
        } else {
            btnAction.text = getString(R.string.add_to_whatsapp)
            btnAction.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary))
            installedIcon.visibility = View.GONE
        }
    }

    private fun showStickerPreview(sticker: Sticker, isLocked: Boolean = false) {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.parseColor("#CC000000")))
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
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
                    .apply(blurTransform)
                    .into(imageView)
            }
            sticker.url.isNotEmpty() -> {
                Glide.with(this)
                    .load(sticker.url)
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
                    imageView.setImageResource(R.drawable.ic_sticker_placeholder)
                }
            }
        }

        view.scaleX = 0.7f
        view.scaleY = 0.7f
        view.alpha = 0f

        view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(250)
            .setInterpolator(OvershootInterpolator(1.1f))
            .start()

        view.setOnClickListener {
            view.animate()
                .scaleX(0.7f)
                .scaleY(0.7f)
                .alpha(0f)
                .setDuration(200)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction {
                    dialog.dismiss()
                    // Kilitli ise premium satın alma göster
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
        // WhatsApp kontrolü
        if (!isWhatsAppInstalled()) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.whatsapp_not_installed_title)
                .setMessage(R.string.whatsapp_not_installed_message)
                .setPositiveButton(R.string.play_store) { _, _ ->
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=com.whatsapp")))
                    } catch (e: Exception) {
                        startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=com.whatsapp")))
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }

        if (pack.trayUrl.isNotEmpty() || pack.stickers.any { it.url.isNotEmpty() }) {
            downloadAndAddToWhatsApp(pack)
        } else {
            sendToWhatsApp(pack)
        }
    }

    private fun removeFromWhatsApp() {
        // Direkt WhatsApp'ı aç - kullanıcı oradan kaldıracak
        currentPack?.let { pack ->
            try {
                val i = Intent().apply {
                    action = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
                    putExtra("sticker_pack_id", pack.id)
                    putExtra("sticker_pack_authority", "$packageName.stickers")
                    putExtra("sticker_pack_name", pack.name)
                }
                startActivityForResult(i, REQUEST_REMOVE)
            } catch (e: Exception) {
                Toast.makeText(this, R.string.whatsapp_not_installed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun downloadAndAddToWhatsApp(pack: Pack) {
        btnAction.isEnabled = false
        btnAction.text = getString(R.string.downloading)

        lifecycleScope.launch {
            try {
                val success = StickerRepository.downloadPackToCache(this@DetailsActivity, pack)
                if (success) {
                    sendToWhatsApp(pack)
                } else {
                    Toast.makeText(this@DetailsActivity, R.string.download_failed, Toast.LENGTH_SHORT).show()
                    btnAction.isEnabled = true
                    updateButton()
                }
            } catch (e: Exception) {
                Toast.makeText(this@DetailsActivity, getString(R.string.error, e.message), Toast.LENGTH_SHORT).show()
                btnAction.isEnabled = true
                updateButton()
            }
        }
    }

    private fun sendToWhatsApp(pack: Pack) {
        try {
            val i = Intent().apply {
                action = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
                putExtra("sticker_pack_id", pack.id)
                putExtra("sticker_pack_authority", "$packageName.stickers")
                putExtra("sticker_pack_name", pack.name)
            }
            startActivityForResult(i, REQUEST_ADD)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.whatsapp_not_installed, Toast.LENGTH_SHORT).show()
            btnAction.isEnabled = true
            updateButton()
        }
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        btnAction.isEnabled = true

        when (req) {
            REQUEST_ADD -> {
                if (res == Activity.RESULT_OK) {
                    PreferencesHelper.addInstalledPack(this, packId)
                    updateButton()
                    Toast.makeText(this, R.string.pack_added, Toast.LENGTH_SHORT).show()

                    // Sticker ekleme sayacını artır
                    val count = PreferencesHelper.incrementStickersAddedCount(this)

                    if (!PreferencesHelper.isPremium(this)) {
                        // Her 4 sticker'da bir premium promo göster
                        if (count % 4 == 0 && !PreferencesHelper.wasPremiumPromoShownForCount(this, count)) {
                            PreferencesHelper.markPremiumPromoShown(this, count)
                            showPremiumPromoDialog()
                        } else {
                            AdManager.showInterstitial(this)
                        }
                    }
                } else {
                    updateButton()
                }
            }
            REQUEST_REMOVE -> {
                // WhatsApp'tan döndü - kullanıcı kaldırdıysa RESULT_OK olmaz
                // Kullanıcı kaldırdı varsayalım
                PreferencesHelper.removeInstalledPack(this, packId)
                updateButton()
                Toast.makeText(this, R.string.pack_removed_from_whatsapp, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showPremiumPromoDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_premium_promo, null)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
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

    override fun onDestroy() {
        super.onDestroy()
        billingManager?.destroy()
    }

    companion object {
        private const val REQUEST_ADD = 200
        private const val REQUEST_REMOVE = 201
    }
}
