package com.danglingdice.vpn.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danglingdice.vpn.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KillSwitchScreen(
    stealthModeEnabled: Boolean = true,
    autoConnectWifiShield: Boolean = false,
    onToggleStealthMode: (Boolean) -> Unit = {},
    onToggleAutoConnectWifi: (Boolean) -> Unit = {},
    onBackClick: () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security & Shield Center", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Slate950)
            )
        },
        containerColor = Slate950
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. Stealth / Campus & Hotel Wi-Fi Bypass Mode
            Surface(
                color = Slate900,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Emerald500.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "🥷 Stealth Anti-DPI Mode (Campus/Hotel Bypass)",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Sets WireGuard packet MTU to 1280 bytes with 15s NAT keepalives to punch through strict school, workplace, and hotel firewalls.",
                            color = Slate400,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = stealthModeEnabled,
                        onCheckedChange = onToggleStealthMode,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Emerald500
                        )
                    )
                }
            }

            // 2. Public Wi-Fi Auto-Connect Shield
            Surface(
                color = Slate900,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "📶 Public Wi-Fi Auto-Shield",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Automatically activates your encrypted WireGuard tunnel whenever your phone joins a Wi-Fi network.",
                            color = Slate400,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = autoConnectWifiShield,
                        onCheckedChange = onToggleAutoConnectWifi,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Emerald500
                        )
                    )
                }
            }

            // 3. System-Level Always-On VPN Kill Switch
            Surface(
                color = Slate900,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(Emerald500.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                .border(1.dp, Emerald500.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = Emerald500, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Hardware Always-On Kill Switch",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Zero-Leak OS Enforcement",
                                fontSize = 12.sp,
                                color = Emerald500
                            )
                        }
                    }

                    Text(
                        text = "Blocks all unencrypted internet traffic at the Android OS level if the VPN tunnel ever drops unexpectedly.",
                        fontSize = 13.sp,
                        color = Slate400
                    )

                    Text(text = "1. Tap below to open Android VPN Settings.\n2. Tap the Gear icon next to Dangling Dice.\n3. Enable 'Always-on VPN' & 'Block connections without VPN'.", color = Color.White, fontSize = 12.sp)

                    Button(
                        onClick = {
                            try {
                                val intent = Intent("android.net.vpn.SETTINGS")
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                val fallback = Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)
                                context.startActivity(fallback)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald500)
                    ) {
                        Text(
                            text = "Open Android VPN Kill Switch Settings",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
