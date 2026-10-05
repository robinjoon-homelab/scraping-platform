package com.example.quality

import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider

class CleanCodeRuleSetProvider : RuleSetProvider {
    override val ruleSetId = RuleSetId("clean-code")

    override fun instance() =
        RuleSet(
            ruleSetId,
            listOf(::LogicalNestingDepth, ::NoMutableCollectionExposure, ::MaximumFunctionLength),
        )
}
