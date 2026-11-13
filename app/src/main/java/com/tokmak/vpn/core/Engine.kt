package com.tokmak.vpn.core

import android.content.Context
import com.tokmak.vpn.data.Profile
import com.tokmak.vpn.data.Store
import libv2ray.Libv2ray
import java.io.File

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

    /** Задержка до сервера в мс или -2 при ошибке. Блокирующий вызов. */
    fun ping(p: Profile): Long = try {
        Libv2ray.measureOutboundDelay(ConfigBuilder.forTest(p), ConfigBuilder.TEST_URL)
    } catch (e: Throwable) {
        -2L
    }
}
