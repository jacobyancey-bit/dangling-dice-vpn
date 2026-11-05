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
    private val baseUrl: String = "http://165.245.142.71"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun fetchServers(): List<ServerNode> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("$baseUrl/api/v1/servers").build()
            val res = client.newCall(req).execute()
            if (res.isSuccessful) {
                val body = res.body?.string() ?: return@withContext defaultServers()
                val json = JSONObject(body)
                val arr = json.optJSONArray("servers") ?: return@withContext defaultServers()
                val list = mutableListOf<ServerNode>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        ServerNode(
                            id = obj.optString("id", "us-east-1"),
                            name = obj.optString("name", "US East Gateway"),
                            endpoint = obj.optString("endpoint", "165.245.142.71:51820"),
                            publicKey = obj.optString("public_key", "xTpevhK8n+3m9+yH8OaJ6uV0cQw4Mv+B9x7pY6s="),
                            isFreeTier = obj.optBoolean("is_free_tier", true),
                            pingMs = 18
                        )
                    )
                }
                return@withContext list
            }
        } catch (_: Exception) {}
        return@withContext defaultServers()
    }

    suspend fun registerPeer(publicKey: String, deviceName: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("public_key", publicKey)
                put("device_name", deviceName)
            }
            val req = Request.Builder()
                .url("$baseUrl/api/v1/peers/register")
                .post(payload.toString().toRequestBody(jsonMedia))
                .build()

            val res = client.newCall(req).execute()
            if (res.isSuccessful) {
                val json = JSONObject(res.body?.string() ?: "")
                val assignedIp = json.optString("assigned_ip", "10.8.0.2/24")
                val serverPubKey = json.optString("server_public_key")
                return@withContext Pair(assignedIp, serverPubKey)
            }
        } catch (_: Exception) {}
        return@withContext null
    }

    suspend fun fetchDiagnostics(): LeakReport = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("$baseUrl/api/v1/tools/diagnostics").build()
            val res = client.newCall(req).execute()
            if (res.isSuccessful) {
                val json = JSONObject(res.body?.string() ?: "")
                return@withContext LeakReport(
                    clientIp = json.optString("client_ip", "Unknown"),
                    isVpnSecured = json.optBoolean("is_vpn_secured", false),
                    serverNode = json.optString("server_node", "Node-US-East-1")
                )
            }
        } catch (_: Exception) {}
        return@withContext LeakReport(clientIp = "Direct Link", isVpnSecured = false)
    }

    private fun defaultServers() = listOf(
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
