package com.sticly

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.android.billingclient.api.*
import kotlinx.coroutines.*
import java.util.LinkedList
import java.util.Queue

class BillingManager(
    private val context: Context,
    private val onPurchaseComplete: (Boolean) -> Unit,
    private val onBillingReady: (() -> Unit)? = null
) : PurchasesUpdatedListener {

    companion object {
        private const val TAG = "BillingManager"

        // Subscription IDs (Google Play Console)
        const val PREMIUM_MONTHLY = "sticky_monthly_premium"
        const val PREMIUM_YEARLY = "sticky_yearly_premium"

        // In-App (Lifetime) ID
        const val PREMIUM_LIFETIME = "sticky_lifetime_premium"

        private const val MAX_RETRY_ATTEMPTS = 3
    }

    private val billingScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val pendingRunnables: Queue<() -> Unit> = LinkedList()

    private var billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .enablePrepaidPlans()
                .build()
        )
        .build()

    private var premiumProductDetails: MutableMap<String, ProductDetails> = mutableMapOf()

    private var retryAttempt = 0
    private var lastCancelToastTime = 0L

    // Son başarılı satın alımın orderId'si (tier ürünleri için Firebase'e kayıt için)
    var lastPurchaseOrderId: String? = null
        private set

    private var isConnecting = false

    init {
        startConnection()
    }

    private fun startConnection() {
        if (isConnecting || billingClient.isReady) {
            if (billingClient.isReady) {
                processPendingRunnables()
            }
            return
        }
        isConnecting = true
        
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                isConnecting = false
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    retryAttempt = 0
                    queryProducts()
                    checkExistingPurchases()
                    processPendingRunnables()
                } else {
                    Log.e(TAG, "Billing setup failed: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                isConnecting = false
                retryWithExponentialBackoff()
            }
        })
    }
    
    // Bağlantı hazır olduğunda bekleyen işlemleri çalıştır
    private fun executeServiceRequest(runnable: () -> Unit) {
        if (billingClient.isReady) {
            runnable()
        } else {
            pendingRunnables.add(runnable)
            startConnection()
        }
    }
    
    private fun processPendingRunnables() {
        while (pendingRunnables.isNotEmpty()) {
            pendingRunnables.poll()?.invoke()
        }
    }

    private fun retryWithExponentialBackoff() {
        if (retryAttempt >= MAX_RETRY_ATTEMPTS) {
            Log.e(TAG, "Max retry attempts reached, giving up reconnection")
            return
        }
        val delayMs = (2000L * (1 shl retryAttempt)).coerceAtMost(8000L)
        retryAttempt++
        Log.d(TAG, "Retrying connection in ${delayMs}ms (attempt $retryAttempt)")
        billingScope.launch {
            delay(delayMs)
            startConnection()
        }
    }

    private fun queryProducts() {
        var subsLoaded = false
        var inAppLoaded = false

        fun checkAllLoaded() {
            if (subsLoaded && inAppLoaded) {
                billingScope.launch(Dispatchers.Main) {
                    onBillingReady?.invoke()
                }
            }
        }

        // 1. Query Subscriptions (Monthly + Yearly)
        val subSkus = listOf(PREMIUM_MONTHLY, PREMIUM_YEARLY)
        val subProductList = subSkus.map { sku ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(sku)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }

        val subParams = QueryProductDetailsParams.newBuilder()
            .setProductList(subProductList)
            .build()

        billingClient.queryProductDetailsAsync(subParams) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetailsList.forEach { premiumProductDetails[it.productId] = it }
                Log.d(TAG, "Loaded ${productDetailsList.size} subscription products")
                writePricesToFirebase()
            }
            subsLoaded = true
            checkAllLoaded()
        }

        // 2. Query In-App Products (Lifetime only)
        val inAppSkus = listOf(PREMIUM_LIFETIME)
        val inAppProductList = inAppSkus.map { sku ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(sku)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }

        val inAppParams = QueryProductDetailsParams.newBuilder()
            .setProductList(inAppProductList)
            .build()

        billingClient.queryProductDetailsAsync(inAppParams) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetailsList.forEach { premiumProductDetails[it.productId] = it }
                Log.d(TAG, "Loaded ${productDetailsList.size} in-app products")
            }
            inAppLoaded = true
            checkAllLoaded()
        }
    }

    /**
     * Google Play'den alinan gercek fiyatlari Firebase settings/billing'e yazar
     */
    private fun writePricesToFirebase() {
        billingScope.launch(Dispatchers.IO) {
            try {
                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val priceData = mutableMapOf<String, Any>()

                premiumProductDetails.forEach { (productId, details) ->
                    val price = if (details.productType == BillingClient.ProductType.SUBS) {
                        details.subscriptionOfferDetails?.firstOrNull()
                            ?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
                    } else {
                        details.oneTimePurchaseOfferDetails?.formattedPrice
                    }
                    if (price != null) {
                        priceData["google_play_price_$productId"] = price
                    }
                }

                if (priceData.isNotEmpty()) {
                    priceData["google_play_prices_updated_at"] = com.google.firebase.firestore.FieldValue.serverTimestamp()
                    firestore.collection("settings").document("billing")
                        .set(priceData, com.google.firebase.firestore.SetOptions.merge())
                    Log.d(TAG, "Wrote ${priceData.size} prices to Firebase")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error writing prices to Firebase: ${e.message}")
            }
        }
    }

    fun getFormattedPrice(productId: String): String? {
        val details = premiumProductDetails[productId] ?: return null
        return if (details.productType == BillingClient.ProductType.SUBS) {
            details.subscriptionOfferDetails?.firstOrNull()
                ?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
        } else {
            details.oneTimePurchaseOfferDetails?.formattedPrice
        }
    }

    /**
     * Fiyatı mikro birim olarak döndür (1.000.000 mikro = 1 birim)
     */
    fun getPriceMicros(productId: String): Long? {
        val details = premiumProductDetails[productId] ?: return null
        return if (details.productType == BillingClient.ProductType.SUBS) {
            details.subscriptionOfferDetails?.firstOrNull()
                ?.pricingPhases?.pricingPhaseList?.firstOrNull()?.priceAmountMicros
        } else {
            details.oneTimePurchaseOfferDetails?.priceAmountMicros
        }
    }

    /**
     * Para birimi kodunu döndür (TRY, USD, EUR vs.)
     */
    fun getPriceCurrencyCode(productId: String): String? {
        val details = premiumProductDetails[productId] ?: return null
        return if (details.productType == BillingClient.ProductType.SUBS) {
            details.subscriptionOfferDetails?.firstOrNull()
                ?.pricingPhases?.pricingPhaseList?.firstOrNull()?.priceCurrencyCode
        } else {
            details.oneTimePurchaseOfferDetails?.priceCurrencyCode
        }
    }

    /**
     * Belirli bir mikro değeri formatlanmış fiyata dönüştür
     */
    fun formatPrice(micros: Long, currencyCode: String): String {
        val amount = micros / 1_000_000.0
        val format = java.text.NumberFormat.getCurrencyInstance()
        format.currency = java.util.Currency.getInstance(currencyCode)
        format.maximumFractionDigits = 0 // Kuruşları gösterme
        return format.format(amount)
    }

    fun launchPurchase(activity: Activity, productId: String) {
        executeServiceRequest {
            val product = premiumProductDetails[productId]
            if (product == null) {
                // Try to launch connection and retry? Or just fail.
                // If details are missing, it means query hasn't finished or product id is wrong.
                Log.e(TAG, "Product details not found for $productId")
                return@executeServiceRequest
            }

            val productDetailsParamsList = mutableListOf<BillingFlowParams.ProductDetailsParams>()
            val builder = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(product)

            // For subscriptions, we must set the offer token (Billing 7.x required)
            if (product.productType == BillingClient.ProductType.SUBS) {
                val offerToken = product.subscriptionOfferDetails?.firstOrNull()?.offerToken
                if (offerToken != null) {
                    builder.setOfferToken(offerToken)
                }
            }

            productDetailsParamsList.add(builder.build())

            val billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(productDetailsParamsList)
                .build()

            billingClient.launchBillingFlow(activity, billingFlowParams)
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases != null) {
                    for (purchase in purchases) {
                        handlePurchase(purchase)
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                val now = System.currentTimeMillis()
                if (now - lastCancelToastTime > 2000) {
                    lastCancelToastTime = now
                    billingScope.launch(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.purchase_cancelled), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                // Zaten satın alınmış - Google Play'den geri yükle
                restorePurchases { result ->
                    billingScope.launch(Dispatchers.Main) {
                        when (result) {
                            RestoreResult.SUCCESS -> {
                                Toast.makeText(context, context.getString(R.string.restore_success), Toast.LENGTH_SHORT).show()
                                onPurchaseComplete(true)
                            }
                            else -> {
                                Toast.makeText(context, context.getString(R.string.purchase_already_owned), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
                billingScope.launch(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.billing_unavailable), Toast.LENGTH_LONG).show()
                }
            }
            else -> {
                Log.e(TAG, "Purchase error: ${billingResult.responseCode} - ${billingResult.debugMessage}")
                billingScope.launch(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.purchase_error), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                // orderId'yi sakla (tier ürünleri için Firebase kayıt için)
                lastPurchaseOrderId = purchase.orderId
                // Log.d(TAG, "Purchase completed successfully")

                if (!purchase.isAcknowledged) {
                    val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()

                    billingScope.launch(Dispatchers.IO) {
                        billingClient.acknowledgePurchase(acknowledgePurchaseParams) { result ->
                            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                                handleSuccessfulPurchase(purchase)
                                billingScope.launch(Dispatchers.Main) {
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
            Purchase.PurchaseState.PENDING -> {
                // Pending purchase - inform user
                billingScope.launch(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.purchase_pending),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun handleSuccessfulPurchase(purchase: Purchase) {
        for (productId in purchase.products) {
            Log.d(TAG, "Processing successful purchase: $productId")
            when {
                // Lifetime Premium (INAPP)
                productId == PREMIUM_LIFETIME -> {
                    Log.d(TAG, "Setting lifetime premium")
                    PreferencesHelper.setPremiumWithType(context, "lifetime", 0L)
                }
                // Subscription Premium (SUBS) - monthly or yearly
                productId == PREMIUM_MONTHLY || productId == PREMIUM_YEARLY -> {
                    val expiryEstimate = purchase.purchaseTime + estimateSubscriptionDurationMs(productId)
                    Log.d(TAG, "Setting subscription premium: $productId, expiry=$expiryEstimate")
                    PreferencesHelper.setPremiumWithType(context, "subscription", expiryEstimate)
                }
            }
            
            // Firebase'e de kaydet
            PreferencesHelper.savePurchasedPackByProductId(context, productId, purchase.orderId)
        }
    }

    private fun estimateSubscriptionDurationMs(productId: String): Long {
        return when (productId) {
            PREMIUM_MONTHLY -> 30L * 24 * 60 * 60 * 1000
            PREMIUM_YEARLY -> 365L * 24 * 60 * 60 * 1000
            else -> 30L * 24 * 60 * 60 * 1000
        }
    }

    fun checkExistingPurchases() {
        executeServiceRequest {
            var hasLifetime = false
            var hasActiveSub = false

            // Check INAPP (lifetime + sticker packs)
            billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            ) { resultInApp, inAppPurchases ->
                if (resultInApp.responseCode == BillingClient.BillingResponseCode.OK) {
                    for (purchase in inAppPurchases) {
                        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                            if (!purchase.isAcknowledged) {
                                acknowledgePurchaseAndHandle(purchase)
                            } else {
                                handleSuccessfulPurchase(purchase)
                            }
                            if (purchase.products.contains(PREMIUM_LIFETIME)) {
                                hasLifetime = true
                            }
                        }
                    }
                }

                // Check SUBS
                billingClient.queryPurchasesAsync(
                    QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                ) { resultSubs, subsPurchases ->
                    if (resultSubs.responseCode == BillingClient.BillingResponseCode.OK) {
                        for (purchase in subsPurchases) {
                            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                                if (!purchase.isAcknowledged) {
                                    acknowledgePurchaseAndHandle(purchase)
                                } else {
                                    handleSuccessfulPurchase(purchase)
                                }
                                hasActiveSub = true
                            }
                        }
                    }

                    // If no lifetime and no active subscription, revoke premium
                    if (!hasLifetime && !hasActiveSub) {
                        // Ancak burada dikkatli olmaliyiz, belki gecici olarak offline
                        // Sadece eminsek iptal ediyoruz.
                        // PreferencesHelper.setPremium(context, false) 
                        // -> Bunu otomatik iptal etmek riskli olabilir (offline durumlar).
                        // Yine de "Geri Yükle" butonu ile manuel tetiklenince mantıklı.
                    }
                }
            }
        }
    }

    private fun acknowledgePurchaseAndHandle(purchase: Purchase) {
        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingScope.launch(Dispatchers.IO) {
            billingClient.acknowledgePurchase(acknowledgePurchaseParams) { result ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Purchase acknowledged: ${purchase.products}")
                    handleSuccessfulPurchase(purchase)
                } else {
                    Log.e(TAG, "Failed to acknowledge purchase: ${result.responseCode} - ${result.debugMessage}")
                    // Acknowledge başarısız olsa bile satın alımı işle
                    handleSuccessfulPurchase(purchase)
                }
            }
        }
    }

    fun restorePurchases(onResult: (RestoreResult) -> Unit) {
        executeServiceRequest {
            restorePurchasesInternal(onResult)
        }
    }

    private fun restorePurchasesInternal(onResult: (RestoreResult) -> Unit) {
        var foundPurchases = false

        // INAPP ve SUBS sorgula
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        ) { resultInApp, purchasesInApp ->
            
            if (resultInApp.responseCode == BillingClient.BillingResponseCode.OK) {
                for (purchase in purchasesInApp) {
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        foundPurchases = true
                        handleSuccessfulPurchase(purchase)
                    }
                }
            }

            billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
            ) { resultSubs, purchasesSubs ->
                
                if (resultSubs.responseCode == BillingClient.BillingResponseCode.OK) {
                    for (purchase in purchasesSubs) {
                        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                            foundPurchases = true
                            handleSuccessfulPurchase(purchase)
                        }
                    }
                }

                billingScope.launch(Dispatchers.Main) {
                    if (foundPurchases) {
                        onResult(RestoreResult.SUCCESS)
                    } else {
                        onResult(RestoreResult.NOT_FOUND)
                    }
                }
            }
        }
    }

    enum class RestoreResult { SUCCESS, NOT_FOUND, ERROR }

    fun destroy() {
        billingScope.cancel()
        if (billingClient.isReady) {
            billingClient.endConnection()
        }
    }
}
