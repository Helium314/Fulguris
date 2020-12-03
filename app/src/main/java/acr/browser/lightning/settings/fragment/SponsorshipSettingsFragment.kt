package acr.browser.lightning.settings.fragment

import acr.browser.lightning.R
import acr.browser.lightning.di.UserPrefs
import acr.browser.lightning.di.injector
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
import androidx.preference.SwitchPreferenceCompat
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Manage in-app purchases and subscriptions.
 */
class SponsorshipSettingsFragment : AbstractSettingsFragment(), PurchasesUpdatedListener, BillingClientStateListener {

    //@Inject
    //internal lateinit var userPreferences: UserPreferences

    private val LOG_TAG = "SponsorshipSettingsFragment"

    val SPONSOR_BRONZE = "sponsor.bronze"
    val SUBS_SKUS = listOf(SPONSOR_BRONZE)

    @Inject
    @UserPrefs
    internal lateinit var preferences: SharedPreferences

    lateinit private var playStoreBillingClient: BillingClient


    override fun providePreferencesXmlResource() = R.xml.preference_sponsorship

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        injector.inject(this)

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

    private fun connectToPlayBillingService(): Boolean {
        Log.d(LOG_TAG, "connectToPlayBillingService")
        if (!playStoreBillingClient.isReady) {
            playStoreBillingClient.startConnection(this)
            return true
        }
        return false
    }

    /**
     * This is the callback for when connection to the Play [BillingClient] has been successfully
     * established. It might make sense to get [SkuDetails] and [Purchases][Purchase] at this point.
     */
    override fun onBillingSetupFinished(billingResult: BillingResult) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                Log.d(LOG_TAG, "onBillingSetupFinished successfully")
                //querySkuDetailsAsync(BillingClient.SkuType.INAPP, GameSku.INAPP_SKUS)
                querySkuDetailsAsync(BillingClient.SkuType.SUBS, SUBS_SKUS)
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


    override fun onPurchasesUpdated(p0: BillingResult, p1: MutableList<Purchase>?) {
        Log.d(LOG_TAG, "onPurchasesUpdated")
    }


    /**
     * Presumably a set of SKUs has been defined on the Google Play Developer Console. This
     * method is for requesting a (improper) subset of those SKUs. Hence, the method accepts a list
     * of product IDs and returns the matching list of SkuDetails.
     *
     * The result is passed to [onSkuDetailsResponse]
     */
    private fun querySkuDetailsAsync(
            @BillingClient.SkuType skuType: String,
            skuList: List<String>) {
        val params = SkuDetailsParams.newBuilder().setSkusList(skuList).setType(skuType).build()
        Log.d(LOG_TAG, "querySkuDetailsAsync for $skuType")
        playStoreBillingClient.querySkuDetailsAsync(params) { billingResult, skuDetailsList ->
            when (billingResult.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    Log.d(LOG_TAG, "querySkuDetailsAsync OK")
                    if (skuDetailsList.orEmpty().isNotEmpty()) {
                        skuDetailsList?.forEach {
                            Log.d(LOG_TAG, it.toString())
                        }
                    }
                }
                else -> {
                    Log.e(LOG_TAG, billingResult.debugMessage)
                }
            }
        }
    }


}
