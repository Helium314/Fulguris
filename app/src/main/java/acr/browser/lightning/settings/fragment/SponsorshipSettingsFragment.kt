package acr.browser.lightning.settings.fragment

import acr.browser.lightning.R
import acr.browser.lightning.Sponsorship
import acr.browser.lightning.di.UserPrefs
import acr.browser.lightning.di.injector
import acr.browser.lightning.preference.UserPreferences
import acr.browser.lightning.utils.IntentUtils
import android.app.Activity
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.android.billingclient.api.*
import kotlinx.coroutines.*
import java.util.HashSet
import javax.inject.Inject

/**
 * Manage in-app purchases and subscriptions.
 */
class SponsorshipSettingsFragment : AbstractSettingsFragment(),
        PurchasesUpdatedListener,
        BillingClientStateListener,
        SkuDetailsResponseListener {

    //@Inject
    //internal lateinit var userPreferences: UserPreferences

    private val LOG_TAG = "SponsorshipSettingsFragment"

    val SPONSOR_BRONZE = "sponsor.bronze"
    val SUBS_SKUS = listOf(SPONSOR_BRONZE)

    @Inject internal lateinit var userPreferences: UserPreferences

    // Google Play Store billing client
    private lateinit var playStoreBillingClient: BillingClient


    override fun providePreferencesXmlResource() = R.xml.preference_sponsorship

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        injector.inject(this)

        // Connect our billing client
        context?.let {
            playStoreBillingClient = BillingClient.newBuilder(it)
                .enablePendingPurchases() // required or app will crash
                .setListener(this).build()
            connectToPlayBillingService()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        playStoreBillingClient.endConnection()

    }

    /**
     * Start connection with Google Play store billing.
     */
    private fun connectToPlayBillingService(): Boolean {
        Log.d(LOG_TAG, "connectToPlayBillingService")
        if (!playStoreBillingClient.isReady) {
            playStoreBillingClient.startConnection(this)
            return true
        }
        return false
    }

    /**
     * Callback from [BillingClient] after opening connection.
     * It might make sense to get [SkuDetails] and [Purchases][Purchase] at this point.
     */
    override fun onBillingSetupFinished(billingResult: BillingResult) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                Log.d(LOG_TAG, "onBillingSetupFinished successfully")
                //querySkuDetailsAsync(BillingClient.SkuType.INAPP, GameSku.INAPP_SKUS)
                // Ask client for list of available subscriptions
                populateSubscriptions()
                // Ask client for a list of purchase belonging to our customer
                //queryPurchasesAsync()
            }
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
                //Some apps may choose to make decisions based on this knowledge.
                Log.d(LOG_TAG, billingResult.debugMessage)
            }
            else -> {
                //do nothing. Someone else will connect it through retry policy.
                //May choose to send to server though
                Log.d(LOG_TAG, billingResult.debugMessage)
            }
        }
    }

    /**
     * This method is called when the app has inadvertently disconnected from the [BillingClient].
     * An attempt should be made to reconnect using a retry policy. Note the distinction between
     * [endConnection][BillingClient.endConnection] and disconnected:
     * - disconnected means it's okay to try reconnecting.
     * - endConnection means the [playStoreBillingClient] must be re-instantiated and then start
     *   a new connection because a [BillingClient] instance is invalid after endConnection has
     *   been called.
     **/
    override fun onBillingServiceDisconnected() {
        Log.d(LOG_TAG, "onBillingServiceDisconnected")
        connectToPlayBillingService()
    }


    /**
     * New purchases are coming in from here.
     * We need to acknowledge them.
     */
    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        Log.d(LOG_TAG, "onPurchasesUpdated")
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach {
                    if (it.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        if (!it.isAcknowledged) {
                            // Just acknowledge our purchase
                            val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(it.purchaseToken).build()
                            playStoreBillingClient.acknowledgePurchase(acknowledgePurchaseParams) {
                                // TODO: Again what should we do with that?
                                billingResult -> Log.d(LOG_TAG, "onAcknowledgePurchaseResponse: $billingResult")
                                when (billingResult.responseCode) {
                                    BillingClient.BillingResponseCode.OK -> {
                                        // TODO: change our settings to unlock entitlement
                                        if (it.sku == SPONSOR_BRONZE) {
                                            userPreferences.sponsorship = Sponsorship.BRONZE
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
            }
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> {
                connectToPlayBillingService()
            }
            else -> {
                Log.i(LOG_TAG, billingResult.debugMessage)
            }
        }

    }


    /**
     * Query our billing client for known subscriptions, then check which ones are currently active
     * to populate our screen.
     */
    private fun populateSubscriptions() {
        if (!isSubscriptionSupported()) {
            return
        }
        val params = SkuDetailsParams.newBuilder().setSkusList(SUBS_SKUS).setType(BillingClient.SkuType.SUBS).build()
        playStoreBillingClient.querySkuDetailsAsync(params) { billingResult, skuDetailsList ->

            when (billingResult.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    Log.d(LOG_TAG, "populateSubscriptions OK")

                    if (skuDetailsList.orEmpty().isNotEmpty()) {
                        // We got a valid list of SKUs for our subscriptions
                        var purchases = playStoreBillingClient.queryPurchases(BillingClient.SkuType.SUBS)
                        Log.d(LOG_TAG, "Purchases dump: ")
                        purchases.purchasesList?.forEach {Log.d(LOG_TAG, it.toString())}

                        // TODO: do we need to check the result?
                        skuDetailsList?.forEach { skuDetails ->
                            Log.d(LOG_TAG, skuDetails.toString())
                            val pref = SwitchPreferenceCompat(context)
                            pref.title = skuDetails.title
                            pref.summary = skuDetails.description
                            //pref.key = skuDetails.sku
                            // Check if that SKU is an active subscription
                            if (purchases.purchasesList.isNullOrEmpty()) {
                                pref.isChecked = false
                            }
                            else {
                                pref.isChecked = purchases.purchasesList?.firstOrNull { purchase -> purchase.sku == skuDetails.sku && purchase.isAcknowledged } != null
                            }

                            pref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue: Any ->
                                if (newValue == true) {
                                    // Launch subscription workflow
                                    val purchaseParams = BillingFlowParams.newBuilder().setSkuDetails(skuDetails).build()
                                    activity?.let {
                                        playStoreBillingClient.launchBillingFlow(it, purchaseParams)
                                        // TODO: Check the result?
                                        // https://developer.android.com/reference/com/android/billingclient/api/BillingClient#launchBillingFlow(android.app.Activity,%20com.android.billingclient.api.BillingFlowParams)
                                        // Purchase results are delivered in onPurchasesUpdated
                                    }
                                }

                                false
                            }


                            preferenceScreen.addPreference(pref)
                        }
                    }
                }
                else -> {
                    Log.e(LOG_TAG, billingResult.debugMessage)
                }
            }
        }
    }

    override fun onSkuDetailsResponse(p0: BillingResult, p1: MutableList<SkuDetails>?) {
        TODO("Not yet implemented")
    }


    /**
     * BACKGROUND
     *
     * Google Play Billing refers to receipts as [Purchases][Purchase]. So when a user buys
     * something, Play Billing returns a [Purchase] object that the app then uses to release the
     * [Entitlement] to the user. Receipts are pivotal within the [BillingRepository]; but they are
     * not part of the repo’s public API, because clients don’t need to know about them. When
     * the release of entitlements occurs depends on the type of purchase. For consumable products,
     * the release may be deferred until after consumption by Google Play; for non-consumable
     * products and subscriptions, the release may be deferred until after
     * [BillingClient.acknowledgePurchaseAsync] is called. You should keep receipts in the local
     * cache for augmented security and for making some transactions easier.
     *
     * THIS METHOD
     *
     * [This method][queryPurchasesAsync] grabs all the active purchases of this user and makes them
     * available to this app instance. Whereas this method plays a central role in the billing
     * system, it should be called at key junctures, such as when user the app starts.
     *
     * Because purchase data is vital to the rest of the app, this method is called each time
     * the [BillingViewModel] successfully establishes connection with the Play [BillingClient]:
     * the call comes through [onBillingSetupFinished]. Recall also from Figure 4 that this method
     * gets called from inside [onPurchasesUpdated] in the event that a purchase is "already
     * owned," which can happen if a user buys the item around the same time
     * on a different device.
     */
    fun queryPurchasesAsync() {
        Log.d(LOG_TAG, "queryPurchasesAsync called")
        val purchasesResult = HashSet<Purchase>()
        var result = playStoreBillingClient.queryPurchases(BillingClient.SkuType.INAPP)
        Log.d(LOG_TAG, "queryPurchasesAsync INAPP results: ${result?.purchasesList?.size}")
        result?.purchasesList?.apply { purchasesResult.addAll(this) }
        if (isSubscriptionSupported()) {
            result = playStoreBillingClient.queryPurchases(BillingClient.SkuType.SUBS)
            result?.purchasesList?.apply { purchasesResult.addAll(this) }
            Log.d(LOG_TAG, "queryPurchasesAsync SUBS results: ${result?.purchasesList?.size}")
        }
        //processPurchases(purchasesResult)
    }


    fun queryCurrentSubscriptions() {
        if (isSubscriptionSupported()) {
            var result = playStoreBillingClient.queryPurchases(BillingClient.SkuType.SUBS)
            //result?.purchasesList?.apply { purchasesResult.addAll(this) }
            Log.d(LOG_TAG, "queryPurchasesAsync SUBS results: ${result?.purchasesList?.size}")
        }
        //processPurchases(purchasesResult)
    }


    /**
     * Checks if the user's device supports subscriptions
     */
    private fun isSubscriptionSupported(): Boolean {
        val billingResult =
                playStoreBillingClient.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS)
        var succeeded = false
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> connectToPlayBillingService()
            BillingClient.BillingResponseCode.OK -> succeeded = true
            else -> Log.w(LOG_TAG,
                    "isSubscriptionSupported() error: ${billingResult.debugMessage}")
        }
        return succeeded
    }


}
