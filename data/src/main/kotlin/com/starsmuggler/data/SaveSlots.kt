package com.starsmuggler.data

import com.starsmuggler.engine.GameState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** What a save file holds: the format it was written in, when, and the whole game. */
@Serializable
data class SaveFile(val format: Int, val savedAt: Long, val state: GameState)

/** One of the save slots, as the list shows it. */
sealed interface SlotInfo {
    val slot: Int

    data class Empty(override val slot: Int) : SlotInfo

    data class Saved(override val slot: Int, val savedAt: Long, val state: GameState) : SlotInfo

    /** The file is there but cannot be read: a newer app, a broken file. It is kept as it is. */
    data class Damaged(override val slot: Int, val reason: String) : SlotInfo
}

/** A save file that cannot be read. */
class SaveException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * The game's save slots (PRD P8): [SLOTS] JSON files in [dir], each overwritten after every action
 * and never kept in older versions, so there is no undo. Writes go to a temporary file renamed over
 * the old one, so closing the app halfway never leaves a broken slot.
 */
class SaveSlots(private val dir: File, private val now: () -> Long = System::currentTimeMillis) {
    fun list(): List<SlotInfo> = (1..SLOTS).map(::info)

    fun info(slot: Int): SlotInfo {
        val file = file(slot)
        if (!file.exists()) return SlotInfo.Empty(slot)
        return try {
            val save = read(file)
            SlotInfo.Saved(slot, save.savedAt, save.state)
        } catch (e: SaveException) {
            SlotInfo.Damaged(slot, e.message.orEmpty())
        }
    }

    /** The game in [slot]; throws [SaveException] if the slot is empty or damaged. */
    fun load(slot: Int): GameState {
        val file = file(slot)
        if (!file.exists()) throw SaveException("Slot $slot is empty")
        return read(file).state
    }

    fun save(slot: Int, state: GameState) {
        val text = JSON.encodeToString(SaveFile(FORMAT, now(), state))
        dir.mkdirs()
        val temp = File(dir, "slot-$slot.json.tmp")
        temp.writeText(text)
        val target = file(slot).toPath()
        try {
            Files.move(temp.toPath(), target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun delete(slot: Int) {
        file(slot).delete()
    }

    /** The slot saved last, to continue the game; null when no slot holds a readable game. */
    fun latest(): Int? = list().filterIsInstance<SlotInfo.Saved>().maxByOrNull { it.savedAt }?.slot

    private fun file(slot: Int): File {
        require(slot in 1..SLOTS) { "No save slot $slot" }
        return File(dir, "slot-$slot.json")
    }

    private fun read(file: File): SaveFile {
        try {
            val json = JSON.parseToJsonElement(file.readText()).jsonObject
            val format = json["format"]?.jsonPrimitive?.int ?: throw SaveException("${file.name}: no format")
            if (format > FORMAT) throw SaveException("${file.name}: format $format is newer than $FORMAT")
            return JSON.decodeFromJsonElement<SaveFile>(migrate(json, format))
        } catch (e: SaveException) {
            throw e
        } catch (e: Exception) {
            // Broken JSON, unknown keys, values the engine refuses: the slot is damaged, not the app.
            throw SaveException("${file.name}: ${e.message}", e)
        }
    }

    companion object {
        const val SLOTS = 3

        /** Version of the save format; bump it and add a migration when saved games would stop loading. */
        const val FORMAT = 1

        /** Migrations of the JSON from format N to N + 1, keyed by N. Empty while the format is 1. */
        private val migrations: Map<Int, (JsonObject) -> JsonObject> = emptyMap()

        /** Every value is written, so changing a default never changes an old game. Unknown keys fail. */
        internal val JSON = Json { encodeDefaults = true }

        /** Brings a save [json] of format [from] up to format [to] with [steps]. */
        internal fun migrate(
            json: JsonObject,
            from: Int,
            to: Int = FORMAT,
            steps: Map<Int, (JsonObject) -> JsonObject> = migrations,
        ): JsonObject {
            var current = json
            for (format in from until to) {
                val step = steps[format] ?: throw SaveException("No migration from save format $format")
                current = step(current)
            }
            return JsonObject(current + ("format" to JsonPrimitive(to)))
        }
    }
}
