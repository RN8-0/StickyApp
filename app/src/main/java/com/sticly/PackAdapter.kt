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
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.nativead.MediaView
import android.widget.Button

class PackAdapter(
    private var items: List<Any>,
    private val click: (Pack) -> Unit,
    private val onFavoriteChanged: (() -> Unit)? = null,
    private val onDeleteClick: ((Pack) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_PACK = 0
        private const val TYPE_AD = 1
    }

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
        val btnDelete: ImageButton = v.findViewById(R.id.btnDelete)
        val downloadCount: TextView = v.findViewById(R.id.downloadCount)
        val premiumPrice: TextView = v.findViewById(R.id.premiumPrice)
    }

    class AdVH(v: View) : RecyclerView.ViewHolder(v) {
        val adView: NativeAdView = v as NativeAdView
        val headline: TextView = v.findViewById(R.id.ad_headline)
        val body: TextView = v.findViewById(R.id.ad_body)
        val callToAction: Button = v.findViewById(R.id.ad_call_to_action)
        val icon: ImageView = v.findViewById(R.id.ad_app_icon)
        val media: MediaView = v.findViewById(R.id.ad_media)
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position] is Pack) TYPE_PACK else TYPE_AD
    }

    override fun onCreateViewHolder(p: ViewGroup, vt: Int): RecyclerView.ViewHolder {
        return if (vt == TYPE_PACK) {
            VH(LayoutInflater.from(p.context).inflate(R.layout.item_pack, p, false))
        } else {
            AdVH(LayoutInflater.from(p.context).inflate(R.layout.item_ad_native, p, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, pos: Int) {
        if (holder is VH) {
            val pack = items[pos] as Pack
            bindPack(holder, pack)
        } else if (holder is AdVH) {
            if (!PreferencesHelper.isPremium(holder.itemView.context)) {
                val adType = if (items[pos] == "AD_FAVORITE_PLACEHOLDER") {
                    AdManager.NativeAdType.FAVORITE
                } else {
                    AdManager.NativeAdType.LIST
                }
                
                AdManager.loadNativeAd(holder.itemView.context, adType) { nativeAd ->
                    AdManager.populateNativeAdView(nativeAd, holder.adView)
                }
            } else {
                holder.itemView.visibility = View.GONE
                holder.itemView.layoutParams = RecyclerView.LayoutParams(0, 0)
            }
        }
    }

    private fun bindPack(h: VH, pack: Pack) {
        val context = h.itemView.context

        h.name.text = pack.localizedName
        h.pub.text = pack.pub
        h.count.text = context.getString(R.string.sticker_count, pack.stickers.size)
        h.itemView.setOnClickListener { click(pack) }

        // Check if pack is installed
        val isInstalled = PreferencesHelper.isPackInstalled(context, pack.id)
        h.checkIcon.visibility = if (isInstalled) View.VISIBLE else View.GONE
        h.installedBadge.visibility = if (isInstalled) View.VISIBLE else View.GONE

        // Özel paket mi?
        val isCustomPack = pack.category == "custom"
        if (isCustomPack) {
            h.btnFavorite.visibility = View.GONE
            h.btnDelete.visibility = View.VISIBLE
            h.btnDelete.setOnClickListener { onDeleteClick?.invoke(pack) }
        } else {
            h.btnFavorite.visibility = View.VISIBLE
            h.btnDelete.visibility = View.GONE
        }

        // Premium badge ve taç
        val isPremiumPack = pack.isPremium
        val hasAccess = PreferencesHelper.hasAccessToPremiumPack(context, pack.id)

        h.crownIcon.visibility = if (isPremiumPack) View.VISIBLE else View.GONE
        h.premiumContainer.visibility = if (isPremiumPack && !hasAccess && !isInstalled) View.VISIBLE else View.GONE
        
        if (h.premiumContainer.visibility == View.VISIBLE) {
            val locale = context.resources.configuration.locales[0]
            val rawPrice = pack.priceTRY.trim().replace(Regex("[^0-9,.]"), "")
            
            val formatted = when {
                locale.country == "TR" -> "₺$rawPrice"
                listOf("DE", "FR", "IT", "ES", "NL", "BE", "AT", "PT", "FI", "GR", "IE", "SK", "SI", "EE", "LV", "LT", "MT", "CY", "LU").contains(locale.country) -> "€${pack.priceEUR.trim().replace(Regex("[^0-9,.]"), "")}"
                else -> "$${pack.priceUSD.trim().replace(Regex("[^0-9,.]"), "")}"
            }
            h.premiumPrice.text = formatted
        }

        // Yeni badge
        val isNew = isPackNew(pack.createdAt)
        h.newBadge.visibility = if (isNew) View.VISIBLE else View.GONE

        // Favori butonu
        if (!isCustomPack) {
            val isFavorite = PreferencesHelper.isPackFavorite(context, pack.id)
            h.btnFavorite.setImageResource(
                if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border
            )
            h.btnFavorite.setOnClickListener {
                val newFavState = PreferencesHelper.toggleFavorite(context, pack.id)
                h.btnFavorite.setImageResource(
                    if (newFavState) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border
                )

                if (newFavState) {
                    StickerRepository.incrementFavoriteCount(pack.id, pack.isPremium)
                } else {
                    StickerRepository.decrementFavoriteCount(pack.id, pack.isPremium)
                }

                val msg = if (newFavState) R.string.added_to_favorites else R.string.removed_from_favorites
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                onFavoriteChanged?.invoke()
            }
        }

        // İndirme sayısı
        if (pack.downloadCount > 0) {
            h.downloadCount.visibility = View.VISIBLE
            h.downloadCount.text = formatDownloadCount(pack.downloadCount)
        } else {
            h.downloadCount.visibility = View.GONE
        }

        // Tray image
        if (isCustomPack) {
            val trayFile = CustomStickerManager.getTrayFile(context, pack.id)
            if (trayFile != null && trayFile.exists()) {
                Glide.with(context).load(trayFile).skipMemoryCache(true).diskCacheStrategy(DiskCacheStrategy.NONE).into(h.tray)
            } else {
                h.tray.setImageResource(R.drawable.ic_sticker_placeholder)
            }
        } else {
            when {
                pack.trayUrl.isNotEmpty() -> {
                    Glide.with(context).load(pack.trayUrl).diskCacheStrategy(DiskCacheStrategy.ALL).into(h.tray)
                }
                else -> {
                    val cachedTray = StickerRepository.getCachedStickerPath(context, pack.id, pack.tray)
                    if (cachedTray.exists() && cachedTray.length() > 0) {
                        Glide.with(context).load(cachedTray).signature(ObjectKey(cachedTray.lastModified())).diskCacheStrategy(DiskCacheStrategy.NONE).into(h.tray)
                    } else {
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
    }

    override fun getItemCount() = items.size

    fun updateList(newList: List<Any>) {
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
