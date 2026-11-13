package com.tokmak.vpn.core

import com.tokmak.vpn.data.Profile
import com.tokmak.vpn.data.RoutingSettings
import org.json.JSONArray
import org.json.JSONObject
import java.net.Inet6Address
import java.net.InetAddress

object ConfigBuilder {
    const val TEST_URL = "https://www.gstatic.com/generate_204"

    /** Конфиг для основного режима: TUN-вход + маршрутизация. */
    fun forVpn(p: Profile, s: RoutingSettings, resolve: Boolean = true): String {
        val root = JSONObject()
        root.put("log", JSONObject().put("loglevel", "warning"))
        root.put(
            "inbounds", JSONArray().put(
                JSONObject()
                    .put("tag", "tun").put("protocol", "tun")
                    .put("settings", JSONObject().put("name", "xray0").put("mtu", 1500).put("userLevel", 0))
                    .put(
                        "sniffing", JSONObject()
                            .put("enabled", true)
                            .put("routeOnly", true)
                            .put("destOverride", JSONArray().put("http").put("tls").put("quic"))
                    )
            )
        )
        root.put(
            "outbounds", JSONArray()
                .put(prepareOutbound(p, resolve))
                .put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
                .put(JSONObject().put("tag", "block").put("protocol", "blackhole"))
        )
        root.put(
            "routing", JSONObject()
                .put("domainStrategy", "AsIs")
                .put("rules", rules(s))
        )
        return root.toString()
    }

    /** Минимальный конфиг для замера задержки. */
    fun forTest(p: Profile): String {
        val root = JSONObject()
        root.put("log", JSONObject().put("loglevel", "none"))
        root.put(
            "outbounds", JSONArray()
                .put(prepareOutbound(p, true))
                .put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
        )
        return root.toString()
    }

    private fun rules(s: RoutingSettings): JSONArray {
        val r = JSONArray()
        fun rule(outbound: String, domain: List<String>? = null, ip: List<String>? = null) {
            val o = JSONObject().put("type", "field").put("outboundTag", outbound)
            if (domain != null) o.put("domain", JSONArray(domain))
            if (ip != null) o.put("ip", JSONArray(ip))
            r.put(o)
        }
        if (s.blockAds) rule("block", domain = listOf("geosite:category-ads-all"))
        val custom = s.directDomains.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { if (it.contains(':')) it else "domain:$it" }
        if (custom.isNotEmpty()) rule("direct", domain = custom)
        if (s.directRu) {
            rule("direct", domain = listOf("regexp:.*\\.(ru|su|xn--p1ai)$"))
            rule("direct", ip = listOf("geoip:ru"))
        }
        if (s.directLan) rule("direct", ip = listOf("geoip:private"))
        return r
    }

    /**
     * Go на Android не умеет резолвить домены без resolv.conf, поэтому адрес сервера
     * заменяем на IP (приложение исключено из VPN, так что резолв идёт напрямую),
     * а имя хоста переносим в SNI / Host.
     */
    private fun prepareOutbound(p: Profile, resolve: Boolean): JSONObject {
        val o = JSONObject(p.outbound)
        val settings = o.getJSONObject("settings")
        val holder: JSONObject = when {
            settings.has("vnext") -> settings.getJSONArray("vnext").getJSONObject(0)
            settings.has("servers") -> settings.getJSONArray("servers").getJSONObject(0)
            else -> return o
        }
        val addr = holder.getString("address")
        if (isIp(addr)) return o
        o.optJSONObject("streamSettings")?.let { fillNames(it, addr) }
        if (!resolve) return o
        val ip = try {
            InetAddress.getAllByName(addr).sortedBy { it is Inet6Address }.firstOrNull()?.hostAddress
        } catch (e: Exception) {
            null
        } ?: throw IllegalStateException("Не удалось определить IP сервера $addr")
        holder.put("address", ip)
        return o
    }

    private fun isIp(s: String) = s.contains(':') || s.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))

    private fun fillNames(stream: JSONObject, host: String) {
        stream.optJSONObject("tlsSettings")?.let { if (it.optString("serverName").isEmpty()) it.put("serverName", host) }
        stream.optJSONObject("realitySettings")?.let { if (it.optString("serverName").isEmpty()) it.put("serverName", host) }
        stream.optJSONObject("wsSettings")?.let { if (it.optString("host").isEmpty()) it.put("host", host) }
        stream.optJSONObject("httpupgradeSettings")?.let { if (it.optString("host").isEmpty()) it.put("host", host) }
        stream.optJSONObject("xhttpSettings")?.let { if (it.optString("host").isEmpty()) it.put("host", host) }
    }
}
