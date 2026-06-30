package com.sticly

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.*
import android.net.Uri
import android.os.Bundle

import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.ScaleGestureDetector
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
import android.media.ExifInterface
import android.os.Build

class StickerMakerActivity : AppCompatActivity(), OnPhotoEditorListener {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    // Views
    private lateinit var editorContainer: View
    private lateinit var photoEditorView: PhotoEditorView
    private lateinit var photoEditorCard: View
    private lateinit var toolOptionsPanel: FrameLayout
    private lateinit var loadingOverlay: View
    private lateinit var lottieLoading: LottieAnimationView
    private lateinit var btnSaveSticker: View
    private lateinit var btnUndo: ImageButton
    private lateinit var btnRedo: ImageButton
    private lateinit var btnClose: ImageButton
    private lateinit var adContainerMaker: FrameLayout
    private lateinit var eraserOverlay: View
    private lateinit var emptyStatePlaceholder: View
    private lateinit var btnSelectImagePlaceholder: View

    // Top-bar tool buttons
    private lateinit var btnToolCrop: ImageButton
    private lateinit var btnToolRemoveBg: ImageButton
    private lateinit var btnToolEmoji: ImageButton
    private lateinit var btnToolText: ImageButton
    private lateinit var btnToolBrush: ImageButton
    private lateinit var btnToolEraser: ImageButton
    private lateinit var btnToolBorder: ImageButton
    private lateinit var btnToolSticker: ImageButton
    private var activeToolButton: ImageButton? = null

    // PhotoEditor
    private lateinit var photoEditor: PhotoEditor

    // ML Kit for background removal
    private lateinit var subjectSegmenter: com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
    private var isSegmenterReady = false

    private fun initSegmenter() {
        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundBitmap()
            .build()
        subjectSegmenter = SubjectSegmentation.getClient(options)

        // Pre-warm: ML Kit model indirilmesini tetikle
        // Küçük bir dummy bitmap ile modeli önceden hazırla
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val warmupBitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                val warmupImage = InputImage.fromBitmap(warmupBitmap, 0)
                Tasks.await(subjectSegmenter.process(warmupImage))
                warmupBitmap.recycle()
                isSegmenterReady = true
                android.util.Log.d("StickerMaker", "ML Kit model pre-warmed successfully")
            } catch (e: Exception) {
                // Model henüz indirilmemiş olabilir, ilk gerçek kullanımda tekrar denenecek
                android.util.Log.w("StickerMaker", "ML Kit warmup attempt: ${e.message}")
                isSegmenterReady = true // İlk kullanımda retry mekanizması devreye girecek
            }
        }
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

        // ML Kit Subject Segmentation modelini önceden başlat
        initSegmenter()
        initViews()
        setupToolButtons()
        setupClickListeners()
        setupEraserTouchListener()
        loadAds()

        targetPackId = intent.getStringExtra("packId")

        // Editor is the primary view; set initial empty state
        showEditor()

        // Handle launch intents
        val editImageUri = intent.getStringExtra("editImageUri")
        when {
            intent.getBooleanExtra("skipTypeSelection", false) -> {
                imagePickerLauncher.launch("image/*")
            }
            editImageUri != null -> {
                photoEditorView.post {
                    try {
                        loadImageAfterCrop(android.net.Uri.parse(editImageUri))
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(this, getString(R.string.error_loading_image), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            else -> {
                showTypeSelectionDialog()
            }
        }
    }


    private fun initViews() {
        editorContainer = findViewById(R.id.editorContainer)
        photoEditorView = findViewById(R.id.photoEditorView)
        photoEditorCard = findViewById(R.id.photoEditorCard)
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

        // Top-bar tool buttons
        btnToolCrop = findViewById(R.id.btnToolCrop)
        btnToolRemoveBg = findViewById(R.id.btnToolRemoveBg)
        btnToolEmoji = findViewById(R.id.btnToolEmoji)
        btnToolText = findViewById(R.id.btnToolText)
        btnToolBrush = findViewById(R.id.btnToolBrush)
        btnToolEraser = findViewById(R.id.btnToolEraser)
        btnToolBorder = findViewById(R.id.btnToolBorder)
        btnToolSticker = findViewById(R.id.btnToolSticker)

        // Dark status/nav bars
        window.statusBarColor = Color.parseColor("#1B1B1B")
        window.navigationBarColor = Color.parseColor("#1B1B1B")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }

        // Setup Lottie
        lottieLoading.setAnimation("material_wave_loading.json")
        lottieLoading.loop(true)

        // Initialize PhotoEditor
        photoEditor = PhotoEditor.Builder(this, photoEditorView)
            .setPinchTextScalable(true)
            .build()
        photoEditor.setOnPhotoEditorListener(this)

        shapeBuilder = ShapeBuilder()

        setupCheckerboard()
        setupPinchZoom()
        setActiveToolButton(null)
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

            val imageView = photoEditorView.source
            val bitmapWidth = bitmap.width.toFloat()
            val bitmapHeight = bitmap.height.toFloat()
            val viewWidth = imageView.width.toFloat()
            val viewHeight = imageView.height.toFloat()

            if (viewWidth == 0f || viewHeight == 0f) return@setOnTouchListener false

            // Direct coordinates — eraserOverlay and photoEditorView are same-size siblings
            val localX = event.x
            val localY = event.y

            // Calculate FitCenter scale
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
            val touchX = (localX - offsetX) / scale
            val touchY = (localY - offsetY) / scale

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

    private fun setupToolButtons() {
        val toolMap = mapOf(
            btnToolCrop to ToolType.CROP,
            btnToolRemoveBg to ToolType.REMOVE_BG,
            btnToolEmoji to ToolType.EMOJI,
            btnToolText to ToolType.TEXT,
            btnToolBrush to ToolType.BRUSH,
            btnToolEraser to ToolType.ERASER,
            btnToolBorder to ToolType.BORDER
        )
        for ((button, type) in toolMap) {
            button.setOnClickListener {
                setActiveToolButton(button)
                onToolSelected(EditorTool(type, "", 0))
            }
        }
        btnToolSticker.setOnClickListener {
            setActiveToolButton(btnToolSticker)
            showGiphyStickerPicker()
        }
    }

    private fun setupClickListeners() {
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
            // Önce preloaded reklamı kontrol et
            val preloadedAd = AdManager.getPreloadedMakerAd()
            if (preloadedAd != null) {
                android.util.Log.d("StickerMaker", "Using preloaded native ad")
                val adView = layoutInflater.inflate(R.layout.item_ad_native, null) as com.google.android.gms.ads.nativead.NativeAdView
                AdManager.populateNativeAdView(preloadedAd, adView)
                adContainerMaker.removeAllViews()
                adContainerMaker.addView(adView)
                adContainerMaker.visibility = View.VISIBLE
                // Bir sonraki kullanım için yeni reklam yükle
                AdManager.preloadMakerNativeAd(this)
            } else {
                // Preload yoksa normal yükle
                adContainerMaker.visibility = View.VISIBLE
                AdManager.loadNativeAd(this, AdManager.NativeAdType.MAKER) { nativeAd ->
                    android.util.Log.d("StickerMaker", "Native ad loaded, populating view")
                    val adView = layoutInflater.inflate(R.layout.item_ad_native, null) as com.google.android.gms.ads.nativead.NativeAdView
                    AdManager.populateNativeAdView(nativeAd, adView)
                    adContainerMaker.removeAllViews()
                    adContainerMaker.addView(adView)
                    adContainerMaker.visibility = View.VISIBLE
                }
            }
        } else {
            android.util.Log.d("StickerMaker", "User is premium, hiding ads")
            adContainerMaker.visibility = View.GONE
        }
    }

    // ==================== Image Loading ====================

    private fun showImageSourceDialog() {
        val dialog = BottomSheetDialog(this, R.style.RoundedBottomSheetDialog)
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
        cameraImageUri?.let { cameraLauncher.launch(it) } ?: return
    }

    private fun startCropFromUri(uri: Uri) {
        val cropCacheDir = File(cacheDir, "crop")
        if (!cropCacheDir.exists()) cropCacheDir.mkdirs()
        val destFile = File(cropCacheDir, "cropped_${System.currentTimeMillis()}.png")
        val destUri = Uri.fromFile(destFile)

        showLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            // Fix EXIF orientation before passing to UCrop to prevent flipped images
            val sourceUri = fixImageOrientation(uri, cropCacheDir)

            withContext(Dispatchers.Main) {
                hideLoading()
                launchCrop(sourceUri, destUri)
            }
        }
    }

    private fun launchCrop(sourceUri: Uri, destUri: Uri) {
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

    private fun fixImageOrientation(uri: Uri, cacheDir: File): Uri {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return uri
            val exif = ExifInterface(inputStream)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            inputStream.close()

            if (orientation == ExifInterface.ORIENTATION_NORMAL ||
                orientation == ExifInterface.ORIENTATION_UNDEFINED) {
                return uri
            }

            val bitmap = contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return uri

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.postRotate(90f)
                    matrix.postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.postRotate(-90f)
                    matrix.postScale(-1f, 1f)
                }
            }

            val corrected = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (corrected != bitmap) bitmap.recycle()

            val correctedFile = File(cacheDir, "exif_corrected_${System.currentTimeMillis()}.jpg")
            FileOutputStream(correctedFile).use { out ->
                corrected.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }
            corrected.recycle()

            return Uri.fromFile(correctedFile)
        } catch (e: Exception) {
            e.printStackTrace()
            return uri
        }
    }

    private fun loadImageAfterCrop(uri: Uri) {
        try {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = if (uri.scheme == "file") {
                    ImageDecoder.createSource(java.io.File(uri.path ?: return))
                } else {
                    ImageDecoder.createSource(contentResolver, uri)
                }
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                }
            } else {
                if (uri.scheme == "file") {
                    BitmapFactory.decodeFile(uri.path)
                } else {
                    contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }
            }
            if (bitmap == null) {
                Toast.makeText(this, getString(R.string.error_loading_image), Toast.LENGTH_SHORT).show()
                return
            }
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
        try {
            currentBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            photoEditorView.source.setImageBitmap(currentBitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.error_loading_image), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEditor() {
        editorContainer.visibility = View.VISIBLE
        // If image is loaded, hide placeholder and show editor view
        if (currentBitmap != null) {
            emptyStatePlaceholder.visibility = View.GONE
            photoEditorCard.visibility = View.VISIBLE
        } else {
            emptyStatePlaceholder.visibility = View.VISIBLE
            photoEditorCard.visibility = View.GONE
            toolOptionsPanel.removeAllViews()
            toolOptionsPanel.visibility = View.GONE
        }
    }

    // ==================== Tool Actions ====================

    private var lastToolClickTime = 0L

    private fun onToolSelected(tool: EditorTool) {
        if (currentBitmap == null) {
            Toast.makeText(this, getString(R.string.error_select_image_first), Toast.LENGTH_SHORT).show()
            return
        }

        // Debounce: ignore rapid clicks within 500ms
        val now = System.currentTimeMillis()
        if (now - lastToolClickTime < 500) return
        lastToolClickTime = now

        currentToolType = tool.type
        
        // Reset brush related states when switching to non-brush tools
        if (tool.type != ToolType.BRUSH && tool.type != ToolType.ERASER) {
            photoEditor.setBrushDrawingMode(false)
            isEraserMode = false
            isBrushModeActive = false
            eraserOverlay.visibility = View.GONE
            toolOptionsPanel.removeAllViews()
            toolOptionsPanel.visibility = View.GONE
        }

        when (tool.type) {
            ToolType.REMOVE_BG -> removeBackground()
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
        updateToolHighlight()
    }

    // ==================== Remove Background (ML Kit) ====================

    private var bgRemovalRetryCount = 0
    private val MAX_BG_RETRY = 3

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

        bgRemovalRetryCount = 0
        attemptRemoveBackground(bitmap)
    }

    private fun attemptRemoveBackground(bitmap: Bitmap) {
        showLoading()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                android.util.Log.d("StickerMaker", "Starting ML Kit background removal (attempt ${bgRemovalRetryCount + 1}/$MAX_BG_RETRY), bitmap: ${bitmap.width}x${bitmap.height}")

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
                    bgRemovalRetryCount = 0
                    saveBitmapToHistory()
                    setEditorImage(finalBitmap)
                    backgroundRemoved = true
                    hideLoading()
                }
            } catch (e: Exception) {
                android.util.Log.e("StickerMaker", "ML Kit background removal error (attempt ${bgRemovalRetryCount + 1}): ${e.message}", e)
                bgRemovalRetryCount++

                if (bgRemovalRetryCount < MAX_BG_RETRY) {
                    // Model henüz hazır olmayabilir, 1 saniye bekle ve tekrar dene
                    android.util.Log.d("StickerMaker", "Retrying background removal in 1 second... (attempt ${bgRemovalRetryCount + 1})")
                    delay(1000L)
                    attemptRemoveBackground(bitmap)
                } else {
                    withContext(Dispatchers.Main) {
                        hideLoading()
                        Toast.makeText(this@StickerMakerActivity, getString(R.string.bg_removal_error), Toast.LENGTH_SHORT).show()
                    }
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
                layoutParams = LinearLayout.LayoutParams(32.dpToPx(), 32.dpToPx()).apply {
                    setMargins(0, 0, 8.dpToPx(), 0)
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

    private var textEditorOverlay: View? = null

    @SuppressLint("ClickableViewAccessibility")
    private fun showTextDialog() {
        if (currentBitmap == null) {
            Toast.makeText(this, getString(R.string.error_select_image_first), Toast.LENGTH_SHORT).show()
            setActiveToolButton(null)
            return
        }

        // Add overlay to content view (below status bar) — not decorView
        val rootView = findViewById<android.widget.FrameLayout>(android.R.id.content)
        val overlay = layoutInflater.inflate(R.layout.dialog_text_editor_fullscreen, rootView, false)
        rootView.addView(overlay)
        textEditorOverlay = overlay

        // Adjust resize so keyboard pushes content up
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val etText = overlay.findViewById<android.widget.EditText>(R.id.etTextOverlay)
        val btnClose = overlay.findViewById<View>(R.id.btnTextClose)
        val btnDone = overlay.findViewById<View>(R.id.btnTextDone)
        val colorDots = overlay.findViewById<LinearLayout>(R.id.textColorDots)
        val fontRow = overlay.findViewById<LinearLayout>(R.id.textFontRow)

        var selectedColor = android.graphics.Color.WHITE
        var selectedColorDotView: View? = null
        var selectedTypeface: Typeface? = null
        var selectedFontView: TextView? = null

        fun dismissOverlay() {
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(etText.windowToken, 0)
            rootView.removeView(overlay)
            textEditorOverlay = null
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
            setActiveToolButton(null)
        }

        // Fonts: load from assets for reliable visual distinction
        fun assetFont(file: String): Typeface = try {
            Typeface.createFromAsset(assets, "fonts/$file")
        } catch (e: Exception) { Typeface.DEFAULT }

        val fontList = listOf(
            "Roboto"     to assetFont("Roboto.ttf"),
            "Bold"       to assetFont("DroidSansBold.ttf"),
            "Serif"      to assetFont("DroidSerif.ttf"),
            "Serif Bold" to assetFont("DroidSerifBold.ttf"),
            "Serif It."  to assetFont("DroidSerifItalic.ttf"),
            "Mono"       to assetFont("DroidSansMono.ttf"),
            "DroidSans"  to assetFont("DroidSans.ttf"),
            "Light"      to assetFont("RobotoLight.ttf"),
            "Thin"       to assetFont("RobotoThin.ttf"),
            "Medium"     to assetFont("RobotoMedium.ttf"),
            "Condensed"  to Typeface.create("sans-serif-condensed", Typeface.BOLD),
            "Black"      to Typeface.create("sans-serif-black", Typeface.NORMAL)
        )
        selectedTypeface = fontList[0].second

        fun selectFont(chip: TextView, tf: Typeface) {
            selectedFontView?.let { prev ->
                prev.setTextColor(android.graphics.Color.parseColor("#AAAAAA"))
                prev.setBackgroundResource(R.drawable.bg_style_chip_inactive)
            }
            chip.setTextColor(android.graphics.Color.BLACK)
            chip.setBackgroundResource(R.drawable.bg_style_chip_active)
            selectedFontView = chip
            selectedTypeface = tf
            etText.typeface = tf
        }

        fontList.forEachIndexed { index, (name, tf) ->
            val chip = TextView(this).apply {
                // Show "Abc" in each font so user can see the difference clearly
                text = "Abc"
                textSize = 15f
                typeface = tf
                gravity = android.view.Gravity.CENTER
                setPadding(12.dpToPx(), 6.dpToPx(), 12.dpToPx(), 6.dpToPx())
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    36.dpToPx()
                ).apply { marginEnd = 6.dpToPx() }
                layoutParams = params
                contentDescription = name
            }
            chip.setOnClickListener { selectFont(chip, tf) }
            if (index == 0) {
                chip.setTextColor(android.graphics.Color.BLACK)
                chip.setBackgroundResource(R.drawable.bg_style_chip_active)
                selectedFontView = chip
            } else {
                chip.setTextColor(android.graphics.Color.parseColor("#CCCCCC"))
                chip.setBackgroundResource(R.drawable.bg_style_chip_inactive)
            }
            fontRow.addView(chip)
        }

        // Color dots
        val colors = listOf(
            0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFFF0000.toInt(),
            0xFF00C853.toInt(), 0xFF2196F3.toInt(), 0xFFFF9800.toInt(),
            0xFFE91E63.toInt(), 0xFF9C27B0.toInt(), 0xFFFFEB3B.toInt(),
            0xFF00BCD4.toInt(), 0xFFFF5722.toInt(), 0xFF607D8B.toInt()
        )
        colors.forEachIndexed { index, color ->
            val dot = View(this).apply {
                val dotSize = 34.dpToPx()
                layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply { marginEnd = 8.dpToPx() }
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    if (index == 0) setStroke(3.dpToPx(), Color.parseColor("#6C5CE7"))
                }
                background = bg
                setOnClickListener {
                    selectedColorDotView?.let { prev ->
                        (prev.background as? GradientDrawable)?.setStroke(0, 0)
                    }
                    bg.setStroke(3.dpToPx(), Color.parseColor("#6C5CE7"))
                    selectedColor = color
                    selectedColorDotView = this
                    etText.setTextColor(color)
                }
            }
            if (index == 0) selectedColorDotView = dot
            colorDots.addView(dot)
        }

        // Show keyboard
        etText.requestFocus()
        etText.postDelayed({
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(etText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }, 150)

        btnClose.setOnClickListener { dismissOverlay() }

        btnDone.setOnClickListener {
            val text = etText.text?.toString()?.trim() ?: ""
            if (text.isNotEmpty()) {
                val tsb = TextStyleBuilder()
                tsb.withTextColor(selectedColor)
                tsb.withTextSize(28f)
                selectedTypeface?.let { tsb.withTextFont(it) }
                photoEditor.addText(text, tsb)
            }
            dismissOverlay()
        }
    }

    // ==================== Emoji ====================

    private fun showEmojiPicker() {
        val dialog = BottomSheetDialog(this, R.style.TransparentBottomSheetDialog)
        val view = layoutInflater.inflate(R.layout.dialog_emoji_picker, null)
        dialog.setContentView(view)

        // Expand bottom sheet to show all emojis
        dialog.setOnShowListener {
            val bs = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bs?.let { sheet ->
                val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheet)
                behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }

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
                    textSize = 32f
                    setPadding(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
                    gravity = android.view.Gravity.CENTER
                    background = null
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
                setTextColor(if (index == 0) android.graphics.Color.WHITE
                             else android.graphics.Color.parseColor("#AAAAAA"))
                background = if (index == 0) ContextCompat.getDrawable(this@StickerMakerActivity, R.drawable.bg_category_selected)
                             else null
                setOnClickListener {
                    selectedTab?.let { prev ->
                        prev.setTextColor(android.graphics.Color.parseColor("#AAAAAA"))
                        prev.background = null
                    }
                    setTextColor(android.graphics.Color.WHITE)
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

    // ==================== GIPHY Sticker Picker ====================

    private val giphyApiKey = "LLWhfEaYJSNyuhTXUEnSol15YU00raps"

    private fun showGiphyStickerPicker() {
        if (currentBitmap == null) {
            Toast.makeText(this, getString(R.string.error_select_image_first), Toast.LENGTH_SHORT).show()
            setActiveToolButton(null)
            return
        }
        val dialog = BottomSheetDialog(this, R.style.RoundedBottomSheetDialog)
        val view = layoutInflater.inflate(R.layout.dialog_giphy_sticker, null)
        dialog.setContentView(view)

        dialog.setOnShowListener {
            val bs = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bs?.let { sheet ->
                val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheet)
                behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }

        val rvStickers = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvGiphyStickers)
        val etSearch = view.findViewById<android.widget.EditText>(R.id.etGiphySearch)
        val categoryTabs = view.findViewById<LinearLayout>(R.id.giphyCategoryTabs)
        val btnCancel = view.findViewById<View>(R.id.btnGiphyCancel)

        data class GiphyItem(val id: String, val previewUrl: String, val originalUrl: String)
        val items = mutableListOf<GiphyItem>()

        val adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
                val iv = android.widget.ImageView(this@StickerMakerActivity).apply {
                    layoutParams = androidx.recyclerview.widget.RecyclerView.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        (parent.width / 4).coerceAtLeast(80)
                    )
                    scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                    setPadding(4.dpToPx(), 4.dpToPx(), 4.dpToPx(), 4.dpToPx())
                }
                return object : androidx.recyclerview.widget.RecyclerView.ViewHolder(iv) {}
            }
            override fun getItemCount() = items.size
            override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
                val item = items[position]
                val iv = holder.itemView as android.widget.ImageView
                com.bumptech.glide.Glide.with(this@StickerMakerActivity)
                    .asGif()
                    .load(item.previewUrl)
                    .placeholder(R.drawable.sticker_placeholder)
                    .into(iv)
                iv.setOnClickListener {
                    dialog.dismiss()
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val url = java.net.URL(item.originalUrl)
                            val connection = url.openConnection() as java.net.HttpURLConnection
                            connection.connect()
                            val input = connection.inputStream
                            val gifBytes = input.readBytes()
                            connection.disconnect()
                            val bmp = android.graphics.BitmapFactory.decodeByteArray(gifBytes, 0, gifBytes.size)
                                ?: return@launch
                            val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, 200, 200, true)
                            withContext(Dispatchers.Main) {
                                photoEditor.addImage(scaled)
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@StickerMakerActivity, "Failed to add sticker", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }
        rvStickers.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 4)
        rvStickers.adapter = adapter

        fun loadGiphy(query: String) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
                    val url = if (query == "trending") {
                        "https://api.giphy.com/v1/stickers/trending?api_key=$giphyApiKey&limit=50&rating=g"
                    } else {
                        "https://api.giphy.com/v1/stickers/search?api_key=$giphyApiKey&q=$encodedQuery&limit=50&rating=g"
                    }
                    val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connect()
                    val response = connection.inputStream.bufferedReader().readText()
                    connection.disconnect()
                    val json = org.json.JSONObject(response)
                    val dataArray = json.getJSONArray("data")
                    val newItems = mutableListOf<GiphyItem>()
                    for (i in 0 until dataArray.length()) {
                        val obj = dataArray.getJSONObject(i)
                        val id = obj.getString("id")
                        val images = obj.getJSONObject("images")
                        val previewUrl = images.getJSONObject("fixed_width_small").getString("url")
                        val origUrl = images.getJSONObject("original").getString("url")
                        newItems.add(GiphyItem(id, previewUrl, origUrl))
                    }
                    withContext(Dispatchers.Main) {
                        items.clear()
                        items.addAll(newItems)
                        adapter.notifyDataSetChanged()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@StickerMakerActivity, "Error loading stickers", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        val categories = listOf("trending", "effects", "bubble", "love", "funny", "animals", "food")
        val displayNames = listOf("Trending", "Effects", "Bubble", "Love", "Funny", "Animals", "Food")
        var selectedTabView: TextView? = null

        categories.forEachIndexed { index, cat ->
            val tab = TextView(this).apply {
                text = displayNames[index]
                textSize = 13f
                setPadding(14.dpToPx(), 6.dpToPx(), 14.dpToPx(), 6.dpToPx())
                setTextColor(if (index == 0) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#888888"))
                if (index == 0) {
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                        cornerRadius = 16.dpToPx().toFloat()
                        setColor(android.graphics.Color.parseColor("#6C5CE7"))
                    }
                    selectedTabView = this
                }
                val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT).apply {
                    marginEnd = 6.dpToPx()
                }
                layoutParams = params
                setOnClickListener {
                    selectedTabView?.let { prev ->
                        prev.setTextColor(android.graphics.Color.parseColor("#888888"))
                        prev.background = null
                    }
                    setTextColor(android.graphics.Color.WHITE)
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                        cornerRadius = 16.dpToPx().toFloat()
                        setColor(android.graphics.Color.parseColor("#6C5CE7"))
                    }
                    selectedTabView = this
                    loadGiphy(cat)
                }
            }
            categoryTabs.addView(tab)
        }

        etSearch.setOnEditorActionListener { v, _, _ ->
            val q = v.text.toString().trim()
            if (q.isNotEmpty()) loadGiphy(q)
            true
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.setOnDismissListener { setActiveToolButton(null) }

        loadGiphy("trending")
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

        val dialog = BottomSheetDialog(this, R.style.RoundedBottomSheetDialog)
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
                layoutParams = LinearLayout.LayoutParams(32.dpToPx(), 32.dpToPx()).apply {
                    marginEnd = 6.dpToPx()
                }
                val bg = GradientDrawable()
                bg.shape = GradientDrawable.OVAL
                bg.setColor(color)
                // First color (white) is selected by default
                if (index == 0) {
                    bg.setStroke(3.dpToPx(), ContextCompat.getColor(this@StickerMakerActivity, R.color.accent))
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
                    // Restore checkerboard background
                    setupCheckerboard()
                    hideLoading()
                    showPackSelectionDialog(bitmap)
                }
            }

            override fun onFailure(exception: Exception) {
                runOnUiThread {
                    // Restore checkerboard background
                    setupCheckerboard()
                    hideLoading()
                    Toast.makeText(this@StickerMakerActivity, getString(R.string.error_save_failed, exception.message), Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun showPackSelectionDialog(bitmap: Bitmap) {
        targetPackId?.let { packId ->
            saveStickerToPack(bitmap, packId)
            return
        }

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
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_discard_changes)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.85).toInt(),
            android.view.WindowManager.LayoutParams.WRAP_CONTENT
        )

        dialog.findViewById<android.widget.Button>(R.id.btnDiscardCancel).setOnClickListener {
            dialog.dismiss()
        }
        dialog.findViewById<android.widget.Button>(R.id.btnDiscardConfirm).setOnClickListener {
            dialog.dismiss()
            resetEditor()
            finish()
        }
        dialog.show()
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
    
    // Double-tap-to-edit tracking: the library calls onEditTextChangeListener on every single tap.
    private var lastTextTapView: View? = null
    private var lastTextTapTime = 0L

    override fun onEditTextChangeListener(rootView: View?, text: String?, colorCode: Int) {
        // A single tap only SELECTS the text (the library draws the bounding box on touch); the
        // edit dialog opens only on a DOUBLE tap of the same text element.
        if (rootView == null || text == null) return
        val now = System.currentTimeMillis()
        if (rootView === lastTextTapView && now - lastTextTapTime < 350L) {
            lastTextTapView = null
            lastTextTapTime = 0L
            showEditTextDialog(rootView, text, colorCode)
        } else {
            lastTextTapView = rootView
            lastTextTapTime = now
        }
    }
    override fun onAddViewListener(viewType: ViewType?, numberOfAddedViews: Int) {
        updateUndoRedoState()
        if (viewType == ViewType.TEXT || viewType == ViewType.EMOJI || viewType == ViewType.IMAGE) {
            photoEditorView.post {
                expandStickerTouchArea()
            }
        }
    }

    /**
     * PhotoEditor adds sticker/text/emoji views as direct children of photoEditorView
     * (a RelativeLayout). Built-in children are DrawingView, ImageFilterView, FilterImageView.
     * We find the last non-built-in child and expand its touch area with padding + transparent bg.
     */
    private fun expandStickerTouchArea() {
        val builtinNames = setOf("DrawingView", "FilterImageView", "ImageFilterView", "ImageView")
        val extraPad = (80 * resources.displayMetrics.density).toInt()

        for (i in photoEditorView.childCount - 1 downTo 0) {
            val child = photoEditorView.getChildAt(i) as? ViewGroup ?: continue
            val name = child.javaClass.simpleName
            if (builtinNames.any { name.contains(it) }) continue

            // This is a sticker container. Expand its touch area.
            child.setPadding(extraPad, extraPad, extraPad, extraPad)
            child.clipChildren = false
            child.clipToPadding = false
            // Non-null transparent background ensures touch events register in padded area
            child.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            break
        }
    }


    private fun showEditTextDialog(textView: View, currentText: String, currentColor: Int) {
        val dialog = BottomSheetDialog(this, R.style.RoundedBottomSheetDialog)
        val view = layoutInflater.inflate(R.layout.dialog_add_text, null)
        dialog.setContentView(view)

        val inputText = view.findViewById<TextInputEditText>(R.id.etStickerText)
        val btnAdd = view.findViewById<MaterialButton>(R.id.btnAddText)
        val tvPreview = view.findViewById<TextView>(R.id.tvTextPreviewInDialog)
        val fontGrid = view.findViewById<GridLayout>(R.id.fontSelectionLayout)

        // Set current values
        inputText.setText(currentText)
        var selectedColor = currentColor
        var fixedTextSize = 28f
        var selectedColorView: View? = null
        var selectedFontView: View? = null
        var selectedTypeface: Typeface? = null
        
        // Try to get current text size from view
        if (textView is TextView) {
            fixedTextSize = textView.textSize / resources.displayMetrics.scaledDensity
            selectedTypeface = textView.typeface
        }

        btnAdd.text = getString(R.string.update_text)

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
        fonts.forEachIndexed { _, (name, typeface) ->
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
                strokeWidth = if (typeface == selectedTypeface) 2.dpToPx() else 0
                strokeColor = ContextCompat.getColor(this@StickerMakerActivity, R.color.accent)

                val fontTextView = TextView(this@StickerMakerActivity).apply {
                    text = name
                    typeface?.let { setTypeface(it) }
                    textSize = 12f
                    setTextColor(ContextCompat.getColor(this@StickerMakerActivity, R.color.text_primary))
                    setPadding(10.dpToPx(), 6.dpToPx(), 10.dpToPx(), 6.dpToPx())
                    gravity = android.view.Gravity.CENTER
                }
                addView(fontTextView)

                setOnClickListener {
                    selectedFontView?.let { prev ->
                        (prev as MaterialCardView).strokeWidth = 0
                    }
                    strokeWidth = 2.dpToPx()
                    selectedFontView = this
                    selectedTypeface = typeface
                    tvPreview.typeface = typeface
                }

                if (typeface == selectedTypeface) {
                    selectedFontView = this
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
            newBg?.setStroke(3.dpToPx(), ContextCompat.getColor(this, R.color.accent))
            selectedColorView = newSelected
            selectedColor = color
            tvPreview.setTextColor(color)
        }

        colors.forEachIndexed { _, color ->
            val colorView = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(32.dpToPx(), 32.dpToPx()).apply {
                    marginEnd = 6.dpToPx()
                }
                val bg = GradientDrawable()
                bg.shape = GradientDrawable.OVAL
                bg.setColor(color)
                if (color == selectedColor) {
                    bg.setStroke(3.dpToPx(), ContextCompat.getColor(this@StickerMakerActivity, R.color.accent))
                    selectedColorView = this
                } else {
                    bg.setStroke(2.dpToPx(), Color.DKGRAY)
                }
                background = bg
                elevation = 4f
                setOnClickListener {
                    updateColorSelection(this, color)
                }
            }
            colorContainer.addView(colorView)
        }

        // Live preview
        tvPreview.text = currentText
        tvPreview.setTextColor(selectedColor)
        tvPreview.textSize = fixedTextSize
        tvPreview.typeface = selectedTypeface
        
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
                textStyleBuilder.withTextSize(fixedTextSize)
                selectedTypeface?.let { textStyleBuilder.withTextFont(it) }
                photoEditor.editText(textView, text, textStyleBuilder)
                dialog.dismiss()
            }
        }

        dialog.show()
    }
    override fun onRemoveViewListener(viewType: ViewType?, numberOfAddedViews: Int) {
        updateUndoRedoState()
    }
    override fun onStartViewChangeListener(viewType: ViewType?) {}
    override fun onStopViewChangeListener(viewType: ViewType?) {}
    override fun onTouchSourceImage(event: MotionEvent?) {
        // Tapping the empty background deselects the currently-selected sticker/text/emoji
        // (clears the bounding box). A single tap on empty space is enough.
        if (event?.action == MotionEvent.ACTION_UP) {
            photoEditor.clearHelperBox()
            lastTextTapView = null
        }
    }

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
        }
    }

    private fun redoBitmap() {
        if (historyIndex < bitmapHistory.size - 1) {
            historyIndex++
            currentBitmap = bitmapHistory[historyIndex].copy(Bitmap.Config.ARGB_8888, true)
            photoEditorView.source.setImageBitmap(currentBitmap)
        }
    }

    private fun undoSticker() {
        // Try PhotoEditor undo first (brush, text, emoji — most recent actions)
        if (!photoEditor.undo()) {
            // If PhotoEditor has nothing to undo, try bitmap history
            if (historyIndex > 0) {
                undoBitmap()
            }
        }
        updateUndoRedoState()
    }

    private fun redoSticker() {
        // Try PhotoEditor redo first
        if (!photoEditor.redo()) {
            // If PhotoEditor has nothing to redo, try bitmap history
            if (historyIndex < bitmapHistory.size - 1) {
                redoBitmap()
            }
        }
        updateUndoRedoState()
    }

    private fun updateUndoRedoState() {
        val canUndoBitmap = historyIndex > 0
        btnUndo.alpha = if (canUndoBitmap) 1f else 0.5f
        btnUndo.isEnabled = true

        val canRedoBitmap = historyIndex < bitmapHistory.size - 1
        btnRedo.alpha = if (canRedoBitmap) 1f else 0.5f
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
        showExitConfirmDialog()
    }

    override fun onDestroy() {
        super.onDestroy()
        subjectSegmenter.close()
    }

    // ==================== Tool Highlight ====================

    private fun updateToolHighlight() {
        when {
            isBrushModeActive && !isEraserMode -> setActiveToolButton(btnToolBrush)
            isEraserMode -> setActiveToolButton(btnToolEraser)
            else -> { /* keep current active button as-is */ }
        }
    }

    private fun setActiveToolButton(active: ImageButton?) {
        val allButtons = listOf(btnToolRemoveBg, btnToolCrop, btnToolBrush, btnToolEraser, btnToolText, btnToolEmoji, btnToolBorder, btnToolSticker)
        val activeColor = ContextCompat.getColor(this, R.color.modern_primary)
        val inactiveColor = Color.parseColor("#AAAAAA")
        for (btn in allButtons) {
            val isActive = btn == active
            btn.imageTintList = ColorStateList.valueOf(if (isActive) activeColor else inactiveColor)
            val parent = btn.parent as? ViewGroup
            parent?.let {
                for (i in 0 until it.childCount) {
                    val child = it.getChildAt(i)
                    if (child is TextView) {
                        child.setTextColor(if (isActive) activeColor else inactiveColor)
                    }
                }
            }
        }
        activeToolButton = active
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupPinchZoom() {
        val zoomLayout = findViewById<ZoomableFrameLayout>(R.id.canvasContainer) ?: return
        val photoCard = findViewById<View>(R.id.photoEditorCard) ?: return
        zoomLayout.targetView = photoCard
        zoomLayout.zoomEnabled = true
    }

    private fun setupCheckerboard() {
        val size = 24
        val px = (size * resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(px * 2, px * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint1 = Paint().apply { color = Color.parseColor("#3A3A3A") }
        val paint2 = Paint().apply { color = Color.parseColor("#2A2A2A") }
        canvas.drawRect(0f, 0f, px.toFloat(), px.toFloat(), paint1)
        canvas.drawRect(px.toFloat(), 0f, (px * 2).toFloat(), px.toFloat(), paint2)
        canvas.drawRect(0f, px.toFloat(), px.toFloat(), (px * 2).toFloat(), paint2)
        canvas.drawRect(px.toFloat(), px.toFloat(), (px * 2).toFloat(), (px * 2).toFloat(), paint1)
        @Suppress("DEPRECATION")
        val bitmapDrawable = android.graphics.drawable.BitmapDrawable(resources, bitmap).apply {
            tileModeX = android.graphics.Shader.TileMode.REPEAT
            tileModeY = android.graphics.Shader.TileMode.REPEAT
        }
        // Only set checkerboard on the FIXED canvasContainer background.
        // photoEditorView stays transparent so its transparent pixels show through
        // to this fixed checkerboard — image floats over the grid, grid never moves.
        photoEditorView.setBackgroundColor(Color.TRANSPARENT)
        @Suppress("DEPRECATION")
        findViewById<ZoomableFrameLayout>(R.id.canvasContainer)?.setBackgroundDrawable(bitmapDrawable)
    }

    private fun showTypeSelectionDialog() {
        val options = arrayOf(
            "\uD83D\uDCF8 ${getString(R.string.static_sticker_title)}",
            "\uD83C\uDFAC ${getString(R.string.animated_sticker_title)}"
        )
        AlertDialog.Builder(this, R.style.MaterialAlertDialogTheme)
            .setTitle(getString(R.string.how_to_create_sticker))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { /* Static sticker: editor already showing */ }
                    1 -> {
                        val intent = Intent(this, AnimatedStickerActivity::class.java)
                        targetPackId?.let { intent.putExtra("packId", it) }
                        startActivity(intent)
                        finish()
                    }
                }
            }
            .setOnCancelListener { /* Stay on editor */ }
            .show()
    }

    // ==================== Pack Selection Adapter ====================

    inner class PackSelectionAdapter(
        private val packs: List<CustomStickerManager.CustomPack>,
        private val onPackClick: (CustomStickerManager.CustomPack) -> Unit
    ) : RecyclerView.Adapter<PackSelectionAdapter.PackViewHolder>() {

        inner class PackViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvPackName: TextView = view.findViewById(R.id.tvPackName)
            val tvStickerCount: TextView = view.findViewById(R.id.tvPackCount)
            val ivCover: ImageView = view.findViewById(R.id.ivPackCover)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PackViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_pack_selection, parent, false)
            return PackViewHolder(view)
        }

        override fun onBindViewHolder(holder: PackViewHolder, position: Int) {
            val pack = packs[position]
            holder.tvPackName.text = pack.name
            holder.tvStickerCount.text = "${pack.stickerCount}${getString(R.string.sticker_count_suffix)}"

            if (pack.stickerCount > 0) {
                val stickerFile = CustomStickerManager.getCustomStickerPath(
                    holder.itemView.context, 
                    pack.id, 
                    "sticker_1.webp"
                )
                Glide.with(holder.itemView.context)
                    .load(stickerFile)
                    .placeholder(R.drawable.ic_sticker_placeholder)
                    .error(R.drawable.ic_sticker_placeholder)
                    .into(holder.ivCover)
            } else {
                holder.ivCover.setImageResource(R.drawable.ic_sticker_placeholder)
            }

            holder.itemView.setOnClickListener { onPackClick(pack) }
        }

        override fun getItemCount() = packs.size
    }
    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
}
