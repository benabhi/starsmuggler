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
import com.starsmuggler.engine.BoatMove
import com.starsmuggler.engine.Flight
import com.starsmuggler.engine.JumpOption
import com.starsmuggler.engine.Location
import com.starsmuggler.engine.LandRoute
import com.starsmuggler.engine.LogEntry
import com.starsmuggler.engine.Rejection
import com.starsmuggler.engine.ShipPosition
import com.starsmuggler.engine.dice.DiceHand
import com.starsmuggler.ui.sound.SoundBoard
import com.starsmuggler.ui.sound.SoundEffect
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
    /** Plays the game's sounds, such as the loan warning (SMUG-31). */
    private val sounds: SoundBoard? = null,
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

    /** Every place the starship can reach with one sublight move (r212g). */
    fun sublightDestinations(): List<ShipPosition> = game?.let { engine.sublightDestinations(it) } ?: emptyList()

    /** The areas the starship can fly to over its planet (r203a). */
    fun flights(): List<Flight> = game?.let { engine.flights(it) } ?: emptyList()

    /** Where the boat can fly from where it is (r214b). */
    fun boatMoves(): List<BoatMove> = game?.let { engine.boatMoves(it) } ?: emptyList()

    /** The stops an orbital shuttle takes Duke to (r215a). */
    fun shuttleStops(): List<Location> = game?.let { engine.shuttleStops(it) } ?: emptyList()

    /** The travel lines out of Duke's area (r204g). */
    fun landRoutes(): List<LandRoute> = game?.let { engine.landRoutes(it) } ?: emptyList()

    /** Every system the starship can jump to, with cost and risk (r212b-c). */
    fun jumpOptions(): List<JumpOption> = game?.let { engine.jumpOptions(it) } ?: emptyList()

    fun perform(action: Action) = run {
        val state = checkNotNull(game)
        when (val result = engine.perform(state, action, hand)) {
            is ActionResult.Done -> {
                game = result.state
                lastEntries = result.entries
                save(result.state)
                if (result.state.cash > state.cash) sounds?.play(SoundEffect.MONEY)
                if (result.entries.any { it is LogEntry.Jumped }) sounds?.play(SoundEffect.JUMP)
                if (result.state.clock.day > state.clock.day) warnOfLoan(result.state)
            }
            is ActionResult.Rejected -> rejection = result.reason
        }
    }

    /** A new day with the loan payment two days away or less beeps a warning (PRD P11, r203e). */
    private fun warnOfLoan(state: GameState) {
        val due = state.loan.interestDueIn(state.clock) ?: return
        // Due in 1 is when today ends, so 3 is two days after today.
        if (due <= LOAN_WARNING_DAYS + 1) sounds?.play(SoundEffect.LOAN_WARNING)
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

/** Days ahead of the loan payment that a new day starts warning (PRD P11). */
private const val LOAN_WARNING_DAYS = 2
