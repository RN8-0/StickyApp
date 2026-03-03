package com.sticly

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

import java.text.SimpleDateFormat
import java.util.*

class AIStickerActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    companion object {
        private const val DAILY_FREE_LIMIT = 7
        private const val PREFS_AI_COUNT = "ai_gen_count"
        private const val PREFS_AI_DATE = "ai_gen_date"
    }

    private lateinit var etPrompt: TextInputEditText
    private lateinit var btnGenerate: MaterialButton
    private lateinit var previewCard: CardView
    private lateinit var ivPreview: ImageView
    private lateinit var loadingOverlay: View
    private lateinit var tvLoadingStatus: TextView
    private lateinit var actionButtons: View
    private lateinit var tvError: TextView
    private lateinit var tvDailyCounter: TextView
    private lateinit var styleChipGroup: ChipGroup
    private lateinit var premiumUpsellCard: CardView
    private lateinit var bgRemovalRow: View
    private lateinit var switchRemoveBg: SwitchMaterial
    private lateinit var exampleSection: View

    private var generatedBitmap: Bitmap? = null
    private var rawBitmap: Bitmap? = null
    private var generateJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_sticker)

        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_dark)

        initAiScreen()
    }

    private fun initAiScreen() {
        etPrompt = findViewById(R.id.etPrompt)
        btnGenerate = findViewById(R.id.btnGenerate)
        previewCard = findViewById(R.id.previewCard)
        ivPreview = findViewById(R.id.ivPreview)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        tvLoadingStatus = findViewById(R.id.tvLoadingStatus)
        actionButtons = findViewById(R.id.actionButtons)
        tvError = findViewById(R.id.tvError)
        tvDailyCounter = findViewById(R.id.tvDailyCounter)
        styleChipGroup = findViewById(R.id.styleChipGroup)
        premiumUpsellCard = findViewById(R.id.premiumUpsellCard)
        bgRemovalRow = findViewById(R.id.bgRemovalRow)
        switchRemoveBg = findViewById(R.id.switchRemoveBg)
        exampleSection = findViewById(R.id.exampleSection)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        updateDailyCounter()

        if (!PreferencesHelper.isPremium(this)) {
            premiumUpsellCard.visibility = View.VISIBLE
            findViewById<MaterialButton>(R.id.btnGetPremium).setOnClickListener {
                startActivity(android.content.Intent(this, PremiumActivity::class.java))
            }
        }

        btnGenerate.setOnClickListener { generateSticker() }

        findViewById<MaterialButton>(R.id.btnTryAgain).setOnClickListener {
            generateSticker()
        }

        findViewById<MaterialButton>(R.id.btnSaveSticker).setOnClickListener {
            showPackPickerDialog()
        }

        switchRemoveBg.setOnCheckedChangeListener { _, isChecked ->
            val bmp = rawBitmap ?: return@setOnCheckedChangeListener
            lifecycleScope.launch {
                val processed = if (isChecked) processSticker(bmp) else {
                    Bitmap.createScaledBitmap(bmp, 512, 512, true)
                }
                generatedBitmap = processed
                ivPreview.setImageBitmap(processed)
            }
        }

        setupExampleTaps()
    }

    private fun setupExampleTaps() {
        val examples = listOf(
            R.string.ai_example_1, R.string.ai_example_2, R.string.ai_example_3,
            R.string.ai_example_4, R.string.ai_example_5, R.string.ai_example_6
        )
        val exampleLayout = exampleSection as? LinearLayout ?: return
        var idx = 0
        for (i in 0 until exampleLayout.childCount) {
            val child = exampleLayout.getChildAt(i)
            if (child is LinearLayout) {
                for (j in 0 until child.childCount) {
                    val card = child.getChildAt(j)
                    if (card is CardView && idx < examples.size) {
                        val text = getString(examples[idx])
                        card.setOnClickListener {
                            etPrompt.setText(text)
                            etPrompt.setSelection(text.length)
                        }
                        idx++
                    }
                }
            }
        }
    }

    private fun getSelectedStyle(): String {
        return when (styleChipGroup.checkedChipId) {
            R.id.chipCartoon -> "cartoon"
            R.id.chipKawaii -> "kawaii cute"
            R.id.chipPixel -> "pixel art"
            R.id.chipRealistic -> "realistic"
            R.id.chipComic -> "comic book"
            R.id.chipWatercolor -> "watercolor"
            R.id.chipFlat -> "flat design minimalist"
            R.id.chip3d -> "3D rendered"
            R.id.chipAnime -> "anime manga"
            R.id.chipDoodle -> "hand drawn doodle"
            R.id.chipPop -> "pop art bold colors"
            R.id.chipRetro -> "retro vintage 80s"
            R.id.chipGraffiti -> "street art graffiti"
            R.id.chipNeon -> "neon glow dark background"
            else -> "cartoon"
        }
    }

    private fun canGenerate(): Boolean {
        if (PreferencesHelper.isPremium(this)) return true
        resetIfNewDay()
        return getPrefs().getInt(PREFS_AI_COUNT, 0) < DAILY_FREE_LIMIT
    }

    private fun getRemainingCount(): Int {
        if (PreferencesHelper.isPremium(this)) return -1
        resetIfNewDay()
        return DAILY_FREE_LIMIT - getPrefs().getInt(PREFS_AI_COUNT, 0)
    }

    private fun incrementCount() {
        if (PreferencesHelper.isPremium(this)) return
        resetIfNewDay()
        val count = getPrefs().getInt(PREFS_AI_COUNT, 0)
        getPrefs().edit()
            .putInt(PREFS_AI_COUNT, count + 1)
            .putString(PREFS_AI_DATE, todayString())
            .apply()
    }

    private fun resetIfNewDay() {
        val saved = getPrefs().getString(PREFS_AI_DATE, "") ?: ""
        if (saved != todayString()) {
            getPrefs().edit()
                .putInt(PREFS_AI_COUNT, 0)
                .putString(PREFS_AI_DATE, todayString())
                .apply()
        }
    }

    private fun todayString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun getPrefs() =
        getSharedPreferences("sticky_prefs", MODE_PRIVATE)

    private fun updateDailyCounter() {
        val remaining = getRemainingCount()
        tvDailyCounter.text = if (remaining < 0) {
            "✨ ${getString(R.string.ai_unlimited)}"
        } else {
            "⚡ ${getString(R.string.ai_remaining, remaining, DAILY_FREE_LIMIT)}"
        }
    }

    private fun generateSticker() {
        val prompt = etPrompt.text?.toString()?.trim() ?: ""
        if (prompt.isEmpty()) {
            Toast.makeText(this, R.string.ai_empty_prompt, Toast.LENGTH_SHORT).show()
            return
        }

        if (!canGenerate()) {
            showDailyLimitDialog()
            return
        }

        generateJob?.cancel()
        generateJob = lifecycleScope.launch {
            try {
                setGenerating(true)
                tvError.visibility = View.GONE
                exampleSection.visibility = View.GONE

                updateStatus(getString(R.string.ai_optimizing_prompt))
                val style = getSelectedStyle()
                val optimizedPrompt = optimizePromptWithDeepSeek(prompt, style)

                updateStatus(getString(R.string.ai_generating_image))
                val bitmap = generateImageWithStableHorde(optimizedPrompt)

                if (bitmap != null) {
                    updateStatus(getString(R.string.ai_processing))
                    rawBitmap = Bitmap.createScaledBitmap(bitmap, 512, 512, true)
                    generatedBitmap = if (switchRemoveBg.isChecked) {
                        processSticker(bitmap)
                    } else {
                        rawBitmap
                    }

                    withContext(Dispatchers.Main) {
                        ivPreview.setImageBitmap(generatedBitmap)
                        actionButtons.visibility = View.VISIBLE
                        bgRemovalRow.visibility = View.VISIBLE
                        incrementCount()
                        updateDailyCounter()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        tvError.text = getString(R.string.ai_generation_failed)
                        tvError.visibility = View.VISIBLE
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvError.text = e.message ?: getString(R.string.ai_generation_failed)
                    tvError.visibility = View.VISIBLE
                }
            } finally {
                withContext(Dispatchers.Main) {
                    setGenerating(false)
                }
            }
        }
    }

    private fun setGenerating(isGenerating: Boolean) {
        previewCard.visibility = View.VISIBLE
        loadingOverlay.visibility = if (isGenerating) View.VISIBLE else View.GONE
        btnGenerate.isEnabled = !isGenerating
        if (isGenerating) {
            actionButtons.visibility = View.GONE
            bgRemovalRow.visibility = View.GONE
            ivPreview.setImageDrawable(null)
        }
    }

    private fun updateStatus(status: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            tvLoadingStatus.text = status
        }
    }

    private suspend fun optimizePromptWithDeepSeek(userPrompt: String, style: String): String =
        withContext(Dispatchers.IO) {
            try {
                val apiKey = BuildConfig.DEEPSEEK_API_KEY
                if (apiKey.isEmpty()) return@withContext buildFallbackPrompt(userPrompt, style)

                val systemMessage = """You are an expert AI sticker prompt engineer for SDXL image generation. Convert user descriptions into highly detailed, optimized prompts.
Rules:
- Output ONLY the optimized prompt text, nothing else
- Style: $style sticker art
- Always include: clean white background, thick bold black outline, vibrant colors, high contrast, professional sticker design, vector-like quality, centered composition
- Add relevant artistic details: lighting, shading, expression, texture
- Use quality boosters: masterpiece, best quality, ultra detailed, sharp focus, professional illustration
- Keep it under 120 words
- Make subjects expressive, appealing and eye-catching"""

                val body = JSONObject().apply {
                    put("model", "deepseek-chat")
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", systemMessage)
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", userPrompt)
                        })
                    })
                    put("max_tokens", 150)
                    put("temperature", 0.7)
                }

                val conn = URL("https://api.deepseek.com/chat/completions").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.doOutput = true

                OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

                if (conn.responseCode == 200) {
                    val response = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                    val content = JSONObject(response).getJSONArray("choices")
                        .getJSONObject(0).getJSONObject("message").getString("content").trim()
                    conn.disconnect()
                    content
                } else {
                    conn.disconnect()
                    buildFallbackPrompt(userPrompt, style)
                }
            } catch (e: Exception) {
                buildFallbackPrompt(userPrompt, style)
            }
        }

    private fun buildFallbackPrompt(userPrompt: String, style: String): String {
        return "$style sticker of $userPrompt, white background, bold black outline, expressive, cute, sticker design, high quality"
    }

    private suspend fun generateImageWithStableHorde(prompt: String): Bitmap? =
        withContext(Dispatchers.IO) {
            // Race: Pollinations and Stable Horde in PARALLEL
            try {
                coroutineScope {
                    val pollinations = async {
                        try { generateWithPollinations(prompt) } catch (_: Exception) { null }
                    }
                    val horde = async {
                        try { tryGenerateWithModel(prompt, "Deliberate") } catch (_: Exception) { null }
                    }

                    val polResult = pollinations.await()
                    if (polResult != null) {
                        horde.cancel()
                        return@coroutineScope polResult
                    }

                    val hordeResult = horde.await()
                    if (hordeResult != null) return@coroutineScope hordeResult

                    for (model in listOf("Dreamshaper", "stable_diffusion")) {
                        try {
                            val result = tryGenerateWithModel(prompt, model)
                            if (result != null) return@coroutineScope result
                        } catch (_: Exception) {}
                    }
                    null
                }
            } catch (_: Exception) { null }
        }

    private suspend fun generateWithPollinations(prompt: String): Bitmap? =
        withContext(Dispatchers.IO) {
            val encodedPrompt = java.net.URLEncoder.encode(prompt, "UTF-8")

            for (model in listOf("turbo", "flux")) {
                try {
                    val urlStr = "https://image.pollinations.ai/prompt/$encodedPrompt?width=512&height=512&nologo=true&model=$model&seed=${System.currentTimeMillis()}"
                    android.util.Log.d("AiGenerate", "Pollinations ($model) request")

                    val conn = URL(urlStr).openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 15000
                    conn.readTimeout = 25000
                    conn.instanceFollowRedirects = true
                    conn.setRequestProperty("User-Agent", "StickyApp/1.0")

                    val code = conn.responseCode
                    android.util.Log.d("AiGenerate", "Pollinations ($model) response: $code")
                    if (code == 200) {
                        val bitmap = BitmapFactory.decodeStream(conn.inputStream)
                        conn.disconnect()
                        if (bitmap != null) return@withContext bitmap
                    }
                    conn.disconnect()
                } catch (e: Exception) {
                    android.util.Log.e("AiGenerate", "Pollinations $model failed: ${e.message}")
                }
            }
            null
        }

    private suspend fun tryGenerateWithModel(prompt: String, model: String): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("prompt", "$prompt ### blurry, low quality, distorted, ugly, deformed, watermark, text, bad anatomy")
                    put("params", JSONObject().apply {
                        put("width", 512)
                        put("height", 512)
                        put("steps", 10)
                        put("cfg_scale", 7.0)
                        put("sampler_name", "k_euler")
                        put("karras", true)
                    })
                    put("nsfw", false)
                    put("censor_nsfw", true)
                    put("models", JSONArray().apply {
                        put(model)
                    })
                }

                val submitConn = URL("https://stablehorde.net/api/v2/generate/async").openConnection() as HttpURLConnection
                submitConn.requestMethod = "POST"
                submitConn.setRequestProperty("Content-Type", "application/json")
                submitConn.setRequestProperty("apikey", "0000000000")
                submitConn.connectTimeout = 10000
                submitConn.readTimeout = 10000
                submitConn.doOutput = true

                OutputStreamWriter(submitConn.outputStream).use { it.write(body.toString()) }

                if (submitConn.responseCode != 202) {
                    submitConn.disconnect()
                    return@withContext null
                }

                val submitResponse = BufferedReader(InputStreamReader(submitConn.inputStream)).use { it.readText() }
                submitConn.disconnect()
                val jobId = JSONObject(submitResponse).getString("id")
                android.util.Log.d("AiGenerate", "Stable Horde job: $jobId model: $model")

                var imageUrl: String? = null
                for (attempt in 1..20) {
                    delay(2000)
                    withContext(Dispatchers.Main) {
                        tvLoadingStatus.text = getString(R.string.ai_generating_image) + " ⏳"
                    }

                    val checkConn = URL("https://stablehorde.net/api/v2/generate/status/$jobId").openConnection() as HttpURLConnection
                    checkConn.requestMethod = "GET"
                    checkConn.connectTimeout = 10000
                    checkConn.readTimeout = 10000

                    val statusCode = checkConn.responseCode
                    if (statusCode != 200) { checkConn.disconnect(); continue }

                    val statusResponse = BufferedReader(InputStreamReader(checkConn.inputStream)).use { it.readText() }
                    checkConn.disconnect()

                    val statusJson = JSONObject(statusResponse)
                    if (statusJson.optBoolean("faulted", false)) return@withContext null
                    if (statusJson.optBoolean("done", false)) {
                        val generations = statusJson.getJSONArray("generations")
                        if (generations.length() > 0) {
                            imageUrl = generations.getJSONObject(0).getString("img")
                        }
                        break
                    }
                }

                if (imageUrl == null) return@withContext null

                val imgConn = URL(imageUrl).openConnection() as HttpURLConnection
                imgConn.requestMethod = "GET"
                imgConn.connectTimeout = 10000
                imgConn.readTimeout = 20000
                imgConn.instanceFollowRedirects = true

                val bitmap = if (imgConn.responseCode == 200) {
                    BitmapFactory.decodeStream(imgConn.inputStream)
                } else null
                imgConn.disconnect()
                bitmap
            } catch (e: Exception) {
                null
            }
        }

    private suspend fun processSticker(source: Bitmap): Bitmap =
        withContext(Dispatchers.IO) {
            val scaled = Bitmap.createScaledBitmap(source, 512, 512, true)
            val result = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(512 * 512)
            scaled.getPixels(pixels, 0, 512, 0, 0, 512, 512)

            for (i in pixels.indices) {
                val r = Color.red(pixels[i])
                val g = Color.green(pixels[i])
                val b = Color.blue(pixels[i])
                if (r > 240 && g > 240 && b > 240) {
                    pixels[i] = Color.TRANSPARENT
                } else if (r > 220 && g > 220 && b > 220) {
                    val alpha = ((255 - r) + (255 - g) + (255 - b)) * 255 / (35 * 3)
                    pixels[i] = Color.argb(alpha.coerceIn(0, 255), r, g, b)
                }
            }

            result.setPixels(pixels, 0, 512, 0, 0, 512, 512)
            if (!scaled.isRecycled && scaled !== source) scaled.recycle()
            result
        }

    private fun showPackPickerDialog() {
        val bitmap = generatedBitmap ?: return
        val packs = CustomStickerManager.getCustomPacks(this)

        if (packs.isEmpty()) {
            showCreatePackDialog(bitmap)
            return
        }

        val packNames = packs.map { "${it.name} (${it.stickerCount})" }.toMutableList()
        packNames.add(getString(R.string.ai_create_new_pack))

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.ai_select_pack))
            .setItems(packNames.toTypedArray()) { _, which ->
                if (which == packs.size) {
                    showCreatePackDialog(bitmap)
                } else {
                    saveStickerToPack(packs[which].id, bitmap)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showCreatePackDialog(bitmap: Bitmap) {
        val input = EditText(this).apply {
            hint = getString(R.string.ai_new_pack_title)
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.ai_new_pack_title))
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val packId = CustomStickerManager.createPack(this, name, false)
                    saveStickerToPack(packId, bitmap)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun saveStickerToPack(packId: String, bitmap: Bitmap) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val success = CustomStickerManager.addStickerToPack(
                    this@AIStickerActivity, packId, bitmap
                )
                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(this@AIStickerActivity, R.string.ai_saved_success, Toast.LENGTH_SHORT).show()
                        generatedBitmap = null
                        rawBitmap = null
                        previewCard.visibility = View.GONE
                        bgRemovalRow.visibility = View.GONE
                        exampleSection.visibility = View.VISIBLE
                        etPrompt.text?.clear()
                    } else {
                        Toast.makeText(this@AIStickerActivity, R.string.ai_generation_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AIStickerActivity, e.message ?: getString(R.string.ai_generation_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showDailyLimitDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.ai_daily_limit_title)
            .setMessage(getString(R.string.ai_daily_limit_msg, DAILY_FREE_LIMIT))
            .setPositiveButton(R.string.get_premium_access) { _, _ ->
                startActivity(android.content.Intent(this, PremiumActivity::class.java))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        generateJob?.cancel()
    }
}
