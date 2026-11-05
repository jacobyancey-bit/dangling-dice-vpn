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

    private var clientKeyPair: KeyPair = KeyPair()
    private var statsJob: Job? = null
    private var connectionStartTime: Long = 0

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

    fun connect(server: ServerNode) {
        if (_status.value != ConnectionStatus.DISCONNECTED) return
        _status.value = ConnectionStatus.CONNECTING

        scope.launch(Dispatchers.IO) {
            try {
                // 1. Generate new client cryptographic keypair
                clientKeyPair = KeyPair()

                // 2. Register public key with server control plane
                val registration = apiClient.registerPeer(
                    publicKey = clientKeyPair.publicKey.toBase64(),
                    deviceName = "Android-${android.os.Build.MODEL}"
                )

                val assignedIp = registration?.first ?: "10.8.0.2/24"
                val serverPubKey = registration?.second ?: server.publicKey

                // 3. Build WireGuard Configuration with Split-Tunneling support
                val ifaceBuilder = Interface.Builder()
                    .parsePrivateKey(clientKeyPair.privateKey.toBase64())
                    .addAddress(com.wireguard.config.InetNetwork.parse(assignedIp))
                    .addDnsServer(InetAddress.getByName("1.1.1.1"))

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
