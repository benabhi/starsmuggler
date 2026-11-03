package com.starsmuggler.content

import com.starsmuggler.engine.content.AreaType
import com.starsmuggler.engine.content.GameContent
import com.starsmuggler.engine.content.Outcome
import com.starsmuggler.engine.content.RuleRef

/**
 * Checks that the whole content holds together (SMUG-16): every rule and event it names exists,
 * every id has its Spanish name and text, and systems, tiles and tables agree with each other.
 * Problems of the original books are listed in [KNOWN] with their reason and are not reported.
 */
object ContentValidator {
    /** One thing that does not hold, found in [where]. */
    data class Problem(val where: String, val what: String) {
        override fun toString() = "$where: $what"
    }

    /**
     * References printed in the books that point nowhere, as `where -> reference`. They come
     * from the original (translation notes of the rule and event books) and stay as printed.
     */
    val KNOWN: Map<String, String> = mapOf(
        "instructions -> e000" to "the rules explain the numbering format, not a reference",
        "instructions -> r000" to "the rules explain the numbering format, not a reference",
        "r225b -> e000" to "the rules explain the numbering format, not a reference",
        "r238 -> r239" to "the rule book says r239 does not exist",
        "r242 -> r243" to "the rule book says r243 to r299 do not exist",
        "r242 -> r299" to "the rule book says r243 to r299 do not exist",
        "r233 -> r295a" to "erratum of the original r233 City row (rules translation note 10)",
        "table r233 -> r295a" to "erratum of the original r233 City row (rules translation note 10)",
        "e086 -> r239" to "erratum of the original event book; r239 does not exist (likely r238)",
        "e153 -> r215d" to "erratum of the original event book; r215d does not exist (likely r216d)",
    )

    fun validate(content: LoadedContent): List<Problem> {
        val problems = mutableListOf<Problem>()
        val game = content.game
        val texts = content.texts
        val names = content.names

        fun reference(where: String, id: String) {
            val exists = if (id.startsWith("e")) texts.event(id) != null else texts.rule(id) != null
            if (!exists && "$where -> $id" !in KNOWN) problems += Problem(where, "names $id, which has no text")
        }

        fun name(where: String, map: Map<String, String>, id: String) {
            if (map[id].isNullOrBlank()) problems += Problem(where, "no Spanish name for '$id'")
        }

        // Every reference inside the rule and event texts.
        for ((id, entry) in texts.rules + texts.events) {
            for (text in entry.blocks.flatMap { it.texts() }) {
                for (match in GameTexts.REFERENCE.findAll(text)) reference(id, match.groupValues[1])
            }
            entry.parent?.let { if (texts.rule(it)?.children?.contains(id) != true) problems += Problem(id, "parent $it does not list it") }
            if (entry.title.isBlank()) problems += Problem(id, "no title")
        }

        // Every rule and event the data names.
        val rules = buildList<Pair<String, RuleRef>> {
            add("sector" to game.sector.rule)
            game.systems.values.forEach { add("system ${it.id}" to it.rule) }
            game.sector.patterns.forEach { add("pattern ${it.id}" to it.rule) }
            game.tables.values.forEach { add("table ${it.id}" to it.rule) }
            game.factors.values.forEach { add("factors ${it.id}" to it.rule) }
            game.paragraphs.values.forEach { add("paragraph ${it.rule}" to it.rule) }
            game.equipment.values.forEach { add("equipment ${it.id}" to it.rule) }
            game.goods.values.forEach { add("goods ${it.id}" to it.rule) }
            AreaType.entries.forEach { add("area type ${it.key}" to it.rule) }
        }
        for ((where, rule) in rules) reference(where, rule.id)
        for (table in game.tables.values) {
            for (row in table.rows) for (entry in row.entries) {
                when (val result = entry.result) {
                    is Outcome.Event -> reference("table ${table.id}", result.ref.id)
                    is Outcome.Rule -> reference("table ${table.id}", result.ref.id)
                    is Outcome.Key -> if (result.key !in names.results && result.key !in names.goods) {
                        problems += Problem("table ${table.id}", "no Spanish name for result #${result.key}")
                    }
                    else -> Unit
                }
            }
        }
        for (system in game.systems.values) system.entryEvents.forEach { reference("system ${system.id}", it.id) }

        // Events: one duration and one text each.
        val eventIds = game.events.keys
        (eventIds - texts.events.keys).forEach { problems += Problem("event $it", "has a duration but no text") }
        (texts.events.keys - eventIds).forEach { problems += Problem("event $it", "has a text but no duration") }

        // Names of every id.
        game.systems.keys.forEach { name("system", names.systems, it) }
        AreaType.entries.forEach { name("area type", names.areaTypes, it.key) }
        game.equipment.keys.forEach { name("equipment", names.equipment, it) }
        game.goods.keys.forEach { name("goods", names.goods, it) }
        game.table("r238").rows.mapNotNull { it.key }.forEach { name("travel mode", names.travelModes, it) }

        // Tables keyed by area types, systems, wealth and tech codes.
        val areaKeys = AreaType.entries.map { it.key }.toSet()
        for (key in game.table("r233").rows.mapNotNull { it.key }) if (key !in areaKeys) problems += Problem("table r233", "row for unknown area type '$key'")
        for (type in AreaType.entries - AreaType.ASTEROID) if (game.table("r233").row(type.key) == null) problems += Problem("table r233", "no row for ${type.key}")
        for (factors in game.factors.values) for (key in factors.factors.keys) {
            if (key !in areaKeys && key !in PLACES) problems += Problem("factors ${factors.id}", "unknown place '$key'")
        }
        for (system in game.systems.values) {
            if (game.table("r233.system").row(system.id) == null) problems += Problem("table r233.system", "no row for ${system.id}")
            if (game.table("r241").row(system.wealth.toString()) == null) problems += Problem("table r241", "no row for wealth ${system.wealth}")
            if (game.table("r235").row(system.tech.toString()) == null) problems += Problem("table r235", "no row for tech ${system.tech}")
            if (system.asteroids && game.tiles.values.none { it.planetSide == null }) problems += Problem("system ${system.id}", "has asteroids but there is no asteroid tile")
        }

        // Tiles: every planet tile is used by some planet, and every area type on them is named.
        val used = game.systems.values.flatMap { s -> s.planets.flatMap { it.tiles } }.toSet()
        for (tile in game.tiles.values.filter { it.planetSide != null }) if (tile.id !in used) problems += Problem("tile ${tile.id}", "no planet uses it")
        for (tile in game.tiles.values) {
            for (area in tile.areas) name("tile ${tile.id}", names.areaTypes, area.type.key)
            val linked = tile.connectors.flatMap { listOfNotNull(it.from, it.to) }.toSet()
            // Every planet area can be reached; orbit-only areas (stations, asteroids) need no lines.
            for (area in tile.areas) {
                val orbital = area.type == AreaType.SPACE_STATION || area.type == AreaType.ASTEROID
                if (!orbital && area.id !in linked) problems += Problem("tile ${tile.id}", "area ${area.id} has no travel line")
            }
        }
        return problems
    }

    /** Places that are not area types: the orbit (r242). */
    private val PLACES = setOf("orbit")

    private fun TextBlock.texts(): List<String> = when (this) {
        is TextBlock.Heading -> listOf(text)
        is TextBlock.Paragraph -> listOf(text)
        is TextBlock.Bullet -> listOf(text)
        is TextBlock.Table -> rows.flatten() + listOfNotNull(caption)
    }
}
