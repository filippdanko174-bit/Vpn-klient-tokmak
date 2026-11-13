package com.tokmak.vpn.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tokmak.vpn.data.Profile
import com.tokmak.vpn.data.RoutingSettings
import com.tokmak.vpn.vpn.VpnState
import com.tokmak.vpn.vpn.VpnStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class Tab(val title: String, val icon: ImageVector)

@Composable
fun TokmakApp(vm: MainViewModel, onToggle: () -> Unit) {
    val tabs = listOf(
        Tab("Главная", Icons.Default.Home),
        Tab("Серверы", Icons.AutoMirrored.Filled.List),
        Tab("Маршруты", Icons.Default.Settings),
    )
    var tab by rememberSaveable { mutableStateOf(0) }
    val snack = remember { SnackbarHostState() }
    val msg by vm.message.collectAsState()
    val status by VpnState.status.collectAsState()

    LaunchedEffect(msg) {
        msg?.let { snack.showSnackbar(it); vm.message.value = null }
    }
    LaunchedEffect(status) {
        (status as? VpnStatus.Error)?.let { snack.showSnackbar(it.message) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = t.title) },
                        label = { Text(t.title) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (tab) {
                0 -> HomeScreen(vm, onToggle) { tab = 1 }
                1 -> ServersScreen(vm)
                else -> RoutingScreen(vm)
            }
        }
    }
}

// ============================ Главная ============================

@Composable
private fun HomeScreen(vm: MainViewModel, onToggle: () -> Unit, openServers: () -> Unit) {
    val status by VpnState.status.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val selectedId by vm.selectedId.collectAsState()
    val selected = profiles.firstOrNull { it.id == selectedId }

    val connected = status is VpnStatus.Connected
    val connecting = status is VpnStatus.Connecting

    val color by animateColorAsState(
        when {
            connected -> MaterialTheme.colorScheme.primary
            connecting -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.surfaceVariant
        }, label = "btn"
    )
    val onColor = if (connected || connecting) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant

    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f, targetValue = 1.07f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "p"
    )

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(status) {
        while (status is VpnStatus.Connected) {
            now = System.currentTimeMillis(); delay(1000)
        }
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Tokmak", fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(36.dp))

        Surface(
            modifier = Modifier.size(200.dp).scale(if (connecting) pulse else 1f).clickable(onClick = onToggle),
            shape = CircleShape,
            color = color,
            shadowElevation = if (connected) 12.dp else 2.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        if (connected) Icons.Default.Lock else Icons.Default.PlayArrow,
                        contentDescription = null, tint = onColor, modifier = Modifier.size(64.dp)
                    )
                    Text(
                        when {
                            connected -> "Отключить"
                            connecting -> "Подключение…"
                            else -> "Подключить"
                        }, color = onColor, fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Text(
            when (val s = status) {
                is VpnStatus.Connected -> "Защищено · " + fmt((now - s.since) / 1000)
                VpnStatus.Connecting -> "Устанавливаем соединение"
                is VpnStatus.Error -> "Ошибка"
                VpnStatus.Disconnected -> "Не подключено"
            },
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(28.dp))

        Card(
            modifier = Modifier.fillMaxWidth().clickable(onClick = openServers),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Сервер", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                if (selected == null) {
                    Text("Не выбран — нажми, чтобы добавить", style = MaterialTheme.typography.titleMedium)
                } else {
                    Text(selected.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(
                        "${selected.protocol} · ${selected.host}:${selected.port}" + delayText(selected.delay)
                            .let { if (it.isEmpty()) "" else " · $it" },
                        style = MaterialTheme.typography.bodySmall, maxLines = 1
                    )
                }
            }
        }
    }
}

private fun fmt(sec: Long): String {
    val s = sec.coerceAtLeast(0)
    return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}

private fun delayText(d: Long): String = when {
    d >= 0 -> "$d мс"
    d == -2L -> "нет связи"
    else -> ""
}

// ============================ Серверы ============================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServersScreen(vm: MainViewModel) {
    val profiles by vm.profiles.collectAsState()
    val subs by vm.subs.collectAsState()
    val selectedId by vm.selectedId.collectAsState()
    val busy by vm.busy.collectAsState()
    val clipboard = LocalClipboardManager.current
    var showSub by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Серверы") })
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(onClick = { vm.importText(clipboard.getText()?.text.orEmpty()) }) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Из буфера")
            }
            FilledTonalButton(onClick = { showSub = true }) { Text("Подписка") }
            FilledTonalButton(onClick = { vm.pingAll() }, enabled = !busy) {
                Icon(Icons.Default.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Пинг")
            }
        }
        if (subs.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Подписок: ${subs.size}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.refreshSubscriptions() }, enabled = !busy) { Text("Обновить") }
                TextButton(onClick = { subs.forEach { vm.removeSubscription(it.id) } }) { Text("Удалить все") }
            }
        }

        if (profiles.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Серверов пока нет.\nСкопируй ссылку vless:// vmess:// trojan:// ss:// и нажми «Из буфера», либо добавь подписку.",
                    textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(profiles, key = { it.id }) { p ->
                    ServerRow(p, p.id == selectedId, { vm.select(p.id) }, { vm.remove(p.id) })
                }
            }
        }
    }

    if (showSub) SubscriptionDialog(onDismiss = { showSub = false }) { name, url ->
        showSub = false; vm.addSubscription(name, url)
    }
}

@Composable
private fun ServerRow(p: Profile, selected: Boolean, onSelect: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainer
        ),
    ) {
        Row(Modifier.padding(start = 18.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text("${p.protocol} · ${p.host}:${p.port}", style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            val d = delayText(p.delay)
            if (p.delay == -1L) Unit else Text(
                d,
                color = when {
                    p.delay < 0 -> MaterialTheme.colorScheme.error
                    p.delay < 300 -> Color(0xFF2E7D32)
                    else -> Color(0xFFEF6C00)
                },
                fontWeight = FontWeight.Medium,
            )
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Удалить") }
        }
    }
}

@Composable
private fun SubscriptionDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить подписку") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true)
                OutlinedTextField(url, { url = it }, label = { Text("Ссылка https://…") }, maxLines = 3)
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(name, url) }) { Text("Добавить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

// ============================ Маршруты ============================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutingScreen(vm: MainViewModel) {
    val s by vm.settings.collectAsState()
    var showApps by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Маршрутизация") })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SwitchRow("Локальная сеть напрямую", "192.168.x.x, 10.x.x.x и т.п.", s.directLan) {
                vm.updateSettings(s.copy(directLan = it))
            }
            SwitchRow("Российские сайты напрямую", "Домены .ru .su .рф и IP России", s.directRu) {
                vm.updateSettings(s.copy(directRu = it))
            }
            SwitchRow("Блокировать рекламу", "Домены из списка category-ads-all", s.blockAds) {
                vm.updateSettings(s.copy(blockAds = it))
            }

            OutlinedTextField(
                value = s.directDomains,
                onValueChange = { vm.updateSettings(s.copy(directDomains = it)) },
                label = { Text("Домены напрямую (по одному в строке)") },
                placeholder = { Text("example.com") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )

            Text("Приложения", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(s.appMode == "all", { vm.updateSettings(s.copy(appMode = "all")) }, { Text("Все через VPN") })
                FilterChip(s.appMode == "bypass", { vm.updateSettings(s.copy(appMode = "bypass")) }, { Text("Исключить") })
                FilterChip(s.appMode == "only", { vm.updateSettings(s.copy(appMode = "only")) }, { Text("Только выбранные") })
            }
            if (s.appMode != "all") {
                FilledTonalButton(onClick = { showApps = true }) {
                    Text("Выбрать приложения (${s.apps.size})")
                }
            }
            Text(
                "Изменения применяются при следующем подключении.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    if (showApps) AppPickerDialog(s, onDismiss = { showApps = false }) { vm.updateSettings(s.copy(apps = it)); showApps = false }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked, onChange)
        }
    }
}

private data class AppItem(val label: String, val pkg: String)

@Composable
private fun AppPickerDialog(s: RoutingSettings, onDismiss: () -> Unit, onSave: (Set<String>) -> Unit) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<AppItem>?>(null) }
    var chosen by remember { mutableStateOf(s.apps) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val i = android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(i, 0)
                .map { AppItem(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
                .filter { it.pkg != ctx.packageName }
                .distinctBy { it.pkg }
                .sortedBy { it.label.lowercase() }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Приложения") },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, label = { Text("Поиск") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                val list = apps
                if (list == null) {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    LazyColumn(Modifier.height(360.dp)) {
                        items(list.filter { query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true) }, key = { it.pkg }) { a ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    chosen = if (a.pkg in chosen) chosen - a.pkg else chosen + a.pkg
                                },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(a.pkg in chosen, { chosen = if (it) chosen + a.pkg else chosen - a.pkg })
                                Column {
                                    Text(a.label, maxLines = 1)
                                    Text(a.pkg, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(chosen) }) { Text("Готово") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
