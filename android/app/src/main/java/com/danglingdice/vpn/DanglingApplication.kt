package com.danglingdice.vpn

import android.app.Application
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend

class DanglingApplication : Application() {

    lateinit var wireGuardBackend: Backend
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Initialize the high-performance WireGuard GoBackend
        wireGuardBackend = GoBackend(applicationContext)
    }

    companion object {
        lateinit var instance: DanglingApplication
            private set
    }
}

