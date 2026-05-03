package com.sticly

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Shows the profile of a sticker pack publisher (only for non-Sticky publishers).
 * Lists all packs published by that user.
 */
class PublisherProfileActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    private lateinit var avatar: ImageView
    private lateinit var displayName: TextView
    private lateinit var subtitle: TextView
    private lateinit var packsContainer: LinearLayout
    private lateinit var progress: ProgressBar
    private lateinit var emptyView: TextView

    private var publisherId: String = ""
    private var publisherName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_publisher_profile)
        window.statusBarColor = ContextCompat.getColor(this, R.color.toolbar_bg)

        publisherId = intent.getStringExtra(EXTRA_PUBLISHER_ID).orEmpty()
        publisherName = intent.getStringExtra(EXTRA_PUBLISHER_NAME).orEmpty()
        val publisherPhoto = intent.getStringExtra(EXTRA_PUBLISHER_PHOTO).orEmpty()

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        avatar = findViewById(R.id.publisherAvatar)
        displayName = findViewById(R.id.publisherDisplayName)
        subtitle = findViewById(R.id.publisherSubtitle)
        packsContainer = findViewById(R.id.publisherPacksContainer)
        progress = findViewById(R.id.publisherProgress)
        emptyView = findViewById(R.id.publisherEmpty)

        displayName.text = publisherName.ifBlank { getString(R.string.publisher_default) }
        subtitle.text = getString(R.string.publisher_subtitle_loading)
        if (publisherPhoto.isNotBlank()) {
            Glide.with(this).load(publisherPhoto).circleCrop().placeholder(R.drawable.ic_person).into(avatar)
        } else {
            avatar.setImageResource(R.drawable.ic_person)
        }

        loadPublisherPacks()
    }

    private fun loadPublisherPacks() {
        progress.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        packsContainer.removeAllViews()

        lifecycleScope.launch {
            val packs = withContext(Dispatchers.IO) { fetchPublisherPacks(publisherId) }
            progress.visibility = View.GONE
            if (packs.isEmpty()) {
                emptyView.visibility = View.VISIBLE
                subtitle.text = getString(R.string.publisher_subtitle_count, 0)
                return@launch
            }
            subtitle.text = getString(R.string.publisher_subtitle_count, packs.size)
            packs.forEach { pack -> packsContainer.addView(buildPackCard(pack)) }
        }
    }

    private data class PackSummary(val id: String, val name: String, val trayUrl: String, val downloadCount: Int)

    private suspend fun fetchPublisherPacks(publisherId: String): List<PackSummary> {
        if (publisherId.isBlank()) return emptyList()
        return runCatching {
            val escaped = publisherId.replace("'", "\\'")
            // Match by publisher_user_id, publisher_email or publisher (name) — only user-submitted packs
            val filter = "(publisher_user_id='$escaped' || publisher_email='$escaped' || publisher='${publisherName.replace("'", "\\'")}') && source='user_submission'"
            val url = "${PocketBaseHelper.PB_URL}/api/collections/stickers/records?perPage=50&filter=${URLEncoder.encode(filter, "UTF-8")}"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.connect()
            if (conn.responseCode != 200) {
                conn.disconnect()
                return@runCatching emptyList()
            }
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val items = JSONObject(body).optJSONArray("items") ?: JSONArray()
            (0 until items.length()).map { i ->
                val r = items.getJSONObject(i)
                val id = r.optString("id")
                val tray = r.optString("tray_image_file").ifBlank { "tray.webp" }
                val trayUrl = r.optString("tray_url").ifBlank {
                    PocketBaseHelper.getFileUrl("stickers", id, tray)
                }
                PackSummary(
                    id = id,
                    name = r.optString("name").ifBlank { r.optString("pack_name") },
                    trayUrl = trayUrl,
                    downloadCount = r.optInt("download_count", 0)
                )
            }
        }.getOrElse { emptyList() }
    }

    private fun buildPackCard(pack: PackSummary): View {
        val card = layoutInflater.inflate(R.layout.item_publisher_pack, packsContainer, false)
        val tray = card.findViewById<ImageView>(R.id.packTray)
        val name = card.findViewById<TextView>(R.id.packName)
        val downloads = card.findViewById<TextView>(R.id.packDownloads)
        Glide.with(this).load(pack.trayUrl).placeholder(R.drawable.ic_logo_white).into(tray)
        name.text = pack.name
        downloads.text = getString(R.string.publisher_pack_downloads, pack.downloadCount)
        card.setOnClickListener {
            val intent = Intent(this, DetailsActivity::class.java)
            intent.putExtra("packId", pack.id)
            startActivity(intent)
        }
        return card
    }

    companion object {
        const val EXTRA_PUBLISHER_ID = "publisher_id"
        const val EXTRA_PUBLISHER_NAME = "publisher_name"
        const val EXTRA_PUBLISHER_PHOTO = "publisher_photo"
    }
}
