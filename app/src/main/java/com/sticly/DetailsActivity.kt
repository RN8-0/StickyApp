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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class DetailsActivity : AppCompatActivity() {

    private lateinit var packId: String
    private lateinit var btnAction: MaterialButton
    private lateinit var installedIcon: ImageView
    private var currentPack: Pack? = null

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_details)

        packId = intent.getStringExtra("id") ?: return finish()

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        var pack = Loader.get(this, packId)

        if (pack == null) {
            Toast.makeText(this, R.string.pack_loading, Toast.LENGTH_SHORT).show()
            loadPackFromFirebase()
            return
        }

        setupUI(pack)
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
                val pack = packs.find { it.id == packId }
                if (pack != null) {
                    setupUI(pack)
                } else {
                    Toast.makeText(this@DetailsActivity, R.string.pack_not_found, Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                Toast.makeText(this@DetailsActivity, R.string.pack_load_failed, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun setupUI(pack: Pack) {
        currentPack = pack

        findViewById<android.widget.TextView>(R.id.name).text = pack.name

        btnAction = findViewById(R.id.btnAction)
        installedIcon = findViewById(R.id.installedIcon)

        val rv = findViewById<RecyclerView>(R.id.rv)
        rv.layoutManager = GridLayoutManager(this, 3)

        val isPremiumUser = PreferencesHelper.isPremium(this)
        val adapter = StickerAdapter(pack.id, pack.stickers, pack.isPremium, isPremiumUser) { sticker, position ->
            if (pack.isPremium && !isPremiumUser && position >= 3) {
                showPremiumRequiredDialog()
            } else {
                showStickerPreview(sticker)
            }
        }
        rv.adapter = adapter

        if (pack.trayUrl.isNotEmpty() || pack.stickers.any { it.url.isNotEmpty() }) {
            lifecycleScope.launch {
                StickerRepository.downloadPackToCache(this@DetailsActivity, pack)
                adapter.notifyDataSetChanged()
            }
        }

        // Butonu güncelle
        updateButton()

        // Premium paket kontrolü
        if (pack.isPremium && !isPremiumUser) {
            btnAction.text = getString(R.string.unlock_premium)
            btnAction.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.premium_gold))
            btnAction.setOnClickListener {
                showPremiumRequiredDialog()
            }
        } else {
            // Normal buton - her tıklamada güncel durumu kontrol et
            btnAction.setOnClickListener {
                handleButtonClick(pack)
            }
        }
    }

    private fun handleButtonClick(pack: Pack) {
        // Her zaman güncel durumu kontrol et
        val isCurrentlyInstalled = PreferencesHelper.isPackInstalled(this, packId)

        if (isCurrentlyInstalled) {
            // Kaldır - WhatsApp'a intent gönder
            removeFromWhatsApp(pack)
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

    private fun showStickerPreview(sticker: Sticker) {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.parseColor("#CC000000")))
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        }

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_sticker_preview, null)
        val imageView = view.findViewById<ImageView>(R.id.previewImage)

        dialog.setContentView(view)

        val cachedFile = StickerRepository.getCachedStickerPath(this, packId, sticker.file)
        when {
            cachedFile.exists() -> Glide.with(this).load(cachedFile).into(imageView)
            sticker.url.isNotEmpty() -> Glide.with(this).load(sticker.url).into(imageView)
            else -> {
                try {
                    val path = "$packId/${sticker.file}"
                    val stream = assets.open(path)
                    val bitmap = BitmapFactory.decodeStream(stream)
                    stream.close()
                    imageView.setImageBitmap(bitmap)
                } catch (e: Exception) {
                    imageView.setImageResource(R.drawable.ic_logo_white)
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
                .withEndAction { dialog.dismiss() }
                .start()
        }

        dialog.show()
    }

    private fun showPremiumRequiredDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.premium_required_title)
            .setMessage(R.string.premium_required_message)
            .setPositiveButton(R.string.buy_premium) { _, _ ->
                val billingManager = BillingManager(this) { isPurchased ->
                    if (isPurchased) {
                        Toast.makeText(this, R.string.premium_purchased, Toast.LENGTH_SHORT).show()
                        recreate()
                    }
                }
                billingManager.launchPurchase(this)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
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

    private fun removeFromWhatsApp(pack: Pack) {
        // Kaldırma onayı - WhatsApp ekranı açılmadan direkt kaldır
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.remove_pack_title)
            .setMessage(R.string.remove_pack_message)
            .setPositiveButton(R.string.confirm_remove) { _, _ ->
                // Sadece lokal durumu güncelle - WhatsApp'ı AÇMA!
                PreferencesHelper.removeInstalledPack(this, packId)
                updateButton()
                // Görsel geri bildirim: Buton anında yeşile döner
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
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

        if (req == REQUEST_ADD) {
            if (res == Activity.RESULT_OK) {
                PreferencesHelper.addInstalledPack(this, packId)
                updateButton()
                Toast.makeText(this, R.string.pack_added, Toast.LENGTH_SHORT).show()

                if (!PreferencesHelper.isPremium(this)) {
                    AdManager.showInterstitial(this)
                }
            } else {
                updateButton()
            }
        }
    }

    companion object {
        private const val REQUEST_ADD = 200
    }
}
