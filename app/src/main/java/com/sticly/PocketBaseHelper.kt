package com.sticly

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * PocketBase REST API helper for StickyApp
 */
object PocketBaseHelper {

    private const val TAG = "PocketBase"
    const val PB_URL = "https://sh3xlf9j7symlj3otlw6s8rx.46.225.95.201.sslip.io"
    const val WORKER_URL = "https://sticky-worker.46.225.95.201.sslip.io"
    const val NTFY_URL = "https://sticky-ntfy.46.225.95.201.sslip.io"

    private var authToken: String? = null
    private var authRecordId: String? = null

    private fun authHeaderValue(): String? {
        val token = authToken?.takeIf { it.isNotBlank() } ?: return null
        return if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"
    }

    data class UploadFile(
        val fieldName: String,
        val fileName: String,
        val mimeType: String,
        val bytes: ByteArray
    )

    fun setAuthToken(token: String?) {
        authToken = token
        if (token == null) authRecordId = null
    }

    fun getToken(): String? = authToken

    fun getAuthRecordId(): String? = authRecordId

    /**
     * Auth with OAuth2 provider (e.g., Google)
     */
    suspend fun authWithOAuth(provider: String, idToken: String): JSONObject = withContext(Dispatchers.IO) {
        val normalizedProvider = provider.lowercase()
        if (normalizedProvider == "google") {
            return@withContext authWithGoogleFallback(idToken)
        }

        val body = JSONObject().apply {
            put("provider", normalizedProvider)
            put("idToken", idToken)
        }
        try {
            val result = post("/api/collections/users/auth-with-oauth2", body)
            authToken = result.optString("token").takeIf { it.isNotBlank() }
            authRecordId = result.optJSONObject("record")?.optString("id")?.takeIf { it.isNotBlank() }
            result
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * GET request to PocketBase
     */
    suspend fun get(path: String): JSONObject = withContext(Dispatchers.IO) {
        val conn = (URL("$PB_URL$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Content-Type", "application/json")
            authHeaderValue()?.let { setRequestProperty("Authorization", it) }
            connectTimeout = 15000
            readTimeout = 15000
        }
        try {
            val response = conn.inputStream.bufferedReader().readText()
            JSONObject(response)
        } catch (e: Exception) {
            val errorBody = try { conn.errorStream?.bufferedReader()?.readText() } catch (_: Exception) { null }
            Log.e(TAG, "GET $path failed: ${e.message} | $errorBody")
            throw e
        } finally {
            conn.disconnect()
        }
    }

    /**
     * POST request to PocketBase
     */
    suspend fun post(path: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val conn = (URL("$PB_URL$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            authHeaderValue()?.let { setRequestProperty("Authorization", it) }
            connectTimeout = 15000
            readTimeout = 15000
        }
        try {
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
            val response = conn.inputStream.bufferedReader().readText()
            JSONObject(response)
        } catch (e: Exception) {
            val errorBody = try { conn.errorStream?.bufferedReader()?.readText() } catch (_: Exception) { null }
            Log.e(TAG, "POST $path failed: ${e.message} | $errorBody")
            throw e
        } finally {
            conn.disconnect()
        }
    }

    /**
     * PATCH request to PocketBase
     */
    suspend fun patch(path: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val conn = (URL("$PB_URL$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "PATCH"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            authHeaderValue()?.let { setRequestProperty("Authorization", it) }
            connectTimeout = 15000
            readTimeout = 15000
        }
        try {
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
            val response = conn.inputStream.bufferedReader().readText()
            JSONObject(response)
        } catch (e: Exception) {
            val errorBody = try { conn.errorStream?.bufferedReader()?.readText() } catch (_: Exception) { null }
            Log.e(TAG, "PATCH $path failed: ${e.message} | $errorBody")
            throw e
        } finally {
            conn.disconnect()
        }
    }

    /**
     * DELETE request to PocketBase
     */
    suspend fun delete(path: String): Boolean = withContext(Dispatchers.IO) {
        val conn = (URL("$PB_URL$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "DELETE"
            authHeaderValue()?.let { setRequestProperty("Authorization", it) }
            connectTimeout = 15000
            readTimeout = 15000
        }
        try {
            conn.responseCode in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "DELETE $path failed: ${e.message}")
            false
        } finally {
            conn.disconnect()
        }
    }

    /**
     * List all records from a collection
     */
    suspend fun listRecords(collection: String, filter: String? = null, perPage: Int = 500, page: Int = 1): List<JSONObject> {
        val params = mutableListOf("perPage=$perPage", "page=$page")
        filter?.let { params.add("filter=${java.net.URLEncoder.encode(it, "UTF-8")}") }
        val query = params.joinToString("&")
        val result = get("/api/collections/$collection/records?$query")
        val items = result.optJSONArray("items") ?: JSONArray()
        return (0 until items.length()).map { items.getJSONObject(it) }
    }

    suspend fun listAllRecords(collection: String, filter: String? = null, perPage: Int = 500): List<JSONObject> {
        val allItems = mutableListOf<JSONObject>()
        var page = 1
        while (true) {
            val params = mutableListOf("perPage=$perPage", "page=$page")
            filter?.let { params.add("filter=${java.net.URLEncoder.encode(it, "UTF-8")}") }
            val query = params.joinToString("&")
            val result = get("/api/collections/$collection/records?$query")
            val items = result.optJSONArray("items") ?: JSONArray()
            for (i in 0 until items.length()) allItems.add(items.getJSONObject(i))
            val totalPages = result.optInt("totalPages", page)
            if (page >= totalPages || items.length() == 0) break
            page++
        }
        return allItems
    }

    /**
     * Get a single record
     */
    suspend fun getRecord(collection: String, id: String): JSONObject {
        return get("/api/collections/$collection/records/$id")
    }

    /**
     * Create a new record
     */
    suspend fun createRecord(collection: String, data: JSONObject): JSONObject {
        return post("/api/collections/$collection/records", data)
    }

    suspend fun createMultipartRecord(
        collection: String,
        fields: Map<String, String>,
        files: List<UploadFile>
    ): JSONObject = withContext(Dispatchers.IO) {
        val boundary = "----StickyBoundary${System.currentTimeMillis()}"
        val conn = (URL("$PB_URL/api/collections/$collection/records").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            authHeaderValue()?.let { setRequestProperty("Authorization", it) }
            connectTimeout = 30_000
            readTimeout = 60_000
        }

        try {
            conn.outputStream.use { os ->
                val writer = os.bufferedWriter()

                fields.forEach { (key, value) ->
                    writer.write("--$boundary\r\n")
                    writer.write("Content-Disposition: form-data; name=\"$key\"\r\n\r\n")
                    writer.write(value)
                    writer.write("\r\n")
                }

                files.forEach { file ->
                    writer.write("--$boundary\r\n")
                    writer.write("Content-Disposition: form-data; name=\"${file.fieldName}\"; filename=\"${file.fileName}\"\r\n")
                    writer.write("Content-Type: ${file.mimeType}\r\n\r\n")
                    writer.flush()
                    os.write(file.bytes)
                    os.write("\r\n".toByteArray())
                }

                writer.write("--$boundary--\r\n")
                writer.flush()
            }

            val responseCode = conn.responseCode
            val body = (if (responseCode in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()
                ?.readText()
                .orEmpty()

            if (responseCode !in 200..299) {
                Log.e(TAG, "MULTIPART POST /api/collections/$collection/records failed: $responseCode | $body")
                throw IllegalStateException(body.ifBlank { "Multipart upload failed with $responseCode" })
            }

            JSONObject(body)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Update a record
     */
    suspend fun updateRecord(collection: String, id: String, data: JSONObject): JSONObject {
        return patch("/api/collections/$collection/records/$id", data)
    }

    /**
     * Delete a record
     */
    suspend fun deleteRecord(collection: String, id: String): Boolean {
        return delete("/api/collections/$collection/records/$id")
    }

    /**
     * Auth with password (for users collection)
     */
    suspend fun authWithPassword(collection: String, identity: String, password: String): JSONObject {
        val body = JSONObject().apply {
            put("identity", identity)
            put("password", password)
        }
        val result = post("/api/collections/$collection/auth-with-password", body)
        authToken = result.optString("token").takeIf { it.isNotBlank() }
        authRecordId = result.optJSONObject("record")?.optString("id")?.takeIf { it.isNotBlank() }
        return result
    }

    /**
     * Get file URL for a record's file field
     */
    fun getFileUrl(collection: String, recordId: String, filename: String): String {
        return "$PB_URL/api/files/$collection/$recordId/$filename"
    }

    private suspend fun authWithGoogleFallback(idToken: String): JSONObject = withContext(Dispatchers.IO) {
        val claims = fetchGoogleTokenInfo(idToken)
        val email = claims.optString("email").trim()
        val subject = claims.optString("sub").trim()
        val name = claims.optString("name").trim()
        val picture = claims.optString("picture").trim()

        if (email.isEmpty() || subject.isEmpty()) {
            throw IllegalArgumentException("Google token missing email or subject")
        }

        val password = "g_$subject"

        try {
            return@withContext authWithPassword("users", email, password)
        } catch (_: Exception) {
            // Continue with create-or-repair flow below.
        }

        val escapedEmail = email.replace("'", "\\'")
        val existing = try {
            listRecords("users", filter = "email='$escapedEmail'", perPage = 1)
        } catch (e: Exception) {
            emptyList()
        }

        val baseAuthData = JSONObject().apply {
            put("email", email)
            put("password", password)
            put("passwordConfirm", password)
        }

        val profileData = JSONObject(baseAuthData.toString()).apply {
            put("name", name)
            put("display_name", name)
            put("photo_url", picture)
            put("uid", subject)
        }

        if (existing.isEmpty()) {
            try {
                createRecord("users", profileData)
            } catch (e: Exception) {
                Log.w(TAG, "Google profile fields unavailable during create, retrying minimal user: ${e.message}")
                createRecord("users", baseAuthData)
            }
        } else {
            val recordId = existing.first().optString("id")
            if (recordId.isNotEmpty()) {
                try {
                    updateRecord("users", recordId, profileData)
                } catch (e: Exception) {
                    try {
                        updateRecord("users", recordId, baseAuthData)
                    } catch (repairError: Exception) {
                        Log.w(TAG, "Could not repair existing Google user: ${repairError.message}")
                    }
                }
            }
        }

        val result = authWithPassword("users", email, password)
        val recordId = result.optJSONObject("record")?.optString("id").orEmpty()
        authRecordId = recordId.takeIf { it.isNotEmpty() }
        if (recordId.isNotEmpty()) {
            try {
                updateRecord("users", recordId, JSONObject().apply {
                    put("name", name)
                    put("display_name", name)
                    put("photo_url", picture)
                    put("uid", subject)
                })
            } catch (e: Exception) {
                Log.w(TAG, "Google profile sync skipped: ${e.message}")
            }
        }
        result
    }

    private fun fetchGoogleTokenInfo(idToken: String): JSONObject {
        val encoded = java.net.URLEncoder.encode(idToken, "UTF-8")
        val conn = (URL("https://oauth2.googleapis.com/tokeninfo?id_token=$encoded").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        return try {
            val responseCode = conn.responseCode
            val body = (if (responseCode in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()
                ?.readText()
                .orEmpty()
            if (responseCode !in 200..299) {
                throw IllegalStateException(body.ifBlank { "Google token validation failed with $responseCode" })
            }
            JSONObject(body)
        } finally {
            conn.disconnect()
        }
    }
}
