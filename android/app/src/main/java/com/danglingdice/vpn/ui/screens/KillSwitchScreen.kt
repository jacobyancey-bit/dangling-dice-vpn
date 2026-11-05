package com.danglingdice.vpn.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
    onBackClick: () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kill Switch", color = Color.White, fontWeight = FontWeight.Bold) },
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
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Emerald500.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                        .border(1.dp, Emerald500.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = Emerald500, modifier = Modifier.size(32.dp))
                }

                Text(
                    text = "System-Level Kill Switch",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = "The Dangling Dice Kill Switch utilizes Android's hardware-enforced Always-on VPN architecture. If your VPN connection drops unexpectedly, Android blocks all unencrypted internet traffic instantly until the tunnel reconnects.",
                    fontSize = 14.sp,
                    color = Slate400,
                    lineHeight = 22.sp
                )

                Surface(
                    color = Slate900,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(text = "How to Enable:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(text = "1. Tap the button below to open Android VPN Settings.", color = Slate400, fontSize = 13.sp)
                        Text(text = "2. Tap the Gear icon next to Dangling Dice.", color = Slate400, fontSize = 13.sp)
                        Text(text = "3. Turn ON 'Always-on VPN' and 'Block connections without VPN'.", color = Emerald500, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

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
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Emerald500)
            ) {
                Text(
                    text = "Open Android VPN Settings",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

