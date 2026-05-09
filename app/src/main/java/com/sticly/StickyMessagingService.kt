package com.sticly

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class StickyMessagingService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID = "sticky_notifications"
        const val CHANNEL_NAME = "Sticker Bildirimleri"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        PushTokenManager.syncToken(applicationContext, token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "Sticky"
        val body = remoteMessage.notification?.body ?: remoteMessage.data["body"] ?: ""
        val imageUrl = remoteMessage.data["imageUrl"]
        val msgType = remoteMessage.data["type"] ?: ""

        // Save submission-related and admin messages to in-app notifications list
        if (msgType.isNotEmpty() && body.isNotEmpty()) {
            saveToInAppNotifications(title, body, msgType)
        }

        // Bildirimler kapalıysa OS bildirimi gösterme
        if (!PreferencesHelper.isNotificationsEnabled(this)) {
            return
        }

        showNotification(title, body, imageUrl)
    }

    private fun saveToInAppNotifications(title: String, body: String, type: String) {
        val deviceId = PreferencesHelper.getDeviceId(this)
        val prefs = getSharedPreferences("sticky_prefs", MODE_PRIVATE)
        val email = prefs.getString("user_email", "") ?: ""
        val firebaseUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        val recipientId = deviceId.ifBlank { firebaseUid.ifBlank { email } }
        if (recipientId.isBlank()) return

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val payload = org.json.JSONObject().apply {
                    put("title", title)
                    put("body", body)
                    put("message", body)
                    put("user_id", recipientId)
                    put("type", type)
                    put("read", false)
                    put("from", "admin")
                    put("timestamp", java.time.Instant.now().toString())
                }
                val url = java.net.URL("${PocketBaseHelper.WORKER_URL}/api/notifications")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 8_000
                conn.outputStream.write(payload.toString().toByteArray())
                conn.connect()
                conn.disconnect()
            }
        }
    }

    private fun showNotification(title: String, body: String, imageUrl: String? = null) {
        createNotificationChannel()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Show exactly what was sent — no decorations, no extra hardcoded copy. The admin
        // panel composes the full title/body and any added emoji/locale text leaks into every
        // notification regardless of language.
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_sticky)
            .setContentTitle(title)
            .setContentText(body)
            .setColor(ContextCompat.getColor(this, R.color.primary))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_PROMO)

        if (!imageUrl.isNullOrEmpty()) {
            try {
                val url = java.net.URL(imageUrl)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.doInput = true
                connection.connect()
                val input = connection.inputStream
                val bitmap = BitmapFactory.decodeStream(input)

                builder.setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(bitmap)
                        .setBigContentTitle(title)
                        .setSummaryText(body)
                )
            } catch (e: Exception) {
                builder.setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(body)
                        .setBigContentTitle(title)
                )
            }
        } else {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(body)
                    .setBigContentTitle(title)
            )
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_desc)
                enableLights(true)
                lightColor = ContextCompat.getColor(this@StickyMessagingService, R.color.primary)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
