package com.sticly

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
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
        val timestamp: Long
    )

    private val items = mutableListOf<Notif>()
    private lateinit var rv: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var emptyView: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)

        window.statusBarColor = ContextCompat.getColor(this, R.color.toolbar_bg)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        rv = findViewById(R.id.rvNotifications)
        progress = findViewById(R.id.notificationsProgress)
        emptyView = findViewById(R.id.notificationsEmpty)

        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = Adapter()

        loadNotifications()
    }

    private fun loadNotifications() {
        progress.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        rv.visibility = View.GONE

        val deviceId = PreferencesHelper.getDeviceId(this)
        val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val prefs = getSharedPreferences("sticky_prefs", MODE_PRIVATE)
        val email = prefs.getString("user_email", "") ?: firebaseUser?.email.orEmpty()
        val userId = firebaseUser?.uid.orEmpty()

        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) { fetch(deviceId, userId, email) }
            progress.visibility = View.GONE
            items.clear()
            items.addAll(list)
            if (items.isEmpty()) {
                emptyView.visibility = View.VISIBLE
                rv.visibility = View.GONE
            } else {
                emptyView.visibility = View.GONE
                rv.visibility = View.VISIBLE
                rv.adapter?.notifyDataSetChanged()
            }
            // Mark unread as read on the server
            withContext(Dispatchers.IO) {
                list.filter { !it.read }.forEach { msg ->
                    runCatching {
                        PocketBaseHelper.updateRecord(
                            "notifications",
                            msg.id,
                            org.json.JSONObject().put("read", true)
                        )
                    }
                }
            }
        }
    }

    private suspend fun fetch(deviceId: String, userId: String, email: String): List<Notif> {
        fun escape(value: String) = value.replace("'", "\\'")
        val filters = listOf(userId, email, deviceId)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" || ") { "user_id='${escape(it)}'" }
        if (filters.isBlank()) return emptyList()
        return runCatching {
            PocketBaseHelper.listRecords("notifications", filter = filters, perPage = 100)
                .map { record ->
                    val timestamp = record.optString("timestamp", record.optString("created", ""))
                    val date = parseTimestamp(timestamp)
                    Notif(
                        id = record.optString("id"),
                        title = record.optString("title", getString(R.string.profile_notifications_title)),
                        body = record.optString("body", record.optString("message", "")),
                        read = record.optBoolean("read", false),
                        dateLabel = date?.let {
                            java.text.SimpleDateFormat(
                                "MMM d, yyyy · HH:mm",
                                java.util.Locale.getDefault()
                            ).format(it)
                        }.orEmpty(),
                        timestamp = date?.time ?: 0L
                    )
                }
                .sortedByDescending { it.timestamp }
        }.getOrElse { emptyList() }
    }

    private fun parseTimestamp(value: String): java.util.Date? {
        if (value.isBlank()) return null
        return try {
            java.util.Date.from(java.time.Instant.parse(value))
        } catch (_: Exception) {
            try {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSXXX", java.util.Locale.US)
                sdf.parse(value)
            } catch (_: Exception) {
                null
            }
        }
    }

    private inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {
        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.tvMsgTitle)
            val body: TextView = view.findViewById(R.id.tvMsgBody)
            val date: TextView = view.findViewById(R.id.tvMsgDate)
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
            holder.itemView.alpha = if (msg.read) 0.75f else 1f
        }

        override fun getItemCount(): Int = items.size
    }
}
