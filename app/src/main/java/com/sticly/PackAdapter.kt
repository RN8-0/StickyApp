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
import com.bumptech.glide.RequestManager

class PackAdapter(
    private var items: List<Any>,
    private val click: (Pack) -> Unit,
    private val onFavoriteChanged: (() -> Unit)? = null,
    private val onDeleteClick: ((Pack) -> Unit)? = null,
    private val onAddClick: ((Pack) -> Unit)? = null
) : RecyclerView.Adapter<PackAdapter.VH>() {

    init {
        setHasStableIds(true)
    }

    // Density-dependent pixel values cached once per adapter instance
    private var stickerSizePx = 0
    private var stickerMarginPx = 0
    private var btnAddWidthPx = 0
    private var btnAdd36Px = 0
    private var btnAdd32Px = 0
    private var iconPadding6Px = 0
    private var densityInitialized = false
    private var glideManager: RequestManager? = null

    // Single shared listener to clear backgrounds and prevent unnecessary object allocations during scroll
    private val clearBgListener = object : com.bumptech.glide.request.RequestListener<Drawable> {
        override fun onLoadFailed(e: GlideException?, m: Any?, t: com.bumptech.glide.request.target.Target<Drawable>, f: Boolean) = false
        override fun onResourceReady(r: Drawable, m: Any, t: com.bumptech.glide.request.target.Target<Drawable>, d: com.bumptech.glide.load.DataSource, f: Boolean): Boolean {
            (t as? com.bumptech.glide.request.target.ImageViewTarget<*>)?.view?.background = null
            return false
        }
    }

    private fun ensureDensityInit(context: android.content.Context) {
        if (densityInitialized) return
        val density = context.resources.displayMetrics.density
        stickerSizePx = (64 * density).toInt()
        stickerMarginPx = (8 * density).toInt()
        btnAdd36Px = (36 * density).toInt()
        btnAdd32Px = (32 * density).toInt()
        iconPadding6Px = (6 * density).toInt()
        densityInitialized = true
    }

    private fun getGlide(context: android.content.Context): RequestManager {
        return glideManager ?: Glide.with(context).also { glideManager = it }
    }

    override fun getItemId(position: Int): Long {
        val item = items.getOrNull(position) as? Pack ?: return position.toLong()
        return item.id.hashCode().toLong()
    }

    companion object {
        private const val STICKER_PREVIEW_COUNT = 5
        private const val GLIDE_OVERRIDE_SIZE = 150

        // Single instance. DateFormat is NOT thread-safe, but PackAdapter only runs on Main Thread
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
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



    override fun getItemViewType(position: Int): Int = 0

    override fun onCreateViewHolder(p: ViewGroup, vt: Int): VH {
        return VH(LayoutInflater.from(p.context).inflate(R.layout.item_pack, p, false))
    }

    override fun onBindViewHolder(holder: VH, pos: Int) {
        val context = holder.itemView.context
        ensureDensityInit(context)
        val pack = items[pos] as Pack
        bindPack(holder, pack)
    }

    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        // Cancel all pending Glide requests to free memory and CPU
        val container = holder.stickerPreviewContainer
        val context = holder.itemView.context
        try {
            val glide = getGlide(context)
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)
                if (child is ImageView) {
                    glide.clear(child)
                    child.setImageDrawable(null)
                }
            }
            glide.clear(holder.tray)
            holder.tray.setImageDrawable(null)
        } catch (_: Exception) { }
    }

    private fun bindPack(h: VH, pack: Pack) {
        val context = h.itemView.context

        h.name.text = pack.localizedName

        // Yayıncı + çıkartma sayısı + indirme sayısı tek satırda
        val stickerCount = pack.stickers.size
        val displayDownloadCount = pack.fakeDownloadBase + pack.downloadCount
        val pubText = StringBuilder(pack.pub.length + 40)
        pubText.append(pack.pub)
        pubText.append(" • ").append(stickerCount).append(" stickers")
        if (displayDownloadCount > 0) {
            pubText.append(" • ").append(formatDownloadCount(displayDownloadCount)).append(" downloads")
        }
        h.pub.text = pubText

        h.count.text = context.getString(R.string.sticker_count, stickerCount)
        h.itemView.setOnClickListener { click(pack) }

        // Read SharedPreferences ONCE, not 4 separate disk reads
        val hasAccess = PreferencesHelper.hasAccessToPack(context, pack.id)
        val isInstalled = PreferencesHelper.isPackInstalled(context, pack.id) && hasAccess
        val isCustomPack = pack.category == "custom"
        
        h.checkIcon.visibility = if (isInstalled) View.VISIBLE else View.GONE
        h.installedBadge.visibility = if (isInstalled) View.VISIBLE else View.GONE

        if (isCustomPack) {
            h.btnFavorite.visibility = View.GONE
            h.btnDelete.visibility = View.GONE
            h.btnDeletePack.visibility = View.VISIBLE
            h.btnDeletePack.setColorFilter(0xFFFF6B6B.toInt())
            h.btnDeletePack.setOnClickListener { onDeleteClick?.invoke(pack) }
        } else {
            h.btnFavorite.visibility = View.VISIBLE
            h.btnDelete.visibility = View.GONE
            h.btnDeletePack.visibility = View.GONE
        }

        h.crownIcon.visibility = View.GONE
        h.premiumContainer.visibility = View.GONE

        // Pack type badge
        if (isCustomPack) {
            h.packTypeBadge.visibility = View.VISIBLE
            if (pack.isAnimated) {
                h.packTypeBadge.text = "ANIMATED"
                h.packTypeBadge.setBackgroundColor(0xFFFF6B35.toInt())
            } else {
                h.packTypeBadge.text = "STATIC"
                h.packTypeBadge.setBackgroundColor(0xFF4ECDC4.toInt())
            }
        } else {
            h.packTypeBadge.visibility = View.GONE
        }

        // Yeni badge
        h.newBadge.visibility = if (isPackNew(pack.createdAt)) View.VISIBLE else View.GONE

        // Zaman bilgisi
        h.timeAgo.text = getTimeAgo(pack.createdAt, context)

        // Ekle / Paylaş butonu — minimize layout changes
        if (isInstalled) {
            if (h.btnAdd.layoutParams.width != btnAdd36Px || h.btnAdd.text != "") {
                h.btnAdd.text = ""
                h.btnAdd.setIconResource(R.drawable.ic_share)
                h.btnAdd.iconTint = androidx.core.content.ContextCompat.getColorStateList(context, R.color.share_blue)
                h.btnAdd.background = null
                h.btnAdd.backgroundTintList = null
                h.btnAdd.iconPadding = 0
                h.btnAdd.iconGravity = com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START
                h.btnAdd.minWidth = 0
                h.btnAdd.layoutParams.width = btnAdd36Px
                h.btnAdd.layoutParams.height = btnAdd36Px
            }
        } else {
            // Tüm paketler için standart "ADD" görümü
            val textStr = context.getString(R.string.btn_add_short)
            val colorRes = R.color.accent
            
            if (h.btnAdd.text != textStr || h.btnAdd.layoutParams.width == btnAdd36Px) {
                h.btnAdd.text = textStr
                h.btnAdd.setIconResource(R.drawable.ic_whatsapp_small)
                val resolvedColor = androidx.core.content.ContextCompat.getColor(context, colorRes)
                h.btnAdd.iconTint = android.content.res.ColorStateList.valueOf(resolvedColor)
                h.btnAdd.setTextColor(resolvedColor)
                h.btnAdd.setStrokeColorResource(colorRes)
                h.btnAdd.background = null 
                h.btnAdd.backgroundTintList = null
                h.btnAdd.iconPadding = iconPadding6Px
                h.btnAdd.iconGravity = com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START
                h.btnAdd.layoutParams.width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                h.btnAdd.layoutParams.height = btnAdd32Px
            } else {
                val resolvedColor = androidx.core.content.ContextCompat.getColor(context, colorRes)
                if (h.btnAdd.currentTextColor != resolvedColor) {
                    h.btnAdd.iconTint = android.content.res.ColorStateList.valueOf(resolvedColor)
                    h.btnAdd.setTextColor(resolvedColor)
                    h.btnAdd.setStrokeColorResource(colorRes)
                }
            }
        }

        h.btnAdd.setOnClickListener {
            if (isInstalled) {
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

        h.downloadCount.visibility = View.GONE

        // Favori butonu
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

        h.btnFavorite.visibility = View.GONE

        // Çıkartma önizlemeleri yükle
        loadStickerPreviews(h, pack, isCustomPack)
    }

    private fun loadStickerPreviews(h: VH, pack: Pack, isCustomPack: Boolean) {
        val context = h.itemView.context
        val container = h.stickerPreviewContainer

        val stickersToShow = pack.stickers.take(STICKER_PREVIEW_COUNT)
        val neededCount = stickersToShow.size
        
        // Always ensure container has exactly STICKER_PREVIEW_COUNT views
        val currentChildCount = container.childCount
        if (currentChildCount < STICKER_PREVIEW_COUNT) {
            for (i in currentChildCount until STICKER_PREVIEW_COUNT) {
                val previewView = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(stickerSizePx, stickerSizePx).also {
                        it.marginEnd = if (i == STICKER_PREVIEW_COUNT - 1) 0 else stickerMarginPx
                    }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
                container.addView(previewView)
            }
        }

        if (stickersToShow.isEmpty()) {
            container.visibility = View.GONE
            return
        }
        container.visibility = View.VISIBLE

        val glide = getGlide(context)

        // Loop over all STICKER_PREVIEW_COUNT views
        for (i in 0 until STICKER_PREVIEW_COUNT) {
            val previewView = container.getChildAt(i) as? ImageView ?: continue
            
            if (i >= neededCount) {
                previewView.visibility = View.GONE
                glide.clear(previewView)
                continue
            }
            
            previewView.visibility = View.VISIBLE
            val sticker = stickersToShow[i]
            
            // Reapply margin end properly if the visible count changes
            val params = previewView.layoutParams as? LinearLayout.LayoutParams
            val expectedMargin = if (i == neededCount - 1) 0 else stickerMarginPx
            if (params?.marginEnd != expectedMargin) {
                params?.marginEnd = expectedMargin
                previewView.layoutParams = params
            }

            previewView.setBackgroundResource(R.drawable.ic_sticker_placeholder)
            previewView.setOnClickListener { click(pack) }

            if (isCustomPack) {
                val stickerFile = CustomStickerManager.getCustomStickerPath(context, pack.id, sticker.file)
                glide.load(stickerFile)
                    .override(GLIDE_OVERRIDE_SIZE)
                    .priority(Priority.NORMAL)
                    .diskCacheStrategy(DiskCacheStrategy.DATA)
                    .dontTransform()
                    .dontAnimate()
                    .listener(clearBgListener)
                    .into(previewView)
            } else {
                var urlToLoad = sticker.url
                if (urlToLoad.isEmpty() && pack.storagePath.isNotEmpty()) {
                    urlToLoad = StickerRepository.getStickerDirectUrl(pack.id, sticker.file, pack.storagePath)
                }

                if (urlToLoad.isNotEmpty()) {
                    glide.load(urlToLoad)
                        .override(GLIDE_OVERRIDE_SIZE)
                        .priority(Priority.NORMAL)
                        .diskCacheStrategy(DiskCacheStrategy.DATA)
                        .dontTransform()
                        .dontAnimate()
                        .listener(clearBgListener)
                        .into(previewView)
                } else {
                    val cachedSticker = StickerRepository.getCachedStickerPath(context, pack.id, sticker.file)
                    val assetPath = "file:///android_asset/${pack.id}/${sticker.file}"
                    
                    glide.load(cachedSticker)
                        .override(GLIDE_OVERRIDE_SIZE)
                        .priority(Priority.NORMAL)
                        .diskCacheStrategy(DiskCacheStrategy.DATA)
                        .dontTransform()
                        .dontAnimate()
                        .listener(clearBgListener)
                        .error(
                            glide.load(android.net.Uri.parse(assetPath))
                                .override(GLIDE_OVERRIDE_SIZE)
                                .priority(Priority.LOW)
                                .diskCacheStrategy(DiskCacheStrategy.DATA)
                                .dontTransform()
                                .dontAnimate()
                                .listener(clearBgListener)
                        )
                        .into(previewView)
                }
            }
        }
    }

    private fun getTimeAgo(createdAt: String, context: android.content.Context): String {
        if (createdAt.isEmpty()) return ""
        return try {
            val createdDate = dateFormat.parse(createdAt) ?: return ""
            val diffInMillis = System.currentTimeMillis() - createdDate.time

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
            val date = dateFormat.parse(createdAt) ?: return false
            val diffInDays = (System.currentTimeMillis() - date.time) / (1000 * 60 * 60 * 24)
            diffInDays <= 7 // Return true if created in last 7 days
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
