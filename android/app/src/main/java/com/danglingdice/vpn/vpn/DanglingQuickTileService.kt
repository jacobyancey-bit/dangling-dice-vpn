package com.danglingdice.vpn.vpn

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.danglingdice.vpn.DanglingApplication
import com.danglingdice.vpn.MainActivity
import com.danglingdice.vpn.data.model.ConnectionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Android Quick Settings Pull-Down Tile ("Dangling Dice VPN")
 * Lets users toggle the WireGuard VPN directly from the Android notification shade.
 */
class DanglingQuickTileService : TileService() {

    private var tileJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        val manager = DanglingApplication.instance.tunnelManager
        tileJob?.cancel()
        tileJob = CoroutineScope(Dispatchers.Main).launch {
            manager.status.collect { status ->
                updateTileUi(status, manager.selectedServer.value.name)
            }
        }
    }

    override fun onStopListening() {
        tileJob?.cancel()
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val manager = DanglingApplication.instance.tunnelManager
        val current = manager.status.value

        if (current == ConnectionStatus.CONNECTED ||
            current == ConnectionStatus.CONNECTING ||
            current == ConnectionStatus.VERIFYING_HANDSHAKE
        ) {
            manager.disconnect()
            return
        }

        // Check if Android VpnService permission has already been granted
        if (VpnService.prepare(applicationContext) == null) {
            manager.connect(manager.selectedServer.value)
        } else {
            // Launch MainActivity to prompt for initial Android VPN permission
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pending = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pending)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
    }

    private fun updateTileUi(status: ConnectionStatus, serverName: String) {
        val tile = qsTile ?: return
        tile.label = "Dangling VPN"
        when (status) {
            ConnectionStatus.CONNECTED -> {
                tile.state = Tile.STATE_ACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = serverName
                }
            }
            ConnectionStatus.CONNECTING, ConnectionStatus.VERIFYING_HANDSHAKE -> {
                tile.state = Tile.STATE_ACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "Connecting..."
                }
            }
            else -> {
                tile.state = Tile.STATE_INACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "Tap to Protect"
                }
            }
        }
        tile.updateTile()
    }
}
