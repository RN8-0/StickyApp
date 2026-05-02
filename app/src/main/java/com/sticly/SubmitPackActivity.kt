package com.sticly

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class SubmitPackActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    private lateinit var etPackName: TextInputEditText
    private lateinit var chipGroupCategory: ChipGroup
    private lateinit var rvStickerUpload: RecyclerView
    private lateinit var btnSubmitPack: MaterialButton

    private var selectedPackId: String? = null
    private var packAdapter: SelectablePackAdapter? = null
    private var isSubmitting = false
    private val minStoreStickerCount = 9
    private val maxStoreStickerCount = 30

    private val categories = listOf(
        "humor", "love", "entertainment", "animals", "memes",
        "anime", "cute", "gaming", "sports", "food", "emoji", "other"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_submit_pack)

        setupEdgeToEdge()
        initViews()
        setupCategories()
        loadMyPacks()
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
        (chipGroupCategory.getChildAt(0) as? Chip)?.isChecked = true
    }

    private fun loadMyPacks() {
        val packs = CustomStickerManager.getCustomPacks(this)
        packAdapter = SelectablePackAdapter(packs) { pack ->
            selectedPackId = pack.id
            etPackName.setText(pack.name)
        }
        rvStickerUpload.layoutManager = LinearLayoutManager(this)
        rvStickerUpload.adapter = packAdapter

        // Pre-select pack if launched from DetailsActivity
        val preselectedPackId = intent.getStringExtra("packId")
        if (preselectedPackId != null) {
            selectedPackId = preselectedPackId
            packAdapter?.selectPack(preselectedPackId)
            val pack = packs.firstOrNull { it.id == preselectedPackId }
            if (pack != null) etPackName.setText(pack.name)
        }
    }

    private fun setupButtons() {
        btnSubmitPack.setOnClickListener { submitPack() }
    }

    private fun submitPack() {
        if (isSubmitting) return

        val prefs = getSharedPreferences("sticky_prefs", MODE_PRIVATE)
        val email = prefs.getString("user_email", "") ?: ""
        if (email.trim().isEmpty()) {
            Toast.makeText(this, getString(R.string.profile_login_required), Toast.LENGTH_SHORT).show()
            return
        }

        val packId = selectedPackId
        if (packId == null) {
            Toast.makeText(this, getString(R.string.submit_select_pack_required), Toast.LENGTH_SHORT).show()
            return
        }

        val packName = etPackName.text?.toString()?.trim() ?: ""
        if (packName.isEmpty()) {
            etPackName.error = getString(R.string.submit_name_required)
            return
        }

        val selectedChipId = chipGroupCategory.checkedChipId
        val selectedChip = chipGroupCategory.findViewById<Chip>(selectedChipId)
        val category = selectedChip?.tag as? String ?: "other"

        val pack = CustomStickerManager.getCustomPacks(this).firstOrNull { it.id == packId }
        if (pack == null || pack.stickerCount !in minStoreStickerCount..maxStoreStickerCount) {
            Toast.makeText(this, getString(R.string.publish_pack_count_range), Toast.LENGTH_SHORT).show()
            return
        }

        isSubmitting = true
        btnSubmitPack.isEnabled = false
        btnSubmitPack.text = getString(R.string.submit_uploading)

        val deviceId = PreferencesHelper.getDeviceId(this)
        val displayName = prefs.getString("user_display_name", "") ?: ""
        val firebaseUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        val userId = firebaseUid.ifBlank { PocketBaseHelper.getAuthRecordId().orEmpty().ifBlank { email } }

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val stickerFiles = CustomStickerManager.getStickerFiles(this@SubmitPackActivity, packId)
                    if (stickerFiles.size !in minStoreStickerCount..maxStoreStickerCount) {
                        throw IllegalStateException(getString(R.string.publish_pack_count_range))
                    }

                    val uploadFiles = stickerFiles.mapIndexed { index, file ->
                        PocketBaseHelper.UploadFile("images", "sticker_${index + 1}.webp", "image/webp", file.readBytes())
                    }
                    val fields = mapOf(
                        "device_id" to deviceId,
                        "user_id" to userId,
                        "user_email" to email,
                        "display_name" to displayName,
                        "publisher_name" to displayName,
                        "source_pack_id" to packId,
                        "pack_name" to packName,
                        "name" to packName,
                        "category" to category,
                        "sticker_count" to stickerFiles.size.toString(),
                        "status" to "pending",
                        "created_at" to java.time.Instant.now().toString(),
                        "is_animated" to pack.isAnimated.toString(),
                        "stickers" to "[]"
                    )
                    val created = PocketBaseHelper.createMultipartRecord("user_submissions", fields, uploadFiles)
                    val recordId = created.getString("id")
                    val uploadedImages = created.optJSONArray("images") ?: JSONArray()
                    val stickersJson = JSONArray()
                    for (index in 0 until uploadedImages.length()) {
                        val uploadedFile = uploadedImages.optString(index)
                        val url = PocketBaseHelper.getFileUrl("user_submissions", recordId, uploadedFile)
                        stickersJson.put(JSONObject().apply {
                            put("name", "sticker_${index + 1}")
                            put("image_file", uploadedFile)
                            put("image_url", url)
                            put("url", url)
                            put("emojis", JSONArray().put("⭐"))
                        })
                    }
                    runCatching {
                        PocketBaseHelper.updateRecord("user_submissions", recordId, JSONObject().apply {
                            put("stickers", stickersJson)
                            put("sticker_data", stickersJson)
                            put("sticker_count", stickersJson.length())
                        })
                    }.onFailure {
                        android.util.Log.w("SubmitPack", "Submission created, sticker metadata update skipped: ${it.message}")
                    }
                }
                Toast.makeText(this@SubmitPackActivity, getString(R.string.submit_success), Toast.LENGTH_LONG).show()
                setResult(android.app.Activity.RESULT_OK)
                finish()
            } catch (e: Exception) {
                Toast.makeText(
                    this@SubmitPackActivity,
                    getString(R.string.submit_failed, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
                isSubmitting = false
                btnSubmitPack.isEnabled = true
                btnSubmitPack.text = getString(R.string.submit_pack_button)
            }
        }
    }

    inner class SelectablePackAdapter(
        private val packs: List<CustomStickerManager.CustomPack>,
        private val onSelect: (CustomStickerManager.CustomPack) -> Unit
    ) : RecyclerView.Adapter<SelectablePackAdapter.VH>() {

        private var selectedId: String? = null

        fun selectPack(packId: String) {
            selectedId = packId
            notifyDataSetChanged()
        }

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val card: MaterialCardView = view.findViewById(R.id.packSelectionCard)
            val cover: ImageView = view.findViewById(R.id.ivPackCover)
            val name: TextView = view.findViewById(R.id.tvPackName)
            val meta: TextView = view.findViewById(R.id.tvPackMeta)
            val checkmark: ImageView = view.findViewById(R.id.ivSelected)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_selectable_pack, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val pack = packs[position]
            val isSelected = pack.id == selectedId

            holder.name.text = pack.name
            holder.meta.text = "${pack.stickerCount} stickers"

            val trayFile = File(filesDir, "custom_stickers/${pack.id}/tray.webp")
            if (trayFile.exists()) {
                Glide.with(holder.cover.context).load(trayFile).centerCrop().into(holder.cover)
            } else {
                holder.cover.setImageResource(R.drawable.ic_sticker)
            }

            holder.checkmark.visibility = if (isSelected) View.VISIBLE else View.GONE
            holder.card.strokeWidth = if (isSelected) 3 else 0
            holder.card.setOnClickListener {
                selectedId = pack.id
                notifyDataSetChanged()
                onSelect(pack)
            }
        }

        override fun getItemCount(): Int = packs.size
    }
}
