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

    private val _statusDetail = MutableStateFlow("Tap the power dial to establish an encrypted WireGuard tunnel")
    val statusDetail: StateFlow<String> = _statusDetail.asStateFlow()

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
                when (wireguardState) {
                    Tunnel.State.UP -> {
                        // Do NOT mark CONNECTED yet — verify real return bytes (RX > 0) first!
                        if (_status.value != ConnectionStatus.CONNECTED) {
                            _status.value = ConnectionStatus.VERIFYING_HANDSHAKE
                            _statusDetail.value = "Verifying cryptographic UDP handshake with 165.245.142.71:51820..."
                        }
                        startStatsAndHandshakeVerification()
                    }
                    Tunnel.State.DOWN -> {
                        stopStatsMonitoring()
                        if (_status.value != ConnectionStatus.ERROR) {
                            _status.value = ConnectionStatus.DISCONNECTED
                            _statusDetail.value = "Tunnel disconnected"
                        }
                    }
                    Tunnel.State.TOGGLE -> {
                        _status.value = ConnectionStatus.CONNECTING
                    }
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

    fun reportPermissionDenied() {
        _status.value = ConnectionStatus.ERROR
        _statusDetail.value = "Android VPN permission was denied. Please tap Connect and press OK."
    }

    fun connect(server: ServerNode) {
        if (_status.value == ConnectionStatus.CONNECTING || _status.value == ConnectionStatus.VERIFYING_HANDSHAKE) return
        _status.value = ConnectionStatus.CONNECTING
        _statusDetail.value = "Step 1/3: Registering Zero-Login hardware key (${_accountToken.value})..."

        scope.launch(Dispatchers.IO) {
            try {
                // 1. Register persistent device Curve25519 public key with server control plane
                val registration = apiClient.registerPeer(
                    publicKey = clientKeyPair.publicKey.toBase64(),
                    deviceName = "Android-${android.os.Build.MODEL}",
                    adBlockEnabled = _adBlockEnabled.value
                )

                // Never allow a false-positive connection if server registration fails
                if (registration == null || registration.second.isBlank()) {
                    _status.value = ConnectionStatus.ERROR
                    _statusDetail.value = "Connection Failed: Could not reach VPN Control Plane API (${server.endpoint}). Check internet connection."
                    return@launch
                }

                val assignedIp = registration.first
                val serverPubKey = registration.second
                val dnsTarget = if (_adBlockEnabled.value) "94.140.14.14" else "1.1.1.1"

                _statusDetail.value = "Step 2/3: Assigned VPN IP $assignedIp. Bringing up WireGuard interface..."

                // 2. Build WireGuard Configuration with Split-Tunneling & Ad-Block DNS support
                val ifaceBuilder = Interface.Builder()
                    .parsePrivateKey(clientKeyPair.privateKey.toBase64())
                    .addAddress(com.wireguard.config.InetNetwork.parse(assignedIp))
                    .addDnsServer(InetAddress.getByName(dnsTarget))

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

                // 3. Activate in-kernel / GoBackend tunnel (triggers Tunnel.State.UP -> VERIFYING_HANDSHAKE)
                connectionStartTime = System.currentTimeMillis()
                DanglingApplication.instance.wireGuardBackend.setState(tunnel, Tunnel.State.UP, config)

            } catch (e: Exception) {
                _status.value = ConnectionStatus.ERROR
                _statusDetail.value = "Tunnel Error: ${e.localizedMessage ?: "Failed to configure WireGuard interface"}"
            }
        }
    }

    fun disconnect() {
        if (_status.value == ConnectionStatus.DISCONNECTED) return
        _status.value = ConnectionStatus.DISCONNECTING
        _statusDetail.value = "Closing encrypted WireGuard tunnel..."

        scope.launch(Dispatchers.IO) {
            try {
                DanglingApplication.instance.wireGuardBackend.setState(tunnel, Tunnel.State.DOWN, null)
                _status.value = ConnectionStatus.DISCONNECTED
                _statusDetail.value = "Tunnel disconnected"
            } catch (e: Exception) {
                _status.value = ConnectionStatus.DISCONNECTED
                _statusDetail.value = "Tunnel disconnected"
            }
        }
    }

    private fun startStatsAndHandshakeVerification() {
        statsJob?.cancel()
        statsJob = scope.launch(Dispatchers.IO) {
            // Fire an active probe through the tunnel to force immediate two-way UDP handshake traffic
            launch {
                try {
                    apiClient.fetchDiagnostics()
                } catch (_: Exception) {}
            }

            var handshakeVerified = false
            val verifyDeadline = System.currentTimeMillis() + 9_000L // 9-second strict handshake window

            while (isActive) {
                try {
                    val backendStats = DanglingApplication.instance.wireGuardBackend.getStatistics(tunnel)
                    val rx = backendStats.totalRx()
                    val tx = backendStats.totalTx()
                    val elapsedSeconds = (System.currentTimeMillis() - connectionStartTime) / 1000

                    _stats.value = TunnelStats(
                        rxBytes = rx,
                        txBytes = tx,
                        durationSeconds = elapsedSeconds
                    )

                    if (!handshakeVerified) {
                        if (rx > 0L) {
                            // Cryptographic proof: the WireGuard server decrypted our packet and replied!
                            handshakeVerified = true
                            connectionStartTime = System.currentTimeMillis()
                            _status.value = ConnectionStatus.CONNECTED
                            _statusDetail.value = "Verified: Encrypted traffic flowing via 165.245.142.71:51820"
                            launch { apiClient.trackEvent("vpn_handshake", mapOf("verified_rx" to rx.toString())) }
                        } else if (System.currentTimeMillis() > verifyDeadline) {
                            // Zero bytes received after 9 seconds -> Tear down tunnel so phone internet isn't blackholed!
                            _status.value = ConnectionStatus.ERROR
                            _statusDetail.value = "Handshake Timeout: Sent $tx B to 165.245.142.71:51820, received 0 B back. Sync server wg0 interface."
                            DanglingApplication.instance.wireGuardBackend.setState(tunnel, Tunnel.State.DOWN, null)
                            break
                        }
                    }
                } catch (_: Exception) {}
                delay(500)
            }
        }
    }

    private fun stopStatsMonitoring() {
        statsJob?.cancel()
        _stats.value = TunnelStats(0, 0, 0)
    }
}
