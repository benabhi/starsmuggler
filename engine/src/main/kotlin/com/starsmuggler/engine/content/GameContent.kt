package com.starsmuggler.engine.content

/**
 * Everything the engine reads from the game: sector, tiles, tables, rule paragraphs, equipment
 * and goods, loaded from the versioned JSON of `:content`. It holds ids and values only; texts
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
) {
    val systems: Map<String, StarSystem> = sector.systems.associateByUnique("system") { it.id }
    val tiles: Map<String, Tile> = tiles.associateByUnique("tile") { it.id }
    val tables: Map<String, DiceTable> = tables.associateByUnique("table") { it.id }
    val factors: Map<String, FactorTable> = factors.associateByUnique("factor table") { it.id }
    val paragraphs: Map<String, RuleParagraph> = paragraphs.associateByUnique("paragraph") { it.rule.id }
    val equipment: Map<String, Equipment> = equipment.associateByUnique("equipment") { it.id }
    val goods: Map<String, Goods> = goods.associateByUnique("goods") { it.id }

    init {
        for (pattern in sector.patterns) {
            for (route in pattern.routes) {
                require(route.a in systems && route.b in systems) { "Pattern ${pattern.id}: route ${route.a}-${route.b} names an unknown system" }
            }
            val placed = pattern.nodes.map { it.system }.toSet()
            require(placed == systems.keys) { "Pattern ${pattern.id} must place every system once; differs in ${(placed - systems.keys) + (systems.keys - placed)}" }
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
