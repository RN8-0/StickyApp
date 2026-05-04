package com.sticly

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class PublisherProfileActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    private lateinit var avatar: ImageView
    private lateinit var displayName: TextView
    private lateinit var subtitle: TextView
    private lateinit var bio: TextView
    private lateinit var statPacks: TextView
    private lateinit var statFollowers: TextView
    private lateinit var statFollowing: TextView
    private lateinit var statLikes: TextView
    private lateinit var followButton: MaterialButton
    private lateinit var packsContainer: LinearLayout
    private lateinit var progress: ProgressBar
    private lateinit var emptyView: TextView

    private var publisherId: String = ""
    private var publisherName: String = ""
    private var publisherEmail: String = ""
    private var publisherPhoto: String = ""
    private var isFollowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_publisher_profile)
        window.statusBarColor = ContextCompat.getColor(this, R.color.toolbar_bg)

        publisherId = intent.getStringExtra(EXTRA_PUBLISHER_ID).orEmpty()
        publisherName = intent.getStringExtra(EXTRA_PUBLISHER_NAME).orEmpty()
        publisherPhoto = intent.getStringExtra(EXTRA_PUBLISHER_PHOTO).orEmpty()
        publisherEmail = intent.getStringExtra(EXTRA_PUBLISHER_EMAIL).orEmpty()

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        avatar = findViewById(R.id.publisherAvatar)
        displayName = findViewById(R.id.publisherDisplayName)
        subtitle = findViewById(R.id.publisherSubtitle)
        bio = findViewById(R.id.publisherBio)
        statPacks = findViewById(R.id.publisherStatPacks)
        statFollowers = findViewById(R.id.publisherStatFollowers)
        statFollowing = findViewById(R.id.publisherStatFollowing)
        statLikes = findViewById(R.id.publisherStatLikes)
        followButton = findViewById(R.id.publisherFollowButton)
        packsContainer = findViewById(R.id.publisherPacksContainer)
        progress = findViewById(R.id.publisherProgress)
        emptyView = findViewById(R.id.publisherEmpty)

        displayName.text = publisherName.ifBlank { getString(R.string.publisher_default) }
        subtitle.text = getString(R.string.publisher_subtitle_loading)
        followButton.text = getString(R.string.follow)
        if (publisherPhoto.isNotBlank()) {
            Glide.with(this).load(publisherPhoto).circleCrop().placeholder(R.drawable.ic_person).into(avatar)
        } else {
            avatar.setImageResource(R.drawable.ic_person)
        }

        followButton.setOnClickListener { toggleFollow() }
        loadPublisherPacks()
    }

    private fun loadPublisherPacks() {
        progress.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        packsContainer.removeAllViews()

        lifecycleScope.launch {
            val profile = runCatching { withContext(Dispatchers.IO) { fetchPublisherProfile() } }.getOrElse { JSONObject() }
            val packs = parsePacks(profile)
            progress.visibility = View.GONE
            bindProfile(profile)
            if (packs.isEmpty()) {
                emptyView.visibility = View.VISIBLE
                subtitle.text = getString(R.string.publisher_subtitle_count, 0)
                return@launch
            }
            subtitle.text = getString(R.string.publisher_subtitle_count, packs.size)
            packs.forEach { pack -> packsContainer.addView(buildPackCard(pack)) }
        }
    }

    private data class PackSummary(
        val id: String,
        val name: String,
        val trayUrl: String,
        val downloadCount: Int,
        val favoriteCount: Int,
        val likeCount: Int,
        val commentCount: Int
    )

    private suspend fun fetchPublisherProfile(): JSONObject {
        val pack = Pack(
            id = "",
            name = "",
            pub = publisherName,
            email = publisherEmail,
            publisherPhotoUrl = publisherPhoto,
            publisherUserId = publisherId
        )
        return SocialRepository.fetchPublisherProfile(pack, this)
    }

    private fun bindProfile(profile: JSONObject) {
        val profileObject = profile.optJSONObject("profile") ?: JSONObject()
        val stats = profile.optJSONObject("stats") ?: JSONObject()
        publisherId = profileObject.optString("user_id", publisherId).ifBlank { publisherId }
        publisherEmail = profileObject.optString("email", publisherEmail).ifBlank { publisherEmail }
        publisherPhoto = profileObject.optString("photo_url", publisherPhoto).ifBlank { publisherPhoto }
        publisherName = profileObject.optString("display_name", publisherName).ifBlank { publisherName }
        isFollowing = profile.optBoolean("is_following", false)

        displayName.text = publisherName.ifBlank { getString(R.string.publisher_default) }
        bio.text = profileObject.optString("bio", "")
        bio.visibility = if (bio.text.isNullOrBlank()) View.GONE else View.VISIBLE
        subtitle.text = getString(R.string.publisher_subtitle_count, stats.optInt("packs", 0))
        statPacks.text = stats.optInt("packs", 0).toString()
        statFollowers.text = stats.optInt("followers", 0).toString()
        statFollowing.text = stats.optInt("following", 0).toString()
        statLikes.text = stats.optInt("likes", 0).toString()
        followButton.text = getString(if (isFollowing) R.string.following else R.string.follow)
        if (publisherPhoto.isNotBlank()) {
            Glide.with(this).load(publisherPhoto).circleCrop().placeholder(R.drawable.ic_person).into(avatar)
        }
    }

    private fun parsePacks(profile: JSONObject): List<PackSummary> {
        val packs = profile.optJSONArray("packs") ?: return emptyList()
        return (0 until packs.length()).mapNotNull { index ->
            val item = packs.optJSONObject(index) ?: return@mapNotNull null
            PackSummary(
                id = item.optString("id"),
                name = item.optString("name"),
                trayUrl = item.optString("tray_url"),
                downloadCount = item.optInt("download_count", 0),
                favoriteCount = item.optInt("favorite_count", 0),
                likeCount = item.optInt("like_count", 0),
                commentCount = item.optInt("comment_count", 0)
            )
        }
    }

    private fun toggleFollow() {
        followButton.isEnabled = false
        lifecycleScope.launch {
            runCatching {
                SocialRepository.toggleFollow(this@PublisherProfileActivity, publisherId, publisherEmail, publisherName, publisherPhoto)
            }.onSuccess { result ->
                isFollowing = result.optBoolean("following", !isFollowing)
                followButton.text = getString(if (isFollowing) R.string.following else R.string.follow)
                loadPublisherPacks()
            }.onFailure { error ->
                Toast.makeText(this@PublisherProfileActivity, error.message ?: "Follow failed", Toast.LENGTH_SHORT).show()
            }
            followButton.isEnabled = true
        }
    }

    private fun buildPackCard(pack: PackSummary): View {
        val card = layoutInflater.inflate(R.layout.item_publisher_pack, packsContainer, false)
        val tray = card.findViewById<ImageView>(R.id.packTray)
        val name = card.findViewById<TextView>(R.id.packName)
        val downloads = card.findViewById<TextView>(R.id.packDownloads)
        val engagement = card.findViewById<TextView>(R.id.packEngagement)
        Glide.with(this).load(pack.trayUrl).placeholder(R.drawable.ic_logo_white).into(tray)
        name.text = pack.name
        downloads.text = getString(R.string.publisher_pack_downloads, pack.downloadCount)
        engagement.text = "${pack.favoriteCount} fav / ${pack.likeCount} likes / ${pack.commentCount} comments"
        card.setOnClickListener {
            startActivity(Intent(this, DetailsActivity::class.java).putExtra("id", pack.id).putExtra("packId", pack.id))
        }
        return card
    }

    companion object {
        const val EXTRA_PUBLISHER_ID = "publisher_id"
        const val EXTRA_PUBLISHER_NAME = "publisher_name"
        const val EXTRA_PUBLISHER_PHOTO = "publisher_photo"
        const val EXTRA_PUBLISHER_EMAIL = "publisher_email"
    }
}