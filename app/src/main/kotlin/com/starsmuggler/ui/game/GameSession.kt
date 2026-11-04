package com.starsmuggler.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.starsmuggler.content.LoadedContent
import com.starsmuggler.data.AstuciaSuggestion
import com.starsmuggler.data.GameHistory
import com.starsmuggler.data.GameRecord
import com.starsmuggler.data.SaveSlots
import com.starsmuggler.engine.Action
import com.starsmuggler.engine.ActionResult
import com.starsmuggler.engine.Astucia
import com.starsmuggler.engine.AstuciaSetup
import com.starsmuggler.engine.Ending
import com.starsmuggler.engine.Engine
import com.starsmuggler.engine.GameState
import com.starsmuggler.engine.LogEntry
import com.starsmuggler.engine.Rejection
import com.starsmuggler.engine.dice.DiceHand
import kotlinx.coroutines.CancellationException
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
class GameSession(
    val content: LoadedContent,
    filesDir: File,
    private val scope: CoroutineScope,
    /** Shows every roll, such as with the 3D dice (SMUG-28). */
    private val hand: DiceHand = DiceHand.APP,
) {
    private val engine = Engine(content.game)
    private val slots = SaveSlots(File(filesDir, SAVES_DIR))
    private val history = GameHistory(filesDir)

    /** Every finished game, oldest first (PRD P10). */
    var games: List<GameRecord> by mutableStateOf(emptyList())
        private set

    /** How the next game may set Astucia, from the last finished one (r202); null before the first game ends. */
    val suggestion: AstuciaSuggestion?
        get() = games.lastOrNull()?.let { AstuciaSuggestion(it.astucia, Astucia.suggestion(it.won), it.won) }

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

    /** The player cancelled a roll of their own dice, and with it the last action (SMUG-30). */
    var rollCancelled: Boolean by mutableStateOf(false)
        private set

    /** What the last action added to the log, to show it on the bridge. */
    var lastEntries: List<LogEntry> by mutableStateOf(emptyList())
        private set

    /** Continues the game saved last, or starts a new one in slot 1 until the title menu exists (SMUG-62). */
    fun start() {
        if (game != null) return
        run {
            games = withContext(Dispatchers.IO) { history.games() }
            val latest = withContext(Dispatchers.IO) { slots.latest() }
            if (latest != null) {
                slot = latest
                game = withContext(Dispatchers.IO) { slots.load(latest) }
            } else {
                startGame(suggestion?.accept ?: AstuciaSetup.Roll)
            }
        }
    }

    /** A new game in the same slot, replacing the one there (r202 sets its Astucia). */
    fun newGame(astucia: AstuciaSetup) = run { startGame(astucia) }

    private suspend fun startGame(astucia: AstuciaSetup) {
        val state = engine.newGame(Random.nextLong(), astucia, hand)
        game = state
        lastEntries = state.log
        save(state)
    }

    /** Ends the game (r203f) and adds it to the history. */
    fun endGame(ending: Ending) = run {
        val result = engine.perform(checkNotNull(game), Action.EndGame(ending), hand)
        if (result !is ActionResult.Done) {
            rejection = (result as ActionResult.Rejected).reason
            return@run
        }
        game = result.state
        lastEntries = result.entries
        save(result.state)
        games = withContext(Dispatchers.IO) {
            history.add(result.state)
            history.games()
        }
    }

    /** Why [action] cannot be performed now, or null if it can. */
    fun check(action: Action): Rejection? {
        val state = game ?: return Rejection.NO_TIME
        return engine.check(state, action)
    }

    fun perform(action: Action) = run {
        val state = checkNotNull(game)
        when (val result = engine.perform(state, action, hand)) {
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
        rollCancelled = false
        scope.launch {
            try {
                block()
            } catch (_: CancellationException) {
                // The engine changes nothing until an action ends, so the game stays as it was.
                rollCancelled = true
            } finally {
                busy = false
            }
        }
    }

    private suspend fun save(state: GameState) = withContext(Dispatchers.IO) { slots.save(slot, state) }
}
