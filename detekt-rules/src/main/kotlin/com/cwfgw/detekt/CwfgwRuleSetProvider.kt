package com.cwfgw.detekt

import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider

/**
 * Found through `META-INF/services/dev.detekt.api.RuleSetProvider`, which is why the
 * class has no caller anywhere.
 *
 * [RULE_SET_ID] is also load-bearing in `detekt.yml`: detekt rejects a config section
 * naming a ruleset it did not load, so the `cwfgw:` block there fails the build if
 * this module ever stops reaching a `detektPlugins` classpath.
 */
public class CwfgwRuleSetProvider : RuleSetProvider {
    override val ruleSetId: RuleSetId = RuleSetId(RULE_SET_ID)

    override fun instance(): RuleSet = RuleSet(ruleSetId, listOf(::KDocLength, ::UnsafeMessageInterpolation))

    public companion object {
        public const val RULE_SET_ID: String = "cwfgw"
    }
}
