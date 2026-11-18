package com.danglingdice.vpn

import android.app.Application
import com.danglingdice.vpn.billing.SubscriptionManager
import com.danglingdice.vpn.vpn.WireGuardTunnelManager
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend

class DanglingApplication : Application() {

    lateinit var wireGuardBackend: Backend
        private set

    lateinit var tunnelManager: WireGuardTunnelManager
        private set

    lateinit var subscriptionManager: SubscriptionManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 1. Initialize the high-performance WireGuard GoBackend
        wireGuardBackend = GoBackend(applicationContext)

        // 2. Initialize Single Source of Truth (SSOT) Tunnel Manager shared across Activity, Quick Settings Tile & Wi-Fi Shield
        tunnelManager = WireGuardTunnelManager(applicationContext)

        // 3. Configure RevenueCat SDK (v11.4.0) with Zero-Login Hardware Passkey App User ID
        Purchases.logLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.WARN
        Purchases.configure(
            PurchasesConfiguration.Builder(
                context = this,
                apiKey = SubscriptionManager.REVENUECAT_API_KEY
            )
                .appUserID(tunnelManager.accountToken.value)
                .build()
        )

        // 4. Initialize SubscriptionManager & sync `dangling_dice_vpn_pro` entitlement with TunnelManager
        subscriptionManager = SubscriptionManager(
            onProStatusChanged = { isPro ->
                tunnelManager.setPremiumStatus(isPro)
            }
        )
        subscriptionManager.startListening()
    }

    companion object {
        lateinit var instance: DanglingApplication
            private set
    }
}
