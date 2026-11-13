package com.tokmak.vpn.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.tokmak.vpn.MainActivity
import com.tokmak.vpn.core.ConfigBuilder
import com.tokmak.vpn.core.Engine
import com.tokmak.vpn.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray

class TokmakVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.tokmak.vpn.START"
        const val ACTION_STOP = "com.tokmak.vpn.STOP"
        private const val CHANNEL = "vpn"
        private const val NOTIF_ID = 1

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(
                ctx, Intent(ctx, TokmakVpnService::class.java).setAction(ACTION_START)
            )
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, TokmakVpnService::class.java).setAction(ACTION_STOP))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var pfd: ParcelFileDescriptor? = null
    private var controller: CoreController? = null

    private val handler = object : CoreCallbackHandler {
        override fun startup(): Long = 0
        override fun shutdown(): Long = 0
        override fun onEmitStatus(code: Long, message: String?): Long = 0
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            disconnect()
            return START_NOT_STICKY
        }
        showForeground("Подключение…")
        VpnState.status.value = VpnStatus.Connecting
        job?.cancel()
        job = scope.launch { connect() }
        return START_NOT_STICKY
    }

    private fun connect() {
        try {
            cleanup()
            Engine.init(this)
            val store = Store(this)
            val profile = store.loadProfiles().firstOrNull { it.id == store.selectedId() }
                ?: throw IllegalStateException("Сервер не выбран")
            val settings = store.loadSettings()
            val config = ConfigBuilder.forVpn(profile, settings)

            val b = Builder()
                .setSession("Tokmak")
                .setMtu(1500)
                .setBlocking(false)
                .addAddress("172.19.0.1", 30)
                .addAddress("fd00:19::1", 126)
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) b.setMetered(false)

            when (settings.appMode) {
                "only" -> {
                    var any = false
                    settings.apps.filter { it != packageName }.forEach {
                        try {
                            b.addAllowedApplication(it); any = true
                        } catch (_: Exception) {
                        }
                    }
                    if (!any) throw IllegalStateException("Не выбрано ни одного приложения")
                }
                "bypass" -> {
                    b.addDisallowedApplication(packageName)
                    settings.apps.forEach {
                        try {
                            b.addDisallowedApplication(it)
                        } catch (_: Exception) {
                        }
                    }
                }
                else -> b.addDisallowedApplication(packageName)
            }

            val fd = b.establish() ?: throw IllegalStateException("Не удалось создать VPN-интерфейс")
            pfd = fd
            val c = Libv2ray.newCoreController(handler)
            controller = c
            c.startLoop(config, fd.fd)

            VpnState.status.value = VpnStatus.Connected(System.currentTimeMillis())
            showForeground("Подключено: ${profile.name}")
        } catch (e: Throwable) {
            fail(e.message ?: e.toString())
        }
    }

    private fun fail(message: String) {
        cleanup()
        VpnState.status.value = VpnStatus.Error(message)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun disconnect() {
        job?.cancel()
        scope.launch {
            cleanup()
            VpnState.status.value = VpnStatus.Disconnected
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    @Synchronized
    private fun cleanup() {
        try {
            controller?.stopLoop()
        } catch (_: Throwable) {
        }
        controller = null
        try {
            pfd?.close()
        } catch (_: Throwable) {
        }
        pfd = null
    }

    override fun onRevoke() {
        disconnect()
    }

    override fun onDestroy() {
        cleanup()
        if (VpnState.status.value !is VpnStatus.Error) VpnState.status.value = VpnStatus.Disconnected
        super.onDestroy()
    }

    // ---------- уведомление ----------

    private fun showForeground(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "VPN", NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, TokmakVpnService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE
        )
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Tokmak")
            .setContentText(text)
            .setContentIntent(open)
            .addAction(0, "Отключить", stop)
            .setOngoing(true)
            .build()
        val type = when {
            Build.VERSION.SDK_INT >= 34 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
            Build.VERSION.SDK_INT >= 29 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
            else -> 0
        }
        ServiceCompat.startForeground(this, NOTIF_ID, n, type)
    }
}
