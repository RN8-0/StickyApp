package com.sticly

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy

class PackAdapter(private var items: List<Pack>, private val click: (Pack) -> Unit) :
    RecyclerView.Adapter<PackAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tray: ImageView = v.findViewById(R.id.tray)
        val name: TextView = v.findViewById(R.id.name)
        val pub: TextView = v.findViewById(R.id.pub)
        val count: TextView = v.findViewById(R.id.count)
        val checkIcon: ImageView = v.findViewById(R.id.checkIcon)
        val crownIcon: ImageView = v.findViewById(R.id.crownIcon)
        val installedBadge: TextView = v.findViewById(R.id.installedBadge)
        val premiumBadge: TextView = v.findViewById(R.id.premiumBadge)
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
        val isUserPremium = PreferencesHelper.isPremium(context)

        // Taç her zaman premium pakette göster
        h.crownIcon.visibility = if (isPremiumPack) View.VISIBLE else View.GONE

        // Premium badge - kullanıcı premium değilse göster
        h.premiumBadge.visibility = if (isPremiumPack && !isUserPremium && !isInstalled) View.VISIBLE else View.GONE

        // Tray image yükleme - önce cache, sonra URL, sonra assets
        val cachedTray = StickerRepository.getCachedStickerPath(context, pack.id, pack.tray)

        when {
            // 1. Cache'de varsa oradan yükle
            cachedTray.exists() -> {
                Glide.with(context)
                    .load(cachedTray)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .into(h.tray)
            }
            // 2. Firebase URL varsa oradan yükle
            pack.trayUrl.isNotEmpty() -> {
                Glide.with(context)
                    .load(pack.trayUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.ic_logo_white)
                    .error(R.drawable.ic_logo_white)
                    .into(h.tray)
            }
            // 3. Lokal assets'ten yükle
            else -> {
                try {
                    val path = "${pack.id}/${pack.tray}"
                    val stream = context.assets.open(path)
                    val bitmap = BitmapFactory.decodeStream(stream)
                    stream.close()
                    h.tray.setImageBitmap(bitmap)
                } catch (e: Exception) {
                    h.tray.setImageResource(R.drawable.ic_logo_white)
                }
            }
        }
    }

    override fun getItemCount() = items.size

    fun updateList(newList: List<Pack>) {
        items = newList
        notifyDataSetChanged()
    }
}
