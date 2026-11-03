package com.starsmuggler.engine.content

import kotlinx.serialization.Serializable

/** An equipment item of the equipment table (r236). Its name is a text asset keyed by [id]. */
@Serializable
data class Equipment(
    val id: String,
    val rule: RuleRef,
    /** Capacity units it takes (r211a); null when it is not carried as cargo ("—" in r236). */
    val capacity: Int? = null,
    /** "1+": it takes as many units as its size, such as a stasis unit. */
    val sized: Boolean = false,
)
