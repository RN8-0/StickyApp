package com.sticly

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
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

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return packs.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()
    }

    private var density = 0f
    private var btn36Px = 0
    private var btn32Px = 0
    private var iconPad6Px = 0
    private var stickerSizePx = 0
    private var stickerMarginPx = 0

    // Reusable listener to avoid allocation in onBind
    private val clearBgListener = object : RequestListener<Drawable> {
        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean = false
        override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>, dataSource: DataSource, isFirstResource: Boolean): Boolean {
            (target as? com.bumptech.glide.request.target.ImageViewTarget<*>)?.view?.background = null
            // A drawable restored from the memory cache can come back paused.
            (resource as? android.graphics.drawable.Animatable)?.let { if (!it.isRunning) it.start() }
            return false
        }
    }

    private fun ensureDensity(context: android.content.Context) {
        if (density > 0f) return
        density = context.resources.displayMetrics.density
        btn36Px = (36 * density).toInt()
        btn32Px = (32 * density).toInt()
        iconPad6Px = (6 * density).toInt()
        stickerSizePx = (52 * density).toInt()
        stickerMarginPx = (6 * density).toInt()
    }

    override fun getItemCount(): Int = packs.size

    fun getRealCount(): Int = packs.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_popular_card, parent, false)
        val displayMetrics = parent.context.resources.displayMetrics
        val parentWidth = if (parent.width > 0) parent.width else displayMetrics.widthPixels
        v.layoutParams.width = (parentWidth * 0.72).toInt()
        val vh = VH(v)
        // Pre-allocate 4 ImageViews in container to avoid creating them in onBind
        ensureDensity(parent.context)
        for (i in 0 until 4) {
            val previewView = ImageView(parent.context).apply {
                val params = LinearLayout.LayoutParams(stickerSizePx, stickerSizePx)
                params.marginEnd = if (i == 3) 0 else stickerMarginPx
                layoutParams = params
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            vh.stickerPreviewContainer.addView(previewView)
        }
        return vh
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.name)
        val pub: TextView = v.findViewById(R.id.pub)
        val btnAdd: MaterialButton = v.findViewById(R.id.btnAdd)
        val rankNumber: TextView = v.findViewById(R.id.rankNumber)
        val stickerPreviewContainer: LinearLayout = v.findViewById(R.id.stickerPreviewContainer)
        val checkIcon: ImageView = v.findViewById(R.id.checkIcon)
        val installedBadge: TextView = v.findViewById(R.id.installedBadge)
        val downloadCount: TextView = v.findViewById(R.id.downloadCount)
        val crownIcon: ImageView = v.findViewById(R.id.crownIcon)
        val premiumContainer: View = v.findViewById(R.id.premiumContainer)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        if (packs.isEmpty()) return
        ensureDensity(holder.itemView.context)
        val pack = packs[position]
        bindPack(holder, pack, position)
    }

    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        val container = holder.stickerPreviewContainer
        try {
            val glide = Glide.with(holder.itemView.context)
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)
                if (child is ImageView) {
                    glide.clear(child)
                    child.setImageDrawable(null)
                }
            }
        } catch (_: Exception) { }
    }

    fun updateData(newPacks: List<Pack>) {
        val oldPacks = packs
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldPacks.size
            override fun getNewListSize() = newPacks.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) = oldPacks[oldPos].id == newPacks[newPos].id
            override fun areContentsTheSame(oldPos: Int, newPos: Int) = oldPacks[oldPos].id == newPacks[newPos].id && oldPacks[oldPos].version == newPacks[newPos].version
        })
        packs = newPacks
        diffResult.dispatchUpdatesTo(this)
    }

    private fun bindPack(h: VH, pack: Pack, position: Int) {
        val context = h.itemView.context
        
        h.name.text = pack.localizedName
        h.pub.text = pack.pub
        
        // Show rank number (1-based position)
        h.rankNumber.text = "${position + 1}"
        h.rankNumber.visibility = View.VISIBLE

        // İndirme sayısı
        h.downloadCount.text = if (pack.downloadCount > 0) {
            "↓ ${formatDownloadValue(pack.downloadCount)}"
        } else {
            context.getString(R.string.sticker_count, pack.stickers.size)
        }

        // Durumlar
        val isInstalled = PreferencesHelper.isPackInstalled(context, pack.id)
        
        h.checkIcon.visibility = if (isInstalled) View.VISIBLE else View.GONE
        h.installedBadge.visibility = if (isInstalled) View.VISIBLE else View.GONE
        h.crownIcon.visibility = if (pack.isPremium) View.VISIBLE else View.GONE
        h.premiumContainer.visibility = View.GONE

        // Buton Ayarı
        if (isInstalled) {
            h.btnAdd.visibility = View.GONE
        } else {
            h.btnAdd.visibility = View.VISIBLE
            h.btnAdd.text = context.getString(R.string.btn_add_short)
            h.btnAdd.setIconResource(R.drawable.ic_whatsapp_small)
            h.btnAdd.iconTint = android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt())
            h.btnAdd.setTextColor(0xFFFFFFFF.toInt())
            h.btnAdd.backgroundTintList = androidx.core.content.ContextCompat.getColorStateList(context, R.color.accent)
            h.btnAdd.iconPadding = (3 * density).toInt()
            val params = h.btnAdd.layoutParams
            params.width = ViewGroup.LayoutParams.WRAP_CONTENT
            params.height = (28 * density).toInt()
            h.btnAdd.layoutParams = params
        }

        h.itemView.setOnClickListener { onClick(pack) }
        h.btnAdd.setOnClickListener { onAddClick(pack) }

        loadStickerPreviews(h.stickerPreviewContainer, pack)
    }

    private fun loadStickerPreviews(container: LinearLayout, pack: Pack) {
        val context = container.context

        val stickersToShow = pack.stickers.take(4)
        val neededCount = stickersToShow.size

        // Show/hide pre-allocated views (never add/remove)
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i) as? ImageView ?: continue
            if (i < neededCount) {
                child.visibility = View.VISIBLE
                (child.layoutParams as? LinearLayout.LayoutParams)?.marginEnd =
                    if (i == neededCount - 1) 0 else stickerMarginPx
            } else {
                child.visibility = View.GONE
            }
        }

        val glide = Glide.with(context)

        stickersToShow.forEachIndexed { index, sticker ->
            val previewView = container.getChildAt(index) as? ImageView ?: return@forEachIndexed
            previewView.setBackgroundResource(R.drawable.sticker_placeholder)

            var urlToLoad = sticker.url
            if (urlToLoad.isEmpty() && pack.storagePath.isNotEmpty()) {
                urlToLoad = StickerRepository.getStickerDirectUrl(pack.id, sticker.file, pack.storagePath)
            }

            when {
                urlToLoad.isNotEmpty() -> {
                    if (StickerRepository.looksAnimated(context, pack)) {
                        // Animasyonlu paket: önizleme oynasın; thumb anında, orijinal arkadan.
                        glide.load(urlToLoad)
                            .thumbnail(
                                glide.load(StickerRepository.thumbUrl(urlToLoad))
                                    .override(GLIDE_OVERRIDE)
                            )
                            .override(GLIDE_OVERRIDE)
                            .priority(Priority.LOW)
                            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                            .listener(clearBgListener)
                            .into(previewView)
                    } else {
                        glide.load(StickerRepository.thumbUrl(urlToLoad))
                            .override(GLIDE_OVERRIDE)
                            .priority(Priority.NORMAL)
                            .diskCacheStrategy(DiskCacheStrategy.DATA)
                            .listener(clearBgListener)
                            .into(previewView)
                    }
                }
                else -> {
                    val cachedSticker = StickerRepository.getCachedStickerPath(context, pack.id, sticker.file)
                    if (cachedSticker.exists() && cachedSticker.length() > 0) {
                        glide.load(cachedSticker)
                            .override(GLIDE_OVERRIDE)
                            .priority(Priority.NORMAL)
                            .diskCacheStrategy(DiskCacheStrategy.DATA)
                            .signature(ObjectKey(cachedSticker.lastModified()))
                            .listener(clearBgListener)
                            .into(previewView)
                    } else {
                        val assetPath = "file:///android_asset/${pack.id}/${sticker.file}"
                        glide.load(android.net.Uri.parse(assetPath))
                            .override(GLIDE_OVERRIDE)
                            .priority(Priority.NORMAL)
                            .diskCacheStrategy(DiskCacheStrategy.DATA)
                            .listener(clearBgListener)
                            .into(previewView)
                    }
                }
            }
        }
    }

    companion object {
        private const val GLIDE_OVERRIDE = 256
    }

    private fun formatDownloadValue(count: Int): String {
        return when {
            count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
            count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
            else -> count.toString()
        }
    }
}
