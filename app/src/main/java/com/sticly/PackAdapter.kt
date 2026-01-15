package com.sticly

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.ObjectKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class PackAdapter(
    private var items: List<Pack>,
    private val click: (Pack) -> Unit,
    private val onFavoriteChanged: (() -> Unit)? = null
) : RecyclerView.Adapter<PackAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tray: ImageView = v.findViewById(R.id.tray)
        val name: TextView = v.findViewById(R.id.name)
        val pub: TextView = v.findViewById(R.id.pub)
        val count: TextView = v.findViewById(R.id.count)
        val checkIcon: ImageView = v.findViewById(R.id.checkIcon)
        val crownIcon: ImageView = v.findViewById(R.id.crownIcon)
        val installedBadge: TextView = v.findViewById(R.id.installedBadge)
        val premiumContainer: View = v.findViewById(R.id.premiumContainer)
        val newBadge: TextView = v.findViewById(R.id.newBadge)
        val btnFavorite: ImageButton = v.findViewById(R.id.btnFavorite)
        val downloadCount: TextView = v.findViewById(R.id.downloadCount)
    }

    override fun onCreateViewHolder(p: ViewGroup, vt: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_pack, p, false))

    override fun onBindViewHolder(h: VH, pos: Int) {
        val pack = items[pos]
        val context = h.itemView.context

        h.name.text = pack.name
        h.pub.text = pack.pub
        h.count.text = "${pack.stickers.size} stickers"
        h.itemView.setOnClickListener { click(pack) }

        // Check if pack is installed
        val isInstalled = PreferencesHelper.isPackInstalled(context, pack.id)
        h.checkIcon.visibility = if (isInstalled) View.VISIBLE else View.GONE
        h.installedBadge.visibility = if (isInstalled) View.VISIBLE else View.GONE

        // Premium badge ve taç
        val isPremiumPack = pack.isPremium
        val hasAccess = PreferencesHelper.hasAccessToPremiumPack(context, pack.id)

        // Taç her zaman premium pakette göster
        h.crownIcon.visibility = if (isPremiumPack) View.VISIBLE else View.GONE

        // Premium container (badge + fiyat) - kullanıcı erişimi yoksa göster
        h.premiumContainer.visibility = if (isPremiumPack && !hasAccess && !isInstalled) View.VISIBLE else View.GONE

        // Yeni badge - son 7 gün içinde eklenen paketler
        val isNew = isPackNew(pack.createdAt)
        h.newBadge.visibility = if (isNew) View.VISIBLE else View.GONE

        // Favori butonu
        val isFavorite = PreferencesHelper.isPackFavorite(context, pack.id)
        h.btnFavorite.setImageResource(
            if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border
        )
        h.btnFavorite.setOnClickListener {
            val newFavState = PreferencesHelper.toggleFavorite(context, pack.id)
            h.btnFavorite.setImageResource(
                if (newFavState) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border
            )
            val msg = if (newFavState) R.string.added_to_favorites else R.string.removed_from_favorites
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            onFavoriteChanged?.invoke()
        }

        // İndirme/ekleme sayısı
        if (pack.downloadCount > 0) {
            h.downloadCount.visibility = View.VISIBLE
            h.downloadCount.text = formatDownloadCount(pack.downloadCount)
        } else {
            h.downloadCount.visibility = View.GONE
        }

        // Tray image yükleme - URL varsa HER ZAMAN URL'den yükle (güncel kalması için)
        when {
            // 1. Firebase URL varsa oradan yükle - cache KULLANMA (güncel tray için)
            pack.trayUrl.isNotEmpty() -> {
                Glide.with(context)
                    .load(pack.trayUrl)
                    .skipMemoryCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .into(h.tray)
            }
            // 2. URL yoksa cache'den yükle
            else -> {
                val cachedTray = StickerRepository.getCachedStickerPath(context, pack.id, pack.tray)
                if (cachedTray.exists() && cachedTray.length() > 0) {
                    Glide.with(context)
                        .load(cachedTray)
                        .signature(ObjectKey(cachedTray.lastModified()))
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .into(h.tray)
                } else {
                    // 3. Lokal assets'ten yükle
                    try {
                        val path = "${pack.id}/${pack.tray}"
                        val stream = context.assets.open(path)
                        val bitmap = BitmapFactory.decodeStream(stream)
                        stream.close()
                        h.tray.setImageBitmap(bitmap)
                    } catch (e: Exception) {
                        h.tray.setImageResource(R.drawable.ic_sticker_placeholder)
                    }
                }
            }
        }
    }

    override fun getItemCount() = items.size

    fun updateList(newList: List<Pack>) {
        items = newList
        notifyDataSetChanged()
    }

    private fun isPackNew(createdAt: String): Boolean {
        if (createdAt.isEmpty()) return false
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val createdDate = format.parse(createdAt) ?: return false
            val now = Date()
            val diffInMillis = now.time - createdDate.time
            val diffInDays = TimeUnit.MILLISECONDS.toDays(diffInMillis)
            diffInDays <= 7
        } catch (e: Exception) {
            false
        }
    }

    private fun formatDownloadCount(count: Int): String {
        return when {
            count >= 1000000 -> "${count / 1000000}M"
            count >= 1000 -> "${count / 1000}K"
            else -> count.toString()
        }
    }
}
