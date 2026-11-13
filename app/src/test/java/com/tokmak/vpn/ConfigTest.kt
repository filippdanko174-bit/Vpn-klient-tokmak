package com.tokmak.vpn

import com.tokmak.vpn.core.ConfigBuilder
import com.tokmak.vpn.data.LinkParser
import com.tokmak.vpn.data.RoutingSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File
import java.util.Base64

class ConfigTest {
    private fun b64(s: String) = Base64.getEncoder().encodeToString(s.toByteArray())

    private val uuid = "11111111-2222-3333-4444-555555555555"
    private val pbk = "7xhH4a5T1m1w2sQ2p3m5JQ4zq0lq0gXr3H5mFZ1kKxM"

    private fun links(): List<String> = listOf(
        "vless://$uuid@203.0.113.10:443?encryption=none&flow=xtls-rprx-vision&security=reality&sni=www.microsoft.com&fp=chrome&pbk=$pbk&sid=ab12cd34&type=tcp#reality",
        "vless://$uuid@example.com:443?type=ws&security=tls&path=%2Fws&host=cdn.example.com&sni=cdn.example.com&fp=chrome&alpn=h2,http/1.1#ws-tls",
        "vless://$uuid@203.0.113.11:443?type=grpc&serviceName=svc&security=tls&sni=a.example.com#grpc",
        "vless://$uuid@203.0.113.15:443?type=xhttp&path=%2Fx&mode=auto&security=tls&sni=x.example.com#xhttp",
        "vless://$uuid@[2001:db8::1]:443?type=httpupgrade&path=%2Fu&host=u.example.com&security=tls#hu",
        "vmess://" + b64("""{"v":"2","ps":"vm","add":"203.0.113.12","port":"443","id":"$uuid","aid":"0","scy":"auto","net":"ws","type":"none","host":"h.example.com","path":"/p","tls":"tls","sni":"h.example.com"}"""),
        "trojan://secret@203.0.113.13:443?security=tls&sni=t.example.com&type=tcp#tj",
        "ss://" + b64("chacha20-ietf-poly1305:pass") + "@203.0.113.14:8388#ss1",
        "ss://" + b64("chacha20-ietf-poly1305:pass@203.0.113.16:8388") + "#ss2",
    )

    @Test
    fun parseAndBuild() {
        val dir = File("build/test-configs").apply { deleteRecursively(); mkdirs() }
        val settings = RoutingSettings(
            directRu = true, blockAds = true,
            directDomains = "example.com\n# comment\ndomain:foo.org",
        )
        links().forEachIndexed { i, l ->
            val p = LinkParser.parse(l)
            assertNotNull("не разобрана: $l", p)
            File(dir, "c$i.json").writeText(ConfigBuilder.forVpn(p!!, settings, resolve = false))
        }
        assertEquals(9, dir.listFiles()!!.size)
    }

    @Test
    fun subscriptionBase64() {
        val body = b64(links().take(3).joinToString("\n"))
        assertEquals(3, LinkParser.parseMany(body).size)
    }
}
