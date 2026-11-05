package com.danglingdice.vpn.data.model

data class ServerNode(
    val id: String,
    val name: String,
    val endpoint: String,
    val publicKey: String,
    val isFreeTier: Boolean = true,
    val pingMs: Int = 22,
    val flagEmoji: String = "🇺🇸",
    val city: String = "Atlanta / NYC",
    val loadPercent: Int = 14
)

fun defaultServerLocations(endpoint: String = "165.245.142.71:51820", pubKey: String = ""): List<ServerNode> = listOf(
    ServerNode(
        id = "us-east-1",
        name = "United States • East",
        city = "Atlanta / New York",
        endpoint = endpoint,
        publicKey = pubKey,
        isFreeTier = true,
        pingMs = 18,
        flagEmoji = "🇺🇸",
        loadPercent = 12
    ),
    ServerNode(
        id = "us-west-1",
        name = "United States • West",
        city = "San Francisco / Silicon Valley",
        endpoint = endpoint,
        publicKey = pubKey,
        isFreeTier = true,
        pingMs = 46,
        flagEmoji = "🇺🇸",
        loadPercent = 24
    ),
    ServerNode(
        id = "eu-uk-1",
        name = "United Kingdom • London",
        city = "London Docklands Relay",
        endpoint = endpoint,
        publicKey = pubKey,
        isFreeTier = true,
        pingMs = 78,
        flagEmoji = "🇬🇧",
        loadPercent = 31
    ),
    ServerNode(
        id = "eu-de-1",
        name = "Germany • Frankfurt",
        city = "Frankfurt EU Privacy Hub",
        endpoint = endpoint,
        publicKey = pubKey,
        isFreeTier = true,
        pingMs = 89,
        flagEmoji = "🇩🇪",
        loadPercent = 19
    ),
    ServerNode(
        id = "ap-jp-1",
        name = "Japan • Tokyo",
        city = "Tokyo Asia-Pacific Edge",
        endpoint = endpoint,
        publicKey = pubKey,
        isFreeTier = true,
        pingMs = 142,
        flagEmoji = "🇯🇵",
        loadPercent = 28
    )
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
