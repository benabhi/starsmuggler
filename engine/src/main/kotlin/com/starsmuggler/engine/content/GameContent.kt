package com.starsmuggler.engine.content

/**
 * Everything the engine reads from the game: sector, tiles, tables, rule paragraphs, equipment,
 * goods and event durations, loaded from the versioned JSON of `:content`. It holds ids and values only; texts
 * are looked up by id.
 */
class GameContent(
    val sector: Sector,
    tiles: List<Tile>,
    tables: List<DiceTable>,
    factors: List<FactorTable>,
    paragraphs: List<RuleParagraph>,
    equipment: List<Equipment>,
    goods: List<Goods>,
    events: List<GameEvent>,
) {
    val systems: Map<String, StarSystem> = sector.systems.associateByUnique("system") { it.id }
    val tiles: Map<String, Tile> = tiles.associateByUnique("tile") { it.id }
    val tables: Map<String, DiceTable> = tables.associateByUnique("table") { it.id }
    val factors: Map<String, FactorTable> = factors.associateByUnique("factor table") { it.id }
    val paragraphs: Map<String, RuleParagraph> = paragraphs.associateByUnique("paragraph") { it.rule.id }
    val equipment: Map<String, Equipment> = equipment.associateByUnique("equipment") { it.id }
    val goods: Map<String, Goods> = goods.associateByUnique("goods") { it.id }
    val events: Map<String, GameEvent> = events.associateByUnique("event") { it.id.id }

    init {
        for (pattern in sector.patterns) {
            for (route in pattern.routes) {
                require(route.a in systems && route.b in systems) { "Pattern ${pattern.id}: route ${route.a}-${route.b} names an unknown system" }
            }
            val placed = pattern.nodes.map { it.system }.toSet()
            require(placed == systems.keys) { "Pattern ${pattern.id} must place every system once; differs in ${(placed - systems.keys) + (systems.keys - placed)}" }
        }
        for (system in systems.values) {
            for (planet in system.planets) {
                for (tile in planet.tiles) require(tile in this.tiles) { "System ${system.id}: planet ${planet.number} uses unknown tile '$tile'" }
                seams(planet)
            }
        }
        for (paragraph in this.paragraphs.values) {
            val table = paragraph.table?.let { id -> this.tables[id] ?: throw IllegalArgumentException("Paragraph ${paragraph.rule}: unknown table '$id'") }
            for (id in paragraph.sells) require(id in this.goods || id in this.equipment) { "Paragraph ${paragraph.rule}: unknown goods or equipment '$id'" }
            if (paragraph.kind == ParagraphKind.BUY && table != null) {
                for (entry in table.rows.flatMap { it.entries }) {
                    val key = (entry.result as? Outcome.Key)?.key
                    require(key != null && key in this.goods) { "Table ${table.id}: purchase result ${entry.result} is not a known goods" }
                }
            }
        }
    }

    fun system(id: String): StarSystem = systems[id] ?: throw NoSuchElementException("No system '$id'")

    fun tile(id: String): Tile = tiles[id] ?: throw NoSuchElementException("No tile '$id'")

    fun table(id: String): DiceTable = tables[id] ?: throw NoSuchElementException("No table '$id'")

    fun factors(id: String): FactorTable = factors[id] ?: throw NoSuchElementException("No factor table '$id'")

    fun paragraph(rule: RuleRef): RuleParagraph = paragraphs[rule.id] ?: throw NoSuchElementException("No paragraph '$rule'")

    fun event(ref: EventRef): GameEvent = events[ref.id] ?: throw NoSuchElementException("No event '$ref'")

    /**
     * The travel lines that cross the seam between the two tiles of [planet] (r204d). When both
     * halves are drawn on the same side of their scans, the second tile is turned around to
     * close the circle, so its top slot meets the first tile's bottom one.
     */
    fun seams(planet: Planet): List<Seam> {
        val first = tile(planet.tiles[0])
        val second = tile(planet.tiles[1])
        require(first.planetSide != null && second.planetSide != null) { "Planet ${planet.tiles} uses a tile with no planet" }
        val turned = first.planetSide == second.planetSide
        return (0 until Tile.EDGE_SLOTS).mapNotNull { slot ->
            val a = first.edge(slot)
            val b = second.edge(if (turned) Tile.EDGE_SLOTS - 1 - slot else slot)
            require((a == null) == (b == null)) { "Planet ${planet.tiles.joinToString("-")}: edge slot $slot is open on one tile only" }
            if (a == null || b == null) return@mapNotNull null
            require(a.kind == b.kind) { "Planet ${planet.tiles.joinToString("-")}: edge slot $slot joins a ${a.kind} line to a ${b.kind} one" }
            val steps = listOfNotNull(a.steps, b.steps)
            require(a.kind == ConnectorKind.TRIPLE || steps.size == 1) {
                "Planet ${planet.tiles.joinToString("-")}: the line at edge slot $slot needs its steps on exactly one tile"
            }
            Seam(first.id, a.from, second.id, b.from, a.kind, steps.singleOrNull())
        }
    }

    fun pattern(id: String = Sector.STANDARD_PATTERN): ConnectionPattern =
        sector.patterns.firstOrNull { it.id == id } ?: throw NoSuchElementException("No connection pattern '$id'")

    companion object {
        /** Version of the JSON content format; each file states it and must match. */
        const val FORMAT_VERSION = 1
    }
}

private fun <T> List<T>.associateByUnique(what: String, key: (T) -> String): Map<String, T> {
    val map = LinkedHashMap<String, T>()
    for (item in this) {
        val id = key(item)
        require(map.put(id, item) == null) { "Duplicate $what id '$id'" }
    }
    return map
}

/** A travel line between the two tiles of a planet: [fromArea] of [fromTile] to [toArea] of [toTile]. */
data class Seam(
    val fromTile: String,
    val fromArea: String,
    val toTile: String,
    val toArea: String,
    val kind: ConnectorKind,
    /** Land steps of a single line; null for a triple one. */
    val steps: Int?,
)
