package com.danglingdice.vpn.ui.screens

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danglingdice.vpn.data.model.ConnectionStatus
import com.danglingdice.vpn.data.model.ServerNode
import com.danglingdice.vpn.data.model.TunnelStats
import com.danglingdice.vpn.ui.theme.*

@Composable
fun HomeScreen(
    status: ConnectionStatus,
    stats: TunnelStats,
    selectedServer: ServerNode,
    onToggleConnection: () -> Unit,
    onSelectServerClick: () -> Unit
) {
    val buttonBgColor by animateColorAsState(
        targetValue = when (status) {
            ConnectionStatus.CONNECTED -> Emerald500
            ConnectionStatus.CONNECTING -> Amber500
            ConnectionStatus.DISCONNECTING -> Amber500
            ConnectionStatus.DISCONNECTED -> Slate800
        },
        label = "ButtonColor"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate950)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🎲", fontSize = 28.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Dangling Dice",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Zero-Login • Hardware Key Auth",
                        fontSize = 11.sp,
                        color = Emerald500,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Surface(
                color = if (status == ConnectionStatus.CONNECTED) Emerald500.copy(alpha = 0.15f) else Slate800,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (status == ConnectionStatus.CONNECTED) Emerald500.copy(alpha = 0.4f) else Color.Transparent
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = if (status == ConnectionStatus.CONNECTED) Emerald500 else Slate400,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (status == ConnectionStatus.CONNECTED) "PROTECTED" else "UNPROTECTED",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (status == ConnectionStatus.CONNECTED) Emerald500 else Slate400
                    )
                }
            }
        }

        // Center Hero 1-Tap Connect Dial
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 32.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(200.dp)
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
                        .size(150.dp)
                        .clip(CircleShape)
                        .background(buttonBgColor)
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = "Toggle VPN",
                        tint = if (status == ConnectionStatus.CONNECTED) Color.Black else Color.White,
                        modifier = Modifier.size(54.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = when (status) {
                    ConnectionStatus.CONNECTED -> "CONNECTED"
                    ConnectionStatus.CONNECTING -> "CONNECTING..."
                    ConnectionStatus.DISCONNECTING -> "DISCONNECTING..."
                    ConnectionStatus.DISCONNECTED -> "NOT CONNECTED"
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            if (status == ConnectionStatus.CONNECTED) {
                Text(
                    text = formatDuration(stats.durationSeconds),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Slate400,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Bottom Stats & Server Card
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Live Transfer Metrics
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Slate900, RoundedCornerShape(16.dp))
                    .border(1.dp, Slate800, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = Emerald500, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(text = "Download", fontSize = 11.sp, color = Slate400)
                        Text(text = formatBytes(stats.rxBytes), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                Divider(modifier = Modifier.height(36.dp).width(1.dp), color = Slate800)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = Amber500, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(text = "Upload", fontSize = 11.sp, color = Slate400)
                        Text(text = formatBytes(stats.txBytes), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            // Server Node Selection Card
            Surface(
                onClick = onSelectServerClick,
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🇺🇸", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = selectedServer.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(text = selectedServer.endpoint, fontSize = 12.sp, color = Slate400, fontFamily = FontFamily.Monospace)
                        }
                    }

                    Surface(
                        color = Emerald500.copy(alpha = 0.1f),
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

