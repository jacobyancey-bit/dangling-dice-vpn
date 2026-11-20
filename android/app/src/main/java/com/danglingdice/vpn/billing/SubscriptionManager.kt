package com.danglingdice.vpn.billing

import android.app.Activity
import android.util.Log
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PackageType
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Single Source of Truth (SSOT) RevenueCat Subscription & Entitlement Manager
 * Handles entitlement checking (`dangling_dice_vpn_pro`), product offerings
 * (`monthly`, `yearly`, `lifetime`), purchases, restores, and live CustomerInfo updates.
 */
class SubscriptionManager(
    private val onProStatusChanged: (Boolean) -> Unit
) {
    companion object {
        private const val TAG = "SubscriptionManager"

        // RevenueCat Entitlement Identifier configured in Dashboard
        const val ENTITLEMENT_PRO = "dangling_dice_vpn_pro"

        // Product / Package Identifiers configured in RevenueCat & Google Play Console
        const val PRODUCT_MONTHLY = "monthly"
        const val PRODUCT_YEARLY = "yearly"
        const val PRODUCT_LIFETIME = "lifetime"

        // Official RevenueCat Google Play Store Public API Key
        const val REVENUECAT_API_KEY = "goog_IVvutPVdklVRajPOPioKrNsutuY"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private val _customerInfo = MutableStateFlow<CustomerInfo?>(null)
    val customerInfo: StateFlow<CustomerInfo?> = _customerInfo.asStateFlow()

    private val _currentOffering = MutableStateFlow<Offering?>(null)
    val currentOffering: StateFlow<Offering?> = _currentOffering.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val customerInfoListener = UpdatedCustomerInfoListener { info ->
        applyCustomerInfo(info)
    }

    fun startListening() {
        try {
            Purchases.sharedInstance.updatedCustomerInfoListener = customerInfoListener
            refreshCustomerInfo()
            fetchOfferings()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach RevenueCat listener: ${e.message}", e)
        }
    }

    fun applyCustomerInfo(info: CustomerInfo) {
        _customerInfo.value = info
        val proActive = info.entitlements[ENTITLEMENT_PRO]?.isActive == true
        _isPro.value = proActive
        onProStatusChanged(proActive)
        Log.d(TAG, "CustomerInfo updated -> $ENTITLEMENT_PRO active=$proActive")
    }

    fun refreshCustomerInfo() {
        scope.launch {
            try {
                val info = Purchases.sharedInstance.awaitCustomerInfo()
                applyCustomerInfo(info)
            } catch (e: PurchasesException) {
                Log.w(TAG, "Unable to refresh customer info: ${e.error.message}")
            } catch (e: Exception) {
                Log.w(TAG, "Unexpected error refreshing customer info: ${e.message}")
            }
        }
    }

    fun fetchOfferings() {
        scope.launch {
            _isLoading.value = true
            try {
                val offerings = Purchases.sharedInstance.awaitOfferings()
                _currentOffering.value = offerings.current
                Log.d(
                    TAG,
                    "Loaded offering=${offerings.current?.identifier}, packages=${offerings.current?.availablePackages?.map { it.identifier }}"
                )
            } catch (e: PurchasesException) {
                Log.w(TAG, "Error fetching offerings: ${e.error.message}")
                _errorMessage.value = "Could not load plans: ${e.error.message}"
            } catch (e: Exception) {
                Log.w(TAG, "Unexpected error fetching offerings: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Resolves the Monthly, Yearly, and Lifetime packages from the current Offering
     * using either standard RevenueCat PackageType or custom identifier (`monthly`, `yearly`, `lifetime`).
     */
    fun getPlanPackages(): List<Package> {
        val offering = _currentOffering.value ?: return emptyList()
        val monthly = offering.monthly ?: offering.availablePackages.find {
            it.packageType == PackageType.MONTHLY ||
                it.identifier.equals(PRODUCT_MONTHLY, ignoreCase = true) ||
                it.product.id.contains(PRODUCT_MONTHLY, ignoreCase = true)
        }
        val yearly = offering.annual ?: offering.availablePackages.find {
            it.packageType == PackageType.ANNUAL ||
                it.identifier.equals(PRODUCT_YEARLY, ignoreCase = true) ||
                it.product.id.contains(PRODUCT_YEARLY, ignoreCase = true)
        }
        val lifetime = offering.lifetime ?: offering.availablePackages.find {
            it.packageType == PackageType.LIFETIME ||
                it.identifier.equals(PRODUCT_LIFETIME, ignoreCase = true) ||
                it.product.id.contains(PRODUCT_LIFETIME, ignoreCase = true)
        }

        val ordered = listOfNotNull(yearly, monthly, lifetime).distinctBy { it.identifier }
        return ordered.ifEmpty { offering.availablePackages }
    }

    fun purchasePackage(
        activity: Activity,
        pkg: Package,
        onSuccess: () -> Unit = {}
    ) {
        scope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _statusMessage.value = null
            try {
                val result = Purchases.sharedInstance.awaitPurchase(
                    PurchaseParams.Builder(activity, pkg).build()
                )
                applyCustomerInfo(result.customerInfo)
                if (_isPro.value) {
                    _statusMessage.value = "Dangling Dice VPN Pro is now active!"
                    onSuccess()
                }
            } catch (e: PurchasesException) {
                when (e.code) {
                    PurchasesErrorCode.PurchaseCancelledError -> {
                        // User backed out of Play Billing sheet — not an error
                        Log.d(TAG, "User cancelled purchase flow.")
                    }
                    PurchasesErrorCode.PaymentPendingError -> {
                        _statusMessage.value = "Payment is pending confirmation from Google Play."
                    }
                    PurchasesErrorCode.ProductAlreadyPurchasedError -> {
                        _statusMessage.value = "You already own this plan. Restoring access..."
                        restorePurchases()
                    }
                    PurchasesErrorCode.NetworkError -> {
                        _errorMessage.value = "Network error while contacting Google Play. Please retry."
                    }
                    else -> {
                        _errorMessage.value = e.error.underlyingErrorMessage ?: e.error.message
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Purchase failed unexpectedly."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun restorePurchases() {
        scope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _statusMessage.value = null
            try {
                val info = Purchases.sharedInstance.awaitRestore()
                applyCustomerInfo(info)
                _statusMessage.value = if (_isPro.value) {
                    "Purchases restored — Dangling Dice VPN Pro active!"
                } else {
                    "No active Dangling Dice VPN Pro subscription found for this account."
                }
            } catch (e: PurchasesException) {
                _errorMessage.value = "Restore failed: ${e.error.message}"
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Unable to restore purchases."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearMessages() {
        _errorMessage.value = null
        _statusMessage.value = null
    }
}
