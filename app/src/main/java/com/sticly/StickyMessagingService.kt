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

class StickyMessagingService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID = "sticky_notifications"
        const val CHANNEL_NAME = "Sticker Bildirimleri"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Token'ı Firebase'e kaydedebilirsiniz
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Bildirimler kapalıysa gösterme
        if (!PreferencesHelper.isNotificationsEnabled(this)) {
            return
        }

        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "Sticky"
        val body = remoteMessage.notification?.body ?: remoteMessage.data["body"] ?: ""
        val imageUrl = remoteMessage.data["imageUrl"]

        showNotification(title, body, imageUrl)
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

        // Süslü başlık ve mesaj oluştur
        val decoratedTitle = "🎉 $title ✨"
        val decoratedBody = "🌟 $body\n\n💫 Hemen keşfet ve arkadaşlarınla paylaş!"

        // Büyük ikon için bitmap
        val largeIcon = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_sticky) // Changed to use the new Sticky logo silhouette
            .setLargeIcon(largeIcon)
            .setContentTitle(decoratedTitle)
            .setContentText(body)
            .setColor(ContextCompat.getColor(this, R.color.primary))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_PROMO)

        // Eğer resim URL'si varsa "Fancy" notification (BigPictureStyle) yap
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
                        .setBigContentTitle(decoratedTitle)
                        .setSummaryText(decoratedBody)
                )
            } catch (e: Exception) {
                // Resim yüklenemezse klasik stile dön
                builder.setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(decoratedBody)
                        .setBigContentTitle(decoratedTitle)
                        .setSummaryText("Sticky Stickers")
                )
            }
        } else {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(decoratedBody)
                    .setBigContentTitle(decoratedTitle)
                    .setSummaryText("Sticky Stickers")
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
                description = "Yeni sticker paketleri hakkında bildirimler"
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
