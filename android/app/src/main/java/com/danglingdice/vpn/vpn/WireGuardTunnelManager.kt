package com.danglingdice.vpn.vpn

import android.content.Context
import com.danglingdice.vpn.DanglingApplication
import com.danglingdice.vpn.data.api.DanglingApiClient
import com.danglingdice.vpn.data.model.ConnectionStatus
import com.danglingdice.vpn.data.model.ServerNode
import com.danglingdice.vpn.data.model.TunnelStats
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import com.wireguard.crypto.KeyPair
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetAddress

class WireGuardTunnelManager(
    private val context: Context,
    private val apiClient: DanglingApiClient = DanglingApiClient()
) {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val tunnel = DanglingTunnel()

    private val _status = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val _stats = MutableStateFlow(TunnelStats())
    val stats: StateFlow<TunnelStats> = _stats.asStateFlow()

    private val _excludedApps = MutableStateFlow<Set<String>>(emptySet())
    val excludedApps: StateFlow<Set<String>> = _excludedApps.asStateFlow()

    private val _adBlockEnabled = MutableStateFlow(true)
    val adBlockEnabled: StateFlow<Boolean> = _adBlockEnabled.asStateFlow()

    // Persistent Zero-Login Cryptographic Device Keypair & Anonymous Passkey ID
    private val prefs = context.getSharedPreferences("dangling_zero_login_vault", Context.MODE_PRIVATE)
    private val clientKeyPair: KeyPair = loadOrGeneratePersistentKeyPair()

    private val _accountToken = MutableStateFlow(deriveZeroLoginToken(clientKeyPair.publicKey.toBase64()))
    val accountToken: StateFlow<String> = _accountToken.asStateFlow()

    private var statsJob: Job? = null
    private var connectionStartTime: Long = 0

    private fun loadOrGeneratePersistentKeyPair(): KeyPair {
        val savedPrivKey = prefs.getString("wg_curve25519_private_key", null)
        if (!savedPrivKey.isNullOrBlank()) {
            try {
                return KeyPair(com.wireguard.crypto.Key.fromBase64(savedPrivKey))
            } catch (_: Exception) {}
        }
        val newPair = KeyPair()
        prefs.edit().putString("wg_curve25519_private_key", newPair.privateKey.toBase64()).apply()
        return newPair
    }

    private fun deriveZeroLoginToken(pubKeyBase64: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(pubKeyBase64.trim().toByteArray())
        val hex = digest.take(6).joinToString("") { "%02X".format(it) }
        return "DD-${hex.substring(0, 4)}-${hex.substring(4, 8)}-${hex.substring(8, 12)}"
    }

    init {
        scope.launch {
            tunnel.state.collect { wireguardState ->
                _status.value = when (wireguardState) {
                    Tunnel.State.UP -> {
                        startStatsMonitoring()
                        ConnectionStatus.CONNECTED
                    }
                    Tunnel.State.DOWN -> {
                        stopStatsMonitoring()
                        ConnectionStatus.DISCONNECTED
                    }
                    Tunnel.State.TOGGLE -> ConnectionStatus.CONNECTING
                }
            }
        }
    }

    fun setExcludedApps(apps: Set<String>) {
        _excludedApps.value = apps
    }

    fun setAdBlockEnabled(enabled: Boolean) {
        _adBlockEnabled.value = enabled
    }

    fun connect(server: ServerNode) {
        if (_status.value != ConnectionStatus.DISCONNECTED) return
        _status.value = ConnectionStatus.CONNECTING

        scope.launch(Dispatchers.IO) {
            try {
                // 1. Register persistent device Curve25519 public key with server control plane (Zero-Login Auth)
                val registration = apiClient.registerPeer(
                    publicKey = clientKeyPair.publicKey.toBase64(),
                    deviceName = "Android-${android.os.Build.MODEL}",
                    adBlockEnabled = _adBlockEnabled.value
                )

                val assignedIp = registration?.first ?: "10.8.0.2/24"
                val serverPubKey = registration?.second ?: server.publicKey
                val dnsTarget = if (_adBlockEnabled.value) "94.140.14.14" else "1.1.1.1"

                // 3. Build WireGuard Configuration with Split-Tunneling & Ad-Block DNS support
                val ifaceBuilder = Interface.Builder()
                    .parsePrivateKey(clientKeyPair.privateKey.toBase64())
                    .addAddress(com.wireguard.config.InetNetwork.parse(assignedIp))
                    .addDnsServer(InetAddress.getByName(dnsTarget))

                // Apply Split Tunneling exclusions
                if (_excludedApps.value.isNotEmpty()) {
                    ifaceBuilder.excludeApplications(_excludedApps.value)
                }

                val peerBuilder = Peer.Builder()
                    .parsePublicKey(serverPubKey)
                    .parseEndpoint(server.endpoint)
                    .parseAllowedIPs("0.0.0.0/0, ::/0")
                    .setPersistentKeepalive(25)

                val config = Config.Builder()
                    .setInterface(ifaceBuilder.build())
                    .addPeer(peerBuilder.build())
                    .build()

                // 4. Activate in-kernel / GoBackend tunnel
                connectionStartTime = System.currentTimeMillis()
                DanglingApplication.instance.wireGuardBackend.setState(tunnel, Tunnel.State.UP, config)
                apiClient.trackEvent("vpn_handshake", mapOf("node" to server.id))

            } catch (e: Exception) {
                _status.value = ConnectionStatus.DISCONNECTED
            }
        }
    }

    fun disconnect() {
        if (_status.value == ConnectionStatus.DISCONNECTED) return
        _status.value = ConnectionStatus.DISCONNECTING

        scope.launch(Dispatchers.IO) {
            try {
                DanglingApplication.instance.wireGuardBackend.setState(tunnel, Tunnel.State.DOWN, null)
            } catch (e: Exception) {
                _status.value = ConnectionStatus.DISCONNECTED
            }
        }
    }

    private fun startStatsMonitoring() {
        statsJob?.cancel()
        statsJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val backendStats = DanglingApplication.instance.wireGuardBackend.getStatistics(tunnel)
                    val elapsedSeconds = (System.currentTimeMillis() - connectionStartTime) / 1000
                    _stats.value = TunnelStats(
                        rxBytes = backendStats.totalRx(),
                        txBytes = backendStats.totalTx(),
                        durationSeconds = elapsedSeconds
                    )
                } catch (_: Exception) {}
                delay(1000)
            }
        }
    }

    private fun stopStatsMonitoring() {
        statsJob?.cancel()
        statsJob = null
        _stats.value = TunnelStats()
    }
}

