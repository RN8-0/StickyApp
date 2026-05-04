package com.sticly

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SuggestActivity : AppCompatActivity() {

    private lateinit var inputSuggestion: TextInputEditText
    private lateinit var categoryChipGroup: ChipGroup
    private lateinit var btnSend: MaterialButton
    private var selectedCategory: String = ""

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
        applyTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_suggest)

        inputSuggestion = findViewById(R.id.inputSuggestion)
        categoryChipGroup = findViewById(R.id.categoryChipGroup)
        btnSend = findViewById(R.id.btnSend)

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        setupCategoryChips()

        btnSend.setOnClickListener {
            sendSuggestion()
        }
        setupEdgeToEdge()
    }

    private fun setupEdgeToEdge() {
        val root = findViewById<android.view.View>(R.id.suggest_root)
        val toolbarLayout = findViewById<android.view.View>(R.id.toolbarLayout)
        
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            toolbarLayout?.setPadding(toolbarLayout.paddingLeft, systemBars.top, toolbarLayout.paddingRight, toolbarLayout.paddingBottom)
            root?.setPadding(root.paddingLeft, root.paddingTop, root.paddingRight, systemBars.bottom)
            
            insets
        }
    }

    private fun setupCategoryChips() {
        categoryChipGroup.isSelectionRequired = true
        categories.forEach { (key, stringRes) ->
            val chip = Chip(this).apply {
                text = getString(stringRes)
                isCheckable = true
                isCheckedIconVisible = true
                minHeight = resources.getDimensionPixelSize(R.dimen.chip_min_height)
                chipCornerRadius = resources.getDimension(R.dimen.chip_corner_radius)
                setChipBackgroundColorResource(R.color.chip_bg)
                setChipStrokeColorResource(R.color.accent)
                chipStrokeWidth = resources.getDimension(R.dimen.chip_stroke_width)
                setTextColor(getColor(R.color.text_primary))
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedCategory = key
                        setChipBackgroundColorResource(R.color.accent)
                        setTextColor(getColor(R.color.white))
                    } else {
                        setChipBackgroundColorResource(R.color.chip_bg)
                        setTextColor(getColor(R.color.text_primary))
                    }
                }
            }
            categoryChipGroup.addView(chip)
        }
        (categoryChipGroup.getChildAt(0) as? Chip)?.isChecked = true
    }

    private fun sendSuggestion() {
        val suggestion = inputSuggestion.text?.toString()?.trim() ?: ""

        if (suggestion.isEmpty()) {
            Toast.makeText(this, R.string.fill_all_fields, Toast.LENGTH_SHORT).show()
            return
        }

        btnSend.isEnabled = false
        btnSend.text = getString(R.string.sending)

        val now = Date()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        val data = JSONObject().apply {
            put("suggestion", suggestion)
            put("category", selectedCategory.ifBlank { "other" })
            put("timestamp", System.currentTimeMillis())
            put("date", dateFormat.format(now))
            put("time", timeFormat.format(now))
            put("source", "android")
        }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                withContext(Dispatchers.IO) {
                    PocketBaseHelper.createRecord("suggestions", data)
                }
                Toast.makeText(this@SuggestActivity, R.string.suggestion_sent, Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                android.util.Log.e("SuggestActivity", "Öneri gönderilemedi: ${e.message}", e)
                btnSend.isEnabled = true
                btnSend.text = getString(R.string.send)
                Toast.makeText(this@SuggestActivity, R.string.message_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun applyTheme() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        when (prefs.getInt("theme", 0)) {
            0 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            2 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }
}
