package com.danglingdice.vpn.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danglingdice.vpn.data.api.DanglingApiClient
import com.danglingdice.vpn.data.model.DevProbeResult
import com.danglingdice.vpn.data.model.LeakReport
import com.danglingdice.vpn.data.model.SpeedTestResult
import com.danglingdice.vpn.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apiClient = remember { DanglingApiClient() }
    var report by remember { mutableStateOf<LeakReport?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var copiedIp by remember { mutableStateOf(false) }

    var speedResult by remember { mutableStateOf<SpeedTestResult?>(null) }
    var isRunningSpeedTest by remember { mutableStateOf(false) }

    var probeInput by remember { mutableStateOf("dangling-dice-studios.com") }
    var probeResult by remember { mutableStateOf<DevProbeResult?>(null) }
    var isProbing by remember { mutableStateOf(false) }

    fun runDevProbe() {
        if (isProbing || probeInput.isBlank()) return
        isProbing = true
        scope.launch {
            probeResult = apiClient.runDevProbe(probeInput)
            isProbing = false
        }
    }

    fun runLeakTest() {
        isLoading = true
        scope.launch {
            report = apiClient.fetchDiagnostics()
            isLoading = false
        }
    }

    fun runSpeedBenchmark() {
        if (isRunningSpeedTest) return
        isRunningSpeedTest = true
        scope.launch {
            speedResult = apiClient.runSpeedBenchmark()
            isRunningSpeedTest = false
        }
    }

    LaunchedEffect(Unit) {
        runLeakTest()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Leak Shield & Speed Benchmark", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { runLeakTest() }) {
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
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Emerald500)
                }
            } else {
                val isSecured = report?.isVpnSecured == true

                Surface(
                    color = if (isSecured) Emerald500.copy(alpha = 0.1f) else Amber500.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSecured) Emerald500.copy(alpha = 0.4f) else Amber500.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = if (isSecured) Icons.Default.Shield else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isSecured) Emerald500 else Amber500,
                            modifier = Modifier.size(34.dp)
                        )
                        Column {
                            Text(
                                text = if (isSecured) "Tunnel Secured — Zero Leaks" else "Direct Connection Detected",
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val ip = report?.clientIp ?: return@clickable
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.setPrimaryClip(ClipData.newPlainText("Public IP", ip))
                                    copiedIp = true
                                },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Detected Public IP", color = Slate400, fontSize = 13.sp)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = if (copiedIp) "${report?.clientIp} (Copied ✓)" else (report?.clientIp ?: "Unknown"),
                                    color = if (copiedIp) Emerald500 else Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy IP", tint = Emerald500, modifier = Modifier.size(14.dp))
                            }
                        }
                        Divider(color = Slate800)
                        DiagnosticRow(title = "Active Exit Node", value = report?.serverNode ?: "Direct")
                        Divider(color = Slate800)
                        DiagnosticRow(title = "DNS Leak Status", value = if (isSecured) "Protected (Zero Leaks)" else "Direct DNS")
                        Divider(color = Slate800)
                        DiagnosticRow(title = "WebRTC Leak Status", value = if (isSecured) "Shielded" else "Direct WebRTC")
                    }
                }
            }

            // Encrypted Tunnel Speed & Latency Benchmark Card
            Surface(
                color = Slate900,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Emerald500.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = Emerald500, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Encrypted Tunnel Speed Benchmark",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Measures real-time throughput & latency via WireGuard",
                                color = Slate400,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Slate950, RoundedCornerShape(12.dp))
                            .border(1.dp, Slate800, RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("DOWNLOAD", fontSize = 10.sp, color = Slate400, fontFamily = FontFamily.Monospace)
                            Text(
                                text = if (speedResult != null) "${speedResult?.downloadMbps} Mbps" else "— Mbps",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Emerald500,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("LATENCY", fontSize = 10.sp, color = Slate400, fontFamily = FontFamily.Monospace)
                            Text(
                                text = if (speedResult != null) "${speedResult?.latencyMs} ms" else "— ms",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("JITTER", fontSize = 10.sp, color = Slate400, fontFamily = FontFamily.Monospace)
                            Text(
                                text = if (speedResult != null) "${speedResult?.jitterMs} ms" else "— ms",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Amber500,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Button(
                        onClick = { runSpeedBenchmark() },
                        enabled = !isRunningSpeedTest,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald500)
                    ) {
                        Text(
                            text = if (isRunningSpeedTest) "Testing Encrypted Tunnel..." else "⚡ Run Speed & Latency Benchmark",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Developer Network Toolkit: Live DNS Lookup + HTTPS & SSL Certificate Inspector
            Surface(
                color = Slate900,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Terminal, contentDescription = null, tint = Emerald500, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Developer DNS & SSL Probe",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Test API endpoints, DNS records & TLS certs over the tunnel",
                                color = Slate400,
                                fontSize = 12.sp
                            )
                        }
                    }

                    OutlinedTextField(
                        value = probeInput,
                        onValueChange = { probeInput = it },
                        singleLine = true,
                        label = { Text("Domain or API URL", color = Slate400, fontSize = 12.sp) },
                        placeholder = { Text("api.example.com", color = Slate400) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Emerald500,
                            unfocusedBorderColor = Slate800,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Emerald500
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = { runDevProbe() },
                        enabled = !isProbing && probeInput.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Slate800)
                    ) {
                        Text(
                            text = if (isProbing) "Probing DNS & TLS..." else "🔍 Run Dev DNS / HTTPS Probe",
                            color = Emerald500,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    probeResult?.let { res ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Slate950, RoundedCornerShape(12.dp))
                                .border(1.dp, Slate800, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DiagnosticRow(
                                title = "DNS IPs",
                                value = if (res.resolvedIps.isNotEmpty()) res.resolvedIps.joinToString(", ") else "Unresolved"
                            )
                            Divider(color = Slate800)
                            DiagnosticRow(
                                title = "HTTP Status",
                                value = "${res.httpStatus} (${res.responseTimeMs} ms)"
                            )
                            Divider(color = Slate800)
                            DiagnosticRow(
                                title = "TLS / SSL",
                                value = res.tlsInfo
                            )
                            if (!res.error.isNullOrBlank()) {
                                Divider(color = Slate800)
                                Text(
                                    text = "Error: ${res.error}",
                                    color = Amber500,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            }
                        }
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
