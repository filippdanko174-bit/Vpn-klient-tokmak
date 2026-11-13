package com.tokmak.vpn.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tokmak.vpn.core.Engine
import com.tokmak.vpn.data.LinkParser
import com.tokmak.vpn.data.Profile
import com.tokmak.vpn.data.RoutingSettings
import com.tokmak.vpn.data.Store
import com.tokmak.vpn.data.SubFetcher
import com.tokmak.vpn.data.Subscription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.UUID

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)

    val profiles = MutableStateFlow(store.loadProfiles())
    val subs = MutableStateFlow(store.loadSubs())
    val selectedId = MutableStateFlow(store.selectedId())
    val settings = MutableStateFlow(store.loadSettings())
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    fun selectedProfile(): Profile? = profiles.value.firstOrNull { it.id == selectedId.value }

    // ---------- серверы ----------

    fun importText(text: String) {
        val t = text.trim()
        // Одна http(s)-ссылка в буфере — это подписка
        if ((t.startsWith("http://") || t.startsWith("https://")) && t.none { it.isWhitespace() }) {
            addSubscription("", t)
            return
        }
        val found = LinkParser.parseMany(text)
        if (found.isEmpty()) {
            message.value = "Ссылки не распознаны"
            return
        }
        mergeProfiles(found, replaceSubId = null)
        message.value = "Добавлено серверов: ${found.size}"
    }

    fun addSubscription(name: String, url: String) {
        val u = url.trim()
        if (!u.startsWith("http")) {
            message.value = "Нужна ссылка http(s)"
            return
        }
        val sub = Subscription(UUID.randomUUID().toString(), name.trim().ifEmpty { "Подписка" }, u)
        viewModelScope.launch {
            busy.value = true
            try {
                val list = withContext(Dispatchers.IO) { SubFetcher.fetch(u, store.hwid()) }
                if (list.isEmpty()) {
                    message.value = "В подписке нет поддерживаемых серверов"
                } else if (list.all { it.host == "0.0.0.0" }) {
                    message.value = "Провайдер: ${list.first().name}"
                } else {
                    subs.update { it + sub }
                    store.saveSubs(subs.value)
                    mergeProfiles(list.map { it.copy(subId = sub.id) }, replaceSubId = sub.id)
                    message.value = "Подписка добавлена: ${list.size}"
                }
            } catch (e: Exception) {
                message.value = "Ошибка загрузки: ${e.message}"
            } finally {
                busy.value = false
            }
        }
    }

    fun refreshSubscriptions() {
        val all = subs.value
        if (all.isEmpty()) {
            message.value = "Подписок нет"
            return
        }
        viewModelScope.launch {
            busy.value = true
            var ok = 0
            for (s in all) {
                try {
                    val list = withContext(Dispatchers.IO) { SubFetcher.fetch(s.url, store.hwid()) }
                    if (list.isNotEmpty() && list.all { it.host == "0.0.0.0" }) {
                        message.value = "Провайдер: ${list.first().name}"
                    } else if (list.isNotEmpty()) {
                        mergeProfiles(list.map { it.copy(subId = s.id) }, replaceSubId = s.id)
                        ok++
                    }
                } catch (_: Exception) {
                }
            }
            busy.value = false
            if (message.value == null) message.value = "Обновлено подписок: $ok из ${all.size}"
        }
    }

    fun removeSubscription(id: String) {
        subs.update { l -> l.filter { it.id != id } }
        store.saveSubs(subs.value)
        profiles.update { l -> l.filter { it.subId != id } }
        store.saveProfiles(profiles.value)
        fixSelection()
    }

    fun remove(id: String) {
        profiles.update { l -> l.filter { it.id != id } }
        store.saveProfiles(profiles.value)
        fixSelection()
    }

    fun select(id: String) {
        selectedId.value = id
        store.saveSelectedId(id)
    }

    private fun mergeProfiles(incoming: List<Profile>, replaceSubId: String?) {
        profiles.update { old ->
            val delays = old.associate { it.id to it.delay }
            val base = if (replaceSubId != null) old.filter { it.subId != replaceSubId } else old
            val fresh = incoming.map { it.copy(delay = delays[it.id] ?: -1L) }
            (base + fresh).distinctBy { it.id }
        }
        store.saveProfiles(profiles.value)
        fixSelection()
    }

    private fun fixSelection() {
        val list = profiles.value
        if (list.none { it.id == selectedId.value }) {
            select0(list.firstOrNull()?.id)
        }
    }

    private fun select0(id: String?) {
        selectedId.value = id
        store.saveSelectedId(id)
    }

    // ---------- пинг ----------

    fun pingAll() {
        val list = profiles.value
        if (list.isEmpty()) {
            message.value = "Нет серверов"
            return
        }
        viewModelScope.launch {
            busy.value = true
            profiles.update { l -> l.map { it.copy(delay = -1L) } }
            withContext(Dispatchers.IO) {
                Engine.init(getApplication())
                val gate = Semaphore(6)
                list.map { p ->
                    async {
                        gate.withPermit {
                            val d = Engine.ping(p)
                            profiles.update { l -> l.map { if (it.id == p.id) it.copy(delay = d) else it } }
                        }
                    }
                }.awaitAll()
            }
            store.saveProfiles(profiles.value)
            busy.value = false
        }
    }

    // ---------- маршрутизация ----------

    fun updateSettings(s: RoutingSettings) {
        settings.value = s
        store.saveSettings(s)
    }
}
