package com.tokmak.vpn.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object SubFetcher {
    /** Скачивает подписку и разбирает её в список профилей. Блокирующий вызов. */
    fun fetch(url: String): List<Profile> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 20000
        c.setRequestProperty("User-Agent", "Tokmak/0.1")
        try {
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            val text = c.inputStream.bufferedReader().use { it.readText() }
            return LinkParser.parseMany(text)
        } finally {
            c.disconnect()
        }
    }
}
