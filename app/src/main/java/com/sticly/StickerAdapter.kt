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

    override fun onBindViewHolder(h: VH, pos: Int) {
        val sticker = items[pos]
        val context = h.itemView.context

        // Kilit ikonu gizle
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
        // Silme modunda checkbox container her zaman görünür
        h.checkboxContainer.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
        // Seçiliyse overlay ve check işareti görünür
        h.selectionOverlay.visibility = if (isSelectionMode && isSelected) View.VISIBLE else View.GONE
        h.selectedCheck.visibility = if (isSelectionMode && isSelected) View.VISIBLE else View.GONE
        // Seçiliyse checkbox arka planını değiştir
        h.checkboxContainer.setBackgroundResource(
            if (isSelected) R.drawable.checkbox_selected else R.drawable.checkbox_border
        )

        // Circular progress drawable oluştur
        val circularProgress = CircularProgressDrawable(context).apply {
            strokeWidth = 5f
            centerRadius = 30f
            setColorSchemeColors(
                context.getColor(R.color.primary),
                context.getColor(R.color.premium_gold)
            )
            start()
        }

        // Önce cache'de var mı kontrol et (en hızlı)
        val cachedFile = StickerRepository.getCachedStickerPath(context, packId, sticker.file)

        // Glide listener - yükleme durumunu takip et
        val glideListener = object : RequestListener<Drawable> {
            override fun onLoadFailed(
                e: GlideException?,
                model: Any?,
                target: Target<Drawable>,
                isFirstResource: Boolean
            ): Boolean {
                h.progressBar.visibility = View.GONE
                return false
            }

            override fun onResourceReady(
                resource: Drawable,
                model: Any,
                target: Target<Drawable>?,
                dataSource: DataSource,
                isFirstResource: Boolean
            ): Boolean {
                h.progressBar.visibility = View.GONE
                return false
            }
        }

        // Cache signature - URL veya dosya adı değişince cache yenilensin
        val cacheSignature = ObjectKey(sticker.url.ifEmpty { sticker.file })

        when {
            // 0. Özel paket kontrolü (filesDir/custom_stickers'dan yükle - KALICI DEPOLAMA)
            packId.startsWith("custom_") -> {
                h.progressBar.visibility = View.GONE
                // KRITIK: filesDir/custom_stickers kullan (kalıcı depolama)
                val customFile = CustomStickerManager.getCustomStickerPath(context, packId, sticker.file)
                android.util.Log.d("StickerAdapter", "Loading custom sticker: ${customFile.absolutePath} exists=${customFile.exists()}")
                if (customFile.exists()) {
                    Glide.with(context)
                        .load(customFile)
                        .signature(ObjectKey(customFile.lastModified()))
                        .diskCacheStrategy(DiskCacheStrategy.NONE) // Local file, memory cache is enough
                        .into(h.img)
                } else {
                    h.img.setImageResource(R.drawable.transparent_placeholder)
                }
            }
            // 1. Cache'de varsa oradan yükle
            cachedFile.exists() && cachedFile.length() > 0 -> {
                h.progressBar.visibility = View.GONE
                Glide.with(context)
                    .load(cachedFile)
                    .signature(ObjectKey(cachedFile.lastModified()))
                    // .skipMemoryCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.NONE) // Local file, memory cache is enough
                    .into(h.img)
            }
            // 2. Firebase URL varsa oradan yükle
            sticker.url.isNotEmpty() -> {
                h.progressBar.visibility = View.VISIBLE
                h.progressBar.isIndeterminate = true
                Glide.with(context)
                    .load(sticker.url)
                    .placeholder(circularProgress as Drawable)
                    .error(R.drawable.transparent_placeholder) // Use transparent placeholder
                    .signature(cacheSignature)
                    // .skipMemoryCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .listener(glideListener)
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
