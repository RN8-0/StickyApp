package com.sticly

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.*
import java.io.File
import kotlin.math.atan2
import kotlin.math.sqrt

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AnimatedStickerActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AnimatedSticker"
        private const val STICKER_SIZE = 512
        private const val MAX_FILE_SIZE_KB = 500
        private const val TARGET_FPS = 12
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    // ── Step 1: Trim + Crop selection views ──
    private lateinit var cropContainer: View
    private lateinit var cropPlayerView: PlayerView
    private lateinit var cropOverlayView: CropOverlayView
    private lateinit var btnCropNext: MaterialButton
    private lateinit var trimFrameRecyclerView: RecyclerView
    private lateinit var trimRangeSelectionView: RangeSelectionView
    private lateinit var tvTrimDuration: TextView

    // Trim state
    private var sourceVideoUri: Uri? = null
    private var videoDurationMs = 0L
    private var trimStartMs = 0L
    private var trimEndMs = 0L
    private val MIN_DURATION = 1
    private val MAX_DURATION = 5
    private val FRAME_COUNT = 10

    // ── Step 2: Processing views ──
    private lateinit var processingContainer: View
    private lateinit var lottieProcessing: LottieAnimationView

    // ── Save Processing Overlay ──
    private lateinit var saveProcessingOverlay: View
    private lateinit var lottieSaveProcessing: LottieAnimationView

    // ── Step 3: Edit views ──
    private lateinit var editContainer: View
    private lateinit var editPreviewImage: ImageView
    private lateinit var overlayContainer: FrameLayout
    private lateinit var toolOptionsPanel: FrameLayout
    private lateinit var btnSaveSticker: MaterialButton
    private lateinit var editPreviewArea: FrameLayout

    // Player
    private var exoPlayer: ExoPlayer? = null

    // State
    private var trimmedVideoPath: String? = null
    private var cropShape: CropShape = CropShape.NONE
    private var outputFile: File? = null
    private var targetPackId: String? = null
    private var currentPanel: String? = null // "text" or "emoji" or null

    // Overlay data
    data class OverlayItem(
        val type: String,
        var content: String,
        var color: String = "#FFFFFF",
        var sizeSp: Int = 36,
        var normX: Float = 0.5f,
        var normY: Float = 0.5f,
        var scale: Float = 1f,
        var rotation: Float = 0f,
        var view: View? = null,
        var typeface: Typeface? = null
    )

    private val overlayItems = mutableListOf<OverlayItem>()

    enum class CropShape { NONE, CIRCLE, SQUARE, RECTANGLE, VERTICAL_RECT }

    // ── Launchers ──

    private val videoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            sourceVideoUri = uri
            showCropScreen()
        } else finish()
    }

    // ── Lifecycle ──

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_animated_sticker)
        initViews()
        targetPackId = intent.getStringExtra("packId")
        
        // Only launch video picker on first creation, not when recreated
        if (savedInstanceState == null) {
            videoPickerLauncher.launch("video/*")
        }
    }

    private fun initViews() {
        cropContainer = findViewById(R.id.cropContainer)
        cropPlayerView = findViewById(R.id.cropPlayerView)
        cropOverlayView = findViewById(R.id.cropOverlayView)
        btnCropNext = findViewById(R.id.btnCropNext)
        trimFrameRecyclerView = findViewById(R.id.trimFrameRecyclerView)
        trimRangeSelectionView = findViewById(R.id.trimRangeSelectionView)
        tvTrimDuration = findViewById(R.id.tvTrimDuration)

        processingContainer = findViewById(R.id.processingContainer)
        lottieProcessing = findViewById(R.id.lottieProcessing)
        lottieProcessing.setAnimation("material_wave_loading.json")
        lottieProcessing.loop(true)

        editContainer = findViewById(R.id.editContainer)
        editPreviewImage = findViewById(R.id.editPreviewImage)
        overlayContainer = findViewById(R.id.overlayContainer)
        toolOptionsPanel = findViewById(R.id.toolOptionsPanel)
        btnSaveSticker = findViewById(R.id.btnSaveSticker)
        editPreviewArea = findViewById(R.id.editPreviewArea)

        saveProcessingOverlay = findViewById(R.id.saveProcessingOverlay)
        lottieSaveProcessing = findViewById(R.id.lottieSaveProcessing)
        lottieSaveProcessing.setAnimation("material_wave_loading.json")
        lottieSaveProcessing.repeatCount = com.airbnb.lottie.LottieDrawable.INFINITE

        // Set initial visibility - all hidden
        cropContainer.visibility = View.GONE
        processingContainer.visibility = View.GONE
        editContainer.visibility = View.GONE

        // Trim frame list
        trimFrameRecyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        trimFrameRecyclerView.adapter = TrimFrameAdapter(emptyList())
        trimFrameRecyclerView.suppressLayout(true)

        // Range selection for trim
        trimRangeSelectionView.onRangeChanged = { leftNorm, rightNorm ->
            trimStartMs = (leftNorm * videoDurationMs).toLong()
            trimEndMs = (rightNorm * videoDurationMs).toLong()
            updateTrimDuration()
            exoPlayer?.seekTo(trimStartMs)
        }

        findViewById<ImageButton>(R.id.btnCropBack).setOnClickListener { finish() }
        btnCropNext.setOnClickListener { trimAndStartConversion() }
        setupCropButtons()

        findViewById<ImageButton>(R.id.btnEditBack).setOnClickListener { 
            // Go back to crop screen instead of exiting
            showCropScreen()
        }
        btnSaveSticker.setOnClickListener { saveSticker() }

        // Toggle panels on button click
        findViewById<View>(R.id.btnToolText).setOnClickListener { 
            hideKeyboard()
            showTextPanel()
        }
        findViewById<View>(R.id.btnToolEmoji).setOnClickListener { showEmojiPicker() }

        editPreviewArea.setOnClickListener { hideKeyboard() }
    }

    private fun togglePanel(panel: String) {
        hideKeyboard()
        if (currentPanel == panel) {
            // Close panel
            toolOptionsPanel.removeAllViews()
            toolOptionsPanel.visibility = View.GONE
            currentPanel = null
        } else {
            currentPanel = panel
            if (panel == "text") showTextPanel()
            else {
                hideKeyboard()
                showEmojiPanel()
            }
        }
    }

    // ══════════════════════════════════════════════
    //  STEP 1: TRIM + CROP (combined)
    // ══════════════════════════════════════════════

    private fun showCropScreen() {
        cropContainer.visibility = View.VISIBLE
        processingContainer.visibility = View.GONE
        editContainer.visibility = View.GONE
        setupPlayer()
        updateCropOverlay()
    }

    private fun updateTrimDuration() {
        val durationSec = (trimEndMs - trimStartMs) / 1000f
        tvTrimDuration.text = String.format("%.1fs", durationSec)
        val isValid = durationSec >= MIN_DURATION && durationSec <= MAX_DURATION
        btnCropNext.isEnabled = isValid
        tvTrimDuration.setTextColor(if (isValid) 0xFFFFFFFF.toInt() else getColor(R.color.error))
    }

    private fun extractVideoFrames() {
        val uri = sourceVideoUri ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            val frames = mutableListOf<Bitmap>()
            try {
                retriever.setDataSource(this@AnimatedStickerActivity, uri)
                val frameInterval = videoDurationMs / FRAME_COUNT
                for (i in 0 until FRAME_COUNT) {
                    val frameTimeUs = (i * frameInterval) * 1000
                    val bitmap = retriever.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    bitmap?.let { frames.add(Bitmap.createScaledBitmap(it, 200, 150, false)) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting frames: ${e.message}")
            } finally {
                retriever.release()
            }
            withContext(Dispatchers.Main) {
                trimFrameRecyclerView.suppressLayout(false)
                trimFrameRecyclerView.adapter = TrimFrameAdapter(frames)
                trimFrameRecyclerView.suppressLayout(true)
            }
        }
    }

    private fun trimAndStartConversion() {
        val uri = sourceVideoUri ?: return
        btnCropNext.isEnabled = false

        lifecycleScope.launch {
            val outputPath = withContext(Dispatchers.IO) {
                trimVideo(uri, trimStartMs, trimEndMs)
            }
            if (outputPath != null) {
                trimmedVideoPath = outputPath
                startConversion()
            } else {
                Toast.makeText(this@AnimatedStickerActivity, getString(R.string.trim_failed), Toast.LENGTH_SHORT).show()
                btnCropNext.isEnabled = true
                btnCropNext.text = getString(R.string.save)
            }
        }
    }

    private fun trimVideo(uri: Uri, startMs: Long, endMs: Long): String? {
        try {
            val inputPath = getInputPath(uri)
            if (inputPath == null) {
                Log.e(TAG, "trimVideo: inputPath is null")
                return null
            }
            val inputFile = File(inputPath)
            if (!inputFile.exists() || inputFile.length() == 0L) {
                Log.e(TAG, "trimVideo: input file missing or empty: $inputPath (exists=${inputFile.exists()}, size=${inputFile.length()})")
                return null
            }
            Log.d(TAG, "trimVideo: input=$inputPath size=${inputFile.length()} startMs=$startMs endMs=$endMs")

            val ts = System.currentTimeMillis()
            val startSec = String.format(java.util.Locale.US, "%.3f", startMs / 1000.0)
            val durationSec = String.format(java.util.Locale.US, "%.3f", (endMs - startMs) / 1000.0)
            val outPath = File(cacheDir, "trimmed_$ts.mp4").absolutePath

            // Strategy 1: stream copy (fastest)
            val cmd1 = "-y -ss $startSec -i $inputPath -t $durationSec -c copy $outPath"
            Log.d(TAG, "Trim strategy 1 (copy): $cmd1")
            var session = FFmpegKit.execute(cmd1)
            var out = File(outPath)
            if (ReturnCode.isSuccess(session.returnCode) && out.exists() && out.length() > 0L) {
                Log.d(TAG, "Strategy 1 success, size=${out.length()}")
                return outPath
            }
            Log.w(TAG, "Strategy 1 failed: rc=${session.returnCode}")

            // Strategy 2: re-encode video + audio
            out.delete()
            val cmd2 = "-y -ss $startSec -i $inputPath -t $durationSec -c:v mpeg4 -q:v 3 -c:a aac -b:a 128k $outPath"
            Log.d(TAG, "Trim strategy 2 (mpeg4+aac): $cmd2")
            session = FFmpegKit.execute(cmd2)
            out = File(outPath)
            if (ReturnCode.isSuccess(session.returnCode) && out.exists() && out.length() > 0L) {
                Log.d(TAG, "Strategy 2 success, size=${out.length()}")
                return outPath
            }
            Log.w(TAG, "Strategy 2 failed: rc=${session.returnCode}")

            // Strategy 3: re-encode video only, no audio
            out.delete()
            val cmd3 = "-y -ss $startSec -i $inputPath -t $durationSec -c:v mpeg4 -q:v 3 -an $outPath"
            Log.d(TAG, "Trim strategy 3 (mpeg4, no audio): $cmd3")
            session = FFmpegKit.execute(cmd3)
            out = File(outPath)
            if (ReturnCode.isSuccess(session.returnCode) && out.exists() && out.length() > 0L) {
                Log.d(TAG, "Strategy 3 success, size=${out.length()}")
                return outPath
            }
            Log.e(TAG, "All trim strategies failed. Last log: ${session.logsAsString?.takeLast(500)}")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error trimming: ${e.message}", e)
            return null
        }
    }

    private var tempInputFile: File? = null

    private fun getInputPath(uri: Uri): String? {
        return try {
            when (uri.scheme) {
                "file" -> uri.path
                "content" -> {
                    // Always copy fresh to avoid stale cache issues
                    val tempFile = File(cacheDir, "temp_input_${System.currentTimeMillis()}.mp4")
                    contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    // Clean up old temp file
                    tempInputFile?.let { if (it.exists() && it != tempFile) it.delete() }
                    tempInputFile = tempFile
                    Log.d(TAG, "getInputPath: copied to ${tempFile.absolutePath} size=${tempFile.length()}")
                    if (tempFile.length() == 0L) {
                        Log.e(TAG, "getInputPath: copied file is empty!")
                        return null
                    }
                    tempFile.absolutePath
                }
                else -> {
                    Log.e(TAG, "getInputPath: unsupported scheme: ${uri.scheme}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting input path: ${e.message}", e)
            null
        }
    }

    private fun setupCropButtons() {
        val btnNone = findViewById<View>(R.id.btnCropNone)
        val btnCircle = findViewById<View>(R.id.btnCropCircle)
        val btnSquare = findViewById<View>(R.id.btnCropSquare)
        val btnRect = findViewById<View>(R.id.btnCropRect)

        val iconNone = findViewById<ImageView>(R.id.iconCropNone)
        val iconCircle = findViewById<ImageView>(R.id.iconCropCircle)
        val iconSquare = findViewById<ImageView>(R.id.iconCropSquare)
        val iconRect = findViewById<ImageView>(R.id.iconCropRect)

        val labelNone = findViewById<TextView>(R.id.labelCropNone)
        val labelCircle = findViewById<TextView>(R.id.labelCropCircle)
        val labelSquare = findViewById<TextView>(R.id.labelCropSquare)
        val labelRect = findViewById<TextView>(R.id.labelCropRect)

        val allIcons = listOf(iconNone, iconCircle, iconSquare, iconRect)
        val allLabels = listOf(labelNone, labelCircle, labelSquare, labelRect)

        fun selectCrop(shape: CropShape, selectedIcon: ImageView, selectedLabel: TextView) {
            cropShape = shape
            updateCropOverlay()
            val activeColor = ContextCompat.getColor(this, R.color.accent)
            val inactiveColor = Color.parseColor("#999999")
            for (icon in allIcons) icon.setColorFilter(inactiveColor)
            for (label in allLabels) label.setTextColor(inactiveColor)
            selectedIcon.setColorFilter(activeColor)
            selectedLabel.setTextColor(activeColor)
        }

        selectCrop(CropShape.NONE, iconNone, labelNone)
        btnNone.setOnClickListener { selectCrop(CropShape.NONE, iconNone, labelNone) }
        btnCircle.setOnClickListener { selectCrop(CropShape.CIRCLE, iconCircle, labelCircle) }
        btnSquare.setOnClickListener { selectCrop(CropShape.SQUARE, iconSquare, labelSquare) }
        btnRect.setOnClickListener { selectCrop(CropShape.RECTANGLE, iconRect, labelRect) }
    }

    private fun updateCropOverlay() {
        cropOverlayView.shape = when (cropShape) {
            CropShape.NONE -> CropOverlayView.Shape.NONE
            CropShape.CIRCLE -> CropOverlayView.Shape.CIRCLE
            CropShape.SQUARE -> CropOverlayView.Shape.SQUARE
            CropShape.RECTANGLE -> CropOverlayView.Shape.RECTANGLE
            CropShape.VERTICAL_RECT -> CropOverlayView.Shape.VERTICAL_RECT
        }
    }

    // ══════════════════════════════════════════════
    //  PLAYER
    // ══════════════════════════════════════════════

    private var isVideoLoaded = false

    private fun setupPlayer() {
        releasePlayer()
        val uri = sourceVideoUri ?: return
        isVideoLoaded = false
        exoPlayer = ExoPlayer.Builder(this).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        cropOverlayView.setVideoDimensions(videoSize.width, videoSize.height)
                    }
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY && !isVideoLoaded) {
                        isVideoLoaded = true
                        videoDurationMs = duration
                        val maxDurationMs = MAX_DURATION * 1000L
                        trimEndMs = minOf(videoDurationMs, maxDurationMs)
                        trimStartMs = 0L
                        val rightNorm = trimEndMs.toFloat() / videoDurationMs
                        trimRangeSelectionView.setRange(0f, rightNorm)
                        updateTrimDuration()
                        extractVideoFrames()
                    }
                }
            })
            prepare()
        }
        cropPlayerView.player = exoPlayer
    }

    // ══════════════════════════════════════════════
    //  STEP 2: CONVERSION
    // ══════════════════════════════════════════════

    private fun startConversion() {
        releasePlayer()
        cropContainer.visibility = View.GONE
        processingContainer.visibility = View.VISIBLE
        editContainer.visibility = View.GONE
        lottieProcessing.playAnimation()

        lifecycleScope.launch(Dispatchers.IO) {
            val success = convertToAnimatedWebP()
            withContext(Dispatchers.Main) {
                lottieProcessing.cancelAnimation()
                processingContainer.visibility = View.GONE
                if (success) showEditScreen()
                else {
                    Toast.makeText(this@AnimatedStickerActivity, getString(R.string.conversion_failed), Toast.LENGTH_LONG).show()
                    showCropScreen()
                }
            }
        }
    }

    private suspend fun convertToAnimatedWebP(): Boolean {
        val inputFile = File(trimmedVideoPath ?: return false)
        if (!inputFile.exists()) return false

        outputFile?.delete()
        outputFile = File(cacheDir, "anim_sticker_${System.currentTimeMillis()}.webp")

        val cropFilter = buildCropFilter()
        val circleAlpha = if (cropShape == CropShape.CIRCLE) {
            ",geq='lum(X,Y)':cb='cb(X,Y)':cr='cr(X,Y)':a='if(gt(pow(X-W/2,2)+pow(Y-H/2,2),pow(min(W,H)/2,2)),0,255)'"
        } else ""

        var q = 60; var ok = false // Start at 60 instead of 70 for speed
        while (q >= 20 && !ok) {
            outputFile?.delete()
            val vf = "${cropFilter}scale=$STICKER_SIZE:$STICKER_SIZE:force_original_aspect_ratio=decrease,pad=$STICKER_SIZE:$STICKER_SIZE:-1:-1:color=0x00000000@0x00,fps=$TARGET_FPS${circleAlpha}"
            // Compression level 2 is much faster than 4 or 6
            val cmd = "-y -i \"${inputFile.absolutePath}\" -vf \"$vf\" -vcodec libwebp -lossless 0 -compression_level 2 -quality $q -loop 0 -preset default -an -pix_fmt yuva420p \"${outputFile!!.absolutePath}\""
            Log.d(TAG, "FFmpeg cmd: $cmd")
            val session = FFmpegKit.execute(cmd)
            if (ReturnCode.isSuccess(session.returnCode)) {
                val kb = outputFile!!.length() / 1024
                Log.d(TAG, "Output size: ${kb}KB at q=$q")
                if (kb <= MAX_FILE_SIZE_KB) ok = true else q -= 25
            } else {
                Log.e(TAG, "FFmpeg fail: ${session.allLogsAsString}")
                break
            }
        }

        if (!ok && outputFile?.exists() == true && outputFile!!.length() / 1024 > MAX_FILE_SIZE_KB) {
            outputFile?.delete()
            val vf = "${cropFilter}scale=$STICKER_SIZE:$STICKER_SIZE:force_original_aspect_ratio=decrease,pad=$STICKER_SIZE:$STICKER_SIZE:-1:-1:color=0x00000000@0x00,fps=10${circleAlpha}"
            val cmd = "-y -i \"${inputFile.absolutePath}\" -vf \"$vf\" -vcodec libwebp -lossless 0 -compression_level 6 -quality 20 -loop 0 -preset default -an -pix_fmt yuva420p \"${outputFile!!.absolutePath}\""
            ok = ReturnCode.isSuccess(FFmpegKit.execute(cmd).returnCode)
        }
        return ok && outputFile?.exists() == true
    }

    private fun buildCropFilter(): String {
        if (cropShape == CropShape.NONE) return ""
        val (cw, ch) = cropOverlayView.getCropSizePixels()
        val (cx, cy) = cropOverlayView.getCropOffsetPixels()
        if (cw <= 0 || ch <= 0) {
            return when (cropShape) {
                CropShape.SQUARE, CropShape.CIRCLE ->
                    "crop=min(iw\\,ih):min(iw\\,ih):(iw-min(iw\\,ih))/2:(ih-min(iw\\,ih))/2,"
                CropShape.RECTANGLE ->
                    "crop=iw:min(ih\\,iw*3/4):0:(ih-min(ih\\,iw*3/4))/2,"
                else -> ""
            }
        }
        return "crop=$cw:$ch:$cx:$cy,"
    }

    // ══════════════════════════════════════════════
    //  OVERLAY BITMAP RENDERING
    // ══════════════════════════════════════════════

    private fun renderOverlayBitmap(): File? {
        if (overlayItems.isEmpty()) return null
        val bmp = Bitmap.createBitmap(STICKER_SIZE, STICKER_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        for (item in overlayItems) {
            val px = item.normX * STICKER_SIZE
            val py = item.normY * STICKER_SIZE
            val fontSize = (item.sizeSp * item.scale).coerceIn(8f, 200f)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = fontSize
                textAlign = Paint.Align.CENTER
            }
            if (item.type == "text") {
                paint.color = Color.parseColor(item.color)
                paint.typeface = Typeface.DEFAULT_BOLD
                paint.setShadowLayer(4f, 1f, 1f, Color.parseColor("#80000000"))
            }
            val fm = paint.fontMetrics
            val textY = py - (fm.ascent + fm.descent) / 2f
            canvas.save()
            canvas.rotate(item.rotation, px, py)
            canvas.drawText(item.content, px, textY, paint)
            canvas.restore()
        }
        val overlayFile = File(cacheDir, "overlay_${System.currentTimeMillis()}.png")
        overlayFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return overlayFile
    }

    // ══════════════════════════════════════════════
    //  STEP 3: EDIT SCREEN
    // ══════════════════════════════════════════════

    private fun showEditScreen() {
        cropContainer.visibility = View.GONE
        processingContainer.visibility = View.GONE
        editContainer.visibility = View.VISIBLE

        outputFile?.let { file ->
            if (file.exists()) {
                Glide.with(this)
                    .load(file)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .into(editPreviewImage)
            }
        }

        overlayContainer.removeAllViews()
        overlayItems.clear()
        toolOptionsPanel.removeAllViews()
        toolOptionsPanel.visibility = View.GONE
        currentPanel = null
    }

    // ── Draggable Overlay System ──

    private fun addOverlayView(item: OverlayItem) {
        val tv = TextView(this).apply {
            text = item.content
            if (item.type == "text") {
                setTextColor(Color.parseColor(item.color))
                textSize = item.sizeSp.toFloat()
                typeface = item.typeface ?: Typeface.DEFAULT_BOLD
                setShadowLayer(4f, 1f, 1f, Color.parseColor("#80000000"))
            } else {
                // Emoji - ensure fully opaque rendering
                textSize = item.sizeSp.toFloat()
                alpha = 1.0f
                setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            }
            gravity = Gravity.CENTER
            // Large padding = large touch area for easier gestures
            setPadding(dpToPx(60), dpToPx(60), dpToPx(60), dpToPx(60))
        }

        tv.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        tv.post {
            val cw = overlayContainer.width.toFloat()
            val ch = overlayContainer.height.toFloat()
            tv.x = cw / 2f - tv.width / 2f
            tv.y = ch / 2f - tv.height / 2f
            item.normX = 0.5f
            item.normY = 0.5f
        }

        setupDragAndPinch(tv, item)
        overlayContainer.addView(tv)
        item.view = tv
    }

    private fun setupDragAndPinch(view: View, item: OverlayItem) {
        var lastTouchX = 0f
        var lastTouchY = 0f
        var pinchStartDist = 0f
        var pinchStartScale = 1f
        var pinchStartAngle = 0f
        var pinchStartRotation = 0f
        var mode = 0
        var lastTapTime = 0L

        view.setOnTouchListener { v, event ->
            val cw = overlayContainer.width.toFloat()
            val ch = overlayContainer.height.toFloat()

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    mode = 1
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY

                    if (item.type == "text") {
                        val now = System.currentTimeMillis()
                        if (now - lastTapTime < 300) {
                            showEditTextOverlay(item)
                            return@setOnTouchListener true
                        }
                        lastTapTime = now
                    }

                    v.parent.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount >= 2) {
                        mode = 2
                        pinchStartDist = getSpacing(event)
                        pinchStartScale = item.scale
                        pinchStartAngle = getAngle(event)
                        pinchStartRotation = item.rotation
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (mode == 1 && event.pointerCount == 1) {
                        val dx = event.rawX - lastTouchX
                        val dy = event.rawY - lastTouchY
                        v.x += dx
                        v.y += dy
                        lastTouchX = event.rawX
                        lastTouchY = event.rawY
                        if (cw > 0 && ch > 0) {
                            item.normX = (v.x + v.width / 2f) / cw
                            item.normY = (v.y + v.height / 2f) / ch
                        }
                    } else if (mode == 2 && event.pointerCount >= 2) {
                        val nd = getSpacing(event)
                        if (pinchStartDist > 10f) {
                            item.scale = (pinchStartScale * nd / pinchStartDist).coerceIn(0.3f, 4f)
                            v.scaleX = item.scale
                            v.scaleY = item.scale
                        }
                        item.rotation = pinchStartRotation + (getAngle(event) - pinchStartAngle)
                        v.rotation = item.rotation
                    }
                    true
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    if (event.pointerCount <= 2) mode = 1
                    true
                }
                MotionEvent.ACTION_UP -> {
                    mode = 0
                    v.parent.requestDisallowInterceptTouchEvent(false)
                    true
                }
                else -> false
            }
        }
    }

    private fun getSpacing(e: MotionEvent): Float {
        if (e.pointerCount < 2) return 0f
        val dx = e.getX(0) - e.getX(1); val dy = e.getY(0) - e.getY(1)
        return sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    private fun getAngle(e: MotionEvent): Float {
        if (e.pointerCount < 2) return 0f
        return Math.toDegrees(atan2((e.getY(1) - e.getY(0)).toDouble(), (e.getX(1) - e.getX(0)).toDouble())).toFloat()
    }

    // ── Text Dialog ──

    private fun showTextPanel() {
        val dialog = BottomSheetDialog(this, R.style.RoundedBottomSheetDialog)
        val view = layoutInflater.inflate(R.layout.dialog_add_text, null)
        dialog.setContentView(view)

        val inputText = view.findViewById<TextInputEditText>(R.id.etStickerText)
        val btnAdd = view.findViewById<MaterialButton>(R.id.btnAddText)
        val tvPreview = view.findViewById<TextView>(R.id.tvTextPreviewInDialog)
        val fontGrid = view.findViewById<GridLayout>(R.id.fontSelectionLayout)

        var selectedColor = Color.WHITE
        val fixedTextSize = 28f // Fixed size, no slider
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
            val fontCard = com.google.android.material.card.MaterialCardView(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
                }
                radius = dpToPx(12).toFloat()
                cardElevation = dpToPx(2).toFloat()
                setCardBackgroundColor(ContextCompat.getColor(this@AnimatedStickerActivity, R.color.card_bg))
                strokeWidth = if (index == 0) dpToPx(2) else 0
                strokeColor = ContextCompat.getColor(this@AnimatedStickerActivity, R.color.accent)

                val fontTextView = TextView(this@AnimatedStickerActivity).apply {
                    text = name
                    typeface?.let { setTypeface(it) }
                    textSize = 12f
                    setTextColor(ContextCompat.getColor(this@AnimatedStickerActivity, R.color.text_primary))
                    setPadding(dpToPx(10), dpToPx(6), dpToPx(10), dpToPx(6))
                    gravity = Gravity.CENTER
                }
                addView(fontTextView)

                setOnClickListener {
                    selectedFontView?.let { prev ->
                        (prev as com.google.android.material.card.MaterialCardView).strokeWidth = 0
                    }
                    strokeWidth = dpToPx(2)
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
                prevBg?.setStroke(dpToPx(2), Color.DKGRAY)
            }
            val newBg = newSelected.background as? GradientDrawable
            newBg?.setStroke(dpToPx(4), ContextCompat.getColor(this, R.color.accent))
            selectedColorView = newSelected
            selectedColor = color
            tvPreview.setTextColor(color)
        }

        colors.forEachIndexed { index, color ->
            val colorView = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(32), dpToPx(32)).apply {
                    marginEnd = dpToPx(6)
                }
                val bg = GradientDrawable()
                bg.shape = GradientDrawable.OVAL
                bg.setColor(color)
                if (index == 0) {
                    bg.setStroke(dpToPx(3), ContextCompat.getColor(this@AnimatedStickerActivity, R.color.accent))
                } else {
                    bg.setStroke(dpToPx(2), Color.DKGRAY)
                }
                background = bg
                elevation = 4f
                setOnClickListener {
                    updateColorSelection(this, color)
                }
            }
            if (index == 0) {
                selectedColorView = colorView
            }
            colorContainer.addView(colorView)
        }

        // Set preview text size to fixed value
        tvPreview.textSize = fixedTextSize

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
                val colorHex = String.format("#%06X", 0xFFFFFF and selectedColor)
                val item = OverlayItem("text", text, colorHex, fixedTextSize.toInt())
                item.typeface = selectedTypeface
                overlayItems.add(item)
                addOverlayView(item)
                dialog.dismiss()
                Toast.makeText(this, getString(R.string.text_added), Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }
    
    private fun showEditTextOverlay(item: OverlayItem) {
        val dialog = BottomSheetDialog(this, R.style.RoundedBottomSheetDialog)
        val view = layoutInflater.inflate(R.layout.dialog_add_text, null)
        dialog.setContentView(view)

        val inputText = view.findViewById<TextInputEditText>(R.id.etStickerText)
        val btnAdd = view.findViewById<MaterialButton>(R.id.btnAddText)
        val tvPreview = view.findViewById<TextView>(R.id.tvTextPreviewInDialog)
        val fontGrid = view.findViewById<GridLayout>(R.id.fontSelectionLayout)

        inputText.setText(item.content)
        var selectedColor = Color.parseColor(item.color)
        val fixedTextSize = item.sizeSp.toFloat() // Keep existing size, no slider
        var selectedColorView: View? = null
        var selectedFontView: View? = null
        var selectedTypeface: Typeface? = item.typeface

        btnAdd.text = getString(R.string.update_text)

        // Font list
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

        fontGrid.columnCount = 2
        fonts.forEachIndexed { _, (name, typeface) ->
            val fontCard = com.google.android.material.card.MaterialCardView(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
                }
                radius = dpToPx(12).toFloat()
                cardElevation = dpToPx(2).toFloat()
                setCardBackgroundColor(ContextCompat.getColor(this@AnimatedStickerActivity, R.color.card_bg))
                strokeWidth = if (typeface == selectedTypeface) dpToPx(2) else 0
                strokeColor = ContextCompat.getColor(this@AnimatedStickerActivity, R.color.accent)

                val fontTextView = TextView(this@AnimatedStickerActivity).apply {
                    text = name
                    typeface?.let { setTypeface(it) }
                    textSize = 12f
                    setTextColor(ContextCompat.getColor(this@AnimatedStickerActivity, R.color.text_primary))
                    setPadding(dpToPx(10), dpToPx(6), dpToPx(10), dpToPx(6))
                    gravity = Gravity.CENTER
                }
                addView(fontTextView)

                setOnClickListener {
                    selectedFontView?.let { prev ->
                        (prev as com.google.android.material.card.MaterialCardView).strokeWidth = 0
                    }
                    strokeWidth = dpToPx(2)
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
                prevBg?.setStroke(dpToPx(2), Color.DKGRAY)
            }
            val newBg = newSelected.background as? GradientDrawable
            newBg?.setStroke(dpToPx(4), ContextCompat.getColor(this, R.color.accent))
            selectedColorView = newSelected
            selectedColor = color
            tvPreview.setTextColor(color)
        }

        colors.forEachIndexed { _, color ->
            val colorView = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(32), dpToPx(32)).apply {
                    marginEnd = dpToPx(6)
                }
                val bg = GradientDrawable()
                bg.shape = GradientDrawable.OVAL
                bg.setColor(color)
                if (color == selectedColor) {
                    bg.setStroke(dpToPx(3), ContextCompat.getColor(this@AnimatedStickerActivity, R.color.accent))
                    selectedColorView = this
                } else {
                    bg.setStroke(dpToPx(2), Color.DKGRAY)
                }
                background = bg
                elevation = 4f
                setOnClickListener {
                    updateColorSelection(this, color)
                }
            }
            colorContainer.addView(colorView)
        }

        tvPreview.text = item.content
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
                item.content = text
                item.color = String.format("#%06X", 0xFFFFFF and selectedColor)
                item.sizeSp = fixedTextSize.toInt()
                item.typeface = selectedTypeface
                
                (item.view as? TextView)?.let { tv ->
                    tv.text = text
                    tv.setTextColor(selectedColor)
                    tv.textSize = fixedTextSize
                    tv.typeface = selectedTypeface ?: Typeface.DEFAULT_BOLD
                }
                
                dialog.dismiss()
                Toast.makeText(this, getString(R.string.text_updated), Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    // ── Emoji Picker ──

    private fun showEmojiPicker() {
        val dialog = BottomSheetDialog(this, R.style.TransparentBottomSheetDialog)
        val view = layoutInflater.inflate(R.layout.dialog_emoji_picker, null)
        dialog.setContentView(view)

        // Emoji categories - same as StickerMakerActivity
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
                    setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
                    gravity = Gravity.CENTER
                    background = ContextCompat.getDrawable(this@AnimatedStickerActivity, R.drawable.bg_emoji_item)
                    setOnClickListener {
                        val item = OverlayItem("emoji", emoji, sizeSp = 48)
                        overlayItems.add(item)
                        addOverlayView(item)
                        dialog.dismiss()
                        Toast.makeText(this@AnimatedStickerActivity, getString(R.string.emoji_added), Toast.LENGTH_SHORT).show()
                    }
                }
                val params = GridLayout.LayoutParams().apply {
                    width = 0
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
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
                setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
                setTextColor(if (index == 0) ContextCompat.getColor(this@AnimatedStickerActivity, R.color.white)
                             else ContextCompat.getColor(this@AnimatedStickerActivity, R.color.text_secondary))
                background = if (index == 0) ContextCompat.getDrawable(this@AnimatedStickerActivity, R.drawable.bg_category_selected)
                             else ContextCompat.getDrawable(this@AnimatedStickerActivity, R.drawable.bg_category_unselected)
                setOnClickListener {
                    selectedTab?.let { prev ->
                        prev.setTextColor(ContextCompat.getColor(this@AnimatedStickerActivity, R.color.text_secondary))
                        prev.background = ContextCompat.getDrawable(this@AnimatedStickerActivity, R.drawable.bg_category_unselected)
                    }
                    setTextColor(ContextCompat.getColor(this@AnimatedStickerActivity, R.color.white))
                    background = ContextCompat.getDrawable(this@AnimatedStickerActivity, R.drawable.bg_category_selected)
                    selectedTab = this
                    updateEmojiGrid(emojiCategories[category] ?: emptyList())
                }
                if (index == 0) selectedTab = this
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dpToPx(8)
            }
            categoryTabs.addView(tabView, params)
        }

        updateEmojiGrid(currentEmojis)
        dialog.show()
    }

    private fun showEmojiPanel() {
        hideKeyboard()
        toolOptionsPanel.removeAllViews()
        val panel = layoutInflater.inflate(R.layout.panel_emoji_options, toolOptionsPanel, false)
        val grid = panel.findViewById<GridLayout>(R.id.emojiGrid)
        grid.isFocusable = false
        grid.isFocusableInTouchMode = false
        val tvEmojiCount = panel.findViewById<TextView>(R.id.tvEmojiCount)
        val emojiSizeRow = panel.findViewById<LinearLayout>(R.id.emojiSizeRow)
        val sliderEmojiSize = panel.findViewById<Slider>(R.id.sliderEmojiSize)
        val tvEmojiSizeLabel = panel.findViewById<TextView>(R.id.tvEmojiSizeLabel)
        val emojiRotationRow = panel.findViewById<LinearLayout>(R.id.emojiRotationRow)
        val sliderEmojiRotation = panel.findViewById<Slider>(R.id.sliderEmojiRotation)
        val tvEmojiRotationLabel = panel.findViewById<TextView>(R.id.tvEmojiRotationLabel)
        panel.findViewById<androidx.core.widget.NestedScrollView>(R.id.emojiScrollView)?.let {
            it.post {
                val maxH = dpToPx(200)
                if (it.height > maxH) it.layoutParams = it.layoutParams.apply { height = maxH }
            }
        }

        var emojiSize = 48
        var emojiRotation = 0f

        val emojis = listOf(
            "\uD83D\uDE00","\uD83D\uDE02","\uD83D\uDE05","\uD83D\uDE0E","\uD83E\uDD29","\uD83D\uDE31","\uD83D\uDE0D","\uD83E\uDD7A",
            "\uD83D\uDE24","\uD83D\uDC80","\uD83E\uDD23","\uD83D\uDE18","\uD83E\uDD73","\uD83D\uDE08","\uD83E\uDD2F","\uD83E\uDD75",
            "\uD83D\uDE0A","\uD83D\uDE01","\uD83D\uDE06","\uD83D\uDE09","\uD83D\uDE0B","\uD83D\uDE0C","\uD83D\uDE0D","\uD83D\uDE0F",
            "\uD83D\uDE10","\uD83D\uDE11","\uD83D\uDE12","\uD83D\uDE13","\uD83D\uDE14","\uD83D\uDE15","\uD83D\uDE16","\uD83D\uDE17",
            "\uD83D\uDC4D","\uD83D\uDC4E","\uD83D\uDC4B","\uD83D\uDE4C","\uD83D\uDCAA","\uD83E\uDD1E","\uD83D\uDC4C","\uD83E\uDD19",
            "\uD83D\uDC4F","\uD83E\uDD32","\uD83E\uDD1D","\uD83D\uDE4F","\u270C\uFE0F","\uD83E\uDD1F","\uD83E\uDD18","\uD83D\uDC48",
            "\u2764\uFE0F","\uD83D\uDC96","\uD83D\uDC9C","\uD83D\uDC99","\uD83D\uDC9A","\uD83D\uDC9B","\uD83E\uDDE1","\uD83D\uDDA4",
            "\uD83D\uDC94","\uD83D\uDC95","\uD83D\uDC9E","\uD83D\uDC93","\uD83D\uDC97","\uD83D\uDC98","\uD83D\uDC9D","\uD83D\uDC9F",
            "\uD83D\uDD25","\u2728","\uD83C\uDF1F","\uD83C\uDF08","\uD83C\uDF89","\uD83D\uDCAF","\u2B50","\uD83C\uDF1E",
            "\uD83C\uDF1A","\uD83C\uDF1B","\uD83C\uDF1C","\uD83C\uDF1D","\u2600\uFE0F","\uD83C\uDF24\uFE0F","\u26C5","\uD83C\uDF25\uFE0F",
            "\uD83D\uDC36","\uD83D\uDC31","\uD83E\uDD8B","\uD83D\uDC3B","\uD83E\uDD81","\uD83D\uDC2F","\uD83E\uDD84","\uD83D\uDC3C",
            "\uD83D\uDC35","\uD83D\uDC12","\uD83E\uDD8D","\uD83E\uDD8C","\uD83D\uDC28","\uD83D\uDC2D","\uD83D\uDC30","\uD83E\uDD8A",
            "\uD83C\uDF55","\uD83C\uDF54","\uD83C\uDF69","\uD83C\uDF70","\uD83C\uDF66","\uD83C\uDF53","\uD83C\uDF49","\uD83E\uDD51",
            "\uD83C\uDF4E","\uD83C\uDF4A","\uD83C\uDF4B","\uD83C\uDF4C","\uD83C\uDF4D","\uD83C\uDF4F","\uD83C\uDF47","\uD83C\uDF48",
            "\u26BD","\uD83C\uDFC0","\uD83C\uDFAE","\uD83C\uDFB5","\uD83C\uDFA4","\uD83C\uDFA8","\uD83D\uDE80","\uD83C\uDF0D",
            "\u2708\uFE0F","\uD83D\uDE97","\uD83D\uDEB2","\uD83D\uDEA2","\u26F5","\uD83D\uDE81","\uD83D\uDE82","\uD83D\uDE8C"
        )

        fun refreshEmojiCount() {
            val count = overlayItems.count { it.type == "emoji" }
            if (count > 0) {
                tvEmojiCount.visibility = View.VISIBLE
                tvEmojiCount.text = String.format(getString(R.string.overlays_count), count)
                emojiSizeRow.visibility = View.VISIBLE
                emojiRotationRow.visibility = View.VISIBLE
            } else {
                tvEmojiCount.visibility = View.GONE
                emojiSizeRow.visibility = View.GONE
                emojiRotationRow.visibility = View.GONE
            }
        }
        refreshEmojiCount()

        // Emoji size slider - updates last added emoji in real-time
        sliderEmojiSize?.addOnChangeListener { _, value, _ ->
            emojiSize = value.toInt()
            tvEmojiSizeLabel?.text = "${emojiSize}px"
            val lastEmoji = overlayItems.lastOrNull { it.type == "emoji" }
            if (lastEmoji != null) {
                lastEmoji.sizeSp = emojiSize
                (lastEmoji.view as? TextView)?.textSize = emojiSize.toFloat()
            }
        }

        // Emoji rotation slider - updates last added emoji in real-time
        sliderEmojiRotation?.addOnChangeListener { _, value, _ ->
            emojiRotation = value
            tvEmojiRotationLabel?.text = "${value.toInt()}°"
            val lastEmoji = overlayItems.lastOrNull { it.type == "emoji" }
            if (lastEmoji != null) {
                lastEmoji.rotation = emojiRotation
                lastEmoji.view?.rotation = emojiRotation
            }
        }

        for (e in emojis) {
            val tv = TextView(this).apply {
                text = e
                textSize = 32f
                gravity = Gravity.CENTER
                layoutParams = GridLayout.LayoutParams().apply {
                    width = dpToPx(46); height = dpToPx(46)
                    setMargins(dpToPx(1), dpToPx(1), dpToPx(1), dpToPx(1))
                }
                isClickable = true
                isFocusable = false
                isFocusableInTouchMode = false
                alpha = 1.0f
            }
            tv.setOnClickListener {
                val item = OverlayItem("emoji", e, sizeSp = emojiSize)
                overlayItems.add(item)
                addOverlayView(item)
                refreshEmojiCount()
                // Set slider to this emoji's size
                sliderEmojiSize?.value = emojiSize.toFloat()
                tv.animate().scaleX(1.3f).scaleY(1.3f).setDuration(80).withEndAction {
                    tv.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                }.start()
                Toast.makeText(this, getString(R.string.emoji_added), Toast.LENGTH_SHORT).show()
            }
            grid.addView(tv)
        }
        toolOptionsPanel.addView(panel)
        toolOptionsPanel.visibility = View.VISIBLE
    }

    private fun updateOverlayCount(tv: TextView, count: Int) {
        if (count > 0) {
            tv.visibility = View.VISIBLE
            tv.text = String.format(getString(R.string.overlays_count), count)
        } else tv.visibility = View.GONE
    }

    // ══════════════════════════════════════════════
    //  SAVE
    // ══════════════════════════════════════════════

    private fun saveSticker() {
        hideKeyboard()
        if (overlayItems.isNotEmpty()) {
            saveProcessingOverlay.visibility = View.VISIBLE
            lottieSaveProcessing.playAnimation()
            
            lifecycleScope.launch(Dispatchers.IO) {
                val success = burnOverlaysOntoWebP()
                withContext(Dispatchers.Main) {
                    lottieSaveProcessing.cancelAnimation()
                    saveProcessingOverlay.visibility = View.GONE
                    if (success) doShowSaveDialog()
                    else {
                        Toast.makeText(this@AnimatedStickerActivity, getString(R.string.conversion_failed), Toast.LENGTH_LONG).show()
                    }
                }
            }
        } else {
            doShowSaveDialog()
        }
    }

    private suspend fun burnOverlaysOntoWebP(): Boolean {
        // Re-encode from ORIGINAL VIDEO with overlay PNG composited
        // This avoids WebP-to-WebP conversion issues
        val inputVideo = File(trimmedVideoPath ?: return false)
        if (!inputVideo.exists()) return false
        val overlayPng = renderOverlayBitmap() ?: return false

        val outFile = File(cacheDir, "anim_final_${System.currentTimeMillis()}.webp")
        val cropFilter = buildCropFilter()
        val circleAlpha = if (cropShape == CropShape.CIRCLE) {
            ",geq='lum(X,Y)':cb='cb(X,Y)':cr='cr(X,Y)':a='if(gt(pow(X-W/2,2)+pow(Y-H/2,2),pow(min(W,H)/2,2)),0,255)'"
        } else ""

        // Build filter: crop → scale → pad → fps → circle → overlay PNG on top
        var q = 50 // Start lower for overlays as they increase file size
        var ok = false
        while (q >= 20 && !ok) {
            outFile.delete()
            val vf = "${cropFilter}scale=$STICKER_SIZE:$STICKER_SIZE:force_original_aspect_ratio=decrease,pad=$STICKER_SIZE:$STICKER_SIZE:-1:-1:color=0x00000000@0x00,fps=$TARGET_FPS${circleAlpha}"
            // Using [1:v] as overlay, and ensuring the overlay is also scaled correctly just in case
            val cmd = "-y -i \"${inputVideo.absolutePath}\" -i \"${overlayPng.absolutePath}\" " +
                    "-filter_complex \"[0:v]${vf}[base];[1:v]scale=$STICKER_SIZE:$STICKER_SIZE[ovrl];[base][ovrl]overlay=0:0\" " +
                    "-vcodec libwebp -lossless 0 -compression_level 2 -quality $q -loop 0 " +
                    "-preset default -an -pix_fmt yuva420p \"${outFile.absolutePath}\""
            Log.d(TAG, "FFmpeg overlay cmd: $cmd")
            val session = FFmpegKit.execute(cmd)
            if (ReturnCode.isSuccess(session.returnCode) && outFile.exists()) {
                val kb = outFile.length() / 1024
                Log.d(TAG, "Overlay output: ${kb}KB at q=$q")
                if (kb <= MAX_FILE_SIZE_KB) ok = true else q -= 20
            } else {
                Log.e(TAG, "FFmpeg overlay fail rc=${session.returnCode}: ${session.allLogsAsString}")
                break
            }
        }

        overlayPng.delete()

        if (ok && outFile.exists()) {
            outputFile?.delete()
            outputFile = outFile
            return true
        } else {
            outFile.delete()
            return false
        }
    }

    private var saveDialog: AlertDialog? = null

    private fun doShowSaveDialog() {
        val file = outputFile ?: return
        if (!file.exists()) {
            Log.e(TAG, "Output file does not exist: ${file.absolutePath}")
            Toast.makeText(this, getString(R.string.conversion_failed), Toast.LENGTH_LONG).show()
            return
        }
        Log.d(TAG, "Showing save dialog, file=${file.absolutePath}, size=${file.length()/1024}KB")

        // If launched with a target pack, skip dialog and add directly
        if (targetPackId != null) {
            addToPack(file, targetPackId!!)
            return
        }

        val view = layoutInflater.inflate(R.layout.dialog_select_pack, null)
        val rv = view.findViewById<RecyclerView>(R.id.rvPacks)
        val input = view.findViewById<TextInputEditText>(R.id.inputPackName)
        val btn = view.findViewById<MaterialButton>(R.id.btnCreatePack)
        val packs = CustomStickerManager.getCustomPacks(this).filter { it.isAnimated }
        if (packs.isNotEmpty()) {
            rv.layoutManager = LinearLayoutManager(this)
            rv.adapter = PackAdapter(packs) { pack ->
                saveDialog?.dismiss()
                addToPack(file, pack.id)
            }
        } else rv.visibility = View.GONE
        saveDialog = AlertDialog.Builder(this, R.style.MaterialAlertDialogTheme).setView(view).create()
        saveDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        btn.setOnClickListener {
            val name = input.text?.toString()?.trim() ?: ""
            if (name.isEmpty()) { input.error = getString(R.string.enter_pack_name); return@setOnClickListener }
            saveDialog?.dismiss()
            addToPack(file, CustomStickerManager.createPack(this, name, true))
        }
        saveDialog?.show()
    }

    private fun addToPack(file: File, packId: String) {
        Log.d(TAG, "addToPack: file=${file.absolutePath}, size=${file.length()/1024}KB, packId=$packId")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val ok = CustomStickerManager.addAnimatedStickerFromFile(this@AnimatedStickerActivity, packId, file)
                Log.d(TAG, "addAnimatedStickerFromFile result: $ok")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AnimatedStickerActivity,
                        if (ok) getString(R.string.animated_sticker_saved) else getString(R.string.conversion_failed),
                        Toast.LENGTH_SHORT).show()
                    if (ok) { setResult(RESULT_OK); finish() }
                }
            } catch (e: Exception) {
                Log.e(TAG, "addToPack error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AnimatedStickerActivity, getString(R.string.conversion_failed), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ══════════════════════════════════════════════
    //  UTILITY
    // ══════════════════════════════════════════════

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun releasePlayer() {
        exoPlayer?.stop(); exoPlayer?.release(); exoPlayer = null
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(window.decorView.windowToken, 0)
        currentFocus?.clearFocus()
    }

    override fun onPause() { super.onPause(); exoPlayer?.pause() }
    override fun onResume() { super.onResume(); exoPlayer?.play() }
    override fun onDestroy() { super.onDestroy(); releasePlayer(); outputFile?.delete() }

    // ══════════════════════════════════════════════
    //  PACK ADAPTER
    // ══════════════════════════════════════════════

    inner class PackAdapter(
        private val packs: List<CustomStickerManager.CustomPack>,
        private val onClick: (CustomStickerManager.CustomPack) -> Unit
    ) : RecyclerView.Adapter<PackAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.tvPackName)
            val count: TextView = v.findViewById(R.id.tvPackCount)
            val cover: ImageView = v.findViewById(R.id.ivPackCover)
        }
        override fun onCreateViewHolder(p: ViewGroup, t: Int) =
            VH(android.view.LayoutInflater.from(p.context).inflate(R.layout.item_pack_selection, p, false))
        override fun onBindViewHolder(h: VH, pos: Int) {
            val pack = packs[pos]
            h.name.text = pack.name
            h.count.text = "${pack.stickerCount}/30"

            if (pack.stickerCount > 0) {
                val stickerFile = CustomStickerManager.getCustomStickerPath(
                    h.itemView.context, pack.id, "sticker_1.webp"
                )
                com.bumptech.glide.Glide.with(h.itemView.context)
                    .load(stickerFile)
                    .placeholder(R.drawable.ic_sticker_placeholder)
                    .error(R.drawable.ic_sticker_placeholder)
                    .into(h.cover)
            } else {
                h.cover.setImageResource(R.drawable.ic_sticker_placeholder)
            }

            h.itemView.setOnClickListener { onClick(pack) }
        }
        override fun getItemCount() = packs.size
    }
}
