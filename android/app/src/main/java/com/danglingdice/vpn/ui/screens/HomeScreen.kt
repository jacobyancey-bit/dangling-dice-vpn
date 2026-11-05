package com.danglingdice.vpn.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danglingdice.vpn.R
import com.danglingdice.vpn.data.model.ConnectionStatus
import com.danglingdice.vpn.data.model.DnsProfile
import com.danglingdice.vpn.data.model.ServerNode
import com.danglingdice.vpn.data.model.TunnelStats
import com.danglingdice.vpn.ui.theme.*

@Composable
fun HomeScreen(
    status: ConnectionStatus,
    statusDetail: String = "",
    stats: TunnelStats,
    selectedServer: ServerNode,
    dnsProfile: DnsProfile = DnsProfile.ADGUARD_SHIELD,
    stealthModeEnabled: Boolean = true,
    isPremiumUser: Boolean = false,
    accountToken: String = "DD-ZERO-AUTH",
    onSelectDnsProfileClick: () -> Unit = {},
    onUpgradePremiumClick: () -> Unit = {},
    onAdBannerClick: () -> Unit = {},
    onPrivacyClick: () -> Unit = {},
    onToggleConnection: () -> Unit,
    onSelectServerClick: () -> Unit
) {
    val buttonBgColor by animateColorAsState(
        targetValue = when (status) {
            ConnectionStatus.CONNECTED -> Emerald500
            ConnectionStatus.CONNECTING -> Amber500
            ConnectionStatus.VERIFYING_HANDSHAKE -> Amber500
            ConnectionStatus.DISCONNECTING -> Amber500
            ConnectionStatus.ERROR -> Red500
            ConnectionStatus.DISCONNECTED -> Slate800
        },
        label = "ButtonColor"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate950)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onPrivacyClick() }
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_logo),
                    contentDescription = "Logo",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Dangling Dice VPN",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = if (isPremiumUser) "👑 PRO • $accountToken" else "FREE • $accountToken ⓘ",
                        fontSize = 12.sp,
                        color = if (isPremiumUser) Amber500 else Emerald500,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = when (status) {
                        ConnectionStatus.CONNECTED -> Emerald500.copy(alpha = 0.15f)
                        ConnectionStatus.ERROR -> Red500.copy(alpha = 0.15f)
                        ConnectionStatus.CONNECTING, ConnectionStatus.VERIFYING_HANDSHAKE -> Amber500.copy(alpha = 0.15f)
                        else -> Slate800
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        when (status) {
                            ConnectionStatus.CONNECTED -> Emerald500.copy(alpha = 0.4f)
                            ConnectionStatus.ERROR -> Red500.copy(alpha = 0.4f)
                            ConnectionStatus.CONNECTING, ConnectionStatus.VERIFYING_HANDSHAKE -> Amber500.copy(alpha = 0.4f)
                            else -> Color.Transparent
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = when (status) {
                                ConnectionStatus.CONNECTED -> Emerald500
                                ConnectionStatus.ERROR -> Red500
                                ConnectionStatus.CONNECTING, ConnectionStatus.VERIFYING_HANDSHAKE -> Amber500
                                else -> Slate400
                            },
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when (status) {
                                ConnectionStatus.CONNECTED -> "VERIFIED SECURE"
                                ConnectionStatus.VERIFYING_HANDSHAKE -> "HANDSHAKING..."
                                ConnectionStatus.CONNECTING -> "CONNECTING..."
                                ConnectionStatus.ERROR -> "CONNECTION ISSUE"
                                else -> "UNPROTECTED"
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (status) {
                                ConnectionStatus.CONNECTED -> Emerald500
                                ConnectionStatus.ERROR -> Red500
                                ConnectionStatus.CONNECTING, ConnectionStatus.VERIFYING_HANDSHAKE -> Amber500
                                else -> Slate400
                            }
                        )
                    }
                }

                if (!isPremiumUser) {
                    Surface(
                        onClick = onUpgradePremiumClick,
                        color = Amber500,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Remove Ads 👑",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // Top Section: Ad Banner (Free Users Only) + Download & Upload Speed Box
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Free-Tier Ad Banner (Positioned directly above Download Speed; removed on Premium Access)
            if (!isPremiumUser) {
                Surface(
                    color = Slate900,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Amber500.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAdBannerClick() }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Amber500.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "AD",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Amber500,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "☁️ DigitalOcean Cloud • $200 Free Credit",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Deploy high-speed VPS droplets in 60s ↗",
                                fontSize = 10.sp,
                                color = Slate400
                            )
                        }
                    }
                }
            }

            // Live Transfer Metrics (Download & Upload Box — Above Power Button)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Slate900, RoundedCornerShape(16.dp))
                    .border(1.dp, Slate800, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = Emerald500, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(text = "Download", fontSize = 12.sp, color = Slate400)
                        Text(text = formatBytes(stats.rxBytes), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                Divider(modifier = Modifier.height(34.dp).width(1.dp), color = Slate800)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = Amber500, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(text = "Upload", fontSize = 12.sp, color = Slate400)
                        Text(text = formatBytes(stats.txBytes), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        // Center Hero 1-Tap Connect Dial (Power Button)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(185.dp)
                    .clip(CircleShape)
                    .background(buttonBgColor.copy(alpha = 0.12f))
                    .border(
                        width = 4.dp,
                        color = buttonBgColor.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
                    .clickable { onToggleConnection() }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(138.dp)
                        .clip(CircleShape)
                        .background(buttonBgColor)
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = "Toggle VPN",
                        tint = if (status == ConnectionStatus.CONNECTED) Color.Black else Color.White,
                        modifier = Modifier.size(50.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = when (status) {
                    ConnectionStatus.CONNECTED -> "CONNECTED & VERIFIED"
                    ConnectionStatus.VERIFYING_HANDSHAKE -> "VERIFYING HANDSHAKE..."
                    ConnectionStatus.CONNECTING -> "REGISTERING KEY..."
                    ConnectionStatus.DISCONNECTING -> "DISCONNECTING..."
                    ConnectionStatus.ERROR -> "CONNECTION FAILED — TAP TO RETRY"
                    ConnectionStatus.DISCONNECTED -> "NOT CONNECTED"
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = when (status) {
                    ConnectionStatus.CONNECTED -> Emerald500
                    ConnectionStatus.ERROR -> Red500
                    ConnectionStatus.CONNECTING, ConnectionStatus.VERIFYING_HANDSHAKE -> Amber500
                    else -> Color.White
                }
            )

            if (statusDetail.isNotBlank()) {
                Text(
                    text = statusDetail,
                    fontSize = 12.sp,
                    color = if (status == ConnectionStatus.ERROR) Red500 else Slate400,
                    modifier = Modifier.padding(top = 4.dp, start = 12.dp, end = 12.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            if (status == ConnectionStatus.CONNECTED) {
                Text(
                    text = "${formatDuration(stats.durationSeconds)} • ${dnsProfile.title}" + if (stealthModeEnabled) " • Stealth" else "",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Emerald500,
                    modifier = Modifier.padding(top = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            } else if (status == ConnectionStatus.DISCONNECTED) {
                Text(
                    text = dnsProfile.title + if (stealthModeEnabled) " • Stealth" else "",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Slate400,
                    modifier = Modifier.padding(top = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        // Bottom Cards (Shield box above Location button on the bottom)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 4-Mode Security & Gaming DNS Profile Selector Card (Shield Box)
            Surface(
                onClick = onSelectDnsProfileClick,
                color = Slate900,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = dnsProfile.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "MODE ▾",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Emerald500,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = dnsProfile.subtitle,
                            fontSize = 12.sp,
                            color = Emerald500
                        )
                    }
                }
            }

            // Server Node Selection Card (Location Button on the Bottom)
            Surface(
                onClick = onSelectServerClick,
                color = Slate900,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Emerald500.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = selectedServer.flagEmoji, fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = selectedServer.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "CHANGE ▾",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Emerald500,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${selectedServer.city} • ${selectedServer.loadPercent}% Load",
                                fontSize = 12.sp,
                                color = Slate400
                            )
                        }
                    }

                    Surface(
                        color = Emerald500.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${selectedServer.pingMs}ms",
                            color = Emerald500,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format("%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}

fun formatDuration(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hrs > 0) String.format("%02d:%02d:%02d", hrs, mins, secs) else String.format("%02d:%02d", mins, secs)
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFF020617)
@Composable
fun HomeScreenPreview() {
    HomeScreen(
        status = ConnectionStatus.CONNECTED,
        stats = TunnelStats(rxBytes = 14_500_000, txBytes = 2_100_000, durationSeconds = 345),
        selectedServer = ServerNode("us-east-1", "US East (N. Virginia)", "10.0.0.1:51820", "pubkey123", true, 42),
        onToggleConnection = {},
        onSelectServerClick = {}
    )
}
