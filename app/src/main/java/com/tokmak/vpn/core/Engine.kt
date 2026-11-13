package com.tokmak.vpn.core

import android.content.Context
import android.util.Log
import com.tokmak.vpn.data.Profile
import com.tokmak.vpn.data.Store
import libv2ray.Libv2ray
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

data class PingResult(val ms: Long, val error: String? = null)

object Engine {
    @Volatile
    private var inited = false

    @Synchronized
    fun init(context: Context) {
        if (inited) return
        val app = context.applicationContext
        go.Seq.setContext(app)
        val dir = File(app.filesDir, "xray").apply { mkdirs() }
        for (name in listOf("geoip.dat", "geosite.dat")) {
            val f = File(dir, name)
            if (!f.exists()) {
                try {
                    app.assets.open(name).use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                } catch (e: Exception) {
                    f.delete()
                }
            }
        }
        Libv2ray.initCoreEnv(dir.absolutePath, Store(app).xudpKey())
        inited = true
    }

    /** Задержка до сервера в мс; при ошибке -2 и текст причины. Блокирующий вызов. */
    fun ping(p: Profile): PingResult = try {
        PingResult(Libv2ray.measureOutboundDelay(ConfigBuilder.forTest(p), ConfigBuilder.TEST_URL))
    } catch (e: Throwable) {
        val msg = (e.message ?: e.toString()).lines().first().take(160)
        Log.w("Tokmak", "ping ${p.name}: $msg")
        PingResult(-2L, "${tcpCheck(p)}. Xray: $msg")
    }

    /** Отличает «сервер недоступен» от «сервер доступен, но прокси не отвечает». */
    private fun tcpCheck(p: Profile): String = try {
        val t = System.currentTimeMillis()
        Socket().use { it.connect(InetSocketAddress(p.host, p.port), 4000) }
        "Порт открыт (${System.currentTimeMillis() - t} мс)"
    } catch (e: Exception) {
        "Порт недоступен (${e.javaClass.simpleName}: ${e.message})"
    }
}
