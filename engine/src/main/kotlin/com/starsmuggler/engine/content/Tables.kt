package com.starsmuggler.engine.content

import kotlinx.serialization.Serializable

/**
 * A table of the rules (r233 to r242, r327 to r333). A one-column table has a single row with
 * no key; a cross table has one row per key, such as an area type or a system id, and each row
 * maps the roll to its result. Without [dice] the entries are looked up by another number,
 * such as the system tech code (r238) or the days since a cache was placed (r242).
 */
@Serializable
data class DiceTable(
    /** Stable id, such as `r233` or `r233.system`. */
    val id: String,
    val rule: RuleRef,
    val dice: Dice? = null,
    val rows: List<TableRow>,
) {
    init {
        require(rows.isNotEmpty()) { "Table $id has no rows" }
        require(rows.map { it.key }.toSet().size == rows.size) { "Table $id repeats a row key" }
    }

    fun row(key: String? = null): TableRow? = rows.firstOrNull { it.key == key }

    /** The result for a modified [roll] in the row [key], or null if no entry covers it. */
    fun lookup(roll: Int, key: String? = null): Outcome? = row(key)?.entries?.firstOrNull { roll in it.roll }?.result
}

@Serializable
data class TableRow(
    val key: String? = null,
    val entries: List<TableEntry>,
) {
    init {
        val sorted = entries.sortedBy { it.roll.min }
        sorted.zipWithNext().forEach { (a, b) ->
            require(a.roll.max < b.roll.min) { "Row ${key ?: "-"}: rolls ${a.roll} and ${b.roll} overlap" }
        }
    }
}

@Serializable
data class TableEntry(val roll: RollRange, val result: Outcome)

/**
 * A factor that depends on where the player is, keyed by area type key or by a place such as
 * `asteroids` or `orbit`: detection adjustments (r238) and base cache factors (r242).
 */
@Serializable
data class FactorTable(
    val id: String,
    val rule: RuleRef,
    val factors: Map<String, Int>,
    /** The factor of any place not listed. */
    val otherwise: Int = 0,
) {
    fun factor(place: String): Int = factors[place] ?: otherwise
}
