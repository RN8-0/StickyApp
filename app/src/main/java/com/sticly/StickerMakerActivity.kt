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
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
    private lateinit var btnRemoveBackground: MaterialButton
    private lateinit var btnSelectMedia: MaterialButton
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

    private lateinit var segmenter: Segmenter

    private var selectedBitmap: Bitmap? = null
    private var originalBitmap: Bitmap? = null
    private var photoUri: Uri? = null
    private var photoFile: File? = null
    private var backgroundRemoved = false
    
    private var isAnimatedMode = false
    private var targetPackId: String? = null
    
    private val selectedUris = mutableListOf<Uri>()
    private var currentUriIndex = -1
    private var currentMimeType: String? = null

    // Medya seçici
    private val pickMediaLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedUris.clear()
            selectedUris.addAll(uris)
            currentUriIndex = 0
            loadMediaFromUri(selectedUris[0])
        }
    }

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
        btnRemoveBackground = findViewById(R.id.btnRemoveBackground)
        btnSelectMedia = findViewById(R.id.btnSelectPhoto)
        btnAddToPack = findViewById(R.id.btnAddToPack)
        tvMediaInfo = findViewById(R.id.tvMediaInfo)
        tvQueueProgress = findViewById(R.id.tvQueueProgress)
        
        processingOverlay = findViewById(R.id.processingOverlay)
        processingCircle = findViewById(R.id.processingCircle)
        tvProcessingTitle = findViewById(R.id.tvProcessingTitle)
        tvProcessingSubtitle = findViewById(R.id.tvProcessingSubtitle)
        tvProcessingPercent = findViewById(R.id.tvProcessingPercent)
        processingIcon = findViewById(R.id.processingIcon)

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

        cardAnimated.setOnClickListener {
            isAnimatedMode = true
            enterEditorMode()
        }

        btnSelectMedia.setOnClickListener {
            val type = if (isAnimatedMode) "video/*, image/gif" else "image/*"
            pickMediaLauncher.launch(if (isAnimatedMode) "video/*" else "image/*") // Note: Multiple filters can be tricky, using primary type
        }

        btnRemoveBackground.setOnClickListener {
            if (backgroundRemoved) {
                originalBitmap?.let { original ->
                    selectedBitmap = original.copy(original.config ?: Bitmap.Config.ARGB_8888, true)
                    imagePreview.setImageBitmap(selectedBitmap)
                    backgroundRemoved = false
                    btnRemoveBackground.text = getString(R.string.remove_background)
                    btnRemoveBackground.setIconResource(R.drawable.ic_photo)
                }
            } else {
                selectedBitmap?.let { bitmap ->
                    processRemoveBackground(bitmap)
                }
            }
        }

        btnAddToPack.setOnClickListener {
            if (targetPackId != null) {
                processAndAddSticker(targetPackId!!)
            } else {
                showPackSelectionDialog()
            }
        }
    }

    private fun enterEditorMode() {
        typeSelectionContainer.visibility = View.GONE
        editorContainer.visibility = View.VISIBLE
        toolbarTitle.text = if (isAnimatedMode) getString(R.string.animated_sticker) else getString(R.string.static_sticker)
        
        // UI Adaptations
        placeholderIcon.setImageResource(if (isAnimatedMode) R.drawable.ic_video else R.drawable.ic_photo)
        placeholderText.text = if (isAnimatedMode) getString(R.string.select_video_gif) else getString(R.string.select_photo_maker)
        btnSelectMedia.text = if (isAnimatedMode) getString(R.string.select_video_gif_btn) else getString(R.string.select_photo_btn)
        btnSelectMedia.setIconResource(if (isAnimatedMode) R.drawable.ic_video else R.drawable.ic_photo)
        
        // Background removal only for static
        btnRemoveBackground.visibility = if (isAnimatedMode) View.GONE else View.VISIBLE
    }

    private fun exitEditorMode() {
        typeSelectionContainer.visibility = View.VISIBLE
        editorContainer.visibility = View.GONE
        toolbarTitle.text = "Sticly"
        resetEditorUI()
    }

    private fun resetEditorUI() {
        selectedUris.clear()
        currentUriIndex = -1
        selectedBitmap = null
        originalBitmap = null
        backgroundRemoved = false
        imagePreview.visibility = View.GONE
        placeholderContainer.visibility = View.VISIBLE
        btnAddToPack.visibility = View.GONE
        tvMediaInfo.visibility = View.GONE
        tvQueueProgress.visibility = View.GONE
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
                        backgroundRemoved = false
                        btnRemoveBackground.text = getString(R.string.remove_background)
                        btnRemoveBackground.setIconResource(R.drawable.ic_photo)
                        imagePreview.setImageBitmap(selectedBitmap)
                        imagePreview.visibility = View.VISIBLE
                        placeholderContainer.visibility = View.GONE
                        btnAddToPack.visibility = View.VISIBLE
                        btnAddToPack.text = getString(R.string.add_to_pack)
                    }
                }
            }

            // Kuyruk
            if (selectedUris.size > 1) {
                tvQueueProgress.visibility = View.VISIBLE
                tvQueueProgress.text = "${currentUriIndex + 1} / ${selectedUris.size}"
            } else {
                tvQueueProgress.visibility = View.GONE
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
                val maskBuffer = mask.buffer
                val width = bitmap.width
                val height = bitmap.height
                val resultBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                val pixels = IntArray(width * height)
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val pos = y * width + x
                        val confidence = maskBuffer.float
                        if (confidence < 0.35f) { // Dramatically improved sensitivity for better cuts (0.6 -> 0.35)
                            pixels[pos] = Color.TRANSPARENT
                        }
                    }
                }

                resultBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
                selectedBitmap = resultBitmap
                imagePreview.setImageBitmap(selectedBitmap)
                backgroundRemoved = true
                btnRemoveBackground.text = getString(R.string.undo)
                btnRemoveBackground.setIconResource(R.drawable.ic_restore)
                hideProcessingOverlay()
            }
            .addOnFailureListener {
                hideProcessingOverlay()
                Toast.makeText(this, getString(R.string.error_occurred), Toast.LENGTH_SHORT).show()
            }
    }

    private fun showPackSelectionDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_select_pack, null)
        val rvPacks = view.findViewById<RecyclerView>(R.id.rvPacks)
        val inputPackName = view.findViewById<TextInputEditText>(R.id.inputPackName)
        val btnCreatePack = view.findViewById<Button>(R.id.btnCreatePack)

        val allPacks = CustomStickerManager.getCustomPacks(this)
        val filteredPacks = allPacks.filter { it.isAnimated == isAnimatedMode }

        val dialog = AlertDialog.Builder(this).setView(view).create()

        if (filteredPacks.isNotEmpty()) {
            rvPacks.visibility = View.VISIBLE
            rvPacks.layoutManager = LinearLayoutManager(this)
            rvPacks.adapter = PackSelectionAdapter(filteredPacks) { pack ->
                dialog.dismiss()
                processAndAddSticker(pack.id)
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
            processAndAddSticker(packId)
        }
        dialog.show()
    }

    private fun processAndAddSticker(packId: String) {
        targetPackId = packId
        val uri = selectedUris[currentUriIndex]
        
        if (isAnimatedMode) {
            // Video-to-WebP Dönüşümü
            val outputFile = File(cacheDir, "temp_sticker_${System.currentTimeMillis()}.webp")
            
            // FFmpeg Komutu: 512x512, max 3s, an (sesiz), webp
            val cmd = "-y -i \"${getFilePathFromUri(uri)}\" -t 3 -vf \"scale=512:512:force_original_aspect_ratio=decrease,pad=512:512:(ow-iw)/2:(oh-ih)/2:color=#00000000\" -vcodec libwebp -lossless 0 -compression_level 6 -q:v 60 -loop 0 -an \"${outputFile.absolutePath}\""
            
            showProcessingOverlay(getString(R.string.converting), getString(R.string.preparing_webp), R.drawable.ic_video)
            tvProcessingPercent.visibility = View.VISIBLE
            tvProcessingPercent.text = "0%"

            /*
            FFmpegKit.executeAsync(cmd, { session ->
                runOnUiThread {
                    hideProcessingOverlay()
                    if (ReturnCode.isSuccess(session.returnCode)) {
                        val success = CustomStickerManager.addAnimatedStickerFromFile(this, packId, outputFile)
                        if (success) checkQueueAndProceed()
                        else Toast.makeText(this, "Paket dolu!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, getString(R.string.download_failed), Toast.LENGTH_SHORT).show()
                    }
                    outputFile.delete()
                }
            }, { log ->
                // Loglardan ilerleme verisi çıkarılabilir (opsiyonel)
            }, { statistics ->
                val timeInMs = statistics.time
                val progress = Math.min((timeInMs / 3000.0 * 100).toInt(), 99)
                runOnUiThread {
                    processingCircle.progress = progress
                    tvProcessingPercent.text = "$progress%"
                }
            })
            */
            Toast.makeText(this, R.string.animated_sticker_coming_soon, Toast.LENGTH_LONG).show()
            hideProcessingOverlay()
        } else {
            // Statik Ekleme
            val bitmap = selectedBitmap ?: return
            val success = CustomStickerManager.addStickerToPack(this, packId, bitmap)
            if (success) checkQueueAndProceed()
            else Toast.makeText(this, "Paket dolu!", Toast.LENGTH_SHORT).show()
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

    private fun checkQueueAndProceed() {
        if (currentUriIndex < selectedUris.size - 1) {
            currentUriIndex++
            loadMediaFromUri(selectedUris[currentUriIndex])
            backgroundRemoved = false
        } else {
            Toast.makeText(this, R.string.pack_updated, Toast.LENGTH_SHORT).show()
            val intent = Intent(this, DetailsActivity::class.java).putExtra("id", targetPackId)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            finish()
        }
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
            view.background = ContextCompat.getDrawable(this@StickerMakerActivity, android.R.attr.selectableItemBackground)
            return ViewHolder(view)
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val pack = packs[position]
            holder.tvName.text = pack.name
            holder.tvName.setTextColor(ContextCompat.getColor(this@StickerMakerActivity, R.color.text_primary))
            holder.tvCount.text = "${pack.stickerCount} sticker"
            holder.tvCount.setTextColor(ContextCompat.getColor(this@StickerMakerActivity, R.color.text_secondary))
        }
        override fun getItemCount() = packs.size
    }
}
