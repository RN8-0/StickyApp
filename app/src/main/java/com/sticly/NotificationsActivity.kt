package com.sticly

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

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
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_clear_all) {
                confirmClearAll()
                true
            } else false
        }

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

        deviceId = PreferencesHelper.getDeviceId(this)
        val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val prefs = getSharedPreferences("sticky_prefs", MODE_PRIVATE)
        userEmail = prefs.getString("user_email", "") ?: firebaseUser?.email.orEmpty()
        userId = firebaseUser?.uid.orEmpty()

        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) { fetchFromWorker(deviceId, userId, userEmail) }
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
            withContext(Dispatchers.IO) {
                list.filter { !it.read }.forEach { msg ->
                    runCatching {
                        workerPatch(
                            "${PocketBaseHelper.WORKER_URL}/api/notifications/${msg.id}",
                            "{\"read\":true}"
                        )
                    }
                }
            }
        }
    }

    private fun confirmClearAll() {
        if (items.isEmpty()) return
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.clear_all_confirm))
            .setPositiveButton(getString(R.string.clear_all)) { _, _ -> doClearAll() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun doClearAll() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val params = buildQueryParams(deviceId, userId, userEmail)
                    if (params.isNotEmpty()) {
                        val conn = URL("${PocketBaseHelper.WORKER_URL}/api/notifications?$params")
                            .openConnection() as HttpURLConnection
                        conn.requestMethod = "DELETE"
                        conn.connectTimeout = 10_000
                        conn.readTimeout = 10_000
                        conn.connect()
                        conn.disconnect()
                    }
                }
            }
            items.clear()
            rv.adapter?.notifyDataSetChanged()
            emptyView.visibility = View.VISIBLE
            rv.visibility = View.GONE
            Toast.makeText(this@NotificationsActivity, getString(R.string.notifications_cleared), Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun fetchFromWorker(deviceId: String, userId: String, email: String): List<Notif> {
        return runCatching {
            val params = buildQueryParams(deviceId, userId, email)
            if (params.isEmpty()) return@runCatching emptyList()
            val conn = URL("${PocketBaseHelper.WORKER_URL}/api/notifications?$params")
                .openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.connect()
            if (conn.responseCode != 200) {
                conn.disconnect()
                return@runCatching emptyList()
            }
            val json = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { i ->
                val record = arr.getJSONObject(i)
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
            }.sortedByDescending { it.timestamp }
        }.getOrElse { emptyList() }
    }

    private fun buildQueryParams(deviceId: String, userId: String, email: String): String {
        val parts = mutableListOf<String>()
        if (deviceId.isNotBlank()) parts.add("deviceId=${URLEncoder.encode(deviceId, "UTF-8")}")
        if (userId.isNotBlank()) parts.add("userId=${URLEncoder.encode(userId, "UTF-8")}")
        if (email.isNotBlank()) parts.add("email=${URLEncoder.encode(email, "UTF-8")}")
        return parts.joinToString("&")
    }

    private fun workerPatch(url: String, body: String) {
        runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "PATCH"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 8_000
            conn.outputStream.write(body.toByteArray())
            conn.connect()
            conn.disconnect()
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
