package com.sticly

import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.signature.ObjectKey

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

    // Tüm sticker'lar asDrawable ile yüklenir; asBitmap kullanılırsa animasyonlu
    // WebP'ler grid'de sadece ilk kareyle donar. Bu listener arka planı temizler
    // ve bellek cache'inden gelen animasyonlu drawable'ı yeniden başlatır.
    private val clearBgDrawableListener = object : RequestListener<Drawable> {
        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean = false
        override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>, dataSource: DataSource, isFirstResource: Boolean): Boolean {
            (target as? com.bumptech.glide.request.target.ImageViewTarget<*>)?.view?.background = null
            (resource as? Animatable)?.start()
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

        // Yüklenecek kaynağı belirle (cache > url > storage > yerel asset sırasıyla).
        val source: Any? = when {
            packId.startsWith("custom_") -> {
                val customFile = CustomStickerManager.getCustomStickerPath(context, packId, sticker.file)
                if (customFile.exists()) customFile else null
            }
            cachedFile.exists() && cachedFile.length() > 0 -> cachedFile
            sticker.url.isNotEmpty() -> sticker.url
            storagePath.isNotEmpty() -> {
                StickerRepository.getStickerDirectUrl(packId, sticker.file, storagePath)
                    .also { sticker.url = it }
            }
            else -> android.net.Uri.parse("file:///android_asset/$packId/${sticker.file}")
        }

        if (source == null) {
            h.img.setImageResource(R.drawable.sticker_placeholder)
            return
        }

        // Her zaman asDrawable: animasyonlu WebP'ler grid'de canlı oynar, statik
        // olanlar normal görünür. asBitmap kullanılırsa animasyon donar.
        var request = glideManager.asDrawable()
            .load(source)
            .override(256, 256)
            .placeholder(R.drawable.sticker_placeholder)
            .error(R.drawable.sticker_placeholder)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .listener(clearBgDrawableListener)
        if (source is java.io.File) {
            request = request.signature(ObjectKey(source.lastModified()))
        }
        request.into(h.img)
    }

    fun setDeleteMode(enabled: Boolean) {
        this.isSelectionMode = enabled
        if (!enabled) selectedPositions.clear()
        // Tüm listeyi yenilemek yerine sadece görünür öğeleri güncelle
        notifyItemRangeChanged(0, itemCount, "selection_mode")
    }

    override fun getItemCount() = items.size
}
