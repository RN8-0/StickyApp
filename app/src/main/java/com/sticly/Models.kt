package com.sticly
import com.google.gson.annotations.SerializedName

data class Pack(
    @SerializedName("identifier") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("publisher") val pub: String,
    @SerializedName("publisher_email") val email: String = "",
    @SerializedName("privacy_policy_website") val privacy: String = "",
    @SerializedName("license_agreement_website") val license: String = "",
    @SerializedName("image_data_version") val version: String = "1",
    @SerializedName("avoid_cache") val avoidCache: Boolean = false,
    @SerializedName("tray_image_file") val tray: String,
    @SerializedName("stickers") val stickers: List<Sticker>
)

data class Sticker(
    @SerializedName("image_file") val file: String,
    @SerializedName("emojis") val emojis: List<String>? = null
)

data class Response(
    @SerializedName("sticker_packs") val packs: List<Pack>
)
