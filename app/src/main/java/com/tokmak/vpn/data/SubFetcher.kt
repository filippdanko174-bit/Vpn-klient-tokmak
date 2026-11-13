package com.tokmak.vpn.data

import android.os.Build
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object SubFetcher {
    /**
     * Скачивает подписку и разбирает её в список профилей. Блокирующий вызов.
     * Многие панели (Remnawave, Marzban и др.) требуют HWID устройства в заголовках.
     */
    fun fetch(url: String, hwid: String): List<Profile> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 20000
        c.setRequestProperty("User-Agent", "Tokmak/0.1 (Android)")
        c.setRequestProperty("x-hwid", hwid)
        c.setRequestProperty("x-device-os", "Android")
        c.setRequestProperty("x-ver-os", Build.VERSION.RELEASE ?: "")
        c.setRequestProperty("x-device-model", asciiHeader(Build.MODEL ?: "Android"))
        try {
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            val text = c.inputStream.bufferedReader().use { it.readText() }
            return LinkParser.parseMany(text)
        } finally {
            c.disconnect()
        }
    }

    private fun asciiHeader(s: String) = s.filter { it.code in 32..126 }.ifEmpty { "Android" }
}
