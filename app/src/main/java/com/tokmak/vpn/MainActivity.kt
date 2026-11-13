package com.tokmak.vpn

import android.Manifest
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.tokmak.vpn.ui.MainViewModel
import com.tokmak.vpn.ui.TokmakApp
import com.tokmak.vpn.ui.theme.TokmakTheme
import com.tokmak.vpn.vpn.TokmakVpnService
import com.tokmak.vpn.vpn.VpnState
import com.tokmak.vpn.vpn.VpnStatus

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val vpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            if (r.resultCode == RESULT_OK) {
                TokmakVpnService.start(this)
            } else {
                VpnState.status.value = VpnStatus.Error("Разрешение на VPN не выдано")
            }
        }

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TokmakTheme {
                TokmakApp(vm = vm, onToggle = ::toggle)
            }
        }
    }

    private fun toggle() {
        when (VpnState.status.value) {
            is VpnStatus.Connected, VpnStatus.Connecting -> TokmakVpnService.stop(this)
            else -> {
                if (vm.selectedProfile() == null) {
                    vm.message.value = "Сначала добавь сервер на вкладке «Серверы»"
                    return
                }
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                val i = VpnService.prepare(this)
                if (i != null) vpnPermission.launch(i) else TokmakVpnService.start(this)
            }
        }
    }
}
