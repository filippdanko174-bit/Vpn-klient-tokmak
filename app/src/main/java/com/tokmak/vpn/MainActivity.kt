package com.tokmak.vpn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.tokmak.vpn.ui.TokmakApp
import com.tokmak.vpn.ui.theme.TokmakTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TokmakTheme {
                TokmakApp()
            }
        }
    }
}
