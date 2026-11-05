package com.danglingdice.vpn.ui.screens

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danglingdice.vpn.data.model.AppItem
import com.danglingdice.vpn.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitTunnelScreen(
    excludedApps: Set<String>,
    onToggleAppExclusion: (String) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var appsList by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val filtered = installed.filter { app ->
                // Filter user apps or common third-party apps
                (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || app.packageName == "com.android.chrome"
            }.map { app ->
                AppItem(
                    name = app.loadLabel(pm).toString(),
                    packageName = app.packageName,
                    isExcluded = excludedApps.contains(app.packageName)
                )
            }.sortedBy { it.name }
            appsList = filtered
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Split Tunneling", color = Color.White, fontWeight = FontWeight.Bold) },
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
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Excluded apps bypass the WireGuard VPN tunnel and use your local internet connection directly.",
                fontSize = 13.sp,
                color = Slate400,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                placeholder = { Text("Search installed applications...", color = Slate400, fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Slate400) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Emerald500,
                    unfocusedBorderColor = Slate800,
                    focusedContainerColor = Slate900,
                    unfocusedContainerColor = Slate900,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Emerald500)
                }
            } else {
                val filteredApps = appsList.filter {
                    it.name.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true)
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val isExcluded = excludedApps.contains(app.packageName)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Slate900, RoundedCornerShape(12.dp))
                                .border(1.dp, Slate800, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(text = app.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(text = app.packageName, color = Slate400, fontSize = 11.sp)
                            }

                            Switch(
                                checked = isExcluded,
                                onCheckedChange = { onToggleAppExclusion(app.packageName) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Emerald500,
                                    checkedTrackColor = Emerald500.copy(alpha = 0.3f),
                                    uncheckedThumbColor = Slate400,
                                    uncheckedTrackColor = Slate800
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
