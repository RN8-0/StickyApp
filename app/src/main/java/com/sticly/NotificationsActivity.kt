package com.sticly

import android.os.Bundle
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotificationsActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    private data class Notif(
        val id: String,
        val title: String,
        val body: String,
        val read: Boolean,
        val dateLabel: String,
        val timestamp: Long,
        val category: String = "general",
        val type: String = "general",
        val actorId: String = "",
        val actorEmail: String = "",
        val actorName: String = "",
        val actorPhoto: String = ""
    )

    private val items = mutableListOf<Notif>()
    private lateinit var rv: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var emptyView: View
    private var deviceId = ""
    private var userId = ""
    private var userEmail = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)

        window.statusBarColor = ContextCompat.getColor(this, R.color.toolbar_bg)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.inflateMenu(R.menu.menu_notifications)
        toolbar.menu.findItem(R.id.action_clear_all)?.icon?.setTint(
            ContextCompat.getColor(this, R.color.toolbar_icon)
        )
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_clear_all) {
                confirmClearAll()
                true
            } else false
        }

        rv = findViewById(R.id.rvNotifications)
        progress = findViewById(R.id.notificationsProgress)
        emptyView = findViewById(R.id.notificationsEmpty)

        val swipeRefresh = findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefresh)
        swipeRefresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.primary))
        swipeRefresh.setOnRefreshListener {
            loadNotifications()
            swipeRefresh.isRefreshing = false
        }

        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = Adapter()

        loadNotifications()
    }

    private fun loadNotifications() {
        progress.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        rv.visibility = View.GONE

        deviceId = PreferencesHelper.getDeviceId(this)
        val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val prefs = getSharedPreferences("sticky_prefs", MODE_PRIVATE)
        userEmail = prefs.getString("user_email", "") ?: firebaseUser?.email.orEmpty()
        userId = firebaseUser?.uid.orEmpty()

        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) { fetchFromPocketBase(deviceId, userId, userEmail) }
            progress.visibility = View.GONE
            items.clear()
            items.addAll(list.map { it.copy(read = true) })
            if (items.isEmpty()) {
                emptyView.visibility = View.VISIBLE
                rv.visibility = View.GONE
            } else {
                emptyView.visibility = View.GONE
                rv.visibility = View.VISIBLE
                rv.adapter?.notifyDataSetChanged()
            }
            withContext(Dispatchers.IO) {
                list.filter { !it.read }.forEach { msg ->
                    runCatching {
                        PocketBaseHelper.updateRecord(
                            "notifications", msg.id,
                            org.json.JSONObject().put("read", true)
                        )
                    }
                }
            }
        }
    }

    private fun confirmClearAll() {
        if (items.isEmpty()) return
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.clear_all))
            .setMessage(getString(R.string.clear_all_confirm))
            .setPositiveButton(getString(R.string.clear_all)) { _, _ -> doClearAll() }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            // Oval corners on dialog window
            dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_rounded)

            // Button colors
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.setTextColor(
                ContextCompat.getColor(this, R.color.primary)
            )
            dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)?.setTextColor(
                ContextCompat.getColor(this, R.color.text_secondary)
            )
        }
        dialog.show()
    }

    private fun doClearAll() {
        lifecycleScope.launch {
            val idsToDelete = items.map { it.id }.toList()
            withContext(Dispatchers.IO) {
                idsToDelete.forEach { id ->
                    runCatching { PocketBaseHelper.deleteRecord("notifications", id) }
                }
            }
            items.clear()
            rv.adapter?.notifyDataSetChanged()
            emptyView.visibility = View.VISIBLE
            rv.visibility = View.GONE
            Toast.makeText(this@NotificationsActivity, getString(R.string.notifications_cleared), Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun fetchFromPocketBase(deviceId: String, userId: String, email: String): List<Notif> {
        return runCatching {
            // notifications schema fields: user_id, device_id, pack_id, body, title, etc.
            // There is NO 'email' field — using it causes HTTP 400 and returns nothing.
            // Admin panel stores recipientId in user_id: it can be a Firebase UID, an email, or a device_id.
            val filterParts = mutableListOf<String>()
            if (userId.isNotBlank()) filterParts.add("user_id='${userId.replace("'", "\\'")}'")
            if (email.isNotBlank()) filterParts.add("user_id='${email.replace("'", "\\'")}'")
            if (deviceId.isNotBlank()) {
                filterParts.add("user_id='${deviceId.replace("'", "\\'")}'")
                filterParts.add("device_id='${deviceId.replace("'", "\\'")}'")
            }
            // Admin broadcasts (sent to all users)
            filterParts.add("user_id='broadcast'")
            val filter = filterParts.distinct().joinToString(" || ")
            val records = PocketBaseHelper.listRecords("notifications", filter = filter, perPage = 200)
            records.map { record ->
                val timestamp = record.optString("created").ifBlank { record.optString("updated", "") }
                val date = parseTimestamp(timestamp)
                val topic = record.optString("topic", record.optString("type", "general"))
                val data = notificationData(record)
                val actorName = data.optString("actor_name", record.optString("from", ""))
                val packName = data.optString("pack_name", "")
                val rawBody = record.optString("body", record.optString("message", ""))
                val rawTitle = record.optString("title", getString(R.string.profile_notifications_title))
                val (title, body) = localizedNotificationText(topic, actorName, packName, rawTitle, rawBody)
                Notif(
                    id = record.optString("id"),
                    title = title,
                    body = body,
                    read = record.optBoolean("read", false),
                    dateLabel = date?.let {
                        java.text.SimpleDateFormat(
                            "MMM d, yyyy · HH:mm",
                            java.util.Locale.getDefault()
                        ).format(it)
                    }.orEmpty(),
                    timestamp = date?.time ?: 0L,
                    category = detectCategory(topic, title, body),
                    type = topic,
                    actorId = data.optString("actor_id"),
                    actorEmail = data.optString("actor_email"),
                    actorName = actorName,
                    actorPhoto = data.optString("actor_photo")
                )
            }.sortedByDescending { it.timestamp }
                // A single like/follow is stored once per owner identity key (user_id, email,
                // device_id, …), so a recipient matching several keys would otherwise see the same
                // event multiple times. Collapse records that represent the same event.
                .distinctBy { "${it.type}|${it.actorId}|${it.actorEmail}|${it.body}" }
        }.getOrElse { emptyList() }
    }

    private fun notificationData(record: org.json.JSONObject): org.json.JSONObject {
        val value = record.opt("data")
        return when (value) {
            is org.json.JSONObject -> value
            is String -> runCatching { org.json.JSONObject(value) }.getOrDefault(org.json.JSONObject())
            else -> org.json.JSONObject()
        }
    }

    private fun localizedNotificationText(type: String, actorName: String, packName: String, fallbackTitle: String, fallbackBody: String): Pair<String, String> {
        val actor = actorName.ifBlank { getString(R.string.publisher_default) }
        return when (type) {
            "social_follow" -> getString(R.string.notification_follow_title) to getString(R.string.notification_follow_body, actor)
            "pack_like" -> getString(R.string.notification_pack_like_title) to getString(R.string.notification_pack_like_body, actor, packName.ifBlank { getString(R.string.sticker_pack) })
            else -> fallbackTitle to fallbackBody
        }
    }

    private fun parseTimestamp(value: String): java.util.Date? {
        if (value.isBlank()) return null
        return try {
            java.util.Date.from(java.time.Instant.parse(value))
        } catch (_: Exception) {
            try {
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSXXX", java.util.Locale.US).parse(value)
            } catch (_: Exception) {
                try {
                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS'Z'", java.util.Locale.US).parse(value)
                } catch (_: Exception) {
                    try {
                        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US).parse(value)
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        }
    }

    private fun detectCategory(type: String, title: String, body: String): String {
        if (type == "social_follow" || type == "pack_like") return "social"
        val text = "$title $body".lowercase()
        return when {
            text.contains("update") || text.contains("new version") || text.contains("güncelleme") -> "update"
            text.contains("approve") || text.contains("approved") || text.contains("onay") -> "approval"
            text.contains("submission") || text.contains("gönderi") || text.contains("pack") -> "pack"
            text.contains("new") || text.contains("yeni") || text.contains("fresh") || text.contains("alert") -> "new_content"
            text.contains("premium") || text.contains("pro") -> "premium"
            text.contains("offer") || text.contains("deal") || text.contains("sale") || text.contains("indirim") -> "promo"
            else -> "general"
        }
    }

    private inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {
        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.tvMsgTitle)
            val body: TextView = view.findViewById(R.id.tvMsgBody)
            val date: TextView = view.findViewById(R.id.tvMsgDate)
            val icon: ImageView = view.findViewById(R.id.ivMsgIcon)
            val unreadDot: View = view.findViewById(R.id.vUnreadDot)
            val category: TextView = view.findViewById(R.id.tvMsgCategory)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_admin_message, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val msg = items[position]
            holder.title.text = msg.title
            holder.body.text = msg.body
            holder.date.text = msg.dateLabel

            // Read state
            holder.itemView.alpha = 1f
            holder.unreadDot.visibility = if (msg.read) View.GONE else View.VISIBLE

            // Category icon & label
            val ctx = holder.itemView.context
            when (msg.category) {
                "update" -> {
                    holder.icon.setImageResource(R.drawable.ic_menu)
                    holder.icon.backgroundTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.primary_light))
                    holder.icon.setColorFilter(ContextCompat.getColor(ctx, R.color.primary))
                    holder.category.text = getString(R.string.category_update)
                }
                "approval" -> {
                    holder.icon.setImageResource(R.drawable.ic_notification)
                    holder.icon.backgroundTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.success) and 0xFFFFFF or 0x20000000.toInt())
                    holder.icon.setColorFilter(ContextCompat.getColor(ctx, R.color.success))
                    holder.category.text = getString(R.string.category_approval)
                }
                "new_content" -> {
                    holder.icon.setImageResource(R.drawable.ic_menu)
                    holder.icon.backgroundTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.warning) and 0xFFFFFF or 0x20000000.toInt())
                    holder.icon.setColorFilter(ContextCompat.getColor(ctx, R.color.warning))
                    holder.category.text = getString(R.string.category_new_content)
                }
                "premium" -> {
                    holder.icon.setImageResource(R.drawable.ic_menu)
                    holder.icon.backgroundTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(ctx, android.graphics.Color.parseColor("#FFD700")) and 0xFFFFFF or 0x25000000.toInt())
                    holder.icon.setColorFilter(android.graphics.Color.parseColor("#DAA520"))
                    holder.category.text = getString(R.string.category_premium)
                }
                "promo" -> {
                    holder.icon.setImageResource(R.drawable.ic_menu)
                    holder.icon.backgroundTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.danger) and 0xFFFFFF or 0x20000000.toInt())
                    holder.icon.setColorFilter(ContextCompat.getColor(ctx, R.color.danger))
                    holder.category.text = getString(R.string.category_promo)
                }
                "pack" -> {
                    holder.icon.setImageResource(R.drawable.ic_menu)
                    holder.icon.backgroundTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.primary_light))
                    holder.icon.setColorFilter(ContextCompat.getColor(ctx, R.color.primary))
                    holder.category.text = getString(R.string.category_pack)
                }
                "social" -> {
                    holder.icon.setImageResource(R.drawable.ic_person)
                    holder.icon.backgroundTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.primary_light))
                    holder.icon.setColorFilter(ContextCompat.getColor(ctx, R.color.primary))
                    holder.category.text = getString(R.string.notification_category_social)
                }
                else -> {
                    holder.icon.setImageResource(R.drawable.ic_notification)
                    holder.icon.backgroundTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.chip_bg))
                    holder.icon.setColorFilter(ContextCompat.getColor(ctx, R.color.text_secondary))
                    holder.category.text = getString(R.string.category_general)
                }
            }
            holder.category.visibility = View.VISIBLE
            holder.itemView.setOnClickListener {
                if (msg.actorId.isBlank() && msg.actorEmail.isBlank() && msg.actorName.isBlank()) return@setOnClickListener
                startActivity(Intent(this@NotificationsActivity, PublisherProfileActivity::class.java).apply {
                    putExtra(PublisherProfileActivity.EXTRA_PUBLISHER_ID, msg.actorId.ifBlank { msg.actorEmail })
                    putExtra(PublisherProfileActivity.EXTRA_PUBLISHER_EMAIL, msg.actorEmail)
                    putExtra(PublisherProfileActivity.EXTRA_PUBLISHER_NAME, msg.actorName)
                    putExtra(PublisherProfileActivity.EXTRA_PUBLISHER_PHOTO, msg.actorPhoto)
                })
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
