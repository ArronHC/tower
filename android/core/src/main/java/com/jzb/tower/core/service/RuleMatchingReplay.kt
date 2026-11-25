package com.jzb.tower.core.service

import com.jzb.tower.core.model.RuleSchemeCustomization
import kotlinx.serialization.Serializable

/**
 * Group name order alone loses which group moved in an adjacent swap.
 * Keep the moves with the snapshot so the same rule blocks move after a restart.
 */
@Serializable
internal data class RuleMatchingMove(
    val group: String,
    val anchor: String,
    val afterward: Boolean,
    val before: String? = null,
)

@Serializable
internal data class RuleMatchingReplay(
    val schemeId: String,
    val groupOrder: List<String>,
    val matchingOrder: List<String>,
    val baseGroupOrder: List<String>,
    val baseMatchingOrder: List<String>,
    val moves: List<RuleMatchingMove>,
) {
    fun matches(id: String, customization: RuleSchemeCustomization) =
        schemeId == id && groupOrder == customization.groupOrder && matchingOrder == customization.matchingOrder
}
