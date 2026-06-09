package com.scamslayer.app.data

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BillingManager(private val context: Context) : PurchasesUpdatedListener {

    companion object {
        private const val TAG = "BillingManager"
        const val PREMIUM_PRODUCT_ID = "scamslayer_premium_monthly"
    }

    private val _purchaseComplete = MutableStateFlow<String?>(null)
    val purchaseComplete: StateFlow<String?> = _purchaseComplete

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    private var productDetails: ProductDetails? = null

    fun connect() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.i(TAG, "Billing connected")
                    queryProduct()
                    restorePurchases()
                } else {
                    Log.w(TAG, "Billing setup failed: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing disconnected")
            }
        })
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PREMIUM_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(params) { result, products ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && products.isNotEmpty()) {
                productDetails = products[0]
                Log.i(TAG, "Product found: ${products[0].name}")
            } else {
                Log.w(TAG, "No products found: ${result.debugMessage}")
            }
        }
    }

    fun getFormattedPrice(): String? {
        return productDetails?.subscriptionOfferDetails?.firstOrNull()
            ?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
    }

    fun launchPurchase(activity: Activity): Boolean {
        val details = productDetails ?: return false
        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return false

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offerToken)
                        .build()
                )
            )
            .build()

        val result = billingClient.launchBillingFlow(activity, params)
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    acknowledgePurchase(purchase)
                    _purchaseComplete.value = purchase.purchaseToken
                }
            }
        } else if (result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.i(TAG, "Purchase cancelled")
        } else {
            Log.w(TAG, "Purchase error: ${result.debugMessage}")
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { result ->
            Log.i(TAG, "Acknowledge result: ${result.responseCode}")
        }
    }

    private val _hasActiveSubscription = MutableStateFlow(false)
    val hasActiveSubscription: StateFlow<Boolean> = _hasActiveSubscription

    private fun restorePurchases() {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val active = purchases.any { purchase ->
                    purchase.products.contains(PREMIUM_PRODUCT_ID) &&
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                _hasActiveSubscription.value = active
                if (active) {
                    val token = purchases.first { it.products.contains(PREMIUM_PRODUCT_ID) }?.purchaseToken
                    if (token != null) {
                        _purchaseComplete.value = token
                    }
                }
                Log.i(TAG, "Restore: ${purchases.size} subscriptions, active=$active")
            } else {
                Log.w(TAG, "Restore failed: ${result.debugMessage}")
            }
        }
    }

    fun disconnect() {
        billingClient.endConnection()
    }
}
