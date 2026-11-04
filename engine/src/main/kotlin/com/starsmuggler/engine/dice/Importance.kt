package com.starsmuggler.engine.dice

/**
 * Whether the roll is an important one, which the 3D dice show by default (PRD P7, SMUG-29):
 * combat, hyperspace jumps, contacts and encounters, and any roll an event asks for. Routine
 * checks, such as breakdowns, are only written to the log.
 */
val RollRequest.important: Boolean
    get() = event != null || IMPORTANT_RULES.any { rule.id.startsWith(it) } || table in IMPORTANT_TABLES

/** Rule families of important rolls; a family also covers its sections (`r230` covers `r230d`). */
private val IMPORTANT_RULES = listOf(
    "r202", // Astucia, asked by events
    "r203b", // contact
    "r212c", "r212d", // hyperspace jumps
    "r224", "r225", "r226", "r227", // encounters
    "r230", // combat
    "r231", // wounds
)

/** Tables of important rolls. */
private val IMPORTANT_TABLES = setOf("r233", "r237", "r238")
