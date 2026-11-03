package com.starsmuggler.engine.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A numbered rule paragraph from r300 to r339 that events and tables send the player to.
 * The data says what kind of paragraph it is and its numbers; the engine applies each kind,
 * and its full text is a text asset keyed by [rule].
 */
@Serializable
data class RuleParagraph(
    val rule: RuleRef,
    val kind: ParagraphKind,
    /** Base price in Sees before the r241 multiplier, for bribes, purchases and sales. */
    val basePrice: Int? = null,
    /** A base price rolled as [basePriceDice] x 100 S instead (r333). */
    val basePriceDice: Dice? = null,
    /** The table this paragraph rolls on, such as `r327` or `r331`. */
    val table: String? = null,
    /** What can be sold here, by goods or equipment id (r334 to r339). */
    val sells: List<String> = emptyList(),
) {
    init {
        require(basePrice == null || basePriceDice == null) { "Paragraph $rule has two base prices" }
    }
}

@Serializable
enum class ParagraphKind {
    @SerialName("surprise") SURPRISE,
    @SerialName("surprised") SURPRISED,
    @SerialName("escape") ESCAPE,
    @SerialName("escape_vehicle") ESCAPE_VEHICLE,
    @SerialName("combat") COMBAT,
    @SerialName("bribe_pass") BRIBE_PASS,
    @SerialName("pass") PASS,
    @SerialName("pass_unarmed") PASS_UNARMED,
    @SerialName("battle") BATTLE,
    @SerialName("entry_rumor") ENTRY_RUMOR,
    @SerialName("entry_map") ENTRY_MAP,
    @SerialName("bribe_entry") BRIBE_ENTRY,
    @SerialName("buy") BUY,
    @SerialName("sell") SELL,
}

/** A trade good from the purchase and sale paragraphs (r331 to r339). Its name is a text asset keyed by [id]. */
@Serializable
data class Goods(
    val id: String,
    val rule: RuleRef,
    /** Capacity units per unit of the goods (r211a); null when the rules give no size. */
    val capacity: Int? = null,
)
