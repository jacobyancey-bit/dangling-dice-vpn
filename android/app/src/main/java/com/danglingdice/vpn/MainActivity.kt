package com.danglingdice.vpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.danglingdice.vpn.data.model.ConnectionStatus
import com.danglingdice.vpn.data.model.ServerNode
import com.danglingdice.vpn.ui.screens.*
import com.danglingdice.vpn.ui.theme.*
import com.danglingdice.vpn.vpn.WireGuardTunnelManager

class MainActivity : ComponentActivity() {

    private lateinit var tunnelManager: WireGuardTunnelManager
    private var pendingServerToConnect: ServerNode? = null

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingServerToConnect?.let { server ->
                tunnelManager.connect(server)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tunnelManager = WireGuardTunnelManager(applicationContext)

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
    var selectedTab by remember { mutableStateOf(0) }
    val status by tunnelManager.status.collectAsState()
    val stats by tunnelManager.stats.collectAsState()
    val excludedApps by tunnelManager.excludedApps.collectAsState()

    var selectedServer by remember {
        mutableStateOf(
            ServerNode(
                id = "us-east-1",
                name = "US East Gateway (Primary)",
                endpoint = "165.245.142.71:51820",
                publicKey = "xTpevhK8n+3m9+yH8OaJ6uV0cQw4Mv+B9x7pY6s=",
                isFreeTier = true,
                pingMs = 18
            )
        )
    }

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
                    icon = { Icon(Icons.Default.AltRoute, contentDescription = "Split") },
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
                    icon = { Icon(Icons.Default.Security, contentDescription = "Kill Switch") },
                    label = { Text("Kill Switch") },
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
                    label = { Text("Diagnostics") },
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
                    stats = stats,
                    selectedServer = selectedServer,
                    onToggleConnection = {
                        if (status == ConnectionStatus.CONNECTED) {
                            tunnelManager.disconnect()
                        } else {
                            onConnectRequested(selectedServer)
                        }
                    },
                    onSelectServerClick = { /* Can show server selection bottom sheet */ }
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
                    onBackClick = { selectedTab = 0 }
                )
                3 -> DiagnosticsScreen(
                    onBackClick = { selectedTab = 0 }
                )
            }
        }
    }
}

