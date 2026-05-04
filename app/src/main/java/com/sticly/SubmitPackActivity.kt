package com.sticly

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.content.res.ColorStateList
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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

    private fun escapePb(value: String): String = value.replace("'", "\\'")

    private fun buildPackSignature(stickerFiles: List<File>): String {
        return stickerFiles
            .sortedBy { it.name }
            .joinToString("|") { file -> "${file.name}:${file.length()}:${file.lastModified()}" }
    }

    private suspend fun ensurePackCanBeSubmittedAgain(
        sourcePackId: String,
        userId: String,
        userEmail: String,
        deviceId: String,
        signature: String,
        stickerCount: Int
    ) {
        val records = runCatching {
            PocketBaseHelper.listAllRecords(
                "user_submissions",
                filter = "source_pack_id='${escapePb(sourcePackId)}'",
                perPage = 200
            )
        }.getOrElse { emptyList() }

        val userKeys = listOf(userId, userEmail, deviceId).map { it.trim().lowercase() }.filter { it.isNotBlank() }
        val matching = records.filter { record ->
            val ownerKeys = listOf(
                record.optString("user_id"),
                record.optString("user_email"),
                record.optString("device_id")
            ).map { it.trim().lowercase() }.filter { it.isNotBlank() }
            userKeys.any { ownerKeys.contains(it) }
        }

        val unchanged = matching.any { record ->
            val previousSignature = record.optString("note").substringAfter("source_signature=", "")
            val previousCount = record.optInt("sticker_count", 0)
            previousSignature == signature || (previousSignature.isBlank() && previousCount == stickerCount)
        }

        if (unchanged) throw IllegalStateException(getString(R.string.publish_pack_already_submitted))
    }

    private val categories = listOf(
        "humor" to R.string.category_humor,
        "love" to R.string.category_love,
        "religious" to R.string.category_religious,
        "entertainment" to R.string.category_entertainment,
        "background" to R.string.category_background,
        "morning" to R.string.category_morning,
        "night" to R.string.category_night,
        "birthday" to R.string.category_birthday,
        "congrats" to R.string.category_congrats,
        "animals" to R.string.category_animals,
        "sports" to R.string.category_sports,
        "gaming" to R.string.category_gaming,
        "movie" to R.string.category_movie,
        "music" to R.string.category_music,
        "food" to R.string.category_food,
        "emoji" to R.string.category_emoji,
        "cars" to R.string.category_cars,
        "motivation" to R.string.category_motivation,
        "cute" to R.string.category_cute,
        "text" to R.string.category_text,
        "memes" to R.string.category_memes,
        "anime" to R.string.category_anime,
        "nature" to R.string.category_nature,
        "other" to R.string.category_other
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
        chipGroupCategory.isSelectionRequired = true
        categories.forEach { (category, stringRes) ->
            val chip = Chip(this).apply {
                text = getString(stringRes)
                isCheckable = true
                isCheckedIconVisible = false
                minHeight = resources.getDimensionPixelSize(R.dimen.chip_min_height)
                chipCornerRadius = 22.dp().toFloat()
                chipStartPadding = 12.dp().toFloat()
                chipEndPadding = 12.dp().toFloat()
                textSize = 13f
                tag = category
                setOnCheckedChangeListener { _, checked -> styleCategoryChip(this, checked) }
            }
            styleCategoryChip(chip, false)
            chipGroupCategory.addView(chip)
        }
        (chipGroupCategory.getChildAt(0) as? Chip)?.isChecked = true
    }

    private fun styleCategoryChip(chip: Chip, checked: Boolean) {
        val background = ContextCompat.getColor(this, if (checked) R.color.primary else R.color.surface)
        val stroke = ContextCompat.getColor(this, if (checked) R.color.primary else R.color.primary_light)
        val text = ContextCompat.getColor(this, if (checked) R.color.white else R.color.text_primary)
        chip.chipBackgroundColor = ColorStateList.valueOf(background)
        chip.chipStrokeColor = ColorStateList.valueOf(stroke)
        chip.chipStrokeWidth = 1.dp().toFloat()
        chip.setTextColor(text)
        chip.elevation = if (checked) 4.dp().toFloat() else 0f
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

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
                    val sourceSignature = buildPackSignature(stickerFiles)
                    ensurePackCanBeSubmittedAgain(packId, userId, email, deviceId, sourceSignature, stickerFiles.size)

                    val uploadFiles = stickerFiles.mapIndexed { index, file ->
                        PocketBaseHelper.UploadFile("images", "sticker_${index + 1}.webp", "image/webp", file.readBytes())
                    }
                    val fields = mapOf(
                        "device_id" to deviceId,
                        "user_id" to userId,
                        "user_email" to email,
                        "display_name" to displayName,
                        "publisher_name" to displayName,
                        "photo_url" to (prefs.getString("user_photo_url", "") ?: ""),
                        "source_pack_id" to packId,
                        "pack_name" to packName,
                        "name" to packName,
                        "category" to category,
                        "sticker_count" to stickerFiles.size.toString(),
                        "status" to "pending",
                        "created_at" to java.time.Instant.now().toString(),
                        "is_animated" to pack.isAnimated.toString(),
                        "note" to "source_signature=$sourceSignature",
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
