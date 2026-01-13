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
    private var productDetails: ProductDetails? = null

    companion object {
        const val PREMIUM_PRODUCT_ID = "premium_lifetime"
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
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PREMIUM_PRODUCT_ID)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient?.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetails = productDetailsList.firstOrNull()
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
                    if (purchase.products.contains(PREMIUM_PRODUCT_ID) &&
                        purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        PreferencesHelper.setPremium(context, true)
                        onPurchaseComplete(true)
                    }
                }
            }
        }
    }

    fun launchPurchase(activity: Activity) {
        val product = productDetails ?: return

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
                            PreferencesHelper.setPremium(context, true)
                            CoroutineScope(Dispatchers.Main).launch {
                                onPurchaseComplete(true)
                            }
                        }
                    }
                }
            } else {
                PreferencesHelper.setPremium(context, true)
                onPurchaseComplete(true)
            }
        }
    }

    fun destroy() {
        billingClient?.endConnection()
    }
}
