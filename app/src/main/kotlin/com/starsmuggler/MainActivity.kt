package com.starsmuggler

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import com.starsmuggler.dice.DiceSound
import com.starsmuggler.dice.DiceTable
import com.starsmuggler.ui.dice.AnimatedDice
import com.starsmuggler.ui.dice.DiceSettings
import com.starsmuggler.ui.dice.LocalDice
import com.starsmuggler.ui.dice.OwnDiceDialog
import com.starsmuggler.ui.dice.RollCaption
import com.starsmuggler.ui.main.MainScreen
import com.starsmuggler.ui.theme.StarSmugglerTheme

/**
 * Single activity. The Compose UI fills the window and the 3D dice overlay sits above it as a
 * separate transparent view, so dice can roll over any screen (SMUG-28).
 */
class MainActivity : ComponentActivity() {

    private lateinit var diceTable: DiceTable
    private lateinit var diceSound: DiceSound

    override fun onCreate(savedInstanceState: Bundle?) {
        // Transparent bars with dark icons over the paper, also with three-button navigation.
        val bars = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this)
        diceTable = DiceTable(root)
        diceSound = DiceSound(this)
        val dice = AnimatedDice(diceTable, diceSound, DiceSettings(this))
        val composeView = ComposeView(this).apply {
            setContent {
                // The page loads while the game does, so the first roll does not wait for it.
                LaunchedEffect(Unit) { dice.preload() }
                StarSmugglerTheme {
                    CompositionLocalProvider(LocalDice provides dice) {
                        Box(Modifier.fillMaxSize()) {
                            MainScreen()
                            RollCaption(dice)
                            dice.pending?.let { roll ->
                                OwnDiceDialog(roll.request, onRoll = { roll.answer.complete(it) }, onCancel = { roll.answer.cancel() })
                            }
                        }
                    }
                }
            }
        }
        root.addView(composeView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        setContentView(root)
    }

    override fun onPause() {
        super.onPause()
        diceTable.pause()
    }

    override fun onResume() {
        super.onResume()
        diceTable.resume()
    }

    override fun onDestroy() {
        diceTable.destroy()
        diceSound.release()
        super.onDestroy()
    }
}
