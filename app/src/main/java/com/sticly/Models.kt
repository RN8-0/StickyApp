package com.sticly

import com.google.gson.annotations.SerializedName

data class Pack(
    @SerializedName("identifier") val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("name_tr") val nameTr: String = "",
    @SerializedName("name_zh") val nameZh: String = "",
    @SerializedName("name_es") val nameEs: String = "",
    @SerializedName("name_ar") val nameAr: String = "",
    @SerializedName("name_hi") val nameHi: String = "",
    @SerializedName("name_pt") val namePt: String = "",
    @SerializedName("publisher") val pub: String = "",
    @SerializedName("publisher_email") val email: String = "",
    @SerializedName("privacy_policy_website") val privacy: String = "",
    @SerializedName("license_agreement_website") val license: String = "",
    @SerializedName("image_data_version") val version: String = "1",
    @SerializedName("avoid_cache") val avoidCache: Boolean = false,
    @SerializedName("tray_image_file") val tray: String = "",
    @SerializedName("stickers") val stickers: List<Sticker> = emptyList(),
    // Firebase alanları
    var trayUrl: String = "",
    val isPremium: Boolean = false,
    val productId: String = "",  // Google Play ürün etiketi (örn: "recep_ivedik")
    val storagePath: String = "stickers",  // Firebase Storage klasör yolu (stickers veya premium_stickers)
    val createdAt: String = "",  // Oluşturulma tarihi (ISO format)
    val category: String = "",   // Kategori (komik, romantik, spor, dizi_film, vb.)
    val downloadCount: Int = 0,  // İndirme/ekleme sayısı
    val fakeDownloadBase: Int = 0, // Fake indirme tabanı (kullanıcıya gösterilir)
    val viewCount: Int = 0,      // Görüntülenme sayısı
    val favoriteCount: Int = 0,  // Favori sayısı
    val isAnimated: Boolean = false, // Animasyonlu paket mi?
    val isActive: Boolean = true, // Paket aktif mi?
    val priceTRY: String = "",    // TRY fiyatı (örn: "4,99 TL")
    val priceUSD: String = "",    // USD fiyatı (örn: "$0.99")
    val priceEUR: String = ""     // EUR fiyatı (örn: "€0.99")
)

data class BillingSettings(
    val priceTRY: String = "4,99 TL",
    val priceUSD: String = "$0.99",
    val priceEUR: String = "€0.99",
    val updatedAt: String = ""
)

data class BillingPlan(
    val id: String = "",
    val name: String = "",
    val type: String = "subscription", // "subscription" or "onetime"
    val priceTry: String = "",
    val priceUsd: String = "",
    val priceEur: String = "",
    val isActive: Boolean = true,
    val trialDays: Int = 0,
    val discountPercentage: Int = 0
)

data class BillingConfig(
    val plans: List<BillingPlan> = emptyList(),
    val campaignActive: Boolean = false,
    val campaignName: String = "",
    val campaignEndDate: String = "",
    val updatedAt: String = ""
)

data class Sticker(
    @SerializedName("image_file") val file: String = "",
    @SerializedName("emojis") val emojis: List<String>? = null,
    // Firebase URL
    var url: String = ""
)

data class Response(
    @SerializedName("sticker_packs") val packs: List<Pack>
)
