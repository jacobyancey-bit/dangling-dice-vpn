package com.danglingdice.vpn

import com.danglingdice.vpn.data.model.ConnectionStatus
import com.danglingdice.vpn.data.model.LeakReport
import com.danglingdice.vpn.data.model.ServerNode
import com.danglingdice.vpn.data.model.TunnelStats
import com.danglingdice.vpn.ui.screens.formatBytes
import com.danglingdice.vpn.ui.screens.formatDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnModelTest {

    @Test
    fun testFormatBytes_correctlyFormatsUnits() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("1.5 MB", formatBytes(1024 * 1024 + 512 * 1024))
    }

    @Test
    fun testFormatDuration_correctlyFormatsMinutesAndHours() {
        assertEquals("00:45", formatDuration(45))
        assertEquals("02:05", formatDuration(125))
        assertEquals("01:00:00", formatDuration(3600))
    }

    @Test
    fun testDefaultServerNode_pointsToLiveGateway() {
        val node = ServerNode(
            id = "us-east-1",
            name = "US East Gateway",
            endpoint = "165.245.142.71:51820",
            publicKey = "xTpevhK8n+3m9+yH8OaJ6uV0cQw4Mv+B9x7pY6s="
        )
        assertTrue(node.isFreeTier)
        assertEquals("165.245.142.71:51820", node.endpoint)
    }

    @Test
    fun testLeakReport_defaultsToUnsecuredUntilVerified() {
        val report = LeakReport()
        assertEquals(false, report.isVpnSecured)
    }

    @Test
    fun testTunnelStats_initializesAtZero() {
        val stats = TunnelStats()
        assertEquals(0L, stats.rxBytes)
        assertEquals(0L, stats.txBytes)
        assertEquals(ConnectionStatus.DISCONNECTED, ConnectionStatus.valueOf("DISCONNECTED"))
        assertEquals(ConnectionStatus.VERIFYING_HANDSHAKE, ConnectionStatus.valueOf("VERIFYING_HANDSHAKE"))
        assertEquals(ConnectionStatus.ERROR, ConnectionStatus.valueOf("ERROR"))
    }
}

