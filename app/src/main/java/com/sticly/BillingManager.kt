package com.sticly

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BillingManager(
    private val context: Context,
    private val onPurchaseComplete: (Boolean) -> Unit
) : PurchasesUpdatedListener {

    private var billingClient: BillingClient? = null
    private var premiumProductDetails: ProductDetails? = null
    private var stickerProductDetails: MutableMap<String, ProductDetails> = mutableMapOf()

    companion object {
        // Premium abonelik - tüm stickerlar için (69,99 TL)
        const val PREMIUM_PRODUCT_ID = "premium_lifetime"

        // Tekil sticker paketi satın alma (7,99 TL)
        // Play Console'da oluşturulacak ürün ID'si: sticker_pack_<pack_id>
        const val STICKER_PACK_PREFIX = "sticker_pack_"
    }

    init {
        setupBillingClient()
    }

    private fun setupBillingClient() {
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases()
            .build()

        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProducts()
                    checkExistingPurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                // Retry connection
            }
        })
    }

    private fun queryProducts() {
        // Premium ürünü sorgula
        val premiumProductList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PREMIUM_PRODUCT_ID)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val premiumParams = QueryProductDetailsParams.newBuilder()
            .setProductList(premiumProductList)
            .build()

        billingClient?.queryProductDetailsAsync(premiumParams) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                premiumProductDetails = productDetailsList.firstOrNull()
            }
        }
    }

    /**
     * Belirli bir sticker paketi için ürün bilgisini sorgula
     * Play Console'da sticker_pack_<pack_id> formatında ürünler oluşturulmalı
     */
    fun queryStickerPackProduct(packId: String, onResult: (ProductDetails?) -> Unit) {
        val productId = "$STICKER_PACK_PREFIX$packId"

        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient?.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val product = productDetailsList.firstOrNull()
                if (product != null) {
                    stickerProductDetails[packId] = product
                }
                onResult(product)
            } else {
                onResult(null)
            }
        }
    }

    private fun checkExistingPurchases() {
        billingClient?.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { billingResult, purchaseList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                for (purchase in purchaseList) {
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        // Tüm satın alınan ürünleri kontrol et
                        handleSuccessfulPurchase(purchase)

                        // Premium satın alındıysa callback'i çağır
                        if (purchase.products.contains(PREMIUM_PRODUCT_ID)) {
                            onPurchaseComplete(true)
                        }
                    }
                }
            }
        }
    }

    /**
     * Premium satın alma başlat (69,99 TL)
     */
    fun launchPurchase(activity: Activity) {
        val product = premiumProductDetails ?: return

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(product)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        billingClient?.launchBillingFlow(activity, billingFlowParams)
    }

    /**
     * Tekil sticker paketi satın alma başlat (7,99 TL)
     */
    fun launchStickerPackPurchase(activity: Activity, packId: String) {
        val product = stickerProductDetails[packId] ?: return

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(product)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        billingClient?.launchBillingFlow(activity, billingFlowParams)
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()

                CoroutineScope(Dispatchers.IO).launch {
                    billingClient?.acknowledgePurchase(acknowledgePurchaseParams) { result ->
                        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                            handleSuccessfulPurchase(purchase)
                            CoroutineScope(Dispatchers.Main).launch {
                                onPurchaseComplete(true)
                            }
                        }
                    }
                }
            } else {
                handleSuccessfulPurchase(purchase)
                onPurchaseComplete(true)
            }
        }
    }

    private fun handleSuccessfulPurchase(purchase: Purchase) {
        for (productId in purchase.products) {
            when {
                // Premium satın alma
                productId == PREMIUM_PRODUCT_ID -> {
                    PreferencesHelper.setPremium(context, true)
                }
                // Tekil sticker paketi satın alma
                productId.startsWith(STICKER_PACK_PREFIX) -> {
                    val packId = productId.removePrefix(STICKER_PACK_PREFIX)
                    PreferencesHelper.addPurchasedPack(context, packId)
                }
            }
        }
    }

    /**
     * Manuel satın alım geri yükleme
     * Kullanıcı uygulamayı silip yeniden yüklediğinde bu fonksiyon çağrılabilir
     */
    fun restorePurchases(onResult: (RestoreResult) -> Unit) {
        if (billingClient?.isReady != true) {
            onResult(RestoreResult.ERROR)
            return
        }

        billingClient?.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { billingResult, purchaseList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                if (purchaseList.isEmpty()) {
                    CoroutineScope(Dispatchers.Main).launch {
                        onResult(RestoreResult.NOT_FOUND)
                    }
                } else {
                    var restoredAny = false
                    for (purchase in purchaseList) {
                        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                            handleSuccessfulPurchase(purchase)
                            restoredAny = true
                        }
                    }
                    CoroutineScope(Dispatchers.Main).launch {
                        if (restoredAny) {
                            onResult(RestoreResult.SUCCESS)
                        } else {
                            onResult(RestoreResult.NOT_FOUND)
                        }
                    }
                }
            } else {
                CoroutineScope(Dispatchers.Main).launch {
                    onResult(RestoreResult.ERROR)
                }
            }
        }
    }

    enum class RestoreResult {
        SUCCESS,
        NOT_FOUND,
        ERROR
    }

    fun destroy() {
        billingClient?.endConnection()
    }
}
