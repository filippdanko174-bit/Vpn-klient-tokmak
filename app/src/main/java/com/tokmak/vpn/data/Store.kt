package com.tokmak.vpn.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class Store(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("tokmak", Context.MODE_PRIVATE)

    fun loadProfiles(): List<Profile> = try {
        val a = JSONArray(sp.getString("profiles", "[]"))
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Profile(
                id = o.getString("id"),
                name = o.getString("name"),
                protocol = o.getString("protocol"),
                host = o.getString("host"),
                port = o.getInt("port"),
                outbound = o.getString("outbound"),
                subId = o.optString("subId").ifEmpty { null },
                delay = o.optLong("delay", -1L),
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun saveProfiles(list: List<Profile>) {
        val a = JSONArray()
        list.forEach {
            a.put(
                JSONObject()
                    .put("id", it.id).put("name", it.name).put("protocol", it.protocol)
                    .put("host", it.host).put("port", it.port).put("outbound", it.outbound)
                    .put("subId", it.subId ?: "").put("delay", it.delay)
            )
        }
        sp.edit().putString("profiles", a.toString()).apply()
    }

    fun loadSubs(): List<Subscription> = try {
        val a = JSONArray(sp.getString("subs", "[]"))
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Subscription(o.getString("id"), o.getString("name"), o.getString("url"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun saveSubs(list: List<Subscription>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("url", it.url)) }
        sp.edit().putString("subs", a.toString()).apply()
    }

    fun selectedId(): String? = sp.getString("selected", null)

    fun saveSelectedId(id: String?) {
        sp.edit().putString("selected", id).apply()
    }

    fun loadSettings(): RoutingSettings = try {
        val o = JSONObject(sp.getString("settings", "{}"))
        val apps = o.optJSONArray("apps")
        RoutingSettings(
            directLan = o.optBoolean("directLan", true),
            directRu = o.optBoolean("directRu", false),
            blockAds = o.optBoolean("blockAds", false),
            directDomains = o.optString("directDomains", ""),
            appMode = o.optString("appMode", "all"),
            apps = if (apps == null) emptySet() else (0 until apps.length()).map { apps.getString(it) }.toSet(),
        )
    } catch (e: Exception) {
        RoutingSettings()
    }

    fun saveSettings(s: RoutingSettings) {
        val o = JSONObject()
            .put("directLan", s.directLan).put("directRu", s.directRu).put("blockAds", s.blockAds)
            .put("directDomains", s.directDomains).put("appMode", s.appMode)
            .put("apps", JSONArray(s.apps.toList()))
        sp.edit().putString("settings", o.toString()).apply()
    }

    /** Постоянный идентификатор устройства для подписок (HWID). */
    fun hwid(): String {
        sp.getString("hwid", null)?.let { return it }
        val k = java.util.UUID.randomUUID().toString().replace("-", "")
        sp.edit().putString("hwid", k).apply()
        return k
    }

    /** Стабильный случайный ключ для XUDP. */
    fun xudpKey(): String {
        sp.getString("xudp", null)?.let { return it }
        val k = java.util.UUID.randomUUID().toString()
        sp.edit().putString("xudp", k).apply()
        return k
    }
}
