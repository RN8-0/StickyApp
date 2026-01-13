package com.sticly

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy

class StickerAdapter(
    private val packId: String,
    private val items: List<Sticker>,
    private val isPackPremium: Boolean = false,
    private val isUserPremium: Boolean = false,
    private val onStickerClick: ((Sticker, Int) -> Unit)? = null
) : RecyclerView.Adapter<StickerAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val img: ImageView = v.findViewById(R.id.img)
        val lockIcon: ImageView = v.findViewById(R.id.lockIcon)
    }

    override fun onCreateViewHolder(p: ViewGroup, vt: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_sticker, p, false))

    override fun onBindViewHolder(h: VH, pos: Int) {
        val sticker = items[pos]
        val context = h.itemView.context

        // Premium pakette ve kullanıcı premium değilse, ilk 3'ten sonrakiler kilitli
        val isLocked = isPackPremium && !isUserPremium && pos >= 3

        // Kilit ikonunu göster/gizle
        h.lockIcon.visibility = if (isLocked) View.VISIBLE else View.GONE

        // Kilitli ise blur efekti için alpha
        h.img.alpha = if (isLocked) 0.3f else 1f

        // Tıklama
        h.itemView.setOnClickListener {
            onStickerClick?.invoke(sticker, pos)
        }

        // Önce cache'de var mı kontrol et (en hızlı)
        val cachedFile = StickerRepository.getCachedStickerPath(context, packId, sticker.file)

        when {
            // 1. Cache'de varsa oradan yükle
            cachedFile.exists() -> {
                Glide.with(context)
                    .load(cachedFile)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .into(h.img)
            }
            // 2. Firebase URL varsa oradan yükle
            sticker.url.isNotEmpty() -> {
                Glide.with(context)
                    .load(sticker.url)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.ic_logo_white)
                    .error(R.drawable.ic_logo_white)
                    .into(h.img)
            }
            // 3. Lokal assets'ten yükle
            else -> {
                try {
                    val path = "$packId/${sticker.file}"
                    val stream = context.assets.open(path)
                    val bitmap = BitmapFactory.decodeStream(stream)
                    stream.close()
                    h.img.setImageBitmap(bitmap)
                } catch (e: Exception) {
                    h.img.setImageResource(R.drawable.ic_logo_white)
                }
            }
        }
    }

    override fun getItemCount() = items.size
}
