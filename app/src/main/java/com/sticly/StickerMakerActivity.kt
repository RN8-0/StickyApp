package com.sticly

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
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
import com.google.android.material.textfield.TextInputEditText
import java.io.File
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.Segmenter
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions

class StickerMakerActivity : AppCompatActivity() {

    private lateinit var imagePreview: ImageView
    private lateinit var placeholderContainer: View
    private lateinit var btnRemoveBackground: Button
    private lateinit var btnAddToPack: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvQueueProgress: TextView

    private lateinit var segmenter: Segmenter

    private var selectedBitmap: Bitmap? = null
    private var originalBitmap: Bitmap? = null  // Geri almak için orijinal
    private var photoUri: Uri? = null
    private var photoFile: File? = null
    private var backgroundRemoved = false
    
    // Hedef paket ID - eğer varsa doğrudan o pakete ekle
    private var targetPackId: String? = null
    
    // Çoklu seçim için kuyruk
    private val selectedUris = mutableListOf<Uri>()
    private var currentUriIndex = -1
    private var currentMimeType: String? = null

    // Galeri seçici (Çoklu seçim destekli)
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedUris.clear()
            selectedUris.addAll(uris)
            currentUriIndex = 0
            loadImageFromUri(selectedUris[0])
        }
    }

    // Kamera çekimi
    private val takePhotoLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoUri != null) {
            // Kamera fotoğrafını kuyruğa ekle (tek elemanlı)
            selectedUris.clear()
            selectedUris.add(photoUri!!)
            currentUriIndex = 0
            loadImageFromUri(photoUri!!)
        }
    }

    // Kamera izni
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera()
        } else {
            Toast.makeText(this, "Kamera izni gerekli", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sticker_maker)

        // ML Kit Segmenter'ı hazırla
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()
        segmenter = Segmentation.getClient(options)

        // Intent'ten hedef packId'yi al (varsa)
        targetPackId = intent.getStringExtra("packId")

        imagePreview = findViewById(R.id.imagePreview)
        placeholderContainer = findViewById(R.id.placeholderContainer)
        btnRemoveBackground = findViewById(R.id.btnRemoveBackground)
        btnAddToPack = findViewById(R.id.btnAddToPack)
        progressBar = findViewById(R.id.progressBar)
        tvQueueProgress = findViewById(R.id.tvQueueProgress)

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnHome = findViewById<ImageButton>(R.id.btnHome)
        val btnSelectPhoto = findViewById<Button>(R.id.btnSelectPhoto)
        val btnTakePhoto = findViewById<Button>(R.id.btnTakePhoto)

        // Hedef paket varsa buton metnini değiştir
        if (targetPackId != null) {
            btnAddToPack.text = "Pakete Ekle"
        }

        btnBack.setOnClickListener { finish() }
        
        // Home butonu - ana sayfaya git
        btnHome.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        btnSelectPhoto.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        btnTakePhoto.setOnClickListener {
            checkCameraPermissionAndLaunch()
        }

        btnRemoveBackground.setOnClickListener {
            if (backgroundRemoved) {
                // Geri al - orijinale dön
                originalBitmap?.let { original ->
                    selectedBitmap = original.copy(original.config, true)
                    imagePreview.setImageBitmap(selectedBitmap)
                    backgroundRemoved = false
                    btnRemoveBackground.text = getString(R.string.remove_background)
                }
            } else {
                // Arka plan kaldır
                selectedBitmap?.let { bitmap ->
                    processRemoveBackground(bitmap)
                }
            }
        }

        btnAddToPack.setOnClickListener {
            // Hedef paket varsa doğrudan ona ekle
            if (targetPackId != null) {
                addStickerToPack(targetPackId!!)
            } else {
                showPackSelectionDialog()
            }
        }
    }

    private fun checkCameraPermissionAndLaunch() {
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                launchCamera()
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun launchCamera() {
        val photoDir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "temp")
        if (!photoDir.exists()) photoDir.mkdirs()
        
        photoFile = File(photoDir, "sticker_${System.currentTimeMillis()}.jpg")
        photoUri = FileProvider.getUriForFile(
            this,
            "${packageName}.provider",
            photoFile!!
        )
        takePhotoLauncher.launch(photoUri)
    }

    private fun loadImageFromUri(uri: Uri) {
        try {
            // Mime type'ı al ve sakla
            currentMimeType = contentResolver.getType(uri)
            Log.d("StickerMaker", "Loading image with mimeType: $currentMimeType")

            contentResolver.openInputStream(uri)?.use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream)
                if (bitmap != null) {
                    // Kare olarak kırp (merkez)
                    selectedBitmap = cropToSquare(bitmap)
                    originalBitmap = selectedBitmap?.copy(selectedBitmap!!.config ?: Bitmap.Config.ARGB_8888, true)
                    backgroundRemoved = false
                    btnRemoveBackground.text = getString(R.string.remove_background)
                    imagePreview.setImageBitmap(selectedBitmap)
                    imagePreview.visibility = View.VISIBLE
                    placeholderContainer.visibility = View.GONE
                    btnRemoveBackground.visibility = View.VISIBLE
                    btnAddToPack.visibility = View.VISIBLE

                    // Kuyruk ilerlemesini göster
                    if (selectedUris.size > 1) {
                        tvQueueProgress.visibility = View.VISIBLE
                        tvQueueProgress.text = "${currentUriIndex + 1} / ${selectedUris.size}"
                    } else {
                        tvQueueProgress.visibility = View.GONE
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("StickerMaker", "Error loading image: ${e.message}")
            Toast.makeText(this, "Resim yüklenemedi", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cropToSquare(bitmap: Bitmap): Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
        val x = (bitmap.width - size) / 2
        val y = (bitmap.height - size) / 2
        return Bitmap.createBitmap(bitmap, x, y, size, size)
    }

    /**
     * AI (ML Kit) tabanlı arka plan kaldırma işlemi
     */
    private fun processRemoveBackground(bitmap: Bitmap) {
        progressBar.visibility = View.VISIBLE
        btnRemoveBackground.isEnabled = false

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
                        // Güven aralığı: 0.8 ve üzeri özne kabul edilir
                        if (confidence < 0.8f) {
                            pixels[pos] = Color.TRANSPARENT
                        }
                    }
                }

                resultBitmap.setPixels(pixels, 0, width, 0, 0, width, height)

                // Seçili bitmap'i güncelle
                selectedBitmap = resultBitmap
                imagePreview.setImageBitmap(selectedBitmap)
                backgroundRemoved = true
                btnRemoveBackground.text = "↩ Geri Al"

                progressBar.visibility = View.GONE
                btnRemoveBackground.isEnabled = true
            }
            .addOnFailureListener { e ->
                Log.e("StickerMaker", "Segmentation failed: ${e.message}")
                progressBar.visibility = View.GONE
                btnRemoveBackground.isEnabled = true
                Toast.makeText(this, R.string.bg_removal_error, Toast.LENGTH_SHORT).show()
            }
    }

    private fun showPackSelectionDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_select_pack, null)
        val rvPacks = view.findViewById<RecyclerView>(R.id.rvPacks)
        val inputPackName = view.findViewById<TextInputEditText>(R.id.inputPackName)
        val btnCreatePack = view.findViewById<Button>(R.id.btnCreatePack)

        val packs = CustomStickerManager.getCustomPacks(this)

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()

        // Mevcut paketler listesi
        if (packs.isNotEmpty()) {
            rvPacks.visibility = View.VISIBLE
            rvPacks.layoutManager = LinearLayoutManager(this)
            rvPacks.adapter = PackSelectionAdapter(packs) { pack ->
                dialog.dismiss()
                addStickerToPack(pack.id)
            }
        } else {
            // Paket yoksa RecyclerView'ı gizle
            rvPacks.visibility = View.GONE
            // Ayırıcı çizgiyi de gizle
            view.findViewById<View>(android.R.id.empty)?.visibility = View.GONE
        }

        // Yeni paket oluştur
        btnCreatePack.setOnClickListener {
            val packName = inputPackName.text?.toString()?.trim() ?: ""
            if (packName.isEmpty()) {
                inputPackName.error = "Paket adı girin"
                return@setOnClickListener
            }

            // Yeni paket oluştur - ilk sticker kapak fotoğrafı olacak
            val packId = CustomStickerManager.createPack(this, packName)

            // İlk sticker'ı ekle ve kapak olarak ayarla
            val bitmap = selectedBitmap ?: return@setOnClickListener
            val success = CustomStickerManager.addStickerToPack(this, packId, bitmap)

            if (success) {
                // İlk sticker'ı kapak olarak ayarla
                CustomStickerManager.setPackCover(this, packId, bitmap)

                // Hedef paketi bu yap ve kuyruğu devam ettir
                targetPackId = packId
                dialog.dismiss()
                checkQueueAndProceed()
            } else {
                Toast.makeText(this, "Sticker eklenemedi (maksimum 30)", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun addStickerToPack(packId: String) {
        val success: Boolean

        // WebP dosyası ve arka plan kaldırılmadıysa, URI'den doğrudan ekle
        // Bu sayede animated WebP'ler animasyonlu kalır
        val currentUri = if (currentUriIndex >= 0 && currentUriIndex < selectedUris.size) {
            selectedUris[currentUriIndex]
        } else null

        if (currentMimeType == "image/webp" && !backgroundRemoved && currentUri != null) {
            // Animated WebP olabilir - doğrudan kopyala
            Log.d("StickerMaker", "Adding WebP directly (may be animated)")
            success = CustomStickerManager.addStickerFromUri(this, packId, currentUri, currentMimeType)
        } else {
            // Bitmap olarak ekle (arka plan kaldırıldıysa veya başka format)
            val bitmap = selectedBitmap ?: return
            success = CustomStickerManager.addStickerToPack(this, packId, bitmap)
        }

        if (success) {
            // Kuyruğu kontrol et
            targetPackId = packId
            checkQueueAndProceed()
        } else {
            Toast.makeText(this, "Sticker eklenemedi (maksimum 30)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkQueueAndProceed() {
        if (currentUriIndex < selectedUris.size - 1) {
            currentUriIndex++
            loadImageFromUri(selectedUris[currentUriIndex])

            // UI tamamen sıfırla (Arka plan silme vb. durumları temizle)
            backgroundRemoved = false
            btnRemoveBackground.text = getString(R.string.remove_background)
            btnRemoveBackground.isEnabled = true
            progressBar.visibility = View.GONE
        } else {
            // Kuyruk bitti - tek mesaj göster
            Toast.makeText(this, "Çıkartma paketiniz hazırlandı", Toast.LENGTH_SHORT).show()

            // Önizleme ekranına git
            if (targetPackId != null) {
                val intent = Intent(this, DetailsActivity::class.java)
                intent.putExtra("id", targetPackId)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                startActivity(intent)
            }
            finish()
        }
    }

    /**
     * Bir sonraki sticker için UI'ı sıfırlar
     */
    private fun resetUIForNextSticker() {
        selectedBitmap?.recycle()
        selectedBitmap = null
        originalBitmap?.recycle()
        originalBitmap = null
        backgroundRemoved = false

        // Kuyruk bilgilerini temizle
        selectedUris.clear()
        currentUriIndex = -1

        // UI'ı sıfırla
        imagePreview.setImageDrawable(null)
        imagePreview.visibility = View.GONE
        placeholderContainer.visibility = View.VISIBLE
        btnRemoveBackground.visibility = View.GONE
        btnRemoveBackground.text = getString(R.string.remove_background)
        btnRemoveBackground.isEnabled = true
        btnAddToPack.visibility = View.GONE
        tvQueueProgress.visibility = View.GONE
        progressBar.visibility = View.GONE

        // Hedef paket ID'yi koru (aynı pakete eklemek için)
        // targetPackId değişmez

        // Buton metnini güncelle
        if (targetPackId != null) {
            btnAddToPack.text = "Pakete Ekle"
        }
    }

    /*
    private fun showPackPreviewDialog(packId: String) {
        // ... Eski dialog kodu kaldırıldı ...
    }
    */

    /*
    private fun showPackPreviewDialog(packId: String) {
        val pack = CustomStickerManager.getPackInfo(this, packId) ?: return
        val stickerFiles = CustomStickerManager.getStickerFiles(this, packId)
        
        // ...
    }
    */

    /**
     * Sticker önizleme adapter'ı
     */
    /*
    inner class StickerPreviewAdapter(
        private val files: List<File>
    ) : RecyclerView.Adapter<StickerPreviewAdapter.ViewHolder>() {
        // ...
    }
    */

    private fun resetUI() {
        selectedBitmap = null
        imagePreview.setImageDrawable(null)
        imagePreview.visibility = View.GONE
        placeholderContainer.visibility = View.VISIBLE
        btnRemoveBackground.visibility = View.GONE
        btnAddToPack.visibility = View.GONE
    }

    private fun showFullScreenPreview(bitmap: Bitmap) {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(ContextCompat.getColor(this@StickerMakerActivity, R.color.overlay_dark)))
            setLayout(android.view.WindowManager.LayoutParams.MATCH_PARENT, android.view.WindowManager.LayoutParams.MATCH_PARENT)
        }

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_sticker_preview, null)
        val imageView = view.findViewById<ImageView>(R.id.previewImage)
        val btnClose = view.findViewById<View>(R.id.btnClose)

        dialog.setContentView(view)
        imageView.setImageBitmap(bitmap)

        btnClose?.setOnClickListener { dialog.dismiss() }
        view.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::segmenter.isInitialized) {
            segmenter.close()
        }
        selectedBitmap?.recycle()
        // Geçici fotoğraf dosyasını sil
        photoFile?.delete()
    }

    /**
     * Paket seçim adapter'ı
     */
    inner class PackSelectionAdapter(
        private val packs: List<CustomStickerManager.CustomPack>,
        private val onPackSelected: (CustomStickerManager.CustomPack) -> Unit
    ) : RecyclerView.Adapter<PackSelectionAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(android.R.id.text1)
            val tvCount: TextView = view.findViewById(android.R.id.text2)

            init {
                // Tıklama listener'ını ViewHolder oluşturulurken ayarla
                view.setOnClickListener {
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        onPackSelected(packs[pos])
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            // Tıklanabilirlik için arka plan ayarla
            view.isClickable = true
            view.isFocusable = true
            val attrs = intArrayOf(android.R.attr.selectableItemBackground)
            val ta = parent.context.obtainStyledAttributes(attrs)
            view.background = ta.getDrawable(0)
            ta.recycle()
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val pack = packs[position]
            holder.tvName.text = pack.name
            holder.tvName.setTextColor(resources.getColor(R.color.text_primary, theme))
            holder.tvCount.text = "${pack.stickerCount} sticker"
        }

        override fun getItemCount() = packs.size
    }
}
