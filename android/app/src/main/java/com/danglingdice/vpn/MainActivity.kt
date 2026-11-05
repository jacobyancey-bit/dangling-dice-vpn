package com.danglingdice.vpn

import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.danglingdice.vpn.data.model.ConnectionStatus
import com.danglingdice.vpn.data.model.DnsProfile
import com.danglingdice.vpn.data.model.ServerNode
import com.danglingdice.vpn.ui.screens.DiagnosticsScreen
import com.danglingdice.vpn.ui.screens.HomeScreen
import com.danglingdice.vpn.ui.screens.KillSwitchScreen
import com.danglingdice.vpn.ui.screens.SplitTunnelScreen
import com.danglingdice.vpn.ui.theme.Amber500
import com.danglingdice.vpn.ui.theme.DanglingDiceTheme
import com.danglingdice.vpn.ui.theme.Emerald500
import com.danglingdice.vpn.ui.theme.Slate400
import com.danglingdice.vpn.ui.theme.Slate800
import com.danglingdice.vpn.ui.theme.Slate900
import com.danglingdice.vpn.ui.theme.Slate950
import com.danglingdice.vpn.vpn.WireGuardTunnelManager

class MainActivity : ComponentActivity() {

    private val tunnelManager: WireGuardTunnelManager
        get() = DanglingApplication.instance.tunnelManager

    private var pendingServerToConnect: ServerNode? = null

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            pendingServerToConnect?.let { server ->
                tunnelManager.connect(server)
            }
        } else {
            tunnelManager.reportPermissionDenied()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            DanglingDiceTheme {
                MainAppScaffold(
                    tunnelManager = tunnelManager,
                    onConnectRequested = { server ->
                        val vpnIntent = VpnService.prepare(this@MainActivity)
                        if (vpnIntent != null) {
                            pendingServerToConnect = server
                            vpnPermissionLauncher.launch(vpnIntent)
                        } else {
                            tunnelManager.connect(server)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun MainAppScaffold(
    tunnelManager: WireGuardTunnelManager,
    onConnectRequested: (ServerNode) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showLocationPicker by remember { mutableStateOf(false) }
    var showDnsPicker by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showPremiumDialog by remember { mutableStateOf(false) }

    val status by tunnelManager.status.collectAsState()
    val statusDetail by tunnelManager.statusDetail.collectAsState()
    val stats by tunnelManager.stats.collectAsState()
    val excludedApps by tunnelManager.excludedApps.collectAsState()
    val availableServers by tunnelManager.servers.collectAsState()
    val selectedServer by tunnelManager.selectedServer.collectAsState()
    val dnsProfile by tunnelManager.dnsProfile.collectAsState()
    val stealthModeEnabled by tunnelManager.stealthModeEnabled.collectAsState()
    val autoConnectWifiShield by tunnelManager.autoConnectWifiShield.collectAsState()
    val isPremiumUser by tunnelManager.isPremiumUser.collectAsState()
    val accountToken by tunnelManager.accountToken.collectAsState()
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Slate900,
                contentColor = Color.White
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("VPN") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Emerald500,
                        selectedTextColor = Emerald500,
                        unselectedIconColor = Slate400,
                        unselectedTextColor = Slate400,
                        indicatorColor = Emerald500.copy(alpha = 0.15f)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.AutoMirrored.Filled.AltRoute, contentDescription = "Split") },
                    label = { Text("Split Tunnel") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Emerald500,
                        selectedTextColor = Emerald500,
                        unselectedIconColor = Slate400,
                        unselectedTextColor = Slate400,
                        indicatorColor = Emerald500.copy(alpha = 0.15f)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Security, contentDescription = "Shield") },
                    label = { Text("Shields") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Emerald500,
                        selectedTextColor = Emerald500,
                        unselectedIconColor = Slate400,
                        unselectedTextColor = Slate400,
                        indicatorColor = Emerald500.copy(alpha = 0.15f)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Speed, contentDescription = "Diagnostics") },
                    label = { Text("Speed & Leak") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Emerald500,
                        selectedTextColor = Emerald500,
                        unselectedIconColor = Slate400,
                        unselectedTextColor = Slate400,
                        indicatorColor = Emerald500.copy(alpha = 0.15f)
                    )
                )
            }
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier.padding(paddingValues),
            color = Slate950
        ) {
            when (selectedTab) {
                0 -> HomeScreen(
                    status = status,
                    statusDetail = statusDetail,
                    stats = stats,
                    selectedServer = selectedServer,
                    dnsProfile = dnsProfile,
                    stealthModeEnabled = stealthModeEnabled,
                    isPremiumUser = isPremiumUser,
                    accountToken = accountToken,
                    onSelectDnsProfileClick = { showDnsPicker = true },
                    onUpgradePremiumClick = { showPremiumDialog = true },
                    onAdBannerClick = { uriHandler.openUri("https://www.digitalocean.com") },
                    onPrivacyClick = { showPrivacyDialog = true },
                    onToggleConnection = {
                        if (status == ConnectionStatus.CONNECTED ||
                            status == ConnectionStatus.VERIFYING_HANDSHAKE ||
                            status == ConnectionStatus.CONNECTING
                        ) {
                            tunnelManager.disconnect()
                        } else {
                            onConnectRequested(selectedServer)
                        }
                    },
                    onSelectServerClick = { showLocationPicker = true }
                )
                1 -> SplitTunnelScreen(
                    excludedApps = excludedApps,
                    onToggleAppExclusion = { pkg ->
                        val updated = if (excludedApps.contains(pkg)) {
                            excludedApps - pkg
                        } else {
                            excludedApps + pkg
                        }
                        tunnelManager.setExcludedApps(updated)
                    },
                    onBackClick = { selectedTab = 0 }
                )
                2 -> KillSwitchScreen(
                    stealthModeEnabled = stealthModeEnabled,
                    autoConnectWifiShield = autoConnectWifiShield,
                    onToggleStealthMode = { tunnelManager.setStealthModeEnabled(it) },
                    onToggleAutoConnectWifi = { tunnelManager.setAutoConnectWifiShield(it) },
                    onBackClick = { selectedTab = 0 }
                )
                3 -> DiagnosticsScreen(
                    onBackClick = { selectedTab = 0 }
                )
            }

            // 1. Global VPN Location Picker Modal
            if (showLocationPicker) {
                AlertDialog(
                    onDismissRequest = { showLocationPicker = false },
                    containerColor = Slate900,
                    titleContentColor = Color.White,
                    title = {
                        androidx.compose.foundation.layout.Column {
                            Text(
                                text = "🌍 Select VPN Location",
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Choose your encrypted WireGuard gateway region",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate400
                            )
                        }
                    },
                    text = {
                        androidx.compose.foundation.layout.Column(
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            availableServers.forEach { node ->
                                val isSelected = node.id == selectedServer.id
                                Surface(
                                    onClick = {
                                        showLocationPicker = false
                                        tunnelManager.selectServerLocation(node)
                                    },
                                    color = if (isSelected) Emerald500.copy(alpha = 0.14f) else Slate950,
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) Emerald500 else Slate800
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    androidx.compose.foundation.layout.Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
                                    ) {
                                        androidx.compose.foundation.layout.Row(
                                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                        ) {
                                            Text(text = node.flagEmoji, style = MaterialTheme.typography.headlineSmall)
                                            androidx.compose.foundation.layout.Spacer(
                                                modifier = Modifier.padding(horizontal = 5.dp)
                                            )
                                            androidx.compose.foundation.layout.Column {
                                                Text(
                                                    text = node.name,
                                                    color = if (isSelected) Emerald500 else Color.White,
                                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                                Text(
                                                    text = "${node.city} • ${node.loadPercent}% Load",
                                                    color = Slate400,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                        Text(
                                            text = "${node.pingMs}ms",
                                            color = Emerald500,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showLocationPicker = false }) {
                            Text("Close", color = Emerald500)
                        }
                    }
                )
            }

            // 2. Multi-Profile Security & Gaming DNS Picker Modal
            if (showDnsPicker) {
                AlertDialog(
                    onDismissRequest = { showDnsPicker = false },
                    containerColor = Slate900,
                    titleContentColor = Color.White,
                    title = {
                        androidx.compose.foundation.layout.Column {
                            Text(
                                text = "🛡️ Select Security & DNS Mode",
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Customize how your tunnel filters ads, malware, or gaming ping",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate400
                            )
                        }
                    },
                    text = {
                        androidx.compose.foundation.layout.Column(
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            DnsProfile.entries.forEach { profile ->
                                val isSelected = profile == dnsProfile
                                Surface(
                                    onClick = {
                                        showDnsPicker = false
                                        tunnelManager.setDnsProfile(profile)
                                    },
                                    color = if (isSelected) Emerald500.copy(alpha = 0.14f) else Slate950,
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) Emerald500 else Slate800
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    androidx.compose.foundation.layout.Column(
                                        modifier = Modifier.padding(12.dp)
                                    ) {
                                        Text(
                                            text = profile.title,
                                            color = if (isSelected) Emerald500 else Color.White,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = profile.subtitle,
                                            color = Slate400,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showDnsPicker = false }) {
                            Text("Close", color = Emerald500)
                        }
                    }
                )
            }

            // 3. Zero-Login Identity & Google Play VpnService Privacy Disclosure Modal
            if (showPrivacyDialog) {
                AlertDialog(
                    onDismissRequest = { showPrivacyDialog = false },
                    containerColor = Slate900,
                    titleContentColor = Color.White,
                    title = {
                        Text(
                            text = "🛡️ Zero-Login Identity & Privacy",
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = Color.White
                        )
                    },
                    text = {
                        androidx.compose.foundation.layout.Column(
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Anonymous Hardware Passkey: $accountToken",
                                color = Emerald500,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "Dangling Dice VPN uses the Android VpnService API to create an encrypted WireGuard (ChaCha20-Poly1305) tunnel to our gateway servers. Your Curve25519 private key never leaves this device.",
                                color = Slate400,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "• Zero Activity Logs: We never log browsing history, DNS queries, or traffic contents.\n• Quick Settings Tile: Pull down your Android notification shade to add the 1-tap Dangling VPN tile!",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Surface(
                                onClick = {
                                    showPrivacyDialog = false
                                    showPremiumDialog = true
                                },
                                color = Amber500.copy(alpha = 0.15f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Amber500.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (isPremiumUser) "👑 Status: Premium Pro Active (Tap to Manage)" else "👑 Status: Free Tier (Tap to Upgrade to Pro)",
                                    color = Amber500,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showPrivacyDialog = false
                                uriHandler.openUri("https://vpn.danglingdicestudios.duckdns.org/privacy")
                            }
                        ) {
                            Text("Privacy Policy ↗", color = Emerald500)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showPrivacyDialog = false }) {
                            Text("Done", color = Color.White)
                        }
                    }
                )
            }

            // 4. Premium Account Access & Ad-Removal Upgrade Modal
            if (showPremiumDialog) {
                AlertDialog(
                    onDismissRequest = { showPremiumDialog = false },
                    containerColor = Slate900,
                    titleContentColor = Color.White,
                    title = {
                        androidx.compose.foundation.layout.Column {
                            Text(
                                text = "👑 Dangling Dice VPN Pro",
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = Amber500
                            )
                            Text(
                                text = "Zero-Login Hardware Passkey: $accountToken",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = Emerald500
                            )
                        }
                    },
                    text = {
                        androidx.compose.foundation.layout.Column(
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Upgrade your hardware key to Premium Pro Access — no email or password required:",
                                color = Slate400,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "✅ Remove All Banner Ads (100% Clean Home Screen)\n✅ Priority 2 Gbps WireGuard Port Bandwidth\n✅ Unlock All 5 Global Relay Locations\n✅ 4-Mode Threat & Gaming DNS + Stealth Anti-DPI",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    dismissButton = {
                        if (isPremiumUser) {
                            TextButton(
                                onClick = {
                                    tunnelManager.setPremiumStatus(false)
                                    showPremiumDialog = false
                                }
                            ) {
                                Text("Switch to Free Tier (Test Ads)", color = Slate400)
                            }
                        } else {
                            TextButton(onClick = { showPremiumDialog = false }) {
                                Text("Keep Free Tier", color = Slate400)
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                tunnelManager.setPremiumStatus(true)
                                showPremiumDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Amber500)
                        ) {
                            Text(
                                text = if (isPremiumUser) "👑 Pro Active" else "Activate Pro ($2.99/mo)",
                                color = Color.Black,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        }
                    }
                )
            }
        }
    }
}
