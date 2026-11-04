package com.starsmuggler.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.starsmuggler.content.LoadedContent
import com.starsmuggler.data.SaveSlots
import com.starsmuggler.engine.Action
import com.starsmuggler.engine.ActionResult
import com.starsmuggler.engine.Engine
import com.starsmuggler.engine.GameState
import com.starsmuggler.engine.LogEntry
import com.starsmuggler.engine.Rejection
import com.starsmuggler.engine.dice.DiceHand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.random.Random

/**
 * The game being played on the main screen (SMUG-25): the engine over the loaded [content], the
 * current state, and the save slot it is written to after every action (PRD P8). Its fields are
 * Compose state, so the screens redraw when an action lands.
 */
class GameSession(val content: LoadedContent, filesDir: File, private val scope: CoroutineScope) {
    private val engine = Engine(content.game)
    private val slots = SaveSlots(File(filesDir, SAVES_DIR))

    var game: GameState? by mutableStateOf(null)
        private set

    /** The save slot the game lives in. */
    var slot: Int by mutableStateOf(1)
        private set

    /** An action is running, such as waiting for dice: no other one may start. */
    var busy: Boolean by mutableStateOf(false)
        private set

    /** Why the last action could not be performed, if it could not. */
    var rejection: Rejection? by mutableStateOf(null)
        private set

    /** What the last action added to the log, to show it on the bridge. */
    var lastEntries: List<LogEntry> by mutableStateOf(emptyList())
        private set

    /** Continues the game saved last, or starts a new one in slot 1 until the title menu exists (SMUG-62). */
    fun start() {
        if (game != null) return
        run {
            val latest = withContext(Dispatchers.IO) { slots.latest() }
            if (latest != null) {
                slot = latest
                game = withContext(Dispatchers.IO) { slots.load(latest) }
            } else {
                val state = engine.newGame(Random.nextLong())
                game = state
                lastEntries = state.log
                save(state)
            }
        }
    }

    /** Why [action] cannot be performed now, or null if it can. */
    fun check(action: Action): Rejection? {
        val state = game ?: return Rejection.NO_TIME
        return engine.check(state, action)
    }

    fun perform(action: Action) = run {
        val state = checkNotNull(game)
        when (val result = engine.perform(state, action, DiceHand.APP)) {
            is ActionResult.Done -> {
                game = result.state
                lastEntries = result.entries
                save(result.state)
            }
            is ActionResult.Rejected -> rejection = result.reason
        }
    }

    private fun run(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        rejection = null
        scope.launch {
            try {
                block()
            } finally {
                busy = false
            }
        }
    }

    private suspend fun save(state: GameState) = withContext(Dispatchers.IO) { slots.save(slot, state) }
}
