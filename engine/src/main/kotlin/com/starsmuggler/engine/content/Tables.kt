package com.starsmuggler.engine.content

import kotlinx.serialization.Serializable

/**
 * A dice table of the rules (r233 to r242). A one-column table has a single row with no key;
 * a cross table has one row per key, such as an area type or a system id, and each row maps
 * the roll to its result.
 */
@Serializable
data class DiceTable(
    /** Stable id, such as `r233` or `r233.system`. */
    val id: String,
    val rule: RuleRef,
    val dice: Dice,
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
