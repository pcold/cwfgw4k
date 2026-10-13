package com.cwfgw.config

import com.sksamuel.hoplite.ConfigFailure
import com.sksamuel.hoplite.ConfigResult
import com.sksamuel.hoplite.DecoderContext
import com.sksamuel.hoplite.LongNode
import com.sksamuel.hoplite.Node
import com.sksamuel.hoplite.StringNode
import com.sksamuel.hoplite.decoder.NullHandlingDecoder
import com.sksamuel.hoplite.fp.invalid
import com.sksamuel.hoplite.fp.valid
import kotlin.reflect.KType

private const val MILLIS_PER_SECOND = 1_000L

/**
 * For a wait between repeats: `delay` returns at once for zero or less, so a loop holding one would spin.
 * Capped where the count in milliseconds still fits a `Long`, because past that it wraps negative.
 */
@JvmInline
value class PositiveSeconds private constructor(val value: Long) {
    companion object {
        val MAX: PositiveSeconds = PositiveSeconds(Long.MAX_VALUE / MILLIS_PER_SECOND)

        fun of(seconds: Long): PositiveSeconds? = if (seconds in 1..MAX.value) PositiveSeconds(seconds) else null
    }
}

/** Hoplite's inline-class fallback cannot call the private constructor, so without this no config loads at all. */
internal class PositiveSecondsDecoder : NullHandlingDecoder<PositiveSeconds> {
    override fun supports(type: KType): Boolean = type.classifier == PositiveSeconds::class

    override fun safeDecode(
        node: Node,
        type: KType,
        context: DecoderContext,
    ): ConfigResult<PositiveSeconds> {
        val seconds =
            when (node) {
                is StringNode -> node.value.toLongOrNull()
                is LongNode -> node.value
                else -> null
            }
        return seconds?.let(PositiveSeconds::of)?.valid() ?: ConfigFailure.DecodeError(node, type).invalid()
    }
}
