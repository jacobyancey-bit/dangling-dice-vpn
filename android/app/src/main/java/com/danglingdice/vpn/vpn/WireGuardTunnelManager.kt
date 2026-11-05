package com.danglingdice.vpn.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import com.danglingdice.vpn.DanglingApplication
import com.danglingdice.vpn.data.api.DanglingApiClient
import com.danglingdice.vpn.data.model.ConnectionStatus
import com.danglingdice.vpn.data.model.DnsProfile
import com.danglingdice.vpn.data.model.ServerNode
import com.danglingdice.vpn.data.model.TunnelStats
import com.danglingdice.vpn.data.model.defaultServerLocations
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

    // Persistent Zero-Login Cryptographic Device Keypair & Premium Settings Vault
    private val prefs = context.getSharedPreferences("dangling_zero_login_vault", Context.MODE_PRIVATE)
    private val clientKeyPair: KeyPair = loadOrGeneratePersistentKeyPair()

    private val _accountToken = MutableStateFlow(deriveZeroLoginToken(clientKeyPair.publicKey.toBase64()))
    val accountToken: StateFlow<String> = _accountToken.asStateFlow()

    private val _excludedApps = MutableStateFlow<Set<String>>(
        prefs.getStringSet("split_tunnel_excluded_apps", emptySet()) ?: emptySet()
    )
    val excludedApps: StateFlow<Set<String>> = _excludedApps.asStateFlow()

    private val _dnsProfile = MutableStateFlow(
        DnsProfile.fromId(prefs.getString("selected_dns_profile", DnsProfile.ADGUARD_SHIELD.id))
    )
    val dnsProfile: StateFlow<DnsProfile> = _dnsProfile.asStateFlow()

    // Stealth / Campus & Hotel Wi-Fi Bypass (Anti-DPI MTU 1280 + 15s NAT Keepalive)
    private val _stealthModeEnabled = MutableStateFlow(
        prefs.getBoolean("stealth_anti_dpi_enabled", true)
    )
    val stealthModeEnabled: StateFlow<Boolean> = _stealthModeEnabled.asStateFlow()

    // Public Wi-Fi Auto-Connect Shield
    private val _autoConnectWifiShield = MutableStateFlow(
        prefs.getBoolean("auto_connect_wifi_shield", false)
    )
    val autoConnectWifiShield: StateFlow<Boolean> = _autoConnectWifiShield.asStateFlow()

    // Premium Account Access Tier (Removes Ad Banner & Unlocks Pro Perks)
    private val _isPremiumUser = MutableStateFlow(
        prefs.getBoolean("is_premium_pro_tier", false)
    )
    val isPremiumUser: StateFlow<Boolean> = _isPremiumUser.asStateFlow()

    private val _servers = MutableStateFlow(defaultServerLocations())
    val servers: StateFlow<List<ServerNode>> = _servers.asStateFlow()

    private val _selectedServer = MutableStateFlow(defaultServerLocations().first())
    val selectedServer: StateFlow<ServerNode> = _selectedServer.asStateFlow()

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
        // 1. Fetch live server gateway registry
        scope.launch {
            try {
                val remoteServers = apiClient.fetchServers()
                if (remoteServers.isNotEmpty()) {
                    _servers.value = remoteServers
                    val savedId = prefs.getString("selected_server_id", null)
                    val matched = remoteServers.find { it.id == savedId } ?: remoteServers.first()
                    _selectedServer.value = matched
                }
            } catch (_: Exception) {}
        }

        // 2. Observe WireGuard tunnel state
        scope.launch {
            tunnel.state.collect { wireguardState ->
                when (wireguardState) {
                    Tunnel.State.UP -> {
                        if (_status.value != ConnectionStatus.CONNECTED) {
                            _status.value = ConnectionStatus.VERIFYING_HANDSHAKE
                            _statusDetail.value = "Verifying cryptographic UDP handshake with ${_selectedServer.value.endpoint}..."
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

        // 3. Register Public Wi-Fi Auto-Connect Shield Monitor
        registerWifiAutoConnectMonitor()
    }

    private fun registerWifiAutoConnectMonitor() {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (_autoConnectWifiShield.value &&
                        _status.value == ConnectionStatus.DISCONNECTED &&
                        VpnService.prepare(context) == null
                    ) {
                        _statusDetail.value = "Wi-Fi Shield: Auto-protecting Wi-Fi connection..."
                        connect(_selectedServer.value)
                    }
                }
            })
        } catch (_: Exception) {}
    }

    fun setExcludedApps(apps: Set<String>) {
        _excludedApps.value = apps
        prefs.edit().putStringSet("split_tunnel_excluded_apps", apps).apply()
    }

    fun setDnsProfile(profile: DnsProfile) {
        _dnsProfile.value = profile
        prefs.edit().putString("selected_dns_profile", profile.id).apply()
        scope.launch(Dispatchers.IO) {
            apiClient.trackEvent("dns_profile_selected", mapOf("profile" to profile.id))
            if (_status.value == ConnectionStatus.CONNECTED) {
                _statusDetail.value = "Applying ${profile.title}..."
                try {
                    DanglingApplication.instance.wireGuardBackend.setState(tunnel, Tunnel.State.DOWN, null)
                } catch (_: Exception) {}
                delay(350)
                connect(_selectedServer.value)
            }
        }
    }

    fun setStealthModeEnabled(enabled: Boolean) {
        _stealthModeEnabled.value = enabled
        prefs.edit().putBoolean("stealth_anti_dpi_enabled", enabled).apply()
    }

    fun setAutoConnectWifiShield(enabled: Boolean) {
        _autoConnectWifiShield.value = enabled
        prefs.edit().putBoolean("auto_connect_wifi_shield", enabled).apply()
    }

    fun setPremiumStatus(isPremium: Boolean) {
        _isPremiumUser.value = isPremium
        prefs.edit().putBoolean("is_premium_pro_tier", isPremium).apply()
        scope.launch(Dispatchers.IO) {
            apiClient.trackEvent(
                if (isPremium) "premium_activated" else "premium_deactivated",
                mapOf("account_token" to _accountToken.value)
            )
        }
    }

    fun selectServerLocation(server: ServerNode) {
        _selectedServer.value = server
        prefs.edit().putString("selected_server_id", server.id).apply()
        scope.launch(Dispatchers.IO) {
            apiClient.trackEvent("location_selected", mapOf("region_id" to server.id, "region_name" to server.name))
            if (_status.value == ConnectionStatus.CONNECTED) {
                _statusDetail.value = "Switching tunnel location to ${server.name} (${server.city})..."
                try {
                    DanglingApplication.instance.wireGuardBackend.setState(tunnel, Tunnel.State.DOWN, null)
                } catch (_: Exception) {}
                delay(400)
                connect(server)
            } else {
                _statusDetail.value = "Selected ${server.flagEmoji} ${server.name} (${server.city}) — Tap power dial to connect"
            }
        }
    }

    fun reportPermissionDenied() {
        _status.value = ConnectionStatus.ERROR
        _statusDetail.value = "Android VPN permission was denied. Please tap Connect and press OK."
    }

    fun connect(server: ServerNode = _selectedServer.value) {
        if (_status.value == ConnectionStatus.CONNECTING || _status.value == ConnectionStatus.VERIFYING_HANDSHAKE) return
        _selectedServer.value = server
        _status.value = ConnectionStatus.CONNECTING
        _statusDetail.value = "Step 1/3: Registering Zero-Login hardware key (${_accountToken.value})..."

        scope.launch(Dispatchers.IO) {
            try {
                val activeDns = _dnsProfile.value
                val isAdBlock = activeDns == DnsProfile.ADGUARD_SHIELD

                // 1. Register persistent device Curve25519 public key with server control plane
                val registration = apiClient.registerPeer(
                    publicKey = clientKeyPair.publicKey.toBase64(),
                    deviceName = "Android-${android.os.Build.MODEL}",
                    adBlockEnabled = isAdBlock,
                    dnsProfile = activeDns.id
                )

                // Never allow a false-positive connection if server registration fails
                if (registration == null || registration.second.isBlank()) {
                    _status.value = ConnectionStatus.ERROR
                    _statusDetail.value = "Connection Failed: Could not reach VPN Control Plane API (${server.endpoint}). Check internet connection."
                    return@launch
                }

                val assignedIp = registration.first
                val serverPubKey = registration.second
                val dnsTarget = activeDns.primaryDns

                _statusDetail.value = "Step 2/3: Assigned VPN IP $assignedIp. Bringing up WireGuard interface..."

                // 2. Build WireGuard Configuration with Split-Tunneling, Multi-Profile DNS & Stealth Anti-DPI MTU
                val ifaceBuilder = Interface.Builder()
                    .parsePrivateKey(clientKeyPair.privateKey.toBase64())
                    .addAddress(com.wireguard.config.InetNetwork.parse(assignedIp))
                    .addDnsServer(InetAddress.getByName(dnsTarget))

                if (_stealthModeEnabled.value) {
                    // Anti-DPI Stealth MTU (1280 bytes prevents fragmentation drops on strict campus/hotel firewalls)
                    ifaceBuilder.setMtu(1280)
                }

                if (_excludedApps.value.isNotEmpty()) {
                    ifaceBuilder.excludeApplications(_excludedApps.value)
                }

                val keepAliveSeconds = if (_stealthModeEnabled.value) 15 else 25
                val peerBuilder = Peer.Builder()
                    .parsePublicKey(serverPubKey)
                    .parseEndpoint(server.endpoint)
                    .parseAllowedIPs("0.0.0.0/0, ::/0")
                    .setPersistentKeepalive(keepAliveSeconds)

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
                            handshakeVerified = true
                            connectionStartTime = System.currentTimeMillis()
                            _status.value = ConnectionStatus.CONNECTED
                            val modeTag = if (_stealthModeEnabled.value) "Stealth MTU 1280" else "Standard"
                            _statusDetail.value = "Verified ($modeTag): Traffic encrypted via ${_selectedServer.value.name}"
                            launch { apiClient.trackEvent("vpn_handshake", mapOf("verified_rx" to rx.toString())) }
                        } else if (System.currentTimeMillis() > verifyDeadline) {
                            _status.value = ConnectionStatus.ERROR
                            _statusDetail.value = "Handshake Timeout: Sent $tx B to ${_selectedServer.value.endpoint}, received 0 B back. Sync server wg0 interface."
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
