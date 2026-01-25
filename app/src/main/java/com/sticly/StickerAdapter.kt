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
    var isSelectionMode: Boolean = false,
    val selectedPositions: MutableSet<Int> = mutableSetOf(),
    private val onStickerClick: ((Sticker, Int) -> Unit)? = null,
    private val onStickerLongClick: ((Sticker, Int) -> Unit)? = null,
    private val onSelectionChanged: ((Int) -> Unit)? = null
) : RecyclerView.Adapter<StickerAdapter.VH>() {

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

        // Glide request manager
        val glide = Glide.with(context)

        when {
            // 0. Özel paket kontrolü
            packId.startsWith("custom_") -> {
                h.progressBar.visibility = View.GONE
                val customFile = CustomStickerManager.getCustomStickerPath(context, packId, sticker.file)
                if (customFile.exists()) {
                    glide.load(customFile)
                        .signature(ObjectKey(customFile.lastModified()))
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .into(h.img)
                } else {
                    h.img.setImageResource(R.drawable.transparent_placeholder)
                }
            }
            // 1. Cache'de varsa oradan yükle
            cachedFile.exists() && cachedFile.length() > 0 -> {
                h.progressBar.visibility = View.GONE
                glide.load(cachedFile)
                    .signature(ObjectKey(cachedFile.lastModified()))
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .placeholder(R.drawable.transparent_placeholder)
                    .into(h.img)
            }
            // 2. Firebase URL varsa oradan yükle
            sticker.url.isNotEmpty() -> {
                h.progressBar.visibility = View.VISIBLE
                glide.load(sticker.url)
                    .placeholder(getProgressDrawable(context))
                    .error(R.drawable.transparent_placeholder)
                    .signature(ObjectKey(sticker.url))
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(e: GlideException?, m: Any?, t: Target<Drawable>, isF: Boolean): Boolean {
                            h.progressBar.visibility = View.GONE
                            return false
                        }
                        override fun onResourceReady(r: Drawable, m: Any, t: Target<Drawable>?, d: DataSource, isF: Boolean): Boolean {
                            h.progressBar.visibility = View.GONE
                            return false
                        }
                    })
                    .into(h.img)
            }
            // 4. Lokal assets'ten yükle (Önceki "3. Lokal assets'ten yükle" bloğu bu sıraya kaydırılıyor)
            else -> {
                h.progressBar.visibility = View.GONE
                try {
                    val path = "$packId/${sticker.file}"
                    val stream = context.assets.open(path)
                    val bitmap = BitmapFactory.decodeStream(stream)
                    stream.close()

                    Glide.with(context)
                        .load(bitmap)
                        // .skipMemoryCache(true) // REMOVED
                        .diskCacheStrategy(DiskCacheStrategy.NONE) // Bitmap from stream, no disk cache source
                        .into(h.img)
                } catch (e: Exception) {
                    // Assets'te yok - placeholder göster
                    h.progressBar.visibility = View.GONE
                    h.img.setImageResource(R.drawable.transparent_placeholder)
                }
            }
        }
    }

    fun setDeleteMode(enabled: Boolean) {
        this.isSelectionMode = enabled
        if (!enabled) selectedPositions.clear()
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size
}
