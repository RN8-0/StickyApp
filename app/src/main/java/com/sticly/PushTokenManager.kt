package com.sticly

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

object PushTokenManager {
    private const val TAG = "PushTokenManager"

    fun refreshAndSync(context: Context) {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> syncToken(context, token) }
            .addOnFailureListener { error -> Log.w(TAG, "FCM token fetch failed: ${error.message}") }
    }

    fun syncToken(context: Context, token: String) {
        if (token.isBlank()) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val deviceId = PreferencesHelper.getDeviceId(context)
                val payload = JSONObject().apply {
                    put("fcm_token", token)
                    put("notifications_enabled", PreferencesHelper.isNotificationsEnabled(context))
                    put("push_provider", "fcm")
                }

                syncCollectionSafely("users", "device_id='$deviceId'", payload)
                syncCollectionSafely("user_profiles", "device_id='$deviceId'", payload)
            } catch (error: Exception) {
                Log.w(TAG, "FCM token sync failed: ${error.message}")
            }
        }
    }

    private suspend fun syncCollectionSafely(collection: String, filter: String, payload: JSONObject) {
        try {
            syncCollection(collection, filter, payload)
        } catch (error: Exception) {
            Log.w(TAG, "FCM token sync failed for $collection: ${error.message}")
        }
    }

    private suspend fun syncCollection(collection: String, filter: String, payload: JSONObject) {
        if (collection == "users") {
            val authRecordId = PocketBaseHelper.getAuthRecordId()
            if (!authRecordId.isNullOrBlank()) {
                PocketBaseHelper.updateRecord(collection, authRecordId, payload)
            }
            return
        }

        val records = PocketBaseHelper.listRecords(collection, filter = filter, perPage = 1)
        if (records.isNotEmpty()) {
            PocketBaseHelper.updateRecord(collection, records[0].getString("id"), payload)
            return
        }

        val deviceId = filter.substringAfter("'").substringBefore("'")
        val email = "$deviceId@device.sticly.local"
        val escapedEmail = email.replace("'", "\\'")
        val existingByEmail = PocketBaseHelper.listRecords(collection, filter = "email='$escapedEmail'", perPage = 1)
        if (existingByEmail.isNotEmpty()) {
            val updatePayload = JSONObject(payload.toString()).apply {
                put("device_id", deviceId)
                put("email", email)
                put("display_name", "Guest")
                if (collection != "users") {
                    put("user_id", deviceId)
                    put("provider", "device")
                    put("platform", "android")
                }
            }
            PocketBaseHelper.updateRecord(collection, existingByEmail[0].getString("id"), updatePayload)
            return
        }

        val createPayload = JSONObject(payload.toString()).apply {
            put("device_id", deviceId)
            put("email", email)
            put("display_name", "Guest")
            if (collection == "users") {
                val password = java.util.UUID.randomUUID().toString()
                put("password", password)
                put("passwordConfirm", password)
            } else {
                put("user_id", deviceId)
                put("provider", "device")
                put("platform", "android")
            }
        }

        try {
            PocketBaseHelper.createRecord(collection, createPayload)
        } catch (error: Exception) {
            throw error
        }
    }
}
