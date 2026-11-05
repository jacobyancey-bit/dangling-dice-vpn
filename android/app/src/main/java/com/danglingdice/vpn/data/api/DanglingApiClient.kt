package com.danglingdice.vpn.data.api

import com.danglingdice.vpn.data.model.LeakReport
import com.danglingdice.vpn.data.model.ServerNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class DanglingApiClient(
    private val baseUrls: List<String> = listOf(
        "https://vpn.danglingdicestudios.duckdns.org",
        "https://danglingdicestudios.duckdns.org",
        "http://165.245.142.71"
    )
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun fetchServers(): List<ServerNode> = withContext(Dispatchers.IO) {
        for (url in baseUrls) {
            try {
                val req = Request.Builder().url("$url/api/v1/servers").build()
                client.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        val body = res.body?.string() ?: continue
                        val json = JSONObject(body)
                        val arr = json.optJSONArray("servers") ?: continue
                        val list = mutableListOf<ServerNode>()
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            list.add(
                                ServerNode(
                                    id = obj.optString("id", "us-east-1"),
                                    name = obj.optString("name", "United States • East"),
                                    city = obj.optString("city", "Atlanta / New York"),
                                    endpoint = obj.optString("endpoint", "165.245.142.71:51820"),
                                    publicKey = obj.optString("public_key", ""),
                                    isFreeTier = obj.optBoolean("is_free_tier", true),
                                    pingMs = obj.optInt("ping_ms", 18),
                                    flagEmoji = obj.optString("flag_emoji", "🇺🇸"),
                                    loadPercent = obj.optInt("load_percent", 14)
                                )
                            )
                        }
                        if (list.isNotEmpty()) return@withContext list
                    }
                }
            } catch (_: Exception) {}
        }
        return@withContext defaultServers()
    }

    suspend fun registerPeer(
        publicKey: String,
        deviceName: String,
        adBlockEnabled: Boolean = true
    ): Pair<String, String>? = withContext(Dispatchers.IO) {
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val payload = JSONObject().apply {
            put("public_key", publicKey)
            put("device_name", deviceName)
            put("adblock_enabled", adBlockEnabled)
        }.toString()

        for (url in baseUrls) {
            try {
                val req = Request.Builder()
                    .url("$url/api/v1/peers/register")
                    .addHeader("X-Dangling-Timestamp", timestamp)
                    .addHeader("X-Dangling-Client-Proof", sha256Hex("$publicKey:$timestamp"))
                    .post(payload.toRequestBody(jsonMedia))
                    .build()

                client.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        val json = JSONObject(res.body?.string() ?: "")
                        val assignedIp = json.optString("assigned_ip", "10.8.0.2/24")
                        val serverPubKey = json.optString("server_public_key", "")
                        if (serverPubKey.isNotBlank()) {
                            trackEvent("peer_registered", mapOf("device" to deviceName))
                            return@withContext Pair(assignedIp, serverPubKey)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return@withContext null
    }

    private fun sha256Hex(input: String): String {
        val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun trackEvent(eventName: String, metadata: Map<String, String> = emptyMap()) = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("event_name", eventName)
            put("session_id", "android_" + android.os.Build.ID)
            put("platform", "android_app")
            put("metadata", JSONObject(metadata))
        }.toString()

        for (url in baseUrls) {
            try {
                val req = Request.Builder()
                    .url("$url/api/v1/telemetry/event")
                    .post(payload.toRequestBody(jsonMedia))
                    .build()
                client.newCall(req).execute().use { res ->
                    if (res.isSuccessful) return@withContext
                }
            } catch (_: Exception) {}
        }
    }

    suspend fun fetchDiagnostics(): LeakReport = withContext(Dispatchers.IO) {
        for (url in baseUrls) {
            try {
                val req = Request.Builder().url("$url/api/v1/tools/diagnostics").build()
                client.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        val json = JSONObject(res.body?.string() ?: "")
                        return@withContext LeakReport(
                            clientIp = json.optString("client_ip", "Unknown"),
                            isVpnSecured = json.optBoolean("is_vpn_secured", false),
                            serverNode = json.optString("server_node", "Node-US-East-1")
                        )
                    }
                }
            } catch (_: Exception) {}
        }
        return@withContext LeakReport(clientIp = "Direct Link", isVpnSecured = false)
    }

    private fun defaultServers() = com.danglingdice.vpn.data.model.defaultServerLocations()
}
