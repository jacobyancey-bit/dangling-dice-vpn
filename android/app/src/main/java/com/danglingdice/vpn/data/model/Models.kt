package com.danglingdice.vpn.data.model

data class ServerNode(
    val id: String,
    val name: String,
    val endpoint: String,
    val publicKey: String,
    val isFreeTier: Boolean = true,
    val pingMs: Int = 22
)

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    VERIFYING_HANDSHAKE,
    CONNECTED,
    DISCONNECTING,
    ERROR
}

data class TunnelStats(
    val rxBytes: Long = 0,
    val txBytes: Long = 0,
    val durationSeconds: Long = 0
)

data class AppItem(
    val name: String,
    val packageName: String,
    val isExcluded: Boolean = false
)

data class LeakReport(
    val clientIp: String = "Detecting...",
    val isVpnSecured: Boolean = false,
    val serverNode: String = "Direct Link"
)
