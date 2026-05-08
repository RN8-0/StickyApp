package com.sticly

import android.graphics.BitmapFactory
import android.util.Log
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
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
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
    private val onAddClick: ((Pack) -> Unit)? = null,
    private val onPublisherClick: ((Pack) -> Unit)? = null,
    private val onShareClick: ((Pack) -> Unit)? = null,
    private val onLikeClick: ((Pack, VH) -> Unit)? = null
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
        // Calculate sticker size to fit exactly STICKER_PREVIEW_COUNT in screen width
        // Account for: RecyclerView paddingHorizontal=8dp (16dp total) + item paddingHorizontal=16dp (32dp total)
        val screenWidthDp = (context.resources.displayMetrics.widthPixels / density).coerceAtMost(620f)
        val totalPaddingDp = 48f // 16dp RV padding + 32dp item padding
        val totalMarginDp = (STICKER_PREVIEW_COUNT - 1) * 8f
        val availableDp = screenWidthDp - totalPaddingDp - totalMarginDp
        val stickerDp = (availableDp / STICKER_PREVIEW_COUNT).coerceIn(40f, 72f)
        stickerSizePx = (stickerDp * density).toInt()
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
        val item = items.getOrNull(position)
        return when (item) {
            is Pack -> item.id.hashCode().toLong()
            is BannerAdPlaceholder -> -(item.slotIndex.toLong() + 1)
            else -> position.toLong()
        }
    }

    companion object {
        private const val STICKER_PREVIEW_COUNT = 5
        private const val GLIDE_OVERRIDE_SIZE = 256

        // Single instance. DateFormat is NOT thread-safe, but PackAdapter only runs on Main Thread
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        // Native ad cache keyed by slotIndex — one request per slot, served from cache on rebind
        private val nativeAdCache = android.util.SparseArray<NativeAd>()
        // Slots currently waiting for an ad response — prevents duplicate requests
        private val pendingSlots = mutableSetOf<Int>()

        /** Destroy all cached NativeAd objects and reset slot state. Call when ads are no longer needed. */
        fun clearNativeAdCache() {
            for (i in 0 until nativeAdCache.size()) {
                nativeAdCache.valueAt(i).destroy()
            }
            nativeAdCache.clear()
            pendingSlots.clear()
        }
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tray: ImageView? = v.findViewById(R.id.tray)
        val name: TextView? = v.findViewById(R.id.name)
        val pub: TextView? = v.findViewById(R.id.pub)
        val publisherPhoto: ImageView? = v.findViewById(R.id.publisherPhoto)
        val count: TextView? = v.findViewById(R.id.count)
        val checkIcon: ImageView? = v.findViewById(R.id.checkIcon)
        val crownIcon: ImageView? = v.findViewById(R.id.crownIcon)
        val installedBadge: TextView? = v.findViewById(R.id.installedBadge)
        val premiumContainer: View? = v.findViewById(R.id.premiumContainer)
        val packTypeBadge: TextView? = v.findViewById(R.id.packTypeBadge)
        val newBadge: TextView? = v.findViewById(R.id.newBadge)
        val btnFavorite: ImageButton? = v.findViewById(R.id.btnFavorite)
        val btnFavoriteNew: ImageButton? = v.findViewById(R.id.btnFavoriteNew)
        val btnDelete: ImageButton? = v.findViewById(R.id.btnDelete)
        val btnDeletePack: ImageButton? = v.findViewById(R.id.btnDeletePack)
        val btnSharePackItem: ImageButton? = v.findViewById(R.id.btnSharePackItem)
        val likeButtonContainer: android.view.ViewGroup? = v.findViewById(R.id.likeButtonContainer)
        val btnItemLike: ImageButton? = v.findViewById(R.id.btnItemLike)
        val tvItemLikeCount: TextView? = v.findViewById(R.id.tvItemLikeCount)
        val downloadCount: TextView? = v.findViewById(R.id.downloadCount)
        val timeAgo: TextView? = v.findViewById(R.id.timeAgo)
        val btnAdd: MaterialButton? = v.findViewById(R.id.btnAdd)
        val stickerPreviewContainer: LinearLayout? = v.findViewById(R.id.stickerPreviewContainer)
    }



    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is BannerAdPlaceholder -> 1
            else -> 0
        }
    }

    override fun onCreateViewHolder(p: ViewGroup, vt: Int): VH {
        return if (vt == 1) {
            VH(LayoutInflater.from(p.context).inflate(R.layout.item_ad_banner, p, false))
        } else {
            VH(LayoutInflater.from(p.context).inflate(R.layout.item_pack, p, false))
        }
    }

    override fun onBindViewHolder(holder: VH, pos: Int) {
        val item = items[pos]
        if (item is BannerAdPlaceholder) {
            val slotIndex = item.slotIndex
            val adView = holder.itemView as? NativeAdView ?: return
            val content = adView.findViewById<View>(R.id.adContent)
            val placeholder = adView.findViewById<View>(R.id.adPlaceholder)

            // Tag the ViewHolder with the current slot index so async callbacks can
            // verify the ViewHolder hasn't been recycled to a different slot.
            adView.tag = slotIndex

            // Serve from cache — no new network request needed
            val cachedAd = nativeAdCache[slotIndex]
            if (cachedAd != null) {
                populateNativeAdIntoView(adView, cachedAd, content, placeholder)
                return
            }

            // Request already in flight for this slot — hide and wait
            if (pendingSlots.contains(slotIndex)) {
                adView.visibility = View.GONE
                adView.layoutParams.height = 0
                content?.visibility = View.GONE
                placeholder?.visibility = View.GONE
                return
            }

            // First request for this slot
            pendingSlots.add(slotIndex)
            adView.visibility = View.GONE
            adView.layoutParams.height = 0
            content?.visibility = View.GONE
            placeholder?.visibility = View.GONE

            val adLoader = AdLoader.Builder(holder.itemView.context, AdManager.FEED_AD_ID)
                .forNativeAd { nativeAd ->
                    nativeAdCache.put(slotIndex, nativeAd)
                    pendingSlots.remove(slotIndex)
                    // Only update the view if this ViewHolder is still bound to this slot
                    if (adView.tag == slotIndex) {
                        populateNativeAdIntoView(adView, nativeAd, content, placeholder)
                    }
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.e("PackAdapter", "Feed ad failed slot=$slotIndex: ${error.message} (code=${error.code})")
                        pendingSlots.remove(slotIndex)
                        if (adView.tag == slotIndex) {
                            adView.visibility = View.GONE
                            adView.layoutParams.height = 0
                        }
                    }
                })
                .withNativeAdOptions(
                    com.google.android.gms.ads.nativead.NativeAdOptions.Builder()
                        .setAdChoicesPlacement(com.google.android.gms.ads.nativead.NativeAdOptions.ADCHOICES_TOP_RIGHT)
                        .setRequestMultipleImages(false)
                        .build()
                )
                .build()
            adLoader.loadAd(AdRequest.Builder().build())
            return
        }
        val context = holder.itemView.context
        ensureDensityInit(context)
        val pack = items[pos] as Pack
        bindPack(holder, pack)
    }

    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        if (holder.itemView is NativeAdView) {
            // Clear the slot tag — the companion-object cache tracks ad availability,
            // so the next bind will correctly serve from cache or skip a pending request.
            holder.itemView.tag = null
            return
        }
        val container = holder.stickerPreviewContainer ?: return
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
            holder.tray?.let { glide.clear(it); it.setImageDrawable(null) }
        } catch (_: Exception) { }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        clearNativeAdCache()
    }

    /** Populates a NativeAdView with data from a loaded NativeAd and makes it visible. */
    private fun populateNativeAdIntoView(
        adView: NativeAdView,
        nativeAd: NativeAd,
        content: View?,
        placeholder: View?
    ) {
        adView.headlineView = adView.findViewById(R.id.ad_headline)
        adView.bodyView = adView.findViewById(R.id.ad_body)
        adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)
        adView.iconView = adView.findViewById(R.id.ad_app_icon)
        // Register the MediaView so AdMob SDK is satisfied, but keep it 0×0 hidden
        adView.mediaView = adView.findViewById<MediaView>(R.id.ad_media)

        (adView.headlineView as? TextView)?.text = nativeAd.headline
        (adView.bodyView as? TextView)?.text = nativeAd.body
        (adView.callToActionView as? Button)?.text = nativeAd.callToAction
        nativeAd.icon?.drawable?.let { (adView.iconView as? ImageView)?.setImageDrawable(it) }
        // MediaView intentionally kept hidden — compact row layout (icon + text + CTA only)

        adView.setNativeAd(nativeAd)
        placeholder?.visibility = View.GONE
        content?.visibility = View.VISIBLE
        adView.visibility = View.VISIBLE
        val lp = adView.layoutParams
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
        adView.layoutParams = lp
    }

    private val timeAgoCache = java.util.concurrent.ConcurrentHashMap<String, String>(64)

    private fun bindPack(h: VH, pack: Pack) {
        val context = h.itemView.context

        h.name?.text = pack.localizedName

        // Yayıncı + çıkartma sayısı + indirme sayısı tek satırda (like ayrı butona taşındı)
        val stickerCount = pack.stickers.size
        val pubText = StringBuilder(pack.pub.length + 60)
        pubText.append(pack.pub)
        pubText.append(" • ").append(stickerCount).append(" stickers")
        if (pack.downloadCount > 0) {
            pubText.append(" • ").append(formatDownloadCount(pack.downloadCount)).append(" downloads")
        }
        if (pack.viewCount > 0) {
            pubText.append(" • ").append(formatCompactNumber(pack.viewCount)).append(" views")
        }
        h.pub?.text = pubText

        // Publisher photo
        if (pack.publisherPhotoUrl.isNotBlank()) {
            h.publisherPhoto?.visibility = View.VISIBLE
            getGlide(context).load(pack.publisherPhotoUrl)
                .circleCrop()
                .placeholder(R.drawable.ic_person)
                .into(h.publisherPhoto!!)
        } else {
            h.publisherPhoto?.visibility = View.GONE
            h.publisherPhoto?.let { getGlide(context).clear(it) }
        }

        h.pub?.setOnClickListener { click(pack) }
        h.publisherPhoto?.setOnClickListener { click(pack) }

        h.itemView.setOnClickListener { click(pack) }

        val isInstalled = PreferencesHelper.isPackInstalled(context, pack.id)
        val isCustomPack = pack.category == "custom"
        
        h.checkIcon?.visibility = if (isInstalled) View.VISIBLE else View.GONE
        h.installedBadge?.visibility = if (isInstalled) View.VISIBLE else View.GONE

        if (isCustomPack) {
            h.btnFavorite?.visibility = View.GONE
            h.btnDelete?.visibility = View.GONE
            h.btnDeletePack?.visibility = View.VISIBLE
            h.btnDeletePack?.setColorFilter(0xFFFF6B6B.toInt())
            h.btnDeletePack?.setOnClickListener { onDeleteClick?.invoke(pack) }
            h.btnSharePackItem?.visibility = View.VISIBLE
            h.btnSharePackItem?.setOnClickListener { onShareClick?.invoke(pack) }
            h.likeButtonContainer?.visibility = View.GONE
            h.stickerPreviewContainer?.gravity = android.view.Gravity.START
        } else {
            h.btnFavorite?.visibility = View.GONE
            h.btnDelete?.visibility = View.GONE
            h.btnDeletePack?.visibility = View.GONE
            h.btnSharePackItem?.visibility = View.GONE
            // Like butonu her zaman görünür; sayı sadece > 0 ise gösterilir
            h.likeButtonContainer?.visibility = View.VISIBLE
            val isLiked = SocialRepository.isLocallyLiked(context, pack.id)
            h.btnItemLike?.setImageResource(if (isLiked) R.drawable.ic_thumb_up else R.drawable.ic_thumb_up_outline)
            val likeColor = androidx.core.content.ContextCompat.getColor(context, if (isLiked) R.color.primary else R.color.text_hint)
            h.btnItemLike?.setColorFilter(likeColor)
            if (pack.likeCount > 0) {
                h.tvItemLikeCount?.text = formatCompactNumber(pack.likeCount)
                h.tvItemLikeCount?.setTextColor(androidx.core.content.ContextCompat.getColor(context, if (isLiked) R.color.primary else R.color.text_hint))
                h.tvItemLikeCount?.visibility = View.VISIBLE
            } else {
                h.tvItemLikeCount?.text = ""
                h.tvItemLikeCount?.visibility = View.GONE
            }
            val likeClick = View.OnClickListener { onLikeClick?.invoke(pack, h) }
            h.likeButtonContainer?.setOnClickListener(likeClick)
            h.btnItemLike?.setOnClickListener(likeClick)
            h.stickerPreviewContainer?.gravity = android.view.Gravity.CENTER
        }

        h.crownIcon?.visibility = if (pack.isPremium) View.VISIBLE else View.GONE
        h.premiumContainer?.visibility = View.GONE

        if (isCustomPack) {
            h.packTypeBadge?.visibility = View.VISIBLE
            if (pack.isAnimated) {
                h.packTypeBadge?.text = "ANIMATED"
                h.packTypeBadge?.setBackgroundColor(0xFFFF6B35.toInt())
            } else {
                h.packTypeBadge?.text = "STATIC"
                h.packTypeBadge?.setBackgroundColor(0xFF4ECDC4.toInt())
            }
        } else {
            h.packTypeBadge?.visibility = View.GONE
        }

        h.newBadge?.visibility = if (isPackNew(pack.createdAt)) View.VISIBLE else View.GONE
        h.timeAgo?.text = timeAgoCache.getOrPut(pack.id) { getTimeAgo(pack.createdAt, context) }

        // Ekle / Paylaş butonu
        val btn = h.btnAdd
        if (btn != null) {
            if (isInstalled) {
                btn.text = ""
                btn.setIconResource(R.drawable.ic_share)
                btn.iconTint = androidx.core.content.ContextCompat.getColorStateList(context, R.color.share_blue)
                btn.background = null
                btn.backgroundTintList = null
                btn.iconPadding = 0
                btn.minWidth = 0
                btn.layoutParams?.width = btnAdd36Px
                btn.layoutParams?.height = btnAdd36Px
            } else {
                val colorRes = if (pack.isPremium) R.color.premium_gold else R.color.accent
                val resolvedColor = androidx.core.content.ContextCompat.getColor(context, colorRes)
                btn.text = context.getString(R.string.btn_add_short)
                btn.setIconResource(R.drawable.ic_whatsapp_small)
                btn.iconTint = android.content.res.ColorStateList.valueOf(resolvedColor)
                btn.setTextColor(resolvedColor)
                btn.setStrokeColorResource(colorRes)
                btn.background = null
                btn.backgroundTintList = null
                btn.iconPadding = iconPadding6Px
                btn.layoutParams?.width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                btn.layoutParams?.height = btnAdd32Px
            }
            btn.setOnClickListener {
                if (isInstalled) {
                    val shareText = "Check out this '${pack.localizedName}' sticker pack! \n\nDownload Sticky app: https://play.google.com/store/apps/details?id=${context.packageName}"
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_SUBJECT, pack.localizedName)
                        putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.share_pack)))
                } else {
                    if (onAddClick != null) onAddClick.invoke(pack) else click(pack)
                }
            }
        }

        h.downloadCount?.visibility = View.GONE

        // Favorite button
        if (!isCustomPack) {
            h.btnFavoriteNew?.visibility = View.VISIBLE
            val isFavorite = PreferencesHelper.isPackFavorite(context, pack.id)
            h.btnFavoriteNew?.setImageResource(if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border)
            h.btnFavoriteNew?.setOnClickListener {
                val favorite = PreferencesHelper.toggleFavorite(context, pack.id)
                h.btnFavoriteNew?.setImageResource(if (favorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border)
                if (favorite) {
                    StickerRepository.incrementFavoriteCount(pack.id, pack.isPremium)
                    Toast.makeText(context, R.string.added_to_favorites, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, R.string.removed_from_favorites, Toast.LENGTH_SHORT).show()
                }
                onFavoriteChanged?.invoke()
            }
        } else {
            h.btnFavoriteNew?.visibility = View.GONE
        }

        // Çıkartma önizlemeleri yükle
        loadStickerPreviews(h, pack, isCustomPack)
    }

    private fun loadStickerPreviews(h: VH, pack: Pack, isCustomPack: Boolean) {
        val context = h.itemView.context
        val container = h.stickerPreviewContainer
        val isAnimatedPack = pack.isAnimated

        val stickersToShow = pack.stickers.take(STICKER_PREVIEW_COUNT)
        val neededCount = stickersToShow.size
        
        // Always ensure container has exactly STICKER_PREVIEW_COUNT views
        val currentChildCount = container?.childCount ?: 0
        if (currentChildCount < STICKER_PREVIEW_COUNT) {
            for (i in currentChildCount until STICKER_PREVIEW_COUNT) {
                val previewView = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(stickerSizePx, stickerSizePx).also {
                        it.marginEnd = if (i == STICKER_PREVIEW_COUNT - 1) 0 else stickerMarginPx
                    }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
                container?.addView(previewView)
            }
        }

        if (stickersToShow.isEmpty()) {
            container?.visibility = View.GONE
            return
        }
        container?.visibility = View.VISIBLE

        val glide = getGlide(context)

        // Loop over all STICKER_PREVIEW_COUNT views
        for (i in 0 until STICKER_PREVIEW_COUNT) {
            val previewView = container?.getChildAt(i) as? ImageView ?: continue
            
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

            if (isCustomPack) {
                val stickerFile = CustomStickerManager.getCustomStickerPath(context, pack.id, sticker.file)
                val req = glide.load(stickerFile)
                    .override(GLIDE_OVERRIDE_SIZE)
                    .priority(Priority.NORMAL)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .signature(com.bumptech.glide.signature.ObjectKey("${pack.id}_${pack.version}_${sticker.file}"))
                    .dontTransform()
                    .listener(clearBgListener)
                if (!isAnimatedPack) req.dontAnimate()
                req.into(previewView)
            } else {
                var urlToLoad = sticker.url
                if (urlToLoad.isEmpty() && pack.storagePath.isNotEmpty()) {
                    urlToLoad = StickerRepository.getStickerDirectUrl(pack.id, sticker.file, pack.storagePath)
                }

                if (urlToLoad.isNotEmpty()) {
                    val req = glide.load(urlToLoad)
                        .override(GLIDE_OVERRIDE_SIZE)
                        .priority(Priority.NORMAL)
                        .diskCacheStrategy(DiskCacheStrategy.DATA)
                        .dontTransform()
                        .listener(clearBgListener)
                    if (!isAnimatedPack) req.dontAnimate()
                    req.into(previewView)
                } else {
                    val cachedSticker = StickerRepository.getCachedStickerPath(context, pack.id, sticker.file)
                    val assetPath = "file:///android_asset/${pack.id}/${sticker.file}"
                    
                    val req = glide.load(cachedSticker)
                        .override(GLIDE_OVERRIDE_SIZE)
                        .priority(Priority.NORMAL)
                        .diskCacheStrategy(DiskCacheStrategy.DATA)
                        .dontTransform()
                        .listener(clearBgListener)
                        .error(
                            glide.load(android.net.Uri.parse(assetPath))
                                .override(GLIDE_OVERRIDE_SIZE)
                                .priority(Priority.LOW)
                                .diskCacheStrategy(DiskCacheStrategy.DATA)
                                .dontTransform()
                                .listener(clearBgListener)
                        )
                    if (!isAnimatedPack) req.dontAnimate()
                    req.into(previewView)
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
                if (old is BannerAdPlaceholder && new is BannerAdPlaceholder) return old.slotIndex == new.slotIndex
                return false
            }
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val old = items[oldItemPosition]
                val new = newList[newItemPosition]
                if (old is BannerAdPlaceholder && new is BannerAdPlaceholder) return true
                return old == new
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

    private fun formatCompactNumber(count: Int): String {
        return when {
            count >= 1000000 -> String.format("%.1fM", count / 1000000.0)
            count >= 1000 -> String.format("%.1fK", count / 1000.0)
            else -> count.toString()
        }
    }
}
