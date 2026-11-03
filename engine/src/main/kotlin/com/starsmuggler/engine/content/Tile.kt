package com.starsmuggler.engine.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A map tile (A to K, or the asteroids): its land areas and the connectors between them (r204g).
 * A planet tile is half a planet, drawn on the [planetSide] of the scan; its flat edge has
 * [EDGE_SLOTS] places where travel lines continue onto the other tile of the planet.
 */
@Serializable
data class Tile(
    val id: String,
    /** Where the planet half is on the scan; null for the asteroid belt. */
    val planetSide: PlanetSide? = null,
    /** The scan the app draws, as an asset path. */
    val image: String,
    val areas: List<Area>,
    val connectors: List<Connector>,
) {
    init {
        val ids = areas.map { it.id }
        require(ids.toSet().size == ids.size) { "Tile $id repeats an area id" }
        for (c in connectors) {
            require(c.from in ids) { "Tile $id: connector from unknown area '${c.from}'" }
            require(c.to == null || c.to in ids) { "Tile $id: connector to unknown area '${c.to}'" }
            require(c.edgeSlot == null || c.edgeSlot in 0 until EDGE_SLOTS) { "Tile $id: edge slot ${c.edgeSlot} outside 0..${EDGE_SLOTS - 1}" }
        }
        val slots = connectors.mapNotNull { it.edgeSlot }
        require(slots.toSet().size == slots.size) { "Tile $id uses an edge slot twice" }
        require(planetSide != null || slots.isEmpty()) { "Tile $id has no planet edge but uses edge slots" }
    }

    fun area(areaId: String): Area? = areas.firstOrNull { it.id == areaId }

    fun edge(slot: Int): Connector? = connectors.firstOrNull { it.edgeSlot == slot }

    companion object {
        /** Travel lines that cross a planet's seam: top, middle and bottom. */
        const val EDGE_SLOTS = 3
    }
}

@Serializable
enum class PlanetSide {
    @SerialName("left") LEFT,
    @SerialName("right") RIGHT,
}

/** A land area of a tile, drawn as a colored symbol (r204g). */
@Serializable
data class Area(
    /** Unique within its tile, such as `colony`. */
    val id: String,
    val type: AreaType,
    /** Symbol color: which ways of travel may enter. */
    val restriction: Restriction,
    /** White name: good air inside the area whatever the planet's air. */
    val ownAir: Boolean = false,
    /** [b]: banking services (r232a). */
    val bank: Boolean = false,
    /** [m]: medical services (r231c). */
    val medical: Boolean = false,
    /** Center of the symbol on the tile scan, for the touch layer. */
    val position: MapPoint,
)

/** A point on a tile scan as fractions of its width and height, 0 to 1 from the top left. */
@Serializable
data class MapPoint(val x: Float, val y: Float) {
    init {
        require(x in 0f..1f && y in 0f..1f) { "Map point ($x, $y) outside the tile" }
    }
}

/**
 * A travel line from area [from] (r204g): either to another area of the same tile ([to]), or to
 * the tile edge ([edgeSlot], 0 at the top), where it meets the other tile of the planet. A single
 * line that crosses the seam prints its steps on one of the two tiles only.
 */
@Serializable
data class Connector(
    val from: String,
    val to: String? = null,
    val edgeSlot: Int? = null,
    val kind: ConnectorKind,
    /** Land steps of a single-line connector; a triple connector always takes one hour. */
    val steps: Int? = null,
) {
    init {
        require((to == null) != (edgeSlot == null)) { "Connector from $from needs exactly one of 'to' or 'edgeSlot'" }
        require(kind == ConnectorKind.SINGLE || steps == null) { "Connector from $from: only single-line connectors have steps" }
        require(kind == ConnectorKind.TRIPLE || steps != null || edgeSlot != null) { "Connector from $from to $to needs its steps" }
        require(steps == null || steps > 0) { "Connector from $from: steps must be positive" }
    }
}

@Serializable
enum class ConnectorKind {
    /** Short triple line: the areas touch, one hour of travel. */
    @SerialName("triple") TRIPLE,

    /** Long single line with its distance in land steps. */
    @SerialName("single") SINGLE,
}

/** Entry restriction shown by the symbol color (r204g). */
@Serializable
enum class Restriction {
    /** Any way of travel. */
    @SerialName("white") WHITE,

    /** Not by starship or hopper. */
    @SerialName("yellow") YELLOW,

    /** On foot or by commercial vehicle only. */
    @SerialName("red") RED,

    /** By starship, hopper or orbital shuttle only. */
    @SerialName("purple") PURPLE,
}

/** The sixteen area types (r205) and the asteroids (r206), each with its rule paragraph. Names are text assets keyed by the serial name. */
@Serializable
enum class AreaType(val rule: RuleRef) {
    @SerialName("city") CITY(RuleRef("r205a")),
    @SerialName("colony") COLONY(RuleRef("r205b")),
    @SerialName("craters") CRATERS(RuleRef("r205c")),
    @SerialName("gambling") GAMBLING(RuleRef("r205d")),
    @SerialName("industrial") INDUSTRIAL(RuleRef("r205e")),
    @SerialName("military") MILITARY(RuleRef("r205f")),
    @SerialName("mining") MINING(RuleRef("r205g")),
    @SerialName("palace") PALACE(RuleRef("r205h")),
    @SerialName("prison") PRISON(RuleRef("r205i")),
    @SerialName("rough_terrain") ROUGH_TERRAIN(RuleRef("r205j")),
    @SerialName("ruins") RUINS(RuleRef("r205k")),
    @SerialName("rural") RURAL(RuleRef("r205l")),
    @SerialName("scientific") SCIENTIFIC(RuleRef("r205m")),
    @SerialName("slum") SLUM(RuleRef("r205n")),
    @SerialName("spaceport") SPACEPORT(RuleRef("r205o")),
    @SerialName("space_station") SPACE_STATION(RuleRef("r205p")),

    /** One of the six asteroids of the belt tile (r206); not one of the sixteen area types. */
    @SerialName("asteroid") ASTEROID(RuleRef("r206")),
    ;

    /** Same as the serial name: the key of its name in the text assets. */
    val key: String get() = name.lowercase()
}
