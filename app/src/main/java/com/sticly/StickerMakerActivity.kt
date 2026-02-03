package com.sticly

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.*
import android.graphics.drawable.GradientDrawable
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import com.google.android.gms.tasks.Tasks
import com.yalantis.ucrop.UCrop
import ja.burhanrashid52.photoeditor.*
import ja.burhanrashid52.photoeditor.shape.ShapeBuilder
import ja.burhanrashid52.photoeditor.shape.ShapeType
import java.io.File
import java.io.FileOutputStream
import java.util.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import com.bumptech.glide.Glide

class StickerMakerActivity : AppCompatActivity(), OnPhotoEditorListener {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    // Views
    private lateinit var typeSelectionContainer: View
    private lateinit var editorContainer: View
    private lateinit var photoEditorView: PhotoEditorView
    private lateinit var rvTools: RecyclerView
    private lateinit var toolOptionsPanel: FrameLayout
    private lateinit var loadingOverlay: View
    private lateinit var lottieLoading: LottieAnimationView
    private lateinit var btnSaveSticker: MaterialButton
    private lateinit var btnUndo: ImageButton
    private lateinit var btnRedo: ImageButton
    private lateinit var btnClose: ImageButton
    private lateinit var adContainerMaker: FrameLayout
    private lateinit var eraserOverlay: View
    private lateinit var emptyStatePlaceholder: View
    private lateinit var btnSelectImagePlaceholder: View
    private lateinit var undoRedoContainer: View

    // PhotoEditor
    private lateinit var photoEditor: PhotoEditor

    // ML Kit for background removal
    private val subjectSegmenter by lazy {
        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundBitmap()
            .build()
        SubjectSegmentation.getClient(options)
    }

    // State
    private var currentBitmap: Bitmap? = null
    private var originalBitmap: Bitmap? = null
    private var backgroundRemoved = false
    private var targetPackId: String? = null
    private var currentToolType: ToolType? = null

    // Bitmap History for Undo/Redo
    private val bitmapHistory = mutableListOf<Bitmap>()
    private var historyIndex = -1
    private val maxHistorySize = 15

    // Shape/Brush State
    private lateinit var shapeBuilder: ShapeBuilder
    private var currentBrushSize = 40f
    private var currentBrushColor = Color.RED
    private var currentBrushOpacity = 255
    private var isEraserMode = false
    private var isBrushModeActive = false
    private var eraserPaint: Paint? = null

    // Camera
    private var cameraImageUri: Uri? = null

    // Tools
    enum class ToolType {
        REMOVE_BG, CROP, BRUSH, ERASER, TEXT, EMOJI, BORDER
    }

    data class EditorTool(
        val type: ToolType,
        val name: String,
        val iconRes: Int
    )

    private val tools by lazy {
        listOf(
            EditorTool(ToolType.REMOVE_BG, getString(R.string.remove_bg_short), R.drawable.ic_photo),
            EditorTool(ToolType.CROP, getString(R.string.crop_short), R.drawable.ic_crop),
            EditorTool(ToolType.BRUSH, getString(R.string.draw_short), R.drawable.ic_brush),
            EditorTool(ToolType.ERASER, getString(R.string.eraser_short), R.drawable.ic_eraser),
            EditorTool(ToolType.TEXT, getString(R.string.add_text_short), R.drawable.ic_text),
            EditorTool(ToolType.EMOJI, getString(R.string.add_emoji_short), R.drawable.ic_emoji),
            EditorTool(ToolType.BORDER, getString(R.string.border_short), R.drawable.ic_border),
            EditorTool(ToolType.REMOVE_BG, getString(R.string.reset_short), R.drawable.ic_restore) // Reset tool
        )
    }



    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { startCropFromUri(it) }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            cameraImageUri?.let { startCropFromUri(it) }
        }
    }

    private val cropLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.let { data ->
                UCrop.getOutput(data)?.let { uri ->
                    loadImageAfterCrop(uri)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sticker_maker)

        // MediaPipe lazy initialization - sadece gerektiğinde başlatılacak
        initViews()
        setupToolsRecyclerView()
        setupClickListeners()
        setupEraserTouchListener()
        loadAds()

        targetPackId = intent.getStringExtra("packId")
    }


    private fun initViews() {
        typeSelectionContainer = findViewById(R.id.typeSelectionContainer)
        editorContainer = findViewById(R.id.editorContainer)
        photoEditorView = findViewById(R.id.photoEditorView)
        rvTools = findViewById(R.id.rvTools)
        toolOptionsPanel = findViewById(R.id.toolOptionsPanel)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        lottieLoading = findViewById(R.id.lottieLoading)
        btnSaveSticker = findViewById(R.id.btnSaveSticker)
        btnUndo = findViewById(R.id.btnUndo)
        btnRedo = findViewById(R.id.btnRedo)
        btnClose = findViewById(R.id.btnClose)
        adContainerMaker = findViewById(R.id.adContainerMaker)
        eraserOverlay = findViewById(R.id.eraserOverlay)
        emptyStatePlaceholder = findViewById(R.id.emptyStatePlaceholder)
        btnSelectImagePlaceholder = findViewById(R.id.btnSelectImagePlaceholder)
        undoRedoContainer = findViewById(R.id.undoRedoContainer)

        // Setup Lottie
        lottieLoading.setAnimation("sandy_loading.json")

        // Initialize PhotoEditor
        photoEditor = PhotoEditor.Builder(this, photoEditorView)
            .setPinchTextScalable(true)
            .build()
        photoEditor.setOnPhotoEditorListener(this)

        shapeBuilder = ShapeBuilder()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupEraserTouchListener() {
        var lastX = -1f
        var lastY = -1f

        eraserOverlay.setOnTouchListener { _, event ->
            if (!isEraserMode) return@setOnTouchListener false

            val bitmap = currentBitmap ?: return@setOnTouchListener false

            if (eraserPaint == null) {
                eraserPaint = Paint().apply {
                    isAntiAlias = true
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
            }
            val paint = eraserPaint ?: return@setOnTouchListener false

            // Get ImageView and bitmap dimensions
            val imageView = photoEditorView.source
            val bitmapWidth = bitmap.width.toFloat()
            val bitmapHeight = bitmap.height.toFloat()

            // Get actual positions on screen
            val overlayLocation = IntArray(2)
            val imageViewLocation = IntArray(2)
            eraserOverlay.getLocationOnScreen(overlayLocation)
            imageView.getLocationOnScreen(imageViewLocation)

            // Calculate offset between eraserOverlay and imageView
            val viewOffsetX = imageViewLocation[0] - overlayLocation[0]
            val viewOffsetY = imageViewLocation[1] - overlayLocation[1]

            // Get ImageView dimensions
            val viewWidth = imageView.width.toFloat()
            val viewHeight = imageView.height.toFloat()

            if (viewWidth == 0f || viewHeight == 0f) return@setOnTouchListener false

            // Transform touch coords from eraserOverlay to imageView coordinate system
            val adjustedX = event.x - viewOffsetX
            val adjustedY = event.y - viewOffsetY

            // Calculate Scale (FitCenter)
            val viewRatio = viewWidth / viewHeight
            val bitmapRatio = bitmapWidth / bitmapHeight
            val scale: Float
            val offsetX: Float
            val offsetY: Float

            if (bitmapRatio > viewRatio) {
                scale = viewWidth / bitmapWidth
                offsetX = 0f
                offsetY = (viewHeight - bitmapHeight * scale) / 2f
            } else {
                scale = viewHeight / bitmapHeight
                offsetX = (viewWidth - bitmapWidth * scale) / 2f
                offsetY = 0f
            }

            // Transform touch coords to bitmap coords
            val touchX = (adjustedX - offsetX) / scale
            val touchY = (adjustedY - offsetY) / scale

            // Boundary check
            if (touchX < 0 || touchX >= bitmapWidth || touchY < 0 || touchY >= bitmapHeight) {
                if (event.action == MotionEvent.ACTION_MOVE) {
                    lastX = touchX
                    lastY = touchY
                } else {
                    lastX = -1f
                    lastY = -1f
                }
                return@setOnTouchListener true
            }

            val canvas = Canvas(bitmap)
            val scaledEraserSize = currentBrushSize / scale

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    saveBitmapToHistory()
                    lastX = touchX
                    lastY = touchY

                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = scaledEraserSize
                    canvas.drawPoint(touchX, touchY, paint)

                    photoEditorView.source.setImageBitmap(bitmap)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (lastX >= 0 && lastY >= 0) {
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = scaledEraserSize
                        canvas.drawLine(lastX, lastY, touchX, touchY, paint)
                    }
                    lastX = touchX
                    lastY = touchY
                    photoEditorView.source.setImageBitmap(bitmap)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    lastX = -1f
                    lastY = -1f
                    updateUndoRedoState()
                }
            }
            true
        }
    }

    private fun setupToolsRecyclerView() {
        rvTools.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvTools.adapter = ToolsAdapter(tools) { tool ->
            onToolSelected(tool)
        }
    }

    private fun setupClickListeners() {
        // Type Selection
        findViewById<View>(R.id.cardStaticType).setOnClickListener {
            showEditor()
        }

        // Editor buttons
        btnClose.setOnClickListener {
            showExitConfirmDialog()
        }

        btnUndo.setOnClickListener {
            undoSticker()
        }

        btnRedo.setOnClickListener {
            redoSticker()
        }

        btnSaveSticker.setOnClickListener {
            saveSticker()
        }

        btnSelectImagePlaceholder.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }
    }

    private fun loadAds() {
        android.util.Log.d("StickerMaker", "loadAds called, isPremium: ${PreferencesHelper.isPremium(this)}")
        if (!PreferencesHelper.isPremium(this)) {
            adContainerMaker.visibility = View.VISIBLE // Başlangıçta görünür yap
            AdManager.loadNativeAd(this, AdManager.NativeAdType.MAKER) { nativeAd ->
                android.util.Log.d("StickerMaker", "Native ad loaded, populating view")
                val adView = layoutInflater.inflate(R.layout.item_ad_native, null) as com.google.android.gms.ads.nativead.NativeAdView
                AdManager.populateNativeAdView(nativeAd, adView)
                adContainerMaker.removeAllViews()
                adContainerMaker.addView(adView)
                adContainerMaker.visibility = View.VISIBLE
            }
        } else {
            android.util.Log.d("StickerMaker", "User is premium, hiding ads")
            adContainerMaker.visibility = View.GONE
        }
    }

    // ==================== Image Loading ====================

    private fun showImageSourceDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_image_source, null)
        dialog.setContentView(view)

        view.findViewById<View>(R.id.btnGallery).setOnClickListener {
            dialog.dismiss()
            imagePickerLauncher.launch("image/*")
        }

        view.findViewById<View>(R.id.btnCamera).setOnClickListener {
            dialog.dismiss()
            openCamera()
        }

        dialog.show()
    }

    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 100)
        }
    }

    private fun launchCamera() {
        val photoFile = File(cacheDir, "camera_${System.currentTimeMillis()}.jpg")
        cameraImageUri = FileProvider.getUriForFile(this, "$packageName.provider", photoFile)
        cameraLauncher.launch(cameraImageUri)
    }

    private fun startCropFromUri(uri: Uri) {
        val cacheDir = File(cacheDir, "crop")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val destFile = File(cacheDir, "cropped_${System.currentTimeMillis()}.png")
        val destUri = Uri.fromFile(destFile)

        val options = UCrop.Options().apply {
            setCompressionFormat(Bitmap.CompressFormat.PNG)
            setCompressionQuality(100)
            setToolbarColor(ContextCompat.getColor(this@StickerMakerActivity, R.color.toolbar_bg))
            setStatusBarColor(ContextCompat.getColor(this@StickerMakerActivity, R.color.toolbar_bg))
            setActiveControlsWidgetColor(ContextCompat.getColor(this@StickerMakerActivity, R.color.accent))
            setToolbarWidgetColor(ContextCompat.getColor(this@StickerMakerActivity, R.color.white))
            setRootViewBackgroundColor(ContextCompat.getColor(this@StickerMakerActivity, R.color.background))
            setFreeStyleCropEnabled(true)
        }

        val intent = UCrop.of(uri, destUri)
            .withOptions(options)
            .getIntent(this)

        cropLauncher.launch(intent)
    }

    private fun loadImageAfterCrop(uri: Uri) {
        try {
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
            originalBitmap = bitmap
            currentBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            backgroundRemoved = false
            showEditor()
            setEditorImage(bitmap)
            saveBitmapToHistory()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.error_loading_image), Toast.LENGTH_SHORT).show()
        }
    }

    private fun setEditorImage(bitmap: Bitmap) {
        currentBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        photoEditorView.source.setImageBitmap(currentBitmap)
    }

    private fun showEditor() {
        typeSelectionContainer.visibility = View.GONE
        editorContainer.visibility = View.VISIBLE
        // If image is loaded, hide placeholder and show editor view
        if (currentBitmap != null) {
            emptyStatePlaceholder.visibility = View.GONE
            photoEditorView.visibility = View.VISIBLE
            undoRedoContainer.visibility = View.VISIBLE
        } else {
            emptyStatePlaceholder.visibility = View.VISIBLE
            photoEditorView.visibility = View.GONE
            undoRedoContainer.visibility = View.GONE
            toolOptionsPanel.removeAllViews()
            toolOptionsPanel.visibility = View.GONE
        }
    }

    // ==================== Tool Actions ====================

    private fun onToolSelected(tool: EditorTool) {
        if (currentBitmap == null) {
            Toast.makeText(this, getString(R.string.error_select_image_first), Toast.LENGTH_SHORT).show()
            return
        }

        currentToolType = tool.type
        
        // Reset brush related states when switching to non-brush tools
        if (tool.type != ToolType.BRUSH && tool.type != ToolType.ERASER) {
            photoEditor.setBrushDrawingMode(false)
            isEraserMode = false
            toolOptionsPanel.removeAllViews()
            toolOptionsPanel.visibility = View.GONE
        }

        when (tool.type) {
            ToolType.REMOVE_BG -> {
                if (tool.name == getString(R.string.reset_short)) restoreOriginalImage()
                else removeBackground()
            }
            ToolType.CROP -> startCrop()
            ToolType.BRUSH -> {
                if (isBrushModeActive && !isEraserMode) {
                    photoEditor.setBrushDrawingMode(false)
                    isBrushModeActive = false
                    toolOptionsPanel.visibility = View.GONE
                } else {
                    enableBrush()
                }
            }
            ToolType.ERASER -> {
                if (isEraserMode) {
                    isEraserMode = false
                    isBrushModeActive = false
                    eraserOverlay.visibility = View.GONE
                    toolOptionsPanel.visibility = View.GONE
                } else {
                    enableEraser()
                }
            }
            ToolType.TEXT -> showTextDialog()
            ToolType.EMOJI -> showEmojiPicker()
            ToolType.BORDER -> showBorderOptions()
        }
    }

    // ==================== Remove Background (ML Kit) ====================

    private fun removeBackground() {
        if (backgroundRemoved) {
            // Restore original
            originalBitmap?.let {
                setEditorImage(it)
                backgroundRemoved = false
            }
            return
        }

        val bitmap = currentBitmap
        if (bitmap == null) {
            Toast.makeText(this, getString(R.string.error_select_image_first), Toast.LENGTH_SHORT).show()
            return
        }

        showLoading()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                android.util.Log.d("StickerMaker", "Starting ML Kit background removal, bitmap: ${bitmap.width}x${bitmap.height}")

                // Bitmap'i ARGB_8888 formatına çevir
                val processedBitmap = if (bitmap.config != Bitmap.Config.ARGB_8888) {
                    bitmap.copy(Bitmap.Config.ARGB_8888, false)
                } else {
                    bitmap
                }

                val inputImage = InputImage.fromBitmap(processedBitmap, 0)
                android.util.Log.d("StickerMaker", "InputImage created, running ML Kit segmentation...")

                // ML Kit Subject Segmentation
                val result = Tasks.await(subjectSegmenter.process(inputImage))
                android.util.Log.d("StickerMaker", "ML Kit segmentation completed")

                val foregroundBitmap = result.foregroundBitmap
                if (foregroundBitmap == null) {
                    android.util.Log.e("StickerMaker", "Foreground bitmap is null")
                    throw Exception("Subject not found")
                }

                // Add thin white contour around the subject
                val finalBitmap = addContour(foregroundBitmap, 4, Color.WHITE)

                withContext(Dispatchers.Main) {
                    saveBitmapToHistory()
                    setEditorImage(finalBitmap)
                    backgroundRemoved = true
                    hideLoading()
                }
            } catch (e: Exception) {
                android.util.Log.e("StickerMaker", "ML Kit background removal error: ${e.message}", e)
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    hideLoading()
                    Toast.makeText(this@StickerMakerActivity, getString(R.string.bg_removal_error), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun addContour(src: Bitmap, strokeWidth: Int, color: Int): Bitmap {
        if (strokeWidth <= 0) return src

        val width = src.width
        val height = src.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // Create alpha mask from source
        val maskAlpha = src.extractAlpha() ?: return src

        // Draw contour by drawing the alpha mask multiple times with offset
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)

        // Draw contour with higher step count for smoother edges
        val steps = 16
        val radius = strokeWidth.toFloat()
        for (i in 0 until steps) {
            val angle = 2.0 * Math.PI * i / steps
            val dx = (radius * Math.cos(angle)).toFloat()
            val dy = (radius * Math.sin(angle)).toFloat()
            canvas.drawBitmap(maskAlpha, dx, dy, paint)
        }

        // Draw original image on top
        canvas.drawBitmap(src, 0f, 0f, null)
        maskAlpha.recycle()

        return output
    }

    private fun addBorder(src: Bitmap, width: Int, color: Int): Bitmap {
        if (width <= 0) return src

        val maskAlpha = src.extractAlpha() ?: return src
        val output = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)

        val radius = width.toFloat()
        val steps = 8
        for (i in 0 until steps) {
            val angle = 2.0 * Math.PI * i / steps
            val dx = (radius * Math.cos(angle)).toFloat()
            val dy = (radius * Math.sin(angle)).toFloat()
            canvas.drawBitmap(maskAlpha, dx, dy, paint)
        }

        canvas.drawBitmap(src, 0f, 0f, null)
        maskAlpha.recycle()
        return output
    }

    // ==================== Crop (uCrop) ====================

    private fun startCrop() {
        val bitmap = currentBitmap ?: return

        val cacheDir = File(cacheDir, "crop")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        val sourceFile = File(cacheDir, "source_${System.currentTimeMillis()}.png")
        val destFile = File(cacheDir, "cropped_${System.currentTimeMillis()}.png")

        FileOutputStream(sourceFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val sourceUri = Uri.fromFile(sourceFile)
        val destUri = Uri.fromFile(destFile)

        val options = UCrop.Options().apply {
            setCompressionFormat(Bitmap.CompressFormat.PNG)
            setCompressionQuality(100)
            setToolbarColor(ContextCompat.getColor(this@StickerMakerActivity, R.color.toolbar_bg))
            setStatusBarColor(ContextCompat.getColor(this@StickerMakerActivity, R.color.toolbar_bg))
            setActiveControlsWidgetColor(ContextCompat.getColor(this@StickerMakerActivity, R.color.accent))
            setToolbarWidgetColor(ContextCompat.getColor(this@StickerMakerActivity, R.color.white))
            setRootViewBackgroundColor(ContextCompat.getColor(this@StickerMakerActivity, R.color.background))
            setFreeStyleCropEnabled(true)
        }

        val intent = UCrop.of(sourceUri, destUri)
            .withOptions(options)
            .getIntent(this)

        cropLauncher.launch(intent)
    }

    // ==================== Brush ====================

    private fun enableBrush() {
        isEraserMode = false
        isBrushModeActive = true
        eraserOverlay.visibility = View.GONE
        photoEditor.setBrushDrawingMode(true)
        shapeBuilder = ShapeBuilder()
            .withShapeColor(currentBrushColor)
            .withShapeSize(currentBrushSize)
            .withShapeOpacity(currentBrushOpacity)
        photoEditor.setShape(shapeBuilder)
        
        showBrushOptions()
    }

    private fun showBrushOptions() {
        toolOptionsPanel.removeAllViews()
        val view = layoutInflater.inflate(R.layout.panel_brush_options, toolOptionsPanel, false)

        val sizeSlider = view.findViewById<Slider>(R.id.sliderBrushSize)
        sizeSlider.value = currentBrushSize
        sizeSlider.addOnChangeListener { _, value, _ ->
            currentBrushSize = value
            photoEditor.setShape(shapeBuilder.withShapeSize(value))
        }

        val colorAction = { color: Int ->
            currentBrushColor = color
            photoEditor.setShape(shapeBuilder.withShapeColor(color))
        }

        setupColorPicker(view, colorAction)

        toolOptionsPanel.addView(view)
        toolOptionsPanel.visibility = View.VISIBLE
    }

    private fun enableEraser() {
        isEraserMode = true
        isBrushModeActive = false
        photoEditor.setBrushDrawingMode(false)
        
        eraserOverlay.visibility = View.VISIBLE
        showEraserOptions()
    }

    private fun disableCustomEraser() {
        isEraserMode = false
        photoEditor.setBrushDrawingMode(false)
    }

    private fun showEraserOptions() {
        toolOptionsPanel.removeAllViews()
        val view = layoutInflater.inflate(R.layout.panel_eraser_options, toolOptionsPanel, false)

        val sizeSlider = view.findViewById<Slider>(R.id.sliderEraserSize)
        sizeSlider.valueFrom = 5f
        sizeSlider.valueTo = 200f
        sizeSlider.value = currentBrushSize.coerceIn(5f, 200f)
        sizeSlider.addOnChangeListener { _, value, _ ->
            currentBrushSize = value
        }

        toolOptionsPanel.addView(view)
        toolOptionsPanel.visibility = View.VISIBLE
    }

    private fun setupColorPicker(view: View, onColorSelected: (Int) -> Unit, initialColor: Int = currentBrushColor) {
        val colors = listOf(
            Color.RED, Color.parseColor("#FF5722"), Color.YELLOW,
            Color.GREEN, Color.parseColor("#009688"), Color.BLUE,
            Color.parseColor("#3F51B5"), Color.parseColor("#9C27B0"),
            Color.BLACK, Color.WHITE, Color.GRAY
        )
        val colorContainer = view.findViewById<LinearLayout>(R.id.colorContainer)
        colorContainer.removeAllViews()

        var selectedView: View? = null

        fun updateSelection(newSelected: View, color: Int) {
            // Remove border from previous selection
            selectedView?.let { prev ->
                val prevBg = prev.background as? GradientDrawable
                prevBg?.setStroke(2.dpToPx(), Color.WHITE)
            }
            // Add thick border to new selection
            val newBg = newSelected.background as? GradientDrawable
            newBg?.setStroke(4.dpToPx(), ContextCompat.getColor(this, R.color.accent))
            selectedView = newSelected
        }

        colors.forEach { color ->
            val colorView = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(44.dpToPx(), 44.dpToPx()).apply {
                    setMargins(0, 0, 10.dpToPx(), 0)
                }
                val bg = GradientDrawable()
                bg.shape = GradientDrawable.OVAL
                bg.setColor(color)
                // Check if this is the initially selected color
                if (color == initialColor) {
                    bg.setStroke(4.dpToPx(), ContextCompat.getColor(this@StickerMakerActivity, R.color.accent))
                } else {
                    bg.setStroke(2.dpToPx(), Color.WHITE)
                }
                background = bg
                elevation = 4f
                setOnClickListener {
                    updateSelection(this, color)
                    onColorSelected(color)
                }
            }
            if (color == initialColor) {
                selectedView = colorView
            }
            colorContainer.addView(colorView)
        }
    }


    // ==================== Text ====================

    private fun showTextDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_add_text, null)
        dialog.setContentView(view)

        val inputText = view.findViewById<TextInputEditText>(R.id.etStickerText)
        val btnAdd = view.findViewById<MaterialButton>(R.id.btnAddText)
        val tvPreview = view.findViewById<TextView>(R.id.tvTextPreviewInDialog)
        val sizeSlider = view.findViewById<Slider>(R.id.textSizeSlider)
        val fontGrid = view.findViewById<GridLayout>(R.id.fontSelectionLayout)

        var selectedColor = Color.WHITE
        var textSize = 28f
        var selectedColorView: View? = null
        var selectedFontView: View? = null
        var selectedTypeface: Typeface? = null

        // Font list with display names
        val fonts = listOf(
            "Default" to Typeface.DEFAULT,
            "Bold" to Typeface.DEFAULT_BOLD,
            "Serif" to Typeface.SERIF,
            "Sans Serif" to Typeface.SANS_SERIF,
            "Monospace" to Typeface.MONOSPACE,
            "Serif Bold" to Typeface.create(Typeface.SERIF, Typeface.BOLD),
            "Sans Bold" to Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD),
            "Mono Bold" to Typeface.create(Typeface.MONOSPACE, Typeface.BOLD),
            "Serif Italic" to Typeface.create(Typeface.SERIF, Typeface.ITALIC),
            "Sans Italic" to Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC),
            "Bold Italic" to Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC),
            "Serif B.Italic" to Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC),
            "Condensed" to Typeface.create("sans-serif-condensed", Typeface.NORMAL),
            "Condensed Bold" to Typeface.create("sans-serif-condensed", Typeface.BOLD),
            "Light" to Typeface.create("sans-serif-light", Typeface.NORMAL),
            "Thin" to Typeface.create("sans-serif-thin", Typeface.NORMAL),
            "Medium" to Typeface.create("sans-serif-medium", Typeface.NORMAL),
            "Black" to Typeface.create("sans-serif-black", Typeface.NORMAL),
            "Casual" to Typeface.create("casual", Typeface.NORMAL),
            "Cursive" to Typeface.create("cursive", Typeface.NORMAL),
            "Serif Medium" to Typeface.create("serif", Typeface.NORMAL),
            "Small Caps" to Typeface.create("sans-serif-smallcaps", Typeface.NORMAL)
        )

        // Setup font grid
        fontGrid.columnCount = 2
        fonts.forEachIndexed { index, (name, typeface) ->
            val fontCard = MaterialCardView(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(4.dpToPx(), 4.dpToPx(), 4.dpToPx(), 4.dpToPx())
                }
                radius = 12.dpToPx().toFloat()
                cardElevation = 2.dpToPx().toFloat()
                setCardBackgroundColor(ContextCompat.getColor(this@StickerMakerActivity, R.color.card_bg))
                strokeWidth = if (index == 0) 2.dpToPx() else 0
                strokeColor = ContextCompat.getColor(this@StickerMakerActivity, R.color.accent)

                val textView = TextView(this@StickerMakerActivity).apply {
                    text = name
                    typeface?.let { setTypeface(it) }
                    textSize = 14f
                    setTextColor(ContextCompat.getColor(this@StickerMakerActivity, R.color.text_primary))
                    setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 12.dpToPx())
                    gravity = android.view.Gravity.CENTER
                }
                addView(textView)

                setOnClickListener {
                    selectedFontView?.let { prev ->
                        (prev as MaterialCardView).strokeWidth = 0
                    }
                    strokeWidth = 2.dpToPx()
                    selectedFontView = this
                    selectedTypeface = typeface
                    tvPreview.typeface = typeface
                }

                if (index == 0) {
                    selectedFontView = this
                    selectedTypeface = typeface
                }
            }
            fontGrid.addView(fontCard)
        }

        // Color picker
        val colors = listOf(Color.WHITE, Color.BLACK, Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW,
            Color.MAGENTA, Color.CYAN, 0xFFFF5722.toInt(), 0xFF9C27B0.toInt())
        val colorContainer = view.findViewById<LinearLayout>(R.id.colorSelectionLayout)

        fun updateColorSelection(newSelected: View, color: Int) {
            selectedColorView?.let { prev ->
                val prevBg = prev.background as? GradientDrawable
                prevBg?.setStroke(2.dpToPx(), Color.DKGRAY)
            }
            val newBg = newSelected.background as? GradientDrawable
            newBg?.setStroke(4.dpToPx(), ContextCompat.getColor(this, R.color.accent))
            selectedColorView = newSelected
        }

        colors.forEachIndexed { index, color ->
            val colorView = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(48.dpToPx(), 48.dpToPx()).apply {
                    marginEnd = 10.dpToPx()
                }
                val bg = GradientDrawable()
                bg.shape = GradientDrawable.OVAL
                bg.setColor(color)
                if (index == 0) {
                    bg.setStroke(4.dpToPx(), ContextCompat.getColor(this@StickerMakerActivity, R.color.accent))
                } else {
                    bg.setStroke(2.dpToPx(), Color.DKGRAY)
                }
                background = bg
                elevation = 4f
                setOnClickListener {
                    updateColorSelection(this, color)
                    selectedColor = color
                    tvPreview.setTextColor(color)
                }
            }
            if (index == 0) {
                selectedColorView = colorView
            }
            colorContainer.addView(colorView)
        }

        // Size slider
        sizeSlider.addOnChangeListener { _, value, _ ->
            textSize = value
            tvPreview.textSize = value
        }

        // Live preview
        inputText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                tvPreview.text = s?.toString() ?: "Preview"
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        btnAdd.setOnClickListener {
            val text = inputText.text?.toString() ?: ""
            if (text.isNotEmpty()) {
                val textStyleBuilder = TextStyleBuilder()
                textStyleBuilder.withTextColor(selectedColor)
                textStyleBuilder.withTextSize(textSize)
                selectedTypeface?.let { textStyleBuilder.withTextFont(it) }
                photoEditor.addText(text, textStyleBuilder)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    // ==================== Emoji ====================

    private fun showEmojiPicker() {
        val dialog = BottomSheetDialog(this, R.style.TransparentBottomSheetDialog)
        val view = layoutInflater.inflate(R.layout.dialog_emoji_picker, null)
        dialog.setContentView(view)

        // Emoji categories
        val emojiCategories = mapOf(
            getString(R.string.emoji_faces) to listOf(
                "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "🙃",
                "😉", "😊", "😇", "🥰", "😍", "🤩", "😘", "😗", "😚", "😙",
                "🥲", "😋", "😛", "😜", "🤪", "😝", "🤑", "🤗", "🤭", "🤫",
                "🤔", "🤐", "🤨", "😐", "😑", "😶", "😏", "😒", "🙄", "😬",
                "😮‍💨", "🤥", "😌", "😔", "😪", "🤤", "😴", "😷", "🤒", "🤕",
                "🤢", "🤮", "🤧", "🥵", "🥶", "🥴", "😵", "🤯", "🤠", "🥳",
                "🥸", "😎", "🤓", "🧐", "😕", "😟", "🙁", "😮", "😯", "😲",
                "😳", "🥺", "😦", "😧", "😨", "😰", "😥", "😢", "😭", "😱",
                "😖", "😣", "😞", "😓", "😩", "😫", "🥱", "😤", "😡", "😠",
                "🤬", "😈", "👿", "💀", "☠️", "💩", "🤡", "👹", "👺", "👻"
            ),
            getString(R.string.emoji_hearts) to listOf(
                "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔",
                "❤️‍🔥", "❤️‍🩹", "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝",
                "💟", "♥️", "💋", "💌", "💑", "👩‍❤️‍👨", "👨‍❤️‍👨", "👩‍❤️‍👩", "🫶", "🤟"
            ),
            getString(R.string.emoji_hands) to listOf(
                "👍", "👎", "👊", "✊", "🤛", "🤜", "👏", "🙌", "👐", "🤲",
                "🤝", "🙏", "✌️", "🤞", "🤟", "🤘", "🤙", "👈", "👉", "👆",
                "👇", "☝️", "✋", "🤚", "🖐️", "🖖", "👋", "🤙", "💪", "🦾",
                "🖕", "✍️", "🤳", "💅", "🦵", "🦶", "👂", "🦻", "👃", "👀"
            ),
            getString(R.string.emoji_symbols) to listOf(
                "⭐", "🌟", "✨", "💫", "⚡", "🔥", "💥", "💢", "💦", "💨",
                "🎉", "🎊", "🎈", "🎁", "🏆", "🥇", "🥈", "🥉", "🎯", "🎮",
                "💯", "✅", "❌", "❓", "❗", "💬", "💭", "🗯️", "💤", "🔔",
                "🎵", "🎶", "🎤", "🎧", "📢", "📣", "💡", "🔮", "🧿", "💎"
            ),
            getString(R.string.emoji_animals) to listOf(
                "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯",
                "🦁", "🐮", "🐷", "🐸", "🐵", "🙈", "🙉", "🙊", "🐔", "🐧",
                "🐦", "🐤", "🦆", "🦅", "🦉", "🦇", "🐺", "🐗", "🐴", "🦄",
                "🐝", "🐛", "🦋", "🐌", "🐞", "🐜", "🦟", "🐢", "🐍", "🦎"
            ),
            getString(R.string.emoji_food) to listOf(
                "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🫐", "🍈",
                "🍒", "🍑", "🥭", "🍍", "🥥", "🥝", "🍅", "🍆", "🥑", "🥦",
                "🌽", "🥕", "🧄", "🧅", "🥔", "🍠", "🥐", "🍞", "🥖", "🥨",
                "🧀", "🥚", "🍳", "🥞", "🧇", "🥓", "🍔", "🍟", "🍕", "🌭",
                "🍿", "🧂", "🥤", "🧃", "🧋", "☕", "🍵", "🍺", "🍻", "🥂"
            ),
            getString(R.string.emoji_nature) to listOf(
                "🌸", "💮", "🏵️", "🌹", "🥀", "🌺", "🌻", "🌼", "🌷", "🌱",
                "🪴", "🌲", "🌳", "🌴", "🌵", "🌾", "🌿", "☘️", "🍀", "🍁",
                "🍂", "🍃", "🍄", "🌰", "🦀", "🦞", "🦐", "🦑", "🌍", "🌎",
                "🌏", "🌐", "🌙", "⭐", "🌟", "💫", "✨", "🌈", "☀️", "🌤️"
            ),
            getString(R.string.emoji_sport) to listOf(
                "⚽", "🏀", "🏈", "⚾", "🥎", "🎾", "🏐", "🏉", "🥏", "🎱",
                "🏓", "🏸", "🏒", "🏑", "🥍", "🏏", "🪃", "🥅", "⛳", "🪁",
                "🏹", "🎣", "🤿", "🥊", "🥋", "🎽", "🛹", "🛼", "🛷", "⛸️",
                "🥌", "🎿", "⛷️", "🏂", "🪂", "🏋️", "🤸", "🏊", "🚴", "🧘"
            )
        )

        var currentEmojis = emojiCategories.values.first()
        val gridLayout = view.findViewById<GridLayout>(R.id.emojiGrid)
        val categoryTabs = view.findViewById<LinearLayout>(R.id.emojiCategoryTabs)

        // Close button
        view.findViewById<View>(R.id.btnCloseEmoji)?.setOnClickListener {
            dialog.dismiss()
        }

        fun updateEmojiGrid(emojis: List<String>) {
            gridLayout.removeAllViews()
            gridLayout.columnCount = 6
            emojis.forEach { emoji ->
                val emojiView = TextView(this).apply {
                    text = emoji
                    textSize = 28f
                    setPadding(12.dpToPx(), 12.dpToPx(), 12.dpToPx(), 12.dpToPx())
                    gravity = android.view.Gravity.CENTER
                    background = ContextCompat.getDrawable(this@StickerMakerActivity, R.drawable.bg_emoji_item)
                    setOnClickListener {
                        photoEditor.addEmoji(emoji)
                        dialog.dismiss()
                    }
                }
                val params = GridLayout.LayoutParams().apply {
                    width = 0
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(4.dpToPx(), 4.dpToPx(), 4.dpToPx(), 4.dpToPx())
                }
                gridLayout.addView(emojiView, params)
            }
        }

        // Create category tabs
        var selectedTab: TextView? = null
        emojiCategories.keys.forEachIndexed { index, category ->
            val tabView = TextView(this).apply {
                text = category
                textSize = 14f
                setPadding(16.dpToPx(), 8.dpToPx(), 16.dpToPx(), 8.dpToPx())
                setTextColor(if (index == 0) ContextCompat.getColor(this@StickerMakerActivity, R.color.white)
                             else ContextCompat.getColor(this@StickerMakerActivity, R.color.text_secondary))
                background = if (index == 0) ContextCompat.getDrawable(this@StickerMakerActivity, R.drawable.bg_category_selected)
                             else ContextCompat.getDrawable(this@StickerMakerActivity, R.drawable.bg_category_unselected)
                setOnClickListener {
                    selectedTab?.let { prev ->
                        prev.setTextColor(ContextCompat.getColor(this@StickerMakerActivity, R.color.text_secondary))
                        prev.background = ContextCompat.getDrawable(this@StickerMakerActivity, R.drawable.bg_category_unselected)
                    }
                    setTextColor(ContextCompat.getColor(this@StickerMakerActivity, R.color.white))
                    background = ContextCompat.getDrawable(this@StickerMakerActivity, R.drawable.bg_category_selected)
                    selectedTab = this
                    updateEmojiGrid(emojiCategories[category] ?: emptyList())
                }
                if (index == 0) selectedTab = this
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = 8.dpToPx()
            }
            categoryTabs.addView(tabView, params)
        }

        // Initialize with first category
        updateEmojiGrid(currentEmojis)

        dialog.show()
    }

    private fun restoreOriginalImage() {
        val original = originalBitmap ?: return
        saveBitmapToHistory()
        setEditorImage(original)
        photoEditor.clearAllViews()
        backgroundRemoved = false
    }

    // ==================== Border ====================

    private fun showBorderOptions() {
        // Apply border to current image
        val bitmap = currentBitmap ?: return

        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_border_options, null)
        dialog.setContentView(view)

        var borderColor = Color.WHITE
        var borderSize = 12
        var selectedColorView: View? = null

        val sizeSlider = view.findViewById<Slider>(R.id.sliderBorderSize)
        val colorContainer = view.findViewById<LinearLayout>(R.id.colorContainer)
        val btnApply = view.findViewById<MaterialButton>(R.id.btnApplyBorder)

        fun updateColorSelection(newSelected: View, color: Int) {
            // Remove border from previous selection
            selectedColorView?.let { prev ->
                val prevBg = prev.background as? GradientDrawable
                prevBg?.setStroke(2.dpToPx(), Color.DKGRAY)
            }
            // Add thick accent border to new selection
            val newBg = newSelected.background as? GradientDrawable
            newBg?.setStroke(4.dpToPx(), ContextCompat.getColor(this, R.color.accent))
            selectedColorView = newSelected
        }

        val colors = listOf(
            Color.WHITE, Color.BLACK, Color.RED, Color.BLUE, Color.GREEN,
            Color.YELLOW, Color.MAGENTA, Color.CYAN, 0xFFFF5722.toInt(), 0xFF9C27B0.toInt()
        )
        colors.forEachIndexed { index, color ->
            val colorView = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(48.dpToPx(), 48.dpToPx()).apply {
                    marginEnd = 10.dpToPx()
                }
                val bg = GradientDrawable()
                bg.shape = GradientDrawable.OVAL
                bg.setColor(color)
                // First color (white) is selected by default
                if (index == 0) {
                    bg.setStroke(4.dpToPx(), ContextCompat.getColor(this@StickerMakerActivity, R.color.accent))
                } else {
                    bg.setStroke(2.dpToPx(), Color.DKGRAY)
                }
                background = bg
                elevation = 4f
                setOnClickListener {
                    updateColorSelection(this, color)
                    borderColor = color
                }
            }
            if (index == 0) {
                selectedColorView = colorView
            }
            colorContainer.addView(colorView)
        }

        sizeSlider.addOnChangeListener { _, value, _ ->
            borderSize = value.toInt()
        }

        btnApply.setOnClickListener {
            saveBitmapToHistory()
            val borderedBitmap = addBorder(bitmap, borderSize, borderColor)
            setEditorImage(borderedBitmap)
            dialog.dismiss()
        }

        dialog.show()
    }

    // ==================== Save Sticker ====================

    private fun saveSticker() {
        showLoading()

        // Geçici olarak arka planı kaldır (Şeffaflık için)
        photoEditorView.background = null

        val file = File(cacheDir, "sticker_${System.currentTimeMillis()}.png")

        val saveSettings = SaveSettings.Builder()
            .setClearViewsEnabled(false)
            .setTransparencyEnabled(true)
            .build()

        photoEditor.saveAsFile(file.absolutePath, saveSettings, object : PhotoEditor.OnSaveListener {
            override fun onSuccess(imagePath: String) {
                val bitmap = BitmapFactory.decodeFile(imagePath)
                runOnUiThread {
                    // Arka planı geri yükle
                    photoEditorView.setBackgroundResource(R.drawable.chat_wallpaper_pattern)
                    hideLoading()
                    showPackSelectionDialog(bitmap)
                }
            }

            override fun onFailure(exception: Exception) {
                runOnUiThread {
                    // Arka planı geri yükle
                    photoEditorView.setBackgroundResource(R.drawable.chat_wallpaper_pattern)
                    hideLoading()
                    Toast.makeText(this@StickerMakerActivity, getString(R.string.error_save_failed, exception.message), Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun showPackSelectionDialog(bitmap: Bitmap) {
        val view = layoutInflater.inflate(R.layout.dialog_select_pack, null)
        val rvPacks = view.findViewById<RecyclerView>(R.id.rvPacks)
        val inputPackName = view.findViewById<TextInputEditText>(R.id.inputPackName)
        val btnCreatePack = view.findViewById<MaterialButton>(R.id.btnCreatePack)

        val packs = CustomStickerManager.getCustomPacks(this).filter { !it.isAnimated }

        val dialog = AlertDialog.Builder(this, R.style.MaterialAlertDialogTheme)
            .setView(view)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        if (packs.isNotEmpty()) {
            rvPacks.visibility = View.VISIBLE
            rvPacks.layoutManager = LinearLayoutManager(this)
            rvPacks.adapter = PackSelectionAdapter(packs) { pack ->
                dialog.dismiss()
                saveStickerToPack(bitmap, pack.id)
            }
        }

        btnCreatePack.setOnClickListener {
            val packName = inputPackName.text?.toString()?.trim() ?: ""
            if (packName.isEmpty()) {
                inputPackName.error = getString(R.string.enter_pack_name)
                return@setOnClickListener
            }
            dialog.dismiss()
            val newPackId = CustomStickerManager.createPack(this, packName, false)
            saveStickerToPack(bitmap, newPackId)
        }

        dialog.show()
    }

    private fun saveStickerToPack(bitmap: Bitmap, packId: String) {
        showLoading()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Resize if needed (max 512x512 for WhatsApp)
                val resized = resizeBitmap(bitmap, 512)

                val result = CustomStickerManager.addStickerToPack(
                    this@StickerMakerActivity,
                    packId,
                    resized
                )

                withContext(Dispatchers.Main) {
                    hideLoading()
                    if (result) {
                        Toast.makeText(this@StickerMakerActivity, getString(R.string.sticker_added), Toast.LENGTH_SHORT).show()
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        Toast.makeText(this@StickerMakerActivity, getString(R.string.error_save_failed_generic), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    hideLoading()
                    Toast.makeText(this@StickerMakerActivity, getString(R.string.error_generic, e.message), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxSize && height <= maxSize) return bitmap

        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int

        if (width > height) {
            newWidth = maxSize
            newHeight = (maxSize / ratio).toInt()
        } else {
            newHeight = maxSize
            newWidth = (maxSize * ratio).toInt()
        }

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    // ==================== UI Helpers ====================

    private fun showLoading() {
        loadingOverlay.visibility = View.VISIBLE
        lottieLoading.playAnimation()
    }

    private fun hideLoading() {
        lottieLoading.cancelAnimation()
        loadingOverlay.visibility = View.GONE
    }

    private fun showExitConfirmDialog() {
        AlertDialog.Builder(this, R.style.MaterialAlertDialogTheme)
            .setTitle(getString(R.string.discard_changes))
            .setMessage(getString(R.string.discard_changes_message))
            .setPositiveButton(getString(R.string.discard)) { _, _ ->
                resetEditor()
                finish()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun resetEditor() {
        currentBitmap = null
        originalBitmap = null
        backgroundRemoved = false
        photoEditor.clearAllViews()
        toolOptionsPanel.removeAllViews()
        toolOptionsPanel.visibility = View.GONE
        showEditor()
    }



    // ==================== PhotoEditor Listener ====================

    /*
    override fun onEditTextChangeListener(rootView: View, text: String, colorCode: Int) {}
    override fun onAddViewListener(viewType: ViewType, numberOfAddedViews: Int) {
        updateUndoRedoState()
    }
    override fun onRemoveViewListener(viewType: ViewType, numberOfAddedViews: Int) {
        updateUndoRedoState()
    }
    override fun onStartViewChangeListener(viewType: ViewType) {}
    override fun onStopViewChangeListener(viewType: ViewType) {}
    override fun onTouchSourceImage(event: MotionEvent) {}
    */
    
    // Empty implementations for the interface if required
    override fun onEditTextChangeListener(rootView: View?, text: String?, colorCode: Int) {}
    override fun onAddViewListener(viewType: ViewType?, numberOfAddedViews: Int) {
        updateUndoRedoState()
    }
    override fun onRemoveViewListener(viewType: ViewType?, numberOfAddedViews: Int) {
        updateUndoRedoState()
    }
    override fun onStartViewChangeListener(viewType: ViewType?) {}
    override fun onStopViewChangeListener(viewType: ViewType?) {}
    override fun onTouchSourceImage(event: MotionEvent?) {}

    // ==================== Bitmap History (Undo/Redo) ====================

    private fun saveBitmapToHistory() {
        currentBitmap?.let { bitmap ->
            // Eğer historyIndex son değilse, sonraki history'yi temizle
            while (bitmapHistory.size > historyIndex + 1) {
                bitmapHistory.removeAt(bitmapHistory.size - 1)
            }

            // Yeni bitmap'i history'ye ekle
            bitmapHistory.add(bitmap.copy(Bitmap.Config.ARGB_8888, true))
            historyIndex = bitmapHistory.size - 1

            // Max boyutu aşarsa eski olanları sil
            while (bitmapHistory.size > maxHistorySize) {
                bitmapHistory.removeAt(0)
                historyIndex--
            }

            updateUndoRedoState()
        }
    }

    private fun undoBitmap() {
        if (historyIndex > 0) {
            historyIndex--
            currentBitmap = bitmapHistory[historyIndex].copy(Bitmap.Config.ARGB_8888, true)
            photoEditorView.source.setImageBitmap(currentBitmap)
            photoEditor.clearAllViews()
        }
    }

    private fun redoBitmap() {
        if (historyIndex < bitmapHistory.size - 1) {
            historyIndex++
            currentBitmap = bitmapHistory[historyIndex].copy(Bitmap.Config.ARGB_8888, true)
            photoEditorView.source.setImageBitmap(currentBitmap)
            photoEditor.clearAllViews()
        }
    }

    private fun undoSticker() {
        if (!photoEditor.undo()) {
            undoBitmap()
        }
        updateUndoRedoState()
    }

    private fun redoSticker() {
        if (!photoEditor.redo()) {
            redoBitmap()
        }
        updateUndoRedoState()
    }

    private fun updateUndoRedoState() {
        // Keep active to allow PhotoEditor's internal undo to catch events
        btnUndo.alpha = 1f
        btnUndo.isEnabled = true
        
        btnRedo.alpha = 1f
        btnRedo.isEnabled = true
    }

    private fun clearHistory() {
        bitmapHistory.clear()
        historyIndex = -1
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (editorContainer.visibility == View.VISIBLE) {
            showExitConfirmDialog()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        subjectSegmenter.close()
    }

    // ==================== Tools Adapter ====================

    inner class ToolsAdapter(
        private val tools: List<EditorTool>,
        private val onToolClick: (EditorTool) -> Unit
    ) : RecyclerView.Adapter<ToolsAdapter.ToolViewHolder>() {

        private var selectedPosition = -1

        inner class ToolViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val card: MaterialCardView = view.findViewById(R.id.cardView)
            val icon: ImageView = view.findViewById(R.id.icon)
            val title: TextView = view.findViewById(R.id.title)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ToolViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_editor_tool, parent, false)
            return ToolViewHolder(view)
        }

        override fun onBindViewHolder(holder: ToolViewHolder, position: Int) {
            val tool = tools[position]
            holder.icon.setImageResource(tool.iconRes)
            holder.title.text = tool.name

            val isSelected = position == selectedPosition
            holder.card.strokeWidth = if (isSelected) 2.dpToPx() else 0
            holder.card.strokeColor = if (isSelected)
                ContextCompat.getColor(this@StickerMakerActivity, R.color.accent)
            else
                Color.TRANSPARENT

            holder.itemView.setOnClickListener {
                val oldPos = selectedPosition
                selectedPosition = holder.adapterPosition
                notifyItemChanged(oldPos)
                notifyItemChanged(selectedPosition)
                onToolClick(tool)
            }
        }

        override fun getItemCount() = tools.size
    }

    // ==================== Pack Selection Adapter ====================

    inner class PackSelectionAdapter(
        private val packs: List<CustomStickerManager.CustomPack>,
        private val onPackClick: (CustomStickerManager.CustomPack) -> Unit
    ) : RecyclerView.Adapter<PackSelectionAdapter.PackViewHolder>() {

        inner class PackViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvPackName: TextView = view.findViewById(R.id.name)
            val tvStickerCount: TextView = view.findViewById(R.id.count)
            val ivTray: ImageView = view.findViewById(R.id.tray)
            val btnFavorite: View = view.findViewById(R.id.btnFavorite)
            val btnDelete: View = view.findViewById(R.id.btnDelete)
            val tvPub: View = view.findViewById(R.id.pub)
            val tvDownloadCount: View = view.findViewById(R.id.downloadCount)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PackViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_pack, parent, false)
            return PackViewHolder(view)
        }

        override fun onBindViewHolder(holder: PackViewHolder, position: Int) {
            val pack = packs[position]
            holder.tvPackName.text = pack.name
            holder.tvStickerCount.text = "${pack.stickerCount}${getString(R.string.sticker_count_suffix)}"
            
            // Hide unwanted views for selection dialog
            holder.btnFavorite.visibility = View.GONE
            holder.btnDelete.visibility = View.GONE
            holder.tvPub.visibility = View.GONE
            holder.tvDownloadCount.visibility = View.GONE

            // Load a random sticker as the cover image
            if (pack.stickerCount > 0) {
                // Pick a random sticker index from the pack
                val randomIndex = (1..pack.stickerCount).random()
                val stickerFile = CustomStickerManager.getCustomStickerPath(
                    holder.itemView.context, 
                    pack.id, 
                    "sticker_$randomIndex.webp"
                )
                
                Glide.with(holder.itemView.context)
                    .load(stickerFile)
                    .placeholder(R.drawable.ic_sticker_placeholder)
                    .error(R.drawable.ic_sticker_placeholder)
                    .into(holder.ivTray)
            } else {
                holder.ivTray.setImageResource(R.drawable.ic_sticker_placeholder)
            }

            holder.itemView.setOnClickListener { onPackClick(pack) }
        }

        override fun getItemCount() = packs.size
    }
    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
}
