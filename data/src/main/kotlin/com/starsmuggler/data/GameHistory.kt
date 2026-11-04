package com.starsmuggler.data

import com.starsmuggler.engine.Astucia
import com.starsmuggler.engine.AstuciaSetup
import com.starsmuggler.engine.Ending
import com.starsmuggler.engine.GameState
import com.starsmuggler.engine.won
import kotlinx.serialization.Serializable
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** A finished game, as the records keep it (PRD P10). */
@Serializable
data class GameRecord(
    val endedAt: Long,
    val ending: Ending,
    val won: Boolean,
    /** Days played: the day the game ended on. */
    val days: Int,
    /** Cash in hand at the end, in Sees. */
    val cash: Int,
    /** Principal of the loan still owed. */
    val debt: Int,
    val astucia: Int,
    val seed: Long,
) {
    /** What the game was worth: cash minus what was still owed. */
    val netWorth: Int get() = cash - debt

    companion object {
        fun of(state: GameState, endedAt: Long): GameRecord = GameRecord(
            endedAt = endedAt,
            ending = checkNotNull(state.ending) { "The game has not ended" },
            won = state.won,
            days = state.clock.day,
            cash = state.cash,
            debt = state.loan.principal,
            astucia = state.duke.astucia,
            seed = state.seed,
        )
    }
}

@Serializable
private data class HistoryFile(val format: Int, val games: List<GameRecord>)

/**
 * Every finished game (PRD P10), in `history.json` in [dir]: it feeds the records screen and the
 * Astucia of the next game (r202). It is written like the save slots, to a temporary file renamed
 * over the old one; a file that cannot be read is moved aside, never overwritten.
 */
class GameHistory(private val dir: File, private val now: () -> Long = System::currentTimeMillis) {
    private val file get() = File(dir, FILE)

    /** The finished games, oldest first. */
    fun games(): List<GameRecord> {
        val file = file
        if (!file.exists()) return emptyList()
        return try {
            val history = SaveSlots.JSON.decodeFromString<HistoryFile>(file.readText())
            require(history.format <= FORMAT) { "History format ${history.format} is newer than $FORMAT" }
            history.games
        } catch (e: Exception) {
            // Kept for whoever wants to look at it; the history starts again.
            file.renameTo(File(dir, "$FILE.damaged-${now()}"))
            emptyList()
        }
    }

    /** Records the ended game [state] and returns it as a record. */
    fun add(state: GameState): GameRecord {
        val record = GameRecord.of(state, now())
        write(games() + record)
        return record
    }

    /** How the next game sets Astucia (r202): rolled the first time, or the last game's one with its suggested change. */
    fun suggestion(): AstuciaSuggestion? = games().lastOrNull()?.let { AstuciaSuggestion(it.astucia, Astucia.suggestion(it.won), it.won) }

    private fun write(games: List<GameRecord>) {
        dir.mkdirs()
        val temp = File(dir, "$FILE.tmp")
        temp.writeText(SaveSlots.JSON.encodeToString(HistoryFile(FORMAT, games)))
        try {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        const val FILE = "history.json"
        const val FORMAT = 1
    }
}

/** The Astucia of the last game, the change the rules suggest after it, and whether it was won (r202). */
data class AstuciaSuggestion(val previous: Int, val adjustment: Int, val lastWon: Boolean) {
    /** The player accepts the change. */
    val accept: AstuciaSetup get() = AstuciaSetup.Adjust(previous, adjustment)

    /** The player goes the other way. */
    val invert: AstuciaSetup get() = AstuciaSetup.Adjust(previous, -adjustment)

    /** The player keeps the same Astucia. */
    val keep: AstuciaSetup get() = AstuciaSetup.Adjust(previous, 0)
}
