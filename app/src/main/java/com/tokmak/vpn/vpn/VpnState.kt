package com.tokmak.vpn.vpn

import kotlinx.coroutines.flow.MutableStateFlow

sealed interface VpnStatus {
    data object Disconnected : VpnStatus
    data object Connecting : VpnStatus
    data class Connected(val since: Long) : VpnStatus
    data class Error(val message: String) : VpnStatus
}

object VpnState {
    val status = MutableStateFlow<VpnStatus>(VpnStatus.Disconnected)
}
