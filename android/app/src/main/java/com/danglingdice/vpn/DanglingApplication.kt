package com.danglingdice.vpn

import android.app.Application
import com.danglingdice.vpn.vpn.WireGuardTunnelManager
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend

class DanglingApplication : Application() {

    lateinit var wireGuardBackend: Backend
        private set

    lateinit var tunnelManager: WireGuardTunnelManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        // 1. Initialize the high-performance WireGuard GoBackend
        wireGuardBackend = GoBackend(applicationContext)
        // 2. Initialize Single Source of Truth (SSOT) Tunnel Manager shared across Activity, Quick Settings Tile & Wi-Fi Shield
        tunnelManager = WireGuardTunnelManager(applicationContext)
    }

    companion object {
        lateinit var instance: DanglingApplication
            private set
    }
}
