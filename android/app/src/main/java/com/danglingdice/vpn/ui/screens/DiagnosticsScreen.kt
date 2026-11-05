package com.danglingdice.vpn.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danglingdice.vpn.data.api.DanglingApiClient
import com.danglingdice.vpn.data.model.LeakReport
import com.danglingdice.vpn.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onBackClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val apiClient = remember { DanglingApiClient() }
    var report by remember { mutableStateOf<LeakReport?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    fun runTest() {
        isLoading = true
        scope.launch {
            report = apiClient.fetchDiagnostics()
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        runTest()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Leak Shield Diagnostics", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { runTest() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Emerald500)
                }
            } else {
                val isSecured = report?.isVpnSecured == true

                Surface(
                    color = if (isSecured) Emerald500.copy(alpha = 0.1f) else Amber500.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isSecured) Emerald500.copy(alpha = 0.4f) else Amber500.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = if (isSecured) Icons.Default.Shield else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isSecured) Emerald500 else Amber500,
                            modifier = Modifier.size(36.dp)
                        )
                        Column {
                            Text(
                                text = if (isSecured) "VPN Tunnel Active" else "Direct Connection Detected",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = if (isSecured) "Your real IP is masked behind Dangling Dice." else "Your ISP and sites can see your IP.",
                                color = Slate400,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Surface(
                    color = Slate900,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        DiagnosticRow(title = "Detected Public IP", value = report?.clientIp ?: "Unknown")
                        Divider(color = Slate800)
                        DiagnosticRow(title = "Active Exit Node", value = report?.serverNode ?: "Direct")
                        Divider(color = Slate800)
                        DiagnosticRow(title = "DNS Leak Status", value = if (isSecured) "No Leaks (Protected)" else "Direct DNS")
                        Divider(color = Slate800)
                        DiagnosticRow(title = "WebRTC Leak Status", value = if (isSecured) "Shielded" else "Direct WebRTC")
                    }
                }
            }
        }
    }
}

@Composable
fun DiagnosticRow(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, color = Slate400, fontSize = 13.sp)
        Text(text = value, color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}
