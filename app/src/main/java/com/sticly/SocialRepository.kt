package com.sticly

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object SocialRepository {
    data class CurrentUser(
        val id: String,
        val email: String,
        val name: String,
        val photoUrl: String
    )

    fun currentUser(context: Context): CurrentUser {
        val prefs = context.getSharedPreferences("sticky_prefs", Context.MODE_PRIVATE)
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        val id = firebaseUser?.uid?.takeIf { it.isNotBlank() } ?: PreferencesHelper.getDeviceId(context)
        val email = prefs.getString("user_email", null)?.takeIf { it.isNotBlank() } ?: firebaseUser?.email.orEmpty()
        val name = prefs.getString("user_display_name", null)?.takeIf { it.isNotBlank() }
            ?: firebaseUser?.displayName.orEmpty()
        val photo = prefs.getString("user_photo_url", null)?.takeIf { it.isNotBlank() }
            ?: firebaseUser?.photoUrl?.toString().orEmpty()
        return CurrentUser(id, email, name, photo)
    }

    fun isSignedIn(context: Context): Boolean {
        val prefs = context.getSharedPreferences("sticky_prefs", Context.MODE_PRIVATE)
        return FirebaseAuth.getInstance().currentUser != null || !prefs.getString("user_email", "")?.trim().isNullOrEmpty()
    }

    private fun requireSignedIn(context: Context) {
        if (!isSignedIn(context)) throw IllegalStateException("Sign in required")
    }

    suspend fun fetchPublisherProfile(pack: Pack, context: Context): JSONObject = withContext(Dispatchers.IO) {
        val viewer = currentUser(context)
        val language = PreferencesHelper.getLanguage(context)
        val params = linkedMapOf(
            "publisherId" to pack.publisherUserId,
            "publisherEmail" to pack.email,
            "publisherName" to pack.pub,
            "publisherPhoto" to pack.publisherPhotoUrl,
            "viewerId" to viewer.id,
            "viewerEmail" to viewer.email,
            "lang" to language
        )
        runCatching {
            val response = getJson("/api/social/profile", params)
            if (response.optJSONObject("stats")?.optInt("packs", 0) == 0) {
                runCatching { fetchPublisherProfileFromPocketBase(pack, viewer, language) }.getOrDefault(response)
            } else response
        }.getOrElse { fetchPublisherProfileFromPocketBase(pack, viewer, language) }
    }

    suspend fun toggleFollow(context: Context, targetId: String, targetEmail: String, targetName: String, targetPhoto: String): JSONObject = withContext(Dispatchers.IO) {
        val viewer = currentUser(context)
        postJson("/api/social/follow", JSONObject().apply {
            put("follower_id", viewer.id)
            put("follower_email", viewer.email)
            put("follower_name", viewer.name)
            put("follower_photo", viewer.photoUrl)
            put("target_id", targetId)
            put("target_email", targetEmail)
            put("target_name", targetName)
            put("target_photo", targetPhoto)
        })
    }

    suspend fun fetchComments(packId: String): JSONArray = withContext(Dispatchers.IO) {
        getJson("/api/social/comments", mapOf("packId" to packId)).optJSONArray("comments") ?: JSONArray()
    }

    suspend fun fetchComments(context: Context, packId: String): JSONArray = withContext(Dispatchers.IO) {
        val viewer = currentUser(context)
        runCatching {
            getJson("/api/social/comments", mapOf("packId" to packId, "viewerId" to viewer.id, "viewerEmail" to viewer.email)).optJSONArray("comments") ?: JSONArray()
        }.getOrElse { fetchCommentsFromPocketBase(packId, viewer) }
    }

    suspend fun fetchPackSocial(context: Context, packId: String): JSONObject = withContext(Dispatchers.IO) {
        val viewer = currentUser(context)
        runCatching { getJson("/api/social/pack", mapOf("packId" to packId, "viewerId" to viewer.id, "viewerEmail" to viewer.email)) }
            .getOrElse { JSONObject().put("liked", isLocallyLiked(context, packId)).put("like_count", 0).put("comment_count", 0) }
    }

    suspend fun addComment(context: Context, pack: Pack, body: String): JSONObject = withContext(Dispatchers.IO) {
        requireSignedIn(context)
        val viewer = currentUser(context)
        val payload = JSONObject().apply {
            put("pack_id", pack.id)
            put("collection", pack.storagePath)
            put("user_id", viewer.id)
            put("user_email", viewer.email)
            put("display_name", viewer.name.ifBlank { viewer.email })
            put("photo_url", viewer.photoUrl)
            put("body", body)
        }
        runCatching { postJson("/api/social/comments", payload) }.getOrElse {
            val created = PocketBaseHelper.createRecord("pack_comments", JSONObject(payload.toString()).apply {
                put("like_count", 0)
                put("created_at", java.time.Instant.now().toString())
            })
            JSONObject().put("comment", created)
        }
    }

    suspend fun togglePackLike(context: Context, pack: Pack): JSONObject = withContext(Dispatchers.IO) {
        requireSignedIn(context)
        val viewer = currentUser(context)
        runCatching { postJson("/api/social/like", JSONObject().apply {
            put("pack_id", pack.id)
            put("collection", pack.storagePath)
            put("user_id", viewer.id)
            put("user_email", viewer.email)
            put("device_id", PreferencesHelper.getDeviceId(context))
            put("display_name", viewer.name.ifBlank { viewer.email })
            put("photo_url", viewer.photoUrl)
        }) }.getOrElse {
            val liked = toggleLocalLike(context, pack.id)
            JSONObject().put("liked", liked).put("like_count", pack.likeCount + if (liked) 1 else -1)
        }
    }

    suspend fun toggleCommentLike(context: Context, commentId: String, packId: String): JSONObject = withContext(Dispatchers.IO) {
        requireSignedIn(context)
        val viewer = currentUser(context)
        runCatching { postJson("/api/social/comments/like", JSONObject().apply {
            put("comment_id", commentId)
            put("pack_id", packId)
            put("user_id", viewer.id)
            put("user_email", viewer.email)
            put("display_name", viewer.name.ifBlank { viewer.email })
        }) }.getOrElse { JSONObject().put("liked", false) }
    }

    suspend fun addCommentReply(context: Context, commentId: String, packId: String, body: String): JSONObject = withContext(Dispatchers.IO) {
        requireSignedIn(context)
        val viewer = currentUser(context)
        postJson("/api/social/comments/reply", JSONObject().apply {
            put("comment_id", commentId)
            put("pack_id", packId)
            put("user_id", viewer.id)
            put("user_email", viewer.email)
            put("display_name", viewer.name.ifBlank { viewer.email })
            put("photo_url", viewer.photoUrl)
            put("body", body)
        })
    }

    suspend fun toggleReplyLike(context: Context, replyId: String, packId: String): JSONObject = withContext(Dispatchers.IO) {
        requireSignedIn(context)
        val viewer = currentUser(context)
        runCatching { postJson("/api/social/comments/reply/like", JSONObject().apply {
            put("reply_id", replyId)
            put("pack_id", packId)
            put("user_id", viewer.id)
            put("user_email", viewer.email)
            put("display_name", viewer.name.ifBlank { viewer.email })
        }) }.getOrElse { JSONObject().put("liked", false) }
    }

    suspend fun updateProfile(context: Context, displayName: String, bio: String, showEmail: Boolean, photoUrl: String): JSONObject = withContext(Dispatchers.IO) {
        val viewer = currentUser(context)
        postPatchJson("/api/social/profile", JSONObject().apply {
            put("id", viewer.id)
            put("user_id", viewer.id)
            put("email", viewer.email)
            put("display_name", displayName)
            put("bio", bio)
            put("show_email", showEmail)
            put("photo_url", photoUrl)
        }, "PATCH")
    }

    suspend fun deleteSharedPack(context: Context, submissionId: String?, packId: String?): JSONObject = withContext(Dispatchers.IO) {
        val viewer = currentUser(context)
        postJson("/api/social/pack/delete", JSONObject().apply {
            put("submission_id", submissionId.orEmpty())
            put("pack_id", packId.orEmpty())
            put("user_id", viewer.id)
            put("user_email", viewer.email)
        })
    }

    private fun encoded(params: Map<String, String>): String = params.entries
        .filter { it.value.isNotBlank() }
        .joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }

    private fun getJson(path: String, params: Map<String, String>): JSONObject {
        val query = encoded(params)
        val suffix = if (query.isNotBlank()) "?$query" else ""
        val conn = URL("${PocketBaseHelper.WORKER_URL}$path$suffix").openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 12000
        conn.readTimeout = 15000
        return readResponse(conn)
    }

    private fun postJson(path: String, body: JSONObject): JSONObject = postPatchJson(path, body, "POST")

    private fun postPatchJson(path: String, body: JSONObject, method: String): JSONObject {
        val conn = URL("${PocketBaseHelper.WORKER_URL}$path").openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 12000
        conn.readTimeout = 15000
        conn.doOutput = true
        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
        return readResponse(conn)
    }

    private fun readResponse(conn: HttpURLConnection): JSONObject {
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        val text = BufferedReader(InputStreamReader(stream)).use { it.readText() }
        if (conn.responseCode !in 200..299) throw IllegalStateException(text.ifBlank { "HTTP ${conn.responseCode}" })
        return JSONObject(text.ifBlank { "{}" })
    }

    private suspend fun fetchPublisherProfileFromPocketBase(pack: Pack, viewer: CurrentUser, language: String): JSONObject {
        val identifiers = listOf(pack.publisherUserId, pack.email, pack.pub).map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
        fun matches(record: JSONObject): Boolean = listOf(
            record.optString("id"), record.optString("user_id"), record.optString("uid"), record.optString("device_id"),
            record.optString("email"), record.optString("publisher_email"), record.optString("publisher_user_id"),
            record.optString("display_name"), record.optString("publisher"), record.optString("publisher_name")
        ).map { it.trim().lowercase() }.any { it in identifiers }

        val profiles = runCatching { PocketBaseHelper.listAllRecords("user_profiles", perPage = 200) }.getOrDefault(emptyList())
        val follows = runCatching { PocketBaseHelper.listAllRecords("user_follows", perPage = 200) }.getOrDefault(emptyList())
        val likes = runCatching { PocketBaseHelper.listAllRecords("pack_likes", perPage = 200) }.getOrDefault(emptyList())
        val comments = runCatching { PocketBaseHelper.listAllRecords("pack_comments", perPage = 200) }.getOrDefault(emptyList())
        val allPacks = listOf(
            runCatching { PocketBaseHelper.listAllRecords("stickers", perPage = 500).map { it to "stickers" } }.getOrDefault(emptyList()),
            runCatching { PocketBaseHelper.listAllRecords("premium_stickers", perPage = 500).map { it to "premium_stickers" } }.getOrDefault(emptyList())
        ).flatten().filter { (record, _) -> record.optBoolean("is_active", true) && matches(record) }

        val matchedProfile = profiles.firstOrNull { matches(it) }
        val profile = JSONObject().apply {
            put("id", matchedProfile?.optString("id") ?: pack.publisherUserId)
            put("user_id", matchedProfile?.optString("user_id") ?: pack.publisherUserId)
            put("email", matchedProfile?.optString("email") ?: pack.email)
            put("display_name", matchedProfile?.let { it.optString("display_name").ifBlank { it.optString("name") } } ?: pack.pub)
            put("photo_url", matchedProfile?.optString("photo_url")?.ifBlank { pack.publisherPhotoUrl } ?: pack.publisherPhotoUrl)
            put("bio", matchedProfile?.optString("bio") ?: "")
            put("show_email", matchedProfile?.optBoolean("show_email", true) ?: true)
        }

        val packSummaries = JSONArray()
        allPacks.map { (record, collection) ->
            val packId = record.optString("id")
            val likeCount = likes.count { it.optString("pack_id") == packId }
            val commentCount = comments.count { it.optString("pack_id") == packId }
            JSONObject().apply {
                put("id", packId)
                put("collection", collection)
                put("name", record.optString("name_${language.substringBefore('-')}").ifBlank { record.optString("name_en") }.ifBlank { record.optString("name") }.ifBlank { record.optString("pack_name") }.ifBlank { packId })
                put("sticker_count", record.optInt("sticker_count", record.optJSONArray("stickers")?.length() ?: 0))
                put("stickers", record.optJSONArray("stickers") ?: record.optJSONArray("sticker_data") ?: JSONArray())
                put("tray_url", record.optString("tray_url"))
                put("download_count", record.optInt("download_count", 0))
                put("favorite_count", record.optInt("favorite_count", 0))
                put("like_count", record.optInt("like_count", likeCount))
                put("comment_count", record.optInt("comment_count", commentCount))
            }
        }.sortedByDescending { it.optInt("download_count") * 5 + it.optInt("like_count") * 6 + it.optInt("comment_count") * 8 }
            .forEach { packSummaries.put(it) }

        val viewerKeys = setOf(viewer.id.lowercase(), viewer.email.lowercase()).filter { it.isNotBlank() }.toSet()
        val followers = follows.filter { follow -> listOf(follow.optString("target_id"), follow.optString("target_email"), follow.optString("following_id"), follow.optString("following_email")).map { it.lowercase() }.any { it in identifiers } }
        val following = follows.filter { follow -> listOf(follow.optString("follower_id"), follow.optString("follower_email")).map { it.lowercase() }.any { it in identifiers } }
        val isFollowing = followers.any { follow -> listOf(follow.optString("follower_id"), follow.optString("follower_email")).map { it.lowercase() }.any { it in viewerKeys } }

        fun profileFor(vararg values: String): JSONObject? {
            val keys = values.map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
            if (keys.isEmpty()) return null
            return profiles.firstOrNull { record ->
                listOf(
                    record.optString("id"), record.optString("user_id"), record.optString("uid"), record.optString("device_id"),
                    record.optString("email"), record.optString("user_email"), record.optString("display_name"), record.optString("name")
                ).map { it.trim().lowercase() }.any { it in keys }
            }
        }

        fun profilePhoto(record: JSONObject?): String = record?.optString("photo_url").orEmpty()
            .ifBlank { record?.optString("avatar_url").orEmpty() }
            .ifBlank { record?.optString("picture").orEmpty() }

        return JSONObject().apply {
            put("profile", profile)
            put("packs", packSummaries)
            put("stats", JSONObject().apply {
                put("packs", packSummaries.length())
                put("downloads", allPacks.sumOf { it.first.optInt("download_count", 0) })
                put("favorites", allPacks.sumOf { it.first.optInt("favorite_count", 0) })
                put("likes", (0 until packSummaries.length()).sumOf { packSummaries.getJSONObject(it).optInt("like_count", 0) })
                put("comments", (0 until packSummaries.length()).sumOf { packSummaries.getJSONObject(it).optInt("comment_count", 0) })
                put("followers", followers.size)
                put("following", following.size)
            })
            put("followers", JSONArray().apply {
                followers.forEach { follow ->
                    val followerProfile = profileFor(follow.optString("follower_id"), follow.optString("follower_email"), follow.optString("follower_name"))
                    val photo = follow.optString("follower_photo").ifBlank { profilePhoto(followerProfile) }
                    put(JSONObject().apply {
                        put("id", follow.optString("follower_id"))
                        put("email", follow.optString("follower_email"))
                        put("name", follow.optString("follower_name").ifBlank { followerProfile?.optString("display_name").orEmpty().ifBlank { followerProfile?.optString("name").orEmpty() } })
                        put("photo_url", photo)
                        put("photo", photo)
                        put("avatar_url", photo)
                    })
                }
            })
            put("following", JSONArray().apply {
                following.forEach { follow ->
                    val targetProfile = profileFor(follow.optString("target_id"), follow.optString("following_id"), follow.optString("target_email"), follow.optString("following_email"), follow.optString("target_name"), follow.optString("following_name"))
                    val photo = follow.optString("target_photo").ifBlank { follow.optString("following_photo") }.ifBlank { profilePhoto(targetProfile) }
                    put(JSONObject().apply {
                        put("id", follow.optString("target_id").ifBlank { follow.optString("following_id") })
                        put("email", follow.optString("target_email").ifBlank { follow.optString("following_email") })
                        put("name", follow.optString("target_name").ifBlank { follow.optString("following_name") }.ifBlank { targetProfile?.optString("display_name").orEmpty().ifBlank { targetProfile?.optString("name").orEmpty() } })
                        put("photo_url", photo)
                        put("photo", photo)
                        put("avatar_url", photo)
                    })
                }
            })
            put("is_following", isFollowing)
        }
    }

    private suspend fun fetchCommentsFromPocketBase(packId: String, viewer: CurrentUser): JSONArray {
        val comments = runCatching { PocketBaseHelper.listAllRecords("pack_comments", filter = "pack_id='${escape(packId)}'", perPage = 200) }.getOrDefault(emptyList())
        val likes = runCatching { PocketBaseHelper.listAllRecords("comment_likes", filter = "pack_id='${escape(packId)}'", perPage = 200) }.getOrDefault(emptyList())
        val replies = runCatching { PocketBaseHelper.listAllRecords("comment_replies", filter = "pack_id='${escape(packId)}'", perPage = 200) }.getOrDefault(emptyList())
        val viewerKeys = setOf(viewer.id.lowercase(), viewer.email.lowercase()).filter { it.isNotBlank() }.toSet()
        val sorted = comments.sortedByDescending { it.optString("created_at", it.optString("created")) }
        return JSONArray().apply {
            sorted.forEach { comment ->
                val commentId = comment.optString("id")
                val commentLikes = likes.filter { it.optString("comment_id") == commentId }
                val commentReplies = replies.filter { it.optString("comment_id") == commentId }
                    .sortedBy { it.optString("created_at", it.optString("created")) }
                put(JSONObject(comment.toString()).apply {
                    put("like_count", comment.optInt("like_count", commentLikes.size))
                    put("liked", commentLikes.any { like -> listOf(like.optString("user_id"), like.optString("user_email")).map { it.lowercase() }.any { it in viewerKeys } })
                    put("replies", JSONArray().apply { commentReplies.forEach { put(it) } })
                })
            }
        }
    }

    private fun escape(value: String): String = value.replace("'", "\\'")

    private fun localLikedSet(context: Context): MutableSet<String> = context
        .getSharedPreferences("sticky_prefs", Context.MODE_PRIVATE)
        .getStringSet("local_pack_likes", emptySet())
        ?.toMutableSet() ?: mutableSetOf()

    private fun isLocallyLiked(context: Context, packId: String): Boolean = localLikedSet(context).contains(packId)

    private fun toggleLocalLike(context: Context, packId: String): Boolean {
        val likes = localLikedSet(context)
        val liked = if (likes.contains(packId)) {
            likes.remove(packId)
            false
        } else {
            likes.add(packId)
            true
        }
        context.getSharedPreferences("sticky_prefs", Context.MODE_PRIVATE).edit().putStringSet("local_pack_likes", likes).apply()
        return liked
    }
}