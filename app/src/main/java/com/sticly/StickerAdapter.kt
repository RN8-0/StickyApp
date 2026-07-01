package com.sticly

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.recyclerview.widget.LinearLayoutManager
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

    private val clearBgBitmapListener = object : RequestListener<Bitmap> {
        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Bitmap>, isFirstResource: Boolean): Boolean = false
        override fun onResourceReady(resource: Bitmap, model: Any, target: Target<Bitmap>, dataSource: DataSource, isFirstResource: Boolean): Boolean {
            val view = (target as? com.bumptech.glide.request.target.ImageViewTarget<*>)?.view ?: return false
            view.background = null
            // Statik kare ekranda: bu holder artık animasyona yükseltilebilir.
            (recyclerView?.findContainingViewHolder(view) as? VH)?.let { holder ->
                holder.staticReady = true
                scheduleAnimateVisible()
            }
            return false
        }
    }

    // Same background-clear behaviour as above, but for the animated Drawable upgrade.
    private val clearBgDrawableListener = object : RequestListener<Drawable> {
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

    // ── Animated grid playback ────────────────────────────────────────────────
    // Enabled on API 28+, where Glide's ImageDecoder path can decode animated WebP without an
    // extra decoder library. The fast, reliable static asBitmap() load stays the base; we upgrade
    // ONLY the currently-visible items to drawables when scrolling is idle — animated WebPs then
    // play, static images stay static. This bounds concurrent decodes to the visible set (~9-12),
    // avoiding the old "only the first few load" regression and keeping scrolling smooth.
    //
    // NOTE: we deliberately do NOT gate on pack.isAnimated — that DB flag has false negatives
    // (e.g. "Animated Emojis 1" / "Shaun the Sheep" are animated but flagged is_animated=false),
    // which left genuinely-animated packs showing a single frame. Upgrading every visible item is
    // cheap and correct regardless of the flag.
    private val animateOnIdle = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    private var recyclerView: RecyclerView? = null
    private var animateScheduled = false

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
            if (newState == RecyclerView.SCROLL_STATE_IDLE) scheduleAnimateVisible()
        }
    }

    init {
        setHasStableIds(true)
    }

    override fun onAttachedToRecyclerView(rv: RecyclerView) {
        super.onAttachedToRecyclerView(rv)
        recyclerView = rv
        if (animateOnIdle) {
            rv.addOnScrollListener(scrollListener)
            scheduleAnimateVisible()
        }
    }

    override fun onDetachedFromRecyclerView(rv: RecyclerView) {
        super.onDetachedFromRecyclerView(rv)
        rv.removeOnScrollListener(scrollListener)
        recyclerView = null
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
        // True once this holder has been upgraded to animated playback (reset on bind/recycle).
        var animated: Boolean = false
        // True once the static frame has actually rendered (reset on bind/recycle).
        var staticReady: Boolean = false
    }

    override fun onCreateViewHolder(p: ViewGroup, vt: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_sticker, p, false))

    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        holder.animated = false
        holder.staticReady = false
        try { getGlide(holder.itemView.context).clear(holder.img) } catch (_: Exception) {}
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val sticker = items[pos]
        val context = h.itemView.context

        // Reset state
        h.animated = false
        h.staticReady = false
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
                    glideManager.asBitmap()
                        .load(customFile)
                        .signature(ObjectKey(customFile.lastModified()))
                        .override(256, 256)
                        .placeholder(R.drawable.sticker_placeholder)
                        .error(R.drawable.sticker_placeholder)
                        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                        .listener(clearBgBitmapListener)
                        .into(h.img)
                } else {
                    h.img.setImageResource(R.drawable.sticker_placeholder)
                }
            }
            // 1. Cache'de varsa oradan yükle
            cachedFile.exists() && cachedFile.length() > 0 -> {
                glideManager.asBitmap()
                    .load(cachedFile)
                    .signature(ObjectKey(cachedFile.lastModified()))
                    .override(256, 256)
                    .placeholder(R.drawable.sticker_placeholder)
                    .error(R.drawable.sticker_placeholder)
                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                    .listener(clearBgBitmapListener)
                    .into(h.img)
            }
            // 2. Firebase URL varsa oradan yükle
            sticker.url.isNotEmpty() -> {
                // Statik kare imgproxy küçük resmi olarak iner (~5KB) → grid anında dolar.
                // Animasyon yükseltmesi (resolveSource) ORİJİNAL URL'i kullanmaya devam eder.
                glideManager.asBitmap()
                    .load(StickerRepository.thumbUrl(sticker.url))
                    .override(256, 256)
                    .placeholder(R.drawable.sticker_placeholder)
                    .error(R.drawable.sticker_placeholder)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .listener(clearBgBitmapListener)
                    .into(h.img)
            }
            // 3. URL yoksa direkt storage URL hesapla ve yükle
            storagePath.isNotEmpty() -> {
                val directUrl = StickerRepository.getStickerDirectUrl(packId, sticker.file, storagePath)
                // Update the sticker URL for future use
                sticker.url = directUrl
                glideManager.asBitmap()
                    .load(StickerRepository.thumbUrl(directUrl))
                    .override(256, 256)
                    .placeholder(R.drawable.sticker_placeholder)
                    .error(R.drawable.sticker_placeholder)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .listener(clearBgBitmapListener)
                    .into(h.img)
            }
            // 4. Lokal assets'ten yükle
            else -> {
                val assetPath = "file:///android_asset/$packId/${sticker.file}"
                glideManager.asBitmap()
                    .load(android.net.Uri.parse(assetPath))
                    .override(256, 256)
                    .placeholder(R.drawable.sticker_placeholder)
                    .error(R.drawable.sticker_placeholder)
                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                    .listener(clearBgBitmapListener)
                    .into(h.img)
            }
        }

        // After the static frame is bound, queue an idle upgrade of the visible items to animated.
        if (animateOnIdle && !isSelectionMode) scheduleAnimateVisible()
    }

    /** Posts a single debounced pass that animates the visible items once scrolling is idle. */
    private fun scheduleAnimateVisible() {
        if (!animateOnIdle) return
        val rv = recyclerView ?: return
        if (animateScheduled) return
        animateScheduled = true
        rv.post {
            animateScheduled = false
            if (rv.scrollState == RecyclerView.SCROLL_STATE_IDLE) upgradeVisibleToAnimated()
        }
    }

    private fun upgradeVisibleToAnimated() {
        if (isSelectionMode) return
        val rv = recyclerView ?: return
        val lm = rv.layoutManager as? LinearLayoutManager ?: return
        val first = lm.findFirstVisibleItemPosition()
        val last = lm.findLastVisibleItemPosition()
        if (first < 0 || last < 0) return
        for (pos in first..last) {
            val h = rv.findViewHolderForAdapterPosition(pos) as? VH ?: continue
            upgradeHolderToAnimated(h, pos)
        }
    }

    /** Upgrades one already-bound (visible) holder from the static frame to animated playback. */
    private fun upgradeHolderToAnimated(h: VH, pos: Int) {
        if (h.animated || isSelectionMode) return
        // into() aynı ImageView'daki isteği iptal ettiği için, statik kare inmeden yükseltme
        // yapmak hücreyi animasyon çözülene kadar boş bırakıyordu. Statik kare gelene kadar bekle;
        // onResourceReady zaten yeni bir yükseltme turu tetikliyor.
        if (!h.staticReady) return
        val context = h.itemView.context
        val source = resolveSource(context, pos) ?: return
        h.animated = true
        var req = getGlide(context).asDrawable()
            .load(source)
            .override(256, 256)
            .dontTransform()
            // Animasyon hazır olana kadar mevcut statik kare ekranda kalsın (gri kutu yok).
            .placeholder(h.img.drawable)
            // Tam boy dosya arka planda insin; görünür statik thumb'ların önüne geçmesin.
            .priority(com.bumptech.glide.Priority.LOW)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .listener(clearBgDrawableListener)
        if (source is java.io.File) req = req.signature(ObjectKey(source.lastModified()))
        req.into(h.img)
    }

    /** Resolves the same load source the static bind uses, so the animated upgrade matches it. */
    private fun resolveSource(context: Context, pos: Int): Any? {
        val sticker = items.getOrNull(pos) ?: return null
        if (packId.startsWith("custom_")) {
            val customFile = CustomStickerManager.getCustomStickerPath(context, packId, sticker.file)
            return if (customFile.exists()) customFile else null
        }
        val cachedFile = StickerRepository.getCachedStickerPath(context, packId, sticker.file)
        if (cachedFile.exists() && cachedFile.length() > 0) return cachedFile
        if (sticker.url.isNotEmpty()) return sticker.url
        if (storagePath.isNotEmpty()) return StickerRepository.getStickerDirectUrl(packId, sticker.file, storagePath)
        return android.net.Uri.parse("file:///android_asset/$packId/${sticker.file}")
    }

    fun setDeleteMode(enabled: Boolean) {
        this.isSelectionMode = enabled
        if (!enabled) selectedPositions.clear()
        // Tüm listeyi yenilemek yerine sadece görünür öğeleri güncelle
        notifyItemRangeChanged(0, itemCount, "selection_mode")
    }

    override fun getItemCount() = items.size
}
