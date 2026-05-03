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
                val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                val isAuthenticated = firebaseUser != null && !firebaseUser.email.isNullOrBlank()
                val payload = JSONObject().apply {
                    put("fcm_token", token)
                    put("notifications_enabled", PreferencesHelper.isNotificationsEnabled(context))
                    put("push_provider", "fcm")
                }

                // Only sync (and possibly create) when authenticated. For unauthenticated users
                // we just update existing records if present — never create new guest profiles.
                syncCollectionSafely("users", "device_id='$deviceId'", payload, isAuthenticated)
                syncCollectionSafely("user_profiles", "device_id='$deviceId'", payload, isAuthenticated)
            } catch (error: Exception) {
                Log.w(TAG, "FCM token sync failed: ${error.message}")
            }
        }
    }

    private suspend fun syncCollectionSafely(collection: String, filter: String, payload: JSONObject, allowCreate: Boolean) {
        try {
            syncCollection(collection, filter, payload, allowCreate)
        } catch (error: Exception) {
            Log.w(TAG, "FCM token sync failed for $collection: ${error.message}")
        }
    }

    private suspend fun syncCollection(collection: String, filter: String, payload: JSONObject, allowCreate: Boolean) {
        if (collection == "users") {
            val authRecordId = PocketBaseHelper.getAuthRecordId()
            if (!authRecordId.isNullOrBlank()) {
                PocketBaseHelper.updateRecord(collection, authRecordId, payload)
            }
            return
        }

        val deviceId = filter.substringAfter("'").substringBefore("'")
        val email = "$deviceId@device.sticly.local"
        val escapedEmail = email.replace("'", "\\'")

        // Search by BOTH device_id and email in one query — covers all duplicate scenarios
        val combinedFilter = "device_id='$deviceId' || email='$escapedEmail'"
        val records = PocketBaseHelper.listRecords(collection, filter = combinedFilter, perPage = 50)

        if (records.isNotEmpty()) {
            // Update the first one, delete extras to prevent duplicates
            val updatePayload = JSONObject(payload.toString()).apply {
                put("device_id", deviceId)
                if (collection != "users") {
                    put("user_id", deviceId)
                    put("provider", "device")
                    put("platform", "android")
                }
            }
            PocketBaseHelper.updateRecord(collection, records[0].getString("id"), updatePayload)
            // Cleanup: delete all duplicates beyond the first
            for (i in 1 until records.size) {
                runCatching {
                    PocketBaseHelper.deleteRecord(collection, records[i].getString("id"))
                }.onFailure { Log.w(TAG, "Failed to delete duplicate record: ${it.message}") }
            }
            return
        }

        // No existing record. Only create if explicitly allowed (i.e., user is authenticated).
        if (!allowCreate) {
            Log.d(TAG, "Skipping guest record creation for $collection (not authenticated)")
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
            // Race condition: another call created it. Re-fetch and update.
            val retry = PocketBaseHelper.listRecords(collection, filter = combinedFilter, perPage = 1)
            if (retry.isNotEmpty()) {
                PocketBaseHelper.updateRecord(collection, retry[0].getString("id"), payload)
            } else {
                throw error
            }
        }
    }
}
