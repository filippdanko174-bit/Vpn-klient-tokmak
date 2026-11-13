package com.tokmak.vpn.data

/** delay: -1 = не измерялся, -2 = недоступен, иначе мс */
data class Profile(
    val id: String,
    val name: String,
    val protocol: String,
    val host: String,
    val port: Int,
    val outbound: String,
    val subId: String? = null,
    val delay: Long = -1L,
)

data class Subscription(
    val id: String,
    val name: String,
    val url: String,
)

data class RoutingSettings(
    val directLan: Boolean = true,
    val directRu: Boolean = false,
    val blockAds: Boolean = false,
    val directDomains: String = "",
    /** all | bypass | only */
    val appMode: String = "all",
    val apps: Set<String> = emptySet(),
)
