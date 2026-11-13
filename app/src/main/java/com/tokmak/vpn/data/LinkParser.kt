package com.tokmak.vpn.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.util.UUID

/** Разбор ссылок vless:// vmess:// trojan:// ss:// и подписок. */
object LinkParser {

    fun parseMany(text: String): List<Profile> {
        val t = text.trim()
        if (t.isEmpty()) return emptyList()
        val body = if (t.contains("://")) t else (decodeB64(t) ?: return emptyList())
        return body.lines().mapNotNull { parse(it.trim()) }.distinctBy { it.id }
    }

    fun parse(link: String): Profile? = try {
        when {
            link.startsWith("vless://") -> vless(link)
            link.startsWith("vmess://") -> vmess(link)
            link.startsWith("trojan://") -> trojan(link)
            link.startsWith("ss://") -> shadowsocks(link)
            else -> null
        }
    } catch (e: Exception) {
        null
    }

    // ---------- протоколы ----------

    private fun vless(link: String): Profile {
        val u = splitUrl(link)
        val q = u.q
        val user = JSONObject().put("id", u.user).put("encryption", q["encryption"] ?: "none")
        q["flow"]?.takeIf { it.isNotEmpty() }?.let { user.put("flow", it) }
        val out = JSONObject()
            .put("tag", "proxy").put("protocol", "vless")
            .put(
                "settings", JSONObject().put(
                    "vnext", JSONArray().put(
                        JSONObject().put("address", u.host).put("port", u.port)
                            .put("users", JSONArray().put(user))
                    )
                )
            )
            .put("streamSettings", stream(q, "none"))
        return make(u.frag, "VLESS", u.host, u.port, out)
    }

    private fun vmess(link: String): Profile {
        val raw = link.removePrefix("vmess://").substringBefore('#')
        val j = JSONObject(decodeB64(raw) ?: error("bad vmess"))
        val host = j.getString("add")
        val port = j.get("port").toString().toInt()
        val net = j.optString("net", "tcp").ifEmpty { "tcp" }
        val q = mutableMapOf<String, String>()
        q["type"] = net
        q["security"] = if (j.optString("tls") == "tls") "tls" else "none"
        q["sni"] = j.optString("sni")
        q["host"] = j.optString("host")
        q["fp"] = j.optString("fp")
        q["alpn"] = j.optString("alpn")
        q["headerType"] = j.optString("type")
        val path = j.optString("path")
        q["path"] = path
        if (net == "grpc") q["serviceName"] = path
        if (net == "kcp") q["seed"] = path
        val user = JSONObject().put("id", j.getString("id"))
            .put("alterId", j.optString("aid", "0").toIntOrNull() ?: 0)
            .put("security", j.optString("scy", "auto").ifEmpty { "auto" })
        val out = JSONObject()
            .put("tag", "proxy").put("protocol", "vmess")
            .put(
                "settings", JSONObject().put(
                    "vnext", JSONArray().put(
                        JSONObject().put("address", host).put("port", port)
                            .put("users", JSONArray().put(user))
                    )
                )
            )
            .put("streamSettings", stream(q, "none"))
        return make(j.optString("ps"), "VMess", host, port, out)
    }

    private fun trojan(link: String): Profile {
        val u = splitUrl(link)
        val out = JSONObject()
            .put("tag", "proxy").put("protocol", "trojan")
            .put(
                "settings", JSONObject().put(
                    "servers", JSONArray().put(
                        JSONObject().put("address", u.host).put("port", u.port).put("password", u.user)
                    )
                )
            )
            .put("streamSettings", stream(u.q, "tls"))
        return make(u.frag, "Trojan", u.host, u.port, out)
    }

    private fun shadowsocks(link: String): Profile {
        var rest = link.removePrefix("ss://")
        var frag = ""
        val h = rest.indexOf('#')
        if (h >= 0) {
            frag = dec(rest.substring(h + 1)); rest = rest.substring(0, h)
        }
        rest = rest.substringBefore('?').trimEnd('/')
        val method: String
        val password: String
        val hostPort: String
        val at = rest.lastIndexOf('@')
        if (at >= 0) {
            var info = dec(rest.substring(0, at))
            if (!info.contains(':')) info = decodeB64(info) ?: error("bad ss")
            method = info.substringBefore(':')
            password = info.substringAfter(':')
            hostPort = rest.substring(at + 1)
        } else {
            val all = decodeB64(rest) ?: error("bad ss")
            val a = all.lastIndexOf('@')
            val info = all.substring(0, a)
            method = info.substringBefore(':')
            password = info.substringAfter(':')
            hostPort = all.substring(a + 1)
        }
        val (host, port) = hostAndPort(hostPort) ?: error("bad ss host")
        val out = JSONObject()
            .put("tag", "proxy").put("protocol", "shadowsocks")
            .put(
                "settings", JSONObject().put(
                    "servers", JSONArray().put(
                        JSONObject().put("address", host).put("port", port)
                            .put("method", method).put("password", password)
                    )
                )
            )
        return make(frag, "Shadowsocks", host, port, out)
    }

    // ---------- транспорт и безопасность ----------

    private fun stream(q: Map<String, String>, defaultSecurity: String): JSONObject {
        val net = when (val n = q["type"]?.takeIf { it.isNotEmpty() } ?: "tcp") {
            "splithttp" -> "xhttp"
            "raw" -> "tcp"
            "mkcp" -> "kcp"
            else -> n
        }
        val s = JSONObject().put("network", net)
        val security = q["security"]?.takeIf { it.isNotEmpty() } ?: defaultSecurity
        s.put("security", security)
        val host = q["host"].orEmpty()
        val path = q["path"].orEmpty()
        when (net) {
            "ws" -> {
                s.put("wsSettings", JSONObject().put("path", path.ifEmpty { "/" }).put("host", host))
            }
            "grpc" -> s.put(
                "grpcSettings",
                JSONObject().put("serviceName", q["serviceName"] ?: path)
                    .put("multiMode", q["mode"] == "multi")
            )
            "httpupgrade" -> s.put(
                "httpupgradeSettings",
                JSONObject().put("path", path.ifEmpty { "/" }).put("host", host)
            )
            "xhttp" -> {
                val x = JSONObject().put("path", path.ifEmpty { "/" }).put("host", host)
                    .put("mode", q["mode"]?.takeIf { it.isNotEmpty() } ?: "auto")
                q["extra"]?.takeIf { it.isNotEmpty() }?.let {
                    try {
                        x.put("extra", JSONObject(it))
                    } catch (_: Exception) {
                    }
                }
                s.put("xhttpSettings", x)
            }
            "kcp" -> {
                val k = JSONObject().put(
                    "header", JSONObject().put("type", q["headerType"]?.takeIf { it.isNotEmpty() } ?: "none")
                )
                q["seed"]?.takeIf { it.isNotEmpty() }?.let { k.put("seed", it) }
                s.put("kcpSettings", k)
            }
            "tcp" -> if (q["headerType"] == "http") {
                val req = JSONObject().put("path", JSONArray().put(path.ifEmpty { "/" }))
                if (host.isNotEmpty()) req.put("headers", JSONObject().put("Host", JSONArray().put(host)))
                s.put("tcpSettings", JSONObject().put("header", JSONObject().put("type", "http").put("request", req)))
            }
        }
        val fp = q["fp"]?.takeIf { it.isNotEmpty() } ?: "chrome"
        when (security) {
            "tls" -> {
                val t = JSONObject().put("fingerprint", fp)
                q["sni"]?.takeIf { it.isNotEmpty() }?.let { t.put("serverName", it) }
                q["alpn"]?.takeIf { it.isNotEmpty() }?.let {
                    t.put("alpn", JSONArray(it.split(',').map { a -> a.trim() }.filter { a -> a.isNotEmpty() }))
                }
                if (q["allowInsecure"] == "1" || q["insecure"] == "1") t.put("allowInsecure", true)
                s.put("tlsSettings", t)
            }
            "reality" -> {
                val r = JSONObject().put("fingerprint", fp)
                    .put("publicKey", q["pbk"].orEmpty())
                    .put("shortId", q["sid"].orEmpty())
                    .put("spiderX", q["spx"].orEmpty())
                q["sni"]?.takeIf { it.isNotEmpty() }?.let { r.put("serverName", it) }
                s.put("realitySettings", r)
            }
        }
        return s
    }

    // ---------- утилиты ----------

    private fun make(name: String, protocol: String, host: String, port: Int, out: JSONObject): Profile {
        val s = out.toString()
        return Profile(
            id = UUID.nameUUIDFromBytes(s.toByteArray()).toString(),
            name = name.ifBlank { "$host:$port" },
            protocol = protocol,
            host = host,
            port = port,
            outbound = s,
        )
    }

    private class Url(val user: String, val host: String, val port: Int, val q: Map<String, String>, val frag: String)

    private fun splitUrl(link: String): Url {
        var rest = link.substring(link.indexOf("://") + 3)
        var frag = ""
        val h = rest.indexOf('#')
        if (h >= 0) {
            frag = dec(rest.substring(h + 1)); rest = rest.substring(0, h)
        }
        var query = ""
        val qi = rest.indexOf('?')
        if (qi >= 0) {
            query = rest.substring(qi + 1); rest = rest.substring(0, qi)
        }
        rest = rest.trimEnd('/')
        val at = rest.lastIndexOf('@')
        val user = if (at >= 0) dec(rest.substring(0, at)) else ""
        val (host, port) = hostAndPort(if (at >= 0) rest.substring(at + 1) else rest) ?: error("bad host")
        val q = HashMap<String, String>()
        query.split('&').filter { it.isNotEmpty() }.forEach {
            val i = it.indexOf('=')
            if (i > 0) q[dec(it.substring(0, i))] = dec(it.substring(i + 1))
        }
        return Url(user, host, port, q, frag)
    }

    private fun hostAndPort(s: String): Pair<String, Int>? {
        if (s.startsWith("[")) {
            val e = s.indexOf(']')
            if (e < 0) return null
            val port = s.substring(e + 1).removePrefix(":").toIntOrNull() ?: 443
            return s.substring(1, e) to port
        }
        val c = s.lastIndexOf(':')
        if (c < 0) return s to 443
        val port = s.substring(c + 1).toIntOrNull() ?: return null
        return s.substring(0, c) to port
    }

    private fun dec(s: String): String = try {
        URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")
    } catch (e: Exception) {
        s
    }

    fun decodeB64(input: String): String? = try {
        var s = input.trim().replace('-', '+').replace('_', '/').filter { !it.isWhitespace() }
        while (s.length % 4 != 0) s += "="
        String(java.util.Base64.getDecoder().decode(s), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }
}
