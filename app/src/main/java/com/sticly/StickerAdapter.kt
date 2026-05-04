package com.sticly

import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.signature.ObjectKey
import java.io.File

class StickerAdapter(
    private val packId: String,
    private val items: List<Sticker>,
    private val isPackPremium: Boolean = false,
    private val hasAccess: Boolean = true,
    private val storagePath: String = "stickers",
    private val isAnimated: Boolean = false,
    var isSelectionMode: Boolean = false,
    val selectedPositions: MutableSet<Int> = mutableSetOf(),
    private val onStickerClick: ((Sticker, Int) -> Unit)? = null,
    private val onStickerLongClick: ((Sticker, Int) -> Unit)? = null,
    private val onSelectionChanged: ((Int) -> Unit)? = null
) : RecyclerView.Adapter<StickerAdapter.VH>() {

    // Reusable listener to avoid allocation in onBind
    private val clearBgListener = object : RequestListener<Drawable> {
        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean = false
        override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>, dataSource: DataSource, isFirstResource: Boolean): Boolean {
            (target as? com.bumptech.glide.request.target.ImageViewTarget<*>)?.view?.background = null
            return false
        }
    }

    private var glideManager: com.bumptech.glide.RequestManager? = null

    private fun getGlide(context: android.content.Context): com.bumptech.glide.RequestManager {
        return glideManager ?: Glide.with(context).also { glideManager = it }
    }

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return items.getOrNull(position)?.file?.hashCode()?.toLong() ?: position.toLong()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val img: ImageView = v.findViewById(R.id.img)
        val progressBar: ProgressBar = v.findViewById(R.id.progressBar)
        val lockIcon: ImageView = v.findViewById(R.id.lockIcon)
        val selectionOverlay: View = v.findViewById(R.id.selectionOverlay)
        val checkboxContainer: View = v.findViewById(R.id.checkboxContainer)
        val selectedCheck: ImageView = v.findViewById(R.id.selectedCheck)
    }

    override fun onCreateViewHolder(p: ViewGroup, vt: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_sticker, p, false))

    // Lazy progress drawable for better performance
    private fun getProgressDrawable(context: android.content.Context) = CircularProgressDrawable(context).apply {
        strokeWidth = 5f
        centerRadius = 30f
        setColorSchemeColors(
            context.getColor(R.color.primary),
            context.getColor(R.color.premium_gold)
        )
        start()
    }

    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        try { getGlide(holder.itemView.context).clear(holder.img) } catch (_: Exception) {}
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val sticker = items[pos]
        val context = h.itemView.context

        // Reset state
        h.lockIcon.visibility = View.GONE
        h.img.alpha = 1f
        h.img.rotation = 0f

        h.itemView.setOnClickListener {
            if (isSelectionMode) {
                if (selectedPositions.contains(pos)) {
                    selectedPositions.remove(pos)
                } else {
                    selectedPositions.add(pos)
                }
                notifyItemChanged(pos)
                onSelectionChanged?.invoke(selectedPositions.size)
            } else {
                onStickerClick?.invoke(sticker, pos)
            }
        }
        
        h.itemView.setOnLongClickListener {
            if (!isSelectionMode) {
                onStickerLongClick?.invoke(sticker, pos)
            }
            true
        }

        // Seçim UI'ını güncelle
        val isSelected = selectedPositions.contains(pos)
        h.checkboxContainer.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
        h.selectionOverlay.visibility = if (isSelectionMode && isSelected) View.VISIBLE else View.GONE
        h.selectedCheck.visibility = if (isSelectionMode && isSelected) View.VISIBLE else View.GONE
        h.checkboxContainer.setBackgroundResource(
            if (isSelected) R.drawable.checkbox_selected else R.drawable.checkbox_border
        )

        // Cache'de var mı kontrol et (en hızlı)
        val cachedFile = StickerRepository.getCachedStickerPath(context, packId, sticker.file)

        // Glide request manager (cached)
        val glideManager = getGlide(context)

        // Hide progressBar (placeholder is enough)
        h.progressBar.visibility = View.GONE

        when {
            // 0. Özel paket kontrolü
            packId.startsWith("custom_") -> {
                val customFile = CustomStickerManager.getCustomStickerPath(context, packId, sticker.file)
                if (customFile.exists()) {
                    glideManager.asDrawable()
                        .load(customFile)
                        .signature(ObjectKey(customFile.lastModified()))
                        .placeholder(R.drawable.sticker_placeholder)
                        .error(R.drawable.sticker_placeholder)
                        .listener(clearBgListener)
                        .into(h.img)
                } else {
                    h.img.setImageResource(R.drawable.sticker_placeholder)
                }
            }
            // Animated packs must prefer the current remote file over an old static cache.
            isAnimated && sticker.url.isNotEmpty() -> {
                glideManager.asDrawable()
                    .load(sticker.url)
                    .thumbnail(0.25f)
                    .placeholder(R.drawable.sticker_placeholder)
                    .error(R.drawable.sticker_placeholder)
                    .diskCacheStrategy(DiskCacheStrategy.DATA)
                    .listener(clearBgListener)
                    .into(h.img)
            }
            // 1. Cache'de varsa oradan yükle
            cachedFile.exists() && cachedFile.length() > 0 -> {
                glideManager.asDrawable()
                    .load(cachedFile)
                    .signature(ObjectKey(cachedFile.lastModified()))
                    .override(384, 384)
                    .placeholder(R.drawable.sticker_placeholder)
                    .error(R.drawable.sticker_placeholder)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .listener(clearBgListener)
                    .into(h.img)
            }
            // 2. Firebase URL varsa oradan yükle
            sticker.url.isNotEmpty() -> {
                val request = glideManager.asDrawable()
                    .load(sticker.url)
                    .thumbnail(0.25f) // Show blurred thumbnail instantly while full image loads
                    .placeholder(R.drawable.sticker_placeholder)
                    .error(R.drawable.sticker_placeholder)
                    .diskCacheStrategy(DiskCacheStrategy.DATA)
                    .listener(clearBgListener)

                if (!isAnimated) {
                    request.override(256, 256).dontAnimate()
                }
                request.into(h.img)
            }
            // 3. URL yoksa direkt storage URL hesapla ve yükle
            storagePath.isNotEmpty() -> {
                val directUrl = StickerRepository.getStickerDirectUrl(packId, sticker.file, storagePath)
                // Update the sticker URL for future use
                sticker.url = directUrl
                val request = glideManager.asDrawable()
                    .load(directUrl)
                    .thumbnail(0.25f)
                    .placeholder(R.drawable.sticker_placeholder)
                    .error(R.drawable.sticker_placeholder)
                    .diskCacheStrategy(DiskCacheStrategy.DATA)
                    .listener(clearBgListener)

                if (!isAnimated) {
                    request.override(256, 256).dontAnimate()
                }
                request.into(h.img)
            }
            // 4. Lokal assets'ten yükle
            else -> {
                val assetPath = "file:///android_asset/$packId/${sticker.file}"
                glideManager.asDrawable()
                    .load(android.net.Uri.parse(assetPath))
                    .placeholder(R.drawable.sticker_placeholder)
                    .error(R.drawable.sticker_placeholder)
                    .diskCacheStrategy(DiskCacheStrategy.DATA)
                    .listener(clearBgListener)
                    .into(h.img)
            }
        }
    }

    fun setDeleteMode(enabled: Boolean) {
        this.isSelectionMode = enabled
        if (!enabled) selectedPositions.clear()
        // Tüm listeyi yenilemek yerine sadece görünür öğeleri güncelle
        notifyItemRangeChanged(0, itemCount, "selection_mode")
    }

    override fun getItemCount() = items.size
}
