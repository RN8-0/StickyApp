package com.sticly

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.ObjectKey
import com.google.android.material.button.MaterialButton
import java.io.File

class RegionalAdapter(
    private var pages: List<Pair<Pack, Pack?>>,
    private val onClick: (Pack) -> Unit,
    private val onAddClick: (Pack) -> Unit
) : RecyclerView.Adapter<RegionalAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val packTop: View = v.findViewById(R.id.packTop)
        val packBottom: View = v.findViewById(R.id.packBottom)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_regional_page, parent, false)
        // Ekranın %85'ini kaplasın ki sağdakinin ucu gözüksün (Sticker.ly tarzı)
        val displayMetrics = parent.context.resources.displayMetrics
        val parentWidth = if (parent.width > 0) parent.width else displayMetrics.widthPixels
        v.layoutParams.width = (parentWidth * 0.85).toInt()
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val (top, bottom) = pages[position]
        
        bindPack(holder.packTop, top, position * 2 + 1)
        
        if (bottom != null) {
            holder.packBottom.visibility = View.VISIBLE
            bindPack(holder.packBottom, bottom, position * 2 + 2)
        } else {
            holder.packBottom.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = pages.size

    fun updateData(newPages: List<Pair<Pack, Pack?>>) {
        pages = newPages
        notifyDataSetChanged()
    }

    private fun bindPack(view: View, pack: Pack, rank: Int) {
        val context = view.context
        
        val name: TextView = view.findViewById(R.id.name)
        val pub: TextView = view.findViewById(R.id.pub)
        val btnAdd: MaterialButton = view.findViewById(R.id.btnAdd)
        val rankNumber: TextView = view.findViewById(R.id.rankNumber)
        val stickerPreviewContainer: LinearLayout = view.findViewById(R.id.stickerPreviewContainer)
        val checkIcon: ImageView = view.findViewById(R.id.checkIcon)
        val installedBadge: TextView = view.findViewById(R.id.installedBadge)
        val downloadCount: TextView = view.findViewById(R.id.downloadCount)
        val btnFavoriteNew: ImageView = view.findViewById(R.id.btnFavoriteNew)
        val crownIcon: ImageView = view.findViewById(R.id.crownIcon)
        val premiumContainer: View = view.findViewById(R.id.premiumContainer)

        // Basit Bilgiler
        name.text = pack.localizedName
        pub.text = pack.pub
        rankNumber.text = rank.toString()
        rankNumber.visibility = View.VISIBLE
        btnFavoriteNew.visibility = View.GONE

        // İndirme sayısı
        val displayDownloadCount = pack.fakeDownloadBase + pack.downloadCount
        downloadCount.text = if (displayDownloadCount > 0) {
            formatDownloadValue(displayDownloadCount)
        } else {
            context.getString(R.string.sticker_count, pack.stickers.size)
        }

        // Durumlar
        val hasAccess = PreferencesHelper.hasAccessToPremiumPack(context, pack.id)
        val isInstalled = PreferencesHelper.isPackInstalled(context, pack.id) && (!pack.isPremium || hasAccess)
        
        checkIcon.visibility = if (isInstalled) View.VISIBLE else View.GONE
        installedBadge.visibility = if (isInstalled) View.VISIBLE else View.GONE
        crownIcon.visibility = if (pack.isPremium) View.VISIBLE else View.GONE
        premiumContainer.visibility = if (pack.isPremium && !hasAccess && !isInstalled) View.VISIBLE else View.GONE

        // Buton Ayarı
        btnAdd.visibility = View.VISIBLE
        if (isInstalled) {
            btnAdd.text = ""
            btnAdd.setIconResource(R.drawable.ic_share)
            btnAdd.iconTint = androidx.core.content.ContextCompat.getColorStateList(context, R.color.white)
            btnAdd.background = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.bg_gradient_share)
            btnAdd.backgroundTintList = null
            btnAdd.iconPadding = 0
            val params = btnAdd.layoutParams
            params.width = (36 * context.resources.displayMetrics.density).toInt()
            params.height = (36 * context.resources.displayMetrics.density).toInt()
            btnAdd.layoutParams = params
        } else {
            btnAdd.text = context.getString(R.string.btn_add_short)
            btnAdd.setIconResource(if (pack.isPremium && !hasAccess) R.drawable.ic_gem else R.drawable.ic_whatsapp_small)
            btnAdd.background = null
            btnAdd.backgroundTintList = androidx.core.content.ContextCompat.getColorStateList(context, 
                if (pack.isPremium && !hasAccess) R.color.premium_gold else R.color.accent)
            btnAdd.iconTint = androidx.core.content.ContextCompat.getColorStateList(context, R.color.white)
            val params = btnAdd.layoutParams
            params.width = ViewGroup.LayoutParams.WRAP_CONTENT
            params.height = (30 * context.resources.displayMetrics.density).toInt()
            btnAdd.layoutParams = params
        }

        view.setOnClickListener { onClick(pack) }
        btnAdd.setOnClickListener { onAddClick(pack) }

        // Stickerlar (7 ADET - Dinamik ve uyumlu)
        loadStickerPreviews(stickerPreviewContainer, pack)
    }

    private fun loadStickerPreviews(container: LinearLayout, pack: Pack) {
        val context = container.context
        container.removeAllViews()
        
        val displayMetrics = context.resources.displayMetrics
        // Çıkartma boyutunu 60dp yapalım ki 7 tane rahat sığsın (toplam ~420dp + margin)
        val stickerSize = (60 * displayMetrics.density).toInt() 
        val stickerMargin = (6 * displayMetrics.density).toInt()
        val glideOverrideSize = (160 * displayMetrics.density).toInt()

        val stickersToShow = pack.stickers.take(7)

        stickersToShow.forEachIndexed { index, sticker ->
            val previewView = ImageView(context).apply {
                val params = LinearLayout.LayoutParams(stickerSize, stickerSize)
                if (index < stickersToShow.size - 1) {
                    params.marginEnd = stickerMargin
                }
                layoutParams = params
                scaleType = ImageView.ScaleType.FIT_CENTER
                // Arka plandaki grimsi kareyi kaldırıp temiz bir görünüm veriyoruz
                // setBackgroundResource(R.drawable.sticker_preview_bg) 
            }
            container.addView(previewView)

            val glideRequest = Glide.with(context)
                .asBitmap()
                .override(glideOverrideSize)
                .dontAnimate()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.ic_sticker_placeholder)
                .error(R.drawable.ic_sticker_placeholder)

            when {
                sticker.url.isNotEmpty() -> {
                    glideRequest.load(sticker.url).into(previewView)
                }
                else -> {
                    val cachedSticker = StickerRepository.getCachedStickerPath(context, pack.id, sticker.file)
                    if (cachedSticker.exists() && cachedSticker.length() > 0) {
                        glideRequest.load(cachedSticker)
                            .signature(ObjectKey(cachedSticker.lastModified()))
                            .into(previewView)
                    } else {
                        // Assets Yüklemesi - Glide ile daha güvenli
                        val assetPath = "file:///android_asset/${pack.id}/${sticker.file}"
                        glideRequest.load(assetPath).into(previewView)
                    }
                }
            }
        }
    }

    private fun formatDownloadValue(count: Int): String {
        return when {
            count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
            count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
            else -> count.toString()
        }
    }
}
