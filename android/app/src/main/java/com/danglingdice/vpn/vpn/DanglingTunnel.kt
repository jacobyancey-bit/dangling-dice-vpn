package com.danglingdice.vpn.vpn

import com.wireguard.android.backend.Tunnel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DanglingTunnel(
    private val tunnelName: String = "dangling_dice_wg0"
) : Tunnel {

    private val _state = MutableStateFlow(Tunnel.State.DOWN)
    val state: StateFlow<Tunnel.State> = _state.asStateFlow()

    override fun getName(): String = tunnelName

    override fun onStateChange(newState: Tunnel.State) {
        _state.value = newState
    }
}

