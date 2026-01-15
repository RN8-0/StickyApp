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

class StickerAdapter(
    private val packId: String,
    private val items: List<Sticker>,
    private val isPackPremium: Boolean = false,
    private val hasAccess: Boolean = true,
    private val storagePath: String = "stickers",
    private val onStickerClick: ((Sticker, Int) -> Unit)? = null
) : RecyclerView.Adapter<StickerAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val img: ImageView = v.findViewById(R.id.img)
        val progressBar: ProgressBar = v.findViewById(R.id.progressBar)
        val lockIcon: ImageView = v.findViewById(R.id.lockIcon)
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

        // Tıklama
        h.itemView.setOnClickListener {
            onStickerClick?.invoke(sticker, pos)
        }

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
            // 1. Cache'de varsa oradan yükle
            cachedFile.exists() && cachedFile.length() > 0 -> {
                h.progressBar.visibility = View.GONE
                Glide.with(context)
                    .load(cachedFile)
                    .signature(ObjectKey(cachedFile.lastModified()))
                    .skipMemoryCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .into(h.img)
            }
            // 2. Firebase URL varsa oradan yükle
            sticker.url.isNotEmpty() -> {
                h.progressBar.visibility = View.VISIBLE
                h.progressBar.isIndeterminate = true
                Glide.with(context)
                    .load(sticker.url)
                    .placeholder(circularProgress as Drawable)
                    .signature(cacheSignature)
                    .skipMemoryCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .listener(glideListener)
                    .into(h.img)
            }
            // 3. Lokal assets'ten yükle
            else -> {
                h.progressBar.visibility = View.GONE
                try {
                    val path = "$packId/${sticker.file}"
                    val stream = context.assets.open(path)
                    val bitmap = BitmapFactory.decodeStream(stream)
                    stream.close()

                    Glide.with(context)
                        .load(bitmap)
                        .skipMemoryCache(true)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .into(h.img)
                } catch (e: Exception) {
                    // Assets'te yok - placeholder göster
                    h.progressBar.visibility = View.GONE
                    h.img.setImageResource(R.drawable.ic_sticker_placeholder)
                }
            }
        }
    }

    override fun getItemCount() = items.size
}
