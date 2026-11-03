package com.starsmuggler.content

import com.starsmuggler.engine.content.DiceTable
import com.starsmuggler.engine.content.Equipment
import com.starsmuggler.engine.content.FactorTable
import com.starsmuggler.engine.content.Goods
import com.starsmuggler.engine.content.GameContent
import com.starsmuggler.engine.content.GameEvent
import com.starsmuggler.engine.content.RuleParagraph
import com.starsmuggler.engine.content.Sector
import com.starsmuggler.engine.content.Tile
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Reads a content file by its path inside the assets, such as `content/sector.json`. */
fun interface ContentSource {
    fun read(path: String): String
}

/** The game content plus the names and long texts of its ids in one language. */
class LoadedContent(val game: GameContent, val names: ContentNames, val texts: GameTexts)

/** A content file that could not be read, with the file and the reason. */
class ContentException(val path: String, message: String, cause: Throwable? = null) : Exception("$path: $message", cause)

/** Loads and checks the versioned JSON content (SMUG-9). */
class ContentLoader(private val source: ContentSource) {
    fun load(language: String = TextLanguage.DEFAULT): LoadedContent {
        val sector = read(SECTOR, SectorFile.serializer()).also { checkVersion(SECTOR, it.formatVersion) }.sector
        val tiles = read(TILES, TilesFile.serializer()).also { checkVersion(TILES, it.formatVersion) }.tiles
        val tables = read(TABLES, TablesFile.serializer()).also { checkVersion(TABLES, it.formatVersion) }
        val paragraphs = read(PARAGRAPHS, ParagraphsFile.serializer()).also { checkVersion(PARAGRAPHS, it.formatVersion) }.paragraphs
        val equipment = read(EQUIPMENT, EquipmentFile.serializer()).also { checkVersion(EQUIPMENT, it.formatVersion) }.equipment
        val goods = read(GOODS, GoodsFile.serializer()).also { checkVersion(GOODS, it.formatVersion) }.goods
        val events = read(EVENTS, EventsFile.serializer()).also { checkVersion(EVENTS, it.formatVersion) }.events
        val game = guard("content") { GameContent(sector, tiles, tables.tables, tables.factors, paragraphs, equipment, goods, events) }
        val dir = TextLanguage.assetDir(language)
        val names = read("$dir/$NAMES", ContentNames.serializer()).also { checkVersion("$dir/$NAMES", it.formatVersion) }
        val rules = read("$dir/$RULE_TEXTS", TextsFile.serializer()).also { checkVersion("$dir/$RULE_TEXTS", it.formatVersion) }.texts
        val eventTexts = read("$dir/$EVENT_TEXTS", TextsFile.serializer()).also { checkVersion("$dir/$EVENT_TEXTS", it.formatVersion) }.texts
        return LoadedContent(game, names, GameTexts(rules, eventTexts))
    }

    private fun <T> read(path: String, strategy: DeserializationStrategy<T>): T {
        val text = guard(path) { source.read(path) }
        return guard(path) { json.decodeFromString(strategy, text) }
    }

    private fun checkVersion(path: String, version: Int) {
        if (version != GameContent.FORMAT_VERSION) {
            throw ContentException(path, "format version $version, expected ${GameContent.FORMAT_VERSION}")
        }
    }

    private inline fun <T> guard(path: String, block: () -> T): T = try {
        block()
    } catch (e: SerializationException) {
        throw ContentException(path, e.message ?: e.toString(), e)
    } catch (e: IllegalArgumentException) {
        throw ContentException(path, e.message ?: e.toString(), e)
    } catch (e: java.io.IOException) {
        throw ContentException(path, "cannot be read (${e.message})", e)
    }

    companion object {
        const val SECTOR = "content/sector.json"
        const val TILES = "content/tiles.json"
        const val TABLES = "content/tables.json"
        const val PARAGRAPHS = "content/paragraphs.json"
        const val EQUIPMENT = "content/equipment.json"
        const val GOODS = "content/goods.json"
        const val EVENTS = "content/events.json"
        const val NAMES = "names.json"
        const val RULE_TEXTS = "rules.json"
        const val EVENT_TEXTS = "events.json"

        /** Strict on purpose: an unknown or misspelled key is an error, not silently dropped. */
        private val json = Json { ignoreUnknownKeys = false }
    }
}

/** Names of the content ids in one language, from `text/<language>/names.json`. */
@Serializable
data class ContentNames(
    val formatVersion: Int,
    val systems: Map<String, String> = emptyMap(),
    val areaTypes: Map<String, String> = emptyMap(),
    val equipment: Map<String, String> = emptyMap(),
    val goods: Map<String, String> = emptyMap(),
    /** Ways of travel, as in the detection table (r238). */
    val travelModes: Map<String, String> = emptyMap(),
    /** Short names of the `#key` table results. */
    val results: Map<String, String> = emptyMap(),
) {
    fun system(id: String): String = systems[id] ?: id

    fun areaType(key: String): String = areaTypes[key] ?: key

    fun equipment(id: String): String = equipment[id] ?: id

    fun goods(id: String): String = goods[id] ?: id

    fun travelMode(id: String): String = travelModes[id] ?: id

    fun result(key: String): String = results[key] ?: key
}

@Serializable
private class SectorFile(val formatVersion: Int, val sector: Sector)

@Serializable
private class TilesFile(val formatVersion: Int, val tiles: List<Tile>)

@Serializable
private class TablesFile(val formatVersion: Int, val tables: List<DiceTable>, val factors: List<FactorTable> = emptyList())

@Serializable
private class ParagraphsFile(val formatVersion: Int, val paragraphs: List<RuleParagraph>)

@Serializable
private class GoodsFile(val formatVersion: Int, val goods: List<Goods>)

@Serializable
private class EventsFile(val formatVersion: Int, val events: List<GameEvent>)

@Serializable
private class TextsFile(val formatVersion: Int, val texts: Map<String, TextEntry>)

@Serializable
private class EquipmentFile(val formatVersion: Int, val equipment: List<Equipment>)
