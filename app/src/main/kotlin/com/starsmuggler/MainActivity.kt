package com.starsmuggler

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.starsmuggler.ui.gallery.ComponentGallery
import com.starsmuggler.ui.theme.PaperBackground
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
                // The gallery stands in for the main screen until SMUG-5; release builds show bare paper.
                if (BuildConfig.DEBUG) ComponentGallery() else PaperBackground(Modifier.fillMaxSize()) {}
            }
        }
    }
}
