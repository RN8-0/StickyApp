package com.sticly

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

class SubmitPackActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    private lateinit var etPackName: TextInputEditText
    private lateinit var chipGroupCategory: ChipGroup
    private lateinit var rvStickerUpload: RecyclerView
    private lateinit var btnAddSticker: MaterialButton
    private lateinit var btnSubmitPack: MaterialButton

    private val stickerUris = mutableListOf<Uri>()
    private var stickerAdapter: StickerUploadAdapter? = null
    private var isSubmitting = false

    private val categories = listOf(
        "humor", "love", "entertainment", "animals", "memes",
        "anime", "cute", "gaming", "sports", "food", "emoji", "other"
    )

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val remaining = MAX_STICKERS - stickerUris.size
            val toAdd = uris.take(remaining)
            stickerUris.addAll(toAdd)
            stickerAdapter?.notifyDataSetChanged()
            updateAddButtonState()
            if (uris.size > remaining) {
                Toast.makeText(this, getString(R.string.submit_max_stickers, MAX_STICKERS), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_submit_pack)

        setupEdgeToEdge()
        initViews()
        setupCategories()
        setupStickerGrid()
        setupButtons()
    }

    private fun setupEdgeToEdge() {
        val toolbarLayout = findViewById<View>(R.id.toolbarLayout)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.submitPackRoot)) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            toolbarLayout?.setPadding(
                toolbarLayout.paddingLeft, systemBars.top,
                toolbarLayout.paddingRight, toolbarLayout.paddingBottom
            )
            insets
        }
    }

    private fun initViews() {
        etPackName = findViewById(R.id.etPackName)
        chipGroupCategory = findViewById(R.id.chipGroupCategory)
        rvStickerUpload = findViewById(R.id.rvStickerUpload)
        btnAddSticker = findViewById(R.id.btnAddSticker)
        btnSubmitPack = findViewById(R.id.btnSubmitPack)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = androidx.core.content.ContextCompat.getColor(this, R.color.toolbar_bg)
    }

    private fun setupCategories() {
        categories.forEach { category ->
            val chip = Chip(this).apply {
                text = category.replaceFirstChar { it.uppercase() }
                isCheckable = true
                chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(this@SubmitPackActivity, R.color.chip_bg)
                )
                setTextColor(androidx.core.content.ContextCompat.getColor(this@SubmitPackActivity, R.color.text_primary))
                tag = category
            }
            chipGroupCategory.addView(chip)
        }
        // Select first chip by default
        (chipGroupCategory.getChildAt(0) as? Chip)?.isChecked = true
    }

    private fun setupStickerGrid() {
        stickerAdapter = StickerUploadAdapter(stickerUris) { position ->
            stickerUris.removeAt(position)
            stickerAdapter?.notifyDataSetChanged()
            updateAddButtonState()
        }
        rvStickerUpload.layoutManager = GridLayoutManager(this, 4)
        rvStickerUpload.adapter = stickerAdapter
    }

    private fun setupButtons() {
        btnAddSticker.setOnClickListener {
            if (stickerUris.size >= MAX_STICKERS) {
                Toast.makeText(this, getString(R.string.submit_max_stickers, MAX_STICKERS), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            pickImageLauncher.launch("image/*")
        }

        btnSubmitPack.setOnClickListener {
            submitPack()
        }
    }

    private fun updateAddButtonState() {
        btnAddSticker.isEnabled = stickerUris.size < MAX_STICKERS
        btnAddSticker.text = getString(R.string.submit_add_sticker_count, stickerUris.size, MAX_STICKERS)
    }

    private fun submitPack() {
        if (isSubmitting) return

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(this, getString(R.string.profile_login_required), Toast.LENGTH_SHORT).show()
            return
        }

        val packName = etPackName.text?.toString()?.trim() ?: ""
        if (packName.isEmpty()) {
            etPackName.error = getString(R.string.submit_name_required)
            return
        }

        if (stickerUris.size < MIN_STICKERS) {
            Toast.makeText(this, getString(R.string.submit_min_stickers, MIN_STICKERS), Toast.LENGTH_SHORT).show()
            return
        }

        val selectedChipId = chipGroupCategory.checkedChipId
        val selectedChip = chipGroupCategory.findViewById<Chip>(selectedChipId)
        val category = selectedChip?.tag as? String ?: "other"

        isSubmitting = true
        btnSubmitPack.isEnabled = false
        btnSubmitPack.text = getString(R.string.submit_uploading)

        val packId = UUID.randomUUID().toString()
        val storage = FirebaseStorage.getInstance()
        val db = FirebaseFirestore.getInstance()

        lifecycleScope.launch {
            try {
                val stickersList = mutableListOf<Map<String, String>>()
                val basePath = "user_uploads/${user.uid}/$packId"

                withContext(Dispatchers.IO) {
                    stickerUris.forEachIndexed { index, uri ->
                        val fileName = "sticker_${index + 1}.webp"
                        val ref = storage.reference.child("$basePath/$fileName")
                        ref.putFile(uri).await()
                        val downloadUrl = ref.downloadUrl.await().toString()
                        stickersList.add(
                            mapOf(
                                "name" to "sticker_${index + 1}",
                                "image_url" to downloadUrl
                            )
                        )
                    }
                }

                val submission = hashMapOf(
                    "user_id" to user.uid,
                    "user_email" to (user.email ?: ""),
                    "display_name" to (user.displayName ?: ""),
                    "pack_name" to packName,
                    "category" to category,
                    "stickers" to stickersList,
                    "status" to "pending",
                    "created_at" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )

                withContext(Dispatchers.IO) {
                    db.collection("user_submissions").document(packId).set(submission).await()
                }

                Toast.makeText(this@SubmitPackActivity, getString(R.string.submit_success), Toast.LENGTH_LONG).show()
                setResult(Activity.RESULT_OK)
                finish()

            } catch (e: Exception) {
                Toast.makeText(this@SubmitPackActivity, getString(R.string.submit_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
                isSubmitting = false
                btnSubmitPack.isEnabled = true
                btnSubmitPack.text = getString(R.string.submit_pack_button)
            }
        }
    }

    // Inner adapter for sticker upload grid
    inner class StickerUploadAdapter(
        private val uris: List<Uri>,
        private val onRemove: (Int) -> Unit
    ) : RecyclerView.Adapter<StickerUploadAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val image: ImageView = view.findViewById(R.id.stickerImage)
            val btnRemove: View = view.findViewById(R.id.btnRemoveSticker)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_sticker_upload, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            Glide.with(holder.itemView.context)
                .load(uris[position])
                .centerCrop()
                .into(holder.image)
            holder.btnRemove.setOnClickListener { onRemove(holder.adapterPosition) }
        }

        override fun getItemCount(): Int = uris.size
    }

    companion object {
        private const val MAX_STICKERS = 30
        private const val MIN_STICKERS = 3
    }
}
