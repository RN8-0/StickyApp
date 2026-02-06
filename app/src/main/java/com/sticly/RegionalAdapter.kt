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
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.signature.ObjectKey
import android.graphics.drawable.Drawable
import com.google.android.material.button.MaterialButton
import java.io.File

class RegionalAdapter(
    private var packs: List<Pack>,
    private val onClick: (Pack) -> Unit,
    private val onAddClick: (Pack) -> Unit
) : RecyclerView.Adapter<RegionalAdapter.VH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_popular_card, parent, false)
        // Kart görünümü için genişlik ayarı (~%85-88)
        val displayMetrics = parent.context.resources.displayMetrics
        val parentWidth = if (parent.width > 0) parent.width else displayMetrics.widthPixels
        v.layoutParams.width = (parentWidth * 0.88).toInt()
        return VH(v)
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val packTop: View = v // Root view is the card itself
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val pack = packs[position]
        bindPack(holder.packTop, pack, position + 1)
    }

    override fun getItemCount(): Int = packs.size

    fun updateData(newPacks: List<Pack>) {
        packs = newPacks
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
        val crownIcon: ImageView = view.findViewById(R.id.crownIcon)
        val premiumContainer: View = view.findViewById(R.id.premiumContainer)
        // btnFavoriteNew removed/hidden in this layout context

        // Basit Bilgiler
        name.text = pack.localizedName
        pub.text = pack.pub
        rankNumber.text = rank.toString()
        rankNumber.visibility = View.VISIBLE

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
            btnAdd.iconTint = androidx.core.content.ContextCompat.getColorStateList(context, R.color.share_blue)
            btnAdd.background = null
            btnAdd.backgroundTintList = null
            btnAdd.iconPadding = 0
            val params = btnAdd.layoutParams
            params.width = (36 * context.resources.displayMetrics.density).toInt()
            params.height = (36 * context.resources.displayMetrics.density).toInt()
            btnAdd.layoutParams = params
        } else {
            btnAdd.text = context.getString(R.string.btn_add_short)
            btnAdd.setIconResource(if (pack.isPremium && !hasAccess) R.drawable.ic_gem else R.drawable.ic_whatsapp_small)
            val colorRes = if (pack.isPremium && !hasAccess) R.color.premium_gold else R.color.accent
            val color = androidx.core.content.ContextCompat.getColorStateList(context, colorRes)
            
            btnAdd.setTextColor(color)
            btnAdd.iconTint = color
            btnAdd.background = null
            btnAdd.backgroundTintList = null
            
            btnAdd.iconPadding = (6 * context.resources.displayMetrics.density).toInt()
            val params = btnAdd.layoutParams
            params.width = ViewGroup.LayoutParams.WRAP_CONTENT
            params.height = (32 * context.resources.displayMetrics.density).toInt()
            btnAdd.layoutParams = params
        }

        view.setOnClickListener { onClick(pack) }
        btnAdd.setOnClickListener { onAddClick(pack) }

        // Stickerlar (5 ADET - Daha büyük ve net)
        loadStickerPreviews(stickerPreviewContainer, pack)
    }

    private fun loadStickerPreviews(container: LinearLayout, pack: Pack) {
        val context = container.context
        container.removeAllViews()

        val displayMetrics = context.resources.displayMetrics
        val stickerSize = (64 * displayMetrics.density).toInt()
        val stickerMargin = (8 * displayMetrics.density).toInt()
        val glideOverrideSize = (200 * displayMetrics.density).toInt()

        val stickersToShow = pack.stickers.take(5)

        // Resim yüklendiğinde arka planı kaldıran listener
        val clearBgListener = object : RequestListener<Drawable> {
            override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean = false
            override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                (target as? com.bumptech.glide.request.target.ImageViewTarget<*>)?.view?.background = null
                return false
            }
        }

        stickersToShow.forEachIndexed { index, sticker ->
            val previewView = ImageView(context).apply {
                val params = LinearLayout.LayoutParams(stickerSize, stickerSize)
                if (index < stickersToShow.size - 1) {
                    params.marginEnd = stickerMargin
                }
                layoutParams = params
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundResource(R.drawable.ic_sticker_placeholder)
            }
            container.addView(previewView)

            // URL belirle
            var urlToLoad = sticker.url
            if (urlToLoad.isEmpty() && pack.storagePath.isNotEmpty()) {
                urlToLoad = StickerRepository.getStickerDirectUrl(pack.id, sticker.file, pack.storagePath)
            }

            when {
                urlToLoad.isNotEmpty() -> {
                    Glide.with(context)
                        .load(urlToLoad)
                        .override(glideOverrideSize)
                        .priority(Priority.IMMEDIATE) // En yüksek öncelik - popüler paketler önce yüklensin
                        .diskCacheStrategy(DiskCacheStrategy.DATA)
                        .listener(clearBgListener)
                        .into(previewView)
                }
                else -> {
                    val cachedSticker = StickerRepository.getCachedStickerPath(context, pack.id, sticker.file)
                    if (cachedSticker.exists() && cachedSticker.length() > 0) {
                        Glide.with(context)
                            .load(cachedSticker)
                            .override(glideOverrideSize)
                            .priority(Priority.IMMEDIATE)
                            .diskCacheStrategy(DiskCacheStrategy.DATA)
                            .signature(ObjectKey(cachedSticker.lastModified()))
                            .listener(clearBgListener)
                            .into(previewView)
                    } else {
                        val assetPath = "file:///android_asset/${pack.id}/${sticker.file}"
                        Glide.with(context)
                            .load(android.net.Uri.parse(assetPath))
                            .override(glideOverrideSize)
                            .priority(Priority.IMMEDIATE)
                            .diskCacheStrategy(DiskCacheStrategy.DATA)
                            .listener(clearBgListener)
                            .into(previewView)
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
