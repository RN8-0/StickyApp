package com.sticly

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.signature.ObjectKey
import android.graphics.drawable.Drawable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.nativead.MediaView
import android.widget.Button
import com.google.android.material.button.MaterialButton

import androidx.recyclerview.widget.DiffUtil

class PackAdapter(
    private var items: List<Any>,
    private val click: (Pack) -> Unit,
    private val onFavoriteChanged: (() -> Unit)? = null,
    private val onDeleteClick: ((Pack) -> Unit)? = null,
    private val onAddClick: ((Pack) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        val item = items.getOrNull(position) ?: return position.toLong()
        return if (item is Pack) item.id.hashCode().toLong() else item.hashCode().toLong()
    }

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
        val packTypeBadge: TextView = v.findViewById(R.id.packTypeBadge)
        val newBadge: TextView = v.findViewById(R.id.newBadge)
        val btnFavorite: ImageButton = v.findViewById(R.id.btnFavorite)
        val btnFavoriteNew: ImageButton = v.findViewById(R.id.btnFavoriteNew)
        val btnDelete: ImageButton = v.findViewById(R.id.btnDelete)
        val btnDeletePack: ImageButton = v.findViewById(R.id.btnDeletePack)
        val downloadCount: TextView = v.findViewById(R.id.downloadCount)
        val timeAgo: TextView = v.findViewById(R.id.timeAgo)
        val btnAdd: MaterialButton = v.findViewById(R.id.btnAdd)
        val stickerPreviewContainer: LinearLayout = v.findViewById(R.id.stickerPreviewContainer)
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
            val context = holder.itemView.context
            if (!PreferencesHelper.isPremium(context)) {
                // Başlangıçta görünür yap
                holder.itemView.visibility = View.VISIBLE
                holder.itemView.layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                )

                val adType = when (items[pos]) {
                    "AD_FAVORITE_PLACEHOLDER" -> AdManager.NativeAdType.FAVORITE
                    "AD_MY_STICKERS_PLACEHOLDER" -> AdManager.NativeAdType.MY_STICKERS
                    else -> AdManager.NativeAdType.LIST
                }

                android.util.Log.d("PackAdapter", "Loading ad at position $pos, type: $adType")
                AdManager.loadNativeAd(context, adType) { nativeAd ->
                    android.util.Log.d("PackAdapter", "Ad loaded, populating view at position $pos")
                    AdManager.populateNativeAdView(nativeAd, holder.adView)
                }
            } else {
                android.util.Log.d("PackAdapter", "User is premium, hiding ad at position $pos")
                holder.itemView.visibility = View.GONE
                holder.itemView.layoutParams = RecyclerView.LayoutParams(0, 0)
            }
        }
    }

    private fun bindPack(h: VH, pack: Pack) {
        val context = h.itemView.context

        h.name.text = pack.localizedName

        // Yayıncı + çıkartma sayısı + indirme sayısı tek satırda
        val stickerCount = pack.stickers.size
        val displayDownloadCount = pack.fakeDownloadBase + pack.downloadCount
        val pubText = StringBuilder(pack.pub)
        pubText.append(" • $stickerCount stickers")
        if (displayDownloadCount > 0) {
            pubText.append(" • ${formatDownloadCount(displayDownloadCount)} downloads")
        }
        h.pub.text = pubText.toString()

        h.count.text = context.getString(R.string.sticker_count, pack.stickers.size)
        h.itemView.setOnClickListener { click(pack) }

        // Check if pack is installed - Safely check with access for premium packs
        val hasAccess = PreferencesHelper.hasAccessToPremiumPack(context, pack.id)
        val isInstalled = PreferencesHelper.isPackInstalled(context, pack.id) && (!pack.isPremium || hasAccess)
        
        h.checkIcon.visibility = if (isInstalled) View.VISIBLE else View.GONE
        h.installedBadge.visibility = if (isInstalled) View.VISIBLE else View.GONE

        // Özel paket mi?
        val isCustomPack = pack.category == "custom"
        if (isCustomPack) {
            h.btnFavorite.visibility = View.GONE
            h.btnDelete.visibility = View.GONE
            h.btnDeletePack.visibility = View.VISIBLE
            h.btnDeletePack.setColorFilter(android.graphics.Color.parseColor("#FF6B6B"))
            h.btnDeletePack.setOnClickListener { onDeleteClick?.invoke(pack) }
        } else {
            h.btnFavorite.visibility = View.VISIBLE
            h.btnDelete.visibility = View.GONE
            h.btnDeletePack.visibility = View.GONE
        }

        // Premium badge ve taç
        val isPremiumPack = pack.isPremium
        // hasAccess yukarıda tanımlandı

        h.crownIcon.visibility = if (isPremiumPack) View.VISIBLE else View.GONE
        h.premiumContainer.visibility = if (isPremiumPack && !hasAccess && !isInstalled) View.VISIBLE else View.GONE

        // Pack type badge (Animated/Static)
        if (isCustomPack) {
            h.packTypeBadge.visibility = View.VISIBLE
            if (pack.isAnimated) {
                h.packTypeBadge.text = "ANIMATED"
                h.packTypeBadge.setBackgroundColor(android.graphics.Color.parseColor("#FF6B35"))
            } else {
                h.packTypeBadge.text = "STATIC"
                h.packTypeBadge.setBackgroundColor(android.graphics.Color.parseColor("#4ECDC4"))
            }
        } else {
            h.packTypeBadge.visibility = View.GONE
        }

        // Yeni badge
        val isNew = isPackNew(pack.createdAt)
        h.newBadge.visibility = if (isNew) View.VISIBLE else View.GONE

        // Zaman bilgisi
        h.timeAgo.text = getTimeAgo(pack.createdAt, context)

        // Ekle / Paylaş butonu
        if (isInstalled) {
            h.btnAdd.text = ""
            h.btnAdd.setIconResource(R.drawable.ic_share)
            h.btnAdd.iconTint = androidx.core.content.ContextCompat.getColorStateList(context, R.color.share_blue)
            h.btnAdd.background = null
            h.btnAdd.backgroundTintList = null
            h.btnAdd.iconPadding = 0
            h.btnAdd.iconGravity = com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START
            h.btnAdd.minWidth = 0
            h.btnAdd.layoutParams.width = (36 * context.resources.displayMetrics.density).toInt()
            h.btnAdd.layoutParams.height = (36 * context.resources.displayMetrics.density).toInt()
        } else {
            h.btnAdd.text = context.getString(R.string.btn_add_short)
            h.btnAdd.setIconResource(R.drawable.ic_whatsapp_small)
            h.btnAdd.iconTint = androidx.core.content.ContextCompat.getColorStateList(context, R.color.accent)
            h.btnAdd.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.accent))
            h.btnAdd.background = null 
            h.btnAdd.backgroundTintList = null
            h.btnAdd.iconPadding = (6 * context.resources.displayMetrics.density).toInt()
            h.btnAdd.iconGravity = com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START
            h.btnAdd.layoutParams.width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            h.btnAdd.layoutParams.height = (32 * context.resources.displayMetrics.density).toInt()
        }

        h.btnAdd.setOnClickListener {
            if (isInstalled) {
                // Paylaşma işlemi
                val shareText = "Check out this '${pack.localizedName}' sticker pack! \n\nDownload Sticky app: https://play.google.com/store/apps/details?id=${context.packageName}"
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_SUBJECT, pack.localizedName)
                    putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                }
                context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.share_pack)))
            } else {
                if (onAddClick != null) {
                    onAddClick.invoke(pack)
                } else {
                    click(pack)
                }
            }
        }

        // İndirme sayısı (artık pub içinde gösteriliyor, bu gizli element)
        h.downloadCount.visibility = View.GONE

        // Favori butonu (yeni - üst satırda)
        if (!isCustomPack) {
            val isFavorite = PreferencesHelper.isPackFavorite(context, pack.id)
            h.btnFavoriteNew.visibility = View.VISIBLE
            h.btnFavoriteNew.setImageResource(
                if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border
            )
            h.btnFavoriteNew.setOnClickListener {
                val newFavState = PreferencesHelper.toggleFavorite(context, pack.id)
                h.btnFavoriteNew.setImageResource(
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
        } else {
            h.btnFavoriteNew.visibility = View.GONE
        }

        // Eski favori butonu (gizli tutulacak - uyumluluk için)
        h.btnFavorite.visibility = View.GONE

        // Çıkartma önizlemeleri yükle
        loadStickerPreviews(h, pack, isCustomPack)
    }

    private fun loadStickerPreviews(h: VH, pack: Pack, isCustomPack: Boolean) {
        val context = h.itemView.context
        val container = h.stickerPreviewContainer

        val displayMetrics = context.resources.displayMetrics
        val stickerSize = (64 * displayMetrics.density).toInt()
        val margin = (8 * displayMetrics.density).toInt()
        val glideOverrideSize = 96 // Daha küçük = daha hızlı

        val stickersToShow = pack.stickers.take(5)
        val currentChildCount = container.childCount
        val neededCount = stickersToShow.size

        // View'ları yeniden kullan - sadece gerekirse yeni oluştur
        if (neededCount > currentChildCount) {
            for (i in currentChildCount until neededCount) {
                val previewView = ImageView(context).apply {
                    val params = LinearLayout.LayoutParams(stickerSize, stickerSize)
                    params.marginEnd = margin
                    layoutParams = params
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
                container.addView(previewView)
            }
        } else if (neededCount < currentChildCount) {
            container.removeViews(neededCount, currentChildCount - neededCount)
        }

        // Son elemanın margin'ini kaldır
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i) as? ImageView ?: continue
            (child.layoutParams as? LinearLayout.LayoutParams)?.marginEnd =
                if (i == container.childCount - 1) 0 else margin
        }

        if (stickersToShow.isEmpty()) {
            container.visibility = View.GONE
            return
        }
        container.visibility = View.VISIBLE

        // Glide request manager - tek seferde al
        val glide = Glide.with(context)

        stickersToShow.forEachIndexed { index, sticker ->
            val previewView = container.getChildAt(index) as? ImageView ?: return@forEachIndexed
            previewView.setBackgroundResource(R.drawable.ic_sticker_placeholder)
            previewView.setOnClickListener { click(pack) }

            val clearBgListener = object : RequestListener<Drawable> {
                override fun onLoadFailed(e: GlideException?, m: Any?, t: Target<Drawable>, f: Boolean) = false
                override fun onResourceReady(r: Drawable, m: Any, t: Target<Drawable>, d: DataSource, f: Boolean): Boolean {
                    previewView.background = null
                    return false
                }
            }

            if (isCustomPack) {
                val stickerFile = CustomStickerManager.getStickerFile(context, pack.id, sticker.file)
                if (stickerFile != null && stickerFile.exists()) {
                    glide.load(stickerFile)
                        .priority(Priority.IMMEDIATE)
                        .diskCacheStrategy(DiskCacheStrategy.DATA)
                        .dontAnimate()
                        .listener(clearBgListener)
                        .into(previewView)
                }
            } else {
                // URL öncelikli yükleme (RegionalAdapter gibi)
                var urlToLoad = sticker.url
                if (urlToLoad.isEmpty() && pack.storagePath.isNotEmpty()) {
                    urlToLoad = StickerRepository.getStickerDirectUrl(pack.id, sticker.file, pack.storagePath)
                }

                when {
                    urlToLoad.isNotEmpty() -> {
                        glide.load(urlToLoad)
                            .priority(Priority.IMMEDIATE)
                            .diskCacheStrategy(DiskCacheStrategy.DATA)
                            .dontAnimate()
                            .listener(clearBgListener)
                            .into(previewView)
                    }
                    else -> {
                        val cachedSticker = StickerRepository.getCachedStickerPath(context, pack.id, sticker.file)
                        if (cachedSticker.exists() && cachedSticker.length() > 0) {
                            glide.load(cachedSticker)
                                .signature(ObjectKey(cachedSticker.lastModified()))
                                .priority(Priority.IMMEDIATE)
                                .diskCacheStrategy(DiskCacheStrategy.DATA)
                                .dontAnimate()
                                .listener(clearBgListener)
                                .into(previewView)
                        } else {
                            val assetPath = "file:///android_asset/${pack.id}/${sticker.file}"
                            glide.load(android.net.Uri.parse(assetPath))
                                .priority(Priority.IMMEDIATE)
                                .diskCacheStrategy(DiskCacheStrategy.DATA)
                                .dontAnimate()
                                .listener(clearBgListener)
                                .into(previewView)
                        }
                    }
                }
            }
        }
    }

    private fun getTimeAgo(createdAt: String, context: android.content.Context): String {
        if (createdAt.isEmpty()) return ""
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val createdDate = format.parse(createdAt) ?: return ""
            val now = Date()
            val diffInMillis = now.time - createdDate.time

            val minutes = TimeUnit.MILLISECONDS.toMinutes(diffInMillis)
            val hours = TimeUnit.MILLISECONDS.toHours(diffInMillis)
            val days = TimeUnit.MILLISECONDS.toDays(diffInMillis)
            val weeks = days / 7
            val months = days / 30
            val years = days / 365

            when {
                minutes < 60 -> "${minutes}m"
                hours < 24 -> "${hours}h"
                days < 7 -> "${days}d"
                weeks < 4 -> "${weeks}w"
                months < 12 -> "${months}mo"
                else -> "${years}y"
            }
        } catch (e: Exception) {
            ""
        }
    }

    override fun getItemCount() = items.size

    fun updateList(newList: List<Any>) {
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = newList.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val old = items[oldItemPosition]
                val new = newList[newItemPosition]
                if (old is Pack && new is Pack) return old.id == new.id
                return old == new
            }
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return items[oldItemPosition] == newList[newItemPosition]
            }
        })
        items = newList
        diffResult.dispatchUpdatesTo(this)
    }

    fun getItems(): List<Any> = items

    fun updateListWithDiff(newList: List<Any>, diffResult: DiffUtil.DiffResult) {
        items = newList
        diffResult.dispatchUpdatesTo(this)
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
            count >= 1000000 -> String.format("%.1fM", count / 1000000.0)
            count >= 1000 -> String.format("%.1fK", count / 1000.0)
            else -> count.toString()
        }
    }
}
