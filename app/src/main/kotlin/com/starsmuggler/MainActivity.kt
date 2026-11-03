package com.starsmuggler

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.starsmuggler.ui.main.MainScreen
import com.starsmuggler.ui.theme.StarSmugglerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Transparent bars with dark icons over the paper, also with three-button navigation.
        val bars = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        super.onCreate(savedInstanceState)
        setContent {
            StarSmugglerTheme {
                MainScreen()
            }
        }
    }
}
