package com.starsmuggler.engine.content

/**
 * Everything the engine reads from the game: sector, tiles, tables and equipment, loaded from
 * the versioned JSON of `:content`. It holds ids and values only; texts are looked up by id.
 */
class GameContent(
    val sector: Sector,
    tiles: List<Tile>,
    tables: List<DiceTable>,
    equipment: List<Equipment>,
) {
    val systems: Map<String, StarSystem> = sector.systems.associateByUnique("system") { it.id }
    val tiles: Map<String, Tile> = tiles.associateByUnique("tile") { it.id }
    val tables: Map<String, DiceTable> = tables.associateByUnique("table") { it.id }
    val equipment: Map<String, Equipment> = equipment.associateByUnique("equipment") { it.id }

    fun system(id: String): StarSystem = systems[id] ?: throw NoSuchElementException("No system '$id'")

    fun tile(id: String): Tile = tiles[id] ?: throw NoSuchElementException("No tile '$id'")

    fun table(id: String): DiceTable = tables[id] ?: throw NoSuchElementException("No table '$id'")

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
