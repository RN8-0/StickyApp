package com.sticly

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import android.view.LayoutInflater
import android.content.Context
import android.view.View
import android.widget.*
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.animation.OvershootInterpolator
import android.content.res.ColorStateList
import android.text.Editable
import android.text.TextWatcher
// import com.arthenica.ffmpegkit.FFmpegKit
// import com.arthenica.ffmpegkit.ReturnCode
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.Segmenter
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import com.yalantis.ucrop.UCrop
import com.yalantis.ucrop.UCropActivity
import android.view.MotionEvent
import android.graphics.drawable.BitmapDrawable
import android.view.Gravity
import android.view.ViewGroup
import androidx.core.view.children
import androidx.core.widget.addTextChangedListener
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.slider.Slider
import java.util.Locale
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*

class StickerMakerActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    private lateinit var toolbarTitle: TextView
    private lateinit var typeSelectionContainer: View
    private lateinit var editorContainer: View
    private lateinit var imagePreview: ImageView
    private lateinit var placeholderContainer: View
    private lateinit var placeholderIcon: ImageView
    private lateinit var placeholderText: TextView
    private lateinit var btnAddToPack: MaterialButton
    private lateinit var tvMediaInfo: TextView
    private lateinit var tvQueueProgress: TextView
    
    // Processing Overlay
    private lateinit var processingOverlay: View
    private lateinit var processingCircle: ProgressBar
    private lateinit var tvProcessingTitle: TextView
    private lateinit var tvProcessingSubtitle: TextView
    private lateinit var tvProcessingPercent: TextView
    private lateinit var processingIcon: ImageView
    private lateinit var adContainerMaker: FrameLayout
    
    // Modern Editor Tools
    private lateinit var stickerEditorView: FrameLayout
    private lateinit var toolsPanel: View
    private lateinit var toolCrop: View
    private lateinit var toolRemoveBg: View
    private lateinit var toolText: View
    private lateinit var toolEmoji: View
    private lateinit var toolReset: View
    private lateinit var toolChangeMedia: View
    private lateinit var toolBorder: View
    private lateinit var toolFlipH: View
    private lateinit var toolFlipV: View
    private lateinit var toolRotate: View
    private lateinit var toolFilters: View
    private lateinit var toolAdjustments: View
    private lateinit var toolBrush: View
    private lateinit var toolEraser: View

    private var drawingView: DrawingView? = null
    private var isDrawingActive = false

    private var borderSize = 0f
    private var currentBrightness = 0f
    private var currentContrast = 1f
    private var currentSaturation = 1f
    private var currentShadows = 0f
    private var currentHighlights = 0f
    private var currentWarmth = 0f
    private var currentFilterMatrix: ColorMatrix? = null
    private var selectedFont = Typeface.DEFAULT_BOLD

    private lateinit var segmenter: Segmenter

    private var selectedBitmap: Bitmap? = null
    private var originalBitmap: Bitmap? = null
    private var contentBitmap: Bitmap? = null
    private var photoUri: Uri? = null
    private var photoFile: File? = null
    private var backgroundRemoved = false
    private var borderColor = Color.WHITE
    
    private var selectedToolView: View? = null
    
    private var isAnimatedMode = false
    private var isProcessing = false
    private var targetPackId: String? = null
    
    private val selectedUris = mutableListOf<Uri>()
    private var currentUriIndex = -1
    private var currentMimeType: String? = null

    private var cachedMaskAlpha: Bitmap? = null
    private var maskSourceBitmap: Bitmap? = null
    
    // Undo History
    private val undoStack = mutableListOf<Bitmap>()
    private lateinit var toolUndo: View
    
    // Medya seçici - PickMultipleVisualMedia for better multi-selection support
    private val pickMultipleMedia = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(30) // Max 30 images
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedUris.clear()
            selectedUris.addAll(uris)
            currentUriIndex = 0
            loadMediaFromUri(selectedUris[0])
            updateQueueProgress()
        }
    }

    // Fallback for older devices
    private val pickMediaLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedUris.clear()
            selectedUris.addAll(uris)
            currentUriIndex = 0
            loadMediaFromUri(selectedUris[0])
            updateQueueProgress()
        }
    }

    // Live text preview view reference
    private var livePreviewTextView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sticker_maker)

        // ML Kit Segmenter
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()
        segmenter = Segmentation.getClient(options)

        targetPackId = intent.getStringExtra("packId")

        // UI Binding
        toolbarTitle = findViewById(R.id.toolbarTitle)
        typeSelectionContainer = findViewById(R.id.typeSelectionContainer)
        editorContainer = findViewById(R.id.editorContainer)
        imagePreview = findViewById(R.id.imagePreview)
        placeholderContainer = findViewById(R.id.placeholderContainer)
        placeholderIcon = findViewById(R.id.placeholderIcon)
        placeholderText = findViewById(R.id.placeholderText)
        btnAddToPack = findViewById(R.id.btnAddToPack)
        tvMediaInfo = findViewById(R.id.tvMediaInfo)
        stickerEditorView = findViewById(R.id.stickerEditorView)
        editorContainer = findViewById(R.id.editorContainer)
        typeSelectionContainer = findViewById(R.id.typeSelectionContainer)
        toolsPanel = editorContainer
        
        // Tool Views Binding
        toolChangeMedia = findViewById(R.id.toolChangeMedia)
        toolCrop = findViewById(R.id.toolCrop)
        toolRemoveBg = findViewById(R.id.toolRemoveBg)
        toolText = findViewById(R.id.toolText)
        toolEmoji = findViewById(R.id.toolEmoji)
        toolReset = findViewById(R.id.toolReset)
        toolBorder = findViewById(R.id.toolBorder)
        toolFlipH = findViewById(R.id.toolFlipH)
        toolFlipV = findViewById(R.id.toolFlipV)
        toolRotate = findViewById(R.id.toolRotate)
        toolFilters = findViewById(R.id.toolFilters)
        toolAdjustments = findViewById(R.id.toolAdjustments)
        toolBrush = findViewById(R.id.toolBrush)
        toolEraser = findViewById(R.id.toolEraser)
        toolUndo = findViewById(R.id.toolUndo)
        
        setupToolsUI()
        
        processingOverlay = findViewById(R.id.processingOverlay)
        processingCircle = findViewById(R.id.processingCircle)
        tvProcessingTitle = findViewById(R.id.tvProcessingTitle)
        tvProcessingSubtitle = findViewById(R.id.tvProcessingSubtitle)
        tvProcessingPercent = findViewById(R.id.tvProcessingPercent)
        adContainerMaker = findViewById(R.id.adContainerMaker)
        
        if (!PreferencesHelper.isPremium(this)) {
            AdManager.loadNativeAd(this, AdManager.NativeAdType.MAKER) { nativeAd ->
                val adView = layoutInflater.inflate(R.layout.item_ad_native, null) as com.google.android.gms.ads.nativead.NativeAdView
                AdManager.populateNativeAdView(nativeAd, adView)
                adContainerMaker.removeAllViews()
                adContainerMaker.addView(adView)
                adContainerMaker.visibility = View.VISIBLE
            }
        }
        
        toolUndo.setOnClickListener { undoSticker() }
        toolUndo.visibility = View.GONE // Start hidden

        // Explicitly bind the icon if needed, or check for typos
        findViewById<ImageView>(R.id.processingIcon)?.let { 
            processingIcon = it
        } ?: run {
            // Fallback or debug
        }

        val btnHome = findViewById<ImageButton>(R.id.btnHome)
        val cardStatic = findViewById<View>(R.id.cardStaticType)
        val cardAnimated = findViewById<View>(R.id.cardAnimatedType)

        // Başlangıç durumu
        if (targetPackId != null) {
            val pack = CustomStickerManager.getPackInfo(this, targetPackId!!)
            if (pack != null) {
                isAnimatedMode = pack.isAnimated
                enterEditorMode()
            }
        }

        // Back button removed, using system back or Home button
        
        btnHome.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        cardStatic.setOnClickListener {
            isAnimatedMode = false
            enterEditorMode()
        }

        placeholderContainer.setOnClickListener {
            openGallery()
        }

        cardAnimated.setOnClickListener {
            Toast.makeText(this, R.string.animated_sticker_coming_soon, Toast.LENGTH_SHORT).show()
        }

        // setupToolsActions() will handle media selection and background removal now.
        
        btnAddToPack.setOnClickListener {
            if (isProcessing) return@setOnClickListener
            if (!checkMediaGuard()) return@setOnClickListener
            
            // Capture the canvas if we have any stickers/text
            val viewWidth = stickerEditorView.width
            val viewHeight = stickerEditorView.height
            
            if (viewWidth <= 0 || viewHeight <= 0) {
                val fallback = selectedBitmap ?: originalBitmap
                if (fallback != null) {
                    showPackSelectionDialog(fallback)
                } else {
                    Toast.makeText(this, "Görsel yüklenemedi", Toast.LENGTH_SHORT).show()
                }
                return@setOnClickListener
            }

            try {
                // First ensure all stickers are correctly placed
                val captureBitmap = Bitmap.createBitmap(viewWidth, viewHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(captureBitmap)
                stickerEditorView.draw(canvas)
                
                showPackSelectionDialog(captureBitmap)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Hata: Görsel yakalanamadı", Toast.LENGTH_SHORT).show()
            }
        }

        setupToolsActions()
        setupFlipRotateTools()
        setupEdgeToEdge()
    }

    private fun saveToHistory() {
        selectedBitmap?.let { 
            if (undoStack.size >= 15) undoStack.removeAt(0)
            undoStack.add(it.copy(it.config ?: Bitmap.Config.ARGB_8888, false))
            toolUndo.visibility = View.VISIBLE
        }
    }

    private fun undoSticker() {
        if (undoStack.isNotEmpty()) {
            val last = undoStack.removeAt(undoStack.size - 1)
            selectedBitmap = last
            contentBitmap = last.copy(last.config ?: Bitmap.Config.ARGB_8888, true)
            imagePreview.setImageBitmap(selectedBitmap)
            if (undoStack.isEmpty()) toolUndo.visibility = View.GONE
            // Reset state to match the undoed bitmap
            borderSize = 0f
            currentBrightness = 0f
            currentContrast = 1f
            currentSaturation = 1f
            currentFilterMatrix = null
            applyAllEffects(isFull = true)
        }
    }

    private fun startCropIntent() {
        if (selectedBitmap == null) return
        
        saveToHistory() // Save before crop
        
        val cacheDir = File(cacheDir, "crop")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val destinationFile = File(cacheDir, "cropped_image_${System.currentTimeMillis()}.png")
        val sourceFile = File(cacheDir, "source_image.png")
        
        lifecycleScope.launch(Dispatchers.IO) {
            val out = FileOutputStream(sourceFile)
            // Use JPEG if background is NOT removed for much faster processing
            val format = if (backgroundRemoved) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            selectedBitmap?.compress(format, 90, out)
            out.close()
            
            withContext(Dispatchers.Main) {
                val options = UCrop.Options().apply {
                    setCompressionFormat(Bitmap.CompressFormat.PNG)
                    setFreeStyleCropEnabled(true)
                    setToolbarColor(ContextCompat.getColor(this@StickerMakerActivity, R.color.toolbar_bg))
                    setStatusBarColor(ContextCompat.getColor(this@StickerMakerActivity, R.color.toolbar_bg))
                    setActiveControlsWidgetColor(ContextCompat.getColor(this@StickerMakerActivity, R.color.accent))
                    setCircleDimmedLayer(false)
                    setHideBottomControls(false)
                    setToolbarTitle("Kırp")
                }

                UCrop.of(Uri.fromFile(sourceFile), Uri.fromFile(destinationFile))
                    .withMaxResultSize(1024, 1024)
                    .withOptions(options)
                    .withAspectRatio(1f, 1f)
                    .start(this@StickerMakerActivity)
            }
        }
    }


    private fun flattenStickersToBitmap() {
        val stickers = mutableListOf<View>()
        for (i in 0 until stickerEditorView.childCount) {
            val child = stickerEditorView.getChildAt(i)
            // imagePreview is at 0, placeholder at 1, tvMediaInfo at 2, DrawingView at 3
            if (i > 3) stickers.add(child)
        }

        if (stickers.isEmpty()) return

        ensureMutableBitmap()
        contentBitmap?.let { base ->
            val canvas = Canvas(base)
            val viewWidth = stickerEditorView.width.toFloat()
            val viewHeight = stickerEditorView.height.toFloat()
            val bmpWidth = base.width.toFloat()
            val bmpHeight = base.height.toFloat()
            val scaleX = bmpWidth / viewWidth
            val scaleY = bmpHeight / viewHeight
            
            stickers.forEach { sticker ->
                canvas.save()
                canvas.scale(scaleX, scaleY)
                sticker.draw(canvas)
                canvas.restore()
                stickerEditorView.removeView(sticker)
            }
            selectedBitmap = base
            imagePreview.setImageBitmap(selectedBitmap)
        }
    }

    private fun setupToolsUI() {
        // Change Media
        toolChangeMedia.findViewById<ImageView>(R.id.icon).setImageResource(R.drawable.ic_photo)
        toolChangeMedia.findViewById<TextView>(R.id.title).text = getString(R.string.change_media_short)

        // Crop
        toolCrop.findViewById<ImageView>(R.id.icon).setImageResource(R.drawable.ic_crop)
        toolCrop.findViewById<TextView>(R.id.title).text = getString(R.string.crop_short)

        // Remove Bg
        toolRemoveBg.findViewById<ImageView>(R.id.icon).setImageResource(R.drawable.ic_check_circle)
        toolRemoveBg.findViewById<TextView>(R.id.title).text = getString(R.string.remove_bg_short)

        // Text
        toolText.findViewById<ImageView>(R.id.icon).setImageResource(R.drawable.ic_text)
        toolText.findViewById<TextView>(R.id.title).text = getString(R.string.add_text_short)

        // Emoji
        toolEmoji.findViewById<ImageView>(R.id.icon).setImageResource(R.drawable.ic_emoji)
        toolEmoji.findViewById<TextView>(R.id.title).text = getString(R.string.add_emoji_short)

        // Reset
        toolReset.findViewById<ImageView>(R.id.icon).setImageResource(R.drawable.ic_delete)
        toolReset.findViewById<TextView>(R.id.title).text = getString(R.string.reset_short)

        // Border
        toolBorder.findViewById<ImageView>(R.id.icon).setImageResource(R.drawable.ic_border)
        toolBorder.findViewById<TextView>(R.id.title).text = getString(R.string.border_short)

        // Flip H
        toolFlipH.findViewById<ImageView>(R.id.icon).setImageResource(R.drawable.ic_back)
        toolFlipH.findViewById<ImageView>(R.id.icon).rotation = 180f
        toolFlipH.findViewById<TextView>(R.id.title).text = getString(R.string.flip_h_short)

        // Flip V
        toolFlipV.findViewById<ImageView>(R.id.icon).setImageResource(R.drawable.ic_back)
        toolFlipV.findViewById<ImageView>(R.id.icon).rotation = 270f
        toolFlipV.findViewById<TextView>(R.id.title).text = getString(R.string.flip_v_short)

        // Rotate
        toolRotate.findViewById<ImageView>(R.id.icon).setImageResource(R.drawable.ic_restore)
        toolRotate.findViewById<TextView>(R.id.title).text = getString(R.string.rotate_short)

        // Undo
        toolUndo.findViewById<ImageView>(R.id.icon).setImageResource(R.drawable.ic_back)
        toolUndo.findViewById<TextView>(R.id.title).text = "GERİ AL"

        // Filters
        toolFilters.findViewById<ImageView>(R.id.icon).setImageResource(R.drawable.ic_theme)
        toolFilters.findViewById<TextView>(R.id.title).text = getString(R.string.filter_short)

        // Adjustments
        toolAdjustments.findViewById<ImageView>(R.id.icon).setImageResource(R.drawable.ic_tune)
        toolAdjustments.findViewById<TextView>(R.id.title).text = getString(R.string.adjustments)

        // Brush
        toolBrush.findViewById<ImageView>(R.id.icon).setImageResource(R.drawable.ic_brush)
        toolBrush.findViewById<TextView>(R.id.title).text = getString(R.string.draw_short)

        // Eraser
        toolEraser.findViewById<ImageView>(R.id.icon).setImageResource(R.drawable.ic_eraser)
        toolEraser.findViewById<TextView>(R.id.title).text = getString(R.string.eraser_short)

        // All tools interaction
        val tools = listOf(toolChangeMedia, toolCrop, toolUndo, toolRemoveBg, toolBrush, toolEraser, toolText, toolEmoji, toolBorder, toolFlipH, toolFlipV, toolRotate, toolFilters, toolAdjustments)
        tools.forEach { tool ->
            tool.setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                }
                false
            }
        }
    }

    private fun setupToolsActions() {
        toolChangeMedia.setOnClickListener { openGallery() }

        toolCrop.setOnClickListener {
            if (!checkMediaGuard()) return@setOnClickListener
            startCropIntent()
        }

        toolBrush.setOnClickListener {
            if (!checkMediaGuard()) return@setOnClickListener
            updateToolSelection(it)
            toggleDrawingMode(false)
        }

        toolEraser.setOnClickListener {
            if (!checkMediaGuard()) return@setOnClickListener
            updateToolSelection(it)
            saveToHistory() // Save before flattening
            flattenStickersToBitmap()
            toggleDrawingMode(true)
        }

        toolText.setOnClickListener {
            if (!checkMediaGuard()) return@setOnClickListener
            updateToolSelection(it)
            showTextEditorDialog()
        }

        toolEmoji.setOnClickListener {
            if (!checkMediaGuard()) return@setOnClickListener
            updateToolSelection(it)
            showEmojiPicker()
        }
        
        toolRemoveBg.setOnClickListener {
            if (!checkMediaGuard()) return@setOnClickListener
            updateToolSelection(it)
            if (backgroundRemoved) undoRemoveBackground() else selectedBitmap?.let { b -> processRemoveBackground(b) }
        }
        
        toolBorder.setOnClickListener {
            if (!checkMediaGuard()) return@setOnClickListener
            updateToolSelection(it)
            showBorderEditor()
        }
        
        toolFilters.setOnClickListener {
            if (!checkMediaGuard()) return@setOnClickListener
            updateToolSelection(it)
            showFiltersDialog()
        }
        
        toolAdjustments.setOnClickListener {
            if (!checkMediaGuard()) return@setOnClickListener
            updateToolSelection(it)
            showAdjustmentsDialog()
        }

        toolReset.setOnClickListener { resetEditorContent() }
    }

    private fun updateToolSelection(view: View?) {
        val accentColor = ContextCompat.getColor(this, R.color.accent)
        val defaultColor = ContextCompat.getColor(this, R.color.text_primary)
        val tools = listOf(toolRemoveBg, toolText, toolEmoji, toolBorder, toolFlipH, toolFlipV, toolRotate, toolFilters, toolAdjustments, toolBrush, toolEraser)
        tools.forEach { tool ->
            tool.background = null
            tool.findViewById<ImageView>(R.id.icon)?.imageTintList = ColorStateList.valueOf(defaultColor)
        }
        view?.findViewById<ImageView>(R.id.icon)?.imageTintList = ColorStateList.valueOf(accentColor)
        selectedToolView = view

        // Pause drawing if switching tools
        if (view != toolBrush && view != toolEraser) {
            isDrawingActive = false
            drawingView?.setDrawingEnabled(false)
        }
    }

    private fun setupFlipRotateTools() {
        toolFlipH.setOnClickListener {
            if (!checkMediaGuard()) return@setOnClickListener
            contentBitmap?.let { bmp ->
                val matrix = Matrix().apply { postScale(-1f, 1f, bmp.width / 2f, bmp.height / 2f) }
                contentBitmap = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                selectedBitmap = contentBitmap
                imagePreview.setImageBitmap(selectedBitmap)
            }
        }

        toolFlipV.setOnClickListener {
            if (!checkMediaGuard()) return@setOnClickListener
            contentBitmap?.let { bmp ->
                val matrix = Matrix().apply { postScale(1f, -1f, bmp.width / 2f, bmp.height / 2f) }
                contentBitmap = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                selectedBitmap = contentBitmap
                imagePreview.setImageBitmap(selectedBitmap)
            }
        }

        toolRotate.setOnClickListener {
            if (!checkMediaGuard()) return@setOnClickListener
            contentBitmap?.let { bmp ->
                val matrix = Matrix().apply { postRotate(90f) }
                contentBitmap = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                selectedBitmap = contentBitmap
                imagePreview.setImageBitmap(selectedBitmap)
            }
        }
    }

    private fun toggleDrawingMode(isEraser: Boolean) {
        if (drawingView == null) {
            drawingView = DrawingView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            // Add DrawingView at index 3 (after imagePreview, placeholder, tvMediaInfo)
            // This ensures it is BEHIND any stickers added with addView()
            stickerEditorView.addView(drawingView, 3)
        }

        isDrawingActive = true
        drawingView?.apply {
            visibility = View.VISIBLE
            setEraserMode(isEraser)
            showDrawingSettingsDialog(isEraser)
        }
    }

    private fun showDrawingSettingsDialog(isEraser: Boolean) {
        val dialog = BottomSheetDialog(this, R.style.TransparentBottomSheetDialog)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 64, 64, 64)
            setBackgroundResource(R.drawable.bg_editor_container)
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.card_bg))
        }
        dialog.setContentView(container)

        val title = TextView(this).apply {
            text = if (isEraser) getString(R.string.eraser_short) else getString(R.string.draw_short)
            textSize = 20f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 32)
        }
        container.addView(title)

        // Brush Size
        val label = TextView(this).apply {
            text = "Boyut"
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            setPadding(0, 16, 0, 8)
        }
        container.addView(label)

        val slider = Slider(this).apply {
            valueFrom = 5f
            valueTo = 200f
            value = drawingView?.getBrushSize() ?: 50f
            thumbTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.accent))
            trackActiveTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.accent))
        }
        container.addView(slider)
        slider.addOnChangeListener { _, value, _ -> drawingView?.setBrushSize(value) }

        // Blur/Softness for Eraser
        if (isEraser) {
            val blurLabel = TextView(this).apply {
                text = "Yumuşaklık (Softness)"
                textSize = 14f
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                setPadding(0, 24, 0, 8)
            }
            container.addView(blurLabel)

            val blurSlider = Slider(this).apply {
                valueFrom = 0f
                valueTo = 100f
                value = drawingView?.getBlurSize() ?: 25f
                thumbTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.accent))
                trackActiveTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.accent))
            }
            container.addView(blurSlider)
            blurSlider.addOnChangeListener { _, value, _ -> drawingView?.setBlurSize(value) }
        }

        if (!isEraser) {
            val colorLabel = TextView(this).apply {
                text = "Renk"
                textSize = 14f
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                setPadding(0, 32, 0, 8)
            }
            container.addView(colorLabel)

            val colorScroll = HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
            }
            val colorLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            colorScroll.addView(colorLayout)
            container.addView(colorScroll)

            val colors = listOf(Color.BLACK, Color.WHITE, Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN, Color.MAGENTA, Color.parseColor("#FF6B6B"), Color.parseColor("#4ECDC4"))
            var selectedColorView: View? = null
            val currentBrushColor = drawingView?.drawColor ?: Color.BLACK
            colors.forEach { color ->
                val cv = FrameLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(100, 100).apply { setMargins(12, 12, 12, 12) }
                }
                val inner = View(this).apply {
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                    background = ContextCompat.getDrawable(context, R.drawable.bg_color_circle)
                    backgroundTintList = ColorStateList.valueOf(color)
                }
                cv.addView(inner)
                if (color == currentBrushColor) {
                    cv.foreground = ContextCompat.getDrawable(this@StickerMakerActivity, R.drawable.ic_check)
                    cv.foregroundGravity = Gravity.CENTER
                    selectedColorView = cv
                }
                cv.setOnClickListener {
                    selectedColorView?.foreground = null
                    cv.foreground = ContextCompat.getDrawable(this@StickerMakerActivity, R.drawable.ic_check)
                    cv.foregroundGravity = Gravity.CENTER
                    selectedColorView = cv
                    drawingView?.setBrushColor(color)
                }
                colorLayout.addView(cv)
            }
        }

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 48, 0, 0)
            gravity = Gravity.END
        }
        container.addView(footer)

        val btnUndo = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.undo)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setOnClickListener { drawingView?.undo() }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = 24 }
        }
        footer.addView(btnUndo)

        val btnOk = MaterialButton(this).apply {
            text = "Tamam"
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.accent))
            setOnClickListener { dialog.dismiss() }
        }
        footer.addView(btnOk)

        dialog.show()
    }

    class DrawingPath(val path: Path, val paint: Paint)

    private fun ensureMutableBitmap() {
        if (contentBitmap == null) {
            contentBitmap = originalBitmap?.copy(originalBitmap!!.config ?: Bitmap.Config.ARGB_8888, true)
        } else if (!contentBitmap!!.isMutable) {
            contentBitmap = contentBitmap!!.copy(contentBitmap!!.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }

    inner class DrawingView(context: Context) : View(context) {
        var drawColor = Color.BLACK
        private var brushSize = 50f
        private var blurSize = 25f
        private var isEraser = false
        private var isDrawingEnabled = true
        private val paths = mutableListOf<DrawingPath>()
        private var currentPath: Path? = null
        private var currentPaint: Paint? = null
        private var eraserCanvas: Canvas? = null

        init {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }

        fun setDrawingEnabled(enabled: Boolean) {
            isDrawingEnabled = enabled
        }

        fun commitDrawingsToContentBitmap() {
            if (paths.isEmpty()) return
            contentBitmap?.let { bmp ->
                val mutableBmp = if (bmp.isMutable) bmp else bmp.copy(bmp.config ?: Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(mutableBmp)
                
                // Draw all paths onto the actual content bitmap
                for (dp in paths) {
                    canvas.drawPath(dp.path, dp.paint)
                }
                
                contentBitmap = mutableBmp
                paths.clear()
                invalidate()
            }
        }

        fun setEraserMode(enabled: Boolean) {
            if (enabled && paths.isNotEmpty()) {
                // Commit all drawings before switching to eraser
                commitDrawingsToContentBitmap()
                applyAllEffects()
            }
            isEraser = enabled
            isDrawingEnabled = true
        }

        fun setBrushSize(size: Float) {
            brushSize = size
            isDrawingEnabled = true
        }

        fun setBlurSize(size: Float) {
            blurSize = size
        }

        fun setBrushColor(color: Int) {
            drawColor = color
            isEraser = false
        }

        fun getBrushSize() = brushSize
        fun getBlurSize() = blurSize

        fun undo() {
            if (paths.isNotEmpty()) {
                paths.removeAt(paths.size - 1)
                invalidate()
            }
        }

        fun clear() {
            paths.clear()
            invalidate()
        }

        private fun mapToBitmap(viewX: Float, viewY: Float): PointF {
            val bitmap = contentBitmap ?: originalBitmap ?: return PointF(viewX, viewY)
            val viewWidth = imagePreview.width.toFloat()
            val viewHeight = imagePreview.height.toFloat()
            val bitmapWidth = bitmap.width.toFloat()
            val bitmapHeight = bitmap.height.toFloat()

            // Correct coordinate mapping considering aspect ratio
            val scale: Float
            var dx = 0f
            var dy = 0f

            if (viewWidth / viewHeight > bitmapWidth / bitmapHeight) {
                scale = viewHeight / bitmapHeight
                dx = (viewWidth - bitmapWidth * scale) / 2f
            } else {
                scale = viewWidth / bitmapWidth
                dy = (viewHeight - bitmapHeight * scale) / 2f
            }

            return PointF((viewX - dx) / scale, (viewY - dy) / scale)
        }

        override fun onDraw(canvas: Canvas) {
            for (dp in paths) {
                canvas.drawPath(dp.path, dp.paint)
            }
            currentPath?.let { path ->
                currentPaint?.let { paint ->
                    if (!isEraser) canvas.drawPath(path, paint)
                }
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!isDrawingEnabled) return false
            
            val viewX = event.x
            val viewY = event.y
            val bp = mapToBitmap(viewX, viewY)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (isEraser) {
                        ensureMutableBitmap()
                        contentBitmap?.let { eraserCanvas = Canvas(it) }
                    }
                    currentPath = Path().apply {
                        if (isEraser) moveTo(bp.x, bp.y) else moveTo(viewX, viewY)
                    }
                    currentPaint = Paint().apply {
                        color = if (isEraser) Color.TRANSPARENT else drawColor
                        style = Paint.Style.STROKE
                        strokeJoin = Paint.Join.ROUND
                        strokeCap = Paint.Cap.ROUND
                        strokeWidth = if (isEraser) brushSize else brushSize
                        isAntiAlias = true
                        if (isEraser) {
                            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                            if (blurSize > 0) {
                                maskFilter = BlurMaskFilter(blurSize, BlurMaskFilter.Blur.NORMAL)
                            }
                        }
                    }
                    if (isEraser) {
                        eraserCanvas?.drawCircle(bp.x, bp.y, brushSize / 2f, currentPaint!!)
                        applyAllEffects()
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isEraser) currentPath?.lineTo(bp.x, bp.y) else currentPath?.lineTo(viewX, viewY)
                    if (isEraser) {
                        eraserCanvas?.drawPath(currentPath!!, currentPaint!!)
                        applyAllEffects(isFull = false) // Use fast preview during move
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!isEraser) {
                        currentPath?.let { p ->
                            currentPaint?.let { pt ->
                                paths.add(DrawingPath(p, pt))
                            }
                        }
                    }
                    if (isEraser) applyAllEffects()
                    currentPath = null
                    currentPaint = null
                }
            }
            invalidate()
            return true
        }
    }

    private fun showAdjustmentsDialog() {
        val dialog = BottomSheetDialog(this, R.style.TransparentBottomSheetDialog)
        
        val mainContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundResource(R.drawable.bg_editor_container)
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.card_bg))
        }
        dialog.setContentView(mainContainer)

        // Title and Reset
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 24)
        }
        val title = TextView(this).apply {
            text = getString(R.string.adjustments)
            textSize = 20f
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setTypeface(null, Typeface.BOLD)
        }
        header.addView(title)
        
        val btnResetAll = MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = "Sıfırla"
            textSize = 12f
            setTextColor(ContextCompat.getColor(context, R.color.accent))
            setOnClickListener {
                // Reset sliders to default values
                // (Would need access to sliders, will implement logic below)
            }
        }
        header.addView(btnResetAll)
        mainContainer.addView(header)

        val scroll = androidx.core.widget.NestedScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            clipToPadding = false
        }
        val adjustView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(adjustView)
        mainContainer.addView(scroll)

        fun createSlider(labelStr: String, iconRes: Int, from: Float, to: Float, current: Float, step: Float): Slider {
            val labelContainer = LinearLayout(this@StickerMakerActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 12, 0, 4)
            }
            val icon = ImageView(this@StickerMakerActivity).apply {
                setImageResource(iconRes)
                layoutParams = LinearLayout.LayoutParams(40, 40)
                imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.text_secondary))
            }
            val label = TextView(this@StickerMakerActivity).apply {
                text = labelStr
                textSize = 13f
                setPadding(16, 0, 0, 0)
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                setTypeface(null, Typeface.BOLD)
            }
            labelContainer.addView(icon)
            labelContainer.addView(label)
            adjustView.addView(labelContainer)

            val slider = Slider(this@StickerMakerActivity).apply {
                valueFrom = from
                valueTo = to
                value = current
                stepSize = step
                thumbTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.accent))
                trackActiveTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.accent))
                trackInactiveTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.divider))
            }
            adjustView.addView(slider)
            return slider
        }

        val brightnessSlider = createSlider(getString(R.string.brightness_short), R.drawable.ic_sun, -100f, 100f, currentBrightness, 1f)
        val contrastSlider = createSlider(getString(R.string.contrast_short), R.drawable.ic_tune, 0.5f, 2f, currentContrast, 0.05f)
        val saturationSlider = createSlider(getString(R.string.saturation_short), R.drawable.ic_photo, 0f, 2f, currentSaturation, 0.05f)
        val warmthSlider = createSlider("Sıcaklık", R.drawable.ic_theme, -50f, 50f, currentWarmth, 1f)
        val shadowsSlider = createSlider("Gölgeler", R.drawable.ic_moon, -50f, 50f, currentShadows, 1f)
        val highlightsSlider = createSlider("Aydınlık Alanlar", R.drawable.ic_sun, -50f, 50f, currentHighlights, 1f)

        fun updateAdjustments(b: Slider, c: Slider, s: Slider, w: Slider, sh: Slider, h: Slider, full: Boolean) {
            currentBrightness = b.value
            currentContrast = c.value
            currentSaturation = s.value
            currentWarmth = w.value
            currentShadows = sh.value
            currentHighlights = h.value
            applyAllEffects(isFull = full)
        }

        btnResetAll.setOnClickListener {
            brightnessSlider.value = 0f
            contrastSlider.value = 1f
            saturationSlider.value = 1f
            warmthSlider.value = 0f
            shadowsSlider.value = 0f
            highlightsSlider.value = 0f
            updateAdjustments(brightnessSlider, contrastSlider, saturationSlider, warmthSlider, shadowsSlider, highlightsSlider, false)
        }

        val sliders = listOf(brightnessSlider, contrastSlider, saturationSlider, warmthSlider, shadowsSlider, highlightsSlider)
        sliders.forEach { slider ->
            slider.addOnChangeListener { _, _, fromUser ->
                if (fromUser) updateAdjustments(brightnessSlider, contrastSlider, saturationSlider, warmthSlider, shadowsSlider, highlightsSlider, false)
            }
        }

        // Apply button
        val applyBtn = MaterialButton(this).apply {
            text = getString(R.string.ok)
            setTextColor(Color.WHITE)
            cornerRadius = 32
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.accent))
            layoutParams = LinearLayout.LayoutParams(-1, 140).apply { topMargin = 32 }
            setOnClickListener {
                updateAdjustments(brightnessSlider, contrastSlider, saturationSlider, warmthSlider, shadowsSlider, highlightsSlider, true)
                dialog.dismiss()
            }
        }
        mainContainer.addView(applyBtn)

        dialog.behavior.peekHeight = 900
        dialog.behavior.isHideable = true
        dialog.show()
    }

    private fun applyAdjustments(brightness: Float, contrast: Float, saturation: Float) {
        val base = originalBitmap ?: return

        lifecycleScope.launch(Dispatchers.Default) {
            try {
                val output = Bitmap.createBitmap(base.width, base.height, base.config ?: Bitmap.Config.ARGB_8888)
                val canvas = Canvas(output)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)

                // Create combined color matrix
                val cm = ColorMatrix()

                // Brightness matrix
                val brightnessMatrix = ColorMatrix(floatArrayOf(
                    1f, 0f, 0f, 0f, brightness,
                    0f, 1f, 0f, 0f, brightness,
                    0f, 0f, 1f, 0f, brightness,
                    0f, 0f, 0f, 1f, 0f
                ))

                // Contrast matrix
                val scale = contrast
                val translate = (-.5f * scale + .5f) * 255f
                val contrastMatrix = ColorMatrix(floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                ))

                // Saturation matrix
                val saturationMatrix = ColorMatrix()
                saturationMatrix.setSaturation(saturation)

                // Combine matrices
                cm.postConcat(brightnessMatrix)
                cm.postConcat(contrastMatrix)
                cm.postConcat(saturationMatrix)

                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(base, 0f, 0f, paint)

                withContext(Dispatchers.Main) {
                    selectedBitmap = output
                    imagePreview.setImageBitmap(selectedBitmap)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun undoRemoveBackground() {
        originalBitmap?.let {
            contentBitmap = it.copy(it.config ?: Bitmap.Config.ARGB_8888, true)
            applyAllEffects()
            backgroundRemoved = false
            toolRemoveBg.findViewById<TextView>(R.id.title).text = getString(R.string.remove_bg_short)
            toolRemoveBg.findViewById<ImageView>(R.id.icon).setImageResource(R.drawable.ic_sticker)
        }
    }

    private fun showFiltersDialog() {
        if (selectedBitmap == null) return

        val filters = listOf(
            getString(R.string.original) to null,
            getString(R.string.grayscale) to ColorMatrix(floatArrayOf(
                0.33f, 0.33f, 0.33f, 0f, 0f,
                0.33f, 0.33f, 0.33f, 0f, 0f,
                0.33f, 0.33f, 0.33f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )),
            getString(R.string.sepia) to ColorMatrix(floatArrayOf(
                .393f, .769f, .189f, 0f, 0f,
                .349f, .686f, .168f, 0f, 0f,
                .272f, .534f, .131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )),
            getString(R.string.vivid) to ColorMatrix().apply { setSaturation(1.6f) },
            getString(R.string.vintage) to ColorMatrix().apply { setSaturation(0.5f) },
            "Noir" to ColorMatrix(floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )),
            "Cool" to ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, -60f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1.2f, 0f, 60f,
                0f, 0f, 0f, 1f, 0f
            )),
            "Warm" to ColorMatrix(floatArrayOf(
                1.2f, 0f, 0f, 0f, 40f,
                0f, 1f, 0f, 0f, 20f,
                0f, 0f, 0.8f, 0f, -20f,
                0f, 0f, 0f, 1f, 0f
            )),
            "Pinky" to ColorMatrix(floatArrayOf(
                1.2f, 0f, 0f, 0f, 50f,
                0f, 0.9f, 0f, 0f, 0f,
                0f, 0f, 1.2f, 0f, 50f,
                0f, 0f, 0f, 1f, 0f
            )),
            "Cyan" to ColorMatrix(floatArrayOf(
                0.8f, 0f, 0f, 0f, 0f,
                0f, 1.2f, 0f, 0f, 40f,
                0f, 0f, 1.2f, 0f, 40f,
                0f, 0f, 0f, 1f, 0f
            )),
            "B&W" to ColorMatrix(floatArrayOf(
                1.5f, 1.5f, 1.5f, 0f, -200f,
                1.5f, 1.5f, 1.5f, 0f, -200f,
                1.5f, 1.5f, 1.5f, 0f, -200f,
                0f, 0f, 0f, 1f, 0f
            ))
        )

        val dialog = BottomSheetDialog(this, R.style.TransparentBottomSheetDialog)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundResource(R.drawable.bg_editor_container)
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.card_bg))
        }
        dialog.setContentView(container)

        val title = TextView(this).apply {
            text = getString(R.string.select_filter)
            textSize = 20f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 32)
        }
        container.addView(title)

        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val horizontalLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 16)
        }
        scroll.addView(horizontalLayout)
        container.addView(scroll)

        filters.forEach { (name, matrix) ->
            val filterItem = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(24, 0, 24, 0)
                setOnClickListener {
                    currentFilterMatrix = matrix
                    applyAllEffects(isFull = true)
                    dialog.dismiss()
                }
            }

            val preview = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(160, 160)
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(contentBitmap ?: originalBitmap)
                background = ContextCompat.getDrawable(context, R.drawable.bg_color_circle)
                clipToOutline = true
                if (matrix != null) colorFilter = ColorMatrixColorFilter(matrix)
            }
            filterItem.addView(preview)

            val label = TextView(this).apply {
                text = name
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                setPadding(0, 8, 0, 0)
                gravity = Gravity.CENTER
            }
            filterItem.addView(label)

            horizontalLayout.addView(filterItem)
        }

        val btnCancel = MaterialButton(this).apply {
            text = getString(R.string.cancel)
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.accent))
            setOnClickListener { dialog.dismiss() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 120).apply { topMargin = 32 }
        }
        container.addView(btnCancel)

        dialog.show()
    }



    private fun applyFilter(colorMatrix: ColorMatrix?) {
        currentFilterMatrix = colorMatrix
        applyAllEffects()
    }

    private fun checkMediaGuard(): Boolean {
        if (selectedBitmap == null) {
            Toast.makeText(this, "Önce bir medya seçin (Fotoğraf Seçin kısmına dokunun)", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun openGallery() {
        if (isAnimatedMode) {
            // Video seçimi için fallback kullan
            pickMediaLauncher.launch("video/*")
        } else {
            // Resim seçimi için PickMultipleVisualMedia kullan (daha iyi çoklu seçim desteği)
            try {
                pickMultipleMedia.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            } catch (e: Exception) {
                // Fallback to GetMultipleContents if PickMultipleVisualMedia fails
                pickMediaLauncher.launch("image/*")
            }
        }
    }

    private fun updateQueueProgress() {
        if (selectedUris.size > 1) {
            tvMediaInfo.visibility = View.VISIBLE
            tvMediaInfo.text = "${currentUriIndex + 1}/${selectedUris.size}"
        } else {
            tvMediaInfo.visibility = View.GONE
        }
    }



    private fun showBorderEditor() {
        val dialog = BottomSheetDialog(this, R.style.TransparentBottomSheetDialog)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundResource(R.drawable.bg_editor_container)
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.card_bg))
        }
        dialog.setContentView(container)

        val title = TextView(this).apply {
            text = getString(R.string.border_short)
            textSize = 20f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 32)
        }
        container.addView(title)

        // Size
        val sizeLabel = TextView(this).apply {
            text = "Boyut"
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        }
        container.addView(sizeLabel)

        val sizeSlider = Slider(this).apply {
            valueFrom = 0f
            valueTo = 40f
            value = borderSize
            thumbTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.accent))
            trackActiveTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.accent))
        }
        container.addView(sizeSlider)
        sizeSlider.addOnChangeListener { _, value, _ ->
            borderSize = value
            applyAllEffects()
        }

        // Color
        val colorLabel = TextView(this).apply {
            text = "Renk"
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            setPadding(0, 24, 0, 8)
        }
        container.addView(colorLabel)

        val colorScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val colorLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        colorScroll.addView(colorLayout)
        container.addView(colorScroll)

        val colors = listOf(Color.WHITE, Color.BLACK, Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN, Color.MAGENTA, Color.parseColor("#FF6B6B"), Color.parseColor("#4ECDC4"), Color.parseColor("#FFD700"))
        var selectedColorView: View? = null
        colors.forEach { color ->
            val cv = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(100, 100).apply { setMargins(12, 12, 12, 12) }
            }
            val inner = View(this).apply {
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                background = ContextCompat.getDrawable(context, R.drawable.bg_color_circle)
                backgroundTintList = ColorStateList.valueOf(color)
            }
            cv.addView(inner)
            if (color == borderColor) {
                cv.foreground = ContextCompat.getDrawable(this@StickerMakerActivity, R.drawable.ic_check)
                cv.foregroundGravity = Gravity.CENTER
                selectedColorView = cv
            }
            cv.setOnClickListener {
                selectedColorView?.foreground = null
                cv.foreground = ContextCompat.getDrawable(this@StickerMakerActivity, R.drawable.ic_check)
                cv.foregroundGravity = Gravity.CENTER
                selectedColorView = cv
                borderColor = color
                applyAllEffects()
            }
            colorLayout.addView(cv)
        }

        val btnOk = MaterialButton(this).apply {
            text = "Tamam"
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.accent))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 140).apply { topMargin = 32 }
            setOnClickListener { dialog.dismiss() }
        }
        container.addView(btnOk)

        dialog.show()
    }

    private fun applyAllEffects(isFull: Boolean = true) {
        if (!checkMediaGuard()) return
        val base = contentBitmap ?: originalBitmap ?: return
        
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                // 1. Combined Matrix for Adjustments
                val matrix = ColorMatrix()
                
                // Brightness
                matrix.postConcat(ColorMatrix(floatArrayOf(
                    1f, 0f, 0f, 0f, currentBrightness,
                    0f, 1f, 0f, 0f, currentBrightness,
                    0f, 0f, 1f, 0f, currentBrightness,
                    0f, 0f, 0f, 1f, 0f
                )))
                
                // Contrast
                val cScale = currentContrast
                val cTranslate = (-.5f * cScale + .5f) * 255f
                matrix.postConcat(ColorMatrix(floatArrayOf(
                    cScale, 0f, 0f, 0f, cTranslate,
                    0f, cScale, 0f, 0f, cTranslate,
                    0f, 0f, cScale, 0f, cTranslate,
                    0f, 0f, 0f, 1f, 0f
                )))
                
                // Saturation
                val sMatrix = ColorMatrix()
                sMatrix.setSaturation(currentSaturation)
                matrix.postConcat(sMatrix)
                
                // Warmth
                val w = currentWarmth * 0.5f
                matrix.postConcat(ColorMatrix(floatArrayOf(
                    1f + w/255f, 0f, 0f, 0f, w,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f - w/255f, 0f, -w,
                    0f, 0f, 0f, 1f, 0f
                )))
                
                // Custom Filter
                currentFilterMatrix?.let { matrix.postConcat(it) }
                
                if (!isFull) {
                    withContext(Dispatchers.Main) {
                        imagePreview.colorFilter = ColorMatrixColorFilter(matrix)
                    }
                    return@launch
                }

                // Bakery
                val processed = Bitmap.createBitmap(base.width, base.height, base.config ?: Bitmap.Config.ARGB_8888)
                val canvas = Canvas(processed)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                paint.colorFilter = ColorMatrixColorFilter(matrix)
                canvas.drawBitmap(base, 0f, 0f, paint)
                
                // 2. Border (Optimized path)
                val final = if (borderSize > 0) {
                    addOutlineInternal(processed, borderSize.toInt(), borderColor)
                } else {
                    processed
                }
                
                withContext(Dispatchers.Main) {
                    selectedBitmap = final
                    imagePreview.setImageBitmap(selectedBitmap)
                    imagePreview.colorFilter = null
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun addOutlineInternal(src: Bitmap, width: Int, color: Int): Bitmap {
        if (width <= 0) return src
        // Optimized for speed: Use a lower-precision alpha extraction for preview if needed
        // For now, using a highly optimized draw loop
        val output = Bitmap.createBitmap(src.width, src.height, src.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        
        val radius = width.toFloat()
        // Use PorterDuff for instant colorization of the copies
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        
        val steps = 8 // Perfect balance between quality and speed
        for (i in 0 until steps) {
            val angle = 2.0 * Math.PI * i / steps
            val dx = (radius * Math.cos(angle)).toFloat()
            val dy = (radius * Math.sin(angle)).toFloat()
            canvas.drawBitmap(src, dx, dy, paint)
        }
        
        // Draw original on top
        canvas.drawBitmap(src, 0f, 0f, null)
        return output
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && requestCode == UCrop.REQUEST_CROP) {
            val resultUri = UCrop.getOutput(data!!)
            resultUri?.let { uri ->
                val bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(uri))
                originalBitmap = bitmap
                contentBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
                selectedBitmap = contentBitmap
                imagePreview.setImageBitmap(selectedBitmap)
                imagePreview.visibility = View.VISIBLE
                placeholderContainer.visibility = View.GONE
                applyAllEffects()
            }
        }
    }

    private fun addOutline(src: Bitmap, width: Int, color: Int): Bitmap {
        if (width <= 0) return src
        
        val radius = width.toFloat()
        
        // Optimize: Cache the mask if the source bitmap haven't changed in shape (alpha)
        if (maskSourceBitmap != src || cachedMaskAlpha == null) {
            maskSourceBitmap = src
            cachedMaskAlpha?.recycle()
            cachedMaskAlpha = src.extractAlpha()
        }
        
        val maskAlpha = cachedMaskAlpha ?: return src
        
        val config = src.config ?: Bitmap.Config.ARGB_8888
        val output = try {
            Bitmap.createBitmap((src.width + radius * 2).toInt(), (src.height + radius * 2).toInt(), config)
        } catch (e: OutOfMemoryError) {
            return src
        }
        
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = color
        
        try {
            // Draw in a circular pattern
            val steps = 12
            for (i in 0 until steps) {
                val angle = 2.0 * Math.PI * i / steps
                val dx = (radius * Math.cos(angle)).toFloat()
                val dy = (radius * Math.sin(angle)).toFloat()
                canvas.drawBitmap(maskAlpha, radius + dx, radius + dy, paint)
            }
            
            canvas.drawBitmap(src, radius, radius, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return output
    }

    private fun showTextEditorDialog() {
        val dialog = BottomSheetDialog(this, R.style.TransparentBottomSheetDialog)
        val view = layoutInflater.inflate(R.layout.dialog_add_text, null)
        dialog.setContentView(view)

        val editText = view.findViewById<TextInputEditText>(R.id.etStickerText)
        val btnAdd = view.findViewById<MaterialButton>(R.id.btnAddText)
        val sizeSlider = view.findViewById<Slider>(R.id.textSizeSlider)
        val fontGrid = view.findViewById<GridLayout>(R.id.fontSelectionLayout)
        val previewText = view.findViewById<TextView>(R.id.tvTextPreviewInDialog)

        var selectedColor = Color.WHITE
        val colors = listOf(
            Color.WHITE, Color.BLACK, Color.RED, Color.GREEN,
            Color.BLUE, Color.YELLOW, Color.CYAN, Color.MAGENTA,
            Color.parseColor("#FF6B6B"), Color.parseColor("#4ECDC4"),
            Color.parseColor("#FFE66D"), Color.parseColor("#95E1D3"),
            Color.parseColor("#F38181"), Color.parseColor("#FCE38A"),
            Color.parseColor("#EAFFD0"), Color.parseColor("#95E1D3"),
            Color.parseColor("#E8E8E8"), Color.parseColor("#555555")
        )
        val colorLayout = view.findViewById<LinearLayout>(R.id.colorSelectionLayout)

        // Update live preview function
        fun updateLivePreview() {
            val currentText = editText.text?.toString() ?: ""
            previewText?.apply {
                text = if (currentText.isEmpty()) getString(R.string.et_sticker_text_hint) else currentText
                textSize = sizeSlider.value
                setTextColor(selectedColor)
                typeface = selectedFont
                setShadowLayer(8f, 0f, 0f, if (selectedColor == Color.BLACK) Color.WHITE else Color.BLACK)
                alpha = if (currentText.isEmpty()) 0.5f else 1f
            }
        }

        // Color selection with visual feedback
        colorLayout?.let { layout ->
            layout.removeAllViews()
            var selectedOverlay: View? = null
            
            colors.forEach { color ->
                val container = FrameLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(100, 100).apply { setMargins(8, 8, 8, 8) }
                }

                val colorView = View(this).apply {
                    layoutParams = FrameLayout.LayoutParams(80, 80).apply { gravity = Gravity.CENTER }
                    background = ContextCompat.getDrawable(context, R.drawable.bg_color_circle)
                    backgroundTintList = ColorStateList.valueOf(color)
                    if (color == Color.WHITE) background = ContextCompat.getDrawable(context, R.drawable.bg_color_circle) // Ensure border for white
                }

                val borderView = View(this).apply {
                    layoutParams = FrameLayout.LayoutParams(100, 100)
                    background = ContextCompat.getDrawable(context, R.drawable.bg_color_circle)
                    backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                    // Custom stroke for selection
                    visibility = if (color == selectedColor) View.VISIBLE else View.INVISIBLE
                }
                
                // Let's use alpha or a dedicated border drawable
                if (color == selectedColor) {
                    colorView.scaleX = 0.8f
                    colorView.scaleY = 0.8f
                    container.setBackgroundResource(R.drawable.bg_color_selected_border)
                }

                container.addView(colorView)
                container.setOnClickListener {
                    selectedColor = color
                    // Simple refresh of the color layout
                    colorLayout.children.forEach { charlie ->
                        charlie.setBackgroundColor(Color.TRANSPARENT)
                        (charlie as ViewGroup).getChildAt(0).scaleX = 1.0f
                        (charlie as ViewGroup).getChildAt(0).scaleY = 1.0f
                    }
                    container.setBackgroundResource(R.drawable.bg_color_selected_border)
                    colorView.scaleX = 0.8f
                    colorView.scaleY = 0.8f
                    updateLivePreview()
                }
                layout.addView(container)
            }
        }

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateLivePreview()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        sizeSlider.addOnChangeListener { _, _, _ ->
            updateLivePreview()
        }

        val fonts = listOf(
            "Default" to Typeface.DEFAULT,
            "Bold" to Typeface.DEFAULT_BOLD,
            "Serif" to Typeface.SERIF,
            "Mono" to Typeface.MONOSPACE,
            "Sans" to Typeface.SANS_SERIF,
            "Medium" to Typeface.create("sans-serif-medium", Typeface.NORMAL),
            "Black" to Typeface.create("sans-serif-black", Typeface.NORMAL),
            "Light" to Typeface.create("sans-serif-light", Typeface.NORMAL),
            "Condensed" to Typeface.create("sans-serif-condensed", Typeface.NORMAL),
            "Italic" to Typeface.create(Typeface.DEFAULT, Typeface.ITALIC),
            "Lobster" to Typeface.create("cursive", Typeface.BOLD),
            "Casual" to Typeface.create("casual", Typeface.NORMAL),
            "Small Caps" to Typeface.create("sans-serif-smallcaps", Typeface.NORMAL),
            "Narrow" to Typeface.create("sans-serif-condensed-light", Typeface.NORMAL),
            "Elegant" to Typeface.create("serif", Typeface.ITALIC),
            "Retro" to Typeface.create("serif-monospace", Typeface.BOLD),
            "Comic" to Typeface.create("casual", Typeface.BOLD),
            "System" to Typeface.create("sans-serif-thin", Typeface.BOLD),
            "Modern" to Typeface.create("sans-serif-black", Typeface.ITALIC),
            "Classic" to Typeface.create("serif", Typeface.BOLD)
        )

        fontGrid.removeAllViews()
        var selectedFontBtn: MaterialButton? = null
        fonts.forEach { (name, tf) ->
            val btn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = name
                typeface = tf
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                setOnClickListener {
                    selectedFontBtn?.strokeWidth = 1
                    selectedFontBtn = this
                    this.strokeWidth = 4
                    selectedFont = tf
                    updateLivePreview()
                }
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(4, 4, 4, 4)
                }
                if (tf == selectedFont) {
                    selectedFontBtn = this
                    strokeWidth = 4
                }
            }
            fontGrid.addView(btn)
        }

        updateLivePreview()

        btnAdd.setOnClickListener {
            val text = editText.text.toString().trim()
            if (text.isNotEmpty()) {
                addTextToCanvas(text, sizeSlider.value, selectedColor)
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun createLivePreviewText() {
        removeLivePreviewText() // Remove any existing preview
        livePreviewTextView = TextView(this).apply {
            text = getString(R.string.et_sticker_text_hint)
            textSize = 28f
            setTextColor(Color.WHITE)
            typeface = selectedFont
            gravity = Gravity.CENTER
            setPadding(20, 20, 20, 20)
            setShadowLayer(8f, 0f, 0f, Color.BLACK)
            alpha = 0.3f
            tag = "live_preview"
        }
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        }
        stickerEditorView.addView(livePreviewTextView, params)
    }

    private fun removeLivePreviewText() {
        livePreviewTextView?.let { stickerEditorView.removeView(it) }
        livePreviewTextView = null
    }

    private fun addTextToCanvas(text: String, size: Float, color: Int) {
        val textView = TextView(this).apply {
            this.text = text
            this.setTextColor(color)
            this.textSize = size
            // Better shadow for visibility on any background
            this.setShadowLayer(12f, 0f, 0f, if (color == Color.BLACK) Color.WHITE else Color.BLACK)
            this.typeface = selectedFont
            this.gravity = Gravity.CENTER
            this.setPadding(30, 30, 30, 30)
            this.textAlignment = View.TEXT_ALIGNMENT_CENTER
        }
        
        addDraggableToCanvas(textView)
    }

    private fun showEmojiPicker() {
        val emojis = arrayOf(
            "😊", "😂", "❤️", "🔥", "✨", "🚀", "🎉", "🌟", "💫", "🎁", 
            "💎", "📱", "🌈", "🎭", "🐱", "🧿", "👑", "⚡", "🔔", "💯",
            "😎", "😍", "😉", "🤔", "😜", "😇", "🥳", "😭", "🤷‍♂️", "👍",
            "👏", "🙌", "💪", "👊", "🤝", "🎈", "🎊", "🎆", "🎇", "🧨",
            "🧧", "🧸", "🎉", "🌹", "🌸", "🌻", "🌼", "🍀", "🍓", "🍒",
            "🍴", "🍕", "🍔", "🍟", "🍗", "🥪", "🍣", "🍱", "🥟", "🍩",
            "🍦", "🍰", "🍭", "🍬", "🍫", "🍿", "🥤", "🧋", "☕", "🍺",
            "🎮", "🎬", "🎸", "🎧", "📷", "📸", "📺", "💻", "⌚", "📱",
            "✈️", "🚗", "🚲", "🏠", "🏢", "🌍", "🗺️", "✅", "❌", "❓",
            "‼️", "⚠️", "🛑", "🆗", "🆒", "🆕", "🆙", "🧿", "🧿", "🧿"
        )
        
        val dialog = BottomSheetDialog(this, R.style.TransparentBottomSheetDialog)
        val view = layoutInflater.inflate(R.layout.dialog_emoji_picker, null)
        dialog.setContentView(view)

        val grid = view.findViewById<GridLayout>(R.id.emojiGrid)
        grid.removeAllViews()
        grid.columnCount = 5
        
        emojis.forEach { emoji ->
            val textView = TextView(this).apply {
                this.text = emoji
                this.textSize = 34f
                this.setPadding(12, 12, 12, 12)
                this.gravity = Gravity.CENTER
                this.background = ContextCompat.getDrawable(context, R.drawable.bg_emoji_circle)
                this.setOnClickListener {
                    addEmojiToCanvas(emoji)
                    dialog.dismiss()
                }
                this.alpha = 1.0f
            }
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(8, 8, 8, 8)
            }
            grid.addView(textView, params)
        }
        dialog.show()
    }

    private fun addEmojiToCanvas(emoji: String) {
        val textView = TextView(this).apply {
            this.text = emoji
            this.textSize = 48f
            this.gravity = Gravity.CENTER
        }
        addDraggableToCanvas(textView)
    }

    private fun addDraggableToCanvas(view: View) {
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        }
        
        view.setOnTouchListener(DraggableTouchListener())
        stickerEditorView.addView(view, params)
    }

    private inner class DraggableTouchListener : View.OnTouchListener {
        private var lastTouchX = 0f
        private var lastTouchY = 0f
        private var initialDistance = 0f
        private var initialRotation = 0f
        private var initialScale = 1f
        private var initialViewRotation = 0f
        private var isScaling = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            if (isDrawingActive) return false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                    v.bringToFront()
                    v.animate().scaleXBy(0.05f).scaleYBy(0.05f).setDuration(100).start()
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount == 2) {
                        isScaling = true
                        initialDistance = getRawDistance(event)
                        initialRotation = getRawRotation(event)
                        initialScale = v.scaleX
                        initialViewRotation = v.rotation
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount == 1 && !isScaling) {
                        val deltaX = event.rawX - lastTouchX
                        val deltaY = event.rawY - lastTouchY
                        v.x += deltaX
                        v.y += deltaY
                        lastTouchX = event.rawX
                        lastTouchY = event.rawY
                    } else if (event.pointerCount == 2) {
                        // Handle Scaling
                        val currentDistance = getRawDistance(event)
                        if (currentDistance > 10f && initialDistance > 0f) {
                            val scale = (currentDistance / initialDistance) * initialScale
                            v.scaleX = scale
                            v.scaleY = scale
                        }

                        // Handle Rotation
                        val currentRotation = getRawRotation(event)
                        val rotationDelta = currentRotation - initialRotation
                        v.rotation = initialViewRotation + rotationDelta
                    }
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    if (event.pointerCount < 2) {
                        isScaling = false
                        lastTouchX = event.rawX
                        lastTouchY = event.rawY
                    }
                }
                MotionEvent.ACTION_UP -> {
                    isScaling = false
                    v.animate().scaleXBy(-0.05f).scaleYBy(-0.05f).setDuration(100).start()
                }
            }
            return true
        }

        private fun getRawDistance(event: MotionEvent): Float {
            if (event.pointerCount < 2) return 0f
            val x = event.getRawX(0) - event.getRawX(1)
            val y = event.getRawY(0) - event.getRawY(1)
            return Math.sqrt((x * x + y * y).toDouble()).toFloat()
        }

        private fun getRawRotation(event: MotionEvent): Float {
            if (event.pointerCount < 2) return 0f
            val x = event.getRawX(0) - event.getRawX(1)
            val y = event.getRawY(0) - event.getRawY(1)
            val radians = Math.atan2(y.toDouble(), x.toDouble())
            return Math.toDegrees(radians).toFloat()
        }

        private fun MotionEvent.getRawX(index: Int): Float {
            return if (index == 0) rawX else {
                val offset = rawX - x
                getX(index) + offset
            }
        }
        private fun MotionEvent.getRawY(index: Int): Float {
            return if (index == 0) rawY else {
                val offset = rawY - y
                getY(index) + offset
            }
        }
    }

    private fun resetEditorContent() {
        // Remove all views except imagePreview and placeholderContainer
        val toRemove = mutableListOf<View>()
        for (i in 0 until stickerEditorView.childCount) {
            val child = stickerEditorView.getChildAt(i)
            if (child.id != R.id.imagePreview &&
                child.id != R.id.placeholderContainer &&
                child.id != R.id.tvMediaInfo) {
                toRemove.add(child)
            }
        }
        toRemove.forEach { stickerEditorView.removeView(it) }
        
        drawingView?.clear()
        drawingView = null
        isDrawingActive = false

        // Reset border
        borderSize = 0f

        // Reset adjustments
        currentBrightness = 0f
        currentContrast = 1f
        currentSaturation = 1f

        // Reset BG removal
        if (backgroundRemoved) {
            originalBitmap?.let {
                selectedBitmap = it.copy(it.config ?: Bitmap.Config.ARGB_8888, true)
                imagePreview.setImageBitmap(selectedBitmap)
                backgroundRemoved = false

                // Reset Remove BG Tool UI
                toolRemoveBg.findViewById<TextView>(R.id.title).text = getString(R.string.remove_bg_short)
                toolRemoveBg.findViewById<ImageView>(R.id.icon).setImageResource(R.drawable.ic_sticker)
            }
        } else {
            // If no background removal, reset to original
            originalBitmap?.let {
                selectedBitmap = it.copy(it.config ?: Bitmap.Config.ARGB_8888, true)
                imagePreview.setImageBitmap(selectedBitmap)
            }
        }
    }

    private fun setupEdgeToEdge() {
        val toolbarLayout = findViewById<View>(R.id.toolbarLayout)
        val makerRoot = findViewById<View>(R.id.maker_root)
        
        ViewCompat.setOnApplyWindowInsetsListener(makerRoot) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            toolbarLayout?.setPadding(toolbarLayout.paddingLeft, systemBars.top, toolbarLayout.paddingRight, toolbarLayout.paddingBottom)
            makerRoot?.setPadding(makerRoot.paddingLeft, makerRoot.paddingTop, makerRoot.paddingRight, systemBars.bottom)
            
            insets
        }
    }

    private fun enterEditorMode() {
        typeSelectionContainer.visibility = View.GONE
        editorContainer.visibility = View.VISIBLE
        toolbarTitle.text = if (isAnimatedMode) getString(R.string.animated_sticker) else getString(R.string.static_sticker)
        
        // UI Adaptations
        placeholderIcon.setImageResource(if (isAnimatedMode) R.drawable.ic_video else R.drawable.ic_photo)
        placeholderText.text = if (isAnimatedMode) getString(R.string.select_video_gif) else getString(R.string.select_photo_maker)
        
        // Update Change Media Tool UI
        toolChangeMedia.findViewById<ImageView>(R.id.icon).setImageResource(if (isAnimatedMode) R.drawable.ic_video else R.drawable.ic_photo)
        toolChangeMedia.findViewById<TextView>(R.id.title).text = if (isAnimatedMode) "Sıfırla" else getString(R.string.change_media_short)

        // Background removal only for static
        toolRemoveBg.visibility = if (isAnimatedMode) View.GONE else View.VISIBLE
        
        toolsPanel.visibility = View.VISIBLE
    }

    private fun exitEditorMode() {
        typeSelectionContainer.visibility = View.VISIBLE
        editorContainer.visibility = View.GONE
        toolbarTitle.text = "Sticly"
        resetEditorUI()
    }

    private fun resetEditorUI() {
        resetEditorContent()
        selectedUris.clear()
        currentUriIndex = -1
        selectedBitmap = null
        originalBitmap = null
        backgroundRemoved = false
        imagePreview.visibility = View.GONE
        placeholderContainer.visibility = View.VISIBLE
        btnAddToPack.visibility = View.GONE
        tvMediaInfo.visibility = View.GONE
    }

    private fun loadMediaFromUri(uri: Uri) {
        try {
            currentMimeType = contentResolver.getType(uri) ?: ""
            Log.d("StickerMaker", "Loading media: $uri, Type: $currentMimeType")

            if (isAnimatedMode) {
                // Video/GIF Önizleme
                tvMediaInfo.visibility = View.VISIBLE
                tvMediaInfo.text = getMediaDuration(uri)
                
                // İlk kareyi önizleme olarak al
                val mimeType = currentMimeType ?: ""
                val bitmap = if (mimeType.contains("video")) {
                    getVideoFrame(uri)
                } else {
                    BitmapFactory.decodeStream(contentResolver.openInputStream(uri))
                }
                
                if (bitmap != null) {
                    selectedBitmap = cropToSquare(bitmap)
                    imagePreview.setImageBitmap(selectedBitmap)
                    imagePreview.visibility = View.VISIBLE
                    placeholderContainer.visibility = View.GONE
                    btnAddToPack.visibility = View.VISIBLE
                    btnAddToPack.text = getString(R.string.convert_and_add)
                }
            } else {
                // Statik Resim
                contentResolver.openInputStream(uri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        selectedBitmap = cropToSquare(bitmap)
                        originalBitmap = selectedBitmap?.copy(selectedBitmap!!.config ?: Bitmap.Config.ARGB_8888, true)
                        contentBitmap = selectedBitmap?.copy(selectedBitmap!!.config ?: Bitmap.Config.ARGB_8888, true)
                        backgroundRemoved = false
                        
                        // Reset Remove BG Tool UI
                        toolRemoveBg.findViewById<TextView>(R.id.title).text = getString(R.string.remove_bg_short)
                        toolRemoveBg.findViewById<ImageView>(R.id.icon).setImageResource(R.drawable.ic_sticker)
                        
                        imagePreview.setImageBitmap(selectedBitmap)
                        imagePreview.visibility = View.VISIBLE
                        placeholderContainer.visibility = View.GONE
                        btnAddToPack.visibility = View.VISIBLE
                        btnAddToPack.text = getString(R.string.add_to_pack)
                    }
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.media_load_error), Toast.LENGTH_SHORT).show()
        }
    }

    private fun getMediaDuration(uri: Uri): String {
        return try {
            val mmr = android.media.MediaMetadataRetriever()
            mmr.setDataSource(this, uri)
            val durationStr = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLong() ?: 0
            val seconds = durationMs / 1000
            mmr.release()
            String.format("%02d:%02d", seconds / 60, seconds % 60)
        } catch (e: Exception) {
            "00:00"
        }
    }

    private fun getVideoFrame(uri: Uri): Bitmap? {
        return try {
            val mmr = android.media.MediaMetadataRetriever()
            mmr.setDataSource(this, uri)
            val bitmap = mmr.getFrameAtTime(0)
            mmr.release()
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    private fun cropToSquare(bitmap: Bitmap): Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
        val x = (bitmap.width - size) / 2
        val y = (bitmap.height - size) / 2
        return Bitmap.createBitmap(bitmap, x, y, size, size)
    }

    private fun processRemoveBackground(bitmap: Bitmap) {
        showProcessingOverlay(getString(R.string.processing_background), getString(R.string.ai_analyzing), R.drawable.ic_photo)

        val inputImage = InputImage.fromBitmap(bitmap, 0)
        segmenter.process(inputImage)
            .addOnSuccessListener { mask: SegmentationMask ->
                lifecycleScope.launch(Dispatchers.Default) {
                    try {
                        val maskBuffer = mask.buffer
                        val width = bitmap.width
                        val height = bitmap.height
                        val resultBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                        val pixels = IntArray(width * height)
                        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

                        // Read confidence values into array for processing
                        val confidences = FloatArray(width * height)
                        maskBuffer.rewind()
                        for (i in confidences.indices) {
                            if (maskBuffer.hasRemaining()) {
                                confidences[i] = maskBuffer.float
                            }
                        }

                        // Apply soft edge removal with better threshold
                        // Lower threshold = more background removed
                        val threshold = 0.3f
                        val softEdgeWidth = 0.2f // For anti-aliasing

                        for (y in 0 until height) {
                            for (x in 0 until width) {
                                val pos = y * width + x
                                val confidence = confidences[pos]

                                if (confidence < threshold) {
                                    // Fully transparent - background
                                    pixels[pos] = Color.TRANSPARENT
                                } else if (confidence < threshold + softEdgeWidth) {
                                    // Soft edge - partial transparency for anti-aliasing
                                    val alpha = ((confidence - threshold) / softEdgeWidth * 255).toInt()
                                    val originalColor = pixels[pos]
                                    pixels[pos] = Color.argb(
                                        alpha,
                                        Color.red(originalColor),
                                        Color.green(originalColor),
                                        Color.blue(originalColor)
                                    )
                                }
                                // else: keep original pixel
                            }
                        }

                        resultBitmap.setPixels(pixels, 0, width, 0, 0, width, height)

                        // Apply edge smoothing
                        val smoothedBitmap = applyEdgeSmoothing(resultBitmap)

                        withContext(Dispatchers.Main) {
                            contentBitmap = smoothedBitmap
                            applyAllEffects()
                            backgroundRemoved = true

                            toolRemoveBg.findViewById<TextView>(R.id.title).text = getString(R.string.undo)
                            toolRemoveBg.findViewById<ImageView>(R.id.icon).setImageResource(R.drawable.ic_restore)

                            hideProcessingOverlay()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        withContext(Dispatchers.Main) {
                            hideProcessingOverlay()
                            Toast.makeText(this@StickerMakerActivity, getString(R.string.bg_removal_error), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                hideProcessingOverlay()
                Toast.makeText(this, getString(R.string.error_occurred), Toast.LENGTH_SHORT).show()
            }
    }

    private fun applyEdgeSmoothing(bitmap: Bitmap): Bitmap {
        // Apply a slight blur to smooth edges
        try {
            val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            return output
        } catch (e: Exception) {
            return bitmap
        }
    }

    private fun showPackSelectionDialog(bitmapToSave: Bitmap? = null) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_select_pack, null)
        val rvPacks = view.findViewById<RecyclerView>(R.id.rvPacks)
        val inputPackName = view.findViewById<TextInputEditText>(R.id.inputPackName)
        val btnCreatePack = view.findViewById<Button>(R.id.btnCreatePack)

        val allPacks = CustomStickerManager.getCustomPacks(this)
        val filteredPacks = allPacks.filter { it.isAnimated == isAnimatedMode }

        val dialog = AlertDialog.Builder(this, R.style.MaterialAlertDialogTheme).setView(view).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        if (filteredPacks.isNotEmpty()) {
            rvPacks.visibility = View.VISIBLE
            rvPacks.layoutManager = LinearLayoutManager(this)
            rvPacks.adapter = PackSelectionAdapter(filteredPacks) { pack ->
                dialog.dismiss()
                targetPackId = pack.id
                bitmapToSave?.let { processStickerResult(it) } ?: run {
                   val b = selectedBitmap ?: originalBitmap
                   if (b != null) processStickerResult(b)
                }
            }
        } else {
            rvPacks.visibility = View.GONE
        }

        btnCreatePack.setOnClickListener {
            val name = inputPackName.text?.toString()?.trim() ?: ""
            if (name.isEmpty()) {
                inputPackName.error = getString(R.string.enter_pack_name)
                return@setOnClickListener
            }
            val packId = CustomStickerManager.createPack(this, name, isAnimatedMode)
            dialog.dismiss()
            targetPackId = packId
            
            bitmapToSave?.let { processStickerResult(it) } ?: run {
                val b = selectedBitmap ?: originalBitmap
                if (b != null) processStickerResult(b)
            }
        }
        dialog.show()
    }

    private fun processStickerResult(bitmap: Bitmap) {
        if (isProcessing) return
        
        isProcessing = true
        showProcessingOverlay(getString(R.string.preparing_sticker), getString(R.string.please_wait), R.drawable.ic_sticker)
        
        // We will process everything in a background thread
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                // Determine the base bitmap to save
                // If we already have a captured bitmap (with text/emojis), use it
                // Otherwise process selectedBitmap/originalBitmap with border
                
                val sourceForProcessing = if (stickerEditorView.childCount > 3) {
                    // More than just imagePreview + placeholder + tvMediaInfo? 
                    // Then it's likely a captured bitmap from UI.
                    bitmap
                } else {
                    // Try to apply border to the source if not already baked in
                    if (borderSize > 0) {
                        val current = if (backgroundRemoved) selectedBitmap ?: originalBitmap ?: bitmap else originalBitmap ?: bitmap
                        addOutline(current, borderSize.toInt(), Color.WHITE)
                    } else {
                        selectedBitmap ?: originalBitmap ?: bitmap
                    }
                }

                // Create the final 512x512 sticker
                val finalBitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(finalBitmap)
                
                // Scale source to fit 512x512
                val scale = 512f / Math.max(sourceForProcessing.width.toFloat(), sourceForProcessing.height.toFloat())
                val matrix = Matrix()
                matrix.postScale(scale, scale)
                val dx = (512 - sourceForProcessing.width * scale) / 2f
                val dy = (512 - sourceForProcessing.height * scale) / 2f
                matrix.postTranslate(dx, dy)
                
                canvas.drawBitmap(sourceForProcessing, matrix, Paint(Paint.ANTI_ALIAS_FLAG))
                
                // Add to pack on IO thread
                val success = if (targetPackId != null) {
                    val added = CustomStickerManager.addStickerToPack(this@StickerMakerActivity, targetPackId!!, finalBitmap)
                    if (added) {
                        StickerRepository.loadPacks(this@StickerMakerActivity, true)
                    }
                    added
                } else {
                    false
                }

                // Update UI on Main thread
                withContext(Dispatchers.Main) {
                    isProcessing = false
                    hideProcessingOverlay()

                    if (targetPackId != null) {
                        if (success) {
                            showSuccessDialog(targetPackId!!)
                            checkQueueAndProceed(suppressToast = true)
                        } else {
                            Toast.makeText(this@StickerMakerActivity, getString(R.string.error_pack_full), Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        // We shouldn't really be here as btnAddToPack handles this, but just in case
                        selectedBitmap = finalBitmap
                        showPackSelectionDialog(finalBitmap)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isProcessing = false
                    hideProcessingOverlay()
                    Toast.makeText(this@StickerMakerActivity, getString(R.string.error_saving_sticker), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    private fun processAndAddSticker(packId: String) {
        targetPackId = packId
        
        if (isAnimatedMode) {
            if (currentUriIndex < 0 || currentUriIndex >= selectedUris.size) {
                Toast.makeText(this, getString(R.string.error_media_not_found), Toast.LENGTH_SHORT).show()
                return
            }
            val uri = selectedUris[currentUriIndex]
            
            // Video-to-WebP Dönüşümü
            val outputFile = File(cacheDir, "temp_sticker_${System.currentTimeMillis()}.webp")
            
            // FFmpeg Komutu: 512x512, max 3s, an (sesiz), webp
            val cmd = "-y -i \"${getFilePathFromUri(uri)}\" -t 3 -vf \"scale=512:512:force_original_aspect_ratio=decrease,pad=512:512:(ow-iw)/2:(oh-ih)/2:color=#00000000\" -vcodec libwebp -lossless 0 -compression_level 6 -q:v 60 -loop 0 -an \"${outputFile.absolutePath}\""
            
            showProcessingOverlay(getString(R.string.converting), getString(R.string.preparing_webp), R.drawable.ic_video)
            tvProcessingPercent.visibility = View.VISIBLE
            tvProcessingPercent.text = "0%"

            // FFmpeg Kit usage is currently commented out, so we show 'soon'
            Toast.makeText(this, R.string.animated_sticker_coming_soon, Toast.LENGTH_LONG).show()
            hideProcessingOverlay()
        } else {
            // Statik Ekleme
            val bitmap = selectedBitmap ?: originalBitmap
            if (bitmap != null) {
                processStickerResult(bitmap)
            } else {
                Toast.makeText(this, getString(R.string.error_image_not_ready), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getFilePathFromUri(uri: Uri): String {
        // FFmpeg için geçici bir dosyaya kopyalamak en güvenlisidir
        val tempFile = File(cacheDir, "input_media_${System.currentTimeMillis()}")
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        return tempFile.absolutePath
    }

    private fun checkQueueAndProceed(suppressToast: Boolean = false) {
        if (currentUriIndex < selectedUris.size - 1) {
            // Kuyrukta daha fazla görsel var, sonrakine geç
            currentUriIndex++
            loadMediaFromUri(selectedUris[currentUriIndex])
            backgroundRemoved = false
            resetEditorContent()
            updateQueueProgress()
            if (!suppressToast) Toast.makeText(this, R.string.sticker_added, Toast.LENGTH_SHORT).show()
        } else {
            // Kuyruk bitti - editörde kal, kullanıcı isterse yeni görsel seçebilir
            if (!suppressToast) Toast.makeText(this, R.string.sticker_added, Toast.LENGTH_SHORT).show()

            // Editörü sıfırla ama packId'yi koru
            val savedPackId = targetPackId
            resetEditorUI()
            targetPackId = savedPackId
        }
    }

    private fun showSuccessDialog(packId: String) {
        val dialog = AlertDialog.Builder(this, R.style.MaterialAlertDialogTheme).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        val view = layoutInflater.inflate(R.layout.dialog_select_pack, null)
        (view as LinearLayout).apply {
            removeAllViews()
            setPadding(64, 64, 64, 64)
            background = ContextCompat.getDrawable(context, R.drawable.bg_editor_container)
            
            val title = TextView(context).apply {
                text = getString(R.string.congratulations)
                textSize = 22f
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 16)
            }
            addView(title)
            
            val desc = TextView(context).apply {
                text = getString(R.string.sticker_added_success)
                textSize = 16f
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 48)
            }
            addView(desc)
            
            val btnContainer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            
            val btnViewPack = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = getString(R.string.view_pack)
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                setOnClickListener {
                    dialog.dismiss()
                    val intent = Intent(this@StickerMakerActivity, DetailsActivity::class.java)
                    intent.putExtra("id", packId)
                    startActivity(intent)
                }
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    rightMargin = 16
                }
            }
            
            val btnOk = MaterialButton(context).apply {
                text = getString(R.string.ok)
                setTextColor(Color.WHITE)
                backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.accent))
                setOnClickListener { dialog.dismiss() }
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            
            btnContainer.addView(btnViewPack)
            btnContainer.addView(btnOk)
            addView(btnContainer)
        }
        
        dialog.setView(view)
        dialog.show()
    }

    private fun showProcessingOverlay(title: String, subtitle: String, iconRes: Int) {
        processingOverlay.visibility = View.VISIBLE
        tvProcessingTitle.text = title
        tvProcessingSubtitle.text = subtitle
        processingIcon.setImageResource(iconRes)
        processingCircle.progress = 0
        tvProcessingPercent.visibility = View.GONE
    }

    private fun hideProcessingOverlay() {
        processingOverlay.visibility = View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::segmenter.isInitialized) segmenter.close()
    }

    inner class PackSelectionAdapter(
        private val packs: List<CustomStickerManager.CustomPack>,
        private val onPackSelected: (CustomStickerManager.CustomPack) -> Unit
    ) : RecyclerView.Adapter<PackSelectionAdapter.ViewHolder>() {
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(android.R.id.text1)
            val tvCount: TextView = view.findViewById(android.R.id.text2)
            init { view.setOnClickListener { onPackSelected(packs[bindingAdapterPosition]) } }
        }
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
            // Use ripple effect properly
            val attrs = intArrayOf(android.R.attr.selectableItemBackground)
            val ta = obtainStyledAttributes(attrs)
            val drawable = ta.getDrawable(0)
            ta.recycle()
            view.background = drawable
            return ViewHolder(view)
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val pack = packs[position]
            holder.tvName.text = pack.name
            holder.tvName.setTextColor(ContextCompat.getColor(this@StickerMakerActivity, R.color.text_primary))
            holder.tvCount.text = getString(R.string.sticker_count, pack.stickerCount)
            holder.tvCount.setTextColor(ContextCompat.getColor(this@StickerMakerActivity, R.color.text_secondary))
        }
        override fun getItemCount() = packs.size
    }
}
